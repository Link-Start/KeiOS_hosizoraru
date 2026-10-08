import os.kei.buildlogic.configureAndroid
import os.kei.buildlogic.consumedReleaseLikeBuildTypes

plugins {
    id("com.android.library")
}

configureAndroid(android)

android {
    // Named pairing also satisfies Android Studio's model; matchingFallbacks alone does not.
    buildTypes {
        consumedReleaseLikeBuildTypes.forEach { buildTypeName ->
            maybeCreate(buildTypeName).apply {
                initWith(getByName("release"))
                matchingFallbacks += listOf("release")
            }
        }
    }
}
