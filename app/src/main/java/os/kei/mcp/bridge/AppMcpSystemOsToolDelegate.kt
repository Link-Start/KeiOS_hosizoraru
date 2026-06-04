package os.kei.mcp.bridge

import android.content.Intent
import android.net.Uri
import os.kei.R
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.os.mcp.McpSystemOsToolDelegate
import os.kei.mcp.server.McpToolEnvironment
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsInfoCache
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.OsUiStateStore
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.transfer.OsCardTransferService
import java.util.Locale

internal class AppMcpSystemOsToolDelegate(
    private val environment: McpToolEnvironment
) : McpSystemOsToolDelegate {
    private data class InfoRow(
        val key: String,
        val value: String
    )

    private val appContext get() = environment.appContext

    override fun buildTopInfoText(query: String, limit: Int): String {
        val rows = readSystemTopInfoRows(maxCount = limit, query = query)
        return if (rows.isEmpty()) {
            if (query.isBlank()) {
                "TopInfo cache empty. Open System page once to build cache."
            } else {
                "No matched TopInfo rows."
            }
        } else {
            rows.joinToString("\n") { "${it.key}=${it.value}" }
        }
    }

    override fun buildOsCardsSnapshotText(): String {
        val uiSnapshot = OsUiStateStore.loadSnapshot()
        val visibleCards = uiSnapshot.visibleCards
        val activityCards = loadOsActivityCards()
        val shellCards = OsShellCommandCardStore.loadCards()
        val shellRunnerVisible = visibleCards.contains(OsSectionCard.SHELL_RUNNER)
        val parameterTotalCount = OsSectionCard.entries.count {
            it != OsSectionCard.GOOGLE_SYSTEM_SERVICE && it != OsSectionCard.SHELL_RUNNER
        }
        val parameterVisibleCount = visibleCards.count {
            it != OsSectionCard.GOOGLE_SYSTEM_SERVICE && it != OsSectionCard.SHELL_RUNNER
        }
        val activityVisibleCount = activityCards.count { it.visible }
        val shellVisibleCount = shellCards.count { it.visible } + if (shellRunnerVisible) 1 else 0
        val shellTotalCount = shellCards.size + 1
        val cachePersisted = OsInfoCache.hasPersistedCache()
        val cachedSectionCount = OsInfoCache.cachedSectionCount(visibleCards)

        return buildString {
            appendLine("visibleCards=${visibleCards.map { it.name }.sorted().joinToString(",")}")
            appendLine("parameterCards=$parameterVisibleCount/$parameterTotalCount")
            appendLine("activityCards=$activityVisibleCount/${activityCards.size}")
            appendLine("shellCards=$shellVisibleCount/$shellTotalCount")
            appendLine("shellRunnerVisible=$shellRunnerVisible")
            appendLine("topInfoExpanded=${uiSnapshot.topInfoExpanded}")
            appendLine("overviewExpanded=${OsUiStateStore.overviewExpanded(true)}")
            appendLine("systemExpanded=${uiSnapshot.systemTableExpanded}")
            appendLine("secureExpanded=${uiSnapshot.secureTableExpanded}")
            appendLine("globalExpanded=${uiSnapshot.globalTableExpanded}")
            appendLine("androidExpanded=${uiSnapshot.androidPropsExpanded}")
            appendLine("javaExpanded=${uiSnapshot.javaPropsExpanded}")
            appendLine("linuxExpanded=${uiSnapshot.linuxEnvExpanded}")
            appendLine("cachePersisted=$cachePersisted")
            appendLine("cachedSectionCount=$cachedSectionCount")
            appendLine("cacheFootprintBytes=${OsInfoCache.storageFootprintBytes()}")
            appendLine("cacheActualDataBytes=${OsInfoCache.actualDataBytes()}")
            appendLine("cacheEstimatedBytes=${OsInfoCache.cacheBytesEstimated()}")
            appendLine("uiStateFootprintBytes=${OsUiStateStore.storageFootprintBytes()}")
            appendLine("uiStateActualDataBytes=${OsUiStateStore.actualDataBytes()}")
            appendLine("uiStateEstimatedBytes=${OsUiStateStore.configBytesEstimated()}")
        }.trim()
    }

    override fun buildOsActivityCardsText(
        query: String,
        onlyVisible: Boolean,
        limit: Int
    ): String {
        val cards = loadOsActivityCards()
        val filtered = cards.filter { card ->
            (!onlyVisible || card.visible) && matchesActivityCard(card, query)
        }
        val rows = filtered.take(limit)
        return buildString {
            appendLine("total=${cards.size}")
            appendLine("visibleTotal=${cards.count { it.visible }}")
            appendLine("matched=${filtered.size}")
            appendLine("returned=${rows.size}")
            appendLine("query=${query.ifBlank { "(none)" }}")
            appendLine("onlyVisible=$onlyVisible")
            rows.forEachIndexed { index, card ->
                appendLine(
                    "activity[$index]=id:${card.id} | visible:${card.visible} | builtIn:${card.isBuiltInSample} | title:${card.config.title} | package:${card.config.packageName} | class:${card.config.className} | action:${card.config.intentAction} | extras:${card.config.intentExtras.size}"
                )
            }
        }.trim()
    }

    override fun buildOsShellCardsText(
        query: String,
        onlyVisible: Boolean,
        includeOutput: Boolean,
        limit: Int
    ): String {
        val uiSnapshot = OsUiStateStore.loadSnapshot()
        val shellRunnerTitle = appContext.getString(R.string.os_shell_card_title)
        val shellRunnerSubtitle = appContext.getString(R.string.os_shell_card_subtitle)
        val shellRunnerVisible = uiSnapshot.visibleCards.contains(OsSectionCard.SHELL_RUNNER)

        val matchesShellRunner = query.isBlank() ||
            shellRunnerTitle.contains(query, ignoreCase = true) ||
            shellRunnerSubtitle.contains(query, ignoreCase = true) ||
            "shell-runner".contains(query, ignoreCase = true)
        val includeShellRunner = (!onlyVisible || shellRunnerVisible) && matchesShellRunner

        val cards = OsShellCommandCardStore.loadCards()
        val filteredCards = cards.filter { card ->
            (!onlyVisible || card.visible) && matchesShellCard(card, query)
        }
        val rows = filteredCards.take(limit)
        val totalWithRunner = cards.size + 1
        val visibleWithRunner = cards.count { it.visible } + if (shellRunnerVisible) 1 else 0
        val matchedCount = filteredCards.size + if (includeShellRunner) 1 else 0
        val returnedCount = rows.size + if (includeShellRunner) 1 else 0

        return buildString {
            appendLine("total=$totalWithRunner")
            appendLine("visibleTotal=$visibleWithRunner")
            appendLine("matched=$matchedCount")
            appendLine("returned=$returnedCount")
            appendLine("query=${query.ifBlank { "(none)" }}")
            appendLine("onlyVisible=$onlyVisible")
            appendLine("includeOutput=$includeOutput")
            if (includeShellRunner) {
                appendLine(
                    "shell[runner]=id:shell-runner | visible:$shellRunnerVisible | builtIn:true | title:$shellRunnerTitle | subtitle:$shellRunnerSubtitle"
                )
            }
            rows.forEachIndexed { index, card ->
                val baseLine = buildString {
                    append("shell[$index]=id:${card.id} | visible:${card.visible} | builtIn:false")
                    append(" | title:${card.title} | subtitle:${card.subtitle}")
                    append(" | command:${card.command}")
                    append(" | lastRunAtMs:${card.lastRunAtMillis}")
                    append(" | updatedAtMs:${card.updatedAtMillis}")
                }
                appendLine(baseLine)
                if (includeOutput && card.runOutput.isNotBlank()) {
                    appendLine("shell[$index].output=${normalizeOutputSnippet(card.runOutput)}")
                }
            }
        }.trim()
    }

    override fun buildOsCardsExportText(target: String): String {
        val normalizedTarget = normalizeOsCardTransferTarget(target)
        val defaults = buildOsGoogleSystemDefaults()
        return when (normalizedTarget) {
            "activity" -> OsCardTransferService.buildActivityCardsExportJson(
                cards = loadOsActivityCards(),
                defaults = defaults
            )
            "shell" -> OsCardTransferService.buildShellCardsExportJson(OsShellCommandCardStore.loadCards())
            else -> OsCardTransferService.buildCardsBundleExportJson(
                activityCards = loadOsActivityCards(),
                shellCards = OsShellCommandCardStore.loadCards(),
                defaults = defaults
            )
        }
    }

    override fun buildOsCardsImportText(
        target: String,
        rawJson: String,
        apply: Boolean
    ): String {
        return when (normalizeOsCardTransferTarget(target)) {
            "activity" -> buildActivityCardsImportText(rawJson = rawJson, apply = apply)
            "shell" -> buildShellCardsImportText(rawJson = rawJson, apply = apply)
            else -> "target=unknown\nmessage=target must be activity or shell"
        }
    }

    private fun buildOsGoogleSystemDefaults(): OsGoogleSystemServiceConfig {
        val defaults = OsGoogleSystemServiceConfig(
            title = appContext.getString(R.string.os_section_google_system_service_title),
            subtitle = appContext.getString(R.string.os_google_system_service_default_subtitle),
            appName = appContext.getString(R.string.os_google_system_service_default_app_name),
            intentFlags = appContext.getString(R.string.os_google_system_service_default_intent_flags)
        )
        return defaults.normalized()
    }

    private fun buildOsGoogleSettingsSampleDefaults(
        defaults: OsGoogleSystemServiceConfig
    ): OsGoogleSystemServiceConfig {
        val sample = OsGoogleSystemServiceConfig(
            title = appContext.getString(R.string.os_activity_builtin_google_settings_title),
            subtitle = appContext.getString(R.string.os_activity_builtin_google_settings_subtitle),
            appName = appContext.getString(R.string.os_activity_builtin_google_settings_app_name),
            packageName = appContext.getString(R.string.os_activity_builtin_google_settings_package),
            className = appContext.getString(R.string.os_activity_builtin_google_settings_class),
            intentAction = Intent.ACTION_VIEW,
            intentFlags = appContext.getString(R.string.os_google_system_service_default_intent_flags)
        )
        return sample.normalized(defaults)
    }

    private fun loadOsActivityCards(): List<OsActivityShortcutCard> {
        val defaults = buildOsGoogleSystemDefaults()
        val sampleDefaults = buildOsGoogleSettingsSampleDefaults(defaults)
        return OsActivityShortcutCardStore.loadCards(
            defaults = defaults,
            builtInSampleDefaults = sampleDefaults
        )
    }

    private fun matchesActivityCard(card: OsActivityShortcutCard, query: String): Boolean {
        if (query.isBlank()) return true
        val key = query.lowercase(Locale.ROOT)
        return card.id.lowercase(Locale.ROOT).contains(key) ||
            card.config.title.lowercase(Locale.ROOT).contains(key) ||
            card.config.subtitle.lowercase(Locale.ROOT).contains(key) ||
            card.config.appName.lowercase(Locale.ROOT).contains(key) ||
            card.config.packageName.lowercase(Locale.ROOT).contains(key) ||
            card.config.className.lowercase(Locale.ROOT).contains(key) ||
            card.config.intentAction.lowercase(Locale.ROOT).contains(key) ||
            card.config.intentCategory.lowercase(Locale.ROOT).contains(key)
    }

    private fun matchesShellCard(card: OsShellCommandCard, query: String): Boolean {
        if (query.isBlank()) return true
        val key = query.lowercase(Locale.ROOT)
        return card.id.lowercase(Locale.ROOT).contains(key) ||
            card.title.lowercase(Locale.ROOT).contains(key) ||
            card.subtitle.lowercase(Locale.ROOT).contains(key) ||
            card.command.lowercase(Locale.ROOT).contains(key) ||
            card.runOutput.lowercase(Locale.ROOT).contains(key)
    }

    private fun normalizeOutputSnippet(raw: String, maxLen: Int = 180): String {
        val normalized = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (normalized.length <= maxLen) normalized else "${normalized.take(maxLen - 1)}..."
    }

    private fun buildActivityCardsImportText(rawJson: String, apply: Boolean): String {
        val defaults = buildOsGoogleSystemDefaults()
        val sampleDefaults = buildOsGoogleSettingsSampleDefaults(defaults)
        val existing = loadOsActivityCards()
        val payload = OsCardTransferService.parseActivityImportPayload(
            raw = rawJson,
            defaults = defaults,
            builtInSampleDefaults = sampleDefaults
        )
        val result = if (apply && payload.cards.isNotEmpty()) {
            OsCardTransferService.applyActivityImport(
                payload = payload,
                existingCards = existing,
                defaults = defaults,
                builtInSampleDefaults = sampleDefaults
            )
        } else {
            OsCardTransferService.previewActivityImport(
                payload = payload,
                existingCards = existing,
                defaults = defaults,
                builtInSampleDefaults = sampleDefaults
            )
        }
        return OsCardTransferService.buildImportSummaryText(
            target = "activity",
            payload = payload,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            apply = apply,
            applied = apply && payload.cards.isNotEmpty()
        )
    }

    private fun buildShellCardsImportText(rawJson: String, apply: Boolean): String {
        val existing = OsShellCommandCardStore.loadCards()
        val payload = OsCardTransferService.parseShellImportPayload(rawJson)
        val result = if (apply && payload.cards.isNotEmpty()) {
            OsCardTransferService.applyShellImport(
                payload = payload,
                existingCards = existing
            )
        } else {
            OsCardTransferService.previewShellImport(
                payload = payload,
                existingCards = existing
            )
        }
        return OsCardTransferService.buildImportSummaryText(
            target = "shell",
            payload = payload,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            apply = apply,
            applied = apply && payload.cards.isNotEmpty()
        )
    }

    private fun normalizeOsCardTransferTarget(target: String): String {
        return when (target.trim().lowercase(Locale.ROOT)) {
            "activity", "activities", "shortcut", "shortcuts" -> "activity"
            "shell", "command", "commands" -> "shell"
            else -> "all"
        }
    }

    private fun decodeRows(raw: String?): List<InfoRow> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('\t')
            if (index <= 0) return@mapNotNull null
            val key = Uri.decode(line.substring(0, index)).trim()
            val value = Uri.decode(line.substring(index + 1)).trim()
            if (key.isBlank() || value.isBlank()) null else InfoRow(key, value)
        }.toList()
    }

    private fun readSystemTopInfoRows(
        maxCount: Int,
        query: String?
    ): List<InfoRow> {
        val kv = KeiMmkv.byId(OS_CACHE_KV_ID)
        val legacyKv = KeiMmkv.byId(LEGACY_SYSTEM_CACHE_KV_ID)
        val readRaw: (String, String) -> String? = { newKey, legacyKey ->
            val newRaw = kv.decodeString(newKey)
            if (!newRaw.isNullOrBlank()) newRaw else legacyKv.decodeString(legacyKey)
        }
        val allRows = (
            decodeRows(readRaw(KEY_OS_SYSTEM, LEGACY_KEY_SYSTEM)) +
                decodeRows(readRaw(KEY_OS_SECURE, LEGACY_KEY_SECURE)) +
                decodeRows(readRaw(KEY_OS_GLOBAL, LEGACY_KEY_GLOBAL)) +
                decodeRows(readRaw(KEY_OS_ANDROID, LEGACY_KEY_ANDROID)) +
                decodeRows(readRaw(KEY_OS_JAVA, LEGACY_KEY_JAVA)) +
                decodeRows(readRaw(KEY_OS_LINUX, LEGACY_KEY_LINUX))
            )
            .distinctBy { "${it.key}\u0000${it.value}" }

        val topKeyHints = listOf(
            "long_press", "fbo", "adb", "share_", "voice_", "autofill", "credential", "zygote",
            "dexopt", "dex2oat", "tango", "aod", "vulkan", "opengl", "graphics", "density", "gsm",
            "miui", "version", "build", "security_patch", "lc3", "lea", "usb", "getprop", "env."
        )
        val filtered = allRows.filter { row ->
            topKeyHints.any { hint -> row.key.contains(hint, ignoreCase = true) }
        }

        val queryText = query?.trim().orEmpty()
        val queried = if (queryText.isBlank()) {
            filtered
        } else {
            filtered.filter {
                it.key.contains(queryText, ignoreCase = true) ||
                    it.value.contains(queryText, ignoreCase = true)
            }
        }

        return queried.take(maxCount)
    }

    private companion object {
        const val OS_CACHE_KV_ID = "os_info_cache"
        const val LEGACY_SYSTEM_CACHE_KV_ID = "system_info_cache"

        const val KEY_OS_SYSTEM = "section_os_system_table"
        const val KEY_OS_SECURE = "section_os_secure_table"
        const val KEY_OS_GLOBAL = "section_os_global_table"
        const val KEY_OS_ANDROID = "section_os_android_properties"
        const val KEY_OS_JAVA = "section_os_java_properties"
        const val KEY_OS_LINUX = "section_os_linux_environment"

        const val LEGACY_KEY_SYSTEM = "section_system_table"
        const val LEGACY_KEY_SECURE = "section_secure_table"
        const val LEGACY_KEY_GLOBAL = "section_global_table"
        const val LEGACY_KEY_ANDROID = "section_android_properties"
        const val LEGACY_KEY_JAVA = "section_java_properties"
        const val LEGACY_KEY_LINUX = "section_linux_environment"
    }
}
