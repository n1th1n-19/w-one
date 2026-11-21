# w-one

Use your Android phone as a Bluetooth keyboard, trackpad, air mouse and gamepad. Nothing to install on the computer: it sees an ordinary Bluetooth keyboard, mouse, touchpad and controller, so it works on any Linux distro.

Status: early development (Phase 0, foundations). Nothing is released yet.

## Modes

- Keyboard: compact programmer layout with layers for letters, numbers, symbols and function keys, sticky modifiers, and mechanical key sounds.
- Trackpad: acts as a real multitouch touchpad, so the desktop applies its own acceleration, scrolling and gestures. A simple mouse-style mode is the fallback.
- Air mouse: move the cursor by tilting the phone, with a hold-to-move button.
- Gamepad: generic HID gamepad with rearrangeable, resizable buttons and saved layouts.

## Requirements

- Android 9 (API 28) or newer, with the Bluetooth HID Device profile enabled by the phone maker. Some phones disable it. Pixel phones support it.
- A computer with Bluetooth Classic (BR/EDR). Bluetooth LE alone is not enough.

## Principles

- Open source, GPL-3.0-or-later.
- No network access: the app does not request the INTERNET permission, and has no analytics or crash reporting.
- Builds from source with no proprietary dependencies, so it can be published on F-Droid.
- All bundled assets (sounds, fonts, icon) use free licences. See [docs/ASSETS.md](docs/ASSETS.md).

## Linux pairing notes

Recent BlueZ versions reject input devices that are not bonded. Pair the phone first, then connect. If a connection is rejected, `journalctl -u bluetooth` shows `Rejected connection from !bonded device`. Do not turn off `ClassicBondedOnly` unless you accept the security trade-off (CVE-2023-45866). Typing assumes a US keyboard layout on the computer.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
