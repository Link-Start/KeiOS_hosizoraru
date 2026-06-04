package os.kei.mcp.bridge

import os.kei.feature.home.mcp.McpHomeBaSnapshot
import os.kei.feature.home.mcp.McpHomeBaSnapshotProvider
import os.kei.feature.home.model.HOME_BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.displayAp
import java.util.Locale

internal data object AppMcpHomeBaSnapshotProvider : McpHomeBaSnapshotProvider {
    override fun loadSnapshot(): McpHomeBaSnapshot {
        val snapshot = BASettingsStore.loadSnapshot()
        val normalizedFriendCode =
            snapshot.idFriendCode
                .uppercase(Locale.ROOT)
                .filter { it.isDigit() || it in 'A'..'Z' }
                .take(8)
        val cafeLevel = snapshot.cafeLevel.coerceIn(1, 10)
        val cafeCap = cafeStorageCap(cafeLevel)
        val cafeStored = snapshot.cafeStoredAp.coerceIn(0.0, cafeCap)
        return McpHomeBaSnapshot(
            activated = normalizedFriendCode.length == 8 && normalizedFriendCode != HOME_BA_DEFAULT_FRIEND_CODE,
            apCurrent = displayAp(snapshot.apCurrent),
            apLimit = snapshot.apLimit,
            cafeLevel = cafeLevel,
            cafeStored = cafeStored.toInt(),
            cafeCap = cafeCap.toInt(),
        )
    }
}
