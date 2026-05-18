# KeiOS

<!-- markdownlint-disable MD013 MD033 -->

[English Version](../README.md)

<p align="center">
  <a href="https://github.com/hosizoraru/KeiOS/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/hosizoraru/KeiOS?include_prereleases&sort=semver&display_name=tag&style=flat-square"></a>
  <a href="../LICENSE"><img alt="License" src="https://img.shields.io/github/license/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/network/members"><img alt="GitHub forks" src="https://img.shields.io/github/forks/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/issues"><img alt="GitHub issues" src="https://img.shields.io/github/issues/hosizoraru/KeiOS?style=flat-square"></a>
  <a href="https://github.com/hosizoraru/KeiOS/commits/master"><img alt="Last commit" src="https://img.shields.io/github/last-commit/hosizoraru/KeiOS/master?style=flat-square"></a>
  <img alt="Release downloads" src="https://img.shields.io/github/downloads/hosizoraru/KeiOS/total?style=flat-square">
</p>

<p align="center">
  <a href="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-debug-apk.yml"><img alt="Debug APK CI" src="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-debug-apk.yml/badge.svg?branch=master"></a>
  <a href="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-benchmark-apk.yml"><img alt="Benchmark APK CI" src="https://github.com/hosizoraru/KeiOS/actions/workflows/ci-benchmark-apk.yml/badge.svg?branch=master"></a>
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-35-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-37-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-1.11.1-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
</p>

KeiOS 是一个 Android 工具台，聚合系统参数查看、本地 MCP 服务、GitHub Releases / Actions、GitHub Star
导入、订阅项目追踪、Blue Archive 辅助与学生图鉴功能。应用使用 Compose + Miuix 构建，并提供 v2
液态玻璃界面、高密度状态卡、导入导出、本地化
MCP Skill、支持语义化图标的通知提醒、仓库发现、反馈 Issue 草稿、缓存诊断和已生成的 Baseline Profiles。

## 项目信息

| 项目         | 内容                                             |
|------------|------------------------------------------------|
| 正式包名       | `os.kei`                                       |
| 支持 ABI     | `arm64-v8a`                                    |
| Android 基线 | Android 15+（`minSdk 35`）                       |
| Target SDK | Android 17 / API 37                            |
| UI 技术栈     | Jetpack Compose、Miuix、液态玻璃风格 chrome            |
| 运行技术栈      | Kotlin、Java 21、Shizuku、Media3、MMKV、Ktor、OkHttp |
| 语言资源       | 简体中文、English、日本語                               |
| 最新稳定标签     | `v1.6.0`                                       |

## 常用入口

- [最新稳定版](https://github.com/hosizoraru/KeiOS/releases/latest)
- [全部 Releases](https://github.com/hosizoraru/KeiOS/releases)
- [Debug APK CI artifact](https://nightly.link/hosizoraru/KeiOS/workflows/ci-debug-apk/master)
- [Benchmark APK CI artifact](https://nightly.link/hosizoraru/KeiOS/workflows/ci-benchmark-apk/master)
- [功能完整介绍](FEATURES_CN.md)
- [构建指南](BUILD_CN.md)
- [贡献指南](../CONTRIBUTING.md)
- [安全策略](../SECURITY.md)

## 主要功能

- Home 仪表盘使用状态 pill 与 MCP、GitHub、BA 摘要卡呈现关键状态。
- OS 工具支持系统表、Android/Java/Linux 属性、内置活动快捷入口、活动 / Shell 卡片搜索分组 sheet、Shizuku Shell
  和卡片导入导出。
- MCP 页面支持本地服务开关、配置复制、运行日志、前台保活、Claw 接入引导、本地化
  SKILL.md、工作流蓝图、结构化工具元数据，以及覆盖运行态、Home、OS、GitHub 发现/追踪、BA 缓存巡检的 45 个工具。
- GitHub 页面支持 Releases 与 Actions artifact 追踪、Atom/API 策略对比、从 release APK
  扫描包名、从本机包名反扫仓库、订阅项目追踪、分享链接导入、本机应用联动和 Star List 导入。
- GitHub Star 导入 Activity 支持自己的 stars、他人的公开 stars 与公开 Star List
  链接，提供分类发现、质量筛选、多选导入、APK 验证和退出确认。
- GitHub 托管安装与分享导入联动支持 Shizuku APK 交付，提供通知 / 超级岛进度、Manifest 检查、versionCode 展示和安装确认界面。
- GitHub Actions 更新通知支持追踪应用图标、跳转到对应 Actions sheet、推荐 run 定位和调试通知测试。
- JSON 导入支持多种 KeiOS 数据结构迁移，包括 OS 卡片迁移数据与导入结果跳转。
- BA 办公室支持 AP、咖啡厅来访、竞技场刷新提醒、分服务器活动/卡池数据、分服务器 ID 卡、媒体设置、超级岛通知和学生图鉴入口。
- 学生图鉴支持全页搜索、排序、媒体缓存、语音语言标签、BGM 收藏、鉴赏媒体、媒体导出、液态底栏和收藏导入导出。
- 设置页提供主题、动效、v2 液态玻璃组件、底栏特效策略、搜索默认聚焦、握姿感知浮动
  dock、背景图、应用语言、权限、缓存诊断、结构化日志、本地 GitHub Issue 反馈、无遥测诊断与通知兼容配置。

## v1.6.0 重点变化

- GitHub 追踪把直链 APK、JSON feed、伴生 JSON、带版本目录和 APK
  目录索引统一为订阅项目，支持远端健康度、稳定/预发通道、发行日志、安装动作和 Shizuku 托管安装联动。
- GitHub 页面保留排序/顺序/过滤记忆、悬浮 dock 按过滤结果批量刷新、桌面快捷方式全量刷新、编辑/设置
  sheet 未保存确认，并支持 Actions 更新间隔独立配置和 2h / 3h 选项。
- MCP 产品化为入口工具、工作流工具和高级工具，补齐 typed catalog、JSON schema、结构化输出、工作流蓝图、资源/帮助注册表、重写后的
  MCP Skill 页面和更轻的运行日志 / 会话监控。
- OS TopInfo 以可读摘要展示设备型号、系统构建、CPU、运行时、内存、存储、语言时区、开发调试状态与 verified boot
  信号，原始属性继续保留在 Android Properties 表中。活动与 Shell card sheet 支持搜索和分组，适配用户新增大量卡片后的查找。
- BA 学生图鉴适配 NPC 与卫星角色，提供更合适的资料标签、相关角色分类、更完整的影画鉴赏解析、媒体导出、收藏数据迁移和老 GameKee 页面兼容。
- 图鉴目录补齐实装学生属性筛选、NPC / 卫星按学园筛选，并收口排序 / 筛选下拉菜单宽度。
- 设置页可切换现有底栏 / pager 链路与 MIUIX iOS-like 底栏 / pager 链路，并收口不同 DPI 与尺寸下的底栏宽度、位置和对齐。
- App 图标以 Android Designs 作为默认图标，同时提供 Apple Designs 焕新版图标供用户切换。
- 发布前完成关于 / 设置 chrome 可见性、深色搜索可读性、benchmark 包启动、Gradle configuration cache、Ktor 3.5.0、
  范围更广的 Baseline Profiles、单元测试、R8 keep 规则和 release/benchmark mapping 审查。

完整功能介绍：

- [功能完整介绍 (CN)](FEATURES_CN.md)
- [Feature Overview (EN)](FEATURES.md)

## 当前分发方式

- 稳定版安装包通过 [GitHub Releases](https://github.com/hosizoraru/KeiOS/releases) 发布。
- 当前稳定标签：[v1.6.0](https://github.com/hosizoraru/KeiOS/releases/tag/v1.6.0)。
- 正式版基线：`os.kei`、`arm64-v8a`、Android 15+（`minSdk 35`）。
- 运行与构建基线：`targetSdk=37`、Java 21、Gradle Wrapper `9.5.1`、Kotlin `2.3.21`、
  Compose `1.11.1`、Android Gradle Plugin `9.2.1`、Ktor `3.5.0`。
- 当前应用语言资源覆盖简体中文、English、日本語。

## 文档

- [文档索引](INDEX.md)
- [Release Notes v1.6.0](RELEASE_V1.6.0.md)
- [Build Guide (EN)](BUILD.md)
- [构建指南 (CN)](BUILD_CN.md)
- [Todo List (EN)](TODO.md)
- [待办清单 (CN)](TODO_CN.md)
- [行为准则](../CODE_OF_CONDUCT.md)
- [贡献指南](../CONTRIBUTING.md)
- [安全策略](../SECURITY.md)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=hosizoraru/KeiOS&type=Date)](https://www.star-history.com/#hosizoraru/KeiOS&Date)
