package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Badge arithmetic and geometry for the floating docks.
 *
 * ## The number has to mean one thing
 *
 * A vertical dock shows one badge per action while expanded, and collapses to a single button that
 * stands in for all of them. The badge on that button therefore has to summarise **everything it
 * conceals** — otherwise the same pixels report a different quantity depending on a state the user
 * did not think they were changing.
 *
 * That was the bug. The BA dock got it right by hand (`compactBadgeLabel = totalCount`, a real sum of
 * its calendar and pool counts) but the GitHub dock passed only the *refresh* count as its collapsed
 * badge while its expanded history action showed the *unread history* count. Same corner, same size,
 * same colour, two unrelated numbers. Deriving the collapsed label here instead of accepting one from
 * the caller is what makes that mistake impossible rather than merely fixed.
 */

/** Above this, a count stops being worth reading precisely. Matches the per-action formatters. */
const val APP_FLOATING_DOCK_BADGE_CAP = 99

/**
 * Shown when something is badged but its label is not a number, so no total can honestly be reported.
 *
 * A dot is the truthful answer to "how many": it says *there is something in here* without inventing
 * a figure. Silently dropping the badge would hide the notification; showing one of the labels would
 * be the very inconsistency this file exists to prevent.
 */
const val APP_FLOATING_DOCK_BADGE_DOT = "•"

/**
 * Summarises the badges a collapsed dock hides into a single honest label.
 *
 * Already-capped inputs stay capped: "99+" plus anything is still "at least 99", so the result keeps
 * the `+` rather than pretending the sum is exact.
 */
fun appFloatingDockCollapsedBadgeLabel(labels: List<String?>): String? {
    var total = 0
    var sawNumber = false
    var sawApproximate = false
    var sawUncountable = false

    labels.forEach { raw ->
        val label = raw?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
        val approximate = label.endsWith("+")
        val digits = label.removeSuffix("+")
        val parsed = digits.toIntOrNull()
        when {
            parsed == null -> sawUncountable = true
            parsed <= 0 -> Unit
            else -> {
                sawNumber = true
                if (approximate) sawApproximate = true
                total += parsed
            }
        }
    }

    return when {
        sawNumber && (sawApproximate || total > APP_FLOATING_DOCK_BADGE_CAP) -> "$APP_FLOATING_DOCK_BADGE_CAP+"
        sawNumber -> total.toString()
        sawUncountable -> APP_FLOATING_DOCK_BADGE_DOT
        else -> null
    }
}

/**
 * Top-left position for a badge on a round host, chosen so the badge never crosses the rim.
 *
 * The docks are capsules, and their content is clipped to that capsule. The previous placement was
 * `align(TopEnd).offset(x = -5.dp, y = 6.dp)` — an offset inside the host's *bounding box*, which for
 * a round host is the one place the badge is guaranteed to escape. Measured on the Android 17 AVD at
 * 3x density: against a 62dp dock the badge's right edge tracked the capsule's curve exactly (210px at
 * one row, 218px eight rows down, 222px further still), because the capsule clip was shaving it. The
 * badge lost its own rounded end and read as bleeding out of the dock.
 *
 * The fix is to solve against the circle rather than the box. The badge's outer corner is placed on the
 * host's inscribed circle along the 45° diagonal, so the corner — the part that reaches furthest — sits
 * exactly on the rim and everything else is inside it. [inlayPx] pulls it in further so the badge
 * clears the glass rim highlight rather than touching it.
 *
 * Falls back to the box corner for a host too small to inscribe the badge at all, which keeps a
 * degenerate size from producing a NaN offset.
 */
internal fun appFloatingDockBadgeOffsetPx(
    hostWidthPx: Float,
    hostHeightPx: Float,
    badgeWidthPx: Float,
    badgeHeightPx: Float,
    inlayPx: Float,
): Offset {
    if (!hostWidthPx.isFinite() || !hostHeightPx.isFinite()) return Offset.Zero
    if (!badgeWidthPx.isFinite() || !badgeHeightPx.isFinite()) return Offset.Zero

    val radius = min(hostWidthPx, hostHeightPx) / 2f - inlayPx.coerceAtLeast(0f)
    val centreX = hostWidthPx / 2f
    val centreY = hostHeightPx / 2f
    // The 45° point on the inscribed circle: the corner lands here, so nothing extends past it.
    val diagonal = radius / sqrt(2f)
    val cornerX = centreX + diagonal
    val cornerY = centreY - diagonal

    val left = cornerX - badgeWidthPx
    val top = cornerY
    if (radius <= 0f || left < 0f || top < 0f) {
        // Too small to inscribe: sit in the box corner and let the caller's clip decide. Better a
        // visible badge than an off-screen one.
        return Offset(
            x = (hostWidthPx - badgeWidthPx).coerceAtLeast(0f),
            y = 0f,
        )
    }
    return Offset(
        x = left.coerceIn(0f, (hostWidthPx - badgeWidthPx).coerceAtLeast(0f)),
        y = top.coerceIn(0f, (hostHeightPx - badgeHeightPx).coerceAtLeast(0f)),
    )
}
