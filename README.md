# w-one

Use your Android phone as a Bluetooth keyboard, trackpad, air mouse and gamepad. Nothing to install on the computer: it sees an ordinary Bluetooth keyboard, mouse, touchpad and controller, so it works on any Linux distro.

Status: first release, 0.1.0. Tested on a Pixel 10a (Android 17) with Fedora KDE Plasma over Bluetooth Classic. Other phones and distros are untested.

## Install

Download `w-one-0.1.0.apk` from the latest release, check it against the `.sha256` file, and install it (your phone will ask to allow installs from your browser or file manager). Pair the phone with your computer from the app's Computers tab.

## Modes

- **Keyboard**: compact programmer layout (letters, numbers, symbols, function keys), sticky and locking modifiers, a typing preview, 13 lighting effects and recorded mechanical key sounds.
- **Trackpad**: acts as a real multitouch touchpad, so the desktop applies its own acceleration, scrolling and gestures. A simple mouse-style mode is the fallback.
- **Air mouse**: move the cursor by tilting or turning the phone while holding a clutch button.
- **Gamepad**: generic HID gamepad with rearrangeable, resizable buttons, saved layouts, and buttons that can send keys or mouse clicks instead.

## Requirements

- Android 9 (API 28) or newer, with the Bluetooth HID Device profile enabled by the phone maker. Some phones disable it. Pixel phones support it.
- A computer with Bluetooth Classic (BR/EDR). Bluetooth LE alone is not enough.

## How it works

One HID report descriptor exposes several devices over a single connection, so switching modes never reconnects. There are two profiles, because Linux binds its multitouch driver to any device that has a touchpad collection and that driver ignores the mouse and gamepad collections of the same device (checked on Fedora with the kernel's input list):

- **Full** (default): keyboard (report 1), relative mouse with wheel (2) and gamepad (3). The trackpad mode sends mouse reports with laptop-style gestures.
- **Touchpad** (Settings > Trackpad > Native multitouch touchpad): keyboard (1) and a three-finger multitouch touchpad (4). libinput treats it like a laptop touchpad; mouse, air mouse and gamepad do not work in this profile.

Changing the profile needs the phone to be removed on the computer and paired again, because the computer caches the descriptor at pairing. Android's Bluetooth stack also limits the size of the stored descriptor (a 573 byte one was rejected by BlueZ, 335 bytes works), so both stay under 340 bytes. See `app/src/main/kotlin/app/wone/hid/`.

## Build

Needs JDK 17 or newer and the Android SDK (platform 37.1, build-tools 36).

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Install it with `adb install -r`.

## Pairing on Linux

Recent BlueZ versions reject input devices that are not bonded. Pair the phone first, then connect. If a connection is rejected, `journalctl -u bluetooth` shows `Rejected connection from !bonded device`. Do not turn off `ClassicBondedOnly` unless you accept the security trade-off (CVE-2023-45866). A computer caches what a device can do when it pairs, so after updating w-one remove the phone on the computer and pair again. Typing assumes a US keyboard layout on the computer.

Useful checks: `bluetoothctl`, `evtest`, `libinput list-devices`, `libinput debug-events`, `btmon`.

## Principles

- Open source, GPL-3.0-or-later.
- No network access: the app does not request the INTERNET permission, and has no analytics or crash reporting. `tools/check_manifest.sh` enforces the permission list in CI.
- Builds from source with no proprietary dependencies, so it can be published on F-Droid.
- All bundled assets (sounds, fonts, icon) use free licences. See [docs/ASSETS.md](docs/ASSETS.md).

## Project layout

- `app/src/main/kotlin/app/wone/hid/`: report descriptor, report encoders, Bluetooth HID engine.
- `app/src/main/kotlin/app/wone/input/`: keyboard, touchpad, air mouse and gamepad logic (no Android UI, unit tested).
- `app/src/main/kotlin/app/wone/ui/`: Jetpack Compose screens.
- `app/src/main/kotlin/app/wone/feedback/`: sounds and haptics.
- `tools/`: scripts that build the sound assets and check the manifest.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Security reports: see [SECURITY.md](SECURITY.md).
