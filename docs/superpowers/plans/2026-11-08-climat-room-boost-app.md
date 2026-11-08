# Climat room boost (app) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Home is filtered to a room that an active Climat zone manages, show a Climat banner (room temp, target, why) with a Boost sheet (target, 1 h / 1 h 30 / 2 h). It sends the boost to the hub over MQTT.

**Architecture:** A new KMP module `core:climate` decodes the hub's retained `nidoo/climate/zone/<zone>/{settings,decision,override}` documents into a `ClimateRepository` (copy of the `core:dashboards` sink/repository pattern). `MqttDiscoverySource` routes those topics to it. `feature/home` builds a `ClimatBannerUi` for the selected room, renders it above the cards, and publishes boost commands through `MqttTransport`.

**Tech Stack:** Kotlin, KMP (`nidoo.kmp.jvm.library`), kotlinx.serialization, Jetpack Compose + Material 3, Hilt, JUnit 5 (KMP) / Robolectric + `createComposeRule` (feature).

**Spec:** `docs/superpowers/specs/2026-11-08-climat-room-boost-design.md` §6. Correction to the spec: the "room" is Home with `RoomFilter.Area` selected (`feature/home/.../ui/HomeScreen.kt`), not `feature/home/rooms/` (that's the onboarding step).

**Depends on:** the hub plan `~/superconductor/projects/Oikos-os/docs/superpowers/plans/2026-11-08-climat-room-boost-hub.md`, for `source: "boost"` and the `…/hold/set` topic. The app can be built and tested against the demo house first.

## Global Constraints

- Branch `feat/climat-room-boost` (exists, holds the spec). Commit as `Gekkotron` (`-c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com`).
- Gate before each commit: the module's tests, then `./gradlew check` before the last commit.
- Prefix constant `nidoo/climate`. Command topic `nidoo/climate/zone/<zone>/hold/set`, non-retained, QoS 1.
- Boost payload `{"setpoint": <°C>, "minutes": <60|90|120>}`. Cancel payload empty.
- Durations are exactly **60, 90, 120 minutes**, default **60**.
- The banner appears only for zones with `state == "active"`. It never appears when no climate document was received.
- The app never publishes a retained climate document.
- All new strings in EN and FR, with keys added to `StringParityTest`.
- Retained climate messages from the cache replay (`restored`) are ignored, as for dashboards.

## Review Focus

1. **The hub stops publishing climate (feature turned off, retained docs deleted):** an empty payload on `settings` removes the zone, so the banner disappears. Test it in Task 1.
2. **A zone whose `settings.entities` is `null`:** match on `decision.entities` keys plus `sensor`. Test it in Task 3.
3. **Old hub without `source: "boost"` (sends `override` with a temperature):** treat it as a boost for display. Test it in Task 3.
4. **Boost sent but no hub reply** (hub on an old version, zone pending, gateway dropped it): after 5 s the sheet shows the error and the banner keeps the hub's state. Test it in Task 4.
5. **Two zones matching one room:** pick the zone with the most matching entities, then the zone id, deterministically. Test it in Task 3.

---

### Task 1: `core:climate` module: documents and repository

**Files:**
- Create: `core/climate/build.gradle.kts` (copy of `core/dashboards/build.gradle.kts`)
- Modify: `settings.gradle.kts` (`include(":core:climate")`), `core/source/build.gradle.kts` (`commonMain` `api(project(":core:climate"))`)
- Create: `core/climate/src/commonMain/kotlin/com/gekkotron/nidoo/climate/ClimateDocs.kt`, `ClimateTopics.kt`, `ClimateSink.kt`, `ClimateRepository.kt`
- Test: `core/climate/src/commonTest/kotlin/com/gekkotron/nidoo/climate/ClimateDocsTest.kt`, `ClimateRepositoryTest.kt`

**Interfaces (produces):**
```kotlin
sealed interface ClimateSetpoint { data class Temp(val celsius: Double) : ClimateSetpoint; data object Off : ClimateSetpoint }
data class ZoneSettings(val state: String, val entities: List<String>?, val sensor: String?)
data class ZoneDecision(val mode: String?, val setpoint: ClimateSetpoint?, val source: String, val reason: String,
                        val reading: Double?, val at: Long, val devices: Set<String>)
data class ZoneOverride(val entity: String?, val setpoint: ClimateSetpoint?, val mode: String?, val until: Long, val origin: String)
data class ClimateZone(val id: String, val settings: ZoneSettings?, val decision: ZoneDecision?, val overrides: List<ZoneOverride>)
enum class ClimateDoc { SETTINGS, DECISION, OVERRIDE }
object ClimateTopics {
    const val PREFIX = "nidoo/climate"
    fun parse(topic: String): Pair<String, ClimateDoc>?   // zone id + doc; null for anything else (incl. hold/set, house)
    fun holdCommand(zone: String): String                 // "nidoo/climate/zone/$zone/hold/set"
}
object ClimateCommands { fun boost(setpoint: Double, minutes: Int): String; const val CANCEL = "" }
interface ClimateSink { fun onRetained(zone: String, doc: ClimateDoc, payload: String?); fun clear() }
class ClimateRepository(log: (String) -> Unit = {}) : ClimateSink { val zones: StateFlow<Map<String, ClimateZone>> }
```
JSON shapes come from Oikos-os `crates/nidoo-climate/src/doc.rs`:
- `setpoint` is a number or `"off"`.
- `at`/`until` are Unix seconds.
- `override` is a JSON **array**.
- `decision.entities` is an object keyed by entity id; only its keys are kept, as `devices`.

Decode with `Json { ignoreUnknownKeys = true }`.

- [ ] **Step 1: Write the failing tests**
  - `ClimateDocsTest`:
    - `decodesSettings`: `{"state":"active","entities":null,"sensor":"living_th","rules":{…}}` decodes to `state "active"`, `entities null`, `sensor "living_th"`.
    - `decodesDecisionWithBoost`: a `source "boost"` decision with `setpoint 22.0`, `reading 20.4`, `at`, and the `devices` keys.
    - `decodesOffSetpoint`: `"off"` decodes to `ClimateSetpoint.Off`.
    - `decodesOverrideArray`: two entries, one with `entity null`.
    - `topicsParse`: `nidoo/climate/zone/living/decision` gives `("living", DECISION)`; `…/hold/set`, `nidoo/climate/house` and `nidoo/climate/zone/living/proposal` give `null`.
    - `boostPayload`: `ClimateCommands.boost(22.5, 90)` equals the JSON `{"setpoint":22.5,"minutes":90}`.
  - `ClimateRepositoryTest`:
    - `collectsDocsPerZone`.
    - `emptySettingsRemovesZone`.
    - `emptyOverrideMeansNone`.
    - `malformedPayloadKeepsPrevious`: the log is called and the state is unchanged.
    - `clearEmptiesAll`.

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :core:climate:jvmTest`
Expected: FAIL (types missing).

- [ ] **Step 3: Implement** the types and the repository. The repository is a `MutableStateFlow<Map<String, ClimateZone>>` updated with `update {}`, one entry per zone. A null or empty `settings` payload removes the zone.

- [ ] **Step 4: Run and confirm everything passes**

Run: `./gradlew :core:climate:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit** `feat(climate): decode the hub's Climat zone documents`

---

### Task 2: Route climate topics into the repository

**Files:**
- Modify: `core/source/src/jvmMain/kotlin/com/gekkotron/nidoo/source/MqttDiscoverySource.kt` (constructor ~64; `handle` `when` ~349-356; `restored` skip ~333; `clear` path wherever `dashboardsSink.clear()` is called)
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/connection/EntitySourceSession.kt` (~36-42, ~134-141), `app/.../di/AppModule.kt` (~92-100)
- Create: `app/src/main/kotlin/com/gekkotron/nidoo/di/ClimateModule.kt` (`@Provides @Singleton fun climateRepository(): ClimateRepository`)
- Test: `core/source/src/jvmTest/kotlin/com/gekkotron/nidoo/source/MqttDiscoverySourceClimateTest.kt` (copy of `MqttDiscoverySourceDashboardsTest.kt`)

**Interfaces:**
- Consumes: `ClimateSink`, `ClimateTopics.parse` (Task 1).
- Produces: `MqttDiscoverySource(..., climateSink: ClimateSink? = null)`.

- [ ] **Step 1: Write the failing tests:**
  - `routesZoneDocsToSink`: a retained `nidoo/climate/zone/living/decision` reaches the sink with `("living", DECISION, payload)` and creates no entity.
  - `ignoresRestoredClimate`.
  - `holdCommandIsNotAnEntity`: a non-retained `…/hold/set` echo creates no entity and doesn't reach the sink.
- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :core:source:jvmTest --tests '*Climate*'`
- [ ] **Step 3: Implement:** add a `when` branch before the discovery `else`. For any topic starting with `ClimateTopics.PREFIX + "/"`, route it when `parse` matches, and always consume it. Pass the sink through `EntitySourceSession` and `AppModule`, like `dashboardsSink`.
- [ ] **Step 4: Run and confirm it passes**

Run: `./gradlew :core:source:jvmTest :app:testDebugUnitTest`
- [ ] **Step 5: Commit** `feat(climate): feed the hub's Climat documents to the app`

---

### Task 3: Room banner state

**Files:**
- Create: `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/presentation/ClimatBanner.kt`
- Modify: `feature/home/build.gradle.kts`, which needs no new dependency if `core:source` exposes `core:climate` via `api`
- Modify: `presentation/HomeModels.kt` (`HomeUiState` gets `climat: ClimatBannerUi? = null`), `presentation/HomeViewModel.kt` (inject `ClimateRepository` and combine its `zones` into the state, rebuilt on a 30 s ticker so "until" and expiry stay right)
- Test: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/presentation/ClimatBannerTest.kt`, plus `HomeViewModelTest` (or the existing VM test) for the wiring

**Interfaces (produces):**
```kotlin
enum class ClimatWhy { SCHEDULE, BOOST, HOLD, ECO, AWAY, HOLIDAY, EMPTY, SAFETY, IDLE, OTHER }
data class ClimatBoostUi(val target: Double, val until: Long)          // Unix s
data class ClimatBannerUi(val zone: String, val reading: Double?, val target: Double?, val why: ClimatWhy,
                          val reason: String, val boost: ClimatBoostUi?, val holdUntil: Long?,
                          val canBoost: Boolean, val min: Double, val max: Double)
fun climatBannerFor(areaId: String, zones: Collection<ClimateZone>, entities: Map<String, Entity>, nowSec: Long): ClimatBannerUi?
```
Rules (spec §6.2–6.3):
- Only `state == "active"` zones count.
- A zone's devices are `decision.devices` (else `settings.entities`) plus `sensor`.
- A zone matches when any of those ids is an entity with `area == areaId`.
- The boost is the live (`until > now`) `entity == null` override with a `Temp` setpoint. The hold is any other live `entity == null` override.
- `why` comes from `decision.source`. `"override"` with a `Temp` setpoint counts as `BOOST` (old hub).
- `canBoost = why != SAFETY`.
- `min`/`max` are the intersection of the zone devices' `capabilities.minTemp`/`maxTemp`. Fall back to 7.0/35.0 (`ClimateMapper` defaults).

The ViewModel exposes the banner only when `selected is RoomFilter.Area`.

- [ ] **Step 1: Write the failing tests** in `ClimatBannerTest`. Each test builds entities with `testEntity` and zones with the Task 1 types:
  - `noZonesNoBanner`
  - `pendingAndOffZonesNoBanner`
  - `matchesByDecisionDevicesWhenEntitiesNull`
  - `matchesBySensorOnly`
  - `otherRoomNoBanner`
  - `liveBoostShown`
  - `expiredBoostIgnored`
  - `oldHubOverrideWithTempIsBoost`
  - `modeOnlyOverrideIsHold`
  - `safetyHidesBoost`
  - `rangeIsIntersectionOfDevices`
  - `twoZonesPickMostMatchesThenId`

  In the VM test, `bannerOnlyForAreaFilter` checks that `All`/`Favorites` give `climat == null`.
- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :feature:home:testDebugUnitTest --tests '*ClimatBanner*'`
- [ ] **Step 3: Implement** `climatBannerFor` and the VM wiring.
- [ ] **Step 4: Run and confirm they pass**

Run: `./gradlew :feature:home:testDebugUnitTest`
- [ ] **Step 5: Commit** `feat(home): work out the Climat banner for the selected room`

---

### Task 4: Boost and cancel commands

**Files:**
- Modify: `presentation/HomeViewModel.kt` (inject `MqttTransport`; new actions)
- Test: the HomeViewModel test file, using `FakeMqttTransport` from `core/transport` testFixtures

**Interfaces (produces):**
- `fun startBoost(zone: String, setpoint: Double, minutes: Int)`
- `fun cancelBoost(zone: String)`
- `HomeUiState.climatSending: Boolean`
- A one-shot error through the VM's existing error/message channel. Reuse what `onSend` uses for `PublishFailed`; if there's none, add `HomeUiState.climatError: Boolean` with `fun dismissClimatError()`.

**Behavior:**
- Publish `ClimateTopics.holdCommand(zone)` with `ClimateCommands.boost(..)` / `CANCEL`, `qos = 1`, `retain = false`.
- `climatSending` is true until the zone's `overrides` change, or `BOOST_CONFIRM_MS = 5_000` elapse. On timeout, or if `publish` throws, raise the error.

- [ ] **Step 1: Write the failing tests:**
  - `startBoostPublishesHoldCommand`: topic, payload `{"setpoint":22.0,"minutes":90}`, and `retain false`.
  - `cancelPublishesEmpty`.
  - `confirmedWhenOverridesChange`: feed a new override into the repository, then `climatSending` is false and there's no error.
  - `errorAfterTimeout`: `advanceTimeBy(5_001)`.
  - `errorWhenPublishFails`.
- [ ] **Step 2: Run them and confirm they fail.** Same command as Task 3.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run and confirm they pass.**
- [ ] **Step 5: Commit** `feat(home): send a Climat boost to the hub`

---

### Task 5: Banner and boost sheet UI

**Files:**
- Create: `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/ui/ClimatBanner.kt` (`ClimatBanner`, `BoostSheet`)
- Modify: `ui/HomeScreen.kt`: add `item(key = "climat-banner", span = { GridItemSpan(maxLineSpan) })` between the `"chips"` item (~242-251) and the cards (~281), when `state.climat != null && !onDashboard`. Wire `startBoost`/`cancelBoost` through the screen's action lambdas.
- Modify: `feature/home/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `src/test/.../ui/StringParityTest.kt`
- Test: `feature/home/src/test/kotlin/com/gekkotron/nidoo/feature/home/ui/ClimatBannerTest.kt` (Robolectric, `@Config(qualifiers = "w411dp-h914dp")`, as in `HomeScreenTest`)

**Interfaces (produces):**
- `@Composable internal fun ClimatBanner(ui: ClimatBannerUi, onBoost: () -> Unit, onCancel: () -> Unit)`
- `@Composable internal fun BoostSheet(ui: ClimatBannerUi, sending: Boolean, error: Boolean, onStart: (Double, Int) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit)`

**Banner (mockup option A):**
- Built on `NidooCard`, full width.
- Room reading large. The target on the right.
- A "why" line, from a `ClimatWhy` → string map, plus the hub `reason` only for `SCHEDULE`.
- **Boost** button when `canBoost` and there's no boost.
- With a boost: "Boost {target}° until {HH:mm}" and a **Cancel** button.
- With a hold: "Hold until {HH:mm}" and **Cancel**.
- Missing numbers show "—".

**Sheet:**
- Built like `ControlSheet` (`ModalBottomSheet`, `skipPartiallyExpanded = true`, 24 dp padding).
- −/+ stepper using `StepMath.stepTemperature`, step **0.5**, clamped to `min`/`max`. It starts at the live boost target, else the decision target, else 20.0.
- Target chips: target−1, target+1, target+2, clamped.
- Duration `FilterChip`s **1 h · 1 h 30 · 2 h**. Default 1 h, or the live boost's nearest duration.
- The line "Room at {v}° until {now+d HH:mm} · ends if you leave".
- **Start boost** / **Update** (when a boost is live), plus **Cancel** when a boost is live.
- The buttons are disabled while `sending`. The error text shows on `error`.
- Times use the phone's local timezone.

**Strings (EN / FR):**

| Key | EN | FR |
|---|---|---|
| `climat_title` | Climat | Climat |
| `climat_boost` | Boost | Boost |
| `climat_cancel` | Cancel | Annuler |
| `climat_start` | Start boost | Lancer le boost |
| `climat_update` | Update | Mettre à jour |
| `climat_boost_until` | Boost %1$s° until %2$s | Boost %1$s° jusqu'à %2$s |
| `climat_hold_until` | Hold until %1$s | Maintien jusqu'à %1$s |
| `climat_sheet_line` | Room at %1$s° until %2$s · ends if you leave | Pièce à %1$s° jusqu'à %2$s · s'arrête si vous partez |
| `climat_error` | Climat didn't take the boost | Climat n'a pas pris le boost |
| `climat_why_*` | Schedule / Boost / Hold / Eco / Away / Holiday / Room empty / Safety / Idle / — | Programme / Boost / Maintien / Éco / Absent / Vacances / Pièce vide / Sécurité / À l'arrêt / — |

- [ ] **Step 1: Write the failing Compose tests:**
  - `bannerShowsReadingTargetAndBoost`
  - `bannerWithBoostShowsUntilAndCancel`
  - `bannerSafetyHasNoBoostButton`
  - `sheetDefaultsToOneHour`
  - `sheetStartSendsChosenValues`: tap +, pick 1 h 30, Start, and `onStart(20.5, 90)` is called.
  - `sheetClampsToMax`
  - `sheetDisabledWhileSending`

  In `HomeScreenTest`, `bannerOnlyInRoomView`. Also add the keys to `StringParityTest`.
- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :feature:home:testDebugUnitTest --tests '*ClimatBanner*' --tests '*HomeScreenTest*' --tests '*StringParity*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run and confirm they pass.** Same command.
- [ ] **Step 5: Commit** `feat(home): Climat banner and boost sheet in the room view`

---

### Task 6: Demo house Climat zone

**Files:**
- Modify: `core/demo/src/main/kotlin/com/gekkotron/nidoo/demo/DemoHouse.kt`, `DemoBroker.kt`
- Test: `core/demo/src/test/kotlin/com/gekkotron/nidoo/demo/DemoHouseTest.kt`, `DemoBrokerTest.kt`

**Behavior:**
- Zone `living` covers `demo_living_ac`, plus a living-room thermometer if the demo has one (otherwise the AC only).
- Retained `settings` (active), `decision` (schedule 20.0, reading 19.4, `entities: {"demo_living_ac": {}}`), and `override` `[]`.
- `nidoo/climate/zone/living/hold/set` is handled by a dedicated climate responder, not the JSON-object `echo`, because `override` is an array:
  - A boost payload republishes `override` as `[{"entity":null,"setpoint":v,"mode":null,"until":now+min*60,"origin":"user"}]`, and `decision` with `source "boost"`, `setpoint v`, and reason `"Boost to v °C until HH:mm"`.
  - An empty payload republishes `override` `[]` and the schedule decision.
- Extend `DemoBroker.publish` so a command can answer with several retained messages: `onCommand` returns `List<DemoMessage>`, and the existing single echo becomes a one-element list.

- [ ] **Step 1: Write the failing tests:**
  - `demoHasActiveLivingZone`: the retained messages include the 3 docs.
  - `holdCommandBoostsDemoZone`.
  - `emptyHoldCommandCancels`.
  - The existing echo tests are unchanged.
- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :core:demo:test`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, then the full gate**

Run: `./gradlew :core:demo:test && ./gradlew check`
Expected: PASS.
- [ ] **Step 5: Commit** `feat(demo): a Climat zone for the demo living room`
