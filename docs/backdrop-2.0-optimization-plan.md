# Backdrop 2.0.0 适配复盘与优化计划

> 复盘时间：2026-05-30
> 依赖现状：`io.github.kyant0:backdrop:2.0.0`（app/build.gradle.kts:328）
> 复盘方式：反编译对比 `2.0.0-alpha03` 与 `2.0.0` 的 AAR/sources，核对项目实际用法，编译验证

---

## 一、核心结论（先看这里）

1. **alpha03 → 2.0.0 没有任何源码级 API 变更。** 升级本质就是一行版本号，项目所有现有调用都兼容，`:app:compileReleaseKotlin` 通过（exit 0）。
2. **旧计划的前提是错的。** 上一版计划把 `drawPlainBackdrop`、`RuntimeShader` 接口、`runtimeShaderEffect` 当作「2.0.0 新增 API」来规划。实际上这三者在 alpha03 里**已经存在且签名完全一致**，不是新东西。
3. **真正需要处理的不是"适配新 API"，而是近期手写的自定义着色器功能（P2）的遗留问题** —— 有死代码、一处坐标对齐 bug、注释语义错误、文档不实。本轮已全部修复并归并文档（详见第三、四节），代码层无遗留，仅剩真机视觉微调待办。

---

## 二、alpha03 → 2.0.0 真实差异

通过反编译两个版本的 classes.jar 与 sources，逐个 `javap` 对比公开签名：

### 公开 API 差异：无

| 模块 | 对比结果 |
|------|----------|
| `DrawBackdropModifierKt`（drawBackdrop / drawPlainBackdrop） | ✅ 签名完全一致 |
| `effects/RenderEffectKt`（effect / runtimeShaderEffect） | ✅ 签名完全一致 |
| `effects/LensKt` / `BlurKt` / `ColorFilterKt` | ✅ 签名完全一致 |
| `RuntimeShader` / `RuntimeShaderCache` 接口 | ✅ 签名完全一致 |
| `BackdropEffectScope` | ✅ 签名完全一致 |
| backdrops（Layer/Combined/Canvas/Empty/rememberBackdrop） | ✅ 签名完全一致 |
| highlight / shadow / innerShadow | ✅ 签名完全一致 |

### 内部实现差异（不影响调用方）

| 变化 | 说明 |
|------|------|
| `internal/PlatformCapabilitiesKt` → `PlatformKt` | 仅内部类重命名，包外不可见 |
| 库形态 | 由 Android 库变为 **Compose Multiplatform** 库 |
| 传递依赖版本抬升 | kotlin-stdlib 2.3.10→2.3.21、compose foundation/ui 1.10.1→1.11.0、annotations 26.0.2→26.1.0；`shapes` 仍为 1.2.0 |

### 官方 release note（2.0.0）原文

> - Backdrop becomes a Compose Multiplatform library now
> - Remove Android related effects, use Compose version instead
> - Add common `RuntimeShader` interface and `BackdropEffectScope.runtimeShaderEffect`
> - （rc01）Fix `LayoutCoordinates` reference leaks

注意：「Add common RuntimeShader / runtimeShaderEffect」「Remove Android related effects」是相对 **1.x** 而言的变更，在我们用的 alpha03 里早已落地。所以对本项目而言，从 alpha03 跨到 2.0.0 是零迁移成本。`LayoutCoordinates` 泄漏修复（rc01）是我们白拿的稳定性收益。

**对依赖抬升的唯一提醒**：项目 compose 编译器插件为 2.3.21（build.gradle.kts:4），composeVersion=1.11.2，与 backdrop 要求的 foundation/ui 1.11.0 兼容，无冲突。

---

## 三、近期手写改动的遗留问题与修复（已落地）

近期 4 个 commit（bb258de / 415a5dc / 75ddb48 / 3c43888）手写了一套自定义 AGSL 着色器并接入了两个组件。复盘发现 4 个问题，本轮已全部处理。

### 问题 1：死代码 —— Demo 文件与两个着色器从未被使用 ✅ 已删除

复盘时的状态：

| 符号 | 位置 | 当时现状 | 处理 |
|------|------|----------|------|
| `LiquidGlassShaderDemo()` | LiquidGlassShaderDemo.kt | ❌ 全项目无引用 | 删除整个文件 |
| `LiquidGlassMagnifierExample()` | LiquidGlassShaderDemo.kt | ❌ 全项目无引用 | 删除整个文件 |
| `pulseRipple()` | LiquidGlassShaders.kt | ❌ 仅 Demo 引用（间接死代码） | 删除函数 + 着色器串 |
| `directionalBlur()` | LiquidGlassShaders.kt | ❌ 全项目无引用 | 删除函数 + 着色器串 |
| `radialRefraction()` | LiquidGlassShaders.kt | ✅ 接入 LiquidSurfaces、LiquidGlassBottomBar | 保留并修复（见问题 2） |

结果：`LiquidGlassShaders.kt` 从 3 个着色器精简到 1 个真正在用的 `radialRefraction`；删除了 `LiquidGlassShaderDemo.kt` 整文件；同步清理了 `InteractiveHighlight.kt` 注释里对 `pulseRipple` 的引用。

### 问题 2（坐标 bug）：自定义着色器没有处理 `padding` 偏移 ✅ 已修复

内置 `lens` 的实现：当链中存在 `blur` 时，`blur` 会把 `padding` 抬到模糊半径大小，layer 实际尺寸向四周各扩 `padding` 像素。内置 lens 着色器因此带了 `uniform float2 offset`，用 `offset = -padding` 把坐标系校正回来：

```text
// 内置 lens shader（节选）
uniform float2 offset;
float2 centeredCoord = (coord + offset) - halfSize;   // 用 offset 抵消 padding
```

修复前 `radialRefraction` 直接读原始 `coord`，而 `center` 用的是组件坐标（`size.width/2` 或 `touchPosition`）。在实际调用链 `vibrancy → blur → lens → radialRefraction` 中：`vibrancy` 先把 `renderEffect` 置非空 → `blur` 满足 `renderEffect != null`、把 `padding` 抬为模糊半径 → `lens` 之后只减掉 `refractionHeight`，通常仍剩非零 padding。于是 `radialRefraction` 运行时 `coord`（padding layer 空间）与 `center`（组件空间）存在约 `padding` 像素的坐标系错位，折射中心会偏移。

修复方式（已对齐内置 lens 的约定）：

- Kotlin 侧在调用 `runtimeShaderEffect` 时读取当前 `padding`，传入 `offset = -padding`。
- AGSL 侧先 `float2 c = coord + offset` 把坐标拉回组件空间再算距离与方向；`content.eval` 仍用原始 `coord`（采样发生在 layer 空间，纯平移不影响方向向量）。

### 问题 3：`radialRefraction` 的语义与注释不符 ✅ 已修正

着色器把坐标向**外**位移，视觉上是凸起/鱼眼，不是放大。已把文档注释从「magnifying glass」改为「press bulge / 凸起折射」，并说明它与内置 lens 的区别（lens 沿形状轮廓折射，本效果绕任意中心点折射）。

### 问题 4：旧计划文档自述与事实不符 ✅ 已归并

`shader-integration-plan.md` 标注「✅ 已完成 / pulseRipple 已集成 / 性能开销极低」与事实不符（pulseRipple 当时是死代码、性能未真机测过）。已删除该文件，内容归并入本文档，避免两份文档互相矛盾。

---

## 四、本轮落地清单

| 项 | 文件 | 改动 | 状态 |
|----|------|------|------|
| 删死代码 | LiquidGlassShaderDemo.kt | 删除整个文件 | ✅ |
| 删死代码 | LiquidGlassShaders.kt | 移除 `pulseRipple`、`directionalBlur` 及其着色器串 | ✅ |
| 修坐标 bug | LiquidGlassShaders.kt | `radialRefraction` 增加 `offset = -padding` 校正 | ✅ |
| 修注释 | LiquidGlassShaders.kt | 语义注释从「放大镜」改为「凸起折射」 | ✅ |
| 清理引用 | InteractiveHighlight.kt | 注释去掉对 `pulseRipple` 的提及 | ✅ |
| 归并文档 | shader-integration-plan.md | 删除并归并入本文档 | ✅ |
| 编译验证 | — | `:app:compileReleaseKotlin` 通过（exit 0） | ✅ |

> 待办（需真机）：在低端设备上验证 `radialRefraction` 修复后折射中心是否对齐、按压视觉是否符合预期，并微调 `strength`/`radius` 参数。代码层面已无遗留。

---

## 五、真正可考虑的"新版本红利"（与适配无关）

既然 2.0.0 没有迁移负担，可单独评估这些既有能力是否值得用起来（都不是 2.0.0 才有的，是我们一直没用的）：

| 能力 | 现状 | 备注 |
|------|------|------|
| `drawPlainBackdrop` | 项目 0 处使用 | 仅适用于「无 highlight/shadow/innerShadow」的纯折射场景；项目现有 16 处 drawBackdrop 都用了 highlight/shadow，**确实没有适用点**，旧计划这条结论是对的 |
| `lens` 的 `depthEffect` / `chromaticAberration` | 已在用 | 无需额外动作 |
| rc01 的 `LayoutCoordinates` 泄漏修复 | 升级后自动获得 | 长列表/频繁重组场景的稳定性收益 |

---

## 附：本次复盘核对过的关键事实

- 版本号：`backdropVersion = "2.0.0"`（app/build.gradle.kts:328、628）
- 编译：`:app:compileReleaseKotlin` 通过（exit 0）
- drawBackdrop 调用、rememberLayerBackdrop、exportedBackdrop、rememberCombinedBackdrop、rememberCanvasBackdrop 均正常编译，无失效调用
- alpha03 与 2.0.0 公开签名逐类 diff：无差异
