package os.kei.ui.page.main.about.model

import android.content.Context
import android.os.Build
import os.kei.BuildConfig
import os.kei.R
import os.kei.core.platform.AndroidPlatformVersions
import os.kei.core.security.AdvancedProtectionCompat
import os.kei.core.shizuku.ShizukuApiUtils

internal fun buildAboutTechDetails(context: Context): AboutTechDetails {
    val projectUrl = context.getString(R.string.about_project_url)
    val dirtySuffix = if (BuildConfig.GIT_WORKTREE_DIRTY) "-dirty" else ""
    val worktreeState =
        context.getString(
            if (BuildConfig.GIT_WORKTREE_DIRTY) {
                R.string.about_value_github_worktree_dirty
            } else {
                R.string.about_value_github_worktree_clean
            },
        )
    val buildVersionText =
        context.getString(
            R.string.about_value_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    val versionSourceText =
        context.getString(
            R.string.about_value_github_version_source,
            BuildConfig.VERSION_ANCHOR_TAG,
            BuildConfig.BASE_VERSION_NAME,
            BuildConfig.NEXT_VERSION_NAME,
            BuildConfig.GIT_COMMIT_COUNT,
            BuildConfig.GIT_TOTAL_COMMIT_COUNT,
            BuildConfig.GIT_SHORT_HASH,
            dirtySuffix,
            BuildConfig.VERSION_CODE,
        )
    return AboutTechDetails(
        buildRows = buildAboutBuildRows(context),
        uiRows = buildAboutUiRows(context),
        networkRows = buildAboutNetworkRows(),
        mediaRows = buildAboutMediaRows(),
        githubProjectUrl = projectUrl,
        githubRows =
            buildAboutGitHubRows(
                context = context,
                projectUrl = projectUrl,
                worktreeState = worktreeState,
                buildVersionText = buildVersionText,
                versionSourceText = versionSourceText,
            ),
    )
}

private fun buildAboutBuildRows(context: Context): List<AboutInfoRowModel> =
    listOf(
        AboutInfoRowModel(R.string.about_row_kotlin, KotlinVersion.CURRENT.toString(), AboutInfoIcon.Config),
        AboutInfoRowModel(R.string.about_row_gradle, BuildConfig.GRADLE_VERSION, AboutInfoIcon.Config),
        AboutInfoRowModel(R.string.about_row_java, BuildConfig.JAVA_VERSION, AboutInfoIcon.Config),
        AboutInfoRowModel(R.string.about_row_jvm_target, BuildConfig.JVM_TARGET_VERSION, AboutInfoIcon.Config),
        AboutInfoRowModel(R.string.about_row_compile_sdk, BuildConfig.COMPILE_SDK_VERSION.toString(), AboutInfoIcon.Filter),
        AboutInfoRowModel(R.string.about_row_min_sdk, BuildConfig.MIN_SDK_VERSION.toString(), AboutInfoIcon.Filter),
        AboutInfoRowModel(R.string.about_row_target_sdk, BuildConfig.TARGET_SDK_VERSION.toString(), AboutInfoIcon.Filter),
        AboutInfoRowModel(R.string.about_row_runtime_api, Build.VERSION.SDK_INT.toString(), AboutInfoIcon.Filter),
        AboutInfoRowModel(
            R.string.about_row_runtime_api_full,
            AndroidPlatformVersions.sdkIntFull.toString(),
            AboutInfoIcon.Filter,
        ),
        AboutInfoRowModel(
            R.string.about_row_advanced_protection,
            when (AdvancedProtectionCompat.isEnabled(context)) {
                true -> context.getString(R.string.about_value_advanced_protection_enabled)
                false -> context.getString(R.string.about_value_advanced_protection_disabled)
                null -> context.getString(R.string.common_na)
            },
            AboutInfoIcon.Lock,
        ),
    )

private fun buildAboutUiRows(context: Context): List<AboutInfoRowModel> =
    listOf(
        AboutInfoRowModel(
            R.string.about_row_ui_framework,
            context.getString(R.string.about_value_ui_framework, BuildConfig.MIUIX_VERSION),
            AboutInfoIcon.AppWindow,
        ),
        AboutInfoRowModel(
            R.string.about_row_declarative_ui,
            context.getString(R.string.about_value_declarative_ui, BuildConfig.COMPOSE_VERSION),
            AboutInfoIcon.Layers,
        ),
        AboutInfoRowModel(
            R.string.about_row_navigation,
            context.getString(R.string.about_value_navigation, BuildConfig.NAVIGATION3_VERSION),
            AboutInfoIcon.List,
        ),
        AboutInfoRowModel(
            R.string.about_row_ui_state_holder,
            context.getString(
                R.string.about_value_ui_state_holder,
                BuildConfig.LIFECYCLE_VIEWMODEL_COMPOSE_VERSION,
            ),
            AboutInfoIcon.Notes,
        ),
        AboutInfoRowModel(
            R.string.about_row_glass_material,
            context.getString(
                R.string.about_value_glass_material,
                BuildConfig.BACKDROP_VERSION,
                BuildConfig.CAPSULE_VERSION,
                BuildConfig.SHAPES_VERSION,
            ),
            AboutInfoIcon.Media,
        ),
        AboutInfoRowModel(
            R.string.about_row_icon_set,
            context.getString(R.string.about_value_icon_set, BuildConfig.LUCIDE_ICONS_VERSION),
            AboutInfoIcon.AppWindow,
        ),
        AboutInfoRowModel(
            R.string.about_row_permission_bridge,
            context.getString(
                R.string.about_value_permission_bridge,
                BuildConfig.SHIZUKU_VERSION,
                ShizukuApiUtils.API_VERSION,
            ),
            AboutInfoIcon.Lock,
        ),
    )

private fun buildAboutNetworkRows(): List<AboutInfoRowModel> =
    listOf(
        AboutInfoRowModel(R.string.about_row_mcp_sdk, BuildConfig.MCP_KOTLIN_SDK_VERSION, AboutInfoIcon.Info),
        AboutInfoRowModel(R.string.about_row_ktor, BuildConfig.KTOR_VERSION, AboutInfoIcon.Settings),
        AboutInfoRowModel(R.string.about_row_okhttp, BuildConfig.OKHTTP_VERSION, AboutInfoIcon.Settings),
        AboutInfoRowModel(R.string.about_row_focus_api, BuildConfig.FOCUS_API_VERSION, AboutInfoIcon.Alert),
    )

private fun buildAboutMediaRows(): List<AboutInfoRowModel> =
    listOf(
        AboutInfoRowModel(R.string.about_row_media3, BuildConfig.MEDIA3_VERSION, AboutInfoIcon.Media),
        AboutInfoRowModel(R.string.about_row_zoomimage, BuildConfig.ZOOMIMAGE_VERSION, AboutInfoIcon.AppWindow),
        AboutInfoRowModel(R.string.about_row_coil3, BuildConfig.COIL3_VERSION, AboutInfoIcon.Media),
        AboutInfoRowModel(R.string.about_row_ucrop, BuildConfig.UCROP_VERSION, AboutInfoIcon.Media),
        AboutInfoRowModel(R.string.about_row_documentfile, BuildConfig.DOCUMENTFILE_VERSION, AboutInfoIcon.List),
        AboutInfoRowModel(R.string.about_row_mmkv, BuildConfig.MMKV_VERSION, AboutInfoIcon.Lock),
    )

private fun buildAboutGitHubRows(
    context: Context,
    projectUrl: String,
    worktreeState: String,
    buildVersionText: String,
    versionSourceText: String,
): List<AboutInfoRowModel> =
    listOf(
        AboutInfoRowModel(R.string.about_row_github_repo_id, parseGitHubRepoId(projectUrl), AboutInfoIcon.Layers),
        AboutInfoRowModel(R.string.about_row_github_anchor, BuildConfig.VERSION_ANCHOR_TAG, AboutInfoIcon.Version),
        AboutInfoRowModel(R.string.about_row_github_build_version, buildVersionText, AboutInfoIcon.Version),
        AboutInfoRowModel(R.string.about_row_github_branch, BuildConfig.GIT_BRANCH_NAME, AboutInfoIcon.AppWindow),
        AboutInfoRowModel(R.string.about_row_github_commit_count, BuildConfig.GIT_COMMIT_COUNT.toString(), AboutInfoIcon.List),
        AboutInfoRowModel(
            R.string.about_row_github_total_commit_count,
            BuildConfig.GIT_TOTAL_COMMIT_COUNT.toString(),
            AboutInfoIcon.List,
        ),
        AboutInfoRowModel(R.string.about_row_github_commit_hash, BuildConfig.GIT_SHORT_HASH, AboutInfoIcon.Notes),
        AboutInfoRowModel(R.string.about_row_github_worktree, worktreeState, AboutInfoIcon.Alert),
        AboutInfoRowModel(
            R.string.about_row_github_data_source,
            context.getString(R.string.about_value_github_data_source, BuildConfig.VERSION_ANCHOR_TAG),
            AboutInfoIcon.Info,
        ),
        AboutInfoRowModel(R.string.about_row_github_version_source, versionSourceText, AboutInfoIcon.Config),
        AboutInfoRowModel(
            R.string.about_row_github_strategy,
            context.getString(R.string.about_value_github_strategy),
            AboutInfoIcon.Filter,
        ),
        AboutInfoRowModel(
            R.string.about_row_github_tracking,
            context.getString(R.string.about_value_github_tracking),
            AboutInfoIcon.Layers,
        ),
        AboutInfoRowModel(
            R.string.about_row_github_notify,
            context.getString(R.string.about_value_github_notify),
            AboutInfoIcon.Alert,
        ),
        AboutInfoRowModel(
            R.string.about_row_broadcast_handler,
            context.getString(R.string.about_value_broadcast_handler),
            AboutInfoIcon.Refresh,
        ),
        AboutInfoRowModel(
            R.string.about_row_foreground_info_handler,
            context.getString(R.string.about_value_foreground_info_handler),
            AboutInfoIcon.Info,
        ),
        AboutInfoRowModel(
            R.string.about_row_background_jobs,
            context.getString(R.string.about_value_background_jobs),
            AboutInfoIcon.Config,
        ),
        AboutInfoRowModel(
            R.string.about_row_github_cache,
            context.getString(R.string.about_value_github_cache),
            AboutInfoIcon.Lock,
        ),
    )

private fun parseGitHubRepoId(projectUrl: String): String {
    val trimmed = projectUrl.trim().removeSuffix("/")
    val marker = "github.com/"
    val index = trimmed.indexOf(marker)
    if (index < 0) return trimmed
    val path = trimmed.substring(index + marker.length)
    return path.ifBlank { trimmed }
}
