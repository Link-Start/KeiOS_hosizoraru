# KeiOS 功能完整介绍

[English Version](FEATURES.md)

KeiOS 是一个面向日常使用的 Android 工具台。它把系统参数查看、MCP 服务管理、GitHub Releases / Actions
追踪、GitHub Star 导入、仓库发现、Blue Archive 办公室提醒和学生图鉴媒体浏览放在同一个移动端界面里。

## Home

Home 是状态入口，集中展示 MCP 运行状态、GitHub 稳定版/预发版更新数、缓存状态、Actions / 分享导入状态、BA
AP、咖啡厅 AP、AP 余量、当前 BA 服务器、Shizuku 状态，以及当前底栏板块和 Home 卡片配置。顶部操作区可以调整底栏板块显示和
Home 摘要卡片。

## OS

OS 页面聚焦设备与系统信息：

- TopInfo，以及 System、Secure、Global、Android Properties、Java Properties、Linux environment 等键值表。
- 支持搜索 OS 参数和活动入口。
- 可配置活动快捷卡片，内置 Google 系统服务示例卡。
- 活动卡片和 Shell 卡片提供独立导入导出入口，导入前会预览并合并变化。
- 基于 Shizuku 的 Shell 运行器，支持命令历史、格式化输出、超时控制、高风险命令确认和保存到卡片。
- 系统快照缓存用于提升回访速度。
- v2 液态浮动 dock 承载添加、刷新、搜索和页面动作。

## MCP

MCP 页面用于管理本地 KeiOS MCP Server：

- 支持启动/停止、本机或局域网连接配置、端口/路径/Token 展示和配置复制。
- 展示 MCP 工具列表、运行日志和可导入配置。
- 提供 Claw Skill 快速接入提示词、本地化 SKILL.md 和单工具帮助资源。
- 42 个 MCP 工具覆盖 Home 总览、OS 卡片、系统 TopInfo、GitHub 跟踪 / 分享导入 / 仓库发现、Star List
  导入、包名扫描、仓库反扫和 Blue Archive 缓存巡检。
- 支持前台保活服务和测试通知。
- 通过通知兼容设置支持 HyperOS 超级岛模板与 AOSP Live Update 实时通知。

## GitHub

GitHub 页面用于追踪 GitHub 项目的 APK 发布：

- 支持稳定版和预发版更新检查。
- 支持 GitHub API 抓取方案配置，Token 可同时服务 Releases 与 Actions。
- 支持读取 Release 资源、APK 下载路由和最新发布下载。
- 支持 GitHub Actions 的分支、workflow、run、artifact 浏览，可走 nightly.link 公开链路或 GitHub API Token 链路。
- 分支推荐会综合默认分支、近期活跃度、成功 run 与 artifact 可用性。
- Artifact 排序会突出 Android 包、构建类型、universal 包、更新时间和历史下载记录。
- 跟踪项可关联包名，并与本机已安装应用匹配；支持从最新稳定 release APK 扫描包名，也支持按包名和应用名反扫仓库。
- 支持从仓库、Release、Tag、直链 APK 分享导入。
- 支持从自己的 stars、他人的公开 stars、公开 Star List 链接导入项目，导入页提供分类发现、搜索、多选筛选、Android/APK
  质量分类、release APK 验证和确认弹窗。
- 抓取方案诊断可对比 Atom 与 API 在 release 检查、包名扫描、仓库反扫上的表现。
- 支持可展开总览卡、刷新通知、本地缓存摘要和 KeiOS 自追踪快捷入口。

## BA 办公室

BA 页面是 Blue Archive 办公室仪表盘：

- 追踪 AP 与咖啡厅 AP，并按不同服务器时区计算。
- 支持 AP 阈值提醒、咖啡厅学生来访提醒、竞技场刷新提醒和 BA 专用超级岛显示。
- 支持按服务器独立的昵称 / 好友码 ID 卡、好友码复制和办公室总览卡。
- 支持服务器、咖啡厅等级、AP 阈值、媒体旋转、自定义媒体保存位置等配置。
- 活动日历与卡池卡片带服务器上下文和紧凑时间布局，可配置通知并跳转到对应学生图鉴。

## 学生图鉴

学生图鉴扩展 BA 相关资料与媒体浏览：

- 图鉴目录支持学生和相关分区、搜索、排序、紧凑本地化标签、同步状态和本地缓存。
- 学生详情支持资料、攻略/模拟、鉴赏媒体、本地化语音语言标签、音频/视频内容和来源分享。
- 礼物偏好解析保留礼物图片和态度标记。
- 回忆大厅 BGM 收藏库支持播放队列、液态底栏、mini player、批量缓存、失败重试、导入导出和跳回学生详情。
- 媒体缓存和导出流程支持表达包/媒体包打包保存。

## 设置与兼容

设置页集中管理运行策略：

- 主题模式、过渡动画、预测返回、搜索默认聚焦、预加载、应用语言入口和 Home HDR 高光。
- v2 液态玻璃 ActionBar、标题卡、搜索栏、浮动 dock、底栏、滑动时底栏完整特效策略和局部卡片按压反馈。
- 非 Home 页面自定义背景图和透明度。
- 通知权限、电池优化、OEM 自启动、应用列表访问和 Shizuku 状态。
- 超级岛通知样式、HyperOS 兼容绕过和恢复延迟调节。
- 复制/文本选择模式、缓存诊断、调试日志、日志 ZIP 导出和一键清理缓存。
- 调试组件实验室与 Liquid 组件样张用于检查共享 chrome、按钮、下拉菜单、滑杆、进度条和 dock 行为。
- 简体中文、English、日本語 资源已覆盖主要显示面，并针对 BA 术语、Android 设置、GitHub、Shell 与 MCP Skill 做本地化。

## 平台基线

- 包名：`os.kei`。
- ABI：`arm64-v8a`。
- Android 基线：Android 15+（`minSdk 35`），`targetSdk=37`。
- UI 技术栈：Jetpack Compose `1.11.1`、Miuix KMP、Lifecycle ViewModel Compose、自研 v2 液态玻璃
  Chrome、MMKV 偏好存储。
- 构建基线：Java 21、Kotlin `2.3.21`、Android Gradle Plugin `9.2.1` 与 Gradle 项目工具链。
