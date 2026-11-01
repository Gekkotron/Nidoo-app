# Dashboards in the App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home's title becomes a `House ▾` switcher that opens the dashboards built in the Oikos-OS web UI and renders their widgets, in the web's order, with the same cards and actions as Home.

**Architecture:** A new KMP module `:core:dashboards` holds the wire model, a Kotlin port of the hub's `layout()`, and a `DashboardsRepository` fed by `MqttDiscoverySource` (which also stops misreading `oikos/ui/*` documents as overrides). `:feature:home` gets a separate `DashboardViewModel` + pure `DashboardUiBuilder` that turn a dashboard into tiles. Entity tiles reuse `HomeUiBuilder` cards, so `HomeViewModel` keeps handling every command. `HomeScreen` swaps its body for the tile grid when a dashboard is selected. House code paths stay as they are.

**Tech Stack:** Kotlin 2 / KMP (`oikos.kmp.jvm.library`), kotlinx.serialization, coroutines/Flow, Hilt, Jetpack Compose, Robolectric screenshot tests, Vico (via `HistoryLineChart` in `:core:designsystem`).

**Spec:** `docs/superpowers/specs/2026-11-01-oikos-dashboards-design.md` (approved 2026-11-01). Read it before any task.

**Hub reference (read-only):** `Gekkotron/Oikos-os` `main` @ 180dfcf: `crates/oikos-core/src/dashboard/{model,layout}.rs`, `crates/oikos-web/src/dashboards/{grid,room_group,energy_stat,flow_button}.rs`. Clone it into the scratchpad if you need it: `git clone --depth 1 git@github.com-gekkotron:Gekkotron/Oikos-os.git`.

## Global Constraints

- Worktree: a separate worktree (`../Oikos-dashboards`), branch `feature/dashboards`. Never touch the main checkout `../Oikos`.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`. End every message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never write the owner's real name anywhere.
- New core logic goes in `commonMain` (as `:core:flows` does). JVM-only transport goes in `jvmMain`. KMP modules test with `:<module>:jvmTest`, Android modules with `:<module>:test`.
- Every colour, type style and dimension comes from `OikosTheme` / `OikosDimens`. Everything must work in Night Glass and Ivoire.
- User-facing strings go in `values/strings.xml` (EN) **and** `values-fr/strings.xml` (FR). Dashboard names, widget titles, room and entity names are user data and are never translated.
- No hub change. Topic `oikos/ui/dashboards/<id>` is retained, and an empty payload means delete. House is never on MQTT: the app's House is today's Home.
- Dashboards are **not** written to or restored from the entity cache (like flows).
- The House id is the constant `"house"` (the hub refuses it as a dashboard id, so it cannot collide).
- Tile keys must be unique within a grid even when the hub sends duplicate widget ids or two widgets on one entity.
- An unknown kind, span, metric, entity, area or flow never crashes and never blanks the screen. It renders the muted "Unavailable" tile.
- CI: add `:core:dashboards:jvmTest` and `:core:dashboards:koverXmlReport` to the `core` job in `.github/workflows/ci.yml`.
- **Deviation from the spec (flow button):** the spec says the button is "disabled with the reason when the hub says the flow can't run". But the hub's retained `oikos/automation/<id>` message holds only the flow definition. The `invalid` reason exists only in the HTTP `FlowView`. So the app disables the button when the flow is off (`enabled == false`, which the hub refuses too). Any other refusal (HTTP 422: invalid or busy) is reported after the tap as `widget_flow_refused`.

## Review Focus

1. **Offline cold start with a saved dashboard.** The link never comes up, so the repository never settles. The user must still see the dashboard's saved name with a "Waiting for the hub…" line, and still be able to pick House. The `▾` stays visible whenever the selection is not House. *(Task 7: `resolve` tests; Task 9: screen test.)*
2. **A room-group or entity tile whose card is not in House's current state.** For example, House is on the Favourites chip and the dashboard shows the Kitchen lights group. A tap, a group send, a Retry and Customize must still reach the members. `HomeViewModel` looks cards up in House state only today. *(Task 8: `HomeViewModel` fallback tests.)*
3. **Widgets on entities that Home deliberately hides.** Energy-role sensors, security devices and hidden entities must still render on a dashboard. A widget on a companion entity (`partOf != null`) renders Unavailable. *(Task 7: builder tests.)*
4. **Duplicate widget ids, or two widgets on one entity, in one dashboard.** A `LazyVerticalGrid` key collision crashes the app. *(Task 7: unique-key test.)*
5. **A dashboard deleted on the web while the link was down.** Its empty retained message never arrives. It must disappear after the replay window, and an open selection must fall back to House with the preference reset. *(Task 2: confirm-window test; Task 7: `resolve` reset test.)*

## Execution Waves

There are 9 tasks. The user's rules are at most 2 subagents in parallel, and confirmation before more than 4 subagents in total. So execution needs the user's go-ahead on these waves:

| Wave | Tasks (parallel) | Suggested model |
|---|---|---|
| 1 | Task 1 (model + layout), Task 4 (flow run) | sonnet, haiku |
| 2 | Task 2 (repository), Task 5 (preferences) | sonnet, haiku |
| 3 | Task 3 (routing + wiring), Task 6 (energy stats) | sonnet, sonnet |
| 4 | Task 7 (builder + VM), then Task 8 (HomeViewModel fallback) can run beside it | sonnet, sonnet |
| 5 | Task 9 (UI + nav + demo + screenshots) | sonnet |

## File Structure

```
core/dashboards/                                   NEW KMP module
  build.gradle.kts                                 copy of core/flows/build.gradle.kts (no :core:transport)
  src/commonMain/kotlin/com/gekkotron/oikos/dashboards/
    Dashboard.kt          wire DTO + domain model + lenient decode
    DashboardLayout.kt    port of hub layout() + readingOrder()
    DashboardsSink.kt     MQTT → repository port
    DashboardsRepository.kt
  src/commonTest/kotlin/com/gekkotron/oikos/dashboards/
    DashboardDecodeTest.kt  DashboardLayoutTest.kt  DashboardsRepositoryTest.kt
core/flows/…/FlowsClient.kt, FlowsRepository.kt, jvmMain/HttpFlowsClient.kt   + run(id)
core/source/…/MqttDiscoverySource.kt              routing fix + dashboardsSink
core/source/src/jvmTest/…/MqttDiscoverySourceDashboardsTest.kt   NEW
core/data/…/AppPreferences.kt                     + selected dashboard
core/demo/…/DemoHouse.kt                          + two sample dashboards
feature/energy/…/presentation/EnergyStats.kt      NEW, public: live figures + N-day house kWh
feature/home/
  build.gradle.kts                                + :feature:energy
  …/presentation/DashboardModels.kt               NEW: screen state, tiles, EnergyMetric
  …/presentation/DashboardSelection.kt            NEW: pure resolve()
  …/presentation/DashboardUiBuilder.kt            NEW: pure build()
  …/presentation/DashboardViewModel.kt            NEW
  …/presentation/HomeViewModel.kt                 card fallback params
  …/ui/DashboardSwitcher.kt                       NEW: title button + bottom sheet
  …/ui/DashboardTiles.kt                          NEW: tile composables + LazyGridScope.dashboardTiles()
  …/ui/HomeScreen.kt                              header switcher, dashboard body, sheet lookup
app/…/di/DashboardsModule.kt                      NEW
app/…/di/AppModule.kt, connection/EntitySourceSession.kt   pass dashboardsSink
app/…/navigation/OikosNavHost.kt                  onOpenEnergy
settings.gradle.kts, .github/workflows/ci.yml, app/build.gradle.kts
```

---

### Task 1: `:core:dashboards` model, decoding and layout port

**Files:**
- Create: `core/dashboards/build.gradle.kts`, `…/dashboards/Dashboard.kt`, `…/dashboards/DashboardLayout.kt`
- Modify: `settings.gradle.kts` (add `include(":core:dashboards")` after `:core:flows`), `.github/workflows/ci.yml` (core job: `:core:dashboards:jvmTest`, `:core:dashboards:koverXmlReport`)
- Test: `core/dashboards/src/commonTest/…/DashboardDecodeTest.kt`, `DashboardLayoutTest.kt`

**Interfaces:**
- Produces (package `com.gekkotron.oikos.dashboards`):
  ```kotlin
  const val HOUSE_DASHBOARD_ID = "house"
  data class Dashboard(val id: String, val name: String, val order: Long, val showHeader: Boolean, val widgets: List<Widget>)
  data class Widget(val id: String, val kind: WidgetKind, val span: WidgetSpan, val config: WidgetConfig, val pos: Cell?)
  data class Cell(val row: Int, val col: Int)
  enum class WidgetKind(val wire: String?) { LIGHT("light"), COVER("cover"), CLIMATE("climate"), LOCK("lock"), SENSOR("sensor"),
      ROOM_GROUP("room_group"), FLOW_BUTTON("flow_button"), ENERGY_STAT("energy_stat"), HISTORY_CHART("history_chart"),
      CAMERA("camera"), WATER_HEATER("water_heater"), UNKNOWN(null) }
  enum class WidgetSpan(val wire: String?, val cols: Int, val rows: Int) { S1X1("1x1",1,1), S2X1("2x1",2,1), S2X2("2x2",2,2),
      S1X2("1x2",1,2), S4X2("4x2",4,2), S6X3("6x3",6,3), UNKNOWN(null,1,1) }
  enum class WidgetRange(val wire: String?) { H1("1h"), H6("6h"), H24("24h"), D7("7d"), D30("30d"), UNKNOWN(null) }
  /** Flat on purpose: the hub's config is untagged; each kind reads its own fields. */
  data class WidgetConfig(val entityId: String? = null, val areaId: String? = null, val flowId: String? = null,
      val metric: String? = null, val cameras: List<String> = emptyList(), val range: WidgetRange? = null, val title: String? = null)
  object DashboardJson { fun decode(id: String, payload: String): Dashboard }   // throws on unusable payload
  object DashboardLayout {
      const val GRID_COLUMNS = 6
      fun cells(widgets: List<Widget>): List<Cell>          // port of layout(), same index order as input
      fun readingOrder(widgets: List<Widget>): List<Widget> // stable sort of widgets by cells() (row, col)
  }
  ```

**Decode rules:** use `Json { ignoreUnknownKeys = true }` on a private `@Serializable` DTO where `kind`, `span` and `range` are `String?` and `config` is a `JsonObject?`. Then map:
- The topic `id` wins over the payload `id`.
- `name` missing → throw (the payload is unusable).
- `order` missing → `Long.MAX_VALUE`.
- `show_header` missing → false.
- An unknown kind, span or range string → `UNKNOWN`.
- A widget with no `id` gets `"#<index>"`.
- `title` blank → null.
- A non-object `config` → an empty `WidgetConfig`.
- A widget that is not a JSON object is dropped. The rest of the dashboard survives.

- [ ] **Step 1: Write the failing tests**

`DashboardDecodeTest`:
- `decodes the spec example` — payload from the spec's "Payload" block. Assert `Dashboard("evening","Soirée",1,true, listOf(Widget("w1", LIGHT, S2X1, WidgetConfig(entityId="…", title="Salon"), Cell(0,0))))`.
- `topic id wins over payload id` — `decode("a", """{"id":"b","name":"N","order":0,"widgets":[]}""").id == "a"`.
- `show_header omitted is false`.
- `history config keeps its range` — `{"entity_id":"t","range":"7d"}` → `WidgetConfig(entityId="t", range=WidgetRange.D7)`.
- `unknown kind span and range decode as UNKNOWN` — `kind:"gauge", span:"3x3", range:"90d"`.
- `cameras config` — `{"cameras":["front","back"],"title":"Cams"}` → `cameras == listOf("front","back")`.
- `missing name throws` — `assertFails { decode("a", """{"order":0,"widgets":[]}""") }`.
- `a malformed widget is dropped, the others survive` — `widgets:[42, {valid}]` → one widget.
- `unknown top-level keys are ignored`.

`DashboardLayoutTest` (ported from the hub's `layout.rs`; helper `w(id, span, pos: Pair<Int,Int>?)` with kind SENSOR):
- `unpositioned widgets flow like the css grid` — `[2x2, 1x1, 4x2, 1x1]`, no pos → cells `(0,0),(0,2),(1,2),(2,0)`.
- `positioned widgets keep their cell and leave gaps` — `ac(0,0), t(1,0), ac2(0,1)` → same cells.
- `a bad position falls back to the flow` — `a 1x1 (0,0)`, `b 2x1 (0,0)` (overlaps), `c 2x1 (0,5)` (runs past) → `(0,0),(0,1),(0,3)`.
- `empty rows are dropped` — `a 1x2 (0,0)`, `b 1x1 (4,3)`, `c 1x1 (7,0)` → `(0,0),(2,3),(3,0)`.
- `reading order sorts by row then column` — `x 1x1 (1,0)`, `y 1x1 (0,3)`, `z 1x1 (0,0)` → ids `z,y,x`.
- `reading order is not list order after a drag` — list `[a(0,1), b(0,0)]` → `b,a`.
- `unknown span lays out as 1x1`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:dashboards:jvmTest`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement `Dashboard.kt` and `DashboardLayout.kt`**

Port `layout()` exactly: the positioned pass, then the flow pass with a cursor (`first_free` scans `col + w <= 6` with `w = min(span.cols, 6)`), then empty-row compression. Port `Rect.overlaps` and `fits` as private helpers. Do not port `move_widget`, `resize_widget`, `append_widget` or `validate_layout`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:dashboards:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit**

`feat(dashboards): core model, lenient decoding and the hub's layout order`

---

### Task 2: `DashboardsRepository` and `DashboardsSink`

**Files:**
- Create: `core/dashboards/src/commonMain/…/DashboardsSink.kt`, `DashboardsRepository.kt`
- Test: `core/dashboards/src/commonTest/…/DashboardsRepositoryTest.kt`

**Interfaces:**
- Consumes: `DashboardJson.decode` (Task 1).
- Produces:
  ```kotlin
  interface DashboardsSink {            // same contract as FlowsSink; any thread
      fun onRetained(id: String, payload: String?)   // null/empty = deleted
      fun markLinkUp()
      fun clear()
  }
  data class DashboardsSnapshot(val isSettled: Boolean, val dashboards: List<Dashboard>, val version: Long = 0L)
  class DashboardsRepository(scope: CoroutineScope, settleAfter: Duration = 2.seconds,
      log: (String) -> Unit = {}, confirmAfter: Duration = 5.seconds) : DashboardsSink {
      val dashboards: StateFlow<DashboardsSnapshot>
  }
  ```

Mirror `FlowsRepository`'s state machine (`core/flows/…/FlowsRepository.kt`) without the in-flight and echo parts. That means an immutable state in one `MutableStateFlow`, a version bump per transform, the settle timer, and the `unconfirmed` set dropped after `confirmAfter`. A malformed payload is logged and leaves the previous value in place. Sort by `order`, then `name.lowercase()`, then `id`.

- [ ] **Step 1: Write the failing tests** (`runTest`, repository on `backgroundScope`)
- `retained message adds a dashboard`.
- `sorted by order then name then id` — orders `(2,"a")`, `(1,"b")`, `(1,"B")` with ids z, y, x → ids `x,y,z` (case-insensitive name tie, then id).
- `empty payload deletes`.
- `malformed payload keeps the previous value` — add `a`, then deliver `"{"` for `a` → still the first value; log called once.
- `not settled until link up plus settle delay` — `markLinkUp()`, `advanceTimeBy(1_999)` → not settled; `advanceTimeBy(2)` → settled.
- `any retained message settles`.
- `a dashboard deleted while offline is dropped after the replay window` — add `a`, `b`; `markLinkUp()`; replay only `a`; `advanceTimeBy(5_001)` → ids `[a]`.
- `clear empties and unsettles`.

- [ ] **Step 2: Run them to see them fail**: `./gradlew :core:dashboards:jvmTest` → FAIL.
- [ ] **Step 3: Implement both files.**
- [ ] **Step 4: Run the tests**: `./gradlew :core:dashboards:jvmTest` → PASS.
- [ ] **Step 5: Commit** `feat(dashboards): repository for the hub's retained dashboards`

---

### Task 3: Routing fix in `MqttDiscoverySource` and app wiring

**Files:**
- Modify: `core/source/build.gradle.kts` (`commonMain`: `api(project(":core:dashboards"))`), `core/source/src/jvmMain/…/MqttDiscoverySource.kt`
- Modify: `app/src/main/kotlin/com/gekkotron/oikos/connection/EntitySourceSession.kt`, `app/…/di/AppModule.kt`, `app/build.gradle.kts` (`implementation(project(":core:dashboards"))`)
- Create: `app/src/main/kotlin/com/gekkotron/oikos/di/DashboardsModule.kt`
- Test: `core/source/src/jvmTest/…/MqttDiscoverySourceDashboardsTest.kt`; adjust `EntitySourceSession` tests only if they call `defaultBuild` positionally.

**Interfaces:**
- Consumes: `DashboardsSink`, `DashboardsRepository` (Task 2).
- Produces:
  - `MqttDiscoverySource(…, flowsSink: FlowsSink? = null, dashboardsSink: DashboardsSink? = null)`.
  - `EntitySourceSession(…, flowsSink, dashboardsSink: DashboardsSink? = null, build = …)`.
  - `EntitySourceSession.defaultBuild(…, flowsSink, dashboardsSink)`.
  - A Hilt `@Singleton DashboardsRepository` built with `@ApplicationScope` scope and `log = { Log.w("OikosDashboards", it) }`.

**Routing, in this order** (spec, "Routing fix"). It sits inside `handle()`'s `when`, after the flow branch:
1. `topic == AREAS_TOPIC` → `onAreas` (unchanged).
2. `dashboardIdOf(topic)` non-null → `dashboardsSink?.onRetained(id, payload.ifEmpty { null })`. Here `dashboardIdOf` is `UI_PREFIX + "dashboards/"` followed by exactly one non-empty segment with no `/`.
3. `topic.startsWith(UI_PREFIX)` and the rest starts with `_` or contains `/` → ignored. Report one diagnostic per distinct rest (`"$topic: not an override, ignored"`), tracked in a `mutableSetOf<String>()`.
4. Other `UI_PREFIX` topics → `onOverride` (unchanged).

Restored (cached) dashboard messages are skipped like flows: `if ((flowId != null || dashboardId != null) && restored) return`. Ignored rests (rule 3) must not be kept in `payloads`, so the cache stops carrying them. Wire `dashboardsSink.clear()` and `markLinkUp()` in `init` exactly like `flowsSink`.

- [ ] **Step 1: Write the failing tests** (copy the harness from `MqttDiscoverySourceFlowsTest`; `RecordingDashboards : DashboardsSink`)
- `retained dashboard topic forwards id and payload` — `oikos/ui/dashboards/evening` → sink gets `"evening" to payload`.
- `empty dashboard payload forwards as delete`.
- `dashboard, ambiance, _home and _types leave overrides untouched`. Deliver an override-shaped JSON (`{"favorite":true}`) on `oikos/ui/dashboards/evening`, `oikos/ui/ambiances/soir`, `oikos/ui/_home` and `oikos/ui/_types`, plus a discovery config for an entity `light_a` with `oikos/ui/light_a` = `{"favorite":true}`. Assert that only `light_a` has `ui.favorite == true`, and that no entity or override key contains `dashboards`.
- `a nested dashboards topic is ignored` — `oikos/ui/dashboards/a/b` → sink empty, no override.
- `an ignored document is reported once` — deliver `oikos/ui/_home` twice → one diagnostic.
- `a plain override still applies` — regression for rule 4.
- `link up reaches the dashboards sink` and `building the source clears the dashboards sink`.
- `restored dashboard messages are not forwarded` — `source.restore(mapOf("oikos/ui/dashboards/x" to "{…}"))` → sink empty.

- [ ] **Step 2: Run them to see them fail**: `./gradlew :core:source:jvmTest --tests '*Dashboards*'` → FAIL.
- [ ] **Step 3: Implement the routing; wire `EntitySourceSession`, `AppModule.entitySourceSession(…, dashboards: DashboardsRepository, …)` and `DashboardsModule`.**
- [ ] **Step 4: Verify**: `./gradlew :core:source:jvmTest :app:compileDebugKotlin` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `fix(source): oikos/ui documents are not overrides; dashboards reach their repository`

---

### Task 4: Run a flow (`POST /api/v1/flows/{id}/run`)

**Files:**
- Modify: `core/flows/src/commonMain/…/FlowsClient.kt`, `FlowsRepository.kt`, `core/flows/src/jvmMain/…/HttpFlowsClient.kt`
- Test: `core/flows/src/jvmTest/…/HttpFlowsClientTest.kt`, `core/flows/src/commonTest/…/FlowsRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `FlowsClient.run(id: String): Result<Unit>`. It has a default body returning `Result.failure(UnsupportedOperationException("run"))`, so existing fakes keep compiling.
  - `HttpFlowsClient.run` posts `"{}"` to `<base>/api/v1/flows/<urlencoded id>/run`. 2xx → success; otherwise `FlowsHttpError(status)`. Map `NotAllowed` and `Unavailable` the same way `setEnabled` does.
  - `FlowsRepository.run(id: String): Result<Unit>` runs the client call in the repository's `scope` via `async { … }.await()`, so a cancelled caller does not abort the request. A non-cancellation exception becomes `Result.failure`. It changes no state.
- The hub answers `202` on success, and `422` when the flow is disabled, invalid or busy. Callers treat `FlowsHttpError(422)` as "refused".

- [ ] **Step 1: Write the failing tests**
  - `run posts to the run route` — URL `http://hub:8090/api/v1/flows/a%20b/run`, body `{}`.
  - `run maps 202 to success and 422 to FlowsHttpError` — `(exceptionOrNull() as FlowsHttpError).status == 422`.
  - `repository run delegates to the client and survives caller cancellation`.
- [ ] **Step 2: Run them to see them fail**: `./gradlew :core:flows:jvmTest` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Verify**: `./gradlew :core:flows:jvmTest :feature:automations:test` → PASS.
- [ ] **Step 5: Commit** `feat(flows): run a flow on the hub`

---

### Task 5: Remember the selected dashboard per phone

**Files:**
- Modify: `core/data/src/main/kotlin/com/gekkotron/oikos/data/AppPreferences.kt`
- Test: `core/data/src/test/kotlin/com/gekkotron/oikos/data/AppPreferencesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class DashboardChoice(val id: String, val name: String?)   // id "house" = House
  val AppPreferences.selectedDashboard: StateFlow<DashboardChoice?>  // member; null until the file is read
  suspend fun AppPreferences.setSelectedDashboard(id: String, name: String?)  // member
  ```
- Stored in `StoredPreferences` as `dashboardId: String = "house"` and `dashboardName: String? = null`. The name lets a cold start show the saved dashboard's title before the hub replays it.

- [ ] **Step 1: Write the failing tests**
  - `missing file selects House` — `DashboardChoice("house", null)`.
  - `dashboard choice round-trips` — set `("evening","Soirée")` → read back equal.
  - `an old file without the field selects House` — file `{"theme":"IVOIRE"}`.
  - `clearing the profile keeps the dashboard choice` (same pattern as the theme test).
- [ ] **Step 2: Run them to see them fail**: `./gradlew :core:data:test --tests '*AppPreferencesTest*'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the tests** → PASS.
- [ ] **Step 5: Commit** `feat(data): remember the selected dashboard on this phone`

---

### Task 6: `EnergyStats` (figures for the energy-stat widget)

**Files:**
- Create: `feature/energy/src/main/kotlin/com/gekkotron/oikos/feature/energy/presentation/EnergyStats.kt`
- Test: `feature/energy/src/test/…/presentation/EnergyStatsTest.kt`

**Interfaces:**
- Consumes: `EnergyFlowBuilder.build`, `EnergyToday.requests`, `HistoryClient.today` / `daily` (all existing and public).
- Produces (public, used by `:feature:home`):
  ```kotlin
  object EnergyStats {
      data class Live(val gridWatts: Double?, val solarWatts: Double?, val selfConsumption: Int?)
      fun live(entities: Map<String, Entity>, states: Map<String, EntityState>, link: LinkState, nowMillis: Long): Live
      /** House consumption over [days] (1 = since midnight); null when unknown. */
      suspend fun consumption(entities: Map<String, Entity>, states: Map<String, EntityState>, link: LinkState,
                              nowMillis: Long, history: HistoryClient, days: Int): Double?
  }
  ```
- `live`: build `EnergyFlowBuilder.build(…)`, then read `node("grid")?.value`, `node("solar")?.value` and `selfConsumption`. This is exactly what the Energy tab shows.
- `consumption`: for each request in `EnergyToday.requests(entities)`, fetch the value. With `days == 1`, use `history.today(ref, scale)`. Otherwise sum `history.daily(ref, days, scale).days.kwh`. Requests that fail are left out. Then feed the resulting map as the `today` argument of `EnergyFlowBuilder.build(…)` and return `node("house")?.todayKwh`. The builder's derived-house arithmetic then applies unchanged to any period, as the web's `consumption()` does.

- [ ] **Step 1: Write the failing tests** (entities: grid, solar and house power sensors with energy roles and paired kWh counters, as in `EnergyFlowBuilderTest`; a fake `HistoryClient`)
  - `live reads grid solar and self-consumption like the Energy tab` — compare against `EnergyFlowBuilder.build(...)` for the same inputs.
  - `today is the house node's today kWh` — fake `today` returns 12.4 for the house meter → `12.4`.
  - `week sums seven daily totals` — fake `daily(ref, 7, …)` returns 7 days of 1.5 → `10.5`.
  - `derived house subtracts export over the period`. No house role; solar daily total 20, grid import 5, grid export 8 → `17.0`.
  - `history off gives null`.
- [ ] **Step 2: Run them to see them fail**: `./gradlew :feature:energy:test --tests '*EnergyStatsTest*'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the tests** → PASS.
- [ ] **Step 5: Commit** `feat(energy): figures for dashboard energy widgets`

---

### Task 7: Dashboard presentation (models, selection, builder, ViewModel)

**Files:**
- Modify: `feature/home/build.gradle.kts` (+ `implementation(project(":feature:energy"))`; `:core:dashboards` and `:core:flows` arrive through `:core:source`'s `api`)
- Create: `feature/home/src/main/kotlin/com/gekkotron/oikos/feature/home/presentation/DashboardModels.kt`, `DashboardSelection.kt`, `DashboardUiBuilder.kt`, `DashboardViewModel.kt`
- Test: `feature/home/src/test/…/presentation/DashboardSelectionTest.kt`, `DashboardUiBuilderTest.kt`, `DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1–2 and 4–6; `HomeUiBuilder.cardFor`, `HomeUiBuilder.build`, `RoomFilter.Area`, `HistoryRefs.of`, `HistoryClient.series`.
- Produces:
  ```kotlin
  // DashboardModels.kt
  enum class EnergyMetric(val wire: String, val days: Int?) { TODAY_KWH("today_kwh",1), WEEK_KWH("week_kwh",7),
      MONTH_KWH("month_kwh",30), GRID_POWER_NOW("grid_power_now",null), PV_POWER_NOW("pv_power_now",null),
      SELF_CONSUMPTION_PCT("self_consumption_pct",null); companion object { fun of(wire: String?): EnergyMetric? } }
  sealed interface EnergyValue { data class Kwh(val v: Double); data class Watts(val v: Double); data class Percent(val v: Int) }
  data class EnergyFigures(val kwhByDays: Map<Int, Double> = emptyMap(), val live: EnergyStats.Live? = null)
  sealed interface TileContent {
      data class Entity(val card: CardUi) : TileContent
      data class Room(val title: String, val cards: List<CardUi>) : TileContent
      data class FlowButton(val flowId: String, val name: String, val enabled: Boolean) : TileContent
      data class EnergyStat(val metric: EnergyMetric, val title: String?, val value: EnergyValue?) : TileContent
      data class History(val entityId: String, val title: String, val range: WidgetRange, val unit: String?,
                         val points: List<HistoryPoint>?) : TileContent   // null = loading
      data class Placeholder(val kind: WidgetKind, val title: String?) : TileContent   // lock, camera
      data class Unavailable(val title: String?) : TileContent
  }
  data class TileUi(val key: String, val columns: Int, val fullWidth: Boolean, val rows: Int, val content: TileContent)
  data class DashboardUi(val id: String, val name: String, val showHeader: Boolean, val tiles: List<TileUi>) {
      fun card(id: String): CardUi?   // searches Entity cards and Room cards, members and companions included
  }
  sealed interface DashboardView { data object House; data class Loading(val name: String?); data class Shown(val ui: DashboardUi) }
  data class SwitcherEntry(val id: String, val name: String?)   // House: id "house", name null (localized by UI)
  data class DashboardScreenState(val switcherVisible: Boolean = false, val entries: List<SwitcherEntry> = emptyList(),
      val selectedId: String = HOUSE_DASHBOARD_ID, val view: DashboardView = DashboardView.House,
      val runningFlows: Set<String> = emptySet()) { fun card(id: String): CardUi? }
  sealed interface DashboardEvent { data class FlowRunFailed(val refused: Boolean) : DashboardEvent }

  // DashboardSelection.kt
  data class Resolution(val view: ResolvedView, val switcherVisible: Boolean, val resetToHouse: Boolean)
  sealed interface ResolvedView { data object House; data class Loading(val name: String?); data class Shown(val dashboard: Dashboard) }
  object DashboardSelection { fun resolve(choice: DashboardChoice?, snapshot: DashboardsSnapshot): Resolution }

  // DashboardUiBuilder.kt
  data class HouseData(val entities: Map<String, Entity>, val states: Map<String, EntityState>, val areas: List<Area>,
      val link: LinkState, val hubGroups: Map<String, HubGroup>, val historyEnabled: Boolean)
  data class TileExtras(val flows: List<FlowDto> = emptyList(), val energy: EnergyFigures = EnergyFigures(),
      val series: Map<Pair<String, WidgetRange>, List<HistoryPoint>> = emptyMap())
  object DashboardUiBuilder {
      fun build(dashboard: Dashboard, house: HouseData, extras: TileExtras, locale: Locale, labels: LabelText, nowMillis: Long): DashboardUi
      fun historyRange(range: WidgetRange): HistoryRange   // H1,H6,H24 → HistoryRange.H24; D7 → D7; D30 → D30
  }

  // DashboardViewModel.kt
  @HiltViewModel class DashboardViewModel internal constructor(source: EntitySource, dashboards: DashboardsRepository,
      flows: FlowsRepository, preferences: AppPreferences, history: HistoryClient, labels: LabelText,
      dispatcher: CoroutineDispatcher, refresh: Duration = 5.minutes, clock: () -> Long) : ViewModel() {
      @Inject constructor(source: EntitySource, dashboards: DashboardsRepository, flows: FlowsRepository,
          preferences: AppPreferences, history: HistoryClient, labels: LabelText, @DefaultDispatcher dispatcher: CoroutineDispatcher)
      val state: StateFlow<DashboardScreenState>
      val events: Flow<DashboardEvent>
      fun select(id: String)       // saves DashboardChoice(id, name of that dashboard or null for House)
      fun runFlow(id: String)
  }
  ```

**`resolve` rules:**

| Choice | Snapshot | View | Switcher | Reset |
|---|---|---|---|---|
| null (prefs not read) | any | `Loading(null)` | dashboards non-empty | no |
| `house` | any | `House` | dashboards non-empty | no |
| id found | any | `Shown` | true | no |
| id missing | not settled | `Loading(choice.name)` | true | no |
| id missing | settled | `House` | dashboards non-empty | **yes** |

**Builder rules.** Each widget comes in `DashboardLayout.readingOrder` order. Tile key = `"$index:${widget.id}"`, where `index` is the position in reading order.

Cells: `columns = min(span.cols, 2)` and `fullWidth = span.cols >= 4`. `rows = span.rows`, except `UNKNOWN`, which renders Unavailable at 1×1.

Content by kind:
- `light/cover/climate/sensor/water_heater` → `HomeUiBuilder.cardFor(…, config.entityId)`, renamed to `title` when set. A null card → Unavailable.
- `room_group` → area unknown → Unavailable. Otherwise take `HomeUiBuilder.build(…, selected = RoomFilter.Area(areaId), …)` `.cards`, keep the LIGHT and COVER cards (groups or singles), and title them with `title ?: area.name`. No cards → Unavailable.
- `flow_button` → flow found in `extras.flows` → `FlowButton(id, title ?: flow.name, enabled = flow.enabled)`. Not found → Unavailable.
- `energy_stat` → `EnergyMetric.of(metric)`, null → Unavailable. Value: `Kwh(kwhByDays[days])`, `Watts(gridWatts | solarWatts)` or `Percent(selfConsumption)`; null while unknown.
- `history_chart` → entity found and `HistoryRefs.of(entity) != null` → `History(…, points = extras.series[entityId to range]?.trimmed)`. For `H1` and `H6`, keep only points with `t >= nowMillis - 1h/6h`. `range == UNKNOWN` → Unavailable.
- `lock`, `camera` → Placeholder. The title is `title`, else the entity name for a lock or the joined camera ids for a camera.
- `UNKNOWN` kind → Unavailable(title).

**ViewModel.** Combine `preferences.selectedDashboard` with `dashboards.dashboards` → `resolve`. When `resetToHouse`, call `setSelectedDashboard("house", null)`. When a dashboard is shown under a stored name that differs from its current name, rewrite the name.

Fetch extras only for the shown dashboard's needs: a `flatMapLatest` on the set of energy `days` and `(entityId, range)` pairs, then re-fetch every `refresh` while collected. Pass `flows.flows.value.flows` and `source.entities/states/areas/link/hubGroups` into the builder. Run all of this `conflate().flowOn(dispatcher)`, exactly like `HomeViewModel.state`.

`runFlow`:
- An id already in `runningFlows` → no-op.
- Link not `Connected` → `FlowRunFailed(refused = false)` with no request.
- Otherwise add the id to `runningFlows`, call `flows.run(id)` and remove the id afterwards. A failure becomes `FlowRunFailed(refused = (error as? FlowsHttpError)?.status == 422)`.

- [ ] **Step 1: Write the failing tests**

`DashboardSelectionTest`: one test per row of the `resolve` table, plus `switcher hidden with no dashboards on House`.

`DashboardUiBuilderTest` (use `testEntity` from `TestEntities.kt`):
- `tiles follow reading order not list order` — list `[a pos(0,1), b pos(0,0)]` → tile card ids in order `b, a`, keys `0:b`, `1:a`.
- `tile keys are unique with duplicate widget ids and entities` — two widgets `id="w"` on the same entity → 2 tiles, distinct keys.
- `spans map to phone cells` — `1x1→(1,false,1)`, `2x1→(2,false,1)`, `1x2→(1,false,2)`, `2x2→(2,false,2)`, `4x2→(2,true,2)`, `6x3→(2,true,3)`.
- `widget title renames the card`.
- `unknown entity, kind, span, metric, area and flow are Unavailable`.
- `energy-role and hidden entities still render`. An entity with `ui.energyRole = SOLAR` and one with `ui.hidden = true` → Entity tiles.
- `companion entity is Unavailable` — `partOf = "parent"`.
- `room group shows the room's light and cover cards` — 2 lights + 2 covers in `kitchen` → `Room("Kitchen", [cover group, light group])` in Home's order.
- `flow button uses the flow name and enabled flag`.
- `energy stat values` — `today_kwh` with `kwhByDays[1]=12.4` → `Kwh(12.4)`; `pv_power_now` → `Watts(solar)`; unknown → null value.
- `one-hour history keeps the last hour of the 24h series`.
- `lock and camera are placeholders`.
- `card() finds group members on a dashboard`.

`DashboardViewModelTest` (fake `EntitySource` from `FakeEntitySource.kt`; real `DashboardsRepository` and `FlowsRepository` on `backgroundScope`; `AppPreferences.create(tmpFile, backgroundScope)`):
- `selecting a dashboard saves its id and name`.
- `a dashboard deleted on the hub resets the choice to House` — select `a`, deliver empty for `a` → the preference becomes `house`.
- `a rename rewrites the stored name`.
- `running a flow shows it running then clears` (gated fake client).
- `a 422 is reported as refused`.
- `offline run fails without a request`.
- `energy figures are fetched only when an energy tile is shown` (count fake history calls).

- [ ] **Step 2: Run them to see them fail**: `./gradlew :feature:home:test --tests '*Dashboard*'` → FAIL.
- [ ] **Step 3: Implement the four files.**
- [ ] **Step 4: Run the tests** → PASS. Also run `./gradlew :feature:home:test` → all PASS (House untouched).
- [ ] **Step 5: Commit** `feat(home): dashboard tiles, selection and view model`

---

### Task 8: `HomeViewModel` acts on cards House doesn't show

**Files:**
- Modify: `feature/home/src/main/kotlin/com/gekkotron/oikos/feature/home/presentation/HomeViewModel.kt`
- Test: `feature/home/src/test/…/presentation/HomeViewModelTest.kt`

**Interfaces:**
- Produces (new trailing params, defaults keep every existing call site valid):
  ```kotlin
  fun send(id: String, command: Command, card: CardUi? = null)                 // stale check: state.value.card(id) ?: card
  fun sendGroup(groupId: String, command: Command, only: List<String>? = null, group: CardUi? = null)
  fun customizeGroup(groupId: String, name: String, favorite: Boolean, removed: List<String>, group: CardUi? = null)
  ```
- When House's state lacks the id, use the passed card. Remember the last group card given per `groupId`, so a snackbar Retry (`sendGroup(g, c, ids)` with no card) still finds it. `tap(card)` already takes the card. Its group branch must pass `card` through to `sendGroup`.

- [ ] **Step 1: Write the failing tests**
  - `group send reaches members when House shows another filter`. Favourites selected; the kitchen light group card comes from `HomeUiBuilder.build(…, RoomFilter.Area("kitchen"))` → `sendGroup(id, TurnOn(), group = card)` → the fake source received TurnOn for both members.
  - `retry without a card reuses the last group card given`.
  - `a stale dashboard card waits for connection`.
  - `House-only calls behave as before` (an existing test suffices; make sure it still passes).
- [ ] **Step 2: Run them to see them fail**: `./gradlew :feature:home:test --tests '*HomeViewModelTest*'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the tests** → PASS.
- [ ] **Step 5: Commit** `feat(home): commands work on cards a dashboard shows`

---

### Task 9: Switcher, dashboard grid, navigation, demo dashboards, screenshots

**Files:**
- Create: `feature/home/src/main/kotlin/com/gekkotron/oikos/feature/home/ui/DashboardSwitcher.kt`, `DashboardTiles.kt`
- Modify: `feature/home/…/ui/HomeScreen.kt`, `feature/home/src/main/res/values/strings.xml`, `values-fr/strings.xml`
- Modify: `app/src/main/kotlin/com/gekkotron/oikos/navigation/OikosNavHost.kt` (`onOpenEnergy = { selectTab(backStack, EnergyKey) }`)
- Modify: `core/demo/src/main/kotlin/com/gekkotron/oikos/demo/DemoHouse.kt`, test `core/demo/src/test/…/DemoHouseTest.kt`
- Test: `feature/home/src/test/…/ui/DashboardScreenTest.kt`, `DashboardScreenshotTest.kt`

**Interfaces:**
- Consumes: `DashboardViewModel`, `DashboardScreenState`, `TileUi` (Task 7); `HomeViewModel.send/sendGroup/customizeGroup(…, card)` (Task 8); `EnergyFormat` (`:feature:energy`); `HistoryLineChart`, `ChartPoint` (`:core:designsystem`).
- Produces:
  ```kotlin
  @Composable fun HomeRoute(…existing…, onOpenEnergy: () -> Unit = {}, dashboardViewModel: DashboardViewModel = hiltViewModel())
  @Composable fun HomeScreen(…existing…, dashboard: DashboardScreenState = DashboardScreenState(),
      onSelectDashboard: (String) -> Unit = {}, onRunFlow: (String) -> Unit = {}, onOpenEnergy: () -> Unit = {})
  internal fun LazyGridScope.dashboardTiles(tiles: List<TileUi>, running: Set<String>, onEntityClick: (CardUi) -> Unit,
      onEntityLongClick: (CardUi) -> Unit, onSendGroup: (String, Command) -> Unit, onForce: (CardUi) -> Unit,
      onRunFlow: (String) -> Unit, onOpenEnergy: () -> Unit, onOpenHistory: (String) -> Unit)
  @Composable internal fun DashboardSwitcher(title: String, onClick: () -> Unit)
  @Composable internal fun DashboardPickerSheet(entries: List<SwitcherEntry>, selectedId: String, onPick: (String) -> Unit, onDismiss: () -> Unit)
  ```

**Screen rules:**
- With `switcherVisible == false`, HomeScreen renders byte-for-byte as today: no new item and no new spacing. The existing `HomeScreenTest` and screenshot tests must pass unchanged.
- With the switcher visible, the first line of `HomeHeader` is `DashboardSwitcher` (`OikosTheme.type.cardName`, name + ` ▾`). On House, the greeting, hero, pill row and chips follow, all unchanged.
- On a dashboard (`view != House` and not searching), the header is the switcher, then the greeting + hero row only when `showHeader`, then the pill/search/settings row. When `showHeader`, the room chips row follows too. Tapping a chip selects House (`onSelectDashboard("house")`) and then that filter (`onSelect`). Then come `dashboardTiles(…)`. `Loading` shows `dashboard_loading` instead of tiles, and an empty `tiles` shows `dashboard_empty`.
- Searching always shows House's search results, even on a dashboard.
- Sheet and customize lookups become `state.card(id) ?: dashboard.card(id)`. Every `onSend`, `onSendGroup` and `onCustomizeGroup` call from a dashboard passes the card (Task 8).
- Tiles:
  - Entity → `EntityCard`, or `GroupCard` for a group card, with Home's exact click and long-press logic.
  - Room → a `Column` holding the title in `OikosTheme.type.label`, then each card.
  - FlowButton → an `OikosCard` with the name and a Run `TextButton`. The card is disabled with `widget_flow_off` when `!enabled`, and the button shows pending while the flow is in `running`.
  - EnergyStat → label + value. Format with `EnergyFormat.energy`, `EnergyFormat.power`, or `"%d %%"`; `"—"` when null. A tap calls `onOpenEnergy`.
  - History → title + `HistoryLineChart` (no axis stats). A tap calls `onOpenHistory(entityId)`.
  - Placeholder → icon + title + `widget_open_on_web`, with no click action.
  - Unavailable → `OikosCard(enabled = false)` with the title (if any) + `widget_unavailable`.
  - Grid span: `GridItemSpan(if (fullWidth) maxLineSpan else min(columns, maxLineSpan))`. When `rows > 1`, apply `Modifier.heightIn(min = rows * OikosDimens.cardMinHeight + (rows - 1) * OikosDimens.gridGap)`.
- Accessibility: the switcher's `contentDescription` is `dashboard_switcher_cd` ("Dashboard: %1$s") and its `onClick` label is `dashboard_switcher_action` ("change"), so TalkBack reads "Dashboard: Soirée, double-tap to change". Cards keep their own labels.
- `HomeRoute` collects `dashboardViewModel.events` into the existing snackbar: `widget_flow_refused` when refused, `widget_flow_failed` otherwise.

**Strings (EN / FR):**

| key | EN | FR |
|---|---|---|
| `dashboard_house` | House | Maison |
| `dashboard_picker_title` | Dashboards | Tableaux de bord |
| `dashboard_switcher_cd` | Dashboard: %1$s | Tableau de bord : %1$s |
| `dashboard_switcher_action` | change | changer |
| `dashboard_loading` | Waiting for the hub… | En attente du hub… |
| `dashboard_empty` | Nothing here yet — add widgets on the web | Rien ici pour l'instant — ajoutez des widgets sur le web |
| `widget_unavailable` | Unavailable | Indisponible |
| `widget_open_on_web` | Open on the web | Ouvrir sur le web |
| `widget_flow_run` | Run | Lancer |
| `widget_flow_off` | This flow is off | Ce flow est désactivé |
| `widget_flow_failed` | Could not run the flow | Impossible de lancer le flow |
| `widget_flow_refused` | The hub refused to run this flow | Le hub a refusé de lancer ce flow |
| `widget_energy_today` / `_week` / `_month` | Today / This week / This month | Aujourd'hui / Cette semaine / Ce mois-ci |
| `widget_energy_grid_now` / `_solar_now` / `_self` | Grid now / Solar now / Self-consumption | Réseau maintenant / Solaire maintenant / Autoconsommation |

("Flow" stays as the app's tab name, which is "Flows" in both languages.)

**Demo:** `DemoHouse.init` adds two dashboards through `extras`, both on topic `oikos/ui/dashboards/<id>`:
- `"evening"`, name "Soirée", order 1, `show_header: true`, widgets:
  - `light` `demo_living_lamp` 1x1 at (0,0)
  - `cover` `demo_living_shutter` 1x1 at (0,1)
  - `room_group` `living_room` 2x1 at (1,0)
  - `climate` `demo_living_ac` 1x1 at (2,0)
  - `lock` `demo_front_door` 1x1 at (2,1)
- `"energy"`, name "Énergie", order 2, widgets:
  - `energy_stat` `pv_power_now` 1x1
  - `energy_stat` `self_consumption_pct` 1x1
  - `energy_stat` `today_kwh` 2x1
  - `history_chart` `demo_bedroom_temperature` `24h` 2x2

No flow buttons: the demo has no flows.

- [ ] **Step 1: Write the failing tests**
  - `DemoHouseTest`: `the demo publishes two dashboards` → `DashboardJson.decode` on each retained `oikos/ui/dashboards/*` gives "Soirée" (5 widgets) and "Énergie" (4). Add `testImplementation(project(":core:dashboards"))` to `core/demo`.
  - `DashboardScreenTest` (Robolectric Compose, like `HomeScreenTest`):
    - `no switcher without dashboards` — the default `DashboardScreenState()` → no node with text `"House ▾"`, and the House header is unchanged.
    - `switcher opens the picker with House first`.
    - `picking a dashboard calls onSelectDashboard`.
    - `dashboard tiles replace House cards`.
    - `loading shows the saved name and the waiting line`.
    - `switcher announces the dashboard` — `contentDescription == "Dashboard: Soirée"`.
    - `unavailable tile renders for an unresolved widget`.
    - `flow button runs and is disabled when the flow is off`.
    - `search on a dashboard shows House results`.
  - `DashboardScreenshotTest` (copy the `GroupScreenshotTest` harness): `dashboard grid in Night Glass` and `dashboard grid in Ivoire`. Use a `DashboardScreenState` with one tile of each content type, with `assertTheme(…)` as in that file.
- [ ] **Step 2: Run them to see them fail**: `./gradlew :core:demo:test :feature:home:test --tests '*Dashboard*'` → FAIL.
- [ ] **Step 3: Implement the composables, HomeScreen/HomeRoute integration, strings, nav wiring and demo dashboards.**
- [ ] **Step 4: Verify the whole branch**:
  `./gradlew :core:dashboards:jvmTest :core:flows:jvmTest :core:source:jvmTest :core:data:test :core:demo:test :feature:energy:test :feature:home:test :feature:automations:test :app:assembleDebug` → all PASS / BUILD SUCCESSFUL. Inspect the two new screenshot PNGs (the `renderPng` output path is printed by `ScreenshotSupport.kt`) for legibility in both themes.
- [ ] **Step 5: Commit** `feat(home): House ▾ switcher shows the hub's dashboards`

---

## After the last task

- Whole-branch review (superpowers:requesting-code-review), then superpowers:finishing-a-development-branch.
- Device check (demo, then the real house): the switcher appears, the order matches the web on a phone-width browser, a light tile toggles, a flow button runs, and a dashboard deleted on the web falls back to House.
- Update memory `oikos-dashboards.md` with the merge SHA and what the device check showed.
