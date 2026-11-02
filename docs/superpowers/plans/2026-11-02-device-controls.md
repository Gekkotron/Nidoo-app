# Device Controls (lights + switches) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nidoo appears in Android's Device controls picker and serves on/off (+ brightness) tiles for lights and switches/plugs.

**Architecture:** A new `:feature:controls` module holds a pure mapper (Entity/State → `ControlModel`, action → `Command`), a `ControlsSession` that drives catalog / refresh / live / send through the existing `WidgetEntities` door, and a thin `@RequiresApi(30)` `ControlsProviderService` that turns models into `android.service.controls.Control`. `:app` wires it with Hilt and a provider interface, exactly like the widget.

**Tech Stack:** Kotlin, coroutines 1.11.0 (+ `kotlinx-coroutines-jdk9` for `java.util.concurrent.Flow.Publisher`), Hilt, JUnit 4 + Robolectric 4.17.

**Spec:** `docs/superpowers/specs/2026-11-02-device-controls-lights-design.md`

## Global Constraints

- `minSdk` stays 28; every reference to `android.service.controls.*` or `java.util.concurrent.Flow` lives in `@RequiresApi(30)` code.
- `Control.StatefulBuilder.setAuthRequired(false)` is API 33+: call it only under `Build.VERSION.SDK_INT >= 33`.
- Never send `Command.Toggle`; always the explicit target (`TurnOn(...)` / `TurnOff`).
- Brightness: tile range 0–100 step 1; `% → 0..255` is `round(pct × 255 / 100)`; 0 % → `TurnOff`. `0..255 → %` is `round(b × 100 / 255)`.
- Control id = `Entity.uniqueId` unchanged. No structure on any control.
- `performControlAction` answers `ControlAction.RESPONSE_OK` immediately (unknown id → `RESPONSE_FAIL`), never after `send`.
- `STATUS_NOT_FOUND` only when the id is missing **and** `RefreshResult.Ok.complete` is true.
- Strings in `values/` and `values-fr/`.
- Commits: `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`, message ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Fast slider drags** send many `FloatAction`s: the last value must win, earlier in-flight sends for the same id are cancelled → test `latest_action_wins_when_sends_overlap` (Task 3).
2. **Panel opened offline / broker down**: tiles must show names with an "Offline"-style status, not spin forever → test `refresh_failure_falls_back_to_catalog_names_with_failure_status` (Task 3).
3. **Light on with unknown brightness** (`brightness == null`): slider shows 100 %, not 0 → test `on_light_without_brightness_shows_full_range` (Task 4).
4. **Switch shown as a light** (`kind = LIGHT`, `protocolKind = SWITCH`, no brightness capability) → plain toggle, `TYPE_LIGHT` → test `switch_as_light_is_a_plain_light_toggle` (Task 1).
5. **No state received yet** (`StateValue.Unknown` / no `EntityState`) → off, status OK, not an error → test `entity_without_state_shows_off_and_ok` (Task 1).

---

## File map

| File | Responsibility |
|---|---|
| `feature/controls/build.gradle.kts`, `src/main/AndroidManifest.xml` | module + service declaration |
| `feature/controls/.../model/ControlModel.kt` | `ControlModel`, `ControlType`, `ControlStatus`, `ControlAction` (pure) |
| `feature/controls/.../ControlMapper.kt` | pure entity/state → model, action → command |
| `feature/controls/.../ControlsSession.kt` | catalog / watch / perform over `WidgetEntities` |
| `feature/controls/.../ControlsComponent.kt` | `ControlsComponent` + `ControlsComponentProvider` |
| `feature/controls/.../android/ControlFactory.kt` | `ControlModel` → `Control` (API 30) |
| `feature/controls/.../android/NidooControlsService.kt` | the system service (API 30) |
| `feature/widget/.../WidgetEntities.kt` | `RefreshResult.Ok` gains `areas` |
| `app/.../connection/HeadlessEntities.kt` | fills `areas` |
| `app/.../di/ControlsModule.kt`, `NidooApplication.kt` | wiring |

Package root: `com.gekkotron.nidoo.feature.controls`.

---

### Task 1: Module + pure mapper

**Files:**
- Create: `feature/controls/build.gradle.kts` (plugins `nidoo.android.library`; namespace `com.gekkotron.nidoo.feature.controls`; deps `project(":core:model")`, `project(":feature:widget")`, `libs.kotlinx.coroutines.core`)
- Modify: `settings.gradle.kts` (add `include(":feature:controls")` after `:feature:widget`)
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/model/ControlModel.kt`
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/ControlMapper.kt`
- Test: `feature/controls/src/test/kotlin/com/gekkotron/nidoo/feature/controls/ControlMapperTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class ControlType { LIGHT, SWITCH, OUTLET }
  enum class ControlStatus { OK, UNAVAILABLE, NOT_FOUND, OFFLINE, AUTH_FAILED, NOT_CONFIGURED, NO_RESPONSE }
  data class ControlModel(
      val id: String, val title: String, val zone: String?, val type: ControlType,
      val dimmable: Boolean, val on: Boolean, val brightnessPercent: Int?, val status: ControlStatus,
  )
  sealed interface ControlAction {
      data class SetOn(val on: Boolean) : ControlAction
      data class SetBrightness(val percent: Float) : ControlAction
  }
  object ControlMapper {
      fun eligible(entity: Entity): Boolean
      fun model(entity: Entity, state: EntityState?, areas: List<Area>, status: ControlStatus = ControlStatus.OK): ControlModel
      fun status(failure: Failure): ControlStatus
      fun command(model: ControlModel, action: ControlAction): Command
  }
  ```

Rules the tests pin:
- `eligible`: `kind == LIGHT || kind == SWITCH`, and `!ui.hidden`, and `partOf == null`.
- `type`: LIGHT → `LIGHT`; SWITCH with `deviceClass == "outlet"` → `OUTLET`; other SWITCH → `SWITCH`.
- `dimmable` = `kind == LIGHT && capabilities.brightness`.
- `zone` = the matching `Area.name` for `entity.area`; `null` when `entity.area == UNASSIGNED_AREA` or no area matches.
- `on` from `StateValue.Light.on` / `StateValue.OnOff.on`, else false. `brightnessPercent` from `Light.brightness` (null when null or not dimmable).
- `status` param wins, except an `EntityState` with `available == false` turns OK into `UNAVAILABLE`.
- `Failure.OFFLINE → OFFLINE`, `AUTH_FAILED → AUTH_FAILED`, `NOT_CONFIGURED → NOT_CONFIGURED`.
- `command`: `SetOn(true)` → `Command.TurnOn()`; `SetOn(false)` → `Command.TurnOff`; `SetBrightness(p)` → `TurnOff` when `round(p × 255 / 100) == 0`, else `TurnOn(brightness = round(p × 255 / 100))`.

- [ ] **Step 1: Write the failing tests** (`ControlMapperTest`, JUnit 4, build `Entity`/`EntityState` inline)
  - `lights_and_switches_are_eligible_others_are_not` — LIGHT, SWITCH true; COVER, SENSOR false; hidden LIGHT false; LIGHT with `partOf = "x"` false.
  - `switch_as_light_is_a_plain_light_toggle` — `kind = LIGHT, protocolKind = SWITCH, brightness = false` → `type == LIGHT, dimmable == false`.
  - `outlet_device_class_maps_to_outlet` — SWITCH + `"outlet"` → `OUTLET`; SWITCH + `null` → `SWITCH`.
  - `zone_is_the_area_name_or_null_when_unassigned` — area "salon" + `Area("salon","Salon")` → `"Salon"`; `UNASSIGNED_AREA` → null; unknown id → null.
  - `light_state_maps_to_on_and_percent` — `Light(true, 128)` dimmable → `on == true, brightnessPercent == 50`; `Light(true, 255)` → 100.
  - `entity_without_state_shows_off_and_ok` — `state = null` → `on == false, status == OK`; `StateValue.Unknown` → same.
  - `unavailable_state_marks_unavailable` — `available = false` → `UNAVAILABLE`.
  - `commands_are_explicit_never_toggle` — `SetOn(true)` → `TurnOn()`, `SetOn(false)` → `TurnOff`, `SetBrightness(50f)` → `TurnOn(brightness = 128)`, `SetBrightness(100f)` → `TurnOn(brightness = 255)`, `SetBrightness(0f)` → `TurnOff`, `SetBrightness(0.1f)` → `TurnOff`.
  - `failures_map_to_statuses` — the three `Failure` values.

- [ ] **Step 2: Run** `./gradlew :feature:controls:test` — expected: FAIL (unresolved `ControlMapper`).
- [ ] **Step 3: Create the module and implement `ControlModel.kt` + `ControlMapper.kt`** per the rules above.
- [ ] **Step 4: Run** `./gradlew :feature:controls:test` — expected: PASS.
- [ ] **Step 5: Commit** `feat(controls): module and pure control mapper`

---

### Task 2: `RefreshResult.Ok` carries areas

**Files:**
- Modify: `feature/widget/src/main/kotlin/com/gekkotron/nidoo/feature/widget/WidgetEntities.kt` — `RefreshResult.Ok` gains `val areas: List<Area> = emptyList()` as the **last** parameter (default keeps the 5 existing call sites compiling).
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/connection/HeadlessEntities.kt` — both `RefreshResult.Ok(...)` constructions in `refresh` pass `areas = source.areas.value`.
- Test: `app/src/test/kotlin/com/gekkotron/nidoo/connection/HeadlessEntitiesTest.kt`

**Interfaces:**
- Produces: `RefreshResult.Ok.areas: List<Area>` (used by Task 3).

- [ ] **Step 1: Write the failing test** `refresh_returns_the_sources_areas` in `HeadlessEntitiesTest` — follow the file's existing live-source and headless-session fixtures; assert `(result as RefreshResult.Ok).areas` equals the areas the source published, for the live path (and the session path if the existing fixtures make it a one-liner).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*HeadlessEntitiesTest*'` — expected: FAIL.
- [ ] **Step 3: Implement** the parameter and the two call sites.
- [ ] **Step 4: Run** the same command plus `./gradlew :feature:widget:test` — expected: PASS.
- [ ] **Step 5: Commit** `feat(widget): refresh results carry the house's areas`

---

### Task 3: `ControlsSession`

**Files:**
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/ControlsSession.kt`
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/ControlsComponent.kt`
- Test: `feature/controls/src/test/kotlin/com/gekkotron/nidoo/feature/controls/ControlsSessionTest.kt` (own `FakeEntities : WidgetEntities` in the test source set; `send` suspends on a `CompletableDeferred` per call so overlap can be tested; `kotlinx-coroutines-test` via the convention's test deps — add `testImplementation(libs.kotlinx.coroutines.test)` if not already provided)

**Interfaces:**
- Consumes: Task 1 `ControlMapper`, `ControlModel`, `ControlAction`; Task 2 `RefreshResult.Ok.areas`; `WidgetEntities`, `CatalogResult`, `SendResult`, `Failure` from `:feature:widget`.
- Produces:
  ```kotlin
  class ControlsSession(
      private val entities: WidgetEntities,
      /** A live view of the house while the app's own connection is up, else null. */
      private val live: () -> Flow<RefreshResult.Ok>?,
      private val scope: CoroutineScope,
  ) {
      suspend fun catalog(): List<ControlModel>                 // eligible entities, status OK, sorted by zone then title; empty on Failed
      fun watch(ids: Set<String>): Flow<ControlModel>
      fun perform(id: String, action: ControlAction): Boolean   // false when id has never been seen
  }
  class ControlsComponent(val session: ControlsSession)
  interface ControlsComponentProvider { fun controlsComponent(): ControlsComponent }
  ```

Behaviour of `watch(ids)` (a `channelFlow`):
1. Emit every already-known model for `ids` (the session keeps a process-lifetime `Map<String, ControlModel>` of the last model per id, updated on every emission).
2. Collect the session's internal update stream (filtered to `ids`) for the rest of the subscription — this is how `perform` results reach the tile.
3. `entities.refresh(ids)`:
   - `Ok`: for each id — present & eligible → `model(entity, state, areas)`; missing & `complete` → known model with `NOT_FOUND`, or, never known, `ControlModel(id, title = id, zone = null, LIGHT, dimmable = false, on = false, brightnessPercent = null, NOT_FOUND)`; missing & `!complete` → known model with `UNAVAILABLE` (skip if never known).
   - `Failed(reason)`: for each id, base = known model, else the entity from `entities.catalog()` (works offline from the cache), else skip; emit `base.copy(status = status(reason))`.
4. If `live()` is non-null, collect it and emit the mapped model for each id on every change, until cancelled.

Behaviour of `perform(id, action)`: look up the known model (unknown → return false). Cancel any in-flight send job for `id`, launch a new one in `scope`: `entities.send(id, ControlMapper.command(model, action))` → `Confirmed` → emit `ControlMapper.model(result.entity, result.state, emptyList()).copy(zone = known.zone)` (send results carry no areas); `NotConfirmed` → emit known model with `NO_RESPONSE`; `Failed(r)` → known model with `status(r)`. Return true immediately.

- [ ] **Step 1: Write the failing tests** (`runTest`, `backgroundScope` as the session scope)
  - `catalog_lists_only_eligible_entities_with_zones` — catalog with a light (area Salon), a switch, a cover → 2 models, zone "Salon" on the light.
  - `watch_emits_refreshed_state` — refresh Ok with `Light(true, 255)` → model `on == true, brightnessPercent == 100, status == OK`.
  - `missing_id_is_not_found_only_when_discovery_settled` — first watch (complete=true) → `NOT_FOUND`; with complete=false and a previously known model → `UNAVAILABLE`.
  - `refresh_failure_falls_back_to_catalog_names_with_failure_status` — never-seen id, refresh `Failed(OFFLINE)`, catalog has it → one model with its title and `OFFLINE`.
  - `perform_on_unknown_id_returns_false`.
  - `perform_emits_the_confirmed_state` — watch, then `perform(id, SetOn(true))`, complete send with `Confirmed(Light(true,128))` → watcher receives `on == true, brightnessPercent == 50`; the fake recorded `Command.TurnOn()`.
  - `unconfirmed_send_shows_no_response` — `NotConfirmed` → `NO_RESPONSE`.
  - `latest_action_wins_when_sends_overlap` — `perform(SetBrightness(20f))` then `perform(SetBrightness(80f))` before the first completes → first send's coroutine is cancelled; the only emitted confirmed/failed result comes from the second; fake recorded `TurnOn(51)` then `TurnOn(204)`.
  - `live_updates_are_forwarded_while_watching` — `live` returns a `MutableStateFlow<RefreshResult.Ok>`; push a new state → watcher receives it.
- [ ] **Step 2: Run** `./gradlew :feature:controls:test` — expected: FAIL.
- [ ] **Step 3: Implement** `ControlsSession` and `ControlsComponent.kt`.
- [ ] **Step 4: Run** `./gradlew :feature:controls:test` — expected: PASS.
- [ ] **Step 5: Commit** `feat(controls): session over the widget's door to the house`

---

### Task 4: `ControlFactory` (model → `Control`)

**Files:**
- Modify: `gradle/libs.versions.toml` — add `kotlinx-coroutines-jdk9 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk9", version.ref = "coroutines" }`
- Modify: `feature/controls/build.gradle.kts` — `implementation(libs.kotlinx.coroutines.jdk9)`
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/android/ControlFactory.kt`
- Create: `feature/controls/src/main/res/values/strings.xml`, `values-fr/strings.xml`
- Test: `feature/controls/src/test/kotlin/com/gekkotron/nidoo/feature/controls/android/ControlFactoryTest.kt` (Robolectric, `@Config(sdk = [34])`)

**Interfaces:**
- Consumes: Task 1 `ControlModel`, `ControlType`, `ControlStatus`.
- Produces:
  ```kotlin
  @RequiresApi(30)
  class ControlFactory(private val context: Context) {
      fun stateless(model: ControlModel): Control
      fun stateful(model: ControlModel): Control
  }
  ```

Rules:
- Both: title = `model.title`; zone and subtitle = `model.zone ?: ""` (zone only set when non-null); device type LIGHT → `DeviceTypes.TYPE_LIGHT`, SWITCH → `TYPE_SWITCH`, OUTLET → `TYPE_OUTLET`; app intent = `PendingIntent.getActivity(context, model.id.hashCode(), launchIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)` where `launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)` (fallback `Intent(Intent.ACTION_MAIN).setPackage(context.packageName)`).
- Stateful template: not dimmable → `ToggleTemplate("${id}_toggle", ControlButton(on, actionLabel))`; dimmable → `ToggleRangeTemplate("${id}_toggle", ControlButton(on, actionLabel), RangeTemplate("${id}_range", 0f, 100f, value, 1f, "%.0f%%"))` with `value = if (on) (brightnessPercent ?: 100) else 0`; status `NOT_FOUND` → `ControlTemplate.getNoTemplateObject()`.
- Status: OK → `Control.STATUS_OK`; NOT_FOUND → `STATUS_NOT_FOUND`; everything else → `STATUS_ERROR` with status text from strings. OK status text: `"%d %%"` for dimmable+on, else `controls_on`/`controls_off`.
- `setAuthRequired(false)` only when `SDK_INT >= 33`.
- Strings (EN / FR): `controls_on` On / Allumé · `controls_off` Off / Éteint · `controls_unavailable` Unavailable / Indisponible · `controls_offline` Offline / Hors ligne · `controls_auth_failed` Sign-in failed / Échec de connexion · `controls_not_configured` Not set up / Non configuré · `controls_no_response` No response / Pas de réponse · `controls_action` Turn on or off / Allumer ou éteindre.

- [ ] **Step 1: Write the failing tests**
  - `dimmable_light_uses_toggle_range` — template is `ToggleRangeTemplate`, range 0–100, current 50 for `brightnessPercent = 50`, device type `TYPE_LIGHT`.
  - `on_light_without_brightness_shows_full_range` — dimmable, `on = true`, `brightnessPercent = null` → range current 100; off → 0.
  - `switch_uses_plain_toggle_and_outlet_type` — OUTLET → `ToggleTemplate`, `TYPE_OUTLET`, button checked == on.
  - `failure_statuses_are_errors_with_text` — OFFLINE → `STATUS_ERROR`, status text "Offline"; NOT_FOUND → `STATUS_NOT_FOUND`.
  - `zone_is_omitted_when_null` — `zone == null` → `control.zone` empty/null, subtitle "".
- [ ] **Step 2: Run** `./gradlew :feature:controls:test` — expected: FAIL.
- [ ] **Step 3: Implement** `ControlFactory` + strings + the dependency.
- [ ] **Step 4: Run** `./gradlew :feature:controls:test` — expected: PASS.
- [ ] **Step 5: Commit** `feat(controls): build system controls from the model`

---

### Task 5: Service, manifest, app wiring, CI

**Files:**
- Create: `feature/controls/src/main/kotlin/com/gekkotron/nidoo/feature/controls/android/NidooControlsService.kt`
- Create: `feature/controls/src/main/AndroidManifest.xml` — `<service android:name=".android.NidooControlsService" android:exported="true" android:permission="android.permission.BIND_CONTROLS">` with intent-filter action `android.service.controls.ControlsProviderService`. No `android:label` (inherits the app's label).
- Create: `app/src/main/kotlin/com/gekkotron/nidoo/di/ControlsModule.kt`
- Modify: `app/build.gradle.kts` — `implementation(project(":feature:controls"))` after `:feature:widget`
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/NidooApplication.kt` — implement `ControlsComponentProvider`, `@Inject lateinit var controls: ControlsComponent`
- Modify: `.github/workflows/ci.yml` — add `:feature:controls:test` and `:feature:controls:koverXmlReport` to the `feature` job lists (after `:feature:widget:*`)
- Test: `feature/controls/src/test/kotlin/com/gekkotron/nidoo/feature/controls/android/NidooControlsServiceTest.kt` (Robolectric `sdk = [34]`, test `Application` implementing `ControlsComponentProvider` with a fake-backed session)

**Interfaces:**
- Consumes: Task 3 `ControlsSession`, `ControlsComponent`, `ControlsComponentProvider`; Task 4 `ControlFactory`.
- Produces: `@RequiresApi(30) class NidooControlsService : ControlsProviderService()`.

Service rules:
- `onCreate`: `session = (applicationContext as ControlsComponentProvider).controlsComponent().session`; `factory = ControlFactory(this)`; own `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, cancelled in `onDestroy`.
- `createPublisherForAllAvailable()` = `flow { session.catalog().forEach { emit(factory.stateless(it)) } }.asPublisher()` (`kotlinx.coroutines.jdk9.asPublisher`).
- `createPublisherFor(ids)` = `session.watch(ids.toSet()).map(factory::stateful).asPublisher()`.
- `performControlAction(id, action, consumer)`: `BooleanAction` → `ControlAction.SetOn(action.newState)`; `FloatAction` → `ControlAction.SetBrightness(action.newValue)`; other → `RESPONSE_FAIL`. `consumer.accept(id, if (session.perform(id, mapped)) RESPONSE_OK else RESPONSE_FAIL)` — synchronously, before any send completes.

`ControlsModule` (`@InstallIn(SingletonComponent::class)`):
```kotlin
@Provides @Singleton
fun controlsComponent(entities: WidgetEntities, session: EntitySourceSession, active: ActiveTransport,
                      @ApplicationScope scope: CoroutineScope): ControlsComponent
```
with `live = { if (active.link.value is LinkState.Connected) session.peek()?.let { s -> combine(s.entities, s.areas, s.states()) { e, a, st -> RefreshResult.Ok(e, st, complete = (s as? MqttDiscoverySource)?.hasSettled?.value ?: true, historyEnabled = false, areas = a) } } else null }`.

- [ ] **Step 1: Write the failing tests** (`NidooControlsServiceTest`, drive the service via `Robolectric.buildService`)
  - `boolean_action_answers_ok_immediately_and_sends_turn_on` — known id; `performControlAction(id, BooleanAction("${id}_toggle", true), consumer)` → consumer got `RESPONSE_OK` before the fake's send completed; fake recorded `TurnOn()`.
  - `float_action_sends_brightness` — `FloatAction(…, 50f)` → `TurnOn(brightness = 128)`.
  - `unknown_id_answers_fail`.
- [ ] **Step 2: Run** `./gradlew :feature:controls:test` — expected: FAIL.
- [ ] **Step 3: Implement** the service, manifest, `ControlsModule`, `NidooApplication` change, `app` dependency, CI lines.
- [ ] **Step 4: Run** `./gradlew :feature:controls:test :feature:widget:test :app:testDebugUnitTest --tests '*HeadlessEntitiesTest*' :app:assembleDebug` — expected: all PASS / BUILD SUCCESSFUL; then `./gradlew :app:processDebugMainManifest` and confirm the merged manifest under `app/build/intermediates/merged_manifest*/` contains `NidooControlsService` with `BIND_CONTROLS`.
- [ ] **Step 5: Commit** `feat(controls): Nidoo in the Android device controls picker`

---

## After the plan (manual, on device)

Install the debug build on an Android 11+ phone → status bar → Device controls → add → Nidoo is listed → tick a dimmable light, a plain light and a plug → tap toggles, drag dims, long-press opens Nidoo; turn Wi-Fi off and reopen the panel → tiles show "Offline".
