plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.json"

}

dependencies {
    api(libs.kotlinx.serialization.json)
}
