# iOS Phase 5B — Security and its tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone gets the Security tab (alarm panels, sensors with their "since" times, security actions), so the iOS bar shows Home, Energy and Security. The Phase 4/5A leftovers land too: a failed first label load is retried, the iOS formatters are cached, one Reduce Motion observer serves the whole app, and the tab bar's lit shield and bolt follow a profile switch on both hosts. The Android app behaves as it does today.

**Architecture:** Three small fixes come first: the label retry in Home's `PreloadedLabelText` (Task 1), the iOS formatter caches and the shared Reduce Motion observer (Task 2), and a `MainTabsViewModel` that re-picks its `EntitySource` when the profile changes (Task 3). `:feature:security` then moves to Compose Multiplatform with Koin, the way 5A moved Energy (Task 4). Its time model switches from `java.time` to `kotlin.time`, and only the date line of the sensor sheet stays platform-specific. The iOS shell then gets the Security entry and tab (Task 5).

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1 (CMP resources with `<plurals>`), JetBrains `navigation3-ui` 1.1.2, Koin 4.2.2.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§3, §4, §7.4, §7.5, §7.6 phase 5, §9). Previous plan: `docs/superpowers/plans/2026-11-26-ios-phase5a-energy.md` (its rulings and "Left for 5B"). Ledger: `.superpowers/sdd/2026-11-27-ios-phase5b-security/progress.md`.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place": no change to DataStore files, keys or the profile format (§9). Security's overrides (`securityHidden`, `securityAction`, `securityAsk`) keep their keys.
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- "Only the iOS behaviours that matter for daily use get polish (swipe-back, safe areas, keyboard)." (§3) No other iOS restyling.
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), positional placeholders (`%1$s`, `%1$d`), a literal percent is a single `%`. Plurals become CMP `<plurals>` read with `pluralStringResource`.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`. In commonTest use `kotlin.test.Test`.
- commonTest ViewModels are cleared (`ViewModelStore.clear()`) inside `runTest`, before `resetMain`.
- Anything holding `EntitySource` is a Koin `factory` or a ViewModel, never a `single` (the source is per profile).
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- **The recent-alerts section stays empty on iOS.** `SecurityRoute`'s `alertsSection` and `alertsEffects` are already nullable; the iOS shell passes neither. Phase 6 brings alerts. Android keeps passing `RecentAlertsRoute`/`RecentAlertsEffects`.
- **Security's time model is `kotlin.time`.** `Since.At(at: kotlin.time.Instant)`, `Since.Over(window: kotlin.time.Duration)`, `SinceFormat.span(since, now: kotlin.time.Instant)`. `LastChange` and `EntityState.changedAt` are already `kotlin.time`, so the builder's `toJava*` conversions go away.
- **The sheet's "Since 08:42" line is the only platform split.** `@Composable internal expect fun sinceDateText(at: Instant, now: Instant): String`. Android's actual calls today's `sinceDate(...)` (kept, unchanged, in `androidMain`, with `DateFormat.getBestDateTimePattern` and `is24HourFormat`). iOS's actual calls an iosMain `sinceDate(at, now, locale, timeZoneId)` that asks `NSDateFormatter.dateFormatFromTemplate` for the same skeletons with `j` in place of `H`/`h`, so the locale picks the clock.
- **"Now" ticks in common.** `:core:designsystem` gains `@Composable fun rememberCurrentTime(): State<kotlin.time.Instant>` in commonMain (`Clock.System.now()`, then every 60 s). Android's `rememberNow()` (java `Instant`) stays for `:feature:alerts` until Phase 6.
- **The tab status follows the profile.** `MainTabsViewModel` takes the profile flow and an `EntitySource` provider, and re-picks the source on every profile change (`flatMapLatest`). Both hosts keep their root-scoped `koinViewModel<MainTabsViewModel>()`; only the module definition changes.
- **Label retry:** a failed load retries after 1 s, 2 s and 4 s (four attempts in all). After the last failure it keeps today's behaviour: the previous labels (or none) are recorded for that locale and nothing retries until the language changes.
- **Formatter caching (iOS):** `NSNumberFormatter` keyed by (locale identifier, min, max, grouping) and `NSDateFormatter` keyed by (pattern, locale identifier, resolved time-zone name), each in a copy-on-write map behind a `kotlin.concurrent.AtomicReference`. A race can build a formatter twice; that's harmless. Each map is cleared when it passes 64 entries.
- **One Reduce Motion observer (iOS):** an `internal object ReduceMotion` in designsystem's iosMain holds a `StateFlow<Boolean>` (true = reduce) and registers one `NSNotificationCenter` observer the first time it's read, for the app's lifetime. `animationsEnabled()` collects it. Android is unchanged.
- **iOS bar:** `IOS_TABS = HOME, ENERGY, SECURITY`. Security is a side tab like Energy: it sits on Home, Home pops it, and Energy replaces it.

## Review Focus

1. **French "since" times on the iPhone** (today, within six days, older): `11:00`, `mer. 11:00`, `15 janv. 11:00`, in a 24-hour clock for `fr_FR`. Task 4 `sinceDateFollowsTheLocale` (iosTest).
2. **"Set up again" or a switch to the demo, then a different house:** the shield and bolt light for the new house, not the old one, on both hosts. Task 3 `statusFollowsTheProfilesSource`.
3. **Plural and apostrophe strings after the move:** "1 ouvert"/"2 ouverts", and "Aucune centrale d'alarme" with a plain apostrophe. Task 4 `apostrophesAreNotEscaped`, `countsUsePlurals`.
4. **Tapping Security from Energy, then Home, on iOS:** the stack goes `[Home, Energy]` → `[Home, Security]` → `[Home]`, and Back on Security returns to Home. Task 5 `securityIsASideTab`.
5. **A label load that fails on the first launch** (resources not readable yet): the labels still arrive without a language change, and a load that keeps failing stops after four tries. Task 1 `aFailedFirstLoadIsRetried`, `retriesStopAfterFourAttempts`.

---

### Task 1: Retry a failed first label load

**Files:**
- Modify: `feature/home/src/commonMain/kotlin/com/gekkotron/nidoo/feature/home/di/PreloadedLabelText.kt`
- Test: `feature/home/src/commonTest/kotlin/com/gekkotron/nidoo/feature/home/di/PreloadedLabelTextTest.kt`

**Interfaces:**
- Produces: `PreloadedLabelText(scope, locale = ::systemLocale, load = ::loadEntityLabels, retryDelays: List<Duration> = listOf(1.seconds, 2.seconds, 4.seconds))`. Its public behaviour is otherwise unchanged (`invoke`, `updates`, `close`). `HomeModule` doesn't change.

- [ ] **Step 1: Write the failing tests** (virtual time in `runTest`, scope = `backgroundScope` or the test scope as the existing tests do):

```kotlin
@Test fun aFailedFirstLoadIsRetried()     // load throws once then returns mapOf("door" to "Porte"); after advanceUntilIdle, invoke("Door") == "Porte" and load ran 2 times
@Test fun retriesStopAfterFourAttempts()  // load always throws; after advanceUntilIdle load ran 4 times, invoke("Door") == null; another invoke + advanceUntilIdle → still 4
```

- [ ] **Step 2:** Run `./gradlew :feature:home:testAndroidHostTest --tests '*PreloadedLabelTextTest*'`. Expected: FAIL (`aFailedFirstLoadIsRetried`: load ran once).
- [ ] **Step 3:** Inside the launched load, try `load()`; on a non-cancellation failure, `delay` the next entry of `retryDelays` and try again. Record `Loaded(wanted, previous-or-empty)` only after the last attempt fails. The cancellation path stays as it is.
- [ ] **Step 4:** Run `./gradlew :feature:home:testAndroidHostTest`. Expected: PASS (the existing `PreloadedLabelTextTest` cases too).
- [ ] **Step 5:** Commit `fix(home): retry a failed label load before giving up`.

### Task 2: Cached iOS formatters and one Reduce Motion observer

**Files:**
- Modify: `core/format/src/iosMain/kotlin/com/gekkotron/nidoo/format/{Numbers,Dates}.ios.kt`
- Create: `core/format/src/iosMain/kotlin/com/gekkotron/nidoo/format/FormatterCache.kt`, `core/format/src/iosTest/kotlin/com/gekkotron/nidoo/format/FormatterCacheTest.kt`
- Modify: `core/designsystem/src/iosMain/kotlin/com/gekkotron/nidoo/designsystem/Motion.ios.kt`
- Create: `core/designsystem/src/iosTest/kotlin/com/gekkotron/nidoo/designsystem/ReduceMotionTest.kt`

**Interfaces** (all iosMain, `internal`):
- `internal class FormatterCache<K : Any, V : Any>(private val limit: Int = 64) { fun getOrPut(key: K, create: () -> V): V }`
- `internal fun numberFormatter(locale: FormatLocale, minFraction: Int, maxFraction: Int, grouping: Boolean): NSNumberFormatter` and `internal fun dateFormatter(pattern: String, locale: FormatLocale, timeZoneId: String?): NSDateFormatter`, which `formatNumber` and `formatEpochSeconds` call. The date key uses the resolved zone's `name`, so a null id follows the device's zone when it changes.
- `internal object ReduceMotion { val reduce: StateFlow<Boolean> }`, seeded from `UIAccessibilityIsReduceMotionEnabled()`. Its observer is registered once, on the main queue, and never removed.
- `animationsEnabled()` keeps its signature: `true` in inspection mode, else `!ReduceMotion.reduce.collectAsState().value`.

- [ ] **Step 1: Write the failing tests** (iosTest):

```kotlin
// FormatterCacheTest
@Test fun sameKeySameFormatter()          // numberFormatter(fr, 1, 1, true) === numberFormatter(fr, 1, 1, true); dateFormatter("HH:mm", fr, "Europe/Paris") === the same call
@Test fun differentKeysDifferentFormatters() // numberFormatter(fr, 1, 1, true) !== numberFormatter(fr, 2, 2, true)
@Test fun cacheIsBounded()                // FormatterCache<Int, Any>(limit = 2): put keys 1, 2, 3; getOrPut(1) { new } builds again
// ReduceMotionTest
@Test fun oneSharedState()                // ReduceMotion.reduce === ReduceMotion.reduce, and its value == UIAccessibilityIsReduceMotionEnabled()
```

- [ ] **Step 2:** Run `./gradlew :core:format:compileTestKotlinIosSimulatorArm64 :core:designsystem:compileTestKotlinIosSimulatorArm64`. Expected: FAIL, unresolved references.
- [ ] **Step 3:** Implement the cache and the observer. `FormatTest` (commonTest) must keep its outputs.
- [ ] **Step 4:** Run `./gradlew :core:format:allTests :core:designsystem:testAndroidHostTest :core:format:compileTestKotlinIosSimulatorArm64 :core:designsystem:compileTestKotlinIosSimulatorArm64`. Expected: BUILD SUCCESSFUL (iOS tests themselves run in CI).
- [ ] **Step 5:** Commit `perf(ios): cache Foundation formatters; one Reduce Motion observer`.

### Task 3: The tab status follows the profile

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/tabs/MainTabsViewModel.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/di/MainTabsModule.kt`
- Create: `shared/src/commonTest/kotlin/com/gekkotron/nidoo/shared/tabs/MainTabsViewModelTest.kt`, `shared/src/commonTest/kotlin/com/gekkotron/nidoo/shared/FakeEntitySource.kt` (copy of `feature/home/src/androidHostTest/.../FakeEntitySource.kt`, trimmed to what the test needs)

**Interfaces:**
- Produces:
  - `class MainTabsViewModel(profiles: Flow<ProfileState>, source: () -> EntitySource) : ViewModel` with `val status: StateFlow<TabStatus>`: `profiles.map { (it as? ProfileState.Ready)?.profile }.distinctUntilChanged().flatMapLatest { val s = source(); combine(s.entities, s.states()) { … } }`, then `stateIn(viewModelScope, WhileSubscribed(5_000), TabStatus())`. A "set up again" passes through `None`, so an equal profile is re-picked too.
  - `mainTabsModule = module { viewModel { MainTabsViewModel(get<ProfileRepository>().state) { get<EntitySource>() } } }`. Android's Koin graph already bridges `ProfileRepository` and `EntitySource` from Hilt; iOS binds both.

- [ ] **Step 1: Write the failing test:**

```kotlin
// MainTabsViewModelTest (commonTest, Dispatchers.setMain(UnconfinedTestDispatcher()))
@Test fun statusFollowsTheProfilesSource()
// profiles = MutableStateFlow(Ready(A)); source = { if (profiles.value == Ready(A)) armedSource else quietSource }
// collect status → alarmArmed == true; profiles.value = Ready(B) → alarmArmed == false; clear the store inside runTest
```

- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest --tests '*MainTabsViewModelTest*'`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement the new constructor and module.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest`. Expected: PASS (`TabStatusTest`, `SharedModuleTest`, `NidooAppTest`, Android's `MainTabsTest` and `AutomationsTabVisibilityTest`).
- [ ] **Step 5:** Commit `fix(tabs): the shield and bolt follow a profile switch`.

### Task 4: Security on Compose Multiplatform with Koin

**Files:**
- Move: `feature/security/src/main/kotlin/**` → `src/commonMain/kotlin/**`; `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`); `src/test/**` → `src/androidHostTest/**` (with `robolectric.properties`), except `SinceFormatTest`, which moves to commonTest on `kotlin.time`.
- Create: `feature/security/src/commonMain/kotlin/com/gekkotron/nidoo/feature/security/di/SecurityModule.kt`; `feature/security/src/{androidMain,iosMain}/kotlin/com/gekkotron/nidoo/feature/security/ui/SinceDate.{android,ios}.kt`; `feature/security/src/androidHostTest/kotlin/com/gekkotron/nidoo/feature/security/StringsTest.kt` (as Energy's); `feature/security/src/iosTest/kotlin/com/gekkotron/nidoo/feature/security/ui/SinceDateIosTest.kt`; `core/designsystem/src/commonMain/kotlin/com/gekkotron/nidoo/designsystem/CurrentTime.kt`.
- Modify: `feature/security/build.gradle.kts` (as `feature/energy/build.gradle.kts`: `nidoo.kmp.library` + `nidoo.kmp.compose`, `packageOfResClass = "com.gekkotron.nidoo.feature.security.resources"`, plus `:core:format`), `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt` (add `securityModule`), `.github/workflows/ci.yml` (`:feature:security:test` → `:feature:security:testAndroidHostTest`; add `:feature:security:iosSimulatorArm64Test` to the iOS job).

**Interfaces:**
- Consumes: `rememberCurrentTime()` (below), `currentFormatLocale()`, `animationsEnabled()`.
- Produces:
  - `val securityModule: Module = module { viewModel { SecurityViewModel(source = get(), dispatcher = Dispatchers.Default, history = get()) } }`. `@HiltViewModel` and the `@Inject` constructor go away.
  - `SecurityRoute(...)` keeps every parameter; its default becomes `viewModel: SecurityViewModel = koinViewModel()`. Snackbar texts resolve with `getString(Res.string.…)` inside the effect, and `saveFailed` the same way inside its `launch`.
  - Models: `Since.At(val at: Instant)`, `Since.Over(val window: Duration)` (`kotlin.time`); `SinceFormat.span(since: Since, now: Instant): SinceSpan` with `SinceSpan.Over(days = window.inWholeDays)` and seconds `= (now - at).inWholeSeconds`, the same thresholds.
  - `SecurityScreen(..., now: Instant = rememberCurrentTime().value, ...)`, with `kotlin.time.Instant`.
  - `@Composable internal expect fun sinceDateText(at: Instant, now: Instant): String`. The Android actual is `sinceDate(at.toJavaInstant(), now.toJavaInstant(), ZoneId.systemDefault(), LocalConfiguration.current.locales[0], DateFormat.is24HourFormat(LocalContext.current))`, with today's `sinceDate` moved unchanged to `androidMain`. The iOS actual is `sinceDate(at, now, currentFormatLocale(), null)`.
  - iosMain: `internal fun sinceDate(at: Instant, now: Instant, locale: FormatLocale, timeZoneId: String?): String`. It picks the skeleton `jm` (same day), `EEEjm` (up to 6 days back), else `dMMMjm`, using an `NSCalendar` in that zone for the day difference. The skeleton goes through `NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, NSLocale(locale.identifier))`, and `formatEpochSeconds` formats with that pattern.
  - `:core:designsystem` commonMain: `@Composable fun rememberCurrentTime(): State<Instant>` (`produceState(Clock.System.now()) { while (true) { delay(60_000); value = Clock.System.now() } }`).

- [ ] **Step 1: Write the failing tests:**

```kotlin
// StringsTest (androidHostTest, Robolectric, French locale as Energy's)
@Test fun apostrophesAreNotEscaped()  // getString(Res.string.security_no_panel) == "Aucune centrale d'alarme. Publiez un alarm_control_panel MQTT pour la voir ici."
@Test fun countsUsePlurals()          // getPluralString(Res.plurals.security_count_opening, 1, 1) == "1 ouvert"; (…, 2, 2) == "2 ouverts"
// SinceDateIosTest (iosTest; Europe/Paris; now = 2025-01-15T10:00:00Z, a Wednesday)
@Test fun sinceDateFollowsTheLocale() // fr_FR: same day 09:00Z → "10:00"; 2025-01-13T10:00Z → starts with "lun." and ends with "11:00"; 2025-01-01T10:00Z → contains "1 janv." and "11:00"
// SinceFormatTest (commonTest): the existing cases, rewritten on kotlin.time
```

- [ ] **Step 2:** Run `./gradlew :feature:security:test`. Expected: FAIL (StringsTest doesn't compile: no `Res` yet).
- [ ] **Step 3:** Move the module and convert strings, plurals, Hilt and time types. `grep -rnE '^import (java|javax|android\.|dagger)' feature/security/src/commonMain` must print nothing. The moved Android tests change only where `java.time` values meet the new `kotlin.time` types (`toKotlinInstant()`, `toKotlinDuration()`).
- [ ] **Step 4:** Run `./gradlew :feature:security:testAndroidHostTest :feature:security:compileTestKotlinIosSimulatorArm64 :core:designsystem:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS. Every moved test stays green, including `SecurityScreenTest`, `SinceDateTest` and `SinceScreenshotTest`'s renders.
- [ ] **Step 5:** Commit `feat(security): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 5: The Security tab on iOS

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/{ShellBackStack,NidooApp}.kt`, `shared/build.gradle.kts` (add `:feature:security`), `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt` (add `securityModule`).
- Test: `shared/src/commonTest/.../ShellBackStackTest.kt`, `shared/src/androidHostTest/.../NidooAppTest.kt`, `shared/src/iosTest/.../di/IosModuleTest.kt` (resolves `SecurityViewModel`).

**Interfaces:**
- Consumes: Task 4's `SecurityRoute`, `securityModule`; Home's `CustomizeEntitySheet(id, onDismiss, onSaveFailed)`.
- Produces (in `ShellBackStack.kt`):
  - `internal data object SecurityKey : NavKey`
  - `IOS_TABS = listOf(MainTab.HOME, MainTab.ENERGY, MainTab.SECURITY)`, and `SIDE_TABS = setOf(EnergyKey, SecurityKey)`
  - `showsTabBar(top)` is true for `HomeKey`, `EnergyKey` and `SecurityKey`; `mainTab()`/`key()` map `SECURITY` ↔ `SecurityKey`.
  - `NidooApp`: `entry<SecurityKey> { SecurityRoute(windowInsets = tabInsets, customizeSheet = { id, onDismiss, onSaveFailed -> CustomizeEntitySheet(id, onDismiss, onSaveFailed) }, onOpenHistory = { push(backStack, HistoryKey(HistoryTarget.Entity(it).encode())) }, onOpenConnectionSettings = openConnectionSettings, route = route, onRouteChange = shell::setRoute) }`, with no alerts section or effects.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// ShellBackStackTest (commonTest)
@Test fun securityIsASideTab()       // [Home] +SECURITY → [Home, Security]; +SECURITY → same; +ENERGY → [Home, Energy]; +SECURITY → [Home, Security]; +HOME → [Home]
@Test fun securityShowsTheTabBar()   // showsTabBar(SecurityKey) true; SecurityKey.mainTab() == MainTab.SECURITY
// NidooAppTest (androidHostTest, Robolectric)
@Test fun securityTabOpensSecurity() // the bar shows "Security"; tap it → the empty-state text (security_empty, en) shows; tap "Home" → Home again
```

- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the shell changes.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64`, then `xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -destination 'generic/platform=iOS Simulator' build` (regenerate with XcodeGen first if the project is missing). Expected: PASS and BUILD SUCCEEDED.
- [ ] **Step 5:** Commit `feat(ios): the Security tab`.

### Task 6: Docs, memory and the PR

**Files:**
- Modify: the spec's §9 decision log: a "2026-11-27, Phase 5B (Security)" entry that sums up the rulings above.
- Modify: the project memory file `ios-compose-multiplatform.md` (5B PR number, branch, Phase 5 done, next: Phase 6).
- PR body: summary and test plan, plus an iPhone checklist: the three tabs, the shield lit while armed, arming and disarming a panel, a door action's confirmation dialog, a sensor sheet's "Depuis …" line in French, edge swipe from History back to Security, Reduce Motion on/off while the app runs, and "set up again" with a different house (the shield follows).

- [ ] **Step 1:** Run `./gradlew check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** Make the doc and memory edits, then commit `docs: iOS phase 5B decision log`.
- [ ] **Step 3:** Push and open the PR against `master` (Gekkotron token, per memory), then watch CI and fix failures until green.
