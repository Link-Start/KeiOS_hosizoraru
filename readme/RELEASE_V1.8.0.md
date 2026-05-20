# KeiOS v1.8.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.8.0 汇总近期 GitHub 追踪、Actions artifacts、托管安装、设置结构与底层模块拆分的体验优化。这个版本建议所有 v1.7.1 及更早版本用户升级，尤其适合经常从 GitHub Releases / Actions 安装 APK 的用户。

### 重点变化

- GitHub Actions artifacts 列表更清爽：移除 sha256 详情，下载控件显示文件大小，安装与下载分成两个独立入口。
- 下载按钮在 nightly.link 与 GitHub API Token 两种模式下保持常驻，公开仓库与 Token 链路的 Actions artifact 下载体验更一致。
- 托管安装链路继续收口：Actions APK artifact 可以进入 KeiOS 安装确认流程，安装完成状态、通知与超级岛路由更稳定。
- GitHub 追踪新增单项目检查间隔，可跟随全局刷新节奏，也可为单个项目设置 1、3、6、12 小时。
- 设置页分区更清晰：通知相关项迁入权限板块，外观与效果控制拆分成更明确的结构。
- GitHub 后端迁入 feature module，core prefs / system 边界、依赖版本与发布检测同步整理。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.8.0-arm64-v8a.apk`

### 升级建议

建议从 v1.7.1 及更早版本直接升级到 v1.8.0。常用 GitHub 追踪、Actions artifacts、Shizuku 托管安装、下载器联动和超级岛通知的用户优先升级。

## English

KeiOS v1.8.0 collects the recent GitHub tracking, Actions artifacts, managed install, Settings, and module-boundary improvements into one stable release. It is recommended for users on v1.7.1 and earlier, especially if you often install APKs from GitHub Releases or Actions artifacts.

### Highlights

- GitHub Actions artifact lists are cleaner: sha256 details are removed, download controls show file size, and install/download are split into separate actions.
- The download button stays visible in both nightly.link and GitHub API Token modes, giving public-repository and token-backed Actions artifact flows a more consistent shape.
- Managed install is steadier: Actions APK artifacts can enter the KeiOS install confirmation flow, with improved completion state, notification, and Super Island routing.
- GitHub tracking now supports per-project update-check intervals: follow the global cadence or set 1, 3, 6, or 12 hours for a single tracked item.
- Settings are easier to scan: notification-related controls moved under permissions, while appearance and effect controls are grouped more clearly.
- GitHub backend code moved into the feature module, with core prefs/system boundaries, dependency versions, and release checks cleaned up together.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.8.0-arm64-v8a.apk`

### Upgrade Advice

Upgrade directly from v1.7.1 or earlier to v1.8.0. This release is especially useful for GitHub tracking, Actions artifacts, Shizuku managed installs, downloader handoff, and Super Island notification users.
