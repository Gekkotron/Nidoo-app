# Wake-up alarms in the app — design

**Date:** 2026-11-20
**Status:** Design, pending approval
**Owner:** Gekkotron

## Intent & why

The hub's Wake-up alarms (music alarms, each stored as a flow labelled
`wake-up`) can only be changed on the Nidoo web page. From the phone the
user can already switch one on or off, because alarms show up as ordinary
rows in the Automations tab, but cannot see or change when it rings.

The user wants to change an alarm's **time and weekdays** from the app, and
wants flows and wake-ups to share one tab.

## Goals

- The Automations tab is renamed **Routines** (EN and FR) and holds two
  sections behind a two-way switch: **Flows** and **Wake-up**.
- The Wake-up section lists every alarm with its name, time, weekdays and
  an on/off switch.
- Tapping an alarm opens a sheet to change hour, minute and weekdays, then
  save. The change is stored on the hub and survives a restart.
- Alarms no longer appear in the Flows section (no duplicates).
- Live updates and LAN/gateway behaviour are the same as flows today.

## Non-goals

- Creating or deleting an alarm.
- Changing its name, playlist, shuffle, output, speakers, volumes or fade-in.
- Showing the playlist name or a "next ring" line.
- Turning the hub's Wake-up feature itself on or off (Settings → Home).
- An iOS screen. Non-UI code goes in `commonMain` so it can follow later.

## Anti-goals

- The app must never rebuild an alarm's flow itself. The generator lives in
  the hub (`nidoo-core::flow::wake_up`) and stays the only one.
- Saving from the app must not lose any setting the app does not show.

## Constraints

- The app transport does GET and JSON POST only (`LanOrGatewayGet`); no PUT.
- No gateway, onboarding or allow-rule change.
- Tab stays hidden under the Demo profile.
- Hub weekdays are 0 = Sunday … 6 = Saturday, never empty.

## Approaches

1. **Hub endpoint (picked).** New `POST /api/v1/flows/{id}/wake-up` taking
   `{hour, minute, days}`. The hub loads the alarm's stored settings,
   replaces those three fields, validates, and rebuilds the flow with its
   own generator. One source of truth; fits the POST-only transport. Costs a
   hub change and redeploy.
2. **App patches the flow JSON** and saves it with `PUT /flows/{id}`. No hub
   change, but the app must know the generated flow's shape (cron format,
   node ids), needs PUT added to the transport, and breaks silently when
   the hub's generator changes. Rejected: violates the first anti-goal.
3. **Kotlin port of the generator.** Same drift problem as 2, larger.
   Rejected.

## Design

### Hub (`Nidoo` repo)

- `POST /api/v1/flows/{id}/wake-up`, body `{"hour":7,"minute":30,"days":[1,2,3,4,5]}`.
- Reads the flow's `wake_up` settings, sets the three fields (days sorted
  and deduplicated), runs `validate`, rebuilds with `to_flow` keeping id and
  `enabled`, writes it like any flow save (retained publish follows).
- Name: kept, except when it is still the default name for the old time
  (« Wake-up 07:00 »); then it follows the new time, as the web page does.
- Answers: `200` on success; `404` unknown flow; `422` when the flow is not
  a readable alarm or the time/days are invalid.

### App

- `core:flows` (`commonMain`): `FlowDto` also reads `labels` and an optional
  `wake_up` (`hour`, `minute`, `days`); unknown keys stay ignored.
  `FlowsClient` gains `setWakeUp(id, hour, minute, days)`; `FlowsRepository`
  exposes it the same way as `run` (request lives in the repository scope).
  The row updates from the hub's retained echo, not optimistically.
- `feature:automations`: a segmented switch `Flows | Wake-up` under the
  title. Flows keeps today's screen minus flows labelled `wake-up`. Wake-up
  shows:

  ```
  Wake-up 07:00                    [on ]
  07:00 · Mon–Fri

  Weekend                          [off]
  09:30 · Sat, Sun
  ```

  Weekdays are shown in the phone's locale order and names.
- Edit sheet (tap a row): time picker, seven day chips, Save. Save is
  disabled with no day picked and while the request runs; the sheet closes
  on success and shows an error line on failure.
- An alarm whose settings cannot be read (label present, `wake_up` missing
  or malformed) shows its name and switch only, and does not open the sheet.
- Empty section: a line saying alarms are created on the Nidoo web page.
- Offline: rows are read-only, as flows are today.
- A hub without the endpoint answers `404`: the sheet says the hub needs an
  update.
- Tab label, route title and strings renamed to Routines in EN and FR.

## Left to the builder

- Exact composables for the switch and the time picker (Material 3).
- Whether the selected section is remembered across launches.
- Test split; at minimum DTO parsing, the client call, the Flows/Wake-up
  partition, weekday formatting, and the hub endpoint's name rule and
  error answers.

## Rollout

Hub first (endpoint, tests, redeploy on the GEEKOM by the owner), then the
app. The app works against an old hub for everything except Save.
