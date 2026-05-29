# v1.8.4 Functional Verification Plan

## Scope

- Reference: v1.8.4 (`7259af5e7`), post MIUIX performance adoption (M0–M9).
- Goal: verify every functional chain across all pages still works correctly
  after the performance changes. This is a regression verification pass, not
  a feature audit.
- Method: code-path verification (read each chain end-to-end) + compile/test
  gates. No AVD visual testing unless a code-path concern requires it.

## Performance changes under verification

| Change | Risk area |
| --- | --- |
| M0: BgEffect `syncAnimation` no longer checks `effectBackground` | Home dynamic background activation/deactivation |
| M2: 6 Squircle clips removed | BGM chrome, dock visuals, bottom bar, search field clipping |
| M4: BGM mini-player `progress: Float` → `progress: () -> Float` | BGM mini-player expand/collapse transition |
| M5: SettingsPage search keeps content mounted | Settings search activation/dismissal, pager state preservation |
| M6: InteractiveHighlight `ShaderBrush` hoisted | Interactive highlight animation |
| M7: Debug FPS overlay added (debug-only) | Debug build startup |
| M8a: `BindScrollToTopEffect` extracted | MCP/GitHub/OS scroll-to-top |
| M8c: `TabbedPageBottomChrome` generic extraction | Settings/About bottom chrome rendering |

## Verification batches

### Batch A — Global chrome + navigation host
- `MainScreenNavHost` route push/pop
- `MainPagerLayout` horizontal swipe between pages
- `MainPagerBottomBar` visibility, category switching
- `LiquidToastHost` overlay
- `LocalGlassEffectRuntime` threading
- Predictive back support

### Batch B — Home
- `BgEffectBackground` activation (M0 risk)
- `BgEffectBackground` deactivation on page leave
- Dynamic background color cycling
- `HomePageHero` parallax motion on scroll
- `LiquidActionBar` items (edit pages, About, Settings)
- `HomePageControlSheet` open/dismiss
- Overview cards (MCP/GitHub/BA status)
- Foreground blur gate

### Batch C — Settings
- Category pager (Access/Appearance/Effects/Data)
- Bottom chrome rendering (M8c risk)
- Search expansion with content mounted (M5 risk)
- Search query filtering + results display
- Search dismissal (BackHandler + bottom bar)
- `ScrollChromeVisibilityController` hide/show on scroll
- `farJumpAlpha` dim animation on distant tab jump
- Theme mode popup, launcher icon popup
- Background image picker
- Log export

### Batch D — GitHub
- Page lifecycle coordinator
- Scroll-to-top (M8a risk)
- Search expansion + tracked search filter
- Track card expand/collapse
- APK asset panel toggle
- Overview expand
- `rememberMainPageBackdropSet` glass effects
- Sheet host (APK info, actions, release notes, install confirm, etc.)
- Star import flow
- Background refresh coordinator

### Batch E — MCP
- Server toggle (start/stop)
- Scroll-to-top (M8a risk)
- Tool search query filtering
- Edit service config sheet
- Reset config/token confirm dialogs
- Skill page navigation
- Log export/clear
- `rememberMainPageBackdropSet` glass effects

### Batch F — OS
- Card expansion toggle (top info, shell runner, system/secure/global tables)
- Scroll-to-top (M8a risk)
- Search expansion + query filtering
- Activity shortcut cards (launch, edit, delete)
- Shell command cards (run, edit, delete)
- Card manager sheet
- Import/export via SAF
- `rememberMainPageBackdropSet` glass effects

### Batch G — BA (Office)
- Server popup selection (CN/Global/JP)
- Calendar/pool sync
- Friend code copy
- Floating dock (calendar, pool, friend code)
- Notification dispatchers
- Settings/notification/debug sheets
- `BaOfficeClock` real-time countdown
- `rememberMainPageBackdropSet` glass effects

### Batch H — BA Guide Catalog (图鉴)
- Category pager (Student, BGM, etc.)
- Bottom chrome expand/collapse on scroll
- Per-tab search query filtering
- Student entry → detail navigation
- BGM playback controls (play/pause/next/prev)
- BGM mini-player expand/collapse (M4 risk)
- BGM volume slider
- BGM favorites management
- Catalog filter/sort
- Import/export via transfer sheet

### Batch I — BA Student Guide
- Pager page switching
- Scroll-to-top on tab re-tap
- Bottom bar chrome (visibility, category switching)
- Section expand/collapse
- Gallery fullscreen media layer
- BGM tab content
- `appSquircleClip` on content (M2 risk — avatar fallback uses
  `appSquircleBackground` now)

### Batch J — About
- Category pager (Overview/System/Tech/Lab)
- Bottom chrome rendering (M8c risk)
- Search expansion + query filtering
- `farJumpAlpha` dim animation
- Section expand/collapse
- Shizuku check button
- External URL opening
- Component Lab navigation
- `ScrollChromeVisibilityController` hide/show

## Status table

| Batch | Scope | Status | Issues |
| --- | --- | --- | --- |
| A | Global chrome | ✅ 6/6 PASS | NavHost routes (6), pager (5 pages), bottom bar, toast, glass runtime, predictive back — all intact. |
| B | Home | Not started | |
| C | Settings | Not started | |
| D | GitHub | Not started | |
| E | MCP | Not started | |
| F | OS | Not started | |
| G | BA Office | Not started | |
| H | BA Guide Catalog | Not started | |
| I | BA Student Guide | Not started | |
| J | About | Not started | |

## Verification commands

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
git diff --check
rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'
```
