# Device controls: lights and switches in the Android status-bar panel

Author: Gekkotron · 2026-11-02 · Status: draft

## Intent

Android 11+ has a system **Device controls** panel, reached from the status bar / Quick Settings. Its
"Sélectionnez l'appli pour laquelle ajouter des commandes" picker currently offers Google Home and
Xiaomi Home. We want **Nidoo** to show up in that list so the user can add their lights and switch them
from the status bar without opening the app.

The user's words: the moment is **lights**; **on/off** is the need; **dimming** "could be nice".
After review the user added **switches / plugs** to v1 (plain on/off).

## Goals

- Nidoo appears in the system Device controls app picker (Android 11+, including Xiaomi HyperOS, which
  uses the same system picker).
- The picker lists every light **and every switch / plug** in the house; the user ticks the ones they want.
- Each light tile shows its name, its room, and its real on/off state.
- **Tap** turns the light on or off.
- **Horizontal drag** on the tile sets brightness, for lights that support it; the tile shows the %.
- Lights without brightness — including switches shown as lights via Customize "Show as a light" —
  are tap-only.
- Works with the Nidoo app closed.
- **Switches / plugs** (`Kind.SWITCH`, not shown as lights) are on/off tiles: tap toggles.
- **Long-press** opens Nidoo.

## Non-goals (v1)

- Any kind other than lights and switches (shutters, climate, alarm, buttons, scenes…). The design should not prevent adding
  them later, but none ship now.
- Whole-room tiles ("Salon — all lights"). *My call, not discussed in depth:* individual lights only;
  room tiles are a likely follow-up.
- Colour or effects. The system tiles have no colour picker; long-press into the app covers it.
- A long-lived background connection kept open while the panel is shown (see "Live updates").

## Anti-goals

- A tap that goes the wrong way. Never send a `Toggle`; always send the explicit target state from the
  system action (the widget follows the same rule — see `TileActions`).
- Draining battery or holding a broker connection after the panel closes.
- Showing a stale state as if it were live without any refresh attempt.

## Constraints

- `minSdk` is 28; the Device controls API (`android.service.controls`) is API 30+. The service class is
  `@RequiresApi(30)`. On API 28–29 nothing changes and nothing crashes (the system never looks for it).
- The system API speaks `java.util.concurrent.Flow.Publisher`. Bridge from Kotlin `Flow` with
  `kotlinx-coroutines-jdk9` (`asPublisher()`), version-aligned with the existing `coroutines = 1.11.0`
  entry in `gradle/libs.versions.toml` (adding it is approved). `java.util.concurrent.Flow` is native
  on API 30, so no desugaring concern as long as every reference to it stays inside `@RequiresApi(30)`
  code.
- Module boundaries as in the rest of the app: the feature module never touches a transport; `:app`
  provides the door to the house.
- Attribution: Gekkotron only (commits use `-c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com`).
- English and French strings (`values/` and `values-fr/`).

## How it fits the existing code (decided)

The home-screen widget already solves "read state and send commands while the app is closed". Reuse it
rather than building a second path:

- **Door to the house:** `com.gekkotron.nidoo.feature.widget.WidgetEntities`, implemented in `:app` by
  `connection/HeadlessEntities.kt` and provided in `di/WidgetModule.kt`.
  - `catalog()` → the picker's list (live, else fresh from the broker, else cache when offline).
  - `refresh(ids)` → current entities + states for the chosen controls (live source when the app is
    connected, otherwise a short headless session that closes before returning).
  - `send(id, command)` → sends and waits for confirmation; returns the confirmed `Entity` + `EntityState`.
- **Entity model:** a switch / plug is `Entity.kind == Kind.SWITCH`, state `StateValue.OnOff(on)`.
  A light is `Entity.kind == Kind.LIGHT`; dimmable when `capabilities.brightness`.
  Switch-as-light entities already arrive as `Kind.LIGHT` (see `KindMapper`) without brightness.
  `Entity.name` already includes the Customize override name.
  State is `StateValue.Light(on, brightness /* 0..255 */)`.
- **Commands:** `Command.TurnOn(brightness = 0..255)` and `Command.TurnOff` (`core/model/.../Command.kt`).

### New pieces

1. **A new module `:feature:controls`** (Android library, same convention plugin as `:feature:widget`)
   holding:
   - `NidooControlsService : ControlsProviderService`, `@RequiresApi(30)`, declared in the module
     manifest with `android:permission="android.permission.BIND_CONTROLS"`, `exported="true"`, and the
     `android.service.controls.ControlsProviderService` intent-filter, label = app name.
   - A **pure mapper** (no Android types, unit-testable) from `Entity` + `EntityState?` + a failure
     reason to a small control model (id, title, zone, kind toggle/toggle-range, on, brightness %,
     status), and from a system action back to a `Command`.
   - A thin Android layer that turns that model into `Control` objects: lights → `ToggleTemplate` /
     `ToggleRangeTemplate` with `DeviceTypes.TYPE_LIGHT`; switches → `ToggleTemplate` with
     `TYPE_OUTLET` when `deviceClass == "outlet"`, else `TYPE_SWITCH`.
2. **Wiring:** the service gets its dependencies the same way the widget's entry points do — a
   `ControlsComponent` handed out by `NidooApplication` through a provider interface (the
   `WidgetComponentProvider` / `PushComponentProvider` pattern). It wraps the existing
   `WidgetEntities` singleton; no new transport code.

### Behaviour

| System call | What Nidoo does |
|---|---|
| `createPublisherForAllAvailable()` | `catalog()`, keep lights and switches, emit one stateless `Control` per entity: title = name, zone = room (area) name, **no structure** (the app has exactly one connection profile — one house — so there is nothing to group by). Completes. |
| `createPublisherFor(ids)` | Emit right away from whatever is cheap (cache / last known), then `refresh(ids)` and emit stateful controls. If the app process has a live source (`live()` non-null, regardless of whether an activity is visible), also collect its state flow and keep emitting changes; stop collecting when the subscriber cancels. |
| `performControlAction(id, BooleanAction)` | `newState` true → `TurnOn()` (no brightness), false → `TurnOff`. Answer the consumer `RESPONSE_OK` **immediately** (do not wait for `send`, which can take up to ~20 s on a cold headless session), then emit the confirmed state from `SendResult.Confirmed`, or an error status if the send fails / is not confirmed. |
| `performControlAction(id, FloatAction)` | Brightness % (range 0–100, step 1) → `TurnOn(brightness = round(pct × 255 / 100))`; 0 % → `TurnOff`. Same answer-then-emit flow. |

- **Control id** = `Entity.uniqueId` unchanged (the widget uses the same id).

- **Tile states:** switch on/off from `StateValue.OnOff.on`; light on/off from `StateValue.Light.on`; brightness % from `brightness / 255`.
  Unavailable entity → `STATUS_ERROR`-style status with a short "Unavailable" text; entity removed from
  the house → `STATUS_NOT_FOUND` **only when `RefreshResult.Ok.complete` is true** (discovery settled);
  an id missing while `complete` is false shows as unavailable, never NOT_FOUND; offline / auth failed /
  not configured → `STATUS_ERROR` with a short reason text (same three reasons as `Failure`).
- **Long-press / app intent:** an explicit `PendingIntent` launching `MainActivity` (Home) —
  there is no entity or Home deep link (`nidoo://` only covers alert/history/settings) and none is added
  in v1. `FLAG_IMMUTABLE`, and a distinct request code per control so the system does not coalesce them.
- **Lock screen:** lights and switches are `authRequired = false` (my call, matches Google Home behaviour) so they
  work from a locked phone when the user has enabled the system "control from locked device" setting.

### Live updates

*My call:* no long-lived headless connection in v1. Panel sessions last seconds; one refresh when the
panel opens plus the confirmed state after each action covers the use case. When the app's own
connection is live, stream from it for free. Revisit if a light changed by someone else while the panel
is open turns out to matter.

## Left to the builder

- Exact class / file names, the `ControlsComponent` shape, and whether `WidgetEntities` is renamed or
  moved to a shared place (keep the change small; renaming is optional).
- How "emit something cheap first" is sourced (entity cache vs widget snapshot vs nothing until refresh).
- Status text wording (EN + FR), and the icon/colour, if any, beyond the system defaults.
- Debouncing rapid slider moves if the system sends many `FloatAction`s.
- Demo mode: whatever is simplest and doesn't break (showing demo lights or nothing are both fine).
- Tests: unit tests for the pure mapper (entity → control model, action → command, edge cases: no
  state, unavailable, removed, brightness rounding, 0 %), plus a Robolectric or instrumented smoke test
  of the service if practical.

## Later (not v1)

Room tiles; shutters (`RangeTemplate`, `TYPE_BLINDS`); climate (`TemperatureControlTemplate`); scenes /
flows (`StatelessTemplate`); alarm (`authRequired = true`); turning on with the Customize "on preset".
