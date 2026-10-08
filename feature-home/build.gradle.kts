plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.feature.home"

}

dependencies {
    implementation(project(":core-prefs"))
    implementation(project(":feature-ba"))
    implementation(project(":feature-mcp"))
    implementation(project(":feature-github"))

    implementation(libs.androidx.compose.runtime)
    implementation(libs.mmkv)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
