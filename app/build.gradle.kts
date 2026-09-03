import com.android.build.api.variant.BuildConfigField
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

fun maxSemVer(
    first: AppSemVer,
    second: AppSemVer,
): AppSemVer =
    when {
        first.major != second.major -> if (first.major > second.major) first else second
        first.minor != second.minor -> if (first.minor > second.minor) first else second
        first.patch >= second.patch -> first
        else -> second
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

abstract class BuildTimestampValueSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}

val fallbackReleaseVersion = AppSemVer(major = 1, minor = 11, patch = 0)
val configuredReleaseVersion =
    parseSemVerTagOrNull(readGradleEnvOrLocalPropertyOrNull("keios.version.name", "KEIOS_VERSION_NAME"))
val configuredVersionAnchorTag =
    readGradleEnvOrLocalPropertyOrNull("keios.version.anchorTag", "KEIOS_VERSION_ANCHOR_TAG")
val discoveredVersionAnchorTag = configuredVersionAnchorTag ?: latestMergedSemVerTagOrNull()
val discoveredReleaseVersion = parseSemVerTagOrNull(discoveredVersionAnchorTag)
val releaseVersion =
    configuredReleaseVersion
        ?: discoveredReleaseVersion?.let { maxSemVer(it, fallbackReleaseVersion) }
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
        ?: libs.versions.miuix.get()
val coreKtxVersion = libs.versions.androidx.core.get()
val activityComposeVersion = libs.versions.activity.compose.get()
val materialVersion = libs.versions.material.get()
val composeVersion = libs.versions.compose.get()
val constraintLayoutComposeVersion = libs.versions.constraintlayout.compose.get()
val navigationEventVersion = libs.versions.navigation.event.get()
val backdropVersion = libs.versions.backdrop.get()
val capsuleVersion = libs.versions.capsule.get()
val shapesVersion = libs.versions.shapes.get()
val releaseSigningStoreFile = readGradleOrLocalPropertyOrNull("keios.release.storeFile")?.trim().orEmpty()
val releaseSigningStorePassword = readGradleOrLocalPropertyOrNull("keios.release.storePassword")?.trim().orEmpty()
val releaseSigningKeyAlias = readGradleOrLocalPropertyOrNull("keios.release.keyAlias")?.trim().orEmpty()
val releaseSigningKeyPassword = readGradleOrLocalPropertyOrNull("keios.release.keyPassword")?.trim().orEmpty()
val releaseSigningConfigured =
    releaseSigningStoreFile.isNotBlank() &&
        releaseSigningStorePassword.isNotBlank() &&
        releaseSigningKeyAlias.isNotBlank() &&
        releaseSigningKeyPassword.isNotBlank()
val shizukuVersion = libs.versions.shizuku.get()
val hiddenApiBypassVersion = libs.versions.hidden.api.bypass.get()
val mmkvVersion = libs.versions.mmkv.get()
val mcpKotlinSdkVersion = libs.versions.mcp.kotlin.sdk.get()
val ktorVersion = libs.versions.ktor.get()
val okhttpVersion = libs.versions.okhttp.get()
val media3Version = libs.versions.media3.get()
val dav4jvmVersion = libs.versions.dav4jvm.get()
val coil3Version = libs.versions.coil3.get()
val zoomImageVersion = libs.versions.zoomimage.get()
val lucideIconsVersion = libs.versions.lucide.icons.get()
val documentFileVersion = libs.versions.documentfile.get()
val uCropVersion = libs.versions.ucrop.get()
val focusApiVersion = libs.versions.focus.api.get()
val metricsPerformanceVersion = libs.versions.metrics.performance.get()
val profileInstallerVersion = libs.versions.profileinstaller.get()
val lifecycleViewModelComposeVersion = libs.versions.lifecycle.get()
val projectCompileSdk = libs.versions.compile.sdk.get().toInt()
val projectMinSdk = libs.versions.min.sdk.get().toInt()
val projectTargetSdk = libs.versions.target.sdk.get().toInt()
val projectGradleVersion = gradle.gradleVersion
val projectJavaVersion = JavaVersion.toVersion(libs.versions.java.get())
val projectJvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
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
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        buildConfigField("String", "MIUIX_NAV_VERSION", "\"miuix-nav $miuixVersion\"")
        buildConfigField("String", "COMPOSE_VERSION", "\"$composeVersion\"")
        buildConfigField("String", "CONSTRAINT_LAYOUT_COMPOSE_VERSION", "\"$constraintLayoutComposeVersion\"")
        buildConfigField("String", "NAVIGATION_EVENT_VERSION", "\"$navigationEventVersion\"")
        buildConfigField("String", "BACKDROP_VERSION", "\"$backdropVersion\"")
        buildConfigField("String", "CAPSULE_VERSION", "\"$capsuleVersion\"")
        buildConfigField("String", "SHAPES_VERSION", "\"$shapesVersion\"")
        buildConfigField("String", "HIDDENAPI_BYPASS_VERSION", "\"$hiddenApiBypassVersion\"")
        buildConfigField("String", "MMKV_VERSION", "\"$mmkvVersion\"")
        buildConfigField("String", "MCP_KOTLIN_SDK_VERSION", "\"$mcpKotlinSdkVersion\"")
        buildConfigField("String", "KTOR_VERSION", "\"$ktorVersion\"")
        buildConfigField("String", "OKHTTP_VERSION", "\"$okhttpVersion\"")
        buildConfigField("String", "MEDIA3_VERSION", "\"$media3Version\"")
        buildConfigField("String", "DAV4JVM_VERSION", "\"$dav4jvmVersion\"")
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
            // Additive: AGP's own optimized defaults still arrive through optimization.enable, verified
            // by diffing R8's configuration.txt across this change.
            proguardFile("proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"off\"")
        }

        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            optimization.enable = false
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
            isProfileable = true
            enableAndroidTestCoverage = false
            enableUnitTestCoverage = false
            signingConfig =
                if (releaseSigningConfigured) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            matchingFallbacks += listOf("release")
            buildConfigField("String", "DEFAULT_LOG_LEVEL_ID", "\"off\"")
        }

        /**
         * A release build that installs *beside* the real one, for A/B measurement.
         *
         * Frame-time work means building the same app with one thing changed and comparing. Doing
         * that by overwriting `os.kei` means the device only ever holds one of the two, the previous
         * build has to be rebuilt to go back, and — the part that actually caused trouble — a
         * diagnostic with the glass switched off can be left sitting on the device looking like a
         * shipped regression.
         *
         * `.diag` keeps both installed at once, and `src/releaseDiagnostic/res` overrides the launcher
         * label so they are told apart at a glance — a `resValue` would collide with the `app_name`
         * that `src/main` already declares. Identical to release otherwise, R8 included, so the numbers are comparable:
         * a diagnostic that optimises differently from release measures the wrong app.
         */
        create("releaseDiagnostic") {
            initWith(getByName("release"))
            applicationIdSuffix = ".diag"
            versionNameSuffix = "-diag"
            signingConfig =
                if (releaseSigningConfigured) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            matchingFallbacks += listOf("release")
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

        maybeCreate("benchmarkRelease").apply {
            initWith(getByName("benchmark"))
            matchingFallbacks.clear()
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
    beforeVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.isMinifyEnabled = false
        variant.shrinkResources = false
    }
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
        // Generated startup profiles include D8/R8 synthetic lambda rules that R8 reports as
        // missing before minification. Keep ART baseline profiles enabled and skip dex layout
        // optimization so release-like builds stay quiet and deterministic.
        variant.experimentalProperties.put(r8DexStartupOptimizationProperty, false)
    }
    onVariants(selector().withBuildType("benchmark")) { variant ->
        variant.sources.baselineProfiles?.addStaticSourceDirectory("src/release/generated/baselineProfiles")
    }
    // The diagnostic build has to carry the same ART profile as release, or it is not the app being
    // measured. Without this it looked for `src/releaseDiagnostic/generated`, found nothing, and
    // shipped unprofiled — which measured the BA page ~9ms of RenderThread slower than release built
    // from the identical source.
    onVariants(selector().withBuildType("releaseDiagnostic")) { variant ->
        variant.sources.baselineProfiles?.addStaticSourceDirectory("src/release/generated/baselineProfiles")
    }
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
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
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
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
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
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

composeCompiler {
    val reportsEnabled = providers.gradleProperty("composeCompilerReports").orNull == "true"
    if (reportsEnabled) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
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
        substitute(module("top.yukonga.miuix.kmp:miuix-nav"))
            .using(module("top.yukonga.miuix.kmp:miuix-nav-android:$miuixVersion"))
    }
}

dependencies {
    baselineProfile(project(":baselineprofile"))

    implementation(project(":core-concurrency"))
    implementation(project(":core-download"))
    implementation(project(":core-log"))
    implementation(project(":core-io"))
    implementation(project(":core-json"))
    implementation(project(":core-notification"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))
    implementation(project(":ui-pip"))
    implementation(project(":ui-liquid-glass"))
    implementation(project(":feature-mcp"))
    implementation(project(":feature-keepalive"))
    implementation(project(":feature-home"))
    implementation(project(":feature-os"))
    implementation(project(":feature-ba"))
    implementation(project(":feature-github"))
    implementation(project(":feature-webdav"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.google.material)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.navigation.event)
    implementation(libs.androidx.navigation.event.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:$miuixVersion")
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.capsule)
    implementation(libs.kyant.shapes)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    implementation(libs.mmkv)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.zoomimage.compose.coil3)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.lucide.icons)
    implementation(libs.ucrop)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.documentfile)

    // Keep kotlin-test aligned with the Kotlin plugin version while keeping Android Studio's model explicit.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
