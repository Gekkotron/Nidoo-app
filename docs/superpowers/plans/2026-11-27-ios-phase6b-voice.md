# iOS Phase 6B — Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone gets the voice assistant. Settings → Voice turns it on (asking for the microphone and speech recognition), Home shows the mic, and a tap opens the same voice sheet as Android. The sheet listens in French, on the device, and sends the sentence to the hub's assistant. It shows the turn, with its choices or yes/no, and plays the hub's spoken reply. The Android app behaves as it does today.

**Architecture:** `:core:assist`'s HTTP client moves to common (Task 1). `:feature:voice` moves to Compose Multiplatform with Koin, the way 6A moved Automations: the session, ViewModel and sheet go to common, and Android keeps its recognizer, player and `VoiceActivity` in `androidMain` (Task 2). iOS gets its recognizer (`SFSpeechRecognizer`, on-device), its WAV player (`AVAudioPlayer`) and its permission prompts in `iosMain` (Task 3). Settings' Voice page becomes common, and the iOS shell wires the Voice row, Home's mic and the sheet as an overlay (Task 4).

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1 (CMP resources), Koin 4.2.2, Kotlin/Native `platform.Speech` and `platform.AVFAudio`.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§3, §4, §5 Info.plist, §7.5 voice, §7.6 phase 6, §9). Previous plan: `docs/superpowers/plans/2026-11-27-ios-phase6a-routines.md` (same pattern, Phase 6 PR table). Ledger: `.superpowers/sdd/2026-11-27-ios-phase6b-voice/progress.md`.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place" (§9): no DataStore, key or profile-format change. `voiceEnabled` keeps its name and default (`false`).
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- Info.plist (§5): `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription` "when voice lands".
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), positional placeholders (`%1$s`), a literal percent is a single `%`.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`. In commonTest use `kotlin.test.Test`.
- commonTest ViewModels are cleared (`ViewModelStore.clear()`) inside `runTest`, before `resetMain`.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing. Microphone and recognizer behaviour is checked on the user's iPhone only.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Local verification: `./gradlew check -x :app:lintDebug --continue` (`:app:lintDebug` has a pre-existing MissingPermission).
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Branch: `feat/ios-phase6b-voice` from `master`; the PR targets `master`.

## Rulings made while planning

- **No `AVSpeechSynthesizer`.** The spec (§7.5) names it, but the reply's voice is the hub's WAV (`AssistClient.speech`), which Android plays as-is and has no local fallback for. iOS plays the same WAV with `AVAudioPlayer(data:)`. When the hub has no voice, iOS stays silent like Android. This goes in the decision log.
- **On-device recognition only, French, as on Android.** iOS sets `requiresOnDeviceRecognition = true` on the request, and the locale is `"fr-FR"` on both hosts (`VOICE_LOCALE` in common). `SpeechAvailability.Status` moves to common unchanged. iOS reports `Ready` when `SFSpeechRecognizer(locale)` exists and `supportsOnDeviceRecognition` is true, otherwise `NoOnDevice`. `NeedsApi31` and `LanguagePackMissing` stay Android-only in practice.
- **iOS ends the sentence itself.** Android's recognizer ends on silence, but `SFSpeechRecognizer` doesn't. A common `SpeechEndpointer` ends the audio 1.5 s after the last partial (`SILENCE_END_MS = 1_500L`) and reports `NO_SPEECH` when nothing is heard within 6 s (`NO_SPEECH_MS = 6_000L`). An empty final text is also `NO_SPEECH` on iOS.
- **iOS audio session:** before listening, `AVAudioSessionCategoryPlayAndRecord` with options `DefaultToSpeaker | DuckOthers`, mode `AVAudioSessionModeDefault`, active. `DefaultToSpeaker` is required, or the reply plays through the earpiece. The session is deactivated with `NotifyOthersOnDeactivation` when the sheet closes.
- **The speech capture is made per session:** `typealias SpeechCaptureFactory = () -> SpeechCapture?`, where `null` means the phone can't listen, so the ViewModel shows `ErrorKind.SPEECH`. Android's factory returns null below API 31, or when `AndroidSpeechCapture` throws `IllegalStateException` (today's two checks, moved out of the ViewModel).
- **Permissions are one common seam:** `interface VoicePermissions { suspend fun request(): Boolean }` in `:feature:voice` common, true when everything needed is granted. iOS asks for the microphone (`AVAudioSession.requestRecordPermission`) and speech recognition (`SFSpeechRecognizer.requestAuthorization`), and returns true only if both are granted. Android keeps its `ActivityResult` launcher in `VoiceActivity` and `AndroidSettingsPage`, and binds no `VoicePermissions`.
- **Clock:** `VoiceSession`'s default clock becomes `{ Clock.System.now().toEpochMilliseconds() }` (`kotlin.time.Clock`). Tests keep passing the scheduler's time.
- **Koin:** a common `voiceModule` declares `viewModel { VoiceViewModel(get(), get(), get(), get()) }`. `VoiceViewModel(preferences: AppPreferences, client: AssistClient, player: AudioPlayer, speech: SpeechCaptureFactory)` drops its `Context` and Hilt. Android's `androidVoiceModule` binds `AudioPlayer` (single `AndroidAudioPlayer(androidContext())`) and `SpeechCaptureFactory`. `AssistClient` joins `KoinBridgeEntryPoint` (`fun assistClient(): AssistClient`, a factory, as for the other bridged types). `VoiceProvidesModule` (Hilt) is deleted. `VoiceActivity` drops `@AndroidEntryPoint` and gets its ViewModel with `by viewModel()` (koin-android).
- **The iOS sheet is an overlay, not a nav entry.** `VoiceSheet` draws over a transparent full screen, so it must sit above the current screen. `Screens` in `NidooApp` keeps `var voiceOpen by remember { mutableStateOf(false) }` and draws `VoiceRoute(onClose, onOpenSettings)` in a `Box` above the `Scaffold` while it is true. Its ViewModel is scoped to the overlay with the same `ScreenViewModels`-style store as before 4C (a `ViewModelStore` cleared on dispose), so every opening starts a new session. "Open Settings" closes the overlay and pushes `SettingsKey`, then `SettingsPageKey(SettingsPage.VOICE)`.
- **Settings' Voice page is common:** `VoiceSection` and `VoiceSettingsUi` move from settings `androidMain` to `commonMain` unchanged, so settings `commonMain` depends on `:feature:voice`. A new common `VoiceSettingsViewModel(preferences: AppPreferences, availability: SpeechAvailability.Status, permissions: VoicePermissions)` and `VoiceSettingsPage(snackbar: SnackbarHostState)` serve iOS. Turning the toggle on calls `permissions.request()` first, and a refusal keeps it off and shows `voice_permission_denied_snackbar`, as Android does. Android keeps `AndroidSettingsViewModel` and its own page.
- **iOS Settings rows:** a common `VoicePlatformSettings(preferences, availability)` lists one row, `PlatformRow(SettingsPage.VOICE, ON/OFF)`, ON only when enabled and `Ready` (Android's rule). iOS binds it instead of `NoPlatformSettings`, and `NidooApp` passes `platformPage = { page, snackbar -> if (page == SettingsPage.VOICE) VoiceSettingsPage(snackbar) }`.
- **Tests:** `VoiceSessionTest` and `Fakes` move to `commonTest` (so they also run on iOS). `AndroidAudioPlayerTest` and the `ui/` sheet tests move to `androidHostTest` with `R.string` → `Res.string` and nothing else.

## Review Focus

1. **A refused permission on iOS:** turning Voice on after a "Don't Allow" leaves the toggle off and shows "Microphone permission is needed for voice commands." The sheet opened with a revoked permission shows the PERMISSION error, not a silent hang. Task 4 `refusedPermissionKeepsVoiceOff`; Task 4 `deniedPermissionShowsPermissionError`.
2. **Silence:** opening the sheet and saying nothing ends in "I didn't hear anything" within ~6 s, never an endless "Listening…". Task 3 `nothingHeardEndsAsNoSpeech`.
3. **A sentence with a pause:** "allume… le salon" with a short pause is one sentence, and the end comes 1.5 s after the last word. Task 3 `eachPartialRestartsTheSilenceWindow`.
4. **The hub has no voice, or returns a broken WAV:** the turn stays on screen, there's no crash, and an ACTED turn closes 5 s after it arrived. Task 3 `garbageWavIsNotPlayable`; the existing `VoiceSessionTest` cases cover the closing.
5. **Voice off:** with the toggle off, iOS Home shows no mic, and Android's widget path still shows "The voice assistant is off". Task 4 `micHiddenWhileVoiceIsOff`; the existing `VoiceDisabledSheetTest`.

---

### Task 1: `:core:assist` HTTP client to common

**Files:**
- Modify: `core/assist/build.gradle.kts` (`api(project(":core:transport"))` moves from `jvmMain` to `commonMain`)
- Move: `core/assist/src/jvmMain/kotlin/com/gekkotron/nidoo/assist/HttpAssistClient.kt` → `src/commonMain/…` (unchanged)
- Move: `core/assist/src/jvmTest/kotlin/com/gekkotron/nidoo/assist/HttpAssistClientTest.kt` → `src/commonTest/…` (`org.junit.Test` → `kotlin.test.Test`; replace JVM-only calls such as `String.format` or `java.*`, if any)

**Interfaces:**
- Produces: `HttpAssistClient(baseUrl, clientId, post, get)` in commonMain, same signature.

- [ ] **Step 1:** Move both files and the dependency. Run `./gradlew :core:assist:jvmTest :core:assist:compileKotlinIosSimulatorArm64`. Expected: PASS, with the same test count as before the move.
- [ ] **Step 2:** Commit `refactor(assist): the HTTP client is common`.

### Task 2: `:feature:voice` on Compose Multiplatform with Koin

**Files:**
- Modify: `feature/voice/build.gradle.kts`: plugins `nidoo.kmp.library` + `nidoo.kmp.compose` (Hilt removed), `android { namespace = "com.gekkotron.nidoo.feature.voice" }`, and `compose.resources { packageOfResClass = "com.gekkotron.nidoo.feature.voice.resources" }`. commonMain gets `:core:model`, `:core:assist`, `:core:data`, `:core:designsystem`, cmp lifecycle viewmodel/runtime compose and Koin (core, compose-viewmodel), as in `feature/automations/build.gradle.kts`. androidMain gets `androidx.activity.compose` and `koin-android`. androidHostTest gets the automations list plus `turbine`.
- Move to `src/commonMain/kotlin/…/feature/voice/`: `SpeechCapture.kt` (interface + `SpeechError` only), `AudioPlayer.kt` (interface only), `SpeechAvailability.kt` (`Status` only, as `object SpeechAvailability { sealed interface Status … }`), `VoiceSession.kt`, `VoiceViewModel.kt`, `ui/ListeningView.kt`, `ui/TurnView.kt`, `ui/VoiceSheet.kt`
- Create: `src/commonMain/…/voice/VoiceSeams.kt`: `const val VOICE_LOCALE = "fr-FR"`, `typealias SpeechCaptureFactory = () -> SpeechCapture?`, `interface VoicePermissions { suspend fun request(): Boolean }`
- Create: `src/commonMain/…/voice/di/VoiceModule.kt`: `val voiceModule: Module`
- Create: `src/commonMain/…/voice/ui/VoiceRoute.kt`: `@Composable fun VoiceRoute(onClose: () -> Unit, onOpenSettings: () -> Unit, permissions: VoicePermissions, viewModel: VoiceViewModel = koinViewModel())`
- Move: `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`)
- Move to `src/androidMain/…`: `AndroidSpeechCapture` (out of `SpeechCapture.kt`), `AndroidAudioPlayer`, `SpeechAvailability.check(context)` (as `fun androidSpeechAvailability(context: Context): SpeechAvailability.Status`), `VoiceActivity.kt`, `AndroidManifest.xml`, `res/values/themes.xml`
- Create: `src/androidMain/…/voice/di/AndroidVoiceModule.kt`: `val androidVoiceModule: Module`
- Delete: `src/main/kotlin/…/voice/di/VoiceProvidesModule.kt`
- Move tests: `VoiceSessionTest.kt` and `Fakes.kt` → `src/commonTest/…`; `AndroidAudioPlayerTest.kt`, `VoiceEnabledGateTest.kt` and `ui/*` → `src/androidHostTest/…`
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt` (`assistClient()` in the entry point and the bridge module; `voiceModule`, `androidVoiceModule` in `startNidooKoin`)
- Modify: `feature/settings/src/androidMain/…/di/AndroidSettingsModule.kt` (`SpeechAvailability.check(androidContext())` → `androidSpeechAvailability(androidContext())`)
- Modify: `.github/workflows/ci.yml` (`:feature:voice:test` → `:feature:voice:testAndroidHostTest`; add `:feature:voice:iosSimulatorArm64Test` after `:feature:automations:iosSimulatorArm64Test`)

**Interfaces:**
- Consumes: `HttpAssistClient` / `AssistClient` (Task 1), `AppPreferences.voiceEnabled: StateFlow<Boolean?>`.
- Produces:
  - `class VoiceViewModel(val preferences: AppPreferences, client: AssistClient, player: AudioPlayer, speech: SpeechCaptureFactory) : ViewModel`. Same public members as today (`state`, `closeRequested`, `canRepeat`, `started`, `start()`, `showDisabled()`, `choose`, `confirm`, `listenAgain`, `repeat`, `cancel`). `start()` shows `Error(SPEECH)` when `speech()` returns null.
  - `suspend fun awaitVoiceEnabled(flow: StateFlow<Boolean?>): Boolean` (unchanged, common).
  - `VoiceRoute`: waits for `awaitVoiceEnabled`. Off means `showDisabled()`. On means `if (permissions.request()) start() else Error(PERMISSION)`; it then renders `VoiceSheet(…, onCancel = onClose, onOpenSettings = onOpenSettings)` and cancels the session on dispose. To set `Error(PERMISSION)`, add `fun showPermissionDenied()` to `VoiceViewModel`, next to `showDisabled()`.
  - `voiceModule`: `viewModel { VoiceViewModel(get(), get(), get(), get()) }`.
  - `androidVoiceModule`: `single<AudioPlayer> { AndroidAudioPlayer(androidContext()) }`, `single<SpeechCaptureFactory> { { if (SDK_INT < S) null else try { AndroidSpeechCapture(androidContext(), VOICE_LOCALE) } catch (e: IllegalStateException) { null } } }`.

- [ ] **Step 1: Write the failing test.** In `src/commonTest/…/VoiceViewModelTest.kt`, add `noRecognizerShowsSpeechError`: build `VoiceViewModel(prefs, FakeAssistClient(), FakePlayer(), speech = { null })` with a real `AppPreferences` over a temp file (as in the settings commonTests), call `start()`, assert `state.value == VoiceSessionState.Error(ErrorKind.SPEECH)` and `started == false`, and clear the store inside `runTest`. Add `startUsesTheFactoryOnce`: a factory that counts its calls and returns `FakeSpeechCapture()`. After `start(); start()`, assert one call and `FakeSpeechCapture` started once.
- [ ] **Step 2:** Run `./gradlew :feature:voice:testAndroidHostTest`. Expected: FAIL (compilation: the module is not KMP yet).
- [ ] **Step 3:** Do the moves, the build file, the seams, the Koin modules, `VoiceRoute` and the bridge. `VoiceSheet` and its views swap `androidx.compose.ui.res.stringResource`/`R.string` for `org.jetbrains.compose.resources.stringResource`/`Res.string`, and `errorRes` returns `StringResource`. `VoiceActivity` keeps its permission launcher, theme and edge-to-edge code, and only swaps Hilt for Koin.
- [ ] **Step 4:** Run `./gradlew :feature:voice:testAndroidHostTest :feature:voice:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest :feature:settings:testAndroidHostTest`. Expected: PASS. `VoiceSessionTest`, the sheet tests and the screenshot test pass unchanged apart from imports.
- [ ] **Step 5:** Run `./gradlew :app:assembleDebug` and check that `VoiceActivity` is still in the merged manifest (`grep VoiceActivity app/build/intermediates/merged_manifests/debug/*/AndroidManifest.xml` or the equivalent AGP 9 path). Expected: one match.
- [ ] **Step 6:** Commit `feat(voice): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 3: iOS recognizer, player and permissions

**Files:**
- Create: `feature/voice/src/commonMain/…/voice/SpeechEndpointer.kt`
- Create: `feature/voice/src/iosMain/kotlin/com/gekkotron/nidoo/feature/voice/IosSpeechCapture.kt`
- Create: `feature/voice/src/iosMain/…/voice/IosAudioPlayer.kt`
- Create: `feature/voice/src/iosMain/…/voice/IosVoicePermissions.kt`
- Create: `feature/voice/src/iosMain/…/voice/IosSpeechAvailability.kt`
- Test: `feature/voice/src/commonTest/…/voice/SpeechEndpointerTest.kt`, `feature/voice/src/iosTest/…/voice/IosSpeechMappingTest.kt`, `feature/voice/src/iosTest/…/voice/IosAudioPlayerTest.kt`

**Interfaces:**
- Consumes: `SpeechCapture`, `SpeechError`, `AudioPlayer`, `VoicePermissions`, `SpeechAvailability.Status`, `VOICE_LOCALE` (Task 2).
- Produces:
  - `const val SILENCE_END_MS = 1_500L`, `const val NO_SPEECH_MS = 6_000L`
  - `class SpeechEndpointer(scope: CoroutineScope, onSilence: () -> Unit, onNothingHeard: () -> Unit) { fun start(); fun heard(); fun stop() }`. `start()` arms the 6 s no-speech timer. Each `heard()` cancels it and restarts the 1.5 s silence timer. Each callback fires at most once, and none after `stop()`.
  - `class IosSpeechCapture(localeTag: String = VOICE_LOCALE) : SpeechCapture`
  - `internal fun speechErrorFor(domain: String, code: Long): SpeechError`
  - `class IosAudioPlayer : AudioPlayer`
  - `object IosVoicePermissions : VoicePermissions`
  - `fun iosSpeechAvailability(localeTag: String = VOICE_LOCALE): SpeechAvailability.Status`
  - `internal fun availabilityOf(recognizerExists: Boolean, supportsOnDevice: Boolean): SpeechAvailability.Status`

- [ ] **Step 1: Write the failing tests.**
  - `SpeechEndpointerTest` (commonTest, `runTest`, endpointer on `backgroundScope`):
    - `nothingHeardEndsAsNoSpeech`: `start()`, `advanceTimeBy(NO_SPEECH_MS + 1)` → `onNothingHeard` called once, `onSilence` never.
    - `eachPartialRestartsTheSilenceWindow`: `start()`, `heard()`, advance 1_000, `heard()`, advance 1_000 → `onSilence` not called. Advance 600 → called once. `onNothingHeard` is never called.
    - `stopSilencesEverything`: `start()`, `heard()`, `stop()`, advance 10_000 → neither is called.
  - `IosSpeechMappingTest` (iosTest):
    - `speechErrorFor("kAFAssistantErrorDomain", 1110) == NO_SPEECH`, and `(…, 203) == NO_SPEECH`.
    - `speechErrorFor("kLSRErrorDomain", 102) == LANGUAGE_UNAVAILABLE`.
    - `speechErrorFor("kAFAssistantErrorDomain", 1700) == SERVICE_UNAVAILABLE`.
    - `speechErrorFor("NSCocoaErrorDomain", 1) == UNKNOWN`.
    - `availabilityOf(true, true) == Ready`, `availabilityOf(true, false) == NoOnDevice`, `availabilityOf(false, false) == NoOnDevice`.
  - `IosAudioPlayerTest` (iosTest): `garbageWavIsNotPlayable`: `IosAudioPlayer().play(byteArrayOf(1, 2, 3))` returns false within `runTest`.
- [ ] **Step 2:** Run `./gradlew :feature:voice:testAndroidHostTest :feature:voice:compileTestKotlinIosSimulatorArm64`. Expected: FAIL (unresolved `SpeechEndpointer`, `speechErrorFor` and the others).
- [ ] **Step 3: Implement.**
  - `SpeechEndpointer`: two `Job`s on `scope` with `delay`.
  - `IosSpeechCapture.start`:
    - Call `cancel()`, then set up the audio session (Rulings).
    - Create `SFSpeechAudioBufferRecognitionRequest` with `shouldReportPartialResults = true` and `requiresOnDeviceRecognition = true`. Tap `AVAudioEngine.inputNode` bus 0 in its `outputFormatForBus(0u)` and append each buffer to the request. Start the engine.
    - Create `recognitionTaskWithRequest`. Each partial calls `callbacks.onPartial` and `endpointer.heard()`.
    - `onSilence`: `request.endAudio()`. The final result calls `onFinal(text)`, or `onError(NO_SPEECH)` when the text is blank. `onNothingHeard` → `onError(NO_SPEECH)`.
    - An `NSError` → `onError(speechErrorFor(error.domain, error.code))`.
    - A nil recognizer or a throwing engine start → `onError(SERVICE_UNAVAILABLE)`.
    - Deliver every callback on the main queue, at most one final or error per `start`.
    - `cancel()` stops the endpointer, cancels the task, removes the tap, stops the engine, and leaves the session active.
    - The endpointer's scope is `CoroutineScope(Dispatchers.Main)`, cancelled in `cancel()`.
  - `IosAudioPlayer.play`: `AVAudioPlayer(data = wav.toNSData(), error = …)`. A nil player or `prepareToPlay() == false` → false. Otherwise `play()` and suspend until a delegate (`NSObject(), AVAudioPlayerDelegateProtocol`, kept in a field) reports `audioPlayerDidFinishPlaying`, or `duration * 1000 + 2_000` ms pass (Android's `END_MARKER_GRACE_MS`). Then stop and return true. `stop()` resumes the waiting `play`.
  - `IosVoicePermissions.request`: `suspendCancellableCoroutine` around `AVAudioSession.sharedInstance().requestRecordPermission`, then around `SFSpeechRecognizer.requestAuthorization` (granted when the status is `SFSpeechRecognizerAuthorizationStatusAuthorized`). True only if both are granted. Already-decided permissions answer immediately without a prompt.
  - `iosSpeechAvailability`: `availabilityOf(recognizer != null, recognizer?.supportsOnDeviceRecognition == true)` with `SFSpeechRecognizer(locale = NSLocale(localeTag))`.
  - Add `fun deactivateVoiceAudio()` in `IosSpeechCapture.kt`: `setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation)`, errors ignored. Task 4 calls it when the overlay closes.
- [ ] **Step 4:** Run `./gradlew :feature:voice:testAndroidHostTest :feature:voice:compileTestKotlinIosSimulatorArm64`. Expected: PASS. If the machine allows it, also run `./gradlew :feature:voice:iosSimulatorArm64Test -Pnidoo.iosTests`, which runs headless and boots no Simulator UI. Otherwise CI runs it.
- [ ] **Step 5:** Commit `feat(voice): iOS on-device recognizer, WAV player and permissions`.

### Task 4: Voice on the iPhone (Settings, Home's mic, the sheet)

**Files:**
- Move: `feature/settings/src/androidMain/…/ui/VoiceSection.kt` and `…/presentation/VoiceSettingsUi.kt` → `src/commonMain/…` (unchanged)
- Modify: `feature/settings/build.gradle.kts` (`implementation(project(":feature:voice"))` from `androidMain` to `commonMain`)
- Create: `feature/settings/src/commonMain/…/presentation/VoiceSettingsViewModel.kt`
- Create: `feature/settings/src/commonMain/…/presentation/VoicePlatformSettings.kt`
- Create: `feature/settings/src/commonMain/…/ui/VoiceSettingsPage.kt`
- Modify: `feature/settings/src/commonMain/…/di/SettingsModule.kt` (`viewModel { VoiceSettingsViewModel(get(), get(), get()) }`)
- Modify: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/NidooApp.kt`
- Modify: `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/di/IosModule.kt`
- Modify: `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt` (`voiceModule` in the module list)
- Modify: `shared/build.gradle.kts` (`:feature:voice` in commonMain)
- Modify: `iosApp/project.yml` (two Info.plist keys)
- Modify: `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (decision log)
- Test: `feature/settings/src/commonTest/…/presentation/VoiceSettingsViewModelTest.kt`, `feature/settings/src/commonTest/…/presentation/VoicePlatformSettingsTest.kt`, `shared/src/androidHostTest/…/NidooAppTest.kt`

**Interfaces:**
- Consumes: Tasks 2 and 3 (`VoiceRoute`, `VoicePermissions`, `IosVoicePermissions`, `IosSpeechCapture`, `IosAudioPlayer`, `iosSpeechAvailability`, `deactivateVoiceAudio`, `voiceModule`), `HttpAssistClient` (Task 1).
- Produces:
  - `class VoiceSettingsViewModel(preferences: AppPreferences, availability: SpeechAvailability.Status, permissions: VoicePermissions) : ViewModel` with `val voice: StateFlow<VoiceSettingsUi>`, `val denied: StateFlow<Boolean>`, `fun setVoiceEnabled(on: Boolean)`, and `fun deniedShown()`. `setVoiceEnabled(true)` first awaits `permissions.request()`; false means it saves `false` and sets `denied = true`. Turning it off asks nothing.
  - `class VoicePlatformSettings(preferences: AppPreferences, availability: SpeechAvailability.Status) : PlatformSettings` (one VOICE row; `refresh()` does nothing).
  - `@Composable fun VoiceSettingsPage(snackbar: SnackbarHostState, viewModel: VoiceSettingsViewModel = koinViewModel())`: `VoiceSection(voice, viewModel::setVoiceEnabled, showTitle = false)`, plus the denied snackbar (`Res.string.voice_permission_denied_snackbar`).

- [ ] **Step 1: Write the failing tests.**
  - `VoiceSettingsViewModelTest` (commonTest, a fake `VoicePermissions` returning a set value and counting calls):
    - `grantedPermissionTurnsVoiceOn`: request → true. `setVoiceEnabled(true)` → `preferences.voiceEnabled` becomes `true`, `denied == false`.
    - `refusedPermissionKeepsVoiceOff`: request → false. `setVoiceEnabled(true)` → `voiceEnabled` stays `false`, `denied == true`. After `deniedShown()`, `denied == false`.
    - `turningOffAsksNothing`: `setVoiceEnabled(false)` → 0 requests, `voiceEnabled == false`.
    - `notReadyShowsTheToggleOff`: availability `NoOnDevice`, preference `true` → `voice.value == VoiceSettingsUi(enabled = true, availability = NoOnDevice)`.
  - `VoicePlatformSettingsTest` (commonTest): `voiceRowFollowsThePreference`: `currentRows() == listOf(PlatformRow(VOICE, OFF))`. After `setVoiceEnabled(true)` the `rows` flow emits `listOf(PlatformRow(VOICE, ON))`. With availability `NoOnDevice`, it stays `OFF`.
  - `NidooAppTest` (androidHostTest, Koin test graph as today, `VoicePlatformSettings` bound): `micHiddenWhileVoiceIsOff`: with `voiceEnabled = false`, Home has no node with content description `Res.string.voice_mic_cd` (or the Home mic's existing test tag). `micOpensTheVoiceSheet`: with `voiceEnabled = true` and a fake `VoicePermissions` that returns true, clicking the mic shows "Listening…". `deniedPermissionShowsPermissionError`: the fake returns false, so clicking the mic shows "Microphone permission needed".
- [ ] **Step 2:** Run `./gradlew :feature:settings:testAndroidHostTest :shared:testAndroidHostTest`. Expected: FAIL (unresolved `VoiceSettingsViewModel`, `VoicePlatformSettings`, and no mic on Home).
- [ ] **Step 3: Implement.**
  - Settings: the moves, the ViewModel, the row source and the page.
  - `NidooApp`'s `Screens`:
    - `voiceOpen` state.
    - `HomeRoute(…, onOpenVoice = { voiceOpen = true })`.
    - Wrap the `Scaffold` in a `Box` and draw `VoiceOverlay` on top when `voiceOpen`. `VoiceOverlay` is a private composable that owns a `ViewModelStore` (`remember { ViewModelStore() }`, cleared in `DisposableEffect`) and provides it through `LocalViewModelStoreOwner`. It calls `VoiceRoute(onClose = close, onOpenSettings = { close(); push(backStack, SettingsKey); push(backStack, SettingsPageKey(SettingsPage.VOICE)) }, permissions = koinInject())`, where `close` sets `voiceOpen = false` and calls the platform close hook.
    - `SettingsPageRoute(…, platformPage = { page, snackbar -> if (page == SettingsPage.VOICE) VoiceSettingsPage(snackbar) })`.
  - Platform close hook: `expect fun onVoiceClosed()` in `:shared` commonMain. Android's actual does nothing, and iOS's calls `deactivateVoiceAudio()`.
  - `iosModule`:
    - `single<AssistClient> { HttpAssistClient(baseUrl = <same lambda as HttpFlowsClient>, clientId = get<AppPreferences>()::assistClientId, post = { url, json -> lanOrGateway.post(url, json, resendViaGateway = false) }, get = { url, max -> lanOrGateway.get(url, max) }) }`
    - `single<AudioPlayer> { IosAudioPlayer() }`
    - `single<SpeechCaptureFactory> { { IosSpeechCapture() } }`
    - `single<VoicePermissions> { IosVoicePermissions }`
    - `factory<SpeechAvailability.Status> { iosSpeechAvailability() }`
    - `single<PlatformSettings> { VoicePlatformSettings(get(), get()) }`, which replaces `NoPlatformSettings`. Update the comment: iOS lists Voice only, with no push and no presence.
  - `iosApp/project.yml` Info.plist:
    - `NSMicrophoneUsageDescription: Nidoo listens to your voice commands for your home hub.`
    - `NSSpeechRecognitionUsageDescription: Nidoo turns your voice commands into text on this iPhone.`
  - Decision log: a `**2026-11-27, Phase 6B (Voice).**` bullet after 6A's. It summarises the Rulings: hub WAV instead of `AVSpeechSynthesizer`, on-device French only, the common endpointer, the overlay, the common Voice page with `VoicePermissions`, and iOS listing the Voice row only.
- [ ] **Step 4:** Run `./gradlew :feature:settings:testAndroidHostTest :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64`, then `./gradlew check -x :app:lintDebug --continue`. Expected: PASS. Then run `xcodegen generate --spec iosApp/project.yml && xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`. Expected: `BUILD SUCCEEDED`.
- [ ] **Step 5:** Commit `feat(ios): voice commands; the Voice settings page on both hosts`, then commit the decision log as `docs: iOS phase 6B decision log`.

## iPhone checklist (for the PR body)

- Settings → Voice → toggle on: both prompts appear once. A refusal shows the snackbar and the toggle stays off.
- Home shows the mic only when Voice is on. "Allume le salon" acts, and the reply is spoken through the speaker, not the earpiece, then the sheet closes.
- A choice question: tap a choice, or "Answer" and speak it. Yes/no works. After 60 s the question shows as expired.
- Silence → "I didn't hear anything" within ~6 s. Background music ducks while listening and comes back after.
- Away from home (gateway): a command still reaches the hub.
- Tap outside while "Listening…" with music playing: the music returns to full volume.
- Before any prompt, the Voice toggle is enabled; after granting, on an iPhone without on-device French it turns off and says why.
