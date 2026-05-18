package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubAppCandidateRow
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.appWindowHeightDp
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidCheckbox
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackAppPickerControls(
    backdrop: LayerBackdrop,
    includeUserApps: Boolean,
    includeSystemApps: Boolean,
    includeTrackedApps: Boolean,
    sortMode: GitHubTrackAppPickerSortMode,
    sortDirection: GitHubTrackAppPickerSortDirection,
    onIncludeUserAppsChange: (Boolean) -> Unit,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onIncludeTrackedAppsChange: (Boolean) -> Unit,
    onSortModeChange: (GitHubTrackAppPickerSortMode) -> Unit,
    onSortDirectionChange: (GitHubTrackAppPickerSortDirection) -> Unit
) {
    var sortModeExpanded by remember { mutableStateOf(false) }
    var sortDirectionExpanded by remember { mutableStateOf(false) }
    var sortModeAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var sortDirectionAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val sortModes = GitHubTrackAppPickerSortMode.entries
    val sortDirections = GitHubTrackAppPickerSortDirection.entries
    val sortOptions = sortModes.map { mode -> stringResource(mode.labelRes) }
    val directionOptions = sortDirections.map { direction -> stringResource(direction.labelRes) }
    val sortIndex = sortModes.indexOf(sortMode).coerceAtLeast(0)
    val directionIndex = sortDirections.indexOf(sortDirection).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GitHubTrackAppPickerButtonRow(
            label = stringResource(R.string.github_track_sheet_app_filter_scope_label)
        ) {
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_user_apps),
                checked = includeUserApps,
                onCheckedChange = onIncludeUserAppsChange,
                modifier = Modifier.weight(1f)
            )
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_system_apps),
                checked = includeSystemApps,
                onCheckedChange = onIncludeSystemAppsChange,
                modifier = Modifier.weight(1f)
            )
            GitHubTrackAppTypeCheckbox(
                backdrop = backdrop,
                text = stringResource(R.string.github_track_sheet_app_filter_tracked_apps),
                checked = includeTrackedApps,
                onCheckedChange = onIncludeTrackedAppsChange,
                modifier = Modifier.weight(1f)
            )
        }
        GitHubTrackAppPickerSortRow(
            label = stringResource(R.string.github_track_sheet_app_sort_label)
        ) {
            AppDropdownSelector(
                selectedText = stringResource(
                    R.string.github_track_sheet_app_sort_dropdown_format,
                    sortOptions.getOrElse(sortIndex) { "" }
                ),
                options = sortOptions,
                selectedIndex = sortIndex,
                expanded = sortModeExpanded,
                anchorBounds = sortModeAnchorBounds,
                onExpandedChange = { sortModeExpanded = it },
                onSelectedIndexChange = { index ->
                    sortModes.getOrNull(index)?.let { mode ->
                        onSortModeChange(mode)
                        if (mode.isTimeSort()) {
                            onSortDirectionChange(GitHubTrackAppPickerSortDirection.Descending)
                        }
                    }
                },
                onAnchorBoundsChange = { sortModeAnchorBounds = it },
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                variant = GlassVariant.Content,
                minHeight = 32.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp
            )
            AppDropdownSelector(
                selectedText = stringResource(
                    R.string.github_track_sheet_app_sort_direction_dropdown_format,
                    directionOptions.getOrElse(directionIndex) { "" }
                ),
                options = directionOptions,
                selectedIndex = directionIndex,
                expanded = sortDirectionExpanded,
                anchorBounds = sortDirectionAnchorBounds,
                onExpandedChange = { sortDirectionExpanded = it },
                onSelectedIndexChange = { index ->
                    sortDirections.getOrNull(index)?.let(onSortDirectionChange)
                },
                onAnchorBoundsChange = { sortDirectionAnchorBounds = it },
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                variant = GlassVariant.Content,
                minHeight = 32.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp
            )
        }
    }
}

@Composable
internal fun GitHubTrackAppPickerSortRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetInputTitle(label)
        content()
    }
}

@Composable
internal fun GitHubTrackAppTypeCheckbox(
    backdrop: LayerBackdrop,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLiquidCheckbox(
            checked = checked,
            onCheckedChange = null,
            backdrop = backdrop
        )
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun GitHubTrackAppPickerButtonRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetInputTitle(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun GitHubTrackAppPickerContent(
    backdrop: LayerBackdrop,
    appSearch: String,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    trackedPackageNames: Set<String>,
    editingPackageName: String,
    appListRefreshing: Boolean,
    rememberAddPickerScroll: Boolean,
    rememberedFirstVisibleItemIndex: Int,
    rememberedFirstVisibleItemScrollOffset: Int,
    onAppSearchChange: (String) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onRefreshAppList: () -> Unit,
    onAddAppPickerScrollPositionChange: (Int, Int) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit
) {
    val listMaxHeight = (appWindowHeightDp() * 0.60f).coerceIn(340.dp, 680.dp)
    val savedPreferences = remember { GitHubTrackStore.loadAppPickerPreferences() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var includeUserApps by remember {
        mutableStateOf(savedPreferences.includeUserApps)
    }
    var includeSystemApps by remember {
        mutableStateOf(savedPreferences.includeSystemApps)
    }
    var includeTrackedApps by remember {
        mutableStateOf(savedPreferences.includeTrackedApps)
    }
    var sortMode by remember {
        mutableStateOf(GitHubTrackAppPickerSortMode.fromStorageId(savedPreferences.sortModeId))
    }
    var sortDirection by remember {
        mutableStateOf(
            GitHubTrackAppPickerSortDirection.fromStorageId(savedPreferences.sortDirectionId)
        )
    }
    var initialAppFocusApplied by remember(selectedApp?.packageName) {
        mutableStateOf(false)
    }
    val showInstallSourcePill = sortMode.showsInstallSourcePill()
    val filteredApps =
        remember(
            appList,
            appSearch,
            includeUserApps,
            includeSystemApps,
            includeTrackedApps,
            trackedPackageNames,
            selectedApp,
            editingPackageName,
            sortMode,
            sortDirection
        ) {
            filterAndSortGitHubTrackAppCandidates(
                apps = appList,
                query = appSearch,
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                trackedPackageNames = trackedPackageNames,
                pinnedPackageNames = setOf(
                    selectedApp?.packageName.orEmpty(),
                    editingPackageName
                ),
                sortMode = sortMode,
                sortDirection = sortDirection
            )
        }
    fun saveAppPickerPreferences() {
        GitHubTrackStore.saveAppPickerPreferences(
            GitHubAppPickerPreferences(
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                sortModeId = sortMode.storageId,
                sortDirectionId = sortDirection.storageId
            )
        )
    }

    fun scrollAppListToTop() {
        coroutineScope.launch {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(
        filteredApps,
        selectedApp?.packageName,
        rememberAddPickerScroll,
        rememberedFirstVisibleItemIndex,
        rememberedFirstVisibleItemScrollOffset,
        initialAppFocusApplied
    ) {
        if (initialAppFocusApplied || filteredApps.isEmpty()) return@LaunchedEffect
        if (rememberAddPickerScroll && selectedApp == null) {
            listState.scrollToItem(
                rememberedFirstVisibleItemIndex.coerceIn(filteredApps.indices),
                rememberedFirstVisibleItemScrollOffset.coerceAtLeast(0)
            )
        } else {
            listState.scrollToItem(
                gitHubTrackAppCandidateInitialScrollIndex(
                    candidates = filteredApps,
                    selectedPackageName = selectedApp?.packageName
                )
            )
        }
        initialAppFocusApplied = true
    }

    LaunchedEffect(rememberAddPickerScroll, listState) {
        if (!rememberAddPickerScroll) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onAddAppPickerScrollPositionChange(index, offset)
            }
    }

    SheetContentColumn(
        scrollable = false,
        verticalSpacing = 10.dp
    ) {
        SheetSectionTitle(stringResource(R.string.github_track_sheet_section_app_candidates))
        SheetSectionCard(verticalSpacing = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetInputTitle(
                    text = stringResource(R.string.github_track_sheet_input_app_filter_title),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = if (appListRefreshing) {
                            stringResource(R.string.common_loading)
                        } else {
                            stringResource(R.string.common_refresh)
                        },
                        leadingIcon = appLucideRefreshIcon(),
                        enabled = !appListRefreshing,
                        onClick = onRefreshAppList,
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis
                    )
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.github_track_sheet_btn_collapse),
                        onClick = { onPickerExpandedChange(false) },
                        minHeight = 30.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 4.dp,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis
                    )
                }
            }
            AppLiquidSearchField(
                value = appSearch,
                onValueChange = { value ->
                    onAppSearchChange(value)
                    scrollAppListToTop()
                },
                label = stringResource(R.string.github_track_sheet_input_app_filter),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true
            )
            GitHubTrackAppPickerControls(
                backdrop = backdrop,
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                sortMode = sortMode,
                sortDirection = sortDirection,
                onIncludeUserAppsChange = {
                    includeUserApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onIncludeSystemAppsChange = {
                    includeSystemApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onIncludeTrackedAppsChange = {
                    includeTrackedApps = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onSortModeChange = {
                    sortMode = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                },
                onSortDirectionChange = {
                    sortDirection = it
                    saveAppPickerPreferences()
                    scrollAppListToTop()
                }
            )
            MiuixInfoItem(
                stringResource(R.string.github_track_sheet_label_app_list),
                stringResource(
                    R.string.github_track_sheet_app_result_count_format,
                    filteredApps.size,
                    appList.size
                )
            )
            selectedApp?.let { app ->
                GitHubSelectedAppCard(
                    selectedApp = app,
                    showInstallSource = showInstallSourcePill
                )
            }
            if (filteredApps.isEmpty()) {
                MiuixInfoItem(
                    stringResource(R.string.github_track_sheet_label_app_list),
                    stringResource(R.string.github_track_sheet_msg_app_no_match)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "github_app_candidate" }
                    ) { app ->
                        GitHubAppCandidateRow(
                            app = app,
                            selected = selectedApp?.packageName == app.packageName,
                            showInstallSource = showInstallSourcePill,
                            onClick = {
                                onSelectedAppChange(app)
                                onPickerExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}
