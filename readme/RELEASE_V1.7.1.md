# KeiOS v1.7.1 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.7.1 是一次体验修复版，重点收口近期新增的 Liquid Glass sheet、OS 快捷卡和轻提示反馈。这个版本适合所有 v1.7.0 用户升级。

### 重点变化

- Liquid Glass sheet 的保存、编辑和返回手势关闭更稳定，减少关闭后状态残留和二次返回异常。
- OS 页面新增内置 shell card：获取状态栏通知图标个数、设置状态栏通知图标个数为 9 个、隐藏手势提示线。
- 全面屏设置快捷入口修正 Intent Extra，并把说明改成更直观的“HyperOS 系统桌面的全面屏设置”。
- Shell、MCP、设置页里的运行完成、复制完成、清空完成、刷新完成等轻提示改为低打扰 Liquid Glass Toast。
- 设置新增“减少轻提示打扰”开关，开启后可静默这些低优先级提示。
- Liquid Glass 组件层继续打磨 dialog、switch、checkbox、progress bar 和 toast 的显示细节。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.7.1-arm64-v8a.apk`

### 升级建议

建议从 v1.7.0 直接升级。使用 OS shell card、MCP 服务和 Liquid Glass sheet 比较频繁的用户，会明显感受到这个版本的细节修复。

## English

KeiOS v1.7.1 is a focused experience update for Liquid Glass sheets, OS shortcut cards, and low-priority feedback hints.

### Highlights

- Liquid Glass sheets now close more reliably after save/edit actions and back gestures.
- OS tools add built-in shell cards for status-bar notification icon count and gesture-line hiding.
- The fullscreen settings shortcut now has corrected Intent extras and clearer copy.
- Shell, MCP, and Settings success hints now use low-interruption Liquid Glass Toast only.
- Settings adds a Reduce Toast Interruption toggle for silencing completion/copy/clear style hints.
- Liquid Glass dialogs, switches, checkboxes, progress bars, and toast presentation received focused polish.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.7.1-arm64-v8a.apk`
