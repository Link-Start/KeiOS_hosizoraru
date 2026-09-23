# F-Droid APK links, read against the repository

On 2026-09-23, Widgets Anywhere (`tk.zwander.lockscreenwidgets`, tracked from IzzyOnDroid) offered its
build 217 as `https://apt.izzysoft.de/tk.zwander.lockscreenwidgets_217.apk`. That address answers 404.
The file is at `https://apt.izzysoft.de/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk`. The APK info
sheet and the install confirm sheet both reported "byte-range request failed (HTTP 404)". Download,
share and install got the same link, because every action uses the asset's URL.

Both F-Droid surfaces build their assets through one function, `fdroidVersionAssetFile`: the tracked
card's asset panel and the version history. That function calls `resolveFdroidApkDownloadUrl`, which
computed `URI("$repoUrl/").resolve(path)`. So a name that starts with `/` was resolved against the host's
root, and the repository's own path was dropped. This records what each source really publishes, the
rule that replaced it, and a stored copy of the old link that a refresh would not have cleared.

## What each source publishes

Read from the live repositories on 2026-09-23:

| source | field | shape | seen |
|---|---|---|---|
| `index-v2`, f-droid.org (full index, 60.8 MB) | `file.name` | `/org.fdroid.fdroid_2000041.apk` | 13,595 of 13,595 builds |
| `index-v2`, IzzyOnDroid (full index, 14 MB) | `file.name` | `/tk.zwander.lockscreenwidgets_217.apk` | 2,944 of 2,944 |
| `index-v2`, f-droid.org archive (diff `1790061772041`) | `file.name` | `/ac.mdiq.Podcini.A_122.apk` | 85 of 85 |
| `index-v2`, Guardian Project (full index) | `file.name` | `/Checkey-0.1.1.apk` | 40 of 40 |
| `index-v1`, IzzyOnDroid (last 3 MB, which holds the package list) | `apkName` | `tk.zwander.lockscreenwidgets_217.apk` | 2,944, all bare |
| package API, f-droid.org and IzzyOnDroid | none | version name and code only | every build listed |
| package page, `f-droid.org/en/packages/org.fdroid.fdroid/` | download `href` | `https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk` | all 8 builds on the page |

Every index-v2 name is one file name with a slash in front. None starts with the repository's path, so
the `/repo/org.fdroid.fdroid_102.apk` shape in this project's tests matches nothing a repository
publishes. KeiOS reads no `index-v1` at all. The bare shape still arrives through the `apkName`
fallback.

A one-byte range request against each preset:

| repository | inside the repository | at the host's root |
|---|---|---|
| IzzyOnDroid | `…/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk` → 206 | `apt.izzysoft.de/tk.zwander…_217.apk` → 404 |
| f-droid.org | `…/repo/ac.mdiq.Podcini.A_126.apk` → 206 | `f-droid.org/ac.mdiq.Podcini.A_126.apk` → 404 |
| f-droid.org archive | `…/archive/ac.mdiq.Podcini.A_122.apk` → 206 | `f-droid.org/ac.mdiq.Podcini.A_122.apk` → 404 |
| Guardian Project | `…/fdroid/repo/Checkey-0.1.1.apk` → 206 | `guardianproject.info/Checkey-0.1.1.apk` → 404 |

Both ends of F-Droid say the same. fdroidserver writes the name as the file's own name with a slash in
front: `ver["file"]["name"] = f'/{version["file"]["name"]}'` in `index.py`. The client appends it to the
repository address: `URLBuilder(url).appendPathSegments(path.trimStart('/'))` in `Mirror.getUrl`.

### Who was affected

Every build KeiOS read from an index, in any repository whose address has a path. IzzyOnDroid publishes
no package page this app reads, so both its refresh and its history come from the index, and every
IzzyOnDroid track was broken on both surfaces. Guardian Project was the same. f-droid.org tracks were
fine in practice. They are read from the package page, whose links are absolute, and the index that
would have broken them is over the 16 MB budget both readers apply.

## The rule now

`resolveFdroidApkDownloadUrl` in `app/.../github/asset/GitHubFdroidInstallAssets.kt`:

- An `http` or `https` URL is used unchanged. This is the package page's shape.
- `//host/file.apk` names its own host and keeps the repository's scheme, which is what it means in HTML.
- Anything else is a path inside the repository, whether or not it starts with a slash. This is what
  F-Droid's client does.

One rule was considered and not adopted: treat a leading slash as inside the repository unless the name
already starts with the repository's own path. For every name observed it gives the same URLs as the
rule above. It differs only for a name like `/repo/x.apk`, which no repository publishes. F-Droid's
client resolves that name inside the repository too, so a repository that published it would already
be broken in F-Droid. The exception could only be right about a shape that does not exist, and wrong
about a real subfolder that happened to share the repository's name.

## The stored copy of the old link

The tracked card derives its F-Droid bundle from the local sidecar on every visit. It still reads a
bundle saved earlier in MMKV `github_release_asset_cache`, and prefers that one whenever its tag and
source signature match. The sidecar keeps the raw name (`/tk.zwander.lockscreenwidgets_217.apk`), so
fixing the resolver fixes every fresh derivation. The saved bundle, though, holds the resolved link.

A refresh does not clear it. Replaying the store on the AVD, the entry for Widgets Anywhere's 4.7.0
(`entry_129b27bf…`) was written once with the host-root link before the fix. After the track refresh
it had no new writes at all: no removal and no rewrite. The entry expires only after the global refresh
interval (1, 3, 6 or 12 hours, 3 by default), counted from when it was saved. Without the check below,
an updated app would have kept offering the dead link until then, refresh or not.

So the saved bundle now has to link the same files as the one just derived (`linksSameFilesAs`). If
it does not, it is cleared and the fresh one is saved in its place. The history page needs none of
this: it builds its links from the raw names every time.

## Verification

`GitHubFdroidInstallAssetsTest` went from 3 tests to 11. The repositories, names and answers in the
per-source cases come from the live repositories; the `example` hosts and build 102 are made up. The
tests cover:

- each index-v2 preset's name, a bare `apkName`, the page's absolute link, `//host`, and a repository
  address with a trailing slash;
- a name that repeats the repository's path, pinned to the decision above;
- the three sources end to end. IzzyOnDroid's index entry goes through `FdroidIndexV2Parser`, the page's
  version block through `FdroidPackagePageParser`, and IzzyOnDroid's package-API answer, served verbatim
  by `MockWebServer`, through `FdroidPackageApiClient`. Each result then goes into an asset. The API's
  builds get no asset, which is the contract for a build with nowhere to download from;
- a saved bundle holding the host-root link does not stand in for the fresh one.

Mutations: putting the host-root reading back fails 6 of the 11 tests. Dropping the `//host` branch
fails the one case that covers it. The full app unit suite passes: 1,775 tests, 0 failures.

On the Android 17 AVD `KeiOS_API37_Validation`, with build `1.15.1+68.g060d00c4a` plus this change
installed over the existing data:

| step | result |
|---|---|
| before installing: asset cache and sidecar | cache holds `https://apt.izzysoft.de/tk.zwander.lockscreenwidgets_217.apk`; sidecar holds `/tk.zwander.lockscreenwidgets_217.apk` |
| tracked card, open the asset panel, no refresh | the saved entry is removed and rewritten with `…/fdroid/repo/…_217.apk` |
| tracked card ⓘ | APK info reads the manifest: 4.7.0 / 217, min 24 · target 37, SHA-256 `efc9938e…590d`, the same as the index lists |
| version history, build 217 ⓘ, then the sheet's refresh | "Reading AndroidManifest.xml", then the same manifest. The manifest cache holds entries for the repository link only |
| refresh the track, then the tracked card ⓘ again | the same manifest; the saved entry is untouched by the refresh |

## Not changed

- The feature-github parser and cache tests still use `/repo/<name>.apk` as index-v2 names: in
  `FdroidIndexV2ParserTest`, `FdroidIndexV2StreamParserTest`, `FdroidRepositoryIndexClientTest`,
  `FdroidMetadataSidecarJsonTest`, `FdroidBatchPackageSnapshotProviderTest` and
  `FdroidReleaseCheckSourceTest`. They test parsing and pass-through, not resolution, so they still hold.
  They are also why host-absolute looked right: that is the shape they show.
- The APK info sheet shows "Source: Atom" for an F-Droid build. The manifest reader marks every download
  that does not go through the GitHub API as `html`, and the sheet labels `html` as Atom. That label is
  about the manifest reader, not about links.
