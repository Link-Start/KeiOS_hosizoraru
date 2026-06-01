import com.android.build.api.variant.BuildConfigField
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties
import kotlin.math.sqrt

data class AppSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val name: String = "$major.$minor.$patch"

    fun toVersionCode(commitCount: Int): Int =
        (major * 10_000_000) +
            (minor * 100_000) +
            (patch * 1_000) +
            commitCount.coerceIn(0, 999)
}

fun parseSemVerTagOrNull(raw: String?): AppSemVer? {
    val normalized = raw?.trim().orEmpty()
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(normalized) ?: return null
    val (major, minor, patch) = match.destructured
    return AppSemVer(
        major = major.toInt(),
        minor = minor.toInt(),
        patch = patch.toInt(),
    )
}

data class GitVersionSnapshot(
    val relativeCommitCount: Int,
    val totalCommitCount: Int,
    val shortHash: String,
    val branchName: String,
    val worktreeDirty: Boolean,
    val gitAvailable: Boolean,
)

fun runGitCommandOrNull(vararg args: String): String? =
    runCatching {
        val output =
            providers.exec {
                commandLine("git", *args)
                workingDir = rootDir
                isIgnoreExitValue = true
            }
        val exitCode = output.result.get().exitValue
        val stdout = output.standardOutput.asText.get().trim()
        stdout.takeIf { exitCode == 0 && it.isNotEmpty() }
    }.getOrNull()

fun latestMergedSemVerTagOrNull(): String? =
    runGitCommandOrNull("tag", "--merged", "HEAD", "--sort=-v:refname")
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { parseSemVerTagOrNull(it) != null }

fun gitRelativeCommitCountOrNull(anchorTag: String): Int? =
    runGitCommandOrNull("rev-list", "--count", "$anchorTag..HEAD")?.toIntOrNull()

fun gitTotalCommitCountOrNull(): Int? =
    runGitCommandOrNull("rev-list", "--count", "HEAD")?.toIntOrNull()

fun readLocalPropertyOrNull(key: String): String? {
    val localPropsFile = rootProject.file("local.properties")
    if (!localPropsFile.exists()) return null
    return runCatching {
        val props = Properties()
        localPropsFile.inputStream().use(props::load)
        props.getProperty(key)
    }.getOrNull()
}

fun readGradleOrLocalPropertyOrNull(key: String): String? =
    providers.gradleProperty(key).orNull
        ?: readLocalPropertyOrNull(key)

fun readGradleEnvOrLocalPropertyOrNull(
    key: String,
    envKey: String,
): String? =
    providers.gradleProperty(key).orNull
        ?: providers.environmentVariable(envKey).orNull
        ?: readLocalPropertyOrNull(key)

fun readBooleanPropertyOrNull(key: String): Boolean? =
    providers.gradleProperty(key).orNull?.toBooleanStrictOrNull()
        ?: readLocalPropertyOrNull(key)?.toBooleanStrictOrNull()

fun readBooleanBuildPropertyOrNull(
    key: String,
    envKey: String,
): Boolean? =
    providers.gradleProperty(key).orNull?.toBooleanStrictOrNull()
        ?: providers.environmentVariable(envKey).orNull?.toBooleanStrictOrNull()
        ?: readLocalPropertyOrNull(key)?.toBooleanStrictOrNull()

fun readIntBuildPropertyOrNull(
    key: String,
    envKey: String,
): Int? =
    readGradleEnvOrLocalPropertyOrNull(key, envKey)
        ?.trim()
        ?.toIntOrNull()

fun normalizeGitLabel(
    value: String?,
    fallback: String,
): String =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.replace(Regex("""[^A-Za-z0-9._-]"""), "-")
        ?: fallback

fun normalizeGitHash(value: String?): String = normalizeGitLabel(value, fallback = "local").take(12)

abstract class BakeAppSquircleSdfTask : DefaultTask() {
    @get:Input
    abstract val control: Property<Float>

    @get:Input
    abstract val size: Property<Int>

    @get:Input
    abstract val halfRange: Property<Float>

    @get:Input
    abstract val bezierSamples: Property<Int>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bake() {
        val resolvedSize = size.get()
        val resolvedControl = control.get()
        val resolvedHalfRange = halfRange.get()
        val resolvedBezierSamples = bezierSamples.get()
        val resolvedPackage = packageName.get()
        val bytes =
            generateSdfBytes(
                size = resolvedSize,
                control = resolvedControl,
                halfRange = resolvedHalfRange,
                bezierSamples = resolvedBezierSamples,
            )
        val chunks = Base64.getEncoder().encodeToString(bytes).chunked(60_000)
        val packageDir = outputDir.get().asFile.resolve(resolvedPackage.replace('.', '/'))
        packageDir.mkdirs()
        packageDir.resolve("BakedAppSquircleSdf.kt").writeText(
            buildString {
                appendLine("// Auto-generated by :app:bakeAppSquircleSdf.")
                appendLine("@file:Suppress(\"PropertyName\")")
                appendLine()
                appendLine("package $resolvedPackage")
                appendLine()
                appendLine("import java.util.Base64")
                appendLine()
                appendLine("internal object BakedAppSquircleSdf {")
                appendLine("    const val CONTROL: Float = ${resolvedControl}f")
                appendLine("    const val SIZE: Int = $resolvedSize")
                appendLine("    const val HALF_RANGE: Float = ${resolvedHalfRange}f")
                appendLine("    private val CHUNKS: Array<String> = arrayOf(")
                chunks.forEach { chunk ->
                    appendLine("        \"$chunk\",")
                }
                appendLine("    )")
                appendLine("    val bytes: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) {")
                appendLine("        Base64.getDecoder().decode(CHUNKS.joinToString(\"\"))")
                appendLine("    }")
                appendLine("}")
            },
        )
    }

    private fun generateSdfBytes(
        size: Int,
        control: Float,
        halfRange: Float,
        bezierSamples: Int,
    ): ByteArray {
        val handle = 1f - control
        val bx = FloatArray(bezierSamples + 1)
        val by = FloatArray(bezierSamples + 1)
        for (index in 0..bezierSamples) {
            val t = index.toFloat() / bezierSamples
            val omt = 1f - t
            bx[index] = 3f * omt * t * t * handle + t * t * t
            by[index] = omt * omt * omt + 3f * omt * omt * t * handle
        }
        val bytes = ByteArray(size * size)
        val invSize = 1f / size
        val invRange2 = 1f / (2f * halfRange)
        for (py in 0 until size) {
            val y = (py + 0.5f) * invSize
            for (px in 0 until size) {
                val x = (px + 0.5f) * invSize
                var minSqDist = Float.MAX_VALUE
                var closestIndex = 0
                for (index in 0 until bezierSamples) {
                    val ax = bx[index]
                    val ay = by[index]
                    val sx = bx[index + 1] - ax
                    val sy = by[index + 1] - ay
                    val len2 = sx * sx + sy * sy
                    if (len2 < 1e-12f) continue
                    val u = (((x - ax) * sx + (y - ay) * sy) / len2).coerceIn(0f, 1f)
                    val qx = ax + u * sx
                    val qy = ay + u * sy
                    val ddx = x - qx
                    val ddy = y - qy
                    val sqDist = ddx * ddx + ddy * ddy
                    if (sqDist < minSqDist) {
                        minSqDist = sqDist
                        closestIndex = index
                    }
                }
                val dist = sqrt(minSqDist)
                val tx = bx[closestIndex + 1] - bx[closestIndex]
                val ty = by[closestIndex + 1] - by[closestIndex]
                val pdx = x - bx[closestIndex]
                val pdy = y - by[closestIndex]
                val cross = tx * pdy - ty * pdx
                val signedDist = if (cross > 0f) -dist else dist
                val alpha = (0.5f - signedDist * invRange2).coerceIn(0f, 1f)
                bytes[py * size + px] = (alpha * 255f + 0.5f).toInt().toByte()
            }
        }
        return bytes
    }
}

abstract class BuildTimestampValueSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}

val fallbackReleaseVersion = AppSemVer(major = 1, minor = 8, patch = 3)
val configuredReleaseVersion =
    parseSemVerTagOrNull(readGradleEnvOrLocalPropertyOrNull("keios.version.name", "KEIOS_VERSION_NAME"))
val configuredVersionAnchorTag =
    readGradleEnvOrLocalPropertyOrNull("keios.version.anchorTag", "KEIOS_VERSION_ANCHOR_TAG")
val discoveredVersionAnchorTag = configuredVersionAnchorTag ?: latestMergedSemVerTagOrNull()
val releaseVersion =
    configuredReleaseVersion
        ?: parseSemVerTagOrNull(discoveredVersionAnchorTag)
        ?: fallbackReleaseVersion
val benchmarkVersion =
    parseSemVerTagOrNull(readGradleEnvOrLocalPropertyOrNull("keios.nextVersion.name", "KEIOS_NEXT_VERSION_NAME"))
        ?: releaseVersion.copy(patch = releaseVersion.patch + 1)
val versionAnchorTag = discoveredVersionAnchorTag ?: "v${releaseVersion.name}"
val gitShortHashValue =
    normalizeGitHash(
        readGradleEnvOrLocalPropertyOrNull("keios.git.shortHash", "KEIOS_GIT_SHORT_HASH")
            ?: runGitCommandOrNull("rev-parse", "--short", "HEAD"),
    )
val gitBranchNameValue =
    normalizeGitLabel(
        readGradleEnvOrLocalPropertyOrNull("keios.git.branchName", "KEIOS_GIT_BRANCH_NAME")
            ?: runGitCommandOrNull("rev-parse", "--abbrev-ref", "HEAD"),
        fallback = "local",
    )
val gitDirtyValue = readBooleanBuildPropertyOrNull("keios.git.worktreeDirty", "KEIOS_GIT_WORKTREE_DIRTY") ?: false
val gitRelativeCommitCount =
    readIntBuildPropertyOrNull("keios.git.relativeCommitCount", "KEIOS_GIT_RELATIVE_COMMIT_COUNT")
        ?: gitRelativeCommitCountOrNull(versionAnchorTag)
        ?: 0
val gitTotalCommitCount =
    readIntBuildPropertyOrNull("keios.git.totalCommitCount", "KEIOS_GIT_TOTAL_COMMIT_COUNT")
        ?: gitTotalCommitCountOrNull()
        ?: 0
val gitVersionSnapshot =
    GitVersionSnapshot(
        relativeCommitCount = gitRelativeCommitCount,
        totalCommitCount = gitTotalCommitCount,
        shortHash = gitShortHashValue,
        branchName = gitBranchNameValue,
        worktreeDirty = gitDirtyValue,
        gitAvailable =
            readBooleanBuildPropertyOrNull("keios.git.available", "KEIOS_GIT_AVAILABLE")
                ?: (gitTotalCommitCount > 0 || gitShortHashValue != "local"),
    )
val buildTimestampMillisOverride =
    readGradleEnvOrLocalPropertyOrNull("keios.build.timestampMillis", "KEIOS_BUILD_TIMESTAMP_MILLIS")
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
val buildTimestampMillisProvider =
    buildTimestampMillisOverride
        ?.let { providers.provider { it } }
        ?: providers.of(BuildTimestampValueSource::class.java) {}
val commitTimestampMillis: Long = run {
    val overrideMillis =
        readGradleEnvOrLocalPropertyOrNull("keios.git.commitTimestampMillis", "KEIOS_GIT_COMMIT_TIMESTAMP_MILLIS")
            ?.trim()
            ?.toLongOrNull()
    if (overrideMillis != null && overrideMillis > 0L) return@run overrideMillis

    val commitMillisSec = runGitCommandOrNull("log", "-1", "--format=%ct")?.trim()?.toLongOrNull()
    if (commitMillisSec != null && commitMillisSec > 0L) return@run commitMillisSec * 1000L

    0L
}
val releaseVersionName = releaseVersion.name
val releaseVersionCode = releaseVersion.toVersionCode(commitCount = 999)
val nonReleaseVersionName =
    "${benchmarkVersion.name}+${gitVersionSnapshot.relativeCommitCount}.g${gitVersionSnapshot.shortHash}"
val preReleaseVersionCode =
    benchmarkVersion.toVersionCode(
        commitCount = gitVersionSnapshot.relativeCommitCount.coerceIn(0, 998),
    )
// Machine-local overrides should live in ~/.gradle/gradle.properties (preferred) or local.properties.
// JDK resolution itself is intentionally not hardcoded here: the project already tracks a cross-platform
// Gradle daemon JVM (JetBrains Java 21) for macOS/Windows/Linux. Use org.gradle.java.home only as a
// developer-local fallback when Android Studio or Gradle cannot auto-resolve a suitable JDK.
// Useful local-only keys include:
// - miuix.version
// - keios.release.storeFile
// - keios.release.storePassword
// - keios.release.keyAlias
// - keios.release.keyPassword
// - keios.github.liveBenchmark
// - keios.github.api.token
// - keios.github.liveTargets
// - keios.github.forceGuest
val miuixVersion =
    providers.gradleProperty("miuix.version").orNull
        ?: readLocalPropertyOrNull("miuix.version")
        ?: "0.9.1"
val coreKtxVersion = "1.18.0"
val activityComposeVersion = "1.13.0"
val materialVersion = "1.14.0"
val composeVersion = "1.11.2"
val constraintLayoutComposeVersion = "1.1.1"
val navigation3Version = "1.1.2"
val navigationEventVersion = "1.1.1"
val navigationCommonVersion = "2.9.8"
val backdropVersion = "2.0.0"
val capsuleVersion = "2.1.3"
val shapesVersion = "1.2.0"
val releaseSigningStoreFile = readGradleOrLocalPropertyOrNull("keios.release.storeFile")?.trim().orEmpty()
val releaseSigningStorePassword = readGradleOrLocalPropertyOrNull("keios.release.storePassword")?.trim().orEmpty()
val releaseSigningKeyAlias = readGradleOrLocalPropertyOrNull("keios.release.keyAlias")?.trim().orEmpty()
val releaseSigningKeyPassword = readGradleOrLocalPropertyOrNull("keios.release.keyPassword")?.trim().orEmpty()
val releaseSigningConfigured =
    releaseSigningStoreFile.isNotBlank() &&
        releaseSigningStorePassword.isNotBlank() &&
        releaseSigningKeyAlias.isNotBlank() &&
        releaseSigningKeyPassword.isNotBlank()
val shizukuVersion = "13.1.5"
val hiddenApiBypassVersion = "6.1"
val mmkvVersion = "2.4.0"
val mcpKotlinSdkVersion = "0.12.0"
val ktorVersion = "3.5.0"
val okhttpVersion = "5.3.2"
val kotlinxSerializationJsonVersion = "1.11.0"
val jsonVersion = "20251224"
val xmlPullVersion = "1.1.3.4d_b4_min"
val kxml2Version = "2.3.0"
val media3Version = "1.10.1"
val coil3Version = "3.4.0"
val zoomImageVersion = "1.4.0"
val lucideIconsVersion = "2.2.1"
val documentFileVersion = "1.1.0"
val uCropVersion = "2.2.11"
val focusApiVersion = "1.4"
val metricsPerformanceVersion = "1.0.0"
val profileInstallerVersion = "1.4.1"
val lifecycleViewModelComposeVersion = "2.10.0"
val robolectricVersion = "4.16.1"
val androidTestExtJunitVersion = "1.3.0"
val roborazziVersion = "1.63.0"
val projectCompileSdk = 37
val projectMinSdk = 35
val projectTargetSdk = 37
val projectGradleVersion = gradle.gradleVersion
val projectJavaVersion = JavaVersion.VERSION_21
val projectJvmTarget = JvmTarget.JVM_21
val r8DexStartupOptimizationProperty = "android.experimental.r8.dex-startup-optimization"

fun countGeneratedProfileRules(fileName: String): Int {
    val profileFile = layout.projectDirectory.file("src/release/generated/baselineProfiles/$fileName").asFile
    if (!profileFile.isFile) return 0
    return profileFile.useLines { lines ->
        lines.count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#")
        }
    }
}

val baselineProfileRuleCount = countGeneratedProfileRules("baseline-prof.txt")
val startupProfileRuleCount = countGeneratedProfileRules("startup-prof.txt")

plugins {
    id("com.android.application")
    id("io.github.takahirom.roborazzi")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val bakeAppSquircleSdf by tasks.registering(BakeAppSquircleSdfTask::class) {
    description = "Bake KeiOS AppSquircle SDF bytes into generated source."
    control.set(0.63f)
    size.set(256)
    halfRange.set(0.125f)
    bezierSamples.set(64)
    packageName.set("os.kei.ui.page.main.widget.shape.internal")
    outputDir.set(layout.buildDirectory.dir("generated/app-squircle-sdf"))
}

android {
    namespace = "os.kei"
    compileSdk = projectCompileSdk

    signingConfigs {
        getByName("debug") {
            storeFile = file("signing/keios-ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningStoreFile)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "os.kei"
        minSdk = projectMinSdk
        targetSdk = projectTargetSdk
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
        buildConfigField("String", "CORE_KTX_VERSION", "\"$coreKtxVersion\"")
        buildConfigField("String", "ACTIVITY_COMPOSE_VERSION", "\"$activityComposeVersion\"")
        buildConfigField("String", "MATERIAL_VERSION", "\"$materialVersion\"")
        buildConfigField("String", "MIUIX_VERSION", "\"$miuixVersion\"")
        buildConfigField("String", "COMPOSE_VERSION", "\"$composeVersion\"")
        buildConfigField("String", "CONSTRAINT_LAYOUT_COMPOSE_VERSION", "\"$constraintLayoutComposeVersion\"")
        buildConfigField("String", "NAVIGATION3_VERSION", "\"$navigation3Version\"")
        buildConfigField("String", "NAVIGATION_EVENT_VERSION", "\"$navigationEventVersion\"")
        buildConfigField("String", "NAVIGATION_COMMON_VERSION", "\"$navigationCommonVersion\"")
        buildConfigField("String", "BACKDROP_VERSION", "\"$backdropVersion\"")
        buildConfigField("String", "CAPSULE_VERSION", "\"$capsuleVersion\"")
        buildConfigField("String", "SHAPES_VERSION", "\"$shapesVersion\"")
        buildConfigField("String", "HIDDENAPI_BYPASS_VERSION", "\"$hiddenApiBypassVersion\"")
        buildConfigField("String", "MMKV_VERSION", "\"$mmkvVersion\"")
        buildConfigField("String", "MCP_KOTLIN_SDK_VERSION", "\"$mcpKotlinSdkVersion\"")
        buildConfigField("String", "KTOR_VERSION", "\"$ktorVersion\"")
        buildConfigField("String", "OKHTTP_VERSION", "\"$okhttpVersion\"")
        buildConfigField("String", "MEDIA3_VERSION", "\"$media3Version\"")
        buildConfigField("String", "ZOOMIMAGE_VERSION", "\"$zoomImageVersion\"")
        buildConfigField("String", "COIL3_VERSION", "\"$coil3Version\"")
        buildConfigField("String", "LUCIDE_ICONS_VERSION", "\"$lucideIconsVersion\"")
        buildConfigField("String", "UCROP_VERSION", "\"$uCropVersion\"")
        buildConfigField("String", "LIFECYCLE_VIEWMODEL_COMPOSE_VERSION", "\"$lifecycleViewModelComposeVersion\"")
        buildConfigField("String", "METRICS_PERFORMANCE_VERSION", "\"$metricsPerformanceVersion\"")
        buildConfigField("String", "PROFILE_INSTALLER_VERSION", "\"$profileInstallerVersion\"")
        buildConfigField("String", "DOCUMENTFILE_VERSION", "\"$documentFileVersion\"")
        buildConfigField("String", "SHIZUKU_VERSION", "\"$shizukuVersion\"")
        buildConfigField("String", "FOCUS_API_VERSION", "\"$focusApiVersion\"")
        buildConfigField("String", "GRADLE_VERSION", "\"$projectGradleVersion\"")
        buildConfigField("String", "BASE_VERSION_NAME", "\"${releaseVersion.name}\"")
        buildConfigField("String", "NEXT_VERSION_NAME", "\"${benchmarkVersion.name}\"")
        buildConfigField("String", "VERSION_ANCHOR_TAG", "\"$versionAnchorTag\"")
        buildConfigField("String", "MANIFEST_COMPONENT_PACKAGE", "\"$namespace\"")
        buildConfigField("long", "COMMIT_TIME_MILLIS", "${commitTimestampMillis}L")
        buildConfigField("int", "GIT_COMMIT_COUNT", gitVersionSnapshot.relativeCommitCount.toString())
        buildConfigField("int", "GIT_TOTAL_COMMIT_COUNT", gitVersionSnapshot.totalCommitCount.toString())
        buildConfigField("String", "GIT_SHORT_HASH", "\"${gitVersionSnapshot.shortHash}\"")
        buildConfigField("String", "GIT_BRANCH_NAME", "\"${gitVersionSnapshot.branchName}\"")
        buildConfigField("boolean", "GIT_WORKTREE_DIRTY", gitVersionSnapshot.worktreeDirty.toString())
        buildConfigField("boolean", "VERSION_GIT_AVAILABLE", gitVersionSnapshot.gitAvailable.toString())
        buildConfigField("int", "COMPILE_SDK_VERSION", projectCompileSdk.toString())
        buildConfigField("int", "MIN_SDK_VERSION", projectMinSdk.toString())
        buildConfigField("int", "TARGET_SDK_VERSION", projectTargetSdk.toString())
        buildConfigField("int", "BASELINE_PROFILE_RULE_COUNT", baselineProfileRuleCount.toString())
        buildConfigField("int", "STARTUP_PROFILE_RULE_COUNT", startupProfileRuleCount.toString())
        buildConfigField("String", "JAVA_VERSION", "\"${projectJavaVersion.majorVersion}\"")
        buildConfigField("String", "JVM_TARGET_VERSION", "\"${projectJvmTarget.target}\"")
        buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"off\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"debug\"")
        }

        release {
            optimization.enable = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"off\"")
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig =
                if (releaseSigningConfigured) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            matchingFallbacks += listOf("release")
            buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"off\"")
        }
    }

    compileOptions {
        sourceCompatibility = projectJavaVersion
        targetCompatibility = projectJavaVersion
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = true
        checkDependencies = false
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libmmkv.so"
        }
    }

    compileSdkMinor = 0

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Keep unit tests on the desktop OkHttp platform. Live GitHub tests read secrets from
            // JVM properties, env vars, or ~/.gradle/gradle.properties; see README.md.
            it.systemProperty("okhttp.platform", "jdk9")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "BUILD_TIME_MILLIS",
            buildTimestampMillisProvider.map { buildTimestampMillis ->
                BuildConfigField(
                    type = "long",
                    value = "${buildTimestampMillis}L",
                    comment = "Wall-clock timestamp captured while generating BuildConfig.",
                )
            },
        )
        variant.sources.java?.addGeneratedSourceDirectory(
            bakeAppSquircleSdf,
            BakeAppSquircleSdfTask::outputDir,
        )
        // dex-startup-optimization uses baseline profiles for dex layout optimization.
        // Keep the property explicit for release-like builds so benchmark checks the same path.
        variant.experimentalProperties.put(r8DexStartupOptimizationProperty, true)
    }
    onVariants(selector().withBuildType("benchmark")) { variant ->
        variant.sources.baselineProfiles?.addStaticSourceDirectory("src/release/generated/baselineProfiles")
    }
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(releaseVersionName)
            output.versionCode.set(releaseVersionCode)
        }
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(nonReleaseVersionName)
            output.versionCode.set(preReleaseVersionCode)
        }
    }
    onVariants(selector().withBuildType("benchmark")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(nonReleaseVersionName)
            output.versionCode.set(preReleaseVersionCode)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(projectJvmTarget)
    }
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-ui"))
            .using(module("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference"))
            .using(module("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons"))
            .using(module("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur"))
            .using(module("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion"))
        substitute(module("top.yukonga.miuix.kmp:miuix-navigation3-ui"))
            .using(module("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:$miuixVersion"))
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-log"))
    implementation(project(":core-io"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))
    implementation(project(":feature-github"))
    implementation(project(":feature-webdav"))
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation("androidx.profileinstaller:profileinstaller:$profileInstallerVersion")
    implementation("com.google.android.material:material:$materialVersion")

    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.constraintlayout:constraintlayout-compose:$constraintLayoutComposeVersion")
    implementation("androidx.navigation3:navigation3-runtime:$navigation3Version")
    implementation("androidx.navigationevent:navigationevent:$navigationEventVersion")
    implementation("androidx.navigationevent:navigationevent-compose:$navigationEventVersion")
    implementation("androidx.navigation:navigation-common-ktx:$navigationCommonVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:$miuixVersion")
    implementation("io.github.kyant0:backdrop:$backdropVersion")
    implementation("io.github.kyant0:capsule:$capsuleVersion")
    implementation("io.github.kyant0:shapes:$shapesVersion")

    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:$hiddenApiBypassVersion")
    implementation("com.tencent:mmkv:$mmkvVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpKotlinSdkVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("io.github.panpf.zoomimage:zoomimage-compose-coil3:$zoomImageVersion")
    implementation("io.coil-kt.coil3:coil-compose:$coil3Version")
    implementation("io.coil-kt.coil3:coil-gif:$coil3Version")
    implementation("com.composables:icons-lucide-android:$lucideIconsVersion")
    implementation("com.github.yalantis:ucrop:$uCropVersion")
    implementation("androidx.metrics:metrics-performance:$metricsPerformanceVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleViewModelComposeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleViewModelComposeVersion")
    implementation("androidx.documentfile:documentfile:$documentFileVersion")
    implementation("com.xzakota.hyper.notification:focus-api:$focusApiVersion")

    // Keep kotlin-test aligned with the applied Kotlin plugin version to avoid version skew.
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
    testImplementation("androidx.test.ext:junit:$androidTestExtJunitVersion")
    testImplementation("org.json:json:$jsonVersion")
    testImplementation("org.robolectric:robolectric:$robolectricVersion")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:$roborazziVersion")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazziVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("xmlpull:xmlpull:$xmlPullVersion")
    testImplementation("net.sf.kxml:kxml2:$kxml2Version")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
}
