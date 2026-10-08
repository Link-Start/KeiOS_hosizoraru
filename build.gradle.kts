// Module conventions are applied explicitly from the build-logic included build.
// Keep external plugin versions on one parent classpath: Baseline Profile references AGP classes
// when it is applied, before a convention plugin's child classpath can supply them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}
