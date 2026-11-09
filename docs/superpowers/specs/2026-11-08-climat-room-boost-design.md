# Climat room banner and boost: design

Date: 2026-11-08 · Author: Gekkotron

Spans two repos:

- **Oikos-os** (`~/superconductor/projects/Oikos-os`, the hub, commit `742147d5`): the climate engine in `crates/nidoo-climate` and `crates/nidoo/src/climate`, specified in Oikos-os `docs/superpowers/specs/2026-10-22-climate-engine-design.md` (below "engine spec").
- **Nidoo-app** (this repo): the Android app.

## 1. Intent

The hub's climate engine ("Climat") keeps each room on its learned schedule, using the room thermometer to nudge the AC/heater setpoint until the room reaches the target (room-thermometer correction, `nidoo-climate/src/feedback.rs`).

Sometimes the user wants a room warmer (or cooler) for a while, **without taking over from Climat**. Today the only way from the phone is to change the AC setpoint directly. The hub treats that as a hand change on that one device: it holds the raw AC number, freezes the correction, and drops the hold at the next scheduled change. From the user's side, "the system lowered it again automatically".

What the user wants: **"put the living room at 22°"**. Climat takes 22° as the room's target for a while and keeps doing its job: it reads the thermometer and drives the AC to get the *room* to 22°.

## 2. Goals

1. A **room boost**: a temporary room target, reached by the room thermometer through the existing correction, not pushed raw to the AC.
2. A boost **lasts a duration the user picks** in the app: **1 h, 1 h 30 or 2 h** (user's call). It ends by itself after that, or earlier if cancelled or if the house goes away (goal 3).
3. A running boost **ends when the house goes away** (presence away or holiday starts). It does **not** come back when someone returns. A boost **started while away** runs, to warm or cool the room before coming home.
4. The app shows a **Climat banner** at the top of a room (mockup option A, §6) with the room temperature, Climat's target and why, and a **Boost** button. While a boost runs, the banner shows it and offers **Cancel**.
5. The app sends boosts **over MQTT** (user's call), so it works the same at home and through the gateway.

## 3. Non-goals

- No change to hand changes: touching the AC (on the device, or through the app's existing climate sheet) still makes a per-device hold, exactly as today (engine spec §7).
- No schedule editing, proposal approval, eco or presence settings in the app. Those stay on the hub's web screen.
- No widget, voice or dashboard changes in the app.
- No new mode control in the boost: a boost sets a temperature, and the mode stays Climat's.

## 4. Anti-goals

- **The banner must never show where Climat isn't running.** A user who doesn't use Climat (no climate engine, or a room with no active zone) sees no trace of it.
- **A boost must never outlive leaving.** Forgetting a boost when going out must not heat an empty house: the leave ends it. Only a boost deliberately started while away runs in an empty house, and it is time-limited.
- **The app must never write a retained climate document itself.** The hub owns `override` and `decision`. The app only sends commands and reads what the hub publishes.

## 5. Hub changes (Oikos-os)

### 5.1 What a whole-zone override means from now on

Today a whole-zone override (`Override { entity: None, origin: User }`, created by `POST /climate/zones/{id}/override`, the web stepper and the MCP `set_climate_override` tool) sends its setpoint **straight to every device** and skips the correction (`decide.rs::zone_override`, test `no_correction_under_zone_override`).

New behavior: a whole-zone override with a setpoint is a **boost**. It **replaces the schedule's target** for the zone. The room-thermometer correction then runs against that target, exactly as it does for a scheduled target (offset, warm-up, limits). (My call: change the existing room-wide hold instead of adding a second kind, so the web stepper and MCP get the same behavior and there's one concept, not two.)

- A whole-zone override with **only a mode** (no setpoint) keeps today's behavior: the schedule's target in that mode.
- A whole-zone override with setpoint `off`: no correction applies to "off". It behaves as today.
- If the zone has no thermometer, or correction is disabled for it, the boost target goes to the devices as today: the correction's own fallbacks already cover this.
- Per-device hand holds under a boost behave as they do under a scheduled target today: the held device keeps its value, the others follow the boosted target, and the correction freezes when every device is held (`decide.rs`, tests `held_device_keeps_its_value`, `all_held_freezes`).

### 5.2 Priority

The engine spec §5.1 tree today runs `safety → pending → zone override → rules (holiday, eco, away, empty, schedule)`. **The order stays as it is.** A live boost beats holiday, away, room eco, the empty-room setback and the schedule. What's new is that **leaving ends a boost**:

- **Leaving ends a running boost.** When the house *goes* away (presence home → away) or a holiday *starts*, the engine removes every live whole-zone override that was set **before** that moment, republishes `override`, and the zone falls back to away/holiday. The boost doesn't come back when someone returns.
- **A boost set while away runs (user's call: to pre-heat the room on the way home).** A boost created while the house is already away or on holiday is accepted and beats away/holiday until its duration runs out or it's cancelled. Coming home doesn't end it. It then runs as a normal boost.
- The rule is "a boost dies at the first leave *after* it was set". How the hub tells (compare the override's creation time with the time of the last home → away / holiday-start transition, or detect the transition from the previous retained `decision.home`) is the builder's call. Removal must be idempotent, so a repeated pass or a hub restart must not remove a boost set after the transition.
- A boost **beats** room eco and the empty-room setback. (My call: the user asked for the boost after those were set, and stepping out of the living room for ten minutes must not undo it.)
- Safety and pending: unchanged. Safety beats a boost, and a pending zone ignores it.

Per-device hand holds are unchanged (they still hold their device under eco/away as today, test `hand_hold_survives_eco`).

### 5.3 How long a boost lasts

The app always sends the duration the user picked, as `minutes` (60, 90 or 120). The hub holds the boost for exactly that long (`until = now + minutes`), as `OverrideRequest::into_override` already does. It is **not** cut short by the next schedule change or by `manual_hold_max_min`, which only govern hand changes. The web stepper and MCP keep sending their own durations as today.

### 5.4 MQTT command topic

A new **non-retained** command topic per zone, which the hub subscribes to:

- Topic: `<climate prefix>/zone/<zone>/hold/set`, for example `nidoo/climate/zone/living/hold/set`. The exact name is the builder's call. It must not end in `/config` and must not collide with the retained document topics (`topics.rs::ZoneDoc`).
- Payload to set: the JSON of the existing `OverrideRequest` (`nidoo-climate/src/view.rs`): `{"setpoint": 22, "minutes": 90}`. Same validation as HTTP: a setpoint and/or mode, plus exactly one of `minutes` or `until`. A payload without a duration is rejected and logged, as HTTP rejects it today.
- Empty payload: clear the zone's whole-zone override (same as `DELETE …/override`). Per-device hand holds stay.
- Invalid payload, unknown zone, a zone that isn't `active`, or climate disabled: ignore and log. There is no reply topic. The app learns the outcome from the retained `override`/`decision` documents (or their absence).
- The command goes through the same code path as the HTTP `set_climate_override`/`clear_climate_override`, so validation and limits are identical.

### 5.4b Edge rules (settled after review)

- **Existing overrides on upgrade:** whole-zone overrides already retained at upgrade time take boost semantics right away. No migration. (My call: they're short-lived holds.)
- **Override creation time:** the "set before the leave" test in §5.2 needs to know when a whole-zone override was created. If `Override` has no such field, add an optional `since` (Unix seconds, `#[serde(default)]`). An override without it (retained before the upgrade) counts as set before any later leave.

### 5.5 Reason text

A boost decision's `reason` reads as a boost, not a raw override, e.g. `"Boost to 22 °C until 18:00"`. The web screen's labels for `Source::Override` can stay as they are. Changing them is the builder's call.

### 5.6 Tests (hub)

Pure-function tests in `decide.rs` for each row: the boost replaces the schedule target and the correction runs (replaces `no_correction_under_zone_override`); going away and a holiday starting remove a boost set before them; a boost set while away (or on holiday) beats away/holiday and survives coming home; removal is idempotent across passes and a hub restart; a boost beats eco and the empty-room setback; safety still beats a boost; a pending zone ignores a boost; a mode-only override is unchanged. Engine tests for the MQTT topic: set, clear, the default end, and ignoring invalid input.

## 6. App changes (Nidoo-app)

### 6.1 Reading Climat

The app subscribes, on whichever connection it has (direct or gateway), to the retained climate documents the hub already publishes:

- `<prefix>/zone/+/settings`: `state` (`off`/`pending`/`active`), `entities` (device unique ids), `sensor` (thermometer unique id), `manual_hold_max_min`.
- `<prefix>/zone/+/decision`: `reading`, `setpoint`, `mode`, `source`, `reason`, `at`, `adjust`.
- `<prefix>/zone/+/override`: the list of overrides. The whole-zone one (`entity: null`) is the boost.

The prefix defaults to `nidoo/climate` (the hub's default, Oikos-os `config.rs`). (My call: a constant for now, not a setting. Making it configurable is the builder's call if it's cheap.)

These documents follow the hub's serde shapes in `nidoo-climate/src/doc.rs`. The app decodes only the fields it uses and ignores unknown fields.

### 6.2 Which room shows the banner

A room shows the Climat banner when an **active** zone's devices or `sensor` include an entity the app shows in that room. Entity ids on both sides are the discovery `unique_id`. The zone's devices are the keys of `decision.entities`: the hub fills these with the zone's *resolved* devices (`decide.rs::targets`), so this works when `settings.entities` is `null` too ("the area's climate entities no other zone claims", `hub.rs::zone_devices`). If `decision.entities` is empty or missing, fall back to `settings.entities`. Zones in `off` or `pending`, rooms with no matching zone, and a hub that publishes no climate documents show nothing. (My call: pending zones get no banner, because the hub ignores holds there, so Boost would do nothing.)

If one zone's entities sit in more than one app room, every one of those rooms shows the same zone's banner. (My call. It's rare, and the banner is about the zone.)

### 6.3 The banner (mockup option A)

The mockup is `.superpowers/brainstorm/58399-1794153683/content/climat-card-placement.html`, option A: a wide card at the top of the room screen, above the device grid.

- Room temperature (`decision.reading`), large.
- Target (`decision.setpoint`), and a short line saying why: schedule / eco / away / room empty / safety / boost, from `decision.source`, plus the hub's `reason` where it helps (for example the next scheduled target). Exact copy is the builder's call, in English and French.
- **Boost** button. While a boost is live: "Boost 22° until 18:00" and a **Cancel** button instead.
- The AC and other devices stay in the grid below, unchanged.
- Stale or missing decision: show what's known, no fake numbers, and keep Boost available (the hub decides).
- **What counts as a boost:** a live override (`until` > now) with `entity: null` and a numeric `setpoint`. The banner then shows "Boost 22° until …" with Cancel. Any other live whole-zone override (mode only, or `"off"`) shows as a plain "Hold until …" chip with the same Cancel. Cancel always clears the whole-zone override and leaves per-device hand holds alone.
- **Safety:** when `decision.source` is `safety`, Boost is hidden and the banner shows the hub's reason (safety beats any boost, so offering one would only end in a timeout).

### 6.3b Document shapes

From `nidoo-climate/src/doc.rs`: `setpoint` is a JSON number (°C) or the string `"off"`. `at` and `until` are Unix seconds. Times shown in the app ("until 18:00") use the device's local timezone. (My call: the phone and the house share a timezone in practice.)

### 6.4 The boost sheet

Opens from **Boost**. It's a bottom sheet in the app's existing style (`ControlSheet.kt`):

- −/+ stepper starting at the current target. Step 0.5 °C (my call), clamped to the **intersection** of the zone devices' discovery `min_temp`/`max_temp` (app `Capabilities.minTemp`/`maxTemp`). The hub clamps again anyway.
- Quick chips: target −1 / current target +1 / +2 (my call; the mockup shows 21/22/23). The existing `climatePresets` override is not reused.
- **Duration chips: 1 h · 1 h 30 · 2 h** (user's call), one selected. 1 h is selected by default (my call).
- A line: "Room at 22° until 18:30 · ends if you leave", where the end time is now + the chosen duration, updated live as the chip changes.
- Sends `{"setpoint": <v>, "minutes": <60|90|120>}`.
- Sending: a **Start boost** button publishes to the zone's hold command topic (my call: with two choices to make, an explicit button replaces the climate sheet's auto-send debounce). While a boost runs, the sheet opens on its current target and duration, and **Update** re-sends it with a fresh duration from now. Cancel publishes an empty payload.
- The sheet shows the hub's confirmed state (`override`/`decision`) once it arrives. If nothing changes within a few seconds, a short error ("Climat didn't take the boost"). The timeout and the copy are the builder's call.

### 6.4b Through the gateway

Nothing to change in Athena-HttpMqttGateway. The app already subscribes to `nidoo/#` and publishes commands to arbitrary topics through `GatewayTransport.publish`. The gateway scopes keys by port and destination, not by topic. Retained documents reach the app the same way discovery does today.

### 6.5 Demo house

The demo house (`core/demo/DemoHouse.kt`) gets a living-room zone with retained `settings`/`decision` documents and a fake hub reply to the hold command, so the banner and the boost can be seen without a hub.

### 6.6 Tests (app)

Unit tests: decoding the three documents (including unknown fields and missing optional fields), room ↔ zone matching (active/pending/off, sensor-only match, no climate at all), and the banner's state (schedule, boost, away, stale). Compose UI tests for the banner and the sheet (boost, cancel). Encode test for the command payloads.

## 7. Decided by the user

- Boost means **the room reaches the target by its thermometer**, with Climat still in control.
- **Going away ends a boost**, and it doesn't resume on return.
- **The app sends a duration: 1 h, 1 h 30 or 2 h.**
- **A boost started while away runs** (beats away), so the room can be ready on arrival.
- The app UI is **option A**: a Climat banner on top of the room.
- The banner shows **only where Climat is actually used**.
- The app talks to the hub **over MQTT**, not HTTP.

## 8. Claude's calls (check these)

- Changing the existing whole-zone override into a boost (web stepper and MCP change too) rather than adding a second override kind.
- 1 h is the default duration chip. A boost runs its full duration and isn't cut short by the next schedule change or `manual_hold_max_min`.
- A boost beats room eco and the empty-room setback.
- Holds already retained at upgrade switch to boost behavior with no migration.
- Zone ↔ room matching uses `decision.entities` keys + `sensor`, falling back to `settings.entities`.
- Boost hidden while safety is in charge. Non-temperature whole-zone holds show as "Hold" with the same Cancel.
- Stepper clamped to the intersection of the devices' ranges. Times in the phone's timezone.
- Pending zones show no banner.
- Prefix `nidoo/climate` as a constant in the app.
- Step 0.5 °C, chips around the current target. An explicit Start boost button instead of auto-send.

## 9. Left to the builder

The exact command topic name, the reason/label copy, the confirmation timeout, the module layout in both repos, and the order of work (hub first is the natural order: the app's boost does nothing until the hub reads the command topic).
