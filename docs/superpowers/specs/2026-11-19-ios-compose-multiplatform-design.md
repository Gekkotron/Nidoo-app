# Nidoo on iOS with Compose Multiplatform: design

Date: 2026-11-19 · Author: Gekkotron

## 1. Intent

The user wants Nidoo on **their own iPhone**: the same app they use on Android, not a separate iOS product. Only they will use it, so there is no App Store release, no TestFlight and no other users.

The project is described as Kotlin Multiplatform, but today only the logic layer is shared: eight `core` modules have `commonMain` code with a JVM target only, and `:shared` has an Android target only. Every screen (about 113 Compose files), the network layer, the encryption, storage, DI and the app shell are Android-only. No module has an iOS target. The KMP convention plugins already say iOS targets "land in Phase 6": this is that phase.

## 2. Goals

1. **The same app on iPhone**: the existing Compose screens run on iOS through Compose Multiplatform (CMP), with the same Night Glass and Ivoire themes, French and English strings, and the same behaviour.
2. **First usable iOS version** (user agreed):
   - setup by scanning the hub's QR code (manual setup as fallback);
   - Home and dashboards, rooms, favourites, groups, with the device control and customize sheets;
   - direct MQTT on the LAN and the encrypted Nidoo-gateway away from home, switching automatically.
3. **Then**, in order: Energy, Security, History; then Flows (automations), voice, in-app alerts.
4. **One codebase for screens**: a feature written once lands on both platforms.
5. **Installs without a paid Apple account**: built and signed from Xcode on this Mac with the user's free Apple ID, and kept alive past the 7-day signing limit by SideStore (or AltStore), not by a paid account.

## 3. Non-goals

- No App Store, TestFlight or distribution to other people.
- No push notifications on iOS (APNs needs the paid Apple Developer Program). Alerts on iOS are in-app only.
- No iOS home-screen widgets, Live Activities, Control Center controls or Siri in this project. Android's widgets (`:feature:widget`), status-bar controls (`:feature:controls`), push (`:core:push`), presence geofencing and WorkManager jobs stay Android-only.
- No redesign: the iOS app looks like Nidoo, not like a stock iOS app. Only the iOS behaviours that matter for daily use get polish (swipe-back, safe areas, keyboard).
- No change to the hub, the gateway protocol or the MQTT topics.

## 4. Anti-goals

- **The Android app must never regress.** Every step keeps `./gradlew check` and CI green, and the Android app ships between steps as it does today. No big-bang branch.
- **No second copy of the screens.** No SwiftUI rewrite of a screen that exists in Compose.
- **No weaker security on iOS**: gateway secrets go in the iOS Keychain, the AES-256-GCM envelope stays byte-compatible with the gateway, and no secret is logged.
- **No dependency on a paid Apple account** for the app to work day to day.

## 5. Constraints

- Toolchain: Kotlin 2.4.20, AGP 9.5 (`com.android.kotlin.multiplatform.library` for KMP + Android modules), Xcode 27.2 on this Mac.
- Compose Multiplatform version: the latest stable release compatible with Kotlin 2.4.20 (builder picks and pins it in `gradle/libs.versions.toml`).
- iOS targets: `iosArm64` (the phone) and `iosSimulatorArm64`. No `iosX64` (Apple Silicon Mac).
- **The iOS Simulator is only booted with the user's explicit approval** (user rule). Default verification is the user's iPhone, or Kotlin/Native tests that don't need the Simulator (`iosSimulatorArm64Test` runs headless through `xcrun simctl` — ask before the first run).
- Free Apple ID limits: 7-day signing, at most 3 sideloaded apps, and restricted capabilities. Push is out. App Groups and other extension capabilities are not needed by this project.
- iOS platform rules the app must satisfy:
  - `NSLocalNetworkUsageDescription` (Local Network prompt) for direct MQTT and the LAN probe;
  - App Transport Security exception for local plain HTTP (`NSAllowsLocalNetworking`) for the history API at `http://<broker host>:8090` and Frigate media;
  - `NSCameraUsageDescription` for QR scanning; `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription` when voice lands.
  - The app is suspended in the background: MQTT disconnects and must reconnect on foreground (the existing `ManagedTransport` reconnect logic covers this once it is fed foreground/background events).
- Attribution: Gekkotron everywhere (Xcode project team name, bundle display, copyright).
- Bundle id: `com.gekkotron.nidoo` (the free signing team makes it unique; builder may suffix it if Xcode refuses).

## 6. Approaches considered

| | A. Compose Multiplatform (**chosen**) | B. Native SwiftUI on the shared core |
|---|---|---|
| Screens | Existing Compose code moves to `commonMain` and runs on both | Every screen rewritten in Swift |
| Look | Identical Nidoo look on both | Native iOS look |
| Cost | Platform layer + DI + resources migration, screens mostly unchanged | All of A's platform work, plus ~113 screen files twice |
| Upkeep | One codebase | Two UI codebases forever |

**The user picked A**: they want the same app on iOS. B contradicts that goal. A home-screen web app was also dropped (separate web build, WebSocket MQTT, browser limits on plain-HTTP LAN access).

## 7. Design

### 7.1 Module shape

Each Android-only module becomes a KMP module with `commonMain`, `androidMain` and `iosMain`, using the existing `nidoo.kmp.library` convention plugin (AGP 9 KMP library plugin), extended with:

- `iosArm64()` and `iosSimulatorArm64()` targets;
- a new `nidoo.kmp.compose` convention plugin for UI modules: CMP plugin, Compose compiler, CMP runtime/foundation/material3/ui, CMP resources, multiplatform lifecycle-viewmodel.

`nidoo.kmp.jvm.library` modules (`model`, `state`, `source`, `history`, `dashboards`, `flows`, `climate`, `assist`) gain iOS targets. Their `jvmMain` code that only exists to reach `:core:transport` moves to `commonMain` once transport is multiplatform. Whether these keep a `jvm` target or switch to the Android target is the builder's call; the Android app's consumers must not change behaviour.

`:shared` becomes the iOS umbrella: it depends on every module the iOS app needs and is exported as the `Nidoo` framework (static) consumed by the Xcode project. It hosts `MainViewController()` (the CMP entry point).

A new `iosApp/` Xcode project (SwiftUI `App` hosting `MainViewController` via `UIViewControllerRepresentable`) holds only the iOS shell: Info.plist keys from §5, app icon, signing, and the Gradle build phase (`embedAndSignAppleFrameworkForXcode`).

### 7.2 Platform layer

The existing interfaces stay the contract; each gets an iOS implementation.

| Area | Today (Android/JVM) | Common contract | iOS |
|---|---|---|---|
| Direct MQTT | HiveMQ client in `DirectMqttTransport` | `MqttTransport` (unchanged, moves to `commonMain` with `LinkState`, `MqttMessage`, `TransportSelector`, `ManagedTransport`, `Handover`, `Backoff`, `TopicFilter`) | A Kotlin Multiplatform MQTT 3.1.1/5 client with TLS and auth (builder evaluates; must support retained messages, QoS 1, clean reconnect). Android keeps HiveMQ. |
| Gateway | `athena-gateway-client` SDK (OkHttp, JVM) behind `GatewayApi` | `GatewayApi` (unchanged) | The gateway SDK made multiplatform in its own repo (§10). |
| LAN probe, `LanOrGatewayGet` | OkHttp / sockets | small `expect`/interface | Ktor client (Darwin engine) or `NWConnection`. |
| Envelope crypto | `javax.crypto` AES-256-GCM, `java.util.Base64`, `SecureRandom` in `EnvelopeCrypto` | `EnvelopeCrypto` in `commonMain` with a platform AES-GCM primitive | A KMP crypto library with an Apple provider, or a Swift CryptoKit shim behind an interface. **Must pass the existing envelope test vectors on both platforms.** `kotlin.io.encoding.Base64` replaces `java.util.Base64`. |
| Secrets | `KeystoreSecretStore` | `SecretStore` (unchanged) | Keychain (`kSecClassGenericPassword`, `AfterFirstUnlockThisDeviceOnly`). |
| Preferences, profiles | AndroidX DataStore | same API (DataStore is KMP) | DataStore with a file in the app's Application Support directory. |
| Entity cache | in `:core:data` | unchanged | same as preferences. |
| Alerts store | Room | Room KMP (2.7+ supports iOS) | bundled SQLite driver. Only needed when in-app alerts land. |
| HTTP (history, hub APIs) | OkHttp | Ktor client in `commonMain` | Ktor Darwin engine. |
| Images | Coil 3 + OkHttp network | Coil 3 (KMP) | Coil 3 + Ktor network. |
| Network changes | Android `ConnectivityManager` (`NetworkChanges`) | `Flow<NetworkChange>` | `NWPathMonitor`. |
| App foreground/background | `ProcessLifecycleOwner` | lifecycle `Flow` | multiplatform lifecycle / `UIApplication` notifications. |

### 7.3 DI

Hilt (54 files plus `app/di/*`) does not run on iOS. **Builder's call, recommended: Koin** on both platforms (KMP, Compose and ViewModel support), migrated module by module so Android stays green at each step. Each `app/di/*Module.kt` becomes a Koin module; `@HiltViewModel` + `hiltViewModel()` become `koinViewModel()`. Android-only bindings (push, widgets, presence, controls, WorkManager) stay in an Android-only Koin module. Hilt is removed once nothing uses it. Keeping Hilt on Android with a separate iOS graph is rejected: shared screens would need two ways to get their ViewModels.

### 7.4 Design system and resources

- `:core:designsystem` moves to `commonMain` with CMP. Inter and Manrope fonts and the ~840 strings across `designsystem` and the feature modules (with `values-fr`) move to **Compose Multiplatform resources** (`composeResources/`, `Res.string.*`, `Res.font.*`). `stringResource` call sites change import only.
- Charts: Vico (`compose-m3` 2.1.3) — builder checks whether Vico's multiplatform artifact covers the charts used; if not, the chart composables get an `expect`/`actual` split with Android keeping Vico.
- Screenshot tests stay Android-only and must still pass unchanged (they are the guard that the move did not alter the look).

### 7.5 Screens, navigation and platform bits

- Feature modules for the iOS scope (`onboarding`, `home`, `settings` (connection pages first), then `energy`, `security`, `history`, then `automations`, `voice`, `alerts`) move their ViewModels and screens to `commonMain`.
- Navigation 3: the app shell (`NidooNavHost`, `MainTabs`) moves to common using the multiplatform Navigation 3 artifacts if available for the chosen CMP version; otherwise a small common back-stack with the same routes. iOS gets edge swipe-back.
- Android-only APIs used in screens (61 files import `android.*`) go behind small interfaces or `expect`/`actual`: haptics, clipboard, share, intents/deep links, permissions, `BackHandler`.
- QR scanning: Android keeps CameraX + ZXing. iOS uses an AVFoundation `AVCaptureMetadataOutput` QR scanner embedded with `UIKitView`, returning the same payload string to the shared `OnboardingPayload` parser.
- Media: Media3 (alert clips, media player card) stays Android; iOS uses `AVPlayer` through `UIKitView` when alerts land. The media player card shows state and controls without in-app playback on iOS until then.
- Voice (later phase): iOS `SFSpeechRecognizer` + `AVSpeechSynthesizer` behind the existing speech capture / availability contracts.
- Demo mode (`:core:demo`): moves to common so the iOS debug build can run against the demo house, the same as Android.

### 7.6 Phases

Each phase ends with Android CI green and something the user can see.

1. **Foundation.** iOS targets in the KMP convention plugins; the eight logic modules compile for iOS and their `commonTest` runs on iOS. `iosApp/` Xcode project and `:shared` framework; a CMP "Nidoo" screen installs on the iPhone from Xcode. SideStore setup documented in the README.
2. **Platform layer.** Transport, gateway, crypto, data and HTTP made multiplatform per §7.2, Android behaviour unchanged (existing tests pass), iOS implementations with tests. Demo mode in common.
3. **DI and design system.** Koin set up next to Hilt (see §9); designsystem, fonts and strings on CMP resources; screenshot tests unchanged.
4. **First iOS version.** App shell, onboarding (QR + manual), Home and dashboards with control and customize sheets, settings connection pages. Usable daily on the iPhone at home and away.
5. **Energy, Security, History.**
6. **Flows, voice, in-app alerts.**

## 8. Testing and verification

- Existing JVM/Android tests keep passing at every step; logic tests that move to `commonTest` also run on `iosSimulatorArm64` (ask before the first Simulator run).
- Envelope crypto: shared test vectors (including ones produced by the Android implementation today) pass on both platforms, and a round-trip against the real gateway is checked on the phone.
- Transport: the existing fake-broker / handover tests run in `commonTest` against the contract; the iOS MQTT client gets a test against a local Mosquitto (on the GEEKOM or the hub, not Docker on the Mac).
- Screens: Android screenshot tests unchanged; on iOS, manual check on the user's iPhone per phase with a short checklist (setup, LAN, gateway away from home, control a light, theme switch, French and English).

## 9. Calls made after the builder review

These were settled by the author, not the user; each is a default the user can overturn.

- **Swift fallbacks are injected, never compiled into Kotlin.** If no KMP MQTT client meets the bar (retained messages, QoS 1, TLS, username/password, clean reconnect), or no KMP crypto library does AES-256-GCM on Apple, the Swift side implements a Kotlin interface exported by the `Nidoo` framework (e.g. CocoaMQTT behind `MqttTransport`'s low-level client, CryptoKit behind the AES-GCM primitive) and passes it to `MainViewController(...)` at startup. Gradle builds and `./gradlew check` stay pure Kotlin; only the Xcode app links the Swift code.
- **iOS runs in CI.** Both repos are public, so GitHub macOS runners are free. CI gains a macOS job: compile the iOS framework (`linkDebugFrameworkIosSimulatorArm64`) and run `iosSimulatorArm64Test`. (The "ask before booting the Simulator" rule is about the user's Mac, not CI.) Signing for the device stays local.
- **Module conversions are one PR per module**, moving `src/main` → `commonMain`/`androidMain` (or `jvmMain`) and `src/test` → `commonTest`/`androidUnitTest` as needed, consumers rewired in the same PR. Each PR keeps Android green and shippable.
- **Koin goes in module by module, alongside Hilt.** Hilt and Koin coexist while modules move (Hilt-side bindings bridge to Koin, or the reverse); each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`. Hilt is removed when the last Hilt user is gone. This replaces a big Phase 3 DI switch with no visible iOS progress.
- **Existing Android installs upgrade in place.** The DataStore files, the Keystore secrets, the entity cache and the Room alerts database keep their names, keys and serialized formats, so the user's current Android setup survives the update. Any format change needs a migration and a test.
- **2026-11-21, MQTT gate (Phase 2, Task 6): Swift fallback.** KMQTT (`io.github.davidepianca98:kmqtt-client`), the leading KMP MQTT client, publishes iosArm64/iosSimulatorArm64 but fails "a release in the last 12 months": its latest is 1.0.0 of 2024-12-11. The iOS direct link is CocoaMQTT (SwiftPM) behind the Kotlin `MqttClientPort`, passed to `MainViewController(mqttPort:)`; its broker checks are manual on the phone.
- **2026-11-22, Phase 3 (design system, DI).** AGP's screenshot test suites don't run in a KMP library module, so the design system's screenshot tests moved, references unchanged, to the Android-only `:core:designsystem-screenshots`. Feature-module strings move to CMP resources with their module (Phases 4–6); Phase 3 moved the design system's. The Glance widget keeps its own Android copy of the alarm labels (it needs them synchronously). On Android, Koin starts after Hilt and a Hilt entry point hands `ProfileRepository`, `AppPreferences` and `EntitySource` to Koin as factories.
- **2026-11-22, Phase 4A (connection, onboarding).** Phase 4 ships as three PRs: 4A connection and onboarding, 4B Home, 4C Settings and the full shell. The app's common connection wiring moved to a new `:core:connection`, keeping the package `com.gekkotron.nidoo.connection`. The platform links (direct MQTT, gateway API, LAN probe) are injected into the common transport factory and connection test. Android's Hilt still owns the transport stack and hands Koin the `ConnectionTester`; iOS builds the graph in an iOS Koin module. iOS has no notification step (no push), and `SecureWindow` is a no-op there. The QR scanner is Kotlin on AVFoundation. The iOS profile file is excluded from backups, because its Keychain key is ThisDeviceOnly. CMP resources need positional placeholders (`%1$d`).
- **2026-11-23, Phase 4B (Home).** Home moved to Compose Multiplatform and runs on iOS. Formatting lives in a new `:core:format`: `FormatLocale` is an `expect class` whose JVM actual is a typealias for `java.util.Locale` (so Android code and tests are untouched) and whose iOS actual wraps an `NSLocale` identifier, with Foundation's formatters doing numbers and dates, and one accent fold shared by search, room icons and area ids. The energy presentation moved to `:core:energy`, keeping the package `com.gekkotron.nidoo.feature.energy.presentation` as `:core:connection` did in 4A. Entity labels are preloaded from CMP strings (`getString` is suspend and the builders are synchronous), so the first few milliseconds after launch show untranslated labels. Voice and History leave Home as nullable callbacks (`onOpenVoice`, `onOpenHistory`): Android wires them, iOS passes none until Phases 5 and 6. The iOS Settings is a temporary screen in `:shared` (Back and "Set up again") until 4C. Home's history chart is `expect`/`actual`: Android keeps Vico and iOS draws a plain line with the min/max band. Each iOS screen's ViewModel store owns its saved state, because the CMP iOS root never enables saved-state handles. `FlowsHttpError` and `HttpFlowsClient` are common, and `httpRouteFor` holds the HTTP route rule (local probe unless GATEWAY, gateway downloader unless DIRECT) that Android's `mediaRouteFor` and iOS both call. Home's Robolectric and screenshot tests moved to `androidHostTest` unchanged; new common tests also run on iOS in CI.
- **2026-11-24, Phase 4C (Settings, Navigation 3 shell).** Settings moved to Compose Multiplatform with Koin, and runs on iOS with all its strings as CMP resources (`presence_home_set` arrives pre-formatted, as `%1$s, %2$s`). Push, presence and voice sit behind a `PlatformSettings` seam: Android supplies their House rows and fills a `platformPage` slot with `AndroidSettingsPage`, and iOS binds none, so those rows don't show there. Android's nav host stays in `:app` until Phase 6, when its other destinations are common and the two hosts merge. The iOS shell is a JetBrains Navigation 3 `NavDisplay` in `:shared` over a plain list back stack (iOS has no process-death restore). Its per-entry ViewModel stores come from the Nav3 decorator, which replaced `ScreenOwner` and makes Home survive profile edits. Licences stay Android-only (iOS bundles no licence report). The iOS app version comes from the bundle (`CFBundleShortVersionString`, plus " · debug" on a debug binary). Home's snackbars now resolve their strings in the composition's resource environment. Retrying a failed first label load, caching the iOS formatters and sharing one Reduce Motion observer wait for Phase 5.
- **2026-11-26, Phase 5A (tab bar, Energy, History).** Phase 5 is two PRs: 5A (tab bar, Energy, History) and 5B (Security and its tab); the Automations tab waits for Phase 6. iOS shows only the tabs whose screens exist (Home, Energy). The tab bar (`MainTabBar`, `TabStatus`, `MainTabsViewModel`, `MainTab`) moved to `:shared` and Android uses it; the back-stack helpers keep one copy per host until Phase 6. `HistoryLineChart` and `DailyBarsChart` are expect/actual in `:core:designsystem`: Android keeps Vico, iOS draws on a Canvas with axes, band, average line and a tap marker resolved against the plot rect, with no pinch-zoom. Home's own chart expect/actual is gone, so the iOS dashboard chart is 220 dp with axes like Android. `currentFormatLocale()` moved to designsystem, and Energy uses designsystem's `animationsEnabled()`. `EnergyHistory` is bound by Koin (`energyModule`) and the Hilt binding is gone. History's numbers keep Android's `%.Nf` output through `roundHalfUp` and `formatNumber(…, grouping = false)`; a literal percent in CMP strings is a single `%`. Left for 5B: label-load retry, iOS formatter caching, and one Reduce Motion observer.
- **2026-11-27, Phase 5B (Security).** `:feature:security` moved to Compose Multiplatform with Koin (`securityModule`), and iOS shows Home, Energy and Security tabs; Security is a side tab like Energy. Its recent-alerts section stays empty on iOS until Phase 6 brings alerts. Security's time model is `kotlin.time`; only the sensor sheet's "Since 08:42" line is `expect`/`actual` (Android keeps `DateFormat.getBestDateTimePattern`; iOS asks `NSDateFormatter.dateFormatFromTemplate` for `j` skeletons, so the locale picks the clock). A common `rememberCurrentTime()` ticks every minute; Android's java `rememberNow()` stays for alerts. The Phase 4 leftovers landed: a failed label load retries after 1, 2 and 4 s before giving up; iOS caches its Foundation number and date formatters; one Reduce Motion observer serves the app. `MainTabsViewModel` now follows the profile and re-picks its `EntitySource` on every change, so the shield and bolt no longer show the previous house after "set up again" or a switch to the demo, on both hosts; with no profile it asks for no source.

## 10. Gateway client (user's call)

The `athena-gateway-client` SDK lives in the user's own `Athena-HttpMqttGateway` (Nidoo-gateway) repo and is JVM/OkHttp. **The user chose to make the SDK itself Kotlin Multiplatform**, in that repo:

- The SDK moves to KMP with `commonMain` on Ktor (OkHttp engine on Android/JVM, Darwin engine on iOS) and gains `iosArm64` / `iosSimulatorArm64` targets. Its public API stays source-compatible for existing JVM users where possible; any break is a new major tag.
- It is released through JitPack tags as today. JitPack builds on Linux and cannot produce iOS artifacts, so the builder decides how iOS consumers get them (e.g. publishing the KMP SDK to GitHub Packages or Maven Central, or Nidoo consuming the gateway repo as a Gradle included build or source dependency). The choice must keep Nidoo's CI reproducible.
- Nidoo's `AthenaGatewayApi` moves to `commonMain` against the multiplatform SDK; **Android and iOS use the same client**. The SDK's own tests (protocol, envelope, refusal and clock-skew mapping) run on both platforms in the gateway repo's CI.
- This work is part of Phase 2 and blocks "away from home" on iOS; LAN-only iOS can be used before it lands.

## 11. Left to the builder

CMP, Koin, Ktor and MQTT library choices and versions within §7; exact module split and source-set layout; whether logic modules keep a `jvm` target; how `iosApp/` is generated; the order of feature modules within a phase; the iOS QR scanner and player wrappers' internals.
