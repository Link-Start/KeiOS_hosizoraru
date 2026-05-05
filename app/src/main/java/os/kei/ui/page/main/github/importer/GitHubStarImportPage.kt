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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
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
    var loading by remember { mutableStateOf(false) }
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

    fun loadPreview() {
        if (loading || importing) return
        if (!sourceReady) {
            error = context.getString(source.requirementMessageRes)
            return
        }
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = GitHubStarredRepositoryImportRequest(
                        source = source.toRequestSource(),
                        username = usernameInput.trim(),
                        starListUrl = listUrlInput.trim(),
                        apiToken = lookupConfig.apiToken,
                        limit = 1_000
                    )
                    GitHubRepositoryDiscoveryService(
                        GitHubRepositoryDiscoveryRepository(apiToken = lookupConfig.apiToken)
                    ).previewStarredRepositoryImport(
                        request = request,
                        existingItems = GitHubTrackStore.load()
                    ).getOrThrow()
                }
            }
            loading = false
            result.onSuccess { nextPreview ->
                preview = nextPreview
                selectedIds = nextPreview.candidates
                    .filterNot { it.alreadyTracked }
                    .map { it.trackedApp.id }
                    .toSet()
            }.onFailure { throwable ->
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName }
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
        filterInput = ""
        viewFilter = StarImportViewFilter.All
    }

    AppPageScaffold(
        title = stringResource(R.string.github_star_import_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = true,
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
                enabled = sourceReady && !loading && !importing
            )
        }
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
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
            item {
                StarImportStatusCard(
                    preview = preview,
                    loading = loading,
                    importing = importing,
                    error = error,
                    selectedCount = selectedImportableCount
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
