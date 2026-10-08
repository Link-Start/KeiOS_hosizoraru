plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.ui.pip"

}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
