package os.kei.i18n

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals

class LocalizedStringParityTest {
    @Test
    fun everyNonBaStringHasAllSupportedLocalesAndMatchingFormatArguments() {
        val projectRoot = locateProjectRoot()

        translatableModules(projectRoot).forEach { module ->
            val contractsByLocale = SUPPORTED_RESOURCE_DIRECTORIES.associateWith { directory ->
                readContracts(File(module, "src/main/res/$directory"))
            }
            val baseline = contractsByLocale.getValue(DEFAULT_RESOURCE_DIRECTORY)

            SUPPORTED_RESOURCE_DIRECTORIES.drop(1).forEach { directory ->
                val localized = contractsByLocale.getValue(directory)
                assertEquals(
                    baseline.keys,
                    localized.keys,
                    keyMismatchMessage(module, directory, baseline.keys, localized.keys),
                )
                baseline.forEach { (name, baselineContract) ->
                    assertEquals(
                        baselineContract,
                        localized.getValue(name),
                        "${module.name}/$directory has a resource-contract mismatch for $name",
                    )
                }
            }
        }
    }
}

private data class ResourceContract(
    val type: String,
    val formatArgumentVariants: Set<List<String>>,
)

private fun translatableModules(projectRoot: File): List<File> =
    projectRoot.listFiles().orEmpty()
        .filter(File::isDirectory)
        .filterNot { module -> module.name == "feature-ba" }
        .filter { module ->
            File(module, "src/main/res/$DEFAULT_RESOURCE_DIRECTORY")
                .listFiles()
                .orEmpty()
                .any(::isIncludedStringResourceFile)
        }
        .sortedBy(File::getName)

private fun readContracts(resourceDirectory: File): Map<String, ResourceContract> {
    val contracts = linkedMapOf<String, ResourceContract>()
    resourceDirectory.listFiles()
        .orEmpty()
        .filter(::isIncludedStringResourceFile)
        .sortedBy(File::getName)
        .forEach { resourceFile ->
            val root = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder().parse(resourceFile).documentElement
            for (index in 0 until root.childNodes.length) {
                val element = root.childNodes.item(index) as? Element ?: continue
                val name = element.getAttribute("name")
                if (element.tagName !in SUPPORTED_RESOURCE_TYPES || element.isExcludedBaResource(name)) {
                    continue
                }
                if (element.getAttribute("translatable") == "false") continue

                val previous = contracts.put(name, element.toContract())
                check(previous == null) { "${resourceFile.path} defines $name more than once" }
            }
        }
    return contracts
}

private fun Element.toContract(): ResourceContract {
    val formatted = getAttribute("formatted") != "false"
    val argumentVariants: Set<List<String>> = when (tagName) {
        "string" -> setOf(textContent.formatArguments(formatted))
        "plurals" -> buildSet {
            for (index in 0 until childNodes.length) {
                val item = childNodes.item(index) as? Element ?: continue
                if (item.tagName == "item") {
                    add(item.textContent.formatArguments(formatted))
                }
            }
        }
        else -> error("Unsupported resource type $tagName")
    }
    return ResourceContract(type = tagName, formatArgumentVariants = argumentVariants)
}

private fun String.formatArguments(formatted: Boolean): List<String> {
    if (!formatted) return emptyList()
    return FORMAT_ARGUMENT_REGEX.findAll(replace("%%", ""))
        .map(MatchResult::value)
        .sorted()
        .toList()
}

private fun Element.isExcludedBaResource(name: String): Boolean =
    name.startsWith("ba_") || "_ba_" in name

private fun isIncludedStringResourceFile(file: File): Boolean =
    file.isFile && file.name.startsWith("strings") && file.extension == "xml" &&
        file.name != "strings_ba.xml"

private fun keyMismatchMessage(
    module: File,
    directory: String,
    baselineKeys: Set<String>,
    localizedKeys: Set<String>,
): String = buildString {
    append("${module.name}/$directory must match the default non-BA resource keys")
    val missing = (baselineKeys - localizedKeys).sorted()
    val extra = (localizedKeys - baselineKeys).sorted()
    if (missing.isNotEmpty()) append("\nMissing: ${missing.joinToString()}")
    if (extra.isNotEmpty()) append("\nExtra: ${extra.joinToString()}")
}

private fun locateProjectRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile },
    ) {
        "Unable to locate the KeiOS project root from $workingDirectory"
    }
}

private val DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
}
private val FORMAT_ARGUMENT_REGEX =
    Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")
private const val DEFAULT_RESOURCE_DIRECTORY = "values"
private val SUPPORTED_RESOURCE_DIRECTORIES =
    listOf(DEFAULT_RESOURCE_DIRECTORY, "values-zh-rCN", "values-en", "values-ja")
private val SUPPORTED_RESOURCE_TYPES = setOf("string", "plurals")
