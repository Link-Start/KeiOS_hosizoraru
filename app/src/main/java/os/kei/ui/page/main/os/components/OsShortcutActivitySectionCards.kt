@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.osLucideEnterIcon
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityIcon
import os.kei.ui.page.main.os.shortcut.normalizeShortcutIntentExtras
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard

internal fun LazyListScope.addShortcutActivityCards(
    cards: List<OsActivityShortcutCard>,
    contentBackdrop: LayerBackdrop,
    defaultCardTitle: String,
    expandedStates: Map<String, Boolean>,
    onExpandedChange: (String, Boolean) -> Unit,
    onOpenActivity: (OsActivityShortcutCard) -> Unit,
    onHeaderLongClick: (OsActivityShortcutCard) -> Unit,
) {
    cards.forEach { card ->
        val shortcutConfig = card.config
        item(key = "os-activity-${card.id}", contentType = "os_shortcut_activity_card") {
            AppLiquidAccordionCard(
                backdrop = contentBackdrop,
                title = shortcutConfig.title.ifBlank { defaultCardTitle },
                subtitle = shortcutConfig.subtitle,
                expanded = expandedStates[card.id] == true,
                onExpandedChange = { expanded -> onExpandedChange(card.id, expanded) },
                headerStartAction = {
                    ShortcutActivityIcon(
                        packageName = shortcutConfig.packageName,
                        className = shortcutConfig.className,
                        size = 24.dp,
                        fallbackToPackageIcon = true,
                    )
                },
                headerActions = {
                    AppCompactIconAction(
                        icon = osLucideEnterIcon(),
                        contentDescription = stringResource(R.string.os_google_system_service_cd_open_activity),
                        onClick = { onOpenActivity(card) },
                    )
                },
                onHeaderLongClick = { onHeaderLongClick(card) },
            ) {
                val emptyValueText = stringResource(R.string.os_google_system_service_value_data_empty)
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_app_name),
                    value = shortcutConfig.appName,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_package_name),
                    value = shortcutConfig.packageName,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_class_name),
                    value = shortcutConfig.className,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_intent_action),
                    value = shortcutConfig.intentAction,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_intent_category),
                    value = shortcutConfig.intentCategory.ifBlank { emptyValueText },
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_intent_flags),
                    value = shortcutConfig.intentFlags.ifBlank { emptyValueText },
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_intent_data),
                    value = shortcutConfig.intentUriData.ifBlank { emptyValueText },
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_google_system_service_label_intent_mime_type),
                    value = shortcutConfig.intentMimeType.ifBlank { emptyValueText },
                )
                val normalizedExtras = normalizeShortcutIntentExtras(shortcutConfig.intentExtras)
                if (normalizedExtras.isEmpty()) {
                    OsSectionInfoRow(
                        label = stringResource(R.string.os_google_system_service_label_intent_extras),
                        value = emptyValueText,
                    )
                } else {
                    normalizedExtras.forEachIndexed { index, extra ->
                        val typeLabel = stringResource(extra.type.labelResId)
                        OsSectionInfoRow(
                            label =
                                stringResource(
                                    R.string.os_google_system_service_label_intent_extra_indexed,
                                    index + 1,
                                ),
                            value = "[$typeLabel] ${extra.key} = ${extra.value.ifBlank { emptyValueText }}",
                        )
                    }
                }
            }
        }
        item(key = "os-activity-space-${card.id}", contentType = "os_section_space") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
