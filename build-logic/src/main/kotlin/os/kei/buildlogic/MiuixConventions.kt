package os.kei.buildlogic

import org.gradle.api.Project

/** Preserve CLI/user/project Gradle properties > local.properties > version catalog precedence. */
fun Project.miuixVersion(): String =
    readGradleOrLocalPropertyOrNull("miuix.version") ?: libs.version("miuix")
