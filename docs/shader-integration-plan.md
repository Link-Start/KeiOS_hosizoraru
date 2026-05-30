# 着色器集成计划

> 创建时间：2026-05-30
> 目标：将自定义 AGSL 着色器集成到现有 liquid glass 组件，提升视觉效果

---

## 扫描分析结果

### 触摸位置追踪现状

| 组件 | 追踪方式 | 位置信息 | 可用性 |
|------|----------|----------|--------|
| LiquidSurfaces | `InteractiveHighlight` | `positionAnimation.value`（私有） | 需暴露 |
| LiquidGlassBottomBar | `tabWidthPx` + `displaySelectionValue` | 可计算选中项中心 | ✅ 可用 |
| AppLiquidButtons | `InteractiveHighlight` | `positionAnimation.value`（私有） | 需暴露 |
| AppLiquidSearchField | 无触摸位置 | 字段中心即可 | ✅ 可用 |

### 关键发现

1. **InteractiveHighlight 已有触摸追踪**：`positionAnimation` 跟踪绝对位置，但未公开
2. **需要暴露 `touchPosition` 属性**：让着色器能获取触摸坐标
3. **LiquidGlassBottomBar 可直接计算**：选中项中心 = `(displayValue + 0.5) * tabWidthPx`

---

## 实施计划

### Phase 1：基础设施（暴露触摸位置）

| 步骤 | 文件 | 内容 | 状态 |
|------|------|------|------|
| 1.1 | `InteractiveHighlight.kt` | 添加 `touchPosition: Offset` 公开属性 | 待实施 |
| 1.2 | `InteractiveHighlight.kt` | 添加 `centerPosition: Offset` 公开属性 | 待实施 |

### Phase 2：集成 radialRefraction

| 步骤 | 文件 | 内容 | 状态 |
|------|------|------|------|
| 2.1 | `LiquidSurfaces.kt` | 在交互模式下添加 `radialRefraction` | 待实施 |
| 2.2 | `LiquidGlassBottomBar.kt` | 在选中项添加 `radialRefraction` | 待实施 |

### Phase 3：优化和验证

| 步骤 | 内容 | 状态 |
|------|------|------|
| 3.1 | 编译验证 | 待实施 |
| 3.2 | 性能评估 | 待实施 |
| 3.3 | 更新文档 | 待实施 |

---

## 技术细节

### radialRefraction 集成方式

```kotlin
// LiquidSurfaces - 交互模式
effects = {
    vibrancy()
    blur(blurRadius.toPx())
    lens(lensRadius.toPx(), lensRadius.toPx())
    // 新增：从触摸点扩散的径向折射
    radialRefraction(
        centerX = interactiveHighlight.touchPosition.x,
        centerY = interactiveHighlight.touchPosition.y,
        radius = lensRadius.toPx() * 1.5f,
        strength = 8f,
    )
}
```

```kotlin
// LiquidGlassBottomBar - 选中项
effects = {
    if (effectiveLiquidEffectEnabled) {
        vibrancy()
        blur(effectBlurDp.toPx())
        lens(effectLensDp.toPx(), effectLensDp.toPx())
        // 新增：从选中项中心扩散的径向折射
        val tabCenterX = (displaySelectionValue + 0.5f) * tabWidthPx
        radialRefraction(
            centerX = tabCenterX,
            centerY = size.height / 2f,
            radius = effectLensDp.toPx() * 2f,
            strength = 6f,
        )
    }
}
```

---

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 性能下降 | 低 | `radialRefraction` 仅 1 次采样，开销极小 |
| 视觉效果不理想 | 中 | 可调整参数或回退 |
| 代码复杂度增加 | 低 | 封装为扩展函数 |

---

## 预期收益

| 指标 | 改进 |
|------|------|
| 视觉效果 | 触摸点/选中项有动态折射反馈 |
| 代码质量 | 着色器封装，易于复用 |
| 用户体验 | 更自然的交互响应 |
