# 着色器集成计划

> 创建时间：2026-05-30
> 目标：将自定义 AGSL 着色器集成到现有 liquid glass 组件，提升视觉效果
> 状态：✅ 已完成

---

## 扫描分析结果

### 触摸位置追踪现状

| 组件 | 追踪方式 | 位置信息 | 可用性 |
|------|----------|----------|--------|
| LiquidSurfaces | `InteractiveHighlight` | `positionAnimation.value`（私有） | ✅ 已暴露 |
| LiquidGlassBottomBar | `tabWidthPx` + `displaySelectionValue` | 可计算选中项中心 | ✅ 已集成 |
| AppLiquidButtons | `InteractiveHighlight` | `positionAnimation.value`（私有） | 待集成 |
| AppLiquidSearchField | 无触摸位置 | 字段中心即可 | ✅ 可用 |

### 关键发现

1. **InteractiveHighlight 已有触摸追踪**：`positionAnimation` 跟踪绝对位置，已添加 `touchPosition` 公开属性
2. **LiquidGlassBottomBar 可直接计算**：选中项中心 = `(displayValue + 0.5) * tabWidthPx`
3. **radialRefraction 采样次数仅为 1**：性能开销极低，可放心使用

---

## 实施记录

### Phase 1：基础设施（暴露触摸位置）✅

| 步骤 | 文件 | 内容 | 状态 |
|------|------|------|------|
| 1.1 | `InteractiveHighlight.kt` | 添加 `touchPosition: Offset` 公开属性 | ✅ 完成 |

### Phase 2：集成 radialRefraction ✅

| 步骤 | 文件 | 内容 | 状态 |
|------|------|------|------|
| 2.1 | `LiquidSurfaces.kt` | 在交互模式下添加 `radialRefraction` | ✅ 完成 |
| 2.2 | `LiquidGlassBottomBar.kt` | 在选中项添加 `radialRefraction` | ✅ 完成 |

### Phase 3：优化和验证 ✅

| 步骤 | 内容 | 状态 |
|------|------|------|
| 3.1 | 编译验证 | ✅ 通过 |
| 3.2 | 性能评估 | ✅ 采样次数 1，开销极低 |
| 3.3 | 更新文档 | ✅ 完成 |

---

## 技术实现

### InteractiveHighlight 新增属性

```kotlin
// InteractiveHighlight.kt
val touchPosition: Offset get() = positionAnimation.value
```

### LiquidSurfaces 集成

```kotlin
effects = {
    vibrancy()
    blur(blurRadius.toPx())
    lens(lensRadius.toPx(), lensRadius.toPx())
    // 新增：从触摸点扩散的径向折射
    if (isInteractive && enabled && interactiveHighlight.pressProgress > 0f) {
        radialRefraction(
            centerX = interactiveHighlight.touchPosition.x,
            centerY = interactiveHighlight.touchPosition.y,
            radius = lensRadius.toPx() * 2f,
            strength = 8f * interactiveHighlight.pressProgress,
        )
    }
}
```

### LiquidGlassBottomBar 集成

```kotlin
effects = {
    val progress = if (effectiveLiquidEffectEnabled) combinedPressProgressProvider() else 0f
    if (progress > 0f) {
        lens(...)
        // 新增：从指示器中心扩散的径向折射
        radialRefraction(
            centerX = size.width / 2f,
            centerY = size.height / 2f,
            radius = 14f.dp.toPx() * progress * interactionLensScale,
            strength = 6f * progress,
        )
    }
}
```

---

## 性能评估

| 组件 | 着色器 | 采样次数 | 性能影响 | 条件 |
|------|--------|----------|----------|------|
| LiquidSurfaces | radialRefraction | 1 | 🟢 极低 | 仅在按压时 |
| LiquidGlassBottomBar | radialRefraction | 1 | 🟢 极低 | 仅在按压时 |

---

## 文件索引

| 文件 | 说明 |
|------|------|
| [LiquidGlassShaders.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassShaders.kt) | 自定义 AGSL 着色器效果库 |
| [InteractiveHighlight.kt](app/src/main/java/os/kei/ui/animation/InteractiveHighlight.kt) | 新增 `touchPosition` 属性 |
| [LiquidSurfaces.kt](app/src/main/java/os/kei/ui/page/main/widget/glass/LiquidSurfaces.kt) | 集成 radialRefraction |
| [LiquidGlassBottomBar.kt](app/src/main/java/os/kei/ui/page/main/widget/chrome/LiquidGlassBottomBar.kt) | 集成 radialRefraction |
| [shader-integration-plan.md](docs/shader-integration-plan.md) | 本文档 |

---

## 后续建议

1. **AppLiquidButtons 集成**：可将 `pulseRipple` 集成到按钮按压效果中
2. **参数调优**：在真机上测试 radialRefraction 的 strength 和 radius 参数
3. **性能监控**：在低端设备上验证着色器性能表现
4. **扩展应用**：将着色器集成到更多组件（AppLiquidSearchField 等）
