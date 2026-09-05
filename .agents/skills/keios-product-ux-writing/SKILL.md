---
name: keios-product-ux-writing
description: >-
  Edit or review KeiOS user-visible wording, localized strings, status and error
  claims, and notification, MCP, export, or public documentation text contracts.
  Use when wording or the behavior it describes changes; ordinary implementation,
  agent instructions, and internal comments without product claims use their own checks.
license: Apache-2.0
metadata:
  author: "scarletkc; adapted for KeiOS"
  source: https://github.com/scarletkc/agents/blob/d5a312346adcf2169eb5a31a1b88368441b32327/skills/ux-writing/SKILL.md
  summary: Keep KeiOS product copy localized, truthful, durable, and synchronized.
---

# KeiOS Product UX Writing

Make the affected product text accurate, useful, localized, and consistent with
its owning behavior. Follow the user's requested scope and `AGENTS.md`.

## Decide what needs changing

Use this skill for visible strings, status and error wording, notification text,
MCP descriptions/results, export text, or public documentation claims. A behavior
change also belongs here when existing text would describe the old behavior.
Internal agent instructions and comments with no product claim need ordinary
editorial review. Layout and implementation use the relevant Android/Compose
skill; load it only when that work is part of the task.

Start from the changed surface and its consumers. Inspect enough current code,
resources, tests, or documentation to identify:

- the fact being shown and its owning state model or operation result;
- the action a user can take and what it actually does;
- the locales, notifications, tool outputs, or documents sharing that contract.

Resolve routine wording from this evidence. Ask only when a missing product
decision changes the meaning or behavior. A scoped text edit can stay scoped.

## Write from effective state

- Keep state semantics in their owning repository, ViewModel, or runtime model;
  Compose projects those semantics into text.
- Distinguish configured, pending, active, cached/stale, unavailable, failed, and
  completed states where the distinction changes the user's decision.
- Announce success at the operation's completion boundary. A launched intent,
  scheduled worker, posted notification, or started import supports its own
  stage until downstream completion is observed.
- Label actions with their verb and target. Error text identifies the failed
  operation, a usable reason, and a recovery action when available. Preserve
  meaningful exception detail when suppressing a traceback.
- Attach freshness, provenance, or failure information to the value it qualifies.
  Keep technical detail in diagnostic surfaces unless it helps a product decision.
- Use direct, specific wording. Claims such as faster or recommended need
  evidence; a local edit does not create a requirement to benchmark the product.

## Preserve the affected contract

| Surface | What to preserve or synchronize |
| --- | --- |
| Android resources | Use the existing `strings_*.xml` domain; align affected keys, placeholders, formatting, and meaning across default, `values-zh-rCN`, `values-en`, and `values-ja` |
| Accessibility text | Describe the perceived action/state; keep decorative icons silent and avoid repeating the visible label in announcements |
| Notifications / Super Island | Fit the current operation and target to the compact surface; retain the existing framework, channels, and system-recognized behavior |
| MCP tools | Keep names, descriptions, mutation boundaries, inputs, meaningful results, and affected public docs aligned with implementation |
| Structured exports / tool results | Preserve field names, types, absence semantics, and clean payloads; keep human notices separate and exclude secrets or incidental private diagnostics |
| Public docs / releases | Preserve each document's purpose and paired-language meaning; link to the authoritative contract instead of copying fast-changing values |

For versions, use Gradle-owned sources for builds and verified published release
metadata for release claims. Preserve intentionally dated release notes and
validation records. Durable comments explain constraints or ordering that the
code alone cannot express. Update only documents that own or consume the change.

## Validate and finish

Choose checks from the changed contract:

- Resource changes: compare affected locale keys and placeholder types/order;
  read the complete sentence in each affected locale.
- State-dependent wording: use focused behavior/semantics tests; add regression
  coverage when it can catch an incorrect state or overstated success.
- Tool/export changes: check required fields, absence semantics, and unwanted
  payload leakage using the existing contract tests where available.
- Documentation: review claims, dates, links, and affected language pairs.
- Renamed terms or behavior: search affected consumers for the retired wording;
  widen the search if shared ownership or remaining occurrences justify it.

Inspect the actual UI when wrapping, input, semantics, or layout is in scope.
Run further builds or device checks only when the changed behavior requires them.
Report the changed surface, meaningful validation, and any unresolved product
choice or evidence limitation. Keep dated evidence distinct from current checks.
