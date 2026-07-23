---
title: "Text Extraction"
type: "schema:DefinedTerm"
lang: en
description: "A group of processes that obtain a document's source text from a PDF, text file, camera photo, or gallery image (TextExtractor / ImageTextExtractor / OcrCorrector)"
termCode: "MF-01"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [On-device processing]
translated_from: .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Text Extraction (MF-01) is the group of processes by which [[SoftwareApplication/paperwork-navigator]] obtains text from an input document (PDF, text file, camera photo, or gallery image), consisting of three classes: `TextExtractor` (MF-01a), `ImageTextExtractor` (MF-01b), and `OcrCorrector` (MF-01c). Every extraction result is trimmed to a 16,000-character cap, after which `LanguageIdentifier` immediately identifies the source language (a BCP-47 code, or `"und"` when confidence is low) via `ML Kit Language Identification` before handing off to MF-02 (Structured Field Extraction).

## Usage

**TextExtractor (MF-01a)**: obtains text from a PDF or text-file URI via `android.graphics.pdf.PdfRenderer` (API 35, `Page.getPageContent()`) or an `InputStream`. Pages are joined with `\n\n`. For a PDF with no text layer, it throws `ExtractionError.NoPdfTextLayer` and S-01 shows "This PDF's text could not be read."

**ImageTextExtractor (MF-01b)**: extracts text from a camera photo or gallery image using ML Kit OCR. Depending on `sourceLanguage`, it selects one of `JapaneseTextRecognizerOptions` / `ChineseTextRecognizerOptions` / `KoreanTextRecognizerOptions` / `TextRecognizerOptions.DEFAULT_OPTIONS` (9 Latin-script languages). Of the 12 supported languages, `ru` / `ar` / `th` are excluded from the language-selection UI because ML Kit has no corresponding OCR model. It waits up to 30 seconds via `Tasks.await()`; on timeout or an empty extraction result, it throws `ExtractionError.OcrFailed`. Camera input is scanned via `GmsDocumentScanner` (SCANNER_MODE_FULL) and then shows the language-selection dialog; gallery input shows the language-selection dialog once, after perspective correction on a single image, or after perspective correction on all images when there are two or more. If OCR fails on even one image, it is treated as `ExtractionError.OcrFailed` and shown as an error.

**OcrCorrector (MF-01c)**: passes `ImageTextExtractor`'s OCR result and the original image to Gemma 4 multimodal inference, obtaining replacement pairs in `CORRECT: <wrong>|<right>` format (or `(none)`) to correct misrecognitions. It has a 60-second timeout; on parse failure or timeout it catches the exception and returns the OCR text unchanged (no retry, no crash). It is not called for PDF input. Even when an image is blurry, correction based on language knowledge is allowed if confidence is high, but proper nouns such as personal names, place names, and organization names are excluded from correction since language rules alone cannot determine a unique correct form (e.g., "令利7年" is corrected to "令和7年" since it is not a valid Japanese era name, but "山回太郎" is not corrected to "山田太郎" since "山回" could itself be a valid surname).

After extraction completes, `LanguageIdentifier.identify()` performs language identification on the first 500 characters of the text (under 1 second). On identification failure, it falls back entirely to `"und"` without blocking MF-02's processing. Since ML Kit identification uses no LLM resources, it is called outside the `Mutex` used for LLM mutual exclusion.

### Error handling and the camera/gallery flow

`TextExtractor.extract()` throws `ExtractionError.NoPdfTextLayer` for a PDF with no text layer, `ExtractionError.UnsupportedFormat` for an unsupported format, and `ExtractionError.IoError` for an I/O error. `ImageTextExtractor` throws `ExtractionError.OcrFailed` on OCR failure or timeout (30 seconds), or when the extraction result is an empty string. Camera input launches `GmsDocumentScanner` (SCANNER_MODE_FULL) via a "Take a photo" tap; once scanning completes and the JPEG URIs for all pages are obtained, the 12-language language-selection dialog is shown. Gallery input shows a perspective-correction screen (corner dragging) once for a single selected image, or applies perspective correction sequentially to each image for two or more, after which the language-selection dialog is shown once. After language selection, each image is OCR'd in sequence, the results joined with `\n\n` and trimmed to `TextExtractor.MAX_CHARS` (16,000 characters). If OCR fails on even one image, it is treated as `ExtractionError.OcrFailed` and shown as an error. The gallery image perspective-correction screen is implemented as the `S01CropContent` component. When a new `ACTION_SEND` Intent is received while the app is running (`onNewIntent`), even if the review screen (S-02) is currently displayed, state is reset to `Idle` before the new input is reflected on S-01.

### Perspective correction for image input (S01CropContent)

Images selected from the gallery go through a perspective-correction (corner-dragging) screen implemented by `S01CropContent` before OCR — immediately after selection for a single image, or sequentially for each image when there are two or more. Images are downscaled on load to a maximum long-edge dimension of 2000px (preserving aspect ratio), and the fractional coordinates of the 4 corners (initially a 5%/95% margin on each edge) can be adjusted by dragging. At drag start, the vertex nearest the touch point is detected within 3x the handle radius, and `applyPerspectiveCorrection()` performs a 4-point perspective transform using Android's `Matrix.setPolyToPoly()`. The output image size is computed from the pixel distance between the 4 corners, clamped to at most twice the size of the original image. Camera input (ML Kit Document Scanner) skips this perspective-correction step and proceeds directly to the OCR language-selection dialog.

### OCR language selection dialog (OcrLanguagePickerDialog)

The language-selection dialog offers only the languages in `SupportedLanguage.entries` where `supportsOcr = true` as candidates, sorted with Japanese first.

### Sharing/opening from other apps (IncomingDocumentViewModel)

The Activity-scoped `IncomingDocumentViewModel` publishes events that the screen subscribes to; whenever a new event arrives, if the current state is anything other than `Idle`/`Error`, the analysis session is reset before processing. There are 3 event types: `PdfUri` (prefers a filename hint supplied by the sending app; if absent, attempts filename resolution via a `ContentResolver` query, falling back to `null` and ignoring the exception if one occurs — e.g. when the sending app only allows stream access and throws `SecurityException` — before running `TextExtractor.extract()`), `RawText` (reflects the text as-is), and `ImageUri` (goes straight to the perspective-correction screen).

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
