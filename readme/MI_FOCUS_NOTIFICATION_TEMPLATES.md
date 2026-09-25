# 小米超级岛模板速查

项目封装入口：

- `os.kei.core.notification.focus.MiFocusNotificationTemplate.build(context, spec)`
- 输出 `Bundle`，内部包含 `miui.focus.param`、`miui.focus.pics`、`miui.focus.actions`
- `miui.focus.param` 由本地 V3 协议引擎构建，展开态位于 `param_v2`，小岛位于 `param_v2.param_island`
- 调用方式：`NotificationCompat.Builder(...).addExtras(MiFocusNotificationTemplate.build(...))`
- `MiFocusNotificationSpec.privateOverrides` 预留给宿主层私有 `RemoteViews` 后处理，当前作为经验模型沉淀。

## 设计规范

摘要态用于一眼识别当前状态。大字优先使用 2-4 个中文字符、1-3 位数字或短百分比；完整标题、正文、统计信息放在展开态。

展开态用于承载完整标题、正文、进度和操作。项目内按钮最多放 2 个，主操作高亮，次操作普通。

## 浮现与通知生命周期

- `islandFirstFloat` 控制同一 `orderId` 首次加入超级岛时的浮现。
- `enableFloat` 控制已有超级岛收到更新时的再次浮现，适合完成、失败、取消等终态。
- `android.requestPromotedOngoing=true` 只用于同时带有 `FLAG_ONGOING_EVENT` 的持续通知。
- 终态更新保持原 `orderId`、`updatable=true`、`setOngoing(false)`、
  `setRequestPromotedOngoing(false)`，并按用户设置决定 `enableFloat`。
- 单次提醒可以使用 `islandFirstFloat=true`、`enableFloat=false`，首次展示后保持安静更新。
- `MiIslandNotificationBuilder` 会统一约束 promoted ongoing 与普通通知 ongoing 标志，防止终态被打落为普通通知。

实体机上的 HyperOS SystemUI 行为：首次加入读取 `islandFirstFloat`；同一超级岛的后续更新读取
`enableFloat`。终态若继续请求 promoted ongoing，SystemUI 可能结束进度岛后只保留普通通知。

### 自动关闭时间

- `param_v2.timeout`：焦点通知的默认消失时间，单位分钟，默认 720（官方开发指南 pId=2131，
  2026-01-29 核对）。
- `param_island.islandTimeout`：岛自动消失时间，单位秒，默认 `60 * 60`。两者互不替代。
- 协议没有“永不”值：官方 FAQ 说明 `timeout=0` 是使用默认值，`-1` 约 5 秒后消失。不要把 0 或 -1
  当常驻开关。
- KeiOS 用设置里的“超级岛自动关闭”（`SuperIslandAutoClose`，默认永不）同时写这两个字段，由
  `SessionNotifierImpl` 传入 `MiIslandNotificationBuilder`，GitHub Actions 更新通知也读取它。
  “永不”发送 35,000 分钟与 2,100,000 秒（约 24 天）：宿主若按 32 位毫秒换算，两者都不会溢出。
  社区实现 HyperIsland 曾以 `Int.MAX_VALUE` 秒表示常驻，但通知的分钟字段没有同等依据。
- GitHub 刷新通知保留自己的短时 `timeout`（运行中 6 分钟、结束后 1 分钟），不受该设置影响。

## 文字样式与颜色

摘要态文字样式有限，优先靠短文本、图标和进度色表达状态：

- `highlightColor`：岛级强调色，配合文本模板里的 `showHighlightColor` 使用。
- `showHighlightColor`：让摘要文字使用强调色，适合完成、有更新、失败、取消等短状态。
- `narrowFont`：给数字、英文、百分比等窄文本使用，减少摘要态挤压。
- `colorReach` / `colorUnReach`：进度组件的已完成/未完成颜色，适合刷新、导入、AP 等进行中状态。

展开态文字支持更完整的颜色字段：

- `colorTitle` / `colorTitleDark`：主标题颜色，适合状态主色。
- `colorContent` / `colorContentDark`：正文颜色，适合弱化长说明。
- `colorSubContent` / `colorSubContentDark`：副正文颜色。
- `specialTitle` + `colorSpecialTitle` + `colorSpecialBg`：短状态标签，适合“刷新”“更4”“败1”“完成”这类语义。
- Action 的 `actionBgColor` / `actionTitleColor`：按钮背景和文字颜色。

文案策略：摘要态只放“范围 · 进度 · 非零结果”，零值省略；expanded 使用彩色 `specialTitle` 承载状态，正文保留完整上下文。

项目默认语义色：

- 运行中 / 进度：蓝色 `#3B82F6`
- 完成 / 发现更新 / 正向结果：绿色 `#22C55E`
- 失败 / 部分失败 / 危险操作：红色 `#E25B6A`
- 取消 / 中断 / 普通上下文：灰色 `#64748B`
- 缓存、降级、待处理冲突等需要谨慎判断的状态：黄色 `#F59E0B`

## Action 约束

- 超级岛展开态按钮需要同时写入模板 JSON 和 `miui.focus.actions`，项目内通过 `MiFocusNotificationAction` +
  `MiFocusNotificationTemplate.build(...)` 统一注册。
- `textButton` 对应 1-2 个文字按钮，适合“稍后 / 立即”“忽略 / 确认”“取消 / 打开”这类双操作场景。
- `highlightInfoV3` 对应 1 个圆头图文按钮，适合价格、购票、支付、去查看这类单主操作场景。
- Broadcast action 需要使用 `Intent.FLAG_RECEIVER_FOREGROUND`，部分 HyperOS 机型依赖该 flag 稳定投递点击事件。
- 自定义 action 指向的 Receiver/Service 需要在 Manifest 显式导出；通用已读/关闭 action 统一走
  `MiFocusNotificationActionReceiver`。
- 可被用户划掉的完成态通知需要设置 `deleteIntent`，让通知栏清除和超级岛按钮共用同一条关闭链路。

项目封装和官方模板的对应关系：

- `MiFocusExpandedComponent.TextButtons` -> `textButton`
- `MiFocusExpandedComponent.HighlightV3` + `action` -> `highlightInfoV3.actionInfo`
- `MiFocusNotificationAction.type = Circle` -> 圆头高亮按钮
- `MiFocusNotificationAction.type = Text` -> 文字按钮

按钮颜色字段：

- `backgroundColor` / `backgroundColorDark` -> `actionBgColor` / `actionBgColorDark`
- `pressedBackgroundColor` / `pressedBackgroundColorDark` -> `actionBgPressColor` / `actionBgPressColorDark`
- `titleColor` / `titleColorDark` -> `actionTitleColor` / `actionTitleColorDark`

本地协议引擎会直接下发常态、深色和按压态颜色字段，`pressedBackgroundColor` /
`pressedBackgroundColorDark` 分别映射为 `actionBgPressColor` / `actionBgPressColorDark`。

推荐写法：

- 次按钮：`action.asSecondaryTextButton()`
- 主按钮：`action.asPrimaryTextButton(...)`
- 圆头主按钮：`action.asHighlightCapsuleButton(...)`

官方模板与 APK 私有样式的边界：

- 官方 PDF、`pId=2131`、`pId=2142`、`pId=2143` 覆盖了公开模板、图片位、进度、文字按钮、圆头图文按钮。
- `miai_4.0.6.apk` 的药品提醒卡片在公开模板外，还通过 `TakeMedicineHelper` 对 `RemoteViews` 做了专用图标替换。
- 项目侧可以稳定复用公开模板能力，能做出很接近的双按钮效果。完全同款的药品按钮外观依赖宿主内置资源和私有布局覆写。

## 官方模板目录

代码内置目录入口：

- `MiFocusOfficialTemplateCatalog`：收录官方摘要态 1-9、小岛 1-3、展开态 1-22 的编号、结构、OS 支持和推荐入口。
- `MiFocusOfficialTemplatePresets`：提供可直接调用的官方摘要态、小岛、展开态 preset，当前展开态已覆盖 1-22 这一整条主模板目录。

代码检索入口：

- `MiFocusOfficialTemplateCatalog.findSummaryTemplate(code)`：查官方大岛摘要模板定义。
- `MiFocusOfficialTemplateCatalog.findSmallIslandTemplate(code)`：查官方小岛模板定义。
- `MiFocusOfficialTemplateCatalog.findExpandedTemplate(code)`：查官方展开态模板定义。
- `MiFocusOfficialTemplateCatalog.findRoute(family, code)`：查本地模板路线，能同时拿到 `recommendedEntry` 和 `primaryHelpers`。
- `summaryRoutes / smallIslandRoutes / expandedRoutes`：完整本地路由矩阵，适合做设置页、调试面板、模板浏览器或内部工具。

编号说明：

- PDF 的展开态尾部按 `14-1 / 14-2 / 15 ... 21` 展示。
- Web `pId=2142` 已经出现 `模版22` 编号。
- 本项目目录同时保留 `TEMPLATE_21` 和 `TEMPLATE_22`，用于表达“编号漂移，结构同族”的事实。

示例：

```kotlin
val route = MiFocusOfficialTemplateCatalog.findRoute(
    family = MiFocusOfficialTemplateFamily.EXPANDED,
    code = "14-2"
)

check(route?.recommendedEntry == "MiFocusOfficialTemplatePresets.expandedTemplate14_2NewImageTextCountdownPicture")
check(route?.primaryHelpers?.contains("MiFocusExpandedComponent.officialPictureCountdown") == true)
```

## 摘要态模板

大岛模板，对应 `MiFocusIslandBigTemplate`：

- `Text`：纯文本组件。
- `Picture`：纯图组件。
- `ImageTextLeft`：左图文组件，常用于固定左侧图标。
- `ImageTextRight`：右图文组件，终态短词推荐 `type = 3`。
- `ProgressText`：进度文本组件，推荐给 AP、刷新、导入等进行中状态。
- `FixedWidthDigit`：定宽数字文本组件，推荐给静态数字。
- `SameWidthDigit`：等宽数字文本组件，推荐给倒计时；倒计时使用 `MiFocusTimer.countdown(deadlineAtMs)`。

小岛模板，对应 `MiFocusIslandSmallTemplate`：

- `Picture`：只展示图标。
- `CombinePic`：图标 + 进度环，推荐给小岛也要展示进度的场景。
- `ImageTextRight(type = 6)`：官方小岛模板 3，图标 + 短数字/短文本，适合红绿灯、倒计时、短计数。

摘要态官方 helper：

- `MiFocusIslandSpec.iconOnlySummary(...)`：官方大岛模板 1 + 小岛模板 1 的快捷组合。
- `MiFocusIslandSpec.textOnlySummary(...)`：官方大岛模板 2 的快捷组合。
- `MiFocusIslandSpec.iconTextSummary(...)`：官方大岛模板 3 的快捷组合。
- `MiFocusIslandSpec.progressSummary(...)`：官方大岛模板 5 + 小岛模板 2 的快捷组合。
- `MiFocusIslandSpec.countdownSummary(...)`：官方大岛模板 6 的快捷组合。
- `MiFocusIslandSpec.terminalSummary(...)`：官方大岛模板 4 的快捷组合。
- `MiFocusIslandSpec.terminalIconSummary(...)`：官方大岛模板 4 的显式图文组合入口。
- `MiFocusIslandSpec.fixedDigitSummary(...)`：官方大岛模板 7 的快捷组合。
- `MiFocusIslandSpec.largePictureSummary(...)`：官方大岛模板 8 的快捷组合。
- `MiFocusIslandSpec.dualImageTextSummary(...)`：官方大岛模板 9 + 小岛模板 3 的快捷组合。
- `MiFocusIslandPic.officialStatic(...)`：官方图标图片语义，`pic.type = 1`。
- `MiFocusIslandPic.officialCompact(...)`：官方紧凑图片语义，`pic.type = 4`。
- `MiFocusIslandBigTemplate.officialImageTextLeft(...)`：官方图文组件1，`type = 1`。
- `MiFocusIslandBigTemplate.officialPicture(...)`：官方大岛图片组件。
- `MiFocusIslandBigTemplate.officialIconTextRight(...)`：官方图文组件2，`type = 2`。
- `MiFocusIslandBigTemplate.officialTerminalTextRight(...)`：官方终态短词图文，`type = 3`。
- `MiFocusIslandBigTemplate.officialDualImageTextLeft(...)`：官方双图文左侧结构，`type = 5`。
- `MiFocusIslandBigTemplate.officialDualImageTextRight(...)`：官方双图文右侧结构，`type = 6`。
- `MiFocusIslandSmallTemplate.officialPicture(...)`：官方小岛图标结构。
- `MiFocusIslandSmallTemplate.officialProgressPicture(...)`：官方小岛图标进度结构。
- `MiFocusIslandSmallTemplate.officialImageTextRight(...)`：官方小岛图标文本结构，`type = 6`，内部 `pic.type = 4`。

官方摘要态覆盖：

- 大岛模板 1：`MiFocusOfficialTemplatePresets.summaryTemplate1IconOnly(...)`
- 大岛模板 2：`MiFocusOfficialTemplatePresets.summaryTemplate2Text(...)`
- 大岛模板 3：`MiFocusOfficialTemplatePresets.summaryTemplate3IconText(...)`
- 大岛模板 4：`MiFocusOfficialTemplatePresets.summaryTemplate4TerminalIconText(...)`
- 大岛模板 5：`MiFocusOfficialTemplatePresets.summaryTemplate5ProgressText(...)`
- 大岛模板 6：`MiFocusOfficialTemplatePresets.summaryTemplate6Countdown(...)`
- 大岛模板 7：`MiFocusOfficialTemplatePresets.summaryTemplate7FixedDigit(...)`
- 大岛模板 8：`MiFocusOfficialTemplatePresets.summaryTemplate8LargePicture(...)`
- 大岛模板 9：`MiFocusOfficialTemplatePresets.summaryTemplate9DualImageText(...)`
- 小岛模板 1：`MiFocusOfficialTemplatePresets.smallTemplate1Icon(...)`
- 小岛模板 2：`MiFocusOfficialTemplatePresets.smallTemplate2ProgressIcon(...)`
- 小岛模板 3：`MiFocusOfficialTemplatePresets.smallTemplate3IconText(...)`

推荐组合：

- 进行中进度：`ImageTextLeft + ProgressText + CombinePic`
- 倒计时：`ImageTextLeft + SameWidthDigit + CombinePic`
- 完成/失败/取消/已读：`ImageTextLeft + ImageTextRight(type = 3) + Picture`
- 导航红绿灯/短数字提醒：大岛 `ImageTextLeft(type = 5) + ImageTextRight(type = 6)`，小岛 `ImageTextRight(type = 6)`

现成 helper：

- `MiFocusIslandSpec.progressSummary(...)`
- `MiFocusIslandSpec.countdownSummary(...)`
- `MiFocusIslandSpec.terminalSummary(...)`
- `MiFocusIslandSpec.iconTextSummary(...)`
- `MiFocusIslandSpec.terminalIconSummary(...)`
- `MiFocusIslandSpec.iconOnlySummary(...)`
- `MiFocusIslandSpec.textOnlySummary(...)`
- `MiFocusIslandSpec.fixedDigitSummary(...)`
- `MiFocusIslandSpec.largePictureSummary(...)`
- `MiFocusIslandSpec.dualImageTextSummary(...)`
- `MiFocusIslandBigTemplate.officialImageTextLeft(...)`
- `MiFocusIslandBigTemplate.officialPicture(...)`
- `MiFocusIslandBigTemplate.officialIconTextRight(...)`
- `MiFocusIslandBigTemplate.officialTerminalTextRight(...)`
- `MiFocusIslandBigTemplate.officialDualImageTextLeft(...)`
- `MiFocusIslandBigTemplate.officialDualImageTextRight(...)`
- `MiFocusIslandSmallTemplate.officialPicture(...)`
- `MiFocusIslandSmallTemplate.officialProgressPicture(...)`
- `MiFocusIslandSmallTemplate.officialImageTextRight(...)`
- `MiFocusOfficialTemplatePresets.*`

当前实现说明：

- 本地 V3 协议引擎覆盖小岛 `picInfo`、`combinePicInfo` 与 `imageTextInfoRight(type = 6)`。
- 官方 summary 1-9 和小岛模板 1-3 现在都能通过本地 summary helper / preset 直接表达，`type = 1 / 2 / 3 / 5 / 6` 与 `pic.type = 1 / 4` 的公开语义集中在一处。
- `MiFocusIslandSmallTemplate.ImageTextRight` 会在首次编码时写入官方 JSON 结构，可以直接使用。
- `MiFocusIslandBigTemplate.Picture / ImageTextLeft / ImageTextRight` 和 `MiFocusIslandSmallTemplate.Picture / CombinePic / ImageTextRight` 继续保留为通用 fallback 层；公共官方模板优先走 `official*` helper。

## 分层建议

推荐把这个 module 理解成三层：

- `official helper / preset`：
  优先给公开模板和稳定调用面使用，包含 `MiFocusIslandSpec.*Summary(...)`、`MiFocusExpandedSpec.*` convenience facade、`MiFocusOfficialTemplatePresets.*`。
- `generic fallback`：
  给未归档的新玩法、兼容老代码、精细定制结构时使用，包含 `MiFocusIslandBigTemplate.*`、`MiFocusIslandSmallTemplate.*`、`MiFocusExpandedComponent.*` 的原始数据类构造器。
- `private override`：
  给宿主层 `RemoteViews` 后处理与 APK 经验沉淀使用，入口是 `MiFocusNotificationSpec.privateOverrides` 及其相关私有模型。

公开模板优先走第一层；只有当官方 helper 不够表达当前场景时，再退到 generic fallback；宿主私有魔改独立放在第三层。

## 展开态模板

展开态组件，对应 `MiFocusExpandedComponent`：

- `Base`：基础标题正文，项目默认展开态。
- `Chat`：头像/应用包名类图文。
- `Highlight`：强调图文。
- `Hint`：提示 + 可选按钮。
- `Progress`：展开态进度条。
- `Picture`：通用识别图形/功能图，适合私有样式或未归档的宿主玩法。
- `OfficialPicture`：官方公开 `picInfo` 组件，覆盖 `type = 1/2/3/5` 的公开语义。
- `Background`：背景色/背景图。
- `Cover`：封面图。
- `HighlightV3`：新高亮信息块。
- `IconText`：通用图文组件，适合兼容旧入口或私有组合。
- `OfficialNewImageText`：官方新图文组件，覆盖 14/15/16/17/21/22 这一支公开语义。
- `MultiProgress`：多段/节点进度。
- `OfficialMultiProgress`：官方进度组件3，覆盖 19/21/22 这一支公开语义。
- `AnimText`：动画文本 + 可选计时器。
- `TextButtons`：展开态文字按钮，项目约定最多 2 个，对应官方 `textButton`。
- `OfficialTextButtons`：官方文字按钮组件，覆盖 17 和同类 `textButton` 语义。
- `Actions`：官方 `按钮组件1(actions)`，适合 1-3 个普通/进度按钮，或 1 个单独文字按钮。
- `OfficialActions`：官方操作按钮组件，覆盖 12/13/15 和同类 `actions` 语义。
- `OfficialHint`：官方提示按钮组件，覆盖 8/9/10/11 和同类按钮组件2/3 语义。
- `OfficialHighlightCapsule`：官方高亮胶囊按钮组件，覆盖 16/18 和同类按钮组件5 语义。

推荐按钮策略：

- `TextButtons(actions = listOf(secondary, primary))`：双按钮确认流，顺序保持左次右主。
- `HighlightV3(action = primaryCapsule)`：单主操作流，视觉更接近官方强调 CTA。
- 药品提醒、打卡、车检这类“左右并列操作”优先用 `TextButtons`。
- 价格变动、抢票、支付、前往处理这类“单主操作”优先用 `HighlightV3`。

现成 helper：

- `MiFocusExpandedSpec.dualTextButtons(...)`
- `MiFocusExpandedSpec.hintAction(...)`
- `MiFocusExpandedSpec.highlightCapsuleAction(...)`
- `MiFocusExpandedComponent.officialBasePrimary(...)`
- `MiFocusExpandedComponent.officialBaseSecondary(...)`
- `MiFocusExpandedComponent.officialChat(...)`
- `MiFocusExpandedComponent.officialHighlight(...)`
- `MiFocusExpandedComponent.officialHintSecondary(...)`
- `MiFocusExpandedComponent.officialHintPrimary(...)`
- `MiFocusExpandedComponent.officialActions(...)`
- `MiFocusExpandedComponent.button2Hint(...)`
- `MiFocusExpandedComponent.button3Hint(...)`
- `MiFocusExpandedComponent.button1Actions(...)`
- `MiFocusExpandedComponent.officialPictureAppIcon(...)`
- `MiFocusExpandedComponent.officialPictureMiddle(...)`
- `MiFocusExpandedComponent.officialPictureLarge(...)`
- `MiFocusExpandedComponent.officialPictureCountdown(...)`
- `MiFocusExpandedComponent.officialProgressNodes(...)`
- `MiFocusExpandedComponent.officialProgressBar(...)`
- `MiFocusExpandedComponent.officialCover(...)`
- `MiFocusExpandedComponent.officialNewImageText(...)`
- `MiFocusExpandedComponent.officialMultiProgress(...)`
- `MiFocusExpandedComponent.officialTextButtons(...)`
- `MiFocusExpandedComponent.officialHighlightCapsule(...)`
- `MiFocusExpandedComponent.button3IconText(...)`
- `MiFocusExpandedComponent.button4TextButtons(...)`
- `MiFocusExpandedComponent.button5Highlight(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate1BaseLargePicture(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate2BaseAppIcon(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate3ChatMiddlePicture(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate4BaseAppIconProgressNodes(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate5BaseAppIconProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate6BaseAppIconProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate7ChatAppIconProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate8ChatButton3(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate9BaseButton2(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate10BaseButton3(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate11HighlightButton2(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate12ChatActions(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate13HighlightActions(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate14_1NewImageText(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate14_2NewImageTextCountdownPicture(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate15NewImageTextActions(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate15NewImageTextTextButton(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate16NewImageTextHighlight(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate17NewImageTextTextButtons(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate18CoverHighlight(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate19PictureMultiProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate20ChatProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate21NewImageTextMultiProgress(...)`
- `MiFocusOfficialTemplatePresets.expandedTemplate22NewImageTextMultiProgress(...)`

`TextButtons` 当前显式限制 1-2 个 action，超出范围会在模型层直接拒绝。

`Actions` 当前显式限制：

- 1-3 个 action
- `Text` 类型只能单独使用 1 个
- `Progress` 类型必须携带进度元数据
- 本地协议引擎直接写入 `param_v2.actions`，同时同步写入 `miui.focus.actions`

官方参考模板映射：

- 模版1 `文本组件1 + 识别图形组件3`：优先看 `expandedTemplate1BaseLargePicture(...)`
- 模版2 `文本组件2 + 识别图形组件1`：优先看 `expandedTemplate2BaseAppIcon(...)`
- 模版3 `IM图文组件 + 识别图形组件2`：优先看 `expandedTemplate3ChatMiddlePicture(...)`
- 模版4 `文本组件2 + 识别图形组件1 + 进度组件1`：优先看 `expandedTemplate4BaseAppIconProgressNodes(...)`
- 模版5 `文本组件1 + 识别图形组件1 + 进度组件2`：优先看 `expandedTemplate5BaseAppIconProgress(...)`
- 模版6 `文本组件2 + 识别图形组件1 + 进度组件2`：优先看 `expandedTemplate6BaseAppIconProgress(...)`
- 模版7 `IM图文组件 + 识别图形组件1 + 进度组件2`：优先看 `expandedTemplate7ChatAppIconProgress(...)`
- 模版8 `IM图文组件 + 识别图形组件1 + 按钮组件3`：优先看 `expandedTemplate8ChatButton3(...)`
- 模版9 `文本组件2 + 识别图形组件1 + 按钮组件2`：优先看 `expandedTemplate9BaseButton2(...)`
- 模版10 `文本组件2 + 识别图形组件1 + 按钮组件3`：优先看 `expandedTemplate10BaseButton3(...)`
- 模版11 `强调图文组件 + 识别图形组件1 + 按钮组件2`：优先看 `expandedTemplate11HighlightButton2(...)`
- 模版12 `IM图文组件 + 按钮组件1`：优先看 `expandedTemplate12ChatActions(...)`
- 模版13 `强调图文组件 + 按钮组件1`：优先看 `expandedTemplate13HighlightActions(...)`
- OS3 `textButton`：优先看 `MiFocusExpandedComponent.button4TextButtons(...)`
- OS3 `highlightInfoV3`：优先看 `MiFocusExpandedComponent.button5Highlight(...)`
- 模版14-1 `新图文组件`：优先看 `expandedTemplate14_1NewImageText(...)`
- 模版14-2 `新图文组件 + 倒计时带图组件`：优先看 `expandedTemplate14_2NewImageTextCountdownPicture(...)`
- 模版15 `新图文组件 + 按钮组件1`：优先看 `expandedTemplate15NewImageTextActions(...)`
- 模版16 `新图文组件 + 识别图形组件1 + 按钮组件5`：优先看 `expandedTemplate16NewImageTextHighlight(...)`
- 模版17 `新图文组件 + 识别图形组件1 + 按钮组件4`：优先看 `expandedTemplate17NewImageTextTextButtons(...)`
- 模版18 `封面组件 + 识别图形组件1 + 按钮组件5`：优先看 `expandedTemplate18CoverHighlight(...)`
- 模版19 `文本组件2 + 识别图形组件1 + 进度组件3`：优先看 `expandedTemplate19PictureMultiProgress(...)`
- 模版20 `IM图文组件 + 进度组件2`：优先看 `expandedTemplate20ChatProgress(...)`
- 模版21 `新图文组件 + 识别图形组件1 + 进度组件3`：优先看 `expandedTemplate21NewImageTextMultiProgress(...)`
- 模版22 `网页新增编号，同 21 结构族`：优先看 `expandedTemplate22NewImageTextMultiProgress(...)`

当前 8-22 builder 的项目映射策略：

- 模版8、10 走 `Hint(type = 1)`，对应官方 `按钮组件3`。
- 模版9、11 走 `Hint(type = 2)`，对应官方 `按钮组件2`。
- 模版12、13、15 走 `Actions`，模块会在最终 JSON 中补齐官方 `actions` 数组。
- 官方 preset 里的 `按钮组件1` 现在统一走 `officialActions(...)` helper。
- 官方 preset 里的 `按钮组件2` 现在统一走 `officialHintSecondary(...)` helper。
- 官方 preset 里的 `按钮组件3` 现在统一走 `officialHintPrimary(...)` helper。
- 模版14-1 走 `OfficialNewImageText`，通过 `OfficialNewImageTextPayload` 承载新图文主图和正文。
- 模版14-2 走 `OfficialNewImageText + OfficialPicture(type = 5)`，并通过局部模型 `OfficialPictureType5Payload` 承载倒计时标题色块，模块会在最终 JSON 中补齐官方 `picInfo(type = 5)` 的 `title` / `colorTitle`。
- 官方 preset 里的 `IM图文组件` 现在统一走 `officialChat(...)` helper。
- 官方 preset 里的 `强调图文组件` 现在统一走 `officialHighlight(...)` helper。
- 官方 preset 里的 `识别图形组件1/2/3/倒计时带图组件` 现在统一走 `OfficialPicture` helper，公开模板语义集中在一处。
- 官方 preset 里的 `进度组件1/2` 现在统一走 `officialProgressNodes` / `officialProgressBar` helper，节点进度和纯进度条语义集中在一处。
- 官方 preset 里的 `文本组件1/2` 现在统一走 `officialBasePrimary` / `officialBaseSecondary` helper，公开文本语义集中在一处。
- 官方 preset 里的 `新图文组件` 现在统一走 `officialNewImageText(...)` helper，并通过 `MiFocusAnimIcon` 承载主图。
- 官方 preset 里的 `封面组件` 现在统一走 `officialCover(...)` helper。
- 官方 preset 里的 `进度组件3` 现在统一走 `officialMultiProgress(...)` helper。
- 模版16、18 走 `highlightInfoV3`，并统一走 `officialHighlightCapsule(...)` helper，适合单主操作流。
- 模版17 走 `textButton` 双按钮，并统一走 `officialTextButtons(...)` helper，适合左右并列确认流。
- 模版19、21、22 走 `MultiProgress`，适合余额、阶段、节点式进度。
- 模版20 走 `Chat + Progress`，适合下载、安装、资源拉取。

### KeiOS 下载与安装链路

- 已知 APK 总大小且进度位于 `1..99`：摘要态使用官方大岛模板 5 + 小岛模板 2，展开态使用模板 6 `文本组件2 + 识别图形组件1 + 进度组件2`。
- 展开态的 `baseInfo` 只承载阶段标题、版本标签和 App 目标，`progressInfo` 只承载真实百分比与进度颜色，避免同一状态在标题、正文和进度组件中重复出现。
- 版本号使用文本组件 2 的 `specialTitle` 彩色标签，正文只保留 App 名称和必要的字节信息；标签文本使用高对比前景色，背景跟随当前下载/成功/失败强调色。
- 未知总大小的下载使用图标 + `下载中` 状态，正文保留目标和已下载字节，不绘制 `0%` 进度环。
- 准备安装、等待确认、提交安装、检测安装和完成态使用图标 + 短状态，不用固定阶段百分比模拟下载进度。
- Live Updates 与 Xiaomi Focus 共用同一判定：只有真实可计算的下载进度进入进度样式，其余阶段进入状态样式。
- 模板 20 适合 OS3 游戏/应用下载，但当前通用链路优先模板 6，以覆盖 OS2/OS3 并保留独立应用图标位。

`新图文组件` 这一支现在也抽成了更明确的局部模型 `OfficialNewImageTextPayload`：

- 适合官方公开的 `14-1 / 14-2 / 15 / 16 / 17 / 21 / 22` 新图文组件家族。
- `OfficialNewImageText` 本体负责官方新图文组件语义，`OfficialNewImageTextPayload` 负责文本、主图和可选 `type` 聚合。
- `officialNewImageText(...)` 支持直接传 `OfficialNewImageTextPayload`。
- `expandedTemplate14_1NewImageText(...)`、`expandedTemplate14_2NewImageTextCountdownPicture(...)`、`expandedTemplate15NewImageTextActions(...)`、`expandedTemplate16NewImageTextHighlight(...)`、`expandedTemplate17NewImageTextTextButtons(...)`、`expandedTemplate21NewImageTextMultiProgress(...)`、`expandedTemplate22NewImageTextMultiProgress(...)` 现在都支持直接传 `OfficialNewImageTextPayload`。
- `button3IconText(...)` 继续保留通用 `IconText` 入口，适合旧 builder 或私有图文玩法。

`进度组件3` 这一支现在也抽成了更明确的局部模型 `OfficialMultiProgressPayload`：

- 适合官方公开的 `19 / 21 / 22` 进度组件3 家族。
- `OfficialMultiProgress` 本体负责官方多段进度语义，`OfficialMultiProgressPayload` 负责进度值、颜色、节点数和可选文本聚合。
- `officialMultiProgress(...)` 支持直接传 `OfficialMultiProgressPayload`。
- `expandedTemplate19PictureMultiProgress(...)`、`expandedTemplate21NewImageTextMultiProgress(...)`、`expandedTemplate22NewImageTextMultiProgress(...)` 现在都支持直接传 `OfficialMultiProgressPayload`。
- 通用 `MultiProgress` 入口继续保留，适合旧 builder 或私有多段进度玩法。

`按钮组件1 / textButton` 这一支现在也开始抽成更明确的局部模型：

- `OfficialActionsPayload` 对应官方 `按钮组件1(actions)`，适合 `12 / 13 / 15` 和同类操作流。
- `OfficialTextButtonsPayload` 对应官方 `按钮组件4(textButton)`，适合 `17` 和同类左右并列文字按钮。
- `OfficialActions` / `OfficialTextButtons` 本体负责官方按钮家族语义，payload 负责 action 列表聚合。
- `officialActions(...)`、`officialTextButtons(...)` 现在都支持直接传对应 payload。
- `expandedTemplate12ChatActions(...)`、`expandedTemplate13HighlightActions(...)`、`expandedTemplate15NewImageTextActions(...)`、`expandedTemplate17NewImageTextTextButtons(...)` 现在都支持直接传官方按钮 payload。
- 通用 `Actions` / `TextButtons` 入口继续保留，适合旧 builder 或私有按钮玩法。

`按钮组件2 / 3 / 5` 这一支也完成了本地模型收口：

- `OfficialHintPayload` 对应官方 `按钮组件2 / 按钮组件3`，通过 `type = 2 / 1` 固定二级提示和主提示的公开语义。
- `OfficialHighlightCapsulePayload` 对应官方 `按钮组件5(highlightInfoV3)`，聚合高亮文案、色彩、胶囊标签和 action。
- `officialHintSecondary(...)`、`officialHintPrimary(...)`、`officialHighlightCapsule(...)` 都支持直接传对应 payload。
- `expandedTemplate8ChatButton3(...)`、`expandedTemplate9BaseButton2(...)`、`expandedTemplate10BaseButton3(...)`、`expandedTemplate11HighlightButton2(...)` 支持直接传 `OfficialHintPayload`。
- `expandedTemplate16NewImageTextHighlight(...)`、`expandedTemplate18CoverHighlight(...)` 支持直接传 `OfficialHighlightCapsulePayload`。
- 通用 `Hint` / `HighlightV3` 入口继续保留，适合旧 builder 或私有按钮玩法。

当前 1-7 builder 的项目映射策略：

- 模版1 走 `Base(type = 1) + OfficialPicture(type = 3)`，适合天气、导航概览、大图预览。
- 模版2 走 `Base(type = 2) + OfficialPicture(type = 1)`，适合支付、结果确认、简洁状态卡。
- 模版3 走 `Chat + OfficialPicture(type = 2)`，适合 IM、联系人、中图消息卡。
- 模版4 走 `Base(type = 2) + OfficialPicture(type = 1) + officialProgressNodes(...)`，节点图资源由调用方传入，适合打车、外卖、阶段流转。
- 模版5 走 `Base(type = 1) + OfficialPicture(type = 1) + officialProgressBar(...)`，适合任务处理、进程状态。
- 模版6 走 `Base(type = 2) + OfficialPicture(type = 1) + officialProgressBar(...)`，适合下载、上传、同步。
- 模版7 走 `Chat + OfficialPicture(type = 1) + officialProgressBar(...)`，适合群组同步、社交侧进度通知。

`Picture(type = 5)` 这一支现在继续抽成了更明确的局部模型 `OfficialPictureType5Payload`：

- 适合官方公开的倒计时带图组件和同类扩展字段。
- `officialPictureAppIcon` / `officialPictureMiddle` / `officialPictureLarge` / `officialPictureCountdown` 对应公开 `type = 1 / 2 / 3 / 5`。
- `officialProgressNodes` / `officialProgressBar` 对应公开 `进度组件1 / 进度组件2`。
- `officialBasePrimary` / `officialBaseSecondary` 对应公开 `文本组件1 / 文本组件2`。
- `officialChat` / `officialHighlight` / `officialCover` / `officialNewImageText` / `officialMultiProgress` 对应公开 `IM图文组件 / 强调图文组件 / 封面组件 / 新图文组件 / 进度组件3`。
- `officialActions` / `officialHintSecondary` / `officialHintPrimary` / `officialTextButtons` / `officialHighlightCapsule` 对应公开 `按钮组件1 / 按钮组件2 / 按钮组件3 / 按钮组件4 / 按钮组件5`。
- `OfficialPicture` 本体负责公开图片类型，`OfficialPictureType5Payload` 负责 `type = 5` 扩展字段，后续继续补 `picInfo` 扩展时可以沿着这个局部模型收口。
- `officialPictureCountdown(...)` 和 `expandedTemplate14_2NewImageTextCountdownPicture(...)` 现在都支持直接传 `OfficialPictureType5Payload`。
- 当前 `title` / `colorTitle` 只在 `type = 5` 时补齐到最终 JSON。
- `CountdownPicture` 当前作为兼容入口保留，方便已有调用平滑迁移；新的公开 preset 和新代码优先走 `OfficialPicture(type = 5)`。

## 私有魔改经验

来自 `miai_4.0.6.apk` 的高频经验：

- `TakeMedicineHelper`：双按钮文案走公开字段，左右图标走私有 `RemoteViews` 覆写。
- `HyperMindHabitHelper` / `PrepareCarReminderHelper`：隐藏正文，改用 `tv_subtitle_right` 承载次级信息。
- `MovieReminderHelper`：下载网络图，裁剪并加圆角后写入背景图位，再替换右侧二维码图标。
- `MetroHelper`：隐藏正文，改用最多 3 个彩色标签 chip 展示线路信息，右侧保留二维码动作。
- `CommonHelper`：保留最简公共模板，只做少量显示裁剪。

项目内对应的经验模型：

- `MiFocusPrivateContentMode`：正文 / 副标题右侧 / 标签化 / 双行拆分
- `MiFocusPrivateSplitRule`：按分隔符拆正文，适合电影、票务、提醒
- `MiFocusPrivateActionIcons`：按 surface 覆写左右 action icon
- `MiFocusPrivateLabelChip`：地铁/线路/标签类彩色 chip
- `MiFocusPrivateBackgroundArtwork`：背景图裁剪、圆角、surface 限定
- `MiFocusPrivateOverrides`：统一挂在 `MiFocusNotificationSpec` 上，供宿主层后处理读取

当前状态：

- 这些私有字段已经进入我们自己的模型层。
- 当前 `MiFocusNotificationTemplate.build(...)` 只负责公开 `miui.focus.*` 数据构建。
- 私有 `RemoteViews` 后处理会放在宿主通知链路实现，避免把过期第三方库当成能力边界。

## KeiOS 复杂通知映射

当前业务链路优先使用以下公开模板语义：

- APK 下载：大岛模板 5 + 小岛模板 2，展开态模板 6；版本号使用 `specialTitle`，仅真实字节进度写入 `progressInfo`。
- APK 解析、准备、确认、安装和完成：摘要态终态短词 + 展开态基础图文；这些阶段不写模拟进度。
- GitHub 刷新：摘要态进度图文 + 小岛进度环，展开态使用单一 `progressInfo`。
- Actions 新构建：摘要态终态图文，构建编号使用 `specialTitle`，展开态使用双文字按钮。
- WebDAV 同步：运行态使用单进度，终态状态使用 `specialTitle`，结果数量放入正文。
- BA AP：当前 AP 使用进度摘要和单进度条；“打开/已读”使用双文字按钮。
- BA 日程和卡池：摘要态使用系统倒计时；服务器名称使用 `specialTitle`，展开态不重复绘制伪阶段进度。
- BA 日常完成：终态短词摘要（`ImageTextRight`，`type = 3`）+ 小岛图标；`specialTitle` 放“日常”，正文放非零结果，
  强调色用完成绿 `#22C55E`。不写任何进度：这条通知在模板已经套用之后才发出，没有进行中的会话可报告。
  `ongoing = false` 且不请求 promoted ongoing——终态请求 promoted ongoing 会让 SystemUI 收掉进度岛只留普通通知。
  `orderId` 固定为 `ba-daily-done`，与它唯一的通知 id 一致，所以第二次运行改写同一张卡而不是叠一张新的。
  **Android Live Updates 一侧要同时改**：`ModernNotificationSpecResolver` 里它必须算进 `isOneShotBaEvent`，
  否则 `ongoing = running || state.ongoing` 会成立、`requestPromotedOngoing` 跟着成立，终态被发成不可划掉的
  `ONGOING_EVENT|PROMOTED_ONGOING` + `ProgressStyle`（AVD 上实测如此）。只改超级岛一侧不够——非 HyperOS 设备走的是
  这条路径，两条路径各有一套 per-feature 判别。
- MCP 服务：大岛模板 7 的定宽数字展示客户端数，小岛模板 3 展示图标 + 短数字，运行状态使用 `specialTitle`。

通用 Live payload 提供三层 Focus 专用文案：

- `miFocusTitle`：短主标题。
- `miFocusSpecialTitle`：版本、构建号、服务器或状态标签。
- `miFocusContent`：去除标签重复后的正文。

普通通知和 Android Live Updates 继续使用完整 `overrideTitle/overrideContent`。网络暴露模式必须由服务状态显式透传；不要根据端口、路径或地址字符串推断“本机/局域网”。

## 语义化图标 (Semantic Icon)

通知构建器支持 `semanticIconBitmap`：

- 如果 `NotificationPayload` 中携带了 `semanticIconBitmap`，构建器将优先使用该位图作为 `LargeIcon`。
- 允许根据通知内容动态生成图标（如 GitHub 项目 Logo、学生头像等）。
- 在超级岛展开态中，该图标将作为主视觉元素展示。

## 最小示例

```kotlin
val extras = MiFocusNotificationTemplate.build(
    context = context,
    spec = MiFocusNotificationSpec(
        title = "GitHub 刷新中",
        content = "已刷新 2/4 个项目",
        displayIconResId = R.drawable.ic_github_invertocat_island_blue,
        island = MiFocusIslandSpec.progressSummary(
            progressPercent = 50,
            content = "2/4",
            colorReach = "#2563EB",
            colorUnReach = "#334155"
        ),
        expanded = MiFocusExpandedSpec.base(
            title = "GitHub 刷新中",
            content = "已刷新 2/4 个项目"
        )
    )
)
```

## 双按钮示例

```kotlin
val ignoreAction = MiFocusNotificationAction(
    key = "ignore",
    title = "忽略",
    pendingIntent = ignorePendingIntent
).asSecondaryTextButton()

val doneAction = MiFocusNotificationAction(
    key = "done",
    title = "已处理",
    pendingIntent = donePendingIntent
).asPrimaryTextButton(
    backgroundColor = "#22C55E",
    pressedBackgroundColor = "#16A34A"
)

val spec = MiFocusNotificationSpec(
    title = "提醒",
    content = "19:30 | 多维元素片 [x2.0]",
    displayIconResId = R.drawable.ic_notification_logo,
    expanded = MiFocusExpandedSpec.dualTextButtons(
        text = MiFocusExpandedText(
            title = "时间到啦，别忘记按时处理哦",
            content = "19:30 | 多维元素片 [x2.0]"
        ),
        secondaryAction = ignoreAction,
        primaryAction = doneAction,
        picture = MiFocusPictureRef.Display
    )
)
```

## 圆头高亮按钮示例

```kotlin
val payAction = MiFocusNotificationAction(
    key = "pay",
    title = "去支付",
    pendingIntent = payPendingIntent,
    iconResId = R.drawable.ic_notification_logo
).asHighlightCapsuleButton(
    backgroundColor = "#006EFF",
    pressedBackgroundColor = "#0057CC"
)

val spec = MiFocusExpandedSpec.highlightCapsuleAction(
    text = MiFocusExpandedText(title = "限时优惠"),
    primaryText = "4899元",
    secondaryText = "4999元",
    label = "限时优惠",
    labelBgColor = "#E8F0FF",
    labelColor = "#006EFF",
    action = payAction
)
```

## R8

`core-notification` consumer rules保留 `os.kei.core.notification.focus.**` 类名，方便 release
环境定位 Focus payload。协议引擎使用显式 JSON 字段，无需反射序列化规则。

新增超级岛模板时优先复用 `MiFocusNotificationTemplate`，保持 JSON 拼接、图片注册、Action 注册和 R8
规则集中维护。
