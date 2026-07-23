---
title: "Document Chat"
type: "schema:DefinedTerm"
lang: en
description: "A chat feature integrated into the review screen that lets users ask on-device Gemma 4 questions about document content (DocumentChatSession)"
termCode: "MF-07"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [On-device processing]
translated_from: .wikicommit/wiki/ja/DefinedTerm/document-chat.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Document Chat (MF-07) is a chat feature in which `DocumentChatSession` is integrated into the review screen (S-02); it sets `ReviewResult`'s structured fields as system context, letting the user ask Gemma 4 questions about the document's content. The system prompt does not include the raw PII text (the `spanText` of `piiSpans`).

## Usage

`initialize()` starts a session in the source language immediately after the review screen is shown, and re-initializes the session in the translated language when translation completes (MF-03), clearing history. `sendMessage()` sends a user message and returns the completed `ChatMessage`, receiving tokens via streaming through the `onToken` callback. The chat history's source of truth is managed inside `DocumentChatSession`, which reuses `LlmChatModelHelper`'s existing chat functionality and only swaps out the system prompt.

Chat history has a cap: once either 20 turns (10 round-trip Q&As) or a cumulative 4,000 characters is reached, whichever comes first, "Chat history limit reached" is displayed and new input is disabled. Inference has a 60-second timeout; on timeout, "Failed to generate a response. Please send again" is shown above the chat input field, and the failed assistant message is not kept in history. If chat session initialization fails, the chat section is hidden while the review and inquiry features remain usable. While chat is generating (`chatIsGenerating = true`), the "Create Inquiry Document" button is disabled.

Besides `initialize()` / `sendMessage()`, `DocumentChatSession` provides `getChatHistory()` (used to retrieve chat history for, e.g., passing Q&A context to S-04) and `clear()` (resets the session when a new document is loaded). Inference for MF-02, MF-03, MF-06a, and MF-07 runs under mutual exclusion via a `Mutex` inside `DocumentReviewViewModel`, guaranteeing that multiple inferences are never issued concurrently.

### Hiding the input field (when the chat history limit is reached)

When chat history reaches its limit, the UI implementation does not disable the input field — it stops rendering the row entirely (`ChatSection` conditionally skips the entire input `Row` when `chatLimitReached = true`).

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/multilingual-translation]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/review-screen]]
