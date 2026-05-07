# 小米超级岛模板速查

项目封装入口：

- `os.kei.core.notification.focus.MiFocusNotificationTemplate.build(context, spec)`
- 输出 `Bundle`，内部包含 `miui.focus.param`、`miui.focus.pics`、`miui.focus.actions`
- 调用方式：`NotificationCompat.Builder(...).addExtras(MiFocusNotificationTemplate.build(...))`

## 设计规范

摘要态用于一眼识别当前状态。大字优先使用 2-4 个中文字符、1-3 位数字或短百分比；完整标题、正文、统计信息放在展开态。

展开态用于承载完整标题、正文、进度和操作。项目内按钮最多放 2 个，主操作高亮，次操作普通。

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

推荐组合：

- 进行中进度：`ImageTextLeft + ProgressText + CombinePic`
- 倒计时：`ImageTextLeft + SameWidthDigit + CombinePic`
- 完成/失败/取消/已读：`ImageTextLeft + ImageTextRight(type = 3) + Picture`

## 展开态模板

展开态组件，对应 `MiFocusExpandedComponent`：

- `Base`：基础标题正文，项目默认展开态。
- `Chat`：头像/应用包名类图文。
- `Highlight`：强调图文。
- `Hint`：提示 + 可选按钮。
- `Progress`：展开态进度条。
- `Picture`：识别图形/功能图。
- `Background`：背景色/背景图。
- `Cover`：封面图。
- `HighlightV3`：新高亮信息块。
- `IconText`：图文组件。
- `MultiProgress`：多段/节点进度。
- `AnimText`：动画文本 + 可选计时器。
- `TextButtons`：展开态文字按钮，项目约定最多 2 个。

## 最小示例

```kotlin
val extras = MiFocusNotificationTemplate.build(
    context = context,
    spec = MiFocusNotificationSpec(
        title = "GitHub 刷新中",
        content = "已刷新 2/4 个项目",
        displayIconResId = R.drawable.ic_github_invertocat_island_blue,
        island = MiFocusIslandSpec(
            bigTemplates = listOf(
                MiFocusIslandBigTemplate.ImageTextLeft(),
                MiFocusIslandBigTemplate.ProgressText(
                    text = MiFocusIslandText(title = "50%", content = "2/4"),
                    progress = MiFocusIslandProgress(
                        progressPercent = 50,
                        colorReach = "#2563EB",
                        colorUnReach = "#334155"
                    )
                )
            ),
            smallTemplate = MiFocusIslandSmallTemplate.CombinePic(
                progress = MiFocusIslandProgress(50, "#2563EB", "#334155")
            )
        ),
        expanded = MiFocusExpandedSpec(
            components = listOf(
                MiFocusExpandedComponent.Base(
                    text = MiFocusExpandedText(
                        title = "GitHub 刷新中",
                        content = "已刷新 2/4 个项目"
                    )
                )
            )
        )
    )
)
```

## R8

`app/proguard-rules.pro` 已保留：

- `com.xzakota.hyper.notification.**$$serializer`
- focus-api 模板字段
- `os.kei.core.notification.focus.**` 类名

新增超级岛模板时优先复用 `MiFocusNotificationTemplate`，保持 JSON 拼接、图片注册、Action 注册和 R8
规则集中维护。
