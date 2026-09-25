package os.kei.feature.github.data.remote.fdroid

/** A three-package index-v2 document (F-Droid, PixEz, Obtainium) shared by the F-Droid index tests. */
internal object FdroidIndexV2Fixtures {
    val index: String =
        """
        {
          "repo": {
            "name": {
              "en-US": "IzzyOnDroid"
            },
            "description": {
              "en-US": "Third-party F-Droid repository"
            },
            "timestamp": 1780000000000
          },
          "packages": {
            "org.fdroid.fdroid": {
              "metadata": {
                "name": {
                  "en-US": "F-Droid"
                },
                "summary": {
                  "en-US": "App store"
                },
                "suggestedVersionCode": 1021051
              },
              "versions": {
                "org.fdroid.fdroid_1021051.apk": {
                  "manifest": {
                    "versionName": "1.21.1",
                    "versionCode": 1021051
                  },
                  "file": {
                    "name": "/repo/org.fdroid.fdroid_1021051.apk",
                    "sha256": "fdroid-sha256"
                  }
                }
              }
            },
            "com.perol.pixez": {
              "metadata": {
                "name": {
                  "en-US": "PixEz"
                },
                "summary": {
                  "en-US": "A third-party Pixiv flutter client that supports viewing ugoira"
                },
                "categories": [
                  "Graphics",
                  "Internet"
                ],
                "antiFeatures": [
                  "NonFreeNet",
                  "NonFreeComp"
                ],
                "suggestedVersionCode": 10010040
              },
              "versions": {
                "com.perol.pixez_10010040.apk": {
                  "manifest": {
                    "versionName": "0.9.104 wsv",
                    "versionCode": 10010040,
                    "usesSdk": {
                      "minSdkVersion": 24,
                      "targetSdkVersion": 35
                    }
                  },
                  "file": {
                    "name": "/repo/com.perol.pixez_10010040.apk",
                    "sha256": "pixez-sha256",
                    "size": 1234567
                  }
                }
              }
            },
            "dev.imranr.obtainium": {
              "metadata": {
                "name": {
                  "en-US": "Obtainium"
                },
                "summary": {
                  "en-US": "App updater"
                },
                "suggestedVersionCode": 200
              },
              "versions": {
                "dev.imranr.obtainium_200.apk": {
                  "manifest": {
                    "versionName": "2.0",
                    "versionCode": 200
                  },
                  "file": {
                    "name": "/repo/dev.imranr.obtainium_200.apk",
                    "sha256": "obtainium-sha256"
                  }
                }
              }
            }
          }
        }
        """.trimIndent()
}
