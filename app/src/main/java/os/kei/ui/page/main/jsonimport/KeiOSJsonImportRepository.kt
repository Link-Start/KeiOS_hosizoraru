package os.kei.ui.page.main.jsonimport

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.log.AppLogger
import os.kei.core.concurrency.AppDispatchers

internal class KeiOSJsonImportRepository(
    ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) {
    private val sourceReader = KeiOSJsonImportSourceReader(ioDispatcher)
    private val githubPlanner = KeiOSJsonImportGitHubPlanner(ioDispatcher, defaultDispatcher)
    private val osPlanner = KeiOSJsonImportOsPlanner(ioDispatcher, defaultDispatcher)
    private val baPlanner = KeiOSJsonImportBaPlanner(ioDispatcher, defaultDispatcher)
    private val readOnlyPlanner = KeiOSJsonImportReadOnlyPlanner(defaultDispatcher)

    suspend fun buildPlan(
        context: Context,
        source: KeiOSJsonImportIntentSource,
        onStage: (KeiOSJsonImportStage) -> Unit
    ): KeiOSJsonImportPlan {
        val appContext = context.applicationContext
        val startedAtMs = System.currentTimeMillis()
        AppLogger.i(TAG, "json import buildPlan start source=${source.displayName}")
        onStage(KeiOSJsonImportStage.Reading)
        val file = sourceReader.readSource(appContext, source)
        val plan = try {
            onStage(KeiOSJsonImportStage.Detecting)
            val header = withContext(defaultDispatcher) {
                KeiOSJsonImportRouter.inspect(file.raw)
            }
            onStage(KeiOSJsonImportStage.Parsing)
            when (header.kind) {
                KeiOSJsonImportKind.GitHubTracked ->
                    githubPlanner.buildPlan(appContext, file, header)

                KeiOSJsonImportKind.OsActivityCards ->
                    osPlanner.buildActivityPlan(appContext, file, header)

                KeiOSJsonImportKind.OsShellCards ->
                    osPlanner.buildShellPlan(appContext, file, header)

                KeiOSJsonImportKind.OsCardsBundle ->
                    osPlanner.buildBundlePlan(appContext, file, header)

                KeiOSJsonImportKind.BaCatalogFavorites ->
                    baPlanner.buildCatalogPlan(appContext, file, header)

                KeiOSJsonImportKind.BaBgmFavorites ->
                    baPlanner.buildBgmPlan(appContext, file, header)

                KeiOSJsonImportKind.BaAllFavorites ->
                    baPlanner.buildAllPlan(appContext, file, header)

                KeiOSJsonImportKind.McpLogs,
                KeiOSJsonImportKind.OsInfoCard ->
                    readOnlyPlanner.buildPlan(appContext, file, header)

                KeiOSJsonImportKind.Unknown -> throw KeiOSJsonImportException(
                    KeiOSJsonImportFailureReason.UnsupportedFormat
                )
            }
        } catch (error: KeiOSJsonImportException) {
            throw error
        } catch (error: Throwable) {
            throw KeiOSJsonImportException(
                reason = KeiOSJsonImportFailureReason.ParseFailed,
                cause = error
            )
        }
        AppLogger.i(
            TAG,
            "json import preview ready kind=${plan.preview.kind} count=${plan.preview.totalCount} ms=${System.currentTimeMillis() - startedAtMs}"
        )
        return plan
    }

    suspend fun sourceFromIntent(context: Context, intent: Intent?): KeiOSJsonImportIntentSource {
        return sourceReader.sourceFromIntent(context, intent)
    }

    fun errorMessage(context: Context, error: Throwable): String {
        return buildJsonImportErrorMessage(context, error)
    }

    private companion object {
        private const val TAG = "KeiOSJsonImport"
    }
}
