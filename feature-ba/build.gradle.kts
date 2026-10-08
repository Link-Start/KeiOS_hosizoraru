plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.feature.ba"

}

dependencies {
    implementation(project(":feature-mcp"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
