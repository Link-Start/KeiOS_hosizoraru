package os.kei.ui.page.main.github.share

internal fun ShareImportCoordinatorResult.toShareImportPhase(): GitHubShareImportPhase {
    return when (this) {
        ShareImportCoordinatorResult.None -> GitHubShareImportPhase.Idle
        is ShareImportCoordinatorResult.AssetReady -> GitHubShareImportPhase.AssetReady
        is ShareImportCoordinatorResult.Pending -> GitHubShareImportPhase.WaitingInstall
        is ShareImportCoordinatorResult.Detected -> GitHubShareImportPhase.InstallDetected
        is ShareImportCoordinatorResult.Added -> GitHubShareImportPhase.Added
        is ShareImportCoordinatorResult.AlreadyTracked -> GitHubShareImportPhase.Added
        is ShareImportCoordinatorResult.Failed -> GitHubShareImportPhase.Failed
        is ShareImportCoordinatorResult.Cancelled -> GitHubShareImportPhase.Idle
    }
}
