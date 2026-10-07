# BA 3D 模型索引

[English](ba-model-3d-index.md)

图鉴的 3D 入口打开由 App 管理的 WebView，使用本地打包的 Three.js 和原生 MIUIX 控件。浏览图鉴时只显示入口图片，进入查看器后才下载当前模型、创建 WebGL。现有 GameKee Spine 链路继续独立运行。

## 身份与资源

三个 ID 各自保留：GameKee `contentId` 标识文章，游戏 `CharacterId` 标识学生及其换装，`DevName` 标识开发资源。`CH`、`NP` 等前缀属于身份的一部分，不应丢掉后只比较数字。

`app/src/main/assets/ba3d/catalog.json` 分为两层。`bindings` 保存人工核对的文章、游戏 ID、开发 ID、WIKI 页面和默认文件对应关系；`groups` 保存 WIKI 的模型分组、精确文件名、固定仓库 revision、大小和 Git blob 哈希。一个学生可以拥有多个模型，换装文章会直接选中对应换装，同时允许查看同一分组的其他模型。

首批核对了 12 个文章：柯伊，日奈普通／泳装／礼服，优香普通／运动服／睡衣，爱丽丝普通／女仆／临战，白子普通／恐怖。清单共收录 295 个模型描述，但尚未建立全部 GameKee 学生的文章对应关系。

| GameKee contentId | CharacterId | DevName | 默认文件 |
| --- | --- | --- | --- |
| 690582 | 10135 | CH0335 | Kei.glb |
| 59934 | 10004 | Hina_default | Hina.glb |
| 170295 | 10053 | CH0184 | Yuuka (Sportswear).glb |
| 162557 | 10100 | CH0263 | Shiroko＊Terror.glb |
| 72904 | 10015 | Aris_default | Arisu.glb |

旧资源可能使用 `Hina_Original`、`Aris_Original`，而 WIKI 记录为 `*_default`。这类别名逐项登记，不能推广成所有后缀都可互换。爱丽丝的开发拼写是 `Aris`，文件名却是 `Arisu`；白子＊恐怖的节点使用 CH0263，但部分纹理名使用 EN0002。柯伊 Prototype 还使用 NP0269。这些例子说明，直接拼接开发 ID 或根据任意纹理名猜文件会选错。

## 匹配与维护

1. 开发 ID 只规范全半角、大小写和 CH／NP 的分隔符、补零形式，保留前缀与变体。纯数字的 CharacterId 或文章 ID 不解释成开发 ID。
2. 优先使用已确认的文章对应；图鉴提供明确的开发 ID 或 CharacterId 时也可定位已登记资源。明确身份彼此冲突、无法解析或前缀不同，均不匹配。
3. 中文名、日文名、英文名及别名可以在维护时生成候选，不参与运行时模糊猜测。没有可靠身份的文章不显示 3D 入口。
4. 扩充 `bindings` 前同时核对 GameKee 文章、WIKI 的 CharacterId／DevName 和精确文件名，换装单独核对。
5. `scripts/ba/generate_model_catalog.py` 可从保存的 WIKI Models HTML 与同一 revision 的 GitHub 完整树重新生成资源层，保留已有身份层。默认资源消失、树不完整、来源 revision 不一致时停止生成，留给维护者复核。

生成示例（输入文件由维护者从相应官方来源保存）：

```bash
python3 scripts/ba/generate_model_catalog.py \
  --wiki-html wiki-models.html --tree-json tree.json \
  --bindings-from app/src/main/assets/ba3d/catalog.json \
  --revision <完整提交SHA> --output catalog.next.json
```

复核差异后替换资源清单，运行索引／缓存测试，并验收受影响模型。

## 查看器与缓存

本地 HTML／JS 使用 app-assets HTTPS 域，WebView 拦截任意外部请求及跳转，只向浏览器提供当前清单允许的模型。256 MiB 的磁盘 LRU 缓存按 Git blob 身份复用；下载先写临时文件，校验长度和 `SHA1("blob <size>\0" + 内容)` 后原子提交。Git blob 哈希不同于文件原始 SHA256。关闭查看器或切换模型会取消对应请求，部分文件不进入有效缓存；切换或退出还会释放旧模型的几何、材质、纹理和动画资源。

渲染使用贴图无光照材质、骨骼／形变动画、默认隐藏的可选道具与描边。原生工具支持模型和动作选择、动作时长、进度拖动、25%～200% 播放速度、循环开关、描边开关及宽度、暂停、恢复视角、重试、旋转／缩放／平移和沉浸模式。静态模型禁用动画相关控件。隐藏控件保持当前动画和相机；进入后台停止绘制，返回后继续原状态。资源本身没有提供音频。

## 来源

- [WIKI 模型清单](https://bluearchive.wiki/wiki/Models)、[柯伊图集](https://bluearchive.wiki/wiki/Kei/gallery)、[游戏身份字段](https://bluearchive.wiki/wiki/Kei)。
- [BlueArchiveModels](https://github.com/lihaohong6/BlueArchiveModels)，初始固定 revision 为 `525ae0faeb0a89f54ef4023f3be3692dcb529e51`。
- [WIKI 查看器文档](https://dev.miraheze.org/wiki/Template:ModelViewer)作为行为和配置参考，其实现代码未复制进 App。
- Three.js 使用 MIT，打包保留 `vendor/LICENSE` 和版本／npm 完整性记录。模型仓库初次核对时没有根 LICENSE；文件可公开访问不等于资产获得了开放许可。

设备验收和时效性观察放在 `.planning/`。目前先覆盖 Phone；Pad 与实体机器需要分别验收。
