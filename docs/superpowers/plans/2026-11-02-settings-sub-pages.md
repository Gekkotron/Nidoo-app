# Settings sub-pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the long Settings page with a hub-style grouped list (header + icon-tile rows with status), each row
opening a sub-page that holds today's section content unchanged.

**Architecture:** `feature/settings` gains a `SettingsPage` enum, a list screen (`SettingsListScreen`) and one page
screen (`SettingsPageScreen`) that dispatches to per-section page bodies moved out of today's `SettingsScreen`.
`SettingsViewModel` gains the three values the list needs (link, room count, presence flag). The app's `NidooNavHost`
adds one parametrised key, `SettingsPageKey(page)`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation 3, Hilt, Robolectric + Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-11-02-settings-sub-pages-design.md`

## Global Constraints

- Branch `feature/settings-sub-pages`, based on `master` `029109a`. Commit identity on every commit:
  `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …`, message ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No behaviour change inside any section: dialogs, permission flows, validation, push actions, presence form, voice
  toggle and shortcut deletion behave exactly as today.
- Every new user-facing string ships in `values/strings.xml` and `values-fr/strings.xml`.
- Row min height 64dp (never below `NidooDimens.minTouch`), 14dp gap, 38dp tile with 12dp corners tinted at 16% of the
  tone, 20dp glyph in the tone colour; title `type.body`, hint `type.label` muted single-line ellipsised, status
  `type.label` muted, "›" chevron muted. Whole row = one click target, `Role.Button`, status included in its label.
- Groups and order: House = Rooms, Notifications, Presence, Voice; App = Appearance, Connection, About.
- Do not add per-repo cache/build overrides (shared `$DEV_SCRATCH` setup).
- Module test command: `./gradlew :feature:settings:testDebugUnitTest` (designsystem: `:core:designsystem:testDebugUnitTest`,
  app: `:app:testDebugUnitTest`).

## Review Focus

- **Demo house** (`ConnectionSummary.Demo`, `PushStatus.Demo`): header shows "Demo house" with no dot/host, Notifications
  status is empty, Connection status reads "Demo house", Register again stays disabled on the page. → Task 3 + Task 4 tests.
- **No profile yet** (`connection == null`): list renders, header shows no host, Connection status empty, no crash. → Task 3 test.
- **Mic permission denied from the Voice page**: the "Microphone permission is needed…" snackbar shows on the Voice page
  (the snackbar host must live on the page scaffold, not the list). → Task 4 test.
- **Rooms with only Unassigned / no areas**: status is empty, not "0" and not "1". → Task 2 test (count) + Task 3 test (empty).
- **Process death / back on a sub-page**: the key is `@Serializable`, so the back stack restores onto the same page and
  back returns to the list. → Task 5 test (push/pop).

---

### Task 1: Settings glyphs

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/gekkotron/nidoo/designsystem/icons/StaticIcon.kt` (enum `NidooGlyph`)
- Test: `core/designsystem/src/test/kotlin/com/gekkotron/nidoo/designsystem/icons/NidooGlyphTest.kt`

**Interfaces:**
- Produces: `NidooGlyph.BELL`, `NidooGlyph.PIN`, `NidooGlyph.SUN_MOON`, `NidooGlyph.BROKER`, `NidooGlyph.INFO`.

- [ ] **Step 1: Extend the test list** in `glyphs parse to non empty paths` with the five new glyphs.
- [ ] **Step 2: Run** `./gradlew :core:designsystem:testDebugUnitTest --tests '*NidooGlyphTest*'`. Expected: compile FAIL (unresolved).
- [ ] **Step 3: Add the five entries**, ported from the hub's `crates/nidoo-web/src/icons.rs` (24-unit grid):
  - BELL: `area("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9z")`, `line("M10 21a2 2 0 0 0 4 0")`
  - PIN: `area("M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z")`, `line("M14.5 9.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z")`
  - SUN_MOON: `line("M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0z")`, `area("M12 3.5a8.5 8.5 0 0 0 0 17z")`
  - BROKER: `line("M4 8h14M14 4l4 4-4 4M20 16H6M10 12l-4 4 4 4")`
  - INFO: `area("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z")`, `line("M12 11v5.5M12 7.5h.01")`
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(designsystem): bell, pin, sun-moon, broker and info glyphs`.

### Task 2: List data in `SettingsViewModel`

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/presentation/SettingsViewModel.kt`
- Test: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/presentation/SettingsViewModelTest.kt`

**Interfaces:**
- Produces on `SettingsUi` (all defaulted, so existing call sites compile):
  `val link: LinkState = LinkState.Connecting`, `val roomCount: Int = 0`, `val presenceEnabled: Boolean = false`.

- [ ] **Step 1: Write the failing test** `state carries the link, the room count without Unassigned, and the presence flag`:
  set `fake.link.value = LinkState.Offline(<any reason constant>)`, `fake.areas.value = listOf(Area("kitchen","Kitchen"), Area("salon","Salon"), Area(UNASSIGNED_AREA, "Unassigned"))`,
  `prefs.setPresence(PresenceConfig(enabled = true))`; assert `state.value.link is LinkState.Offline`,
  `state.value.roomCount == 2`, `state.value.presenceEnabled`. Second case in the same test: `areas = listOf(Area(UNASSIGNED_AREA, …))` → `roomCount == 0`.
- [ ] **Step 2: Run** `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsViewModelTest*'`. Expected: FAIL (unresolved fields).
- [ ] **Step 3: Implement.** `roomCount = areas.count { it.id != UNASSIGNED_AREA }`; presence from
  `preferences.presence.map { it?.enabled == true }`. The current `combine` already has five flows: combine the three
  new flows into one small `ListExtras(link, roomCount, presenceEnabled)` flow first and add it as a sixth via a nested
  `combine`. Seed the `stateIn` initial value from `source.link.value`, `source.areas.value`, `preferences.presence.value`.
- [ ] **Step 4: Run** the same command. Expected: PASS (all existing VM tests too).
- [ ] **Step 5: Commit** `feat(settings): link, room count and presence flag in the settings state`.

### Task 3: The Settings list screen

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/presentation/SettingsPage.kt`
- Create: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsList.kt`
- Create: `feature/settings/src/main/res/drawable/settings_mark.xml` (copy of `app/src/main/res/drawable/ic_launcher_foreground.xml`)
- Modify: `feature/settings/src/main/res/values/strings.xml`, `values-fr/strings.xml`
- Test: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsListScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 glyphs; Task 2 `SettingsUi.link/roomCount/presenceEnabled`.
- Produces:
  - `enum class SettingsPage { APPEARANCE, NOTIFICATIONS, PRESENCE, VOICE, CONNECTION, ABOUT }` (public; the app's nav key stores `name`).
  - `@Composable fun SettingsListScreen(state: SettingsUi, version: String, onOpenPage: (SettingsPage) -> Unit, onOpenRooms: () -> Unit, onBack: () -> Unit)`
  - `@StringRes internal fun pushWord(status: PushStatus): Int?` (null for Demo) and `@StringRes internal fun linkWord(link: LinkState): Int`
    (`Connected` → online, `Connecting`/`Reconnecting` → connecting, `Offline` → offline).

Strings to add (EN / FR):

| key | EN | FR |
|---|---|---|
| settings_group_house | House | Maison |
| settings_group_app | App | Application |
| settings_appearance | Appearance | Apparence |
| settings_appearance_hint | Theme, energy view | Thème, vue énergie |
| settings_notifications | Notifications | Notifications |
| settings_notifications_hint | Push alerts and channels | Alertes push et canaux |
| settings_presence_hint | Arm the alarm when you leave | Armer l\'alarme quand vous partez |
| settings_voice_hint | Voice commands and shortcuts | Commandes vocales et raccourcis |
| settings_connection_hint | Broker, route, history, Frigate | Broker, accès, historique, Frigate |
| settings_about_hint | Version and licences | Version et licences |
| settings_on / settings_off | On / Off | Activé / Désactivé |
| settings_voice_status | On · %1$d | Activé · %1$d |
| settings_push_short_registered / _waiting / _failed / _off | Registered / Waiting / Failed / Off | Enregistré / En attente / Échec / Désactivées |
| settings_link_online / _connecting / _offline | Online / Connecting / Offline | En ligne / Connexion… / Hors ligne |
| settings_wordmark (translatable="false") | Nidoo. | — |

Row table (title, hint, glyph, tone, status):

| Row | Title string | Glyph | Tone → `NidooTheme.colors` | Status |
|---|---|---|---|---|
| Rooms | settings_rooms | HOUSE | solar | `roomCount` as text, empty when 0 |
| Notifications | settings_notifications | BELL | offline | `pushWord(state.push)` or empty |
| Presence | presence_section_title | PIN | water | settings_on / settings_off |
| Voice | voice_section_title | MIC | grid | settings_voice_status(shortcuts.size) when enabled, else settings_off |
| Appearance | settings_appearance | SUN_MOON | grid | theme name (existing theme strings) |
| Connection | settings_connection | BROKER | connected | Demo → settings_demo; null → empty; else `linkWord(state.link)` |
| About | settings_about | INFO | contentMuted | `version` |

Header: a top bar with only the back arrow (reuse the arrow + `settings_back` semantics from `SettingsTopBar`; add a
`title: String?` parameter to `SettingsTopBar` so `null` shows no title), then a row: `settings_mark` (56dp) | column of
"Settings" (`type.display`), "Nidoo." + version, and either `[dot] linkWord · broker` (dot colour: connected / warning /
offline) or, for Demo, `settings_demo`; nothing when `connection == null`. Header and groups scroll together.

- [ ] **Step 1: Write the failing tests** in `SettingsListScreenTest` (Robolectric, `@Config(qualifiers = "w411dp-h914dp")`, NIGHT_GLASS):
  - `the list shows both groups and the seven rows in order` — texts "House", "App", and the seven titles exist.
  - `each row reports its page, Rooms opens rooms` — click each title; assert `onOpenPage` received
    `[APPEARANCE, NOTIFICATIONS, PRESENCE, VOICE, CONNECTION, ABOUT]` (click order) and `rooms == 1`.
  - `the statuses follow the state` — `SettingsUi(connection = direct, push = Registered(now), link = Connected(<mode>), roomCount = 6, presenceEnabled = true, voice = VoiceSettingsUi(enabled = true, shortcuts = 3 rows), theme = IVOIRE)`:
    "6", "Registered", "On", "On · 3", "Ivoire", "Online" (status) and "0.12 · debug" exist.
  - `the header shows the link and the broker` — "Online · 192.168.1.91:1883" reachable as text (or the two parts).
  - `the demo house shows no host and no push word` — `connection = Demo, push = PushStatus.Demo`: "Demo house" shown twice
    (header + Connection status), no "Registered|Waiting|Failed" text.
  - `no profile, no rooms: empty statuses and no crash` — `SettingsUi()`: "Rooms" exists, no node with text "0".
  - `the whole row is one button whose label carries the status` — `onNode(hasText("Presence") and hasClickAction())`
    merged semantics contain "On".
- [ ] **Step 2: Run** `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsListScreenTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** `SettingsPage`, the strings, `settings_mark.xml`, `pushWord`, `linkWord`, `SettingsListScreen`
  per the tables above. The tile uses `StaticIcon(glyph, accent = tone, Modifier.size(20.dp))` inside a 38dp `Box`
  with `RoundedCornerShape(12.dp)` background `tone.copy(alpha = 0.16f)`, under `CompositionLocalProvider(LocalContentColor provides tone)`.
  Rows in a card matching `NidooCard`'s surface (card colour, `cardBorder` 1dp, hairline dividers between rows).
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(settings): hub-style Settings list with icon tiles and statuses`.

### Task 4: The sub-pages (content moved as-is)

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsPages.kt`
- Modify: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsScreen.kt` (old monolithic body removed; keeps `SettingsTopBar`, `Section`, `NavRow`)
- Modify: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/PushSheet.kt` → rename to `NotificationsPage.kt`; `PushSheet` replaced by a page body
- Move tests: `SettingsScreenTest.kt` → `SettingsPagesTest.kt` (same file, cases retargeted at pages)

**Interfaces:**
- Consumes: `SettingsPage` (Task 3).
- Produces:
  ```kotlin
  @Composable fun SettingsPageScreen(
      page: SettingsPage, state: SettingsUi, version: String, route: Route?, now: Instant,
      onBack: () -> Unit,
      onTheme: (ThemePreference) -> Unit = {}, onEnergyView: (EnergyViewPreference) -> Unit = {},
      onRegisterAgain: () -> Unit = {}, onOpenChannels: () -> Unit = {},
      onRouteChange: (Route) -> Unit = {}, onSetUpAgain: () -> Unit = {},
      onSaveHistoryUrl: (String) -> Boolean = { true },
      onSaveFrigate: (url: String, user: String, password: String) -> Boolean = { _, _, _ -> true },
      onVoiceEnabled: (Boolean) -> Unit = {}, onDeleteShortcut: (String) -> Unit = {},
      onOpenLicences: () -> Unit = {},
      presenceSection: @Composable (onInfo: (String) -> Unit) -> Unit = {},
  )
  ```
  One `Scaffold` (transparent, `SettingsTopBar(<page title>, onBack)`, its own `SnackbarHost`) wrapping a scrolling
  column whose body is chosen by `page`. Page titles = the row titles from Task 3.

Page bodies (lifted from today's `SettingsScreen`, unchanged in behaviour):
- APPEARANCE: theme radios + `Section(settings_energy_view) { EnergyViewSection(...) }`.
- NOTIFICATIONS: `pushStatusText(status, now)`, "Register again" (`FilledTonalButton`, disabled for Demo), "Notification
  channels" (`OutlinedButton`). No sheet, no dismiss.
- PRESENCE: `presenceSection { msg -> show snackbar on this page }`.
- VOICE: mic-permission launcher + denied snackbar + `VoiceSection` + `VoiceShortcutsSection` (moved from `SettingsScreen`).
- CONNECTION: `ConnectionRows`, `RoutePicker` when `route != null`, "Set up connection again" + confirmation dialog, history and Frigate dialogs.
- ABOUT: version, notice, licences `NavRow`.

- [ ] **Step 1: Retarget the tests.** Rename `SettingsScreenTest` → `SettingsPagesTest`; `show(...)` takes a `page`
  and renders `SettingsPageScreen`. Theme case → APPEARANCE. Connection/route/set-up-again/history/Frigate cases → CONNECTION.
  Licences + version case → ABOUT (rooms half moves to Task 3, already covered). Push word cases → NOTIFICATIONS, asserting
  the full text ("Registered · …"); "tapping the row opens the sheet…" becomes `Register again reports`;
  "Notification channels reports"; "in the demo Register again is disabled" kept. Add:
  - `the voice page shows the denied snackbar when the mic is refused` — grant nothing (Robolectric shadow
    `denyPermissions(RECORD_AUDIO)`), toggle voice on, answer the launcher with denied, assert the snackbar text and
    `onVoiceEnabled` got `false`.
  - `each page has its title and a back arrow that reports` — for every `SettingsPage`, title text exists and the
    "Back" node click increments a counter.
- [ ] **Step 2: Run** `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsPagesTest*'`. Expected: FAIL (unresolved `SettingsPageScreen`).
- [ ] **Step 3: Implement** `SettingsPages.kt` and the Notifications page; delete the old `SettingsScreen` composable and `PushSheet`.
- [ ] **Step 4: Run** `./gradlew :feature:settings:testDebugUnitTest`. Expected: PASS for the whole module (except `SettingsRouteTest`, fixed in Task 5; if it fails to compile, keep the old `SettingsRoute` compiling by pointing it at `SettingsListScreen` temporarily).
- [ ] **Step 5: Commit** `refactor(settings): each Settings section on its own page`.

### Task 5: Routes and navigation

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsScreen.kt` (`SettingsRoute`, new `SettingsPageRoute`)
- Modify: `app/src/main/kotlin/com/gekkotron/nidoo/navigation/NidooNavHost.kt`
- Test: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsRouteTest.kt`, `app/src/test/kotlin/com/gekkotron/nidoo/navigation/MainTabsTest.kt`

**Interfaces:**
- Consumes: `SettingsListScreen` (Task 3), `SettingsPageScreen` (Task 4).
- Produces:
  - `@Composable fun SettingsRoute(onOpenPage: (SettingsPage) -> Unit, onOpenRooms: () -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel())`
    — collects state, calls `refreshPush()` on every resume, renders the list.
  - `@Composable fun SettingsPageRoute(page: SettingsPage, route: Route?, onRouteChange: (Route) -> Unit, onSetUpAgain: () -> Unit, onOpenLicences: () -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel(), clock: () -> Instant = Instant::now, presenceSection: … = { info -> PresenceSettingsSlot(onInfoMessage = info) })`
    — owns today's resume refresh + 60 s ticker (only meaningful on NOTIFICATIONS; harmless elsewhere) and wires every VM callback.
  - App: `@Serializable data class SettingsPageKey(val page: String) : NavKey` (`page` = `SettingsPage.name`).

- [ ] **Step 1: Update tests.** In `SettingsRouteTest`, both existing cases render `SettingsPageRoute(SettingsPage.NOTIFICATIONS, …)`
  (same assertions); add `the list re-reads the push status on resume` rendering `SettingsRoute` (same pattern as the first case).
  In `MainTabsTest`, add `a settings page pushes over Settings and pops back to it`:
  `stack = [HomeKey, SettingsKey]`, `push(stack, SettingsPageKey("VOICE"))` → `[HomeKey, SettingsKey, SettingsPageKey("VOICE")]`,
  `pop(stack, SettingsPageKey("VOICE"))` → `[HomeKey, SettingsKey]`.
- [ ] **Step 2: Run** `./gradlew :feature:settings:testDebugUnitTest :app:testDebugUnitTest --tests '*SettingsRouteTest*' --tests '*MainTabsTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the two routes and the nav entries: `entry<SettingsKey>` → `SettingsRoute(onOpenPage = { push(backStack, SettingsPageKey(it.name)) }, onOpenRooms = { push(backStack, RoomOrderKey) }, onBack = { pop(backStack, SettingsKey) })`;
  `entry<SettingsPageKey> { key -> SettingsPageRoute(SettingsPage.valueOf(key.page), route, viewModel::setRoute, viewModel::setUpAgain, { push(backStack, LicencesKey) }, { pop(backStack, key) }) }`.
  `AppLink.Settings` still lands on `SettingsKey` (unchanged).
- [ ] **Step 4: Run** `./gradlew :feature:settings:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(settings): open each Settings section as its own page`.

### Task 6: Visual check and wrap-up

**Files:**
- Create: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/ScreenshotSupport.kt` (copy of `feature/home`'s `renderPng` / `distinctColors`)
- Create: `feature/settings/src/test/kotlin/com/gekkotron/nidoo/feature/settings/ui/SettingsListScreenshotTest.kt`

- [ ] **Step 1: Write** `settings list renders in Night Glass and Ivoire` (`@GraphicsMode(NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`,
  `@Config(qualifiers = "w411dp-h914dp")`): render the full state from Task 3's status test in each theme, `renderPng("settings-list-night")`
  / `("settings-list-ivoire")`, assert `distinctColors() > 20`.
- [ ] **Step 2: Run** `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsListScreenshotTest*'`. Expected: PASS; open
  `feature/settings/build/screenshots/settings-list-*.png` and check the anti-goal: the whole list (header + both groups)
  fits on the 914dp-tall screen, tiles are tinted, statuses right-aligned, both themes legible.
- [ ] **Step 3: Full check** `./gradlew :core:designsystem:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 4: Commit** `test(settings): list screenshots in both themes`.
