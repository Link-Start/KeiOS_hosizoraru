package os.kei.feature.github.model

internal fun GitHubActionsArtifactMatch.supportsManagedApkInstall(lookupConfig: GitHubLookupConfig): Boolean =
    lookupConfig.appManagedShareInstallEnabled &&
        traits.kind == GitHubActionsArtifactKind.AndroidPackage
