package os.kei.feature.github.data.remote

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubStrategyParityTest {
    @Test
    fun `tracked corpus expectations document current atom and token alignment`() {
        val fixtures = GitHubTrackedRepoFixtures.parityCorpus

        fixtures.forEach { fixture ->
            when (fixture.id) {
                "AChep/keyguard-app" -> {
                    assertEquals("r20260410", fixture.atomStableRawTag)
                    assertEquals("r20260410", fixture.tokenStableRawTag)
                    assertNull(fixture.atomPreRawTag)
                    assertNull(fixture.tokenPreRawTag)
                }
                "YumeLira/YumeBox" -> {
                    assertEquals("v0.5.1", fixture.atomStableRawTag)
                    assertEquals("v0.5.1", fixture.tokenStableRawTag)
                    assertNull(fixture.atomPreRawTag)
                    assertNull(fixture.tokenPreRawTag)
                }
                "T8RIN/ImageToolbox" -> {
                    assertEquals("3.8.0", fixture.atomStableRawTag)
                    assertEquals("3.8.0", fixture.tokenStableRawTag)
                    assertEquals("3.8.0-rc04", fixture.atomPreRawTag)
                    assertEquals("3.8.0-rc04", fixture.tokenPreRawTag)
                }
                "DimensionDev/Flare" -> {
                    assertEquals("1.4.3", fixture.atomStableRawTag)
                    assertEquals("1.4.3", fixture.tokenStableRawTag)
                    assertNull(fixture.atomPreRawTag)
                    assertNull(fixture.tokenPreRawTag)
                }
                "anilbeesetti/nextplayer" -> {
                    assertEquals("v0.16.3", fixture.atomStableRawTag)
                    assertEquals("v0.16.3", fixture.tokenStableRawTag)
                    assertNull(fixture.atomPreRawTag)
                    assertNull(fixture.tokenPreRawTag)
                }
                "open-ani/animeko" -> {
                    assertEquals("v5.4.3", fixture.atomStableRawTag)
                    assertEquals("v5.4.3", fixture.tokenStableRawTag)
                    assertEquals("v5.4.0-beta05", fixture.atomPreRawTag)
                    assertEquals("v5.4.0-beta05", fixture.tokenPreRawTag)
                }
                "CeuiLiSA/Pixiv-Shaft" -> {
                    assertEquals("v4.5.3", fixture.atomStableRawTag)
                    assertEquals("v4.5.3", fixture.tokenStableRawTag)
                }
                "monogram-android/monogram" -> {
                    assertEquals("0.0.8", fixture.atomStableRawTag)
                    assertEquals("0.0.8", fixture.atomPreRawTag)
                    assertEquals("0.0.8", fixture.tokenStableRawTag)
                    assertEquals("0.0.8", fixture.tokenPreRawTag)
                    assertEquals(false, fixture.atomHasStableRelease)
                    assertEquals(false, fixture.tokenHasStableRelease)
                }
            }
        }
    }

    @Test
    fun `most tracked repos already have parity after recent token iterations`() {
        val fixtures = GitHubTrackedRepoFixtures.parityCorpus
        val mismatches = fixtures.filter {
            it.atomStableRawTag != it.tokenStableRawTag || it.atomPreRawTag != it.tokenPreRawTag
        }

        assertEquals(emptyList(), mismatches.map { it.id })
    }
}
