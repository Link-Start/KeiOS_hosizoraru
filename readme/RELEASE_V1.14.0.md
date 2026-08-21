# KeiOS v1.14.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.14.0 是 v1.13.0 之后的 BA 日常效率、大屏导航、媒体交互与运行效率更新。98 个提交将制造室计时和一键日常从页面功能扩展到通知、快速设置磁贴、启动器快捷方式与 MCP；平板和折叠屏获得按窗口宽度变化的导航结构；Liquid Glass、自定义背景、学生图鉴播放与 Android 17 运行链路也完成了一轮集中收敛。

### BA 制造室与一键日常

- 每个 BA 账号新增六个独立制造槽：三个生成槽与三个物质合成槽。生成支持节点等级组合，物质合成支持等级与数量，二者统一计算总时长与完成时间。
- 制造完成提醒按槽位精确调度，每次装填会自然重新布防；通知发送成功后才写入去重标记，避免通知失败后丢失提醒，也避免已完成槽位形成高频重试。
- 制造室概览会折叠显示运行数、可领取数和最近完成时间；每个冷却项与制造槽使用独立卡片，离开可见区域后可由 Lazy 列表释放。
- 运行中的制造可以修改等级、数量与总时长，原始开始时间保持不变，新的完成时间按现有进度重新计算。
- 一键日常模板可配置 AP 余量、咖啡厅摸头 / 邀请次数和制造计划，并可应用到当前账号或全部账号。模板保持幂等：运行中的制造和仍在冷却的操作会得到保护。
- 快速设置提供全部账号与分账号固定槽位磁贴，启动器快捷方式与 MCP 也可触发相同日常流程；长按磁贴直接打开模板编辑器，完成结果可通过普通通知与超级岛呈现。

### 大屏导航、自定义背景与 Liquid Glass

- 内容区域新增最大宽度、单双栏阈值和随窗口变化的边距。手机保持紧凑底栏，常规平板宽度使用顶部导航，超宽窗口使用侧边栏。
- 超宽侧边栏支持拖动收起，并记住用户选择；折叠屏内屏、平板横竖屏和自由窗口之间切换时，导航会按实时宽度重新布局。
- 自定义背景连续覆盖二级页面、卡片 chrome 和弹层采样场景，降低页面跳转时材质突然切回默认背景的割裂感。
- Sheet 的嵌套滚动链路完成修复，内容超过首个展开高度时可以继续滚动；下拉菜单、滑杆与卡片堆叠的玻璃材质和手势反馈保持一致。

### 学生图鉴与媒体体验

- BGM 收藏移除后提供 Undo，降低误删成本；收藏队列、mini player、批量缓存、失败重试和导入导出继续共享同一播放状态。
- 原生媒体通知默认开启，后台播放、锁屏控制和应用内播放器使用一致的队列与播放状态。
- 学生图鉴目录和媒体页面适配新的大屏宽度与滚动边缘；搜索、筛选、详情、回忆大厅和媒体导出在宽窗口中保持可读内容宽度。

### 性能、内存与 Android 17

- 打开模态弹层时，弹层覆盖下不可见的动态背景会暂停空闲重绘；关闭弹层后从原位置继续。API 37 AVD 的同场景 A/B 中，静止三秒由 131 帧降到 0 帧，Sheet 滚动 RenderThread p50 由 132.92 ms 降到 17.47 ms。
- BA 办公室卡片移除均匀父材质上的冗余内层玻璃。API 37 AVD 交错测试中，滚动 p50 由 75 ms 降到 55 ms，p99 由约 150 ms 降到约 100 ms；深浅色截图差异最大值为 6/255，超过 3% 阈值的像素为 0。
- Android 通用内存压力回调与 ITGSA 公平运行内存协议共用同一套分级释放路径，只清理可重建的内存缓存并保留磁盘缓存。API 37 AVD 的近空闲 Debug 样例释放约 3.8 MB，R8 Release 样例释放约 4.7 MB。
- ITGSA 接收范围覆盖联盟设备，并为 TRIM / KILL、三秒回复、频率限制、时钟回拨和拒绝原因提供保护与可观测性。厂商系统真实广播继续列入发布前实体机 smoke。
- Android 17 的 Debug 与 R8 Release 路径完成适配审计；后台 BGM、前台服务、广播接收、通知与受限非 SDK 接口按各自运行场景检查。

### MCP、构建与发布质量

- MCP 工具目录扩展到 54 项，新增 BA 账号、日常模板与制造室相关能力，同时保留 typed catalog、JSON Schema、结构化输出、资源与 prompt 注册表。
- 构建基线更新为 Gradle `9.7.0`、Android Gradle Plugin `9.3.1`、Kotlin `2.4.10`、Compose `1.12.0-rc01` 与 Miuix `0.9.4-4a6b750b-SNAPSHOT`。
- R8 保留 Throwable 子类名称，Release 错误报告继续提供可读异常类型；Baseline Profile 旅程覆盖 BA 卡片、制造编辑器与大屏导航。
- 当前生成的 Baseline Profile 包含 53,428 条 baseline 规则与 23,875 条 startup 规则。发布门禁会比较 profile 捕获提交与后续 Kotlin / Java / AIDL 运行时代码，发现过期 profile 时直接失败。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- Target SDK：Android 17 / API 37
- APK：`KeiOS_1.14.0.apk`
- 校验文件：`KeiOS_1.14.0.apk.sha256`

### 升级建议

建议所有 v1.13.x 用户升级到 v1.14.0。使用 BA 多账号、制造提醒、快速设置磁贴、学生图鉴后台播放、平板 / 折叠屏、自定义背景或 Android 17 设备的用户会获得最直接的改善。升级后建议打开一次 BA 办公室确认日常模板、通知权限和账号磁贴配置。

## English

KeiOS v1.14.0 is the BA daily-efficiency, large-screen navigation, media-interaction, and runtime-efficiency update after v1.13.0. Across 98 commits, Craft Chamber timing and one-tap dailies extend from the app page into notifications, Quick Settings tiles, launcher shortcuts, and MCP. Tablets and foldables gain width-aware navigation, while Liquid Glass, custom backgrounds, Student Guide playback, and Android 17 runtime paths receive a focused reliability pass.

### BA Craft Chamber And One-Tap Dailies

- Every BA account gains six independent craft slots: three Generate slots and three Fusion slots. Generate accepts node-grade combinations, Fusion accepts grade and quantity, and both derive total duration and completion time through one model.
- Craft reminders are scheduled precisely per slot. Reloading a slot naturally rearms it, and deduplication is recorded only after successful delivery so a failed notification is retained without creating rapid retries for completed crafts.
- The Craft Chamber overview summarizes running, ready, and nearest-completion state. Each cooldown and craft slot is an independent card that a lazy list can release after it leaves the visible region.
- An active craft can be edited without changing its original start time; the updated duration produces a new completion time from the progress already made.
- The one-tap daily template can configure AP remainder, cafe headpat/invite counts, and craft plans for the current account or every account. Applying it is idempotent and protects active crafts and cooldowns that are still running.
- Quick Settings exposes fixed slots for all-account and per-account tiles. Launcher shortcuts and MCP invoke the same daily path, a tile long press opens the template editor, and completion feedback can use both standard notifications and Super Island.

### Large-Screen Navigation, Custom Backgrounds, And Liquid Glass

- Content gains maximum widths, single/two-pane thresholds, and window-aware gutters. Phones retain the compact bottom bar, regular tablet widths use top navigation, and ultra-wide windows use a sidebar.
- The ultra-wide sidebar can be dragged closed and remembers that choice. Navigation recomputes from the live window width across foldable inner displays, tablet rotations, and freeform windows.
- Custom backgrounds continue through secondary pages, card chrome, and modal sampling scenes, reducing material discontinuities during navigation.
- The sheet nested-scroll chain is fixed so content can move beyond its initial detent. Dropdown, slider, and card-pile materials and gestures now follow the same Liquid Glass behavior.

### Student Guide And Media

- Removing a BGM favorite now provides Undo. Favorites, the queue, mini player, batch cache, retry, and import/export continue to share one playback state.
- Native media notifications are enabled by default, keeping background playback, lock-screen controls, and the in-app player on the same queue and state.
- Student Guide catalog and media surfaces adapt to the new large-screen widths and scroll edges. Search, filters, details, Memorial Lobby, and media export keep a readable content width on wide windows.

### Performance, Memory, And Android 17

- While a modal covers the page, its invisible dynamic background pauses idle redraws and resumes from the same position after dismissal. In the same API 37 AVD A/B scene, a three-second idle sample drops from 131 frames to 0, and sheet-scroll RenderThread p50 drops from 132.92 ms to 17.47 ms.
- BA Office cards remove redundant inner glass over uniform parent material. Interleaved API 37 AVD runs move scroll p50 from 75 ms to 55 ms and p99 from about 150 ms to about 100 ms; light/dark screenshot differences peak at 6/255 with zero pixels beyond the 3% threshold.
- Android memory-pressure callbacks and the ITGSA fair-running-memory protocol share one tiered release path that evicts rebuildable memory caches while retaining disk caches. A near-idle API 37 AVD Debug sample releases about 3.8 MB, while an R8 Release sample releases about 4.7 MB.
- ITGSA registration now covers alliance devices and handles TRIM/KILL, three-second replies, rate limits, clock rollback, and rejected-message observability. Real vendor-system broadcasts remain part of the pre-release physical-device smoke.
- Android 17 Debug and R8 Release paths receive an adaptation audit covering background BGM, foreground services, broadcast reception, notifications, and the restricted non-SDK interfaces reached by their respective runtime flows.

### MCP, Build, And Release Quality

- The MCP catalog grows to 54 tools with BA account, daily-template, and Craft Chamber operations while retaining typed catalogs, JSON Schema, structured output, resources, and prompt registries.
- The build baseline moves to Gradle `9.7.0`, Android Gradle Plugin `9.3.1`, Kotlin `2.4.10`, Compose `1.12.0-rc01`, and Miuix `0.9.4-4a6b750b-SNAPSHOT`.
- R8 preserves Throwable subclass names so Release reports retain readable exception types. Baseline Profile journeys now cover BA cards, the craft/template editors, and large-screen navigation.
- The generated Baseline Profile contains 53,428 baseline rules and 23,875 startup rules. The release gate compares the capture commit against later Kotlin, Java, and AIDL runtime sources and fails when the profile is stale.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- Target SDK: Android 17 / API 37
- APK: `KeiOS_1.14.0.apk`
- Checksum file: `KeiOS_1.14.0.apk.sha256`

### Upgrade Advice

Every v1.13.x user should upgrade to v1.14.0. The most direct improvements apply to BA multi-account workflows, craft reminders, Quick Settings tiles, Student Guide background playback, tablets and foldables, custom backgrounds, and Android 17 devices. After upgrading, open BA Office once to confirm the daily template, notification permission, and account-tile configuration.
