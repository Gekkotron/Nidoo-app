# iOS Phase 5A — the tab bar, Energy and History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone gets Android's bottom tab bar, with Home and Energy tabs, plus the Energy tab and the History screen (opened from Home, Energy and a device's chart). The Android app behaves exactly as it does today.

**Architecture:** The history charts become multiplatform in `:core:designsystem`. Android keeps Vico; iOS draws them on a Canvas (Task 1). The tab bar, its live status (lit bolt, lit shield) and its ViewModel move from `:app` into `:shared`, and Android's `NidooNavHost` uses the shared bar (Task 2). `:feature:energy` (Task 3) and `:feature:history` (Task 4) move to Compose Multiplatform with Koin, the way 4B moved Home. The iOS shell then gets the bar and the Energy and History entries (Task 5).

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1, JetBrains `navigation3-ui` 1.1.2, Koin 4.2.2, Vico (Android only).

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§3, §4, §7.5 "Navigation 3", §7.6 phase 5, §9). Previous plans: `docs/superpowers/plans/2026-11-23-ios-phase4b-home.md`, `docs/superpowers/plans/2026-11-24-ios-phase4c-settings.md`. Ledger: `.superpowers/sdd/2026-11-26-ios-phase5a-energy/progress.md`.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place": no change to DataStore files, keys or the profile format (§9).
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- "Only the iOS behaviours that matter for daily use get polish (swipe-back, safe areas, keyboard)." (§3) No other iOS restyling.
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), and positional placeholders (`%1$s`, `%1$d`); a bare `%d` renders unformatted. CMP has no `%.Nf`: numbers go through `formatNumber`.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`. In commonTest use `kotlin.test.Test`.
- commonTest ViewModels are cleared (`ViewModelStore.clear()`) inside `runTest`, before `resetMain` and before temp-dir deletion.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing.
- Android's `:app` keeps androidx `navigation3` 1.1.7. The JetBrains Navigation 3 artifacts stay in `:shared` only.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- **Phase 5 is two PRs.** 5A (this one) adds the tab bar, Energy and History. 5B adds Security and its tab. Security's recent-alerts section stays empty on iOS until Phase 6 brings alerts. Phase 6 adds the Automations tab on iOS.
- **On iOS, the bar shows only the tabs whose screens exist there.** That's Home and Energy in 5A. Android keeps its four tabs, with Automations hidden under the demo as today.
- **The bar is shared and the back stacks are not.** `MainTabBar`, `TabStatus`, `alarmArmed`/`solarActive` and `MainTabsViewModel` move to `:shared`. They take a `MainTab` enum, so they don't depend on either host's keys. `selectTab`, `showsTabBar` and `resetToRoot` keep one copy per host, as 4C ruled for `push`/`pop`. Phase 6 merges the two hosts.
- **Charts:** `HistoryLineChart` and `DailyBarsChart` become `expect`. Android keeps the Vico bodies unchanged. On iOS a Canvas draws the line or bars, the min/max band, the average line, four y-axis labels and four x-axis labels, plus a tap marker showing the nearest point's value and time. iOS has no pinch-zoom (the Vico zoom is Android-only). Home's own `HistoryChart` expect/actual goes away and Home calls `HistoryLineChart`, so the iOS dashboard chart grows to Android's 220 dp with axes.
- **`currentFormatLocale()`** moves from Home (internal) to `:core:designsystem` (public, package `com.gekkotron.nidoo.designsystem`), which gains `implementation(project(":core:format"))`. Home, Energy and History all call it.
- **Energy's own Reduce Motion check** (`FlowDiagram.animationsEnabled`, which reads `Settings.Global`) is deleted, and the diagrams use designsystem's `animationsEnabled()`.
- **`EnergyHistory` moves from Hilt to Koin.** `energyModule` binds `single<EnergyHistory> { EnergyHistoryImpl(get(), get()) }`. Hilt's `EnergyHistoryModule` stays until Task 4, because the Hilt `HistoryViewModel` still injects it; Task 4 deletes it.
- **Tests:** Energy's and History's existing tests move to `androidHostTest` unchanged (Robolectric, PNG renders). The chart geometry and the tab rules get new commonTest tests, which also run on iOS in CI.
- **Left for 5B:** retrying a failed first label load, caching the iOS formatters, and sharing one Reduce Motion observer.

## Review Focus

1. **Tapping the Energy tab twice, or tapping Home while on Energy, on iOS:** Energy is pushed once, Home pops it, and Back on Energy returns to Home. Task 5 `energyTabSelectsOnceAndHomePopsIt`.
2. **History opened from Energy, then Back** leaves the bar hidden on History and shows it again on Energy. Task 5 `historyHidesTheTabBar`.
3. **A chart with one point, all-equal values or a gap** draws without a crash or a divide-by-zero on iOS: an empty frame for fewer than 2 points, and a flat line in the middle for a zero range. Task 1 `scaleOfAFlatSeriesIsCentred`, `fewerThanTwoPointsDrawNothing`.
4. **French on the iPhone's Energy and History:** decimal commas, "15 janv." on day axes, a 24-hour clock, and apostrophes that read `'`. Task 3 `apostrophesAreNotEscaped`, Task 4 `timeLabelsFollowTheLocale` (commonTest, run on iOS in CI). History's strings have no apostrophe.
5. **The lit bolt and shield on Android** still follow solar production and an armed alarm after the move. Task 2 keeps `TabStatusTest`'s five cases green in `:shared` commonTest.

---

### Task 1: Multiplatform history charts

**Files:**
- Create: `core/designsystem/src/commonMain/kotlin/com/gekkotron/nidoo/designsystem/charts/{HistoryCharts,ChartGeometry}.kt`, `core/designsystem/src/iosMain/kotlin/com/gekkotron/nidoo/designsystem/charts/HistoryCharts.ios.kt`, `core/designsystem/src/commonMain/kotlin/com/gekkotron/nidoo/designsystem/FormatLocales.kt` (+ `androidMain`/`iosMain` actuals, moved from Home).
- Modify: `core/designsystem/src/androidMain/kotlin/com/gekkotron/nidoo/designsystem/charts/HistoryCharts.kt` (becomes `HistoryCharts.android.kt`: the two charts become `actual`; `ChartPoint`, `ChartBar`, `ChartHeight`, `HistoryBandKey`, `ChartSkeleton` move to common). `core/designsystem/build.gradle.kts`. `feature/home/src/commonMain/.../ui/` callers of `HistoryChart` and `currentFormatLocale`.
- Delete: `feature/home/src/{commonMain,androidMain,iosMain}/.../ui/HistoryChart*.kt` and `.../ui/FormatLocales*.kt`.
- Test: create `core/designsystem/src/commonTest/kotlin/com/gekkotron/nidoo/designsystem/charts/ChartGeometryTest.kt`; `HistoryChartsTest` (androidHostTest) stays green.

**Interfaces** (package `com.gekkotron.nidoo.designsystem.charts`):
- Produces:
  - In common: `data class ChartPoint(t: Long, mean: Double, min: Double, max: Double)` and `data class ChartBar(t: Long, value: Double)`, both unchanged. `val ChartHeight = 220.dp`. `@Composable fun HistoryBandKey(...)` and `@Composable fun ChartSkeleton(...)` keep their signatures.
  - `@Composable expect fun HistoryLineChart(points: List<ChartPoint>, color: Color, formatValue: (Double) -> String, formatTime: (Long) -> String, contentDescription: String, modifier: Modifier = Modifier, step: Boolean = false, formatBand: ((min: Double, max: Double) -> String)? = null)`
  - `@Composable expect fun DailyBarsChart(bars: List<ChartBar>, average: Double?, color: Color, formatValue: (Double) -> String, formatDay: (Long) -> String, contentDescription: String, modifier: Modifier = Modifier)`
  - `ChartGeometry.kt` (internal, common, pure):
    - `internal data class ValueScale(val low: Double, val high: Double) { fun fraction(v: Double): Float }`. It maps `low` to 0f and `high` to 1f. A zero range is widened by ±1 around the value, so the line sits at 0.5.
    - `internal fun valueScale(values: List<Double>): ValueScale`, with 5% headroom above and below.
    - `internal fun axisTicks(scale: ValueScale, count: Int = 4): List<Double>`: evenly spaced, `low` to `high` inclusive.
    - `internal fun timeTicks(first: Long, last: Long, count: Int = 4): List<Long>`: evenly spaced, `first` to `last` inclusive.
    - `internal fun nearestIndex(times: List<Long>, t: Double): Int`
    - `internal fun canDrawLine(count: Int): Boolean = count >= 2`, which the iOS line chart checks before drawing anything but the frame
  - `com.gekkotron.nidoo.designsystem.currentFormatLocale(): FormatLocale` (`@Composable expect`). On Android it reads `LocalConfiguration.current.locales[0]`; on iOS it's `systemLocale()`.
  - iOS actuals: a `Canvas` 220 dp high with the `contentDescription` semantics. Fewer than 2 points (or 0 bars) draws the empty frame. The band uses alpha 0.15f. `step = true` draws horizontal-then-vertical segments and no band. The average draws as a horizontal line in `NidooTheme.colors.content` at alpha 0.85f, 1.5 dp. Labels use `rememberTextMeasurer()` in `NidooTheme.type.label`, colour `contentMuted`. A tap (`detectTapGestures`) sets the marker: a vertical line at the nearest point, plus a label "value · time" (with the band line under it when `formatBand` is set). Tapping again elsewhere moves it.

- [ ] **Step 1: Write the failing tests** (`ChartGeometryTest`, commonTest):

```kotlin
@Test fun scaleOfAFlatSeriesIsCentred()      // valueScale(listOf(5.0, 5.0)).fraction(5.0) == 0.5f
@Test fun scaleHasHeadroom()                 // valueScale(listOf(0.0, 100.0)) == ValueScale(-5.0, 105.0)
@Test fun axisTicksSpanTheScale()            // axisTicks(ValueScale(0.0, 30.0)) == listOf(0.0, 10.0, 20.0, 30.0)
@Test fun timeTicksSpanTheData()             // timeTicks(0, 300) == listOf(0L, 100L, 200L, 300L)
@Test fun nearestIndexPicksTheClosestSample() // nearestIndex(listOf(0, 10, 20), 14.0) == 1; (…, 16.0) == 2; (…, -5.0) == 0
@Test fun fewerThanTwoPointsDrawNothing()    // canDrawLine(0) and canDrawLine(1) false; canDrawLine(2) true
```

- [ ] **Step 2:** Run `./gradlew :core:designsystem:testAndroidHostTest` (commonTest runs there too) and check that they fail. Expected: FAIL, unresolved references.
- [ ] **Step 3:** Implement `ChartGeometry.kt`. Split the charts into expect/actual and write the iOS actuals. Move `currentFormatLocale`. Point Home at `HistoryLineChart` (map `HistoryPoint` → `ChartPoint`, as `HistoryChart.android.kt` does today) and at the moved `currentFormatLocale`.
- [ ] **Step 4:** Run `./gradlew :core:designsystem:testAndroidHostTest :feature:home:testAndroidHostTest :feature:home:compileKotlinIosSimulatorArm64 :core:designsystem:compileKotlinIosSimulatorArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(designsystem): multiplatform history charts; iOS draws them on a Canvas`.

### Task 2: The tab bar moves to `:shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/tabs/{MainTab,MainTabBar,MainTabsViewModel}.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/di/MainTabsModule.kt`, and `shared/src/commonMain/composeResources/values{,-fr}/strings.xml` entries `nav_home`, `nav_energy`, `nav_security`, `nav_automations`, copied from `app/src/main/res/values{,-fr}/strings.xml` (en: Home, Energy, Security, Routines; fr: Accueil, Énergie, Sécurité, Routines).
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/navigation/{MainTabs,NidooNavHost}.kt`, `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt`. Delete `app/.../navigation/MainTabsViewModel.kt` and the four `nav_*` strings in `app/src/main/res` (only if nothing else in `:app` uses them).
- Test: move `app/src/test/.../navigation/TabStatusTest.kt` to `shared/src/commonTest/kotlin/com/gekkotron/nidoo/shared/tabs/TabStatusTest.kt` (`kotlin.test`, names without backticks or commas). `MainTabsTest` and `AutomationsTabVisibilityTest` stay in `:app` and stay green.

**Interfaces** (package `com.gekkotron.nidoo.shared.tabs`):
- Produces:
  - `enum class MainTab { HOME, ENERGY, SECURITY, AUTOMATIONS }`
  - `data class TabStatus(val alarmArmed: Boolean = false, val solarActive: Boolean = false)`, plus `fun alarmArmed(entities, states): Boolean` and `fun solarActive(entities, states): Boolean`, with `ACTIVE_ALARM_STATES` and `SOLAR_IDLE_W = 10.0` unchanged.
  - `class MainTabsViewModel(source: EntitySource) : ViewModel` with `val status: StateFlow<TabStatus>` (same `combine`/`WhileSubscribed(5_000)`).
  - `val mainTabsModule: Module = module { viewModelOf(::MainTabsViewModel) }`. Android adds it to `startNidooKoin`'s modules. iOS adds it in Task 5.
  - `@Composable fun MainTabBar(tabs: List<MainTab>, selected: MainTab?, onSelect: (MainTab) -> Unit, status: TabStatus = TabStatus())`. It draws the same `NavigationBar` with today's colours, glyphs (HOUSE, BOLT, SHIELD, AUTOMATION) and accent rules, one item per entry of `tabs`, in order.
  - Android: `MainTabs.kt` keeps `selectTab`, `resetToRoot` and `showsTabBar` and gains `internal fun NavKey.mainTab(): MainTab?` and `internal fun MainTab.key(): NavKey`. `NidooNavHost` calls `MainTabBar(tabs = listOfNotNull(HOME, ENERGY, SECURITY, AUTOMATIONS.takeIf { showAutomations }), selected = top?.mainTab(), onSelect = { selectTab(backStack, it.key()) }, status)`, with `koinViewModel<MainTabsViewModel>()`.

- [ ] **Step 1:** Move `TabStatusTest` to `:shared` commonTest (it should fail to compile), and add `@Test fun mainTabRoundTripsThroughItsKey()` to `MainTabsTest` (each `MainTab` → `key()` → `mainTab()` is itself).
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest --tests '*MainTabsTest*'`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement the move. Android's bar looks and behaves the same.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest`. Expected: PASS, including `AutomationsTabVisibilityTest` and the existing `MainActivityTest`/screenshot renders of Home with the bar.
- [ ] **Step 5:** Commit `refactor(shell): the tab bar and its status move to :shared`.

### Task 3: Energy on Compose Multiplatform with Koin

**Files:**
- Move: `feature/energy/src/main/kotlin/**` → `src/commonMain/kotlin/**`; `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`, positional placeholders in both locales); `src/test/**` → `src/androidHostTest/**`.
- Create: `feature/energy/src/commonMain/kotlin/com/gekkotron/nidoo/feature/energy/di/EnergyModule.kt`; `feature/energy/src/androidHostTest/kotlin/com/gekkotron/nidoo/feature/energy/StringsTest.kt` (copy of `feature/settings/.../StringsTest.kt`).
- Modify: `feature/energy/build.gradle.kts` (as `feature/home/build.gradle.kts`: `nidoo.kmp.library` + `nidoo.kmp.compose`, `packageOfResClass = "com.gekkotron.nidoo.feature.energy.resources"`, plus `:core:format`), `app/.../di/KoinBridge.kt` (add `energyModule`), `app/.../navigation/NidooNavHost.kt` (no change expected beyond imports).
- Keep in `androidMain` for now: `di/EnergyHistoryModule.kt` (Hilt), deleted in Task 4.

**Interfaces:**
- Consumes: `currentFormatLocale()`, `animationsEnabled()` (designsystem), `formatNumber`, `FormatLocale` (`:core:format`).
- Produces:
  - `val energyModule: Module`, which declares `single<EnergyHistory> { EnergyHistoryImpl(get(), get()) }` and `viewModel { EnergyViewModel(source = get(), dispatcher = Dispatchers.Default, history = get(), energyView = get<AppPreferences>().energyView.filterNotNull(), clock = { Clock.System.now().toEpochMilliseconds() }) }`. The Hilt `@Inject` secondary constructor and `@HiltViewModel` go away.
  - `EnergyRoute(onOpenHome, windowInsets, onOpenHistory: (String?) -> Unit, onOpenConnectionSettings, route, onRouteChange, viewModel: EnergyViewModel = koinViewModel())` keeps its other parameters. The save-failed snackbar resolves `Res.string.energy_save_failed` with `getString` inside the effect.
  - Every `java.util.Locale` becomes `FormatLocale`; `String.format` and `"%…".format` become `formatNumber` or CMP `stringResource(res, args)`. In `EnergyDescription`, the pre-resolved templates become `stringResource` calls with positional args.

- [ ] **Step 1: Write the failing test** `StringsTest.apostrophesAreNotEscaped`: `getString(Res.string.energy_save_failed) == "Couldn't save your changes. Check the connection and try again."` (Robolectric, as Settings' `StringsTest`).
- [ ] **Step 2:** Run `./gradlew :feature:energy:testAndroidHostTest`. Expected: FAIL (the module has not moved yet).
- [ ] **Step 3:** Move the module, convert strings and Hilt to CMP and Koin, then remove `java.*`, `android.*`, `LocalContext` and `R.` from `commonMain`. `grep -rnE '^import (java|javax|android\.|dagger)' feature/energy/src/commonMain` must print nothing.
- [ ] **Step 4:** Run `./gradlew :feature:energy:testAndroidHostTest :feature:energy:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest`. Expected: PASS. Every moved Energy test, `EnergyScreenTest`'s renders included, stays green.
- [ ] **Step 5:** Commit `feat(energy): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 4: History on Compose Multiplatform with Koin

**Files:**
- Move: as Task 3, for `feature/history` (`packageOfResClass = "com.gekkotron.nidoo.feature.history.resources"`). `HistoryTarget` moves too; `:app` keeps using it.
- Create: `feature/history/src/commonMain/kotlin/com/gekkotron/nidoo/feature/history/di/HistoryModule.kt`, `feature/history/src/commonTest/kotlin/com/gekkotron/nidoo/feature/history/ui/TimeLabelsTest.kt`.
- Delete: `feature/energy/src/androidMain/.../di/EnergyHistoryModule.kt`.
- Modify: `app/.../di/KoinBridge.kt` (add `historyModule`).

**Interfaces:**
- Consumes: `HistoryLineChart`, `DailyBarsChart`, `ChartSkeleton`, `HistoryBandKey` (Task 1); `EnergyHistory` from `energyModule` (Task 3).
- Produces:
  - `val historyModule: Module = module { viewModel { HistoryViewModel(source = get(), history = get(), energy = get()) } }`.
  - `HistoryRoute(target: HistoryTarget, onBack: () -> Unit, windowInsets: WindowInsets, viewModel: HistoryViewModel = koinViewModel())`.
  - `internal fun timeFormatter(range: HistoryRange, locale: FormatLocale, timeZoneId: String? = null): (Long) -> String`, which calls `formatEpochSeconds` with today's patterns (`HH:mm`, `EEE HH:mm`, `d MMM`, `MMM yy`). `private fun format(value, decimals, locale)` becomes `formatNumber(value, locale, decimals, decimals)`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// TimeLabelsTest (commonTest; Europe/Paris, 1 736 935 200 = 2025-01-15T10:00:00Z)
@Test fun timeLabelsFollowTheLocale()  // fr, D30 → "15 janv."; fr, H24 → "11:00"; en, D30 → "15 Jan"
```

- [ ] **Step 2:** Run `./gradlew :feature:history:testAndroidHostTest`. Expected: FAIL.
- [ ] **Step 3:** Move the module, convert it, and delete the Hilt `EnergyHistoryModule`. The import grep from Task 3 must print nothing for `feature/history/src/commonMain`.
- [ ] **Step 4:** Run `./gradlew :feature:history:testAndroidHostTest :feature:history:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS. `HistoryScreenshotTest` still writes non-blank PNGs.
- [ ] **Step 5:** Commit `feat(history): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 5: The iOS shell gets the tab bar, Energy and History

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/{ShellBackStack,NidooApp}.kt`, `shared/build.gradle.kts` (add `:feature:energy`, `:feature:history`), `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt` (add `energyModule`, `historyModule`, `mainTabsModule`).
- Test: `shared/src/commonTest/.../ShellBackStackTest.kt`, `shared/src/androidHostTest/.../NidooAppTest.kt`, and `shared/src/iosTest/.../di/` (the iOS graph test resolves `EnergyViewModel`, `HistoryViewModel` and `MainTabsViewModel`).

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces (in `ShellBackStack.kt`):
  - `internal data object EnergyKey : NavKey` and `internal data class HistoryKey(val target: String) : NavKey`
  - `internal val IOS_TABS: List<MainTab> = listOf(MainTab.HOME, MainTab.ENERGY)`
  - `internal fun selectTab(stack: MutableList<NavKey>, tab: MainTab)`: HOME removes the side tabs; ENERGY pushes `EnergyKey` over Home unless it is already on top. Same rules as Android's `selectTab`, with side tabs = `{EnergyKey}`.
  - `internal fun showsTabBar(top: NavKey?): Boolean = top == HomeKey || top == EnergyKey`
  - `internal fun NavKey.mainTab(): MainTab?`
  - `NidooApp`'s `Screens` wraps `NavDisplay` in a `Scaffold` the way `NidooNavHost` does: transparent container, `WindowInsets(0)`, and a `bottomBar` with `MainTabBar(IOS_TABS, top?.mainTab(), { selectTab(backStack, it) }, status)` when `showsTabBar(top)`. Home and Energy get `tabInsets`. Home gets `onOpenEnergy = { selectTab(backStack, MainTab.ENERGY) }` and `onOpenHistory = { push(backStack, HistoryKey(HistoryTarget.Entity(it).encode())) }`. Energy's `onOpenHistory` maps `null` to `HistoryTarget.House`. History pops itself on an undecodable target, as Android does.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// ShellBackStackTest (commonTest)
@Test fun energyTabSelectsOnceAndHomePopsIt()  // [Home] +ENERGY → [Home, Energy]; +ENERGY again → same; +HOME → [Home]
@Test fun historyHidesTheTabBar()              // showsTabBar(HomeKey), showsTabBar(EnergyKey) true; showsTabBar(HistoryKey("x")), showsTabBar(SettingsKey) false
// NidooAppTest (androidHostTest, Robolectric)
@Test fun homeShowsTheTabBarAndOpensEnergy()   // bar shows "Home" and "Energy" and not "Security"; tap Energy → Energy screen; tap Home → Home
@Test fun settingsHasNoTabBar()                // open Settings from Home → no "Energy" tab node
```

- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the iOS shell changes.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`, then `xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -destination 'generic/platform=iOS Simulator' build` (regenerate with XcodeGen first if the 4C plan says so). Expected: PASS and BUILD SUCCEEDED.
- [ ] **Step 5:** Commit `feat(ios): the tab bar, Energy and History on iOS`.

### Task 6: CI, docs, memory and the PR

**Files:**
- Modify: `.github/workflows/ci.yml`: `:feature:energy:test` → `:feature:energy:testAndroidHostTest`, same for history; add `:core:designsystem:iosSimulatorArm64Test`, `:feature:history:iosSimulatorArm64Test` and `:shared:iosSimulatorArm64Test` to the iOS job where a commonTest exists, if missing. Kover task names stay valid.
- Modify: the spec's decision log (§7.6 list): a "2026-11-26, Phase 5A (tab bar, Energy, History)" entry summarising the rulings above.
- Modify: the project memory file `ios-compose-multiplatform.md` (5A PR number, branch, what's next: 5B Security).
- PR body: summary and test plan, plus an iPhone checklist: the bar sits above the home indicator, the Energy tab's bolt lights while solar produces, edge swipe from History back to Energy, a tap on a History chart shows the marker, and French labels.

- [ ] **Step 1:** Run `./gradlew check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** Make the doc and memory edits, then commit `docs: iOS phase 5A decision log and CI tasks`.
- [ ] **Step 3:** Push and open the PR against `master` (Gekkotron token, per memory), then watch CI and fix failures until green.
