---
title: "Quick Action"
type: "schema:DefinedTerm"
lang: en
description: "Intent-based shortcut operations shown on the review screen (S-02) according to context_label — add-to-calendar, show map, call, email, open URL, and similar"
termCode: ""
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
translated_from: .wikicommit/wiki/ja/DefinedTerm/quick-action.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Quick Actions are Android-Intent-based shortcut operations shown on [[SoftwareApplication/paperwork-navigator]]'s review screen (S-02) according to `context_label`; the action type is statically determined from the `context_label` derived by [[DefinedTerm/structured-field-extraction]] (MF-02).

## Usage

| `context_label` | Intent | Notes |
|--------------|--------|------|
| `deadline` | `CalendarContract.Events.INSERT` | Event name: `{doc_name} - Submission deadline` |
| `event_date` | `CalendarContract.Events.INSERT` | Event name: `{doc_name} - {raw_text}` |
| `issuer_address` / `other_address` | `geo:0,0?q={raw_text}` | Shows a map |
| `issuer_phone` / `other_phone` | `tel:{raw_text}` | Places a call |
| `issuer_email` / `other_email` | `mailto:{raw_text}` | Sends an email |
| `url` | `ACTION_VIEW` | |
| `tracking_number` | `ACTION_VIEW` | Builds a tracking URL from `metadata.carrier` |
| `flight_number` | `ACTION_VIEW` | Builds a flight-tracking URL from `metadata.airlineCode` |

For `applicant_*` fields (the applicant's own information), quick actions for calling, mapping, or emailing are deliberately not shown, since that information belongs to the user themselves. If no app on the device can handle the Intent, `ActivityNotFoundException` is caught to prevent a crash; Intent construction is centralized in `util/DocumentIntentBuilder.kt`.

### IntentIconButton

The button that executes a quick action is implemented as `IntentIconButton` (a shared component displaying a single emoji character), with a minimum tap target of 32dp wide by 48dp tall. When no app on the device can handle the corresponding Intent, `ActivityNotFoundException` is caught and an error message is shown via a Toast, preventing the app from crashing.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
