# Paperwork Navigator テスト仕様書

> バージョン: 0.1.0
> 作成日: 2026-05-07
> 対象: MVP（P1 機能 MF-01〜MF-07）
> 依拠: 実装仕様書 v0.1.0

---

## 目次

1. [テスト方針・スコープ](#1-テスト方針スコープ)
2. [テスト環境](#2-テスト環境)
3. [MF-01 TextExtractor テスト](#3-mf-01-textextractor-テスト)
4. [MF-02 FieldExtractor テスト](#4-mf-02-fieldextractor-テスト)
5. [MF-03 Translator テスト](#5-mf-03-translator-テスト)
6. [MF-05 PiiMasker テスト](#6-mf-05-piimasker-テスト)
7. [MF-06 EscalationPackageGenerator テスト](#7-mf-06-escalationpackagegenerator-テスト)
8. [MF-07 DocumentChatSession テスト](#8-mf-07-documentchatsession-テスト)
9. [DocumentReviewViewModel 状態遷移テスト](#9-documentreviewviewmodel-状態遷移テスト)
10. [画面テスト（S-01 / S-02 / S-03）](#10-画面テストs-01--s-02--s-03)
11. [エラーハンドリングテスト](#11-エラーハンドリングテスト)
12. [EscalationPackage.toPlainText() テスト](#12-escalationpackagetoplaintext-テスト)
13. [DocumentRepository テスト](#13-documentrepository-テスト)
14. [非機能テスト](#14-非機能テスト)
15. [EntityExtractor テスト](#15-entityextractor-テスト)
16. [テストデータ](#16-テストデータ)
17. [Intent 受け取りテスト](#17-intent-受け取りテスト)

---

## 1. テスト方針・スコープ

### 1.1 テストレベル

| レベル | 対象 | ツール |
|--------|------|--------|
| ユニットテスト | 処理ステップ（`processing/`）、データモデル、ViewModel ロジック | JUnit 4 / 5、Kotlin Coroutines Test |
| インテグレーションテスト | ViewModel + 処理ステップ間の連携、Repository + ファイルシステム | JUnit 4、Robolectric |
| UI テスト | Compose 画面の表示・操作 | Compose UI Testing、Espresso |
| E2E テスト | S-01 → S-02 → S-03 全体フロー（実機） | 手動テスト / UIAutomator |

### 1.2 スコープ外（MVP 対象外）

- カメラ撮影・OCR 処理
- クラウド LLM エスカレーション
- 暗号化ストレージ
- 複数書類横断検索

### 1.3 LLM 依存処理のテスト方針

MF-02・MF-03・MF-06・MF-07 は実際の Gemma 4 モデルを使わず、`LlmModelHelper` をモック化してユニットテストを行う。実機での E2E テストで実モデルを使用する。

---

## 2. テスト環境

| 項目 | 値 |
|------|----|
| 実機 | Google Pixel 9（RAM 12 GB、Android 15） |
| minSdk | 35 |
| ユニットテスト JVM | JDK 17 |
| モック | MockK または Mockito-Kotlin |
| テスト用 PDF | `edogawa_R7_genkyo_kinyuurei.pdf`（テキスト層あり） |
| タイムアウトテストの実装方法 | 実際に 150 秒 / 60 秒 / 30 秒 / 20 秒待つと CI が遅くなるため、`kotlinx.coroutines.test.TestCoroutineScheduler.advanceTimeBy()` で仮想時間を進めるか、モックで即時タイムアウト例外をスローして代替する |

---

## 3. MF-01 TextExtractor テスト

### TC-01-01: テキスト層あり PDF からの正常抽出

| 項目 | 内容 |
|------|------|
| 対象 | `TextExtractor.extract()` |
| 種別 | インテグレーション（Robolectric） |
| 前提 | テスト用 PDF（テキスト層あり）を `assets/` に配置 |
| 手順 | 1. テスト用 PDF の URI を生成する<br>2. `TextExtractor.extract(context, uri)` を呼ぶ |
| 期待結果 | 空でない文字列が返る<br>ページ間が `\n\n` で連結されている |

### TC-01-02: 複数ページ PDF の連結

| 項目 | 内容 |
|------|------|
| 前提 | 2ページ以上のテキスト層あり PDF |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | 各ページのテキストが `\n\n` で連結されている |

### TC-01-03: テキスト層なし PDF

| 項目 | 内容 |
|------|------|
| 前提 | スキャン画像のみの PDF（テキスト層なし） |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | `ExtractionError.NoPdfTextLayer` がスローされる |

### TC-01-04: テキストファイル（UTF-8）

| 項目 | 内容 |
|------|------|
| 前提 | UTF-8 エンコードのプレーンテキストファイル |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | ファイルの内容がそのまま返る |

### TC-01-05: 8,000 文字超過のトリム

| 項目 | 内容 |
|------|------|
| 前提 | 9,000 文字のテキストを含む PDF |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | 返り値が最大 8,000 文字である<br>先頭から 8,000 文字が保持されている |

### TC-01-06: 非対応フォーマット

| 項目 | 内容 |
|------|------|
| 前提 | `.docx` ファイルの URI |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | `ExtractionError.UnsupportedFormat` がスローされる |

### TC-01-07: I/O エラー

| 項目 | 内容 |
|------|------|
| 前提 | 存在しない URI |
| 手順 | `TextExtractor.extract()` を呼ぶ |
| 期待結果 | `ExtractionError.IoError` がスローされる |

### TC-01-08: タブ区切りテキストのスペース正規化

| 項目 | 内容 |
|------|------|
| 対象 | `TextExtractor.normalizeSpaces()` |
| 種別 | ユニット |
| 前提 | 英語 PDF の `PdfRenderer.Page.getTextContents()` がタブ文字区切りのテキストを 1 件の `TextContent` として返すケースがある（`bounds=[]` で位置情報なし） |
| 手順 | `TextExtractor.normalizeSpaces("I\tconsent\tform")` を直接呼ぶ |
| 期待結果 | `"I consent form"` が返る |

### TC-01-09: 水平空白の一括正規化

| 項目 | 内容 |
|------|------|
| 対象 | `TextExtractor.normalizeSpaces()` |
| 種別 | ユニット |
| 手順 | `TextExtractor.normalizeSpaces("a\t  　b")` を呼ぶ（タブ・通常スペース・非改行スペース・全角スペースの混在） |
| 期待結果 | `"a b"` が返る（連続する水平空白が 1 スペースに集約される） |

---

## 4. MF-02 FieldExtractor テスト

### TC-02-01: 正常な JSON 抽出

| 項目 | 内容 |
|------|------|
| 対象 | `FieldExtractor.extract()` |
| 種別 | ユニット（LlmModelHelper モック） |
| 前提 | `llmHelper.runInference()` が仕様書 §6.6 の few-shot example JSON を返す |
| 手順 | `FieldExtractor.extract(model, sampleText, {})` を呼ぶ |
| 期待結果 | `ReviewResult` が正しくデシリアライズされる<br>`docType == "児童手当現況届"`<br>`importance == "high"`<br>`translation == null` |

### TC-02-02: pii_candidates の全フィールド検証

| 項目 | 内容 |
|------|------|
| 前提 | モックが few-shot example JSON を返す |
| 期待結果 | `pii_candidates` が 4 件<br>各 PiiSpan の `id`, `labelJa`, `spanText`, `category`, `maskRecommended` が正しい |

### TC-02-03: deadline.date の ISO 8601 形式

| 項目 | 内容 |
|------|------|
| 前提 | モックが `"date": "2025-06-30"` を含む JSON を返す |
| 期待結果 | `reviewResult.deadline.date == "2025-06-30"` |

### TC-02-04: deadline.date が null

| 項目 | 内容 |
|------|------|
| 前提 | モックが `"date": null` を含む JSON を返す |
| 期待結果 | `reviewResult.deadline.date == null` |

### TC-02-05: JSON パースエラー時のリトライ（1 回目失敗、2 回目成功）

| 項目 | 内容 |
|------|------|
| 前提 | 1 回目は不正 JSON、2 回目は正常 JSON をモックが返す |
| 手順 | `FieldExtractor.extract()` を呼ぶ |
| 期待結果 | リトライが発生し、最終的に `ReviewResult` が返る<br>合計 `runInference()` 呼び出しが 2 回 |

### TC-02-06: JSON パースエラーが 3 回連続

| 項目 | 内容 |
|------|------|
| 前提 | モックが常に不正 JSON を返す |
| 期待結果 | `FieldExtractionError.JsonParseError` がスローされる<br>`runInference()` の呼び出しが最大 3 回 |

### TC-02-07: モデル未初期化

| 項目 | 内容 |
|------|------|
| 前提 | `llmHelper` がモデル未初期化状態をシミュレート |
| 期待結果 | `FieldExtractionError.ModelNotInitialized` がスローされる |

### TC-02-08: 推論タイムアウト（150 秒）

| 項目 | 内容 |
|------|------|
| 前提 | `llmHelper.runInference()` が 150 秒以上応答しない |
| 期待結果 | `FieldExtractionError.InferenceError` がスローされる |

### TC-02-09: onProgress コールバック

| 項目 | 内容 |
|------|------|
| 前提 | モックが複数トークンをストリーム |
| 期待結果 | `onProgress` コールバックが複数回呼ばれる |

### TC-02-10: conditions.applicable が null

| 項目 | 内容 |
|------|------|
| 前提 | モックが `"applicable": null` を含む JSON を返す |
| 期待結果 | `conditions[0].applicable == null` |

### TC-02-11: MF-02 への入力テキストが 6,000 文字以内にトリムされる

| 項目 | 内容 |
|------|------|
| 種別 | ユニット（ViewModel） |
| 前提 | `TextExtractor` が 7,000 文字のテキストを返す |
| 期待結果 | `FieldExtractor.extract()` に渡されるテキストが 6,000 文字以内にトリムされている<br>（仕様書 §9.4: MF-02 入力上限 6,000 文字） |

---

## 5. MF-03 Translator テスト

### TC-03-01: 英語翻訳の正常動作

| 項目 | 内容 |
|------|------|
| 対象 | `Translator.translate()` |
| 種別 | ユニット（モック） |
| 前提 | `reviewResult` にサンプルデータ、`targetLanguage = "en"` |
| 手順 | `Translator.translate(model, reviewResult, "en")` を呼ぶ |
| 期待結果 | 返り値の `translation.language == "en"`<br>`translation.summary` が非空文字列<br>`translation.actionItems` の件数が元の `actionItems` と一致 |

### TC-03-02: 翻訳後も id フィールドが保持される

| 項目 | 内容 |
|------|------|
| 期待結果 | `translation.actionItems[].id` が元の `actionItems[].id` と一致 |

### TC-03-03: required_docs.note_ja が null のとき translation.note が null

| 項目 | 内容 |
|------|------|
| 前提 | `required_docs[0].noteJa = null` |
| 期待結果 | `translation.requiredDocs[0].note == null` |

### TC-03-04: 翻訳対象外フィールドが変更されない

| 項目 | 内容 |
|------|------|
| 期待結果 | 翻訳後の `reviewResult.piiCandidates`、`conditions`、`id` 系フィールドが元の値と同一<br>（仕様書 §5.1 注釈: `conditions` は翻訳対象外） |

### TC-03-05: 翻訳失敗

| 項目 | 内容 |
|------|------|
| 前提 | `llmHelper.runInference()` が例外をスロー |
| 期待結果 | `Translator.translate()` が例外をスローする<br>ViewModel 側で翻訳失敗フラグが立ち、`translation == null` の状態で S-02 に遷移 |

### TC-03-06: 15 言語すべてのコードを受け付ける

| 項目 | 内容 |
|------|------|
| 手順 | `targetLanguage` に `"ja"`, `"en"`, `"zh"`, `"ko"`, `"es"`, `"fr"`, `"de"`, `"it"`, `"pt"`, `"ru"`, `"pl"`, `"nl"`, `"ar"`, `"th"`, `"tr"` をそれぞれ渡す |
| 期待結果 | いずれも正常に実行され、`translation.language` が渡した言語コードと一致 |

---

## 6. MF-05 PiiMasker テスト

### TC-05-01: 推奨スパンのデフォルトマスク

| 項目 | 内容 |
|------|------|
| 対象 | `PiiMasker.mask()` |
| 種別 | ユニット |
| 前提 | `spanText = "山田太郎"`、`sourceField = "applicant_name"`、`maskRecommended = true`、`userOverride = null` |
| 手順 | `PiiMasker.mask(sourceText, listOf(span))` を呼ぶ |
| 期待結果 | `maskedText` 内の "山田太郎" が `[Applicant name]` に置換されている |

### TC-05-02: userOverride = true（強制マスク）

| 項目 | 内容 |
|------|------|
| 前提 | `maskRecommended = false`、`userOverride = true` |
| 期待結果 | スパンがマスクされる |

### TC-05-03: userOverride = false（強制除外）

| 項目 | 内容 |
|------|------|
| 前提 | `maskRecommended = true`、`userOverride = false` |
| 期待結果 | スパンがマスクされない<br>`skippedSpans` にスパンが含まれる |

### TC-05-04: maskRecommended = false かつ userOverride = null（デフォルト除外）

| 項目 | 内容 |
|------|------|
| 前提 | `maskRecommended = false`、`userOverride = null` |
| 期待結果 | スパンがマスクされない<br>`appliedSpans` にスパンが含まれない |
| 備考 | 仕様書 §5.3 の `skippedSpans` は「ユーザーが除外したスパン」と定義されており、ユーザー操作なし（`userOverride = null`）のデフォルト除外がこのリストに含まれるかは仕様書に明示がない。実装確定後に期待結果を `skippedSpans` への収録可否と合わせて更新すること |

### TC-05-05: 同一テキストが複数箇所に出現する場合はすべてマスク

| 項目 | 内容 |
|------|------|
| 前提 | `sourceText = "山田太郎と山田太郎は同一人物"`、`spanText = "山田太郎"` |
| 期待結果 | 2 箇所ともマスクされる |

### TC-05-06: スパンが原文に存在しない場合は unmatchedSpans に記録

| 項目 | 内容 |
|------|------|
| 前提 | `spanText = "存在しない文字列"` |
| 期待結果 | `maskedText` は変更なし<br>`unmatchedSpans` にスパンが含まれる<br>`appliedSpans` にスパンが含まれない |

### TC-05-07: 正規化後マッチ（空白・改行の差異）

| 項目 | 内容 |
|------|------|
| 前提 | `sourceText` の氏名部分に全角スペースあり<br>`spanText` は通常スペース区切り |
| 期待結果 | 正規化後マッチでマスクされる |

### TC-05-08: 複数スパンのマスク順序

| 項目 | 内容 |
|------|------|
| 前提 | 氏名、住所、生年月日の 3 スパン |
| 期待結果 | すべてマスクされる<br>`appliedSpans` が 3 件 |

### TC-05-09: remask でユーザー操作後の再マスク

| 項目 | 内容 |
|------|------|
| 前提 | 初回 `mask()` 後、スパンの `userOverride` を変更する |
| 手順 | `PiiMasker.remask(originalText, updatedSpans)` を呼ぶ |
| 期待結果 | 変更後の `userOverride` に従った新たなマスク結果が返る |

### TC-05-10: maskToken() がラベル付きトークンを返す

| 項目 | 内容 |
|------|------|
| 手順 | `sourceField` を設定した各スパンで `PiiMasker.mask()` を呼ぶ |
| 期待結果 | `sourceField = "applicant_name"` のスパン → `[Applicant name]` に置換される<br>`sourceField = "issuer_address"` のスパン → `[Issuer address]` に置換される<br>`sourceField = null` のスパン → `[■■■]` にフォールバックされる |

---

## 7. MF-06 EscalationPackageGenerator テスト

### TC-06-01: 正常なパッケージ生成

| 項目 | 内容 |
|------|------|
| 対象 | `EscalationPackageGenerator.generate()` |
| 種別 | ユニット（モック） |
| 前提 | モックが有効な JSON（`consultation_summary`, `timeline`, `ai_hypotheses`）を返す |
| 手順 | `generate(model, maskResult, reviewResult, "", emptyList(), "en")` を呼ぶ |
| 期待結果 | `EscalationPackage` が返る<br>`consultationSummary` が非空<br>`language == "en"` |

### TC-06-02: key_points の言語優先順位（翻訳済みフィールド優先）

| 項目 | 内容 |
|------|------|
| 前提 | `reviewResult.translation` が存在する |
| 期待結果 | `key_points[].description` に翻訳済みフィールドの値が使われる |

### TC-06-03: key_points の言語優先順位（翻訳なし → _ja フォールバック）

| 項目 | 内容 |
|------|------|
| 前提 | `reviewResult.translation == null` |
| 期待結果 | `key_points[].description` に `_ja` フィールドの値が使われる |

### TC-06-04: maskedFields にマスクしたカテゴリ一覧が含まれる

| 項目 | 内容 |
|------|------|
| 前提 | `appliedSpans` に name, address, dob の 3 カテゴリ |
| 期待結果 | `maskedFields` に `["name", "address", "dob"]` が含まれる（順序問わず） |

### TC-06-05: relatedDocuments に ReviewResult.docType が収録される

| 項目 | 内容 |
|------|------|
| 期待結果 | `relatedDocuments[0].name == reviewResult.docType` |

### TC-06-06: chatHistory がそのまま収録される

| 項目 | 内容 |
|------|------|
| 前提 | `chatMessages` に 2 件（user, assistant） |
| 期待結果 | `chatHistory` の role / content が元データと一致 |

### TC-06-07: userNotes が空のとき

| 項目 | 内容 |
|------|------|
| 前提 | `userNotes = null` |
| 期待結果 | `EscalationPackage.userNotes == null`<br>`toPlainText()` のセクション 7 に "(メモなし)" が出力される |
| 備考 | 空文字 `""` と `null` の扱いは実装仕様書 §5.5 が `"string or null"` とのみ記載し未定義。実装時に空文字を `null` に正規化するかを決定し、TC-TXT-04 と合わせて確定すること |

### TC-06-08: conditions が applicable=null のもののみプロンプトに渡される

| 項目 | 内容 |
|------|------|
| 前提 | `conditions` に `applicable=true` 1 件・`applicable=null` 1 件 |
| 期待結果 | モックへのプロンプト引数の `{conditions}` に `applicable=null` の項目のみ含まれる |

### TC-06-09: conditions が空のとき "None" が渡される

| 項目 | 内容 |
|------|------|
| 前提 | `applicable=null` の条件が 0 件 |
| 期待結果 | プロンプトの `{conditions}` が `"None"` |

### TC-06-10: maskedSourceText が maskResult.maskedText と一致する

| 項目 | 内容 |
|------|------|
| 前提 | `maskResult.maskedText = "受給者 [Applicant name]、住所 [Applicant address]..."` |
| 手順 | `generate()` を呼ぶ |
| 期待結果 | `escalationPackage.maskedSourceText == maskResult.maskedText` |

---

## 8. MF-07 DocumentChatSession テスト

### TC-07-01: initialize 後の最初のメッセージ（英語）

| 項目 | 内容 |
|------|------|
| 対象 | `DocumentChatSession.initialize()`, `sendMessage()` |
| 種別 | ユニット（モック） |
| 前提 | `targetLanguage = "en"` |
| 手順 | `initialize()` 後 `sendMessage()` を呼ぶ |
| 期待結果 | `getChatHistory()` にユーザーとアシスタントのメッセージが 1 件ずつ含まれる |

### TC-07-02: 初回アシスタントメッセージの言語別切り替え

| 項目 | 内容 |
|------|------|
| 手順 | `targetLanguage` に `"ja"`, `"en"`, `"zh"`, `"ko"` を渡して各 `initialize()` を呼ぶ |
| 期待結果 | 各言語の初回メッセージが仕様書 §6.5 の表の通りになっている |

### TC-07-03: チャット履歴の蓄積

| 項目 | 内容 |
|------|------|
| 手順 | `sendMessage()` を 3 回呼ぶ |
| 期待結果 | `getChatHistory()` が 3 往復（6 件）の履歴を返す |

### TC-07-04: clear() 後に履歴がリセットされる

| 項目 | 内容 |
|------|------|
| 手順 | `sendMessage()` を 2 回呼んだ後 `clear()` を呼ぶ |
| 期待結果 | `getChatHistory()` が空リストを返す |

### TC-07-05: 20 ターン上限

| 項目 | 内容 |
|------|------|
| 手順 | `sendMessage()` を 11 回呼ぶ（仕様書 §9.4: 20 ターン = Q&A 10往復 = `sendMessage()` 10回が上限） |
| 期待結果 | 1〜10 回目は正常に処理される<br>11 回目の呼び出しで上限到達が検知され、「チャット履歴の上限に達しました」状態になる |

### TC-07-06: 累計 4,000 文字上限

| 項目 | 内容 |
|------|------|
| 手順 | 1 メッセージで 2,100 文字の入力を 2 回行う |
| 期待結果 | 上限到達が検知され、新規入力が無効化される |

### TC-07-07: onToken コールバックのストリーミング

| 項目 | 内容 |
|------|------|
| 前提 | モックが複数トークンをストリーム |
| 期待結果 | `onToken` コールバックが複数回呼ばれる<br>返り値 `ChatMessage.content` が全トークンを連結した文字列 |

### TC-07-08: ChatMessage の role と id

| 項目 | 内容 |
|------|------|
| 期待結果 | `sendMessage()` の戻り値の `role == ChatRole.ASSISTANT`<br>`id` が UUID 形式（非空） |

### TC-07-09: システムプロンプトに piiSpans の spanText が含まれない

| 項目 | 内容 |
|------|------|
| 前提 | `reviewResult.piiCandidates` に "山田太郎" あり（piiSpan の spanText として登録） |
| 期待結果 | `initialize()` でモックに渡されるシステムプロンプトに `piiSpans[].spanText`（"山田太郎" 等）が含まれない。ただし `sourceText` はシステムプロンプトに含まれる（オンデバイス推論のため許容） |

### TC-07-10: システムプロンプトに3文制限が含まれる

| 項目 | 内容 |
|------|------|
| 対象 | `PromptBuilder.mf07SystemPrompt()` |
| 種別 | ユニット |
| 手順 | 任意の `ReviewResult` と `targetLanguage` を渡して `mf07SystemPrompt()` を呼ぶ |
| 期待結果 | 返り値のシステムプロンプトに `"Keep each response under 3 sentences."` が含まれる |

### TC-07-11: 典型的な質問への3文以内の応答（実機）

| 項目 | 内容 |
|------|------|
| 対象 | `DocumentChatSession.sendMessage()` |
| 種別 | 手動（実機 Pixel 9、Gemma 4 E2B） |
| 手順 | 1. 児童手当現況届を解析して S-02 を表示する<br>2. 「締め切りはいつですか？」を送信する<br>3. 「何を持参すれば良いですか？」を送信する |
| 期待結果 | 各回答が3文以内である |
| 備考 | プロンプト制約（`Keep each response under 3 sentences.`）が安全網としての `maxOutputTokens` を代替する（プロンプト仕様書 §5.1 注記参照） |

---

## 9. DocumentReviewViewModel 状態遷移テスト

### TC-VM-01: 初期状態が Idle

| 項目 | 内容 |
|------|------|
| 期待結果 | `viewModel.uiState.value` が `DocumentReviewUiState.Idle` |

### TC-VM-02: 解析開始で Processing に遷移

| 項目 | 内容 |
|------|------|
| 手順 | テキスト入力後 `startAnalysis()` を呼ぶ |
| 期待結果 | `uiState` が `Processing(step=EXTRACTING_TEXT, progress=0.0f)` に遷移 |

### TC-VM-03: Processing ステップの進行

| 項目 | 内容 |
|------|------|
| 期待結果 | MF-01 完了後 `step=EXTRACTING_FIELDS`<br>MF-02 完了後 `step=TRANSLATING`<br>MF-03 完了後 `Review` 状態 |

### TC-VM-04: 解析成功で Review に遷移

| 項目 | 内容 |
|------|------|
| 前提 | MF-01〜03 がすべて成功 |
| 期待結果 | `uiState` が `Review` に遷移<br>`Review.reviewResult`、`Review.maskResult` が設定されている |

### TC-VM-05: Review 遷移時にチャットセッションが初期化される

| 項目 | 内容 |
|------|------|
| 期待結果 | `Review` 遷移と同時に `DocumentChatSession.initialize()` が呼ばれる |

### TC-VM-06: 解析失敗で Error に遷移

| 項目 | 内容 |
|------|------|
| 前提 | MF-02 が `FieldExtractionError.JsonParseError` をスロー |
| 期待結果 | `uiState` が `Error(message="JSONの解析に失敗しました")` に遷移 |

### TC-VM-07: Error 状態から再試行で Idle に戻る

| 項目 | 内容 |
|------|------|
| 手順 | `retryAnalysis()` を呼ぶ |
| 期待結果 | `uiState` が `Idle` に遷移 |

### TC-VM-08: 引継ぎ用ファイル作成で GeneratingEscalation に遷移

| 項目 | 内容 |
|------|------|
| 前提 | `uiState` が `Review` |
| 手順 | `generateEscalation(userNotes)` を呼ぶ |
| 期待結果 | `uiState` が `GeneratingEscalation` に遷移 |

### TC-VM-09: エスカレーション生成完了で OutputPreview に遷移

| 項目 | 内容 |
|------|------|
| 前提 | MF-06 が正常に完了 |
| 期待結果 | `uiState` が `OutputPreview(pkg=...)` に遷移 |

### TC-VM-10: Review → Idle 遷移でデータクリア

| 項目 | 内容 |
|------|------|
| 手順 | S-02 から戻るナビゲーションを実行 |
| 期待結果 | `uiState` が `Idle`<br>`ReviewResult`・`MaskResult`・チャット履歴がクリアされている |

### TC-VM-11: OutputPreview → Review 遷移でデータ保持

| 項目 | 内容 |
|------|------|
| 手順 | S-03 から S-02 に戻る |
| 期待結果 | `uiState` が `Review`<br>`ReviewResult`・`MaskResult`・チャット履歴が保持されている |

### TC-VM-12: chatIsGenerating 中は引継ぎボタンが無効

| 項目 | 内容 |
|------|------|
| 前提 | `uiState` が `Review(chatIsGenerating=true)` |
| 期待結果 | `generateEscalation()` 呼び出しが無効化される（もしくは Mutex でブロックされる） |

### TC-VM-13: LLM 推論の排他制御

| 項目 | 内容 |
|------|------|
| 手順 | チャット生成中に `generateEscalation()` を並行して呼ぶ |
| 期待結果 | Mutex により同時実行されない<br>先行推論完了後にエスカレーション推論が開始する |

### TC-VM-14: 翻訳失敗時の部分遷移

| 項目 | 内容 |
|------|------|
| 前提 | MF-03 が失敗 |
| 期待結果 | `uiState` が `Review` に遷移（翻訳列なし）<br>翻訳失敗フラグが立つ |

### TC-VM-15: MF-05（PiiMasker）が MF-02 完了直後に実行される

| 項目 | 内容 |
|------|------|
| 種別 | ユニット（ViewModel）|
| 前提 | MF-01・MF-02 が成功し、MF-03 はまだ実行中 |
| 手順 | MF-02 が完了した直後の ViewModel 状態を確認する |
| 期待結果 | `PiiMasker.mask()` が MF-03 完了を待たずに呼ばれる<br>（仕様書 §4.1: MF-05 は MF-02 完了後すぐ実行可能・LLM 不要） |

---

## 10. 画面テスト（S-01 / S-02 / S-03）

### S-01: 入力画面

#### TC-UI-S01-01: 待機中の表示

| 項目 | 内容 |
|------|------|
| 前提 | `uiState = Idle` |
| 期待結果 | PDF 選択ボタンが表示される<br>テキスト貼り付けエリアが表示される<br>翻訳先言語ラジオボタンが 4 件表示される<br>「解析を開始する」ボタンが表示される |

#### TC-UI-S01-02: 言語選択のデフォルト値

| 項目 | 内容 |
|------|------|
| 期待結果 | 初期状態で English が選択されている |
| 備考 | 仕様書 §3.3 は "MVP では English を主な検証対象とする" と記載しているが、UI のデフォルト選択値を明示していない。実装時に English をデフォルトとすることを仕様書に追記し、このテストケースを確定させること |

#### TC-UI-S01-03: 解析中の表示（プログレス表示）

| 項目 | 内容 |
|------|------|
| 前提 | `uiState = Processing(step=EXTRACTING_FIELDS, progress=0.6f)` |
| 期待結果 | 現在のステップラベル「重要項目を抽出中...」が表示される<br>プログレスバーに 60% が反映される<br>入力エリアと言語選択が無効化されている<br>「解析を開始する」ボタンが非表示 |

#### TC-UI-S01-04: エラー表示と再試行ボタン

| 項目 | 内容 |
|------|------|
| 前提 | `uiState = Error("JSONの解析に失敗しました")` |
| 期待結果 | エラーメッセージが表示される<br>「再試行する」ボタンが表示される |

#### TC-UI-S01-05: PDF ファイル選択後のファイル名表示

| 項目 | 内容 |
|------|------|
| 手順 | PDF ファイルを選択する |
| 期待結果 | ファイル名が PDF 選択ボタン領域に表示される |

#### TC-UI-S01-06: モデル未ダウンロード時の誘導

| 項目 | 内容 |
|------|------|
| 前提 | モデルが未ダウンロード |
| 手順 | 「解析を開始する」をタップ |
| 期待結果 | モデルマネージャー画面（S-M）へ遷移する |

### S-02: レビュー画面

#### TC-UI-S02-01: 日英並列表示

| 項目 | 内容 |
|------|------|
| 前提 | `uiState = Review(reviewResult=..., targetLanguage="en")` |
| 期待結果 | 左列に日本語、右列に英語が表示される<br>両列が同じ書類内容を表示している |

#### TC-UI-S02-02: 翻訳失敗バナーの表示

| 項目 | 内容 |
|------|------|
| 前提 | 翻訳失敗フラグが立っている |
| 期待結果 | 「翻訳に失敗しました。日本語のみ表示しています」バナーが表示される<br>英語列が非表示 |

#### TC-UI-S02-03: 色分けバッジの表示

| 項目 | 内容 |
|------|------|
| 期待結果 | `severity: high` の警告に赤バッジが表示される<br>`action_items` に橙バッジが表示される |

#### TC-UI-S02-04: PII マスクセクションの表示

| 項目 | 内容 |
|------|------|
| 期待結果 | マスク済み PII が `[Applicant name]` / `[Issuer address]` 等のラベル付きトークンで表示される<br>「マスク範囲を確認・編集する」ボタンが表示される |

#### TC-UI-S02-05: PII 編集パネルの展開・折りたたみ

| 項目 | 内容 |
|------|------|
| 手順 | 「マスク範囲を確認・編集する ▼」をタップ |
| 期待結果 | 各 PiiSpan の一覧がインラインで展開される |

#### TC-UI-S02-06: PII トグルによるマスク除外

| 項目 | 内容 |
|------|------|
| 手順 | PII 編集パネルで口座スパンのチェックボックスをオフにする |
| 期待結果 | `remask()` が呼ばれる<br>上部の PII プレビューが更新される |

#### TC-UI-S02-07: チャットセクションの初回メッセージ

| 項目 | 内容 |
|------|------|
| 前提 | `Review` 遷移直後 |
| 期待結果 | AI の初回メッセージが表示されている |

#### TC-UI-S02-08: チャット生成中の入力無効化

| 項目 | 内容 |
|------|------|
| 前提 | `chatIsGenerating = true` |
| 期待結果 | テキスト入力フィールドと送信ボタンが無効化されている<br>「引継ぎ用ファイル作成」ボタンが無効化されている |

#### TC-UI-S02-09: チャット送信と回答ストリーミング

| 項目 | 内容 |
|------|------|
| 手順 | メッセージを入力して送信ボタンをタップ |
| 期待結果 | ユーザーメッセージが表示される<br>AI 回答がストリーミングで徐々に表示される |

#### TC-UI-S02-10: 「引継ぎ用ファイル作成」ボタンでエスカレーション開始

| 項目 | 内容 |
|------|------|
| 手順 | 「引継ぎ用ファイル作成」ボタンをタップ |
| 期待結果 | ボタンがスピナー付きプログレス表示に切り替わる<br>チャット入力が無効化される<br>推論完了後に S-03 へ遷移する |

#### TC-UI-S02-11: チャット履歴の上限到達表示

| 項目 | 内容 |
|------|------|
| 前提 | チャット履歴が上限（20 ターンまたは 4,000 文字）に到達 |
| 期待結果 | 「チャット履歴の上限に達しました」が表示される<br>新規入力フィールドが無効化される |

#### TC-UI-S02-12: unmatchedSpans がある場合のユーザー通知

| 項目 | 内容 |
|------|------|
| 前提 | `maskResult.unmatchedSpans` が 1 件以上ある |
| 期待結果 | S-02 の PII セクションに「マスクできなかった項目があります」等の通知が表示される<br>（仕様書 §7.4: unmatchedSpans を UI 上でユーザーに通知する） |

### S-03: 出力確認画面

#### TC-UI-S03-01: 引き継ぎテキストのプレビュー表示

| 項目 | 内容 |
|------|------|
| 前提 | `uiState = OutputPreview(pkg=...)` |
| 期待結果 | `## 1. 相談概要` から `## 8. 事前質問・確認事項` までのセクションが表示される |

#### TC-UI-S03-02: マスク済み警告バナー

| 項目 | 内容 |
|------|------|
| 期待結果 | 「⚠️ 個人情報はマスク済み / masked: name, address...」バナーが表示される |

#### TC-UI-S03-03: コピーボタン

| 項目 | 内容 |
|------|------|
| 手順 | 「📋 コピー」ボタンをタップ |
| 期待結果 | `toPlainText()` の内容がクリップボードにコピーされる |

#### TC-UI-S03-04: 共有ボタン

| 項目 | 内容 |
|------|------|
| 手順 | 「↑ 共有」ボタンをタップ |
| 期待結果 | Android の共有シート（ACTION_SEND）が起動する<br>共有テキストが `toPlainText()` の内容である |

#### TC-UI-S03-05: 戻るボタンで S-02 へ遷移

| 項目 | 内容 |
|------|------|
| 手順 | 「←」ボタンをタップ |
| 期待結果 | S-02 に遷移する<br>`ReviewResult` と `MaskResult` が保持されている |

---

## 11. エラーハンドリングテスト

### TC-ERR-01: PDF にテキスト層なし

| 項目 | 内容 |
|------|------|
| 前提 | テキスト層なしの PDF |
| 期待結果 | S-01 上に「このPDFはテキストを読み取れません」が表示される |

### TC-ERR-02: MF-02 タイムアウト（150 秒）

| 項目 | 内容 |
|------|------|
| 前提 | `FieldExtractor` が 150 秒以上応答しない |
| 期待結果 | S-01 上に「解析に失敗しました。再試行してください」が表示される<br>`uiState` が `Error` |

### TC-ERR-03: MF-06 タイムアウト（30 秒）

| 項目 | 内容 |
|------|------|
| 前提 | `EscalationPackageGenerator` が 30 秒以上応答しない（`GeneratingEscalation` 状態中） |
| 期待結果 | 「解析に失敗しました。再試行してください」が S-02 上に表示される<br>（仕様書 §4.3: MF-06 タイムアウトは MF-02 と同じメッセージ）<br>`uiState` が `Review` に戻り、「引継ぎ用ファイル作成」ボタンが再度有効化される |

### TC-ERR-04: MF-07 チャットタイムアウト（20 秒）

| 項目 | 内容 |
|------|------|
| 前提 | `DocumentChatSession.sendMessage()` が 20 秒以上応答しない |
| 期待結果 | チャット入力欄の上に「回答の生成に失敗しました。もう一度送信してください」が表示される<br>失敗したアシスタントメッセージが履歴に残らない |

### TC-ERR-05: MF-07 チャットセッション初期化失敗

| 項目 | 内容 |
|------|------|
| 前提 | `DocumentChatSession.initialize()` が例外をスロー |
| 期待結果 | チャットセクションが非表示になる<br>レビュー・エスカレーション機能は引き続き使用可能 |

### TC-ERR-06: テキストエリア直接入力が 8,000 文字超過

| 項目 | 内容 |
|------|------|
| 前提 | **テキストエリアに直接** 8,001 文字を貼り付ける（PDF 選択ではなくテキスト入力経由） |
| 期待結果 | 「書類が長すぎます（上限 8,000 文字）」が表示される<br>`startAnalysis()` が中断される |
| 備考 | PDF 選択時は仕様書 §7.1 の TextExtractor が自動で 8,000 文字にトリムするため本エラーは発生しない（TC-01-05 で確認済み）。本ケースはテキスト直接入力パスに限定する |

### TC-ERR-07: モデル未ダウンロード

| 項目 | 内容 |
|------|------|
| 前提 | Gemma 4 モデルが未ダウンロード |
| 手順 | 解析を開始する |
| 期待結果 | モデルマネージャー画面へ誘導される |

### TC-ERR-08: MF-03 翻訳失敗

| 項目 | 内容 |
|------|------|
| 前提 | `Translator.translate()` が例外をスロー |
| 期待結果 | S-02 の英語列が非表示<br>「翻訳に失敗しました。日本語のみ表示しています」バナーが S-02 上部に表示される |

---

## 12. EscalationPackage.toPlainText() テスト

### TC-TXT-01: セクション見出しと内容の正常出力

| 項目 | 内容 |
|------|------|
| 対象 | `EscalationPackage.toPlainText()` |
| 期待結果 | `## 1. 相談概要` から `## 8. 事前質問・確認事項` まで 8 セクションが含まれる |

### TC-TXT-02: 先頭のマスク警告行

| 項目 | 内容 |
|------|------|
| 期待結果 | 最初の行が `⚠️ 個人情報はマスク済み / masked: ` で始まる<br>`maskedFields` が `, ` 区切りで列挙される |

### TC-TXT-03: ai_hypotheses が空のときセクション 6 が省略される

| 項目 | 内容 |
|------|------|
| 前提 | `aiHypotheses = emptyList()` |
| 期待結果 | `## 6. AI 仮説・不明点` セクションが出力されない |

### TC-TXT-04: user_notes が空のとき "(メモなし)" が出力される

| 項目 | 内容 |
|------|------|
| 前提 | `userNotes = null` |
| 期待結果 | セクション 7 に "(メモなし)" が出力される |

### TC-TXT-05: chat_history が空のときセクション 8 が空

| 項目 | 内容 |
|------|------|
| 前提 | `chatHistory = emptyList()` |
| 期待結果 | セクション 8 の見出しは出力されるが内容が空 |

### TC-TXT-06: timeline の日付とイベントが正しくフォーマットされる

| 項目 | 内容 |
|------|------|
| 前提 | `timeline = [{"date": "2025-06-30", "event": "提出期限"}]` |
| 期待結果 | `- 2025-06-30: 提出期限` の形式で出力される |

### TC-TXT-07: セクション間の区切りが空行 1 行

| 項目 | 内容 |
|------|------|
| 期待結果 | 各セクション間が `\n\n` で区切られている（`\n\n\n` や空行複数ではない） |

---

## 13. DocumentRepository テスト

### TC-REPO-01: save() でファイルが作成される

| 項目 | 内容 |
|------|------|
| 対象 | `DocumentRepository.save()` |
| 種別 | インテグレーション（Robolectric） |
| 期待結果 | `{filesDir}/documents/{docId}/meta.json` と `source.txt` が作成される |

### TC-REPO-02: saveEscalation() で escalation.json が作成される

| 項目 | 内容 |
|------|------|
| 期待結果 | `{filesDir}/documents/{docId}/escalation.json` が作成される |

### TC-REPO-03: load() で保存したデータが正しく復元される

| 項目 | 内容 |
|------|------|
| 手順 | `save()` 後に `load(docId)` を呼ぶ |
| 期待結果 | `DocumentBundle.reviewResult` が保存時と一致する |

### TC-REPO-04: list() で保存済みドキュメント一覧が返る

| 項目 | 内容 |
|------|------|
| 手順 | 2 件 `save()` 後に `list()` を呼ぶ |
| 期待結果 | `List<DocumentMeta>` が 2 件返る |

### TC-REPO-05: delete() でファイルが削除される

| 項目 | 内容 |
|------|------|
| 手順 | `save()` 後に `delete(docId)` を呼ぶ |
| 期待結果 | `{filesDir}/documents/{docId}/` ディレクトリが存在しなくなる |

### TC-REPO-06: 外部ストレージを使用しない

| 項目 | 内容 |
|------|------|
| 期待結果 | すべての読み書きが `context.filesDir` 配下で行われる |

---

## 14. 非機能テスト

### TC-PERF-01: MF-01 テキスト抽出（< 1 秒）

| 項目 | 内容 |
|------|------|
| 種別 | パフォーマンス（実機 Pixel 9） |
| 手順 | `edogawa_R7_genkyo_kinyuurei.pdf` を `TextExtractor.extract()` で処理する |
| 期待結果 | 完了まで 1 秒未満 |

### TC-PERF-02: MF-02 フィールド抽出（60〜150 秒）

| 項目 | 内容 |
|------|------|
| 前提 | Gemma 4 E2B モデル使用 |
| 期待結果 | 完了まで 150 秒以内 |

### TC-PERF-03: MF-03 翻訳（15〜60 秒）

| 項目 | 内容 |
|------|------|
| 期待結果 | 完了まで 60 秒以内 |

### TC-PERF-04: MF-06 要約生成（5〜15 秒）

| 項目 | 内容 |
|------|------|
| 期待結果 | 完了まで 15 秒以内 |

### TC-PERF-05: MF-07 チャット返答（3〜10 秒）

| 項目 | 内容 |
|------|------|
| 手順 | 1 ターンの質問を送信する |
| 期待結果 | 最初のトークンが 3 秒以内に届く（ストリーミング） |


---

## 15. EntityExtractor / EntityAnnotator テスト

### TC-ENT-01: ML Kit 専用エンティティに staticContextLabel が静的設定される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.extract()` |
| 種別 | ユニット |
| 前提 | ML Kit が URL・IBAN・PAYMENT_CARD・TRACKING_NUMBER・FLIGHT_NUMBER・ISBN の各エンティティを返す |
| 手順 | `EntityExtractor.extract(text)` を呼ぶ |
| 期待結果 | 各エンティティの `contextLabel` が `"url"` / `"iban"` / `"payment_card"` / `"tracking_number"` / `"flight_number"` / `"isbn"` にそれぞれ静的設定される |

### TC-ENT-02: 全 11 種のエンティティが `detected_entities` に収録される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.extract()` |
| 種別 | ユニット |
| 前提 | ML Kit が PHONE・EMAIL・ADDRESS・DATE_TIME・URL・MONEY・IBAN・PAYMENT_CARD・FLIGHT_NUMBER・ISBN・TRACKING_NUMBER の各エンティティを 1 件ずつ返す |
| 期待結果 | 返り値の `map { it.type }` が全 11 種を含む |

### TC-ENT-03: EntityExtractor がモデルダウンロード失敗時に空リストを返す

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.extract()` |
| 種別 | ユニット |
| 前提 | ML Kit モデルダウンロードが `ExecutionException` をスローする状況をシミュレート |
| 期待結果 | 例外をスローせず空リストを返す |

### TC-ENT-04: ML Kit でエンティティが未検出の場合 mergeEntities がクラッシュしない

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `annotatedEntities` が空リスト |
| 期待結果 | `mergedResult.detectedEntities` が空・`piiSpans` が空（クラッシュなし） |

### TC-ENT-05: issuer_address と other_address が locations に収録される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `annotatedEntities` に `contextLabel="issuer_address"` 1件・`contextLabel="other_address"` 2件 |
| 期待結果 | `mergedResult.locations` が 3 件（issuer_address が先頭） |

### TC-ENT-06: deadline エンティティから DeadlineInfo が導出される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `annotatedEntities` に `contextLabel="deadline"`・`rawText="令和7年6月30日"` の DATE_TIME エンティティ |
| 期待結果 | `mergedResult.deadline.noteJa == "令和7年6月30日"` |

### TC-ENT-07: applicant_name / other_name / issuer_name が PiiSpan として構築される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `gemmaResult.applicantName = "山田太郎"`、`gemmaResult.otherName = "田中次郎"`、`gemmaResult.issuerName = "江戸川区役所"` |
| 期待結果 | `piiSpans` に `PiiSpan(spanText="山田太郎", category="name")` と `PiiSpan(spanText="田中次郎", category="name")` と `PiiSpan(spanText="江戸川区役所", category="name")` が含まれる |

### TC-ENT-08: issuer_name が Tier 2 の PiiSpan として収録される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `gemmaResult.issuerName = "江戸川区役所"` |
| 期待結果 | `piiSpans` に `PiiSpan(spanText="江戸川区役所", category="name")` が含まれる（Tier 2 昇格により piiSpans 収録対象になった） |

### TC-ENT-09: Tier-1/2 エンティティが piiSpans に収集される

| 項目 | 内容 |
|------|------|
| 対象 | `EntityExtractor.mergeEntities()` |
| 種別 | ユニット |
| 前提 | `contextLabel="applicant_address"` (Tier 1) の ADDRESS エンティティ・`contextLabel="issuer_phone"` (Tier 2) の PHONE エンティティ・`contextLabel="flight_number"` (Tier 3) の FLIGHT_NUMBER エンティティ |
| 期待結果 | `piiSpans` に Tier 1/2 の 2 件のみ含まれ、Tier 3 は含まれない |

### TC-ENT-10: EntityExtractor が FieldExtractor より前に実行される

| 項目 | 内容 |
|------|------|
| 対象 | `DocumentReviewViewModel.analyzeDocument()` |
| 種別 | ユニット（ViewModel）|
| 前提 | `EntityExtractor.extract()` と `FieldExtractor.extract()` をモック化し呼び出し順を記録する |
| 手順 | `analyzeDocument()` を呼ぶ |
| 期待結果 | `EntityExtractor.extract()` が `FieldExtractor.extract()` より先に呼ばれる |

### TC-ENT-11: EntityAnnotator が Mutex 内で FieldExtractor の後に実行される

| 項目 | 内容 |
|------|------|
| 対象 | `DocumentReviewViewModel.analyzeDocument()` |
| 種別 | ユニット（ViewModel）|
| 前提 | `FieldExtractor.extract()` と `EntityAnnotator.annotate()` をモック化し呼び出し順を記録する |
| 手順 | `analyzeDocument()` を呼ぶ |
| 期待結果 | `FieldExtractor.extract()` → `EntityAnnotator.annotate()` の順で実行される |

### TC-ENT-tier-01: computePiiTier() が applicant_* エンティティを Tier 1 に分類する

| 項目 | 内容 |
|------|------|
| 対象 | `DetectedEntity.computePiiTier()` |
| 種別 | ユニット |
| 手順 | `contextLabel="applicant_address"` の ADDRESS・`contextLabel="applicant_phone"` の PHONE・`contextLabel="applicant_email"` の EMAIL それぞれで `computePiiTier()` を呼ぶ |
| 期待結果 | 3 件すべてが `1` を返す |

### TC-ENT-tier-02: computePiiTier() が issuer_* / other_* エンティティを Tier 2 に分類する

| 項目 | 内容 |
|------|------|
| 対象 | `DetectedEntity.computePiiTier()` |
| 種別 | ユニット |
| 手順 | `contextLabel="issuer_phone"` の PHONE・`contextLabel="other_address"` の ADDRESS それぞれで呼ぶ |
| 期待結果 | 2 件ともに `2` を返す |

### TC-ENT-tier-03: computePiiTier() が date_of_birth を Tier 1 に、event_date を Tier 3 に分類する

| 項目 | 内容 |
|------|------|
| 対象 | `DetectedEntity.computePiiTier()` |
| 種別 | ユニット |
| 期待結果 | `contextLabel="date_of_birth"` → `1`、`contextLabel="event_date"` → `3`、`contextLabel=null` → `3` |

---

## 16. テストデータ

### 16.1 標準 ReviewResult（ユニットテスト共通）

仕様書 §6.6 の few-shot example JSON をベースとする。

| フィールド | 値 |
|-----------|-----|
| docType | 児童手当現況届 |
| importance | high |
| deadline.date | 2025-06-30 |
| piiCandidates 件数 | 4 件（name, address, dob, account） |
| translation | null（初期値） |

### 16.2 テスト用 PiiSpan セット

| id | spanText | category | maskRecommended |
|----|----------|----------|-----------------|
| pii_01 | 山田太郎 | name | true |
| pii_02 | 港区虚空町1-2-3 | address | true |
| pii_03 | 昭和60年1月1日 | dob | true |
| pii_04 | 1234567 | account | true |

### 16.3 テスト用 sourceText

```
江戸川区役所　子育て支援課
受給者氏名: 山田太郎
住所: 港区虚空町1-2-3
生年月日: 昭和60年1月1日
受取口座: 1234567
令和7年6月30日（月）までに現況届を提出してください。
未提出の場合、翌月以降の支給が停止されます。
```

### 16.4 翻訳失敗シミュレーション

`Translator.translate()` のモックを `RuntimeException("translation failed")` をスローするよう設定する。

---

## 17. Intent 受け取りテスト

### TC-INTENT-01: ACTION_SEND（PDF）を受け取る

| 項目 | 内容 |
|------|------|
| 対象 | `IncomingDocumentViewModel.handleIntent()` + `DocumentReviewScreen` |
| 種別 | UI テスト（実機 or Robolectric） |
| 前提 | アプリが Idle 状態 |
| 手順 | 1. `action = ACTION_SEND`、`type = "application/pdf"`、`EXTRA_STREAM = contentUri` の Intent を `handleIntent()` に渡す |
| 期待結果 | S-01 のファイル選択欄にファイル名が表示される（抽出中）<br>テキスト抽出完了後、テキストエリアに PDF の内容が反映される<br>解析は自動開始されない |

### TC-INTENT-02: ACTION_VIEW（PDF）で起動する

| 項目 | 内容 |
|------|------|
| 手順 | 1. `action = ACTION_VIEW`、`type = "application/pdf"`、`data = contentUri` の Intent を `handleIntent()` に渡す |
| 期待結果 | S-01 のファイル選択欄にファイル名が表示される（抽出中）<br>テキスト抽出完了後、テキストエリアに PDF の内容が反映される<br>解析は自動開始されない |

### TC-INTENT-03: ACTION_SEND（text/plain）を受け取る

| 項目 | 内容 |
|------|------|
| 手順 | 1. `action = ACTION_SEND`、`type = "text/plain"`、`EXTRA_TEXT = "テスト文字列"` の Intent を `handleIntent()` に渡す |
| 期待結果 | S-01 のテキストエリアに `"テスト文字列"` がセットされる<br>解析は自動開始されない |

### TC-INTENT-04: ACTION_SEND（image/\*）を受け取る

| 項目 | 内容 |
|------|------|
| 手順 | 1. `action = ACTION_SEND`、`type = "image/jpeg"`、`EXTRA_STREAM = contentUri` の Intent を `handleIntent()` に渡す |
| 期待結果 | クラッシュしない<br>台形補正画面（S01CropContent）に遷移する<br>補正確定後、OCR でテキストエリアに内容が反映される<br>解析は自動開始されない |

### TC-INTENT-05: アプリ起動中に Intent を受け取る（onNewIntent）

| 項目 | 内容 |
|------|------|
| 前提 | アプリが S-02（Review 状態）で動作中 |
| 手順 | 1. `ACTION_SEND`（PDF）の Intent を `onNewIntent` 経由で受け取る |
| 期待結果 | Review 状態がリセットされ Idle に戻る<br>S-01 のファイル選択欄に新しいファイル名が表示される |

### TC-INTENT-06: 不正な URI を受け取る

| 項目 | 内容 |
|------|------|
| 手順 | 1. `ACTION_VIEW`（PDF）で `intent.data` が null の Intent を渡す<br>2. `ACTION_SEND`（テキスト）で EXTRA_TEXT が空文字の Intent を渡す |
| 期待結果 | クラッシュしない<br>イベントが emit されず、UI は変化しない |

---

*本テスト仕様書は実装仕様書 v0.1.0 に基づく。実装が仕様から変更された場合は本書を合わせて更新すること。*
