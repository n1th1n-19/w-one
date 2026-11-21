# Asset sources and licences

Every bundled asset must have a free licence compatible with GPL-3.0-or-later and F-Droid. Never bundle CC-BY-NC or audio of unknown origin. Check the licence on the source page before adding a file, and record it here.

## Keyboard sounds (bundled)

Cut from CC0 recordings by the Mechanify project (MIT code, https://github.com/abraraltaf92/mechanify), converted with `tools/build_keyboard_sounds.sh`. Licence verified on each Freesound source page.

| Switch feel | Samples | Source recording | Licence |
|---|---|---|---|
| Thocky (Cherry MX Red, linear) | 32 presses, 16 releases | handygaber, "Keyboard clicking typing Cherry Red Mechanical", https://freesound.org/s/499773/ | CC0 1.0 |
| Tactile (Cherry MX Clear) | 17 presses, 14 releases | humi74, "Mechanical keyboard clicking. Different keys (4)", https://freesound.org/s/412926/ | CC0 1.0 |
| Clicky (Cherry MX Blue) | 3 presses, 9 releases | UberBosser, "wKey", "qKey", "ctrlKey", https://freesound.org/s/421581/ , /421583/ , /421584/ | CC0 1.0 |

Space, Enter, Backspace and Shift use the same samples pitched down about 20 % for a deeper stabiliser sound. Blue has few source takes, so it is the weakest set. A Cherry Brown set (majod, https://freesound.org/s/400167/, CC0, verified) is planned to replace or extend Tactile.

Mechvibes sound packs are not used: the repository states an MIT licence for the app code only, not for the sounds.

## Mouse, trackpad and gamepad sounds

Synthesised from scratch by `tools/gen_ui_sounds.py` (damped sine bursts and noise), so they carry the project licence. They are not recordings.

## Fonts

JetBrains Mono (Regular, Medium, Bold), SIL OFL 1.1, https://github.com/JetBrains/JetBrainsMono. Licence text in `third_party/JetBrainsMono-OFL.txt`.

## Icon

Created for this project and released under the project licence.
