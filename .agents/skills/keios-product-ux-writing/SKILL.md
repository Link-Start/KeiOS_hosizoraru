---
name: keios-product-ux-writing
description: >-
  Review KeiOS product copy, localized resources, status and error surfaces,
  notifications, MCP and export text contracts, release notes, durable comments,
  and user-facing docs. Use for substantive wording or behavior changes across
  these surfaces; route pure Compose layout, visual design, animation, platform,
  performance, and accessibility implementation to their dedicated project skills.
license: Apache-2.0
metadata:
  author: "scarletkc; adapted for KeiOS"
  source: https://github.com/scarletkc/agents/blob/d5a312346adcf2169eb5a31a1b88368441b32327/skills/ux-writing/SKILL.md
  summary: Keep KeiOS product copy localized, truthful, durable, and synchronized.
---

# KeiOS Product UX Writing

Treat user-visible text as product behavior. Follow `AGENTS.md`, especially its
direct-positive writing style and resource conventions. This skill covers the
claim, wording, ownership, localization, and synchronization of the text.

## Route the work

Apply this skill to substantive changes or reviews involving:

- Compose titles, labels, supporting copy, status pills and cards, settings,
  empty states, errors, recovery actions, dialogs, snackbars, and search hints;
- notification and Super Island text, import/export results, WebDAV sync,
  GitHub workflows, OS tools, BA helpers, and runtime summaries;
- MCP tool names and descriptions, generated skill text, human-readable tool
  results, structured output fields, and exported reports;
- README, feature guides, paired language docs, release notes, validation
  artifacts, durable titles, and code comments;
- behavior changes whose old copy can remain in resources, notifications,
  tests, tool contracts, or documentation.

Use `android-ux-design` and `compose-expert` for visual and Compose structure.
Use `android-accessibility` for semantics, focus, touch targets, contrast, and
screen-reader behavior. Apply this skill to the text those implementations
expose.

## Write product copy from effective state

- Read the current value from its owning repository, ViewModel, state model,
  build source, or platform result. Keep Compose responsible for projection,
  with state semantics owned outside display-only wording.
- Give configured, active, pending, cached, stale, unavailable, degraded,
  failed, and completed states distinct labels when they lead to different
  user decisions.
- Announce success at the operation's completion boundary. A tap, launched
  intent, scheduled worker, posted notification, or started import represents
  its own earlier stage.
- Attach provenance, freshness, or failure detail to the exact value it
  qualifies. Keep adjacent labels free from duplicate suffixes and ambiguous
  status terms.
- Give every actionable error the failed operation or input, relevant surface,
  usable reason, and next safe action. Preserve the original exception message
  when only the traceback is being suppressed.
- Choose action labels that describe the action and target. Destructive or
  irreversible actions state their consequence before confirmation.

Lead with the result or action. Keep supporting text specific and compact.
Claims such as recommended, faster, safer, or better require current project
evidence that supports the user's decision.

## Preserve resources and accessibility text

- Put new user-visible strings in the appropriate `strings_*.xml` domain file.
  Keep default, `values-zh-rCN`, `values-en`, and `values-ja` resources aligned
  for keys, placeholders, formatting, and meaning.
- Preserve placeholder order and type. Review the full sentence in every
  locale when a noun, state model, or action changes.
- Use content descriptions for the action or state perceived through the
  control. Decorative icons remain silent; visible labels and semantics avoid
  redundant announcements.
- Keep long values readable and copyable. Layout adaptation belongs to the UI
  skills while this skill protects the payload and meaning.

## Protect notification, MCP, and export contracts

- Notification titles and bodies report the current operation, target, and
  actionable outcome within the compact surface. Preserve the existing
  notification framework and channel behavior required by `AGENTS.md`.
- MCP tool descriptions state the actual capability, mutation boundary,
  required input, and meaningful result. Tool metadata and user-facing docs
  change together when behavior changes.
- Structured JSON and other machine-readable output keep stable fields,
  absence semantics, and clean payloads. Human notices use the human-format
  path. Secrets, credentials, private paths, and incidental diagnostics stay
  outside public tool and export contracts.
- Human-readable status output emphasizes deviations, owners, freshness, and
  recovery. Full inspection data belongs in its dedicated detail surface.

## Keep docs and release claims durable

- Let each document keep one job. README introduces the product and links to
  detailed feature, build, contribution, and release material. Planning and QA
  documents retain their dated work scope.
- Give changing field lists, supported values, precedence rules, and feature
  contracts one authoritative home. Other pages carry a specific link plus
  surface-specific nuance.
- Resolve build and dependency versions from Gradle-owned sources. Resolve the
  latest published release from GitHub Releases. Release notes may preserve a
  dated version snapshot; long-lived docs should avoid additional manual copies
  of fast-moving values.
- Keep English and Chinese document pairs semantically aligned when both own
  the same user workflow. Japanese coverage follows the existing resource and
  documentation boundary of the touched feature.
- Preserve unaffected wording and structure during focused edits. A behavior
  change earns the smallest sufficient copy and documentation update.

## Deliver the final state

Titles, comments, release text, screenshots, and generated reports describe the
result a reader can use. Place design alternatives and abandoned attempts in
the planning record, review discussion, or commit history. Comments retain a
non-obvious platform constraint, ordering requirement, upstream quirk, or
security boundary that the code alone cannot explain.

## Sweep copy after behavior changes

Search old terms and behavior across:

1. default, Chinese, English, and Japanese `strings*.xml` resources;
2. Compose call sites, state models, ViewModels, and status projections;
3. notifications, Super Island templates, imports, exports, sync, and errors;
4. MCP catalog entries, tool descriptions, annotations, generated skills, and
   structured results;
5. tests for displayed text, semantics, state transitions, and absent claims;
6. README, `readme/`, `docs/`, release notes, headings, anchors, and links.

Assert behavior and flattened text where console wrapping can vary. For
machine formats, test required absences alongside required fields. Finish with
a locale-key check and a targeted search for the retired wording.
