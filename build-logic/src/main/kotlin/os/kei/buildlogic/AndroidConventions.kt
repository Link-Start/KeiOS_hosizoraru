package os.kei.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configureEach
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = libs.version("compile-sdk").toInt()
    android.defaultConfig.minSdk = libs.version("min-sdk").toInt()
    val javaVersion = JavaVersion.toVersion(libs.version("java"))
    android.compileOptions.sourceCompatibility = javaVersion
    android.compileOptions.targetCompatibility = javaVersion

    // Robolectric API 36+ FileDescriptor interception needs SharedSecrets on the test JVM.
    tasks.withType<Test>().configureEach {
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    }
}

/** Names consumed by :app and its Baseline Profile-derived diagnostic variants. */
internal val consumedReleaseLikeBuildTypes = listOf(
    "benchmarkRelease",
    "releaseDiagnostic",
    "benchmarkReleaseDiagnostic",
    "nonMinifiedReleaseDiagnostic",
)
