package os.kei.ui.page.main.github.importer

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
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
    val lookupConfig = remember { GitHubTrackStore.loadLookupConfig() }
    var source by remember { mutableStateOf(StarImportUiSource.MyStars) }
    var usernameInput by remember { mutableStateOf("") }
    var listUrlInput by remember { mutableStateOf("") }
    var filterInput by remember { mutableStateOf("") }
    var viewFilter by remember { mutableStateOf(StarImportViewFilter.All) }
    var preview by remember { mutableStateOf<GitHubStarredRepositoryImportPreview?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
    val candidates = preview?.candidates.orEmpty()
    val searchedCandidates = candidates.filter { candidate ->
        val query = filterInput.trim()
        query.isBlank() ||
                candidate.repository.fullName.contains(query, ignoreCase = true) ||
                candidate.repository.description.contains(query, ignoreCase = true) ||
                candidate.repository.language.contains(query, ignoreCase = true)
    }
    val filteredCandidates = searchedCandidates.filter { candidate ->
        when (viewFilter) {
            StarImportViewFilter.All -> true
            StarImportViewFilter.Importable -> !candidate.alreadyTracked
            StarImportViewFilter.Selected -> candidate.trackedApp.id in selectedIds
            StarImportViewFilter.Tracked -> candidate.alreadyTracked
        }
    }
    val selectedImportableCount = candidates.count { candidate ->
        !candidate.alreadyTracked && candidate.trackedApp.id in selectedIds
    }
    val importEnabled = selectedImportableCount > 0 && !loading && !importing
    val visibleImportableIds = filteredCandidates
        .filterNot { it.alreadyTracked }
        .map { it.trackedApp.id }
        .toSet()

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
                        selectedIds = nextPreview.candidates
                            .filterNot { it.alreadyTracked }
                            .map { it.trackedApp.id }
                            .toSet()
                    }
                }
            }.onFailure { throwable ->
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName }
                loadingPhase = ""
            }
        }
    }

    fun applyImport() {
        val snapshot = preview ?: return
        val selected = snapshot.candidates
            .filter { candidate -> !candidate.alreadyTracked && candidate.trackedApp.id in selectedIds }
        if (selected.isEmpty() || importing) return
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { applyStarImport(context, selected) }
            }
            importing = false
            result.onSuccess { count ->
                Toast.makeText(
                    context,
                    context.getString(R.string.github_star_import_toast_imported, count),
                    Toast.LENGTH_SHORT
                ).show()
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
        starLists = emptyList()
        filterInput = ""
        viewFilter = StarImportViewFilter.All
        loadingProgress = 0f
        loadingPhase = ""
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
                onClick = onClose,
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
                        filteredCount = filteredCandidates.size,
                        visibleImportableCount = visibleImportableIds.size,
                        selectedCount = selectedImportableCount,
                        importEnabled = importEnabled,
                        importing = importing,
                        onFilterInputChange = { filterInput = it },
                        onViewFilterChange = { viewFilter = it },
                        onSelectVisible = { selectedIds = selectedIds + visibleImportableIds },
                        onClearSelection = { selectedIds = emptySet() },
                        onImport = { applyImport() }
                    )
                }
                items(
                    items = filteredCandidates,
                    key = { candidate -> candidate.trackedApp.id }
                ) { candidate ->
                    StarImportCandidateCard(
                        candidate = candidate,
                        selected = candidate.trackedApp.id in selectedIds,
                        onToggle = {
                            if (candidate.alreadyTracked) return@StarImportCandidateCard
                            selectedIds = if (candidate.trackedApp.id in selectedIds) {
                                selectedIds - candidate.trackedApp.id
                            } else {
                                selectedIds + candidate.trackedApp.id
                            }
                        }
                    )
                }
                if (filteredCandidates.isEmpty()) {
                    item {
                        StarImportEmptyCard()
                    }
                }
            }
        }
    }
}

private sealed interface StarImportLoadResult {
    data class Lists(val items: List<GitHubStarListSummary>) : StarImportLoadResult
    data class Preview(val preview: GitHubStarredRepositoryImportPreview) : StarImportLoadResult
}
