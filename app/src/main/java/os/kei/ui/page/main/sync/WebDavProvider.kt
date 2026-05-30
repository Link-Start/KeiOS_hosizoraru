package os.kei.ui.page.main.sync

import os.kei.feature.webdav.jianguoyun.JianguoyunPreset
import os.kei.feature.webdav.model.WebDavConfig

/**
 * Supported WebDAV providers. A provider pins the server URL (or leaves it user-editable for
 * [Custom]) and supplies a sensible default remote directory.
 */
internal enum class WebDavProvider(
    val presetServerUrl: String?,
    val defaultRemoteDir: String,
) {
    Jianguoyun(
        presetServerUrl = JianguoyunPreset.SERVER_URL,
        defaultRemoteDir = WebDavSyncStore.DEFAULT_REMOTE_DIR,
    ),
    Custom(
        presetServerUrl = null,
        defaultRemoteDir = WebDavSyncStore.DEFAULT_REMOTE_DIR,
    );

    /** True when the server URL is fixed by the provider and should not be user-edited. */
    val serverUrlLocked: Boolean get() = presetServerUrl != null
}

/**
 * Build a [WebDavConfig] from raw field values, applying the provider's preset server URL when
 * the provider locks it.
 */
internal fun WebDavProvider.buildConfig(
    serverUrl: String,
    username: String,
    appPassword: String,
    remoteDir: String,
): WebDavConfig =
    WebDavConfig(
        serverUrl = (presetServerUrl ?: serverUrl).trim(),
        username = username.trim(),
        appPassword = appPassword.trim(),
        remoteDir = remoteDir.trim().ifBlank { defaultRemoteDir },
    )
