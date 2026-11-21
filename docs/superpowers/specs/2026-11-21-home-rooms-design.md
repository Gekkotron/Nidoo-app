# Home: rooms first, and a room laid out by type: design

Date: 2026-11-21 · Author: Gekkotron

Android app only (`feature/home`, a little of `core/designsystem`). No hub change, no iOS change.

Mockup: `docs/design/mockups/home-rooms-layout.html` (a fragment; the user picked **B + C**). It is a rough layout with emoji standing in for the real icons, not a pixel reference.

## 1. Intent

Home is hard to scan. The user's words: "the room display, finding the right devices".

How Home works today (`feature/home/.../ui/HomeScreen.kt`, `presentation/HomeUiBuilder.kt`):

- Rooms are one horizontally scrolling row of pills (`RoomChips`): Favorites, All, one per room, Hidden. With many rooms the wanted one is off-screen.
- "All" is one flat grid ordered by room then name, with no visible boundary between rooms.
- Inside a room, hub groups come first and everything else is alphabetical: a temperature sensor sits between a lamp and a shutter. Nothing separates what you control from what you only read.
- Search exists but sits behind the magnifier icon in the header and replaces the pills while open.

What the user wants: open Home, see the rooms, act on a whole room in one tap, and inside a room find a device by what it is.

## 2. Goals

1. A **Rooms view** on Home: one card per room with its icon, name, a one-line status and one-tap whole-room buttons for lights and shutters.
2. A **room view laid out by type**: read-only sensors as a compact strip, controls grouped under type labels.
3. **Search always visible** on Home, not behind an icon.
4. Every device stays reachable with what exists today: tap, long-press, sheet, Customize, history.

## 3. Non-goals

- Room headings inside "All devices" (mockup option A; not picked).
- Any change to how a tile looks, to the control sheets, to Customize, to dashboards' content, or to either theme's colours and type.
- Creating, renaming, reordering rooms or choosing a room's icon. Rooms come from `Area` as today.
- iOS. Its Home screen does not exist yet; nothing here is written in `shared` for it.
- Hub changes. Everything is built from the entities, states, areas and hub groups the app already receives.
- Widgets and voice.

## 4. Anti-goals

- **A device must never become harder to reach than today from inside its room.** One tap to toggle, long-press for the sheet or Customize, exactly as now.
- **A whole-room button must never act on a device the user cannot see on Home.** Hidden devices and devices that live only on Security or Energy are not touched.
- **A whole-room button must never flip-flop on Retry.** Commands are absolute (on / off / open / close), like every other Home command.
- **No device may disappear.** Anything Home shows today must appear in exactly one place in its room view (strip or a section).

## 5. Approaches

1. **Rooms as one more selectable view inside the existing Home screen (picked).** Add a `RoomFilter.Rooms`; the grid shows room cards for it. A room is still `RoomFilter.Area`, as today. Sheets, Customize, the Climat banner, snackbars and Retry, search, dashboards and saved selection keep working unchanged, because they hang off the same screen and state. Cost: `HomeScreen.kt` and `HomeUiBuilder.kt` grow; split them where they do.
2. **Separate navigation destinations** for the room list and for a room. Cleaner back stack and room for transitions, but every sheet, event effect, the Climat plumbing and search would be wired a second time. Rejected: much duplication for the same result.
3. **Rooms as a hub dashboard tile type.** Needs hub work and only serves users who set dashboards up. Rejected: the request is a better Home, not a new dashboard.

The user left the how to the builder; approach 1 is the author's call.

## 6. Design

### 6.1 Top-level views and pills

Home's top-level pills become, in this order: **Favorites** (only when something is a favourite, as today) · **Rooms** · **All devices** · **Hidden (n)** (only when something is hidden, as today).

- Room pills are no longer in this top-level row.
- Launch default stays **Favorites** (user's earlier call, and the cold-start rule in `HomeViewModel` that keeps the Favorites key while favourite flags arrive stays). When there are no favourites the fallback is **Rooms**, not All.
- The "All" pill is relabelled "All devices". Its content is the grid as today, cut into the sections of §6.4 with their labels, plus a last **Sensors** section holding the sensors as today's tiles. There is no strip here (a strip merged across rooms would lose which room a value belongs to) and no room headings. Inside a section the order is today's global order (pinned position, room, name), and tiles keep their small room name. Author's call.
- Favorites and Hidden keep today's flat grid, unchanged.
- The saved selection (`home.filter` in the saved state) keeps working; `RoomFilter.Rooms` gets its own key.

### 6.2 Rooms view

One card per room that has at least one device on Home (the same rule that gives a room a pill today), in `areas` order. "Unassigned" comes last when it has devices.

A room card is full width on a phone. On wider windows it follows the grid: one card per two columns (so 1 across under 600 dp, then as many as fit).

Card content, left to right:

- **Icon**: `Area.icon` exists in the model but nothing in the app sets or draws it today, so it cannot be relied on. The icon is chosen in this order: (1) `Area.icon` when set and it matches a known key; (2) a guess from the room's name, through a small keyword table covering English and French (living room/salon, kitchen/cuisine, bedroom/chambre, bathroom/salle de bain, office/bureau, garage, garden/jardin, entrance/entrée, laundry/buanderie, WC/toilettes); (3) one generic room glyph. The known keys for (1) are the same names as the table's rooms.- **Name**, in the card title style.
- **Status line**, muted, joined with " · ", in this order, each part only when the room has it:
  1. temperature: the room's first available temperature sensor on Home (by the same order as tiles), formatted like its tile;
  2. humidity: same rule;
  3. lights: "n lights on" / "1 light on"; when the room has lights and none is on, "Lights off";
  4. shutters: "Shutters open" / "Shutters closed" / "n of m open" when mixed; "Opening…" / "Closing…" while any moves.
  A room with none of these shows the device count ("4 devices"). The line is one line, ellipsised.
- **Whole-room buttons**, round, 48 dp touch target each: a **lights** button when the room has at least one light, a **shutters** button when it has at least one shutter. No other kinds.

The card uses the "on" card style (`NidooCard(on = true)`) when any light in the room is on.

Tapping the card (outside the buttons) opens the room (§6.4). No long-press action.

Unassigned has a card but no whole-room buttons (author's call: it is not a place, so "all lights" there is arbitrary).

**State shape.** `HomeUiState` gains a list of room cards, built on every `build()` whatever the selected view, because `cards` only holds the selected view's cards. Each room card carries what the buttons need: the ids of its light and shutter targets with their on / cover state, availability and stale flags. `chips` splits into two lists: the top-level pills, and the rooms for the pill row of §6.4. The builder runs on every state message, so the room pass is one loop over the entities already in hand, with no card built per device.

### 6.3 Whole-room buttons

**Which devices.** The room's devices that Home shows (`Placement.onHome`, not hidden), by effective kind:

- lights: `Kind.LIGHT`, including a switch shown as a light;
- shutters: `Kind.COVER`.

Targets are computed from the entities, not from the folded cards: standalone entities (`partOf == null`, so no companion) whose own area is this room. Commands go to the individual devices, never to both a hub group and its members. A hub group entity in the room is not itself a target; its members are, when their own area is this room. A member that sits in another room is not a target here, even if this room's group lists it; so the room card's lit state can differ from a cross-room group card shown inside the room, which is accepted. Members a hub group hides from the grid (`hideMembers`) still count: they are on Home, folded into the group card.

**Lights button** (user's call: one tap):

- any target light on → `TurnOff` to every target that is on;
- none on → turn on every target, each with its own default on-state (the same command a tile tap sends, `turnOnFor`).

The button looks lit when any light is on.

**Shutters button** (user's call: one tap moves every shutter in the room, no confirmation, no sheet):

- any shutter moving (`OPENING` / `CLOSING`) → `Stop` to the moving ones (author's call, so a mistaken tap can be undone with a second tap);
- otherwise any shutter open → close all that are not closed;
- otherwise → open all.

A shutter counts as **closed** when its position is known and is 0, or when it has no position and its state is `CLOSED`. Anything else (`OPEN`, `STOPPED` part-way, `UNKNOWN`) counts as open, so the first tap closes. The status line's "n of m open" uses the same rule.

The button's icon and content description say what the tap will do ("Close living room shutters").

**Rules shared with existing sends.**

- Unavailable targets are skipped. If any target that would receive the command has a stale value (from the cache, not yet confirmed), nothing is sent and the existing "waiting for connection" snackbar shows, as for a group today.
- Sends run in parallel. Failures are merged into one snackbar with Retry, the way `sendGroup` merges member failures today (the 1 s window and `GroupNotConfirmed`). A room has no group card, so the send is keyed by a virtual id per room and kind (`room:<areaId>:light`, `room:<areaId>:cover`), and the failure event carries the room name, the kind and the failed ids itself, so Retry needs no card lookup. Retry re-sends the same absolute command to the failed devices only.
- Haptic feedback on tap, like tiles.
- A light with a `securityAsk`-style confirmation does not exist; switches that are door openers or sirens are not lights and are never targets.

### 6.4 Room view

Selecting a room card shows that room (`RoomFilter.Area`), in the same grid.

**Header.** The room's name replaces the greeting as the title; under it one muted line with the same status text as the room card. The link pill, voice and settings buttons stay as today. The hero weather card is part of the header today and shows on every House view; it stays on Favorites, Rooms and All devices and is not shown inside a room, where the room's own status line takes its place (author's call).

**Search field**, always visible (§6.5).

**Room pills.** Inside a room this row replaces the top-level pills: first **‹ Rooms** (goes back to the Rooms view), then every room, the current one selected and scrolled into view. Tapping another room switches sideways.

**Back.** System back, in this order of precedence: a non-blank search query is cleared (and the field loses focus); otherwise a focused empty search field loses focus; otherwise a room goes to the Rooms view. From Rooms, back behaves as today from Home.

**Saved selection.** A saved room that no longer has devices falls back as today (Favorites, else Rooms).

**Climat banner.** Unchanged: shown for the room when a zone covers it, placed directly under the pills, above the strip.

**Sensor strip.** Every `Kind.SENSOR` and `Kind.BINARY_SENSOR` the room shows on Home becomes a pill in a wrapping row at the top: a small kind icon, a short label and the value ("21.5 °C", "48 %", "Balcony closed"). Temperature and humidity pills drop the label when the room has only one of that class.

- Tap: opens history when the sensor has history (`hasHistory`), otherwise nothing.
- Long-press: Customize, as on the tile today.
- Unavailable and stale look as on tiles (dimmed; "Unavailable").
- Wide power/energy sensor tiles become pills too.
- Note: door, window, motion and the other security-class binary sensors live on the Security tab and are not on Home unless the user pinned them there (`Placement.onHome`). The strip follows that rule, so they appear only when pinned. Author's call; it keeps Home and Security from showing the same sensor twice.

**Sections.** Below the strip, the remaining cards under small uppercase muted labels, in this order; a section with no card is omitted, and when only one section would remain its label is omitted too:

1. **Lights**: lights, switches shown as lights, hub light groups.
2. **Shutters & climate**: covers, hub cover groups, climate, water heater.
3. **Plugs & media**: switches, media players, vacuum, buttons.
4. **Other**: everything else Home shows that is not a sensor (number, select, alarm panel if pinned…).

Inside a section the order is today's: hub groups first, then pinned position (`ui.order`), then name. `ui.order` no longer moves a card across sections.

Tiles, their spans (wide group cards, wide media cards), taps, long-presses and sheets are unchanged.

### 6.5 Search

- The magnifier in the header is removed. A search field sits under the header in the Rooms view, in a room and in All devices. It is not shown on Favorites or Hidden (author's call: those lists are short), nor on a dashboard.
- Typing searches the whole house exactly as today (`SearchMatch`, hidden devices included, flat ungrouped results, "no match" text). While a query is present the pills, strip, sections and Climat banner are replaced by results; clearing the field or system back returns to where the user was.
- The field does not take focus on its own; the keyboard opens only when it is tapped.
- The query is cleared when the user switches view (a top-level pill, a room pill, a room card). It survives rotation, as today (`home.query` in the saved state). The separate `searching` flag in `HomeScreen` goes away: a non-blank query is what "searching" means.

### 6.6 Dashboards

Unchanged. When a dashboard is selected and its header is shown, the top-level pills show as today and choosing one goes back to House with that view, as room pills do now.

### 6.7 Accessibility and text

- Room card: one content description with name and status; each whole-room button is its own focusable button with an action-phrased description and a state description.
- Section labels are headings for TalkBack.
- Strip pills are read as "name, value, room".
- All new strings in `values` and `values-fr`. Plurals use plural resources.
- Both themes (Night Glass, Ivoire) use existing tokens only; no new colour.
- Touch targets ≥ 48 dp (`NidooDimens.minTouch`).

## 7. Testing

- Unit tests for the builder: which rooms get a card and in what order; each part of the status line; section assignment for every `Kind`, switch-as-light and hub groups; sensors go to the strip and nowhere else; no card lost compared with today's room list (anti-goal 4).
- Unit tests for the whole-room commands: lights any-on → off, none-on → each light's preset; shutters moving → stop, open → close, closed → open; hidden / off-Home / unavailable devices excluded; stale → nothing sent; hub group and members never both targeted; merged failure and Retry.
- View-model tests: fallback to Rooms without favourites; saved selection round-trip; back from a room.
- Compose UI and screenshot tests following the module's existing ones (`DashboardScreenTest`, `DashboardScreenshotTest`): Rooms view and a room view, both themes, phone and one wide width.
- Existing Home tests keep passing; tests that assert room pills in the top-level row are updated to the new structure.

## 8. Left to the builder

- Exact file split, names of the new models, and whether the room-view builder lives in `HomeUiBuilder` or beside it.
- The glyphs for the lights and shutters buttons and the generic room icon, drawn with the existing `IconCanvas` icons.
- Paddings and type styles, within `NidooDimens` and `NidooTheme.type`.
- Whether the strip is a `FlowRow` in one grid item or laid out otherwise.
- The transition between Rooms and a room (a plain content change is acceptable; `NidooMotion` if animated).

## 9. Calls to check

User's calls: option B + C; one tap toggles all lights; one tap opens/closes all shutters in the room.

The user approved this document on 2026-11-21 with the author's calls below as written.

Author's calls:

- Favorites stays first and the launch default; Rooms is the fallback.
- **Room icons are guessed from the room's name** (keyword table, generic glyph otherwise), because the app has no room icon data today. Offered against a per-room picker and an initial in a circle; the user answered "yes" to the document as written.
- "All devices" gets by-type sections with sensors as a last section of tiles, no strip, no room headings.
- The weather hero is not shown inside a room.
- A part-open or unknown shutter counts as open, so the first tap closes.
- Shutters button stops when something is moving.
- Unassigned has a card but no whole-room buttons.
- Door/motion sensors appear in a room only when pinned to Home.
- Search field on Rooms, a room and All devices only; header magnifier removed.
- Section names and what goes in each (§6.4).
