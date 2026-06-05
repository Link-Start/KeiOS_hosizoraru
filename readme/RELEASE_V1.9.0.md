# KeiOS v1.9.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.9.0 是一次面向日常使用链路的大版本更新：GitHub/Git 追踪、WebDAV 同步、BA 多账号、MCP/Claw 接入、OS/Shell 工具、图鉴缓存播放和整体性能都做了较大整理。这个版本适合所有 v1.8.x 用户升级。

### GitHub / Git 追踪更完整

- 新增更通用的 Git 来源支持。除了 GitHub 项目和订阅项目，现在可以追踪 Gitee、GitLab/Gitea 发行日志、通用 Git 标签，以及远端 APK 的更精准版本号。
- 新增版本忽略策略。可以临时忽略追踪、忽略全部版本、忽略当前稳定版、忽略当前预发版，也可以从追踪卡片菜单里直接忽略本版本。
- GitHub Actions 继续增强：更新通知会进入“Actions 历史”，历史卡片支持展开收纳、过滤、排序、刷新和清理旧记录。
- Actions artifact、托管安装、分享导入、下载历史和推荐 run 的状态更稳定，通知跳转会定位到对应项目和页面。
- GitHub 刷新状态、缓存时间、Home 概览卡和通知显示继续收口，长时间运行后缓存时间会保持正确。

### WebDAV 同步更安全

- WebDAV 现在有独立页面和底部板块，连接、同步、上传、自动同步、历史状态和数据项更容易找到。
- 同步和上传前会先刷新远端并展示变更计划，帮助确认本地与远端分别会发生什么变化。
- 上传、下载、合并和清理动作增加确认流程，减少误覆盖远端数据或丢失本地数据的风险。
- 同步数据覆盖范围扩展到 GitHub 追踪、OS 活动/Shell 卡片、BA 多账号、图鉴/BGM 收藏等更多项目数据。
- Jianguoyun 等 WebDAV 服务的路径、目录创建、HTTP 410、远端摘要和本地数量显示继续修复。

### BA 办公室升级为多账号

- BA 页面现在支持同一个服务器多个账号，也支持多个服务器分别管理账号。
- AP、咖啡厅、竞技场、活动日历和卡池提醒会按账号独立工作；通知点开后会跳转到对应账号和对应页面。
- 账号卡、AP 卡、咖啡厅卡、活动日历、卡池信息和设置入口重新整理，减少重复信息和旧的按服务器 ID 卡链路。
- BA 通知、Live Updates 和小米超级岛的摘要文案更精简，活动与卡池提醒更适合通知区域显示。
- 旧的 BA 配置会自动迁移到多账号模型，升级后可以继续使用原有数据。

### MCP / Claw 接入更清楚

- MCP runtime、工具目录和 GitHub/OS/BA 工具拆分到 feature module，后续维护更清晰。
- MCP 页面新增更完整的 Claw 引导：一键接入说明、Skill URI、Workflow URI、Sub Agent URI 和升级后的重新添加提醒。
- 工具目录、工作流资源、子 Agent markdown、运行日志和页面状态更稳定，Claw 更容易理解应该调用哪些 tools。
- MCP 本地服务优化了后台常驻性能：降低 Ktor/CIO 线程开销、缓存常用 markdown 资源、合并高频日志发布。

### OS / Shell 工具更稳

- OS 页面和 Shell Runner 继续整理：Shizuku 就绪状态、Shell 输出流、输出历史虚拟列表、内置卡片恢复和活动/Shell 卡片导入导出更稳定。
- Shell 输出历史和设置迁移到更清晰的页面状态链路，长输出和多次运行时更少卡顿。
- OS 活动卡片、Shell 卡片和 WebDAV 数据端口打通，常用卡片可以随备份一起迁移。

### 图鉴、音乐和媒体缓存

- 学生图鉴、NPC/卫星、音乐和播放板块继续优化缓存、预加载和滚动体验。
- 学生头像、BGM 信息、Now Playing、mini player、收藏 BGM 和媒体缓存更稳定，减少滚动回退后头像重新空白的问题。
- 音乐进度条拖动时减少播放状态闪烁，播放状态、收藏状态和 UI 展示更同步。
- 图鉴缓存刷新策略更适合游戏更新节奏：降低无意义频繁刷新，保留手动全量刷新入口。

### 界面和性能

- 液态玻璃 bottom sheet、dialog、slider、action bar、浮动 dock 和卡片阴影继续打磨。
- 液态玻璃弹出面板现在默认关闭。用户仍可在设置中手动启用，后续版本会继续优化帧率和功耗。
- 预测式返回、底栏/竖向 dock 收纳、Home 卡片、WebDAV 页面、Actions 历史页面、设置页和关于页继续统一交互。
- 大量页面把数据加载、派生状态和缓存工作移到 repository / ViewModel / 后台 dispatcher，减少 UI 渲染被数据加载阻塞。
- R8、Baseline Profile、benchmark/release 构建链路继续整理，减少启动 profile 旧符号警告并保持 release 构建可验证。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS_1.9.0.apk`

### 升级建议

建议所有 v1.8.x 用户升级到 v1.9.0。经常使用 GitHub/Git 追踪、WebDAV、BA 多账号提醒、MCP/Claw、Shizuku Shell、图鉴音乐缓存和小米超级岛通知的用户优先升级。升级后如果 Claw 没有识别新的 MCP tools，请在 Claw 中删除旧的 KeiOS MCP 服务器并重新添加。

## English

KeiOS v1.9.0 is a major daily-workflow update across GitHub/Git tracking, WebDAV sync, BA multi-account helpers, MCP/Claw onboarding, OS/Shell tools, Student Guide caching/playback, and general performance. It is recommended for all v1.8.x users.

### More Complete GitHub / Git Tracking

- Generic Git source support is now broader. In addition to GitHub projects and subscription projects, KeiOS can track Gitee, GitLab/Gitea release notes, generic Git tags, and more precise remote APK versions.
- New release ignore modes let you pause tracking, ignore all versions, ignore the current stable release, ignore the current pre-release, or ignore one version directly from a tracked card.
- GitHub Actions received a full notification history page with expandable cards, filtering, sorting, refresh, and old-record cleanup.
- Actions artifacts, managed installs, share import, download history, and recommended run selection are steadier, and notifications deep-link to the relevant project and screen.
- GitHub refresh status, cache age, Home overview cards, and notification summaries now stay aligned during longer app sessions.

### Safer WebDAV Sync

- WebDAV now has its own page with bottom sections for connection, sync, upload, auto-sync, status, and data items.
- Sync and upload refresh the remote side first and show a change plan before writing data.
- Upload, download, merge, and cleanup actions now use confirmation flows to reduce accidental overwrite or data loss.
- Sync coverage now includes GitHub tracking, OS activity/Shell cards, BA accounts, Student Guide/BGM favorites, and more app data.
- Jianguoyun and other WebDAV services received fixes around paths, folder creation, HTTP 410 responses, remote summaries, and local item counts.

### BA Office Multi-Account

- BA now supports multiple accounts on the same server and accounts across multiple servers.
- AP, cafe, arena, calendar, and pool reminders are account-aware; tapping a notification opens the matching account and page.
- Account cards, AP/cafe cards, calendar/pool pages, and settings were reorganized to remove repeated information and legacy server-only ID flows.
- BA notifications, Live Updates, and Xiaomi Super Island content are shorter and easier to read.
- Existing BA configuration migrates automatically into the multi-account model.

### Clearer MCP / Claw Onboarding

- MCP runtime, tool catalogs, and GitHub/OS/BA tools are split into feature modules for cleaner maintenance.
- MCP pages now provide clearer Claw setup guidance: one-shot setup prompt, Skill URI, Workflow URI, Sub Agent URI, and an upgrade reminder for re-adding the MCP server.
- Tool catalogs, workflow resources, sub-agent markdown, runtime logs, and page state are steadier so Claw can understand which tools to call.
- The local MCP service now has lower background overhead through bounded Ktor/CIO resources, cached markdown resources, and coalesced runtime log publishing.

### OS / Shell Tooling

- OS Page and Shell Runner received Shizuku readiness, streaming output, virtualized history, built-in card restore, and activity/Shell card import/export fixes.
- Shell output history and settings now use cleaner page-state ownership, improving behavior during long output and repeated runs.
- OS activity cards, Shell cards, and WebDAV data ports are connected, making card backup and migration more complete.

### Student Guide, Music, And Media Cache

- Student, NPC/Satellite, music, and playback sections received cache, preload, and scroll behavior improvements.
- Student avatars, BGM metadata, Now Playing, mini player, BGM favorites, and media cache state are more reliable.
- BGM seek interactions reduce play/pause flicker and keep playback state closer to the actual player.
- Cache refresh intervals better match game update cadence while keeping manual full refresh available.

### UI And Performance

- Liquid-glass bottom sheets, dialogs, sliders, action bars, floating docks, and card shadows received continued polish.
- Liquid-glass popup sheets are disabled by default in v1.9.0. They remain available in Settings while frame-rate and power usage work continues.
- Predictive back, bottom/vertical dock collapse, Home cards, WebDAV, Actions History, Settings, and About were aligned across screens.
- More data loading, derived state, and cache work now lives in repositories, ViewModels, and background dispatchers so UI rendering stays responsive.
- R8, Baseline Profile, benchmark, and release build paths were cleaned up to keep release artifacts verifiable and reduce stale startup-profile warnings.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS_1.9.0.apk`

### Upgrade Advice

Upgrade directly from any v1.8.x build to v1.9.0. This release is especially useful for GitHub/Git tracking, WebDAV sync, BA multi-account reminders, MCP/Claw, Shizuku Shell, Student Guide media caching, and Xiaomi Super Island notification users. After upgrading, re-add the KeiOS MCP server in Claw if Claw does not detect the new tool set.
