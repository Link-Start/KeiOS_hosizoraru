package os.kei.ui.page.main.about.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.about.model.AboutComponentEntry
import os.kei.ui.page.main.about.model.AboutPermissionEntry
import java.util.Locale

internal enum class AboutSearchCard {
    App,
    GitHub,
    Runtime,
    Network,
    Media,
    Permission,
    Component,
    Build,
    Ui,
    ProjectLicense,
    License,
    Lab
}

internal data class AboutSearchTarget(
    val card: AboutSearchCard,
    val category: AboutCategory,
    private val tokens: List<String>
) {
    fun matches(query: String): Boolean {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) return true
        return tokens.any { token ->
            token.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }
}

@Composable
internal fun rememberAboutSearchTargets(
    appLabel: String,
    shizukuStatus: String,
    permissionEntries: List<AboutPermissionEntry>,
    componentEntries: List<AboutComponentEntry>,
): List<AboutSearchTarget> {
    return listOf(
        AboutSearchTarget(
            card = AboutSearchCard.App,
            category = AboutCategory.Overview,
            tokens = aboutTokens(
                stringResource(R.string.about_card_app_title),
                stringResource(R.string.about_card_app_subtitle),
                appLabel,
                stringResource(R.string.about_label_name),
                stringResource(R.string.about_label_package_name),
                stringResource(R.string.about_label_version),
                stringResource(R.string.about_label_build_type),
                stringResource(R.string.about_label_build_time),
                stringResource(R.string.about_label_last_update),
                stringResource(R.string.about_label_debug),
                stringResource(R.string.about_label_test_only),
                stringResource(R.string.about_label_api_level),
                stringResource(R.string.about_label_security_patch),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.GitHub,
            category = AboutCategory.Overview,
            tokens = aboutTokens(
                stringResource(R.string.about_card_github_title),
                stringResource(R.string.about_card_github_subtitle),
                stringResource(R.string.about_label_project_url),
                stringResource(R.string.about_row_github_repo_id),
                stringResource(R.string.about_row_github_anchor),
                stringResource(R.string.about_row_github_build_version),
                stringResource(R.string.about_row_github_branch),
                stringResource(R.string.about_row_github_commit_count),
                stringResource(R.string.about_row_github_total_commit_count),
                stringResource(R.string.about_row_github_commit_hash),
                stringResource(R.string.about_row_github_worktree),
                stringResource(R.string.about_row_github_data_source),
                stringResource(R.string.about_row_github_version_source),
                stringResource(R.string.about_row_github_strategy),
                stringResource(R.string.about_row_github_tracking),
                stringResource(R.string.about_row_github_notify),
                stringResource(R.string.about_row_broadcast_handler),
                stringResource(R.string.about_row_foreground_info_handler),
                stringResource(R.string.about_row_background_jobs),
                stringResource(R.string.about_row_github_cache),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Runtime,
            category = AboutCategory.System,
            tokens = aboutTokens(
                stringResource(R.string.about_card_runtime_title),
                stringResource(R.string.about_card_runtime_subtitle),
                shizukuStatus,
                stringResource(R.string.about_runtime_label_notification_permission),
                stringResource(R.string.about_runtime_label_selinux),
                stringResource(R.string.about_runtime_label_uname),
                stringResource(R.string.about_runtime_label_permission_count),
                stringResource(R.string.about_runtime_label_component_count),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Network,
            category = AboutCategory.System,
            tokens = aboutTokens(
                stringResource(R.string.about_card_network_title),
                stringResource(R.string.about_card_network_subtitle),
                stringResource(R.string.about_row_mcp_sdk),
                stringResource(R.string.about_row_ktor),
                stringResource(R.string.about_row_okhttp),
                stringResource(R.string.about_row_focus_api),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Media,
            category = AboutCategory.System,
            tokens = aboutTokens(
                stringResource(R.string.about_card_media_title),
                stringResource(R.string.about_card_media_subtitle),
                stringResource(R.string.about_row_media3),
                stringResource(R.string.about_row_zoomimage),
                stringResource(R.string.about_row_coil3),
                stringResource(R.string.about_row_ucrop),
                stringResource(R.string.about_row_documentfile),
                stringResource(R.string.about_row_mmkv),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Permission,
            category = AboutCategory.System,
            tokens = aboutTokens(
                stringResource(R.string.about_card_permission_title),
                stringResource(R.string.about_card_permission_subtitle),
                stringResource(R.string.about_label_status),
                stringResource(R.string.about_permission_empty),
                stringResource(R.string.about_permission_label_permission),
                stringResource(R.string.about_permission_label_granted),
                stringResource(R.string.about_permission_label_system_name),
                stringResource(R.string.about_permission_label_purpose),
                stringResource(R.string.about_permission_label_used_in),
                *permissionEntries.flatMap { entry ->
                    listOf(entry.title, entry.name, entry.purpose, entry.usedIn)
                }.toTypedArray(),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Component,
            category = AboutCategory.System,
            tokens = aboutTokens(
                stringResource(R.string.about_card_component_title),
                stringResource(R.string.about_card_component_subtitle),
                stringResource(R.string.about_label_status),
                stringResource(R.string.about_component_empty),
                stringResource(R.string.about_component_label_export_state),
                stringResource(R.string.about_permission_label_purpose),
                stringResource(R.string.about_permission_label_used_in),
                stringResource(R.string.about_component_type_service),
                stringResource(R.string.about_component_type_receiver),
                stringResource(R.string.about_component_type_provider),
                *componentEntries.flatMap { entry ->
                    buildList {
                        add(entry.name)
                        add(entry.purpose)
                        add(entry.usedIn)
                        entry.extra.forEach { extra ->
                            add(stringResource(extra.labelRes))
                            add(extra.value)
                        }
                    }
                }.toTypedArray(),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Build,
            category = AboutCategory.Tech,
            tokens = aboutTokens(
                stringResource(R.string.about_card_build_title),
                stringResource(R.string.about_card_build_subtitle),
                stringResource(R.string.about_row_kotlin),
                stringResource(R.string.about_row_gradle),
                stringResource(R.string.about_row_java),
                stringResource(R.string.about_row_jvm_target),
                stringResource(R.string.about_row_compile_sdk),
                stringResource(R.string.about_row_min_sdk),
                stringResource(R.string.about_row_target_sdk),
                stringResource(R.string.about_row_runtime_api),
                stringResource(R.string.about_row_runtime_api_full),
                stringResource(R.string.about_row_advanced_protection),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Ui,
            category = AboutCategory.Tech,
            tokens = aboutTokens(
                stringResource(R.string.about_card_ui_title),
                stringResource(R.string.about_card_ui_subtitle),
                stringResource(R.string.about_row_ui_framework),
                stringResource(R.string.about_row_declarative_ui),
                stringResource(R.string.about_row_navigation),
                stringResource(R.string.about_row_ui_state_holder),
                stringResource(R.string.about_row_glass_material),
                stringResource(R.string.about_row_icon_set),
                stringResource(R.string.about_row_permission_bridge),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.ProjectLicense,
            category = AboutCategory.Tech,
            tokens = aboutTokens(
                stringResource(R.string.about_card_project_license_title),
                stringResource(R.string.about_card_project_license_subtitle),
                stringResource(R.string.about_project_license_row_name),
                stringResource(R.string.about_project_license_row_spdx),
                stringResource(R.string.about_project_license_row_file),
                stringResource(R.string.about_project_license_row_copyright),
                stringResource(R.string.about_project_license_row_url),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.License,
            category = AboutCategory.Tech,
            tokens = aboutTokens(
                stringResource(R.string.about_card_license_title),
                stringResource(R.string.about_card_license_subtitle),
                stringResource(R.string.about_license_row_scope),
                stringResource(R.string.about_license_row_mix),
                stringResource(R.string.about_license_row_compliance),
                stringResource(R.string.about_license_row_miuix),
                stringResource(R.string.about_license_row_androidx_runtime),
                stringResource(R.string.about_license_row_material_components),
                stringResource(R.string.about_license_row_androidx_stack),
                stringResource(R.string.about_license_row_lucide),
                stringResource(R.string.about_license_row_blue_archive_logos),
                stringResource(R.string.about_license_row_backdrop),
                stringResource(R.string.about_license_row_capsule),
                stringResource(R.string.about_license_row_shapes),
                stringResource(R.string.about_license_row_shizuku),
                stringResource(R.string.about_license_row_mmkv),
                stringResource(R.string.about_license_row_mcp),
                stringResource(R.string.about_license_row_network_stack),
                stringResource(R.string.about_license_row_media_stack),
            ),
        ),
        AboutSearchTarget(
            card = AboutSearchCard.Lab,
            category = AboutCategory.Lab,
            tokens = aboutTokens(
                stringResource(R.string.about_card_component_lab_title),
                stringResource(R.string.about_card_component_lab_subtitle),
                stringResource(R.string.about_component_lab_row_entry),
                stringResource(R.string.about_component_lab_row_scope),
                stringResource(R.string.about_component_lab_row_activity),
                stringResource(R.string.about_component_lab_row_components),
                stringResource(R.string.debug_component_lab_title),
                stringResource(R.string.debug_component_lab_liquid_catalog_title),
                stringResource(R.string.debug_component_lab_liquid_row_components_value),
            ),
        ),
    )
}

private fun aboutTokens(vararg values: String): List<String> {
    return values.filter { it.isNotBlank() }
}
