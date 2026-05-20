# KeiOS v1.7.3 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.7.3 聚焦 GitHub Actions artifacts 的使用体验。这个版本适合经常从 Actions 下载或安装 APK 的用户升级。

### 重点变化

- Actions artifacts card 去掉 sha256 详情，列表信息更轻、更容易扫读。
- 下载控件直接显示文件大小，动作含义由 icon 承载，减少重复文字。
- 支持 KeiOS 托管安装的 APK artifact 现在会同时显示安装和下载两个动作：
  - 安装继续进入 KeiOS 的安装确认流程。
  - 下载直接走外部浏览器 / 系统下载器 / 用户选择的下载器。
- Actions artifact 下载入口保持常显，适配 GitHub Web 登录或 Token 下载场景。
- Artifact 详情 sheet 同步精简 metadata，列表和详情保持一致。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.7.3-arm64-v8a.apk`

### 升级建议

建议从 v1.7.2 直接升级。常用 GitHub Actions artifacts、Shizuku 托管安装、下载器联动和 GitHub 追踪的用户优先升级。

## English

KeiOS v1.7.3 focuses on the GitHub Actions artifacts experience. It is recommended if you often download or install APKs from Actions artifacts.

### Highlights

- Actions artifact cards hide sha256 details, making the artifact list cleaner and easier to scan.
- The download control now shows the file size directly, while the icon carries the action meaning.
- APK artifacts that support KeiOS managed install now expose two separate actions:
  - Install opens the KeiOS install confirmation flow.
  - Download opens the external browser, system downloader, or selected downloader path.
- The Actions artifact download action stays visible for GitHub web login and token-backed download flows.
- The artifact detail sheet now follows the same simplified metadata style as the list cards.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.7.3-arm64-v8a.apk`

### Upgrade Advice

Upgrade directly from v1.7.2. This release is especially useful for GitHub Actions artifacts, Shizuku managed installs, downloader handoff, and GitHub tracking users.
