package os.kei.feature.github.data.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.optString
import os.kei.feature.github.model.GitHubReleaseDecisionBasis
import os.kei.feature.github.model.GitHubReleaseDecisionNote
import os.kei.feature.github.model.GitHubReleaseRejection

/**
 * The decision note, small enough to sit beside every cached check.
 *
 * Written only when it has something to say, so an ordinary repository -- the overwhelming
 * majority -- adds nothing to the cache at all. Enum names are stored rather than ordinals: a value
 * added to either enum must not silently re-label every note already on disk.
 */
internal fun releaseDecisionNoteToJson(note: GitHubReleaseDecisionNote): JsonObject? {
    if (note.isEmpty) return null
    return buildJsonObject {
        put("basis", note.stableBasis.name)
        if (note.stableRunnerUpTag.isNotBlank()) put("over", note.stableRunnerUpTag)
        note.preReleaseRejection?.let { put("preReject", it.name) }
    }
}

internal fun releaseDecisionNoteFromJson(obj: JsonObject?): GitHubReleaseDecisionNote {
    if (obj == null) return GitHubReleaseDecisionNote()
    // An unknown name is a note written by a newer build. Dropping that field is right: the rest of
    // the note still reads, and inventing a meaning for it would put a wrong sentence on the card.
    val basis = obj.optString("basis").takeIf { it.isNotBlank() }?.let { name ->
        GitHubReleaseDecisionBasis.entries.firstOrNull { it.name == name }
    } ?: GitHubReleaseDecisionBasis.Ranked
    val rejection = obj.optString("preReject").takeIf { it.isNotBlank() }?.let { name ->
        GitHubReleaseRejection.entries.firstOrNull { it.name == name }
    }
    return GitHubReleaseDecisionNote(
        stableBasis = basis,
        stableRunnerUpTag = obj.optString("over"),
        preReleaseRejection = rejection
    )
}
