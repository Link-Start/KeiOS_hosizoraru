package os.kei.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeiosVersionMetadataTest {
    @Test
    fun mergedOlderReleaseDoesNotLowerCurrentReleaseTarget() {
        assertEquals(AppSemVer(1, 16, 0), maxSemVer(AppSemVer(1, 15, 0), AppSemVer(1, 16, 0)))
        assertEquals(AppSemVer(2, 0, 0), maxSemVer(AppSemVer(2, 0, 0), AppSemVer(1, 16, 0)))
    }

    @Test
    fun releaseCodeKeepsReservedFinalSlotAndBoundsCommitCount() {
        val version = AppSemVer(1, 16, 0)
        assertEquals(11600999, version.toVersionCode(999))
        assertEquals(11600000, version.toVersionCode(-1))
        assertEquals(11600999, version.toVersionCode(1_000))
    }

    @Test
    fun versionAnchorAcceptsPlainSemverAndRejectsPreviewTags() {
        assertEquals(AppSemVer(1, 16, 0), parseSemVerTagOrNull(" v1.16.0 "))
        assertEquals(AppSemVer(1, 16, 0), parseSemVerTagOrNull("1.16.0"))
        assertNull(parseSemVerTagOrNull("v1.16.0-rc01"))
        assertNull(parseSemVerTagOrNull(null))
    }
}
