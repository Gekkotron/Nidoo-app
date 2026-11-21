# Home Rooms View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home gets a Rooms view (one card per room with status and one-tap whole-room light and shutter buttons), a room view laid out by type with a sensor strip, and an always-visible search field.

**Architecture:** Everything stays inside the existing Home screen and its state. Pure builders in `presentation/` (room cards, room commands, sections) feed new fields of `HomeUiState`; `HomeViewModel` gains one whole-room send with merged failures; `HomeScreen` renders room cards, room pills, the strip and section labels in the same `LazyVerticalGrid`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, coroutines/Flow, JUnit4 + Robolectric + Compose UI test. Module `feature/home`, glyphs in `core/designsystem`.

**Spec:** `docs/superpowers/specs/2026-11-21-home-rooms-design.md` (read it first; section numbers below refer to it). Mockup: `docs/design/mockups/home-rooms-layout.html`.

## Global Constraints

- Android app only. No change under `shared/`, `iosApp/`, the hub, widgets or voice.
- No new colour or type token: only `NidooTheme.colors`, `NidooTheme.type`, `NidooDimens`. Touch targets ≥ `NidooDimens.minTouch` (48 dp).
- Tiles (`EntityCard`, `GroupCard`), control sheets, Customize and dashboards' content are not modified.
- Whole-room commands are absolute (`TurnOn`/`TurnOff`/`Open`/`Close`/`Stop`), never `Toggle`.
- A whole-room button only targets standalone entities (`partOf == null`) with `Placement.onHome(...)` true whose own `area` is the room. Never a hub group entity (`deviceId == HUB_GROUP_DEVICE_ID`).
- Every new string exists in `feature/home/src/main/res/values/strings.xml` and `values-fr/strings.xml` (the `*StringParity*` test enforces it). Counts use `<plurals>`.
- Paths below are relative to `feature/home/src/main/kotlin/com/gekkotron/nidoo/feature/home/` (tests: same under `src/test/kotlin/...`) unless they start with a module name.
- Test command: `./gradlew :feature:home:testDebugUnitTest --tests '<pattern>'`. Use `testEntity(...)`, `FakeEntitySource`, `withLivingHubGroups` from `TestEntities.kt` / `FakeEntitySource.kt`.
- Commits: one per task, identity passed on the command line, never written to git config:
  `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit -m "<message>"`.
- Work on a new branch `feat/home-rooms` cut from `master` (the current branch `fix/presence-verify-exit` is unrelated). Do not push or open a PR unless asked.

## Review Focus

Inputs the spec implies that are most likely to bite; each has a test in the task named.

1. A house with no areas at all (everything Unassigned): Rooms shows one "Unassigned" card with no buttons, and nothing crashes. (Task 1)
2. A room whose only lights are unavailable: the lights button shows but a tap sends nothing and raises no snackbar. (Task 2)
3. A light with an on-state preset in a room where another light has none: one tap turns both on, each with its own command, and their failures share one snackbar. (Task 5)
4. A saved selection `area:x` for a room that no longer has devices, on cold start: lands on Favorites or Rooms, not a blank room. (Task 4)
5. Typing in the search field from inside a room, then system back twice: first back clears the query and shows the room again, second goes to Rooms. (Task 8)

---

## File Structure

| File | Responsibility |
|---|---|
| `presentation/RoomCards.kt` (new) | `RoomIcon`, `RoomTarget`, `RoomCardUi`, `RoomCards.build` |
| `presentation/RoomCommands.kt` (new) | what a lights / shutters tap sends |
| `presentation/HomeSections.kt` (new) | `HomeSection`, `SectionUi`, splitting cards into strip + sections |
| `presentation/HomeModels.kt` | `RoomFilter.Rooms`, new `HomeUiState` fields, `HomeEvent.RoomNotConfirmed` |
| `presentation/HomeUiBuilder.kt` | wires the three builders, new pills and fallback |
| `presentation/HomeViewModel.kt` | `sendRoom`, `retryRoom`, query cleared on `select` |
| `core/designsystem/.../icons/StaticIcon.kt` | room glyphs |
| `ui/RoomCard.kt` (new) | the room card and its two buttons |
| `ui/SensorStrip.kt` (new) | strip pills |
| `ui/HomeSearchField.kt` (new) | the always-visible field (replaces `SearchField` in `HomeScreen.kt`) |
| `ui/RoomText.kt` (new) | status line and button descriptions from `RoomCardUi` |
| `ui/HomeScreen.kt`, `ui/HomeEventsEffect.kt` | layout of the four views, back handling, room snackbar |

---

### Task 1: Room cards model and builder

**Files:**
- Create: `presentation/RoomCards.kt`
- Test: `presentation/RoomCardsTest.kt`, `presentation/RoomIconTest.kt`

**Interfaces:**
- Consumes: `Entity`, `EntityState`, `Area`, `Placement.onHome`, `UNASSIGNED_AREA`, `HUB_GROUP_DEVICE_ID`, `LightOnPreset`, `CardFormatter` (for the sensor value).
- Produces:

```kotlin
enum class RoomIcon { LIVING_ROOM, KITCHEN, BEDROOM, BATHROOM, OFFICE, GARAGE, GARDEN, ENTRANCE, LAUNDRY, TOILET, GENERIC;
    val key: String get() = name.lowercase()
    companion object { fun of(areaIcon: String?, areaName: String?): RoomIcon }
}

/** One device a whole-room button acts on. For a shutter, [on] means "not closed" (spec §6.3). */
data class RoomTarget(
    val id: String, val name: String, val available: Boolean, val stale: Boolean, val on: Boolean,
    /** OPENING or CLOSING while a shutter moves; null otherwise and for lights. */
    val moving: CoverState? = null,
    /** What turning this light on sends (its preset, or a bare on). */
    val onCommand: Command.TurnOn = Command.TurnOn(),
)

data class RoomCardUi(
    val areaId: String,
    /** Null = Unassigned, localized by the UI. */
    val name: String?,
    val icon: RoomIcon,
    val temperature: CardValue.Measurement?, val humidity: CardValue.Measurement?,
    val lights: List<RoomTarget>, val shutters: List<RoomTarget>,
    /** Devices Home shows in this room. */
    val deviceCount: Int,
    /** False for Unassigned: a card without whole-room buttons. */
    val buttons: Boolean,
) { val lightsOn: Int; val shuttersOpen: Int; val shuttersMoving: CoverState? }

object RoomCards {
    fun build(entities: Collection<Entity>, states: Map<String, EntityState>, areas: List<Area>, names: Map<String, String>, locale: Locale): List<RoomCardUi>
}
```

`HomeViewModel.turnOnFor(card)` moves here as `internal fun lightOnCommand(ui: UiOverride, capabilities: Capabilities): Command.TurnOn` (same body, read from the entity instead of the card); `HomeViewModel` calls it. Check how `EntityState` exposes `stale` and availability by reading `HomeUiBuilder.card(...)` and use the same expressions.

- [ ] **Step 1: Write `RoomIconTest`**

```kotlin
@Test fun `a known Area icon key wins over the name`() = assertEquals(RoomIcon.GARAGE, RoomIcon.of("garage", "Salon"))
@Test fun `an unknown Area icon key falls back to the name`() = assertEquals(RoomIcon.LIVING_ROOM, RoomIcon.of("mdi:sofa", "Salon"))
@Test fun `names match in English and French, whatever the case and accents`() {
    mapOf("Living room" to RoomIcon.LIVING_ROOM, "SALON" to RoomIcon.LIVING_ROOM, "Cuisine" to RoomIcon.KITCHEN, "Kitchen" to RoomIcon.KITCHEN,
        "Chambre parents" to RoomIcon.BEDROOM, "Kids bedroom" to RoomIcon.BEDROOM, "Salle de bain" to RoomIcon.BATHROOM, "Bathroom" to RoomIcon.BATHROOM,
        "Bureau" to RoomIcon.OFFICE, "Office" to RoomIcon.OFFICE, "Garage" to RoomIcon.GARAGE, "Jardin" to RoomIcon.GARDEN, "Garden" to RoomIcon.GARDEN,
        "Entrée" to RoomIcon.ENTRANCE, "Entrance" to RoomIcon.ENTRANCE, "Buanderie" to RoomIcon.LAUNDRY, "Laundry" to RoomIcon.LAUNDRY,
        "WC" to RoomIcon.TOILET, "Toilettes" to RoomIcon.TOILET,
    ).forEach { (name, icon) -> assertEquals(name, icon, RoomIcon.of(null, name)) }
}
@Test fun `anything else is generic`() { assertEquals(RoomIcon.GENERIC, RoomIcon.of(null, "Mezzanine")); assertEquals(RoomIcon.GENERIC, RoomIcon.of(null, null)) }
```

- [ ] **Step 2: Write `RoomCardsTest`** with these tests (areas `living`, `kitchen` in that order; build through a local helper):

| Test name | Arrange | Assert |
|---|---|---|
| `one card per room with a Home device, in area order, Unassigned last` | lamp in kitchen, lamp in living, switch unassigned, area `garage` empty | `areaId`s = `[living, kitchen, UNASSIGNED_AREA]`; last has `name == null`, `buttons == false` |
| `a house with no areas shows one Unassigned card` | `areas = emptyList()`, two unassigned lights | one card, `buttons == false`, `lights.size == 2` |
| `lights are lights and switches shown as lights, never hidden, companions or hub group entities` | living: light, switch `showAs="light"`, plain switch, hidden light, light with `partOf`, hub group entity (`deviceId = HUB_GROUP_DEVICE_ID`, kind LIGHT) | `lights.map { it.id }` = the first two only |
| `a hub group member counts in its own room only` | `withLivingHubGroups` plus a light in kitchen | living card lists the living lights; kitchen card lists its own |
| `a device shown only on Security is not a target and not counted` | cover in living with `ui.energyRole`/a door binary sensor (use what makes `Placement.onHome` false) | absent from targets; `deviceCount` excludes it |
| `a light's preset is its on command` | light with `ui.lightOnPreset` set to a valid `LightOnPreset` key and brightness capability | `onCommand == lightOnCommand(ui, capabilities)` and is not `Command.TurnOn()` |
| `a shutter is closed at position 0 or CLOSED without position, open otherwise` | covers: `Cover(CLOSED, 0)`, `Cover(CLOSED, null)`, `Cover(OPEN, 100)`, `Cover(STOPPED, 40)`, `Cover(UNKNOWN, null)`, `Cover(OPEN, 0)` | `on` = `[false, false, true, true, true, false]`; `shuttersOpen == 3` |
| `a moving shutter reports its direction` | `Cover(CLOSING, 60)` | `moving == CLOSING`, `shuttersMoving == CLOSING` |
| `temperature and humidity are the first available sensor of that class` | two temperature sensors (names "A", "B"; "A" has no state), one humidity 48.0 `%` | `temperature` is B's `Measurement`; `humidity?.text == "48"` |
| `counts` | 4 lights, 2 on | `lightsOn == 2`, `deviceCount == 4` |

- [ ] **Step 3: Run both; expect compilation failure.** `./gradlew :feature:home:testDebugUnitTest --tests '*RoomCardsTest*' --tests '*RoomIconTest*'`

- [ ] **Step 4: Implement `RoomCards.kt`.** `RoomIcon.of`: lower-case and strip accents (`java.text.Normalizer` NFD, drop `\p{Mn}`), then first keyword contained wins, in this order: `salle de bain|salle d'eau|bath|douche|shower` → BATHROOM; `wc|toilet` → TOILET; `chambre|bedroom` → BEDROOM; `salon|sejour|living|lounge` → LIVING_ROOM; `cuisine|kitchen` → KITCHEN; `bureau|office|study` → OFFICE; `garage` → GARAGE; `jardin|garden|terrasse|terrace` → GARDEN; `entree|entrance|hall` → ENTRANCE; `buanderie|laundry` → LAUNDRY. `RoomCards.build` is one pass grouping eligible entities by `area`; "first" sensor = lowest (`ui.order ?: Int.MAX_VALUE`, name lower-cased in `locale`, id), same as tiles.

- [ ] **Step 5: Run; expect PASS.** Also run `--tests '*HomeViewModelTest*'` (the `turnOnFor` move).

- [ ] **Step 6: Commit** `feat(home): room cards model and builder`

---

### Task 2: Whole-room commands

**Files:**
- Create: `presentation/RoomCommands.kt`
- Test: `presentation/RoomCommandsTest.kt`

**Interfaces:**
- Consumes: `RoomCardUi`, `RoomTarget` (Task 1).
- Produces:

```kotlin
enum class RoomKind(val key: String) { LIGHT("light"), COVER("cover") }
enum class ShutterAction { STOP, CLOSE, OPEN }

object RoomCommands {
    fun sendId(areaId: String, kind: RoomKind): String            // "room:<areaId>:<key>"
    /** What a tap would do now; null when no shutter is available. */
    fun shutterAction(room: RoomCardUi): ShutterAction?
    /** Device id → command for one tap; empty when nothing would be sent. */
    fun commands(room: RoomCardUi, kind: RoomKind): Map<String, Command>
    /** A device that would receive a command has a cached, unconfirmed value: send nothing. */
    fun blockedByStale(room: RoomCardUi, kind: RoomKind): Boolean
}
```

- [ ] **Step 1: Write `RoomCommandsTest`** (build `RoomCardUi` by hand from `RoomTarget`s):

| Test name | Targets | Assert `commands(...)` |
|---|---|---|
| `any light on turns off the lights that are on` | a on, b off, c on | `{a: TurnOff, c: TurnOff}` |
| `no light on turns each one on with its own command` | a off (`onCommand = TurnOn(brightness = 128)`), b off | `{a: TurnOn(brightness = 128), b: TurnOn()}` |
| `unavailable lights are skipped and do not count as on` | a on but unavailable, b off | `{b: TurnOn()}` |
| `a room whose only lights are unavailable sends nothing` | a unavailable | empty; `blockedByStale` false |
| `a moving shutter stops the moving ones only` | a moving CLOSING, b open | `shutterAction == STOP`; `{a: Stop}` |
| `any open shutter closes those not closed` | a open, b closed | `CLOSE`; `{a: Close}` |
| `all closed opens all` | a closed, b closed | `OPEN`; `{a: Open, b: Open}` |
| `no available shutter has no action` | a unavailable | `shutterAction == null`; empty |
| `a stale receiver blocks the send` | a on + stale, b off | `blockedByStale(LIGHT)` true |
| `a stale device that would not receive the command does not block` | a on, b off + stale | `blockedByStale(LIGHT)` false |
| `send ids differ per kind` | — | `sendId("living", LIGHT) == "room:living:light"`, `COVER` → `"room:living:cover"` |

- [ ] **Step 2: Run; expect compilation failure.** `--tests '*RoomCommandsTest*'`
- [ ] **Step 3: Implement.** `blockedByStale` = any key of `commands(...)` whose target is stale.
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Commit** `feat(home): whole-room light and shutter commands`

---

### Task 3: Sections and strip

**Files:**
- Create: `presentation/HomeSections.kt`
- Test: `presentation/HomeSectionsTest.kt`

**Interfaces:**
- Consumes: `CardUi`, `Kind`.
- Produces:

```kotlin
enum class HomeSection { LIGHTS, SHUTTERS_CLIMATE, PLUGS_MEDIA, OTHER, SENSORS }
data class SectionUi(val section: HomeSection, val cards: List<CardUi>, /** False when it is the only section. */ val showLabel: Boolean)
data class HomeLayout(val strip: List<CardUi>, val sections: List<SectionUi>)

object HomeSections {
    fun of(card: CardUi): HomeSection
    /** A room: sensors go to [HomeLayout.strip]. */
    fun room(cards: List<CardUi>): HomeLayout
    /** All devices: sensors are the last section, as tiles; no strip. */
    fun allDevices(cards: List<CardUi>): HomeLayout
}
```

`of` maps on `card.kind` (the effective kind, so a switch shown as a light is LIGHT; group cards carry their kind): `LIGHT` → LIGHTS; `COVER`, `CLIMATE`, `WATER_HEATER` → SHUTTERS_CLIMATE; `SWITCH`, `MEDIA_PLAYER`, `VACUUM`, `BUTTON` → PLUGS_MEDIA; `SENSOR`, `BINARY_SENSOR` → SENSORS; every other `Kind` → OTHER. Sections come out in enum order; a section keeps the incoming relative order of its cards (stable); empty sections are dropped.

- [ ] **Step 1: Write `HomeSectionsTest`:**
  - `every kind has a section`: loop over `Kind.entries`, assert the mapping above (so a new `Kind` fails the test until placed).
  - `a switch shown as a light is a light`: card with `kind = LIGHT, protocolKind = SWITCH` → LIGHTS.
  - `room puts sensors in the strip and nowhere else`: cards [sensor, light, binary sensor, cover] → `strip` ids = the two sensors, no section is SENSORS.
  - `allDevices keeps sensors as a last section and has no strip`.
  - `no card is lost or duplicated`: for a list with one card of every kind, `strip + sections.flatMap { it.cards }` has the same ids as the input, each once (both `room` and `allDevices`).
  - `order inside a section is the incoming order`.
  - `a single section hides its label`, `two sections show theirs`.
- [ ] **Step 2: Run; expect compilation failure.** `--tests '*HomeSectionsTest*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Commit** `feat(home): group Home cards by type`

---

### Task 4: State shape, Rooms filter and fallback

**Files:**
- Modify: `presentation/HomeModels.kt`, `presentation/HomeUiBuilder.kt`
- Test: `presentation/HomeUiBuilderTest.kt` (update + add), `presentation/HomeViewModelTest.kt` (update)

**Interfaces:**
- Consumes: Tasks 1 and 3.
- Produces, in `HomeModels.kt`:

```kotlin
// RoomFilter
data object Rooms : RoomFilter { override val key = "rooms" }      // fromKey("rooms") -> Rooms

// HomeUiState: `chips` now holds the top-level pills only
val roomChips: List<ChipUi> = emptyList(),      // one RoomFilter.Area per room card, same order
val roomCards: List<RoomCardUi> = emptyList(),
val strip: List<CardUi> = emptyList(),          // a room view only
val sections: List<SectionUi> = emptyList(),    // a room view and All devices; empty elsewhere and while searching
```

`HomeUiState.cards` stays the full list for the view (strip and section cards included), so `HomeUiState.card(id)` and every existing caller keep working.

Builder rules (spec §6.1):
- `chips` = Favorites (if any) · Rooms (if `roomCards` is not empty) · All · Hidden (if any).
- `roomChips` = `roomCards` mapped to `ChipUi(RoomFilter.Area(areaId), name)`.
- `selected` is valid when it is in `chips` or in `roomChips`. Fallback: Favorites if present, else Rooms if present, else All.
- `selected == Rooms`: `cards`, `strip`, `sections` empty.
- `selected is Area`: `HomeSections.room(cards)`; `selected == All`: `HomeSections.allDevices(cards)`; Favorites, Hidden and any non-blank `query`: `strip` and `sections` empty (flat grid as today).
- `roomCards` and both chip lists are built whatever the selection and the query.

- [ ] **Step 1: Update and add tests in `HomeUiBuilderTest`.**
  - Update `empty source is loading`: `chips == listOf(ChipUi(RoomFilter.All, null))` still holds (no room card).
  - Replace the chips test with `top-level pills are Favourites when any, Rooms, All, Hidden when any, and rooms move to roomChips`: assert exact `chips.map { it.filter }` and `roomChips.map { it.filter }` = areas with visible cards in area order.
  - Add `without favourites the fallback is Rooms`: build with `selected = RoomFilter.Favorites`, no favourite → `selected == RoomFilter.Rooms`.
  - Add `a saved room without devices falls back`: `selected = RoomFilter.Area("garage")` (empty area) → Rooms; with a favourite → Favorites.
  - Add `the Rooms view has room cards and no device cards`.
  - Add `a room view has a strip and sections that together are exactly its cards`.
  - Add `All devices has sections with sensors last and no strip`.
  - Add `searching is flat`: `query = "lamp"` with `selected = Area("living")` → `sections` and `strip` empty, `cards` holds the matches.
  - Add `Favorites and Hidden stay flat`.
  - Fix any other test in the module that reads room entries from `chips` (search for `chips` under `src/test`): read `roomChips`.
- [ ] **Step 2: Update `HomeViewModelTest`:** `a stale Hidden selection resets to All…` now resets to Rooms when the fixture has a room card (rename the test accordingly); add `a saved room that lost its devices lands on Rooms and the saved key follows`.
- [ ] **Step 3: Run; expect failures.** `--tests '*HomeUiBuilderTest*' --tests '*HomeViewModelTest*'`
- [ ] **Step 4: Implement** in `HomeModels.kt` and `HomeUiBuilder.build` (call `RoomCards.build(grid, states, areas, names, locale)` with the builder's `grid` plus hidden-by-group members: pass `visible` minus the hero, which is exactly today's `grid`).
- [ ] **Step 5: Compile the module** (`./gradlew :feature:home:compileDebugKotlin`): `HomeScreen.HomeChips` has a `when` over `RoomFilter`; add the `Rooms` branch with a temporary label `stringResource(R.string.chip_rooms)` and add `chip_rooms` = "Rooms" / "Pièces" to both string files so the app still builds between tasks.
- [ ] **Step 6: Run the whole module; expect PASS.** `./gradlew :feature:home:testDebugUnitTest`
- [ ] **Step 7: Commit** `feat(home): Rooms filter and sectioned Home state`

---

### Task 5: View model — whole-room send, merged failures, Retry

**Files:**
- Modify: `presentation/HomeViewModel.kt`, `presentation/HomeModels.kt`
- Test: `presentation/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `RoomCommands` (Task 2), `HomeUiState.roomCards` (Task 4).
- Produces:

```kotlin
// HomeEvent
/** Devices of one whole-room tap that were not confirmed, merged. [failed] is device id → the command to repeat. */
data class RoomNotConfirmed(val sendId: String, val areaId: String, val kind: RoomKind, val failed: Map<String, Command>, val failedNames: List<String>) : HomeEvent

// HomeViewModel
fun sendRoom(areaId: String, kind: RoomKind)
fun retryRoom(event: HomeEvent.RoomNotConfirmed)
fun select(filter: RoomFilter)     // now also clears the search query
```

Behaviour:
- `sendRoom`: find the room in `state.value.roomCards`; `blockedByStale` → `HomeEvent.WaitingForConnection` and nothing sent; otherwise send every entry of `RoomCommands.commands(...)` in parallel through `source.send`; the first `CommandResult.Rejected` → `HomeEvent.Rejected(sendId, reason)`. An empty map sends nothing and emits nothing.
- Failures: today `groupSends[id]` holds one `command` and buckets are keyed `groupId to command`, which would split a lights-on tap (per-light commands differ) into several snackbars. Give `GroupSend` a `bucket: Any` (the command for hub groups, unchanged; the `sendId` for room sends) and key `failedInGroup` by `groupId to bucket`. A room failure is matched when `groupSends[failure.uniqueId]` is a room send whose recorded command for that id equals `failure.command`. After the same 1 s window emit one `RoomNotConfirmed`.
- `retryRoom`: re-sends `event.failed` only, registered again as a room send under the same `sendId`.
- A device's own `send(id, …)` still removes its `groupSends` entry (unchanged).

- [ ] **Step 1: Add tests to `HomeViewModelTest`** (living room: lamp `a` on, lamp `b` off, cover `c` open, via `FakeEntitySource`):
  - `the lights button turns off what is on`: `sendRoom("living", RoomKind.LIGHT)` → `source.sent == listOf("a" to Command.TurnOff)`.
  - `the lights button turns every light on with its own preset`: both off, `a` has a preset → `sent` holds `a` with the preset command and `b` with `Command.TurnOn()`.
  - `the shutters button closes, then opens`: first tap `c to Command.Close`; set state closed; second tap `c to Command.Open`.
  - `a stale light blocks the room and asks to wait for the connection`: nothing in `sent`, event `WaitingForConnection`.
  - `a room whose lights are all unavailable sends nothing and says nothing`.
  - `failures of one room tap share one snackbar, even with different commands`: turn-on tap, emit two `CommandFailure`s (one per light, each with that light's command) on `source.commandFailures`, advance 1 s → exactly one `RoomNotConfirmed` with `failed.keys == setOf("a", "b")` and `sendId == "room:living:light"`.
  - `Retry repeats the failed devices only, each with its command`: `retryRoom(event)` → `sent` gains exactly `event.failed`'s pairs.
  - `lights and shutters of the same room fail separately`: one failure each → two events.
  - `a rejected room command reports the room send id`.
  - `selecting a view clears the search`: `search("lamp")`, `select(RoomFilter.Rooms)` → `state.query == ""`.
  - Keep the existing hub group failure tests green (the `bucket` change must not alter them).
- [ ] **Step 2: Run; expect failures.** `--tests '*HomeViewModelTest*'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Commit** `feat(home): whole-room send with merged failures and Retry`

---

### Task 6: Room card UI and the Rooms view

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/icons/StaticIcon.kt`
- Create: `ui/RoomCard.kt`, `ui/RoomText.kt`
- Modify: `ui/HomeScreen.kt`, `ui/HomeEventsEffect.kt`, both `strings.xml`
- Test: `ui/RoomCardTest.kt`, `ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `RoomCardUi`, `RoomCommands.shutterAction`, `RoomKind`, `HomeViewModel.sendRoom/retryRoom`, `HomeEvent.RoomNotConfirmed`.
- Produces:

```kotlin
// core/designsystem: NidooGlyph gains SOFA, POT, BED, BATH, DESK, CAR, LEAF, DOOR_OPEN, WASHER, TOILET, HOUSE
// (24-unit duotone paths in the style of the existing glyphs; reuse an existing glyph when one already draws it).

@Composable internal fun RoomCard(room: RoomCardUi, onOpen: () -> Unit, onLights: () -> Unit, onShutters: () -> Unit, modifier: Modifier = Modifier)
@Composable internal fun roomName(room: RoomCardUi): String                 // name or area_unassigned
@Composable internal fun roomStatus(room: RoomCardUi): String               // spec §6.2 status line
@Composable internal fun shutterActionLabel(room: RoomCardUi): String?      // "Close Living room shutters" …
internal fun roomGlyph(icon: RoomIcon): NidooGlyph

// HomeScreen / HomeRoute gain:
onSendRoom: (areaId: String, kind: RoomKind) -> Unit = { _, _ -> }
// HomeEventsEffect gains:
onRetryRoom: (HomeEvent.RoomNotConfirmed) -> Unit = {}
```

Strings (en / fr):

| Key | en | fr |
|---|---|---|
| `chip_rooms` (Task 4) | Rooms | Pièces |
| `chip_all` (changed) | All devices | Tous les appareils |
| `room_lights_on` (plurals one/other) | %1$d light on / %1$d lights on | %1$d lumière allumée / %1$d lumières allumées |
| `room_lights_off` | Lights off | Lumières éteintes |
| `room_shutters_open` | Shutters open | Volets ouverts |
| `room_shutters_closed` | Shutters closed | Volets fermés |
| `room_shutters_some` | %1$d of %2$d open | %1$d sur %2$d ouverts |
| `room_shutters_opening` | Opening… | Ouverture… |
| `room_shutters_closing` | Closing… | Fermeture… |
| `room_devices` (plurals) | %1$d device / %1$d devices | %1$d appareil / %1$d appareils |
| `room_action_lights_on` | Turn on %1$s lights | Allumer les lumières : %1$s |
| `room_action_lights_off` | Turn off %1$s lights | Éteindre les lumières : %1$s |
| `room_action_shutters_open` | Open %1$s shutters | Ouvrir les volets : %1$s |
| `room_action_shutters_close` | Close %1$s shutters | Fermer les volets : %1$s |
| `room_action_shutters_stop` | Stop %1$s shutters | Arrêter les volets : %1$s |
| `room_card_description` | %1$s, %2$s | %1$s, %2$s |
| `home_room_not_confirmed_lights` (plurals) | %2$s: %1$d light didn\'t respond / %2$s: %1$d lights didn\'t respond | %2$s : %1$d lumière n\'a pas répondu / %2$s : %1$d lumières n\'ont pas répondu |
| `home_room_not_confirmed_covers` (plurals) | %2$s: %1$d shutter didn\'t respond / … shutters … | %2$s : %1$d volet n\'a pas répondu / %2$s : %1$d volets n\'ont pas répondu |

Layout decisions:
- `RoomCard` is a `NidooCard(on = room.lightsOn > 0, onClick = onOpen)` holding a `Row`: icon in a 40 dp rounded box (`StaticIcon(roomGlyph(room.icon))`), a weighted column (name in `NidooTheme.type.cardValue`, status in `NidooTheme.type.label` with `colors.contentMuted`, one line, ellipsis), then the buttons.
- A button is a 48 dp `Box` with a 36 dp circle, `Modifier.clickable(role = Role.Button)`, with the same haptic call `HomeScreen` already makes for tile interactions. Lights: `KindIcon(IconSpec.Light(on = room.lightsOn > 0, color = null, available = true))`, lit background `colors.cardOnBorder` when on. Shutters: `KindIcon(IconSpec.Shutter(target = …, moving = room.shuttersMoving))`. Shown only when `room.buttons` and the list is not empty; disabled (alpha 0.4, no click) when every target is unavailable.
- Status parts joined with `" · "` in the order temperature, humidity, lights, shutters; temperature/humidity through `cardValueText(measurement)`; when all four are absent, `room_devices`.
- Card test tag `room-<areaId>`; buttons `room-<areaId>-lights`, `room-<areaId>-shutters`. The card's content description is `room_card_description(name, status)`; each button has its action string as content description and `stateDescription` on/off for lights.
- In `HomeScreen`'s grid, when `state.selected == RoomFilter.Rooms` and not on a dashboard and no query: `items(state.roomCards, key = { "room-" + it.areaId }, span = { GridItemSpan(minOf(2, maxLineSpan)) })`. `onOpen = { onSelect(RoomFilter.Area(room.areaId)) }`.
- `HomeChips`: the `Rooms` label is `chip_rooms`.
- `HomeEventsEffect`: `RoomNotConfirmed` → one failure: `home_group_not_confirmed_one` with the device name; more: the room plural with the count and the room name (looked up by the caller: pass `roomName: (areaId: String) -> String` or resolve in `HomeRoute` from `state.roomCards`); action Retry → `onRetryRoom(event)`.

- [ ] **Step 1: Write `RoomCardTest`** (Robolectric Compose, like `GroupCardTest`):
  - `shows the name and the status line`: living, 21.5 °C, 48 %, 2 lights on → text "Living room" and a node containing "2 lights on" and "48".
  - `status without lights or sensors is the device count`.
  - `mixed shutters read n of m open`, `moving reads Closing…`.
  - `tapping the card opens the room, tapping a button does not`: counters on the three lambdas.
  - `the shutters button says what it will do`: open shutters → content description "Close Living room shutters".
  - `Unassigned has no buttons and the localized name`.
  - `a room with only unavailable lights shows a disabled lights button`: click does not call `onLights`.
- [ ] **Step 2: Add to `HomeScreenTest`:** `the Rooms view lists a card per room and no device tile`; `tapping a room card selects that room` (assert `onSelect` received `RoomFilter.Area("living")`); `tapping the lights button sends the room` (assert `onSendRoom("living", RoomKind.LIGHT)`).
- [ ] **Step 3: Run; expect failures.** `--tests '*RoomCardTest*' --tests '*HomeScreenTest*'`
- [ ] **Step 4: Implement** glyphs, `RoomText.kt`, `RoomCard.kt`, the grid branch, the event branch, strings; wire `HomeRoute` (`onSendRoom = viewModel::sendRoom`, `onRetryRoom = viewModel::retryRoom`).
- [ ] **Step 5: Run; expect PASS,** plus `--tests '*StringParity*'` and `./gradlew :core:designsystem:testDebugUnitTest`.
- [ ] **Step 6: Commit** `feat(home): Rooms view with whole-room buttons`

---

### Task 7: Room view — header, room pills, strip, sections

**Files:**
- Create: `ui/SensorStrip.kt`
- Modify: `ui/HomeScreen.kt`, both `strings.xml`
- Test: `ui/SensorStripTest.kt`, `ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeUiState.strip/sections/roomChips/roomCards`, `SectionUi`, `HomeSection`, `roomStatus`, `roomName`.
- Produces:

```kotlin
@Composable internal fun SensorStrip(cards: List<CardUi>, onClick: (CardUi) -> Unit, onLongClick: (CardUi) -> Unit, modifier: Modifier = Modifier)
@Composable internal fun sectionLabel(section: HomeSection): String
```

Strings: `section_lights` Lights / Lumières; `section_shutters_climate` Shutters & climate / Volets et climat; `section_plugs_media` Plugs & media / Prises et médias; `section_other` Other / Autres; `section_sensors` Sensors / Capteurs; `chip_back_rooms` ‹ Rooms / ‹ Pièces; `strip_pill_description` %1$s, %2$s, %3$s (name, value, room) in both.

Layout decisions:
- **Header** (`HomeHeader`): when `state.selected is RoomFilter.Area` and not on a dashboard, the title is the room's name (from `state.roomCards`, `roomName`) instead of the greeting, the line under it is `roomStatus(room)`, and the hero weather card is not drawn. Everything else in the header is unchanged.
- **Pills** (`HomeChips`): in a room the row is `ChipItem("rooms", chip_back_rooms)` followed by `state.roomChips`; selected key = the room's. Selecting the first calls `onSelect(RoomFilter.Rooms)`. Elsewhere the row is `state.chips`. `RoomChips` gets the selected item scrolled into view: add a `LazyListState` and `LaunchedEffect(selectedKey) { animateScrollToItem(index) }` in `core/designsystem/.../components/RoomChips.kt`.
- **Order of grid items in a room:** header, search field (Task 8), pills, Climat banner (unchanged condition), strip (one full-span item, only when not empty), then for each `SectionUi`: a full-span label item when `showLabel` (key `"section-" + section.name`), then its cards with exactly today's `items` block (extract that block into a local `LazyGridScope.homeCards(cards)` function so Favorites, Hidden, search, sections all call it).
- **All devices:** same loop over `state.sections`, no strip.
- **Favorites, Hidden, search results:** `homeCards(state.cards)` as today.
- **Section label:** `Text(label.uppercase(), style = NidooTheme.type.label, color = colors.contentMuted)` with `Modifier.semantics { heading() }`, top padding 8 dp.
- **`SensorStrip`:** `FlowRow` with 6 dp gaps; a pill is a 1 dp `colors.chipBorder` capsule, min height 48 dp touch area around a 32 dp visual, containing `KindIcon(iconSpec(card))` at 16 dp, the label and the value (`cardValueText(card.value, card.kind)` or `value_unavailable`). The label is the card's name, omitted when the card is the only one of its `deviceClass` among temperature and humidity sensors in the strip. `combinedClickable`: click → `onClick(card)` only when `card.tap != TapAction.NONE` (history), long-click → `onLongClick(card)`. Alpha 0.6 when `stale` or unavailable. Test tag `strip-<id>`; content description `strip_pill_description`.
- **Back:** `BackHandler(enabled = state.selected is RoomFilter.Area && !onDashboard) { onSelect(RoomFilter.Rooms) }`, declared before the search back handler of Task 8 so search wins.

- [ ] **Step 1: Write `SensorStripTest`:** `a lone temperature sensor shows its value without its name`; `two temperature sensors keep their names`; `a sensor with history opens it on tap` (`tap = OPEN_HISTORY` → `onClick` called); `a sensor without history ignores the tap`; `long-press customizes`; `an unavailable sensor reads Unavailable`.
- [ ] **Step 2: Add to `HomeScreenTest`:**
  - `a room shows its name as the title, not the greeting, and no weather hero`.
  - `a room lists section labels in order and hides the label of a single section`.
  - `a room's sensors are pills, not tiles`: `strip-<id>` exists, `card-<id>` does not.
  - `the room pills start with back to Rooms and select sideways`.
  - `system back from a room goes to Rooms` (`Espresso.pressBack()` or the activity's `onBackPressedDispatcher`, as the existing search-back test does).
  - `All devices shows sections with Sensors last and no strip`.
  - `Favourites stays a flat grid without labels`.
  - `every card of the room is on screen exactly once` (anti-goal 4): for a fixture with one device of every `Kind` in one room, each id matches exactly one of `card-<id>` / `strip-<id>`.
- [ ] **Step 3: Run; expect failures.** `--tests '*SensorStripTest*' --tests '*HomeScreenTest*'`
- [ ] **Step 4: Implement.**
- [ ] **Step 5: Run the module; expect PASS.** `./gradlew :feature:home:testDebugUnitTest :core:designsystem:testDebugUnitTest`
- [ ] **Step 6: Commit** `feat(home): room view by type with a sensor strip`

---

### Task 8: Always-visible search

**Files:**
- Create: `ui/HomeSearchField.kt`
- Modify: `ui/HomeScreen.kt`, both `strings.xml` (remove `search_open` only if nothing else uses it)
- Test: `ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeUiState.query`, `onSearch: (String) -> Unit`.
- Produces: `@Composable internal fun HomeSearchField(query: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier)`

Decisions (spec §6.5):
- The `searching` flag and the header's magnifier button are removed; `val searching = state.query.isNotBlank()` replaces every use.
- The field is a grid item directly under the header, shown when not on a dashboard and `state.selected` is `Rooms`, `All` or an `Area`, or whenever `searching` (so results from Favorites cannot occur: the field is not offered there).
- `HomeSearchField` keeps the existing field's rule that it owns its text (`rememberSaveable`), does **not** request focus on composition, shows a clear (✕) trailing button with `search_close` when the text is not empty, hint `search_hint`. When `query` becomes blank from outside (a view switch cleared it), the local text resets: `LaunchedEffect(query) { if (query.isEmpty()) text = "" }`.
- While `searching`: pills, Climat banner, strip, room cards and sections are not emitted; results are `homeCards(state.cards)`; the "no match" item stays.
- Back precedence, highest first: `BackHandler(enabled = searching) { onSearch(""); focusManager.clearFocus() }`; `BackHandler(enabled = fieldFocused && !searching) { focusManager.clearFocus() }`; then Task 7's room handler. In Compose the last enabled `BackHandler` composed wins, so declare the room handler first and the search handler last.

- [ ] **Step 1: Update and add in `HomeScreenTest`:**
  - Rewrite the existing tests that open search through the magnifier to type into the field.
  - `the search field is visible on Rooms, in a room and on All devices, without focus`.
  - `the search field is not offered on Favourites, Hidden or a dashboard`.
  - `typing replaces the pills and sections with flat results`.
  - `back clears the query and shows the room again, a second back goes to Rooms` (Review Focus 5): drive a small stateful wrapper around `HomeScreen` that applies `onSearch`/`onSelect` to a `HomeUiState` so the two backs can be observed.
  - `switching view empties the field`: state goes from `query = "la"` to `query = ""` → the field shows the hint.
- [ ] **Step 2: Run; expect failures.** `--tests '*HomeScreenTest*'`
- [ ] **Step 3: Implement;** delete `SearchField` and the magnifier from `HomeScreen.kt`.
- [ ] **Step 4: Run the module; expect PASS.**
- [ ] **Step 5: Commit** `feat(home): always-visible search on Home`

---

### Task 9: Screenshots and full check

**Files:**
- Create: `ui/RoomsScreenshotTest.kt` (test source set), a fixture `ui/RoomsFixtures.kt`

**Interfaces:**
- Consumes: `HomeScreen`, `HomeUiBuilder.build`, `renderPng`, `distinctColors`, `luminanceAt` (`ScreenshotSupport.kt`), the pattern of `DashboardScreenshotTest`.

- [ ] **Step 1: Write `RoomsFixtures.house(selected: RoomFilter): HomeUiState`** built with `HomeUiBuilder.build` from seven rooms (Living room, Kitchen, Bedroom, Bathroom, Garage, Garden, Laundry) holding lights, a hub light group, covers, a climate, a media player, plugs, temperature and humidity sensors, with states (two lights on, one cover open, one closing).
- [ ] **Step 2: Write `RoomsScreenshotTest`** with six renders, each asserting `distinctColors() > 8` and the theme luminance like `DashboardScreenshotTest`: `rooms-night`, `rooms-warm`, `room-night`, `room-warm` (`@Config(qualifiers = "w411dp-h2400dp-xxhdpi")`), and `rooms-wide-night`, `room-wide-night` (`w900dp-h1400dp-xhdpi`).
- [ ] **Step 3: Run:** `./gradlew :feature:home:testDebugUnitTest --tests '*RoomsScreenshotTest*'`; expect PASS. Open the six PNGs in `feature/home/build/screenshots/` and check against the mockup: no clipped status line, buttons aligned, section labels readable in both themes, room cards two-up on the wide render. Fix what is off (paddings, spans) and re-run.
- [ ] **Step 4: Full check:** `./gradlew check`; expect BUILD SUCCESSFUL. Fix anything it reports (lint on new strings, unused `search_open`, detekt/ktlint if configured).
- [ ] **Step 5: Commit** `test(home): Rooms and room view screenshots`

---

## Spec coverage

| Spec | Task |
|---|---|
| §6.1 pills, fallback, All devices | 4, 6 (labels), 7 (sections) |
| §6.2 room cards, icon, status, state shape | 1, 4, 6 |
| §6.3 whole-room buttons, stale, failures, Retry | 2, 5, 6 |
| §6.4 header, pills, back, Climat, strip, sections | 3, 7 |
| §6.5 search | 5 (`select` clears), 8 |
| §6.6 dashboards unchanged | 7, 8 (conditions keep `onDashboard`); existing `DashboardScreenTest` stays green |
| §6.7 accessibility, strings, themes | 6, 7, 9 |
| §7 testing | every task; 9 |
