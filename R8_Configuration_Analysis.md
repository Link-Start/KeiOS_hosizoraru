# R8 Configuration Analysis

## Build Configuration

- Android Gradle Plugin: `9.2.1`
- Release optimization: `optimization.enable = true`
- R8 Gradle property: `android.r8.gradual.support=true`
- Release build result: `./gradlew :app:assembleRelease` passed
- Unit test result: `./gradlew :app:testDebugUnitTest` passed
- Feature unit test result: `./gradlew :feature-github:testDebugUnitTest` passed
- Release mapping outputs present: `configuration.txt`, `mapping.txt`, `seeds.txt`, `usage.txt`, `resources.txt`
- Missing-rule output: no release missing-rule file was produced

The app uses the Compose compiler plugin and has no app-level `androidx.compose.**` keep rules. Compose and Kotlin serialization rules are supplied by dependency/plugin consumer rules in the merged release configuration.

## Applied Rule Changes

| Area | Current Rule | Finding | Recommended Action |
| --- | --- | --- | --- |
| GitHub managed install binder bridge | Removed `-keep public interface ** extends android.os.IInterface { *; }` | The code reflects platform hidden interfaces by exact names in `ShizukuPackageInstallerBridge`; the release build succeeds with only the `ShizukuBinderWrapper` constructor and `queryLocalInterface` kept. | Keep the narrowed bridge rule. |
| Shizuku provider | Removed local `moe.shizuku.api.BinderContainer` and `rikka.shizuku.ShizukuProvider` rules | `dev.rikka.shizuku:provider:13.1.5` contributes the needed `BinderContainer` rules in the merged release configuration. | Rely on dependency consumer rules. |
| GitHub install package readability | Replaced `-keepnames class os.kei.feature.github.install.**` with five class-level keepnames rules | GitHub install seeds dropped from 75 to 10 after the enum rule was narrowed. The remaining install seeds are the five diagnostic entry classes and required constructors/companions. | Keep receiver, commit registry, confirm registry, installer, and bridge names readable. |
| Enum member names | Replaced `-keepclassmembernames enum os.kei.** { *; }` with explicit enum keep rules | Enum member seeds dropped from 402 to 155. The retained set covers persisted/wire-name enums for app theme, visible pages/cards, OS cards, GitHub profile/star-import/share-import state, BA catalog cache, BGM queue mode, and MCP execution profile. | Keep the explicit enum list and add future enum-name persistence to this list deliberately. |

## Remaining Suggested Rule Changes

| Area | Current Rule | Finding | Recommended Action |
| --- | --- | --- | --- |
| Navigation route keys | `-keep class os.kei.ui.navigation.KeiosRoute { *; }` and nested route rules | Route surface is small: six route types and 109 release seeds. The rule protects Navigation 3 save/restore and serializers, but keeps all route members. | Keep for safety during the current perf phase. Later narrow to route names, object instances, companions, serializers, and serializer classes. |

## Rules To Keep

- Manifest component `keepnames` rules are low risk and preserve names for About/runtime introspection.
- `rikka.shizuku.Shizuku` member rules match `ShizukuApiUtils` reflective static method access.
- Focus notification rules match the HyperOS payload contract, serializers, declared fields, and template models.
- `android.util.Log` side-effect stripping for `v/d/i` is release-only and keeps warning/error paths intact.
- Ktor JDK management `dontwarn` entries are harmless and scoped to Android-incompatible debug probe references.

## Validation Required After Rule Edits

- `./gradlew :app:assembleRelease`
- `./gradlew :app:testDebugUnitTest`
- Release install smoke test for main navigation restore, GitHub Shizuku managed install, share import install result receiver, Focus/Super Island notification payloads, and OS/GitHub settings persistence.
- Compare `app/build/outputs/mapping/release/seeds.txt` before and after edits, focusing on `os.kei.feature.github.install`, `os.kei.ui.navigation.KeiosRoute`, and enum seeds.
