# Paperwork Navigator プライバシー仕様書

> バージョン: 0.2.3
> 作成日: 2026-05-07
> 最終更新: 2026-05-18（§5.3 applicant_name 名前バリアントマスクのルールを追記）
> 対象: MVP（P1 機能）

---

## 目次

1. [設計原則](#1-設計原則)
2. [データ分類](#2-データ分類)
3. [PII ライフサイクル](#3-pii-ライフサイクル)
4. [LLM へのデータ送信ポリシー](#4-llm-へのデータ送信ポリシー)
5. [UI・UX のプライバシー配慮](#5-uiux-のプライバシー配慮)
6. [MVP の制限と今後の計画](#6-mvp-の制限と今後の計画)

> **関連仕様書**: PII の Tier 分類（Tier 1〜3）・各 context_label とその Tier の詳細定義・`computePiiTier()` の実装は **[抽出アーキテクチャ仕様書](抽出アーキテクチャ仕様書_ja.md)** §4 を参照。本書はデータの外部送信ポリシーとライフサイクルに特化する。

---

## 1. 設計原則

| 原則 | 実装方針 |
|------|---------|
| **オンデバイス推論** | LLM 推論はすべて端末内で完結。PII を含むテキストは外部サーバーに送信しない |
| **最小権限** | 必要最低限のパーミッションのみ要求（外部ストレージ読み書きは要求しない） |
| **ユーザー同意によるエスカレーション** | マスク済みテキストの外部送信はユーザー自身が Android 共有シートで行う。アプリは自動送信しない |
| **PII の明示的マスク** | 外部に出力しうるテキストには必ずマスク済みデータのみを使用する |
| **透明性** | マスクされたフィールドのカテゴリを UI で明示する |

---

## 2. データ分類

各フィールドの Tier は `DetectedEntity.computePiiTier()` と `context_label` から静的に導出される。詳細な分類ルールは **[抽出アーキテクチャ仕様書](抽出アーキテクチャ仕様書_ja.md) §4** を参照。

| Tier | 外部送信可否 | 代表データ |
|------|------------|----------|
| **Tier 1（端末外に出さない）** | 不可 | `source.txt`、`piiSpans` の spanText、`meta.json` 全体 |
| **Tier 2（ユーザー同意のもと外部出力可）** | ユーザーが共有シートで手動送信 | `MaskResult.maskedText`、`EscalationPackage` |
| **Tier 3（PII なし）** | 可 | `DocumentMeta`（`docName`, `importanceLevel`, `createdAt`） |

> **piiSpans の構築**: `piiTier` が 1 または 2 の全 `DetectedEntity` から `piiSpans` を静的に構築する。`applicant_name` / `other_name` / `issuer_name`（FieldExtractor 出力）は `mergeEntities` で明示的に `PiiSpan` として追加する（`issuer_name` は Tier 2）。詳細は `抽出アーキテクチャ仕様書.md` §7.2 参照。
>
> **`maskRecommended` の設定ルール**: Tier 1 PII（`applicant_name`、`applicant_address`、`applicant_phone`、`applicant_email`、`date_of_birth`、`iban`、`payment_card`）は `maskRecommended = true`。Tier 2 PII（`issuer_name`、`issuer_address`、`issuer_phone`、`issuer_email`、`other_*`、MONEY エンティティ等）は `maskRecommended = false`。S-04 の初期状態では `maskRecommended = true` のスパンのみがデフォルトでチェック（マスク）状態になる。

---

## 3. PII ライフサイクル

```
PDF / テキスト
  │
  ▼ MF-01
TextExtractor ──────────────────────────────────────────► source.txt（Tier 1: 保存）
  │
  ▼ EntityExtractor（ML Kit）
  全 11 種のエンティティを抽出。ML Kit 専用項目は contextLabel を静的設定
  │
  ▼ System.gc()
  │
  ▼ MF-02
FieldExtractor（Gemma 4）──────────────────────────────► issuer_name / applicant_name / other_name 等
  │
  ▼ EntityAnnotator（Gemma 4）
  DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY に context_label を付与
  │
  ▼ mergeEntities
  computePiiTier() → piiSpans 構築 ──────────────────────► meta.json（Tier 1: 保存）
  │                    │
  │ （summary 等）      │ （ReviewResult + piiSpans）
  ▼ MF-03              ▼ MF-05
Translator           PiiMasker ──────────────────────────► maskedText（Tier 2）
（翻訳結果を              │
 reviewResult に追記）    ▼ MF-06
                  EscalationPackageGenerator ─────────────► escalation.json（Tier 2: 保存）
                                                                │
                                                                ▼
                                                   [ユーザーが共有シート経由で外部送信]
```

### 3.1 各フェーズの PII ポリシー

| フェーズ | Tier 1 PII | Tier 2（マスク済み） |
|---------|-----------|-------------------|
| TextExtractor → EntityExtractor | 端末内 ML Kit に渡す（オンデバイスのため許容） | — |
| EntityExtractor → FieldExtractor | 端末内 LLM に渡す（オンデバイスのため許容） | — |
| FieldExtractor → EntityAnnotator | issuer_name / applicant_name / other_name を渡す。spanText は渡さない | — |
| mergeEntities → PiiMasker | piiSpans（spanText を含む）は端末内で処理。ログに出力しない | — |
| MF-03 Translator | summary 等の構造化フィールドのみ渡す（PiiMasker で事前マスク済み）。`piiSpans[].spanText` は渡さない | — |
| MF-07 チャットセッション | システムプロンプトに PII 原文を含めない（ReviewResult の構造化データのみ） | — |
| MF-06 エスカレーション生成 | LLM プロンプトに渡さない | maskedText のみ使用 |
| DocumentRepository.save() | filesDir に保存（他アプリからアクセス不可） | — |
| 共有インテント | **渡してはならない** | `toPlainText()` の出力のみ渡す |

### 3.2 削除ポリシー

- `DocumentRepository.delete(docId)` 呼び出しで `{filesDir}/documents/{docId}/` ディレクトリ全体を削除する
- アプリアンインストール時は OS により `filesDir` 全体が削除される
- **MVP では書類管理 UI（一覧・削除）を未実装のため、ユーザーがアプリ操作でデータを削除する手段はアンインストールのみ。** Phase 2 で書類管理画面を実装する際に削除機能を合わせて追加する

---

## 4. LLM へのデータ送信ポリシー

LLM 推論はすべて LiteRT-LM を使用したオンデバイス処理であり、ネットワーク通信は発生しない。

### 4.1 各 LLM 呼び出しの入力データ

| 推論 | 渡すデータ | 渡さないデータ |
|------|-----------|--------------|
| MF-02 FieldExtractor | sourceText（PII 原文を含む） | — （オンデバイスのため許容） |
| EntityAnnotator | issuer_name / applicant_name / other_name（名前ヒント）+ 5 種エンティティの rawText | piiSpans の spanText |
| MF-03 Translator | ReviewResult の summary 等の構造化フィールド（事前マスク済みのため fields_text にも PII 原文を含まない） | piiSpans の spanText |
| MF-06a InquiryContextBuilder.suggestPurposes | ReviewResult の summary 等の構造化フィールド + sourceText（PII 原文を含む） | — （オンデバイスのため許容。MF-02 と同様） |
| MF-06 EscalationPackageGenerator | maskResult.maskedText（マスク済み） | PII 原文、source.txt |
| MF-07 DocumentChatSession | ReviewResult の構造化フィールド（doc_name, summary 等）+ sourceText（PII 原文を含む） | piiSpans の spanText（オンデバイスのため sourceText 自体は許容。MF-02 と同様） |

### 4.2 クラウド LLM エスカレーション（MVP 対象外）

Phase 2 でクラウド LLM へのエスカレーションを実装する場合は、必ず `EscalationPackage`（マスク済み）のみを送信する。Tier 1 データを送信しないことを実装・レビューで保証する。

---

## 5. UI・UX のプライバシー配慮

### 5.1 マスク状態の視覚的フィードバック

- マスク済みスパンは `[Applicant name]` / `[Issuer address]` 等のラベル付きトークンで表示する（`PiiSpan.maskToken()` 参照）。`sourceField` が不明な場合は `[■■■]` にフォールバックする
- S-03 の先頭に「⚠️ 個人情報はマスク済み / masked: {categories}」を表示する
- `unmatchedSpans` が存在する場合は S-02 で「マスクできなかった項目があります」を通知する

### 5.2 ユーザーによるマスク解除操作

- ユーザーは S-02 の PII 編集パネルでスパンごとに `userOverride = false`（マスク除外）を設定できる
- マスク除外したスパンの spanText は `maskedText` に原文のまま残り、`escalation.json` の `masked_source_text` にも含まれる。これはユーザーが意図的に共有を選んだ情報として扱う

### 5.3 applicant_name の名前バリアントマスク

`PiiMasker` は `applicant_name` スパンに対して、フルネームの完全一致に加えて **部分名バリアント** を自動生成してマスクする。これにより「Dear Mr. Rivera,」のような敬称付き宛名での PII 漏洩を防止する。

**バリアント生成ルール（`PiiMasker.nameVariants()`）:**

| 入力（fullName） | 候補トークン | 生成されるバリアント |
|----------------|------------|-------------------|
| "Carlos Rivera" | "Carlos" / "Rivera" | "Carlos", "Rivera", "Mr. Carlos", "Ms. Carlos", "Mrs. Carlos", "Dr. Carlos", "Sir Carlos", "Mr. Rivera", ... |
| "山田 太郎" | "山田" / "太郎" | "山田", "太郎"（3文字未満はスキップ、敬称なし） |
| "山田太郎" | なし（スペースなし） | なし |

**制約:**
- スペース区切りがない場合（例: "山田太郎"）はバリアント生成対象外
- 3文字未満のトークン（例: "Li"）はバリアント生成対象外（誤検出防止）
- バリアントのマスクトークンはフルネームと同じ `[Applicant name]` を使用
- `issuer_name` / `other_name` はバリアントマスク対象外

---

## 6. MVP の制限と今後の計画

| 項目 | MVP 状態 | 今後の計画 |
|------|---------|-----------|
| ストレージ暗号化 | なし（OS サンドボックスのみ） | Keystore + AES-256-GCM |
| バックアップ無効化 | `android:allowBackup="false"` を AndroidManifest に設定すること（AI Edge Gallery fork のベースコードに含まれているか確認の上、なければ追加） | 同左（継続） |
| 自動削除（TTL） | なし | 設定可能な TTL（例: 30 日） |
| 共有前確認ダイアログ | なし | 確認ダイアログを追加 |
| クラウド LLM へのデータ送信 | 非対応（オンデバイスのみ） | マスク済みデータのみ送信可能にする |

---

*本仕様書は実装仕様書 v0.2.6 に基づく。実装が仕様から変更された場合は本書を合わせて更新すること。*
*v0.2.0: §2 データ分類を抽出アーキテクチャ仕様書（§4）へ移管し概要のみ残す。PII ライフサイクル図（§3）に EntityExtractor / EntityAnnotator / mergeEntities ステップを追加。piiCandidates → piiSpans に統一。§3.1 / §4.1 のポリシー表を新アーキテクチャに合わせて更新。*
