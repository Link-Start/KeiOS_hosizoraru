import com.android.build.api.dsl.CommonExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import os.kei.buildlogic.libs
import os.kei.buildlogic.version

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

fun configureAndroidCompose() {
    extensions.configure<CommonExtension>("android") {
        buildFeatures.compose = true
        compileSdkMinor = 0
    }
    extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(project.libs.version("java")))
    }
}

pluginManager.withPlugin("com.android.application") { configureAndroidCompose() }
pluginManager.withPlugin("com.android.library") { configureAndroidCompose() }

extensions.configure<ComposeCompilerGradlePluginExtension> {
    if (providers.gradleProperty("composeCompilerReports").orNull == "true") {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}
