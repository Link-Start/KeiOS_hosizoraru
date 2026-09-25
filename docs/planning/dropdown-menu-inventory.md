# Dropdown and Dropdown Menu Inventory

> Baseline: `fd27396e4` on 2026-07-29.
>
> Purpose: keep a source-backed inventory of every dropdown selector and anchored
> dropdown menu before the next component and interaction pass.

## Scope and terminology

This document separates the two user-facing patterns that currently share the
same popup and Liquid Glass rendering infrastructure:

- **Dropdown selector**: an anchor displays the current value and opens a
  single-choice list. Production call sites use `AppDropdownSelector`.
- **Dropdown menu / action menu**: an icon or action-bar anchor opens commands,
  quick actions, filters, or nested single-/multiple-choice submenus. Production
  call sites use `SnapshotWindowListPopup` with `LiquidGlassActionMenu`.
- **Component-lab proof**: debug-only examples used to exercise the shared
  component surface. They remain part of the migration checklist because they
  are the fastest place to validate styling and interaction changes.

Pull-to-refresh copy containing “下拉” and non-menu popup state names are outside
this inventory.

## Scan result

| Surface | Production source call sites | Concrete controls / menus | Debug proofs |
| --- | ---: | ---: | ---: |
| `AppDropdownSelector` | 42 | At least 43 | 1 reusable stress-matrix call site |
| Anchored `LiquidGlassActionMenu` | 8 | 8 | 1 |
| Raw Liquid Glass dropdown/popup proof | 0 | 0 | 1 |

The concrete selector count is higher than the source-call count in dynamic
lists. The OS Shell wrapper has one `AppDropdownSelector` call and two concrete
settings. Intent-extra types, skill levels, and media variants can also create
multiple selector instances at runtime.

The scan found no direct production use of:

- Material or Material3 `DropdownMenu`, `DropdownMenuItem`, or
  `ExposedDropdownMenu`.
- MIUIX dropdown components outside the project-owned adapter chain.
- XML or View-system `Spinner`, `AutoCompleteTextView`, or `PopupMenu`.
- Raw Compose `Popup` anywhere. Since 2bcc2063a (2026-08-09) the dropdown and
  action menu are hosted in-window through `LiquidOverlayPortal`, and the
  unused `LiquidBackdropWindowPopup`/`LiquidBackdropWindowDialog` wrappers were
  removed on 2026-09-25. `PlatformWindowSourceContractTest` bans platform
  windows repo-wide.

## Shared component chain

```text
Dropdown selector
AppDropdownSelector
├── AppDropdownAnchorButton
├── capturePopupAnchor -> IntRect
├── SnapshotWindowListPopup
└── AppLiquidGlassDropdownColumn
    └── LiquidGlassDropdownSingleChoiceList
        └── LiquidGlassDropdownSingleChoiceItem

Dropdown action menu
anchor / LiquidActionBar slot
├── capturePopupAnchor or LiquidActionBarPopupAnchors
├── SnapshotWindowListPopup
└── LiquidGlassActionMenu
    ├── quick actions
    ├── action / info rows
    └── nested single- or multiple-choice submenu rows
        └── AppLiquidGlassDropdownColumn
```

### Component ownership

| Layer | File | Responsibility |
| --- | --- | --- |
| Selector API | `ui-liquid-glass/.../glass/AppDropdownControls.kt` | Anchor rendering, measured popup width, enabled/empty behavior, selected-item initial scroll, dismissal, and single-choice wiring. |
| Dropdown container | `ui-liquid-glass/.../glass/LiquidGlassDropdown.kt` | Liquid Glass material, width/height limits, scroll container, backdrop handling, and Default/ActionMenu metrics. |
| Dropdown rows | `ui-liquid-glass/.../glass/LiquidGlassDropdownItems.kt` | Action, info, single-choice, and multiple-choice rows; semantics; selection state; text/icon layout. |
| Action-menu model | `ui-liquid-glass/.../glass/LiquidGlassActionMenu.kt` | Quick actions, command rows, nested submenu state, submenu back handling, and menu dismissal policy. |
| Popup adapter | `ui-liquid-glass/.../sheet/MiuixSnapshotAdapters.kt` | Window-safe placement, reveal/exit animation, focus, back interception, and delayed removal after exit. |
| In-window host | `ui-liquid-glass/.../glass/LiquidOverlayHost.kt` (`LiquidOverlayPortal`) | Hosts dropdown and menu presentations inside the page window, where Liquid Glass can sample the backdrop. |

## Production dropdown selectors

### GitHub

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-GH-01 | `GitHubTrackEditFormContent.kt:301` | Tracking source mode | First conditional layout branch; shares state with DD-GH-02. |
| DD-GH-02 | `GitHubTrackEditFormContent.kt:354` | Tracking source mode | Second conditional layout branch; preserve parity with DD-GH-01. |
| DD-GH-03 | `GitHubTrackEditFormContent.kt:526` | Update interval | Sheet action selector. |
| DD-GH-04 | `GitHubTrackEditFormContent.kt:547` | Ignore-update mode | Sheet action selector. |
| DD-GH-05 | `GitHubTrackEditFormContent.kt:633` | GitHub Actions update interval | Conditionally visible with Actions update checks. |
| DD-GH-06 | `GitHubTrackEditFormContent.kt:666` | Precise APK-version mode | Conditionally visible. |
| DD-GH-07 | `GitHubTrackEditFdroidDiscoverySection.kt:136` | F-Droid repository scope | Long localized options require width and wrapping checks. |
| DD-GH-08 | `GitHubTrackEditFdroidOptions.kt:77` | F-Droid version selection | Controls downstream version/regex fields. |
| DD-GH-09 | `GitHubTrackEditFdroidOptions.kt:127` | F-Droid trust policy | Security-sensitive choice copy. |
| DD-GH-10 | `GitHubTrackEditFdroidOptions.kt:149` | F-Droid Anti-Feature policy | Security-sensitive choice copy. |
| DD-GH-11 | `GitHubCheckLogicSections.kt:288` | Share-import flow mode | Controlled by GitHub page state/actions. |
| DD-GH-12 | `GitHubCheckLogicSections.kt:344` | Online share target / installer | Options depend on available targets. |
| DD-GH-13 | `GitHubCheckLogicSections.kt:383` | Downloader | Options depend on available downloader apps. |
| DD-GH-14 | `GitHubTrackAppPickerContent.kt:119` | Installed-app sort field | Paired horizontally with DD-GH-15. |
| DD-GH-15 | `GitHubTrackAppPickerContent.kt:145` | Installed-app sort direction | Paired horizontally with DD-GH-14. |
| DD-GH-16 | `GitHubDecisionAssistDetailSheets.kt:299` | Release selection | Release-notes decision-assist Sheet; option count can grow. |

### Settings

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-ST-01 | `SettingsVisualSection.kt:83` | Theme mode | Expanded state lives in `SettingsPageViewModel`. |
| DD-ST-02 | `SettingsVisualSection.kt:106` | Launcher icon design | Expanded state lives in `SettingsPageViewModel`; selection can trigger launcher-component changes. |
| DD-ST-03 | `SettingsLogSection.kt:88` | Log level | Uses a fixed maximum width and matches the anchor width. |
| DD-ST-04 | `SettingsNotifySection.kt:103` | Super Island floating behavior | Disabled/collapsed when the parent feature is off. |
| DD-ST-05 | `SettingsPermissionKeepAliveSection.kt:306` | Privilege mode | Security-sensitive Disabled/Shizuku/Root selection. |
| DD-ST-06 | `SettingsBackgroundSection.kt:258` | Non-Home background content scale | Enabled only with non-Home background. |
| DD-ST-07 | `SettingsBackgroundSection.kt:284` | Non-Home background alignment | Enabled only with non-Home background. |
| DD-ST-08 | `SettingsBackgroundSection.kt:310` | Non-Home background page style | Enabled only with non-Home background. |

### BA account, calendar, pool, and Home cards

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-BA-01 | `BaAccountManagementSheet.kt:329` | Account server | Changing it also normalizes the friend code. |
| DD-BA-02 | `BaAccountManagementSheet.kt:370` | Account notification mode | Hidden when all accounts follow global settings. |
| DD-BA-03 | `BaCalendarPoolDataSettingsSheet.kt:132` | Calendar/pool refresh interval | Fixed 128 dp anchor. |
| DD-BA-04 | `BaCalendarPoolServerPanel.kt:61` | Calendar/pool server | The server card is a fixed visual anchor; preserve its role. |
| DD-BA-05 | `BaNotificationSettingsSheet.kt:426` | Notification lead time | Fixed 128 dp anchor. |
| DD-BA-06 | `BaCafeCard.kt:152` | Cafe level | Compact content-card selector with `Lv` labels. |

### Student guide, gallery, and BGM

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-SG-01 | `BaGuideBgmLibraryControls.kt:335` | BGM sort mode | Compact half-width control paired with DD-SG-02. |
| DD-SG-02 | `BaGuideBgmLibraryControls.kt:356` | BGM group mode | Compact half-width control paired with DD-SG-01. |
| DD-SG-03 | `BaGuideMemoryLobbyCards.kt:719` | Memorial Lobby media variant | Reusable selector; only shown with multiple variants. |
| DD-SG-04 | `GuideSectionSkill.kt:290` | Skill level | Dynamic per skill/card; compact 30 dp visual height. |
| DD-SG-05 | `GuideSectionWeapon.kt:169` | Unique-weapon level | Compact selector inside a stat row. |
| DD-SG-06 | `GuideSectionWeaponSupport.kt:84` | Effect/support level | Shared `GuideEffectLevelPicker` entry point. |
| DD-SG-07 | `GuideGalleryExpressionSection.kt:282` | Expression/media variant | Supports capped popup height and potentially long lists. |
| DD-SG-08 | `GuideGalleryVideoGroupControls.kt:45` | Video-group variant | Compact gallery selector. |

### OS tools

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-OS-01 | `OsGoogleSystemServiceEditorSheet.kt:340` | Intent-extra value type | Dynamic per intent-extra row; multiple menus can exist in one Sheet. |
| DD-OS-02 | `OsShellRunnerSheets.kt:330` | Shell settings selector wrapper | One source call backs the timeout and output-limit controls below. |
| DD-OS-02A | `OsShellRunnerSheets.kt:64` | Command timeout | Concrete use of DD-OS-02. |
| DD-OS-02B | `OsShellRunnerSheets.kt:211` | Output character limit | Concrete use of DD-OS-02. |

### WebDAV

| ID | File and line | Control | Notes for the next pass |
| --- | --- | --- | --- |
| DD-WD-01 | `WebDavConnectionCards.kt:122` | WebDAV provider | Matches anchor width; fixed 220 dp popup cap. |
| DD-WD-02 | `WebDavSyncCards.kt:202` | Automatic-sync interval | Disabled while sync is unavailable or interaction is locked. |

## Production dropdown/action menus

| ID | File and line | Anchor and purpose | Contents |
| --- | --- | --- | --- |
| DM-GH-01 | `GitHubTopBarSection.kt:195` | GitHub top-bar More action | Export/import quick actions; Droid sources, debug, app refresh; nested sort field, sort direction, tracked filter, and refresh interval. |
| DM-GH-02 | `GitHubActionsNotificationHistoryActionBar.kt:208` | GitHub history action-bar More action | Mode-specific filter and sort submenus, sort direction, export, and cleanup-age submenu. |
| DM-GH-03 | `GitHubTrackedItemMoreMenu.kt:132` | Per-tracked-item More action | Refresh, Actions, F-Droid details, release notes, ignore version, and destructive delete. Contents vary by item state. |
| DM-SG-01 | `BaGuideBgmFavoriteCards.kt:313` | Favorite BGM/student card More action | Open gallery, cache, and destructive delete. |
| DM-SG-02 | `BaGuideCatalogFilterActionPopup.kt:38` | Student-catalog filter action | Clear filters plus a generated submenu per filter definition. |
| DM-SG-03 | `BaGuideCatalogMoreActionPopup.kt:52` | Student-catalog More action | Transfer; student type, sort, and incremental-refresh submenus; refresh-scope info; full refresh. |
| DM-SG-04 | `BaGuideMemoryLobbyCards.kt:512` | Memorial Lobby card More action | Favorite toggle, fullscreen, and open guide; entries vary with availability. |
| DM-SG-05 | `BaGuideBgmTrackRow.kt:177` | Per-track More action | Play, favorite, and offline quick actions plus open gallery. |

All eight menus use an explicit anchor `IntRect`, `SnapshotPopupPlacement`, and a
window-safe `SnapshotWindowListPopup`. DM-GH-01 and DM-GH-02 obtain anchors from
`LiquidActionBarPopupAnchors`; the remaining menus capture their icon/card
anchors directly.

## Debug and component-lab coverage

| ID | File and line | Purpose |
| --- | --- | --- |
| LAB-DD-01 | `DebugLiquidDropdownSelectorSamples.kt:176` | Production `AppDropdownSelector` stress matrix covering narrow/wide anchors, long labels, tall lists, and popup sizing. |
| LAB-DM-01 | `DebugLiquidActionMenuCard.kt:164` | Full anchored action-menu proof with quick actions and nested single-/multiple-choice submenus. |
| LAB-RAW-01 | `DebugLiquidCatalogCard.kt:324,370,405` | Raw `LiquidGlassDropdownColumn`, backdrop/non-backdrop variants, and an anchored `AppLiquidGlassDropdownColumn` popup. |

Raw `LiquidGlassDropdownColumn` and `AppLiquidGlassDropdownColumn` usage is
limited to the shared implementation, debug lab, and tests. Production pages
should continue entering through `AppDropdownSelector` or
`LiquidGlassActionMenu`.

## State, layout, and interaction observations

### 2026-07-29 visibility investigation

The previous popup material behaved like a Clear Glass treatment in situations
that require a Regular Glass treatment:

- Default and ActionMenu used only 4 dp and 6 dp of base blur.
- Active-backdrop surface alpha stayed between 0.34 and 0.52, so light popups
  disappeared over white cards and dark popups merged into dark cards.
- Light borders were white, which removed the last visible container boundary
  over light content.
- Popup elevation and shadow alpha were too weak to establish a controls layer.
- A selected Default row sampled the backdrop again through `LiquidSurface`.
  This created glass-on-glass, made the row look washed out, and weakened the
  relationship between the popup and its selected state.
- Unselected icon alpha reached 0.78 in light mode and supporting text reached
  0.62 in ActionMenu, both below the surrounding content hierarchy.

The revised shared material follows the Luminous Regular Glass model:

- Default uses 6 dp blur, a 26–42 dp lens, 22 dp elevation, depth refraction,
  and a 0.46 light / 0.46 dark active-backdrop surface.
- ActionMenu behaves as the thicker large-menu material: 8 dp blur, a 30–50 dp
  lens, 28 dp elevation, deeper shadows, and a 0.50 light / 0.50 dark
  active-backdrop surface.
- Both materials add a directional tint field and a separate diagonal caustic
  band. Their gradient coordinates resolve from the actual popup bounds, so
  the highlight-to-surface ratio remains stable across popup sizes and device
  densities.
- Primary and supporting labels use a subtle theme-opposed local halo. This
  keeps text legible over refracted detail without raising the opacity of the
  entire glass surface.
- Fallback rendering is 0.92–0.96 opaque because it has no backdrop blur to
  provide separation.
- Rows use tonal fills inside the popup's single glass layer. Selection uses a
  restrained accent fill and border; press feedback uses the existing neutral
  overlay.
- Primary text, icons, and supporting text use stronger theme-relative alpha in
  both appearance modes.

This direction follows Apple's guidance that Liquid Glass must remain visually
clear and dynamically preserve legibility, and that larger menu morphs should
look thicker, cast deeper shadows, and use stronger scattering. It also follows
the explicit guidance to avoid glass-on-glass:

- [Liquid Glass technology overview](https://developer.apple.com/documentation/technologyoverviews/liquid-glass)
- [Meet Liquid Glass, WWDC25 session 219](https://developer.apple.com/videos/play/wwdc2025/219/)

Backdrop's MCP documentation was used to refine the final optics. It identifies
`onDrawSurface` as the readability control, `lens()` as the primary refractive
character, `vibrancy()` as saturation enhancement, and `blur()` as background
detail smoothing. Effects remain ordered as color filter, blur, then lens.
`chromaticAberration` stays disabled for text menus, and `safeLiquidLens`
clamps refraction height and amount to the documented shape and size limits:

- [Backdrop effects](https://kyant.gitbook.io/backdrop/api/backdrop-effects)
- [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar)

AVD proof was captured on the single visible
`KeiOS_API37_Validation` instance at `emulator-5554`, 1280×2856 and
480 dpi. The matrix covered light/dark standalone dropdowns, anchored
ActionMenu, and the production `AppDropdownSelector` popup.

### 2026-07-30 production-page and iOS-reference acceptance

The shared material was evaluated in production call sites as well as the
component lab:

- DD-ST-01 theme mode in light and dark themes;
- DD-ST-05 privilege mode over a dense settings card;
- DM-GH-01 GitHub top-bar menu and its sort submenu;
- DM-GH-03 tracked-item menu with a destructive delete action.

Four iOS Liquid Glass references supplied by the user established the target
for compact and tall menus in both appearance modes. The important visual
characteristics were a neutral glass surface, broad blurred background fields,
fine edge definition, restrained specular light, crisp foreground labels, and
selection communicated through accent content with a clear surrounding field.

The resulting Soft-Field Regular Glass pass uses 12 dp / 16 dp blur for Default
and ActionMenu, retains 28–44 dp / 32–54 dp lens ranges, reduces synthetic
accent tint, and raises the neutral surface to 0.58 / 0.64 in light mode and
0.52 / 0.56 in dark mode. Persistent selected-row pills were removed; press
feedback remains tonal. The stronger blur turns duplicated background text
into broad soft shapes while the lens retains liquid edge behavior.

Production acceptance confirmed:

- light and dark menu labels remain legible over cards and empty page regions;
- the light menu now carries a visible near-white glass boundary and soft
  background shadows;
- the dark menu inherits the blue overview-card color field without exposing
  readable duplicate labels;
- destructive actions retain a clear red icon and label;
- outside-tap dismissal and selection dismissal remain functional.

One functional issue remains independently reproducible: pressing system Back
inside a nested ActionMenu submenu dismisses the entire popup. The shared
`LiquidGlassActionMenu` handler behaves correctly in its Compose test, while
the focusable platform Popup consumes Back at the window boundary before the
submenu handler. Fixing this requires a popup-window routing change; the menu
state and material layers already expose the expected behavior.

### Shared strengths

- `AppDropdownSelector` owns empty-options collapse, disabled anchor behavior,
  measured option width, optional anchor-width matching, popup height capping,
  selected-item initial scroll, and close-after-selection.
- `SnapshotWindowListPopup` owns safe-window placement, reveal direction,
  exit-before-removal, back interception, and system-inset clamping.
- Dropdown rows expose RadioButton/Checkbox/Button roles and selected/checked
  state according to item type.
- Action-menu submenus handle Back locally before the outer popup dismisses.
- Production pages already converge on one visual family and one popup adapter.

### Repeated caller responsibilities

Most selector call sites still own these four pieces independently:

1. `expanded: Boolean`
2. `anchorBounds: IntRect?`
3. index-to-domain mapping
4. index-to-localized-label mapping

This repetition is the main future refactor surface. A typed selector state/model
could reduce parallel-list and out-of-range risks while preserving the current
Route/Screen/leaf boundaries.

### Adaptive and accessibility watch points

- DD-GH-07 through DD-GH-10 contain longer security/repository labels.
- DD-GH-14/DD-GH-15 and DD-SG-01/DD-SG-02 share one horizontal row and need
  compact-width plus large-font validation.
- DD-SG-04 uses a 30 dp visual minimum; validate the effective press/touch
  target and TalkBack focus bounds separately from the painted anchor.
- DD-OS-01 is created per editable row, so stable state ownership and anchor
  cleanup matter when rows are inserted or removed.
- DM-GH-01 and DM-GH-02 contain deep nested choice lists and should be checked
  near every screen edge, with gesture navigation, display cutouts, and large
  font.
- DM-GH-03 and DM-SG-01 mix normal and destructive actions; retain clear danger
  semantics and dismissal behavior.
- Dynamic option lists must close an already-expanded popup when they become
  empty or disabled.

## Existing automated coverage

### Shared selector and row behavior

- `AppDropdownControlsTest`
  - empty options collapse and disable the anchor;
  - out-of-range selection remains usable;
  - selection closes the popup;
  - maximum height caps tall lists;
  - custom minimum width preserves compact geometry.
- `LiquidControlAccessibilityTest`
  - choice rows expose selected state and minimum touch targets.
- `LiquidGlassDropdownMaterialTest`
  - Default and ActionMenu optics, translucency, and action-menu geometry.

### Popup lifecycle and placement

- `SnapshotWindowListPopupLifecycleTest`
  - custom provider execution;
  - exit animation completes before removal;
  - Back during exit remains consumed by the popup.
- `SnapshotWindowListPopupPlacementTest`
  - safe system-inset bounds;
  - above/below/middle reveal selection;
  - safe-bound clamping;
  - stable ButtonEnd and ActionBarCenter horizontal origins.

### Action-menu behavior

- `LiquidGlassActionMenuTest`
  - single- and multiple-choice semantics;
  - keep-open versus dismissing actions;
  - submenu persistence and local Back;
  - quick-action semantics and disabled state;
  - passive info-row semantics;
  - large-font compact geometry;
  - submenu choice forwarding (it replaced a source-text contract test on 2026-09-23).
- `AppDesignSystemScreenshotTest.liquidGlassActionMenuLight`
  - light-theme action-menu visual regression.

### App-level source and interaction contracts

- Debug selector/action-menu source-contract tests.
- Student skill metadata selector test.
- Memorial Lobby selector delegation and interaction test.
- Student catalog filter-menu contract test.
- BGM track quick-action menu test.
- BGM library header reuse and passive info-row reuse tests.

Current coverage is strongest at the shared component layer. Most of the 42
production selector call sites rely on compilation and shared behavior tests;
future visual work should add focused screenshots or UI tests for the highest
risk compact, long-label, dynamic-list, and nested-menu surfaces.

## Checklist for the next dropdown pass

Use the IDs above in commits and QA notes.

### Component work

- [x] Freeze the desired selector anchor, popup container, row, selected state,
  disabled state, and action-menu visual specifications.
- [ ] Decide whether selector state stays caller-owned or moves into a reusable
  typed state holder.
- [ ] Keep one popup placement/lifecycle implementation.
- [x] Preserve Default and ActionMenu as distinct material variants.
- [ ] Preserve submenu Back behavior and exit-before-removal.
- [ ] Preserve destructive action styling and semantic roles.

### Regression matrix

- [ ] Compact phone at normal font.
- [ ] `25098PN5AC`, Android 16/API 36, 1220×2656 at 520 dpi.
- [ ] Android 17/API 37 AVD.
- [ ] 1.3× font scale and constrained width.
- [ ] Dark and light themes.
- [ ] Top, bottom, start, and end screen-edge anchors.
- [ ] Empty, one-item, long-label, tall-list, disabled, and dynamically changing
  option sets.
- [ ] Keyboard/TalkBack focus, RadioButton/Checkbox state, and effective touch
  targets.
- [ ] Nested submenu Back, outside-tap dismissal, rapid reopen, and selection
  during popup animation.

## Repeatable audit commands

Run these after each migration batch:

```bash
rg -n --glob '*.kt' --glob '!**/build/**' \
  'AppDropdownSelector\s*\(' app/src/main ui-liquid-glass/src/main

rg -n --glob '*.kt' --glob '!**/build/**' \
  'SnapshotWindowListPopup\s*\(|LiquidGlassActionMenu\s*\(' \
  app/src/main ui-liquid-glass/src/main

rg -n --hidden --glob '!**/build/**' --glob '!**/.gradle/**' \
  --glob '!**/.git/**' \
  'DropdownMenu|ExposedDropdownMenu|Spinner|AutoCompleteTextView|PopupMenu' .
```

When a new call site is added, assign the next ID in its product-area table and
record its adaptive, accessibility, and state-ownership characteristics.
