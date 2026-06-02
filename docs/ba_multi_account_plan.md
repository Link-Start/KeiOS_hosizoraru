# BA Multi-Account Refactor Plan

## Goal

Build a complete BA multi-account system while preserving the existing BA data pages and catalog boundaries.

The final system supports:

- Multiple accounts on the same server, such as two CN accounts.
- Multiple accounts across different servers, such as CN, Global, and JP accounts.
- Account-scoped AP, cafe AP, cafe cooldown, identity, and personal reminders.
- Server-scoped activity calendar and pool information caches.
- Background reminders from every enabled account, even when the user last viewed another server.
- A BA Page layout centered on account switching, AP status, cafe status, and three vertical navigation icons.

## Scope Split

| Area | Scope | Target Owner |
|---|---|---|
| Account identity | Account-scoped | Account management sheet and account pager card |
| AP runtime | Account-scoped | AP card |
| Cafe AP and cafe cooldown | Account-scoped | Cafe card |
| AP/cafe/arena/cafe visit reminders | Account-scoped, with global defaults and per-account overrides | BA settings sheet and account management sheet |
| Activity calendar data | Server-scoped | Activity Calendar Page |
| Pool data | Server-scoped | Pool Page |
| Activity/pool display settings | Server/page-scoped | Activity Calendar Page and Pool Page |
| Media settings | Global BA/user-scoped | BA settings sheet |
| Student/NPC/Satellite catalog | User/game-data-scoped | Catalog Page; not part of account context |
| Vertical dock | Navigation only | BA Page |

## Page Architecture

| Surface | Content |
|---|---|
| BA Page action bar | Account management, BA settings, More/debug if needed |
| BA Page main content | Account pager card, AP card, Cafe card, optional current-server summary |
| BA Page vertical dock | Activity Calendar, Pool, Student/NPC/Satellite Catalog |
| Account management sheet | Account list, add/edit/delete/reorder, active account, global-follow strategy |
| BA settings sheet | Global reminder defaults, media settings, low-frequency BA-wide settings |
| Activity Calendar Page | Server selector, refresh, sync interval, ended-content toggle, image/display settings |
| Pool Page | Server selector, refresh, sync interval, ended-content toggle, image/display settings |

## Data Model

```kotlin
@JvmInline
value class BaAccountId(val value: String)

enum class BaAccountNotificationMode {
    FollowGlobal,
    Custom,
}

data class BaAccountProfile(
    val id: BaAccountId,
    val serverIndex: Int,
    val displayName: String,
    val nickname: String,
    val friendCode: String,
    val notificationMode: BaAccountNotificationMode,
    val remindersEnabled: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
)

data class BaAccountRuntime(
    val apLimit: Int,
    val apCurrent: Double,
    val apRegenBaseMs: Long,
    val apSyncMs: Long,
    val cafeLevel: Int,
    val cafeStoredAp: Double,
    val cafeLastHourMs: Long,
    val coffeeHeadpatMs: Long,
    val coffeeInvite1UsedMs: Long,
    val coffeeInvite2UsedMs: Long,
)

data class BaGlobalReminderSettings(
    val apNotifyEnabled: Boolean,
    val apNotifyThreshold: Int,
    val cafeApNotifyEnabled: Boolean,
    val cafeApNotifyThreshold: Int,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
)

data class BaAccountReminderOverride(
    val accountId: BaAccountId,
    val apNotifyEnabled: Boolean,
    val apNotifyThreshold: Int,
    val cafeApNotifyEnabled: Boolean,
    val cafeApNotifyThreshold: Int,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
)
```

## Storage Strategy

| Store Item | Suggested Key | Notes |
|---|---|---|
| Account list | `ba_accounts_v1` | JSON array encoded by the BA account store |
| Active account | `active_account_id` | Replaces `server_index` as the BA Page primary context |
| Global reminder defaults | Existing reminder keys first, then grouped model if needed | Keeps migration small |
| Account reminder override | `account_reminder_override_<accountId>` | Written only for custom accounts |
| Legacy server identity | Existing `id_nickname*` and `id_friend_code*` | Read during migration, kept as compatibility data |
| Calendar/pool cache | Existing per-server keys | Kept server-scoped |

The first backend phase keeps `BaPageSnapshot` as a compatibility DTO. It represents the active account plus global/server settings, so existing UI code can keep rendering while the backend moves to account ownership.

## Migration Rules

| Legacy State | Migration Result |
|---|---|
| Shared ID mode | Create one default account on the saved server, carrying identity, AP, cafe, cooldown, and reminder settings |
| Server-specific ID mode | Create up to three server accounts; current server account receives AP/cafe/runtime/reminder settings, other accounts receive identity and default runtime |
| Empty legacy server identity | Fall back to shared nickname/friend code |
| Calendar/pool caches | Remain unchanged under server keys |
| Old `idIndependentByServer` | Removed from user-facing settings; kept only as migration input |

Migration should run idempotently. Re-running must keep account ids stable and avoid duplicating accounts.

## Backend Phases

| Phase | Status | Deliverables | Verification |
|---|---|---|---|
| P0 Plan document | Done | This md file | Commit exists |
| P1 Account backend foundation | Done | Account models, store, repository, migration, account tests | Targeted unit tests |
| P2 Active account compatibility | Done | `BASettingsStore.loadSnapshot()` and BA repositories read active account snapshot | Existing BA tests plus new active-account tests |
| P3 Account-scoped runtime writes | Done | Runtime persistence update carries account id, AP/cafe/cooldown writes target active account | Runtime tests |
| P4 Multi-account reminders | Done | Background reminder service scans all enabled accounts, uses scoped notification ids | Reminder service tests and scheduler tests |
| P5 Server-scoped calendar/pool sync cleanup | Done | Distinct server sync for enabled accounts, server-scoped notified keys | Calendar/pool notification tests |
| P6 Settings migration | Done | Data sync/display moved to Activity Calendar and Pool pages; BA settings no longer owns those page-specific fields | `09b5dfda`, targeted BA tests |
| P7 Account UI bridge | In Progress | Account pager card and active-account switching bridge landed; account management sheet and AP card split remain | `3a9f91e0`, targeted BA tests |
| P8 Dock cleanup | Pending | Vertical dock only has Calendar, Pool, Catalog icons | AVD visual/navigation check |
| P9 Release verification | Pending | Release build, R8 path, AVD smoke | `:app:assembleRelease`, AVD only |

## Commit Plan

| Commit | Scope |
|---|---|
| 1 | Add this plan document |
| 2 | Add account models/store/repository/migration and unit tests |
| 3 | Connect active account compatibility snapshot |
| 4 | Move runtime persistence to account scope |
| 5 | Add multi-account reminder backend |
| 6 | Move calendar/pool settings into data-page backend paths |
| 7a | Add account pager and active-account switching bridge |
| 7b | Add account management sheet and AP card split |
| 8 | Simplify vertical dock and move catalog entry |
| 9 | Final release/AVD cleanup |

## UI Phases

| Phase | Main Change | Notes |
|---|---|---|
| UI-1 | `BaAccountPagerCard` | Horizontal account switching; `activeAccountId` drives page state |
| UI-2 | `BaApCard` | AP leaves old overview card and becomes account-scoped |
| UI-3 | `BaCafeCard` binding | Cafe state follows active account |
| UI-4 | Account management sheet | Account list, add/edit/delete/reorder, follow-global controls |
| UI-5 | BA settings sheet | Global reminders and media settings |
| UI-6 | Data-page settings | Activity/pool sync and display settings move to their pages |
| UI-7 | Dock cleanup | Only Calendar, Pool, Catalog navigation icons remain |

## Invariants

- Student/NPC/Satellite catalog remains user/game-data-scoped.
- Activity calendar and pool caches remain server-scoped.
- AP, cafe AP, identity, cafe cooldown, and personal reminders become account-scoped.
- Background reminders evaluate every enabled account.
- Server data reminders are deduplicated per server.
- Notification framework stays intact; BA dispatcher receives scoped ids and text.
- UI state is hoisted into ViewModels and exposed through read-only `StateFlow`.
- Repository suspend functions are main-safe.
- Tests and AVD validation use emulator targets, not physical devices.

## Validation Checklist

| Requirement | Evidence |
|---|---|
| Shared legacy profile migrates to one account | Unit test |
| Server-specific legacy IDs migrate to server accounts | Unit test |
| Same server can hold multiple accounts | Store/repository unit test |
| Active account snapshot reads correct AP/cafe/identity | Repository unit test |
| Runtime update writes selected account only | Runtime persistence unit test |
| Follow-global notification settings affect all follow-global accounts | Reminder settings unit test |
| Custom account reminder settings ignore global threshold changes | Reminder settings unit test |
| Background tick scans every enabled account | Reminder service unit test |
| Calendar/pool sync deduplicates servers | Server sync unit test |
| BA Page account switching updates AP and cafe cards | AVD smoke |
| Vertical dock opens Calendar, Pool, Catalog | AVD smoke |
| Release and R8 path pass | `./gradlew :app:assembleRelease` |
