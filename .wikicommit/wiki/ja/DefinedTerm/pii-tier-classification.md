---
title: "PII Tier分類"
type: "schema:DefinedTerm"
lang: ja
description: "Paperwork Navigator が扱う各フィールドを外部送信可否によって3段階に分類する、プライバシー設計の基盤となるデータ分類体系"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [プライバシー設計]
sources:
  - type: file
    path: 'docs/privacy-spec_ja.md'
    hash: sha256:53dd18ffcce0a1e42958f5d057dcf4cca7021a4b13aab110f8450765cfda1caf
  - type: file
    path: 'docs/extraction-architecture-spec.md'
    hash: sha256:69627bb2df6e428d71a0b8a314b7853995a51c3099f9a3961a8faabff9797d8d
  - type: file
    path: 'docs/extraction-architecture-spec_ja.md'
    hash: sha256:0f0a3a2cf7dab13cb02ca0c0621acc8ced7666550be96ab3243f6a0e4ad61bb6
  - type: file
    path: 'docs/privacy-spec.md'
    hash: sha256:aed644ff5174a945730801d0d7037ebbe8e502975d4d4219730f261e5cf3ae07
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

PII Tier分類は、[[SoftwareApplication/paperwork-navigator]] が扱う各フィールドを外部送信可否によって Tier 1〜3 の3段階に分類する体系であり、`DetectedEntity.computePiiTier()` と `context_label` から静的に導出される。この分類は [[DefinedTerm/pii-masking]]（MF-05）の `maskRecommended` 設定や、各 LLM 呼び出しへのデータ送信ポリシーの判断基準として使われる。

## Usage

| Tier | 外部送信可否 | 代表データ |
|------|------------|----------|
| Tier 1（端末外に出さない） | 不可 | `source.txt`、`piiSpans` の spanText、`meta.json` 全体 |
| Tier 2（ユーザー同意のもと外部出力可） | ユーザーが共有シートで手動送信 | `MaskResult.maskedText`、`EscalationPackage` |
| Tier 3（PII なし） | 可 | `DocumentMeta`（`docName`、`importanceLevel`、`createdAt`） |

`piiSpans` は `piiTier` が 1 または 2 の全 `DetectedEntity` から静的に構築され、`applicant_name` / `other_name` / `issuer_name`（FieldExtractor 出力）は `mergeEntities` で明示的に `PiiSpan` として追加される（`issuer_name` は Tier 2）。

この分類は、次の設計原則に基づいている。

- **オンデバイス推論**: LLM 推論はすべて端末内で完結し、PII を含むテキストは外部サーバーに送信しない
- **最小権限**: 必要最低限のパーミッションのみ要求する（外部ストレージ読み書きは要求しない）
- **ユーザー同意によるエスカレーション**: マスク済みテキストの外部送信はユーザー自身が Android 共有シートで行い、アプリは自動送信しない
- **PII の明示的マスク**: 外部に出力しうるテキストには必ずマスク済みデータのみを使用する
- **透明性**: マスクされたフィールドのカテゴリを UI で明示する

Tier 1 と Tier 2 の判定はフェーズごとにも規定されており、例えば `TextExtractor` → `EntityExtractor` や `EntityExtractor` → `FieldExtractor` のように端末内 ML Kit / LLM に渡す処理はオンデバイスのため Tier 1 PII の受け渡しが許容される一方、共有インテント（Android 共有シート経由の外部送信）には Tier 1 PII を渡してはならず、`toPlainText()` の出力（Tier 2 相当のマスク済みテキスト）のみを渡す。Phase 2 でクラウド LLM へのエスカレーションを実装する場合も、`EscalationPackage`（マスク済み）のみを送信し、Tier 1 データを送信しないことを実装・レビューで保証する。

### 各 LLM 呼び出しのデータ送信ポリシー

| 推論 | 渡すデータ | 渡さないデータ |
|------|-----------|--------------|
| MF-02 FieldExtractor | `sourceText`（PII 原文を含む） | — （オンデバイスのため許容） |
| EntityAnnotator | `issuer_name` / `applicant_name` / `other_name`（名前ヒント）+ 5種エンティティの `rawText` | `piiSpans` の `spanText` |
| MF-03 Translator | `ReviewResult` の `summary` 等の構造化フィールド（事前マスク済み） | `piiSpans` の `spanText` |
| MF-06a `InquiryContextBuilder.suggestPurposes` | `ReviewResult` の構造化フィールド + `sourceText`（PII 原文を含む） | — （オンデバイスのため許容。MF-02 と同様） |
| MF-06 `EscalationPackageGenerator` | `maskResult.maskedText`（マスク済み） | PII 原文、`source.txt` |
| MF-07 `DocumentChatSession` | `ReviewResult` の構造化フィールド（`doc_name`、`summary` 等）+ `sourceText`（PII 原文を含む） | `piiSpans` の `spanText`（`sourceText` 自体はオンデバイスのため許容） |

`DocumentRepository.save()` は他アプリからアクセスできない `filesDir` 配下に保存する。共有インテント（Android 共有シート経由の外部送信）には Tier 1 データを渡してはならず、`toPlainText()` の出力のみを渡す。

### computePiiTier() とフィールド別 Tier

`DetectedEntity` の `piiTier` は次のロジックで導出される。

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

`DetectedEntity` として扱われない `applicant_name` / `other_name` / `issuer_name`（FieldExtractor 出力）は `computePiiTier()` を通さず `mergeEntities` で明示的に Tier を割り当てる（`applicant_name` は Tier 1、`other_name` / `issuer_name` は Tier 2）。代表的な `context_label` は次のとおりである。

| Tier | 定義 | 代表的な `context_label` |
|------|------|----------------------|
| Tier 1（高感度） | 個人を直接特定できる情報。端末外に出さない | `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth` / `iban` / `payment_card` |
| Tier 2（中感度） | 文脈次第で個人に結びつく情報。マスク対象 | `issuer_address` / `issuer_phone` / `issuer_email` / `other_*` / `benefit_amount` / `fee` / `penalty` / `tracking_number` |
| Tier 3（低感度） | 組織情報・公開情報・識別子 | `deadline` / `document_date` / `event_date` / `url` / `isbn` / `flight_number` |

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/pii-masking]]
