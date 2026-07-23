---
title: "PIIマスク"
type: "schema:DefinedTerm"
lang: ja
description: "抽出済みエンティティをルールベースでマスクし、氏名・住所・ID番号などの機密情報を端末外に出さないための非LLM処理"
termCode: "MF-05"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [オンデバイス処理]
sources:
  - type: file
    path: 'README_ja.md'
    hash: sha256:3a8db998c216c576c641a151a9a2bcad45b3ce95bec627233dc53dbd50f41719
  - type: file
    path: 'docs/privacy-spec_ja.md'
    hash: sha256:53dd18ffcce0a1e42958f5d057dcf4cca7021a4b13aab110f8450765cfda1caf
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
  - type: file
    path: 'docs/privacy-spec.md'
    hash: sha256:aed644ff5174a945730801d0d7037ebbe8e502975d4d4219730f261e5cf3ae07
  - type: file
    path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
    hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

PIIマスク（MF-05）は、[[SoftwareApplication/paperwork-navigator]] においてエンティティ抽出済みの情報をルールベースでマスクする処理である。LLM は使用せず、ユーザーはマスクをスパン単位でオン/オフできる。

## Usage

[[SoftwareApplication/paperwork-navigator]] のプライバシー設計では、情報を [[DefinedTerm/pii-tier-classification]] に基づき 3 段階の Tier に分類している。Tier 1（氏名・住所・生年月日・マイナンバー・口座番号など、`source.txt` / `piiSpans` の spanText / `meta.json` 全体）は端末外に一切出さず、Tier 2（発行者連絡先・期限・金額など、`MaskResult.maskedText` / `EscalationPackage`）はユーザー同意のもとマスク済み形式でのみ外部出力可能、Tier 3（`DocumentMeta` の `docName` / `importanceLevel` / `createdAt` など）は PII を含まない。PIIマスクはこの Tier 分類に基づき、ML Kit エンティティ抽出（MF-02）と Gemma 4 によるエンティティ意味付けで得られたエンティティに対して、レビュー画面（MF-04）に表示される前の段階で適用され、マスクしたフィールドのカテゴリは UI 上で明示される。マスク済みのテキストは [[DefinedTerm/inquiry-context-generation]]（MF-06）の入力として利用される。

`maskRecommended` の設定ルールでは、Tier 1 PII（`applicant_name`、`applicant_address`、`applicant_phone`、`applicant_email`、`date_of_birth`、`iban`、`payment_card`）は `maskRecommended = true`、Tier 2 PII（`issuer_name`、`issuer_address`、`issuer_phone`、`issuer_email`、`other_*`、MONEY エンティティ等）は `maskRecommended = false` となる。問い合わせ文書作成画面（S-04）の初期状態では `maskRecommended = true` のスパンのみがデフォルトでチェック（マスク）状態になる。

### piiSpans の構築（mergeEntities）

`piiSpans` は MF-02 の `mergeEntities` 処理で構築される。`piiTier` が 1 または 2 の `DetectedEntity` を全件収集し、重複する `rawText` を除去したうえで追加する。ML Kit エンティティは `sourceField = entity.contextLabel`（例: `"issuer_address"`、`"applicant_phone"`）が設定され、`contextLabel` が `null`（EntityAnnotator 失敗時）の場合は `sourceField = null` となる。MONEY エンティティ（`benefit_amount` / `fee` / `penalty` / `other_amount`、Tier 2）も `category="other"` として `piiSpans` に収録される。`applicant_name` / `other_name` / `issuer_name`（いずれも FieldExtractor の MF-02 出力）は `DetectedEntity` ではないため、`mergeEntities` が `PiiSpan(spanText=rawText, category="name", sourceField=<フィールド名>, maskRecommended=<真偽値>)` を明示的に構築する。`applicant_name` は Tier 1・`maskRecommended=true`、`other_name` は Tier 2・`maskRecommended=false`、`issuer_name` も Tier 2・`maskRecommended=false` として構築される（`issuer_name` はこの構築時に Tier 2 に昇格する）。

### 氏名バリアントマスク

`applicant_name` スパンに対しては、フルネームの完全一致に加えて部分名バリアントを自動生成してマスクする（`PiiMasker.nameVariants()`）。例えば "Carlos Rivera" からは "Carlos" / "Rivera" 単独や "Mr. Carlos" / "Mr. Rivera" のような敬称付きバリアントが生成され、「Dear Mr. Rivera,」のような敬称付き宛名での PII 漏洩を防止する。3 文字未満のトークン（例: "Li"）や、スペース区切りのないフルネーム（例: "山田太郎"）はバリアント生成の対象外となる。バリアントのマスクトークンはフルネームと同じもの（例: `[Applicant name]`）を使用し、`issuer_name` / `other_name` はバリアントマスクの対象外である。

### UI 表示と削除

マスク済みスパンは `[Applicant name]` / `[Issuer address]` 等のラベル付きトークンで表示され、`sourceField` が不明な場合は `[■■■]` にフォールバックする。問い合わせコンテキスト確認画面（S-03）の先頭には「⚠️ 個人情報はマスク済み / masked: {categories}」が表示される。マッチしなかったスパン（`unmatchedSpans`）がある場合は「マスクできなかった項目があります」を通知する。ユーザーは PII 編集パネルでスパンごとに `userOverride = false`（マスク除外）を設定でき、マスク除外したスパンの原文はユーザーが意図的に共有を選んだ情報として `maskedText` および `escalation.json` の `masked_source_text` に残る。

削除は `DocumentRepository.delete(docId)` で `{filesDir}/documents/{docId}/` ディレクトリ全体を削除するか、アプリのアンインストールで OS が `filesDir` 全体を削除する方法に限られる。MVP では書類管理 UI（一覧・削除）が未実装のため、ユーザーがアプリ操作でデータを削除する手段はアンインストールのみであり、Phase 2 で書類管理画面と合わせて削除機能が追加される予定である。

### ローカルストレージ

書類データは `{filesDir}/documents/doc_{yyyyMMdd_HHmmss}_{shortUuid}/` 配下に、`meta.json`（`ReviewResult` + `PiiSpan` 情報、PII を含む）・`source.txt`（抽出済み原文、PII を含む）・`escalation.json`（マスク済み `EscalationPackage`、ユーザーが MF-06 を実行した場合のみ）・`inquiry.json`（ユーザーが S-04 で明示的に含めた PII を含みうる `InquiryContext`）として保存され、外部ストレージは使用しない。`source.txt` と `meta.json` は端末外に送信されない。`DocumentRepository` は `save()` / `saveEscalation()` / `saveInquiry()` / `list()` / `load()` / `delete()` を提供し、`list()` が返す `DocumentMeta`（`docId` / `docName` / `importanceLevel` / `createdAt` / `hasEscalation` / `hasInquiry`）は Tier 3 相当の PII を含まない一覧情報である。MVP では暗号化を行わず、Phase 2 で Keystore の利用が検討されている。

`PiiSpan` は `id` / `spanText` / `category`（`"name"` / `"address"` / `"phone"` / `"account"` / `"dob"` / `"id_number"` / `"other"`）/ `sourceField` / `maskRecommended` / `userOverride` のフィールドを持つ。カテゴリラベルは `PiiSpan.categoryLabel(lang)` 拡張関数で多言語表示され、マスクトークンは `PiiSpan.maskToken()` 拡張関数が `sourceField` から `[Applicant name]` 等を生成する（`sourceField` が `null` の場合は `[■■■]`）。

### 実装（PiiMasker）

`PiiMasker.mask(text, spans)` は LLM を使わず、`spanText` から全空白を除去し各文字を `Regex.escape()` でエスケープしたうえで `[\s　]*` で連結した正規表現を生成してテキスト中を検索する（PDF テキスト抽出時のスペース差異・全角スペースも吸収する）。全空白除去後に空文字になる spanText（空白のみのスパン）は正規表現が生成できず `unmatchedSpans` に記録される。マッチした箇所はすべて `span.maskToken()` のトークンに置換され、マッチしなかったスパンは `appliedSpans` に含めず `unmatchedSpans` に記録して UI 上でユーザーに通知する。

マスク適用の優先順位は次のとおりである。

1. `userOverride == true` → 強制マスク
2. `userOverride == false` → 強制除外
3. `userOverride == null && maskRecommended == true` → マスク（デフォルト）
4. `userOverride == null && maskRecommended == false` → 除外

ユーザーが PII 編集パネルでスパンの `userOverride` を変更した場合は `PiiMasker.remask(text, spans)` で再マスクする。

### PII 編集パネルの行単位 UI（PiiEditRow）

PII 編集パネルの各行はチェックボックス＋スパンテキスト（マスク中は `[■■■]` 表示）＋カテゴリラベルで構成され、`unmatchedSpans` に該当するスパンはチェックボックスが無効化され赤色で「マスクできませんでした」に相当する注記が表示される。マスク中のスパンには primary カラーで「マスク済み」の注記が表示される。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/pii-tier-classification]]
- [[DefinedTerm/structured-field-extraction]]
