plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.versioning"

}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
