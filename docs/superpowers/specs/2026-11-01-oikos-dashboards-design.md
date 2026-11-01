# Dashboards in the app — design

**Date:** 2026-11-01
**Status:** Design approved 2026-11-01, pending plan
**Owner:** Gekkotron

## Intent & why

Oikos-OS lets the user build named dashboards in its web UI: a grid of
widgets (lights, covers, climate, sensors, room groups, flow buttons,
energy figures, history charts, cameras, locks, water heaters). On the
phone today the only "dashboard" is Home. The user wants to **see and
use the dashboards they built on the web, inside the app**. The web UI
stays the only place to build or edit them.

The hub already publishes every dashboard as a retained MQTT message,
and the app already subscribes to `oikos/#`, so the data reaches the
phone today. It is just ignored, or rather, misread (see "Routing fix").

## Goals

- **Switcher in the Home title.** The Home title becomes `House ▾`.
  Tapping it opens a bottom sheet listing **House** first, then the
  user's dashboards in the hub's `order` (the web sidebar's order).
  Picking one swaps Home's content for that dashboard; the title shows
  its name followed by `▾`.
- **House is today's Home, unchanged:** room chips, hero, search,
  settings, every card and behaviour as now.
- **A dashboard renders its widgets in the web's order** in a 2-column
  grid (user's requirement: "keep the order").
  - "The web's order" is the order the web UI uses on a phone or
    tablet: **reading order of the desktop cells**. Each widget's cell
    on the 6-column grid is computed by the hub's `layout()`
    (`oikos-core/src/dashboard/layout.rs`). Widgets are then sorted by
    `(row, col)`, as `oikos-web/src/dashboards/grid.rs` does for
    narrow grids.
  - This is **not** the raw `widgets` list order. Dragging a widget on
    the desktop changes its `pos`, not its place in the list.
  - The app ports `layout()` to Kotlin (`commonMain`). Positioned
    widgets keep their cell unless it runs past the grid or covers an
    earlier widget; the others flow into the first free cells after the
    previous one. The hub's `layout.rs` tests are ported as the
    contract.
  - Gaps from the desktop grid are not reproduced on the phone, as on
    the web.
- **Widgets behave like the matching Home cards:** tap toggles a light,
  long-press opens its controls sheet, and so on (see the widget table).
- **Live:** a dashboard changed, renamed, reordered or deleted on the
  web updates the switcher and the open dashboard without reopening the
  app.
- **The choice is kept per phone:** the app reopens on the last
  dashboard picked on that phone, like the theme.
- **Works locally and remotely,** the same way the existing MQTT and
  HTTP features (flows, history) do.

## Non-goals

- Creating, editing, reordering, renaming or deleting a dashboard, or
  any widget, from the app. The web UI stays the author's tool.
- Reproducing the desktop gaps, or 4- or 6-column grids. The phone gets
  the web's phone layout: `pos` decides the order, not empty space.
- **Lock and camera widgets** in this pass. They render as a
  placeholder card (see below) and get a second pass of their own: the
  app has no lock device type, and live camera frames are new ground.
- Swipe navigation between dashboards: rejected as not discoverable.
- A new bottom tab: rejected in favour of the title switcher. The tab
  bar stays Home / Energy / Security / Flows.

## Anti-goals (failure even if it "works")

- **House changes in any way:** a missing chip, a moved hero, a
  regression in search or customize.
- **The `▾` shows when the user has no dashboards of their own.** It
  must be hidden, and Home must look exactly as it does today.
- **A widget the app can't resolve crashes or blanks the screen.** This
  covers an unknown entity, a deleted flow, or an unknown widget kind or
  span from a newer hub. It must render a muted "unavailable" card
  instead, as the web does.
- **Dashboard payloads leak into entity overrides** (see "Routing fix").

## The hub contract (as of Oikos-OS `main`, 2026-11-01)

- **Topic:** `<ui_prefix>/dashboards/<id>`, with `ui_prefix` =
  `oikos/ui`, so `oikos/ui/dashboards/<id>`. Retained. Exactly one
  segment after `dashboards/`.
- **Delete:** an empty retained payload removes the dashboard.
- **House is not on MQTT.** The hub synthesizes it (id `house`) only in
  its HTTP API. The app has its own House: today's Home.
- **Payload** (`oikos-core/src/dashboard/model.rs`), JSON:
  ```json
  {
    "id": "evening", "name": "Soirée", "order": 1,
    "show_header": true,
    "widgets": [
      { "id": "w1", "kind": "light", "span": "2x1",
        "config": { "entity_id": "…", "title": "Salon" },
        "pos": { "row": 0, "col": 0 } }
    ]
  }
  ```
  - `show_header` is omitted when false.
  - `pos` is optional. The app uses it only to compute the order (see
    Goals).
  - `kind` is one of `light`, `cover`, `climate`, `lock`, `sensor`,
    `room_group`, `flow_button`, `energy_stat`, `history_chart`,
    `camera`, `water_heater`.
  - `span` is one of `1x1`, `2x1`, `2x2`, `1x2`, `4x2`, `6x3`.
  - `config` is untagged, and its shape depends on the kind:
    - `{entity_id, range}`: history chart. Range is `1h`, `6h`, `24h`,
      `7d` or `30d`.
    - `{entity_id, title?}`: light, cover, climate, lock, sensor, water
      heater.
    - `{area_id, title?}`: room group.
    - `{flow_id, title?}`: flow button.
    - `{metric, title?}`: energy stat. Metric is `today_kwh`,
      `week_kwh`, `month_kwh`, `grid_power_now`, `pv_power_now` or
      `self_consumption_pct`.
    - `{cameras: [id…], title?}`: camera.
- **Ids match the app's (verified in the hub code).**
  - `entity_id` is the discovery unique id, the same key as the app's
    `Entity.uniqueId` and as `oikos/ui/<unique_id>`. The web's widget
    library writes `entity_id: e.id`, and the hub's `EntityView` sets
    `id: entity.unique_id` (`oikos-api/src/views.rs`). The hub's test
    fixtures use ids like `"light.a"`; those are only fixtures.
  - `area_id` is an `Area.id` from `oikos/ui/_areas`: the library
    writes `area_id: area.id`, and the app reads the same document.
  - No mapping layer is needed.
- **Run a flow:** `POST /api/v1/flows/{id}/run` (the hub route exists;
  the app today only calls `/flows/{id}/enabled`).

## Routing fix (prerequisite)

`MqttDiscoverySource` sends every `oikos/ui/<rest>` topic to
`onOverride(rest, …)`. So `oikos/ui/dashboards/evening` is decoded as an
entity override for a unique id `"dashboards/evening"`. The same happens
to the hub's other `oikos/ui/*` documents (`ambiances/<id>`, `_home`,
`_types`). `_areas` is already handled before the override branch.

The rule, in this order (my call):

1. `oikos/ui/_areas` → areas, unchanged.
2. `oikos/ui/dashboards/<id>`, with exactly one non-empty segment →
   the dashboards sink.
3. Any other rest that **starts with `_` or contains `/`** → ignored.
   It is not an override.
4. Everything else → `onOverride`, unchanged.

Rule 3 is structural rather than a list of known documents, so a new
hub document type can't leak into overrides. The cost is that an
entity whose unique id contains `/` or starts with `_` can't carry an
override. None of the hub's own documents uses any other shape. Z2M
unique ids (`0x…_light_zigbee2mqtt`) don't use these characters either.
Unverified: whether any retained override in the user's house uses
such an id. The builder logs each ignored rest once, so a device check
would show it.

Tests must prove that a dashboard, ambiance, `_home` and `_types`
payload each leave `overrides` untouched.

## Shape of the change

- **A `:core:dashboards` KMP module**, mirroring `:core:flows`:
  - a `DashboardDto` and its decoding: lenient, unknown keys ignored,
    an unknown `kind` or `span` kept as "unsupported" rather than
    failing the whole dashboard;
  - a `DashboardsSink` (`onRetained(id, payload?)`, `markLinkUp`,
    `clear`) fed by `MqttDiscoverySource`;
  - a `DashboardsRepository` exposing the dashboards sorted by
    `order`, then name, then id, and a settled flag, so the switcher
    can tell "none" from "not loaded yet".
- **The selected dashboard id** goes in `AppPreferences`, next to the
  theme. Unknown or missing means House. A dashboard deleted on the
  hub resets the selection to House.
- **Home** (`:feature:home`) gains the title switcher and the bottom
  sheet, and renders either today's House content or a dashboard grid.
  Search and settings stay reachable on a dashboard. Room chips and the
  hero belong to House only.
- **`show_header: true`** puts the House header (greeting/date, status
  pill, chips row as on House) above the widgets. The exact subset is
  left to the builder; match the web's "House greeting, chips and
  weather" as far as the app has those pieces.
- **The demo house** (`:core:demo`) publishes one or two sample
  dashboards, so demo mode and screenshots show the feature.

## Widgets

The span maps to the 2-column phone grid. Wider spans clamp to the full
row, as on the web:

| Span | Phone cells |
|---|---|
| `1x1` | 1 column × 1 row |
| `2x1` | 2 × 1 (full width) |
| `1x2` | 1 × 2 |
| `2x2` | 2 × 2 |
| `4x2` | full width × 2 |
| `6x3` | full width × 3 |

| Kind | Renders as | Interaction |
|---|---|---|
| `light`, `cover`, `climate`, `sensor`, `water_heater` | The same card Home uses for that entity, titled with the widget's `title` when set | As on Home: tap action, long-press for the controls sheet |
| `room_group` | Home's room group card for that area (shutters and lights auto-grouped per room) | As on Home |
| `flow_button` | A card with the flow's name (or the widget's `title`) and a Run button. Disabled with the reason when the hub says the flow can't run | Run calls `POST /api/v1/flows/{id}/run`, shows pending, and reports failure the way the Flows tab reports toggle failures |
| `energy_stat` | Label and value ("Today · 12.4 kWh", "Solar now · 1.2 kW", "Self-consumption · 64 %"), from the data the Energy tab already has. "—" when unknown | Tap opens the Energy tab |
| `history_chart` | A small chart of the entity over the widget's range, reusing the History feature's data and chart | Tap opens the full History screen for that entity |
| `lock`, `camera` | Placeholder card: icon, title (or entity/camera name), "Open on the web" | None in this pass |
| unknown kind, or unresolved target | Muted "Unavailable" card with the widget's title if any | None |

## Constraints

- The KMP rules of the ongoing migration: new core logic goes in
  `commonMain` (as `:core:flows` does), with JVM-specific transport in
  `jvmMain`.
- Theme: everything draws from `OikosTheme` tokens and works in Night
  Glass and Ivoire. Add screenshot previews for a dashboard grid in both
  themes to the existing `:core:designsystem` screenshot suite, or to a
  feature-level one if the components live in `:feature:home`.
- Accessibility as on Home: cards keep their TalkBack labels and actions,
  and the switcher announces "Dashboard: Soirée, double-tap to change".
- **Strings in EN and FR.** Dashboard and widget titles are user data and
  are never translated.
- **No hub change is required.**
- **Retained dashboards are not cached** by the app's offline entity
  cache, like flows. The hub's retained messages repopulate them on
  connect, and the switcher waits for "settled" before deciding
  whether to show `▾`. On a cold start with a saved dashboard, Home
  shows that dashboard's title with a loading state until settled. Only
  then does it fall back to House if the dashboard is gone, so the app
  never flashes House and then jumps.

## Left to the builder

- The exact composable structure and where the grid lives
  (`:feature:home` vs a new `:feature:dashboards`).
- How `energy_stat` reads its figures from the existing energy pipeline.
- The history mini-chart's rendering details. It reuses Vico, as the
  History screen does.
- The empty-dashboard state (a dashboard with no widgets): a short
  "Nothing here yet — add widgets on the web" line is enough.
