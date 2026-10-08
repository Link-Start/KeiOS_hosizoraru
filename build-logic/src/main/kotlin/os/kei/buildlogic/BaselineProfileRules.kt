package os.kei.buildlogic

import org.gradle.api.Project

/** Diagnostics count the accepted release source rules, independently of a device capture. */
fun Project.countGeneratedProfileRules(fileName: String): Int {
    val profileFile = layout.projectDirectory.file("src/release/generated/baselineProfiles/$fileName").asFile
    if (!profileFile.isFile) return 0
    return profileFile.useLines { lines ->
        lines.count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#")
        }
    }
}
