# Energy views: Classic, Flux, Orbite (design)

Date: 2026-11-02 · Branch: `feature/energy-views` · Author: Gekkotron

## Intent

The Energy tab's live diagram comes in one style today (bubbles around the house, "Classic").
The hub (Oikos-OS web) has three: Classic, **Flux** (a Sankey) and **Orbite** (two rings). The
user wants all three in the app, chosen **in the app**, so each phone shows the style its owner
likes. Flux shows proportions at a glance; Orbite is the calm, decorative one; Classic stays the
easy default.

## Goals

- The Energy tab draws the live flow as Classic, Flux or Orbite.
- The choice lives in **Settings, a new "Energy view" section right after "Theme"**: three chips
  (Classic · Flux · Orbite) and a small static preview sketch of the selected view under them —
  the hub's Settings › Appearance pattern (mockup approved by the user).
- The choice is **per phone** (stored in `AppPreferences`), independent of the hub's own
  `home_settings.energy_view`. Default: Classic, so nothing changes until someone picks.
- Flux and Orbite behave like the hub's versions (the working reference, see below):
  - **Flux**: three columns — sources (solar, grid, battery discharge) → house → sinks
    (consumers, battery charge, export). Band thickness ∝ watts. Power the house uses that no
    consumer meter accounts for is a **hatched** band ("unitemised"). Consumers past the fold
    limit collapse into one "Others" node (hub: 4 on narrow screens; the app is a phone, so 4).
  - **Orbite**: inner ring = where power comes from, outer ring = where it goes, arcs ∝ share of
    their ring, small gaps between arcs, faint track behind, dots drifting slowly along the arcs,
    house total in the middle.
- Tapping in the new views does what tapping a bubble does in Classic: a band/bar (Flux) or an arc
  (Orbite) opens that node's `EnergyDetailSheet`; "Others" opens `ConsumersSheet`. The hatched
  unitemised band/arc is not tappable.
- Colours come from the theme in both Ivoire and Night Glass.
- Animations stop when the diagram isn't on screen and when the system animator scale is 0
  (same rule as Classic's dashes today).

## Non-goals

- No change to the hub, its setting, or the MQTT/hub contract.
- No change to Classic's look, the cards under the diagram, or the detail sheet.
- No picker on the Energy tab itself (the user chose Settings over chips/corner button).
- No sync of the choice across phones.
- Widgets keep their current rendering.

## Anti-goals (would be a failure even if it works)

- The Energy tab janks or burns battery: animation must not recompose per frame (Classic reads
  its clock in the draw phase — keep that discipline).
- Switching style loses or changes data: all three views read the same `EnergyUiState`; only
  drawing differs.
- A view that lies: band/arc sizes must sum consistently (a balanced Sankey — sources = house =
  sinks), and unknown values must look unknown, not zero-and-fine.

## Constraints and context

- App: Kotlin + Jetpack Compose, multi-module. Energy UI in `feature/energy` (`ui/FlowDiagram.kt`
  draws Classic; `ui/EnergyScreen.kt` puts it in a `NidooCard`). State model in
  `presentation/EnergyModels.kt` (`EnergyNode`, `NodeKind`, `Flow`, `NodeStatus`,
  `EnergyUiState`).
- `EnergyUiState.diagram` is shaped for Classic (≤ 3 consumer slots, 2 + `MORE` when folded).
  Flux and Orbite need their own fold (4) and the unitemised remainder, so they work from
  `EnergyUiState.consumers` plus the role nodes, not from `diagram`'s slots.
- Preferences: `core/data/.../AppPreferences.kt` already stores the theme as a **name string** so
  unknown values from a newer app decode safely; store the energy view the same way (unknown →
  Classic). `feature/home` already injects `AppPreferences`, so `feature/energy` may too.
- Settings: `feature/settings/.../SettingsScreen.kt`, section `settings_theme`. Strings in EN
  (`values/`) and FR (`values-fr/`).
- **Hub reference** (Gekkotron/Oikos-os @ `1226316`, 2026-11-02), Rust/Leptos, pure geometry
  modules are unit-tested and port directly:
  - `crates/nidoo-web/src/energy_sankey.rs` — Flux model: roles, balancing, folding, `IDLE_W`.
  - `crates/nidoo-web/src/sankey_layout.rs` — Flux band/bar geometry.
  - `crates/nidoo-web/src/screens/energy_flux.rs` — Flux drawing and taps.
  - `crates/nidoo-web/src/energy_orbite.rs` — Orbite geometry (`GAP_DEG` 1.6°,
    `MIN_DOT_ARC_DEG` 6°, `MIN_SPAN_DEG` 0.2°, rings, shares, dots).
  - `crates/nidoo-web/src/screens/energy_orbite.rs` — Orbite drawing and taps.
  - `crates/nidoo-web/src/screens/energy_preview.rs` — the three Settings preview sketches.
  - `crates/nidoo-web/styles/main.css` `.energy-preview` / `:root[data-theme="light"] .energy` —
    the energy palette (`--energy-c0..c4`, `--energy-muted`, `--energy-hatch*`).
- Accessibility: the diagram today is one `contentDescription` sentence (`energyDescription`);
  the cards are the accessible path to details. Keep that for all three views.

## Decided by the user

- All three styles, in the app.
- Picker in Settings next to Theme, with chips + preview (not on the Energy tab).
- Behaviour summary above (fold, hatching, taps, animation pause) — approved as played back.

## Builder's calls (made by Claude, flagged for the user)

- **Per-phone storage, default Classic** — follows from "select in the app".
- **Consumer colours**: Classic tints every consumer with `colors.heat`. Flux and Orbite need to
  tell consumers apart, so add a small consumer palette to `NidooColors` (5 slots + muted +
  hatch/hatch-ground) with the hub's values for Ivoire (`#35b9f0 #ff6f91 #7f9cff #ff9a55
  #3fd6c6`, muted `#d9d1c3`, hatch `#c4b8a5` on `#f1ece2`) and equivalents for Night Glass.
  Classic keeps `heat`.
- Settings preview is a static Compose `Canvas` sketch with sample values, no live data — a port
  of the hub's `energy_preview.rs` sketches (shapes, not pixel-exact).

### Settled after the builder check (Claude's calls)

- **Data path**: every source (MQTT/HA discovery or the Oikos-OS hub) reaches the tab through
  `EntitySource` → `EnergyViewModel` → `EnergyUiState`; the new views read only that state.
- **Unknown / stale values**: mirror the hub's `energy_sankey.rs` — only a LIVE node's value sizes
  bands and arcs (null or non-LIVE counts as 0, values under `IDLE_W` are idle). To keep "unknown
  looks unknown", a non-LIVE node still keeps its bar/arc label and is drawn with Classic's
  `statusAlpha` fade; it never silently vanishes while the others look fine.
- **Balancing**: port the hub's rules as they are (house = measured, else derived; unitemised =
  house − shown consumers, clamped at 0; fold to "Others" past 4).
- **Night Glass palette**: the hub's dark values (`.energy-preview` / `.energy` block in
  `main.css`, built from `--accent-*`), mapped onto the app's Night Glass accents.
- **Role colours**: solar, grid, battery and house use the same `NidooColors` slots as Classic
  (`edgeColor`); only consumers get the new 5-slot palette.
- **Diagram size**: the card may be taller than Classic's for Flux/Orbite; the screen already
  scrolls (`LazyVerticalGrid`).
- **Water**: not power — excluded from Flux and Orbite (the hub does the same); it keeps its card.

## Left to the builder

- How to split the code (e.g. pure `SankeyLayout` / `OrbiteLayout` in `presentation`, unit-tested
  like the hub's; thin Canvas composables in `ui`).
- Exact sizes/aspect ratios on a phone, label placement, dot count and speed.
- Battery and export handling in Orbite/Flux beyond what the hub does — mirror the hub.
- Test strategy: unit tests for layouts; screenshot tests of the three views via the
  `:core:designsystem`-style screenshot suite or the Robolectric draw trick; `./gradlew check`
  green.
