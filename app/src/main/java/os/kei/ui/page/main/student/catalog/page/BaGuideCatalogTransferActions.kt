package os.kei.ui.page.main.student.catalog.page

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.page.support.createUniqueDocumentInTree

internal data class BaGuideCatalogTransferSaveLocationState(
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    val onPickMediaSaveLocation: () -> Unit
)

internal data class BaGuideCatalogJsonExportAction(
    val saveLocationState: BaGuideCatalogTransferSaveLocationState,
    val exportJson: (payload: String, fileName: String, successToast: String) -> Unit
)

private data class BaGuideCatalogJsonExportRequest(
    val payload: String,
    val fileName: String,
    val successToast: String
)

@Composable
internal fun rememberBaGuideCatalogJsonExportAction(
    context: Context,
    pageScope: CoroutineScope,
    exportDoneText: String,
    exportFailedText: String
): BaGuideCatalogJsonExportAction {
    var pendingSafExportPayload by remember { mutableStateOf("") }
    var pendingSafExportToast by remember { mutableStateOf("") }
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
        val payload = pendingSafExportPayload
        val toast = pendingSafExportToast
        pendingSafExportPayload = ""
        pendingSafExportToast = ""
        if (uri == null || payload.isBlank()) return@rememberLauncherForActivityResult
        pageScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        if (writer == null) return@runCatching false
                        writer.write(payload)
                        true
                    }
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (success) toast.ifBlank { exportDoneText } else exportFailedText,
                Toast.LENGTH_SHORT
            ).show()
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
            val success = withContext(Dispatchers.IO) {
                writeBaGuideCatalogJsonExportToTree(context, treeUri, request)
            }
            Toast.makeText(
                context,
                if (success) request.successToast.ifBlank { exportDoneText } else exportFailedText,
                Toast.LENGTH_SHORT
            ).show()
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

    val exportJson: (String, String, String) -> Unit = remember(
        context,
        pageScope,
        mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri,
        safExportLauncher,
        fixedExportFolderLauncher,
        exportDoneText,
        exportFailedText
    ) {
        export@{ payload, fileName, successToast ->
            if (payload.isBlank()) return@export
            val request = BaGuideCatalogJsonExportRequest(
                payload = payload,
                fileName = fileName,
                successToast = successToast
            )
            if (!mediaSaveCustomEnabled) {
                pendingSafExportPayload = payload
                pendingSafExportToast = successToast
                safExportLauncher.launch(fileName)
                return@export
            }
            val fixedTreeUri = mediaSaveFixedTreeUri
                .takeIf { it.isNotBlank() }
                ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
            if (fixedTreeUri == null) {
                pendingFixedExportRequest = request
                launchFixedExportFolderPicker()
                return@export
            }
            pageScope.launch {
                val success = withContext(Dispatchers.IO) {
                    writeBaGuideCatalogJsonExportToTree(context, fixedTreeUri, request)
                }
                if (success) {
                    Toast.makeText(
                        context,
                        successToast.ifBlank { exportDoneText },
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    BASettingsStore.saveMediaSaveFixedTreeUri("")
                    mediaSaveFixedTreeUri = ""
                    pendingFixedExportRequest = request
                    launchFixedExportFolderPicker()
                }
            }
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
    return remember(saveLocationState, exportJson) {
        BaGuideCatalogJsonExportAction(
            saveLocationState = saveLocationState,
            exportJson = exportJson
        )
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun writeBaGuideCatalogJsonExportToTree(
    context: Context,
    treeUri: Uri,
    request: BaGuideCatalogJsonExportRequest
): Boolean {
    if (request.payload.isBlank() || request.fileName.isBlank()) return false
    return runCatching {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching false
        val targetDoc = createUniqueDocumentInTree(
            tree = treeDoc,
            mimeType = "application/json",
            fileName = request.fileName
        ) ?: return@runCatching false
        val output = context.contentResolver.openOutputStream(targetDoc.uri) ?: return@runCatching false
        output.use { stream ->
            stream.bufferedWriter().use { writer ->
                writer.write(request.payload)
            }
        }
        true
    }.getOrDefault(false)
}
