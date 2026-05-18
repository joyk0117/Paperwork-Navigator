# Paperwork Navigator プロンプト仕様書

> バージョン: 0.3.1
> 作成日: 2026-05-07
> 最終更新: 2026-05-15（MF-02 を 9 フィールドに削減・EntityAnnotator セクション追加・MF-08 廃止ノートを EntityAnnotator セクションに統合・MF-06/MF-07 のマスクトークン記述をラベル付き形式に更新）
> 対象: MVP（MF-02 / EntityAnnotator / MF-03 / MF-06 / MF-07）
> 依拠: 実装仕様書 v0.2.6 / 抽出アーキテクチャ仕様書 v1.0.0

---

## 目次

1. [設計方針](#1-設計方針)
2. [MF-01c: OCR 補正プロンプト](#2-mf-01c-ocr-補正プロンプト)
3. [MF-02: フィールド抽出プロンプト（9 フィールド）](#3-mf-02-フィールド抽出プロンプト9-フィールド)
4. [EntityAnnotator: エンティティ意味付けプロンプト](#4-entityannotator-エンティティ意味付けプロンプト)
5. [MF-03: 翻訳プロンプト](#5-mf-03-翻訳プロンプト)
6. [MF-06: 問い合わせ文書プロンプト](#6-mf-06-問い合わせ文書プロンプト)
7. [MF-07: チャット用システムプロンプト](#7-mf-07-チャット用システムプロンプト)
8. [Few-shot Example（MF-02 / EntityAnnotator / MF-03 用）](#8-few-shot-examplemf-02--entityannotator--mf-03-用)
9. [プロンプト変数リファレンス](#9-プロンプト変数リファレンス)
10. [評価基準](#10-評価基準)

---

## 1. 設計方針

- システムプロンプトは英語で記述（LLM の多言語能力を活かし、あらゆる言語の書類に対して均質な出力品質を保つ）
- ユーザーメッセージに書類テキスト（任意言語）を渡し、出力形式はモデルの能力に合わせて選択する（後述）
- few-shot example を含めることで精度を安定させる
- コンテキスト長の上限: 入力テキストは 16,000 文字以内にトリム（ViewModel 側で実施、仕様書 §9.4）

### 1.1 MF-02 出力形式の選択経緯

当初は LiteRT-LM の **function calling（structured output）** を使って JSON を確実に得る方針だったが、実機検証の結果 **Kotlin API（LiteRT-LM 0.10.0）には JSON Schema / Regex / Grammar によるConstrained Decoding が存在しない** ことが判明した。

> **補足**: LiteRT-LM の C++ API には Grammar-based Constrained Decoding が実装されているが、0.10.0 時点の Kotlin API に公開されているのは `ExperimentalFlags.enableConversationConstrainedDecoding`（二値フラグ）のみで、スキーマ指定は不可能。

さらに実機テストで、長い入力テキスト（8,000 文字程度）+ 複雑なネスト JSON スキーマの組み合わせで、Gemma 4 E2B が **key-as-value 崩壊**（例: `"importance": "summary_ja": "..."` のように値の位置にキー名を出力する）を起こすことが確認された。

これを受け、MF-02 の出力形式を JSON から **行形式（Key-Value lines）** に変更した。

| 方針 | 内容 |
|------|------|
| 出力形式 | `KEY: value` の行形式（16 フィールド固定） |
| 配列の区切り | `\|\|\|`（トリプルパイプ） |
| サブフィールドの区切り | `\|`（シングルパイプ） |
| JSON 組み立て | Kotlin 側の `parseLineFormat()` が行うため、LLM は構造を意識不要 |
| フォールバック | MF-03 も同様の理由で行形式を採用。MF-06a（目的候補）・MF-06（エスカレーション）は出力フィールドが少なく崩壊リスクが低いため JSON 出力を維持 |

---

## 2. MF-01c: OCR 補正プロンプト

カメラ・ギャラリー画像入力後の ML Kit OCR テキストを Gemma 4 マルチモーダル推論で補正するステップ。
PDF テキスト抽出（MF-01a）には元画像がないためスキップする。

### 2.1 System Prompt

```
You are a precise OCR correction assistant.

The user will provide an image of a document and the OCR text extracted from it.
Compare the image with the OCR text and identify transcription errors.
Output corrections only—no explanation, no markdown fences.

Format:
CORRECT: <wrong_text>|<corrected_text>

Rules:
- Output one CORRECT line per error.
- <wrong_text> must be copied verbatim from the OCR text.
- <corrected_text> must match exactly what appears in the image.
- If no errors are found, output: (none)
- Do not correct spacing or line-break differences unless they change meaning.
- Do not guess corrections for text that is unclear or obscured in the image.
```

### 2.2 User Message

```
Here is the OCR text extracted from the document image above:

{ocr_text}

List any transcription errors found.
```

### 2.3 変数

| 変数 | 説明 |
|------|------|
| `{ocr_text}` | ML Kit OCR が出力したテキスト（`LLM_INPUT_MAX_CHARS` でトリム済み） |

### 2.4 出力形式と Kotlin 側パース

Gemma 4 は以下の2つの形式を使用する（どちらも `OcrCorrector.parseCorrections()` が受け入れる）。

**パイプ形式（推奨）:**
```
CORRECT: 山回太郎|山田太郎
CORRECT: 令利7年|令和7年
(none)
```

**角括弧形式（モデルが自然に出力することがある）:**
```
CORRECT:山回太郎<山田太郎>
CORRECT:令利7年<令和7年>
```

- `CORRECT:` の後のスペースは有無を問わない
- wrong == right の恒等補正は除外する
- 誤りがない場合は `(none)` のみ出力
- `OcrCorrector.parseCorrections()` が行を読み取り `List<Pair<String, String>>` に変換
- `OcrCorrector.applyCorrections()` が順に `String.replace()` を適用
- パース失敗・`(none)` 出力・タイムアウト時は ocrText をそのまま使用（リトライなし）

---

## 3. MF-02: フィールド抽出プロンプト（9 フィールド）

日付・住所・電話・メール・金額は ML Kit + EntityAnnotator で処理するため、MF-02 では名前・重要度・要約・アクション・警告の 9 フィールドのみを抽出する。

### 3.1 System Prompt

```
You are a precise assistant that extracts structured information from documents.

The user will provide text from a document in any language.
Output lines in the exact format below. Output only the lines—no explanation, no markdown fences.

Format:
DOC_NAME: <exact document title/name as written on the document>
ISSUER_NAME: <name of the organization or person who issued the document or (none)>
APPLICANT_NAME: <name of the specific person the document is addressed to or (none)>
OTHER_NAME: <name of any other person or organization mentioned in the document or (none)>
IMPORTANCE: <high | medium | low>
SUMMARY: <1-2 sentence summary in the document's original language>
ACTION_ITEMS: <description1>|||<description2>|||... or (none)
REQUIRED_ITEMS: <name1>|<note1>|||<name2>|<note2>|||... or (none)
WARNING: <severity>|<description> or (none)

Rules:
- DOC_NAME: extract the exact title or heading as it appears on the document.
- ISSUER_NAME: the organization or person who issued or published the document.
- APPLICANT_NAME: use the person's actual name if stated, not a generic description (e.g. "山田太郎" not "受給者").
- OTHER_NAME: any other named person or organization who is neither the issuer nor the applicant. Use (none) if not found.
- IMPORTANCE is "high" when failure to act causes significant consequences (e.g. suspension of benefits, penalty, legal obligation).
- WARNING: the single most important warning only. severity must be: high | medium | low. Use (none) if no warning exists.
- Use (none) when a field has no value.
- Use ||| to separate multiple items in array fields.
- Use | to separate sub-fields within an item.

Example output:
{few_shot_example}
```

### 3.2 User Message

```
Analyze the following document text:

{document_text}
```

### 3.3 変数

| 変数 | 説明 | 参照 |
|------|------|------|
| `{few_shot_example}` | §8 の few-shot example（行形式） | [Few-shot Example](#8-few-shot-examplemf-02--entityannotator--mf-03-用) |
| `{document_text}` | TextExtractor が抽出したテキスト（16,000 文字以内にトリム済み） | — |

### 3.4 リトライ方針

パース失敗時は最大 2 回リトライ（合計 3 回試行）。リトライ時はエラー情報をユーザーメッセージに追記する。

```
Analyze the following document text:

{document_text}

The previous output caused a parse error: {parse_error_message}
Please output the correct line format again.
```

> `LlmChatModelHelper` はチャット形式でセッションを保持するため、リトライ時は前回の不正出力もコンテキストに蓄積される。3 回目も失敗した場合は `FieldExtractionError.ParseError` をスローしてリトライを打ち切る。

### 3.5 フィールド一覧（9 フィールド）

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `DOC_NAME` | 文字列 | 書類のタイトル（原文ママ） |
| `ISSUER_NAME` | 文字列 / (none) | 発行者 |
| `APPLICANT_NAME` | 文字列 / (none) | 宛先の人物（実名を抽出。generic description 不可） |
| `OTHER_NAME` | 文字列 / (none) | 発行者・申請者以外の人物・組織 |
| `IMPORTANCE` | `high` / `medium` / `low` | 重要度 |
| `SUMMARY` | 文字列 | 原文言語による 1〜2 文の要約 |
| `ACTION_ITEMS` | 説明文字列 × `\|\|\|` / (none) | 必要アクション |
| `REQUIRED_ITEMS` | `<name>\|<note>` × `\|\|\|` / (none) | 持参書類・必要物 |
| `WARNING` | `<severity>\|<description>` / (none) | 最重要の警告（単一） |

> 削除したフィールド（旧16フィールド → 9フィールド）の移管先：`DEADLINE_DATE` / `DEADLINE_NOTE` / `DOC_DATE` / `ISSUER_ADDRESS` / `LOCATIONS` / `EVENT_DATES` は ML Kit + EntityAnnotator が抽出した `context_label` 付きエンティティから `mergeEntities()` が導出する。`CONTACT` は `issuer_phone` / `issuer_email` エンティティから拡張関数で導出する。`EXTRA_PII` は静的 Tier ルール（`computePiiTier()`）に置き換え。

---

## 4. EntityAnnotator: エンティティ意味付けプロンプト

ML Kit が抽出した DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY エンティティに、文脈から `context_label` を付与する Gemma 4 ステップ。FieldExtractor の直後に実行する（`issuer_name` / `applicant_name` / `other_name` が確定した後）。

### 4.1 System Prompt

```
You are a precise assistant that labels extracted entities from a document.

The user will provide:
1. Names identified from the document (issuer, applicant, other)
2. A numbered list of entities extracted from the document

Assign a context label to each entity based on its role in the document.
Output one line per entity in the exact format: {index}: {label}
Output only the lines—no explanation, no markdown fences.

Allowed labels per entity type:
- DATE_TIME: deadline | document_date | event_date | date_of_birth | unknown
- ADDRESS:   issuer_address | applicant_address | other_address | unknown
- PHONE:     issuer_phone | applicant_phone | other_phone | unknown
- EMAIL:     issuer_email | applicant_email | other_email | unknown
- MONEY:     benefit_amount | fee | penalty | other_amount | unknown

Rules:
- Use the provided names as anchors to determine ownership.
  Addresses, phones, and emails belonging to issuer_name → issuer_*
  Addresses, phones, and emails belonging to applicant_name → applicant_*
  Addresses, phones, and emails not belonging to either → other_*
- other_* labels may be used even when other_name is (none).
- deadline: the main submission or response deadline for the document.
- document_date: the date the document was issued or created.
- event_date: any other date that is neither a deadline nor a date of birth.
- date_of_birth: a birth date, typically associated with the applicant.
- benefit_amount: money the recipient will receive or is eligible for.
- fee: a required payment or charge.
- penalty: a fine or consequence for non-compliance.
- other_amount: any monetary value that does not fit the above.
- Use unknown when the role cannot be determined from context.

Example:
{few_shot_example}
```

### 4.2 User Message

```
issuer_name: {issuer_name}
applicant_name: {applicant_name}
other_name: {other_name}

{numbered_entities}

Document text:
{source_text}

Label each entity.
```

### 4.3 変数

| 変数 | 説明 |
|------|------|
| `{few_shot_example}` | §8 の EntityAnnotator few-shot example（`===` 区切り 2 件） |
| `{issuer_name}` | `reviewResult.issuerName ?: "(none)"` |
| `{applicant_name}` | `reviewResult.applicantName ?: "(none)"` |
| `{other_name}` | `reviewResult.otherName ?: "(none)"` |
| `{numbered_entities}` | `"${i+1}. ${entity.type}: ${entity.rawText}"` を改行で結合（DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY のみ） |
| `{source_text}` | `DocumentReviewViewModel.sourceText`（TextExtractor が抽出した生テキスト、16,000 文字以内にトリム済み）。オンデバイス推論のため raw テキストを渡すことは許容（MF-02 と同様） |

### 4.4 パース・フォールバック方針

- 出力形式: `{index}: {label}` の行（1 件 1 行）
- 許容ラベルは `ALLOWED_LABELS` マップで型ごとに定義（`§4.1 System Prompt` 参照）
- `unknown` が返ったエンティティ → `contextLabel = null`
- パース失敗・タイムアウト（120 秒）時 → 全エンティティを `contextLabel = null` のままフォールバック（リトライなし）

---

## 5. MF-03: 翻訳プロンプト

### 5.1 System Prompt

```
You are a precise translator.

Translate the provided {source_language} document fields into {target_language}.
Output lines in the exact format below. Output only the lines—no explanation, no markdown fences.

Format:
SUMMARY: <translated summary>
DEADLINE_NOTE: <translated deadline note or (none)>
ACTION_ITEMS: <id1>|<description1>|||<id2>|<description2>|||... or (none)
REQUIRED_ITEMS: <id1>|<name1>|<note1>|||<id2>|<name2>|(none)|||... or (none)
WARNING: <translated description or (none)>

Rules:
- Use plain, clear language that non-native speakers can understand.
- Keep document-specific terms accurate (e.g. 現況届 → "Status Report Form").
- For DEADLINE_NOTE, include the date in a human-readable format.
- Do not translate id fields.
- WARNING: translate the single warning description only—no severity prefix.
- Use ||| to separate multiple items in array fields.
- Use | to separate sub-fields within an item.
- Use (none) when a field has no value.
- Strings enclosed in square brackets such as [Applicant name] or [■■■] are privacy placeholders. Copy them verbatim—do not translate or modify them.

Example output:
{few_shot_example}
```

### 5.2 User Message

```
Translate the following fields from a {source_language} document into {target_language}:

{fields_text}
```

リトライ時はエラー情報を末尾に追記する。

```
Translate the following fields from a {source_language} document into {target_language}:

{fields_text}

The previous output caused a parse error: {parse_error_message}
Please output the correct line format again.
```

### 5.3 変数

| 変数 | 説明 |
|------|------|
| `{source_language}` | 書類の原文言語の英語表記（`LanguageIdentifier` が識別した言語コードを変換） |
| `{target_language}` | 翻訳先言語の英語表記（下表参照） |
| `{fields_text}` | 翻訳対象フィールドのテキスト（SUMMARY / DEADLINE_NOTE / ACTION_ITEMS / REQUIRED_ITEMS / WARNING を原文言語で列挙） |
| `{few_shot_example}` | §8 の MF-03 few-shot example |

### 5.5 言語コードと LLM プロンプト内表記の対応

| UI 表示 | 言語コード | LLM プロンプト内表記 |
|---------|-----------|---------------------|
| 日本語 | `ja` | Japanese |
| English | `en` | English |
| 中文 | `zh` | Chinese (Simplified) |
| 한국어 | `ko` | Korean |
| Español | `es` | Spanish |
| Français | `fr` | French |
| Deutsch | `de` | German |
| Italiano | `it` | Italian |
| Português | `pt` | Portuguese |
| Русский | `ru` | Russian |
| Polski | `pl` | Polish |
| Nederlands | `nl` | Dutch |
| العربية | `ar` | Arabic |
| ภาษาไทย | `th` | Thai |
| Türkçe | `tr` | Turkish |

> アプリ内部では常に言語コードで保持し、プロンプト生成時に上表で変換する。

### 5.6 翻訳対象・対象外フィールド

**翻訳対象（5 フィールド）:**
`summaryJa`, `deadline.noteJa`, `actionItems[].descriptionJa`, `requiredItems[].nameJa`, `requiredItems[].noteJa`, `warning.descriptionJa`

> MF-03 出力の `WARNING` には severity prefix を含めない（翻訳済み description 文字列のみ）。重要度は元の MF-02 出力から保持する。

**翻訳しない:**
- PiiSpan（スパン位置が変わるため）
- `id` 系フィールド（`action_01` 等）
- `warning.severity`（`high`/`medium`/`low` は多言語化不要）

> **事前マスク**: 翻訳対象フィールドのテキストは LLM に渡す前に `PiiMasker.mask()` を適用し、PII スパンが一致する文字列をマスクトークン（`[Applicant name]` 等）に置換する。LLM はマスクトークンを不透明な文字列として翻訳後もそのまま保持するため、翻訳済みフィールドにも自動的にマスクが入る。

---

## 6. MF-06: 問い合わせ文書プロンプト

S-04 ウィザードは MF-06a（目的候補提示）と MF-06（エスカレーションパッケージ生成）の2つのプロンプトで構成される。コンテキストテキスト生成（`InquiryContext.toContextText()`）は LLM を使わずアプリ側で行う。

### 6.1 MF-06a: 問い合わせ目的候補提示

#### System Prompt

```
You are a helpful assistant analyzing a document.
Suggest 3-5 concise inquiry purposes a recipient might have for this document.

Output a JSON array of short strings only—no explanation, no markdown fences.
Each string should be 5-15 words in {target_language}.

Example output:
["Confirm the submission deadline", "Clarify required documents", "Ask about payment status"]
```

#### User Message

```
Document: {doc_name}
Summary: {summary}
Required actions: {action_items}

Original document text:
{source_text}

Prior Q&A with the user (for context only — reflect topics raised, do not copy verbatim):
{chat_history}

Suggest inquiry purposes in {target_language}.
```

チャット履歴が空の場合は「Prior Q&A」セクション自体を省略する（`{chat_history}` が空文字のときはセクション全体を出力しない）。

#### 変数

| 変数 | 説明 | 参照 |
|------|------|------|
| `{target_language}` | 翻訳先言語の英語表記（§5.5 の表を使用） | — |
| `{doc_name}` | `reviewResult.docName` | — |
| `{summary}` | `reviewResult.translation.summary` または `reviewResult.summaryJa` | — |
| `{action_items}` | 番号付きリスト形式（翻訳済みフィールド優先） | — |
| `{source_text}` | `DocumentReviewViewModel.sourceText`（TextExtractor が抽出した生テキスト、16,000 文字以内にトリム済み）。オンデバイス推論のため raw テキストを渡すことは許容（MF-02 と同様） | — |
| `{chat_history}` | `DocumentChatSession.getChatHistory()` を `Q:` / `A:` 形式に変換したテキスト。空の場合はセクション自体を省略 | — |

#### エラーハンドリング

JSON パースエラー時は空リストで即時フォールバック（リトライなし）。タイムアウト（15 秒）発生時も同様。

---

---

> **MF-06b（不足情報質問生成）・MF-06c（問い合わせ文書生成）について**: MF-06b は実機検証で有用性が確認できなかったため削除。MF-06c はオンデバイス推論での文書品質懸念（文体・敬語・言語ミスマッチ）のため P2 延期（Issue #57）。現行の MF-06 は目的候補提示（MF-06a）・エスカレーションパッケージ生成（MF-06）・コンテキストテキスト組み立て（LLM 不要）の3要素で構成される。

### 6.2 MF-06: エスカレーションパッケージ生成

S-02 の「引継ぎ用ファイル作成」ボタン押下時に実行する LLM 推論。マスク済みテキストをもとに相談サマリー・タイムライン・AI 仮説を生成する。

#### System Prompt

```
You are a precise assistant helping prepare a privacy-safe escalation document.
The document has been masked—personal information replaced with descriptive labels
like [Applicant name], [Issuer address], [Date of birth], etc.

Output raw JSON only—no explanation, no markdown fences.

Output schema:
{
  "consultation_summary": "string (2-3 sentences explaining what the user needs help with)",
  "timeline": [{"date": "YYYY-MM-DD or descriptive string", "event": "string"}],
  "ai_hypotheses": [{"point": "string", "type": "hypothesis | unclear"}]
}

Rules:
- consultation_summary: explain the document type, what action is needed, and why
  the user is seeking expert help. Do not include any masked label values (e.g. [Applicant name]).
- timeline: extract all dates and associated events from the document.
- ai_hypotheses: list conditions that could not be determined from the document alone
  (type "unclear"), and any interpretations that might be wrong (type "hypothesis").
- Write all generated text fields (consultation_summary, timeline[].event, ai_hypotheses[].point)
  in {target_language}.
```

#### User Message

```
Masked document text:
{masked_text}

Masked PII categories: {masked_categories}

User notes: {user_notes}

Prior Q&A with the user (for context only—do not copy verbatim):
{chat_history}
```

#### 変数

| 変数 | 説明 |
|------|------|
| `{target_language}` | 翻訳先言語の英語表記（§5.5 の表を使用） |
| `{masked_text}` | `MaskResult.maskedText`（PII が `[Applicant name]` 等のラベル付きトークンに置換済み） |
| `{masked_categories}` | マスクした PII カテゴリの一覧（カンマ区切り） |
| `{user_notes}` | ユーザーが S-04 で入力したメモ（空の場合は `"(none)"`） |
| `{chat_history}` | `DocumentChatSession.getChatHistory()` を `Q:` / `A:` 形式に変換したテキスト |

---

## 7. MF-07: チャット用システムプロンプト

### 7.1 System Prompt

```
You are a helpful assistant for a user who has received a document.
The document has been analyzed and key information has been extracted.
Answer questions using both the structured summary and the original document text provided below.

Document name: {doc_name}
Summary: {summary}
Deadline: {deadline_note}
Required actions: {action_items}
Required items: {required_items}
Warnings: {warnings}

Original document text:
{source_text}

Help the user:
1. Understand the document and its requirements
2. Identify what they need to do and by when
3. Clarify any points they want to share with an expert or an AI assistant

Respond in {target_language}. Be concise. Limit your reply to 1–2 sentences.
```

> **maxOutputTokens について**: LiteRT-LM 0.11.0 の Kotlin API（`SamplerConfig`・`ConversationConfig`）にはトークン上限を per-call で設定するパラメータが存在しない。安全網としての `maxOutputTokens` はプロンプト制約（`Limit your reply to 1–2 sentences.`）で代替する。

### 7.2 変数

| 変数 | 生成元 | 形式 |
|------|--------|------|
| `{doc_name}` | `reviewResult.docName` | 文字列 |
| `{summary}` | `reviewResult.translation.summary`（翻訳あり）または `reviewResult.summaryJa` | 文字列 |
| `{deadline_note}` | `reviewResult.translation.deadlineNote` または `reviewResult.deadline.noteJa` | 文字列 |
| `{action_items}` | 番号付きリスト（翻訳済みフィールド優先） | プレーンテキスト |
| `{required_items}` | 番号付きリスト（翻訳済みフィールド優先） | プレーンテキスト |
| `{warnings}` | 番号付きリスト（翻訳済みフィールド優先） | プレーンテキスト |
| `{source_text}` | `DocumentReviewViewModel.sourceText`（TextExtractor が抽出した生テキスト、16,000 文字以内にトリム済み）。オンデバイス推論のため raw テキストを渡すことは許容（MF-02 と同様） | 文字列 |
| `{target_language}` | `reviewResult.translation?.language ?: reviewResult.sourceLanguage` → 英語表記（§5.5 の表を使用）。翻訳済みならその言語、未翻訳なら書類の原文言語（`sourceLanguage`）。翻訳完了時に再初期化するため常に正しい言語が渡される | 文字列 |

> `{deadline_note}` が null（期限なし書類）の場合は `"None"` を埋め込む。

> `{action_items}`・`{required_items}`・`{warnings}` は番号付きリスト形式でプレーンテキストに変換して埋め込む（JSON ではなく人間が読めるテキスト）。

### 7.3 初回アシスタントメッセージ（システムが挿入）

`initialize()` 呼び出し後にシステムが挿入する固定メッセージ。`{doc_name}` は `reviewResult.docName` を埋め込む。

| target_language | 初回メッセージ |
|----------------|--------------|
| `ja` | こんにちは。「{doc_name}」について何でも聞いてください。 |
| `en` | Hello! Feel free to ask me anything about the {doc_name}. |
| `zh` | 您好！请随时向我询问有关「{doc_name}」的任何问题。 |
| `ko` | 안녕하세요. 「{doc_name}」에 대해 무엇이든 질문해 주세요. |
| `es` | ¡Hola! No dudes en preguntarme cualquier cosa sobre el documento «{doc_name}». |
| `fr` | Bonjour ! N'hésitez pas à me poser toutes vos questions sur le document «{doc_name}». |
| `de` | Hallo! Stellen Sie mir gerne Fragen zum Dokument „{doc_name}". |
| `it` | Ciao! Sentiti libero di chiedermi qualsiasi cosa riguardo al documento «{doc_name}». |
| `pt` | Olá! Sinta-se à vontade para me perguntar qualquer coisa sobre o documento «{doc_name}». |
| `ru` | Здравствуйте! Не стесняйтесь задавать любые вопросы о документе «{doc_name}». |
| `pl` | Cześć! Śmiało pytaj mnie o wszystko dotyczące dokumentu „{doc_name}". |
| `nl` | Hallo! Stel gerust vragen over het document «{doc_name}». |
| `ar` | مرحباً! لا تتردد في سؤالي عن أي شيء يتعلق بالوثيقة «{doc_name}». |
| `th` | สวัสดี! อย่าลังเลที่จะถามฉันเกี่ยวกับเอกสาร «{doc_name}» |
| `tr` | Merhaba! «{doc_name}» belgesi hakkında bana istediğiniz her şeyi sorabilirsiniz. |
| その他 | Hello! Feel free to ask me anything about the {doc_name}. |

### 7.4 PII に関する注意事項

- `piiSpans[].spanText` はシステムプロンプトおよびチャット履歴に含めない
- `sourceText`（書類本文）はシステムプロンプトに含まれるが、オンデバイス推論（LiteRT-LM）であるため外部送信は発生しない（MF-02 と同様の扱い）

---

### 8.1 フィールド抽出 Few-shot Example（MF-02 用、9 フィールド）

`{few_shot_example}` に埋め込む行形式のサンプル（2 件）。`===` で区切る。

```
DOC_NAME: 令和7年度 児童手当現況届
ISSUER_NAME: 江戸川区役所
APPLICANT_NAME: 山田太郎
OTHER_NAME: (none)
IMPORTANCE: high
SUMMARY: 毎年6月に提出が必要な現況届です。未提出の場合、翌月以降の支給が停止されます。
ACTION_ITEMS: 現況届を記入して江戸川区役所に提出する
REQUIRED_ITEMS: 健康保険証|コピー可|||印鑑|(none)
WARNING: high|2年間未提出の場合、受給資格が消滅します

===

DOC_NAME: Surgical Procedure Consent Form
ISSUER_NAME: City General Hospital
APPLICANT_NAME: John Smith
OTHER_NAME: (none)
IMPORTANCE: medium
SUMMARY: A consent form for a scheduled surgical procedure. The patient must sign and return the form before the procedure date.
ACTION_ITEMS: Review and sign the consent form|||Return the signed form to the admissions desk before the procedure
REQUIRED_ITEMS: Government-issued photo ID|Required at check-in|||Insurance card|(none)
WARNING: medium|Failure to return the signed form may result in postponement of the procedure
```

### 8.2 EntityAnnotator Few-shot Example（2 件、`===` 区切り）

`{few_shot_example}` に埋め込むサンプル（Issue #134 §EntityAnnotator プロンプト仕様 より）。

```
issuer_name: 江戸川区役所
applicant_name: 山田太郎
other_name: (none)

1. DATE_TIME: 令和7年6月30日
2. DATE_TIME: 令和7年度
3. ADDRESS: 江戸川区中央1-2-3
4. PHONE: 03-1234-5678
5. EMAIL: kosodate@city.edogawa.tokyo.jp
6. ADDRESS: 港区虚空町1-2-3
7. DATE_TIME: 昭和60年1月1日
8. MONEY: 15,000円

---

1: deadline
2: document_date
3: issuer_address
4: issuer_phone
5: issuer_email
6: applicant_address
7: date_of_birth
8: benefit_amount

===

issuer_name: City General Hospital
applicant_name: John Smith
other_name: (none)

1. DATE_TIME: March 15, 2025
2. DATE_TIME: March 20, 2025
3. DATE_TIME: February 3, 1985
4. ADDRESS: 123 Medical Center Drive, Springfield
5. PHONE: (555) 234-5678
6. EMAIL: consent@citygeneral.org
7. ADDRESS: 456 Oak Street, Springfield
8. PHONE: (555) 987-6543
9. MONEY: $2,500.00

---

1: document_date
2: event_date
3: date_of_birth
4: issuer_address
5: issuer_phone
6: issuer_email
7: applicant_address
8: other_phone
9: fee
```

### 8.3 翻訳 Few-shot Example（MF-03 用）

`{few_shot_example}` に埋め込む行形式のサンプル。MF-02 と同じ書類（児童手当現況届）を英訳した標準サンプル。

```
SUMMARY: This is an annual status report that must be submitted in June. Failure to submit will stop benefit payments.
DEADLINE_NOTE: By June 30, 2025 (Monday)
ACTION_ITEMS: action_01|Fill in and submit the Status Report Form to the ward office
REQUIRED_ITEMS: doc_01|Health Insurance Card|Copies accepted|||doc_02|Personal Seal|(none)
WARNING: Failure to submit for 2 years will result in loss of eligibility
```

---

## 9. プロンプト変数リファレンス

実装時に各プロンプトへ埋め込む変数の一覧。

| 変数 | 使用プロンプト | 生成元クラス |
|------|--------------|------------|
| `{few_shot_example}` | MF-02 | `PromptBuilder.FEW_SHOT_MF02`（ハードコード、9 フィールド 2 件） |
| `{few_shot_example}` | EntityAnnotator | `PromptBuilder.FEW_SHOT_ENTITY_ANNOTATOR`（ハードコード、2 件） |
| `{few_shot_example}` | MF-03 | `PromptBuilder.FEW_SHOT_MF03`（ハードコード、5 フィールド） |
| `{issuer_name}` | EntityAnnotator | `reviewResult.issuerName ?: "(none)"` |
| `{applicant_name}` | EntityAnnotator | `reviewResult.applicantName ?: "(none)"` |
| `{other_name}` | EntityAnnotator | `reviewResult.otherName ?: "(none)"` |
| `{numbered_entities}` | EntityAnnotator | DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY のみ番号付きリスト |
| `{document_text}` | MF-02 | `DocumentReviewViewModel`（TextExtractor の出力をトリム） |
| `{parse_error_message}` | MF-02 / MF-03 リトライ | `FieldExtractor` / `Translator`（パースエラーメッセージ） |
| `{source_language}` | MF-03 | `LanguageIdentifier` が識別した言語コードを `PromptBuilder.languageCodeToLabel()` で変換 |
| `{target_language}` | MF-03 | `Review.selectedLanguage`（S-02 翻訳バーの選択言語） |
| `{target_language}` | MF-06a / MF-06 | `InquiryWizard.targetLanguage`（翻訳済みの場合はその言語をデフォルト） |
| `{target_language}` | MF-07 | `reviewResult.translation?.language ?: reviewResult.sourceLanguage` |
| `{fields_text}` | MF-03 | `Translator`（ReviewResult のフィールドをテキスト形式に変換） |
| `{doc_name}` | MF-06a / MF-07 | `reviewResult.docName` |
| `{summary}` | MF-06a / MF-07 | 翻訳済みフィールド優先（`translation.summary` → `summaryJa`） |
| `{action_items}` | MF-06a / MF-07 | 番号付きリスト形式（翻訳済み優先） |
| `{deadline_note}` | MF-07 | `DocumentChatSession`（翻訳済み優先、null → `"None"`） |
| `{required_items}` | MF-07 | `DocumentChatSession`（番号付きリスト形式） |
| `{warnings}` | MF-07 | `DocumentChatSession`（番号付きリスト形式） |
| `{source_text}` | MF-07 / MF-06a | `DocumentReviewViewModel.sourceText`（TextExtractor が抽出した生テキスト、16,000 文字以内にトリム済み） |
| `{masked_text}` | MF-06 | `MaskResult.maskedText` |
| `{masked_categories}` | MF-06 | `MaskResult.appliedSpans` のカテゴリ一覧（カンマ区切り） |
| `{user_notes}` | MF-06 | S-04 ユーザー入力メモ（null → `"(none)"`） |
| `{chat_history}` | MF-06 / MF-06a | `DocumentChatSession.getChatHistory()` を `Q:` / `A:` 形式に変換。MF-06a では空の場合はセクション自体を省略 |

---

## 10. 評価基準

### 10.1 MF-02 フィールド抽出

| 評価項目 | 合格基準 | テスト手法 |
|---------|---------|----------|
| 行形式パース成功率 | 初回リトライなしで 80% 以上 | `edogawa_R7_genkyo_kinyuurei.pdf` で 10 回実行 |
| `DOC_NAME` の抽出 | 書類タイトルが原文ママで抽出される | 手動確認 |
| `ISSUER_NAME` / `APPLICANT_NAME` の抽出 | 発行者・受給者の氏名が正しく分離して抽出される | 手動確認 |
| `IMPORTANCE` の判定 | 締め切り違反ペナルティあり書類で `"high"` | 手動確認 |
| 推論タイムアウト | 150 秒以内 | TC-PERF-02 |

### 10.2 MF-03 翻訳

| 評価項目 | 合格基準 | テスト手法 |
|---------|---------|----------|
| `translation.language` | 要求言語コードと一致 | 自動（TC-03-01） |
| `action_items` の件数 | 元データと一致 | 自動（TC-03-01） |
| 翻訳精度（英語） | summary・action_items・deadline_note・warnings の全フィールドが欠落なく翻訳されており、書類固有の用語が適切に英訳されている（例: 現況届 → "Status Report Form"、子育て支援課 → "Child Welfare Division"） | 手動確認 |
| 推論タイムアウト | 60 秒以内 | TC-PERF-03 |

### 10.3 MF-06 問い合わせコンテキスト生成

| 評価項目 | 合格基準 | テスト手法 |
|---------|---------|----------|
| MF-06a: 目的候補が 3〜5 件生成される | true（失敗時は空リストでフォールバック） | 手動確認 |
| コンテキストテキスト: PII 原文が含まれない | `piiSpans[].spanText` が `toContextText()` の出力に含まれない（`includedPiiSpans` 除く） | 自動（TC-06-ctx-01） |
| コンテキストテキスト: 各セクションの省略ロジック | deadline/required_docs/warnings が空の場合に対応セクションが省略される | 自動（TC-06-ctx-02） |

### 10.4 MF-07 チャット

| 評価項目 | 合格基準 | テスト手法 |
|---------|---------|----------|
| 初回メッセージの言語 | `target_language` と一致 | 自動（TC-07-02） |
| PII 原文を回答に含まない | `spanText` の文字列が回答に含まれない | 手動確認 |
| ファーストトークンまでの時間 | 3 秒以内 | TC-PERF-05 |
| システムプロンプトに応答制約が含まれる | `mf07SystemPrompt()` の出力に `"Limit your reply to 1–2 sentences."` が含まれる | 自動（TC-07-10） |
| 典型的な質問への応答が1–2文以内 | 「締め切りはいつか」「何を持参するか」等の質問に対して1–2文で返答される | 手動確認（TC-07-11） |

### 10.5 EntityAnnotator 評価

| 評価項目 | 合格基準 | テスト手法 |
|---------|---------|----------|
| context_label カバレッジ | DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY の全エンティティに対してラベルが付与される（"unknown" 以外） | `edogawa_R7_genkyo_kinyuurei.pdf` で 5 回実行・手動確認 |
| タイムアウト時のフォールバック | 60 秒超過時にエンティティが contextLabel = null のまま返り、クラッシュしない | 自動（モック仮想時間 advanceTimeBy） |
| パース失敗時のフォールバック | 不正な出力時にエンティティが unchanged で返り、クラッシュしない | 自動（モック不正応答） |
| PiiSpan の件数 | Tier-1/2 エンティティ + applicantName / otherName から 3 件以上生成される | 手動確認 |
| applicant_* ラベルのエンティティが Tier 1 に分類される | `computePiiTier()` が applicant_address / applicant_phone / applicant_email に 1 を返す | 自動（TC-ENT-tier-01） |
| ユーザーが含めなかった PII が問い合わせ文書に混入しない | `includedPiiSpans` 以外の `spanText` が `InquiryContext` に含まれない | 手動確認（TC-08-02） |

### 10.6 Gemma 4 E2B vs E4B の選択ガイドライン

E2B・E4B ともにモデルマネージャーでユーザーが手動選択する。自動切り替えは行わない。

| 状況 | 推奨 |
|------|------|
| 通常使用 | E2B（ダウンロードサイズが小さく起動が速い） |
| MF-02 のパース成功率が 50% 未満（10 回試行） | E4B への切り替えを検討 |
| MF-02 タイムアウト（150 秒）が 3 回連続 | E4B への切り替えを検討 |

---

*本仕様書は実装仕様書 v0.2.6 に基づく。プロンプトの改訂時は実機検証結果とともに本書を更新すること。*
*v0.1.1: MF-02 出力形式を JSON から行形式（Key-Value lines）に変更。LiteRT-LM 0.10.0 Kotlin API の Constrained Decoding 非対応と Gemma 4 E2B の key-as-value 崩壊問題への対応。*
*v0.1.2: 対象書類を行政書類から書類一般に拡張。MF-02 に DOC_NAME・DOC_DATE フィールドを追加。REQUIRED_DOCS を REQUIRED_ITEMS に改名。IMPORTANCE・CONDITIONS を削除（14 フィールド）。*
*v0.1.3: MF-02 に ISSUER・RECIPIENT フィールドを追加（16 フィールド）。*
*v0.1.4: DOC_TYPE を削除。LOCATION_NAME + LOCATION_ADDRESS → LOCATION、CONTACT_NAME + CONTACT_PHONE → CONTACT に統合。IMPORTANCE を復活。{doc_type} を {doc_name} に改名（14 フィールド）。*
*v0.1.5: PII 抽出を MF-02 から分離し MF-08 として独立ステップ化（エスカレーション時の PII 混入防止）。MF-02 は 13 フィールドに。§6 新設・§6-§8 を §7-§9 に繰り下げ。*
*v0.1.6: RECIPIENT を TARGET_PERSON に改名。MF-08 を MF-02 セッション継続方式に変更（テキスト再送不要）。*
*v0.1.7: MF-06 をエスカレーションパッケージ生成から問い合わせ文書生成ウィザードに全面改訂（目的候補提示 MF-06a・質問リスト一括生成 MF-06b・文書生成 MF-06c の3ステップ）。MF-06b を対話式1問ずつ方式から一括質問リスト生成方式に変更（LLM 呼び出し回数削減）。CONTACT に email サブフィールドを追加（3サブフィールド構成に変更）。*
*v0.1.9: 翻訳（MF-03）をユーザー手動実行に変更。S-01 の言語選択を廃止し、S-02 の翻訳バーに移動。チャット（MF-07）の初期言語を「日本語」に固定し、翻訳完了時にセッションを再初期化する設計に変更。`{target_language}` の導出ルールを MF-03/MF-06/MF-07 で明示的に分離。*
*v0.1.8: MF-06c（問い合わせ文書生成）を P2 に延期。オンデバイス推論での文書品質懸念（文体・敬語・言語ミスマッチ）のため。MF-06 の出力を「問い合わせ文書」から「コンテキストテキスト」に変更。ユーザーが外部 AI アシスタントに貼り付けて文書生成を依頼する設計に転換。§4.3 削除・変数リファレンスおよび評価基準を更新。*
*v0.2.0: MF-02・MF-08 のユーザーメッセージを日本語から英語に変更（日本語指示が LLM の出力言語を日本語に固定してしまう問題の修正）。MF-02 に SOURCE_LANGUAGE フィールドを追加し多言語書類に対応。*
*v0.2.1: MF-06b（不足情報質問リスト生成）を削除。実機検証で有用性が確認できなかったため。S-04 を単一ステップに簡略化。変数リファレンス・評価基準を更新。*
*v0.2.2: 書類・言語の制限を撤廃。MF-02/03/06/07/08 のシステムプロンプトから "Japanese document" を削除し多言語書類に対応。MF-02 に SOURCE_LANGUAGE フィールドを正式追加（14 フィールド）。チャット初期言語を日本語固定から書類の原文言語（sourceLanguage）に変更。*
*v0.2.3: MF-07 System Prompt に `Keep each response under 3 sentences.` を追加（長文応答の抑制・20 秒タイムアウト誘因の低減）。LiteRT-LM 0.10.0 Kotlin API には per-call の maxOutputTokens が存在しないため、プロンプト制約のみで対応。§5.1 に注記を追加。*
*v0.2.4: MF-02 を 16 フィールドに更新（LOCATION → LOCATIONS 複数対応・WARNINGS → WARNING 単一化・ISSUER_ADDRESS・EVENT_DATES・EXTRA_PII を追加）。旧 MF-08 を EXTRA_PII に統合し独立 LLM ステップを廃止。MF-03 出力形式を JSON から行形式（5 フィールド）に変更。MF-06（エスカレーション）サブセクションを新設。§7 MF-08 廃止ノート・§8 Few-shot 例・§9 変数リファレンス・§10 評価基準を更新。*
*v0.3.0: MF-02 を 16 フィールドから 9 フィールドに削減（DOC_NAME・ISSUER_NAME・APPLICANT_NAME・OTHER_NAME・IMPORTANCE・SUMMARY・ACTION_ITEMS・REQUIRED_ITEMS・WARNING）。日付・住所・電話・メール・金額・URL の抽出責務を ML Kit EntityExtractor + EntityAnnotator に移管。§4 EntityAnnotator セクション新設（システムプロンプト・ユーザーメッセージ・ALLOWED_LABELS・パース仕様）。§8 Few-shot examples に EntityAnnotator 用サンプルを追加。§10.1 評価基準を 9 フィールド対応に更新（DEADLINE_DATE 行を削除）。§10.5 を EXTRA_PII（旧 MF-08）から EntityAnnotator 評価基準に全面改訂。*
*v0.3.1: MF-07 チャットタイムアウトを 20 秒から 60 秒に延長（Translator・OcrCorrector と統一）。応答制約を `Keep each response under 3 sentences.` から `Limit your reply to 1–2 sentences.` に強化（3 文でも日本語で 150 字超になり得るため）。§7.1 System Prompt・§7.1 maxOutputTokens 注記・§10.4 TC-07-10/TC-07-11 を更新。*
