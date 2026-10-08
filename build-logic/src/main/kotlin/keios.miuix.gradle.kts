import os.kei.buildlogic.miuixVersion

val miuixVersion = miuixVersion()

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        listOf("miuix-ui", "miuix-preference", "miuix-icons", "miuix-squircle", "miuix-blur", "miuix-nav")
            .forEach { moduleName ->
                substitute(module("top.yukonga.miuix.kmp:$moduleName"))
                    .using(module("top.yukonga.miuix.kmp:$moduleName-android:$miuixVersion"))
            }
    }
}
