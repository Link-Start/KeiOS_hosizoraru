# One implementation for an asset row's four actions

Every surface that shows a release file draws the same row: ⓘ, 📦, ⬇ and share. Until 2026-09-23 the
row was shared and what its buttons did was not. Each surface wired the four callbacks itself, so the
same button did different things one page apart. [#29](https://github.com/hosizoraru/KeiOS/issues/29)
reported the first one noticed: share on the release list opened the system share sheet even with
分享到安装器 set to InstallerX Revived, while the same button on the tracked card sent the link straight
to it.

The owner's direction was that a capability is wired once and reached from every card, not written
again by each place that shows one. This records what was found, what now owns each action, and how
it was verified. It landed as three commits: `6523b0459` (share and download), `de039d941` (APK info
and managed install lifted out of the GitHub page) and `f38f59622` (both on the history pages).

## What each surface did

Share and download, as surveyed on master @ `7e0132bbd`:

| surface | share | download |
|---|---|---|
| tracked card asset panel, APK info sheet (`GitHubAssetTransferActions`) | follows 分享到安装器 | follows 下载器 |
| Actions artifact card and detail sheet (`GitHubActionsArtifactActions`) | own copy, follows it | own copy, follows it |
| share-import flow (`sendAssetToConfiguredChannel`) | own copy | own copy |
| release list (`GitHubReleaseListPage`) | own `ACTION_SEND` chooser, reads neither setting | `uriHandler.openUri`, reads neither |
| F-Droid version history (`FdroidVersionListPage`) | same, copied from the release list | same |

Info and install on the two history pages:

| | tracked card | release list | F-Droid history |
|---|---|---|---|
| ⓘ ("View APK info for X") | APK info sheet | release page in a browser | package page in a browser |
| 📦 ("Install or update X", only with KeiOS 接管安装) | install confirm sheet, then a managed install | APK link in a browser | APK link in a browser |

Commit `1dc312a63` had deferred in-app install on the release list deliberately: the managed-install
runner and its confirm registry needed `GitHubPageActionEnvironment`, the GitHub page's whole state
machine. So the history pages, which exist to go *back* through builds, could inspect and install
only the newest one, from the tracked card.

## What owns each action now

**Share and download: `GitHubAssetHandoff`** (`app/.../github/asset/`). One object decides three
things. A share goes straight to the chosen installer, with the Online channel extras InstallerX reads,
or to the system share sheet when none is chosen. A download goes to the chosen downloader, the
built-in DownloadManager or the system default, and falls back when the chosen app refuses. And an
asset leaves through its API asset link when the token strategy is the source, otherwise its browser
link. It returns a route (`Delivered`) or a failure message rather than toasting, so each caller keeps
its own messages (Actions says "share started"; share-import names the hand-off in its closing line).

| surface | enters through |
|---|---|
| tracked card, APK info sheet | `GitHubAssetTransferActions` |
| Actions artifacts | `GitHubActionsArtifactActions` (URL-level `share` / `openInDownloader`) |
| share-import | `sendAssetToConfiguredChannel` in `GitHubShareImportAssetSupport` |
| release list, F-Droid history | `rememberGitHubAssetHandoffActions` |

**Info and install: `GitHubApkInstallController`** (`app/.../github/install/`). It owns the manifest
inspection, the APK info sheet request, the 📦 rule (KeiOS installs the file itself when 接管安装 is on
and the file is a verified install asset; otherwise it goes to the downloader, as ⬇ does), the confirm
request, its notification and registry, and launching the install. `GitHubManagedInstallRunner` is the
old page runner with the page environment taken out.

A surface brings two things. One is a `GitHubApkInstallState`: what it has inspected, and which sheet is
open. The GitHub page keeps its in `GitHubPageState`, so every existing reader is unchanged; each
history page keeps its in its view model, so a rotation keeps both. The other is a `GitHubApkInstallHost`:
how it speaks, and what a finished install changes on it.

| surface | host | after a successful install |
|---|---|---|
| GitHub page | `GitHubPageApkInstallHost` | the card's local version, the app list, focus on the card |
| release list, F-Droid history | `rememberGitHubApkInstallController` | the page's "Installed" mark moves (`markInstalled`) |

The two sheet bindings take the controller, so the GitHub page and the history pages render the same
`GitHubApkInfoSheet` and `GitHubManagedInstallConfirmSheet`. The GitHub page still derives their UI
state in its view model. `GitHubApkInstallSheetHost` derives it on a history page with the same
`derive…` functions.

### Decisions that change behaviour

- **An install outlives the page that confirmed it.** The owner chose this (2026-09-23, "the real flow
  on the page"). Installs run in `GitHubManagedInstallScope`, a process-lifetime scope on Main, rather
  than in the page's composition scope. That now includes installs started on the GitHub page. The
  notification's cancel action is still how an install is stopped.
- **"Being installed" is one fact for the whole app.** `managedInstallLoading` reads
  `GitHubManagedInstallRuns`, so an install confirmed on the release list shows as running on the
  tracked card for the same file, and on the release list again if it is reopened.
- **The tracked card learns about a history-page install on its own.** The GitHub page's host is not
  involved. When the page is active again, `syncLocalAppStateOnPageActive` reads the installed version
  code and refreshes any card whose version moved. The page does not collect package events while
  inactive, and that stream does not replay.
- **A confirm survives a configuration change.** A new controller that finds a confirm open in its
  state takes the notification registration over, so the notification's confirm button keeps working
  through a rotation.

## Guards

- `GitHubAssetHandoffTest` (Robolectric, 11): the intents actually sent. That covers the chosen
  installer and its extras, the chooser, https only, each downloader route and its fallback, share-import's
  preference order and `FLAG_ACTIVITY_NEW_TASK`, and which results carry a message.
- `GitHubAssetHandoffSourceTest`: no GitHub UI file builds a share intent except the hand-off and the
  release-notes translation fallback. Run against `7e0132bbd`, its pattern flags exactly the five files
  that had their own share code.
- `GitHubApkInstallControllerTest` (Robolectric, 7, fake manifest cache and fake installer, no network):
  - ⓘ opens and inspects; 📦 confirms or falls back.
  - A confirmed install runs once and reports what it installed.
  - A dismissed confirm cannot be confirmed later.
  - A running install shows on a second surface.
  - An install finishes after the page that confirmed it is disposed. This one was checked to fail
    when the install is moved back into the page scope.

## Verified on the AVD

Android 17 AVD `KeiOS_API37_Validation`, debug build of this work, InstallerX Revived 26.09.8ede272 as
the share target. Shizuku is not active there, so a managed install stops at the confirm sheet by design.

| check | observed |
|---|---|
| release list share (NekoBox 1.4.2) | `START act=SEND pkg=com.rosan.installer.x.revived`; InstallerX read moe.nb4a 1.4.2 (230) |
| F-Droid history share (Syncthing-Fork 2010500) | the same start, into InstallerActivity |
| release list ⓘ | APK info sheet over the page: moe.nb4a 1.4.2 / 230, trust check; no activity started |
| release list 📦 (接管安装 on) | "Confirm install" over the page: trusted, ABI match, local vs incoming |
| F-Droid history ⓘ, then the sheet's 📦 | APK info (2.1.5.0 / 2010500, min 23, target 36), then "Confirm install" for 2.1.3.0 → 2.1.5.0 |
| F-Droid history row 📦 | "Confirm install" |
| tracked card ⓘ and 📦 | APK info sheet and "Confirm install" on the GitHub page, as before |

接管安装 was switched on for the 📦 checks and back off afterwards.

## Found while verifying

**IzzyOnDroid APK links drop the repository path.** Widgets Anywhere's build 217 is stored as
`https://apt.izzysoft.de/tk.zwander.lockscreenwidgets_217.apk`. That answers 404; the real file is under
`/fdroid/repo/`. `resolveFdroidApkDownloadUrl` resolves an index-v2 file name, which starts with `/`,
as host-absolute. This predates this work and affects the tracked card and the F-Droid history alike.
Fixed on its own, with what each F-Droid source really publishes: see
[fdroid-apk-paths.md](fdroid-apk-paths.md).

**A deleted track that was not the tooling.** During the tracked-card check, Syncthing-Fork was deleted
at 03:32:28.690 (history source "Page"). The only adb input in that window was one tap at 03:32:25.8 on
a share button. A delete needs the menu's Delete and then a confirm, and the emulator log shows clicks
in its window that adb did not send, so the AVD was also being used by hand. The track was re-added
later through the add sheet with the same id.

**The AVD's DNS is fixed when it boots.** Midway through, every hostname stopped resolving inside the
emulator while `ping 8.8.8.8` still answered. The emulator's Wi-Fi comes from `netsimd`, launched as
`netsimd --host-dns=<the Mac's DNS at launch>` (after the restart, `--host-dns=198.18.0.2`), and it
does not follow later changes. Tailscale had been turned off during the session. An earlier lookup
had also returned a poisoned address for f-droid.org (`…:face:b00c:…`), so that session was not
resolving through the proxy at all. A cold restart fixed it. After toggling Tailscale, a VPN or the
proxy, cold-restart the AVD before trusting any network result.

## Not done

- The share-import flow keeps its own managed-install machinery (`GitHubShareImportManagedInstallCoordinator`
  and the `GitHubManagedInstall*Actions` in `share/`). It stages and commits from a sheet or a
  notification for an app that is not tracked yet, which is a different flow, not a copy of this one.
- #29's other question, the bar at the bottom of the release list (first page, previous, refresh, next,
  plus a tag filter in Atom mode), is unchanged and unanswered on the issue.
