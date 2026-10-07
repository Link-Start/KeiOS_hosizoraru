# BA 3D model index

[简体中文](ba-model-3d-index_CN.md)

The gallery opens an app-managed WebView renderer with bundled Three.js and narrow native playback controls. Models are loaded on demand; the gallery entry does not initialize WebGL. Existing GameKee Spine playback remains independently owned.

## Identity namespaces

GameKee `contentId` is a Wiki article identity, game `CharacterId` identifies the playable student/costume, and `DevName` identifies a development resource. Keep all three. A resource prefix such as `CH` or `NP` is meaningful and must not be discarded.

The shipped registry is `app/src/main/assets/ba3d/catalog.json`. Its `bindings` link confirmed GameKee articles to canonical game identities, Wiki page names, groups and exact default files. Its `groups` preserve upstream model labels, immutable repository revision, file sizes and Git blob hashes. Multiple models can belong to one character; costume articles select their corresponding default model while retaining the group's alternatives.

The reviewed registry covers 267 GameKee student/costume articles across all 144 playable-student groups in the pinned model repository. Those groups expose 286 GLB files, including optional story/prop/cut-in alternatives. Four additional skill/halo resources are explicitly associated by their body/halo nodes: sm030001 → Kikyou (Swimsuit), sm032601 → Reisa (Magical), st0004 → Momoi, st0005 → Midori. Their families expose them as alternatives through `additionalGroups`, never as a replacement default. Altogether 290 of 295 files are accessible from student entries. The remaining five resources are the generic Material Development Club NPC (NP0036) and four biker enemies; they have no corresponding student article and remain explicitly unbound. Eleven GameKee articles have no upstream model (including recent swimsuit students, Kokoro, Kotone, Erina, Anna and 䌷); the app does not borrow a different costume's model for them. These counts describe the reviewed revision, not future upstream coverage.

The readable review source is `scripts/ba/model_identity_bindings.json`: it records GameKee article names/aliases, Wiki page revision IDs, canonical game/development identities, exact defaults, exceptional correspondence notes, and every unbound group or model-less article. `ba-model-3d-coverage.csv` accounts for each repository GLB as a student default, a selectable family alternative, or an explicitly unbound resource.

Examples verified against Wiki character-page metadata and GLB nodes:

| GameKee contentId | CharacterId | DevName | Default file |
| --- | --- | --- | --- |
| 690582 | 10135 | CH0335 | Kei.glb |
| 59934 | 10004 | Hina_default | Hina.glb |
| 170295 | 10053 | CH0184 | Yuuka (Sportswear).glb |
| 162557 | 10100 | CH0263 | Shiroko＊Terror.glb |
| 72904 | 10015 | Aris_default | Arisu.glb |

GLB nodes use `Hina_Original` and `Aris_Original` for the two legacy examples. These aliases are explicit registry entries, not a global assumption that every `Original`/`default` suffix is interchangeable. `Shiroko＊Terror.glb` contains both CH0263 nodes and EN0002 texture names; texture prefixes alone cannot establish identity. Kei Prototype has NP0269 resources and is an alternate model, not another playable CH0335 identity.

Two important exceptions are explicit: Hoshino Battle Tank (10098 / CH0258_02 / article 621572) and Attacker (10099 / CH0258_01 / article 597535) share `Hoshino (Battle).glb` with explicit `defaultAnimation` 02_Formation_Idle and 01_Cafe_Reaction respectively, while Shun Swimsuit adult (10143 / CH0355_01 / article 709616) selects model `(1)` and kid (10144 / CH0355_02 / article 709617) selects `(2)`. Their identities cannot collapse to a shared base name or a stripped resource prefix. The Wiki's legacy `Hihumi_Swimsuit` spelling is preserved even though the file is named `Hifumi (Swimsuit).glb`.

## Resolution and additions

1. Normalize only development-ID representation: width/case and `CH`/`NP` separator/zero-padding. Preserve prefix and variant. Preserve form suffixes such as CH0258_01/CH0258_02 and legacy named variants such as Hihumi_Swimsuit. Numeric CharacterIds and article IDs are not development IDs.
2. Resolve confirmed article bindings, or an explicit known DevName/CharacterId if an article changed. Explicit development or character identity fields must agree; conflicting, unknown or malformed identity metadata prevents a match.
3. Names/aliases may generate review candidates during maintenance; they never choose a runtime model by fuzzy matching. Unknown articles remain without a 3D entry.
4. Add a binding only after checking the catalog article, the Wiki's CharacterId/DevName and the exact model descriptor. Verify costumes independently.
5. Preserve the immutable revision and update file byte size/Git blob identity with every resource update. Run the focused index/cache tests and validate affected defaults/alternatives in the viewer.

`scripts/ba/generate_model_catalog.py` regenerates descriptors from saved Wiki Models HTML and a complete GitHub tree at the supplied revision, using the readable identity review as `--bindings-from`. It rejects differing revisions, incomplete trees, duplicate or shadowed identities, missing defaults, resource-list disagreements and any resource group without an explicit coverage decision. The generator also writes a neighboring `catalog.coverage.csv`, accounting for every GLB. Review both outputs before replacing the bundled registry and the documented coverage table; run the offline Python generator tests, Node model-part behavior test, and focused Android index/cache tests. See the Chinese companion for a CLI example.

## Resource and rendering ownership

Bundled HTML/JS use the app-assets HTTPS origin; the WebView blocks arbitrary navigations and requests. Only known models are served by the native interceptor. The native disk cache validates byte size and Git blob SHA-1 (UTF-8 `blob <size>`, a NUL byte, then file contents) before committing downloads atomically. A raw-file SHA256 is a different digest. The cache has a 256 MiB LRU budget; failed/partial downloads are not installed as cache entries. Each viewer owns and closes its requests/streams. Models and graphics resources are released on switch/exit; the render loop stops in the background. Hiding chrome preserves animation and camera state.

The renderer follows the Wiki's unlit texture treatment, morph/skeleton animations, optional-prop baseline and outline. GLB animation `extras.show`/`extras.hide` rules update the matching tagged nodes on clip selection and restore the baseline between clips (for example Hoshino’s shield and Hifumi Swimsuit’s tank). Native Miuix tools expose model/animation selection and duration, timeline scrubbing, 25–200% playback speed, looping, outline toggle/width, pause, reset, retry, orbit/zoom/pan and immersive viewing. Static models disable animation controls. Model switches cancel the previous model's requests. No audio is supplied by these model assets.

The tools sheet also opens a native Miuix `ColorPalette` or HSV `ColorPicker`, with RGB/ARGB text entry. Automatic backgrounds use a subtle tint of the application's Miuix background and primary colors; they follow system light/dark changes when the app theme follows the system. A custom ARGB color is shared across student viewers through `KeiMmkv`, written when the palette closes or the activity stops rather than on every slider sample. Reset removes the override. Picker alpha composites over the current theme default, so the WebGL viewport and native glass capture source receive the same opaque background. Native chrome and system-bar icon appearance follow effective background contrast. Background changes update the renderer's clear color without recreating the WebView, loading the model again, or changing camera/animation state.

## Sources and licenses

- [Blue Archive Wiki models](https://bluearchive.wiki/wiki/Models), [Kei gallery](https://bluearchive.wiki/wiki/Kei/gallery), [Kei game metadata](https://bluearchive.wiki/wiki/Kei).
- [BlueArchiveModels](https://github.com/lihaohong6/BlueArchiveModels), initial pinned revision `525ae0faeb0a89f54ef4023f3be3692dcb529e51`.
- [Wiki viewer documentation](https://dev.miraheze.org/wiki/Template:ModelViewer), used as behavior/configuration reference. Its implementation is not copied into this renderer.
- Three.js is MIT; bundled `vendor/LICENSE` and `vendor/provenance.json` retain its notice, version and npm integrity. Model availability does not establish an asset redistribution license. The upstream model repository has no root LICENSE at the initial observation.

Device acceptance and dated resource observations belong in `.planning/`, not this contract. The current rollout is Phone-first; tablet and physical-device coverage require their own observations.
