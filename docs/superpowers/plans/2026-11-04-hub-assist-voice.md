# Voice through the hub's assistant — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The mic sends the recognized French sentence to the hub's `/api/v1/assist`, shows the hub's turn (reply, room choice, Oui/Non), and plays the hub's speech WAV; every phone-side understanding rule is deleted.

**Architecture:** A new KMP module `:core:assist` holds the wire models and `HttpAssistClient` over the existing `LanOrGatewayGet` (LAN direct, gateway away). `:feature:voice` keeps speech capture, the sheet and the triggers, and gets a new `VoiceSession` state machine plus a WAV player. `:core:voice`, shortcuts in `:core:source`, the picker and Android TTS are deleted.

**Tech Stack:** Kotlin 2 / KMP (jvm), kotlinx-serialization, coroutines, Hilt, Jetpack Compose, Android `SpeechRecognizer` + `AudioTrack`, JUnit 5 (core) / JUnit 4 + Robolectric (feature).

**Spec:** `docs/superpowers/specs/2026-11-04-hub-assist-voice-design.md`

## Global Constraints

- French only: the recognizer always uses `"fr-FR"`; no English path.
- No understanding rule, lexicon, shortcut or phone TTS may remain in the app (spec §4).
- Hub base URL = `ConnectionProfile.Direct.effectiveHistoryUrl()`; `Demo` / no profile → no request (`NoHub`).
- Transport = `LanOrGatewayGet` only; no new MQTT topic.
- `POST /api/v1/assist` body is camelCase `{client, text}` or `{client, reply}`; never send `simulate`.
- `POST /api/v1/assist/speech` body `{"text": reply}` only; skip speech when `reply` is blank or longer than 500 characters.
- WAV download cap: 4 MiB (`4L * 1024 * 1024`).
- Question expiry on the phone: 60 s after the turn arrived.
- Auto-close: 1.5 s after audio ends; 5 s after the turn arrived when there is no audio; only for `acted`/`answered` with no question.
- Unknown `outcome` strings are treated as `failed`.
- Voice remains opt-in (Settings toggle, default off) and Android 12+ only.
- Commits use `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …` and end with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01DoAkWEPKe8L6AyDC7xs4R9`.
- Never write the real name of the user anywhere; bylines are `Gekkotron`.

## Review Focus

1. **The phone loses the network between the text POST and the speech GET** (LAN → 4G mid-turn): the reply text must still show and the sheet must not show an error — pinned in Task 5 (`speech failure leaves the turn and no error`).
2. **The user taps a choice twice fast, or taps after a new listen started**: only one `answer()` goes out per pending id, and a turn from an older request never replaces a newer state — pinned in Task 5 (`a second tap on the same question is ignored`, `a stale answer does not overwrite a newer listen`).
3. **The sheet is closed while audio plays or a speech request is in flight**: playback stops and nothing plays after close — pinned in Task 5 (`cancel stops playback and drops pending speech`).
4. **The hub's WAV header says data length `0xFFFFFFFF`** (streamed): the parser must take the bytes to the end, not trust the size, and odd trailing bytes must not crash — pinned in Task 3 (`streamed header length is ignored`, `odd trailing byte is dropped`).
5. **Retained `nidoo/voice/shortcut/*` messages still arrive under `nidoo/#`**: they must be ignored without an "invalid" report or an entity — pinned in Task 7 (`retained voice shortcut topics are ignored silently`).

---

### Task 1: LAN-or-gateway POST returns the response body

**Files:**
- Modify: `core/transport/src/main/kotlin/com/gekkotron/nidoo/transport/GatewayDownloader.kt` (`RawPost`, `AthenaGatewayDownloader.post`)
- Modify: `core/transport/src/main/kotlin/com/gekkotron/nidoo/transport/LanOrGatewayGet.kt` (`HttpPostResult.Response`, `post`)
- Test: `core/transport/src/test/kotlin/com/gekkotron/nidoo/transport/LanOrGatewayGetTest.kt`, `GatewayDownloaderTest.kt`
- Touch (compile only, default arg covers them): callers constructing `HttpPostResult.Response(…)` / `RawPost(…)` in `core/alerts` and `core/flows` tests.

**Interfaces:**
- Produces: `class RawPost(val status: Int, val headers: Map<String, String>, val body: String = "")`;
  `class HttpPostResult.Response(val status: Int, val headers: Map<String, String>, val body: String = "")`.

- [ ] **Step 1: Write the failing tests**
  - `LanOrGatewayGetTest`: `a LAN post returns the body` — MockWebServer (as the existing LAN post test) answers `200` with `{"ok":1}`; assert `r.body == """{"ok":1}"""`.
  - `a gateway post returns the body` — `FakeGateway(postAnswer = { RawPost(200, emptyMap(), """{"id":"t1"}""") })`, LAN down; assert `r.body == """{"id":"t1"}"""`.
  - `a LAN post body over 256 KiB is cut to empty and keeps the status` — body of `256 * 1024 + 1` bytes → `r.status == 200`, `r.body == ""`.
  - `GatewayDownloaderTest`: `post maps the gateway body to text` — `send` returns `GatewayResponse(200, buildJsonObject { put("a", 1) }, emptyMap())` → `RawPost.body == """{"a":1}"""`; `JsonNull` body → `""`.
- [ ] **Step 2: Run** `./gradlew :core:transport:test --tests '*LanOrGatewayGetTest*' --tests '*GatewayDownloaderTest*'` — Expected: FAIL (no `body`).
- [ ] **Step 3: Implement.** LAN: read `response.body` as UTF-8 up to `MAX_POST_BODY = 256 * 1024` bytes (over the cap → `""`, status kept). Gateway: `body = if (it.body is JsonNull) "" else it.body.toString()`.
- [ ] **Step 4: Run** `./gradlew :core:transport:test :core:flows:jvmTest :core:alerts:testDebugUnitTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(transport): return the response body of a LAN-or-gateway POST`

---

### Task 2: Gateway audio spike (throwaway, not committed)

Checks spec §7.4 before the UI work: the gateway must carry the assist turn body and the speech WAV.

**Files:**
- Create (temporary, never committed): `core/transport/src/test/kotlin/…/GatewaySpikeTest.kt`, **deleted before Task 3's commit**.

- [ ] **Step 1: Write the spike test** reading `oikos-setup.json` at the repo root (`gateway.url`, `gateway.key` hex, `local.host`), building `AthenaGatewayDownloader(TransportSpec.Gateway(url, key…))`, then through the gateway only:
  1. `post("http://<host>:8081/api/v1/assist", """{"client":"spike","text":"il fait combien dehors ?","simulate":true}""", 30)` → print status and body.
  2. `post("http://<host>:8081/api/v1/assist/speech", """{"text":"Bonjour, je suis l'assistant de la maison."}""", 30)` → print status and body (`{id,url}`).
  3. `download("http://<host>:8081" + url, 30)` → print status, `contentType`, byte count, first 4 bytes.
  Guard with `@EnabledIfEnvironmentVariable(named = "NIDOO_GATEWAY_SPIKE", matches = "1")`. Never print the key.
- [ ] **Step 2: Run** `NIDOO_GATEWAY_SPIKE=1 ./gradlew :core:transport:test --tests '*GatewaySpikeTest*' -i`
  Expected: assist `200` with a JSON turn whose `reply` starts with `Simulation :`; speech `201`; download `200`, `audio/wav`, > 10 000 bytes, starts with `RIFF`.
- [ ] **Step 3: Record the result** in the spec §7.4 as one line (`Gateway spike 2026-11-xx: OK` or the failure). On failure: the speech HTTP 409 (no French model) is a hub prerequisite, not a failure of the route — note it and continue; a gateway failure on the WAV means away use is text only: in Task 5, `AssistClient.speech` must then only be called when `LanOrGatewayGet` would use the LAN — stop and tell the user before continuing.
- [ ] **Step 4: Delete** `GatewaySpikeTest.kt`; `git status` shows nothing under `core/transport/src/test`. Commit only the spec line: `docs(voice): gateway audio spike result`.

---

### Task 3: `:core:assist` — models, HTTP client, WAV parsing

**Files:**
- Create: `core/assist/build.gradle.kts` (copy of `core/flows/build.gradle.kts`)
- Modify: `settings.gradle.kts` (`include(":core:assist")` after `:core:flows`)
- Create: `core/assist/src/commonMain/kotlin/com/gekkotron/nidoo/assist/AssistModels.kt`
- Create: `core/assist/src/commonMain/kotlin/com/gekkotron/nidoo/assist/AssistClient.kt`
- Create: `core/assist/src/jvmMain/kotlin/com/gekkotron/nidoo/assist/HttpAssistClient.kt`
- Create: `core/assist/src/commonMain/kotlin/com/gekkotron/nidoo/assist/WavPcm.kt`
- Test: `core/assist/src/commonTest/kotlin/com/gekkotron/nidoo/assist/AssistModelsTest.kt`, `WavPcmTest.kt`; `core/assist/src/jvmTest/kotlin/com/gekkotron/nidoo/assist/HttpAssistClientTest.kt`

**Interfaces:**
- Consumes: Task 1 `HttpPostResult.Response.body`; `HttpGetResult` from `:core:transport`.
- Produces:
  ```kotlin
  enum class AssistOutcome { ACTED, ANSWERED, ASKED, CONFIRMING, NOT_UNDERSTOOD, FAILED }
  enum class QuestionKind { CHOICE, CONFIRM }
  data class AssistQuestion(val pending: String, val kind: QuestionKind, val choices: List<String>)
  data class AssistTurn(val id: String, val text: String, val outcome: AssistOutcome, val reply: String, val question: AssistQuestion?)
  sealed interface AssistReply { val pending: String
      data class Choice(override val pending: String, val index: Int) : AssistReply
      data class Yes(override val pending: String) : AssistReply
      data class No(override val pending: String) : AssistReply }
  sealed interface AssistResult {
      data class Ok(val turn: AssistTurn) : AssistResult
      data object NoHub : AssistResult
      data object Unreachable : AssistResult
      data object GatewayRefused : AssistResult
      data object QuestionExpired : AssistResult
      data class Failed(val status: Int?) : AssistResult }
  interface AssistClient {
      suspend fun send(text: String): AssistResult
      suspend fun answer(reply: AssistReply): AssistResult
      /** The full WAV, or null on any failure (no hub voice, queue full, too long, network). */
      suspend fun speech(text: String): ByteArray? }
  object AssistJson { fun parseTurn(json: String): AssistTurn?; fun textRequest(client: String, text: String): String; fun replyRequest(client: String, reply: AssistReply): String }
  class HttpAssistClient(
      baseUrl: suspend () -> String?,
      clientId: suspend () -> String,
      post: suspend (url: String, json: String) -> HttpPostResult,
      get: suspend (url: String, maxBytes: Long) -> HttpGetResult,
  ) : AssistClient
  class WavPcm(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val pcm: ByteArray)
  object WavParser { fun parse(bytes: ByteArray): WavPcm? }
  const val MAX_SPEECH_CHARS = 500
  const val MAX_WAV_BYTES = 4L * 1024 * 1024
  ```

- [ ] **Step 1: Write the failing tests**
  - `AssistModelsTest`:
    - `parses an acted turn` — the hub JSON `{"id":"t1","at":1,"client":"c","text":"allume le salon","simulate":false,"outcome":"acted","reply":"Salon allumé.","question":null,"trace":{"words":[]},"wrong":false}` → `AssistTurn("t1","allume le salon",ACTED,"Salon allumé.",null)` (unknown keys ignored).
    - `parses a choice question` — `"outcome":"asked","question":{"pending":"p1","kind":"choice","choices":["Chambre Léa","Chambre parentale"]}` → `AssistQuestion("p1", CHOICE, listOf(…))`.
    - `parses a confirm question with empty choices` → `kind == CONFIRM`, `choices.isEmpty()`.
    - `an unknown outcome is FAILED` — `"outcome":"thinking"` → `FAILED`.
    - `garbage is null` — `parseTurn("nope") == null`.
    - `text request` — `textRequest("android-1","éteins")` == `{"client":"android-1","text":"éteins"}`.
    - `reply requests` — Choice → `{"client":"c","reply":{"type":"choice","pending":"p1","choice":1}}`; Yes → `{"client":"c","reply":{"type":"yes","pending":"p1"}}`; No → `"type":"no"`.
  - `WavPcmTest` (build headers in the test with a small helper):
    - `parses a 24 kHz mono 16-bit file` → `sampleRate == 24000`, `channels == 1`, `bitsPerSample == 16`, `pcm.size == 480`.
    - `streamed header length is ignored` — `data` chunk size `0xFFFFFFFF`, 480 bytes after → `pcm.size == 480`.
    - `odd trailing byte is dropped` — 481 bytes after `data` → `pcm.size == 480`.
    - `skips an unknown chunk before data` — a `LIST` chunk between `fmt ` and `data` → parses.
    - `not RIFF is null`; `missing fmt is null`; `non-PCM format (3) is null`.
  - `HttpAssistClientTest` (fake `post`/`get` recording calls, base `http://192.168.1.91:8081/`, client id `android-x`):
    - `send posts the text to the assist route` → url `http://192.168.1.91:8081/api/v1/assist`, body `{"client":"android-x","text":"allume le salon"}`, `200` body turn → `Ok`.
    - `answer posts the reply` → body contains `"reply":{"type":"yes","pending":"p1"}`.
    - `no base url is NoHub without a request`.
    - `NotAllowed is GatewayRefused`, `Unavailable is Unreachable`.
    - `422 question expired is QuestionExpired` — `HttpPostResult.Response(422, emptyMap(), """{"error":"question expired"}""")`.
    - `another 422 is Failed(422)`; `200 with an unparsable body is Failed(200)`.
    - `speech posts the text then downloads the url` — post answer `201` `{"id":"s1","url":"/api/v1/assist/speech/s1.wav"}` → `get` called with `http://192.168.1.91:8081/api/v1/assist/speech/s1.wav` and `MAX_WAV_BYTES`; returns the bytes.
    - `speech returns null for 409, 503, a failed download, TooLarge, and never posts a blank or 501-char text`.
- [ ] **Step 2: Run** `./gradlew :core:assist:jvmTest` — Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the interfaces above. JSON via a private `Json { ignoreUnknownKeys = true; explicitNulls = false }` with private `@Serializable` DTOs mapped to the public models (outcome/kind as strings, mapped case-insensitively). `QuestionExpired` = status `422` and the `error` field contains `question expired`. `WavParser` walks RIFF chunks little-endian, accepts format tag 1 only, PCM = bytes from after the `data` header to the end, truncated to a whole frame (`channels * bitsPerSample / 8`).
- [ ] **Step 4: Run** `./gradlew :core:assist:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(assist): hub assist client, turn models and WAV parsing`

---

### Task 4: Wire the client in `:app` with a per-install client id

**Files:**
- Modify: `core/data/src/main/kotlin/com/gekkotron/nidoo/data/AppPreferences.kt` (`StoredPreferences.assistClientId: String? = null`; `suspend fun assistClientId(): String`)
- Create: `app/src/main/kotlin/com/gekkotron/nidoo/di/AssistModule.kt`
- Modify: `app/build.gradle.kts` (`implementation(project(":core:assist"))`)
- Test: `core/data/src/test/kotlin/com/gekkotron/nidoo/data/AppPreferencesTest.kt`

**Interfaces:**
- Consumes: Task 3 `HttpAssistClient`, `AssistClient`.
- Produces: `AppPreferences.assistClientId(): String` → `"android-<uuid>"`, generated and stored on first call, the same value afterwards (also across a re-created `AppPreferences` on the same file). Hilt binding `@Singleton AssistClient`.

- [ ] **Step 1: Write failing tests** in `AppPreferencesTest`: `assist client id is created once and kept` (two calls equal, starts with `android-`, a new `AppPreferences.create(sameFile, scope)` returns the same) and `concurrent first calls agree` (two `async` calls → same id).
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests '*AppPreferencesTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** `assistClientId()` with a read-then-`update` under the existing write path (DataStore `updateData` keeps it atomic: generate inside the transform only when null). `AssistModule` mirrors `FlowsModule`: same `LanOrGatewayGet(routes, http, ::isLikelyLocalHost, log = { Log.w("NidooAssist", it) })`, same `baseUrl` lambda, `clientId = preferences::assistClientId`, `post = lanOrGateway::post`, `get = { url, max -> lanOrGateway.get(url, max) }`.
- [ ] **Step 4: Run** `./gradlew :core:data:testDebugUnitTest :app:compileDebugKotlin` — Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(assist): per-install client id and hub assist client wiring`

---

### Task 5: New `VoiceSession` state machine and WAV player

**Files:**
- Rewrite: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/VoiceSession.kt`
- Rewrite: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/VoiceViewModel.kt`
- Create: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/AudioPlayer.kt`
- Modify: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/di/VoiceProvidesModule.kt` (bind `AudioPlayer`; drop runner + TTS)
- Delete: `VoiceCommandRunner.kt`, `TtsSpeaker.kt`, `VoiceReadiness.kt` and their tests (`VoiceCommandRunnerTest`, `TtsSpeakerTest`, `VoiceReadinessTest`, `AutoCloseTest`)
- Modify: `feature/voice/build.gradle.kts` (`:core:assist` in, `:core:voice` and `:core:source` out)
- Test: rewrite `feature/voice/src/test/kotlin/com/gekkotron/nidoo/feature/voice/VoiceSessionTest.kt`, `Fakes.kt`; delete `Fixtures.kt`

**Interfaces:**
- Consumes: Task 3 `AssistClient`, `AssistTurn`, `AssistReply`, `AssistResult`, `WavParser`.
- Produces:
  ```kotlin
  sealed interface VoiceSessionState {
      data class Listening(val partial: String = "", val answering: Boolean = false) : VoiceSessionState
      data class Sending(val heard: String?) : VoiceSessionState          // null after a tap
      data class Turn(val turn: AssistTurn, val expired: Boolean = false) : VoiceSessionState
      data class Error(val kind: ErrorKind) : VoiceSessionState
  }
  enum class ErrorKind { NO_HUB, HUB_UNREACHABLE, GATEWAY_REFUSED, HUB_FAILED, SPEECH, PERMISSION, SILENCE, DISABLED }
  interface AudioPlayer { suspend fun play(wav: ByteArray): Boolean; fun stop() }   // play suspends until the end; false = not playable
  class VoiceSession(client: AssistClient, speech: SpeechCapture, player: AudioPlayer, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis) {
      val state: StateFlow<VoiceSessionState>
      val closeRequested: StateFlow<Boolean>
      val canRepeat: StateFlow<Boolean>
      fun start()                 // listen → send
      fun choose(index: Int)      // Choice reply for the pending question
      fun confirm(yes: Boolean)   // Yes / No reply
      fun listenAgain()           // Réessayer / Répondre: listen, send the text (answers the hub's pending question if any)
      fun repeat()                // replay the last WAV, no request
      fun cancel()                // stop speech capture, playback and in-flight work
  }
  const val QUESTION_TTL_MS = 60_000L; const val AUDIO_TAIL_MS = 1_500L; const val NO_AUDIO_CLOSE_MS = 5_000L
  ```
  `VoiceViewModel` exposes `state`, `closeRequested`, `canRepeat`, `preferences`, `start()`, `choose`, `confirm`, `listenAgain`, `repeat`, `showDisabled()`, `cancel()`. `start()` takes no language: it builds `AndroidSpeechCapture(appContext, "fr-FR")`.

- [ ] **Step 1: Write the failing tests** in `VoiceSessionTest` (`runTest` + `StandardTestDispatcher`, fakes: `FakeSpeechCapture` (kept), `FakeAssistClient` (queued `AssistResult`s, records calls, `speech` returns queued `ByteArray?`, optional suspend gate), `FakePlayer` (records plays, completes on `finish()`)):
  - `a heard sentence is sent and the turn is shown` → client got `send("allume le salon")`, state `Turn(turn)`.
  - `sending shows the heard text` → `Sending("allume le salon")` while the client is gated.
  - `an acted turn plays the hub audio then requests close 1500 ms after it ends`.
  - `an answered turn without audio requests close 5000 ms after the turn`.
  - `a choice tap sends Choice(pending, index) and shows the next turn`.
  - `a confirm tap sends Yes or No`.
  - `a second tap on the same question is ignored` → one `answer()` call.
  - `a question does not request close and expires after 60 s` → `Turn(expired = true)`, `closeRequested == false`.
  - `a tap answered QuestionExpired shows the turn as expired`.
  - `not understood stays open` → `closeRequested == false` after 10 s.
  - `NoHub, Unreachable, GatewayRefused, Failed map to NO_HUB, HUB_UNREACHABLE, GATEWAY_REFUSED, HUB_FAILED`.
  - `speech failure leaves the turn and no error` → speech `null`: state stays `Turn`, `canRepeat == false`.
  - `the reply shows before the audio arrives` → state `Turn` while `speech` is gated.
  - `repeat replays the last wav without a request`.
  - `cancel stops playback and drops pending speech` → `player.stop()` called; a speech result arriving after cancel is never played.
  - `a stale answer does not overwrite a newer listen` → tap, then `listenAgain()` before the answer returns: state stays `Listening`.
  - `listen again sends the heard text as text` (hub answers pending questions from text).
  - `recognizer errors map to SILENCE, PERMISSION, SPEECH` (as today).
- [ ] **Step 2: Run** `./gradlew :feature:voice:testDebugUnitTest --tests '*VoiceSessionTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** `VoiceSession` (one `Job` for the current request/audio, cancelled by every new action; a `generation` counter guards stale results), `AndroidAudioPlayer` (parses with `WavParser`, `AudioTrack` `MODE_STATIC`, `USAGE_ASSISTANT`, transient audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `suspendCancellableCoroutine` resumed by the playback head reaching the frame count via `setNotificationMarkerPosition`, released on stop/finish; `false` when the WAV does not parse), the reduced `VoiceViewModel` (no `EntitySource`, no readiness wait).
- [ ] **Step 4: Run** `./gradlew :feature:voice:testDebugUnitTest --tests '*VoiceSessionTest*'` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(voice): send what was heard to the hub's assistant and play its voice`

---

### Task 6: The sheet for hub turns

**Files:**
- Modify: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/ui/VoiceSheet.kt` (new signature below; auto-close from `closeRequested`)
- Create: `feature/voice/src/main/kotlin/com/gekkotron/nidoo/feature/voice/ui/TurnView.kt`
- Modify: `ListeningView.kt` (show "Envoi…" + greyed heard text for `Sending`)
- Delete: `ActionPickerView.kt`, `DevicePickerView.kt`, `ConfirmView.kt`, `NotUnderstoodView.kt`, `UnderstoodView.kt`, `SpokenView.kt` and their screenshot tests
- Modify: `VoiceActivity.kt` (new callbacks; `viewModel.start()` without language)
- Modify: `feature/voice/src/main/res/values/strings.xml` and `values-fr/strings.xml` (remove unused, add below)
- Test: `feature/voice/src/test/kotlin/com/gekkotron/nidoo/feature/voice/ui/VoiceSheetDispatchTest.kt`, `VoiceSheetScreenshotTest.kt`, `VoiceDisabledSheetTest.kt` (kept, adapted)

**Interfaces:**
- Consumes: Task 5 `VoiceSessionState`, `ErrorKind`, `VoiceViewModel` methods.
- Produces:
  ```kotlin
  @Composable fun VoiceSheet(
      state: StateFlow<VoiceSessionState>, closeRequested: StateFlow<Boolean>, canRepeat: StateFlow<Boolean>,
      onChoose: (Int) -> Unit, onConfirm: (Boolean) -> Unit, onListenAgain: () -> Unit, onRepeat: () -> Unit,
      onCancel: () -> Unit, onOpenSettings: () -> Unit = {})
  ```
  New strings (EN / FR): `voice_sending` "Sending…" / "Envoi…"; `voice_retry` "Try again" / "Réessayer"; `voice_answer_by_voice` "Answer" / "Répondre"; `voice_question_expired` "The question expired." / "La question a expiré."; `voice_no_hub` "The assistant needs the hub." / "L'assistant a besoin du hub."; `voice_hub_unreachable` "Hub unreachable." / "Hub injoignable."; `voice_gateway_refused` "The gateway doesn't allow the hub: add its address to the key's rules." / "La passerelle n'autorise pas le hub : ajoutez son adresse aux règles de la clé."; `voice_hub_failed` "The hub couldn't answer." / "Le hub n'a pas pu répondre.". Kept: `voice_yes`/`voice_no` (FR "Oui"/"Non"), `voice_repeat`, `voice_cancel`, `voice_listening`, `voice_disabled`, `voice_open_settings`, `voice_silence`, `voice_speech_error`, `voice_permission_denied`, `voice_mic_cd`.

- [ ] **Step 1: Write failing tests**
  - `VoiceSheetDispatchTest`: `a choice question shows one row per choice and taps send the index` (choices `["Chambre Léa","Chambre parentale"]`, tap second → `onChoose(1)`); `a confirm question shows Yes and No` (tap Yes → `onConfirm(true)`); `an expired question shows Try again and no choices` (tap → `onListenAgain()`); `not understood shows Try again`; `repeat shows only when canRepeat`; `each error kind shows its text and Try again except DISABLED which shows Open Settings`; `closeRequested closes unless the sheet was touched`.
  - `VoiceSheetScreenshotTest`: Robolectric captures for an acted turn, a choice question, a confirm question, not understood (Ivoire + Night Glass, existing helper).
- [ ] **Step 2: Run** `./gradlew :feature:voice:testDebugUnitTest` — Expected: FAIL.
- [ ] **Step 3: Implement** `TurnView`: reply text centred; question `CHOICE` → `VoiceRow` per choice; `CONFIRM` → Oui/Non rows; question present and not expired → a `Répondre` row (`onListenAgain`); expired → `voice_question_expired` + `Réessayer`; outcome `NOT_UNDERSTOOD`/`FAILED` → `Réessayer`; `canRepeat` → `voice_repeat`. Update `VoiceActivity` wiring.
- [ ] **Step 4: Run** `./gradlew :feature:voice:testDebugUnitTest` — Expected: PASS; review the new screenshots under the module's screenshot output.
- [ ] **Step 5: Commit** `feat(voice): show the hub's reply, room choices and Oui/Non in the sheet`

---

### Task 7: Remove shortcuts, phone TTS settings and `:core:voice`

**Files:**
- Modify: `core/source/src/jvmMain/kotlin/com/gekkotron/nidoo/source/EntitySource.kt` (drop `shortcuts`, `saveShortcut`, `deleteShortcut`)
- Modify: `core/source/src/jvmMain/kotlin/com/gekkotron/nidoo/source/MqttDiscoverySource.kt` (drop shortcut state/publish; topics under `nidoo/voice/shortcut/` return early, no report)
- Delete: `core/source/src/jvmMain/kotlin/com/gekkotron/nidoo/source/VoiceShortcutDocument.kt`, `core/source/src/jvmTest/…/VoiceShortcutDocumentTest.kt`
- Modify: `core/source/build.gradle.kts` (drop `api(project(":core:voice"))`)
- Modify: every `FakeEntitySource.kt` (settings, home, security, energy, alerts, history) and `app/src/test/…/HeadlessEntitiesTest.kt`, `WidgetMirrorTest.kt`
- Modify: `feature/settings/…/presentation/SettingsViewModel.kt`, `VoiceSettingsUi.kt` (keep `enabled`, `availability` only), `ui/SettingsPages.kt`, `ui/SettingsScreen.kt`, `ui/SettingsList.kt`, `ui/VoiceSection.kt` (drop `TtsLine`)
- Delete: `feature/settings/…/ui/VoiceShortcutsSection.kt`; settings strings `settings_voice_tts_*` and shortcut strings (EN + FR)
- Modify: `feature/settings/build.gradle.kts` (drop `:core:voice`)
- Delete: `core/voice/` entirely; `settings.gradle.kts` (drop `include(":core:voice")`)
- Test: `MqttDiscoverySourceTest.kt`, `SettingsVoiceTest.kt`, `SettingsViewModelTest.kt`, `SettingsListScreenTest.kt`, `SettingsListScreenshotTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `VoiceSettingsUi(val enabled: Boolean = false, val availability: SpeechAvailability.Status = SpeechAvailability.Status.Ready)`; `SettingsViewModel` constructor without `TtsSpeaker`/`ttsAvailability`.

- [ ] **Step 1: Write the failing test** in `MqttDiscoverySourceTest`: `retained voice shortcut topics are ignored silently` — deliver `nidoo/voice/shortcut/fr_abc` with a valid old payload and one with garbage → no entity added, `reports` empty. Update `SettingsVoiceTest` to assert the Voice page shows the toggle and recognizer line and no shortcut or TTS row.
- [ ] **Step 2: Run** `./gradlew :core:source:jvmTest --tests '*MqttDiscoverySourceTest*' :feature:settings:testDebugUnitTest` — Expected: FAIL (compile or assertion).
- [ ] **Step 3: Delete and adapt** per the file list; `rg -n 'com\.gekkotron\.nidoo\.voice\.|:core:voice|TtsSpeaker|VoiceShortcut|saveShortcut|deleteShortcut' --glob '!**/build/**' --glob '!docs/**'` returns nothing.
- [ ] **Step 4: Run** `./gradlew :core:source:jvmTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest` — Expected: PASS.
- [ ] **Step 5: Commit** `refactor(voice): drop phone-side rules, shortcuts and text-to-speech`

---

### Task 8: Whole-build verification and device checklist

**Files:**
- Modify: `docs/superpowers/specs/2026-11-04-hub-assist-voice-design.md` only if a verification result needs recording.

- [ ] **Step 1: Run** `./gradlew assembleDebug lint testDebugUnitTest jvmTest` — Expected: BUILD SUCCESSFUL, no new lint errors (compare with `master`).
- [ ] **Step 2: Run** `rg -n 'RuleMatcher|VoiceLexicon|Suggester|AnswerFormatter|TextToSpeech' --glob '!**/build/**' --glob '!docs/**'` — Expected: no matches.
- [ ] **Step 3: Write the device checklist into the PR body** (spec §7.8): at home on Wi-Fi and away on the gateway — « allume la lumière du salon »; « mets la chambre à 25 » → Oui; « éteins la chambre » → room buttons; « il fait combien dehors ? » → audio plays; a shutters sentence → hub's not-understood reply with Réessayer; voice off → widget off state still works.
- [ ] **Step 4: Commit** any spec note: `docs(voice): verification notes`.
