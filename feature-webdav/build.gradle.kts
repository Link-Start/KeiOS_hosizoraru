plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "os.kei.feature.webdav"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-log"))
    implementation(project(":core-io"))
    implementation(project(":core-prefs"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.encoding)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.ktor.client.mock)

    // WebDAV client (used by DAVx⁵, production-grade).
    // A tagged release (JitPack builds the tag). 4.x is the Ktor API with suspend/Flow multi-status
    // handling; an upgrade across a major version needs a matching WebDavSyncClient migration.
    // Exclude xpp3 — Android has built-in XmlPullParser.
    implementation(libs.dav4jvm) {
        exclude(group = "org.ogce", module = "xpp3")
    }
}
