# KeiOS v1.16.0 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.16.0 集中提升版本追踪的判断可靠性、刷新速度与网络诊断，以及 Liquid Glass 的渲染开销。GitHub 追踪能正确识别重新编号的项目、已被正式版取代或长期无人更新的预发布，以及没有可安装文件的发行版；刷新请求不再占用线程，Atom 模式的两个请求并行发出；主要页面的玻璃卡片在纯色背景上少走整条离屏效果链。材质、动效、虚化与交互反馈保持原样。

### 版本追踪的判断

- 重新从较小版本号开始的项目（例如更名后从 `v1.0.1` 重新发布）不再被旧的大版本号永久压住。只有列表本身显示出这种迹象时，才额外读取一次仓库的 `releases/latest`，普通仓库的请求数不变；这次读取失败会回退到本地判断。
- 以已发布正式版为前缀的预发布（例如 `pre-1.4.2-…` 在 `1.4.2` 发布之后）会被正式版取代，不再提示一个比已安装版本更旧的“更新”。
- 没有任何可下载文件的发行版不再算作更新；Atom 模式看不到文件列表，因此不据此下结论。
- 预发布线按文件最后更新时间判断活跃度：正式版之后超过 14 天没有更新的预览线会退出比较，用 CI 反复替换产物的旧标签也不会被误判为停更。
- 版本比较保留置信度。本地版本与发行版没有共同的前导版本号时，卡片显示“无法确定”，而不是“已是最新”。
- 结果出乎意料时，卡片会说明选择原因，例如“仓库将此版本标为最新，而不是编号更大的 1.25.2”；Atom 模式在只能依据订阅源判断时也会明确说明。

### 刷新速度与网络诊断

- 网络请求改为异步执行，不再在整个往返期间占用调用线程。原先刷新并发实际被限制在 10 个线程。
- Atom 模式每个仓库的两个请求并行发出：40 个仓库、120 ms 延迟的测试服务器上，从 1973 ms 降到 522 ms。
- 刷新批次按每台主机的实际并发上限重新分档：40 个仓库的 API 模式批次从 803 ms 降到 402 ms；后台批次仍保持较低并发。
- 刷新历史中的慢项目会显示耗时所在的网络阶段：排队、DNS、建立连接、服务器等待或下载，并附带请求数、流量和连接复用情况。这些字段也会出现在导出文件和 MCP 输出中。

### 发行版、F-Droid 与安装

- 发行版历史和 F-Droid 版本历史中的旧版本可以直接查看 APK 信息并通过应用内安装流程安装。
- 所有文件的分享和下载统一走同一个出口，并遵循“分享到安装器”和下载方式设置；此前发行版列表与 F-Droid 历史会绕过这两项设置（#29）。
- F-Droid 会在仓库内部正确读取索引文件名；已保存但仍指向旧地址的 F-Droid 数据包会被自动替换。

### Liquid Glass 渲染

- 主要页面的卡片材质是单一颜色，模糊、透镜、折射和色散作用于单色背景时结果不变，因此这类玻璃直接按效果链的输出颜色绘制，不再每帧先录制离屏图层、再对它运行 RenderEffect。没有背景图的二级页面也会走这条路径。实机测试：BA 办公室总帧时间 p50 从 24.3 ms 降到 11.6 ms，设置页从 11.6 ms 降到 8.8 ms。
- 连续圆角的裁剪移到玻璃自身的图层内部完成。原来每张卡片移动时，都要在 RenderThread 上重新光栅化并上传一次遮罩。实机 RenderThread p50：OS 页降低 16%，MCP 页降低 18%。按钮、徽标、复选框和搜索框也走同一路径，外阴影与高光按原实现复现。
- 底栏和悬浮 dock 的完整形态与紧凑形态保持组合，隐藏的一方不参与布局、绘制，也不进入无障碍树。滑动开始时 UI 线程最慢的一帧，BA 从 13–23 ms 降到 6–10 ms。
- 卡片堆叠修复三处问题：展开后内容超出堆叠可显示范围的卡片不再进入堆叠，避免下载与分享按钮再也无法触及；堆叠中的卡片按堆叠状态裁剪，不再切掉仍然不透明的玻璃边缘；按压会落到手指下实际显示的那张卡片，不再被误送到它前方的卡片。

### 手势与导航

- 主页面切换 Tab 与松手吸附使用 Miuix 的页面导航弹簧，按 Tab 与用手指滑动到达页面的方式一致。
- 学生图鉴翻页改用 Miuix Cross-Axis 模式：列表仍在惯性滚动时，一次横滑即可直接翻页；音频进度条保留自己的横向拖动，竖向滚动和点击不会被误认为翻页。
- MCP 页面展开的卡片在进程被系统回收后仍会恢复。
- 导航顶层页面切换时会清除被覆盖页面的输入焦点；Shell 的“进入时聚焦输入框”保持正常。

### 安全

- 媒体会话按控制方是否受信任分配权限。其他应用找到 BGM 会话后只能读取播放状态，不能再控制播放；应用自身界面、媒体通知、Android Auto 与 Wear 不受影响。

### Baseline Profile 与构建

- Baseline Profile 已在本版本代码上重新采集：六段旅程在 A17 Phone AVD 上全部通过，完整采集约 11 分钟；合并产物包含 62,312 条 baseline 规则与 24,262 条 startup 规则，覆盖本版本新增的玻璃绘制路径与翻页代码。
- 新鲜度门禁现在也会报告采集之后变动的依赖版本，因为 profile 中很大一部分规则来自 Compose 与 Miuix。
- 构建基线：Gradle `9.8.0`、Android Gradle Plugin `9.4.1`、Kotlin `2.4.20`、Compose `1.12.1`、Ktor `3.6.0`、Coil `3.6.3`、Miuix `0.9.4-2afdbb39-SNAPSHOT`；WebDAV 客户端 dav4jvm 从提交快照换到正式版 `4.1.0`。

### 构建与安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- Target SDK：Android 17 / API 37
- 版本：`1.16.0`（`versionCode 11600999`）
- 构建基线：Java 21、Gradle `9.8.0`、Kotlin `2.4.20`、Android Gradle Plugin `9.4.1`、Compose `1.12.1`、Ktor `3.6.0`
- APK：`KeiOS_1.16.0.apk`
- 校验文件：`KeiOS_1.16.0.apk.sha256`

### 升级建议

建议所有 v1.15.x 用户升级到 v1.16.0。使用 GitHub 版本追踪的用户，尤其是追踪过更名项目、滚动预览标签或 CI 预发布的用户，会直接获得更准确的更新判断和更快的刷新；所有用户都会受益于更低的页面渲染开销。

## English

KeiOS v1.16.0 focuses on how reliably version tracking decides, how fast it refreshes and explains its network time, and how much Liquid Glass costs to draw. GitHub tracking now reads projects that restarted their numbering, pre-releases superseded by a stable or left unfed, and releases with nothing to install. Refresh requests no longer hold threads, and Atom mode sends its two requests at once. Glass cards on the main pages skip most of their offscreen effect chain over a flat field. Materials, motion, blur, and interaction feedback are unchanged.

### How Tracking Decides

- A project that restarted its numbering (for example, a rebrand that began again at `v1.0.1`) is no longer held down forever by an old, higher number. The repository's `releases/latest` is read only when the list itself shows that pattern, so an ordinary repository makes the same requests as before, and a failed lookup falls back to the local reading.
- A pre-release whose number begins with a shipped stable (for example `pre-1.4.2-…` once `1.4.2` is out) is retired by that stable instead of being offered as an update older than what is installed.
- A release with no downloadable file is never an update. Atom mode cannot see assets and makes no claim either way.
- Pre-release lines are judged by when their files last changed. A preview line with no update for 14 days after a stable is dropped from comparison, while an old tag whose CI artifacts keep being replaced still counts as active.
- Comparisons keep their confidence. When a local version shares no leading number with the release, the card says the comparison is uncertain rather than "up to date".
- Where the result would surprise, the card says why a release was chosen, for example "the repository marks this as its latest release, not the higher-numbered 1.25.2". Atom mode says so when the feed alone was all it had.

### Refresh Speed And Network Diagnostics

- Network requests run asynchronously and no longer hold the calling thread for the whole round trip. Refresh concurrency had silently been capped at ten threads.
- Atom mode sends its two requests per repository at once: 40 repositories against a 120 ms test server went from 1973 ms to 522 ms.
- Refresh batches are re-tiered against the real per-host limit: a 40-repository API batch went from 803 ms to 402 ms. Background batches stay well below interactive ones.
- A slow item in refresh history names the network phase that took its time: queued, DNS, connecting, server wait, or download, with the request count, bytes, and connection reuse. The same fields appear in the export and over MCP.

### Releases, F-Droid, And Installation

- Older builds in the release history and the F-Droid version history can open their APK info and install through the in-app installer.
- Every asset share and download goes through one hand-off that follows the "share to installer" and download settings. The release list and F-Droid history used to bypass both (#29).
- F-Droid reads index file names inside the repository correctly, and a saved F-Droid bundle that still links the old address is replaced.

### Liquid Glass Rendering

- The main pages' card material is a single colour, and blur, lens, refraction, and chromatic aberration all return that colour from it. Such glass now draws the chain's output colour directly instead of recording an offscreen layer and running a RenderEffect over it every frame. Routes without a background image take the same path. On a phone, BA Office total frame time p50 went from 24.3 ms to 11.6 ms, and Settings from 11.6 ms to 8.8 ms.
- The continuous-corner clip now happens inside the glass surface's own layer. Before, every moving card re-rasterised and uploaded a mask on the RenderThread each frame. Phone RenderThread p50 fell 16% on OS and 18% on MCP. Buttons, badges, the checkbox, and the search field take the same path, with the outer shadow and highlights reproduced from the original.
- The bottom bar and floating docks keep their full and compact forms composed; the hidden form is not laid out, drawn, or exposed to accessibility. The slowest UI-thread frame at scroll start went from 13–23 ms to 6–10 ms on BA.
- Three card-pile fixes: a card whose opened content cannot fit under the pile no longer joins it, so download and share buttons stay reachable; a piled card is culled on the pile's own state instead of cutting still-opaque glass edges; and a press reaches the card drawn under the finger instead of the card in front of it.

### Gestures And Navigation

- Main-page tab switches and swipe settling use Miuix's page-navigation spring, so a page lands the same way whether a tab or a finger moved it.
- The Student Guide pages with Miuix's Cross-Axis mode: while a list is still coasting, one horizontal swipe pages directly. The audio progress slider keeps its own horizontal drag, and vertical scrolls and taps are not mistaken for paging.
- Cards opened on the MCP page come back after the system kills the process.
- Focus is cleared from covered pages when the top route changes. The Shell's "focus input on entry" option still works.

### Security

- The media session grants commands by whether a controller is trusted. Another app that finds the BGM session can read playback state but can no longer control playback. The app's own UI, the media notification, Android Auto, and Wear are unaffected.

### Baseline Profile And Build

- The baseline profile is re-captured on this release's code: all six journeys pass on the A17 Phone AVD in a complete capture of about 11 minutes, and the merged output contains 62,312 baseline rules and 24,262 startup rules, covering this release's new glass drawing path and paging code.
- The freshness gate now also reports dependency versions that moved after the capture, since a large share of the profile's rules come from Compose and Miuix.
- Build baseline: Gradle `9.8.0`, Android Gradle Plugin `9.4.1`, Kotlin `2.4.20`, Compose `1.12.1`, Ktor `3.6.0`, Coil `3.6.3`, Miuix `0.9.4-2afdbb39-SNAPSHOT`. The dav4jvm WebDAV client moves from a commit snapshot to the `4.1.0` release.

### Build And Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- Target SDK: Android 17 / API 37
- Version: `1.16.0` (`versionCode 11600999`)
- Build baseline: Java 21, Gradle `9.8.0`, Kotlin `2.4.20`, Android Gradle Plugin `9.4.1`, Compose `1.12.1`, Ktor `3.6.0`
- APK: `KeiOS_1.16.0.apk`
- Checksum file: `KeiOS_1.16.0.apk.sha256`

### Upgrade Advice

Every v1.15.x user should upgrade to v1.16.0. GitHub version-tracking users, especially those tracking renamed projects, rolling preview tags, or CI pre-releases, get more accurate update decisions and faster refreshes directly, and everyone benefits from lower page rendering cost.
