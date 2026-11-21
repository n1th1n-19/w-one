# Changelog

## 0.1.0

First release.

- Modes: keyboard (layers, sticky and locking modifiers, typed-text preview with a blinking cursor, optional per-key backlight effects), trackpad (simple mouse mode with gestures, optional native multitouch profile), air mouse (rotation sensor with a hold-to-move button), gamepad (rearrangeable layouts, buttons that send keys or mouse clicks).
- One Bluetooth Classic HID connection; two descriptor profiles because Linux ignores mouse and gamepad next to a touchpad (see README).
- Computers shown as numbered slots 1 to 5 with one-tap switching from every mode screen; connection retry limits and plain-language failure reasons.
- Material 3 interface with the phone's wallpaper colours, dark by default, left navigation rail in landscape modes.
- Click sounds from CC0 mechanical keyboard recordings, played on the phone speaker; haptics.
- No network permission; builds from source for F-Droid.

Known gaps: Now Playing with a PC helper, stickers and image keycaps, per-mode colour editor, emoji layer, word suggestions, paste to type, host keyboard layout setting and a diagnostics screen are planned but not built. The signed release build was not launched on a device before publishing; the debug build was tested.
