# KeiOS v1.8.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.8.0 汇总 v1.7.1 之后的 GitHub 追踪、Actions artifacts、托管安装、超级岛通知、设置结构、图标偏好、模块拆分与发布检测优化。这个版本建议所有 v1.7.1 及更早版本用户升级，尤其适合经常从 GitHub Releases / Actions 安装 APK、使用 Shizuku 托管安装、依赖超级岛通知和 GitHub 追踪的用户。

### GitHub 追踪与 Actions artifacts

- GitHub Actions artifacts 列表更清爽：移除 sha256 详情，下载控件显示文件大小，安装与下载分成两个独立入口。
- 下载按钮在 nightly.link 与 GitHub API Token 两种模式下保持常驻，公开仓库、Token 链路和 Web 登录下载场景体验更一致。
- 支持托管安装的 Actions APK artifact 可以直接进入 KeiOS 安装确认流程，同时保留直接下载入口。
- Actions artifacts 按钮改成更紧凑的图标操作，列表空间利用更好。
- Actions sheet 加强加载态保护，刷新 run / artifact 时减少重复点击和中途状态错乱。
- Artifact 详情 sheet 单独拆分，列表与详情的元数据展示更一致。
- GitHub 追踪新增单项目检查间隔，可跟随全局刷新节奏，也可为单个项目设置 1、3、6、12 小时。

### 托管安装、通知与超级岛

- 托管安装链路继续收口：安装确认、安装完成状态、通知与超级岛路由更稳定。
- GitHub 分享导入与 Actions 安装动作通过 receiver 接管，减少从通知 / 超级岛进入安装时被拉回主界面的跳转感。
- 小米超级岛动作接入独立 action receiver，点击“知道了”、安装动作与焦点通知清理更符合 HyperOS 模板要求。
- GitHub 更新通知与刷新通知的动作、默认 Mi Focus 配置和调试目标继续整理。
- 安装进度发送链路和 Shizuku PackageInstaller 写入链路拆分，安装失败、完成和状态上报更可控。

### 设置、图标与日常体验

- 设置页分区更清晰：通知相关项迁入权限板块，外观与效果控制拆分成更明确的结构。
- App 图标偏好链路修复并迁移到 core prefs，默认图标策略和 Apple / Android Designs 切换更稳定。
- 后台刷新调度、前台状态信息和 package change 处理继续整理，减少刷新与状态展示的边界问题。
- About 页面、README、构建指南和 release notes 同步到 v1.8.0。

### 架构、构建与测试

- GitHub 后端迁入 feature module，数据层、domain 层、安装支持、模型与测试一起迁移，主 app 模块更轻。
- core-prefs、core-system、core-concurrency 边界继续完善，运行时默认值、系统工具和调度能力更清晰。
- 依赖版本更新并完成适配，相关单元测试同步补强。
- Release 构建继续保留 Baseline Profiles，并关闭 R8 dex startup layout 的实验链路以避免 obfuscated startup profile 警告。
- 发布包完成签名、versionCode/versionName、APK metadata、GitHub Release asset 与单元测试验证。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.8.0-arm64-v8a.apk`

### 升级建议

建议从 v1.7.1 及更早版本直接升级到 v1.8.0。常用 GitHub 追踪、Actions artifacts、Shizuku 托管安装、下载器联动、超级岛通知和设置页的用户优先升级。

## English

KeiOS v1.8.0 collects the changes since v1.7.1 across GitHub tracking, Actions artifacts, managed installs, Super Island notifications, Settings structure, icon preferences, module boundaries, and release validation. It is recommended for users on v1.7.1 and earlier, especially if you often install APKs from GitHub Releases / Actions, use Shizuku managed installs, rely on Super Island notifications, or track GitHub projects.

### GitHub Tracking And Actions Artifacts

- GitHub Actions artifact lists are cleaner: sha256 details are removed, download controls show file size, and install/download are split into separate actions.
- The download button stays visible in both nightly.link and GitHub API Token modes, giving public repositories, token-backed flows, and web-login downloads a more consistent shape.
- Actions APK artifacts that support managed install can now enter the KeiOS install confirmation flow while keeping a direct download path.
- Actions artifact buttons are more compact icon actions, improving list density.
- Actions sheet loading states are safer, reducing repeated refresh and mid-load state issues around runs and artifacts.
- The artifact detail sheet was split into its own surface, keeping list and detail metadata consistent.
- GitHub tracking now supports per-project update-check intervals: follow the global cadence or set 1, 3, 6, or 12 hours for a single tracked item.

### Managed Install, Notifications, And Super Island

- Managed install is steadier across install confirmation, completion state, notification routing, and Super Island routing.
- GitHub share-import and Actions install actions are routed through receivers, reducing the jump back into the main app when starting install work from notifications or Super Island.
- Xiaomi Super Island actions now use a dedicated receiver, improving “Got it”, install actions, and focus-notification cleanup against the HyperOS template requirements.
- GitHub update notifications, refresh notifications, default Mi Focus config, and debug targets were cleaned up together.
- Install progress delivery and the Shizuku PackageInstaller writer were split into focused helpers, making failure, completion, and progress reporting easier to control.

### Settings, Icons, And Daily UX

- Settings are easier to scan: notification-related controls moved under permissions, while appearance and effect controls are grouped more clearly.
- App icon preferences moved into core prefs, making the Apple / Android Designs switch and default icon behavior steadier.
- Background refresh scheduling, foreground state reporting, and package-change handling were tightened to reduce edge-case state issues.
- About, README, build guides, and release notes are synchronized for v1.8.0.

### Architecture, Build, And Tests

- The GitHub backend moved into the feature module, including data/domain/install/model surfaces and tests, making the app module lighter.
- core-prefs, core-system, and core-concurrency boundaries were refined around runtime defaults, system utilities, and scheduling.
- Dependency versions were refreshed with matching migrations and test updates.
- Release builds keep Baseline Profiles, while the experimental R8 dex startup layout path stays disabled to avoid obfuscated startup profile warnings.
- The release package was verified for signing, versionCode/versionName, APK metadata, GitHub Release asset upload, and unit tests.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.8.0-arm64-v8a.apk`

### Upgrade Advice

Upgrade directly from v1.7.1 or earlier to v1.8.0. This release is especially useful for GitHub tracking, Actions artifacts, Shizuku managed installs, downloader handoff, Super Island notifications, and Settings users.
