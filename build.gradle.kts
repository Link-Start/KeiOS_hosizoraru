import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}

/**
 * The release-like build types `:app` has that a library module does not get for free.
 *
 * `:app` declares `release`, `nonMinifiedRelease`, `benchmark`, `benchmarkRelease` and
 * `releaseDiagnostic` itself, and the baseline profile plugin then *derives* a `benchmark…` and a
 * `nonMinified…` variant from each release build type — which is where the two `…Diagnostic` names
 * below come from. Nothing declares them in a library, so every consumed module has to be given the
 * same name or the pairing is unmatched.
 *
 * Gradle alone would survive that: `assembleBenchmarkReleaseDiagnostic` resolves through
 * `matchingFallbacks` and picks up each library's `release`, which a `--dry-run` confirms. Android
 * Studio's Project Structure model is stricter and checks for the *name*, so it reported 36 errors
 * across the modules while the command line was green — this is a real gap in the project model, not a
 * broken build, and that is why it went unnoticed until the IDE said so.
 *
 * Declaring the names here satisfies both readings, and matches how `benchmarkRelease` was already
 * handled. Keep this list in step with `:app`'s `buildTypes` block: anything release-like added there,
 * including whatever the baseline profile plugin derives from it, needs its name here too.
 *
 * One consequence to keep in view. Completing the pairing also completes the profile-generation wiring,
 * so the aggregate `:app:generateBaselineProfile` now walks every journey once per profileable variant —
 * `release` and `releaseDiagnostic` both, for an identical result, at roughly half an hour a pass. The
 * capture command is therefore `:app:generateReleaseBaselineProfile`, which is what
 * `scripts/qa/baseline_profile_freshness.sh` prints; the bare aggregate is the one to avoid.
 */
val consumedReleaseLikeBuildTypes =
    listOf(
        "benchmarkRelease",
        "releaseDiagnostic",
        "benchmarkReleaseDiagnostic",
        "nonMinifiedReleaseDiagnostic",
    )

/**
 * The same pairing, for the profile generator `:app` consumes through `baselineProfile(project(...))`.
 *
 * `:baselineprofile` is a `com.android.test` module, and there the plugin works the other way round: it
 * takes each build type and *replaces* it with a `benchmark…` and a `nonMinified…` variant, which is why
 * the module shows `benchmarkRelease` and `nonMinifiedRelease` but no plain `release` at all. So the
 * build type to add is the **base** `releaseDiagnostic`, and the two variants `:app` is looking for come
 * out of it. Adding the prefixed names here instead produces `benchmarkBenchmarkReleaseDiagnostic` —
 * the prefix applied twice — which is what the first attempt at this did.
 */
private val diagnosticTestBuildType = "releaseDiagnostic"

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            buildTypes {
                consumedReleaseLikeBuildTypes.forEach { buildTypeName ->
                    maybeCreate(buildTypeName).apply {
                        initWith(getByName("release"))
                        matchingFallbacks += listOf("release")
                    }
                }
            }
        }
    }

    plugins.withId("com.android.test") {
        extensions.configure<TestExtension>("android") {
            buildTypes {
                maybeCreate(diagnosticTestBuildType).apply {
                    matchingFallbacks += listOf("release")
                }
            }
        }
    }
}
