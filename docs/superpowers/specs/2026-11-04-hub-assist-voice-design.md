# Voice through the hub's assistant — design

Status: draft, 2026-11-04. Author: Gekkotron.

## 1. Intent

The phone's voice assistant currently understands sentences itself: `RuleMatcher`, `VoiceLexicon`, `Suggester`,
taught shortcuts on `nidoo/voice/shortcut/*`, the "Autre…" picker, `AnswerFormatter` and Android text-to-speech.
The hub (Oikos-os, `nidoo-assist`) now has its own assistant: `POST /api/v1/assist` and hub-side speech
(`POST /api/v1/assist/speech`). Under the hub migration policy (one owner per feature, the old path deleted), the
assistant belongs to the hub only.

The app becomes a thin microphone: it turns speech into text on the phone, sends the text to the hub, shows the hub's
reply, and plays the audio the hub produces. Every understanding rule leaves the app.

## 2. Goals

- One assistant, one behaviour: the phone says exactly what the hub's web Assistant page would say for the same sentence.
- Works at home (Wi-Fi, direct to the hub) and away (through the encryption gateway), with the same route the app
  already uses for Flows and History.
- Hub questions work from the phone: a room choice (« Dans quelle pièce ? ») and a confirmation (Oui / Non).
- The hub's voice is the phone's voice: the reply audio is the hub's WAV, played by the app.

## 3. Non-goals

- English. The hub understands French only, so the phone always listens in `fr-FR`, whatever the app language.
  (User: "for now only French".)
- Anything the hub does not do yet: shutters, plugs, the alarm, yes/no state questions, brightness, scenes. The phone
  loses them until the hub learns them.
- A Simulation switch, the "How it understood" trace, the Wrong/Faux button, the turn history. They stay on the web page.
- Speech-to-text on the hub (satellites). Recognition stays on the phone.

## 4. Anti-goals

- No understanding rule, lexicon, fallback matcher or shortcut left in the app, not even "temporarily". If the hub does
  not understand, the phone says what the hub said.
- No phone text-to-speech fallback (see §7.6).
- No new MQTT topic for the assistant. HTTP only, over the existing LAN-or-gateway route.

## 5. Constraints

- The hub's contract (Oikos-os `docs/reference/http-api.md#assist` and `#speech`, `crates/nidoo-assist/src/view.rs`):
  - `POST /api/v1/assist` body `{client, text?, reply?, simulate?}` (camelCase). `reply` is
    `{"type":"choice","pending","choice":<index>}`, `{"type":"yes","pending"}` or `{"type":"no","pending"}`.
    Returns `200` with a turn `{id, at, client, text, simulate, outcome, reply, question, trace, wrong}`;
    `outcome` ∈ `acted | answered | asked | confirming | not_understood | failed`; `question` is `null` or
    `{pending, kind: "choice" | "confirm", choices: [String]}` (choices empty for a confirmation). `422` without
    text/reply, or for an unknown or expired `pending` (`question expired`). One pending question per `client`, 60 s.
    A typed `text` that is yes/no or names a choice answers the pending question; any other text drops it.
  - `POST /api/v1/assist/speech` body `{text, language?, voice?}` (snake_case) → `201 {id, url}`, `url` =
    `/api/v1/assist/speech/{id}.wav`, valid 60 s. `409` when no language/model is ready, `503` when the queue (4) is
    full, `422` for empty or > 500 characters. The WAV is 24 kHz, 16-bit, mono, with a header of unknown length
    (streamed).
  - v1 hub has no auth.
- The hub's base URL is the profile's `effectiveHistoryUrl()` (same host and port as History and Flows; see
  `app/.../di/FlowsModule.kt`). Only a `ConnectionProfile.Direct` has a hub; `Demo` and no profile do not.
- Transport is `LanOrGatewayGet` (`core/transport`): LAN when the probe says reachable, gateway otherwise.
  Its `post()` today returns only status + headers. The gateway SDK's `GatewayResponse` already carries `body`
  (`JsonElement`), and the LAN path has the OkHttp response; the app drops both.
- Android 12+ only for voice (unchanged: on-device recognizer).
- Voice stays opt-in: the Settings toggle, off by default.
- KMP migration rules apply ([[oikos-kmp-migration]]): pure models and the HTTP client go in a KMP `core` module's
  `commonMain`/`jvmMain` like `core/flows`; Android pieces stay in `feature/voice`.

## 6. Decided by the user

- The app only sends text to the hub, over Wi-Fi or through the gateway; the hub handles it.
- The audio comes from the hub and the app plays it.
- French only for now.
- Phone-side rules, shortcuts and the "Autre…" picker are deleted; losing shutters/alarm/English until the hub has
  them is accepted.

## 7. Design

### 7.1 Flow

1. Trigger (unchanged): Home mic button, mic on the existing widget, voice widget → `VoiceActivity` sheet.
   Voice off → the existing `ErrorKind.DISABLED` state (PR #24) — unchanged.
2. Listening (unchanged UI): `AndroidSpeechCapture(context, "fr-FR")`.
3. Final text → "Envoi…" state → `POST /api/v1/assist {client, text}`.
4. The turn arrives. The sheet shows `turn.reply` as text and, at the same time, the app asks the hub for its audio
   (§7.4). Then by `outcome`:
   - `acted`, `answered`: reply shown, audio plays; the sheet closes 1.5 s after the audio ends (or 5 s after the
     reply appears when there is no audio).
   - `asked` (`question.kind = choice`): reply shown ("Dans quelle pièce ?") + one button per `choices[i]`.
     Tap → `POST {client, reply: {type: "choice", pending, choice: i}}` → back to step 4 with the new turn.
   - `confirming` (`question.kind = confirm`): reply + **Oui** / **Non** buttons → `reply: {type: "yes"|"no", pending}`.
   - `not_understood`, `failed`: reply shown (the hub's own "Je n'ai pas compris…" with example sentences), audio
     plays, sheet stays open with **Réessayer** (listen again).
   - A question state also has a mic button **Répondre** that listens again and sends the heard text as `text`
     (the hub accepts "oui", "Léa"…). Builder's call on its exact placement.
5. The sheet's existing "repeat" affordance replays the last WAV (already downloaded; no new request).

Question expiry *(my call)*: the sheet stays open while a question is pending. 60 s after the turn arrived, the
buttons and Répondre are replaced by "La question a expiré." + Réessayer (a fresh listen). A tap that still races the
hub's expiry and gets `422 question expired` lands in the same state. No auto-dismiss.

The decision of what a turn means is driven by `question` first (non-null → show buttons for its `kind`), `outcome`
second. Unknown `outcome` strings are treated as `failed` (forward-compatible).

### 7.2 Errors

| Situation | What the sheet says |
| --- | --- |
| Profile is `Demo` / none | "L'assistant a besoin du hub." (no request) |
| `HttpPostResult.Unavailable` | "Hub injoignable." + Réessayer |
| `HttpPostResult.NotAllowed` (gateway key lacks the hub host) | "La passerelle n'autorise pas le hub : ajoutez son adresse aux règles de la clé." |
| `422` with "question expired" | "La question a expiré." + Réessayer |
| any other non-2xx / unparsable body | "Le hub n'a pas pu répondre." + Réessayer |
| Speech failures (`409`, `503`, `422`, download failure) | Not an error: the reply text stays, no audio, nothing else said. |

`ErrorKind.MQTT_DOWN` and `awaitVoiceSourceReady` go away: voice no longer needs the MQTT link or the entity
catalog to start. Speech-recognizer errors (`SILENCE`, `PERMISSION`, `SPEECH`) stay as they are.

### 7.3 Client id

A stable per-install id, generated once (random UUID) and stored in `AppPreferences`, sent as `client`
(e.g. `android-<uuid>`). It scopes the hub's one-pending-question-per-client rule to this phone. *(My call.)*

### 7.4 Audio

- Gateway capability (checked 2026-11-04): the gateway's `http` call already returns the upstream body
  (`GatewayResponse.body`), and its `download` returns the full upstream body as bytes once the upstream closes — the
  same path that carries Frigate snapshots and clips over 4G. A streamed WAV ends when the hub finishes synthesis, well
  inside the 30 s gateway timeout for a one-line reply. **First plan task is a spike on the device away from home**
  (POST speech + GET wav through the gateway); if it fails, away use is text-only and the rest of the design stands.
- **Gateway spike 2026-11-04: OK** (from the Mac through the real gateway, `simulate: true`): assist `200` in 335 ms, speech `201` in 34 ms, WAV `200 audio/wav` 126 764 bytes starting `RIFF` in 714 ms. Note: the SDK clock argument is in seconds.
- The speech `text` is `turn.reply` as is; a reply over 500 characters is not sent for speech (text only). *(My call.)*
- After each turn with a non-blank `reply`: `POST /api/v1/assist/speech {text: reply}` (no `language`: the house
  language, French; no `voice`: the language's chosen voice), then GET the returned `url` resolved against the hub
  base URL, through `LanOrGatewayGet.get(…, maxBytes = 4 MiB)`. Downloaded fully, then played. *(My call: full
  download is simpler and works identically over the gateway, which cannot stream; replies are short.)*
- Playback: builder's call (`MediaPlayer` from a cache file, or `AudioTrack` after parsing the WAV header — the header's
  data length is unknown/streaming, so a parser must not trust it).
- Audio focus: request transient focus with ducking while playing; release after.
- A new turn, a tap, Réessayer, or closing the sheet stops current playback and cancels a pending speech request.
- The request for audio must not delay showing the reply text.

### 7.5 Transport change

`LanOrGatewayGet.post()` returns the response body:
`HttpPostResult.Response(status, headers, body: String)` (UTF-8; empty when none). LAN: read the OkHttp body (cap it,
e.g. 256 KiB). Gateway: `RawPost` gains `body: String` from `GatewayResponse.body` (`JsonElement.toString()`; a
`JsonNull` → empty). Existing callers (Flows, Frigate login…) keep working and ignore it. Tests in
`core/transport` cover both paths.

### 7.6 No phone TTS *(my call)*

The user did not choose a fallback for when the hub cannot speak. Following "the audio comes from the hub", there is
none: the reply stays as text. `TtsSpeaker`, `AndroidTtsEngine`, `TtsAvailability` and the Settings TTS
availability line / `ACTION_INSTALL_TTS_DATA` button are deleted.

### 7.7 Code moves

New:
- `core/assist` (KMP, like `core/flows`): `AssistTurn`, `AssistQuestion`, `AssistOutcome`, `AssistReply` models +
  kotlinx-serialization; `HttpAssistClient(baseUrl: suspend () -> String?, post: suspend (url, json) -> HttpPostResult-like, get: …)`
  with `send(text)`, `answer(reply)`, `speech(text): ByteArray?`. Pure, unit-tested against JSON fixtures copied
  from the hub's own tests/docs.
- `app/.../di/AssistModule.kt`: wires it like `FlowsModule` (same base URL, same `LanOrGatewayGet`).

Rewritten:
- `feature/voice`: `VoiceSession` / `VoiceViewModel` become: listen → send → turn states → reply/answer → audio.
  New session states roughly `Listening`, `Sending`, `Turn(turn, audioState)`, `Error(kind)`.
  New views for the question buttons; `ListeningView`, sheet chrome, `VoiceActivity`, widgets and triggers kept.
- `SpeechCapture` locale fixed to `fr-FR`.

Deleted:
- The whole `:core:voice` module (RuleMatcher, VoiceLexicon, Suggester, CommandMapper, VoiceCatalog,
  AnswerFormatter, Confirmation, VoiceShortcut, VoiceText, VoiceModels…) and its tests; `include(":core:voice")`.
- `feature/voice`: `VoiceCommandRunner`/`CommandRunner`, `TtsSpeaker`, `DevicePickerView`, `ActionPickerView`,
  `UnderstoodView`/`ConfirmView`/`NotUnderstoodView` as they exist (replaced), `VoiceReadiness` if it only served the
  entity wait.
- `core/source`: `EntitySource.shortcuts`, `saveShortcut`, `deleteShortcut`, `VoiceShortcutDocument`,
  `MqttDiscoverySource` shortcut handling, the `api(project(":core:voice"))` dependency; every `FakeEntitySource`
  in feature tests.
- `feature/settings`: `VoiceShortcutsSection`, `ShortcutRow`, shortcut deletion in `SettingsViewModel`, the TTS
  availability line. The Voice page keeps the on/off toggle and the speech-recognizer availability line.
- Strings for the removed UI (FR + EN).

Retained topics on the broker: the app now receives `nidoo/voice/shortcut/*` under its `nidoo/#` subscription and
must ignore them silently (no "invalid"/unknown-topic report). Clearing them from the broker is left to the user
(one empty retained publish per topic); the design does not do it. *(My call.)*

### 7.8 Testing

- `core/assist`: request JSON (text, each reply type), turn parsing (each outcome, question kinds, null question,
  unknown outcome), speech URL resolution, 422 "question expired" mapping.
- `core/transport`: post body on LAN and gateway, size cap.
- `feature/voice`: session state machine with a fake client + fake speech capture + fake player: acted closes after
  audio, asked → choice tap sends the right `pending`/index, confirm Oui/Non, not_understood keeps the sheet open,
  speech `409` leaves text and no error, errors table §7.2, new turn stops playback.
- Robolectric screenshot of the question state (choices) and the not-understood state.
- Device check (on the user): at home on Wi-Fi and away on the gateway — « allume la lumière du salon »,
  « mets la chambre à 25 » (confirm Oui), « éteins la chambre » with two bedrooms (choice), « il fait combien
  dehors ? » (audio plays), a sentence about shutters (hub's not-understood reply).

## 8. Left to the builder

- Exact sheet layout of the question buttons and the Répondre mic, within the existing Ivoire theme.
- WAV playback mechanism (§7.4).
- Module name (`core/assist` suggested) and how `HttpAssistClient` receives the GET/POST lambdas.
- Whether the "Envoi…" state shows the heard sentence (suggested: yes, greyed, so the user sees what the hub got).

## 9. Hub-side prerequisites (user owns)

- A French Phonon model installed and Ready on the hub, or the phone stays silent (text only).
- For away use: the gateway key's rules must allow the hub host:port (same rule History/Flows already need). The hub
  serves every `/api/v1/*` route on that one port, and gateway rules filter by host and port, not path, so no new rule.
