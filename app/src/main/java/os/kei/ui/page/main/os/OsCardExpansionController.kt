package os.kei.ui.page.main.os

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
import os.kei.ui.page.main.os.shortcut.LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

internal class OsCardExpansionController {
    private val mutableState = MutableStateFlow(OsCardExpansionUiState())
    val state: StateFlow<OsCardExpansionUiState> = mutableState.asStateFlow()

    fun syncActivityCards(
        cards: List<OsActivityShortcutCard>,
        initialGoogleSystemServiceExpanded: Boolean,
    ) {
        val currentIds = cards.mapTo(mutableSetOf()) { it.id }
        val previous = mutableState.value.activityCards
        val next =
            cards
                .mapIndexed { index, card ->
                    val usesStoredDefaultExpansion =
                        index == 0 && (
                            card.id == LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID ||
                                card.id == BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
                        )
                    val expanded =
                        if (usesStoredDefaultExpansion) {
                            initialGoogleSystemServiceExpanded
                        } else {
                            previous[card.id] ?: false
                        }
                    card.id to expanded
                }.toMap()
                .filterKeys(currentIds::contains)
        mutableState.update { state ->
            if (state.activityCards == next) state else state.copy(activityCards = next)
        }
    }

    fun syncShellCommandCards(cards: List<OsShellCommandCard>) {
        val currentIds = cards.mapTo(mutableSetOf()) { it.id }
        val previous = mutableState.value.shellCommandCards
        val next =
            cards
                .associate { card ->
                    card.id to (previous[card.id] ?: false)
                }.filterKeys(currentIds::contains)
        mutableState.update { state ->
            if (state.shellCommandCards == next) state else state.copy(shellCommandCards = next)
        }
    }

    fun updateActivityCard(
        cardId: String,
        expanded: Boolean,
    ) {
        mutableState.update { state ->
            val next = state.activityCards + (cardId to expanded)
            if (state.activityCards == next) state else state.copy(activityCards = next)
        }
    }

    fun updateShellCommandCard(
        cardId: String,
        expanded: Boolean,
    ) {
        mutableState.update { state ->
            val next = state.shellCommandCards + (cardId to expanded)
            if (state.shellCommandCards == next) state else state.copy(shellCommandCards = next)
        }
    }

    fun removeActivityCard(cardId: String) {
        mutableState.update { state ->
            if (!state.activityCards.containsKey(cardId)) return@update state
            state.copy(activityCards = state.activityCards - cardId)
        }
    }

    fun removeShellCommandCard(cardId: String) {
        mutableState.update { state ->
            if (!state.shellCommandCards.containsKey(cardId)) return@update state
            state.copy(shellCommandCards = state.shellCommandCards - cardId)
        }
    }

    fun retainActivityCards(validIds: Set<String>) {
        mutableState.update { state ->
            val next = state.activityCards.filterKeys(validIds::contains)
            if (state.activityCards == next) state else state.copy(activityCards = next)
        }
    }

    fun retainShellCommandCards(validIds: Set<String>) {
        mutableState.update { state ->
            val next = state.shellCommandCards.filterKeys(validIds::contains)
            if (state.shellCommandCards == next) state else state.copy(shellCommandCards = next)
        }
    }
}
