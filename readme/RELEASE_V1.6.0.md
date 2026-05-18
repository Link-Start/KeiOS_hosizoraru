# KeiOS v1.6.0 Release Notes

[Main README](../README.md) · [中文 README](CN.md) · [Build Guide](BUILD.md)

## 中文

KeiOS v1.6.0 是一次面向日常体验的大版本更新。重点是让应用更新追踪更省心、系统信息更容易读懂、Blue Archive 图鉴更完整，同时把底栏、搜索、设置和关于页面的细节打磨到更稳定的发布状态。

### 更新体验

- GitHub 追踪现在可以管理更多来源：GitHub Release、Actions artifact、直链 APK、JSON feed、带版本目录和 APK 目录索引。
- 追踪卡会显示更清晰的远端状态、稳定版 / 预发版信息、发行日志、安装入口和已安装应用信息。
- 分享 GitHub 仓库、Release、Tag 或 APK 链接到 KeiOS 后，可以直接进入解析、检查和安装流程。
- GitHub Actions 追踪支持更灵活的检查间隔，通知可以直接回到对应项目。

### 系统工具

- OS TopInfo 现在会用更容易理解的方式展示设备型号、系统版本、CPU、运行时、内存、存储、语言时区、开发状态和 verified boot。
- 活动快捷入口加入更多 AOSP / Google 系统设置入口，适合快速打开一些系统里比较深的位置。
- 活动卡片和 Shell 卡片管理 sheet 加入搜索和分组，卡片变多后查找会更顺手。
- Shell 相关导入、导出、保存命令和运行结果展示继续保留，适合常用系统命令流。

### BA 图鉴

- 实装学生目录加入更完整的筛选条件，包含星级、限定 / 常驻、攻击类型、防御类型、职责、站位、武器、适应性、学校、社团和评分排序。
- NPC / 卫星角色详情页重新适配，信息行更贴合这些角色的实际资料结构。
- NPC / 卫星角色的影画鉴赏解析覆盖更多老页面格式，相关角色分类也更清楚。
- 图鉴媒体、BGM 收藏、媒体导出和收藏数据迁移继续完善。

### 界面体验

- 设置里可以切换经典底栏 / pager 和 MIUIX iOS-like 底栏 / pager。
- MIUIX 底栏适配了不同 DPI 与屏幕尺寸下的位置、宽度和对齐。
- 长页面跳转短页面时，底栏和搜索按钮可以恢复正常显示。
- 关于页、设置页和深色模式搜索栏做了可读性修整。
- 应用图标默认使用 Android Designs，同时保留 Apple Designs 作为可切换的新图标风格。

### 稳定性

- Benchmark 包启动问题已修复。
- Gradle configuration cache 的 git 外部进程提示已收口。
- Ktor 更新到 3.5.0。
- Baseline Profiles 覆盖了更广的应用路径。
- R8 keep 规则、release / benchmark mapping、单元测试和发布构建流程已做发布前审查。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`），`targetSdk 37`
- APK：`KeiOS-v1.6.0-arm64-v8a.apk`
- SHA-256：`74175b4b0380d6fb186f34ff7ec7e926989b8c7fefcac1dad3b4abbbf7107d1d`

### 验证记录

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
apksigner verify
zipalign -c -p 4
```

## English

KeiOS v1.6.0 is a user-facing release focused on easier app-update tracking, clearer system information, richer Blue Archive guide pages, and a more polished navigation/search/settings experience.

### Update Tracking

- GitHub tracking now supports GitHub Releases, Actions artifacts, direct APK links, JSON feeds, versioned directories, and APK directory indexes.
- Tracked cards show clearer remote status, stable / prerelease information, release notes, install actions, and installed-app context.
- Shared GitHub repository, Release, Tag, and APK links can enter the KeiOS parse, check, and install flow.
- GitHub Actions checks gained more flexible intervals and notification deep links.

### System Tools

- OS TopInfo now explains device, build, CPU, runtime, memory, storage, locale, developer state, and verified boot signals in a friendlier format.
- Built-in activity shortcuts cover more AOSP / Google settings entries.
- Activity-card and Shell-card sheets now include search and grouped sections for larger personal card sets.
- Shell import/export, saved commands, and formatted run output remain available for daily system workflows.

### BA Guide

- Implemented-student catalog filtering now covers rarity, limited/permanent status, attack type, defense type, role, position, weapon, terrain, school, club, and score sorting.
- NPC and satellite character pages now use information rows that match their lighter profile data.
- NPC and satellite gallery parsing covers more older GameKee page formats, with clearer related-role grouping.
- Media viewing, BGM favorites, media export, and favorite-data migration continue to improve.

### Interface

- Settings can switch between the classic bottom bar / pager path and the MIUIX iOS-like bottom bar / pager path.
- The MIUIX bottom bar has better width, position, and alignment behavior across display sizes and densities.
- Bottom chrome and search buttons recover properly when moving from long pages to short pages.
- About, Settings, and dark-mode search surfaces received readability polish.
- Android Designs is the default icon style, and Apple Designs remains available as the refreshed alternate style.

### Reliability

- Benchmark package startup is fixed.
- Gradle configuration-cache git process churn is reduced.
- Ktor is updated to 3.5.0.
- Baseline Profiles now cover broader journeys.
- R8 rules, release / benchmark mapping, unit tests, and release build gates were reviewed before publishing.

### Package

- Package: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`), `targetSdk 37`
- APK: `KeiOS-v1.6.0-arm64-v8a.apk`
- SHA-256: `74175b4b0380d6fb186f34ff7ec7e926989b8c7fefcac1dad3b4abbbf7107d1d`
