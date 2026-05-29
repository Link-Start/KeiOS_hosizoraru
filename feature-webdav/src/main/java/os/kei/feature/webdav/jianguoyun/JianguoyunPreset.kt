package os.kei.feature.webdav.jianguoyun

import os.kei.feature.webdav.model.WebDavConfig

/**
 * Preset configuration for 坚果云 (Jianguoyun) WebDAV.
 *
 * Jianguoyun is the most popular personal cloud storage in China that supports WebDAV.
 * Users need to enable WebDAV in settings and generate an app-specific password.
 *
 * Reference: https://help.jianguoyun.com/?p=2064
 */
object JianguoyunPreset {
    const val SERVER_URL = "https://dav.jianguoyun.com/dav/"
    const val HELP_URL = "https://help.jianguoyun.com/?p=2064"

    fun config(
        username: String,
        appPassword: String,
        remoteDir: String = "KeiOS/",
    ): WebDavConfig = WebDavConfig(
        serverUrl = SERVER_URL,
        username = username,
        appPassword = appPassword,
        remoteDir = remoteDir,
    )
}
