---
title: "テキスト抽出"
type: "schema:DefinedTerm"
lang: ja
description: "PDF・テキストファイル・カメラ撮影・ギャラリー画像から書類の原文テキストを取得する処理群（TextExtractor / ImageTextExtractor / OcrCorrector）"
termCode: "MF-01"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [オンデバイス処理]
sources:
  - type: file
    path: 'docs/implementation-spec_ja.md'
    hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312
  - type: file
    path: 'docs/implementation-spec.md'
    hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523
  - type: file
    path: 'docs/prompt-spec.md'
    hash: sha256:144c3266a5a9c7f51265c174bccc2f3bc91093f079f90460614d6466d639087a
  - type: file
    path: 'docs/prompt-spec_ja.md'
    hash: sha256:2a73881ef8f5e964725c85c69fe787cfdee469e1957cfced836774474e3e283d
  - type: file
    path: 'docs/test-spec_ja.md'
    hash: sha256:c66a33e49f6591d28e37f1344a38c21ccc3fc4b3ae1e3c59fe9840116dea63bd
  - type: file
    path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
    hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

テキスト抽出（MF-01）は、[[SoftwareApplication/paperwork-navigator]] が入力書類（PDF・テキストファイル・カメラ撮影・ギャラリー画像）からテキストを取得する処理群であり、`TextExtractor`（MF-01a）・`ImageTextExtractor`（MF-01b）・`OcrCorrector`（MF-01c）の3クラスで構成される。抽出結果はいずれも 16,000 文字を上限にトリムされ、直後に `LanguageIdentifier` が `ML Kit Language Identification` で原文言語（BCP-47 コード、信頼度が低い場合は `"und"`）を識別してから MF-02（構造化フィールド抽出）に渡される。

## Usage

**TextExtractor（MF-01a）**: PDF またはテキストファイルの URI から `android.graphics.pdf.PdfRenderer`（API 35、`Page.getPageContent()`）または `InputStream` でテキストを取得する。ページ間は `\n\n` で連結する。テキスト層のない PDF では `ExtractionError.NoPdfTextLayer` を投げ、S-01 上に「このPDFはテキストを読み取れません」を表示する。

**ImageTextExtractor（MF-01b）**: カメラ撮影・ギャラリー画像から ML Kit OCR でテキストを抽出する。`sourceLanguage` に応じて `JapaneseTextRecognizerOptions` / `ChineseTextRecognizerOptions` / `KoreanTextRecognizerOptions` / `TextRecognizerOptions.DEFAULT_OPTIONS`（ラテン文字 9 言語）のいずれかを選択する。対応 12 言語のうち `ru` / `ar` / `th` は ML Kit に対応する OCR モデルがないため言語選択 UI から除外される。`Tasks.await()` で最大 30 秒待機し、タイムアウトまたは抽出結果が空の場合は `ExtractionError.OcrFailed` を投げる。カメラ入力は `GmsDocumentScanner`（SCANNER_MODE_FULL）でスキャン後に言語選択ダイアログを表示し、ギャラリー入力は 1 枚ならパース補正後、2 枚以上なら全枚のパース補正後に言語選択ダイアログを 1 回表示する。いずれか 1 枚でも OCR 失敗した場合は `ExtractionError.OcrFailed` としてエラー表示する。

**OcrCorrector（MF-01c）**: `ImageTextExtractor` の OCR 結果と元画像を Gemma 4 マルチモーダル推論に渡し、`CORRECT: <wrong>|<right>` 形式の置換ペア（または `(none)`）を取得して誤認識を補正する。タイムアウトは 60 秒で、パース失敗・タイムアウト時は例外をキャッチして OCR テキストをそのまま返す（リトライなし、クラッシュしない）。PDF 入力の場合は呼ばれない。画像が不鮮明な場合でも高確信度であれば言語知識に基づく補正が許可されるが、氏名・地名・組織名などの固有名詞は言語規則だけでは唯一の正解形が定まらないため補正の対象外である（例: 「令利7年」→「令和7年」は元号として存在しない語のため補正するが、「山回太郎」は「山回」も姓として成立しうるため「山田太郎」へは補正しない）。

抽出完了後、`LanguageIdentifier.identify()` がテキストの先頭 500 文字から言語識別を行う（1 秒未満）。識別失敗時はすべて `"und"` にフォールバックし、MF-02 の処理を止めない。ML Kit 識別は LLM リソースを使用しないため、LLM の排他制御用 `Mutex` の外側で呼ばれる。

### エラーハンドリングと撮影・ギャラリーフロー

`TextExtractor.extract()` はテキスト層のない PDF で `ExtractionError.NoPdfTextLayer` を、未対応形式で `ExtractionError.UnsupportedFormat` を、I/O エラーで `ExtractionError.IoError` を送出する。`ImageTextExtractor` は OCR失敗またはタイムアウト（30秒）、および抽出結果が空文字の場合に `ExtractionError.OcrFailed` を送出する。カメラ入力は「写真を撮る」タップで `GmsDocumentScanner`（SCANNER_MODE_FULL）を起動し、スキャン完了後に全ページの JPEG URI を取得してから12言語の言語選択ダイアログを表示する。ギャラリー入力は画像選択後、1枚なら遠近補正画面（コーナードラッグ）を1回、2枚以上なら各画像に順次遠近補正を適用したうえで言語選択ダイアログを1回表示する。言語選択後、各画像を順次 OCR し `\n\n` で連結して `TextExtractor.MAX_CHARS`（16,000文字）でトリムする。いずれか1枚でも OCR に失敗した場合は `ExtractionError.OcrFailed` としてエラー表示する。ギャラリー画像の遠近補正画面は `S01CropContent` コンポーネントで実装される。アプリ起動中に新たな `ACTION_SEND` Intent を受け取った場合（`onNewIntent`）は、レビュー画面（S-02）で表示中であっても状態を `Idle` にリセットしてから S-01 に新しい入力を反映する。

### 画像入力の遠近補正（S01CropContent）

ギャラリーから選択した画像は、1枚の場合は選択直後に、2枚以上の場合は各画像に順次、`S01CropContent` による遠近補正（コーナードラッグ）画面を経てから OCR に進む。画像は読み込み時に長辺 2000px を上限に縮小され（アスペクト比維持）、4隅のフラクショナル座標（初期値は各辺 5%/95% のマージン）をドラッグで調整できる。ドラッグ開始時は指先に最も近い頂点をハンドル半径の3倍以内から検出し、`applyPerspectiveCorrection()` が Android の `Matrix.setPolyToPoly()` で4点対応の透視変換を行う。出力画像のサイズは4隅間のピクセル距離から算出され、元画像の2倍を上限にクランプされる。カメラ入力（ML Kit Document Scanner）は、この遠近補正ステップを経由せずそのまま OCR 言語選択ダイアログに進む。

### OCR言語選択ダイアログ（OcrLanguagePickerDialog）

言語選択ダイアログは `SupportedLanguage.entries` のうち `supportsOcr = true` の言語のみを候補とし、日本語を先頭にソートして表示する。

### 他アプリからの共有・オープン（IncomingDocumentViewModel）

Activity スコープの `IncomingDocumentViewModel` が発行するイベントを画面側で購読し、新しいイベントを受け取るたびに `Idle`/`Error` 以外の状態であれば解析セッションをリセットしてから処理する。イベント種別は `PdfUri`（送信元アプリが渡すファイル名ヒントを優先し、なければ `ContentResolver` へのクエリでファイル名解決を試み、例外発生時（送信元アプリがストリームアクセスのみ許可し `SecurityException` を投げる場合など）は無視して `null` にフォールバックしたうえで `TextExtractor.extract()` を実行）・`RawText`（テキストをそのまま反映）・`ImageUri`（遠近補正画面に直行）の3種類がある。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
