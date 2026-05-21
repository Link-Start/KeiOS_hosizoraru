# KeiOS v1.8.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.8.0 是面向 GitHub 追踪、Actions 下载 / 安装、超级岛通知和设置页的一次体验整理版。这个版本适合所有 v1.7.1 及更早版本用户升级。

### 更好用的 GitHub Actions 安装体验

- Actions artifacts 列表更清爽，隐藏 sha256 等低频信息，保留文件名、类型、版本、大小和推荐状态。
- APK artifact 同时提供“安装”和“下载”两个入口：安装进入 KeiOS 确认流程，下载交给浏览器、系统下载器或用户选择的下载器。
- nightly.link 和 GitHub API Token 两种模式下都会显示下载按钮，公开仓库和 Token 链路的体验更一致。
- 按钮尺寸更紧凑，长 artifact 列表扫起来更轻松。
- Actions sheet 在加载和刷新时更稳，减少重复点击、刷新中状态错乱和临时空白。

### GitHub 追踪更灵活

- 新增单项目检查更新间隔。每个追踪项目可以跟随全局刷新节奏，也可以单独设置 1、3、6、12 小时。
- GitHub 后台刷新、下载记录和推荐 artifact 选择继续优化，追踪列表的状态更可靠。

### 安装、通知和超级岛更稳

- Shizuku 托管安装流程继续修复，确认安装、安装中、安装完成和失败提示更连贯。
- 从通知或超级岛里触发安装时，跳转更少，流程更接近“直接继续当前动作”。
- 小米超级岛的操作按钮和焦点通知清理更稳定，减少点了“知道了”后仍残留、划掉通知后仍残留的情况。
- GitHub 更新通知、刷新通知和安装进度通知的状态显示更清楚。

### 设置和图标体验整理

- 设置页重新整理分区：通知相关项迁入权限板块，外观与效果相关项分组更明确。
- App 图标切换修复，Apple Designs / Android Designs 的切换和默认值更稳定。
- About 页面、README 和构建指南同步到 v1.8.0。

### 兼容性与稳定性

- GitHub 相关底层能力完成一次结构整理，后续维护和功能扩展会更轻。
- 依赖版本、构建配置、R8 / Baseline Profile 相关设置和测试覆盖同步更新。
- Release 包已完成签名、版本信息、APK metadata、GitHub Release 附件和单元测试验证。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.8.0-arm64-v8a.apk`

### 升级建议

建议 v1.7.1 及更早版本用户直接升级到 v1.8.0。经常使用 GitHub 追踪、Actions artifacts、Shizuku 托管安装、下载器联动和超级岛通知的用户优先升级。

## English

KeiOS v1.8.0 is a user-facing polish release for GitHub tracking, Actions download/install flows, Super Island notifications, and Settings. It is recommended for all users on v1.7.1 and earlier.

### Better GitHub Actions Install Flow

- Actions artifact lists are cleaner, hiding low-frequency sha256 details while keeping the filename, type, version, size, and recommendation status visible.
- APK artifacts now expose both Install and Download actions: Install opens the KeiOS confirmation flow, while Download hands off to the browser, system downloader, or your preferred downloader.
- The Download button is visible in both nightly.link and GitHub API Token modes, making public-repository and token-backed flows more consistent.
- Action buttons are more compact, making long artifact lists easier to scan.
- Actions sheets are steadier during loading and refresh, reducing repeated taps, mid-refresh state glitches, and temporary empty states.

### More Flexible GitHub Tracking

- Each tracked project can now use its own update-check interval. It can follow the global cadence or use 1, 3, 6, or 12 hours.
- GitHub background refresh, download history, and recommended artifact selection received reliability improvements.

### Steadier Install, Notification, And Super Island Behavior

- Shizuku managed install is smoother across confirmation, installing, completed, and failed states.
- Install actions launched from notifications or Super Island continue with fewer jumps back into the main app.
- Xiaomi Super Island action buttons and focus-notification cleanup are more reliable, reducing leftover islands after “Got it” or notification dismissal.
- GitHub update, refresh, and install-progress notifications now present clearer states.

### Settings And Icon Polish

- Settings sections were reorganized: notification controls moved under permissions, while appearance and effect controls are grouped more clearly.
- App icon switching is fixed and steadier for Apple Designs / Android Designs.
- About, README, and build guides are synchronized for v1.8.0.

### Compatibility And Reliability

- GitHub internals were reorganized to make future maintenance and feature work lighter.
- Dependency versions, build configuration, R8 / Baseline Profile settings, and tests were refreshed together.
- The release package was verified for signing, version metadata, APK metadata, GitHub Release upload, and unit tests.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.8.0-arm64-v8a.apk`

### Upgrade Advice

Upgrade directly from v1.7.1 or earlier to v1.8.0. This release is especially useful for GitHub tracking, Actions artifacts, Shizuku managed installs, downloader handoff, and Super Island notification users.
