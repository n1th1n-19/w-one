# w-one design

Direction decided with the project owner. Every decision has a one-line reason (antislop R-31).

## System
Material 3 as the base, with Apple Human Interface habits where they fit.
- Reason: w-one is an Android app on Pixel phones; Material 3 components, motion and accessibility behaviour are what the platform already teaches users.
- Apple details kept: large collapsing titles on portrait screens, inset grouped lists for settings, bottom sheets for choices (host switcher), plain segmented controls, restrained haptics.
- Not used: iOS translucency, SF Symbols, iOS-style navigation back chrome.

## Colour
- Dynamic colour (Material You): the app takes its palette from the phone's wallpaper theme on Android 12 and newer. Reason: the owner rejected fixed accents; matching the phone is the neutral answer.
- Fallback on older phones, and the choice "Fixed neutral": a grey Material 3 scheme with no hue.
- Default theme: dark. Light and follow-system are available. Reason: dark is the owner's default for use in dim rooms; both themes are generated from the same scheme so both pass contrast.
- No rainbow, no gradients, no glow, no decorative shapes. Reason: owner rule, and the main AI-slop tell in the first build.
- Per-mode custom colours (keyboard, trackpad, air mouse, gamepad) are an owner option layered on top (planned, not built yet).

## Type
- UI text: the system sans (Roboto Flex on Pixel) at the Material 3 type scale. Reason: owner picked "system sans + JetBrains Mono".
- JetBrains Mono only for key legends, addresses, values and the typed preview. Reason: those are code-like strings where fixed width helps reading.

## Shape and spacing
- Material 3 shape scale (small 8, medium 12, large 16, extra large 28 dp). Keycaps 8 dp. No pills except Material's own controls.
- 4 dp base, 8 dp rhythm, 48 dp minimum touch targets.
- No shadows; depth is tone (surface containers).

## Layout
- Portrait: large top app bar, tonal status card, mode tiles, bottom navigation (Home, Computers, Settings). Reason: owner asked for a mix of the three Home options.
- Landscape modes: Material 3 navigation rail on the left (Keyboard, Trackpad, Air mouse, Gamepad, Now playing, Home). Reason: always visible, one tap, gives content the full height.

## Dials (antislop)
ENERGY 1 / RHYTHM 2 / MOTION 2. Motion only where it carries state: key sink, stick return, switch slide, sheet entry.

## Icon
A small mascot: a keycap-shaped creature whose outline is noise-shaped, mouth drawn as a w. Parameters chosen by the owner from the mascot lab page.
