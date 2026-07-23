---
title: "Paperwork Navigator"
type: "schema:SoftwareApplication"
lang: en
description: "A privacy-first Android app that reads, analyzes, and translates administrative, medical, and everyday-life documents written in a language other than the user's native tongue, entirely on-device, and generates PII-masked consultation context"
applicationCategory: "Productivity"
operatingSystem: "Android 15+"
softwareVersion: ""
downloadUrl: "https://github.com/joyk0117/Paperwork-Navigator/releases/latest/download/app-debug.apk"
featureList:
  - "Text Extraction (MF-01)"
  - "OCR Correction (MF-01c)"
  - "Structured Field Extraction (MF-02)"
  - "Multilingual Translation (MF-03)"
  - "Review Screen (MF-04)"
  - "PII Masking (MF-05)"
  - "Inquiry Context Generation (MF-06)"
  - "Document Chat (MF-07)"
tags: [On-device AI, Privacy-first design]
translated_from: .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Paperwork Navigator is a privacy-first document navigator for anyone who has received an administrative, medical, or everyday-life document written in a language other than their native tongue — immigrants, international students, expatriate workers, refugees, and others. It is forked from Google AI Edge Gallery, adding a Document Review task on top. It reads, analyzes, and translates a received document entirely on the device, masks PII (personally identifiable information), and then generates consultation context for a professional or an external AI. The design ensures personal information never leaves the device.

## Overview

The app targets people facing challenges such as: deadlines, required documents, and penalties written in a foreign language that leave them unsure what to do; the risk that pasting a document into an online translation service sends sensitive information — names, addresses, ID numbers — to an external server; the difficulty of organizing importance, deadlines, and required actions even once a document is translated; and the risk of handing a document containing personal information to a professional or an AI just to get advice. Paperwork Navigator resolves this "I want to consult someone, but I don't want to hand over my personal information" trade-off by running all inference on-device.

The processing pipeline runs: document ingestion (PDF, camera, gallery, or share from another app) → text extraction (`PdfRenderer` / ML Kit OCR) → ML Kit entity extraction and Gemma 4 field extraction → entity semantic labeling by Gemma 4 → PII masking (non-LLM, on-device) → the review screen. From the review screen, the flow continues on to document chat, multilingual translation, and inquiry context generation. After the initial model download, all AI inference runs entirely on-device; data leaves the device only during the initial model download and when the user explicitly performs a share.

The privacy design is organized around a 3-tier system called [[DefinedTerm/pii-tier-classification]]: Tier 1 (name, address, date of birth, national ID number, account number, etc.) never leaves the device; Tier 2 (issuer contact info, deadlines, amounts, etc.) can only be sent externally in masked form and with user consent; Tier 3 (document title, importance flag, translated text, etc.) contains no PII. All Gemma 4 inference runs entirely on-device via LiteRT-LM, and the app requests no external storage read/write permission.

In the MVP, storage encryption (relying solely on the OS sandbox — Keystore + AES-256-GCM is not yet implemented), automatic deletion (TTL), a pre-share confirmation dialog, and sending data to a cloud LLM are all unsupported; these are positioned in the Phase 2 plan. Disabling backups (`android:allowBackup="false"` in the AndroidManifest) is also still required. Document deletion is limited to per-document deletion via `DocumentRepository.delete(docId)`, or deletion of all of `filesDir` by uninstalling the app; the MVP does not implement a document management UI (list/delete screen). Also out of scope for the MVP (planned for P2 or later) are: web search to supplement policy information, escalation to a cloud LLM, agentic RAG across multiple documents, legal advice or regulatory interpretation, and multiple user accounts.

There are four main reasons for adopting Gemma 4. First, it enables high-quality on-device inference that turns document content into structured, actionable information — not just translation, but also summarization, deadline extraction, action items, warnings, OCR correction, and document-chat responses. Second, it fits a multimodal, tool-using workflow that doesn't replace deterministic tools like OCR and entity extraction, but instead pairs with them to supply context and meaning. Third, it offers a high degree of controllability — consistent structured output and predictable behavior — which matters for a product handling sensitive documents. Fourth, it supports the project's central story of AI that remains useful under the constraints of privacy, the edge, and low-connectivity environments.

The commitment to on-device inference is the core of Paperwork Navigator's value to users: running Gemma 4 on LiteRT-LM eliminates the need for server round-trips or sending the document off-device after the initial model download. Technically, this keeps the app usable in unstable network conditions; socially, it builds product trust for sensitive workflows involving administrative documents, benefits, identity, and family information. Being mobile is not a stylistic choice — it's where the benefits of privacy and accessibility actually become real.

The current prototype demonstrates the product's overall shape on Android, centered on Japanese administrative documents (a use case with particularly high need among foreign residents). Future plans include broader multilingual evaluation, expanding the range of supported document types, strengthening benchmark validation, and usability testing with users who actually handle administrative documents in a foreign language. Application to other domains where privacy, comprehensibility, and access intersect — medical documents, school-related documents, immigration-related documents, social security applications, and more — is also under consideration.

## Features

| ID | Feature | Details |
|----|------|------|
| MF-01 | Text Extraction | Text PDFs, text files, camera photos, and gallery images (12-language OCR support). Can also be launched directly via another app's `ACTION_SEND` / `ACTION_VIEW`. See [[DefinedTerm/text-extraction]] for details |
| MF-01c | OCR Correction | After camera/image input, Gemma 4 multimodal inference cross-checks the original image against the OCR text to correct misrecognitions (optional) |
| MF-02 | Structured Field Extraction | ML Kit Entity Extraction extracts dates, addresses, phone numbers, amounts, and more; Gemma 4 (EntityAnnotator) assigns context labels; Gemma 4 field extraction obtains deadlines, actions, and warnings. See [[DefinedTerm/structured-field-extraction]] for details |
| MF-03 | Multilingual Translation | Supports 15 languages. Displays source and translation side by side in two columns. See [[DefinedTerm/multilingual-translation]] for details |
| MF-04 | Review Screen | Displays deadlines, required documents, and warnings with category-specific colored badges. Provides quick actions for adding to the calendar (`CalendarContract`) and viewing on a map (`geo:` URI). See [[DefinedTerm/review-screen]] for details |
| MF-05 | PII Masking | See [[DefinedTerm/pii-masking]] for details |
| MF-06 | Inquiry Context Generation | See [[DefinedTerm/inquiry-context-generation]] for details |
| MF-07 | Document Chat | Passes ReviewResult as context to Gemma 4 and runs document-related Q&A on-device. See [[DefinedTerm/document-chat]] for details |

The technology stack: LiteRT-LM as the LLM runtime; Gemma 4 E2B (2.58 GB, recommended) / E4B (3.65 GB, higher-accuracy option) as the model; Jetpack Compose for UI; Hilt for DI; the Android standard `PdfRenderer` (API 35) for PDF text extraction; ML Kit Text Recognition for OCR (image/camera); Gemma 4 multimodal inference for OCR correction (MF-01c); ML Kit Entity Extraction + Gemma 4 for entity extraction; Gemma 4 for entity annotation; ML Kit Language Identification for language identification; ML Kit Document Scanner for camera scanning; CameraX for the camera preview; and ViewModel + StateFlow for state management. The validation environment is Min SDK 35 (Android 15) on a Google Pixel 9 (12 GB RAM). Input text is trimmed to a 16,000-character cap. Evaluation and demos primarily use Japanese administrative documents (a child allowance current-status report, Edogawa Ward, fiscal 2025 sample) as sample data, though the app targets documents of any language or type. Licensed under the Apache License 2.0.

Approximate inference times on a Pixel 9: MF-01a text extraction (PDF) under 1 second, MF-01b OCR (ML Kit) under 5 seconds, MF-02 field extraction (Gemma 4) 60-150 seconds, MF-03 translation (Gemma 4) 15-60 seconds, MF-06a inquiry-purpose candidate generation (Gemma 4) 5-15 seconds, MF-06 context text generation (no LLM needed) under 1 second, and MF-07 chat response (Gemma 4, single turn, streaming) 3-10 seconds — these are estimates intended to be measured and tuned on real devices.

Testing spans four levels — unit, integration, UI, and E2E — using JUnit 4/5, Kotlin Coroutines Test, Robolectric (integration tests), Compose UI Testing / Espresso (UI tests), and MockK or Mockito-Kotlin (mocking). LLM-dependent processing such as MF-02/03/06/07 is unit-tested with `LlmModelHelper` mocked, with the actual Gemma 4 model used only in on-device E2E tests.

See [[HowTo/paperwork-navigator-usage]] for detailed usage instructions.
