# Animated robot and AC icons (app + README)

Status: draft, 2026-11-19. Parent spec: `2026-10-01-oikos-device-icons-design.md` (duotone 24-unit grid, motion rules). Picked in the brainstorm companion: AC direction A, robot direction A (mockup kept under `.superpowers/brainstorm/`, not committed).

## Intent

Make the two "living" Home icons, the robot vacuum and the AC, nicer to look at, and show them off as animated images in the README (which has no visuals today).

- **Goals:** the AC reads as an air conditioner (not a generic fan) and shows its mode at a glance; the robot reads as "cleaning a room"; both appear, animated, at the top of the README.
- **Non-goals:** no change to models, state mapping, commands or the control sheets; no other icon changes; no screen recordings of the app.
- **Anti-goals:** icons that re-measure or recompose the card every frame (keep motion in the draw phase); motion that keeps running when the system has animations off; a README image that looks blurry or heavy.

## Approaches (README images)

1. **Animated SVG files** in `docs/images/` — picked. Crisp at any size, a few KB each, GitHub animates them in `<img>`. Trade-off: a separate copy of the geometry that will not follow later changes to the Kotlin icons.
2. GIFs recorded from the real app on the emulator — real cards, but the 24 dp icons are tiny and GIF is blurry.
3. GIFs rendered frame by frame from the Compose code — always in sync, but needs a new frame-rendering harness.

In the app there is one sensible way: draw on the existing `IconCanvas` like `VacuumIcon.kt`.

## AC icon (replaces the spinning fan)

New `AcIcon(mode, fanMode, running, animate)` in `core/designsystem/.../icons/`, used by `ClimateIcon` in `KindIcon.kt`.

- **Unit:** rounded rect `(2.5,4)–(21.5,12.4)`, radius 2.6, duotone; a faint grille line `M5.5 7.3h9` (stroke 1, alpha .55); a status LED dot at `(18.2,7.3)` r .85 in the mode colour (neutral at alpha .3 when off).
- **Vane:** line `M6 12.4h12` (stroke 1.3). While running it flips up and down (scaleY 1 → -1 → 1 about y 12.4) over 3.2 s, ease-in-out.
- **Airflow (running, not dry):** three wavy strokes at x 7, 12, 17 from y 14.2 to 24 (`c-1.1 1.6 1.1 3.2 0 4.8` repeated), stroke 1.5, dashed 2.6 / 2.4, the dash offset flowing downward one period per fan-speed period (today's `fanPeriodMs`), staggered by a third; faded out toward the bottom with a vertical alpha gradient.
- **Dry mode:** the airflow is replaced by three drops falling 7 units and fading, 1.4 s, staggered.
- **Mode colour:** cool → `water`, heat → `heat`, dry → `water`, fan only → content colour, auto/other → `connected`. Taken from the theme so Ivoire works unchanged.
- **Off:** unit only, vane closed, no airflow, LED dim. **Animations off:** the running pose drawn still (airflow solid, vane flat).

## Robot icon (replaces the brush-and-dust robot)

`VacuumIcon` keeps its signature; `VacuumMotion` gains `DOCKED` (state `docked`); `IDLE` covers idle, paused, error and unknown.

- **Room:** rounded rect `(1.5,2)–(22.5,22.5)`, radius 3, stroke 1, alpha .28, always drawn.
- **Robot:** a disc r 3.1 (fill .35, stroke 1.3), a front bumper arc, a centre puck r 1.05; it faces its direction of travel.
- **Cleaning:** the robot follows the rows `M5 5.5H19V10H5V14.5H19V19H5` in 8 s (linear, restart) and leaves a trail on the same path (stroke 4.4, round, alpha .16) that grows with it. Accent: the existing active tint (`warning` on dark, card content colour otherwise).
- **Returning:** a dock `(18,16.5)–(22,21.5)` in the bottom-right corner; the robot drives from `(6.5,8.5)` into the dock in 2.25 s (ease-in-out), holds, loops every 3 s.
- **Docked:** the robot sits in the dock, the dock outline is `connected` (mint) and a small charging bolt in the room breathes (alpha .4 ↔ .9, 1.8 s).
- **Idle:** the robot drawn still at the start of the rows, neutral.
- **Animations off:** each state's still pose (cleaning shows the robot mid-room with a half trail).
- `fanSpeed` keeps setting the pace: the row period scales with today's `brushPeriodMs` (800 ms → 8 s baseline).

## README images

- `docs/images/ac.svg` and `docs/images/robot.svg`: self-contained animated SVGs (CSS keyframes, no scripts, no external refs) on a Night Glass card, about 360 × 200 each, cycling through the states so one image shows everything: AC cool → heat → dry → off; robot cleaning → returning → docked. Labels in the image name the current state.
- README: a short "Living icons" section right after the intro, the two images side by side (`<img width="49%">`).

## Tests

- Unit tests for the pure parts: mode → colour role, vacuum state → `VacuumMotion` (incl. `docked`), the path position for a given phase.
- Screenshot references updated if `NidooIconsPreview` sheets include these icons.

## Left to the builder

Exact keyframe easings within the stated durations; the bolt path; SVG card styling details, as long as it matches the Night Glass tokens above.
