package os.kei.ui.page.main.github.importer

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.model.StarImportApplyResult
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun GitHubStarImportPage(
    onImported: (StarImportApplyResult) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val viewModel: GitHubStarImportViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listUiState = uiState.listUiState
    val sourceRequirementMessage = stringResource(uiState.source.requirementMessageRes)
    val listUrlRequirementMessage = stringResource(StarImportUiSource.ListUrl.requirementMessageRes)
    val loadingPhaseRes = uiState.loadingPhaseRes
    val loadingPhase = when {
        uiState.readyStarListCount > 0 -> stringResource(
            R.string.github_star_import_status_lists_ready_format,
            uiState.readyStarListCount
        )

        loadingPhaseRes != null -> stringResource(loadingPhaseRes)
        else -> ""
    }

    LaunchedEffect(viewModel, context, onClose) {
        viewModel.events.collect { event ->
            when (event) {
                is GitHubStarImportEvent.Imported -> {
                    onImported(event.result)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.github_star_import_toast_imported,
                            event.result.changedCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                GitHubStarImportEvent.Close -> onClose()
            }
        }
    }

    KeiOSActivityRootBackHandler(
        needsInterception = uiState.hasPendingImportWork ||
                uiState.showExitConfirm ||
                uiState.importing
    ) {
        viewModel.requestClose()
    }

    AppPageScaffold(
        title = stringResource(R.string.github_star_import_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = false,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = viewModel::requestClose,
                backdrop = pageBackdrop
            )
        },
        actions = {
            AppLiquidIconButton(
                backdrop = pageBackdrop,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.github_star_import_cd_load),
                onClick = { viewModel.loadPreview(sourceRequirementMessage) },
                enabled = uiState.lookupConfigReady && !uiState.loading && !uiState.importing,
                width = 52.dp,
                height = 52.dp,
                variant = GlassVariant.Bar
            )
        }
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(pageBackdrop),
            sectionSpacing = 10.dp
        ) {
            item {
                StarImportSourceCard(
                    source = uiState.source,
                    tokenAvailable = uiState.apiTokenAvailable,
                    usernameInput = uiState.usernameInput,
                    listUrlInput = uiState.listUrlInput,
                    loading = uiState.loading,
                    importing = uiState.importing,
                    sourceReady = uiState.lookupConfigReady,
                    onSourceChange = viewModel::updateSource,
                    onUsernameInputChange = viewModel::updateUsernameInput,
                    onListUrlInputChange = viewModel::updateListUrlInput,
                    onLoadPreview = { viewModel.loadPreview(sourceRequirementMessage) }
                )
            }
            item {
                StarImportSourceGuideCard(
                    source = uiState.source,
                    sourceReady = uiState.lookupConfigReady
                )
            }
            if (uiState.starLists.isNotEmpty()) {
                item {
                    StarImportStarListPickerCard(
                        lists = uiState.starLists,
                        loading = uiState.loading,
                        onSelect = { list ->
                            viewModel.updateListUrlInput(list.url)
                            viewModel.loadPreview(
                                requirementMessage = listUrlRequirementMessage,
                                forcedStarListUrl = list.url
                            )
                        }
                    )
                }
            }
            item {
                StarImportStatusCard(
                    preview = uiState.preview,
                    loading = uiState.loading,
                    loadingProgress = uiState.loadingProgress,
                    loadingPhase = loadingPhase,
                    importing = uiState.importing,
                    error = uiState.error,
                    selectedCount = uiState.selectedImportableCount,
                    discoveredListCount = uiState.starLists.size
                )
            }
            if (uiState.preview != null) {
                item {
                    StarImportListControlCard(
                        filterInput = uiState.filterInput,
                        viewFilter = uiState.viewFilter,
                        qualityFilters = uiState.qualityFilters,
                        conflictStrategy = uiState.conflictStrategy,
                        qualityFilterCounts = listUiState.qualityFilterCounts,
                        filteredCount = listUiState.filteredCandidates.size,
                        visibleImportableCount = listUiState.visibleImportableIds.size,
                        visibleRecommendedCount = listUiState.visibleRecommendedIds.size,
                        visibleVerifiedApkCount = listUiState.visibleVerifiedApkIds.size,
                        selectedCount = uiState.selectedImportableCount,
                        verifiedApkCount = listUiState.verifiedApkCount,
                        checkingCount = listUiState.checkingCount,
                        verifySelectedEnabled = listUiState.selectedVerificationTargets.isNotEmpty() &&
                                !uiState.loading &&
                                !uiState.importing,
                        verifyVisibleEnabled = listUiState.visibleVerificationTargets.isNotEmpty() &&
                                !uiState.loading &&
                                !uiState.importing,
                        importEnabled = uiState.importEnabled,
                        importing = uiState.importing,
                        onFilterInputChange = viewModel::updateFilterInput,
                        onViewFilterChange = viewModel::updateViewFilter,
                        onQualityFilterToggle = viewModel::toggleQualityFilter,
                        onConflictStrategyChange = viewModel::updateConflictStrategy,
                        onVerifySelected = {
                            viewModel.verifyApkAssets(listUiState.selectedVerificationTargets)
                        },
                        onVerifyVisible = {
                            viewModel.verifyApkAssets(listUiState.visibleVerificationTargets)
                        },
                        onSelectRecommendedVisible = viewModel::selectRecommendedVisible,
                        onSelectVerifiedVisible = viewModel::selectVerifiedVisible,
                        onSelectVisible = viewModel::selectVisible,
                        onClearSelection = viewModel::clearSelection,
                        onImport = viewModel::requestImport
                    )
                }
                items(
                    items = listUiState.filteredCandidates,
                    key = { candidate -> candidate.trackedApp.id },
                    contentType = { "github_star_import_candidate" }
                ) { candidate ->
                    StarImportCandidateCard(
                        candidate = candidate,
                        selected = candidate.trackedApp.id in uiState.selectedIds,
                        trackedSelectable = uiState.conflictStrategy == StarImportConflictStrategy.IncludeTracked,
                        apkVerificationState = uiState.apkVerificationStates[candidate.trackedApp.id],
                        onToggle = { viewModel.toggleCandidate(candidate) }
                    )
                }
                if (listUiState.filteredCandidates.isEmpty()) {
                    item {
                        StarImportEmptyCard()
                    }
                }
            }
        }
    }
    GitHubStarImportConfirmDialog(
        candidates = uiState.pendingImportCandidates,
        verificationStates = uiState.apkVerificationStates,
        importing = uiState.importing,
        onDismissRequest = viewModel::dismissPendingImport,
        onConfirmImport = {
            viewModel.applyImport(
                context = context,
                selected = uiState.pendingImportCandidates
            )
        }
    )
    GitHubStarImportExitConfirmDialog(
        show = uiState.showExitConfirm,
        selectedCount = uiState.selectedImportableCount,
        onDismissRequest = viewModel::dismissExitConfirm,
        onConfirmExit = viewModel::confirmExit
    )
}
