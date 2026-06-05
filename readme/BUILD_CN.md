# KeiOS 构建指南 (CN)

[主 README](CN.md) · [文档索引](INDEX.md)

## 安装方式

- 稳定安装建议直接使用 [GitHub Releases](https://github.com/hosizoraru/KeiOS/releases)。
- 当前公开标签基线为 [KeiOS v1.9.0](https://github.com/hosizoraru/KeiOS/releases/tag/v1.9.0)。
- `master` 当前作为 v1.9.0 发布基线，覆盖 GitHub/Git 追踪、WebDAV 同步、MCP 服务模块、
  BA 多账号、Actions 历史和 UI/性能打磨。
- 本构建指南覆盖源码本地构建、Debug 包生成和贡献者开发流程。
- 使用 `常用本地命令` 中的命令即可产出 Debug、Benchmark 与 Release APK。

## 本地构建说明（Local Build Notes）

本项目有意将机器相关路径与密钥排除在版本控制之外。

### 构建基线

- Gradle daemon、Java 编译、Kotlin JVM 目标统一为 Java 21。
- 跨平台 daemon toolchain 配置已在 `gradle/gradle-daemon-jvm.properties` 中跟踪（JetBrains Java 21）。
- Android 构建基线：`compileSdk=37`、`targetSdk=37`、`minSdk=35`。
- Gradle Wrapper：`9.5.1`；Kotlin 插件：`2.3.21`；Android Gradle Plugin：`9.2.1`；
  Compose 运行库：`1.11.2`；Ktor：`3.5.0`。
- Release APK 读取 `app/src/release/generated/baselineProfiles/` 中已生成的 Baseline Profiles。
  Benchmark 构建会接入同一份 profile 目录，用于预发行性能验证。
- 本地 JDK 路径与 Token 保留在未跟踪的本机配置文件中。

### 版本号规则

- CI 会在 Gradle 外根据当前 HEAD 已合入的最新 semver tag 注入版本元数据。
- 本地构建可在 `~/.gradle/gradle.properties` 或 `local.properties` 覆盖
  `keios.version.name`、`keios.nextVersion.name`、`keios.version.anchorTag` 与 `keios.git.*`。
- Release 构建使用 semver 基础版本，例如 `1.9.0`。
- Debug / Benchmark 构建使用下一 patch 版本，并追加 commit 数和短 SHA，例如 `1.9.1+12.gabcdef0`。
- 缺少 CI 注入 metadata 的本地构建会直接读取 git metadata，让 versionName 和 versionCode
  保持同一套 commit 数后缀规则。
- 包名链路保持精简：Debug 安装为 `os.kei.debug`；Benchmark 与 Release 安装为 `os.kei`。
- 当前 CI artifact 名称保持简洁：`KeiOS_<versionName>`，APK 文件名为 `KeiOS_<versionName>.apk`。
- workflow run name 会用 `D#<run_number>` 表示 Debug、`B#<run_number>` 表示 Benchmark；job summary
  会输出完整 commit SHA、run number、versionName、versionCode、application ID 与 artifact digest。

### 必需的本地凭据（依赖解析）

`settings.gradle.kts` 通过 GitHub Packages 拉取 Miuix 依赖，需要本地凭据。请在 `~/.gradle/gradle.properties` 中配置：

```properties
gpr.user=<你的_github_用户名_或_actor>
gpr.key=<具备_packages_read_权限的_github_token>
```

Gradle 配置也支持环境变量兜底：

- `GITHUB_ACTOR`
- `GITHUB_TOKEN`

### 可选本地覆盖项

推荐通过 `~/.gradle/gradle.properties`（优先）或 `local.properties` 做本机覆盖：

```properties
# 仅在你的机器无法自动解析 JDK 时再设置
org.gradle.java.home=/path/to/your/jdk

# 可选：本地覆盖 Miuix 版本
miuix.version=0.9.1-fb3d442d-SNAPSHOT
```

JDK 兜底示例路径：

- macOS Android Studio JBR：`/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Windows Android Studio JBR：`C:\\Program Files\\Android\\Android Studio\\jbr`

### 常用本地命令

```bash
# Kotlin 编译检查
./gradlew :app:compileDebugKotlin

# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest
```

### v1.9.0 发布门禁

打 tag 或发布稳定版 APK 前建议跑完：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:assembleBenchmark
git diff --check
```

本次发布建议重点复查：

- GitHub、Gitee、通用 Git、Actions、忽略版本和托管安装链路可以正常打开和执行。
- WebDAV 同步会在同步或上传前展示远端刷新与变更计划确认。
- BA 账号提醒与 AP / 咖啡厅 / 活动日历 / 卡池通知入口能跳转到对应账号。
- MCP 本地服务能启动、拒绝未授权请求、输出 Claw 接入资源，并保持日志响应。
- 液态玻璃弹出面板默认走 Miuix 标准 sheet，用户可在设置里手动启用。
- Release APK 签名、版本元数据、R8/minify 输出和启动完成验证。
- GitHub Release 发布文案可直接参考 [Release Notes v1.9.0](RELEASE_V1.9.0.md)。

### 截图基线

共享 UI 基础组件已接入 `Roborazzi`，基线图位于 `app/src/test/screenshots/design-system`。

```bash
# 录制 / 刷新截图基线
./gradlew :app:recordRoborazziDebug --tests "os.kei.ui.page.main.widget.AppDesignSystemScreenshotTest"

# 校验当前渲染结果是否与基线一致
./gradlew :app:verifyRoborazziDebug --tests "os.kei.ui.page.main.widget.AppDesignSystemScreenshotTest"
```

当前基线覆盖范围：

- `AppCardHeader`
- `AppOverviewCard`
- 统一后的列表正文骨架与说明块节奏

## GitHub Actions：CI / Debug APK

工作流路径：`.github/workflows/ci-debug-apk.yml`

- 触发方式：`master` 分支 `push` 与非 draft `pull_request`；仅 Markdown/readme 变更会跳过。
- 手动触发：`workflow_dispatch`，可选 `commit`（commit SHA / branch / tag）。
- 构建产物：自动构建并上传 Debug APK 到 GitHub Actions。
- 使用场景：开发过程中的快速预览与验证。
- 签名：仅 Debug / Benchmark artifact 使用 `app/signing/` 内的共享 CI debug keystore。
- 保留期：14 天。
- nightly.link：`https://nightly.link/hosizoraru/KeiOS/workflows/ci-debug-apk/master`
- APK 文件名格式：`KeiOS_<versionName>.apk`。
- Artifact 名称格式：`KeiOS_<versionName>`。

## GitHub Actions：CI / Benchmark APK

工作流路径：`.github/workflows/ci-benchmark-apk.yml`

- 触发方式：`master` 分支 `push`；仅 Markdown/readme 变更会跳过。
- 手动触发：`workflow_dispatch`，可选 `commit`（commit SHA / branch / tag）。
- 默认行为：`commit` 为空时构建所选分支的最新提交。
- 构建任务：`./gradlew :app:assembleBenchmark --stacktrace`。
- 构建产物：自动上传 Benchmark APK 到 GitHub Actions Artifact。
- 使用场景：以接近 Release 的 R8、资源收缩和 Baseline Profile 链路做预发行性能验证。
- 签名：CI Benchmark artifact 使用 `app/signing/` 内的共享 debug keystore；本地签名构建可复用 release 签名。
- 保留期：14 天。
- nightly.link：`https://nightly.link/hosizoraru/KeiOS/workflows/ci-benchmark-apk/master`
- APK 文件名格式：`KeiOS_<versionName>.apk`。
- Artifact 名称格式：`KeiOS_<versionName>`。

## GitHub 实时基准测试（GitHub Live Benchmark Test）

`GitHubStrategyLiveBenchmarkTest` 是一个按需启用的联网测试，用于对比 Atom 与 API 两种策略在真实仓库上的行为。它覆盖
release 读取、策略缓存 warm 样本、从 release APK 扫描包名、订阅项目检查，以及按包名反扫仓库。

### 启用开关（默认关闭）

仅当 `keios.github.liveBenchmark=true` 时执行。读取优先级如下：

1. JVM 系统属性
2. 环境变量
3. `~/.gradle/gradle.properties`

### 本地参数

```properties
keios.github.liveBenchmark=true
keios.github.api.token=ghp_xxx
keios.github.liveTargets=topjohnwu/Magisk,neovim/neovim,shadowsocks/shadowsocks-android
keios.github.forceGuest=false
```

说明：

- `keios.github.liveTargets` 可省略，省略时使用内置默认仓库。
- `keios.github.forceGuest=true` 会在有 token 的情况下仍强制走游客模式。
- `gpr.key` 也可作为 `keios.github.api.token` 的兜底值。

### 运行方式

```bash
./gradlew :app:testDebugUnitTest --tests "os.kei.feature.github.data.remote.GitHubStrategyLiveBenchmarkTest"
```

一次性命令示例（不改本地配置文件）：

```bash
./gradlew :app:testDebugUnitTest \
  --tests "os.kei.feature.github.data.remote.GitHubStrategyLiveBenchmarkTest" \
  -Dkeios.github.liveBenchmark=true \
  -Dkeios.github.api.token=ghp_xxx \
  -Dkeios.github.liveTargets=topjohnwu/Magisk,neovim/neovim
```

### 此测试验证内容

- 两种策略都执行并产出基准结果。
- 目标仓库列表非空。
- warm 阶段样本来自策略缓存。
- 包名扫描样本能从 release APK 的 AndroidManifest 数据中读取 package ID。
- 仓库反扫样本能按包名 / 应用名搜索候选，并通过 APK 包名扫描验证匹配结果。

该测试依赖实时网络，请考虑 GitHub API 限流、网络波动等外部因素导致的偶发失败。
