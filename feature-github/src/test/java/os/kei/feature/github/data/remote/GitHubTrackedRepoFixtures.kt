package os.kei.feature.github.data.remote

internal data class GitHubTrackedRepoFixture(
    val owner: String,
    val repo: String,
    val localVersion: String,
    val preferPreRelease: Boolean,
    val atomStableRawTag: String,
    val atomPreRawTag: String? = null,
    val atomHasStableRelease: Boolean = true,
    val tokenStableRawTag: String = atomStableRawTag,
    val tokenPreRawTag: String? = atomPreRawTag,
    val tokenHasStableRelease: Boolean = atomHasStableRelease,
    val notes: String = ""
) {
    val id: String = "$owner/$repo"
}

internal object GitHubTrackedRepoFixtures {
    val parityCorpus: List<GitHubTrackedRepoFixture> = listOf(
        GitHubTrackedRepoFixture(
            owner = "jay3-yy",
            repo = "BiliPai",
            localVersion = "7.5.0",
            preferPreRelease = false,
            atomStableRawTag = "7.8.0",
            atomPreRawTag = null,
            tokenStableRawTag = "7.8.0",
            tokenPreRawTag = null,
            notes = "Live Atom and API now both point at 7.8.0; previous 7.7.2 corpus value was stale"
        ),
        GitHubTrackedRepoFixture(
            owner = "bggRGjQaUbCoE",
            repo = "PiliPlus",
            localVersion = "2.0.3-634166078",
            preferPreRelease = false,
            atomStableRawTag = "2.0.3.2",
            atomPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "AChep",
            repo = "keyguard-app",
            localVersion = "2.7.0",
            preferPreRelease = false,
            atomStableRawTag = "r20260410",
            atomPreRawTag = null,
            tokenStableRawTag = "r20260410",
            tokenPreRawTag = null,
            notes = "Token strategy must trust releases/latest over newer prerelease-like date tags"
        ),
        GitHubTrackedRepoFixture(
            owner = "Itosang",
            repo = "BatteryRecorder",
            localVersion = "1.4.2-release",
            preferPreRelease = true,
            atomStableRawTag = "v1.4.4-release",
            atomPreRawTag = "v1.4.7-prerelease3"
        ),
        GitHubTrackedRepoFixture(
            owner = "FrancoGiudans",
            repo = "Capsulyric",
            localVersion = "Version.26.4.Canary_C378",
            preferPreRelease = true,
            atomStableRawTag = "Version.1.3.Fix2_C359",
            atomPreRawTag = "Version.26.4.Alpha2_C384",
            notes = "Wild numbering where prerelease train outruns stable branch"
        ),
        GitHubTrackedRepoFixture(
            owner = "LanRhyme",
            repo = "MicYou",
            localVersion = "1.2.1",
            preferPreRelease = false,
            atomStableRawTag = "v1.2.1",
            atomPreRawTag = null,
            tokenStableRawTag = "v1.2.1",
            tokenPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "YumeLira",
            repo = "YumeBox",
            localVersion = "0.5.1",
            preferPreRelease = false,
            atomStableRawTag = "v0.5.1",
            atomPreRawTag = null,
            tokenStableRawTag = "v0.5.1",
            tokenPreRawTag = null,
            notes = "Placeholder Pre-release tag must be ignored by both strategies"
        ),
        GitHubTrackedRepoFixture(
            owner = "YuKongA",
            repo = "Updater-KMP",
            localVersion = "1.6.2",
            preferPreRelease = false,
            atomStableRawTag = "v1.6.2",
            atomPreRawTag = null,
            tokenStableRawTag = "v1.6.2",
            tokenPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "amir1376",
            repo = "ab-download-manager",
            localVersion = "1.8.7",
            preferPreRelease = false,
            atomStableRawTag = "v1.8.7",
            atomPreRawTag = null,
            tokenStableRawTag = "v1.8.7",
            tokenPreRawTag = null,
            notes = "Old dev tag should not surface as prerelease when prerelease tracking is off and version is not comparable"
        ),
        GitHubTrackedRepoFixture(
            owner = "AhmetCanArslan",
            repo = "ShizuWall",
            localVersion = "4.4.1",
            preferPreRelease = false,
            atomStableRawTag = "v4.4.1",
            atomPreRawTag = null,
            tokenStableRawTag = "v4.4.1",
            tokenPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "DimensionDev",
            repo = "Flare",
            localVersion = "1.4.2",
            preferPreRelease = false,
            atomStableRawTag = "1.4.3",
            atomPreRawTag = null,
            tokenStableRawTag = "1.4.3",
            tokenPreRawTag = null,
            notes = "Stable should come from latest endpoint and prerelease should not repeat same stable version"
        ),
        GitHubTrackedRepoFixture(
            owner = "open-ani",
            repo = "animeko",
            localVersion = "5.4.0",
            preferPreRelease = true,
            atomStableRawTag = "v5.4.3",
            atomPreRawTag = "v5.4.0-beta05",
            tokenStableRawTag = "v5.4.3",
            tokenPreRawTag = "v5.4.0-beta05",
            notes = "Older beta should still be visible as latest prerelease info, while stable remains the recommended install"
        ),
        GitHubTrackedRepoFixture(
            owner = "anilbeesetti",
            repo = "nextplayer",
            localVersion = "0.16.2",
            preferPreRelease = false,
            atomStableRawTag = "v0.16.3",
            atomPreRawTag = null,
            tokenStableRawTag = "v0.16.3",
            tokenPreRawTag = null,
            notes = "Ancient rc must not survive as current prerelease"
        ),
        GitHubTrackedRepoFixture(
            owner = "mihonapp",
            repo = "mihon",
            localVersion = "0.19.7",
            preferPreRelease = false,
            atomStableRawTag = "v0.19.9",
            atomPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "Notsfsssf",
            repo = "pixez-flutter",
            localVersion = "0.9.100 ttl",
            preferPreRelease = false,
            atomStableRawTag = "0.9.100",
            atomPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "CeuiLiSA",
            repo = "Pixiv-Shaft",
            localVersion = "5.0.21",
            preferPreRelease = false,
            atomStableRawTag = "v4.5.3",
            atomPreRawTag = null,
            tokenStableRawTag = "v4.5.3",
            tokenPreRawTag = null,
            notes = "Live Atom and latest API agree on v4.5.3; non-app sakura model release must not hijack stable selection"
        ),
        GitHubTrackedRepoFixture(
            owner = "rikkahub",
            repo = "rikkahub",
            localVersion = "2.1.7",
            preferPreRelease = false,
            atomStableRawTag = "2.1.7",
            atomPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "T8RIN",
            repo = "ImageToolbox",
            localVersion = "3.8.0-rc04",
            preferPreRelease = true,
            atomStableRawTag = "3.8.0",
            atomPreRawTag = "3.8.0-rc04",
            tokenStableRawTag = "3.8.0",
            tokenPreRawTag = "3.8.0-rc04",
            notes = "Stable 3.8.0 supersedes rc04 for recommendation, but rc04 should still remain visible as latest prerelease info"
        ),
        GitHubTrackedRepoFixture(
            owner = "JanYoStudio",
            repo = "WhatAnime",
            localVersion = "1.8.8.r471.cff36155",
            preferPreRelease = false,
            atomStableRawTag = "1.9.0.r488.d07a3e1b",
            atomPreRawTag = "1.9.0.n488.nightly"
        ),
        GitHubTrackedRepoFixture(
            owner = "venera-app",
            repo = "venera",
            localVersion = "1.6.3",
            preferPreRelease = false,
            atomStableRawTag = "v1.6.3",
            atomPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "badnng",
            repo = "Hyper-pick-up-code",
            localVersion = "26.4.3.C01",
            preferPreRelease = true,
            atomStableRawTag = "v26.4.3.C01",
            atomPreRawTag = "v26.4.9.C01-Dev"
        ),
        GitHubTrackedRepoFixture(
            owner = "Moriafly",
            repo = "SaltPlayerSource",
            localVersion = "11.2.0-alpha01",
            preferPreRelease = false,
            atomStableRawTag = "11.1.0",
            atomPreRawTag = null,
            tokenStableRawTag = "11.1.0",
            tokenPreRawTag = null,
            notes = "Older alpha branch should not surface as latest prerelease once local stable line moved on"
        ),
        GitHubTrackedRepoFixture(
            owner = "NEORUAA",
            repo = "WeType_UI_Enhanced",
            localVersion = "1.22",
            preferPreRelease = false,
            atomStableRawTag = "1.22",
            atomPreRawTag = null,
            tokenStableRawTag = "1.22",
            tokenPreRawTag = null
        ),
        GitHubTrackedRepoFixture(
            owner = "monogram-android",
            repo = "monogram",
            localVersion = "unknown",
            preferPreRelease = true,
            atomStableRawTag = "0.0.8",
            atomPreRawTag = "0.0.8",
            atomHasStableRelease = false,
            tokenStableRawTag = "0.0.8",
            tokenPreRawTag = "0.0.8",
            tokenHasStableRelease = false,
            notes = "Repository currently ships prerelease-only tags; strategies should keep the effective latest signal internally while exposing the newest build as prerelease-only"
        )
    )
}
