# KeiOS v1.9.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.9.0 面向 v1.8.0 用户带来一轮大版本升级。这个版本新增 WebDAV 数据同步、BA 多账号、Git 通用发行源、Actions 历史、MCP/Claw 引导和更多通知入口，同时继续打磨图鉴、音乐、OS/Shell、Home、设置和整体性能。

### 全新 WebDAV 数据同步

- 新增独立 WebDAV 页面。连接配置、数据项、同步状态、上传、同步、自动同步和历史信息现在集中在一个完整入口里。
- 支持先刷新远端，再生成变更计划。执行同步或上传前，可以先看到本地与远端分别有哪些数据会写入、合并或保留。
- 支持上传、下载、合并、清理和自动同步。关键写入动作带确认流程，适合把它作为长期备份和多设备迁移工具使用。
- 支持多类应用数据同步。覆盖 GitHub/Git 追踪、OS 活动卡片、Shell 卡片、BA 多账号、图鉴/BGM 收藏等数据。
- 支持常见 WebDAV 服务使用场景。页面提供服务商预设、远端目录处理、远端摘要、本地数量和同步结果展示。

### GitHub / Git 追踪升级

- 新增 Git 通用来源。除了 GitHub 项目和订阅项目，现在可以追踪 Gitee、GitLab/Gitea 发行日志、通用 Git tag，以及远端 APK 的更精准版本号。
- 新增版本忽略策略。可以暂停追踪、忽略全部版本、忽略当前稳定版、忽略当前预发版，也可以在追踪卡片菜单里直接跳过当前版本。
- 新增 Actions 历史页面。Actions 更新通知会进入历史记录，卡片支持展开、收纳、过滤、排序、刷新和清理旧记录。
- Actions artifact 更适合日常下载和安装。托管安装、分享导入、下载记录、推荐 run 和通知入口会围绕具体项目展示。
- 缓存和刷新状态更透明。Home 概览、追踪列表、通知文案和缓存时间会用同一套刷新状态表达，方便判断当前数据是否新鲜。

### BA 办公室多账号

- 新增多账号模型。可以在同一个服务器保存多个账号，也可以同时管理多个服务器的账号。
- 新增账号卡片和账号管理入口。BA 页面可以左右切换账号，账号资料、好友码、AP、咖啡厅、竞技场、活动日历和卡池信息会跟随当前账号展示。
- 通知现在携带账号目标。AP、咖啡厅、活动日历、卡池提醒点开后会进入对应账号和对应页面。
- BA 通知、Live Updates 和小米超级岛展示更精简。活动与卡池提醒摘要更适合通知区域和超级岛展示。
- v1.8.x 的 BA 数据会自动迁移。原有服务器配置、提醒和收藏会进入新的多账号数据结构。

### MCP / Claw 接入更完整

- MCP runtime、工具目录和 GitHub/OS/BA tools 拆分到 feature module，服务端、页面和工具描述的维护边界更清晰。
- MCP 页面新增 Claw 接入引导。可以复制一段完整配置提示，让 Claw 逐步添加 MCP 服务器、学习 Skill、Workflow 和 Sub Agent。
- 新增 Skill URI、Workflow URI 和 Sub Agent URI 引导。Claw 可以更清楚地理解 KeiOS 提供的工具、组合技能和子智能体角色。
- 新增升级后重新添加服务器提醒。Claw 缓存旧工具列表时，用户可以按页面提示重新添加 KeiOS MCP 服务器。
- 本地 MCP 服务后台开销更低。常用 markdown 资源会缓存，运行日志会合并发布，Ktor/CIO 资源占用也做了收敛。

### OS / Shell 工具增强

- OS 页面主列表状态重新整理。内置活动卡片和 Shell 卡片的恢复、导入导出、刷新状态和 WebDAV 数据端口更统一。
- Shell Runner 支持更顺滑的流式输出。长输出和多次运行时，历史列表会更稳定地滚动和展示。
- Shizuku 状态展示更明确。运行 Shell 命令前，页面会更清楚地反馈授权和服务可用状态。
- Shell 输出历史采用更适合长列表的页面结构，常用短命令和多次执行场景会更易读。

### 图鉴、音乐和媒体缓存

- 图鉴缓存策略更贴近游戏更新节奏。增量刷新、定期全量刷新和手动全量刷新入口更清楚，减少无意义的频繁网络请求。
- 学生、NPC/卫星、音乐和播放板块的预加载更稳。上下滚动时头像、BGM 信息和媒体缓存会更好地复用。
- Now Playing 和 mini player 展示更完整。学生头像、BGM 信息、收藏状态和播放状态会跟随缓存与播放器状态同步。
- 拖动音乐进度条时，播放状态会更稳定地保持当前意图，减少松手瞬间的状态跳动。

### 界面、通知和性能

- Home 卡片、Actions 历史、WebDAV 页面、BA 卡片、设置页和关于页统一了更多 action bar、底栏、竖向 dock 和卡片交互。
- 预测式返回动画、底栏收纳、竖向 dock 收纳、搜索 dock 和卡片按压反馈更贴近日常手势节奏。
- 液态玻璃弹出面板默认关闭。用户仍可在设置中手动启用；默认配置优先采用更稳的标准 sheet。
- 数据加载更多地放到 repository、ViewModel 和后台 dispatcher 中，页面渲染与网络、缓存、预加载任务的耦合更低。
- Release 构建链路继续覆盖 R8、Baseline Profile、lintVital、资源优化和签名校验，正式包保持可验证。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS_1.9.0.apk`

### 升级建议

建议所有 v1.8.x 用户升级到 v1.9.0。经常使用 GitHub/Git 追踪、WebDAV 备份、BA 多账号提醒、MCP/Claw、Shizuku Shell、图鉴音乐缓存和小米超级岛通知的用户会获得最明显的体验变化。升级后，Claw 没有识别新 tools 时，请在 Claw 中删除旧的 KeiOS MCP 服务器并重新添加。

## English

KeiOS v1.9.0 is a major upgrade for v1.8.0 users. It introduces WebDAV data sync, BA multi-account support, generic Git release sources, Actions History, MCP/Claw onboarding, and more notification entry points, while also improving Student Guide, music playback, OS/Shell tools, Home, Settings, and overall performance.

### New WebDAV Data Sync

- A dedicated WebDAV page is now available. Connection setup, sync items, status, upload, sync, auto-sync, and history are grouped in one full workflow.
- Sync and upload refresh the remote side first, then show a change plan. You can see which local and remote data will be written, merged, or kept before the operation runs.
- Upload, download, merge, cleanup, and auto-sync are supported. Important write actions include confirmation flows so WebDAV can work as a long-term backup and migration tool.
- More app data can be synced, including GitHub/Git tracking, OS activity cards, Shell cards, BA accounts, and Student Guide/BGM favorites.
- Common WebDAV provider flows are covered with provider presets, remote folder handling, remote summaries, local counts, and sync result details.

### GitHub / Git Tracking Upgrade

- Generic Git sources are now supported. In addition to GitHub projects and subscription projects, KeiOS can track Gitee, GitLab/Gitea release notes, generic Git tags, and more precise remote APK versions.
- Release ignore modes are now available. You can pause tracking, ignore all versions, ignore the current stable release, ignore the current pre-release, or skip the current version from a tracked card.
- Actions History is now available. Actions update notifications are recorded with expandable cards, filtering, sorting, refresh, and old-record cleanup.
- Actions artifacts are easier to download and install. Managed install, share import, download history, recommended runs, and notification entry points are organized around each tracked project.
- Cache and refresh status are clearer. Home overview cards, tracking lists, notifications, and cache age text use the same refresh state model.

### BA Office Multi-Account

- BA now uses a multi-account model. You can store multiple accounts on the same server and manage accounts across multiple servers.
- Account cards and account management are available. The BA page can switch accounts horizontally, and profile, friend code, AP, cafe, arena, calendar, and pool information follow the selected account.
- Notifications carry the target account. AP, cafe, calendar, and pool reminders open the matching account and screen.
- BA notifications, Live Updates, and Xiaomi Super Island presentation are shorter and easier to scan.
- Existing v1.8.x BA data migrates automatically into the new account model.

### More Complete MCP / Claw Onboarding

- MCP runtime, tool catalogs, and GitHub/OS/BA tools are split into feature modules with cleaner service, page, and tool-description boundaries.
- The MCP page now includes clearer Claw onboarding. You can copy one setup prompt that asks Claw to add the MCP server, learn the Skill, Workflow, and Sub Agent resources step by step.
- Skill URI, Workflow URI, and Sub Agent URI guidance is available so Claw can understand KeiOS tools, composed skills, and the sub-agent role.
- Upgrade guidance is included for Claw clients that cache older tool lists. Re-adding the KeiOS MCP server refreshes the available tools.
- The local MCP service has lower background overhead through cached markdown resources, coalesced runtime logs, and tighter Ktor/CIO resource usage.

### OS / Shell Tooling

- OS page list state is reorganized. Built-in activity cards, Shell cards, restore flows, import/export, refresh state, and WebDAV data ports now share a cleaner model.
- Shell Runner has smoother streaming output. Long output and repeated command runs are easier to read in the history list.
- Shizuku state is clearer before running commands, including authorization and service availability.
- Shell output history now uses a page structure that handles long lists and repeated short commands more comfortably.

### Student Guide, Music, And Media Cache

- Student Guide cache timing better matches the game update cadence, with clearer incremental refresh, periodic full refresh, and manual full refresh entry points.
- Student, NPC/Satellite, music, and playback preloading are steadier. Avatars, BGM metadata, and media cache are reused better while scrolling.
- Now Playing and mini player content is more complete. Student avatars, BGM metadata, favorites, and playback state stay closer to cache and player state.
- BGM seek interactions preserve the current playback intent more consistently when the user releases the slider.

### UI, Notifications, And Performance

- Home cards, Actions History, WebDAV, BA cards, Settings, and About now share more consistent action bars, bottom chrome, vertical docks, and card interactions.
- Predictive back, bottom chrome collapse, vertical dock collapse, search dock motion, and card press feedback are tuned for everyday gestures.
- Liquid-glass popup sheets are disabled by default. Users can still enable them in Settings; the default configuration now favors the steadier standard sheet path.
- More data loading runs through repositories, ViewModels, and background dispatchers, reducing coupling between UI rendering and network/cache/preload work.
- Release builds continue to cover R8, Baseline Profile, lintVital, resource optimization, and signing verification.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS_1.9.0.apk`

### Upgrade Advice

Upgrade directly from any v1.8.x build to v1.9.0. This release brings the largest gains for GitHub/Git tracking, WebDAV backup, BA multi-account reminders, MCP/Claw, Shizuku Shell, Student Guide media caching, and Xiaomi Super Island notification users. If Claw does not detect the new tools after upgrading, delete the old KeiOS MCP server in Claw and add it again.
