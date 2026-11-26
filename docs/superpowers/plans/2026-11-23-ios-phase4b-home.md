# iOS Phase 4B — Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone opens on Home, the same screen as on Android. It has rooms, favourites, groups, dashboards, the control and customize sheets, the Rooms step of a new setup, and history-backed tiles. The Android app behaves exactly as it does today.

**Architecture:** `:feature:home` moves to Compose Multiplatform (`commonMain`) with Koin, the same way 4A moved `:feature:onboarding`. A new `:core:format` covers what home took from `java.text`, `java.time` and `java.math`: locale numbers, local dates and times, accent folding and rounding. Its Android actual of `FormatLocale` is a typealias to `java.util.Locale`, so Android passes the same objects as today. `EnergyStats` and `EnergyFormat` (and the energy builder they need) leave `:feature:energy` for a new `:core:energy`. The history and flows HTTP clients become common, so iOS gets them over the Ktor LAN-or-gateway path. The iOS shell in `:shared` swaps 4A's house summary for the Rooms step and Home.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5, Compose Multiplatform 1.11.1 (resources, `ui-backhandler`), JetBrains lifecycle 2.10.0, Koin 4.2.2, Coil 3.5.0 (`coil-compose`, plus `coil-network-ktor3` on iOS), Ktor 3.6.1 (Darwin), Foundation `NSNumberFormatter`/`NSDateFormatter` on iOS.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§2 goal 2, §4, §7.4, §7.5, §7.6 phase 4, §9). Phase overview and 4A: `docs/superpowers/plans/2026-11-22-ios-phase4-first-version.md`. Ledger: `.superpowers/sdd/2026-11-23-ios-phase4b-home/progress.md`.

## Global Constraints

- "The Android app must never regress. Every step keeps `./gradlew check` and CI green." (§4)
- "Existing Android installs upgrade in place": no change to DataStore files, Keystore secrets or the entity cache (§9).
- "Each feature module switches to `koinViewModel()` in the same PR that moves it to `commonMain`." (§9)
- "No weaker security on iOS … no secret is logged." (§4) iOS logs (`NSLog`) carry parse warnings only, never URLs with keys or payloads.
- CMP strings: a plain `'` in `composeResources` XML (never `\'`), and positional placeholders (`%1$d`, `%1$s`); a bare `%d` renders unformatted.
- Kotlin/Native test names can't contain commas. KMP Android tests run with `testAndroidHostTest`; KMP JVM modules with `jvmTest`.
- iOS tests run in CI only (`-Pnidoo.iosTests` locally). Never boot a Simulator without the user's yes; `xcodebuild … -destination 'generic/platform=iOS Simulator' build` boots nothing.
- Keep the Android app on OkHttp 4.12 and HiveMQ. Ktor (Darwin) is iOS only.
- ci.yml task lists are `>-` folded scalars: a `#` line inside one is passed to Gradle, not a comment.
- Energy, History and Security screens are Phase 5. On iOS, Home's open-history actions are hidden and the dashboard's energy link does nothing.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`; the message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings made while planning

- **`FormatLocale` is an `expect class`**: `actual typealias FormatLocale = java.util.Locale` on JVM, so Android code and the ~75 test call sites that pass `Locale.US`/`Locale.FRANCE` don't change. iOS wraps an `NSLocale` identifier. The module turns on `-Xexpect-actual-classes`.
- **`Locale.ROOT` case mapping becomes Kotlin's own `lowercase()`/`titlecase()`.** They are locale-invariant, the same as `Locale.ROOT` on the JVM.
- **One accent fold for the three call sites.** `stripMarks` = NFD, then drop every `Char` in a mark category (Mn, Mc, Me). `SearchMatch` and `RoomCards` used `\p{Mn}` and `AreaNames` used `\p{M}`. They differ only on spacing marks (Indic scripts), not on French or English names.
- **`StepMath.round2` becomes `roundHalfUp(v, 2)`** in plain double arithmetic (half away from zero). It can differ from `BigDecimal(v).setScale(2, HALF_UP)` only on an exact binary tie at the third decimal. The UI shows one decimal, so the difference never shows.
- **`String.format(locale, "%.1f", v)` becomes `formatNumber(v, locale, 1, 1, grouping = false)`.** `NumberFormat` rounds half-even and `%.1f` rounds half-up, so a history tile can read 0.1 lower on an exact tie. That's accepted.
- **`LabelText` is preloaded from CMP strings.** CMP's `getString` is suspend and `LabelText` is called synchronously inside the builders. `PreloadedLabelText` loads every entity label for the current locale once, reloads when the locale changes, and exposes `updates` so the ViewModels rebuild when a load lands. Until the first load lands, labels show untranslated, for a few milliseconds at most.
- **`:core:energy` keeps the package `com.gekkotron.nidoo.feature.energy.presentation`** (as `:core:connection` kept its package in 4A), so `:feature:energy`'s UI changes no imports. Phase 5 decides where they finally live.
- **Home's tests move to `androidHostTest` unchanged** (Robolectric, `java.util.Locale`, screenshot PNGs). New common tests live in `commonTest` and also run on iOS in CI. Home's screenshot tests are Robolectric renders, not AGP screenshot suites, so they move with the rest.
- **Voice leaves Home.** `HomeRoute` takes `onOpenVoice: (() -> Unit)? = null`, and the mic shows only when voice is on *and* the callback is set. The Android nav host starts `VoiceActivity`; iOS passes nothing (voice is Phase 6).
- **`onOpenHistory` becomes nullable** (`((String) -> Unit)? = null`) on `HomeRoute`; null hides the open-history actions, as `EntityControlSheet`'s null already does. Android passes it; iOS doesn't (History is Phase 5).
- **iOS Settings until 4C**: the Home gear opens a temporary screen in `:shared` with Back and "Set up again". 4C replaces it with the real Settings.
- **The common HTTP route rule.** `httpRouteFor(profile, route, local, gateway)` holds the rule `mediaRouteFor` has today: a local probe unless the route is GATEWAY, and a gateway downloader unless it is DIRECT. Android's `mediaRouteFor` calls it with OkHttp links; iOS calls it with `NetworkTcpProbe` and the Darwin downloader.
- **4A leftovers folded in** (ledger "Final: minor (deferred)"): iOS shows the Rooms step (4A's `finishRooms` ruling ends), an iOS test resolves the whole iOS graph, the gateway publish timeout no longer swallows a caller's own timeout, French `InfoPlist.strings`, checklist item 6 reworded, the stray `constructor` keyword. "iOS Reduce Motion is read only once" (from the 4A plan) is fixed here, now that Home's animations reach iOS.

## Review Focus

1. **French formatting on the iPhone**: a decimal comma, a narrow no-break space as the thousands separator, "15 janv." on day-month axes, a 24-hour clock. Foundation's formatters, not the JVM's, do it there. Task 1 `frenchNumbersUseADecimalComma` and `dayMonthFollowsTheLocale` (commonTest, run on iOS in CI).
2. **Accented names on iOS**: searching "sejour" finds "Séjour", "Salle à manger" gets the dining icon and the same area id as on Android. Task 1 `accentsFold` (commonTest, iOS in CI); Task 3 keeps `SearchMatchTest`, `RoomIconTest` and `AreaNamesTest` green.
3. **The system language changes while Nidoo runs**: entity labels switch on the next rebuild, and a cold start doesn't leave English labels on screen after the strings load. Task 4 `reloadsWhenTheLocaleChanges`, `updatesFireWhenLabelsLoad`, and `homeRelabelsWhenLabelsLoad` (HomeViewModelTest).
4. **A flow id with a space, a `+`, a `/` or an accent** toggles the same flow on iOS as on Android (`URLEncoder` + `%20` today). Task 5 `encodesLikeUrlEncoder` (commonTest, iOS in CI).
5. **A new profile on iOS shows the Rooms step once, then Home.** A profile set up under 4A (step already closed) goes straight to Home, and "Set up again" from the temporary Settings returns to Welcome. Task 6 `aNewProfileShowsTheRoomsStepThenHome` and `settingsOffersSetUpAgain` (NidooAppTest).

---

### Task 1: `:core:format`

**Files:**
- Create: `core/format/build.gradle.kts`: `nidoo.kmp.jvm.library`; `kotlin { compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") } }`; commonTest gets what `core/history` commonTest uses.
- Create: `core/format/src/commonMain/kotlin/com/gekkotron/nidoo/format/{FormatLocale,Numbers,Dates,Text,Rounding}.kt`, the `jvmMain` actuals `{FormatLocale,Numbers,Dates,Text}.jvm.kt` and the `iosMain` actuals `{FormatLocale,Numbers,Dates,Text}.ios.kt`.
- Create: `core/format/src/commonTest/kotlin/com/gekkotron/nidoo/format/FormatTest.kt`.
- Modify: `settings.gradle.kts` (`include(":core:format")`), root `build.gradle.kts` (`kover(project(":core:format"))` if the root lists core modules), `.github/workflows/ci.yml`: the same entries `:core:history` has (core group test + `koverXmlReport`, codecov path, and `:core:format:iosSimulatorArm64Test` in the iOS job).

**Interfaces** (package `com.gekkotron.nidoo.format`):
- Produces:
  - `expect class FormatLocale`: JVM `actual typealias FormatLocale = java.util.Locale`. iOS `actual class FormatLocale(val identifier: String)` with `equals`/`hashCode` by identifier.
  - `expect fun systemLocale(): FormatLocale`: JVM `Locale.getDefault()`, iOS `NSLocale.currentLocale.localeIdentifier`.
  - `expect fun formatLocale(languageTag: String): FormatLocale`: JVM `Locale.forLanguageTag`, iOS `NSLocale.canonicalLocaleIdentifierFromString`.
  - `expect fun formatNumber(value: Double, locale: FormatLocale, minFraction: Int, maxFraction: Int, grouping: Boolean = true): String`: JVM `NumberFormat.getNumberInstance(locale)` with the fraction digits and `isGroupingUsed`. iOS `NSNumberFormatter` (decimal style, `usesGroupingSeparator`, `NSNumberFormatterRoundHalfEven`).
  - `expect fun formatEpochSeconds(seconds: Long, pattern: String, locale: FormatLocale, timeZoneId: String? = null): String`: JVM `DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)`; iOS `NSDateFormatter` (`dateFormat = pattern`, locale, time zone). A null zone means the system's.
  - `expect fun localHour(epochSeconds: Long, timeZoneId: String? = null): Int`.
  - `expect fun nfd(text: String): String`: JVM `Normalizer.normalize(text, NFD)`, iOS `NSString.decomposedStringWithCanonicalMapping`.
  - `fun stripMarks(text: String): String`: `nfd(text).filterNot { it.category in setOf(NON_SPACING_MARK, COMBINING_SPACING_MARK, ENCLOSING_MARK) }`.
  - `fun roundHalfUp(value: Double, decimals: Int): Double`: scale by `10^decimals`, then `floor(s + 0.5)` for s ≥ 0 and `-floor(-s + 0.5)` below.

- [ ] **Step 1: Write the failing test** `FormatTest.kt`. `NOON = 1768478400L` is 2026-01-15T12:00:00Z.

```kotlin
@Test fun englishNumbersGroupWithCommas()    // formatNumber(1234.5, formatLocale("en-US"), 0, 1) == "1,234.5"
@Test fun frenchNumbersUseADecimalComma()    // formatNumber(1234.5, formatLocale("fr-FR"), 0, 1) in setOf("1 234,5", "1 234,5")
@Test fun groupingCanBeOff()                 // formatNumber(1234.5, en-US, 1, 1, grouping = false) == "1234.5"
@Test fun fractionDigitsAreBounded()         // formatNumber(2.0, en-US, 0, 1) == "2"; formatNumber(2.34, en-US, 0, 1) == "2.3"
@Test fun dayMonthFollowsTheLocale()         // formatEpochSeconds(NOON, "d MMM", fr-FR, "UTC") == "15 janv."; en-US → "15 Jan"
@Test fun clockIsTwentyFourHour()            // formatEpochSeconds(NOON + 3 * 3600 + 5 * 60, "HH:mm", en-US, "Europe/Paris") == "16:05"
@Test fun localHourUsesTheZone()             // localHour(NOON, "Europe/Paris") == 13; localHour(NOON, "UTC") == 12
@Test fun accentsFold()                      // stripMarks("Salle à manger") == "Salle a manger"; stripMarks("Séjour Élodie Ç") == "Sejour Elodie C"
@Test fun halfUpRounding()                   // roundHalfUp(20.1 + 0.1, 2) == 20.2; roundHalfUp(0.125, 2) == 0.13; roundHalfUp(-0.125, 2) == -0.13; roundHalfUp(21.0, 2) == 21.0
```
- [ ] **Step 2:** Run `./gradlew :core:format:jvmTest`. Expected: FAIL (module missing).
- [ ] **Step 3:** Create the module, the expects and both sets of actuals, and the settings, kover and CI entries.
- [ ] **Step 4:** Run `./gradlew :core:format:jvmTest :core:format:compileTestKotlinIosSimulatorArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(format): :core:format for locale numbers, dates, accents and rounding`.

### Task 2: `:core:energy`, the energy figures Home shares with the Energy tab

**Files:**
- Create: `core/energy/build.gradle.kts`: `nidoo.kmp.jvm.library`; commonMain `api(:core:model)`, `api(:core:format)`, `implementation(:core:history)`, `implementation(:core:transport)` (for `LinkState`); jvmTest gets what `feature/energy`'s unit tests use (junit4, coroutines-test).
- Move (package unchanged, `com.gekkotron.nidoo.feature.energy.presentation`) from `feature/energy/src/main/kotlin/…/presentation/` to `core/energy/src/commonMain/kotlin/com/gekkotron/nidoo/feature/energy/presentation/`: `EnergyStats.kt`, `EnergyFormat.kt`, `EnergyFlowBuilder.kt`, `EnergyToday.kt`, `EnergyMeters.kt`, `EnergyModels.kt`. If the compiler asks for more, move the smallest closure and record it in the ledger. If that pulls in a ViewModel or a UI file, stop and report instead.
- Move tests: `{EnergyStats,EnergyFormat,EnergyFlowBuilder,EnergyToday,EnergyMeters}Test.kt` → `core/energy/src/jvmTest/kotlin/…/presentation/`, unchanged.
- Modify: `EnergyFormat.kt` (its private `number` calls `formatNumber`; the `locale` parameters become `FormatLocale`, which is still `java.util.Locale` on JVM). `feature/energy/build.gradle.kts` and `feature/home/build.gradle.kts` (`implementation(project(":core:energy"))`; home drops `:feature:energy`). `settings.gradle.kts`, root kover, and `ci.yml` (as Task 1, iOS job only if a commonTest exists).

**Interfaces:**
- Consumes: `formatNumber`, `FormatLocale` (Task 1).
- Produces: `EnergyStats`, `EnergyFormat` and the rest, unchanged in name and signature, now from `:core:energy`.

- [ ] **Step 1:** Run `./gradlew :feature:energy:testDebugUnitTest --tests '*EnergyFormatTest*'` to record the green baseline (it is the behaviour pin for this pure move).
- [ ] **Step 2:** Create the module, move the files and tests, and rewire both features.
- [ ] **Step 3:** Run `./gradlew :core:energy:jvmTest :core:energy:compileKotlinIosSimulatorArm64 :feature:energy:testDebugUnitTest :feature:home:testDebugUnitTest`. Expected: PASS, with the moved tests unchanged.
- [ ] **Step 4:** Commit `refactor(energy): energy figures and formats in common :core:energy`.

### Task 3: Home without `java.*` (still an Android module)

A refactor guarded by the existing suite: Home stops using JVM-only APIs, except Android resources, `@StringRes`, `LocalContext` resource lookups, Hilt and `BackHandler`, which Task 4 converts.

**Files:**
- Modify: `feature/home/build.gradle.kts` (`implementation(project(":core:format"))`), and under `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/`: `presentation/{AreaNames,SearchMatch,RoomCards,EntityLabels,EffectNames,CardFormatter,StepMath,HomeUiBuilder,DashboardUiBuilder,HomeViewModel,DashboardViewModel,CustomizeViewModel}.kt`, `rooms/{RoomsUiBuilder,RoomsViewModel}.kt`, `ui/{ClimatBanner,ControlSheet,WaterHeaterCard,DashboardTiles,MediaPlayerControls,HomeScreen,WheelMath}.kt`, plus a new `ui/FormatLocales.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/navigation/NidooNavHost.kt` (passes `onOpenVoice`, which starts `VoiceActivity`; the class-name constant moves here).
- Test: the existing `feature/home/src/test/**`, updated only where a signature below changes. New: `presentation/SearchMatchTest.kt` gains `foldsAccentsInBothDirections`.

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - Every `java.util.Locale` parameter in home becomes `FormatLocale` (source-compatible on Android). `Locale.getDefault()` → `systemLocale()`.
  - `@Composable internal fun currentFormatLocale(): FormatLocale` in `ui/FormatLocales.kt`, which is `LocalConfiguration.current.locales[0]` on Android today. Task 4 splits it into expect/actual. It replaces the four `LocalConfiguration.current.locales[0]` reads.
  - Replacements: `Normalizer` + mark regex → `stripMarks`. `lowercase(Locale.ROOT)` → `lowercase()`. `titlecase(Locale.ROOT)` → `titlecase()`. `NumberFormat` → `formatNumber`. `String.format(locale, "%.1f", v)` → `formatNumber(v, locale, 1, 1, grouping = false)`. `"%d %%".format(locale, n)` → `formatNumber(n.toDouble(), locale, 0, 0, grouping = false) + " %"`. `bandLine.format(locale, a, b)` → `stringResource(R.string.<that string>, a, b)` (positional placeholders). `DateTimeFormatter` → `formatEpochSeconds(seconds, "HH:mm" | "d MMM", locale)`. `BigDecimal` → `roundHalfUp(v, 2)`. `System.currentTimeMillis()` → `Clock.System.now().toEpochMilliseconds()` (`kotlin.time`, as core already does). `Math.toDegrees/toRadians` → `* 180 / PI` and `* PI / 180`. `@Volatile` → `kotlin.concurrent.Volatile`.
  - `HomeScreen`: the `now: LocalTime = LocalTime.now()` parameter becomes `hour: Int = localHour(Clock.System.now().epochSeconds)`, and `greeting(hour)` uses the same bands (5..11, 12..17, else).
  - `HomeRoute(…, onOpenHistory: ((String) -> Unit)? = null, onOpenVoice: (() -> Unit)? = null, …)`: null hides the history actions and the mic.

- [ ] **Step 1: Write the failing test** in `SearchMatchTest`:

```kotlin
@Test fun foldsAccentsInBothDirections() // SearchMatch.normalize("Séjour") == SearchMatch.normalize("sejour") == "sejour"; normalize("ÉTÉ") == "ete"
```
Run `./gradlew :feature:home:testDebugUnitTest --tests '*SearchMatchTest*'`. Expected: PASS already (it pins today's behaviour before the swap; keep it).
- [ ] **Step 2:** Apply the replacements. Then `grep -rnE '^import (java|javax\.inject\.Qualifier)' feature/home/src/main` must list only what Task 4 removes (`javax.inject`).
- [ ] **Step 3:** Update the tests whose signatures moved (`LocalTime.of(h, m)` → `hour = h`; tests that expect the mic or the history action pass the callback).
- [ ] **Step 4:** Run `./gradlew :feature:home:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(home): no java.text, java.time or java.math in Home`.

### Task 4: Home on Compose Multiplatform with Koin

**Files:**
- Modify: `feature/home/build.gradle.kts`: `nidoo.kmp.library` + `nidoo.kmp.compose`, laid out like `feature/onboarding/build.gradle.kts`. commonMain: `:core:source`, `:core:data`, `:core:designsystem`, `:core:history`, `:core:energy`, `:core:format`, `:core:dashboards`, `:core:flows`, `:core:climate`, `:core:transport`, `cmp-lifecycle-viewmodel-compose`, `cmp-lifecycle-runtime-compose`, `cmp-ui-backhandler`, koin BOM + `koin-core` + `koin-compose-viewmodel`, `coil-compose`. commonTest: coroutines-test, `:core:transport-testing`, koin-test. androidHostTest: onboarding's list plus `:core:transport-testing`. `compose.resources { packageOfResClass = "com.gekkotron.nidoo.feature.home.resources" }`.
- Move: `src/main/kotlin/**` → `src/commonMain/kotlin/**`; `src/main/res/values{,-fr}/strings.xml` → `src/commonMain/composeResources/values{,-fr}/strings.xml` (`\'` → `'`, positional placeholders in both locales); `src/test/**` → `src/androidHostTest/**` (`robolectric.properties` to `androidHostTest/resources`).
- Create: `src/androidMain/…/ui/FormatLocales.android.kt` and `src/iosMain/…/ui/FormatLocales.ios.kt` (`currentFormatLocale()` becomes `internal expect`; iOS = `systemLocale()`). `src/commonMain/…/di/HomeModule.kt` (Koin; it replaces the Hilt file of the same name). `src/commonMain/…/di/PreloadedLabelText.kt`.
- Create tests: `src/commonTest/…/di/PreloadedLabelTextTest.kt`, `src/commonTest/…/di/HomeModuleTest.kt`; `HomeViewModelTest` gains `homeRelabelsWhenLabelsLoad`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/di/{KoinBridge,AppModule}.kt` (drop the `@DefaultDispatcher` provider), `NidooNavHost.kt` imports, `app/src/test/…/di/KoinBridgeTest.kt`, and `.github/workflows/ci.yml` (`:feature:home:test` → `:feature:home:testAndroidHostTest`; add `:feature:home:iosSimulatorArm64Test` to the iOS job).

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces:
  - `fun interface LabelText { operator fun invoke(label: String): String?; val updates: Flow<Unit> get() = flowOf(Unit) }`. `updates` emits once at first, then again whenever translations change. `HomeViewModel`, `DashboardViewModel` and `CustomizeViewModel` add `labels.updates` to the flows they combine.
  - `EntityLabels.resource(label: String): StringResource?` replaces `resId`.
  - `class PreloadedLabelText(scope: CoroutineScope, locale: () -> FormatLocale = ::systemLocale, load: suspend () -> Map<String, String> = ::loadEntityLabels) : LabelText`. `invoke` looks `EntityLabels.normalize(label)` up in the map loaded for the current `locale()`. When `locale()` differs from the loaded one, it starts one reload for that locale (never two at once) and answers from the old map meanwhile. `updates` emits after each completed load. `suspend fun loadEntityLabels(): Map<String, String>` = every `EntityLabels` key → `getString(resource)`.
  - `CardTap.Confirm(command: Command, label: StringResource)`, `HomeEvent.AskBeforeAction(id: String, label: StringResource, command: Command)`, `vacuumStateLabel`/`vacuumActionLabel(…): StringResource`.
  - `HomeEventsEffect` and `RoomsRoute` look up their snackbar texts with the suspend CMP `getString(Res.string.…)` instead of `LocalContext.resources`.
  - `hiltViewModel()` → `koinViewModel()`; the JetBrains `collectAsStateWithLifecycle`; `androidx.compose.ui.backhandler.BackHandler` (`@OptIn(ExperimentalComposeUiApi::class)`). No `@HiltViewModel`/`@Inject`/`DefaultDispatcher` remains.
  - `val homeModule: Module`: `single<LabelText> { PreloadedLabelText(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }`, plus explicit `viewModel { … }` definitions (not `viewModelOf`: the constructors have defaults Koin would not use). `HomeViewModel(source, savedState, Dispatchers.Default, labels, history, preferences, climate, transport)`, `DashboardViewModel(source, dashboards, flows, preferences, history, labels, Dispatchers.Default)`, `CustomizeViewModel(source, labels, history)`, `RoomsViewModel(source, savedState, Dispatchers.Default)`. The platform supplies `EntitySource`, `AppPreferences`, `HistoryClient`, `DashboardsRepository`, `FlowsRepository`, `ClimateRepository` and `MqttTransport`.
  - `KoinBridgeEntryPoint` gains `historyClient()`, `dashboards()`, `flows()`, `climate()` and `transport(): MqttTransport`. `hiltBridgeModule` adds a factory for each, and `startNidooKoin` loads `modules(hiltBridgeModule(bridge), onboardingModule, homeModule)`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// PreloadedLabelTextTest (commonTest; a StandardTestDispatcher scope, a fake locale var and a fake load)
@Test fun unknownUntilLoadedThenTranslated() // labels("Temperature") == null; advanceUntilIdle; labels("Local_Temperature") == "Température locale"
@Test fun reloadsWhenTheLocaleChanges()      // loaded for en; locale = fr; labels("Temperature"); advanceUntilIdle; labels("Temperature") == "Température"
@Test fun updatesFireWhenLabelsLoad()        // collect updates: one emission at first, a second after the load completes
@Test fun oneReloadAtATime()                 // two lookups before the load completes start one load (load call count == 1)
// HomeModuleTest (commonTest; platform fakes as in shared's SharedModuleTest, plus HistoryClient.None, DashboardsRepository(scope), FlowsRepository(fake client, scope), ClimateRepository(scope), FakeMqttTransport)
@Test fun homeModuleResolvesItsViewModels()  // get<HomeViewModel> and get<RoomsViewModel> { parametersOf(SavedStateHandle()) }, get<DashboardViewModel>(), get<CustomizeViewModel>()
// HomeViewModelTest (androidHostTest)
@Test fun homeRelabelsWhenLabelsLoad()       // a LabelText answering null until its updates flow emits, then "Température": a card named "Temperature" ends as "Température"
```
- [ ] **Step 2:** Run `./gradlew :feature:home:testDebugUnitTest`. Expected: FAIL (unresolved `PreloadedLabelText`, `homeModule`, `updates`).
- [ ] **Step 3:** Convert the build file. Move sources, resources and tests, and apply the interface changes above. Delete the Hilt `HomeModule`, then update the Koin bridge, `startNidooKoin`, `KoinBridgeTest` (`homeViewModelsResolve`, like 4A's `onboardingViewModelResolves`) and CI. In moved tests, replace `R.string.x` lookups with `runBlocking { getString(Res.string.x) }`. Point `StringParityTest` at the two `composeResources` files.
- [ ] **Step 4:** Run `./gradlew :feature:home:testAndroidHostTest :feature:home:compileKotlinIosSimulatorArm64 :feature:energy:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS. If `MainActivityTest` fails with "Cannot bind to port", re-run it (embedded broker flake).
- [ ] **Step 5:** Commit `feat(home): Compose Multiplatform with Koin; strings as CMP resources`.

### Task 5: Common history and flows clients, and the HTTP route rule

**Files:**
- Move: `app/src/main/kotlin/com/gekkotron/nidoo/connection/ProfileHistoryClient.kt` → `core/connection/src/commonMain/kotlin/com/gekkotron/nidoo/connection/` (package unchanged; `:core:connection` gains `implementation(:core:history)`). Its test `app/src/test/…/connection/ProfileHistoryClientTest.kt` → `core/connection/src/commonTest/…` if it only uses common APIs, otherwise `androidHostTest`.
- Create: `core/connection/src/commonMain/kotlin/com/gekkotron/nidoo/connection/HttpRouteRule.kt`, `core/connection/src/commonTest/kotlin/com/gekkotron/nidoo/connection/HttpRouteRuleTest.kt`.
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/connection/MediaRoutes.kt` (`mediaRouteFor` delegates to `httpRouteFor`; `MediaRoutesTest` stays green unchanged).
- Move: `core/flows/src/jvmMain/kotlin/com/gekkotron/nidoo/flows/HttpFlowsClient.kt` → `core/flows/src/commonMain/…` (its `URLEncoder` replaced, see below). `HttpFlowsClientTest` moves to `commonTest` if it has no JVM-only API.
- Create: `core/flows/src/commonTest/kotlin/com/gekkotron/nidoo/flows/FlowIdEncodingTest.kt`.

**Interfaces:**
- Produces:
  - `fun httpRouteFor(profile: ConnectionProfile?, route: RoutePreference, local: (host: String, port: Int) -> LocalProbe, gateway: (TransportSpec.Gateway) -> GatewayDownloader): HttpRoute`: no route parts for anything but a Direct profile; `local` unless the route is GATEWAY; `gateway(direct.gateway.toSpec(direct.local))` unless the route is DIRECT and the profile has a gateway.
  - `class ProfileHttpRoutes(profiles: ProfileRepository, local: …, gateway: …) : HttpRoutes`, the same as Android's `ProfileMediaRoutes`, built on `httpRouteFor`.
  - `ProfileHistoryClient` in `:core:connection`, unchanged.
  - `internal fun encodePathPart(value: String): String` in `:core:flows`: keeps `A–Z a–z 0–9 . - * _` and writes every other UTF-8 byte as `%XX` (upper case). That matches `URLEncoder.encode(v, UTF_8).replace("+", "%20")`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// HttpRouteRuleTest (commonTest; recording lambdas)
@Test fun demoHasNoRoute()                 // httpRouteFor(Demo, AUTO, …) has local == null and gateway == null
@Test fun autoHasProbeAndGateway()         // a Direct profile with a gateway, AUTO → both set; the probe got the profile's local host and port
@Test fun directRouteSkipsTheGateway()     // DIRECT → gateway == null
@Test fun gatewayRouteSkipsTheProbe()      // GATEWAY → local == null
// FlowIdEncodingTest (commonTest)
@Test fun encodesLikeUrlEncoder()          // encodePathPart("a b+c/é*_.-~") == "a%20b%2Bc%2F%C3%A9*_.-%7E"
```
- [ ] **Step 2:** Run `./gradlew :core:connection:testAndroidHostTest :core:flows:jvmTest`. Expected: FAIL (unresolved `httpRouteFor`, `encodePathPart`).
- [ ] **Step 3:** Implement and move as listed. Delete Android's `ProfileMediaRoutes` if the common class replaces it everywhere (`AlertsModule` then builds `ProfileHttpRoutes(profiles, ::TcpProbe) { AthenaGatewayDownloader(it, http) }`); keep `mediaRouteFor` only while something still calls it.
- [ ] **Step 4:** Run `./gradlew :core:connection:testAndroidHostTest :core:connection:compileKotlinIosSimulatorArm64 :core:flows:jvmTest :core:flows:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(connection): common history client, flows client and HTTP route rule`.

### Task 6: The iOS shell shows the Rooms step and Home

**Files:**
- Modify: `gradle/libs.versions.toml` (`coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }`). `shared/build.gradle.kts`: commonMain adds `:feature:home`, `:core:history`, `:core:flows`, `:core:dashboards`, `:core:climate`; iosMain gets `coil-network-ktor3` and `ktor-client-darwin`; androidHostTest gets koin-test if `NidooAppTest` needs it.
- Modify: `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/di/IosModule.kt`, `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/{AppShellViewModel,NidooApp}.kt`, `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/di/SharedModule.kt`, `shared/src/commonMain/composeResources/values{,-fr}/strings.xml`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/InterimSettings.kt`, `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/IosLog.kt`, `shared/src/iosTest/kotlin/com/gekkotron/nidoo/shared/di/IosModuleTest.kt`.
- Delete: `HouseSummaryViewModel.kt`, its test, the `HouseSummary` composable and the `house_devices` plurals.
- Modify: `core/designsystem/src/iosMain/kotlin/com/gekkotron/nidoo/designsystem/Motion.ios.kt`.
- Test: `shared/src/commonTest/…/AppShellViewModelTest.kt`, `shared/src/commonTest/…/di/SharedModuleTest.kt`, `shared/src/androidHostTest/…/NidooAppTest.kt`.

**Interfaces:**
- Consumes: `homeModule`, `HomeRoute`, `RoomsRoute` (Task 4), `ProfileHistoryClient`, `ProfileHttpRoutes`, `HttpFlowsClient` (Task 5), and `KtorLanOrGatewayGet`, `lanHttpClients()`, `AthenaGatewayDownloader(spec)`, `NetworkTcpProbe`, `isLikelyLocalHost` (Phase 2), `routeChoice`, `Route.toPreference()` (`:core:connection`).
- Produces:
  - `internal fun iosLog(tag: String, message: String)` = `NSLog("%@: %@", tag, message)`.
  - `iosModule` adds singles for `ClimateRepository(scope, log = { iosLog("NidooClimate", it) })` and `DashboardsRepository(scope, log = …)`; `HttpRoutes` = `ProfileHttpRoutes(profiles, local = { h, p -> NetworkTcpProbe(h, p) }, gateway = { AthenaGatewayDownloader(it) })`; `KtorLanOrGatewayGet(routes, lanHttpClients(), ::isLikelyLocalHost, log = …)`; `HistoryClient` = `ProfileHistoryClient(profiles.state, get = { lanOrGateway.get(it, 1L shl 20) }, scope)` (Android's `MAX_BYTES`); `FlowsRepository(HttpFlowsClient(baseUrl = <Android FlowsModule's rule>, post = { url, json -> lanOrGateway.post(url, json) }), scope, log = …)`. `EntitySourceSession` gets `flowsSink`, `dashboardsSink` and `climateSink` as on Android.
  - `MainViewController` loads `modules(iosModule(mqttPortFactory), sharedModule, onboardingModule, homeModule)`.
  - `AppShellViewModel` gains `val route: StateFlow<Route?>` (= `combine(profiles.state, profiles.route, ::routeChoice)`, eagerly, null) and `fun setRoute(route: Route)` (`profiles.setRoute(route.toPreference())`), copied from Android's `AppViewModel`.
  - `NidooApp`, for a Ready profile: with `notificationsPending`, `finishNotifications()` as before. With `roomsPending`, `ScreenViewModels(p.profile to "rooms") { RoomsRoute(onDone = shell::finishRooms) }`. Otherwise `ScreenViewModels(p.profile) { HomeRoute(onOpenSettings = open, onOpenConnectionSettings = open, route = route, onRouteChange = shell::setRoute) }`, with no history or voice callback. `open` sets a `rememberSaveable` flag that shows `InterimSettings(onSetUpAgain = shell::setUpAgain, onBack = close)` in place of Home; `BackHandler` closes it too.
  - `@Composable internal fun InterimSettings(onSetUpAgain: () -> Unit, onBack: () -> Unit)`: a title `Res.string.interim_settings_title` ("Settings" / "Réglages"), a Back button, and an `OutlinedButton` with `Res.string.house_set_up_again`.
  - `sharedModule` = `viewModelOf(::AppShellViewModel)` only.
  - `animationsEnabled()` on iOS: state seeded from `UIAccessibilityIsReduceMotionEnabled()`, updated by an `NSNotificationCenter` observer on `UIAccessibilityReduceMotionStatusDidChangeNotification` (main queue), removed in `onDispose`.

- [ ] **Step 1: Write the failing tests:**

```kotlin
// AppShellViewModelTest (commonTest)
@Test fun setRouteSavesThePreference()                      // a Direct profile with a gateway; vm.setRoute(<the gateway Route>); advanceUntilIdle → profiles.route.value == RoutePreference.GATEWAY
// NidooAppTest (androidHostTest; its test Koin graph gains homeModule and the fakes HomeModuleTest uses)
@Test fun aNewProfileShowsTheRoomsStepThenHome()            // save the demo with roomsPending → the Rooms step shows; tap its Done → Home shows (the demo house's first room name is on screen)
@Test fun settingsOffersSetUpAgain()                        // Ready demo → tap the Home settings button → "Set up again" → Welcome shows
// IosModuleTest (iosTest; a no-op MqttClientPort object)
@Test fun iosGraphResolves()                                // koinApplication { modules(iosModule { NoopPort }, sharedModule, onboardingModule, homeModule) }: get EntitySourceSession, HistoryClient, FlowsRepository, DashboardsRepository, ClimateRepository, LabelText, ConnectionTester, AppShellViewModel; close()
```
Keep 4A's `setUpAgainAfterTheDemoShowsAWorkingWelcome` and `aNewHouseGetsANewSource`. They now check Home in place of the house summary: same scenario, Home's content instead of the device count.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL (unresolved `setRoute`, and the Rooms step not shown).
- [ ] **Step 3:** Implement the listed changes and delete the house summary.
- [ ] **Step 4:** Run `./gradlew :shared:testAndroidHostTest :shared:compileTestKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64 :core:designsystem:compileKotlinIosSimulatorArm64`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Build the iOS app as CI does, with no Simulator boot: `xcodegen generate --spec iosApp/project.yml && xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=NO build`. Expected: BUILD SUCCEEDED.
- [ ] **Step 6:** Commit `feat(shared): iOS opens on Home, with the Rooms step for a new setup`.

### Task 7: 4A leftovers

**Files:**
- Modify: `core/transport/src/commonMain/kotlin/com/gekkotron/nidoo/transport/AthenaGatewayApi.kt`; test `core/transport/src/jvmTest/kotlin/com/gekkotron/nidoo/transport/AthenaGatewayApiTest.kt`.
- Create: `iosApp/Nidoo/fr.lproj/InfoPlist.strings`. Modify: `iosApp/project.yml` (`CFBundleLocalizations: [en, fr]`; the `.lproj` is in a source folder so XcodeGen bundles it) and the regenerated `iosApp/Nidoo/Info.plist`.
- Modify: `docs/superpowers/plans/2026-11-22-ios-phase4-first-version.md` (4A checklist item 6), and `feature/onboarding/src/commonMain/…/OnboardingViewModel.kt` (the stray `constructor` keyword).

**Interfaces:**
- Produces: `AthenaGatewayApi.publish` times out through `withTimeoutOrNull(publishTimeout) { …; true } ?: throw PublishFailed("publish timed out")`, with no `TimeoutCancellationException` catch. A caller's own `withTimeout` now cancels through it untouched.
- Produces: `InfoPlist.strings` (fr): `NSCameraUsageDescription` = "Nidoo scanne le code de configuration de votre hub.", plus a French `NSLocalNetworkUsageDescription` translating the English one in `project.yml`.

- [ ] **Step 1: Write the failing test** in `AthenaGatewayApiTest`, next to 4A's `publishGivesUpAfterTheTimeout` (same never-answering MockWebServer):

```kotlin
@Test fun callerTimeoutIsNotAPublishTimeout() // api with publishTimeout = 5.seconds; assertFailsWith<TimeoutCancellationException> { withTimeout(200.milliseconds) { api.publish(…) } }
```
- [ ] **Step 2:** Run `./gradlew :core:transport:jvmTest --tests '*AthenaGatewayApiTest*'`. Expected: FAIL (a `PublishFailed` comes out instead).
- [ ] **Step 3:** Narrow the timeout, add the French plist strings and the plist key, and reword the 4A checklist item 6: "Deny the camera, then allow it in Settings: iOS restarts Nidoo, which opens on Welcome; Scan then shows the camera." Drop the `constructor` keyword.
- [ ] **Step 4:** Run `./gradlew :core:transport:jvmTest :feature:onboarding:testAndroidHostTest`, then the Task 6 Step 5 `xcodebuild`. Expected: PASS, BUILD SUCCEEDED, and `iosApp/build/Build/Products/Debug-iphonesimulator/Nidoo.app/fr.lproj/InfoPlist.strings` exists.
- [ ] **Step 5:** Commit `fix(ios): caller timeouts pass through gateway publishes; French permission prompts`.

### Task 8: CI, docs, memory and the PR

**Files:**
- Modify: `.github/workflows/ci.yml`. Check that every new module and test task is listed: core group `:core:format`, `:core:energy` (tests, `koverXmlReport`, codecov paths); feature group `:feature:home:testAndroidHostTest`; iOS job `:core:format:iosSimulatorArm64Test`, `:feature:home:iosSimulatorArm64Test`, and `:core:energy:iosSimulatorArm64Test` only if it has a commonTest.
- Modify: `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md`, adding a dated §9 line for the 4B rulings (`:core:format` and its typealias locale, `:core:energy` keeping its package, preloaded labels, voice and history callbacks, the interim iOS Settings, the common HTTP route rule).
- Modify: the memory note `ios-compose-multiplatform.md` (4B state, PR number).

- [ ] **Step 1:** Edit CI and the spec.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest :core:format:jvmTest :core:energy:jvmTest :core:connection:testAndroidHostTest :feature:home:testAndroidHostTest :feature:onboarding:testAndroidHostTest :shared:testAndroidHostTest`. Expected: PASS.
- [ ] **Step 3:** Commit `docs(ios): phase 4B rulings; CI runs format, energy and home`.
- [ ] **Step 4:** Push `feat/ios-phase4b-home`, open the PR "iOS phase 4B: Home on iOS" against `master`, and watch CI until it is green. Re-run a Robolectric HTTP 429 or a "Cannot bind to port" failure; fix anything else.

## iPhone checklist for 4B (the user, after install)

1. A fresh setup (or "Set up again", then the demo) shows the Rooms step; Done opens Home.
2. In French: numbers with a decimal comma, the greeting fits the time of day, and sensor names are translated ("Température").
3. Toggle a light at home (Direct) and away (Gateway). Move a brightness slider, and rename a device in the customize sheet.
4. Dashboards: switch dashboards; history tiles read "HH:mm" or "15 janv."; energy tiles show kW or kWh.
5. Search "sejour" finds "Séjour".
6. A media player's cover shows on the home Wi-Fi.
7. The settings gear opens Settings; "Set up again" returns to Welcome.
8. With Nidoo open, turn on Reduce Motion in iOS Settings, then return: Home's animations stop without a relaunch.
