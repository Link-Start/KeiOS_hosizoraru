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

### 评估结论

| 组件 | 文件 | 问题 | 可行性 |
|------|------|------|--------|
| LiquidGlassBottomBar | `LiquidGlassBottomBar.kt` | 3 处 effects 块参数各异 | ⚠️ 提取收益有限 |
| LiquidActionBar | `LiquidActionBar.kt` | 与 VisualLayers 条件检查不同 | ⚠️ 提取收益有限 |
| LiquidActionBarVisualLayers | `LiquidActionBarVisualLayers.kt` | 2 处 effects 块进度源不同 | ⚠️ 提取收益有限 |

**结论**：LiquidGlassBottomBar 和 LiquidActionBar 的 effects 块虽然相似，但各自有不同的条件检查和进度源。强行提取为共享函数会增加参数复杂度，收益有限。

---

## P2：自定义着色器效果（`runtimeShaderEffect`）

**原理**：`runtimeShaderEffect` 允许编写 AGSL 着色器实现自定义视觉效果，是 `lens` 等内置效果的扩展点。

### 已实现

#### 自定义着色器库

已创建 [LiquidGlassShaders.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaders.kt)，包含 3 个自定义着色器效果：

| 效果 | 函数 | 用途 | 复杂度 |
|------|------|------|--------|
| 脉冲波纹 | `pulseRipple()` | 按钮按压时的扩散波纹反馈 | 中 |
| 径向折射 | `radialRefraction()` | 中心强、边缘弱的放大镜效果 | 中 |
| 方向模糊 | `directionalBlur()` | 运动方向的模糊效果 | 低 |

#### 使用示例

已创建 [LiquidGlassShaderDemo.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaderDemo.kt)，展示如何集成自定义着色器：

```kotlin
// 按钮按压时的脉冲波纹
Modifier.drawBackdrop(
    backdrop = backdrop,
    shape = { ContinuousCapsule },
    effects = {
        vibrancy()
        blur(4.dp.toPx())
        lens(16.dp.toPx(), 28.dp.toPx())
        // 添加脉冲波纹
        pulseRipple(
            centerX = size.width / 2f,
            centerY = size.height / 2f,
            radius = rippleRadius,  // 动画值
            strength = 8f,
        )
    }
)
```

### 待集成

| 组件 | 效果 | 集成方式 | 状态 |
|------|------|----------|------|
| AppLiquidButtons | `pulseRipple` | 按压时从中心扩散 | 待集成 |
| LiquidSurfaces | `radialRefraction` | 交互时的径向放大 | 待集成 |
| LiquidGlassBottomBar | `pulseRipple` | 选中项的波纹反馈 | 待集成 |

### 兼容性

| API | 最低 API | 项目 minSdk | 备注 |
|-----|----------|-------------|------|
| `runtimeShaderEffect` | API 33 | API 35 | ✅ 完全兼容 |
| `RuntimeShader` | API 33 | API 35 | ✅ 完全兼容 |

---

## 项目统计

| 指标 | 数量 |
|------|------|
| `drawBackdrop` 使用处 | 16 处 |
| `rememberLayerBackdrop` 创建处 | 129 处 |
| 使用 backdrop 的文件 | 13 个 |
| 使用 highlight/shadow 的组件 | 8 个 |

---

## 实施记录

| 日期 | 任务 | 状态 |
|------|------|------|
| 2026-05-30 | backdrop 升级到 2.0.0 稳定版 | ✅ |
| 2026-05-30 | P0 评估：drawPlainBackdrop 不适用 | ✅ |
| 2026-05-30 | P1-1: AppLiquidButtons effects 提取 | ✅ |
| 2026-05-30 | P1 评估：其他组件提取收益有限 | ✅ |
| 2026-05-30 | P2-1: 研究 lens 效果实现和 AGSL 着色器 | ✅ |
| 2026-05-30 | P2-2: 创建 LiquidGlassShaders.kt（3 个自定义着色器） | ✅ |
| 2026-05-30 | P2-3: 创建 LiquidGlassShaderDemo.kt（使用示例） | ✅ |

---

## 文件索引

| 文件 | 说明 |
|------|------|
| [LiquidGlassShaders.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaders.kt) | 自定义 AGSL 着色器效果库 |
| [LiquidGlassShaderDemo.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaderDemo.kt) | 着色器使用示例组件 |
| [AppLiquidButtons.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/AppLiquidButtons.kt) | 已提取共享 effects |
| [backdrop-2.0-optimization-plan.md](docs/backdrop-2.0-optimization-plan.md) | 本文档 |

---

## 后续建议

1. **集成着色器到生产组件**：将 `pulseRipple` 集成到 AppLiquidButtons，需要添加触摸位置追踪
2. **性能测试**：自定义着色器在低端设备上的性能表现需要验证
3. **视觉调优**：着色器参数（strength、width 等）需要在真机上调试到最佳值
4. **扩展着色器库**：根据需求添加更多效果（如波浪、渐变折射等）
