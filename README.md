# Nidoo

[![CI](https://github.com/Gekkotron/Nidoo-app/actions/workflows/ci.yml/badge.svg)](https://github.com/Gekkotron/Nidoo-app/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/Gekkotron/Nidoo-app/graph/badge.svg)](https://codecov.io/gh/Gekkotron/Nidoo-app)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A beautiful, self-discovering home-automation app for Android (Kotlin + Jetpack Compose).

Nidoo talks **MQTT** directly. It connects to your local broker at home and goes through an end-to-end encrypted [Nidoo-gateway](https://github.com/Gekkotron/Nidoo-gateway) when you're away. It builds its dashboard from **Home Assistant MQTT Discovery** messages, so devices appear on their own, with no hard-coded device list.

> **Status:** foundation and demo Home screen. The core and the Home screen run against a built-in demo house; real broker transports come next. See the design spec (in [docs/superpowers/specs/](docs/superpowers/specs/)) and [mockups](docs/design/mockups/).

## Modules

| Module | Role |
|---|---|
| `:core:model` | Domain types: `Entity`, `StateValue`, `Command`, `UiOverride`, `Area` |
| `:core:crypto` | AES-256-GCM envelope for Nidoo-gateway |
| `:core:transport` | `MqttTransport` contract and topic matching (+ a test fake) |
| `:core:state` | Jinja-subset templates, one mapper per kind, `StateStore`, optimistic `CommandTracker` |
| `:core:source` | HA MQTT Discovery parser and `MqttDiscoverySource` |
| `:core:demo` | In-process demo broker with a fake house (discovery, state, echoed commands, live power) |
| `:core:designsystem` | Night Glass / Ivoire tokens, bundled Inter + Manrope, cards, pill, chips |
| `:feature:home` | Home screen: rooms, cards, control sheet |
| `:app` | Hilt graph, Navigation 3, demo-mode wiring |

## Try the demo

With a phone connected over USB (developer mode on):

```bash
./gradlew :app:installDebug
adb shell am start -n com.gekkotron.nidoo.debug/com.gekkotron.nidoo.MainActivity
```

The debug build talks to a built-in demo house, so no broker is needed.

Build and test: `./gradlew check koverXmlReport`.

## Screenshot tests

`:core:designsystem` has Compose screenshot tests (AGP test suites, experimental) that render a sheet of the design system and the opening icons in both Night Glass and Ivoire. They catch unintended theme changes.

```bash
# Validate against the committed references
./gradlew :core:designsystem:testDebugScreenshotTestDefaultTestSuite

# Regenerate the references after an intended visual change
./gradlew :core:designsystem:updateDebugScreenshotTestDefaultTestSuite
```

The sources live in `core/designsystem/src/screenshotTest/` and the reference PNGs in `core/designsystem/src/screenshotTestDefaultDebug/reference/`. This is local only: `check` and CI do not run it.

## Highlights (v1)

- Live state over MQTT subscriptions, with no polling.
- Automatic switching between direct Wi-Fi and the encrypted gateway.
- Device discovery via `homeassistant/…/config`, plus `nidoo/ui/…` overrides (rename, rooms, order, card style).
- Home, Energy (live power flow) and Security screens.
- Night Glass (dark) and Ivoire (light) themes.
- Push notifications, with the device token registered over MQTT.
- History: sensor and energy charts (24 h, 7 d, 30 d, kWh per day) read from the hub's history API. It is found automatically at `http://<broker host>:8090` (the history API runs next to Mosquitto); Settings → History service (or `history.url` in the setup QR, `tools/setup-qr.py --history-url`) only overrides that. Away from home, add `<broker host>:8090` to the gateway key's rules once.

## License

Copyright 2026 Gekkotron. Licensed under the [Apache License 2.0](LICENSE).
