---
title: "構造化フィールド抽出"
type: "schema:DefinedTerm"
lang: ja
description: "書類テキストから Gemma 4 と ML Kit Entity Extraction により ReviewResult と PiiSpan を導出する処理（FieldExtractor / EntityAnnotator / mergeEntities）"
termCode: "MF-02"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [オンデバイス処理]
sources:
  - type: file
    path: 'docs/implementation-spec_ja.md'
    hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312
  - type: file
    path: 'docs/extraction-architecture-spec.md'
    hash: sha256:69627bb2df6e428d71a0b8a314b7853995a51c3099f9a3961a8faabff9797d8d
  - type: file
    path: 'docs/extraction-architecture-spec_ja.md'
    hash: sha256:0f0a3a2cf7dab13cb02ca0c0621acc8ced7666550be96ab3243f6a0e4ad61bb6
  - type: file
    path: 'docs/implementation-spec.md'
    hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

構造化フィールド抽出（MF-02）は、[[SoftwareApplication/paperwork-navigator]] が書類テキストから `ReviewResult`（書類タイトル・締め切り・必要な行動・警告など）と PII スパンを導出する処理であり、`FieldExtractor`（Gemma 4 推論 #1）・`EntityAnnotator`（Gemma 4 推論 #2）・`EntityExtractor.mergeEntities`（LLM 不要）の3段階で構成される。

## Usage

**FieldExtractor**: 書類テキスト（16,000 文字以内）を Gemma 4 に渡し、行形式（Key-Value lines）で9フィールドを出力させる。ストリームで受け取り、完了後に行形式パースを行う。パースエラー時は最大2回リトライ（エラー情報をプロンプトに追加）し、失敗時は `FieldExtractionError.JsonParseError` 等を投げる。推論タイムアウトは150秒で、タイムアウト時は「解析に失敗しました。再試行してください」を表示する。

**EntityAnnotator**: ML Kit Entity Extraction が抽出した全11種のエンティティのうち、`DATE_TIME` / `ADDRESS` / `PHONE` / `EMAIL` / `MONEY` の5種に `context_label` を付与する（Gemma 4 推論 #2）。`issuer_name` / `applicant_name` / `other_name`（FieldExtractor 出力の名前ヒント）を渡して文脈付けの手がかりとする。

**mergeEntities**: `context_label` から `deadline` / `docDate` / `issuerAddress` / `locations` / `eventDates` を導出し、`piiTier ∈ {1, 2}` の全エンティティを `piiSpans` に収集する（[[DefinedTerm/pii-tier-classification]] 参照）。この結果は `meta.json`（Tier 1）として保存される。

行形式が採用されている理由は、実機検証により LiteRT-LM 0.11.0 Kotlin API に Constrained Decoding が存在せず、複雑なネスト JSON では key-as-value 崩壊が発生することが確認されたためであり、MF-02・MF-03 共通の設計判断である。System Prompt は英語で記述し、few-shot example を含めることで精度を安定させる。

### 設計原則

MF-02 の抽出は ML Kit Entity Extraction（値の検出、高速・確定的）と Gemma 4（意味づけ・構造化、柔軟・文脈理解）の2層に分かれる。ML Kit は文字列が電話番号であることは確実に検出できるが、それが発行者のものか申請者のものかは判断できない一方、LLM は文脈からの意味推定は得意だが正規表現的に確定抽出できる値の抽出は苦手であるため、各レイヤーが得意な処理に専念する設計になっている。FieldExtractor を EntityAnnotator より先に実行するのは、`issuer_name` / `applicant_name` / `other_name` を先に確定させ、EntityAnnotator が ADDRESS / PHONE / EMAIL の帰属判定（発行者・申請者・その他）を行う際の手がかりとするためである。

すべての ML Kit 由来エンティティは `context_label` によって意味が確定し、そこから PII Tier（[[DefinedTerm/pii-tier-classification]] 参照）・[[DefinedTerm/quick-action]] の種別・`piiSpans` 構築対象（[[DefinedTerm/pii-masking]] 参照）が静的に導出される。DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY の5種は EntityAnnotator が文脈から `context_label` を付与し、IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN の6種は EntityExtractor が型名をそのまま静的に設定する。`importance` / `warning` / `action_items` などの LLM 専用項目は `DetectedEntity` ではなく `ReviewResult` の直接フィールドのため `context_label` を持たない。EntityAnnotator が `unknown` を返した場合や、パース失敗・タイムアウト時（リトライなし）は全エンティティが `contextLabel = null` にフォールバックする。

### データモデル

`DetectedEntity`（`type` / `rawText` / `contextLabel` / `piiTier` / `metadata`）が ML Kit 由来エンティティの基本単位で、`piiTier` は `computePiiTier()` により導出される。`EntityMetadata` は DATE_TIME（`timestampMillis` / `granularity`）・MONEY（`currency` / `integerPart`）・IBAN（`ibanCountryCode`）・PAYMENT_CARD（`cardNetwork`）・TRACKING_NUMBER（`carrier`）・FLIGHT_NUMBER（`airlineCode`）ごとの付随情報を保持する。

### 抽出項目カタログと mergeEntities

抽出対象は `importance` / `deadline` / `event_date` / `warning` / `action_items` / `required_items` の緊急情報（6項目）、`doc_name` / `document_date` / `summary` の基本情報（3項目）、`issuer_name` / `issuer_address` / `issuer_phone` / `issuer_email` の発行者連絡先（4項目）、`applicant_name` / `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth` の申請者個人情報（5項目）、`benefit_amount` / `fee` / `penalty` / `other_amount` / `iban` / `payment_card` の金融情報（6項目）、`other_name` / `other_address` / `other_phone` / `other_email` のその他人物・場所（4項目）、`url` / `tracking_number` / `flight_number` / `isbn` のデジタル・識別子（4項目）の全32フィールドで構成され、レビュー画面（S-02）での表示順もこの並びに対応する。`mergeEntities` は `context_label` ごとに `firstOrNull`（`deadline` / `issuer_address` / `applicant_phone` など単一値項目）または全件リスト（`other_address` / `event_date` / `url` など複数取りうる項目）のいずれかで `DetectedEntity` を集約する。

### ReviewResult データモデルとエラー

`ReviewResult` は `docName` / `docDate` / `issuerName` / `applicantName` / `otherName` / `importance`（high/medium/low）/ `summaryJa` / `deadline`（`DeadlineInfo`: `date` + `noteJa`）/ `issuerAddress` / `locations`（`LocationEntry` のリスト）/ `actionItems`（`ActionItem`: id・`descriptionJa`・`priority`）/ `requiredItems`（`RequiredItem`: id・`nameJa`・`noteJa`）/ `warning`（`Warning`: id・`descriptionJa`・`severity`、最重要の1件のみ）/ `eventDates`（`EventDate`）/ `translation` / `sourceLanguage` / `detectedEntities` の各フィールドで構成される。フィールド名の `_ja` サフィックスは日本語限定ではなく、書類の原文言語（`sourceLanguage`）のテキストを保持することを意味する。`FieldExtractor.extract()` は JSON パースエラー時に `FieldExtractionError.JsonParseError`、モデル未初期化時に `ModelNotInitialized`、その他の推論エラー時に `InferenceError` を投げる。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/text-extraction]]
- [[DefinedTerm/pii-masking]]
- [[DefinedTerm/pii-tier-classification]]
- [[DefinedTerm/quick-action]]
- [[DefinedTerm/review-screen]]
