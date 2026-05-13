# KeiOS

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
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-1.11.1-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
</p>

KeiOS is an Android utility console for system inspection, local MCP service control, GitHub
Releases / Actions workflows, GitHub Star import, subscription-project tracking, and Blue Archive
helper tools. It combines a Compose + Miuix interface with v2 liquid-glass chrome, dense status
cards, import/export tools, localized MCP skills, notification helpers, repository discovery,
feedback issue drafting, cache diagnostics, and generated Baseline Profiles.

## Project Signals

| Item              | Value                                                |
|-------------------|------------------------------------------------------|
| Stable package    | `os.kei`                                             |
| Supported ABI     | `arm64-v8a`                                          |
| Android baseline  | Android 15+ (`minSdk 35`)                            |
| Target SDK        | Android 17 / API 37                                  |
| UI stack          | Jetpack Compose, Miuix, liquid-glass chrome          |
| Runtime stack     | Kotlin, Java 21, Shizuku, Media3, MMKV, Ktor, OkHttp |
| Languages         | Simplified Chinese, English, Japanese                |
| Latest stable tag | `v1.5.0`                                             |

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
- OS tools for system tables, Android/Java/Linux properties, activity shortcuts, Shizuku shell cards, and card import/export.
- Local MCP server controls with config copy, runtime logs, foreground service support, Claw
  onboarding, localized SKILL.md output, workflow blueprints, structured tool metadata, and 45 tools
  across runtime, Home, OS, GitHub discovery/tracking, and BA cache inspection.
- GitHub tracking for Releases and Actions artifacts, with Atom/API strategy comparison,
  package-name scanning from release APKs, reverse repository scanning from installed packages,
  subscription-project tracking, share-import links, app linkage, and Star List import.
- GitHub Star import activity for authenticated stars, public user stars, and public Star List URLs,
  with list discovery, quality filters, multi-select import, APK verification, and exit
  confirmation.
- GitHub managed install and share-import handoff paths for Shizuku-backed APK delivery, with
  notification/Super Island progress, manifest inspection, versionCode display, and install
  confirmation surfaces.
- GitHub Actions update notifications with tracked-app icons, deep links into the Actions sheet,
  recommended run targeting, and debug notification testing.
- JSON import for multi-schema KeiOS data migration, including OS card transfer data and routed
  result screens.
- BA office helpers for AP, cafe visit, arena refresh reminders, server-aware calendar/pool data,
  per-server ID cards, media settings, AP/cafe Super Island notifications, and student-guide entry
  points.
- Student Guide catalog with full-page search, sorting, media cache, voice-language labels, BGM
  favorites, gallery viewing, media export, liquid bottom dock, and import/export for favorites.
- Settings for theme, motion, v2 liquid-glass components, bottom-bar effect policy, search focus
  behavior, grip-aware floating docks, background images, app language, permissions, cache
  diagnostics, structured logs, local GitHub issue feedback, telemetry-free diagnostics, and
  notification compatibility.

## Post-v1.5.0 HEAD Highlights

- GitHub tracking now treats direct APK, JSON feed, companion JSON, versioned directory, and APK
  directory-index sources as subscription projects, with remote health, stable/prerelease channels,
  release notes, and remote stable/prerelease cards.
- Subscription and GitHub tracked items can surface install actions when the package is missing;
  the install button reuses the existing APK asset, downloader, app-managed Shizuku install, and
  manifest inspection flow.
- GitHub page state gained remembered sort/filter/order settings, visible-list batch refresh from
  the floating dock, full-refresh shortcuts, unsaved-change confirmation across edit/settings
  sheets, and separate Actions update intervals with 2h / 3h options.
- MCP was productized around entrypoint, workflow, and advanced tools, with typed catalog metadata,
  JSON schemas, structured outputs, workflow blueprints, resource/help registries, a redesigned MCP
  Skill page, and adaptive runtime-log/session monitoring.
- Home was simplified into status pills plus focused MCP / GitHub / BA cards, removing the repeated
  overview card while keeping compact, scan-first summaries.
- GitHub install and subscription cards now use installed app labels when available, keep remote
  health at the bottom, and expose release notes through the same more-actions model as GitHub
  projects.

Read the full feature tour:
- [Feature Overview (EN)](readme/FEATURES.md)
- [功能完整介绍 (CN)](readme/FEATURES_CN.md)

## Current Distribution

- Stable APKs are published through [GitHub Releases](https://github.com/hosizoraru/KeiOS/releases).
- Current stable tag: [v1.5.0](https://github.com/hosizoraru/KeiOS/releases/tag/v1.5.0).
- Release package baseline: `os.kei`, `arm64-v8a`, Android 15+ (`minSdk 35`).
- Runtime and build baseline: `targetSdk=37`, Java 21, Kotlin `2.3.21`, Compose `1.11.1`, Android
  Gradle Plugin `9.2.1`.
- App language resources currently cover Simplified Chinese, English, and Japanese.

## Documentation

- [Documentation Index](readme/INDEX.md)
- [Build Guide (EN)](readme/BUILD.md)
- [构建指南 (CN)](readme/BUILD_CN.md)
- [Todo List (EN)](readme/TODO.md)
- [待办清单 (CN)](readme/TODO_CN.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=hosizoraru/KeiOS&type=Date)](https://www.star-history.com/#hosizoraru/KeiOS&Date)
