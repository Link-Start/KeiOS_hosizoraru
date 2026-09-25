package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class FdroidRepositoryPresetsTest {
    @Test
    fun `common source normalization drops unknown ids and preserves preset order`() {
        assertEquals(
            listOf(
                FdroidRepositoryPresets.MAIN_ID,
                FdroidRepositoryPresets.GUARDIAN_ID,
            ),
            FdroidRepositoryPresets.normalizedCommonSearchRepoIds(
                listOf(
                    "missing",
                    FdroidRepositoryPresets.GUARDIAN_ID,
                    FdroidRepositoryPresets.MAIN_ID,
                    FdroidRepositoryPresets.MAIN_ID,
                ),
            ),
        )
    }

    @Test
    fun `empty common source ids fall back to defaults`() {
        assertEquals(
            FdroidRepositoryPresets.defaultCommonSearchRepoIds,
            FdroidRepositoryPresets.normalizedCommonSearchRepoIds(emptyList()),
        )
        assertEquals(
            FdroidRepositoryPresets.defaultCommonSearchRepoIds,
            FdroidRepositoryPresets.normalizedCommonSearchRepoIds(listOf("missing")),
        )
    }

    @Test
    fun `common scope resolves configured repo urls`() {
        assertEquals(
            listOf(FdroidRepositoryPresets.GuardianProject.repoUrl),
            FdroidRepositoryPresets.repoUrlsForScope(
                scopeId = FdroidRepositoryPresets.COMMON_ID,
                customRepoUrl = "",
                commonRepoIds = listOf(FdroidRepositoryPresets.GUARDIAN_ID),
            ),
        )
    }
}
