# KeiOS

<!-- markdownlint-disable MD013 MD033 -->

[中文版本 (CN)](readme/CN.md)

<p align="center">
  <a href="https://github.com/hosizoraru/KeiOS/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/hosizoraru/KeiOS?include_prereleases&sort=semver&display_name=tag&style=flat-square"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/network/members"><img alt="GitHub forks" src="https://img.shields.io/github/forks/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/issues"><img alt="GitHub issues" src="https://img.shields.io/github/issues/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/commits/master"><img alt="Last commit" src="https://img.shields.io/github/last-commit/hosizoraru/KeiOS/master?style=flat-square"></a>
  <img alt="Release downloads" src="https://img.shields.io/github/downloads/hosizoraru/KeiOS/total?style=flat-square">
</p>

<p align="center">
  <a href="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-debug-apk.yml"><img alt="Debug APK CI" src="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-debug-apk.yml/badge.svg?branch=master"></a>
  <a href="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-benchmark-apk.yml"><img alt="Benchmark APK CI" src="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-benchmark-apk.yml/badge.svg?branch=master"></a>
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-35-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-37-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20--RC2-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-1.12.0-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
</p>

KeiOS is an Android utility console for system inspection, local MCP service control, GitHub
Releases / Actions workflows, GitHub Star import, subscription-project tracking, and Blue Archive
helper tools. It combines a Compose + Miuix interface with v2 liquid-glass chrome, dense status
cards, import/export tools, localized MCP skills, notification helpers, repository discovery,
feedback issue drafting, cache diagnostics, and generated Baseline Profiles.

## Project Signals

| Item | Value |
| --- | --- |
| Stable package | `os.kei` |
| Supported ABI | `arm64-v8a` |
| Android baseline | Android 15+ (`minSdk 35`) |
| Target SDK | Android 17 / API 37 |
| UI stack | Jetpack Compose, Miuix, liquid-glass chrome |
| Runtime stack | Kotlin, Java 21, Shizuku/Root, Media3, MMKV, Ktor, OkHttp |
| Languages | Simplified Chinese, English, Japanese |
| Source release | `v1.16.0` |

## Quick Links

- [Latest Stable Release](https://github.com/hosizoraru/KeiOS/releases/latest)
- [All Releases](https://github.com/hosizoraru/KeiOS/releases)
- [Debug APK CI artifact](https://nightly.link/hosizoraru/KeiOS/workflows/ci-debug-apk/master)
- [Benchmark APK CI artifact](https://nightly.link/hosizoraru/KeiOS/workflows/ci-benchmark-apk/master)
- [Feature Overview](readme/FEATURES.md)
- [Build Guide](readme/BUILD.md)
- [Contributing](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)

## Main Features

- Home dashboard with compact status pills and MCP, GitHub, and BA summary cards.
- OS tools for system tables, Android/Java/Linux properties, built-in activity shortcuts,
  searchable activity/shell card sheets, privileged shell cards, and card import/export.
- Local MCP server controls with config copy, runtime logs, foreground service support, Claw
  onboarding, localized SKILL.md output, workflow blueprints, structured tool metadata, and 51 tools
  across runtime, Home, OS, GitHub discovery/tracking, BA accounts, dailies, and cache inspection.
- GitHub tracking for Releases, Actions artifacts, generic Git sources, Direct APKs, and F-Droid
  repositories, with Atom/API strategy comparison, package-name scanning, installed-app reverse
  scan, subscription projects, share-import links, app linkage, and Star List import.
- GitHub Star import activity for authenticated stars, public user stars, and public Star List URLs,
  with list discovery, quality filters, multi-select import, APK verification, and exit
  confirmation.
- GitHub managed install and share-import handoff paths for privileged APK delivery, with
  notification/Super Island progress, manifest inspection, versionCode display, and install
  confirmation surfaces.
- GitHub Actions update notifications with tracked-app icons, deep links into the Actions sheet,
  recommended run targeting, and debug notification testing.
- History Hub for Actions, refresh diagnostics, tracking changes, and tracked-app install/update
  events, with per-phase network timing for slow refreshes, unread badges, search, export, and MCP
  query support.
- JSON import and WebDAV sync for multi-schema KeiOS data migration, including GitHub/F-Droid
  tracking, OS card transfer data, BA multi-account data, previews, and routed result screens.
- BA office helpers for AP, cafe visit, arena refresh reminders, six-slot Craft Chamber timers,
  configurable one-tap dailies, per-account Quick Settings tiles and launcher shortcuts,
  server-aware calendar/pool data, Super Island notifications, and student-guide entry points.
- Student Guide catalog with full-page search, sorting, long-lived implemented-student detail
  cache, media cache, Memorial Lobby cards and PiP video playback, BGM favorites with removal Undo,
  native media notifications, gallery viewing, media export, liquid bottom dock, and import/export
  for favorites.
- Settings for theme, motion, v2 liquid-glass components, bottom-bar effect policy, search focus
  behavior, grip-aware floating docks, background images, app language, permissions, cache
  diagnostics, structured logs, local GitHub issue feedback, telemetry-free diagnostics, and
  notification compatibility.

## v1.16.0 Highlights

- GitHub tracking decides more reliably: projects that restarted their numbering, pre-releases
  superseded by a shipped stable or left unfed for 14 days, and releases with no downloadable file
  are read correctly, uncertain comparisons say so, and a surprising choice explains itself.
- Refresh requests no longer hold threads and Atom mode sends its two requests at once: 40
  repositories went from 1973 ms to 522 ms against a 120 ms test server. Slow items name the network
  phase that cost them, in history, exports, and MCP.
- Liquid Glass over a flat field draws its effect chain's colour directly, and continuous-corner clips
  happen inside the glass layer: BA Office frame p50 24.3 ms to 11.6 ms, RenderThread p50 down 16% on
  OS and 18% on MCP, with materials and motion unchanged.
- Older builds in the release and F-Droid histories open APK info and install in-app; every share and
  download follows the installer and download settings. The Student Guide pages with Miuix Cross-Axis,
  and the media session gives untrusted controllers read-only access.
- Built with Gradle 9.8.0, AGP 9.4.1, Compose 1.12.1, Ktor 3.6.0, Miuix 2afdbb39, and dav4jvm 4.1.0,
  with a Baseline Profile re-captured on this release's code.

Read the full feature tour:

- [Feature Overview (EN)](readme/FEATURES.md)
- [功能完整介绍 (CN)](readme/FEATURES_CN.md)

## Current Distribution

- Stable APKs are published through [GitHub Releases](https://github.com/hosizoraru/KeiOS/releases).
- The public stable channel always resolves through [Latest Stable Release](https://github.com/hosizoraru/KeiOS/releases/latest).
- This source snapshot and its local release tag target `v1.16.0`.
- Release package baseline: `os.kei`, `arm64-v8a`, Android 15+ (`minSdk 35`).
- Runtime and build baseline: `targetSdk=37`, Java 21, Gradle Wrapper `9.8.0`, Kotlin `2.4.20`,
  Compose `1.12.1`, Android Gradle Plugin `9.4.1`, Ktor `3.6.0`.
- App language resources currently cover Simplified Chinese, English, and Japanese.

## Documentation

- [Documentation Index](readme/INDEX.md)
- [Release Notes v1.16.0](readme/RELEASE_V1.16.0.md)
- [Build Guide (EN)](readme/BUILD.md)
- [构建指南 (CN)](readme/BUILD_CN.md)
- [Todo List (EN)](readme/TODO.md)
- [待办清单 (CN)](readme/TODO_CN.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=hosizoraru/KeiOS&type=Date)](https://www.star-history.com/#hosizoraru/KeiOS&Date)
