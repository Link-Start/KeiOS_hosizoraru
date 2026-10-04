# KeiOS (Claude Code)

Follow [AGENTS.md](AGENTS.md) for shared repository rules. This file keeps only Claude-specific routing and current-state cautions.

## Find task-relevant guidance

- Read only the matching record under docs/planning/ when an issue has prior measurements or a recorded decision. Treat captures and counts as dated evidence; check current source, build, device, and inputs before relying on them.
- For frame-time, stutter, scroll, or transition investigations, use [.claude/skills/keios-frame-performance/SKILL.md](.claude/skills/keios-frame-performance/SKILL.md) and load only the measurement reference needed for the task.
- For dependency maintenance, follow AGENTS.md and inspect current Gradle declarations and sources. KeiOS tracks the latest Miuix SNAPSHOT; do not assume a snapshot is current or stale from an old note.

## Shared tools and tests

- Before device work, inspect connected targets and select the intended device explicitly. Do not assume emulator-5554 or reuse a serial from an old run. Preserve active ADB, device, and Gradle work.
- Add or update a focused test when it can catch an observable regression not already covered. Put it in the owning module and avoid tests that assert implementation text or call order without a behavior contract. Consult readme/BUILD.md for commands.
- Commit, push, or publish only when the user asks. When commits are requested, group them by review boundary and verify each requested state.
