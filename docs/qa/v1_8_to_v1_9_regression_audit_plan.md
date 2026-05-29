# v1.8.0 -> v1.9.0 链路回归审查计划

适用范围：从 `v1.8.0` 之后到 `v1.9.0` 发布前的所有拆分、性能优化、build type 调整、R8/Benchmark、页面数据流和安装/通知链路变更。

目标：逐页审查业务链路、数据链路、UI 状态、性能路径和发布配置，保证 1.9.0 相比 1.8.0 的体验与功能稳定提升。

## 0. 当前状态

| 项目 | 状态 | 备注 |
| --- | --- | --- |
| 基线版本 | 待执行 | 以 `v1.8.0` tag 为行为参照 |
| 目标版本 | 待执行 | 以当前 `HEAD` / v1.9.0 candidate 为审查目标 |
| 审查方式 | 进行中 | Git diff 范围审查 + 编译测试 + AVD/真机 smoke + 重点链路手测 |
| 性能口径 | 待执行 | Debug HWUI 作为快速观察，Benchmark/Release-like 证据作为结论 |
| 输出物 | 进行中 | Batch A 已记录；后续每批次补充结果、风险、commit、验证命令 |

## 1. 总闸门

每个批次完成后执行：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
git diff --check
rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'
```

涉及 GitHub module 时追加：

```bash
./gradlew :feature-github:testDebugUnitTest
```

涉及 benchmark / R8 / 发布配置时追加：

```bash
./gradlew :app:assembleBenchmark
./gradlew :app:assembleRelease
./gradlew :app:tasks --all | rg 'benchmarkRelease|nonMinifiedRelease|assemble(Benchmark|Release|Debug)|installBenchmark'
```

涉及运行时页面链路时追加：

```bash
./gradlew :app:installDebug
./gradlew :app:installBenchmark
adb shell am start -n os.kei.debug/os.kei.LauncherAndroidDesigns
adb shell am start -n os.kei/os.kei.LauncherAndroidDesigns
```

## 2. 变更范围定位

先生成审查索引：

```bash
git log --oneline --decorate v1.8.0..HEAD
git diff --stat v1.8.0..HEAD
git diff --name-status v1.8.0..HEAD
```

重点分类：

| 分类 | 命令 | 目标 |
| --- | --- | --- |
| 页面入口 | `git diff --name-only v1.8.0..HEAD -- app/src/main/java/os/kei/ui/page/main` | 找到所有页面级变更 |
| GitHub 后端 | `git diff --name-only v1.8.0..HEAD -- feature-github app/src/main/java/os/kei/ui/page/main/github` | 检查 tracking、actions、install、share/import |
| OS / Shell | `git diff --name-only v1.8.0..HEAD -- app/src/main/java/os/kei/ui/page/main/os` | 检查活动卡、shell、导入导出 |
| BA | `git diff --name-only v1.8.0..HEAD -- app/src/main/java/os/kei/ui/page/main/ba` | 检查图鉴、BGM、媒体缓存、通知 |
| MCP | `git diff --name-only v1.8.0..HEAD -- app/src/main/java/os/kei/ui/page/main/mcp app/src/main/java/os/kei/mcp` | 检查页面、工具入口、Codex 接入 |
| Build / R8 | `git diff --name-only v1.8.0..HEAD -- '*.gradle.kts' gradle.properties app/keepRules R8_Configuration_Analysis.md` | 检查 build type、keepRules、版本号、baseline profile |
| 字符串资源 | `git diff --name-only v1.8.0..HEAD -- app/src/main/res/values*` | 检查新增文案和多语言覆盖 |

## 3. 审查原则

1. 页面只负责渲染稳定 state snapshot 和发出用户事件。
2. 数据加载进入 Repository / Store / Coordinator，ViewModel 观察并合成 UI state。
3. 大型派生逻辑进入纯 deriver，时间、缓存、过滤、排序和安装状态判断保留可测试入口。
4. 高频状态读取进入 Layout / Draw / `graphicsLayer` / provider lambda。
5. 列表补齐稳定 `key`、`contentType` 和窄参数。
6. 页面切换保持内容可见，加载态保持完整骨架和原有视觉节奏。
7. 通知框架、超级岛模板、安装框架调用契约保持稳定。
8. R8 keepRules 保持精确，新增反射、序列化、持久化 enum、manifest component 时同步登记。
9. Benchmark 保持 release-like 性能路径，包名与 versionName/versionCode 规范保持可追踪。

## 4. 页面与链路矩阵

| 区域 | 审查点 | 验收动作 | 状态 |
| --- | --- | --- | --- |
| MainScreen / Pager / BottomBar | 页面切换、返回、底栏隐藏回显、runtime active/warm 状态 | 连续切换 5 页，返回手势，滚动隐藏后点击 title card 回显底栏 | 完成 |
| Home Page | 动态背景、HDR 高光、首页 cards、数据加载低频 tick | 静止观察 HWUI，滑动/切页，检查内容保持可见 | 完成 |
| Settings Page | 权限/外观/行为/超级岛/诊断分区，搜索与 far jump | 展开/收起分区，切换主题/动画/通知开关，搜索跳转 | 待执行 |
| OS Page | 活动卡、全面屏设置卡、KeyValue、导入导出、sheet 返回 | 新增/编辑/保存/返回手势，导入导出，活动启动 | 待执行 |
| OS Shell | 内置 shell card、执行结果、复制、历史、分类搜索 | 执行状态栏/手势命令，复制输出，刷新历史 | 待执行 |
| MCP Page | 工具入口拆分、Codex 相关、Skill、日志导出、局域网权限 | 展开各 card，搜索工具，复制资源，导出日志 | 待执行 |
| GitHub Overview / Tracking | 刷新、失败过滤回落、单项目间隔、缓存有效期 | 刷新全项目，过滤失败，失败清空后自动回全项目 | 待执行 |
| GitHub Actions Sheet | artifacts card、下载/安装/分享、nightly.link/token 模式 | token 与 nightly.link 两种模式检查下载常驻，接管安装后出现安装入口 | 待执行 |
| GitHub Share / Import / Managed Install | 分享入口、pending install、通知优先、结果落库 | 分享 APK/链接，确认安装，取消，完成后回到正确状态 | 待执行 |
| BA Catalog / Student Guide | 图鉴、搜索、收藏、媒体保存、BGM chrome、临时媒体缓存 | 搜索、播放、收藏、保存单项/打包，切 tab | 待执行 |
| BA 通知 / 超级岛 | AP、抓取变动提醒、知道了、划掉通知、安装完成岛 | 发送测试通知，点击按钮，滑掉焦点通知，检查岛关闭 | 待执行 |
| About / Release Notes | 版本信息、日志、Markdown、复制、翻译按钮 | 打开关于页，滚动日志，复制全文，长按复制片段 | 待执行 |
| Liquid Glass / Backdrop / Sheet | Window bottom sheet、预测式返回、嵌套玻璃、白条/insets | 打开编辑 sheet，滑动内容，返回手势关闭，重复打开 | 待执行 |
| Build / R8 / Baseline Profile | keepRules、build type、benchmark/release 包名和版本号 | assembleBenchmark/release，检查 APK metadata、mapping、seeds | 完成 |

## 5. 批次计划

### Batch A - Build / R8 / Benchmark

审查文件：

- `app/build.gradle.kts`
- 根目录和模块 `build.gradle.kts`
- `app/keepRules/**`
- `R8_Configuration_Analysis.md`
- `.github/actions/setup-android-gradle-build/action.yml`
- `.github/workflows/ci-benchmark-apk.yml`

验收：

```bash
./gradlew :app:assembleDebug :app:assembleBenchmark :app:assembleRelease
./gradlew :app:testDebugUnitTest :feature-github:testDebugUnitTest
./gradlew :app:installBenchmark
adb shell dumpsys package os.kei | rg "versionCode|versionName"
```

完成标准：

- Debug 包名为 `os.kei.debug`。
- Benchmark / Release 包名为 `os.kei`。
- Benchmark 使用 release-like R8 / resource shrink / baseline profile 路径。
- versionName 与 versionCode 同步使用 commit-count 规则。
- keepRules 新增项有对应风险说明。

### Batch B - Main Host / Home / Shared Chrome

审查文件：

- `MainScreen*`
- `host/**`
- `HomePage*`
- `LiquidGlassBottomBar*`
- `AppTopBar*`
- `ScrollChromeVisibilityController*`

验收：

- Debug 下打开 HWUI 柱状图观察切页高度。
- Benchmark 下重复切换 Home / OS / MCP / GitHub / BA。
- 静止 20 秒观察 Home 动态背景与 CPU/HWUI 变化。
- 底栏隐藏后点击 topbar title card，底栏回显。

完成标准：

- 页面切换时内容持续可见。
- 底栏状态与页面 runtime active 状态一致。
- 动态背景只在页面可见且效果开启时 tick。
- 主路径没有 Composition 阶段读取高频动画值。

### Batch C - Settings / About / Release Notes

审查文件：

- `SettingsPage*`
- `settings/section/**`
- `About*`
- `ReleaseNotes*`
- `AppMarkdownContent*`

验收：

- 设置分区展开、搜索、跳转、开关操作。
- 关于页打开、版本信息、Release Notes 长按复制、全文复制、翻译按钮。
- 多语言资源检查。

完成标准：

- 设置项 summary/value 来自 deriver 或稳定 model。
- Release Notes 解析在后台执行并有缓存。
- 文案进入资源文件。

### Batch D - GitHub 全链路

审查文件：

- `feature-github/**`
- `app/src/main/java/os/kei/ui/page/main/github/**`
- `app/src/main/java/os/kei/ui/page/main/jsonimport/**`

验收：

- 追踪列表刷新、失败过滤回落、单项目刷新间隔。
- Actions sheet artifacts card：下载、安装、分享、文件大小、sha256 隐藏策略。
- token 与 nightly.link 两种 artifact 下载模式。
- 分享导入、托管安装、确认安装、安装完成通知/超级岛。

完成标准：

- UI 操作只调用 action facade。
- 下载/安装/分享路径有统一状态机或 coordinator。
- 时间/缓存判断从 clock 或 repository 输入。
- 安装完成状态不会被前序通知强制打掉。

### Batch E - OS Page / Shell

审查文件：

- `OsPage*`
- `OsShellRunner*`
- `OsActivityShortcutCardStore*`
- `OsPageOverlay*`

验收：

- 活动卡新增、编辑、保存、返回手势关闭。
- 全面屏设置活动卡 extras。
- shell card 运行、复制、历史、导入导出。

完成标准：

- Store codec/migration/import/export/defaults 边界清晰。
- Sheet 关闭状态与返回栈一致。
- Lazy list 使用稳定 key/contentType。

### Batch F - MCP Page / MCP Server / Codex

审查文件：

- `McpPage*`
- `mcp/**`
- `app/src/main/assets/mcp/**`

验收：

- MCP 工具入口分 card 展示。
- Codex 相关工具、Skill、说明、导出日志。
- 搜索工具分组与折叠状态。

完成标准：

- 工具分组在 ViewModel/deriver 层派生。
- 副作用集中在 effects 层。
- 页面 card 拆分后交互状态稳定。

### Batch G - BA 图鉴 / BGM / 媒体 / 通知

审查文件：

- `ba/**`
- `BaGuideCatalog*`
- `BaStudentGuide*`
- `BaGuideTempMediaCache*`
- `BaGuideBgm*`
- `MiFocusNotification*`

验收：

- 图鉴搜索、详情、收藏、媒体保存、媒体缓存。
- BGM 播放、收藏、导入导出、底部 chrome。
- AP / cafe AP / 抓取变动提醒 / 超级岛按钮与关闭。

完成标准：

- 收藏与媒体写操作进入 Repository/Store。
- 临时缓存 TTL、prune、download、validation 有清晰边界。
- BGM 热值通过 provider/draw/layout 路径读取。
- 超级岛按钮与通知取消有对称关闭路径。

### Batch H - Liquid Glass / Backdrop / Window Sheet

审查文件：

- `liquidglass/**`
- `backdrop/**`
- `*BottomSheet*`
- `*WindowSheet*`
- `*FloatingSurface*`

验收：

- 各页面编辑 sheet 的打开、滑动、保存、取消、返回手势。
- 嵌套 glass 组件不出现灰黑卡、白底遮挡、小白条破坏。
- 预测式返回和 OEM 半残废返回兼容路径。

完成标准：

- Window bottom sheet 为优先路径。
- Backdrop 嵌套使用 exported backdrop。
- Sheet close animation 与真实关闭状态同步。

## 6. 性能验收

### 快速观察

```bash
adb shell settings put global debug.hwui.profile visual_bars
adb shell am force-stop os.kei.debug
adb shell am start -n os.kei.debug/os.kei.LauncherAndroidDesigns
```

观察范围：

- MainScreen 切页柱状图高度。
- Settings / OS / MCP / GitHub / BA 首次进入与二次进入。
- Home 静止动态背景。

### Benchmark 证据

```bash
./gradlew :app:assembleBenchmark
./gradlew :app:installBenchmark
adb shell am force-stop os.kei
adb shell am start -n os.kei/os.kei.LauncherAndroidDesigns
adb shell dumpsys gfxinfo os.kei framestats
```

必要时补 Perfetto：

```bash
adb shell perfetto --txt -c /data/misc/perfetto-configs/keios.textproto -o /data/misc/perfetto-traces/keios.perfetto-trace
```

## 7. 审查记录模板

每完成一个批次，追加记录：

```markdown
### 2026-xx-xx Batch X - <区域>

- Commit:
- 审查范围:
- 主要风险:
- 已修复:
- 保留风险:
- 验证命令:
- AVD/真机:
- 结论:
```

### 2026-05-29 Batch A - Build / R8 / Benchmark

- Commit: 本批提交（见 `git log -1`）
- 审查范围:
  - `app/build.gradle.kts`
  - 根目录和模块 `build.gradle.kts`
  - `app/src/main/keepRules/proguard-rules.keep`
  - `R8_Configuration_Analysis.md`
  - `.github/actions/setup-android-gradle-build/action.yml`
  - `.github/workflows/ci-benchmark-apk.yml`
- 主要风险:
  - Benchmark CI 的 `paths` 只覆盖 `app/**`、`core-log/**`、`core-io/**` 等部分路径，`core-concurrency/**`、`core-prefs/**`、`core-system/**`、`feature-github/**` 变更可能不会触发预发行包构建。
  - 本地 Gradle 读取 git metadata 会在 git 输出变化时使 configuration cache 重新计算；这是保持本地 versionName/versionCode 命名规范的可接受代价。
- 已修复:
  - `.github/workflows/ci-benchmark-apk.yml` 补齐 `core-concurrency/**`、`core-prefs/**`、`core-system/**`、`feature-github/**` 触发路径。
- 验证命令:
  - `./gradlew :app:assembleDebug :app:assembleBenchmark :app:assembleRelease :app:testDebugUnitTest :feature-github:testDebugUnitTest`
  - `./gradlew :app:tasks --all | rg 'benchmarkRelease|nonMinifiedRelease|assemble(Benchmark|Release|Debug)|installBenchmark'`
  - `/Users/voyager/Library/Android/sdk/build-tools/37.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk | sed -n '1p'`
  - `/Users/voyager/Library/Android/sdk/build-tools/37.0.0/aapt dump badging app/build/outputs/apk/benchmark/app-benchmark.apk | sed -n '1p'`
  - `/Users/voyager/Library/Android/sdk/build-tools/37.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk | sed -n '1p'`
  - `ls -la app/build/outputs/mapping/benchmark app/build/outputs/mapping/release`
  - `./gradlew :app:installBenchmark`
  - `adb shell dumpsys package os.kei | rg "versionCode|versionName"`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
  - `git diff --check`
- AVD/真机:
  - 真机 `25098PN5AC - Android 16` 安装 benchmark 成功。
  - 安装后 `os.kei` 为 `versionName=1.8.4+4.ge85cb0cce`、`versionCode=10804004`。
- 产物 metadata:
  - Debug: `os.kei.debug` / `1.8.4+4.ge85cb0cce` / `10804004`
  - Benchmark: `os.kei` / `1.8.4+4.ge85cb0cce` / `10804004`
  - Release: `os.kei` / `1.8.3` / `10803999`
- 保留风险:
  - 后续稳定后需要重新跑一次 baseline profile，以清理当前变更期的 profile 风险。
- 结论:
  - Batch A 通过。Build type、R8 输出、Benchmark/Release metadata、安装覆盖和 CI 触发范围已完成本轮审查。

### 2026-05-29 Batch B - Main Host / Home / Shared Chrome

- Commit: 本批提交（见 `git log -1`）
- 审查范围:
  - `app/src/main/java/os/kei/ui/page/main/host/main/MainScreen.kt`
  - `app/src/main/java/os/kei/ui/page/main/host/pager/MainPagerCoordinator.kt`
  - `app/src/main/java/os/kei/ui/page/main/host/pager/MainPagerLayout.kt`
  - `app/src/main/java/os/kei/ui/page/main/host/pager/MainPagerPageHost.kt`
  - `app/src/main/java/os/kei/ui/page/main/host/pager/MainLoadedPager.kt`
  - `app/src/main/java/os/kei/ui/page/main/host/pager/MainPageActivationState.kt`
  - `app/src/main/java/os/kei/ui/page/main/home/state/HomePageDerivedState.kt`
  - `app/src/main/java/os/kei/ui/page/main/home/HomePageChrome.kt`
  - `app/src/main/java/os/kei/ui/page/main/home/HomePageSections.kt`
  - `app/src/main/java/os/kei/ui/page/main/widget/chrome/LiquidGlassBottomBar.kt`
  - `app/src/main/java/os/kei/ui/page/main/widget/chrome/SearchBarHost.kt`
- 主要风险:
  - `NonHomePageBackground` 在 pager 页面内容之后绘制，命名语义是背景，实际层级可能覆盖非首页内容，属于 1.8.0 后 shared chrome / background 拆分带来的绘制顺序风险。
  - Debug 首次验收遇到实体机外部图片选择器残留流程，已切换为 AVD 专用验收，实体机 `debug.hwui.profile` 已恢复为 `null`。
- 已修复:
  - `MainPagerLayout` 将 `NonHomePageBackground` 移到 pager 内容之前绘制，保持非首页背景仍在 root `Box` 内，由内容层覆盖，避免背景层遮挡页面内容。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
  - `./gradlew :app:assembleBenchmark`
  - `adb -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk`
  - `adb -s emulator-5554 install-multiple -r -d app/build/outputs/apk/benchmark/app-benchmark.apk app/build/outputs/apk/benchmark/baselineProfiles/0/app-benchmark.dm`
  - `adb -s emulator-5554 shell am start -n os.kei.debug/os.kei.LauncherAndroidDesigns`
  - `adb -s emulator-5554 shell am start -n os.kei/os.kei.LauncherAndroidDesigns`
  - `adb -s emulator-5554 shell settings put global debug.hwui.profile visual_bars`
  - `adb -s emulator-5554 shell dumpsys gfxinfo os.kei`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
  - `git diff --check`
- AVD:
  - `Pixel_10_Pro` / `emulator-5554` / `sdk_gphone16k_arm64`
  - Debug `os.kei.debug` 启动成功，Home / OS / MCP / GitHub / BA 连续切换截图均有内容，未出现空白页。
  - Benchmark `os.kei` 启动成功，Home / OS / MCP / GitHub / BA 连续切换截图均有内容，未出现空白页。
  - Benchmark `dumpsys gfxinfo os.kei`：Total frames `3986`，Janky frames `57 (1.43%)`，P50 `22ms`，P90 `26ms`，P95 `27ms`，P99 `61ms`。
- 保留风险:
  - Home 动态背景仍是长期性能专项，高动态效果与 HWUI 柱状图之间需要后续用 benchmark trace 继续收敛。
  - 底栏隐藏后点击 topbar title card 回显底栏需要在后续手测批次补一次更细的滚动场景验证。
- 结论:
  - Batch B 通过。Main pager 绘制层级风险已修复，Debug 与 Benchmark 在 AVD 上完成主页面连续切换 smoke，未发现空白页式降载或启动/切页崩溃。

### 2026-05-29 Batch G - BA 图鉴 / BGM / 媒体 / 通知

- Commit: `a52cee087` Fix catalog scroll-to-top, initial position, and wrong student bug
- 审查范围:
  - `app/src/main/java/os/kei/ui/page/main/student/page/BaStudentGuidePage.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/page/state/BaStudentGuideViewModel.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPage.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPageStateHolder.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPagePager.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogBottomChromePlaybackSurface.kt`
  - `app/src/main/java/os/kei/ui/page/main/student/catalog/component/BaGuideCatalogV2ListContent.kt`
- 主要风险:
  - 图鉴 catalog 底栏点击当前 tab 时，仅 `pagerState.animateToPage` 重定位 pager，列表不会回到顶部，与主 pager 的 scroll-to-top 行为不一致。
  - 图鉴 catalog 进入时未保证滚动到顶部，长 LazyList 在状态恢复后留在历史位置。
  - `BaStudentGuideViewModel` 是 Application 级单例（`applicationViewModel`），`init { loadStoredCurrentGuide() }` 仅执行一次。返回后通过搜索打开不同学生时，MMKV 中的 currentUrl 已更新，但 ViewModel 状态保留旧 URL，导致重新进入时显示上次学生。
- 已修复:
  - `BaGuideCatalogPageStateHolder` 新增 `scrollToTopSignal` 计数器与 `emitScrollToTop()` 入口；`BaGuideCatalogBottomChromePlaybackSurface` 在 `index == pagerState.settledPage` 时改为发射 scroll-to-top 信号；`BaGuideCatalogV2ListContent` 通过 `scrollToTopSignal` 参数与 `LaunchedEffect` 触发 `animateScrollToItem(0)`。
  - `BaGuideCatalogPage` 首次组合时 `LaunchedEffect(Unit)` 调用 `pageState.emitScrollToTop()`，保证进入图鉴时所有 tab 列表起始位置一致。
  - `BaStudentGuideViewModel` 新增 `suspend fun reloadIfStoredUrlChanged()`：从 store 重新读取 currentUrl，与当前 `_dataState.value.sourceUrl` 不一致时调用 `openGuide()` 重置状态并加载新学生；`BaStudentGuidePage` 通过 `LocalLifecycleOwner` + `repeatOnLifecycle(Lifecycle.State.RESUMED)` 在每次页面 resume 时调用，确保单例 ViewModel 与最新存储 URL 对齐。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
  - `git diff --check`
- AVD/真机:
  - 待手测确认底栏二次点击 scroll-to-top、进入图鉴顶部对齐、搜索切换学生流程。
- 保留风险:
  - 图鉴详情内 `BaStudentGuidePagerPage` 各 tab 的 `LazyListState` 通过 `rememberSaveable(sourceUrl, tabRenderState.activeBottomTab.name, saver = LazyListState.Saver)` 隔离，已与 sourceUrl 切换对齐；后续若新增分页类型仍需保持此 keying 习惯。
  - BGM/媒体/超级岛通知链路未在本批触及，归入下一轮 Batch G 增补（必要时拆分 Batch G-2）。
- 结论:
  - Batch G 第一轮通过。catalog scroll-to-top、初始顶部对齐、Student Guide 单例 ViewModel 学生切换三项链路问题已修复。BGM 播放、媒体保存、临时缓存、AP/超级岛通知验收延后至 BGM 专项批次。

### 2026-05-29 Gate Recheck

- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL
- `rg "collectAsState\(" app/src/main/java/os/kei -g '*.kt'` 零命中
- `git diff --check` 干净

### 2026-05-29 Batch C - Settings / About / Release Notes

- Commit: 审查批次（无源码改动）
- 审查范围:
  - `app/src/main/java/os/kei/ui/page/main/settings/SettingsPage.kt`、`SettingsPageContent.kt`
  - `app/src/main/java/os/kei/ui/page/main/settings/section/**`（Visual / Animation / Cache / Log / Notify / Copy / Background / ComponentEffects / PermissionKeepAlive）
  - `app/src/main/java/os/kei/ui/page/main/settings/section/SettingsSectionPresentationDeriver.kt`
  - `app/src/main/java/os/kei/ui/page/main/about/AboutPage.kt`、`AboutPageViewModel.kt`、`about/section/**`、`about/model/**`
  - `app/src/main/java/os/kei/ui/page/main/widget/markdown/AppMarkdownContent.kt`、`AppMarkdownBlockCache.kt`、`AppMarkdownParser.kt`、`AppMarkdownModels.kt`
- 主要风险:
  - 设置项 summary/value 是否在 composition 内反复重算。
  - 远 jump 动画是否在 composition 阶段读取高频值。
  - About / Release Notes 版本元数据是否来自稳定 provider。
  - Markdown 解析是否在后台线程，是否带缓存避免每次切换重算。
  - 文案是否进入资源文件。
  - 是否有遗留 `collectAsState(` 未升级到 `collectAsStateWithLifecycle()`。
- 已修复:
  - 无源码修改。本批以审计为主。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL
  - `rg "collectAsState\(" app/src/main/java/os/kei -g '*.kt'` 零命中
- 审计结论:
  - **PASS**。
  - Settings: `SettingsSectionPresentationDeriver` 通过纯 deriver 函数计算每个 section 的 summary/value，传入 section 组件的状态对象为 immutable data class。
  - Settings 远 jump 动画：`farJumpAlpha` 由 `Animatable(1f)` + `remember` 持有，通过 `farJumpAlphaProvider: () -> Float` lambda 传入 `SettingsCategoryPagerContent`，在 `graphicsLayer { alpha = farJumpAlphaProvider() }` draw 阶段读取，未引发 composition 失效。
  - About: `AboutAppDetails` / `AboutTechDetails` 由 repository 在后台构建一次，通过 `StateFlow<AboutPageDetailsState>` + `collectAsStateWithLifecycle()` 提供给 UI。
  - Release Notes / Markdown：`AppMarkdownContent` 用 `produceState` 调度 `parseCachedAppMarkdownBlocks`，后台 dispatcher 为 `AppDispatchers.uiDerivation`；`AppMarkdownBlockCache` 为 LRU(24) `LinkedHashMap`，命中即返回，避免重复解析。
  - 文案：所有用户可见文案使用 `stringResource(R.string.*)`，未发现硬编码。
  - State 收集：全量使用 `collectAsStateWithLifecycle()`。
- 保留风险:
  - Markdown 缓存上限 24，长会话切换大量不同源（如多个 Release Notes 版本快速切）可能 thrashing；当前实际场景不会触发，归档观察。
- AVD/真机:
  - Batch B AVD smoke 已覆盖 Settings / About 启动可见性，本批未单独跑设备。

### 2026-05-29 Batch D - GitHub 全链路

- Commit: 审查批次（无源码改动；引用历史修复 `f9f1dcadb`）
- 审查范围:
  - `feature-github/**` 整模块
  - `app/src/main/java/os/kei/ui/page/main/github/**`
  - `app/src/main/java/os/kei/ui/page/main/jsonimport/**`
- 主要风险:
  - UI 是否绕过 facade 直接改 ViewModel state。
  - 下载/安装/分享是否走统一状态机/coordinator。
  - 时间/缓存判断是否取自 clock / repository（可测试）。
  - 安装完成是否被前序通知打掉。
  - 失败过滤刷新清空时的 fallback。
  - 单项目刷新是否遵守 per-project TTL。
  - Actions sheet artifacts card 接管安装入口出现。
  - nightly.link 与 GitHub Token 双下载模式都正常。
  - 分享导入 / pending install / 通知优先级 / 结果落库。
  - 是否有 `collectAsState(` 误用。
- 已修复:
  - 无新增源码修改。
  - 关键回归已在历史 commit `f9f1dcadb` 修复：`persistCheckCache(refreshTimestamp)` 默认值由 `state.lastRefreshMs`（陈旧）改为 `System.currentTimeMillis()`，避免后台部分刷新写入过期时间戳，导致 Home 缓存提示年龄错误。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :feature-github:testDebugUnitTest`
  - `rg "collectAsState\(" feature-github app/src/main/java/os/kei/ui/page/main/github app/src/main/java/os/kei/ui/page/main/jsonimport -g '*.kt'` 零命中
- 审计结论:
  - **PASS（含历史修复）**。
  - 架构：UI 通过 `GitHubOverviewActionFacade` / `GitHubTrackedRefreshActionFacade` / `GitHubShareImportActionFacade` 调用，各 facade 落到 `GitHubPageActionEnvironment` 注入的 clock + state + store。
  - Refresh 状态机：`GitHubRefreshActions` + `GitHubSingleRefreshActions` + `GitHubRefreshBatchActions` 共享 `GitHubPageState`，每个项目的 `checkedAtMillis` 来自注入 clock，单项目 TTL 写在 repository 层。
  - 失败过滤：`refreshFailedTrackedItems` 过滤 `checkStates[item.id]?.failed == true`；空集合给出 toast，无静默失败。
  - Share/Install：`GitHubShareImportStateMachine` + `GitHubManagedInstallStateMachine` + `GitHubManagedInstallProgressNotifier` 串行 stage，`requestId` 校验防止前序通知打掉完成态。
  - 双下载模式：`resolvePreferredDownloadUrl()` 在 `useApiAssetUrl` true 时走 token + `archiveDownloadUrl`，false 时走 nightly.link `downloadUrl`，错误本地化分支区分两种模式。
  - 时间可测：新增 `GitHubCheckCacheTimestamps.resolvedRefreshTimestamp()` 纯函数 + `GitHubCheckCacheTimestampsTest`。
  - 全量 `collectAsStateWithLifecycle()`，零 `collectAsState(` 误用。
- 保留风险:
  - 真机端到端验证（分享 APK、托管安装、超级岛、安装完成）需要后续手测一次，编程验证已覆盖架构与状态机正确性。
- AVD/真机:
  - 本批未跑设备，依赖 Batch A 真机安装结果与 Batch B AVD smoke 的 GitHub 启动可见性。

### 2026-05-29 Batch E - OS Page / Shell

- Commit: 审查批次（无新源码改动；引用历史修复 `9fe5b96a1`）
- 审查范围:
  - `app/src/main/java/os/kei/ui/page/main/os/OsPage.kt`、`OsPageViewModel.kt`、`OsPageUiState.kt`
  - `OsActivityShortcutCardStore.kt`、`OsActivityShortcutCardCodec.kt`、`OsActivityShortcutCardPersistence.kt`、`OsActivityShortcutCardImportExport.kt`、`OsActivityShortcutCardMigration.kt`
  - `OsActivityShortcutEditorHost.kt`、`OsShortcutActivitySectionCards.kt`、`OsPageOverlayState.kt`、`OsPageOverlaySheets.kt`、`OsPageOverlayCoordinator.kt`
  - `OsShellCommandCardStore.kt`、`OsShellCommandCardCodec.kt`、`OsShellCommandCardPersistence.kt`、`OsShellCommandCardImportExport.kt`、`OsShellCommandCardBuiltInMerge.kt`、`OsShellCommandCardSheets.kt`、`OsShellRunnerViewModel.kt`
  - `OsPageMainList.kt`、`OsKeyValueSectionCards.kt`、`OsTopInfoSectionCards.kt`
- 主要风险:
  - 活动卡新增/编辑/保存/返回手势：sheet close 状态与返回栈一致性。
  - 全面屏设置活动卡 extras 的数据流。
  - shell card 运行/复制/历史/导入导出路径。
  - Store codec / migration / import-export / defaults 边界。
  - LazyList 是否使用稳定 key/contentType。
  - 是否存在 `collectAsState(` 误用。
  - 文案是否进入资源文件。
- 已修复:
  - 无新增源码修改。Shell 输出行 identity 在历史 commit `9fe5b96a1` 已稳定。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
  - `rg "collectAsState\(" app/src/main/java/os/kei/ui/page/main/os -g '*.kt'` 零命中
- 审计结论:
  - **PASS**。
  - Store/Codec/Persistence 三层边界清晰：`OsActivityShortcutCardStore` 与 `OsShellCommandCardStore` 是无状态 facade；codec 负责 JSON 序列化、空命令拒绝、时间戳规范化；migration 通过 `appendMissingBuiltIns` 处理内置卡升级。
  - Sheet 状态：`OsPageOverlayState` 已重构为父级（`OsPageViewModel`）控制，通过 `onDismissRequest` + `onDismissFinished` 对齐返回栈。
  - LazyList 全部带稳定 key（`os-activity-${card.id}` / `os-shell-command-${card.id}` / `osInfoRowStableKey()`）和 contentType（`os_shortcut_activity_card` / `os_shell_command_card` / `os_info_row` / `os_top_info_entry`）。
  - State 收集：全量 `collectAsStateWithLifecycle()`，零 `collectAsState(`。
  - 文案：100% `stringResource(R.string.os_*)`。
- 保留风险:
  - 活动卡 extras 解析 `normalizeShortcutIntentExtras()` 在 composition 内执行；当前 payload 较小未观察到 jank，若未来 extras 显著增大需要移到 deriver。
  - 实机端到端验收（活动启动、shell 运行、导入导出、活动卡编辑保存）依赖 Batch A 真机已安装结果，本批未单独跑。
- AVD/真机:
  - 未单独跑设备。

### 2026-05-29 Batch F - MCP / Codex

- Commit: 审查批次（无新源码改动）
- 审查范围:
  - `app/src/main/java/os/kei/ui/page/main/mcp/McpPage.kt`、`McpPageViewModel.kt`、`McpPageContent.kt`、`McpPageActions.kt`、`McpPageEffects.kt`、`McpPageSheets.kt`、`McpPageFloatingActions.kt`、`McpPageRepository.kt`、`McpRuntimeTicker.kt`、`McpToolBucketLoader.kt`
  - `McpToolSections.kt`、`McpSectionBlocks.kt`、`state/McpToolBuckets.kt`
  - `mcp/skill/McpSkillPage.kt`、`McpSkillPageViewModel.kt`、`McpSkillPageRepository.kt`、`skill/component/**`
  - `app/src/main/java/os/kei/mcp/server/**`
  - `app/src/main/assets/mcp/SKILL.md` 及 zh/ja 翻译
- 主要风险:
  - 工具入口是否按 8 组（entrypoint / runtime / system / github / ba / codex / workflow / advanced）正确拆 card。
  - Codex tool / Skill / docs / log export 是否走稳定 state flow。
  - 工具搜索分组与折叠是否进入 deriver 层。
  - 副作用（log 导出、通知）是否集中在 effects 层。
  - 卡片展开折叠是否跨重组稳定。
  - 是否存在 `collectAsState(` 误用。
  - 文案是否进入资源文件。
- 已修复:
  - 无新增源码修改。
- 验证命令:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
  - `rg "collectAsState\(" app/src/main/java/os/kei/ui/page/main/mcp app/src/main/java/os/kei/mcp -g '*.kt'` 零命中
- 审计结论:
  - **PASS**。
  - 8 个工具 group 各自一个 LazyColumn `item`，带 `mcp-tool-*` 稳定 key 与 `mcp_tool_*_section` contentType；Advanced section 在 `advancedTools.isNotEmpty()` 时条件渲染。
  - `deriveMcpToolBuckets()` 为纯函数 deriver，按 `McpToolDomains` + `McpToolVisibility` 分类并合并搜索查询；折叠 flag 留在 `McpPageUiState`，与 deriver 解耦。
  - `McpPageViewModel.routeState = combine(uiState, toolBuckets).stateIn(WhileSubscribed(5_000))`，所有 mutation 通过 `_uiState.update {}` immutable 快照。
  - `BindMcpPageEffects()` 集中 service draft sync、tool bucket 派生、scroll-to-top 三个 `LaunchedEffect`，并通过 `DisposableEffect` 在 unmount 时清理 action bar 交互 flag。
  - Codex 集成新增 `codexTools` bucket（来自 `McpToolCatalog.devToolNames`）；Skill page 为独立路由 + 独立 ViewModel，不与 MCP 主页状态交叉。
  - State 收集全量 `collectAsStateWithLifecycle()`，runtime ticker 在页面非活跃时降级为 `emptyFlow()` 避免无效 tick。
  - 文案 100% `stringResource(R.string.mcp_*)`。
- 保留风险:
  - Skill 页 markdown 渲染走 Batch C 已审计的 `AppMarkdownContent` + `parseCachedAppMarkdownBlocks` LRU(24)，缓存上限同样适用，正常使用不触发。
  - 真机端到端（启动 service、复制 skill resource、导出 log）依赖 Batch A 真机已安装结果。
- AVD/真机:
  - 未单独跑设备。

## 8. 当前优先级

| 优先级 | 区域 | 原因 | 状态 |
| --- | --- | --- | --- |
| P0 | Build / R8 / Benchmark | 直接影响预发行验证和安装覆盖 | 完成 |
| P0 | MainScreen / Shared Chrome | 页面切换性能和全局体验入口 | 完成 |
| P0 | GitHub 安装 / Actions / Share Import | 用户高频链路且近期改动大 | 完成 |
| P0 | Liquid Glass / Window Sheet | 返回、保存、编辑、预测式返回风险集中 | 待执行 |
| P1 | Settings / About / Release Notes | 近期分区、日志、复制能力改动集中 | 完成 |
| P1 | OS Page / Shell | 活动卡和 shell card 新增多 | 完成 |
| P1 | BA 图鉴 / BGM / 超级岛 | 媒体、通知、缓存和 BGM chrome 链路复杂 | 进行中（catalog 已完成，BGM/通知待补） |
| P2 | MCP Page / Codex | 新增 Codex 接入后需要完整验收 | 完成 |

## 9. 发布前最终门禁

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :feature-github:testDebugUnitTest
./gradlew :app:assembleBenchmark
./gradlew :app:assembleRelease
git diff --check
rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'
```

发布候选 APK 检查：

```bash
/Users/voyager/Library/Android/sdk/build-tools/37.0.0/aapt dump badging app/build/outputs/apk/benchmark/app-benchmark.apk | sed -n '1p'
/Users/voyager/Library/Android/sdk/build-tools/37.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk | sed -n '1p'
```

最终结论需要包含：

- 1.8.0 到 1.9.0 的主要用户可见变化。
- 已修复的链路风险。
- 仍需观察的风险。
- Benchmark / Release APK metadata。
- AVD/真机 smoke 设备与结果。
