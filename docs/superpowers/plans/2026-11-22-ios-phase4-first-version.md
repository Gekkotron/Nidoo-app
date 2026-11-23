# iOS Phase 4 — First iOS version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nidoo is usable every day on the iPhone, at home and away. It covers setup by QR code (with manual setup as a fallback), Home and dashboards with the control and customize sheets, and the Settings connection pages. The Android app keeps behaving exactly as it does today.

**Architecture:** Phase 4 ships as three PRs, one per module group (spec §9: one PR per module, consumers rewired in the same PR):

| PR | Branch | Moves to common | iOS result |
|---|---|---|---|
| **4A** (this file, detailed) | `feat/ios-phase4a-onboarding` | app's connection wiring into a new `:core:connection`, and `:feature:onboarding` | Set up by QR or by hand. The iPhone then shows the live link (direct at home, gateway away) and the device count, and has a "Set up again" button. |
| **4B** (`2026-11-2x-ios-phase4b-home.md`, written after 4A) | `feat/ios-phase4b-home` | `:feature:home` (with a new `:core:format` for locale numbers, dates and accents; `EnergyStats`/`EnergyFormat` leave `:feature:energy`) | Rooms step, Home, dashboards, control and customize sheets, history availability. |
| **4C** (`2026-11-2x-ios-phase4c-settings.md`, written after 4B) | `feat/ios-phase4c-settings` | `:feature:settings` (the pages iOS has; Presence, Notifications and Voice stay Android-only), and the common shell on Navigation 3 | Settings with the connection pages, room order, theme; swipe-back; the iPhone checklist. |

4A is the plan below. Plans 4B and 4C are written when the previous PR is open, against the code as it is then.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1, JetBrains lifecycle 2.10.0 (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`), `org.jetbrains.compose.ui:ui-backhandler:1.11.1`, Koin 4.2.2, AVFoundation (iOS QR), CocoaMQTT 2.4.2 (Swift, unchanged).

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§5, §7.2, §7.5, §7.6 phase 4, §9).

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place": no change to the DataStore files, Keystore secrets or entity cache formats (§9).
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- "No weaker security on iOS: gateway secrets go in the iOS Keychain … no secret is logged." (§4)
- `NSCameraUsageDescription` for QR scanning (§5).
- "The app is suspended in the background: MQTT disconnects and must reconnect on foreground" (§5).
- CMP strings: write a plain `'` in `composeResources` XML, never `\'`. Feature strings move to CMP resources with their module.
- Keep the Android app on OkHttp 4.12 and HiveMQ. Ktor (Darwin) and CocoaMQTT are for iOS only.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator without the user's yes.
- KMP Android tests: `testAndroidHostTest` (the `--tests` filter works there); Android-only modules: `testDebugUnitTest`.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- **`:core:connection` keeps the package `com.gekkotron.nidoo.connection`**, so the Android-only code that stays in `app` (push, widgets, presence, headless entities) changes no imports. Only the common wiring moves: `EntitySourceSession`, `SharedSubscriptions`, `TransportSpecs`, `CacheKey`, `TransportFactory`, `DirectConnectionTester` and the `ConnectionTester` contract (from `:feature:onboarding`). `NetworkChanges` (ConnectivityManager), `MediaRoutes`, `HistoryWiring`, `ProfileHistoryClient` and the push/widget files stay in `app` for now. 4B moves the history client.
- **Platform transports are injected into the common factory as lambdas**: Android passes HiveMQ `DirectMqttTransport`, OkHttp `AthenaGatewayApi` and `TcpProbe`; iOS passes `PortMqttTransport`, the Darwin `AthenaGatewayApi` and `NetworkTcpProbe`. A new common `DirectLink` interface (a `ManagedTransport` with a `failure` flow) is what both direct transports already offer.
- **Android keeps Hilt as the owner of the transport stack.** Koin gets `ConnectionTester` through the existing `KoinBridgeEntryPoint`, the same as `ProfileRepository` in Phase 3. iOS builds the whole graph in an iOS Koin module in `:shared`.
- **No push on iOS, so no notification step:** the iOS shell finishes a pending notification step by itself. `NotificationsRoute` stays in `androidMain`.
- **`SecureWindow` is a no-op on iOS.** iOS has no public `FLAG_SECURE`; the setup code is only on screen for the scan.
- **The iOS QR scanner is Kotlin on AVFoundation** (`AVCaptureMetadataOutput` in a `UIKitView`), not Swift, because nothing in it needs Swift. It feeds the same `onQrScanned(text)`.
- **4A's iOS "house" screen is temporary.** It shows the link pill, the device count and a "Set up again" button, so the phone checks the transports end to end. 4B replaces it with Home.
- From the Phase 3 review, still deferred: the widget alarm-label literal pin, koin-android's appcompat/fragment weight, and the `LocalNidooType` default. 4B fixes "iOS Reduce Motion is read only once", when Home's animations land on iOS. The Phase 4 note "assist client id prefix is still `android-`" belongs with voice (Phase 6).
- Moved tests go to `commonTest` when they only use common APIs and fakes (`core:transport-testing` commonMain). Tests that need the embedded broker, Robolectric or OkHttp go to `androidHostTest`.

## Review Focus

1. **The phone goes to the background mid-test or mid-session.** The onboarding test connection must close on background and restart on foreground (iOS lifecycle ON_STOP/ON_START from `ComposeUIViewController`). The main link must pause after the 30 s grace and reconnect on return (`iosForeground()` → `ActiveTransport.setForeground`). Task 5 test `foregroundDrivesTheTransport`.
2. **The camera permission is refused, then granted in iOS Settings.** On return the scanner must start without restarting the app: re-check on ON_RESUME, as on Android. Task 4 iOS actual, checked by reading; logic test `deniedThenGrantedShowsTheCamera` on the common `CameraPermission` reducer.
3. **The same QR code is decoded many times a second**, and a rejected code too: one connection test only, and one error (existing `onQrScanned` dedupe; the AVFoundation delegate must not bypass it). Task 4 keeps `OnboardingViewModelTest`'s repeat tests green.
4. **A profile restored from an iCloud backup onto another iPhone** would carry secrets the new Keychain can't open (the Keychain item is ThisDeviceOnly). `profile.json` is excluded from backups. Task 5 test `profileFileIsExcludedFromBackup` (iosTest).
5. **A gateway publish that never gets an answer on iOS** must fail after 10 s instead of hanging the control sheet. Task 1 test `publishGivesUpAfterTheTimeout`.

---

### Task 1: `DirectLink` and the iOS gateway publish timeout

**Files:**
- Create: `core/transport/src/commonMain/kotlin/com/gekkotron/nidoo/transport/DirectLink.kt`
- Modify: `core/transport/src/commonMain/kotlin/com/gekkotron/nidoo/transport/PortMqttTransport.kt`, `core/transport/src/jvmMain/kotlin/com/gekkotron/nidoo/transport/DirectMqttTransport.kt`, `core/transport/src/commonMain/kotlin/com/gekkotron/nidoo/transport/AthenaGatewayApi.kt`, `core/transport/src/iosMain/kotlin/com/gekkotron/nidoo/transport/IosHttpClients.kt`
- Modify: `iosApp/Nidoo/Mqtt/CocoaMqttClientPort.swift` (connect timeout)
- Test: `core/transport/src/commonTest/kotlin/com/gekkotron/nidoo/transport/GatewayApiTest.kt`

**Interfaces:**
- Produces: `interface DirectLink : ManagedTransport { val failure: StateFlow<DirectFailure?> }`, implemented by `PortMqttTransport` and `DirectMqttTransport` (their existing `failure` becomes `override`).
- Produces: `AthenaGatewayApi internal constructor(client: AthenaGatewayClient, broker: Broker?, publishTimeout: Duration? = null)`. The iOS factory `AthenaGatewayApi(spec)` passes `10.seconds`; the JVM factory keeps its OkHttp call timeout and passes nothing.

- [ ] **Step 1: Write the failing test** in `GatewayApiTest.kt`, building the API the way the file already does (SDK client on a Ktor `MockEngine`):

```kotlin
@Test fun publishGivesUpAfterTheTimeout() = runTest {
    // the engine never answers: MockEngine { awaitCancellation() }
    val api = AthenaGatewayApi(client(neverAnswers), broker = null, publishTimeout = 200.milliseconds)
    val e = assertFailsWith<PublishFailed> { withContext(Dispatchers.Default) { api.publish("nidoo/x/set", "on".encodeToByteArray(), 1, false) } }
    assertEquals("publish timed out", e.message)
}
```
- [ ] **Step 2:** Run `./gradlew :core:transport:jvmTest --tests '*GatewayApiTest*'`. Expected: FAIL (no `publishTimeout` parameter).
- [ ] **Step 3:** Add `DirectLink`. Wrap `publish` in `withTimeout(publishTimeout)` when it is set, mapping `TimeoutCancellationException` to `PublishFailed("publish timed out")` (the same text as `PortMqttTransport`). Pass `10.seconds` from the iOS factory. In `CocoaMqttClientPort.swift`, give CocoaMQTT a 10 s connect timeout (`mqtt.connect(timeout: 10)`).
- [ ] **Step 4:** Run `./gradlew :core:transport:jvmTest :core:transport:compileKotlinIosSimulatorArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(transport): DirectLink, and iOS gateway publishes time out after 10 s`.

### Task 2: `:core:connection`, the common connection wiring

**Files:**
- Create: `core/connection/build.gradle.kts` (`nidoo.kmp.library`; `kotlin.android { namespace = "com.gekkotron.nidoo.connection" }`; commonMain `api(:core:transport)`, `api(:core:data)`, `api(:core:source)`, `implementation(:core:flows)`, `implementation(:core:dashboards)`, `implementation(:core:climate)`; commonTest `:core:transport-testing`, coroutines-test; androidHostTest `:core:transport-testing`, junit4).
- Modify: `settings.gradle.kts` (`include(":core:connection")`), `app/build.gradle.kts` (`implementation(project(":core:connection"))`).
- Move (package unchanged, `com.gekkotron.nidoo.connection`) from `app/src/main/kotlin/com/gekkotron/nidoo/connection/` to `core/connection/src/commonMain/kotlin/com/gekkotron/nidoo/connection/`: `EntitySourceSession.kt`, `SharedSubscriptions.kt`, `TransportSpecs.kt`, `CacheKey.kt`, `TransportFactory.kt`, `DirectConnectionTester.kt`.
- Move: `feature/onboarding/src/main/kotlin/com/gekkotron/nidoo/feature/onboarding/ConnectionTester.kt` → `core/connection/src/commonMain/kotlin/com/gekkotron/nidoo/connection/ConnectionTester.kt` (package becomes `com.gekkotron.nidoo.connection`). `:feature:onboarding` gets `implementation(project(":core:connection"))`, and its imports follow.
- Create: `core/connection/src/commonMain/kotlin/com/gekkotron/nidoo/connection/ProfileSpecs.kt`.
- Move tests: `app/src/test/.../connection/{EntitySourceSessionTest,SharedSubscriptionsTest,TransportSpecsTest}.kt` → `core/connection/src/commonTest/...` if they compile there, otherwise `androidHostTest`. `{DirectConnectionTesterTest,TransportFactoryTest}.kt` → `core/connection/src/androidHostTest/...` (embedded broker, HiveMQ, OkHttp).
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/di/AppModule.kt`.
- Modify: `.github/workflows/ci.yml`. Add `:core:connection:testAndroidHostTest` and `:core:connection:koverXmlReport` to the core group, its kover path to the codecov list, and `:core:connection:iosSimulatorArm64Test` to the iOS job.

**Interfaces:**
- Consumes: `DirectLink` (Task 1).
- Produces:
  - `class TransportFactory(scope: CoroutineScope, networkChanges: Flow<Unit>, direct: (TransportSpec.Direct, CoroutineScope) -> DirectLink, gatewayApi: (TransportSpec.Gateway) -> GatewayApi, probe: (host: String, port: Int) -> LocalProbe) { fun create(spec: TransportSpec): ManagedTransport }`, with the same `when` as today.
  - `class DirectConnectionTester(clientId: suspend () -> String, direct: (TransportSpec.Direct, CoroutineScope) -> DirectLink, gatewayApi: (TransportSpec.Gateway) -> GatewayApi, connectTimeout: Duration = 5.seconds, gatewayTimeout: Duration = 10.seconds, dispatcher: CoroutineDispatcher = Dispatchers.Default) : ConnectionTester`. `testDirect` builds its transport with `direct(spec, this)` instead of `DirectMqttTransport`.
  - `fun profileSpecs(profiles: ProfileRepository): Flow<TransportSpec?>`: today's `flow { val id = profiles.installId(); emitAll(combine(profiles.state, profiles.route) { s, r -> s.toSpec(clientIdFor(id), r) }) }`, lifted out of `AppModule.activeTransport`.
  - `ConnectionTester`, `TestState`, `TestFailure` in `com.gekkotron.nidoo.connection`, unchanged otherwise.
  - Synchronization: `EntitySourceSession` uses `@Synchronized`, which is JVM-only. Replace it with a `kotlinx.atomicfu.locks.SynchronizedObject` + `synchronized(lock)` (atomicfu is already on the classpath through coroutines; add `org.jetbrains.kotlinx:atomicfu` to the catalog if the build asks).

- [ ] **Step 1: Write the failing test** `core/connection/src/commonTest/.../ProfileSpecsTest.kt`, with a `ProfileRepository` on a fake `SecretStore` and an in-memory Okio path. Use the helpers `:core:data`'s commonTest already uses; if those are test-only, use `FakeFileSystem` from `okio-fakefilesystem`.

```kotlin
@Test fun noProfileHasNoSpec()             // first non-loading emission of profileSpecs(repo) == null
@Test fun demoProfileIsTheDemo()           // repo.save(ConnectionProfile.Demo) → TransportSpec.Demo
@Test fun gatewayProfileIsRouted()         // a Direct profile with a gateway → TransportSpec.Routed whose direct.clientId starts with "nidoo-"
```
- [ ] **Step 2:** Run `./gradlew :core:connection:testAndroidHostTest`. Expected: FAIL (module or `profileSpecs` missing).
- [ ] **Step 3:** Create the module and move the files and tests listed above. Apply the constructor changes, add `ProfileSpecs.kt`, and replace `@Synchronized`. In `AppModule`, the factory becomes `TransportFactory(scope, context.networkChanges(), direct = ::DirectMqttTransport, gatewayApi = { AthenaGatewayApi(it, http) }, probe = ::TcpProbe)`, the specs come from `profileSpecs(profiles)`, and the tester becomes `DirectConnectionTester(clientId = …, direct = ::DirectMqttTransport, gatewayApi = { AthenaGatewayApi(it, http) })`. Moved tests that built `DirectConnectionTester`/`TransportFactory` with the old parameters pass the JVM lambdas the same way.
- [ ] **Step 4:** Run `./gradlew :core:connection:testAndroidHostTest :core:connection:compileKotlinIosSimulatorArm64 :feature:onboarding:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS. Every moved test passes unchanged except for its construction lines.
- [ ] **Step 5:** Commit `refactor(connection): common connection wiring in :core:connection`.

### Task 3: Onboarding on Compose Multiplatform with Koin

**Files:**
- Modify: `gradle/libs.versions.toml`. Add `cmp-lifecycle-runtime-compose = org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` (ref `lifecycle`) and `cmp-ui-backhandler = org.jetbrains.compose.ui:ui-backhandler` (ref `composeMultiplatform`).
- Modify: `feature/onboarding/build.gradle.kts`. Use `nidoo.kmp.library` + `nidoo.kmp.compose` (no Hilt); `kotlin.android { namespace = "com.gekkotron.nidoo.feature.onboarding" }`. commonMain: `:core:data`, `:core:connection`, `:core:designsystem`, `cmp-lifecycle-viewmodel-compose`, `cmp-lifecycle-runtime-compose`, `cmp-ui-backhandler`, koin BOM + `koin-core`, `koin-compose-viewmodel`. androidMain: activity-compose, core-ktx, camera2, camera-lifecycle, camera-compose, zxing. androidHostTest: what `src/test` uses today (junit4, robolectric, compose ui-test-junit4 + BOM, ui-test-manifest, coroutines-test). `compose.resources { packageOfResClass = "com.gekkotron.nidoo.feature.onboarding.resources" }`.
- Move: `src/main/kotlin/**` → `src/commonMain/kotlin/**`, except `ui/QrCamera.kt`, `ui/NotificationsScreen.kt` and `ui/SecureWindow.kt` → `src/androidMain/kotlin/**`. The Android half of `ScanScreen.kt` (the permission launcher and the settings intent) goes to `src/androidMain/.../ui/CameraAccess.android.kt`.
- Move: `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (replace any `\'` with `'`).
- Move: `src/test/**` → `src/androidHostTest/**`.
- Create: `src/commonMain/kotlin/com/gekkotron/nidoo/feature/onboarding/ui/CameraAccess.kt`, `src/commonMain/kotlin/com/gekkotron/nidoo/feature/onboarding/di/OnboardingModule.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt` and `app/src/main/kotlin/com/gekkotron/nidoo/di/AppModule.kt` (the tester stays a Hilt singleton; the bridge exposes it). The Android `NidooNavHost` imports are unchanged apart from `NotificationsRoute`'s and `OnboardingRoute`'s packages, if they move.
- Modify: `.github/workflows/ci.yml` (`:feature:onboarding:test` → `:feature:onboarding:testAndroidHostTest`).
- Test: `src/androidHostTest/.../di/OnboardingModuleTest.kt`, `src/commonTest/.../ui/CameraPermissionTest.kt`.

**Interfaces:**
- Consumes: `ConnectionTester` (Task 2), `ProfileRepository`.
- Produces:
  - `val onboardingModule: Module = module { viewModelOf(::OnboardingViewModel) }`. `OnboardingViewModel(profiles: ProfileRepository, tester: ConnectionTester)` loses `@HiltViewModel`/`@Inject` and nothing else.
  - `@Composable fun OnboardingRoute(viewModel: OnboardingViewModel = koinViewModel())`, which uses the common `LifecycleStartEffect` and `BackHandler`.
  - `enum class CameraPermission { WAITING, GRANTED, DENIED }` (now `internal` in commonMain) and `internal fun CameraPermission.onResume(granted: Boolean): CameraPermission` (GRANTED when granted, else unchanged).
  - `internal class CameraAccess(val permission: CameraPermission, val openSettings: () -> Unit)`; `@Composable internal expect fun rememberCameraAccess(): CameraAccess`, which asks once on first composition and re-checks on ON_RESUME; `@Composable internal expect fun QrCamera(modifier: Modifier, onText: (String) -> Unit)`; `@Composable internal expect fun SecureWindow()`.
  - `ScanScreen(error, onText, onManual, onBack, camera = …)` keeps its signature in commonMain and reads `rememberCameraAccess()`. `ScanContent` is unchanged.
  - `KoinBridgeEntryPoint` gains `fun connectionTester(): ConnectionTester`. `hiltBridgeModule` adds `factory { bridge.connectionTester() }`, and `startNidooKoin` loads `modules(hiltBridgeModule(bridge), onboardingModule)`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// CameraPermissionTest (commonTest)
@Test fun deniedThenGrantedShowsTheCamera()   // CameraPermission.DENIED.onResume(granted = true) == GRANTED
@Test fun resumeWithoutGrantKeepsTheState()   // DENIED.onResume(false) == DENIED; WAITING.onResume(false) == WAITING
// OnboardingModuleTest (androidHostTest)
@Test fun onboardingModuleResolves()          // onboardingModule.verify(extraTypes = listOf(ProfileRepository::class, ConnectionTester::class))
```
- [ ] **Step 2:** Run `./gradlew :feature:onboarding:testDebugUnitTest`. Expected: FAIL (unresolved `onResume`, `onboardingModule`).
- [ ] **Step 3:** Convert the build file, and move sources, resources and tests as listed. Replace `R.string.x` with `Res.string.x`, and `androidx.compose.ui.res.stringResource` with the CMP one, in commonMain. `androidMain` files that keep Android resources use `Res` too. Replace `hiltViewModel()` with `koinViewModel()`, `collectAsStateWithLifecycle` and the `LifecycleStartEffect`/`LifecycleEventEffect` imports with the JetBrains lifecycle ones, and `androidx.activity.compose.BackHandler` with `androidx.compose.ui.backhandler.BackHandler`. Write `CameraAccess.kt` and the Android actuals (today's `ScanScreen` permission code, now using `onResume`). For iOS, Task 4 brings the real actuals; add `iosMain` stubs here (`rememberCameraAccess` = DENIED with a no-op, `QrCamera` empty, `SecureWindow` no-op), so this task compiles on its own. Update the Koin bridge and `startNidooKoin`.
- [ ] **Step 4:** Run `./gradlew :feature:onboarding:testAndroidHostTest :feature:onboarding:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS. The existing `OnboardingViewModelTest`, `ManualFormTest`, `OnboardingScreensTest` and `NotificationsScreenTest` are unchanged apart from imports and their resource lookups.
- [ ] **Step 5:** Commit `feat(onboarding): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 4: The iOS QR scanner and camera permission

**Files:**
- Create: `feature/onboarding/src/iosMain/kotlin/com/gekkotron/nidoo/feature/onboarding/ui/CameraAccess.ios.kt`, `feature/onboarding/src/iosMain/kotlin/com/gekkotron/nidoo/feature/onboarding/ui/QrCamera.ios.kt`, `feature/onboarding/src/iosMain/kotlin/com/gekkotron/nidoo/feature/onboarding/ui/SecureWindow.ios.kt` (they replace Task 3's stubs).
- Modify: `iosApp/project.yml` (`NSCameraUsageDescription: Nidoo scans your hub's setup code.`).

**Interfaces:**
- Consumes: the `expect` declarations of Task 3.
- Produces:
  - `rememberCameraAccess()` on iOS. It maps `AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)` to authorized → GRANTED, notDetermined → WAITING (then `requestAccessForMediaType`, with the answer delivered on the main queue), denied or restricted → DENIED. It re-checks on ON_RESUME through `onResume`. `openSettings` opens `UIApplicationOpenSettingsURLString`.
  - `QrCamera(modifier, onText)` on iOS: a `UIKitView` around a `UIView` subclass whose `layoutSubviews` sizes its `AVCaptureVideoPreviewLayer` (`videoGravity = AVLayerVideoGravityResizeAspectFill`). The session has the back wide-angle camera as input and an `AVCaptureMetadataOutput` with `metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)`; its delegate (an `NSObject` implementing `AVCaptureMetadataOutputObjectsDelegateProtocol`) runs on the main queue and calls the latest `onText` (`rememberUpdatedState`) with `stringValue`. `startRunning()` runs on a background dispatch queue (Apple: never on the main thread); `stopRunning()` runs in `onRelease`. Without a camera (Simulator), the view stays black and nothing crashes.

- [ ] **Step 1:** Write the three actuals and the plist key.
- [ ] **Step 2:** Run `./gradlew :feature:onboarding:compileKotlinIosSimulatorArm64 :feature:onboarding:compileKotlinIosArm64`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Read through Review Focus items 2 and 3 against the code: the ON_RESUME re-check, and repeated frames going through `onQrScanned`'s dedupe (the delegate does not filter, so the ViewModel stays the single place). No Simulator.
- [ ] **Step 4:** Commit `feat(onboarding): QR scanner and camera permission on iOS`.

### Task 5: The iOS app shell: live connection, onboarding, house summary

**Files:**
- Modify: `shared/build.gradle.kts`. commonMain adds `:core:connection`, `:core:data`, `:feature:onboarding`, `cmp-lifecycle-runtime-compose`; the framework keeps `export(project(":core:transport"))`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/AppShellViewModel.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/HouseSummaryViewModel.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/NidooApp.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/ThemeChoices.kt`, `shared/src/commonMain/composeResources/values{,-fr}/strings.xml`, `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/di/IosModule.kt`.
- Delete: `shared/src/commonMain/.../NidooRoot.kt`, `DemoHouseViewModel.kt`, `DemoHouseSummary.kt` and their tests, once nothing else uses them (`NidooShared.VERSION` and `sharedCoreSummary` stay).
- Modify: `shared/src/commonMain/.../di/SharedModule.kt`, `shared/src/iosMain/.../MainViewController.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/AppViewModel.kt` (use `ThemePreference.toChoice()` from `:shared`; delete the app copy).
- Modify: `core/data/src/iosMain/kotlin/com/gekkotron/nidoo/data/IosDataPaths.kt` (add `nidooDeviceOnlyPath(file)`).
- Test: `shared/src/commonTest/.../AppShellViewModelTest.kt`, `shared/src/commonTest/.../HouseSummaryViewModelTest.kt`, `shared/src/androidHostTest/.../di/SharedModuleTest.kt` (updated), `core/data/src/iosTest/kotlin/com/gekkotron/nidoo/data/IosDataPathsTest.kt`.

**Interfaces:**
- Consumes: `TransportFactory`, `profileSpecs`, `SharedSubscriptions`, `EntitySourceSession`, `DirectConnectionTester`, `setupClientIdFor` (Task 2), `onboardingModule`, `OnboardingRoute` (Task 3), `iosForeground()`, `iosNetworkChanges()`, `NetworkTcpProbe`, `PortMqttTransport`, `AthenaGatewayApi(spec)` (Phase 2 / Task 1), `ProfileRepository.create`, `AppPreferences.create`, `FileEntityCache.create`, `KeychainSecretStore`, `nidooDataPath`, `nidooCachePath`.
- Produces:
  - `fun ThemePreference.toChoice(): ThemeChoice` (moved from `app`, now public in `com.gekkotron.nidoo.shared`).
  - `class AppShellViewModel(profiles: ProfileRepository, preferences: AppPreferences, sources: EntitySourceSession) : ViewModel`, with `val profile: StateFlow<ProfileState>`, `val theme: StateFlow<ThemeChoice?>`, `fun finishRooms()`, `fun finishNotifications()` and `fun setUpAgain()`. `setUpAgain()` calls `profiles.clear()` then `sources.reset()`, as on Android without the push cleanup.
  - `data class HouseSummary(val link: LinkState, val devices: Int)`; `class HouseSummaryViewModel(source: EntitySource) : ViewModel { val summary: StateFlow<HouseSummary> }`, where `devices` = `deviceNames(devices, entities).size` (the onboarding count, made `public` in `:core:connection`).
  - `@Composable fun NidooApp(shell: AppShellViewModel = koinViewModel())`: `NidooTheme(theme ?: SYSTEM) { NidooBackground { … } }`. Loading draws only the background. None and Locked show `OnboardingRoute()`. Ready with `notificationsPending` calls `finishNotifications()` in a `LaunchedEffect`. Ready with `roomsPending` calls `finishRooms()` (4B shows the Rooms step instead). Otherwise Ready shows the house summary: `LinkPill(summary.link, onClick = null)`, `Res.string.house_devices` ("%1$d devices" / "%1$d appareils"), and an `OutlinedButton` with `Res.string.house_set_up_again` ("Set up again" / "Reconfigurer"). Each Ready profile gets its own `HouseSummaryViewModel`, keyed by profile, so a new house never shows the old one's source.
  - `val sharedModule` = `viewModelOf(::AppShellViewModel)`, `viewModelOf(::HouseSummaryViewModel)`.
  - `fun iosModule(mqttPortFactory: () -> MqttClientPort): Module` (iosMain), with singles for: the app `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; `KeychainSecretStore()`; `ProfileRepository.create(nidooDeviceOnlyPath("profile.json"), …)`; `AppPreferences.create(nidooDataPath("preferences.json"), …)`; `FileEntityCache.create(nidooCachePath("entities.json"), …)`. Copy the file names `AndroidStores.kt` uses if they differ from these. It also has singles for `TransportFactory(scope, iosNetworkChanges(), direct = { s, sc -> PortMqttTransport(s, sc, mqttPortFactory()) }, gatewayApi = ::AthenaGatewayApi, probe = { h, p -> NetworkTcpProbe(h, p) })`, `ActiveTransport(profileSpecs(get()), factory::create, scope)`, `SharedSubscriptions`, `MqttTransport` (= the shared subscriptions), `EntitySourceSession(transport, scope, cache = get())` and `ConnectionTester` (`DirectConnectionTester` with the same `direct`/`gatewayApi` and `setupClientIdFor(profiles.installId())`). `factory<EntitySource>` is Android's rule (`session.current(ready profile)`). At creation, a `scope.launch { iosForeground().collect(active::setForeground) }` feeds the app lifecycle to the transport.
  - `fun nidooDeviceOnlyPath(file: String): Path`: `Application Support/Nidoo/device/<file>`, excluded from backup like `nidooCachePath`.
  - `MainViewController(mqttPortFactory)` is now `KoinApplication(configuration = koinConfiguration { modules(iosModule(mqttPortFactory), sharedModule, onboardingModule) }) { NidooApp() }`. `IosPlatform` goes away if nothing else reads it.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// AppShellViewModelTest (commonTest; FakeMqttTransport, a ProfileRepository on a fake store as in Task 2, Dispatchers.setMain)
@Test fun setUpAgainForgetsTheProfileAndTheSource() // save(Demo); vm.setUpAgain(); advanceUntilIdle → profile.value == ProfileState.None; session.peek() == null
@Test fun themeFollowsThePreference()               // preferences.setTheme(IVOIRE) → theme.value == ThemeChoice.IVOIRE
@Test fun finishNotificationsClearsTheStep()         // a Ready state with notificationsPending; finishNotifications() → (profile.value as Ready).notificationsPending == false
// HouseSummaryViewModelTest (commonTest; a fake EntitySource)
@Test fun countsDevicesAndLooseEntities()           // 2 devices + 1 entity without a device → summary.value.devices == 3
@Test fun followsTheLink()                          // source link → Connected(GATEWAY) → summary.value.link == Connected(GATEWAY)
// IosDataPathsTest (iosTest)
@Test fun profileFileIsExcludedFromBackup()          // NSURL(nidooDeviceOnlyPath("x").parent) resourceValue NSURLIsExcludedFromBackupKey == true
// SharedModuleTest (androidHostTest)
@Test fun sharedModuleResolves()                     // sharedModule.verify(extraTypes = listOf(ProfileRepository::class, AppPreferences::class, EntitySourceSession::class, EntitySource::class))
```
Add `foregroundDrivesTheTransport` to `core/transport`'s `ActiveTransportTest` only if no existing test already covers `setForeground(true)` → connected and `setForeground(false)` → paused after the grace. Otherwise, name the covering test in the commit message.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL (unresolved types).
- [ ] **Step 3:** Implement the files listed. Move `toChoice()` and point `AppViewModel` at it. Add the plist key from Task 4 if it is not in yet.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64 :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Build the iOS app as CI does, with no Simulator boot: `xcodegen generate --spec iosApp/project.yml && xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=NO build`. Expected: BUILD SUCCEEDED.
- [ ] **Step 6:** Commit `feat(shared): iOS shell with the live connection, onboarding and house summary`.

### Task 6: CI, docs and memory

**Files:**
- Modify: `.github/workflows/ci.yml`. Add any task the earlier tasks did not add: `:feature:onboarding:iosSimulatorArm64Test` only if onboarding has `commonTest`; the core group's `:core:connection` entries.
- Modify: `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md`. Add a dated §9 line covering the 4A rulings: `:core:connection` and its package, injected platform transports, no notification step on iOS, `SecureWindow` a no-op, a Kotlin AVFoundation scanner, `profile.json` excluded from backup.
- Modify: `README.md`. Add one line to the iOS section if it lists what the iOS app can do.

- [ ] **Step 1:** Edit CI and the docs.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest :core:connection:testAndroidHostTest :feature:onboarding:testAndroidHostTest :shared:testAndroidHostTest`. Expected: PASS.
- [ ] **Step 3:** Commit `docs(ios): phase 4A rulings; CI runs the connection and onboarding modules`.
- [ ] **Step 4:** Push, open PR "iOS phase 4A: onboarding and the live connection on iOS", and watch CI until it is green (CLAUDE.md: Gekkotron repos). Re-run a Robolectric HTTP 429 failure; fix anything else.

## iPhone checklist for 4A (the user, after install)

1. Fresh install: the Welcome screen is in the phone's language (French or English).
2. Scan: the camera prompt shows the plist text. Scan the hub's QR code; the connect screen lists the devices; Continue opens the house summary with the link pill set to Direct.
3. Leave the house Wi-Fi: the pill goes Reconnecting, then Gateway. Come back: Direct.
4. Background the app for over 30 s, then return: the pill reconnects.
5. "Set up again" returns to Welcome; "Try the demo" shows Demo.
6. Deny the camera, open Settings, allow it, then return: the scanner shows.
