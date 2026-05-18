# Paperwork Navigator Extraction Architecture Specification

> Version: 1.0.3
> Created: 2026-05-15
> Last updated: 2026-05-18 (`maskRecommended` rule added to §7.2 for piiSpans construction: `piiTier == 1` -> true / `piiTier == 2` -> false)
> Scope: MVP (EntityExtractor / FieldExtractor / EntityAnnotator / mergeEntities)

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [Extracted Field Catalog](#2-extracted-field-catalog)
3. [Processing Flow](#3-processing-flow)
4. [Static PII Tier Definitions](#4-static-pii-tier-definitions)
5. [Data Models](#5-data-models)
6. [Quick Action Specification](#6-quick-action-specification)
7. [mergeEntities Rules](#7-mergeentities-rules)

---

## 1. Design Principles

### 1.1 Two-Stage ML Kit + LLM Approach

Information extraction from documents is split across two layers.

| Layer | Responsibility | Characteristics |
|---------|------|------|
| ML Kit Entity Extraction | Detect values (type + text) | Fast, deterministic, type-oriented |
| Gemma 4 (LLM) | Interpret meaning and structure | Flexible, context-aware, probabilistic |

ML Kit can reliably detect that a string is a phone number, but it cannot determine whether that number belongs to the issuer or the applicant. The LLM has the opposite strength profile: it is good at inferring meaning from context, but not as reliable at extracting values that can be captured deterministically with pattern-like rules. This separation lets each layer focus on what it does best.

### 1.2 `context_label` Is the Central Concept

All ML Kit-derived entities get their meaning through `context_label`.

| Type | How `context_label` is assigned |
|------|------------------------|
| ML Kit + LLM fields (DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY) | Assigned from context by EntityAnnotator (Gemma 4) |
| ML Kit-only fields (IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN) | Assigned statically by EntityExtractor |
| LLM-only fields (`importance`, `warning`, `action_items`, etc.) | Stored directly on `ReviewResult`, not as `DetectedEntity`, so they do not have `context_label` |

The following are derived statically from `context_label`:
- **PII tier** (`computePiiTier()`)
- **Quick action** type
- Eligibility for **piiSpans** construction

### 1.3 Why FieldExtractor Runs First

EntityAnnotator needs person names appearing in the document to decide ownership for ADDRESS / PHONE / EMAIL values (`issuer`, `applicant`, or `other`). Running FieldExtractor first determines `issuer_name` / `applicant_name` / `other_name`, which improves EntityAnnotator accuracy.

---

## 2. Extracted Field Catalog

All ML Kit-derived field names are identical to their `context_label`. Row order matches the display order on the S-02 review screen.

### Action and Urgency Information

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `importance` | LLM | - | Tier 3 | - |
| `deadline` | ML Kit + LLM | DATE_TIME | Tier 3 | `CalendarContract.Events.INSERT` |
| `event_date` | ML Kit + LLM | DATE_TIME | Tier 3 | `CalendarContract.Events.INSERT` |
| `warning` | LLM | - | Tier 3 | - |
| `action_items` | LLM | - | Tier 3 | - |
| `required_items` | LLM | - | Tier 3 | - |

### Basic Document Information

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `doc_name` | LLM | - | Tier 3 | - |
| `document_date` | ML Kit + LLM | DATE_TIME | Tier 3 | - |
| `summary` | LLM | - | Tier 3 | - |

### Issuer and Contact Information

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `issuer_name` | LLM | - | Tier 2 | - |
| `issuer_address` | ML Kit + LLM | ADDRESS | Tier 2 | `geo:0,0?q={address}` |
| `issuer_phone` | ML Kit + LLM | PHONE | Tier 2 | `tel:{phone}` |
| `issuer_email` | ML Kit + LLM | EMAIL | Tier 2 | `mailto:{email}` |

### Applicant Personal Information

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `applicant_name` | LLM | - | Tier 1 | - |
| `applicant_address` | ML Kit + LLM | ADDRESS | Tier 1 | - |
| `applicant_phone` | ML Kit + LLM | PHONE | Tier 1 | - |
| `applicant_email` | ML Kit + LLM | EMAIL | Tier 1 | - |
| `date_of_birth` | ML Kit + LLM | DATE_TIME | Tier 1 | - |

### Financial Information

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `benefit_amount` | ML Kit + LLM | MONEY | Tier 2 | - |
| `fee` | ML Kit + LLM | MONEY | Tier 2 | - |
| `penalty` | ML Kit + LLM | MONEY | Tier 2 | - |
| `other_amount` | ML Kit + LLM | MONEY | Tier 2 | - |
| `iban` | ML Kit | IBAN | Tier 1 | - |
| `payment_card` | ML Kit | PAYMENT_CARD | Tier 1 | - |

### Other People and Places

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `other_name` | LLM | - | Tier 2 | - |
| `other_address` | ML Kit + LLM | ADDRESS | Tier 2 | `geo:0,0?q={address}` |
| `other_phone` | ML Kit + LLM | PHONE | Tier 2 | `tel:{phone}` |
| `other_email` | ML Kit + LLM | EMAIL | Tier 2 | `mailto:{email}` |

### Digital and Identifier Fields

| Field name | Source | ML Kit entity | PII Tier | Quick action |
|--------|--------|-------------------|---------|-----------------|
| `url` | ML Kit | URL | Tier 3 | `ACTION_VIEW` |
| `tracking_number` | ML Kit | TRACKING_NUMBER | Tier 2 | `ACTION_VIEW` (tracking URL built from `metadata.carrier`) |
| `flight_number` | ML Kit | FLIGHT_NUMBER | Tier 3 | `ACTION_VIEW` (flight tracking URL built from `metadata.airlineCode`) |
| `isbn` | ML Kit | ISBN | Tier 3 | - |

---

## 3. Processing Flow

```text
[Text input]
     |
     v
EntityExtractor (ML Kit Entity Extraction)
  Extracts all 11 entity types and collects them as `DetectedEntity`
  ML Kit-only items (IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN)
  receive a static `contextLabel`
     |
     v
System.gc() (release memory before starting the LLM)
     |
     v
FieldExtractor (Gemma 4 MF-02)  --- inside Mutex
  Extracts 9 fields in line-based form
  Output: `doc_name` / `issuer_name` / `applicant_name` / `other_name` /
          `importance` / `summary` / `action_items` / `required_items` / `warning`
     |
     v
EntityAnnotator (Gemma 4)  --- inside Mutex, immediately after FieldExtractor
  Assigns `context_label` to the 5 types DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY
  Input hints: `issuer_name` / `applicant_name` / `other_name` from FieldExtractor
     |
     v
mergeEntities
  Derives `piiTier` for each `DetectedEntity` with `computePiiTier()`
  Builds `ReviewResult` according to rules per `context_label`
  Collects all entities where `piiTier` is in {1, 2} into `piiSpans`
```

### 3.1 Entities Targeted by EntityAnnotator

| Targeted by LLM annotation | Skipped with static assignment |
|--------------------------|-------------------|
| DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY | IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN |

Skipped items do not change meaning based on context, so EntityAnnotator is unnecessary for them. EntityExtractor simply assigns the type name as `contextLabel`.

### 3.2 EntityAnnotator Fallback

- If an entity returns `unknown` -> `contextLabel = null`
- On parse failure or timeout -> fall back with all entities left as `contextLabel = null` (no retry)

---

## 4. Static PII Tier Definitions

### 4.1 `computePiiTier()`

```kotlin
fun DetectedEntity.computePiiTier(): Int = when (type) {
    "IBAN", "PAYMENT_CARD" -> 1
    "ADDRESS", "PHONE", "EMAIL" ->
        if (contextLabel?.startsWith("applicant") == true) 1 else 2
    "TRACKING_NUMBER" -> 2
    "DATE_TIME" -> if (contextLabel == "date_of_birth") 1 else 3
    "MONEY" -> 2
    else -> 3  // URL, ISBN, FLIGHT_NUMBER
}
```

### 4.2 Meaning of Each Tier

| Tier | Definition | Representative `context_label` |
|------|------|----------------------|
| **Tier 1 (high sensitivity)** | Information that directly identifies a person and must never leave the device | `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth` / `iban` / `payment_card` |
| **Tier 2 (medium sensitivity)** | Information that may be linked to a person depending on context and should be masked | `issuer_address` / `issuer_phone` / `issuer_email` / `other_*` / `benefit_amount` / `fee` / `penalty` / `tracking_number` |
| **Tier 3 (low sensitivity)** | Organization data, public information, and low-risk identifiers | `deadline` / `document_date` / `event_date` / `url` / `isbn` / `flight_number` |

### 4.3 Tier for LLM Fields

LLM output fields that are not `DetectedEntity` do not go through `computePiiTier()` and are handled explicitly in `mergeEntities`.

| Field | Tier | Included in `piiSpans` |
|-----------|------|-----------------|
| `applicant_name` | Tier 1 | Built as `PiiSpan(category="name", piiTier=1)` |
| `other_name` | Tier 2 | Built as `PiiSpan(category="name", piiTier=2)` |
| `issuer_name` | Tier 2 | Built as `PiiSpan(category="name", piiTier=2)` |

---

## 5. Data Models

### 5.1 `DetectedEntity`

```kotlin
@Serializable
data class DetectedEntity(
    val type: String,                                    // ML Kit entity type
    @SerialName("raw_text") val rawText: String,         // raw source text
    @SerialName("context_label") val contextLabel: String? = null, // semantic label (null = unknown)
    @SerialName("pii_tier") val piiTier: Int? = null,   // derived by computePiiTier()
    val metadata: EntityMetadata? = null,
)
```

Cases where `contextLabel` becomes `null`:
- EntityAnnotator returned `unknown`
- EntityAnnotator failed parsing or timed out and fell back

### 5.2 `EntityMetadata`

```kotlin
@Serializable
data class EntityMetadata(
    // DATE_TIME
    val timestampMillis: Long? = null,
    val granularity: String? = null,
    // MONEY
    val currency: String? = null,
    val integerPart: Long? = null,
    // IBAN
    val ibanCountryCode: String? = null,
    // PAYMENT_CARD
    val cardNetwork: String? = null,
    // TRACKING_NUMBER
    val carrier: String? = null,
    // FLIGHT_NUMBER
    val airlineCode: String? = null,
)
```

---

## 6. Quick Action Specification

### 6.1 Intent Mapping

| `context_label` | Intent | Notes |
|--------------|--------|------|
| `deadline` | `CalendarContract.Events.INSERT` | Event title: `{doc_name} - Submission deadline` |
| `event_date` | `CalendarContract.Events.INSERT` | Event title: `{doc_name} - {raw_text}` |
| `issuer_address` | `geo:0,0?q={raw_text}` | |
| `other_address` | `geo:0,0?q={raw_text}` | |
| `issuer_phone` | `tel:{raw_text}` | |
| `other_phone` | `tel:{raw_text}` | |
| `issuer_email` | `mailto:{raw_text}` | |
| `other_email` | `mailto:{raw_text}` | |
| `url` | `ACTION_VIEW` | |
| `tracking_number` | `ACTION_VIEW` | Build the tracking URL from `metadata.carrier` |
| `flight_number` | `ACTION_VIEW` | Build the flight tracking URL from `metadata.airlineCode` |

### 6.2 Policy

- Do not show phone, map, or email actions for `applicant_*`, because that data refers to the user themself
- If no installed app can handle an intent, catch `ActivityNotFoundException` to avoid crashes
- Centralize intent generation in `util/DocumentIntentBuilder.kt`

---

## 7. `mergeEntities` Rules

### 7.1 Selection Rule per `context_label`

| `context_label` | Selection rule |
|--------------|--------|
| `deadline` | `firstOrNull` |
| `document_date` | `firstOrNull` |
| `issuer_address` | `firstOrNull` |
| `issuer_phone` | `firstOrNull` |
| `issuer_email` | `firstOrNull` |
| `applicant_address` | `firstOrNull` |
| `applicant_phone` | `firstOrNull` |
| `applicant_email` | `firstOrNull` |
| `date_of_birth` | `firstOrNull` |
| `other_address` | full list |
| `other_phone` | full list |
| `other_email` | full list |
| `event_date` | full list |
| `benefit_amount` | `firstOrNull` |
| `fee` | `firstOrNull` |
| `penalty` | `firstOrNull` |
| `other_amount` | full list |
| `iban` | `firstOrNull` |
| `payment_card` | `firstOrNull` |
| `url` | full list |
| `tracking_number` | full list |
| `flight_number` | full list |
| `isbn` | full list |

### 7.2 Building `piiSpans`

- Collect all `DetectedEntity` items whose `piiTier` is 1 or 2, remove duplicate `rawText`, and add them to `piiSpans`
- `applicant_name` (MF-02 output) -> explicitly construct `PiiSpan(spanText=rawText, category="name", sourceField="applicant_name", maskRecommended=true)` in `mergeEntities` (Tier 1)
- `other_name` (MF-02 output) -> explicitly construct `PiiSpan(spanText=rawText, category="name", sourceField="other_name", maskRecommended=false)` in `mergeEntities` (Tier 2)
- `issuer_name` (MF-02 output) -> explicitly construct `PiiSpan(spanText=rawText, category="name", sourceField="issuer_name", maskRecommended=false)` in `mergeEntities` (Tier 2 promotion)
- For ML Kit entities, set `sourceField = entity.contextLabel` (for example, `"issuer_address"`, `"applicant_phone"`). If `contextLabel` is null, as in EntityAnnotator failure, use `sourceField = null`
- MONEY entities (`benefit_amount` / `fee` / `penalty` / `other_amount`, Tier 2) are also included in `piiSpans` with `category="other"`
- `sourceField` is used by `PiiSpan.maskToken()` to generate mask tokens such as `[Applicant name]`
- **`maskRecommended` rule**: use `true` when `piiTier == 1`, and `false` when `piiTier == 2`. In the initial state of S-04, only Tier 1 spans are masked by default (`startInquiryWizard()` filters with `filter { it.maskRecommended }`)# Paperwork Navigator 抽出アーキテクチャ仕様書

> バージョン: 1.0.3
> 作成日: 2026-05-15
> 最終更新: 2026-05-18（§7.2 piiSpans 構築ルールに `maskRecommended` 設定ルールを追記: piiTier==1 → true / piiTier==2 → false）
> 対象: MVP（EntityExtractor / FieldExtractor / EntityAnnotator / mergeEntities）

---

## 目次

1. [設計思想](#1-設計思想)
2. [抽出項目一覧](#2-抽出項目一覧)
3. [処理フロー](#3-処理フロー)
4. [PII Tier 静的定義](#4-pii-tier-静的定義)
5. [データモデル](#5-データモデル)
6. [クイックアクション仕様](#6-クイックアクション仕様)
7. [mergeEntities ルール](#7-mergeentities-ルール)

---

## 1. 設計思想

### 1.1 ML Kit + LLM 二段階方式

書類からの情報抽出を2つのレイヤーで担う。

| レイヤー | 担当 | 特性 |
|---------|------|------|
| ML Kit Entity Extraction | 値の検出（型・テキスト） | 高速・確定的・型情報のみ |
| Gemma 4（LLM） | 値の意味付け・構造化 | 柔軟・文脈理解・確率的 |

ML Kit は「この文字列は電話番号である」という事実を確実に検出するが、「これは発行者の電話番号か申請者の電話番号か」は判断できない。LLM はその逆で、文脈から意味を読み取るのは得意だが、正規表現で拾えるような値を確実に抽出することは苦手。この役割分担により、各レイヤーが得意な処理に集中する。

### 1.2 context_label が中心概念

すべての ML Kit 由来エンティティは `context_label` によって意味が確定する。

| 種別 | context_label の設定方法 |
|------|------------------------|
| ML Kit+LLM 項目（DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY） | EntityAnnotator（Gemma 4）が文脈から付与 |
| ML Kit 専用項目（IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN） | EntityExtractor が静的に設定 |
| LLM 専用項目（importance / warning / action_items 等） | `DetectedEntity` でなく `ReviewResult` の直接フィールドのため `context_label` を持たない |

`context_label` から以下を静的に導出する：
- **PII Tier**（`computePiiTier()`）
- **クイックアクション**の種別
- **piiSpans** の構築対象

### 1.3 FieldExtractor を先に実行する理由

EntityAnnotator は ADDRESS / PHONE / EMAIL の帰属（issuer・applicant・other）を判断するために、書類に登場する人物名を手がかりとして使う。FieldExtractor を先に実行することで `issuer_name` / `applicant_name` / `other_name` が確定し、EntityAnnotator の精度が上がる。

---

## 2. 抽出項目一覧

ML Kit 由来の項目名はすべて `context_label` と同一。行の順序は S-02 レビュー画面での表示順に対応する。

### アクション・緊急情報

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `importance` | LLM | — | Tier 3 | — |
| `deadline` | ML Kit+LLM | DATE_TIME | Tier 3 | `CalendarContract.Events.INSERT` |
| `event_date` | ML Kit+LLM | DATE_TIME | Tier 3 | `CalendarContract.Events.INSERT` |
| `warning` | LLM | — | Tier 3 | — |
| `action_items` | LLM | — | Tier 3 | — |
| `required_items` | LLM | — | Tier 3 | — |

### 書類基本情報

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `doc_name` | LLM | — | Tier 3 | — |
| `document_date` | ML Kit+LLM | DATE_TIME | Tier 3 | — |
| `summary` | LLM | — | Tier 3 | — |

### 発行者・窓口連絡先

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `issuer_name` | LLM | — | Tier 2 | — |
| `issuer_address` | ML Kit+LLM | ADDRESS | Tier 2 | `geo:0,0?q={address}` |
| `issuer_phone` | ML Kit+LLM | PHONE | Tier 2 | `tel:{phone}` |
| `issuer_email` | ML Kit+LLM | EMAIL | Tier 2 | `mailto:{email}` |

### 申請者個人情報

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `applicant_name` | LLM | — | Tier 1 | — |
| `applicant_address` | ML Kit+LLM | ADDRESS | Tier 1 | — |
| `applicant_phone` | ML Kit+LLM | PHONE | Tier 1 | — |
| `applicant_email` | ML Kit+LLM | EMAIL | Tier 1 | — |
| `date_of_birth` | ML Kit+LLM | DATE_TIME | Tier 1 | — |

### 金融情報

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `benefit_amount` | ML Kit+LLM | MONEY | Tier 2 | — |
| `fee` | ML Kit+LLM | MONEY | Tier 2 | — |
| `penalty` | ML Kit+LLM | MONEY | Tier 2 | — |
| `other_amount` | ML Kit+LLM | MONEY | Tier 2 | — |
| `iban` | ML Kit | IBAN | Tier 1 | — |
| `payment_card` | ML Kit | PAYMENT_CARD | Tier 1 | — |

### その他の人物・場所

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `other_name` | LLM | — | Tier 2 | — |
| `other_address` | ML Kit+LLM | ADDRESS | Tier 2 | `geo:0,0?q={address}` |
| `other_phone` | ML Kit+LLM | PHONE | Tier 2 | `tel:{phone}` |
| `other_email` | ML Kit+LLM | EMAIL | Tier 2 | `mailto:{email}` |

### デジタル・識別子

| 項目名 | 抽出元 | ML Kit エンティティ | PII Tier | クイックアクション |
|--------|--------|-------------------|---------|-----------------|
| `url` | ML Kit | URL | Tier 3 | `ACTION_VIEW` |
| `tracking_number` | ML Kit | TRACKING_NUMBER | Tier 2 | `ACTION_VIEW`（キャリア別追跡 URL、metadata.carrier から構築） |
| `flight_number` | ML Kit | FLIGHT_NUMBER | Tier 3 | `ACTION_VIEW`（フライト追跡 URL、metadata.airlineCode から構築） |
| `isbn` | ML Kit | ISBN | Tier 3 | — |

---

## 3. 処理フロー

```
[テキスト入力]
     │
     ▼
EntityExtractor（ML Kit Entity Extraction）
  全 11 種のエンティティを抽出し DetectedEntity として収集
  ML Kit 専用項目（IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN）は
  contextLabel を静的に設定
     │
     ▼
System.gc()（LLM 起動前のメモリ解放）
     │
     ▼
FieldExtractor（Gemma 4 MF-02）  ─── Mutex 内
  9 フィールドを行形式で抽出
  出力: doc_name / issuer_name / applicant_name / other_name /
        importance / summary / action_items / required_items / warning
     │
     ▼
EntityAnnotator（Gemma 4）  ─── Mutex 内（FieldExtractor の直後）
  DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY の 5 種に context_label を付与
  入力ヒント: FieldExtractor の issuer_name / applicant_name / other_name
     │
     ▼
mergeEntities
  computePiiTier() で各 DetectedEntity の piiTier を導出
  context_label ごとのルールで ReviewResult を構築
  piiTier ∈ {1, 2} の全エンティティを piiSpans に収集
```

### 3.1 EntityAnnotator の対象エンティティ

| 対象（LLM アノテーション） | スキップ（静的設定） |
|--------------------------|-------------------|
| DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY | IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN |

スキップ対象は文脈によって意味が変わらないため EntityAnnotator によるアノテーションは不要。EntityExtractor が型名をそのまま `contextLabel` として設定する。

### 3.2 EntityAnnotator のフォールバック

- `unknown` が返ったエンティティ → `contextLabel = null`
- パース失敗・タイムアウト時 → 全エンティティを `contextLabel = null` のままフォールバック（リトライなし）

---

## 4. PII Tier 静的定義

### 4.1 computePiiTier()

```kotlin
fun DetectedEntity.computePiiTier(): Int = when (type) {
    "IBAN", "PAYMENT_CARD" -> 1
    "ADDRESS", "PHONE", "EMAIL" ->
        if (contextLabel?.startsWith("applicant") == true) 1 else 2
    "TRACKING_NUMBER" -> 2
    "DATE_TIME" -> if (contextLabel == "date_of_birth") 1 else 3
    "MONEY" -> 2
    else -> 3  // URL, ISBN, FLIGHT_NUMBER
}
```

### 4.2 Tier の意味

| Tier | 定義 | 代表的な context_label |
|------|------|----------------------|
| **Tier 1（高感度）** | 個人を直接特定できる情報。端末外に出さない | `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth` / `iban` / `payment_card` |
| **Tier 2（中感度）** | 文脈次第で個人に結びつく情報。マスク対象 | `issuer_address` / `issuer_phone` / `issuer_email` / `other_*` / `benefit_amount` / `fee` / `penalty` / `tracking_number` |
| **Tier 3（低感度）** | 組織情報・公開情報・識別子 | `deadline` / `document_date` / `event_date` / `url` / `isbn` / `flight_number` |

### 4.3 LLM フィールドの Tier

`DetectedEntity` でない LLM 出力フィールドは `computePiiTier()` を通さず、mergeEntities で明示的に処理する。

| フィールド | Tier | piiSpans への収録 |
|-----------|------|-----------------|
| `applicant_name` | Tier 1 | `PiiSpan(category="name", piiTier=1)` として構築 |
| `other_name` | Tier 2 | `PiiSpan(category="name", piiTier=2)` として構築 |
| `issuer_name` | Tier 2 | `PiiSpan(category="name", piiTier=2)` として構築 |

---

## 5. データモデル

### 5.1 DetectedEntity

```kotlin
@Serializable
data class DetectedEntity(
    val type: String,                                    // ML Kit エンティティ型
    @SerialName("raw_text") val rawText: String,         // 原文テキスト
    @SerialName("context_label") val contextLabel: String? = null, // 意味ラベル（null = unknown）
    @SerialName("pii_tier") val piiTier: Int? = null,   // computePiiTier() で導出
    val metadata: EntityMetadata? = null,
)
```

`contextLabel` が `null` になるケース：
- EntityAnnotator が `unknown` を返した
- EntityAnnotator がパース失敗・タイムアウトしてフォールバックした

### 5.2 EntityMetadata

```kotlin
@Serializable
data class EntityMetadata(
    // DATE_TIME
    val timestampMillis: Long? = null,
    val granularity: String? = null,
    // MONEY
    val currency: String? = null,
    val integerPart: Long? = null,
    // IBAN
    val ibanCountryCode: String? = null,
    // PAYMENT_CARD
    val cardNetwork: String? = null,
    // TRACKING_NUMBER
    val carrier: String? = null,
    // FLIGHT_NUMBER
    val airlineCode: String? = null,
)
```

---

## 6. クイックアクション仕様

### 6.1 Intent 対応表

| context_label | Intent | 備考 |
|--------------|--------|------|
| `deadline` | `CalendarContract.Events.INSERT` | イベント名: `{doc_name} - 提出期限` |
| `event_date` | `CalendarContract.Events.INSERT` | イベント名: `{doc_name} - {raw_text}` |
| `issuer_address` | `geo:0,0?q={raw_text}` | |
| `other_address` | `geo:0,0?q={raw_text}` | |
| `issuer_phone` | `tel:{raw_text}` | |
| `other_phone` | `tel:{raw_text}` | |
| `issuer_email` | `mailto:{raw_text}` | |
| `other_email` | `mailto:{raw_text}` | |
| `url` | `ACTION_VIEW` | |
| `tracking_number` | `ACTION_VIEW` | metadata.carrier から追跡 URL を構築 |
| `flight_number` | `ACTION_VIEW` | metadata.airlineCode からフライト追跡 URL を構築 |

### 6.2 方針

- `applicant_*` はユーザー自身の情報のため、電話・地図・メールへのアクションは表示しない
- Intent を処理できるアプリが端末にない場合は `ActivityNotFoundException` をキャッチしてクラッシュを防ぐ
- Intent 生成は `util/DocumentIntentBuilder.kt` に集約する

---

## 7. mergeEntities ルール

### 7.1 context_label ごとの取り方

| context_label | 取り方 |
|--------------|--------|
| `deadline` | `firstOrNull` |
| `document_date` | `firstOrNull` |
| `issuer_address` | `firstOrNull` |
| `issuer_phone` | `firstOrNull` |
| `issuer_email` | `firstOrNull` |
| `applicant_address` | `firstOrNull` |
| `applicant_phone` | `firstOrNull` |
| `applicant_email` | `firstOrNull` |
| `date_of_birth` | `firstOrNull` |
| `other_address` | 全件リスト |
| `other_phone` | 全件リスト |
| `other_email` | 全件リスト |
| `event_date` | 全件リスト |
| `benefit_amount` | `firstOrNull` |
| `fee` | `firstOrNull` |
| `penalty` | `firstOrNull` |
| `other_amount` | 全件リスト |
| `iban` | `firstOrNull` |
| `payment_card` | `firstOrNull` |
| `url` | 全件リスト |
| `tracking_number` | 全件リスト |
| `flight_number` | 全件リスト |
| `isbn` | 全件リスト |

### 7.2 piiSpans の構築

- `piiTier` が 1 または 2 の `DetectedEntity` を全件収集し、重複 `rawText` を除去して `piiSpans` に追加する
- `applicant_name`（MF-02 出力）→ `PiiSpan(spanText=rawText, category="name", sourceField="applicant_name", maskRecommended=true)` を mergeEntities で明示的に構築（Tier 1）
- `other_name`（MF-02 出力）→ `PiiSpan(spanText=rawText, category="name", sourceField="other_name", maskRecommended=false)` を mergeEntities で明示的に構築（Tier 2）
- `issuer_name`（MF-02 出力）→ `PiiSpan(spanText=rawText, category="name", sourceField="issuer_name", maskRecommended=false)` を mergeEntities で明示的に構築（Tier 2 昇格）
- ML Kit エンティティ → `sourceField = entity.contextLabel`（例: `"issuer_address"`, `"applicant_phone"` 等）を設定する。`contextLabel` が null の場合（EntityAnnotator 失敗時）は `sourceField = null`
- MONEY エンティティ（`benefit_amount` / `fee` / `penalty` / `other_amount`、Tier 2）も piiSpans に収録する（`category="other"`）
- `sourceField` は `PiiSpan.maskToken()` でマスクトークン（`[Applicant name]` 等）を生成するために使用する
- **`maskRecommended` の設定ルール**: `piiTier == 1` の場合 `true`、`piiTier == 2` の場合 `false`。S-04 の初期状態で Tier 1 スパンのみがデフォルトでマスクされる（`startInquiryWizard()` が `filter { it.maskRecommended }` で絞り込む）
