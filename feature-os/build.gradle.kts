plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.feature.os"

}

dependencies {
    implementation(project(":feature-mcp"))
}
