package os.kei.feature.github.model

fun GitHubActionsArtifactMatch.supportsManagedApkInstall(lookupConfig: GitHubLookupConfig): Boolean =
    lookupConfig.appManagedShareInstallEnabled &&
        traits.kind == GitHubActionsArtifactKind.AndroidPackage
