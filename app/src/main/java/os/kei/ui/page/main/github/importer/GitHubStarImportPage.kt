package os.kei.ui.page.main.github.importer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun GitHubStarImportPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val trackSnapshot = remember { GitHubTrackStore.loadSnapshot() }
    val lookupConfig = trackSnapshot.lookupConfig
    val refreshIntervalHours = trackSnapshot.refreshIntervalHours
    val savedDraft = remember { GitHubStarImportDraftStore.load() }
    var source by remember { mutableStateOf(savedDraft.source) }
    var usernameInput by remember { mutableStateOf(savedDraft.usernameInput) }
    var listUrlInput by remember { mutableStateOf(savedDraft.listUrlInput) }
    var filterInput by remember { mutableStateOf(savedDraft.filterInput) }
    var viewFilter by remember { mutableStateOf(savedDraft.viewFilter) }
    var qualityFilters by remember { mutableStateOf(savedDraft.qualityFilters) }
    var conflictStrategy by remember { mutableStateOf(savedDraft.conflictStrategy) }
    var preview by remember { mutableStateOf<GitHubStarredRepositoryImportPreview?>(null) }
    var selectedIds by remember { mutableStateOf(savedDraft.selectedIds) }
    val apkVerificationStates =
        remember { mutableStateMapOf<String, StarImportApkVerificationUiState>() }
    var pendingImportCandidates by remember {
        mutableStateOf<List<GitHubRepositoryImportCandidate>>(
            emptyList()
        )
    }
    var showExitConfirm by remember { mutableStateOf(false) }
    var starLists by remember { mutableStateOf<List<GitHubStarListSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var loadingPhase by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val sourceReady = source.isReady(
        token = lookupConfig.apiToken,
        username = usernameInput,
        listUrl = listUrlInput
    )
    val candidates = remember(preview) { preview?.candidates.orEmpty() }
    val listUiState by remember(
        candidates,
        filterInput,
        viewFilter,
        qualityFilters,
        conflictStrategy,
        selectedIds
    ) {
        derivedStateOf {
            buildStarImportCandidateListUiState(
                candidates = candidates,
                filterInput = filterInput,
                viewFilter = viewFilter,
                qualityFilters = qualityFilters,
                conflictStrategy = conflictStrategy,
                selectedIds = selectedIds,
                verificationStates = apkVerificationStates
            )
        }
    }
    val selectedImportableCount = listUiState.selectedImportableCount
    val importEnabled = selectedImportableCount > 0 && !loading && !importing
    val hasPendingImportWork = selectedImportableCount > 0 || pendingImportCandidates.isNotEmpty()

    LaunchedEffect(
        source,
        usernameInput,
        listUrlInput,
        filterInput,
        viewFilter,
        qualityFilters,
        conflictStrategy,
        selectedIds
    ) {
        GitHubStarImportDraftStore.save(
            GitHubStarImportDraft(
                source = source,
                usernameInput = usernameInput,
                listUrlInput = listUrlInput,
                filterInput = filterInput,
                viewFilter = viewFilter,
                qualityFilters = qualityFilters,
                conflictStrategy = conflictStrategy,
                selectedIds = selectedIds
            )
        )
    }

    fun requestClose() {
        if (importing) return
        if (hasPendingImportWork) {
            showExitConfirm = true
        } else {
            onClose()
        }
    }

    fun loadPreview(forcedStarListUrl: String? = null) {
        if (loading || importing) return
        val targetSource = if (forcedStarListUrl != null) StarImportUiSource.ListUrl else source
        val targetListUrl = forcedStarListUrl ?: listUrlInput.trim()
        val targetReady = targetSource.isReady(
            token = lookupConfig.apiToken,
            username = usernameInput,
            listUrl = targetListUrl
        )
        if (!targetReady) {
            error = context.getString(targetSource.requirementMessageRes)
            return
        }
        loading = true
        loadingProgress = 0.08f
        loadingPhase = context.getString(R.string.github_star_import_loading_source)
        error = null
        if (forcedStarListUrl == null) {
            starLists = emptyList()
        }
        val targetUsername = usernameInput.toGitHubUsernameInput()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val repository =
                        GitHubRepositoryDiscoveryRepository(apiToken = lookupConfig.apiToken)
                    if (
                        targetSource == StarImportUiSource.ListUrl &&
                        forcedStarListUrl == null &&
                        targetListUrl.isGitHubStarsOverviewInput()
                    ) {
                        withContext(Dispatchers.Main) {
                            loadingProgress = 0.34f
                            loadingPhase =
                                context.getString(R.string.github_star_import_loading_lists)
                        }
                        val lists = repository.fetchStarLists(targetListUrl).getOrThrow()
                        if (lists.isNotEmpty()) {
                            return@runCatching StarImportLoadResult.Lists(lists)
                        }
                    }
                    val resolvedListUrl = if (
                        targetSource == StarImportUiSource.ListUrl &&
                        forcedStarListUrl == null &&
                        targetListUrl.isGitHubStarsOverviewInput()
                    ) {
                        targetListUrl.toGitHubStarsRepositoryUrlInput()
                    } else {
                        targetListUrl
                    }
                    withContext(Dispatchers.Main) {
                        loadingProgress = 0.64f
                        loadingPhase =
                            context.getString(R.string.github_star_import_loading_repositories)
                    }
                    val request = GitHubStarredRepositoryImportRequest(
                        source = targetSource.toRequestSource(),
                        username = targetUsername,
                        starListUrl = resolvedListUrl,
                        apiToken = lookupConfig.apiToken,
                        limit = 1_000
                    )
                    val preview =
                        GitHubRepositoryDiscoveryService(repository).previewStarredRepositoryImport(
                        request = request,
                        existingItems = GitHubTrackStore.load()
                    ).getOrThrow()
                    withContext(Dispatchers.Main) {
                        loadingProgress = 0.88f
                        loadingPhase =
                            context.getString(R.string.github_star_import_loading_preview)
                    }
                    StarImportLoadResult.Preview(preview)
                }
            }
            loading = false
            loadingProgress = 1f
            result.onSuccess { loadResult ->
                when (loadResult) {
                    is StarImportLoadResult.Lists -> {
                        preview = null
                        selectedIds = emptySet()
                        starLists = loadResult.items
                        loadingPhase = context.getString(
                            R.string.github_star_import_status_lists_ready_format,
                            loadResult.items.size
                        )
                    }

                    is StarImportLoadResult.Preview -> {
                        val nextPreview = loadResult.preview
                        preview = nextPreview
                        starLists = emptyList()
                        apkVerificationStates.clear()
                        val importableIds = nextPreview.candidates
                            .map { it.trackedApp.id }
                            .toSet()
                        val restoredSelection = selectedIds.intersect(importableIds)
                        selectedIds = restoredSelection.ifEmpty {
                            nextPreview.candidates
                            .filter { GitHubStarImportClassifier.isDefaultSelected(it) }
                            .map { it.trackedApp.id }
                            .toSet()
                        }
                    }
                }
            }.onFailure { throwable ->
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName }
                loadingPhase = ""
            }
        }
    }

    fun verifyApkAssets(targets: List<GitHubRepositoryImportCandidate>) {
        if (loading || importing || targets.isEmpty()) return
        val uniqueTargets = targets
            .distinctBy { it.trackedApp.id }
            .take(MAX_APK_VERIFICATION_BATCH)
        uniqueTargets.forEach { candidate ->
            apkVerificationStates[candidate.trackedApp.id] = StarImportApkVerificationUiState(
                checking = true,
                verification = apkVerificationStates[candidate.trackedApp.id]?.verification
            )
        }
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                val verifier = GitHubStarImportApkVerifier(
                    source = GitHubApkPackageNameScanRepository(),
                    cache = GitHubStarImportApkVerificationCacheStore
                )
                val semaphore = Semaphore(MAX_PARALLEL_APK_VERIFICATIONS)
                coroutineScope {
                    uniqueTargets.map { candidate ->
                        async {
                            semaphore.withPermit {
                                candidate.trackedApp.id to verifier.verify(
                                    candidate = candidate,
                                    lookupConfig = lookupConfig,
                                    refreshIntervalHours = refreshIntervalHours
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
            results.forEach { (id, verification) ->
                apkVerificationStates[id] = StarImportApkVerificationUiState(
                    checking = false,
                    verification = verification
                )
            }
        }
    }

    fun requestImport() {
        if (listUiState.selectedCandidates.isEmpty() || importing) return
        pendingImportCandidates = listUiState.selectedCandidates
    }

    fun applyImport(selected: List<GitHubRepositoryImportCandidate>) {
        if (selected.isEmpty() || importing) return
        importing = true
        pendingImportCandidates = emptyList()
        scope.launch {
            val selectedForImport = applyVerifiedPackageNamesToStarImportCandidates(
                candidates = selected,
                verificationStates = apkVerificationStates
            )
            val result = withContext(Dispatchers.IO) {
                runCatching { applyStarImport(context, selectedForImport) }
            }
            importing = false
            result.onSuccess { count ->
                Toast.makeText(
                    context,
                    context.getString(R.string.github_star_import_toast_imported, count),
                    Toast.LENGTH_SHORT
                ).show()
                GitHubStarImportDraftStore.clearSelection()
                onClose()
            }.onFailure { throwable ->
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName }
            }
        }
    }

    LaunchedEffect(source) {
        error = null
        preview = null
        selectedIds = emptySet()
        pendingImportCandidates = emptyList()
        showExitConfirm = false
        apkVerificationStates.clear()
        starLists = emptyList()
        filterInput = ""
        viewFilter = StarImportViewFilter.All
        qualityFilters = defaultVisibleStarImportQualities()
        loadingProgress = 0f
        loadingPhase = ""
    }

    BackHandler(enabled = true) {
        requestClose()
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
                onClick = { requestClose() },
                backdrop = pageBackdrop
            )
        },
        actions = {
            AppLiquidIconButton(
                backdrop = pageBackdrop,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.github_star_import_cd_load),
                onClick = { loadPreview() },
                enabled = sourceReady && !loading && !importing,
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
                    source = source,
                    tokenAvailable = lookupConfig.apiToken.isNotBlank(),
                    usernameInput = usernameInput,
                    listUrlInput = listUrlInput,
                    loading = loading,
                    importing = importing,
                    sourceReady = sourceReady,
                    onSourceChange = { source = it },
                    onUsernameInputChange = { usernameInput = it },
                    onListUrlInputChange = { listUrlInput = it },
                    onLoadPreview = { loadPreview() }
                )
            }
            if (starLists.isNotEmpty()) {
                item {
                    StarImportStarListPickerCard(
                        lists = starLists,
                        loading = loading,
                        onSelect = { list ->
                            listUrlInput = list.url
                            loadPreview(forcedStarListUrl = list.url)
                        }
                    )
                }
            }
            item {
                StarImportStatusCard(
                    preview = preview,
                    loading = loading,
                    loadingProgress = loadingProgress,
                    loadingPhase = loadingPhase,
                    importing = importing,
                    error = error,
                    selectedCount = selectedImportableCount,
                    discoveredListCount = starLists.size
                )
            }
            if (preview != null) {
                item {
                    StarImportListControlCard(
                        filterInput = filterInput,
                        viewFilter = viewFilter,
                        qualityFilters = qualityFilters,
                        conflictStrategy = conflictStrategy,
                        qualityFilterCounts = listUiState.qualityFilterCounts,
                        filteredCount = listUiState.filteredCandidates.size,
                        visibleImportableCount = listUiState.visibleImportableIds.size,
                        visibleRecommendedCount = listUiState.visibleRecommendedIds.size,
                        visibleVerifiedApkCount = listUiState.visibleVerifiedApkIds.size,
                        selectedCount = selectedImportableCount,
                        verifiedApkCount = listUiState.verifiedApkCount,
                        checkingCount = listUiState.checkingCount,
                        verifySelectedEnabled = listUiState.selectedVerificationTargets.isNotEmpty() &&
                                !loading &&
                                !importing,
                        verifyVisibleEnabled = listUiState.visibleVerificationTargets.isNotEmpty() &&
                                !loading &&
                                !importing,
                        importEnabled = importEnabled,
                        importing = importing,
                        onFilterInputChange = { filterInput = it },
                        onViewFilterChange = { viewFilter = it },
                        onQualityFilterToggle = { filter ->
                            val nextFilters = if (filter in qualityFilters) {
                                qualityFilters - filter
                            } else {
                                qualityFilters + filter
                            }
                            qualityFilters = nextFilters.ifEmpty {
                                GitHubStarImportQuality.entries.toSet()
                            }
                        },
                        onConflictStrategyChange = { strategy ->
                            conflictStrategy = strategy
                            if (strategy == StarImportConflictStrategy.NewOnly) {
                                selectedIds = selectedIds - candidates
                                    .asSequence()
                                    .filter { it.alreadyTracked }
                                    .map { it.trackedApp.id }
                                    .toSet()
                            }
                        },
                        onVerifySelected = { verifyApkAssets(listUiState.selectedVerificationTargets) },
                        onVerifyVisible = { verifyApkAssets(listUiState.visibleVerificationTargets) },
                        onSelectRecommendedVisible = {
                            selectedIds = selectedIds + listUiState.visibleRecommendedIds
                        },
                        onSelectVerifiedVisible = {
                            selectedIds = selectedIds + listUiState.visibleVerifiedApkIds
                        },
                        onSelectVisible = {
                            selectedIds = selectedIds + listUiState.visibleImportableIds
                        },
                        onClearSelection = { selectedIds = emptySet() },
                        onImport = { requestImport() }
                    )
                }
                items(
                    items = listUiState.filteredCandidates,
                    key = { candidate -> candidate.trackedApp.id }
                ) { candidate ->
                    StarImportCandidateCard(
                        candidate = candidate,
                        selected = candidate.trackedApp.id in selectedIds,
                        trackedSelectable = conflictStrategy == StarImportConflictStrategy.IncludeTracked,
                        apkVerificationState = apkVerificationStates[candidate.trackedApp.id],
                        onToggle = {
                            if (
                                candidate.alreadyTracked &&
                                conflictStrategy != StarImportConflictStrategy.IncludeTracked
                            ) return@StarImportCandidateCard
                            selectedIds = if (candidate.trackedApp.id in selectedIds) {
                                selectedIds - candidate.trackedApp.id
                            } else {
                                selectedIds + candidate.trackedApp.id
                            }
                        }
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
        candidates = pendingImportCandidates,
        verificationStates = apkVerificationStates,
        importing = importing,
        onDismissRequest = { pendingImportCandidates = emptyList() },
        onConfirmImport = { applyImport(pendingImportCandidates) }
    )
    GitHubStarImportExitConfirmDialog(
        show = showExitConfirm,
        selectedCount = selectedImportableCount,
        onDismissRequest = { showExitConfirm = false },
        onConfirmExit = {
            showExitConfirm = false
            onClose()
        }
    )
}

private sealed interface StarImportLoadResult {
    data class Lists(val items: List<GitHubStarListSummary>) : StarImportLoadResult
    data class Preview(val preview: GitHubStarredRepositoryImportPreview) : StarImportLoadResult
}

private const val MAX_APK_VERIFICATION_BATCH = 30
private const val MAX_PARALLEL_APK_VERIFICATIONS = 4
