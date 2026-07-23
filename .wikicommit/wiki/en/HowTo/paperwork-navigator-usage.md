---
title: "How to Use Paperwork Navigator"
type: "schema:HowTo"
lang: en
description: "The full sequence of operations from importing a document, through reviewing the analysis results, to generating inquiry context"
totalTime: ""
tool: ["A device running Android 15 or later", "the Paperwork Navigator APK"]
supply: ["A document file such as a PDF, a document photographed with the camera, or a gallery image"]
tags: []
translated_from: .wikicommit/wiki/ja/HowTo/paperwork-navigator-usage.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

A walkthrough for first-time users of [[SoftwareApplication/paperwork-navigator]], covering everything from installing the APK and downloading the model, through loading and analyzing a document, operating the review screen, and generating inquiry context.

## Prerequisites

- A device running Android 15 or later (Min SDK 35)
- The latest APK (installation may require allowing "install from unknown sources")

## Steps

1. **Install** — download and install the latest APK on the device. On first install, allowing "install from unknown sources" may be required.
2. **Download the model (first run only)** — launching the app opens the Model Manager screen (S-M); choose either Gemma 4 E2B (2.58 GB, recommended) or E4B (3.65 GB, higher-accuracy option) to download. Once complete, the app works offline.
3. **Provide a document (S-01)** — on the input screen, load a document via "Open a PDF file," "Take a photo," "Choose from gallery," or "Paste text." For camera or gallery input, the OCR language-selection dialog (12 supported languages) appears after the image is captured. The app can also be opened directly from another app's share action (`ACTION_SEND` / `ACTION_VIEW`); in that case, the text is likewise reflected into the text area, following the normal flow where the "Start analysis" button is pressed (analysis does not start automatically).
4. **Run analysis** — pressing "Start analysis" runs Structured Field Extraction (MF-02, [[DefinedTerm/structured-field-extraction]]) on-device, and the button switches to a progress indicator. On completion, the app navigates to the review screen (S-02). If analysis fails, an error message is shown on S-01 and can be retried.
5. **Operate the review screen (S-02)** — deadlines (red), required documents/actions (orange), items to bring/contacts (blue), and notes (gray) are shown with category-specific colored badges. Quick actions are available: "📅 Add deadline to calendar" (shown only if a deadline exists) and "🗺 View on map" (shown only if an issuer address or name exists). Choosing a language from the translation bar and pressing "Translate" runs [[DefinedTerm/multilingual-translation]] (MF-03, supporting 15 languages); once complete, the display switches to a two-column source/translation layout. The "Ask Gemma 4" field at the bottom of the screen lets you ask about the document's content via [[DefinedTerm/document-chat]] (MF-07); when translation completes, the chat history is cleared and re-initialized in the translated language.
6. **Create an inquiry document (optional)** — tapping "Create Inquiry Document" at the bottom of the review screen navigates to S-04 (if already translated, a two-choice dialog appears asking whether to proceed in the source language or the translated language). Enter the inquiry purpose using a suggested-purpose button or free text, confirm the recipient (auto-filled, but overwritable), and confirm which personal information is checked for masking (Tier 1 is checked by default). Pressing "Confirm Context" immediately generates the context text via [[DefinedTerm/inquiry-context-generation]] (MF-06), and on the inquiry context confirmation screen (S-03) it can be copied or shared with an external AI or a professional via the Android share sheet.

## Notes

After the initial model download, all AI inference runs entirely on-device. Data leaves the device only during the initial model download and when the user explicitly performs a share. The input document has a character limit (16,000 characters); exceeding it shows "Document is too long (limit: 16,000 characters)."
