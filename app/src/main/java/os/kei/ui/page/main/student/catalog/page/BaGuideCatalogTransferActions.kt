package os.kei.ui.page.main.student.catalog.page

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import os.kei.core.ext.showToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.ui.page.main.ba.support.BASettingsStore

internal data class BaGuideCatalogTransferSaveLocationState(
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    val onPickMediaSaveLocation: () -> Unit
)

internal data class BaGuideCatalogJsonExportAction(
    val saveLocationState: BaGuideCatalogTransferSaveLocationState,
    val exportJson: (payload: String, fileName: String, successToast: String) -> Unit,
    val exportJsonFrom: (
        payloadBuilder: BaGuideCatalogJsonExportPayloadBuilder,
        fileName: String,
        successToast: String
    ) -> Unit
)

internal typealias BaGuideCatalogJsonExportPayloadBuilder = suspend () -> String

@Composable
internal fun rememberBaGuideCatalogJsonExportAction(
    context: Context,
    pageScope: CoroutineScope,
    exportDoneText: String,
    exportFailedText: String
): BaGuideCatalogJsonExportAction {
    var pendingSafExportRequest by remember { mutableStateOf<BaGuideCatalogJsonExportRequest?>(null) }
    var pendingFixedExportRequest by remember { mutableStateOf<BaGuideCatalogJsonExportRequest?>(null) }
    var mediaSaveCustomEnabled by rememberSaveable {
        mutableStateOf(BASettingsStore.loadMediaSaveCustomEnabled())
    }
    var mediaSaveFixedTreeUri by rememberSaveable {
        mutableStateOf(BASettingsStore.loadMediaSaveFixedTreeUri())
    }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val request = pendingSafExportRequest
        pendingSafExportRequest = null
        if (uri == null || request == null || request.payload.isBlank()) {
            return@rememberLauncherForActivityResult
        }
        pageScope.launch {
            val success = writeBaGuideCatalogJsonExportAsync(
                context = context,
                uri = uri,
                request = request
            )
            context.showToast(
                if (success) request.successToast.ifBlank { exportDoneText } else exportFailedText
            )
        }
    }
    val fixedExportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingFixedExportRequest
        if (result.resultCode != Activity.RESULT_OK || request == null) {
            pendingFixedExportRequest = null
            return@rememberLauncherForActivityResult
        }
        val treeUri = result.data?.data
        if (treeUri == null) {
            pendingFixedExportRequest = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val persistableFlags = result.data?.flags.orZero() and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (persistableFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (persistableFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        BASettingsStore.saveMediaSaveFixedTreeUri(treeUri.toString())
        mediaSaveFixedTreeUri = treeUri.toString()
        pendingFixedExportRequest = null
        pageScope.launch {
            val success = writeBaGuideCatalogJsonExportToTreeAsync(
                context = context,
                treeUri = treeUri,
                request = request
            )
            context.showToast(
                if (success) request.successToast.ifBlank { exportDoneText } else exportFailedText
            )
        }
    }

    fun launchFixedExportFolderPicker() {
        val currentTreeUri = mediaSaveFixedTreeUri
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                currentTreeUri ?: "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri()
            )
        }
        fixedExportFolderLauncher.launch(pickerIntent)
    }

    fun dispatchExportRequest(request: BaGuideCatalogJsonExportRequest) {
        if (request.payload.isBlank()) return
        if (!mediaSaveCustomEnabled) {
            pendingSafExportRequest = request
            safExportLauncher.launch(request.fileName)
            return
        }
        val fixedTreeUri = mediaSaveFixedTreeUri
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
        if (fixedTreeUri == null) {
            pendingFixedExportRequest = request
            launchFixedExportFolderPicker()
            return
        }
        pageScope.launch {
            val success = writeBaGuideCatalogJsonExportToTreeAsync(
                context = context,
                treeUri = fixedTreeUri,
                request = request
            )
            if (success) {
                context.showToast(
                    request.successToast.ifBlank { exportDoneText }
                )
            } else {
                BASettingsStore.saveMediaSaveFixedTreeUri("")
                mediaSaveFixedTreeUri = ""
                pendingFixedExportRequest = request
                launchFixedExportFolderPicker()
            }
        }
    }

    val exportJsonFrom: (
        BaGuideCatalogJsonExportPayloadBuilder,
        String,
        String
    ) -> Unit = remember(
        context,
        pageScope,
        mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri,
        safExportLauncher,
        fixedExportFolderLauncher,
        exportDoneText,
        exportFailedText
    ) {
        export@{ payloadBuilder, fileName, successToast ->
            pageScope.launch {
                val request = try {
                    BaGuideCatalogJsonExportRequest(
                        payload = payloadBuilder(),
                        fileName = fileName,
                        successToast = successToast
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    context.showToast(exportFailedText)
                    return@launch
                }
                if (request.payload.isBlank()) {
                    context.showToast(exportFailedText)
                    return@launch
                }
                dispatchExportRequest(request)
            }
        }
    }
    val exportJson: (String, String, String) -> Unit = remember(exportJsonFrom) {
        { payload, fileName, successToast ->
            exportJsonFrom({ payload }, fileName, successToast)
        }
    }
    val saveLocationState = remember(mediaSaveCustomEnabled, mediaSaveFixedTreeUri) {
        BaGuideCatalogTransferSaveLocationState(
            mediaSaveCustomEnabled = mediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
            onMediaSaveCustomEnabledChange = { enabled ->
                mediaSaveCustomEnabled = enabled
                BASettingsStore.saveMediaSaveCustomEnabled(enabled)
            },
            onPickMediaSaveLocation = {
                pendingFixedExportRequest = null
                launchFixedExportFolderPicker()
            }
        )
    }
    return remember(saveLocationState, exportJson, exportJsonFrom) {
        BaGuideCatalogJsonExportAction(
            saveLocationState = saveLocationState,
            exportJson = exportJson,
            exportJsonFrom = exportJsonFrom
        )
    }
}

private fun Int?.orZero(): Int = this ?: 0
