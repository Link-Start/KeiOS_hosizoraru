package os.kei.ui.page.main.github.importer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class GitHubStarImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GitHubStarImportTheme {
                GitHubStarImportPage(onClose = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val hostActivity = context.findGitHubStarImportHostActivity()
            val intent = Intent(context, GitHubStarImportActivity::class.java).apply {
                if (hostActivity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (hostActivity != null) {
                hostActivity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun GitHubStarImportTheme(content: @Composable () -> Unit) {
    val colorSchemeMode = when (UiPrefs.getAppThemeMode()) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }
    MiuixTheme(controller = ThemeController(colorSchemeMode)) {
        content()
    }
}

@Composable
private fun GitHubStarImportPage(onClose: () -> Unit) {
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
    var preview by remember { mutableStateOf<GitHubStarredRepositoryImportPreview?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val candidates = preview?.candidates.orEmpty()
    val filteredCandidates = candidates.filter { candidate ->
        val query = filterInput.trim()
        query.isBlank() ||
                candidate.repository.fullName.contains(query, ignoreCase = true) ||
                candidate.repository.description.contains(query, ignoreCase = true)
    }
    val selectedImportableCount = candidates.count { candidate ->
        !candidate.alreadyTracked && candidate.trackedApp.id in selectedIds
    }
    val importEnabled = selectedImportableCount > 0 && !loading && !importing

    fun loadPreview() {
        if (loading || importing) return
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

    BackHandler(onBack = onClose)
    LaunchedEffect(source) {
        error = null
        preview = null
        selectedIds = emptySet()
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
                enabled = !loading && !importing
            )
            AppLiquidIconButton(
                modifier = Modifier.graphicsLayer { alpha = if (importEnabled) 1f else 0.52f },
                backdrop = pageBackdrop,
                icon = appLucideConfirmIcon(),
                contentDescription = stringResource(R.string.github_star_import_cd_import),
                onClick = { applyImport() },
                enabled = importEnabled
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
                    AppLiquidSearchField(
                        value = filterInput,
                        onValueChange = { filterInput = it },
                        label = stringResource(R.string.github_star_import_filter_label),
                        backdrop = pageBackdrop,
                        variant = GlassVariant.Content,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
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
            }
        }
    }
}

@Composable
private fun StarImportSourceCard(
    source: StarImportUiSource,
    tokenAvailable: Boolean,
    usernameInput: String,
    listUrlInput: String,
    loading: Boolean,
    onSourceChange: (StarImportUiSource) -> Unit,
    onUsernameInputChange: (String) -> Unit,
    onListUrlInputChange: (String) -> Unit,
    onLoadPreview: () -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_source_title),
        subtitle = stringResource(R.string.github_star_import_source_summary),
        sectionIcon = appLucideHeartIcon(),
        showIndication = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportSourceButton(
                text = stringResource(R.string.github_star_import_source_my_stars),
                selected = source == StarImportUiSource.MyStars,
                onClick = { onSourceChange(StarImportUiSource.MyStars) },
                modifier = Modifier.weight(1f)
            )
            StarImportSourceButton(
                text = stringResource(R.string.github_star_import_source_user),
                selected = source == StarImportUiSource.PublicUser,
                onClick = { onSourceChange(StarImportUiSource.PublicUser) },
                modifier = Modifier.weight(1f)
            )
            StarImportSourceButton(
                text = stringResource(R.string.github_star_import_source_list_url),
                selected = source == StarImportUiSource.ListUrl,
                onClick = { onSourceChange(StarImportUiSource.ListUrl) },
                modifier = Modifier.weight(1f)
            )
        }
        when (source) {
            StarImportUiSource.MyStars -> {
                StarImportInfoLine(
                    label = stringResource(R.string.github_star_import_token_label),
                    value = stringResource(
                        if (tokenAvailable) {
                            R.string.github_star_import_token_ready
                        } else {
                            R.string.github_star_import_token_missing
                        }
                    ),
                    color = if (tokenAvailable) GitHubStatusPalette.Update else GitHubStatusPalette.Error
                )
            }

            StarImportUiSource.PublicUser -> {
                AppLiquidSearchField(
                    value = usernameInput,
                    onValueChange = onUsernameInputChange,
                    label = stringResource(R.string.github_star_import_username_label),
                    backdrop = null,
                    variant = GlassVariant.Content,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }

            StarImportUiSource.ListUrl -> {
                AppLiquidSearchField(
                    value = listUrlInput,
                    onValueChange = onListUrlInputChange,
                    label = stringResource(R.string.github_star_import_list_url_label),
                    backdrop = null,
                    variant = GlassVariant.Content,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
        AppLiquidTextButton(
            backdrop = null,
            text = if (loading) {
                stringResource(R.string.github_star_import_action_loading)
            } else {
                stringResource(R.string.github_star_import_action_load)
            },
            onClick = onLoadPreview,
            enabled = !loading,
            variant = GlassVariant.Content,
            leadingIcon = appLucideListIcon(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StarImportSourceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppLiquidTextButton(
        backdrop = null,
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = if (selected) GlassVariant.SheetAction else GlassVariant.Content
    )
}

@Composable
private fun StarImportStatusCard(
    preview: GitHubStarredRepositoryImportPreview?,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    selectedCount: Int
) {
    val title = when {
        loading -> stringResource(R.string.github_star_import_status_loading)
        importing -> stringResource(R.string.github_star_import_status_importing)
        error != null -> stringResource(R.string.github_star_import_status_error)
        preview != null -> stringResource(R.string.github_star_import_status_ready)
        else -> stringResource(R.string.github_star_import_status_waiting)
    }
    val subtitle = when {
        error != null -> error
        preview != null -> stringResource(
            R.string.github_star_import_status_preview_format,
            preview.totalFetchedCount,
            preview.importableCount,
            preview.alreadyTrackedCount,
            selectedCount
        )

        else -> stringResource(R.string.github_star_import_status_waiting_summary)
    }
    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        sectionIcon = appLucideListIcon(),
        showIndication = false,
        headerEndActions = {
            StatusPill(
                label = when {
                    error != null -> stringResource(R.string.common_status_failed)
                    loading || importing -> stringResource(R.string.common_status_running)
                    preview != null -> stringResource(R.string.common_available)
                    else -> stringResource(R.string.common_not_loaded)
                },
                color = when {
                    error != null -> GitHubStatusPalette.Error
                    loading || importing -> GitHubStatusPalette.Active
                    preview != null -> GitHubStatusPalette.Update
                    else -> MiuixTheme.colorScheme.onBackgroundVariant
                }
            )
        }
    ) {
        if (preview != null) {
            StarImportInfoLine(
                label = stringResource(R.string.github_star_import_source_label),
                value = preview.sourceLabel,
                color = MiuixTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun StarImportCandidateCard(
    candidate: GitHubRepositoryImportCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val disabled = candidate.alreadyTracked
    val accent = when {
        disabled -> MiuixTheme.colorScheme.onBackgroundVariant
        selected -> GitHubStatusPalette.Update
        else -> MiuixTheme.colorScheme.primary
    }
    AppFeatureCard(
        title = candidate.repository.fullName,
        subtitle = candidate.repository.description.ifBlank {
            stringResource(R.string.github_star_import_candidate_no_description)
        },
        sectionIcon = appLucideHeartIcon(),
        titleColor = accent,
        onClick = if (disabled) null else onToggle,
        showIndication = !disabled,
        headerEndActions = {
            StatusPill(
                label = when {
                    disabled -> stringResource(R.string.github_star_import_candidate_tracked)
                    selected -> stringResource(R.string.github_star_import_candidate_selected)
                    else -> stringResource(R.string.github_star_import_candidate_optional)
                },
                color = when {
                    disabled -> MiuixTheme.colorScheme.onBackgroundVariant
                    selected -> GitHubStatusPalette.Update
                    else -> GitHubStatusPalette.Active
                }
            )
        }
    ) {
        Text(
            text = candidate.trackedApp.repoUrl,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StarImportInfoLine(
    label: String,
    value: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private suspend fun applyStarImport(
    context: Context,
    candidates: List<GitHubRepositoryImportCandidate>
): Int {
    if (candidates.isEmpty()) return 0
    val selectedItems = candidates.map { it.trackedApp }
    val existing = GitHubTrackStore.load()
    val merged = existing.toMutableList()
    val indexById = merged.withIndex().associate { it.value.id to it.index }.toMutableMap()
    var changedCount = 0
    selectedItems.forEach { item ->
        val existingIndex = indexById[item.id]
        if (existingIndex == null) {
            merged += item
            indexById[item.id] = merged.lastIndex
            changedCount += 1
        } else if (merged[existingIndex] != item) {
            merged[existingIndex] = item
            changedCount += 1
        }
    }
    if (changedCount == 0) return 0
    GitHubTrackStore.save(merged)
    selectedItems.forEach { item ->
        GitHubTrackStoreSignals.requestTrackRefresh(
            trackId = item.id,
            notifyChangeSignal = false
        )
    }
    GitHubTrackStoreSignals.notifyChanged()
    AppBackgroundScheduler.scheduleGitHubRefresh(context)
    return changedCount
}

private enum class StarImportUiSource {
    MyStars,
    PublicUser,
    ListUrl;

    fun toRequestSource(): GitHubStarredRepositoryImportSource {
        return when (this) {
            MyStars -> GitHubStarredRepositoryImportSource.AuthenticatedUser
            PublicUser -> GitHubStarredRepositoryImportSource.PublicUser
            ListUrl -> GitHubStarredRepositoryImportSource.StarListUrl
        }
    }
}

private tailrec fun Context.findGitHubStarImportHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findGitHubStarImportHostActivity()
        else -> null
    }
}
