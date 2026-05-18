# KeiOS v1.6.0 Release Notes

[Main README](../README.md) · [中文 README](CN.md) · [Build Guide](BUILD.md)

## English

KeiOS v1.6.0 is the release that turns the app into a fuller Android utility console: GitHub
tracking now handles subscription-style APK sources, MCP tools are easier to discover, Student Guide
pages handle NPC/satellite entries more accurately, and navigation can switch between the classic
bottom bar path and the MIUIX iOS-like path.

### Highlights

- GitHub tracking now supports direct APK links, JSON feeds, companion JSON, versioned directories,
  and APK directory indexes as subscription projects, with remote health, stable/prerelease
  channels, release notes, install actions, and Shizuku managed install handoff.
- GitHub page state keeps remembered sort/filter/order choices, filtered-list refresh from the
  floating dock, full-refresh shortcuts, edit/settings unsaved-change confirmation, and independent
  Actions update intervals including 2h and 3h.
- MCP tools are grouped into entrypoint, workflow, and advanced sections, with typed catalog
  metadata, JSON schema, structured output, workflow blueprints, resource/help registries, and a
  redesigned MCP Skill page.
- OS TopInfo now shows readable device, build, CPU, runtime, memory, storage, locale,
  developer-state, and verified-boot summaries, while raw Android properties remain available for
  deeper inspection. Built-in activity shortcuts now cover more AOSP/Google settings entries, and
  activity/shell card sheets include search plus grouped sections.
- Student Guide NPC and satellite pages now use leaner profile labels, related-role grouping,
  broader gallery parsing, media export support, favorite-data migration, and older GameKee page
  compatibility.
- Student Guide catalog filters now cover implemented-student attributes, school filtering for
  NPC/satellite entries, and tighter dropdown sizing for sort/filter menus.
- Settings can switch between the classic bottom bar / pager path and the MIUIX iOS-like bottom bar
  / pager path, with shared sizing fixes across display densities.
- Icon design selection keeps Android Designs as the default icon set and offers Apple Designs as a
  refreshed alternate set.
- Release hardening covers About/Settings chrome visibility, dark search readability, benchmark
  package startup, Gradle configuration-cache hygiene, Ktor 3.5.0, broader Baseline Profiles, test
  coverage, R8 keep-rule review, and release/benchmark mapping verification.

### Package Baseline

- Package: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`), `targetSdk 37`
- Signed APK: `app-release.apk`, SHA-256
  `74175b4b0380d6fb186f34ff7ec7e926989b8c7fefcac1dad3b4abbbf7107d1d`
- Toolchain: Java 21, Gradle Wrapper `9.5.1`, Kotlin `2.3.21`, Compose `1.11.1`, Android Gradle
  Plugin `9.2.1`, Ktor `3.5.0`

### Final Gate

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
```

## 中文

KeiOS v1.6.0 是一次发布前大收口：GitHub 追踪正式覆盖订阅式 APK 来源，MCP 工具入口更清晰，学生图鉴能更准确适配
NPC / 卫星角色，主导航可以在经典底栏链路与 MIUIX iOS-like 链路之间切换。

### 重点变化

- GitHub 追踪把直链 APK、JSON feed、伴生 JSON、带版本目录和 APK 目录索引统一为订阅项目，支持远端健康度、稳定
  / 预发通道、发行日志、安装动作和 Shizuku 托管安装联动。
- GitHub 页面保留排序 / 顺序 / 过滤记忆、悬浮 dock 按过滤结果批量刷新、桌面快捷方式全量刷新、编辑 / 设置
  sheet 未保存确认，并支持 Actions 更新间隔独立配置和 2h / 3h 选项。
- MCP 工具按入口工具、工作流工具和高级工具重新组织，补齐 typed catalog、JSON schema、结构化输出、工作流蓝图、资源
  / 帮助注册表和重写后的 MCP Skill 页面。
- OS TopInfo 以可读摘要展示设备型号、系统构建、CPU、运行时、内存、存储、语言时区、开发调试状态与 verified boot
  信号，原始 Android 属性继续保留给深度排查。内置活动入口覆盖更多 AOSP / Google 系统设置，活动 / Shell card sheet 支持搜索和分组。
- 学生图鉴 NPC / 卫星详情页补齐更合适的资料标签、相关角色分类、更完整的影画鉴赏解析、媒体导出、收藏数据迁移和老
  GameKee 页面兼容。
- 学生图鉴目录补齐实装学生属性筛选、NPC / 卫星按学园筛选，并收口排序 / 筛选下拉菜单宽度。
- 设置页可切换经典底栏 / pager 链路与 MIUIX iOS-like 底栏 / pager 链路，并统一不同 DPI 与尺寸下的底栏适配。
- 图标设计以 Android Designs 作为默认图标，同时提供 Apple Designs 焕新版图标供用户切换。
- 发布前收口覆盖关于 / 设置 chrome 可见性、深色搜索可读性、Benchmark 包启动、Gradle configuration cache、Ktor 3.5.0、
  范围更广的 Baseline Profiles、单元测试、R8 keep 规则和 Release / Benchmark mapping 审查。

### 包基线

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`），`targetSdk 37`
- 签名 APK：`app-release.apk`，SHA-256
  `74175b4b0380d6fb186f34ff7ec7e926989b8c7fefcac1dad3b4abbbf7107d1d`
- 工具链：Java 21、Gradle Wrapper `9.5.1`、Kotlin `2.3.21`、Compose `1.11.1`、Android Gradle Plugin
  `9.2.1`、Ktor `3.5.0`

### 最终门禁

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
```
