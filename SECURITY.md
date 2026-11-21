# Security policy

w-one sends keyboard, mouse and gamepad input to a computer you pair it with, so security problems can affect that computer. Please report them privately.

## Reporting a vulnerability

Use GitHub private vulnerability reporting on this repository (Security tab, "Report a vulnerability"). Do not open a public issue for security problems. Include the Android version, phone model, app version and steps to reproduce.

## Scope

- Pairing and connection handling, including connecting to a host the user did not choose.
- Anything that lets another app on the phone send input through w-one.
- Leaking typed text, macros or saved layouts.
- Unwanted permissions or network access added by the app or a dependency.

## Design rules the project follows

- No INTERNET permission, no analytics, no crash reporting.
- Only bonded hosts the user selected are used. The notification shows the connected host with a Disconnect action.
- Components are not exported unless required. Backups of app data are disabled.
- The typing preview has a secure mode that masks and does not store text. Release builds do not log keystrokes.

## Supported versions

Only the latest release receives security fixes.
