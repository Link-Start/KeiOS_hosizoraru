package os.kei.ui.page.main.widget.glass

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * A declared `modifier` parameter has to actually reach the composable's own node.
 *
 * Declaring one and never using it is a warning, not an error, so it ships. `AppLiquidExpandableCardFrame`
 * did exactly that: it took `modifier` and called `AppSurfaceCard` without it, which silently swallowed
 * every accordion card's `Modifier.testTag`. The BA slot cards looked correctly tagged in source, the tags
 * were compiled into the APK, and nothing carried a resource-id on screen — the baseline profile journey
 * failed 24 minutes into a run with "Timed out waiting for testTag=ba_cooldown_card_first".
 *
 * The same silence swallows padding, size and clickable from any caller, so this is not only about tags.
 */
class ComposableModifierParameterSourceTest {
    @Test
    fun everyDeclaredModifierParameterIsUsed() {
        val root = repositoryRoot()
        val offenders =
            sourceRoots(root)
                .flatMap { sourceRoot ->
                    sourceRoot
                        .walkTopDown()
                        .filter { file -> file.isFile && file.extension == "kt" }
                        .flatMap { file ->
                            unusedModifierParameters(file.readText()).map { name ->
                                "${file.relativeTo(root).invariantSeparatorsPath}: $name"
                            }
                        }
                        .toList()
                }
                .sorted()

        assertTrue(
            offenders.isEmpty(),
            "These functions declare a modifier parameter and never use it, so callers' modifiers are " +
                "silently dropped:\n${offenders.joinToString("\n")}",
        )
    }

    /**
     * Guards the scan's *reach*, so the sweep above cannot quietly stop covering the app.
     *
     * The roots are discovered rather than listed, so a new module is covered the day it appears — which
     * only helps if discovery actually finds them.
     */
    @Test
    fun theScanReachesEveryModule() {
        val root = repositoryRoot()
        val roots = sourceRoots(root).map { file -> file.relativeTo(root).invariantSeparatorsPath }

        assertTrue(
            "app/src/main/java/os/kei" in roots && "ui-liquid-glass/src/main/java/os/kei" in roots,
            "Discovery missed a module it is known to cover: $roots",
        )
        assertTrue(
            roots.size >= MINIMUM_SOURCE_ROOTS,
            "Only ${roots.size} source roots found, so discovery is broken rather than the tree shrinking",
        )
    }

    @Test
    fun theScanFindsAKnownOffender() {
        val planted =
            """
            @Composable
            fun Planted(
                title: String,
                modifier: Modifier = Modifier,
            ) {
                Text(text = title)
            }
            """.trimIndent()

        assertTrue(
            unusedModifierParameters(planted) == listOf("Planted"),
            "The scan has to be able to see an unused modifier parameter at all",
        )
    }

    @Test
    fun theScanAcceptsAModifierThatIsPassedOn() {
        val forwarded =
            """
            @Composable
            fun Forwarded(
                title: String,
                modifier: Modifier = Modifier,
            ) {
                Text(text = title, modifier = modifier)
            }

            @Composable
            fun Expression(modifier: Modifier = Modifier) = Text(text = "x", modifier = modifier)
            """.trimIndent()

        assertTrue(
            unusedModifierParameters(forwarded).isEmpty(),
            "A forwarded modifier is the correct shape, in both block and expression bodies",
        )
    }
}

/** Every Gradle module's own `os.kei` main sources. */
private fun sourceRoots(root: File): List<File> =
    root
        .listFiles()
        .orEmpty()
        .filter { module -> module.isDirectory }
        .map { module -> File(module, "src/main/java/os/kei") }
        .filter { source -> source.isDirectory }
        .sortedBy { source -> source.path }

/** The tree had 21 when this landed; well under that means discovery broke, not that modules went away. */
private const val MINIMUM_SOURCE_ROOTS = 15

private val DECLARATION =
    Regex("""(?m)^\s*(?:(?:private|internal|public|abstract|open|override)\s+)*fun\s+([A-Za-z0-9_]+)\s*\(""")

private val MODIFIER_PARAMETER = Regex("""\bmodifier\s*:\s*Modifier\b""")

private val MODIFIER_REFERENCE = Regex("""\bmodifier\b""")

/**
 * The functions in [source] that take a `modifier` and never mention it again.
 *
 * Brace matching rather than a line-based heuristic, because the parameter list and the body both nest,
 * and an expression body has no braces to match at all — that case takes the text up to the next
 * declaration instead.
 */
private fun unusedModifierParameters(source: String): List<String> {
    val declarations = DECLARATION.findAll(source).toList()
    return declarations.mapIndexedNotNull { index, declaration ->
        val name = declaration.groupValues[1]
        val parameterStart = declaration.range.last
        val parameterEnd = matchingIndex(source, parameterStart, '(', ')') ?: return@mapIndexedNotNull null
        val parameters = source.substring(parameterStart, parameterEnd + 1)
        if (!MODIFIER_PARAMETER.containsMatchIn(parameters)) return@mapIndexedNotNull null

        val nextDeclaration = declarations.getOrNull(index + 1)?.range?.first ?: source.length
        val body = functionBody(source, parameterEnd, nextDeclaration) ?: return@mapIndexedNotNull null
        name.takeUnless { MODIFIER_REFERENCE.containsMatchIn(body) }
    }
}

/**
 * The body that follows a parameter list, block or expression.
 *
 * A `{` before any `=` is a block body and is brace matched. An `=` first is an expression body, whose
 * extent is whatever precedes the next declaration.
 */
private fun functionBody(
    source: String,
    parameterEnd: Int,
    nextDeclaration: Int,
): String? {
    val gap = source.substring(parameterEnd + 1, minOf(nextDeclaration, source.length))
    val braceOffset = gap.indexOf('{')
    val assignOffset = gap.indexOf('=')
    val isBlockBody = braceOffset != -1 && (assignOffset == -1 || braceOffset < assignOffset)
    if (!isBlockBody) {
        return if (assignOffset == -1) null else gap.substring(assignOffset)
    }
    val bodyStart = parameterEnd + 1 + braceOffset
    val bodyEnd = matchingIndex(source, bodyStart, '{', '}') ?: return null
    return source.substring(bodyStart, bodyEnd + 1)
}

/** The index that closes the [open] at [start], or null when the source is unbalanced. */
private fun matchingIndex(
    source: String,
    start: Int,
    open: Char,
    close: Char,
): Int? {
    var depth = 0
    var index = start
    while (index < source.length) {
        when (source[index]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return null
}

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val root =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
    return requireNotNull(root) { "Unable to locate the repository root from $workingDirectory" }
}
