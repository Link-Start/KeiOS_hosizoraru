# KeiOS v1.7.0 Release Notes

<!-- markdownlint-disable MD013 -->

[Main README](../README.md) · [中文 README](CN.md) · [Build Guide](BUILD.md)

## 中文

KeiOS v1.7.0 重点打磨底层交互、GitHub 追踪、OS 快捷入口、MCP 开发辅助和发布稳定性。这个版本延续 v1.6.0 的功能扩展，并把近期频繁使用的 sheet、通知、复制、图标和构建链路收得更稳。

### 界面与导航

- Liquid Glass bottom sheet 默认以 3/4 屏显示，内容放不下时自动扩展到全屏，Actions sheet 默认全屏。
- Sheet 顶部和底部 chrome 遮挡继续收口，内容区域可见性更好，小白条和顶部区域恢复稳定。
- Liquid Glass bottom sheet 补齐预测式返回，并优化系统返回手势完成后的关闭动画。
- 点击 topbar 的 title card 可以唤回隐藏的底栏，长页面跳到短页面后的恢复体验更自然。
- 复制能力扩展优化了长按文字选择时的光标表现，发行日志页面支持长按选择文本。

### GitHub 追踪

- GitHub 追踪列表在失败项目恢复后，会自动从“失败”过滤切回全项目，减少空列表困惑。
- GitHub Actions 新构建通知按追踪项目独立存在，避免多个项目之间互相覆盖。
- 发行日志翻译按钮旁新增复制按钮，可以保留 Markdown 格式复制全文。
- 更新提示文案更简洁，去掉“推荐 Run 更新到”。

### OS 工具

- 新增 HyperOS 系统桌面的全面屏设置入口，并修正 SubSettings extras。
- 内置活动卡保留更实用的系统入口：极暗、电池使用情况、通知设置、应用管理、应用内存用量、语言设置、跨设备服务和全面屏设置。
- 活动 card sheet 与 Shell card sheet 加入搜索和分类，用户自定义卡片变多后更容易查找。
- 活动卡编辑 sheet 的返回关闭状态修复完成，关闭后可以继续正常打开和编辑 OS Page。

### MCP 与开发辅助

- MCP 页面拆分更细的工具 card，减少单个 card 过载。
- BA 相关 MCP 工具独立成组，Codex 辅助开发相关能力同步接入页面、内置 skill 和工具说明。
- MCP 工具入口高级工具展开/收起链路修复，交互状态更加稳定。

### 图标、构建与稳定性

- Android Designs 保持默认图标，Apple Designs 图标切换在 benchmark 构建中恢复正常。
- R8 startup profile 缺失类警告已收口，Release / Benchmark 构建日志更干净。
- `libmmkv.so` 作为需要保留调试符号的 native 库处理，strip 任务提示收口。
- Gradle daemon 内存配置上调，减少大型 assemble 过程中的 daemon 重启。
- Baseline Profile 重新覆盖更广路径，预构建和 sheet 相关单测已通过。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`），`targetSdk 37`
- APK：`KeiOS-v1.7.0-arm64-v8a.apk`
- SHA-256：发布 APK 后填写

### 验证建议

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
apksigner verify
zipalign -c -p 4
```

## English

KeiOS v1.7.0 focuses on interaction polish, GitHub tracking, OS shortcuts, MCP development helpers, and release stability. It builds on the v1.6.0 feature expansion and tightens the sheet, notification, copy, icon, and build paths used most often.

### Interface And Navigation

- Liquid Glass bottom sheets now open at three-quarter height by default, expand to full height when content overflows, and open Actions sheets at full height.
- Top and bottom sheet chrome received more spacing fixes so sheet content stays visible and the drag handle remains stable.
- Liquid Glass bottom sheets now support predictive back with a smoother close animation after system back gestures.
- Tapping the topbar title card can restore the hidden bottom bar, improving short-page recovery after scrolling long pages.
- Copy-extension text selection is smoother, and release notes now support long-press text selection.

### GitHub Tracking

- Failed-item filtering now returns to all items after failed projects recover, reducing empty-list dead ends.
- GitHub Actions update notifications are separated per tracked project, so one project's notification no longer hides another.
- Release notes now include a copy button next to translate, preserving Markdown formatting.
- Update wording is shorter after removing the recommended-run prefix.

### OS Tools

- A HyperOS launcher fullscreen-settings shortcut was added with corrected SubSettings extras.
- Built-in activity cards keep the more useful system entries: low-brightness settings, battery usage, notification settings, app management, app memory usage, language settings, cross-device services, and fullscreen settings.
- Activity-card and Shell-card sheets now include search and categories for larger custom card sets.
- The activity-card editor sheet now closes cleanly through back gestures and can be reopened normally.

### MCP And Development Helpers

- The MCP page splits dense tool groups into smaller cards.
- BA MCP tools are grouped separately, and Codex-oriented development helpers are wired into the page, packaged skills, and tool descriptions.
- The advanced-tool expand/collapse chain in MCP tool entry cards is fixed.

### Icons, Builds, And Reliability

- Android Designs remains the default icon set, and Apple Designs icon switching is fixed for benchmark builds.
- R8 startup-profile missing-class warnings are reduced for Release / Benchmark builds.
- `libmmkv.so` is treated as a native library that keeps debug symbols, reducing strip-task noise.
- Gradle daemon memory settings were raised to reduce daemon restarts during larger assemble runs.
- Baseline Profiles were refreshed across broader journeys, and focused prebuild / sheet tests passed.

### Package

- Package: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`), `targetSdk 37`
- APK: `KeiOS-v1.7.0-arm64-v8a.apk`
- SHA-256: Fill after publishing the release APK

### Suggested Verification

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
apksigner verify
zipalign -c -p 4
```
