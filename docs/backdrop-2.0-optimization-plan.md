# Backdrop 2.0.0 新 API 优化计划

> 升级时间：2026-05-30
> 背景：backdrop 从 2.0.0-alpha03 升级到 2.0.0 稳定版，新增 `drawPlainBackdrop`、`RuntimeShader` 接口、`runtimeShaderEffect`

---

## 优先级定义

| 优先级 | 含义 | 预估收益 |
|--------|------|----------|
| P0 | 立即可做，低风险，代码简化 | 减少样板代码，提升可读性 |
| P1 | 值得做，中等复杂度 | 性能或视觉体验提升 |
| P2 | 探索性，高复杂度 | 新视觉能力，需评估 ROI |

---

## P0：使用 `drawPlainBackdrop` 简化无高光/阴影场景

**结论：不适用。** 经过详细扫描，项目中所有使用 `drawBackdrop` 的组件都实际使用了 `highlight`/`shadow`/`innerShadow` 效果。`drawPlainBackdrop` 在当前项目中没有直接适用场景。

| 组件 | 文件 | 原因 |
|------|------|------|
| LiquidGlassBottomBar | `LiquidGlassBottomBar.kt` | 使用 highlight + shadow |
| AppFloatingLiquidDockSurface | `AppFloatingLiquidDockSurface.kt` | 使用 highlight + shadow + innerShadow |
| LiquidSurfaces | `LiquidSurfaces.kt` | 使用 highlight + shadow + innerShadow |
| LiquidProgressBars | `LiquidProgressBars.kt` | 使用 highlight + shadow + innerShadow |
| AppLiquidButtons | `AppLiquidButtons.kt` | 使用 highlight + shadow + innerShadow |
| LiquidActionBar | `LiquidActionBar.kt` | 使用 highlight + shadow |
| LiquidActionBarVisualLayers | `LiquidActionBarVisualLayers.kt` | 使用 highlight + shadow |
| BaGuideBgmDockVisuals | `BaGuideBgmDockVisuals.kt` | 使用 highlight + shadow + innerShadow |

---

## P1：提取共享 effects 配置

**目标**：减少重复的 effects 代码块，确保视觉行为一致性。

### 已完成

| 组件 | 文件 | 优化内容 | 状态 |
|------|------|----------|------|
| AppLiquidButtons | `AppLiquidButtons.kt` | 提取 `applyLiquidButtonEffects(variant)` 共享函数，替换 2 处重复 effects 块 | ✅ 已完成 |

### 评估中

| 组件 | 文件 | 问题 | 优化方式 | 可行性 |
|------|------|------|----------|--------|
| LiquidGlassBottomBar | `LiquidGlassBottomBar.kt` | 3 处 effects 块，但参数各不相同 | 提取为参数化函数 | ⚠️ 需权衡复杂度 |
| LiquidActionBar | `LiquidActionBar.kt` | 与 LiquidActionBarVisualLayers 相似 | 共享 effects 配置 | ⚠️ 条件检查不同 |
| LiquidActionBarVisualLayers | `LiquidActionBarVisualLayers.kt` | 2 处 effects 块 | 提取为共享函数 | ⚠️ 进度源不同 |

**评估结论**：LiquidGlassBottomBar 和 LiquidActionBar 的 effects 块虽然相似，但各自有不同的条件检查（`effectiveLiquidEffectEnabled` vs `isBlurEnabled`）和不同的进度源（`combinedPressProgressProvider()` vs `dampedDragAnimation.pressProgress`）。强行提取为共享函数会增加参数复杂度，收益有限。

---

## P2：自定义着色器效果（`runtimeShaderEffect`）

**原理**：`runtimeShaderEffect` 允许编写 AGSL 着色器实现自定义视觉效果，是 `lens` 等内置效果的扩展点。

### 已实现

已创建 [LiquidGlassShaders.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaders.kt)，包含 3 个自定义着色器效果：

| 效果 | 函数 | 用途 | 复杂度 |
|------|------|------|--------|
| 脉冲波纹 | `pulseRipple()` | 按钮按压时的扩散波纹反馈 | 中 |
| 径向折射 | `radialRefraction()` | 中心强、边缘弱的放大镜效果 | 中 |
| 方向模糊 | `directionalBlur()` | 运动方向的模糊效果 | 低 |

### 使用示例

```kotlin
// 按钮按压时的脉冲波纹
Modifier.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(4.dp.toPx())
        lens(16.dp.toPx(), 28.dp.toPx())
        // 添加脉冲波纹
        pulseRipple(
            centerX = touchX,
            centerY = touchY,
            radius = animationProgress * maxRadius,
            strength = 8f,
        )
    }
)
```

### 待集成

| 组件 | 效果 | 集成方式 | 状态 |
|------|------|----------|------|
| AppLiquidButtons | `pulseRipple` | 按压时从触摸点扩散 | 待集成 |
| LiquidSurfaces | `radialRefraction` | 交互时的径向放大 | 待集成 |
| LiquidGlassBottomBar | `pulseRipple` | 选中项的波纹反馈 | 待集成 |

### 兼容性

| API | 最低 API | 项目 minSdk | 备注 |
|-----|----------|-------------|------|
| `runtimeShaderEffect` | API 33 | API 35 | ✅ 完全兼容 |
| `RuntimeShader` | API 33 | API 35 | ✅ 完全兼容 |

---

## 实施记录

| 日期 | 任务 | 状态 |
|------|------|------|
| 2026-05-30 | P0 评估：drawPlainBackdrop 不适用 | ✅ |
| 2026-05-30 | P1-1: AppLiquidButtons effects 提取 | ✅ |
| 2026-05-30 | P1 评估：LiquidGlassBottomBar/LiquidActionBar 提取收益有限 | ✅ |
| 2026-05-30 | P2-1: 研究 lens 效果实现和 AGSL 着色器 | ✅ |
| 2026-05-30 | P2-2: 创建 LiquidGlassShaders.kt（3 个自定义着色器） | ✅ |
| 2026-05-30 | P2-3: 集成到组件（待定） | ⏳ |

---

## 文件索引

| 文件 | 说明 |
|------|------|
| [LiquidGlassShaders.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaders.kt) | 自定义 AGSL 着色器效果 |
| [AppLiquidButtons.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/AppLiquidButtons.kt) | 已提取共享 effects |
