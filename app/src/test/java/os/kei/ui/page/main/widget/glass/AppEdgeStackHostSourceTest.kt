package os.kei.ui.page.main.widget.glass

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Every stacking host, held to the one contract the compiler cannot check.
 *
 * `AppEdgeStackKeepAlive` gives its child an extra viewport *above* the visible top and places the
 * child that far up. The child's own top content inset has to absorb the shift, or the first card
 * starts a headroom off screen — and nothing about that is a type error. `appEdgeStackContainer` is
 * private now, so the other half of the old contract (tagging the list rather than the visible box)
 * is enforced by the compiler and does not need a test.
 *
 * A new host arriving without either half is the failure this guards: it would compile, and the
 * pile on it would silently be one card deep again.
 */
class AppEdgeStackHostSourceTest {
    @Test
    fun everyStackingHostWrapsItsListInTheKeepAliveBox() {
        STACKING_HOSTS.forEach { host ->
            val source = sourceFile(host)
            assertTrue(
                "AppEdgeStackKeepAlive(" in source,
                "$host provides LocalAppEdgeStackCards, so its list must sit in the keep-alive box",
            )
        }
    }

    @Test
    fun everyStackingHostRunsItsTopInsetThroughTheHelper() {
        STACKING_HOSTS.forEach { host ->
            val source = sourceFile(host)
            assertTrue(
                "appEdgeStackKeepAliveTopPadding(" in source,
                "$host shifts its list up, so its top inset must absorb the headroom",
            )
        }
    }

    /**
     * The two pages whose chrome floats over their own list must tell the pile how much it covers.
     *
     * `AppEdgeStackKeepAlive`'s `bottomInset` is what stops a card being judged pinnable on the
     * strength of a row that can only ever sit under the pager bar — visible, and not tappable. That
     * distinction is the whole of issue #29: the download and share buttons live in the *last* rows of
     * an expanded card, so a bound that counts the covered strip as reachable lets exactly the card
     * that matters back into the pile.
     *
     * Only these two are held to it. The other hosts float chrome too, but their piled cards are
     * collapsed headers a fraction of the window tall, so the inset cannot change the verdict for them
     * and leaving it unset keeps their behaviour bit-identical.
     */
    @Test
    fun pagesWithFloatingBottomChromeDeclareItToThePile() {
        BOTTOM_CHROME_HOSTS.forEach { host ->
            val source = sourceFile(host)
            assertTrue(
                "bottomInset = " in source,
                "$host floats a bar over its list, so the pile must be told what it covers",
            )
        }
    }

    /**
     * Guards the *set*, so a new host cannot be added to the app without being added here.
     *
     * Keyed on `rememberAppEdgeStackState(`, not on the provider. Providing the local is not the
     * marker it looks like: `GuideLiquidCard` and `BaLiquidSurfaces` both provide it as `null` to
     * suppress stacking on a nested surface, and they are the opposite of hosts. Creating the state
     * is what only a host does.
     */
    @Test
    fun theListOfStackingHostsIsComplete() {
        val root = repositoryRoot()
        val hosts =
            File(root, APP_SOURCE_PREFIX)
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .filter { file -> "rememberAppEdgeStackState(" in file.readText() }
                .map { file -> file.relativeTo(root).invariantSeparatorsPath }
                .toList()

        val expected = STACKING_HOSTS.map { host -> "$APP_SOURCE_PREFIX$host" }.sorted()
        assertTrue(
            hosts.sorted() == expected,
            "Stacking hosts changed. Found:\n${hosts.sorted().joinToString("\n")}\nExpected:\n" +
                expected.joinToString("\n"),
        )
    }
}

private const val APP_SOURCE_PREFIX = "app/src/main/java/os/kei/ui/page/main/"

private val STACKING_HOSTS =
    listOf(
        "ba/BaCalendarPoolStackedLayout.kt",
        "ba/BaPageContent.kt",
        "github/fdroid/FdroidVersionListPage.kt",
        "github/history/GitHubActionsNotificationHistoryPage.kt",
        "github/release/GitHubReleaseListPage.kt",
        "github/section/GitHubMainContentSection.kt",
        "mcp/McpPageContent.kt",
        "os/components/OsPageMainList.kt",
        "student/catalog/component/BaGuideCatalogV2ListContent.kt",
        "student/catalog/component/BaGuideMemoryLobbyTabContent.kt",
        "student/catalog/component/BaGuideStudentBgmTabContent.kt",
    )

private val BOTTOM_CHROME_HOSTS =
    listOf(
        "github/fdroid/FdroidVersionListPage.kt",
        "github/release/GitHubReleaseListPage.kt",
    )

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "${APP_SOURCE_PREFIX}os/components").isDirectory },
    ) {
        "Unable to locate the repository root from $workingDirectory"
    }
}

private fun sourceFile(relativePath: String): String {
    val file = File(repositoryRoot(), "$APP_SOURCE_PREFIX$relativePath")
    assertTrue(file.isFile, "Missing stacking host source: $relativePath")
    return file.readText()
}
