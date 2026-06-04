package os.kei.feature.home.mcp

data class McpHomeBaSnapshot(
    val activated: Boolean,
    val apCurrent: Int,
    val apLimit: Int,
    val cafeLevel: Int,
    val cafeStored: Int,
    val cafeCap: Int,
)

fun interface McpHomeBaSnapshotProvider {
    fun loadSnapshot(): McpHomeBaSnapshot
}
