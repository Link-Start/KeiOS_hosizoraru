# Contributing to KeiOS

Thanks for your interest in contributing to KeiOS. This project is a personal open source Android
app focused on system utilities, GitHub tracking, and Blue Archive content pages.

## Project Stack

KeiOS is built primarily with:

- Kotlin
- Jetpack Compose
- Gradle
- Android minSdk 35
- Miuix and custom liquid glass style UI components

For local build instructions, please read `README_BUILD.md` and `README_CN_BUILD.md`.

## Issues

Please use issues to report bugs, request features, or discuss larger changes before implementation.

When reporting a bug, include:

- What happened and what you expected.
- Clear reproduction steps.
- Device model, Android version, KeiOS version, and install source.
- Logs, screenshots, or recordings when available.

For feature requests, describe the user problem, the workflow it improves, and any alternatives you
considered.

## Pull Requests

Small, focused pull requests are easiest to review and merge. Please keep changes scoped to one
concern whenever possible.

For large features, UI redesigns, architecture changes, storage changes, or behavior that affects
multiple pages, please open an issue first so the approach can be discussed.

Before opening a PR:

- Keep the diff focused and reviewable.
- Explain what changed and why.
- Include screenshots or recordings for UI changes.
- Describe how you tested the change.
- For performance-related changes, state whether testing used debug, release, or benchmark builds,
  and mention any meaningful differences.
- Avoid committing secrets, local machine paths, generated private files, or device-specific debug
  data.

## Testing

This repository may not have complete automated coverage for every feature. Use the most relevant
local validation for your change, such as building the app, running available tests, checking the
affected screen on a device or emulator, and reviewing logs.

## Documentation

Update documentation when the user-facing behavior, build process, setup flow, or contribution
workflow changes. If your change affects local builds, check whether `README_BUILD.md` or
`README_CN_BUILD.md` should be updated.
