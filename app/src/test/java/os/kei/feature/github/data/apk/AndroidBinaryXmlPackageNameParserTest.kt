package os.kei.feature.github.data.apk

import org.junit.Test
import kotlin.test.assertEquals

class AndroidBinaryXmlPackageNameParserTest {
    @Test
    fun `parser reads package name from binary Android manifest`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.debug")

        val packageName = AndroidBinaryXmlPackageNameParser.parsePackageName(manifest).getOrThrow()

        assertEquals("os.kei.debug", packageName)
    }
}
