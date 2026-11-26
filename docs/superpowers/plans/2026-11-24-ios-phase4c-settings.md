# iOS Phase 4C — Settings and the Navigation 3 shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone gets the real Settings (Appearance, Connection, About, room order), and its screens sit on a Navigation 3 back stack with per-screen ViewModels and edge swipe-back. The Android app behaves exactly as it does today.

**Architecture:** First, Settings' Android-only parts (push, presence, voice) go behind a `PlatformSettings` seam and an Android page slot, still inside the Android module (Task 1). Then `:feature:settings` moves to Compose Multiplatform with Koin, as 4B moved Home (Task 2). The iOS shell in `:shared` swaps its hand-made screen switch for JetBrains' multiplatform `NavDisplay`, using the same keys and back-stack rules as Android's `NidooNavHost`, and drops `ScreenOwner` (Task 3). Task 4 handles a 4B leftover and the docs.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1, JetBrains `navigation3-ui` 1.1.2 (on androidx `navigation3-runtime` 1.1.7, the app's version) and `lifecycle-viewmodel-navigation3` 2.10.0, Koin 4.2.2, Reorderable 3.1.0 (KMP).

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§3, §4, §7.5, §7.6 phase 4, §9). Phase overview: `docs/superpowers/plans/2026-11-22-ios-phase4-first-version.md`. 4B: `docs/superpowers/plans/2026-11-23-ios-phase4b-home.md`. Ledger: `.superpowers/sdd/2026-11-24-ios-phase4c-settings/progress.md`.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place": no change to DataStore files, keys or the profile format (§9).
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- Presence, Notifications (push) and Voice stay Android-only (§3). On iOS their Settings rows don't show.
- "Only the iOS behaviours that matter for daily use get polish (swipe-back, safe areas, keyboard)." (§3) No other iOS restyling.
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), and positional placeholders (`%1$s`, `%1$d`). CMP has no `%.5f`.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`. JUnit4 `org.junit.Test` is fine there; in commonTest use `kotlin.test.Test`.
- commonTest ViewModels are cleared (`ViewModelStore.clear()`) inside `runTest`, before `resetMain` and before temp-dir deletion.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing.
- Android's `:app` keeps androidx `navigation3` 1.1.7. The JetBrains Navigation 3 artifacts go in `:shared` only.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- **Android's `NidooNavHost` stays in `:app`** until its other destinations (Energy, Security, History, Alerts, Automations) are common, in Phases 5–6. The iOS shell is a `NavDisplay` in `:shared` with the same key names and the same `push`/`pop`/`resetToRoot` rules. Those helpers are three short functions, so a copy is cheaper than a shared module now. Phase 6 merges the two hosts.
- **The `PlatformSettings` seam.** The common `SettingsViewModel` gets the platform's extra House rows (`PlatformRow(page, status)`) from `PlatformSettings`. Android supplies Notifications, Presence and Voice; iOS binds `NoPlatformSettings`. `RowStatus` is a plain enum, so the ViewModel and its tests need no resources. The bodies of the Android-only pages come through a `platformPage` slot on `SettingsPageScreen`, which Android's nav host fills with `AndroidSettingsPage`.
- **All Settings strings move to CMP resources**, the Android-only pages' strings included, so the module has one string table. `presence_home_set` becomes `Home set at %1$s, %2$s`, and the coordinates are formatted with `"%.5f".format(…)` before they reach it.
- **Licences: Android only for now.** `onOpenLicences` becomes nullable, and null hides the About page's Licences row. iOS passes null because no licence report is bundled there. `LicencesScreen` itself is common code.
- **iOS gets per-screen ViewModels from `rememberViewModelStoreNavEntryDecorator`.** Its owner enables saved-state handles itself (checked in the 2.10.0 bytecode: it calls `enableSavedStateHandles()`), so `ScreenOwner` and `ScreenViewModels` go away. Home's entry now survives profile edits (history URL, Frigate), which closes the 4B item "key Home's iOS ViewModel store on less than the whole profile".
- **The iOS back stack is a plain `mutableStateListOf<NavKey>`.** iOS has no process-death restore, so the serializable `rememberNavBackStack` would buy nothing.
- **App version on iOS** is `CFBundleShortVersionString` ("0.1"), with " · debug" added on a debug binary, the same as Android.
- **4B leftovers:** Home's snackbars resolve their strings in the composition's resource environment (Task 4). Retrying a failed first label load, caching the iOS formatters and sharing one Reduce Motion observer wait for Phase 5. The ATS check for a `.lan` hub hostname goes on the iPhone checklist.

## Review Focus

1. **"Set up again" from Settings › Connection on iOS** returns to Welcome. The next setup starts with fresh ViewModels, so no previous house shows. Task 3 `setUpAgainFromConnectionReturnsToWelcome`, plus 4A's `aNewHouseGetsANewSource` kept green.
2. **Editing the history URL or Frigate on iOS** (a profile change) neither rebuilds Home nor reconnects it. Task 3 `homeKeepsItsViewModelWhenTheProfileIsEdited`.
3. **Back on a root screen** (Home, Welcome) does nothing on iOS: no blank screen and no pop of the last entry. Task 3 `popOnlyPopsTheTopAndNeverTheRoot` and `backOnHomeStaysOnHome`.
4. **A double tap on the gear or on a back arrow** pushes or pops once. Task 3 `pushSkipsTheKeyOnTop` and `popOnlyPopsTheTopAndNeverTheRoot`.
5. **Apostrophes in French and English Settings strings** read `'`, not `\'`, after the move to CMP. Task 2 `apostrophesAreNotEscaped`.

---

### Task 1: Settings' Android-only parts behind a seam

Still the Android module with Hilt. Behaviour, screens and screenshots stay the same.

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/presentation/{PlatformSettings,AndroidPlatformSettings,AndroidSettingsViewModel}.kt`, `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/AndroidSettingsPage.kt`.
- Modify: `presentation/SettingsViewModel.kt`, `ui/{SettingsScreen,SettingsList,SettingsPages}.kt` (same folder); `app/src/main/kotlin/com/gekkotron/nidoo/navigation/NidooNavHost.kt`.
- Test: `feature/settings/src/test/…`: `SettingsViewModelTest`, `SettingsVoiceTest` (becomes `AndroidSettingsViewModelTest`), `SettingsListScreenTest`, `SettingsPagesTest`, `SettingsRouteTest` (becomes `AndroidSettingsPageTest`); create `AndroidPlatformSettingsTest`.

**Interfaces** (package `com.gekkotron.nidoo.feature.settings.presentation` unless noted):
- Produces:
  - `enum class RowStatus { NONE, ON, OFF, PUSH_REGISTERED, PUSH_WAITING, PUSH_FAILED, PUSH_OFF }`
  - `data class PlatformRow(val page: SettingsPage, val status: RowStatus)`
  - `interface PlatformSettings { val rows: Flow<List<PlatformRow>>; fun currentRows(): List<PlatformRow>; fun refresh() }` and `object NoPlatformSettings : PlatformSettings` (no rows; `refresh` does nothing).
  - `data class SettingsUi(theme, connection, energyView, link, roomCount, platformRows: List<PlatformRow> = emptyList())`. `push`, `voice` and `presenceEnabled` leave it.
  - `class SettingsViewModel(profiles: ProfileRepository, preferences: AppPreferences, source: EntitySource, platform: PlatformSettings)`. It keeps `setTheme`, `setEnergyView`, `setHistoryUrl` and `setFrigate`, and gains `fun refresh() = platform.refresh()`. The seed uses `platform.currentRows()`. Until Task 2, a Hilt `@Inject constructor(profiles, preferences, push: PushStatusSource, source, @ApplicationContext context)` delegates with `AndroidPlatformSettings(push, preferences, SpeechAvailability.check(context))`.
  - `class AndroidPlatformSettings(push: PushStatusSource, preferences: AppPreferences, availability: SpeechAvailability.Status) : PlatformSettings`. Its rows come in the order NOTIFICATIONS, PRESENCE, VOICE. Push maps Registered→PUSH_REGISTERED, Waiting→PUSH_WAITING, Failed→PUSH_FAILED, NotificationsOff→PUSH_OFF and Demo→NONE. Presence is ON when `presence?.enabled == true`. Voice is ON when `voiceEnabled == true` and `availability == Ready`. `refresh()` calls `push.refresh()`.
  - `class AndroidSettingsViewModel(push: PushStatusSource, preferences: AppPreferences, availability: SpeechAvailability.Status) : ViewModel` with `val push: StateFlow<PushStatus>`, `val voice: StateFlow<VoiceSettingsUi>` (seeded from `.value`), `setVoiceEnabled(Boolean)`, `registerAgain()` and `refreshPush()`. Its Hilt `@Inject constructor(push, preferences, @ApplicationContext context)` lasts until Task 2.
  - In `ui`: `SettingsListScreen(state, version, onOpenPage, onOpenRooms, onBack)` keeps its signature. The House group is Rooms plus one row per `state.platformRows`. Title, hint, glyph and tone come from a per-page table holding today's values (Notifications: BELL/ROSE, Presence: PIN/CYAN, Voice: MIC/VIOLET). Status text: NONE → none, ON/OFF → `settings_on`/`settings_off`, PUSH_* → `settings_push_short_*`.
  - `SettingsPageScreen(page, state, version, route, onBack, onTheme = {}, onEnergyView = {}, onRouteChange = {}, onSetUpAgain = {}, onSaveHistoryUrl = { true }, onSaveFrigate = { _, _, _ -> true }, onOpenLicences: (() -> Unit)? = null, platformPage: @Composable (SettingsPage, SnackbarHostState) -> Unit = { _, _ -> })`. NOTIFICATIONS, PRESENCE and VOICE render `platformPage`. A null `onOpenLicences` hides the Licences row. `now`, `onRegisterAgain`, `onOpenChannels`, `onVoiceEnabled` and `presenceSection` leave this signature.
  - `SettingsRoute(onOpenPage, onOpenRooms, onBack, viewModel)` calls `viewModel.refresh()` on every resume. `SettingsPageRoute(page, route, onRouteChange, onSetUpAgain, onOpenLicences: (() -> Unit)?, onBack, viewModel, platformPage)`.
  - `@Composable fun AndroidSettingsPage(page: SettingsPage, snackbar: SnackbarHostState, viewModel: AndroidSettingsViewModel = hiltViewModel(), clock: () -> Instant = Instant::now, presenceSection: @Composable (onInfo: (String) -> Unit) -> Unit = { PresenceSettingsSlot(onInfoMessage = it, showTitle = false) })`. NOTIFICATIONS keeps today's 60 s tick, the refresh on resume and `NotificationsBody`. PRESENCE renders `presenceSection` with a snackbar `onInfo`. VOICE renders today's `VoiceBody`, permission request included. Other pages render nothing.
  - `NidooNavHost`: `SettingsPageRoute(…, onOpenLicences = { push(backStack, LicencesKey) }, platformPage = { page, snackbar -> AndroidSettingsPage(page, snackbar) })`.

- [ ] **Step 1: Write the failing tests.** Move each push, voice and presence assertion from `SettingsViewModelTest`, `SettingsVoiceTest` and `SettingsRouteTest` to its new owner; the cases stay the same. New cases:

```kotlin
// AndroidPlatformSettingsTest
@Test fun rowsFollowPushPresenceAndVoice()   // Registered + presence on + voice on/Ready → [NOTIFICATIONS:PUSH_REGISTERED, PRESENCE:ON, VOICE:ON]
@Test fun demoPushHasNoWord()                // PushStatus.Demo → NOTIFICATIONS:NONE
@Test fun voiceIsOffWithoutOnDeviceRecognition() // voiceEnabled = true, NoOnDevice → VOICE:OFF
@Test fun refreshRereadsPush()               // refresh() → FakePushStatusSource.refreshes == 1
// SettingsViewModelTest
@Test fun platformRowsReachTheUi()           // fake PlatformSettings emitting one row → state.value.platformRows == that row; seeded from currentRows()
@Test fun refreshAsksThePlatform()
// SettingsListScreenTest
@Test fun noPlatformRowsMeansOnlyRooms()     // platformRows empty → "Notifications", "Presence", "Voice" absent; "Rooms" shown
// SettingsPagesTest
@Test fun licencesRowHiddenWithoutCallback() // ABOUT with onOpenLicences = null → no "Licences" node
```
- [ ] **Step 2:** Run `./gradlew :feature:settings:testDebugUnitTest`. Expected: FAIL to compile (unresolved `PlatformSettings`, `AndroidSettingsPage`).
- [ ] **Step 3:** Implement as listed. Keep `NotificationsBody`, `VoiceBody` and `PresenceSettingsSlot` unchanged and call them from `AndroidSettingsPage`.
- [ ] **Step 4:** Run `./gradlew :feature:settings:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS. `SettingsListScreenshotTest` and `VoiceSectionScreenshotTest` render the same images when given Android's rows.
- [ ] **Step 5:** Commit `refactor(settings): push, presence and voice behind a platform seam`.

### Task 2: `:feature:settings` on Compose Multiplatform with Koin

**Files:**
- Modify: `feature/settings/build.gradle.kts`. Use `nidoo.kmp.library` + `nidoo.kmp.compose`, namespace unchanged. commonMain: `:core:source`, `:core:data`, `:core:designsystem`, `:core:transport`, `:core:model`, CMP lifecycle viewmodel/runtime compose, Koin core + compose-viewmodel, `libs.reorderable`, kotlinx-serialization-json. androidMain: `:core:push`, `:feature:voice`, `androidx.activity.compose`, `androidx.core.ktx`, `koin-android`. Test deps as in `feature/home/build.gradle.kts`. `compose.resources { packageOfResClass = "com.gekkotron.nidoo.feature.settings.resources" }`. Hilt goes away.
- Move (packages unchanged) to `commonMain`: `SettingsViewModel`, `SettingsPage`, `ConnectionSummary`, `PlatformSettings`, `ui/{SettingsScreen,SettingsList,SettingsPages,ConnectionSection,EnergyViewSection,FrigateDialog,HistoryUrlDialog}`, `rooms/*`, `licences/*`. Move to `androidMain`: `AndroidPlatformSettings`, `AndroidSettingsViewModel`, `PresenceSettings`, `VoiceSettingsUi`, `ui/{AndroidSettingsPage,NotificationsPage,VoiceSection,PresenceSection,PresenceRoute}`, and `appVersion(context)`. Tests go to `androidHostTest` unchanged apart from imports.
- Move: `res/values{,-fr}/strings.xml` to `commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`, `presence_home_set` → `%1$s, %2$s`). Move `res/drawable/settings_mark_*.xml` to `commonMain/composeResources/drawable/`.
- Create: `commonMain/…/settings/di/SettingsModule.kt`, `androidMain/…/settings/di/AndroidSettingsModule.kt`, `commonMain/…/presentation/AppVersion.kt` (expect), `iosMain/…/presentation/AppVersion.ios.kt`, `commonTest/…/presentation/SettingsViewModelCommonTest.kt`, `androidHostTest/…/StringsTest.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt`, `app/src/test/kotlin/com/gekkotron/nidoo/di/KoinBridgeTest.kt`, `.github/workflows/ci.yml` (feature group: `:feature:settings:test` → `:feature:settings:testAndroidHostTest`; iOS job: add `:feature:settings:iosSimulatorArm64Test`).

**Interfaces:**
- Consumes: Task 1's types, unchanged.
- Produces:
  - `@Composable expect fun rememberAppVersion(): String`. Android remembers `appVersion(LocalContext.current)`. iOS reads `NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString")` ("?" when missing) and adds `" · debug"` when `Platform.isDebugBinary`. `SettingsRoute` and `SettingsPageRoute` use it.
  - `val settingsModule: Module` = `viewModel { SettingsViewModel(get(), get(), get(), get()) }` and `viewModel { RoomOrderViewModel(get()) }`. Each app binds `PlatformSettings`.
  - `val androidSettingsModule: Module`: `factory<SpeechAvailability.Status> { SpeechAvailability.check(androidContext()) }`, `factory<PlatformSettings> { AndroidPlatformSettings(get(), get(), get()) }`, `viewModel { AndroidSettingsViewModel(get(), get(), get()) }` and `viewModel { PresenceSettingsViewModel(get()) }`.
  - Every route's `viewModel` default becomes `koinViewModel()`. `AndroidSettingsPage` and `PresenceSettingsSlot` do the same.
  - `KoinBridgeEntryPoint.pushStatus(): PushStatusSource`, bridged as a factory. `startNidooKoin` loads `settingsModule` and `androidSettingsModule` too.
  - `pushWord`, `linkWord` and `themeName` return `StringResource`. `@StringRes` goes away.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// SettingsViewModelCommonTest (commonTest; fixtures like shared's AppShellViewModelTest)
@Test fun themeIsSeededFromTheStore()        // preferences.setTheme(IVOIRE) before the VM → state.value.theme == IVOIRE with no collection
@Test fun noPlatformMeansNoPlatformRows()    // NoPlatformSettings → state.value.platformRows.isEmpty()
@Test fun aBadHistoryUrlIsRefused()          // setHistoryUrl("not a url") == false; profile unchanged
@Test fun aBlankHistoryUrlClearsTheOverride()
// StringsTest (androidHostTest)
@Test fun apostrophesAreNotEscaped()         // runTest: getString(Res.string.rooms_save_failed) == "Couldn't save the order. Check the connection."
// KoinBridgeTest: the Android graph also resolves SettingsViewModel, RoomOrderViewModel, AndroidSettingsViewModel and PresenceSettingsViewModel.
```
- [ ] **Step 2:** Run `./gradlew :feature:settings:testDebugUnitTest`. Expected: FAIL (the new tests don't compile).
- [ ] **Step 3:** Convert the module and move the code as listed. If AGP won't let the KMP `androidMain` depend on the Android-library modules `:core:push` and `:feature:voice`, stop and report BLOCKED, with the error.
- [ ] **Step 4:** Run `./gradlew :feature:settings:testAndroidHostTest :feature:settings:compileKotlinIosSimulatorArm64 :feature:settings:compileTestKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(settings): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 3: The iOS shell on Navigation 3, with the real Settings

**Files:**
- Modify: `gradle/libs.versions.toml`. Add `jetbrainsNavigation3 = "1.1.2"`, `cmp-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "jetbrainsNavigation3" }` and `cmp-lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }`.
- Modify: `shared/build.gradle.kts` (commonMain adds both, plus `:feature:settings`). Also `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/NidooApp.kt`, `shared/src/iosMain/…/MainViewController.kt` and `shared/src/iosMain/…/di/IosModule.kt`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/ShellBackStack.kt`, `shared/src/commonTest/kotlin/com/gekkotron/nidoo/shared/ShellBackStackTest.kt`.
- Delete: `InterimSettings.kt`, `ScreenViewModels`/`ScreenOwner` (in `NidooApp.kt`), `shared/src/androidHostTest/…/ScreenOwnerTest.kt` and the three strings in `shared/src/commonMain/composeResources/values{,-fr}/strings.xml`. Delete the files, and the `compose.resources` block, if no resource remains.
- Test: `shared/src/androidHostTest/…/NidooAppTest.kt`, `shared/src/iosTest/…/di/IosModuleTest.kt`.

**Interfaces:**
- Consumes: `settingsModule`, `NoPlatformSettings`, `SettingsRoute`, `SettingsPageRoute`, `SettingsPage`, `RoomOrderRoute` (Task 2). `RoomsRoute`, `HomeRoute`, `OnboardingRoute` and `AppShellViewModel` as they are.
- Produces (internal, package `com.gekkotron.nidoo.shared`):
  - `data object OnboardingKey`, `RoomsKey`, `HomeKey`, `SettingsKey`, `RoomOrderKey : NavKey`, and `data class SettingsPageKey(val page: SettingsPage) : NavKey`.
  - `fun rootFor(profile: ProfileState): NavKey?`: Loading → null; None, Locked → OnboardingKey; Ready with `roomsPending` → RoomsKey; any other Ready → HomeKey (`notificationsPending` still triggers `finishNotifications()`).
  - `fun push(stack, key)`, `fun pop(stack, key)` and `fun resetToRoot(stack, root)`, with the exact bodies of Android's `NidooNavHost.push`/`pop` and `MainTabs.resetToRoot`.
  - `NidooApp`: theme and background as now. Then `val backStack = remember { mutableStateListOf<NavKey>(root) }`, `LaunchedEffect(root) { resetToRoot(backStack, root) }`, and `NavDisplay(backStack, onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), entryProvider = …)` with these entries:
    - Onboarding: `OnboardingRoute()`.
    - Rooms: `RoomsRoute(onDone = { shell.finishRooms(); pop(backStack, RoomsKey) })`.
    - Home: `HomeRoute(onOpenSettings = push SettingsKey, onOpenConnectionSettings = push SettingsKey then SettingsPageKey(CONNECTION), route, onRouteChange = shell::setRoute)`.
    - Settings: `SettingsRoute(onOpenPage = push SettingsPageKey, onOpenRooms = push RoomOrderKey, onBack = pop)`.
    - SettingsPage: `SettingsPageRoute(page, route, shell::setRoute, onSetUpAgain = shell::setUpAgain, onOpenLicences = null, onBack = pop)`.
    - RoomOrder: `RoomOrderRoute(onAssignRooms = push RoomsKey, onBack = pop)`.
  - `iosModule` adds `single<PlatformSettings> { NoPlatformSettings }`. `MainViewController` loads `settingsModule` too.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// ShellBackStackTest (commonTest)
@Test fun pushSkipsTheKeyOnTop()                 // [Home] push Settings twice → [Home, Settings]
@Test fun popOnlyPopsTheTopAndNeverTheRoot()     // [Home, Settings] pop(Home) → unchanged; pop(Settings) → [Home]; pop(Home) → [Home]
@Test fun resetToRootDropsEverythingElse()       // [Home, Settings, SettingsPage(CONNECTION)] reset Onboarding → [Onboarding]
@Test fun rootFollowsTheProfile()                // Loading→null, None→Onboarding, Locked→Onboarding, Ready(roomsPending)→Rooms, Ready(notificationsPending)→Home
// NidooAppTest (androidHostTest; its Koin graph adds settingsModule and single<PlatformSettings> { NoPlatformSettings })
@Test fun settingsOpensTheRealSettings()         // Ready demo → tap "Settings" → "Appearance" and "Connection" rows; no "Notifications" row
@Test fun setUpAgainFromConnectionReturnsToWelcome() // replaces settingsOffersSetUpAgain: Settings › Connection › "Set up again" › confirm → Welcome
@Test fun backFromASettingsPageReturnsToTheList()    // Settings › Appearance; system back → the list; back again → Home
@Test fun backOnHomeStaysOnHome()                // system back on Home → Home still shown
@Test fun roomOrderOpensFromSettings()           // Settings › Rooms → the room-order screen's title
@Test fun homeKeepsItsViewModelWhenTheProfileIsEdited() // Ready Direct profile (as aNewHouseGetsANewSource builds one); test graph overrides HomeViewModel's definition to count creations; open Settings; profiles.setHistoryUrl("http://hub.lan:8090"); back → Home; created == 1
// IosModuleTest: also resolves SettingsViewModel, RoomOrderViewModel and PlatformSettings.
```
Fire system back with `Espresso.pressBack()`, or with the host activity's `onBackPressedDispatcher`. Keep `setUpAgainAfterTheDemoShowsAWorkingWelcome`, `aNewHouseGetsANewSource` and `aNewProfileShowsTheRoomsStepThenHome` green.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL (unresolved `ShellBackStack` symbols, and Settings is still the interim screen).
- [ ] **Step 3:** Implement as listed.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:compileTestKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Build the iOS app with no Simulator boot: `xcodegen generate --spec iosApp/project.yml && xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=NO build`. Expected: BUILD SUCCEEDED.
- [ ] **Step 6:** Commit `feat(shared): iOS screens on Navigation 3, with the real Settings`.

### Task 4: Home snackbar strings, docs and the PR

**Files:**
- Modify: `feature/home/src/commonMain/kotlin/com/gekkotron/nidoo/feature/home/ui/HomeEventsEffect.kt`, and any other effect in `feature/*/src/commonMain` that calls `getString(Res.string.…)` outside composition. Each takes `val environment = rememberResourceEnvironment()` in composition and calls `getString(environment, …)`.
- Modify: `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (a dated §9 line, "2026-11-24, Phase 4C", stating this plan's rulings), `docs/superpowers/plans/2026-11-22-ios-phase4-first-version.md` (the 4C row points to this plan), and `.github/workflows/ci.yml` (check Task 2's entries are there).

- [ ] **Step 1:** Switch the snackbar lookups. Run `./gradlew :feature:home:testAndroidHostTest :feature:onboarding:testAndroidHostTest`. Expected: PASS; the existing snackbar tests cover the text.
- [ ] **Step 2:** Edit the spec and the phase overview. Run `./gradlew testDebugUnitTest :feature:settings:testAndroidHostTest :feature:home:testAndroidHostTest :feature:onboarding:testAndroidHostTest :shared:testAndroidHostTest :core:connection:testAndroidHostTest`. Expected: PASS.
- [ ] **Step 3:** Commit `docs(ios): phase 4C rulings; home snackbars use the composition's strings`.
- [ ] **Step 4 (controller):** Push `feat/ios-phase4c-settings` and open "iOS phase 4C: Settings and the Navigation 3 shell". Its base is `feat/ios-phase4b-home` while #59 is open, and `master` once #59 is merged. Watch CI until it is green. Re-run a Robolectric HTTP 429 or a "Cannot bind to port" failure; fix anything else.

## iPhone checklist for 4C (the user, after install)

1. The Home gear opens Settings with House (Rooms) and App (Appearance, Connection, About). No Notifications, Presence or Voice rows.
2. Appearance: switch to Ivoire, then Night Glass; the whole app follows at once.
3. Connection: broker, user and prefix are right. Set a history URL; Home keeps its state and doesn't reconnect. Pick the gateway route; Home's link follows.
4. Rooms: drag a room to a new place; Home shows the new order.
5. Swipe from the left edge on a Settings page: the list comes back. On Home, the swipe does nothing.
6. About shows "Nidoo 0.1 · debug" and no Licences row.
7. Settings › Connection › Set up again returns to Welcome. The demo then shows the demo house, with nothing from the previous one.
8. In French, all of Settings reads in French with real apostrophes ("l'ordre").
9. On a `.lan` hub hostname, history tiles load (ATS local networking).
