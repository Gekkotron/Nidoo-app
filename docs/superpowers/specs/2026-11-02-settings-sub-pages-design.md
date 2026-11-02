# Settings as a short list of sub-pages — design

Date: 2026-11-02 · Author: Gekkotron · Status: approved direction (mockup A), awaiting doc review

## Why

The app's Settings screen is one long scrolling page: theme radios, the energy-view picker (PR #23), connection
details, route picker, "Set up again", voice toggle, the full voice-shortcuts list, the whole presence form, and About.
It is too long. The Nidoo hub (`Oikos-os`, `crates/nidoo-web/src/screens/settings.rs`) already solved this: Settings is
a short list of rows, each with a tinted icon tile, a title, a one-line hint and a status, and each row opens its own
page. The app should look and work the same way so the two feel like one product.

## Goals

- Settings becomes a short grouped list (hub style, mockup A) that fits on one phone screen.
- Every row has a tinted icon tile, a title, a one-line hint, a short status on the right and a chevron.
- Every row opens a sub-page holding the content that is inline today, moved as-is.
- A header at the top like the hub's: Nidoo mark, "Settings", "Nidoo." + version, connection dot + word + host.

## Non-goals

- No behaviour change inside any section. Dialogs, permission flows, validation, the push sheet's actions, the presence
  form, the voice toggle and shortcut deletion all behave exactly as today.
- No new settings.
- No change to the existing Rooms screen (`RoomOrderRoute`) or the Licences screen.

## Anti-goals (a failure even if it works)

- The list no longer fits on a phone screen, or the header pushes it below the fold.
- A setting becomes harder to reach than today (more than one tap from the list to the control).
- Any section's state or flow regresses during the move (e.g. mic-permission snackbar, presence permission prompts,
  history/Frigate dialogs, "Set up again" confirmation).

## Base

Built on `master` (`029109a`), which already contains PR #23 (energy views, merged `4ef9438`), so the Appearance page
carries the energy-view picker from the start. `stash@{0}` (energy-views Settings WIP) is an older copy of what was
merged; it is not used.

## The list (mockup A)

Top of the list (my call): a compact top bar with only the back arrow (same back semantics and `settings_back`
label as `SettingsTopBar`, no title), then the hero below it. "Settings" appears once, in the hero.

Header (hub's `settings-hero`):
- Nidoo mark on the left.
- "Settings" as the title.
- "Nidoo." wordmark + version (`appVersion`).
- Connection dot + word (Online for `Connected`, Connecting for `Connecting`/`Reconnecting`, Offline for `Offline`,
  from `EntitySource.link`) + "·" + `ConnectionSummary.Direct.broker` shown as-is (`host:port`, like the hub's host
  text). Demo shows the existing `settings_demo` string ("Demo house") instead of the dot, word and host.
- No "Run setup again" chip in the header. In the app, "Set up connection again" stays inside Connection, behind its
  confirmation (the hub's chip is a hub-only setup wizard).

Two groups, each a single card with rows separated by hairlines (hub's `settings__list`):

| Group | Row | Glyph · tone | Hint | Status |
|---|---|---|---|---|
| House | Rooms | HOUSE · amber | Order of the room chips, room assignment (existing string) | number of rooms |
| House | Notifications | BELL · rose | Push alerts and channels | short push word, no age: Registered / Waiting / Failed / Off / nothing in demo |
| House | Presence | PIN · cyan | Arm the alarm when you leave | On / Off (the saved flag only, no permission check) |
| House | Voice | MIC · violet | Voice commands and shortcuts | On · N (shortcut count), or Off |
| App | Appearance | SUN_MOON · violet | Theme, energy view | theme name |
| App | Connection | BROKER · mint | Broker, route, history, Frigate | Online / Connecting / Offline (or Demo) |
| App | About | INFO · muted | Version and licences | version |

Order inside each group as above. Group labels "House" / "App" (FR: "Maison" / "Application").
All new strings ship in EN and FR.

Row anatomy, matching the hub CSS: min height 64dp (never below `NidooDimens.minTouch`), 14dp gap, 38dp icon tile with
12dp corners tinted at 16% of the tone, 20dp glyph in the tone colour, title in `type.body`, hint in `type.label` muted
and single-line ellipsised, status in `type.label` muted, "›" chevron muted. The whole row is one click target with
`Role.Button`; the status is part of the row's spoken label.

Tones map to existing `NidooColors` tokens per theme (amber → solar/warm accent, rose → offline/rose, cyan → water,
violet → grid, mint → connected, muted → contentMuted). Exact token per tone is the builder's call, provided both Night
Glass and Ivoire read well.

## Sub-pages

Each sub-page uses `SettingsTopBar(title, onBack)` and holds today's content for that section, moved as-is:

- **Appearance**: theme radios + `EnergyViewSection` (from PR #23).
- **Notifications**: what `PushSheet` shows today, as a page instead of a sheet (status, "Register again",
  "Notification channels"). The sheet is retired.
- **Presence**: `PresenceSettingsSlot` (its own view-model, unchanged), with its snackbar messages shown on this page.
- **Voice**: `VoiceSection` + `VoiceShortcutsSection` on one page, including the mic-permission request and the
  "permission denied" snackbar.
- **Connection**: `ConnectionRows` + `RoutePicker` + "Set up connection again" with its confirmation dialog, plus the
  History and Frigate dialogs.
- **About**: version, notice, "Open-source licences" row (still opens `LicencesKey`).
- **Rooms**: the row opens the existing `RoomOrderKey` directly; there is no new Rooms page.

## Navigation (builder may adjust)

My call: one parametrised Nav3 key in `NidooNavHost`, e.g. `SettingsPageKey(page)` with a `SettingsPage` enum
(`APPEARANCE, NOTIFICATIONS, PRESENCE, VOICE, CONNECTION, ABOUT`) owned by `feature/settings`, mirroring the hub's
`SettingsPage`. System back and the top-bar back pop to the list. Each entry may get its own `SettingsViewModel`
instance via `hiltViewModel()`; all its state comes from shared repositories, so that is fine.

Callbacks stay where they are today: `NidooNavHost` passes `route`, `onRouteChange` and `onSetUpAgain` to the Connection
page entry exactly as it passes them to `SettingsRoute` now, `onOpenLicences` to the About page, `onOpenRooms`
(→ `RoomOrderKey`) to the list. "Set up again" keeps today's behaviour (`viewModel.setUpAgain` resets the profile and
the back stack); no extra pop is added.

The push `refreshPush()` on resume, which lives in `SettingsRoute` today, runs on both the list (for the short status
word) and the Notifications page. The 60 s "N min ago" ticker only runs on the Notifications page, because the list's
short word carries no age.

## Glyphs

Add BELL, PIN, SUN_MOON, BROKER and INFO to `NidooGlyph`, ported from the hub's `icons.rs` SVG paths (same 24-unit grid)
and split into line/area parts in the house duotone style. HOUSE and MIC already exist; the builder may keep the app's
existing versions.

## New data

All added to `SettingsUi` / `SettingsViewModel` (my call: no separate list model), covered in `SettingsViewModelTest`:

- `roomCount`: `EntitySource.areas` size, not counting the area whose id is `UNASSIGNED_AREA` (the source already
  lists declared, then implicit, then Unassigned if used).
- `link`: `EntitySource.link`, for the header and the Connection status.
- `presenceEnabled`: the presence-enabled flag from `AppPreferences` (the same preference `PresenceSettingsViewModel`
  reads).

## Tests

- Update `SettingsScreenTest` / `SettingsRouteTest` for the list (rows, statuses, click → callback) and move the
  per-section assertions to the sub-page tests.
- Keep `VoiceSectionScreenshotTest` working on the Voice page.
- Add a screenshot of the list in Night Glass and Ivoire via the local screenshot suite.

## Left to the builder

- Exact composable/file split for the sub-pages.
- The Nidoo mark asset in the header. Prefer the launcher foreground moved or copied into `core/designsystem`.
- Exact tone→token mapping, provided both themes pass contrast.
- Whether the list and header scroll together (they should, if the screen is short).
