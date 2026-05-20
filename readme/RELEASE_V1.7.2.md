# KeiOS v1.7.2 Release Notes

<!-- markdownlint-disable MD013 -->

## 中文

KeiOS v1.7.2 继续收口 GitHub 追踪、托管安装、超级岛通知和设置结构。这个版本适合所有 v1.7.x 用户升级，尤其适合经常用 GitHub Releases / Actions 安装 APK 的用户。

### 重点变化

- GitHub 托管安装链路继续修复，安装确认动作、安装完成状态和超级岛 / 通知路由更稳定。
- Actions artifacts 里的 APK 现在可以走应用接管安装链路，安装与分享按钮改成更紧凑的图标按钮。
- GitHub 追踪新增单项目“检查更新”间隔，新增 / 编辑追踪时可选择跟随全局，也可单独设置 1、3、6、12 小时。
- GitHub Actions 刷新中的加载态更稳，减少 sheet 内数据还在加载时重复刷新导致的异常状态。
- 设置页把通知相关项迁移到权限板块，并把外观与效果控制拆成更清晰的分区。
- GitHub 后端拆入 feature module，core prefs / system 边界和依赖版本继续整理，发布测试覆盖同步补强。

### 安装信息

- 包名：`os.kei`
- ABI：`arm64-v8a`
- Android：Android 15+（`minSdk 35`）
- APK：`KeiOS-v1.7.2-arm64-v8a.apk`

### 升级建议

建议从 v1.7.1 直接升级。常用 GitHub 追踪、Actions artifacts、Shizuku 托管安装、超级岛通知和设置页的用户优先升级。

## English

KeiOS v1.7.2 tightens GitHub tracking, managed installs, Super Island notifications, and Settings structure. It is recommended for all v1.7.x users, especially if you often install APKs from GitHub Releases or Actions artifacts.

### Highlights

- GitHub managed install is more reliable across install confirmation actions, completion state, and Super Island / notification routing.
- Actions APK artifacts can use the app-managed install path, with compact icon buttons for install and share actions.
- GitHub tracking now supports per-project update-check intervals. Add/edit tracking can follow the global interval or use 1, 3, 6, or 12 hours for that project.
- GitHub Actions refresh loading state is steadier, reducing repeated refresh issues while the sheet is still loading.
- Settings now groups notification-related controls under permissions and separates appearance/effect controls more clearly.
- The GitHub backend moved into a feature module, core prefs/system boundaries were cleaned up, dependencies were refreshed, and release tests were updated.

### Package

- Package name: `os.kei`
- ABI: `arm64-v8a`
- Android: Android 15+ (`minSdk 35`)
- APK: `KeiOS-v1.7.2-arm64-v8a.apk`
