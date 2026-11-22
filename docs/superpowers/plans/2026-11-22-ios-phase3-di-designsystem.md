# iOS Phase 3 — DI and design system Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:core:designsystem` runs on Android and iOS (Compose Multiplatform, fonts and strings as CMP resources), Koin is started on both platforms next to Hilt, and the iPhone shows the Nidoo theme with a localized demo link pill.

**Architecture:** `:core:designsystem` becomes a `nidoo.kmp.library` + `nidoo.kmp.compose` module; Android-only pieces (Vico charts, the notification permission enabler, `rememberNow`, the icon preview) stay in `androidMain`, so Android consumers see the same API. The Android-only screenshot test moves to a new `:core:designsystem-screenshots` Android module with its references unchanged, because the screenshot plugin does not support KMP library modules. Koin: `:shared` gets a common Koin module and a `DemoHouseViewModel` used by the iOS root; the Android app starts Koin after Hilt with a bridge module that hands Hilt's singletons to Koin, ready for Phase 4's common ViewModels.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1 (material3 1.9.0, components-resources 1.11.1), Koin 4.2.2 (BOM), Robolectric 4.17, Compose Preview Screenshot Testing 0.0.1-alpha17.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§7.3, §7.4, §7.6 phase 3, §9).

## Global Constraints

- "Each phase ends with Android CI green and something the user can see." (§7.6)
- "Existing Android installs upgrade in place." No change to stored formats. (§9)
- "Screenshot tests stay Android-only and must still pass unchanged (they are the guard that the move did not alter the look)." (§7.4)
- "Koin goes in module by module, alongside Hilt … Hilt is removed when the last Hilt user is gone." (§9)
- "`stringResource` call sites change import only." (§7.4)
- Keep the Android app on Compose BOM 2026.06.01 (material3 1.4.0, ui 1.11.4); CMP stays 1.11.1. CMP material3 is `1.9.0`, the version CMP 1.11.1's plugin maps to.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally); never boot a Simulator without the user's yes.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`, message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- Feature-module strings (~780) move to CMP resources with their module in Phases 4–6 (§9: one PR per module, consumers rewired in the same PR). Phase 3 moves the design system's strings and fonts, the only shared ones today.
- Vico stays Android: `HistoryCharts.kt` goes to `androidMain`. The common/expect split is done when the history screens move (Phase 5).
- Screenshot tests are not added to CI (they never were; references are rendered on macOS). They are run locally in Tasks 1 and 2.
- Pure-logic designsystem tests stay in `androidHostTest` for now; moving them to `commonTest` buys nothing until common screens use them.

## Review Focus

1. French device language: designsystem strings come from `composeResources/values-fr` on Android and iOS (Task 2 test `linkPillIsFrenchOnAFrenchPhone`).
2. Android escapes `\'` and `%1$d` placeholders survive the move to CMP XML (Task 2 tests `apostropheIsUnescaped`, `reconnectingShowsAttempt`).
3. The variable fonts render with the same weights through CMP `Font(FontResource, …, variationSettings)`: the screenshot references in Task 1/2 catch a change.
4. A second `NidooApplication.onCreate` in the same process (Robolectric, process reuse) must not throw `KoinApplicationAlreadyStartedException` (Task 4 test `startingTwiceReplacesTheGraph`).
5. iOS "Reduce Motion" on: `animationsEnabled()` returns false (Task 2 iOS actual; checked by reading, no Simulator).

---

### Task 1: Screenshot tests in their own Android module

**Files:**
- Create: `core/designsystem-screenshots/build.gradle.kts`
- Move: `core/designsystem/src/screenshotTest/**` → `core/designsystem-screenshots/src/screenshotTest/**`
- Move: `core/designsystem/src/screenshotTestDefaultDebug/**` → `core/designsystem-screenshots/src/screenshotTestDefaultDebug/**` (same file names)
- Modify: `settings.gradle.kts` (add `include(":core:designsystem-screenshots")`), `core/designsystem/build.gradle.kts` (drop `testOptions.screenshotTests`)

**Interfaces:**
- Produces: task `:core:designsystem-screenshots:validateDebugScreenshotTest`, used by Task 2 as the look guard.

- [ ] **Step 1:** Create the module: `nidoo.android.library` + `nidoo.android.compose`, namespace `com.gekkotron.nidoo.designsystem.screenshots`, the `screenshotTests.create("screenshotTest")` block copied from designsystem, the screenshot plugin as designsystem applies it today (check `gradle.properties`/root for `android.experimental.enableScreenshotTest`), `implementation(project(":core:designsystem"))`. The test file keeps package `com.gekkotron.nidoo.designsystem` so reference names stay `ThemeScreenshotsKt/*_ab3426bf_0.png`.
- [ ] **Step 2:** `git mv` the test sources and references; remove the screenshot block from designsystem.
- [ ] **Step 3:** Run `./gradlew :core:designsystem-screenshots:validateDebugScreenshotTest`.
  Expected: BUILD SUCCESSFUL, 4 previews matched. If the reference folder name the plugin expects differs (e.g. `screenshotTestDebug`), `git mv` to that name; never regenerate references.
- [ ] **Step 4:** Run `./gradlew :core:designsystem:test`. Expected: PASS.
- [ ] **Step 5:** Commit `test(designsystem): screenshot tests in their own Android module`.

### Task 2: Design system on Compose Multiplatform

**Files:**
- Modify: `gradle/libs.versions.toml` — `cmp-material3 = org.jetbrains.compose.material3:material3:1.9.0`, `cmp-components-resources = org.jetbrains.compose.components:components-resources` (ref `composeMultiplatform`), `cmp-ui-tooling-preview = org.jetbrains.compose.ui:ui-tooling-preview` (ref `composeMultiplatform`).
- Modify: `build-logic/convention/src/main/kotlin/nidoo.kmp.compose.gradle.kts` — commonMain adds `cmp-material3`, `cmp-components-resources`; the KMP Android target gets `androidResources { enable = true }` (CMP resources on the AGP 9 KMP library plugin).
- Modify: `core/designsystem/build.gradle.kts` — `nidoo.kmp.library` + `nidoo.kmp.compose`; `kotlin.android { namespace = "com.gekkotron.nidoo.designsystem"; withHostTest { isIncludeAndroidResources = true } }`; commonMain: `api(:core:model)`, `implementation(:core:transport)`; androidMain: `activity-compose`, `core-ktx`, `vico-compose-m3`, androidx `compose-ui-tooling-preview`; androidHostTest: junit4, robolectric, androidx compose `ui-test-junit4` + BOM, `ui-test-manifest`; `compose.resources { publicResClass = true; packageOfResClass = "com.gekkotron.nidoo.designsystem.resources" }`.
- Move: `src/main/kotlin/**` → `src/commonMain/kotlin/**`, except `charts/HistoryCharts.kt`, `permissions/NotificationEnabler.kt`, `icons/NidooIconsPreview.kt`, `Now.kt` → `src/androidMain/kotlin/**` (same packages).
- Move: `src/main/res/font/{inter,manrope}.ttf` → `src/commonMain/composeResources/font/`; `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (contents unchanged).
- Move: `src/test/**` → `src/androidHostTest/**`.
- Create: `src/androidMain/kotlin/com/gekkotron/nidoo/designsystem/Motion.android.kt`, `src/iosMain/kotlin/com/gekkotron/nidoo/designsystem/Motion.ios.kt`.
- Modify: consumers of `designsystem.R`: `feature/home/.../ui/CustomizeSheet.kt`, `feature/energy/.../ui/{EnergyScreen,EnergyDetailSheet,FluxDiagram,OrbiteDiagram}.kt`.
- Modify: `.github/workflows/ci.yml` — `:core:designsystem:test` → `:core:designsystem:testAndroidHostTest`.
- Test: `src/androidHostTest/kotlin/com/gekkotron/nidoo/designsystem/components/LinkResourcesTest.kt`.

**Interfaces:**
- Consumes: Task 1's screenshot module.
- Produces: `com.gekkotron.nidoo.designsystem.resources.Res` (public; `Res.string.*`, `Res.font.inter`, `Res.font.manrope`); `expect @Composable fun animationsEnabled(): Boolean`; every existing public composable keeps its signature, except `alarmStateText`-style helpers in `alarm/AlarmText.kt` that return `@StringRes Int` now return `org.jetbrains.compose.resources.StringResource`.

- [ ] **Step 1: Write the failing tests** in `LinkResourcesTest.kt` (Robolectric, `createComposeRule()`, `NidooTheme { … }`):

```kotlin
@Test fun linkPillIsFrenchOnAFrenchPhone() // @Config(qualifiers = "fr"); LinkPill(LinkState.Connected(TransportMode.DEMO), …) → onNodeWithText("Démo").assertExists()
@Test fun apostropheIsUnescaped()           // LinkSheet content for DEMO → onNodeWithText("You're exploring the demo house. Nothing leaves this phone.").assertExists()
@Test fun reconnectingShowsAttempt()        // LinkSheet content for LinkState.Reconnecting(attempt = 3, …) → onNodeWithText("Connection lost. Reconnecting (attempt 3)…").assertExists()
```

Use the existing `LinkPill`/`LinkSheet` signatures (see `components/LinkPillTest.kt` for how they are called).
- [ ] **Step 2:** Run `./gradlew :core:designsystem:test --tests '*LinkResourcesTest*'`. Expected: the French test FAILS if the qualifier is not honoured, otherwise all PASS on the Android resources. Record the result (these pin behaviour across the move; they must pass before and after).
- [ ] **Step 3:** Catalog and convention changes; convert the build file; move sources, resources and tests as listed.
- [ ] **Step 4:** Code changes:
  - Strings: replace `R.string.x` with `Res.string.x` and `androidx.compose.ui.res.stringResource` with `org.jetbrains.compose.resources.stringResource`.
  - `AlarmText.kt`: `@StringRes Int` → `StringResource`.
  - `NidooType.kt`: fonts come from `Res.font.*` via CMP `Font(resource, weight, variationSettings = FontVariation.Settings(FontVariation.weight(w)))`, which is `@Composable`. So `nidooType(dark)` becomes `nidooType(dark: Boolean, inter: FontFamily, manrope: FontFamily)`. `NidooTheme` builds the two families with `remember`-free composable calls, and the `LocalNidooType` default uses `FontFamily.Default`.
  - `Motion.kt`: `expect fun animationsEnabled()`. The Android actual is today's body. iOS returns `LocalInspectionMode.current || !UIAccessibilityIsReduceMotionEnabled()`.
  - Feature consumers: `DsR.string.energy_*` → `Res.string.energy_*` from `com.gekkotron.nidoo.designsystem.resources`. Fields typed `@StringRes Int` that hold them become `StringResource`. Their `stringResource(...)` calls switch to the CMP import (`components-resources` comes through designsystem as `api`).
- [ ] **Step 5:** Run, and save the output to the workspace:
  `./gradlew :core:designsystem:testAndroidHostTest :core:designsystem:compileKotlinIosSimulatorArm64 :core:designsystem-screenshots:validateDebugScreenshotTest :feature:home:testDebugUnitTest :feature:energy:testDebugUnitTest :app:assembleDebug`
  Expected: BUILD SUCCESSFUL. All designsystem tests pass, including the 3 new ones. The 4 screenshots match their references unchanged.
- [ ] **Step 6:** Run the Android suites the CI runs, as the `feature` and `app` groups do: `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7:** Commit `feat(designsystem): Compose Multiplatform with shared fonts and strings`.

### Task 3: Koin in `:shared` and the themed iOS root

**Files:**
- Modify: `gradle/libs.versions.toml`. Add `koin = "4.2.2"`; `koin-bom`, `koin-core`, `koin-compose`, `koin-compose-viewmodel`, `koin-android`, `koin-test`; and `cmp-lifecycle-viewmodel = org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`.
- Modify: `shared/build.gradle.kts`. commonMain gets `implementation(project(":core:designsystem"))`, `platform(koin-bom)`, `koin-core`, `koin-compose`, `koin-compose-viewmodel` and `cmp-lifecycle-viewmodel`; androidHostTest gets `koin-test`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/DemoHouseViewModel.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/di/SharedModule.kt`.
- Modify: `shared/src/commonMain/.../NidooRoot.kt`, `shared/src/iosMain/.../MainViewController.kt`.
- Test: `shared/src/commonTest/.../DemoHouseViewModelTest.kt`, `shared/src/androidHostTest/kotlin/com/gekkotron/nidoo/shared/di/SharedModuleTest.kt`.

**Interfaces:**
- Consumes: `suspend fun demoHouseSummary(scope: CoroutineScope): String` (Phase 2), `NidooTheme`, `LinkPill`.
- Produces: `class DemoHouseViewModel(summary: suspend (CoroutineScope) -> String = ::demoHouseSummary) : ViewModel { val summary: StateFlow<String?> }`; `val sharedModule: Module` (`viewModelOf(::DemoHouseViewModel)`); `@Composable fun NidooRoot()`, which reads the ViewModel with `koinViewModel()`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// DemoHouseViewModelTest (commonTest; Dispatchers.setMain(StandardTestDispatcher(testScheduler)))
@Test fun summaryStartsEmptyThenShowsTheDemoHouse() // summary.value == null, then after advanceUntilIdle == "Demo house: 3 entities" with summary = { "Demo house: 3 entities" }
// SharedModuleTest (androidHostTest)
@Test fun sharedModuleResolves() // sharedModule.verify(extraTypes = listOf(Function2::class))
```
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL (unresolved `DemoHouseViewModel`, `sharedModule`).
- [ ] **Step 3:** Implement `DemoHouseViewModel` (launch in `viewModelScope`, `MutableStateFlow<String?>(null)`) and `sharedModule`. Rewrite `NidooRoot` as `NidooTheme { NidooBackground(Modifier.fillMaxSize()) { … } }`: a "Nidoo" title in `NidooTheme.type.display`, `sharedCoreSummary()`, `LinkPill(LinkState.Connected(TransportMode.DEMO), onClick = {})` and the summary text ("…" while null). `MainViewController` wraps the content in `KoinApplication(application = { modules(sharedModule) }) { NidooRoot() }`.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:linkDebugFrameworkIosSimulatorArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(shared): Koin and the Nidoo theme on the iOS root`.

### Task 4: Koin started on Android next to Hilt

**Files:**
- Modify: `app/build.gradle.kts` (`platform(koin-bom)`, `koin-android`; test: `koin-test`).
- Create: `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/NidooApplication.kt` (`onCreate`, right after `super.onCreate()`).
- Test: `app/src/test/kotlin/com/gekkotron/nidoo/di/KoinBridgeTest.kt`.

**Interfaces:**
- Produces:
  - `@EntryPoint @InstallIn(SingletonComponent::class) interface KoinBridgeEntryPoint { fun profiles(): ProfileRepository; fun preferences(): AppPreferences; fun entitySource(): EntitySource }`.
  - `fun hiltBridgeModule(bridge: KoinBridgeEntryPoint): Module`, where each type is a Koin `factory` delegating to the bridge, so Hilt keeps owning the lifetimes.
  - `fun startNidooKoin(app: Application, bridge: KoinBridgeEntryPoint)`, which calls `stopKoin()` and then `startKoin { androidContext(app); modules(hiltBridgeModule(bridge)) }`.
  - Phase 4 adds common feature modules to that list.

- [ ] **Step 1: Write the failing tests** (Robolectric for `androidContext`; a fake bridge with fixed instances, built with the classes' test helpers or `mockk` if the app already uses it):

```kotlin
@Test fun bridgeHandsOutHiltsInstances() // after startNidooKoin: GlobalContext.get().get<ProfileRepository>() === fake.profiles; same for AppPreferences, EntitySource
@Test fun startingTwiceReplacesTheGraph() // startNidooKoin(app, fakeA); startNidooKoin(app, fakeB) → no exception; get<AppPreferences>() === fakeB.preferences
```
`@After` calls `stopKoin()`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*KoinBridgeTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3:** Implement `KoinBridge.kt`. In `NidooApplication.onCreate`, call `startNidooKoin(this, EntryPointAccessors.fromApplication(this, KoinBridgeEntryPoint::class.java))`.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest`. Expected: PASS, the whole app suite included.
- [ ] **Step 5:** Commit `feat(app): start Koin next to Hilt with a Hilt bridge`.

### Task 5: Docs, CI coverage, memory

**Files:**
- Modify: `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md`. Add a dated line in §9 for the screenshot-module and feature-strings rulings.
- Modify: `.github/workflows/ci.yml`. Add `:core:designsystem:compileKotlinIosSimulatorArm64` to the iOS job, if `:shared` linking does not already cover it, and the screenshot module's `testDebugUnitTest` only if it has unit tests.

- [ ] **Step 1:** Edit the docs and CI.
- [ ] **Step 2:** Run `./gradlew :core:designsystem:testAndroidHostTest :shared:testAndroidHostTest :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 3:** Commit `docs(ios): phase 3 rulings`.
