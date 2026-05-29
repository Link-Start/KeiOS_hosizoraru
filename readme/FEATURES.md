# KeiOS Feature Overview

[中文版本 (CN)](FEATURES_CN.md)

KeiOS is built as a daily Android utility console. The app brings together system inspection, MCP
service management, GitHub Releases / Actions tracking, subscription-project tracking, GitHub Star
import,
repository discovery, Blue Archive office reminders, JSON data migration, local issue feedback, and
a Student Guide media browser in one phone-first interface.

## Home

Home is the status hub. It uses compact status pills for Shizuku, MCP, GitHub, and BA state, then
keeps focused MCP, GitHub, and BA cards below the hero. Users can adjust bottom-page visibility and
Home summary-card visibility from the top action area.

## OS

The OS page focuses on device and system inspection:

- TopInfo and key-value sections for System, Secure, Global, Android properties, Java properties, and Linux environment.
- TopInfo highlights readable device, build, CPU, runtime, memory, storage, locale, developer-state,
  and verified-boot summaries before the raw property tables.
- Search across OS parameters and activity entries.
- Configurable activity shortcut cards, including built-in AOSP/Google system entries for hidden
  system settings.
- Searchable, grouped activity-card and shell-card sheets with separate import/export flows,
  preview, and merge handling.
- Background shortcut action execution for refresh and page-entry flows.
- Shizuku-powered shell runner with command history, formatted output, timeout controls, dangerous-command confirmation, and save-to-card support.
- Cached system snapshots for faster return visits.
- v2 liquid floating dock controls for add, refresh, search, and page actions.

## MCP

The MCP page manages a local KeiOS MCP server:

- Start/stop controls, local-only or LAN-oriented connection settings, port/path/token display, and config copy.
- Productized tool sections for entrypoint, workflow, and advanced tools, with search and grouped
  cards for users who want a smaller starting surface.
- Claw Skill quick setup prompt, localized SKILL.md generation, workflow blueprints, and tool-level
  help resources.
- 45 MCP tools for Home overview, OS cards, system TopInfo, GitHub tracking/share import/discovery,
  Star List import, package scans, reverse repository scans, direct subscription inspection, and
  Blue Archive cache inspection.
- Typed catalog metadata, JSON schemas, tool annotations, structured outputs, and resource/prompt
  registries for MCP clients that support the newer protocol surface.
- Adaptive runtime-log and session monitoring to keep long-running MCP service sessions lighter.
- Foreground keep-alive service, test notifications, and semantic icon bitmap support for
  notification builders.
- HyperOS Super Island template support and AOSP Live Update fallback settings through the notification compatibility controls.

## GitHub

The GitHub page tracks APK updates from GitHub projects and subscription projects:

- Stable and prerelease update checks for tracked repositories, plus remote-version checks for
  direct APK links, JSON feeds, companion JSON files, versioned directories, and APK directory
  indexes.
- GitHub API strategy configuration with optional token support shared by Releases and Actions.
- Release asset reading, APK download routing, app-managed install routing, and latest-release
  download actions.
- GitHub Actions browser for branches, workflows, runs, and artifacts, with nightly.link public lookup and token-backed GitHub API lookup.
- Actions recommended-run update checks, app-icon notifications, debug notification testing, and
  notification deep links into the tracked project's Actions sheet.
- Branch recommendation that considers the default branch, recent activity, successful runs, and artifact availability.
- Artifact ranking that highlights Android packages, build types, universal packages, recency, and previous download history.
- Tracked-item editing with app package linkage, installed-app matching, package-name scanning from
  latest stable release APKs, reverse repository scanning from package name plus app label, source
  mode switching, subscription import/export compatibility, and unsaved-change confirmation.
- Deep repository profiles, health scoring, archived/fork signals, release-note parsing,
  release-note translation, precise APK version modes, and runtime cache freshness checks.
- Subscription-project cards with remote health, remote stable/prerelease releases, Scene-style
  index release notes, installed-app labels, release-note actions, and install actions when the
  tracked package is missing.
- Share-import flow for repository, release, tag, and direct APK links with transparent window
  handling, notification-first/sheet-first routing, external installer handoff, and app-managed
  Shizuku delivery.
- Managed install surfaces with remote/local APK comparison, manifest inspection, ABI/SDK/package
  hints, versionName/versionCode display, install confirmation notifications, and Shizuku
  PackageInstaller session handling.
- Star List import from authenticated stars, public user stars, and public Star List URLs, with list
  discovery, search, multi-select filters, Android/APK quality classification, release APK
  verification, and import confirmation.
- Strategy diagnostics that compare Atom and API behavior for release checks, package-name scans,
  and reverse repository scans.
- Remembered sort/order/filter preferences, filtered-list refresh from the floating dock, full
  refresh from shortcuts, refresh notifications, local cache summaries, tracked item
  focus/auto-scroll, and self-track shortcut for KeiOS.
- Independent Actions update intervals with Follow global / 1h / 2h / 3h / longer options.

## Import, Feedback, And Migration

- JSON import activity for KeiOS data migration with preview, metric tiles, result navigation, and
  multi-schema routing for OS card transfer data.
- Local GitHub issue assistant with structured log levels, issue markdown generation, and
  telemetry-free diagnostics.
- Shared import/export services for OS cards, shell cards, GitHub tracks, and student-guide data.

## BA Office

The BA page acts as a Blue Archive office dashboard:

- AP and cafe AP tracking with server-aware timing.
- AP threshold notifications, cafe visit reminders, arena refresh reminders, and BA-specific Super
  Island presentation using progress and countdown templates.
- Shortcut-triggered AP and cafe AP Super Island notifications.
- Server-specific nickname and friend-code ID cards, plus friend code copy and office overview
  cards.
- Server, cafe level, AP threshold, media rotation, and custom media save-location settings.
- Server-aware calendar and pool cards with in-title server context, compact time layout,
  notification settings, and student-guide entry points.

## Student Guide

The student guide expands the BA workflow into catalog and media browsing:

- Catalog tabs for students and related entries, with search, sort, compact localized labels, sync
  status, local caching, implemented-student filters, and school filters for NPC/satellite entries.
- Student and related-entry detail pages with profile, strategy/simulation sections, NPC/satellite
  label grouping, gallery media, localized voice-language labels, audio/video content, and source
  sharing.
- Gift preference parsing with image and attitude markers.
- BGM favorites library with playback queue, liquid bottom dock, mini player, batch cache, retry,
  import/export, and jump back into the student guide.
- Media cache controls and export flows, including archive-style saves for expression/media packs.
- NPC and satellite entries adapt older GameKee pages with broader gallery parsing, related-role
  classification, leaner information rows, and compatibility for pages with fewer implemented-student
  fields.

## Settings And Compatibility

Settings collect the runtime controls in one place:

- Theme mode, transition animations, predictive back, search focus behavior, preloading, app
  language shortcut, and Home HDR highlight.
- v2 liquid-glass ActionBar, title cards, search fields, floating docks, bottom bar, bottom-bar
  full-effect policy during scrolling, and scoped card press feedback.
- Toggleable main navigation style between the existing bottom bar / pager chain and the MIUIX
  iOS-like bottom bar / pager chain.
- Icon design selector with Android Designs as the default set and Apple Designs as the refreshed
  alternate set.
- Custom non-Home background image and opacity controls.
- Notification permission, battery optimization, OEM autostart, app-list access, and Shizuku status.
- Super Island notification style, HyperOS compatibility bypass, and restore-delay tuning.
- Copy/text-selection mode, cache diagnostics, debug logs, exportable log ZIPs, and clear-cache actions.
- Local GitHub issue feedback and structured log-level controls.
- Debug component lab and liquid catalog for checking shared chrome, buttons, dropdowns, sliders,
  progress bars, and dock behavior.
- Simplified Chinese, English, and Japanese resources, with BA terms, Android settings, GitHub, shell, and MCP skill text localized for display surfaces.

## Platform Baseline

- Package: `os.kei`.
- ABI: `arm64-v8a`.
- Android baseline: Android 15+ (`minSdk 35`), `targetSdk=37`.
- UI stack: Jetpack Compose `1.11.2`, Miuix KMP, Lifecycle ViewModel Compose, custom v2 liquid-glass
  chrome, MMKV-backed preferences.
- Build baseline: Java 21, Gradle Wrapper `9.5.1`, Kotlin `2.3.21`, Android Gradle Plugin `9.2.1`,
  generated Baseline Profiles, and Gradle project tooling.
