package os.kei.ui.page.main.os

internal class OsPageSectionLoadGeneration {
    private var currentGeneration = 0

    fun current(): Int = currentGeneration

    fun advance(): Int {
        currentGeneration += 1
        return currentGeneration
    }

    fun isCurrent(generation: Int): Boolean = generation == currentGeneration
}
