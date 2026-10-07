# BA 3D model index

[简体中文](ba-model-3d-index_CN.md)

The gallery opens an app-managed WebView renderer with bundled Three.js and narrow native playback controls. Models are loaded on demand; the gallery entry does not initialize WebGL. Existing GameKee Spine playback remains independently owned.

## Identity namespaces

GameKee `contentId` is a Wiki article identity, game `CharacterId` identifies the playable student/costume, and `DevName` identifies a development resource. Keep all three. A resource prefix such as `CH` or `NP` is meaningful and must not be discarded.

The shipped registry is `app/src/main/assets/ba3d/catalog.json`. Its `bindings` link confirmed GameKee articles to canonical game identities, Wiki page names, groups and exact default files. Its `groups` preserve upstream model labels, immutable repository revision, file sizes and Git blob hashes. Multiple models can belong to one character; costume articles select their corresponding default model while retaining the group's alternatives.

The initial verified bindings cover Kei; Hina ordinary/Swimsuit/Dress; Yuuka ordinary/Sportswear/Pajama; Arisu ordinary/Maid/Battle; Shiroko ordinary/Terror. The resource manifest has 295 model descriptors, but this does not mean all GameKee articles are supported.

Examples verified against Wiki character-page metadata and GLB nodes:

| GameKee contentId | CharacterId | DevName | Default file |
| --- | --- | --- | --- |
| 690582 | 10135 | CH0335 | Kei.glb |
| 59934 | 10004 | Hina_default | Hina.glb |
| 170295 | 10053 | CH0184 | Yuuka (Sportswear).glb |
| 162557 | 10100 | CH0263 | Shiroko＊Terror.glb |
| 72904 | 10015 | Aris_default | Arisu.glb |

GLB nodes use `Hina_Original` and `Aris_Original` for the two legacy examples. These aliases are explicit registry entries, not a global assumption that every `Original`/`default` suffix is interchangeable. `Shiroko＊Terror.glb` contains both CH0263 nodes and EN0002 texture names; texture prefixes alone cannot establish identity. Kei Prototype has NP0269 resources and is an alternate model, not another playable CH0335 identity.

## Resolution and additions

1. Normalize only development-ID representation: width/case and `CH`/`NP` separator/zero-padding. Preserve prefix and variant. Numeric CharacterIds and article IDs are not development IDs.
2. Resolve confirmed article bindings, or an explicit known DevName/CharacterId if an article changed. Explicit development or character identity fields must agree; conflicting, unknown or malformed identity metadata prevents a match.
3. Names/aliases may generate review candidates during maintenance; they never choose a runtime model by fuzzy matching. Unknown articles remain without a 3D entry.
4. Add a binding only after checking the catalog article, the Wiki's CharacterId/DevName and the exact model descriptor. Verify costumes independently.
5. Preserve the immutable revision and update file byte size/Git blob identity with every resource update. Run the focused index/cache tests and validate affected defaults/alternatives in the viewer.

`scripts/ba/generate_model_catalog.py` regenerates descriptors from saved Wiki Models HTML and a complete GitHub tree at the supplied revision, preserving existing reviewed bindings. It rejects differing revisions, incomplete trees, missing defaults and resource-list disagreements. Review a generated candidate before replacing the bundled registry; see the Chinese companion for a CLI example.

## Resource and rendering ownership

Bundled HTML/JS use the app-assets HTTPS origin; the WebView blocks arbitrary navigations and requests. Only known models are served by the native interceptor. The native disk cache validates byte size and Git blob SHA-1 (UTF-8 `blob <size>`, a NUL byte, then file contents) before committing downloads atomically. A raw-file SHA256 is a different digest. The cache has a 256 MiB LRU budget; failed/partial downloads are not installed as cache entries. Each viewer owns and closes its requests/streams. Models and graphics resources are released on switch/exit; the render loop stops in the background. Hiding chrome preserves animation and camera state.

The renderer follows the Wiki's unlit texture treatment, morph/skeleton animations, optional-prop baseline and outline. Native Miuix tools expose model/animation selection and duration, timeline scrubbing, 25–200% playback speed, looping, outline toggle/width, pause, reset, retry, orbit/zoom/pan and immersive viewing. Static models disable animation controls. Model switches cancel the previous model's requests. No audio is supplied by these model assets.

## Sources and licenses

- [Blue Archive Wiki models](https://bluearchive.wiki/wiki/Models), [Kei gallery](https://bluearchive.wiki/wiki/Kei/gallery), [Kei game metadata](https://bluearchive.wiki/wiki/Kei).
- [BlueArchiveModels](https://github.com/lihaohong6/BlueArchiveModels), initial pinned revision `525ae0faeb0a89f54ef4023f3be3692dcb529e51`.
- [Wiki viewer documentation](https://dev.miraheze.org/wiki/Template:ModelViewer), used as behavior/configuration reference. Its implementation is not copied into this renderer.
- Three.js is MIT; bundled `vendor/LICENSE` and `vendor/provenance.json` retain its notice, version and npm integrity. Model availability does not establish an asset redistribution license. The upstream model repository has no root LICENSE at the initial observation.

Device acceptance and dated resource observations belong in `.planning/`, not this contract. The current rollout is Phone-first; tablet and physical-device coverage require their own observations.
