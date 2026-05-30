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

**原理**：`drawPlainBackdrop` 不包含 `highlight`、`shadow`、`innerShadow` 参数，适用于不需要这些效果的场景，减少不必要的计算和代码量。

| 组件 | 文件 | 当前状态 | 优化方式 |
|------|------|----------|----------|
| LiquidGlassBottomBar (lightweight) | `widget/chrome/LiquidGlassBottomBar.kt` | `drawBackdrop` 传入 `highlight=null, shadow=null` | 改用 `drawPlainBackdrop` |
| AppFloatingLiquidDockSurface | `widget/glass/AppFloatingLiquidDockSurface.kt` | `drawBackdrop` 无 highlight/shadow 需求 | 改用 `drawPlainBackdrop` |
| LiquidSurfaces (无交互模式) | `widget/glass/LiquidSurfaces.kt` | `isInteractive=false` 时 highlight alpha 固定 | 改用 `drawPlainBackdrop` |
| LiquidProgressBars | `widget/glass/LiquidProgressBars.kt` | 进度条无高光/阴影需求 | 改用 `drawPlainBackdrop` |

**操作步骤**：
1. 检查组件是否使用 `highlight = null` 或固定值
2. 检查组件是否使用 `shadow = null` 或 `Shadow(alpha = 0f)`
3. 检查组件是否使用 `innerShadow = null`
4. 若三项均满足，替换为 `drawPlainBackdrop`

---

## P1：优化重复 backdrop 创建

**原理**：多个组件可能创建相似的 backdrop 配置，可提取为共享常量或工厂函数。

| 组件 | 文件 | 当前状态 | 优化方式 |
|------|------|----------|----------|
| AppLiquidButtons | `widget/glass/AppLiquidButtons.kt` | 每个按钮实例独立创建 backdrop 效果 | 提取为共享 `effects` lambda |
| LiquidGlassDropdown | `widget/glass/LiquidGlassDropdown.kt` | 与 LiquidSurfaces 效果相同 | 复用 LiquidSurfaces 效果配置 |
| LiquidGlassBottomBar items | `widget/chrome/LiquidGlassBottomBar.kt` | 多个 item 重复相同 effects | 提取为 `remember` 的 effects 块 |
| AppLiquidSearchField | `widget/glass/AppLiquidSearchField.kt` | 两处 drawBackdrop 配置相似 | 合并为共享配置 |

**操作步骤**：
1. 扫描各组件的 `effects` lambda 内容
2. 识别重复的 `vibrancy() + blur() + lens()` 组合
3. 提取为 `remember` 的共享 lambda 或常量
4. 多处引用同一配置

---

## P2：探索 `runtimeShaderEffect` 自定义着色器

**原理**：`runtimeShaderEffect` 允许编写 AGSL 着色器实现自定义视觉效果，是 `lens` 等内置效果的扩展点。

| 场景 | 当前实现 | 自定义着色器潜力 | 复杂度 |
|------|----------|------------------|--------|
| 动态折射 | `lens(refractionHeight, refractionAmount)` 固定参数 | 基于动画参数的动态折射曲线 | 高 |
| 方向模糊 | `blur(radius)` 各向同性 | 运动模糊、径向模糊 | 高 |
| 色彩分离 | `chromaticAberration` 固定色散 | 可控色散强度和方向 | 中 |
| 波纹效果 | 无 | 水波纹、脉冲波纹 | 高 |
| 渐变折射 | 无 | 基于位置的渐变折射率 | 中 |

**适用组件**：
- `LiquidSurfaces` — 可添加动态视觉效果
- `AppLiquidButtons` — 按压时的波纹反馈
- `LiquidGlassBottomBar` — 选中项的动态高亮

**操作步骤**：
1. 确定需要自定义效果的场景
2. 编写 AGSL 着色器代码
3. 使用 `runtimeShaderEffect` 集成
4. 测试 API 33+ 设备兼容性

---

## 实施顺序

```
P0-1: LiquidGlassBottomBar lightweight → drawPlainBackdrop
P0-2: AppFloatingLiquidDockSurface → drawPlainBackdrop
P0-3: LiquidSurfaces 无交互模式 → drawPlainBackdrop
P0-4: LiquidProgressBars → drawPlainBackdrop
  ↓
P1-1: AppLiquidButtons effects 提取
P1-2: LiquidGlassDropdown effects 复用
P1-3: LiquidGlassBottomBar items effects 提取
P1-4: AppLiquidSearchField effects 合并
  ↓
P2-1: 评估 runtimeShaderEffect 使用场景
P2-2: 实现第一个自定义着色器（如有需求）
```

---

## 兼容性说明

| API | 最低 API | 项目 minSdk | 备注 |
|-----|----------|-------------|------|
| `drawPlainBackdrop` | API 31 | API 35 | ✅ 完全兼容 |
| `drawBackdrop` | API 31 | API 35 | ✅ 完全兼容 |
| `runtimeShaderEffect` | API 33 | API 35 | ✅ 完全兼容 |
| `RuntimeShader` | API 33 | API 35 | ✅ 完全兼容 |
