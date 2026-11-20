# Wake-up alarms in the app — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change a hub Wake-up alarm's time and weekdays from the app, in a renamed **Routines** tab that holds Flows and Wake-up side by side.

**Architecture:** The hub gains one POST endpoint that re-times an alarm and rebuilds its flow with its own generator. The app reads `labels` and `wake_up` from the retained flow payload it already receives, splits the tab into two sections, and calls the endpoint from an edit sheet.

**Tech Stack:** Rust (axum, serde) in `Nidoo`; Kotlin (KMP `core:flows`, Compose Material 3, Hilt) in `Nidoo-app`.

**Spec:** `docs/superpowers/specs/2026-11-20-wake-up-in-app-design.md` (in the app repo)

**Worktrees:**
- Hub: `~/.superconductor/worktrees/Nidoo/wake-up-time-endpoint`, branch `feat/wake-up-time-endpoint` (from `origin/main`)
- App: `~/.superconductor/worktrees/Nidoo-app/wake-up-routines`, branch `feat/wake-up-routines` (from `origin/master`)

## Global Constraints

- Endpoint: `POST /api/v1/flows/{id}/wake-up`, body `{"hour":7,"minute":30,"days":[1,2,3,4,5]}`.
- Weekdays on the wire: 0 = Sunday … 6 = Saturday, never empty.
- Answers: `200` success, `404` unknown flow, `422` not a readable alarm or invalid time/days.
- The app never builds or patches an alarm's flow; it only sends hour, minute, days.
- A save must not lose any setting the app does not show (playlist, shuffle, output, fade-in, speakers, muted, area, labels, enabled).
- App transport is GET + JSON POST only; no PUT, no new HTTP library, no gateway change.
- Tab hidden under the Demo profile (unchanged).
- Commits: identity `-c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com`; never the owner's real name anywhere. End messages with `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>`.
- No Docker and no emulator launch on this Mac. No push, no PR.
- Do not add per-repo cache or target-dir overrides.

## Review Focus

1. An alarm flow carries an `area` or extra labels: re-timing must keep them (Task 1 test `retime_keeps_everything_else`).
2. The alarm's name was typed by the user: it must not be renamed; a default name in either language must follow the new time (Task 1 tests).
3. A flow labelled `wake-up` whose `wake_up` is missing or malformed: the retained payload must still parse, the row shows name + switch, tapping does nothing (Task 3 and Task 4 tests).
4. The hub is older and answers `404` on save: the sheet stays open and says the hub needs an update (Task 4 test).
5. Days arrive unsorted or duplicated (`[5,1,1]`): hub stores `[1,5]`; all days unticked in the sheet: Save disabled, no request (Task 1 and Task 4 tests).

---

# Part A — Hub (`Nidoo` worktree)

### Task 1: `retime` in the alarm module

**Files:**
- Modify + test: `crates/nidoo-core/src/flow/wake_up.rs`

**Interfaces:**
- Produces:
  ```rust
  pub enum RetimeError { NotAnAlarm, Unreadable(String), Invalid(WakeUpError) } // Debug, PartialEq, Display, Error
  pub fn retime(flow: &Flow, hour: u8, minute: u8, days: &[u8]) -> Result<Flow, RetimeError>
  ```

- [ ] **Step 1: Write failing tests** in the module's `tests`:
  - `retime_sets_time_and_days`: from a `to_flow("wake-up-1", "Morning", true, &full())` flow, `retime(&f, 6, 15, &[5, 1, 1])` → `read` gives hour 6, minute 15, days `[1, 5]`; the `trigger` node's cron is `"15 6 * * 1,5"`.
  - `retime_keeps_everything_else`: set `area = Some("bedroom")`, push a second label, `enabled = false` on the source flow; after `retime` the id, area, labels, enabled, and every `WakeUp` field other than hour/minute/days are equal, and the node ids other than the trigger's params are unchanged.
  - `retime_keeps_a_typed_name`: name `"Morning"` stays `"Morning"`.
  - `retime_moves_a_default_name`: `"Wake-up 07:05"` → `"Wake-up 06:15"`, and `"Réveil 07:05"` → `"Réveil 06:15"` (source alarm at 07:05).
  - `retime_refuses_bad_input`: no days → `Invalid(WakeUpError::NoDay)`; day 7 → `Invalid(NoDay)`; hour 24 → `Invalid(BadTime)`.
  - `retime_refuses_other_flows`: `Flow::default()` → `NotAnAlarm`; a flow whose `wake_up` extra is `json!("x")` → `Unreadable(_)`.
- [ ] **Step 2:** `cargo test -p nidoo-core wake_up` → the new tests fail to compile.
- [ ] **Step 3: Implement.** `read` the settings, replace the three fields (days sorted, deduplicated), `validate`, call `to_flow`, then return the **original flow cloned** with only `name`, `nodes`, `edges` and `extra["wake_up"]` taken from the rebuilt one. Name: if it equals `default_name(false, old_h, old_m)` or `default_name(true, old_h, old_m)`, use the same language's default for the new time; otherwise keep it.
- [ ] **Step 4:** `cargo test -p nidoo-core wake_up` → all pass.
- [ ] **Step 5:** Commit `feat(wake-up): retime an alarm from its stored settings`.

### Task 2: Hub method, route and docs

**Files:**
- Modify: `crates/nidoo/src/flows/hub.rs` (next to `set_flow_enabled`)
- Modify: `crates/nidoo/src/http/api.rs` (route next to `/flows/{id}/enabled`, handler next to `set_flow_enabled`)
- Test: the existing HTTP flow tests (`crates/nidoo/tests/http.rs` or `flows.rs`; use whichever already exercises `/flows/{id}/enabled`, and its helpers)
- Modify: `docs/users/wake-up.md`, plus the API reference under `docs/reference` if it lists flow routes

**Interfaces:**
- Consumes: `nidoo_core::flow::wake_up::{retime, RetimeError}`
- Produces:
  ```rust
  pub async fn set_wake_up_time(&self, id: &str, hour: u8, minute: u8, days: &[u8]) -> Result<FlowView, HubError>
  ```
  and the route `POST /flows/{id}/wake-up` with body `struct WakeUpTimeBody { hour: u8, minute: u8, days: Vec<u8> }`, answering `Json<FlowView>`.

- [ ] **Step 1: Write failing tests:**
  - `wake_up_time_is_saved`: save an alarm flow (built with `to_flow`, entities present in the test house as the existing wake-up tests do), POST `{"hour":6,"minute":15,"days":[6,0]}` → `200`; `GET /flows/{id}` shows `wake_up.hour == 6`, `days == [0,6]`, trigger cron `"15 6 * * 0,6"`, and `enabled` unchanged.
  - `wake_up_time_unknown_flow_is_404`.
  - `wake_up_time_on_a_plain_flow_is_422`.
  - `wake_up_time_without_days_is_422` (`"days":[]`), and the stored flow is unchanged.
- [ ] **Step 2:** Run them → fail (route missing, `404`/`405`).
- [ ] **Step 3: Implement.** `set_wake_up_time`: unknown id → `HubError::UnknownFlow`; an invalid stored flow → `HubError::InvalidFlow(reason)` as `set_flow_enabled` does; any `RetimeError` → `HubError::InvalidFlow(err.to_string())`; then save through `set_flow` (so house checks still run) and return its `.flow`. Confirm `InvalidFlow` maps to `422` in `api.rs`.
- [ ] **Step 4:** `cargo test -p nidoo-core && cargo test -p nidoo` for the touched test files → pass. `cargo fmt --all` and `cargo clippy -p nidoo -p nidoo-core --all-targets -- -D warnings` → clean.
- [ ] **Step 5: Docs.** In `docs/users/wake-up.md` add a short section "From the phone": the Nidoo app lists the alarms under Routines → Wake-up and can change the time and the days; everything else is set on this page.
- [ ] **Step 6:** Commit `feat(api): POST /flows/{id}/wake-up changes an alarm's time and days`.

---

# Part B — App (`Nidoo-app` worktree)

### Task 3: `core:flows` reads alarms and can re-time one

**Files:**
- Modify: `core/flows/src/commonMain/kotlin/com/gekkotron/nidoo/flows/FlowDto.kt`, `FlowsClient.kt`, `FlowsRepository.kt`
- Modify: `core/flows/src/jvmMain/kotlin/com/gekkotron/nidoo/flows/HttpFlowsClient.kt`
- Test: `FlowDtoParseTest.kt`, `FlowsRepositoryTest.kt` (commonTest), `HttpFlowsClientTest.kt` (jvmTest)

**Interfaces:**
- Produces:
  ```kotlin
  const val WAKE_UP_LABEL = "wake-up"
  data class WakeUpTime(val hour: Int, val minute: Int, val days: List<Int>) // days 0=Sunday..6, sorted, distinct, non-empty
  // FlowDto gains:
  val labels: List<String> = emptyList()
  @SerialName("wake_up") val wakeUp: JsonElement? = null
  val FlowDto.isWakeUp: Boolean          // label present OR wakeUp != null
  fun FlowDto.wakeUpTime(): WakeUpTime?  // null when missing, malformed, out of range or no day
  // FlowsClient:
  suspend fun setWakeUp(id: String, hour: Int, minute: Int, days: List<Int>): Result<Unit>  // default: failure(UnsupportedOperationException)
  // FlowsRepository:
  suspend fun setWakeUp(id: String, hour: Int, minute: Int, days: List<Int>): Result<Unit>  // same shape as run(); no optimistic state
  ```

- [ ] **Step 1: Write failing tests:**
  - Parse: a payload with `"labels":["wake-up"]` and `"wake_up":{"version":1,"hour":7,"minute":5,"days":[1,2,3,4,5],"player":"p","playlist":"favorites",...}` → `isWakeUp`, `wakeUpTime() == WakeUpTime(7, 5, listOf(1,2,3,4,5))`.
  - Parse: `"wake_up":"x"`, `"wake_up":{"hour":25,"minute":0,"days":[1]}`, `"wake_up":{"hour":7,"minute":0,"days":[]}` each decode without throwing, `isWakeUp` true, `wakeUpTime()` null.
  - Parse: a flow with neither → `isWakeUp` false. Days `[5,1,1]` → `[1,5]`.
  - Repository: a retained payload with malformed `wake_up` still appears in `flows`.
  - Client: `setWakeUp("wake-up-1", 6, 15, listOf(1, 5))` posts to `…/api/v1/flows/wake-up-1/wake-up` with body `{"hour":6,"minute":15,"days":[1,5]}`; a `404` answer is `Result.failure(FlowsHttpError(404))`.
  - Repository: `setWakeUp` forwards to the client and returns its result; a client exception becomes a failure.
- [ ] **Step 2:** `./gradlew :core:flows:allTests` → new tests fail.
- [ ] **Step 3: Implement.** Reuse `HttpFlowsClient.postTo` with action `"wake-up"`.
- [ ] **Step 4:** `./gradlew :core:flows:allTests` → pass.
- [ ] **Step 5:** Commit `feat(flows): read wake-up alarms and re-time one on the hub`.

### Task 4: View model — two sections and the alarm editor

**Files:**
- Modify: `feature/automations/src/main/kotlin/com/gekkotron/nidoo/feature/automations/presentation/AutomationsUiState.kt`, `AutomationsViewModel.kt`
- Create: `feature/automations/src/main/kotlin/com/gekkotron/nidoo/feature/automations/presentation/WeekdayText.kt`
- Modify: `feature/automations/src/main/res/values/strings.xml`, `values-fr/strings.xml`
- Test: `AutomationsViewModelTest.kt`; create `WeekdayTextTest.kt`

**Interfaces:**
- Consumes: Task 3's `isWakeUp`, `wakeUpTime()`, `FlowsRepository.setWakeUp`, `FlowsHttpError`.
- Produces:
  ```kotlin
  enum class RoutineSection { Flows, WakeUp }
  data class AlarmRow(val id: String, val name: String, val time: WakeUpTime?, val enabled: Boolean, val toggleEnabled: Boolean)
  data class AlarmEditor(val id: String, val name: String, val hour: Int, val minute: Int, val days: Set<Int>,
                         val saving: Boolean = false, @StringRes val error: Int? = null) { val canSave get() = days.isNotEmpty() && !saving }
  // AutomationsUiState gains: section: RoutineSection, alarms: List<AlarmRow>, editor: AlarmEditor?
  // AutomationsViewModel gains:
  fun onSectionSelected(section: RoutineSection)
  fun onAlarmTapped(id: String)
  fun onEditorTime(hour: Int, minute: Int)
  fun onEditorDayToggled(day: Int)
  fun onEditorDismissed()
  fun onEditorSave()
  // WeekdayText.kt — pure, no Android types:
  /** [days] 0=Sunday..6. Ordered from [firstDay]; a run of 3+ consecutive days is "first–last", others comma-separated. */
  fun weekdayText(days: List<Int>, firstDay: java.time.DayOfWeek, shortName: (java.time.DayOfWeek) -> String): String
  ```
- Strings (EN / FR): `automations_title` → `Routines` / `Routines`; add `routines_section_flows` (Flows / Flows), `routines_section_wake_up` (Wake-up / Réveil), `wake_up_empty_title` (No alarms yet / Aucun réveil), `wake_up_empty_body` (Create one from the Nidoo web interface. / Créez-en un depuis l'interface web de Nidoo.), `wake_up_every_day` (Every day / Tous les jours), `wake_up_unreadable` (Open the Nidoo web interface to fix this alarm. / Ouvrez l'interface web de Nidoo pour corriger ce réveil.), `wake_up_save` (Save / Enregistrer), `wake_up_pick_a_day` (Pick at least one day / Choisissez au moins un jour), `wake_up_save_failed` (Could not save the alarm / Impossible d'enregistrer le réveil), `wake_up_hub_outdated` (Update your Nidoo hub to change alarms from the app / Mettez à jour votre hub Nidoo pour modifier les réveils depuis l'app).

- [ ] **Step 1: Write failing tests:**
  - `alarms_leave_the_flows_section`: with one plain flow and one alarm, `rows` holds only the plain flow, `alarms` only the alarm, and the room chips are computed from plain flows only.
  - `alarm_row_carries_its_time`; `unreadable_alarm_has_no_time_and_does_not_open` (`onAlarmTapped` leaves `editor` null).
  - `tapping_an_alarm_opens_the_editor_with_its_values`; offline → `onAlarmTapped` does nothing.
  - `toggling_the_last_day_off_disables_save` (`canSave` false; `onEditorSave` makes no client call).
  - `save_sends_sorted_days_and_closes`: client receives `("wake-up-1", 6, 15, [1, 5])`; `editor` null after success; `saving` true while the call is gated.
  - `save_404_keeps_the_editor_with_hub_outdated`: `FlowsHttpError(404)` → `editor.error == R.string.wake_up_hub_outdated`, `saving` false.
  - `save_failure_keeps_the_editor_with_save_failed` for any other failure.
  - `section_defaults_to_flows_and_switches`.
  - `weekdayText`: `[1,2,3,4,5]` Monday-first → `"Mon–Fri"`; `[0,6]` Monday-first → `"Sat, Sun"`; `[0,6]` Sunday-first → `"Sun, Sat"`; `[1,3]` → `"Mon, Wed"`; `[0,1,2,3,4,5,6]` Monday-first → `"Mon–Sun"`; `[]` → `""`.
- [ ] **Step 2:** `./gradlew :feature:automations:testDebugUnitTest` → new tests fail.
- [ ] **Step 3: Implement.** Section and editor are `MutableStateFlow`s combined into `state` (the `combine` has five inputs today: group or nest as needed). Alarms are sorted by time then name; unreadable ones last. The editor is a copy of the row's values taken when tapped: a live update of the same flow does not overwrite what is being edited. If the flow disappears, the editor closes.
- [ ] **Step 4:** `./gradlew :feature:automations:testDebugUnitTest` → pass.
- [ ] **Step 5:** Commit `feat(routines): wake-up section state and alarm editor`.

### Task 5: Screen — section switch, alarm rows, edit sheet, tab name

**Files:**
- Modify: `feature/automations/src/main/kotlin/com/gekkotron/nidoo/feature/automations/ui/AutomationsScreen.kt`, `AutomationsRoute.kt`
- Create: `feature/automations/src/main/kotlin/com/gekkotron/nidoo/feature/automations/ui/WakeUpSection.kt` (alarm list + edit sheet)
- Modify: `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` (`nav_automations` → `Routines` in both)
- Test: `feature/automations/src/test/.../ui/AutomationsScreenTest.kt`

**Interfaces:**
- Consumes: Task 4's state and callbacks, `weekdayText`.
- Test tags: `routines_section_flows`, `routines_section_wake_up`, `alarm_row_<id>`, `alarm_switch_<id>`, `alarm_editor`, `alarm_day_<0..6>`, `alarm_save`.

- [ ] **Step 1: Write failing tests** (same harness as the existing screen test):
  - The switch shows both sections; choosing Wake-up calls `onSectionSelected(RoutineSection.WakeUp)`.
  - With `section = WakeUp`, an alarm row shows its name and `07:05 · Mon–Fri`; its switch calls `onToggle(id, …)`; tapping the row calls `onAlarmTapped(id)`.
  - An unreadable alarm shows the `wake_up_unreadable` text.
  - No alarms → the empty title and body.
  - With an `editor` in state, the sheet shows seven day chips with the editor's days selected; tapping one calls `onEditorDayToggled`; Save is disabled when `canSave` is false and shows `wake_up_pick_a_day`; an `error` is displayed.
- [ ] **Step 2:** `./gradlew :feature:automations:testDebugUnitTest` → new tests fail.
- [ ] **Step 3: Implement.** Material 3 `SingleChoiceSegmentedButtonRow` under the title; the room chips and flow list only under Flows. Alarm rows reuse the flow row's look. Time is shown `HH:mm`. Sheet: `ModalBottomSheet` with a Material 3 `TimePicker` (24-hour per `DateFormat.is24HourFormat`), seven `FilterChip`s ordered from `WeekFields.of(locale).firstDayOfWeek` with `DayOfWeek.getDisplayName(TextStyle.SHORT, locale)`, a Save button with a progress indicator while `saving`. Seven days selected is shown as `wake_up_every_day` on the row. Offline: rows not clickable, switches disabled (as flows today).
- [ ] **Step 4:** `./gradlew :feature:automations:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest` → pass (a nav test may assert the old tab label; update it).
- [ ] **Step 5:** Commit `feat(routines): Routines tab with Flows and Wake-up, and the alarm edit sheet`. Commit the spec and this plan in a separate `docs:` commit.
