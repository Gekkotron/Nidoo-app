# Animated Robot and AC Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Home card's spinning-fan AC icon and brush-and-dust robot icon with a wall-unit-with-airflow AC and a robot cleaning a room, and show both as animated SVGs in the README.

**Architecture:** Both icons are drawn on the existing 24-unit `IconCanvas` in `:core:designsystem`, with every animated value held in a `State<Float>` and read only inside the draw lambda (draw-phase animation, no per-frame recomposition). Pure geometry and mapping live in small testable functions. `KindIcon.kt` in `:feature:home` maps entity state to the new parameters. The README images are hand-written, self-contained animated SVGs in `docs/images/`.

**Tech Stack:** Kotlin, Jetpack Compose (`Canvas`, `rememberInfiniteTransition`), JUnit + Robolectric compose tests, SVG + CSS keyframes.

**Spec:** `docs/superpowers/specs/2026-11-19-animated-robot-ac-icons-design.md`

## Global Constraints

- 24-unit grid, stroke `IconGeometry.STROKE` (1.6), duotone fill `IconGeometry.FILL_ALPHA` (0.22); use `IconCanvas`, `outline`, `surface` from `IconCanvas.kt`.
- Colours come from `NidooTheme.colors` (never hard-coded in Kotlin): cool → `water`, heat → `heat`, dry → `water`, fan only → content (`LocalContentColor`), auto/other → `connected`; dock while docked → `connected`.
- Animated values are read only inside the `IconCanvas { }` draw lambda, never in composition (the `KindIconRecompositionTest` pattern).
- `animate = false` (system animations off) draws a still pose and starts no infinite transition.
- README SVGs: no scripts, no external references, CSS keyframes only, Night Glass tokens (`#1D2B55`→`#0B1020` background, ink `#E8ECFF`, amber `#FFC457`, mint `#6FF0BF`, water `#58E1FF`, heat `#FF7A9A`).
- Commits as Gekkotron: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`, message ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. An AC that reports `on` but no mode (or an unknown mode such as `heat_cool`) must still animate, as AUTO in mint — not fall back to the off drawing. Pinned in Task 2's `IconSpecTest`.
2. An unavailable AC or robot must draw still (off / idle), even if its last state was cooling or cleaning. Pinned in Task 2 and Task 4 `IconSpecTest`.
3. An unknown `fan_mode` / `fan_speed` string (e.g. `"Standard"`, `"auto"`, `null`) must use the baseline period, never 0 or a crash. Pinned in Task 1 and Task 3 unit tests.
4. The robot's pose at phase exactly 0 and 1 (and slightly out of range from float drift) must stay on the path, not jump off-canvas. Pinned in Task 3 `RobotPathTest`.
5. A tile grid with several running ACs and a cleaning robot must not recompose per frame. Pinned in Task 2 and Task 4 `KindIconRecompositionTest`.

---

### Task 1: `AcIcon` — wall unit with airflow

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/icons/AcIcon.kt`
- Test: `core/designsystem/src/test/kotlin/com/gekkotron/nidoo/designsystem/icons/AcIconTest.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gekkotron/nidoo/designsystem/icons/IconsSmokeTest.kt` (add a smoke test)

**Interfaces:**
- Produces:
  - `enum class AcMode { COOL, HEAT, DRY, FAN, AUTO }`
  - `fun acModeColor(mode: AcMode, colors: NidooColors, neutral: Color): Color`
  - `fun airflowPeriodMs(fanMode: String?): Int`
  - `@Composable fun AcIcon(mode: AcMode?, fanMode: String? = null, animate: Boolean = true, modifier: Modifier = Modifier)` — `mode == null` means off.

- [ ] **Step 1: Write the failing tests** in `AcIconTest.kt` (plain JUnit, no Robolectric):

```kotlin
@Test fun `each mode takes its theme colour`() {
    val c = NightGlassColors; val n = Color.White
    assertEquals(c.water, acModeColor(AcMode.COOL, c, n))
    assertEquals(c.heat, acModeColor(AcMode.HEAT, c, n))
    assertEquals(c.water, acModeColor(AcMode.DRY, c, n))
    assertEquals(n, acModeColor(AcMode.FAN, c, n))
    assertEquals(c.connected, acModeColor(AcMode.AUTO, c, n))
}
@Test fun `airflow speed follows the fan mode, baseline for anything unknown`() {
    assertEquals(2400, airflowPeriodMs("silence")); assertEquals(2400, airflowPeriodMs("Quiet"))
    assertEquals(2000, airflowPeriodMs("2")); assertEquals(1600, airflowPeriodMs("medium"))
    assertEquals(1300, airflowPeriodMs("4")); assertEquals(1000, airflowPeriodMs("high"))
    assertEquals(1800, airflowPeriodMs(null)); assertEquals(1800, airflowPeriodMs("auto")); assertEquals(1800, airflowPeriodMs("Standard"))
}
```

And in `IconsSmokeTest`:

```kotlin
@Test fun `the AC draws off and in every mode, still and animated, in both themes`() {
    compose.setContent {
        Row {
            for (theme in ThemeChoice.entries) NidooTheme(theme) {
                AcIcon(null)
                AcMode.entries.forEach { AcIcon(it, "high"); AcIcon(it, null, animate = false) }
            }
        }
    }
    compose.waitForIdle()
}
```
(If `ThemeChoice` has more than two entries, iterate `NIGHT_GLASS` and `IVOIRE` only.)

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*AcIconTest*' --tests '*IconsSmokeTest*'`
Expected: compilation failure, `AcMode` / `AcIcon` unresolved.

- [ ] **Step 3: Implement `AcIcon.kt`**

`airflowPeriodMs` takes over the table of today's private `fanPeriodMs` in `KindIcon.kt` (lower-cased: silence/silent/quiet/low/"1" → 2400, "2" → 2000, medium/med/"3" → 1600, "4" → 1300, high/"5" → 1000, else 1800). Drawing, per spec "AC icon":
- Unit: rounded rect `(2.5,4)–(21.5,12.4)` r 2.6 via `surface`; grille `M5.5 7.3h9` stroke 1, alpha 0.55; LED circle `(18.2,7.3)` r 0.85 in the mode colour (neutral, alpha 0.3, when off).
- Vane: line `(6,12.4)→(18,12.4)` stroke 1.3. Running + animate: draw it with `scale(scaleX = 1f, scaleY = cos(2π·vanePhase), pivot = Offset(12f, 12.6f))`, `vanePhase` 0→1 linear over 3200 ms.
- Airflow (running, mode ≠ DRY): three paths at x 7, 12, 17, each `M x 14.2 c-1.1 1.6 1.1 3.2 0 4.8 s1.1 3.2 0 4.8`, stroke 1.5, round cap, colour = mode colour, `PathEffect.dashPathEffect(floatArrayOf(2.6f, 2.4f), phase = -10f * flowPhase - k * 10f / 3f)` for k = 0,1,2, `flowPhase` 0→1 linear over `airflowPeriodMs(fanMode)`. Fade toward the bottom: draw the three into a layer (`drawIntoCanvas { saveLayer }` or `drawWithContent`-free equivalent) and finish with a `Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = 13f, endY = 24f)` rect with `BlendMode.DstIn`.
- Dry: three drops (`M x 14.6 c.8 1.1 1.3 1.8 1.3 2.4 a1.3 1.3 0 0 1-2.6 0 c0-.6.5-1.3 1.3-2.4z`, build with `PathParser().parsePathString(...).toPath()`) translated down `7 · p` with alpha `p<0.15 ? p/0.15 : 1-(p-0.15)/0.85`, where p = (dropPhase + k/3) mod 1, `dropPhase` 0→1 over 1400 ms, `FastOutLinearInEasing`.
- `animate == false` while running: vane flat, airflow drawn solid (no dash) with the fade, drops at p = 0.4.
- Only create the infinite transitions when `mode != null && animate`; hold them as `State<Float>` and read `.value` inside the `IconCanvas` lambda only.

- [ ] **Step 4: Run the tests**

Run: same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(designsystem): a wall-unit AC icon whose airflow shows the mode`

---

### Task 2: Wire the AC icon into Home cards

**Files:**
- Modify: `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/ui/KindIcon.kt` (`IconSpec.Climate`, `iconSpec`, `ClimateIcon`, remove `fanPeriodMs`)
- Modify: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/ui/IconSpecTest.kt:99-102`
- Modify: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/ui/KindIconRecompositionTest.kt:39-48`

**Interfaces:**
- Consumes: `AcMode`, `AcIcon` from Task 1.
- Produces: `IconSpec.Climate(val mode: AcMode?, val fanMode: String?)` (replaces `running: Boolean`; `mode == null` means off/unavailable); `internal fun acMode(hvacMode: String?): AcMode`.

- [ ] **Step 1: Update the tests first**

In `IconSpecTest`, replace lines 99-102 with:

```kotlin
assertEquals(IconSpec.Climate(mode = null, fanMode = null), spec(Kind.CLIMATE, null))
assertEquals(IconSpec.Climate(mode = null, fanMode = "silence"), spec(Kind.CLIMATE, StateValue.Climate(mode = "off", target = 21.0, current = 22.0, fanMode = "silence")))
assertEquals(IconSpec.Climate(mode = AcMode.COOL, fanMode = "high"), spec(Kind.CLIMATE, StateValue.Climate(mode = "cool", target = 21.0, current = 22.0, fanMode = "high")))
assertEquals(IconSpec.Climate(mode = null, fanMode = "high"), spec(Kind.CLIMATE, StateValue.Climate(mode = "cool", target = 21.0, current = 22.0, fanMode = "high"), available = false))
assertEquals(IconSpec.Climate(mode = AcMode.HEAT, fanMode = null), spec(Kind.CLIMATE, StateValue.Climate(mode = "heat", target = 21.0, current = 19.0)))
assertEquals(IconSpec.Climate(mode = AcMode.DRY, fanMode = null), spec(Kind.CLIMATE, StateValue.Climate(mode = "dry", target = null, current = null)))
assertEquals(IconSpec.Climate(mode = AcMode.FAN, fanMode = null), spec(Kind.CLIMATE, StateValue.Climate(mode = "fan_only", target = null, current = null)))
assertEquals(IconSpec.Climate(mode = AcMode.AUTO, fanMode = null), spec(Kind.CLIMATE, StateValue.Climate(mode = "heat_cool", target = 21.0, current = 21.0)))
```

Add a separate test `an AC that is on without a mode still runs, as auto`: `assertEquals(AcMode.AUTO, acMode(null))`, `assertEquals(AcMode.AUTO, acMode("auto"))`, `assertEquals(AcMode.COOL, acMode("COOL"))`.

In `KindIconRecompositionTest`, change the AC test to `KindIcon(IconSpec.Climate(mode = AcMode.COOL, fanMode = "high"))` and trace `"AcIcon"` instead of `"ClimateIcon"` (rename the test: `the running AC airflow animates without recomposing every frame`).

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :feature:home:testDebugUnitTest --tests '*IconSpecTest*' --tests '*KindIconRecompositionTest*'`
Expected: compilation failure on `IconSpec.Climate(mode = …)`.

- [ ] **Step 3: Implement**

`iconSpec`: `Kind.CLIMATE -> IconSpec.Climate(mode = if (card.available && card.on) acMode(climate?.mode) else null, fanMode = climate?.fanMode)`. `acMode`: lower-cased `cool`→COOL, `heat`→HEAT, `dry`→DRY, `fan_only`→FAN, anything else (incl. null) → AUTO. `ClimateIcon(spec, animate, m)` becomes a one-line call to `AcIcon(spec.mode, spec.fanMode, animate, m)`; delete `fanPeriodMs` and the now-unused imports. Leave `NidooGlyph.FAN` and `SNOWFLAKE` in place (other code may use them). Update the KDoc on `IconSpec.Climate`.

- [ ] **Step 4: Run the tests**

Run: same as Step 2, then `./gradlew :feature:home:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(home): the AC card shows a wall unit blowing air in its mode's colour`

---

### Task 3: Robot icon — cleaning the room

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/icons/RobotPath.kt`
- Modify (rewrite): `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/icons/VacuumIcon.kt`
- Test: `core/designsystem/src/test/kotlin/com/gekkotron/nidoo/designsystem/icons/RobotPathTest.kt`
- Modify: `IconsSmokeTest.kt` (the existing vacuum test covers the new `DOCKED` automatically via `VacuumMotion.entries`)

**Interfaces:**
- Produces:
  - `enum class VacuumMotion { IDLE, CLEANING, RETURNING, DOCKED }`
  - `internal data class RobotPose(val center: Offset, val headingDeg: Float)`
  - `internal object RobotPath { val ROWS: List<Offset>; val LENGTH: Float; fun poseAt(t: Float): RobotPose; fun trailTo(t: Float): List<Offset> }` — ROWS = `(5,5.5) (19,5.5) (19,10) (5,10) (5,14.5) (19,14.5) (19,19) (5,19)`, LENGTH = 69.5; `t` is clamped to 0..1; heading 0° = +x, 90° = +y.
  - `internal fun rowPeriodMs(fanSpeed: String?): Int`
  - `VacuumIcon(motion, fanSpeed = null, accent = null, animate = true, modifier)` — signature unchanged.

- [ ] **Step 1: Write the failing tests** in `RobotPathTest.kt`:

```kotlin
@Test fun `the robot starts top-left facing right and ends bottom-left facing left`() {
    assertEquals(RobotPose(Offset(5f, 5.5f), 0f), RobotPath.poseAt(0f))
    assertEquals(RobotPose(Offset(5f, 19f), 180f), RobotPath.poseAt(1f))
}
@Test fun `it turns down at the end of the first row`() {
    val p = RobotPath.poseAt((14f + 2.25f) / 69.5f)
    assertEquals(19f, p.center.x, 1e-3f); assertEquals(7.75f, p.center.y, 1e-3f); assertEquals(90f, p.headingDeg, 1e-3f)
}
@Test fun `out-of-range phases stay on the path`() {
    assertEquals(RobotPath.poseAt(0f), RobotPath.poseAt(-0.01f))
    assertEquals(RobotPath.poseAt(1f), RobotPath.poseAt(1.01f))
}
@Test fun `the trail holds every corner passed plus the robot's position`() {
    assertEquals(listOf(Offset(5f, 5.5f)), RobotPath.trailTo(0f))
    val t = (14f + 4.5f + 7f) / 69.5f
    assertEquals(listOf(Offset(5f, 5.5f), Offset(19f, 5.5f), Offset(19f, 10f), Offset(12f, 10f)), RobotPath.trailTo(t).map { Offset(round(it.x * 1000) / 1000, round(it.y * 1000) / 1000) })
    assertEquals(RobotPath.ROWS, RobotPath.trailTo(1f))
}
@Test fun `the round takes ten brush periods, baseline for unknown speeds`() {
    assertEquals(8000, rowPeriodMs(null)); assertEquals(8000, rowPeriodMs("Standard"))
    assertEquals(11000, rowPeriodMs("quiet")); assertEquals(6000, rowPeriodMs("turbo")); assertEquals(4500, rowPeriodMs("max"))
}
```

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*RobotPathTest*'`
Expected: compilation failure, `RobotPath` unresolved.

- [ ] **Step 3: Implement `RobotPath.kt` and rewrite `VacuumIcon.kt`**

`rowPeriodMs` = 10 × today's `brushPeriodMs` table (silent/silence/quiet/eco/low → 1100, turbo/high → 600, max/max+/max_plus → 450, else 800). Drawing, per spec "Robot icon":
- Room: rounded rect `(1.5,2)–(22.5,22.5)` r 3, stroke 1, neutral alpha 0.28, always.
- Robot (helper `drawRobot(pose, color, fillAlpha = 0.35f)`): disc r 3.1 fill + stroke 1.3; bumper arc on the front (sweep −45°..45° of r 2.9, stroke 1.3); puck r 1.05 filled; rotated by `pose.headingDeg` about `pose.center`.
- CLEANING: `phase` 0→1 linear over `rowPeriodMs(fanSpeed)`, restart. Trail = `polygon(RobotPath.trailTo(phase), close = false)` stroked 4.4, round cap/join, tint alpha 0.16; then the robot at `poseAt(phase)`. Still pose: phase 0.5.
- RETURNING: dock `(18,16.5)–(22,21.5)` r 1 via `surface` in neutral; robot heading 39°, from `(6.5,8.5)` to `(19.5,19)`; `p` 0→1 over 3000 ms with keyframes (0 at 0 ms, 1 at 2250 ms `FastOutSlowInEasing`, 1 at 3000 ms). Still pose: p = 0.5.
- DOCKED: dock filled 0.22 with outline in `NidooTheme.colors.connected` (stroke 1.4); robot neutral at `(19.5,19)`, fill 0.22; bolt `M12.3 8 L10 12 h2.5 l-.8 3.8 L14.4 11.5 H11.9 L12.8 8z` in `connected`, alpha breathing 0.4↔0.9 over 1800 ms reverse (still: 0.9).
- IDLE: robot neutral, fill 0.22, at `poseAt(0f)`, no trail.
- Tint: `accent ?: neutral` for CLEANING and RETURNING. All animated `State<Float>`s read only in the draw lambda; no transition created when `!animate` or for IDLE.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`. Expected: PASS (incl. `IconsSmokeTest`'s vacuum test over all four motions).

- [ ] **Step 5: Commit** — `feat(designsystem): the robot icon cleans its room in rows and charges in its dock`

---

### Task 4: Wire the robot into Home cards

**Files:**
- Modify: `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/ui/KindIcon.kt` (`vacuumMotion`, `iconSpec` vacuum branch, the `IconSpec.Vacuum` draw branch)
- Modify: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/ui/IconSpecTest.kt:107-110`
- Modify: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/ui/KindIconRecompositionTest.kt`

**Interfaces:**
- Consumes: `VacuumMotion.DOCKED`, `VacuumIcon` from Task 3.

- [ ] **Step 1: Update the tests first**

`IconSpecTest` line 108 becomes `assertEquals(IconSpec.Vacuum(motion = VacuumMotion.DOCKED, fanSpeed = "balanced"), spec(Kind.VACUUM, StateValue.Vacuum("docked", 80, "balanced")))`, and add:

```kotlin
assertEquals(IconSpec.Vacuum(motion = VacuumMotion.IDLE, fanSpeed = null), spec(Kind.VACUUM, StateValue.Vacuum("paused", 50, null)))
assertEquals(IconSpec.Vacuum(motion = VacuumMotion.IDLE, fanSpeed = "turbo"), spec(Kind.VACUUM, StateValue.Vacuum("cleaning", 60, "turbo"), available = false))
```

`KindIconRecompositionTest`: add `the cleaning robot drives without recomposing every frame` — same shape as the AC test, `KindIcon(IconSpec.Vacuum(VacuumMotion.CLEANING, "turbo"))`, tracing `"VacuumIcon"`.

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :feature:home:testDebugUnitTest --tests '*IconSpecTest*' --tests '*KindIconRecompositionTest*'`
Expected: FAIL — docked maps to IDLE; an unavailable cleaning robot maps to CLEANING.

- [ ] **Step 3: Implement**

`vacuumMotion(state, available)`: unavailable → IDLE; `"cleaning"` → CLEANING, `"returning"` → RETURNING, `"docked"` → DOCKED, else IDLE. Draw branch: accent (`warning` on dark, neutral otherwise) only for CLEANING and RETURNING; DOCKED and IDLE pass `null`. Update the `IconSpec.Vacuum` KDoc.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :feature:home:testDebugUnitTest :core:designsystem:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Look at it on the running emulator** (already booted, no launch needed): `./gradlew :app:installDebug`, open the app on the demo house, start the vacuum and switch the AC through cool/heat/off; take `adb exec-out screencap -p` shots of both cards and check them against the spec.

- [ ] **Step 6: Commit** — `feat(home): the robot card follows docked, cleaning and returning`

---

### Task 5: README animated images

**Files:**
- Create: `docs/images/ac.svg`, `docs/images/robot.svg`
- Modify: `README.md` (new section after the Status paragraph, before `## Modules`)

**Interfaces:**
- Consumes: the geometry from the spec (same shapes as Tasks 1 and 3).

- [ ] **Step 1: Write `docs/images/ac.svg`** — `viewBox="0 0 360 200"`, a Night Glass card (rounded rect r 24, gradient `#1D2B55`→`#0B1020`, border white 9 %), the AC drawn on the 24 grid scaled ×5 at the left, the name "Salon" and a state label at the right. One 12 s loop of four 3 s windows: Cool (`#58E1FF`), Heat (`#FF7A9A`), Dry (drops, `#58E1FF`), Off. Each window is a `<g>` whose opacity keyframes are 1 in its window and 0 outside (0.3 s crossfade); the airflow uses `stroke-dashoffset` keyframes, the vane `transform: scaleY` keyframes (`transform-box: view-box`), drops `translateY` + opacity. A `<title>` for accessibility. Card "on" tint (amber 16 %→4 %) during the three running windows.

- [ ] **Step 2: Write `docs/images/robot.svg`** — same card, "Roborock" label. 14 s loop: Cleaning (8 s: robot moves with CSS `offset-path: path('M5 5.5H19V10H5V14.5H19V19H5')` + `offset-rotate: auto` inside the ×5 scaled group, trail via `stroke-dasharray: 70; stroke-dashoffset` 70→0), Returning (3 s: translate into the dock), Docked (3 s: mint dock, breathing bolt).

- [ ] **Step 3: Check both render** — `xmllint --noout docs/images/*.svg` (expected: no output), then open both in Chrome (or the brainstorm companion) and watch one full loop each; confirm no element is clipped and the state labels match what is drawn.

- [ ] **Step 4: Add the README section**

```markdown
## Living icons

<p align="center">
  <img src="docs/images/ac.svg" width="49%" alt="The AC icon blowing cool, warm and dry air, then switching off">
  <img src="docs/images/robot.svg" width="49%" alt="The robot vacuum cleaning a room row by row, returning to its dock and charging">
</p>

Home cards are drawn, not emoji: the AC's airflow takes its mode's colour, the robot cleans its room row by row and charges in its dock.
```

- [ ] **Step 5: Commit** — `docs(readme): animated AC and robot icons`
