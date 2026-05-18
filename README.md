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
| Latest stable tag | `v1.6.0`                                             |

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

## v1.6.0 Highlights

- GitHub tracking treats direct APK, JSON feed, companion JSON, versioned directory, and APK
  directory-index sources as subscription projects, with remote health, stable/prerelease channels,
  release notes, install actions, and Shizuku-backed managed install handoff.
- GitHub page state keeps remembered sort/filter/order settings, visible-list batch refresh from the
  floating dock, full-refresh shortcuts, edit/settings unsaved-change confirmation, and independent
  Actions update intervals including 2h / 3h choices.
- MCP is organized around entrypoint, workflow, and advanced tools, with typed catalog metadata, JSON
  schemas, structured outputs, workflow blueprints, resource/help registries, a redesigned MCP Skill
  page, and lighter runtime-log/session monitoring.
- OS TopInfo now presents device, build, CPU, runtime, memory, storage, locale, developer-state, and
  verified-boot signals as readable summaries, while raw properties stay available in the Android
  Properties tables.
- BA Student Guide adapts NPC and satellite entries with leaner profile labels, related-role
  grouping, broader gallery parsing, media export, favorite-data migration, and older GameKee page
  compatibility. Catalog filters now cover implemented-student attributes, school filtering for
  NPC/satellite entries, and tighter dropdown sizing for sort/filter menus.
- Settings can switch between the existing bottom bar / pager path and the MIUIX iOS-like bottom bar
  / pager path, with shared sizing fixes for different display densities.
- App icon selection keeps Android Designs as the default icon set and offers Apple Designs as the
  refreshed alternate set.
- Release hardening covered bottom-bar visibility recovery, benchmark package startup, Gradle
  configuration-cache hygiene, Ktor 3.5.0, broader Baseline Profiles, test coverage, R8 keep-rule
  review, and release/benchmark mapping verification.

Read the full feature tour:

- [Feature Overview (EN)](readme/FEATURES.md)
- [功能完整介绍 (CN)](readme/FEATURES_CN.md)

## Current Distribution

- Stable APKs are published through [GitHub Releases](https://github.com/hosizoraru/KeiOS/releases).
- Current stable tag: [v1.6.0](https://github.com/hosizoraru/KeiOS/releases/tag/v1.6.0).
- Release package baseline: `os.kei`, `arm64-v8a`, Android 15+ (`minSdk 35`).
- Runtime and build baseline: `targetSdk=37`, Java 21, Gradle Wrapper `9.5.1`, Kotlin `2.3.21`,
  Compose `1.11.1`, Android Gradle Plugin `9.2.1`, Ktor `3.5.0`.
- App language resources currently cover Simplified Chinese, English, and Japanese.

## Documentation

- [Documentation Index](readme/INDEX.md)
- [Release Notes v1.6.0](readme/RELEASE_V1.6.0.md)
- [Build Guide (EN)](readme/BUILD.md)
- [构建指南 (CN)](readme/BUILD_CN.md)
- [Todo List (EN)](readme/TODO.md)
- [待办清单 (CN)](readme/TODO_CN.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=hosizoraru/KeiOS&type=Date)](https://www.star-history.com/#hosizoraru/KeiOS&Date)
