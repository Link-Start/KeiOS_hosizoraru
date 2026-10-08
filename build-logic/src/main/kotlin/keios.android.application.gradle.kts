import os.kei.buildlogic.configureAndroid
import os.kei.buildlogic.libs
import os.kei.buildlogic.version

plugins {
    id("com.android.application")
}

configureAndroid(android)

android {
    defaultConfig {
        targetSdk = project.libs.version("target-sdk").toInt()
    }
}
