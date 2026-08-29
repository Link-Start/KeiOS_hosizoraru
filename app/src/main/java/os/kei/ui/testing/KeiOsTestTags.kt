@file:Suppress("PropertyName")

package os.kei.ui.testing

object KeiOsTestTags {
    const val MainBottomTabHome = "main_bottom_tab_home"
    const val MainBottomTabOs = "main_bottom_tab_os"
    const val MainBottomTabMcp = "main_bottom_tab_mcp"
    const val MainBottomTabGitHub = "main_bottom_tab_github"
    const val MainBottomTabBa = "main_bottom_tab_ba"

    /**
     * The button that converts the tab bar into a sidebar, and back.
     *
     * Tagged so the baseline profile can reach the sidebar at all: it is the only entry point, and the rail's
     * composables would otherwise never be compiled into the profile even on a tablet-shaped window.
     */
    const val MainSidebarToggle = "main_sidebar_toggle"
    const val MainSidebarRowHome = "main_sidebar_row_home"
    const val MainSidebarRowOs = "main_sidebar_row_os"
    const val MainSidebarRowMcp = "main_sidebar_row_mcp"
    const val MainSidebarRowGitHub = "main_sidebar_row_github"
    const val MainSidebarRowBa = "main_sidebar_row_ba"
    const val MainPagerSettledHome = "main_pager_settled_home"
    const val MainPagerSettledOs = "main_pager_settled_os"
    const val MainPagerSettledMcp = "main_pager_settled_mcp"
    const val MainPagerSettledGitHub = "main_pager_settled_github"
    const val MainPagerSettledBa = "main_pager_settled_ba"
    const val HomePageRoot = "home_page_root"
    const val HomeSettingsButton = "home_settings_button"
    const val HomeAboutButton = "home_about_button"
    const val HomeWebDavCard = "home_webdav_card"
    const val SettingsPageRoot = "settings_page_root"
    const val AboutPageRoot = "about_page_root"
    const val WebDavSyncPageRoot = "webdav_sync_page_root"
    const val OsPageRoot = "os_page_root"
    const val OsShellRunnerButton = "os_shell_runner_button"
    const val OsShellRunnerPageRoot = "os_shell_runner_page_root"
    const val McpPageRoot = "mcp_page_root"
    const val McpSkillButton = "mcp_skill_button"
    const val McpSkillPageRoot = "mcp_skill_page_root"
    const val BaPageRoot = "ba_page_root"
    const val BaAccountManagementButton = "ba_account_management_button"
    const val BaCraftCardHeader = "ba_craft_card_header"

    /** The release list page's root, and the handles a journey needs on it. */
    const val GitHubReleasePageRoot = "github_release_page_root"
    const val GitHubReleaseCardFirst = "github_release_card_first"
    const val GitHubReleaseNextPageButton = "github_release_next_page_button"
    const val GitHubReleasePageJumpField = "github_release_page_jump_field"

    /** A tracked card's overflow trigger, and the release entry inside it. */
    const val GitHubTrackedItemMoreButton = "github_tracked_item_more_button"
    const val GitHubReleaseMenuItem = "github_release_menu_item"
    const val GitHubReleaseTagFilterButton = "github_release_tag_filter_button"

    /**
     * The first cooldown card, the cafe's headpat.
     *
     * The three cooldowns are cards in the list now, so the profile has something to expand that is not
     * a craft slot — and the two card kinds animate the same accordion but compose different bodies.
     */
    const val BaCooldownCardFirst = "ba_cooldown_card_first"

    /** The cooldown editor's entry point, inside an expanded cooldown card. */
    const val BaCooldownAdjustButton = "ba_cooldown_adjust_button"

    /**
     * The first craft slot's card.
     *
     * The baseline profile's "the craft section is open" signal, and its way in: the slots are one card
     * each now, so there is no single container whose presence says the section is showing.
     */
    const val BaCraftSlotCardFirst = "ba_craft_slot_card_first"

    /** The configure button inside that card, which opens the craft sheet. */
    const val BaCraftSlotFirst = "ba_craft_slot_first"
    const val BaDockOpenCalendar = "ba_dock_open_calendar"
    const val BaDockOpenPool = "ba_dock_open_pool"

    /**
     * One switch in the daily-done template editor, tagged so a journey can make an edit it knows about.
     *
     * A switch rather than one of the four dropdowns: a toggle always changes the draft, so the
     * unsaved-changes path it is there to reach cannot silently not happen because a picker landed back
     * on the value it started from.
     */
    const val BaDailyTemplateHeadpatSwitch = "ba_daily_template_headpat_switch"
    const val GitHubPageRoot = "github_page_root"
    const val GitHubImportMenuButton = "github_import_menu_button"
    const val GitHubImportTracks = "github_import_tracks"
    const val GitHubImportStars = "github_import_stars"
    const val GitHubShareImportCancel = "github_share_import_cancel"
    const val GitHubShareImportConfirm = "github_share_import_confirm"
    const val GitHubShareImportPendingClose = "github_share_import_pending_close"
    const val GitHubShareImportPendingCancel = "github_share_import_pending_cancel"
    const val GitHubShareImportAttachClose = "github_share_import_attach_close"
    const val GitHubShareImportAttachCancel = "github_share_import_attach_cancel"
    const val GitHubShareImportAttachConfirm = "github_share_import_attach_confirm"
    const val GitHubActionsHistoryButton = "github_actions_history_button"
    const val GitHubActionsHistoryPageRoot = "github_actions_history_page_root"
    const val GitHubShareImportAttachConfirmOpenGitHub =
        "github_share_import_attach_confirm_open_github"
}
