package os.kei.mcp.server

object McpServerRuntimeRegistry {
    @Volatile
    private var runningManager: McpServerManager? = null

    fun registerRunning(serverManager: McpServerManager) {
        runningManager = serverManager
    }

    fun clearRunning(serverManager: McpServerManager) {
        if (runningManager === serverManager) {
            runningManager = null
        }
    }

    fun currentManager(): McpServerManager? = runningManager

    fun stopCurrentServer(): Boolean {
        val manager = currentManager() ?: return false
        manager.stop()
        return true
    }
}
