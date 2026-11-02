# Energy Views (Classic · Flux · Orbite) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Energy tab can draw its live flow as Classic (today), Flux (Sankey) or Orbite (two rings), chosen per phone in Settings next to Theme.

**Architecture:** Port the hub's pure geometry (`energy_sankey.rs`, `sankey_layout.rs`, `energy_orbite.rs`) to pure Kotlin in `feature/energy/presentation`, unit-tested like the hub's; draw each with a thin Compose `Canvas` in `feature/energy/ui`. The choice is a new `AppPreferences` field read by `EnergyViewModel` and written by `SettingsViewModel`. All three views read the same `EnergyUiState`.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas), Hilt, DataStore (`AppPreferences`), JUnit + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-02-energy-views-design.md`

**Hub reference:** Gekkotron/Oikos-os @ `1226316`, `crates/nidoo-web/src/` — clone with
`git clone --depth 1 git@github.com-gekkotron:Gekkotron/Oikos-os.git <scratch>/oikos-os`. Port its tests along with its code.

## Global Constraints

- Gradle runs with the sandbox disabled (`~/.gradle` writes fail otherwise).
- Commit identity on every commit: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`, message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Default view: Classic. Stored as a name string; unknown names decode as Classic.
- Choice is per phone; never read or write the hub's `home_settings.energy_view`.
- Phone fold: consumers past **4** powered collapse into "Others" (hub `FOLD_NARROW`).
- Idle threshold: reuse `EnergyFlowBuilder.IDLE_W` (10 W) — same value as hub `IDLE_W`.
- Only a LIVE node's value sizes anything; null/STALE/UNAVAILABLE count as 0 (hub `active`/`measured`).
- Water is excluded from Flux and Orbite.
- Orbite constants: `GAP_DEG = 1.6`, `MIN_DOT_ARC_DEG = 6.0`, `MIN_SPAN_DEG = 0.2`, `dotSpeed(w) = 10 + 0.9·√max(w,0)` °/s, dt clamp 0.1 s, dots per arc `max(1, round(span/42))`, no dots on Others/Unitemised.
- Animation: frame clock read in the draw phase (no per-frame recomposition), off when `ANIMATOR_DURATION_SCALE == 0` (reuse `animationsEnabled()` from `FlowDiagram.kt`, make it `internal`).
- Strings in EN `values/` and FR `values-fr/`.
- `./gradlew :feature:energy:check :feature:settings:check :core:data:check :core:designsystem:check` green before each commit.

## Review Focus

1. **Everything unknown** (no live solar/grid/house, e.g. broker just connected): Flux/Orbite show nothing broken — an empty state, not a crash or NaN path. → test `emptyStateBuildsEmptySankey` (Task 3), `ringsOfEmptySankeyAreEmpty` (Task 5), `fluxDrawsNothingForEmptySankey` (Task 6).
2. **Grid exporting** (solar > house): export appears as a sink in column 1 / outer ring, not as a negative band. → `exportTakesSolarFirst` (Task 3).
3. **Measured house below the sum of consumers** (meters disagree): unitemised clamps at 0, never negative. → `unitemisedNeverNegative` (Task 3).
4. **A consumer or source goes STALE** mid-session: it leaves the geometry and appears in the "Not reporting" caption. → `notReportingListsStaleRolesAndConsumers` (Task 3), `fluxShowsNotReportingCaption` (Task 6).
5. **Six powered consumers on a phone**: 3 shown + Others(3) (fold_at 4 keeps `fold_at − 1`), tapping Others opens the consumers sheet. → `foldsPastFourKeepsThree` (Task 3), `tapOthersOpensConsumers` (Task 6).

---

### Task 1: Energy view preference

**Files:**
- Modify: `core/data/src/main/kotlin/com/gekkotron/nidoo/data/AppPreferences.kt`
- Test: `core/data/src/test/kotlin/com/gekkotron/nidoo/data/AppPreferencesTest.kt`

**Interfaces:**
- Produces: `enum class EnergyViewPreference { CLASSIC, FLUX, ORBITE }`; `AppPreferences.energyView: StateFlow<EnergyViewPreference?>` (null until read); `suspend fun AppPreferences.setEnergyView(view: EnergyViewPreference)`. Stored as `StoredPreferences.energyView: String = "CLASSIC"`.

- [ ] **Step 1: Failing tests** — `energyViewDefaultsToClassic` (fresh file → `CLASSIC`), `setEnergyViewPersists` (set `ORBITE`, re-create from the same file → `ORBITE`), `unknownEnergyViewDecodesAsClassic` (file JSON with `"energyView":"SPIRAL"` → `CLASSIC`), following the existing theme tests in the file.
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest --tests '*AppPreferencesTest*'` → FAIL (unresolved `energyView`).
- [ ] **Step 3:** Implement in the style of `theme` (decode in `publish`, `firstOrNull { it.name == … } ?: CLASSIC`).
- [ ] **Step 4:** Same command → PASS.
- [ ] **Step 5:** Commit `feat(prefs): per-phone energy view choice`.

### Task 2: Energy palette in the theme

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/NidooColors.kt`
- Test: `core/designsystem/src/test/kotlin/com/gekkotron/nidoo/designsystem/ThemeTest.kt`

**Interfaces:**
- Produces on `NidooColors`: `energyConsumers: List<Color>` (exactly 5), `energyMuted: Color`, `energyHatch: Color`, `energyHatchGround: Color`.

Values (spec "Builder's calls"; Night Glass = hub dark tokens with `color-mix` precomputed):

| | c0 | c1 | c2 | c3 | c4 | muted | hatch | hatch ground |
|---|---|---|---|---|---|---|---|---|
| Ivoire | `#35B9F0` | `#FF6F91` | `#7F9CFF` | `#FF9A55` | `#3FD6C6` | `#D9D1C3` | `#C4B8A5` | `#F1ECE2` |
| Night Glass | `#58E1FF` | `#FF7A9A` | `#71B3FF` | `#FF9B7C` | `#62E8E2` | Ink α .35 | Ink α .42 | Ink α .10 |

- [ ] **Step 1: Failing test** `bothThemesHaveFiveEnergyConsumerColours` (size == 5 for `IvoireColors` and `NightGlassColors`) and `ivoireEnergyConsumerColoursMatchHub` (c0 == `Color(0xFF35B9F0)`).
- [ ] **Step 2:** `./gradlew :core:designsystem:testDebugUnitTest --tests '*ThemeTest*'` → FAIL.
- [ ] **Step 3:** Add the fields and values.
- [ ] **Step 4:** Same → PASS; `:core:designsystem:check` green (fix any other `NidooColors(...)` constructor call sites, e.g. previews/tests).
- [ ] **Step 5:** Commit `feat(theme): energy consumer palette for Flux and Orbite`.

### Task 3: Sankey model (port of `energy_sankey.rs`)

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/presentation/EnergySankey.kt`
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/presentation/EnergySankeyTest.kt`

**Interfaces:**
- Consumes: `EnergyUiState` (`node(id)` for `"solar" "grid" "battery" "house"`, `consumers`), `EnergyNode`, `Flow`, `NodeStatus`, `EnergyFlowBuilder.IDLE_W`.
- Produces:
  - `enum class SankeyRole { SOLAR, GRID, BATTERY, HOUSE, EXPORT, BATTERY_CHARGE, CONSUMER, OTHERS, UNITEMISED }`
  - `data class SankeyNode(id: String, role: SankeyRole, column: Int, name: String?, watts: Double, size: Double, count: Int = 0, slot: Int = 0)` — ids as the hub: `"solar" "grid" "battery" "house" "export" "battery-charge" "others" "unitemised"` or the consumer's `EnergyNode.id`. (The hub's `sensor` field is dropped: taps resolve through `tapTarget`.)
  - `data class SankeyLink(from: String, to: String, watts: Double)`
  - `data class Sankey(nodes: List<SankeyNode>, links: List<SankeyLink>, houseW: Double?, folded: Boolean) { val isEmpty: Boolean }`
  - `object EnergySankey { const val FOLD_PHONE = 4; fun build(state: EnergyUiState, foldAt: Int = FOLD_PHONE): Sankey }`
  - `fun EnergyUiState.notReporting(): List<EnergyNode>` — role nodes (solar, grid, battery, house) and consumers whose `status != LIVE`, in that order; never water.
  - `fun EnergyUiState.tapTarget(sankeyId: String): EnergyNode?` — `"export"`→grid node, `"battery-charge"`→battery node, `"others"`/`"unitemised"`→null, else `node(id)` ?: `consumers.firstOrNull { it.id == id }`.

- [ ] **Step 1: Failing tests** — port every `#[test]` in the hub's `energy_sankey.rs` (same scenarios and numbers), plus:
  - `emptyStateBuildsEmptySankey`: `EnergyUiState()` → `isEmpty`, `houseW == null`.
  - `exportTakesSolarFirst`: solar 3000 IN, grid 1000 OUT, house 2000 → links solar→house 2000, solar→export 1000; no node has negative size.
  - `unitemisedNeverNegative`: house 500 measured, consumers 400 + 300 → unitemised node absent or `watts == 0.0`.
  - `foldsPastFourKeepsThree`: 6 LIVE consumers ≥ 10 W → 3 CONSUMER nodes + OTHERS with `count == 3`, `folded`.
  - `staleConsumerIsLeftOut`: one STALE consumer of 3 → 2 CONSUMER nodes.
  - `notReportingListsStaleRolesAndConsumers`: grid STALE + one consumer UNAVAILABLE → `[grid, thatConsumer]`.
  - `tapTargetMapsSinksToTheirRoleNode`: `"export"`→grid, `"battery-charge"`→battery, `"others"`→null.
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*EnergySankeyTest*'` → FAIL.
- [ ] **Step 3:** Port `build` line for line (allocation order: export from solar then battery; charge from solar then grid; house measured else derived when any input is LIVE; unitemised = house − shown − others clamped at 0; links dropped under IDLE_W; sizes = max(in, out)).
- [ ] **Step 4:** Same → PASS.
- [ ] **Step 5:** Commit `feat(energy): Sankey model ported from the hub`.

### Task 4: Sankey layout (port of `sankey_layout.rs`)

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/presentation/SankeyLayout.kt`
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/presentation/SankeyLayoutTest.kt`

**Interfaces:**
- Consumes: `Sankey`, `SankeyNode`, `SankeyRole` (Task 3).
- Produces (units = the frame's own, the Canvas scales them):
  - `data class Frame(w: Double, h: Double, vertical: Boolean)`; `val WIDE = Frame(980.0, 460.0, false)`, `val NARROW = Frame(360.0, 620.0, true)`
  - `data class SankeyBar(id: String, x: Double, y: Double, w: Double, h: Double)`
  - `data class Pt(x: Double, y: Double)`
  - `data class SankeyBand(from: String, to: String, a0: Pt, a1: Pt, b0: Pt, b1: Pt)` — the band's two ends on the source bar (`a0..a1`) and target bar (`b0..b1`), already in frame x/y for either orientation; the UI draws `a0 → cubic → b0 → b1 → cubic → a1 → close` with control points at the main-axis midpoint (the hub's band path).
  - `data class SankeyLabel(id: String, x: Double, y: Double, anchor: Anchor)`; `enum class Anchor { START, MIDDLE, END }`
  - `data class MixStop(from: String, start: Double, end: Double)`
  - `data class Placed(bars: List<SankeyBar>, bands: List<SankeyBand>, labels: List<SankeyLabel>, houseMix: List<MixStop>)`
  - `object SankeyLayout { const val BAR = 16.0; const val LINE = 14.0; const val LABEL_HALF = 44.0; fun place(s: Sankey, frame: Frame): Placed }`

- [ ] **Step 1: Failing tests** — port the hub's layout tests that do not test SVG strings: `bars_fit_the_frame`, `bands_never_outgrow_their_bar`, `house_mix_follows_the_bands_into_it`, `house_mix_leaves_the_unsourced_rest_open`, `columns_sit_on_the_main_axis`, `vertical_swaps_axes`, `labels_follow_the_side_rules`, `narrow_labels_stay_inside_the_frame`, `narrow_labels_in_a_row_never_overlap`, `empty_model_places_nothing` (camelCase names). Skip `paths_keep_one_shape` (SVG-only).
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*SankeyLayoutTest*'` → FAIL.
- [ ] **Step 3:** Port `place` (column shares vertical `[0.14, 0.46, 0.78]`, horizontal `[0.17, 0.49, 0.79]`, `ROW_STEP = 2·LINE + 4`), emitting points instead of SVG strings.
- [ ] **Step 4:** Same → PASS.
- [ ] **Step 5:** Commit `feat(energy): Sankey layout ported from the hub`.

### Task 5: Orbite geometry (port of `energy_orbite.rs`)

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/presentation/OrbiteLayout.kt`
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/presentation/OrbiteLayoutTest.kt`

**Interfaces:**
- Consumes: `Sankey`, `SankeyNode`, `SankeyRole` (Task 3).
- Produces:
  - `enum class Ring { INNER, OUTER }`
  - `data class OrbiteArc(id: String, ring: Ring, role: SankeyRole, slot: Int, name: String?, watts: Double, size: Double, share: Int, a0: Double, a1: Double, count: Int)` (degrees, 0 = top, clockwise)
  - `data class Rings(inner: List<OrbiteArc>, outer: List<OrbiteArc>, total: Double)`
  - `object OrbiteLayout { const val GAP_DEG = 1.6; const val MIN_DOT_ARC_DEG = 6.0; fun rings(s: Sankey): Rings; fun point(cx: Double, cy: Double, r: Double, deg: Double): Pair<Double, Double>; fun dotSpeed(watts: Double): Double; fun advancePhase(phase: Double, dtS: Double, arc: OrbiteArc): Double; fun arcDots(arc: OrbiteArc, phase: Double): List<Pair<Double, Double>> }` — inner = SOLAR, BATTERY, GRID; outer = EXPORT, BATTERY_CHARGE, CONSUMER, OTHERS, UNITEMISED; both scaled to the larger ring sum.

- [ ] **Step 1: Failing tests** — port every `#[test]` in the hub's `energy_orbite.rs` except ones asserting SVG `arc_path` strings, plus `ringsOfEmptySankeyAreEmpty` (`Sankey(emptyList(), emptyList(), null, false)` → both rings empty, `total == 0.0`).
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*OrbiteLayoutTest*'` → FAIL.
- [ ] **Step 3:** Port.
- [ ] **Step 4:** Same → PASS.
- [ ] **Step 5:** Commit `feat(energy): Orbite geometry ported from the hub`.

### Task 6: Flux diagram

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/ui/FluxDiagram.kt`, `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/ui/EnergyPalette.kt`, `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/ui/NotReportingCaption.kt`
- Modify: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/ui/FlowDiagram.kt` (make `animationsEnabled()` internal), `feature/energy/src/main/res/values/strings.xml`, `values-fr/strings.xml`
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/ui/FluxDiagramTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4; `EnergyUiState.notReporting()`, `tapTarget()`; `nodeLabel`, `nodeValueText`/`EnergyFormat` for text.
- Produces:
  - `internal fun sankeyColor(role: SankeyRole, slot: Int, colors: NidooColors): Color` — SOLAR→`solar`, GRID/EXPORT→`grid`, BATTERY/BATTERY_CHARGE→`connected`, CONSUMER→`energyConsumers[slot % 5]`, HOUSE/OTHERS/UNITEMISED→`energyMuted` (hub `colour_var`).
  - `@Composable internal fun FluxDiagram(state: EnergyUiState, locale: Locale, onMore: () -> Unit, onNode: (EnergyNode) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable internal fun NotReportingCaption(nodes: List<EnergyNode>)` — hidden when empty.
  - Strings: `energy_not_reporting` = "Not reporting: %1$s" / FR "Sans données : %1$s"; `energy_others` = "Others" / "Autres"; `energy_unitemised` = "Unmetered" / "Non mesuré".

Drawing: frame = `Frame.NARROW` when the available width < 600 dp, else `Frame.WIDE`; aspect ratio = frame w/h. Bands at α 0.38 in the target's colour (unitemised: hatch pattern of `energyHatch` stripes 45° on `energyHatchGround`); bars solid; house bar painted by `houseMix`; labels value + name at `SankeyLabel` positions. No infinite animation: when a reading changes, bar/band geometry tweens over 400 ms (`animateFloatAsState` on sizes or `Animatable` on the placed numbers — implementer's choice). Tap: hit-test bars and bands (band → its `to` id; bar → its id) and call `onNode(tapTarget(id))`, or `onMore()` for `"others"`; unitemised ignored. Semantics: same `energyDescription(state, locale)` sentence as Classic.

- [ ] **Step 1: Failing Robolectric tests** (pattern from `EnergyScreenTest`): `fluxDrawsNothingForEmptySankey` (`EnergyUiState()` → no crash, node with tag `"energy-flux"` exists, no labels), `fluxShowsNotReportingCaption` (grid STALE → text "Not reporting: Grid" displayed), `tapOthersOpensConsumers` (6 consumers → click label/bar tagged `"flux-others"` → `onMore` called), `tapConsumerOpensItsNode` (click tag `"flux-<consumerId>"` → `onNode` gets that node). Give each bar an invisible clickable `Box` with `testTag("flux-$id")` over its rect (also the hit target).
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*FluxDiagramTest*'` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same → PASS.
- [ ] **Step 5:** Commit `feat(energy): Flux view`.

### Task 7: Orbite diagram

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/nidoo/feature/energy/ui/OrbiteDiagram.kt`
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/ui/OrbiteDiagramTest.kt`

**Interfaces:**
- Consumes: Tasks 3, 5, 6 (`sankeyColor`, `NotReportingCaption`, `animationsEnabled`).
- Produces: `@Composable internal fun OrbiteDiagram(state: EnergyUiState, locale: Locale, onMore: () -> Unit, onNode: (EnergyNode) -> Unit, modifier: Modifier = Modifier)`.

Drawing: square, track rings (`energyMuted` α .3) under arcs; outer radius 0.42·side stroke 0.06·side, inner radius 0.31·side stroke 0.04·side; arcs `drawArc` with `startAngle = a0 − 90`; dots (white, r 2.5 dp, alpha from `arcDots`) advanced by `advancePhase` per arc id kept in a `remember`ed map, clock read in the draw phase like Classic; house total (`EnergyFormat` watts of `sankey.houseW`) centred, "House" label under it. Tap: angle + radius from centre → arc → `onNode(tapTarget(id))` / `onMore()` for others. Not-reporting caption below.

- [ ] **Step 1: Failing tests** — `orbiteEmptyStateShowsNoTotal` (`EnergyUiState()` → tag `"energy-orbite"` exists, no "W" text), `orbiteShowsHouseTotal` (house 2260 LIVE → text from `EnergyFormat` for 2260 W displayed), `orbiteTapOnConsumerArcOpensNode` (single consumer → `performTouchInput { click(<point on outer ring at that arc's mid angle>) }` → `onNode` gets it).
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*OrbiteDiagramTest*'` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same → PASS.
- [ ] **Step 5:** Commit `feat(energy): Orbite view`.

### Task 8: Energy tab draws the chosen view

**Files:**
- Modify: `feature/energy/build.gradle.kts` (add `implementation(project(":core:data"))`), `presentation/EnergyViewModel.kt`, `ui/EnergyScreen.kt` (+ its route composable)
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/presentation/EnergyViewModelTest.kt`, `ui/EnergyScreenTest.kt`

**Interfaces:**
- Consumes: `AppPreferences.energyView` (Task 1); `FluxDiagram`, `OrbiteDiagram`.
- Produces: `EnergyViewModel.energyView: StateFlow<EnergyViewPreference>` (initial `CLASSIC`). Internal constructor gains `energyView: Flow<EnergyViewPreference> = flowOf(EnergyViewPreference.CLASSIC)`; the `@Inject` constructor gains `preferences: AppPreferences` and passes `preferences.energyView.filterNotNull()`. `EnergyScreen(...)` gains `view: EnergyViewPreference = EnergyViewPreference.CLASSIC`.

- [ ] **Step 1: Failing tests** — `energyViewFollowsPreference` (VM with `MutableStateFlow(FLUX)` → `energyView.value == FLUX`), `screenDrawsFluxWhenChosen` (`view = FLUX` → tag `"energy-flux"` exists, `"energy-diagram"` absent), same for ORBITE.
- [ ] **Step 2:** `./gradlew :feature:energy:testDebugUnitTest --tests '*EnergyViewModelTest*' --tests '*EnergyScreenTest*'` → FAIL.
- [ ] **Step 3:** In the diagram card: `when (view) { CLASSIC -> FlowDiagram(…); FLUX -> FluxDiagram(…); ORBITE -> OrbiteDiagram(…) }` with the same `onMore`/`onNode` lambdas.
- [ ] **Step 4:** Same → PASS; `:feature:energy:check` and `:app:testDebugUnitTest` green (Hilt graph).
- [ ] **Step 5:** Commit `feat(energy): draw the view chosen in Settings`.

### Task 9: Settings › Energy view

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/EnergyViewSection.kt`
- Modify: `presentation/SettingsViewModel.kt`, `ui/SettingsScreen.kt` (section right after Theme), `res/values/strings.xml`, `res/values-fr/strings.xml`
- Test: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/…` (next to the existing Settings VM / screen tests)

**Interfaces:**
- Consumes: `AppPreferences.energyView`, `setEnergyView` (Task 1); `NidooColors` energy palette (Task 2).
- Produces: `SettingsUi.energyView: EnergyViewPreference` (default CLASSIC); `SettingsViewModel.setEnergyView(view)`; `@Composable internal fun EnergyViewSection(selected: EnergyViewPreference, onSelect: (EnergyViewPreference) -> Unit)` — three chips (`RoomChips(items: List<ChipItem>, selectedKey, onSelect)` from `core/designsystem/components/RoomChips.kt`, keys = enum names) + `EnergyViewSketch(view)`, a static `Canvas` port of the hub's `energy_preview.rs` sketches (viewBox 150×112 offset −8; same shapes/sample shares), decorative (`clearAndSetSemantics {}`).
- Strings: `settings_energy_view` = "Energy view" / "Vue énergie"; `settings_energy_view_classic` "Classic" / "Classique"; `…_flux` "Flux" / "Flux"; `…_orbite` "Orbite" / "Orbite"; `settings_energy_view_hint` = "How the Energy tab draws the live flow on this phone." / "Comment l'onglet Énergie dessine le flux en direct sur ce téléphone."

- [ ] **Step 1: Failing tests** — `setEnergyViewWritesPreference` (VM → `preferences.energyView.value == ORBITE`), `energyViewSectionSelectsChip` (click "Flux" → `onSelect(FLUX)`; "Classic" chip selected initially).
- [ ] **Step 2:** `./gradlew :feature:settings:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same → PASS; `:feature:settings:check` green.
- [ ] **Step 5:** Commit `feat(settings): Energy view picker with preview`.

### Task 10: Visual and device check

**Files:**
- Test: `feature/energy/src/test/kotlin/com/gekkotron/nidoo/feature/energy/ui/EnergyViewsRenderTest.kt` (local-only render, `@Ignore`d on CI like the existing screenshot approach if it is slow)

- [ ] **Step 1:** Render Flux and Orbite with the sample house (solar 1850, grid 410 IN, house 2260, AC 1080, water heater 480) in Ivoire and Night Glass to PNGs with the Robolectric draw trick (`@GraphicsMode(NATIVE)`, `mainClock.autoAdvance = false`, `decorView.draw(Canvas(bitmap))`); inspect them: no overlaps, labels inside, hatch visible, colours per theme.
- [ ] **Step 2:** `./gradlew check` (sandbox disabled) → BUILD SUCCESSFUL.
- [ ] **Step 3:** Install on the Pixel 6a (`./gradlew :app:installDebug`), switch each view in Settings, open Energy; confirm animation pauses when leaving the tab.
- [ ] **Step 4:** Commit any fixes; push branch; open PR to `master` (GH_REPO=Gekkotron/Nidoo-app); watch CI.
