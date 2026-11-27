# iOS Phase 6A — Routines (the Automations tab) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone gets the Routines tab: the hub's flows with their on/off switches, room chips, and the wake-up alarms with their edit sheet. The iOS bar then shows Home, Energy, Security and Routines, like Android. One rule picks the tabs for both hosts, and the demo hides Routines on both. The 5B leftover lands too: iOS dates follow the phone's 24-hour setting. The Android app behaves as it does today.

**Architecture:** `:core:format` gains locale-aware weekday helpers, and its iOS half stops dropping the user's clock setting (Task 1). `:feature:automations` then moves to Compose Multiplatform with Koin, the way 5B moved Security (Task 2). Its weekdays switch from `java.time.DayOfWeek` to the hub's 0 = Sunday numbering, and the time picker's 12/24-hour choice is the one platform split. Last, a common `mainTabsFor(profile)` picks the tabs for both hosts, and the iOS shell gets the Routines entry (Task 3).

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1 (CMP resources, material3 `TimePicker`), JetBrains `navigation3-ui` 1.1.2, Koin 4.2.2.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§3, §4, §7.5, §7.6 phase 6, §9). Previous plan: `docs/superpowers/plans/2026-11-27-ios-phase5b-security.md` (same pattern). Ledger: `.superpowers/sdd/2026-11-27-ios-phase6a-routines/progress.md`.

## Phase 6 as PRs

Phase 6 is "Flows, voice, in-app alerts" (§7.6), plus merging the two nav hosts (§9, 4C). Five PRs, each with its own plan written once the previous PR is open, as in Phases 4 and 5:

| PR | Scope | Main risk |
|---|---|---|
| **6A Routines** (this plan) | `:feature:automations` on CMP + Koin; the Routines tab on iOS; one tab rule; iOS 24-hour fix | Small: the data path (`FlowsRepository`) is already in the iOS graph |
| 6B Voice | `:core:assist` (its `jvmMain` transport code to common), `:feature:voice` on CMP + Koin; iOS `SFSpeechRecognizer` + `AVSpeechSynthesizer` behind the existing `SpeechCapture` / `SpeechAvailability` contracts; Home's `onOpenVoice` wired on iOS; `NSMicrophoneUsageDescription`, `NSSpeechRecognitionUsageDescription` | Speech APIs only testable on the phone; `VoiceActivity` (Android assistant entry) stays Android |
| 6C Alerts data | `:core:alerts` to KMP: Room KMP with the bundled SQLite driver (same database name, schema and migrations on Android), media fetch on Ktor, `HistorySync` run on foreground on iOS | Room KMP migration must keep Android installs upgrading in place (§9) |
| 6D In-app alerts | `:feature:alerts` on CMP + Koin: the Alerts screen, the detail sheet, Security's recent-alerts section on iOS; video via `AVPlayer` in `UIKitView`, images via Coil 3 + Ktor; push stays Android (§3) | Media3 vs AVPlayer split |
| 6E One nav host | Android's `NidooNavHost` replaced by the common `NavDisplay` in `:shared`; one set of keys and back-stack helpers; Android-only entries (notification step, licences, alert deep links) through a platform slot | Android navigation regressions (deep links, process-death restore of the Serializable keys) |

Removing Hilt is not in Phase 6 (Hilt still owns Android's transport stack); it gets its own decision after 6E.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place" (§9): no DataStore, key or profile-format change in this PR.
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- "Only the iOS behaviours that matter for daily use get polish (swipe-back, safe areas, keyboard)." (§3) No other iOS restyling.
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), positional placeholders (`%1$s`), a literal percent is a single `%`.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`. In commonTest use `kotlin.test.Test`.
- commonTest ViewModels are cleared (`ViewModelStore.clear()`) inside `runTest`, before `resetMain`.
- Anything holding `EntitySource` is a Koin `factory` or a ViewModel, never a `single`.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Branch: `feat/ios-phase6a-routines`, stacked on `feat/ios-phase5b-security` while PR #62 is open (rebase onto `master` once it merges); the PR targets `master`.

## Rulings made while planning

- **Weekdays use the hub's numbering everywhere: 0 = Sunday … 6 = Saturday.** `weekdayText(days: List<Int>, firstDay: Int, shortName: (Int) -> String)` drops `DayOfWeek`. The editor's chips run `(firstDay + i) % 7` for `i` in `0..6`.
- **Locale facts live in `:core:format`:** `expect fun firstDayOfWeek(locale: FormatLocale): Int` (0 = Sunday) and `expect fun shortWeekdayName(day: Int, locale: FormatLocale): String`. JVM: `WeekFields.of(locale).firstDayOfWeek.value % 7` and `DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(TextStyle.SHORT, locale)` (today's output, unchanged). iOS: an `NSCalendar` (Gregorian) with that locale's `firstWeekday - 1`, and `NSDateFormatter.shortWeekdaySymbols[day]` (Foundation's index 0 is Sunday too).
- **The alarm summary keeps a fixed `HH:mm`** as on Android today (`"07:05 · Mon–Fri"`), built with `padStart(2, '0')` instead of JVM `String.format`.
- **The time picker's clock is the one platform split:** `@Composable expect fun is24HourClock(): Boolean` in `:core:designsystem`. Android: `DateFormat.is24HourFormat(LocalContext.current)` (today's call). iOS: `uses24HourClock(NSLocale.currentLocale)`, where `internal fun uses24HourClock(locale: NSLocale): Boolean` is true when `NSDateFormatter.dateFormatFromTemplate("j", 0u, locale)` has no `a`.
- **iOS dates honour the 24-hour switch (5B leftover).** `NSLocale(localeIdentifier)` drops the user's clock override; only `NSLocale.currentLocale` carries it. `:core:format` iosMain gets `internal fun localeFor(identifier: String, current: NSLocale): NSLocale` (returns `current` when the identifiers match, else `NSLocale(identifier)`), and `FormatLocale.toNSLocale()` becomes public and calls `localeFor(identifier, NSLocale.currentLocale)`. Security's `sinceDate` asks `dateFormatFromTemplate` with `locale.toNSLocale()` instead of `NSLocale(locale.identifier)`. A setting changed while the app runs shows up for new patterns; already-cached formatters keep their pattern, which is fine (the pattern is part of the cache key).
- **Messages in the UI state are CMP `StringResource`s:** `AutomationsUiState.toast: StringResource?` and `AlarmEditor.error: StringResource?` replace `@StringRes Int?`. The screen resolves the toast with `getString(res)` inside its `launch`, as 5B's Security does.
- **One tab rule for both hosts:** `fun mainTabsFor(profile: ProfileState): List<MainTab>` in `:shared` (`tabs/MainTab.kt`) returns `HOME, ENERGY, SECURITY, AUTOMATIONS`, minus `AUTOMATIONS` when the profile is `Ready` with `ConnectionProfile.Demo` (the demo has no hub; today's Android rule, `Loading` and `None` keep the tab). Android's `NidooNavHost` and iOS's `NidooApp` both call it; `IOS_TABS` goes away. Both hosts keep their "leave Routines when the demo turns on" effect.
- **iOS keys:** `AutomationsKey` joins `ShellBackStack.kt`; Routines is a side tab like Energy and Security.
- **Tests:** `AutomationsViewModelTest` and `AutomationsScreenTest` move to `androidHostTest` with `R.string` → `Res.string` and nothing else; `WeekdayTextTest` moves to commonTest on the new signature, so it also runs on iOS.

## Review Focus

1. **The demo house:** no Routines tab on either host, and turning the demo on while Routines is open lands on Home. Task 3 `demoHidesRoutines`, `routinesTabIsHiddenUnderTheDemo`.
2. **French alarms on the iPhone:** a weekday alarm reads `07:05 · lun.–ven.` (Monday first), while `en_US` starts the week on Sunday (`Sun, Sat` for a weekend alarm). Task 1 `weekStartsByLocale`, `shortNamesFollowTheLocale`; Task 2 `weekdayText` cases.
3. **The 12/24-hour clock on iOS:** `en_US` gets an AM/PM picker and `fr_FR` a 24-hour one; the Security sheet's "Since" line follows the phone's 24-hour switch. Task 1 `currentLocaleKeepsItsOverrides`, Task 2 `clockFollowsTheLocale`.
4. **A save the hub refuses (404):** the sheet shows "Mettez à jour votre hub Nidoo pour modifier les réveils depuis l'app" with a plain apostrophe. Task 2 `apostrophesAreNotEscaped`.
5. **A failed toggle:** the snackbar shows the translated text once and the toast is acknowledged. Task 2 `failedToggleShowsItsSnackbar`.

---

### Task 1: Weekday helpers and the iOS clock setting in `:core:format`

**Files:**
- Create: `core/format/src/commonMain/kotlin/com/gekkotron/nidoo/format/Weekdays.kt`, `core/format/src/jvmMain/kotlin/com/gekkotron/nidoo/format/Weekdays.jvm.kt`, `core/format/src/iosMain/kotlin/com/gekkotron/nidoo/format/Weekdays.ios.kt`
- Modify: `core/format/src/iosMain/kotlin/com/gekkotron/nidoo/format/FormatLocale.ios.kt`, `feature/security/src/iosMain/kotlin/com/gekkotron/nidoo/feature/security/ui/SinceDate.ios.kt`
- Test: `core/format/src/commonTest/kotlin/com/gekkotron/nidoo/format/WeekdaysTest.kt`, `core/format/src/iosTest/kotlin/com/gekkotron/nidoo/format/LocaleForTest.kt`

**Interfaces:**
- Produces:
  - `expect fun firstDayOfWeek(locale: FormatLocale): Int` (0 = Sunday … 6 = Saturday)
  - `expect fun shortWeekdayName(day: Int, locale: FormatLocale): String` (`day` 0 = Sunday)
  - iosMain: `fun FormatLocale.toNSLocale(): NSLocale` (now public), `internal fun localeFor(identifier: String, current: NSLocale): NSLocale`

- [ ] **Step 1: Write the failing tests**

```kotlin
// WeekdaysTest (commonTest: runs on JVM and iOS)
@Test fun weekStartsByLocale()        // firstDayOfWeek(formatLocale("fr-FR")) == 1; firstDayOfWeek(formatLocale("en-US")) == 0
@Test fun shortNamesFollowTheLocale() // shortWeekdayName(1, formatLocale("en-US")) == "Mon"; shortWeekdayName(0, formatLocale("fr-FR")) == "dim."; (0..6).map { shortWeekdayName(it, en) }.toSet().size == 7
// LocaleForTest (iosTest)
@Test fun currentLocaleKeepsItsOverrides() // val cur = NSLocale.currentLocale; assertSame(cur, localeFor(cur.localeIdentifier, cur))
@Test fun otherLocalesAreBuilt()           // localeFor("fr_FR", NSLocale(localeIdentifier = "en_US")).localeIdentifier == "fr_FR"
```

- [ ] **Step 2:** Run `./gradlew :core:format:allTests :core:format:compileTestKotlinIosSimulatorArm64`. Expected: FAIL, unresolved references.
- [ ] **Step 3:** Implement per the rulings (iOS weekday formatter through the existing `FormatterCache` if it fits, else a plain `NSDateFormatter`). Switch `sinceDate` to `locale.toNSLocale()`.
- [ ] **Step 4:** Run `./gradlew :core:format:allTests :core:format:compileTestKotlinIosSimulatorArm64 :feature:security:compileKotlinIosSimulatorArm64`. Expected: BUILD SUCCESSFUL (iOS tests run in CI).
- [ ] **Step 5:** Commit `feat(format): locale weekdays; iOS dates follow the 24-hour setting`.

### Task 2: Automations on Compose Multiplatform with Koin

**Files:**
- Move: `feature/automations/src/main/kotlin/**` → `src/commonMain/kotlin/**`; `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`, six French strings); `src/test/**` → `src/androidHostTest/**` (with `robolectric.properties`), except `WeekdayTextTest`, which moves to commonTest.
- Create: `feature/automations/src/commonMain/kotlin/com/gekkotron/nidoo/feature/automations/di/AutomationsModule.kt`; `feature/automations/src/androidHostTest/kotlin/com/gekkotron/nidoo/feature/automations/StringsTest.kt` (as Energy's); `core/designsystem/src/{commonMain,androidMain,iosMain}/kotlin/com/gekkotron/nidoo/designsystem/Clock{,.android,.ios}.kt`; `core/designsystem/src/iosTest/kotlin/com/gekkotron/nidoo/designsystem/ClockTest.kt`.
- Modify: `feature/automations/build.gradle.kts` (as `feature/security/build.gradle.kts`: `nidoo.kmp.library` + `nidoo.kmp.compose`, namespace `com.gekkotron.nidoo.feature.automations`, `packageOfResClass = "com.gekkotron.nidoo.feature.automations.resources"`, commonMain deps `:core:flows`, `:core:source`, `:core:designsystem`, `:core:format`, CMP lifecycle, Koin); `app/src/main/kotlin/com/gekkotron/nidoo/di/KoinBridge.kt` (add `automationsModule`); `app/src/test/kotlin/com/gekkotron/nidoo/di/KoinBridgeTest.kt` (`koin.get<AutomationsViewModel>()` next to `SecurityViewModel`); `.github/workflows/ci.yml` (`:feature:automations:test` → `:feature:automations:testAndroidHostTest`; add `:feature:automations:iosSimulatorArm64Test` to the iOS job).

**Interfaces:**
- Consumes: Task 1's `firstDayOfWeek`, `shortWeekdayName`; designsystem's `currentFormatLocale()`.
- Produces:
  - `val automationsModule: Module = module { viewModel { AutomationsViewModel(get(), get<EntitySource>()) } }`. `@HiltViewModel`/`@Inject` go; the public `(repo, source)` and internal `(repo, areas, link, dispatcher)` constructors stay.
  - `AutomationsRoute(windowInsets, viewModel: AutomationsViewModel = koinViewModel())`, otherwise unchanged.
  - `AutomationsUiState.toast: StringResource?`, `AlarmEditor.error: StringResource?`.
  - `fun weekdayText(days: List<Int>, firstDay: Int, shortName: (Int) -> String): String` (same ranges and separators).
  - `:core:designsystem`: `@Composable expect fun is24HourClock(): Boolean`; iosMain `internal fun uses24HourClock(locale: NSLocale): Boolean`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// StringsTest (androidHostTest, Robolectric, French locale as Energy's)
@Test fun apostrophesAreNotEscaped() // getString(Res.string.wake_up_hub_outdated) == "Mettez à jour votre hub Nidoo pour modifier les réveils depuis l'app"
// WeekdayTextTest (commonTest): today's five cases on Int days, with shortName = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")::get
//   weekdaysAreARange: ([1,2,3,4,5], first 1) == "Mon–Fri"; weekendFollowsTheFirstDay: ([0,6], 1) == "Sat, Sun" and ([0,6], 0) == "Sun, Sat"; …
// ClockTest (designsystem iosTest)
@Test fun clockFollowsTheLocale()    // uses24HourClock(NSLocale("en_US")) == false; uses24HourClock(NSLocale("fr_FR")) == true
// AutomationsScreenTest (androidHostTest), add if no existing case covers it
@Test fun failedToggleShowsItsSnackbar() // state.toast = Res.string.automations_toggle_failed → "Could not update the automation" shows once; onToastShown called once
```

- [ ] **Step 2:** Run `./gradlew :feature:automations:test`. Expected: FAIL (StringsTest doesn't compile: no `Res` yet).
- [ ] **Step 3:** Move the module and convert strings, Hilt, `DayOfWeek`/`WeekFields`/`Locale` and `String.format` per the rulings. `grep -rnE '^import (java|javax|android\.|dagger|androidx\.hilt|androidx\.annotation)' feature/automations/src/commonMain` must print nothing. The moved Android tests change only `R.string` → `Res.string`.
- [ ] **Step 4:** Run `./gradlew :feature:automations:testAndroidHostTest :feature:automations:allTests :feature:automations:compileTestKotlinIosSimulatorArm64 :core:designsystem:testAndroidHostTest :core:designsystem:compileTestKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS. Every moved test stays green, including `AutomationsScreenTest`'s renders.
- [ ] **Step 5:** Commit `feat(automations): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 3: One tab rule, and the Routines tab on iOS

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/tabs/MainTab.kt` (add `mainTabsFor`), `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/{ShellBackStack,NidooApp}.kt`, `shared/build.gradle.kts` (add `:feature:automations`), `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt` (add `automationsModule`), `app/src/main/kotlin/com/gekkotron/nidoo/navigation/NidooNavHost.kt` (call `mainTabsFor(profile)`).
- Test: `shared/src/commonTest/.../tabs/MainTabsForTest.kt` (create), `shared/src/commonTest/.../ShellBackStackTest.kt`, `shared/src/androidHostTest/.../NidooAppTest.kt`, `shared/src/iosTest/.../di/IosModuleTest.kt` (resolves `AutomationsViewModel`). Android's `AutomationsTabVisibilityTest` must pass unchanged.

**Interfaces:**
- Consumes: Task 2's `AutomationsRoute`, `automationsModule`.
- Produces:
  - `fun mainTabsFor(profile: ProfileState): List<MainTab>` (see rulings).
  - `ShellBackStack.kt`: `internal data object AutomationsKey : NavKey`; `SIDE_TABS = setOf(EnergyKey, SecurityKey, AutomationsKey)`; `showsTabBar` true for it; `mainTab()`/`key()` map `AUTOMATIONS` ↔ `AutomationsKey`. `IOS_TABS` is removed.
  - `NidooApp`: `MainTabBar(mainTabsFor(profile), …)`, `entry<AutomationsKey> { AutomationsRoute(windowInsets = tabInsets) }`, and `LaunchedEffect(showAutomations, top) { if (!showAutomations && top == AutomationsKey) selectTab(backStack, MainTab.HOME) }` with `showAutomations = MainTab.AUTOMATIONS in tabs`. `Screens` takes the profile it needs from `shell.profile`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// MainTabsForTest (commonTest)
@Test fun everyTabForAHub()     // mainTabsFor(Ready(a Direct profile)) == listOf(HOME, ENERGY, SECURITY, AUTOMATIONS); same for Loading and None
@Test fun demoHidesRoutines()   // mainTabsFor(Ready(ConnectionProfile.Demo)) == listOf(HOME, ENERGY, SECURITY)
// ShellBackStackTest (commonTest)
@Test fun routinesIsASideTab()  // [Home] +AUTOMATIONS → [Home, Automations]; +SECURITY → [Home, Security]; +AUTOMATIONS → [Home, Automations]; +HOME → [Home]
@Test fun routinesShowsTheTabBar() // showsTabBar(AutomationsKey); AutomationsKey.mainTab() == MainTab.AUTOMATIONS
// NidooAppTest (androidHostTest; its iOS-module setup runs the demo)
@Test fun routinesTabIsHiddenUnderTheDemo() // demoHome(); "Security" tab shows; no node with text "Routines"
```

- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement the rule, the iOS keys and entry, and switch Android's `NidooNavHost` to `mainTabsFor(profile)` (its `showAutomations` becomes `MainTab.AUTOMATIONS in tabs`).
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64`, then `xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -destination 'generic/platform=iOS Simulator' build` (regenerate with XcodeGen first if the project is missing). Expected: PASS and BUILD SUCCEEDED.
- [ ] **Step 5:** Commit `feat(ios): the Routines tab; one tab rule for both hosts`.

### Task 4: Docs, memory and the PR

**Files:**
- Modify: the spec's §9 decision log: a "2026-11-27, Phase 6A (Routines)" entry summing up the rulings above and the Phase 6 PR split.
- Modify: the project memory file `ios-compose-multiplatform.md` (Phase 6 split, 6A PR number and branch, next: 6B Voice).
- PR body: summary and test plan, plus an iPhone checklist: four tabs on a real hub and three under the demo; toggling a flow on the LAN and through the gateway; a failed toggle's snackbar (hub off); room chips; a wake-up alarm edited and saved in French (Monday-first chips, `lun.–ven.` summary); the time picker in a 12-hour region; the Security sheet's "Depuis …" line with the 24-hour switch off; edge swipe and Back from Routines to Home.

- [ ] **Step 1:** Run `./gradlew check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** Make the doc and memory edits, then commit `docs: iOS phase 6A decision log`.
- [ ] **Step 3:** Push and open the PR against `master` (Gekkotron token, per memory; note in the body that it stacks on #62 until that merges), then watch CI and fix failures until green.
