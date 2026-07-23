---
source:
  type: file
  path: 'docs/extraction-architecture-spec.md'
  hash: sha256:69627bb2df6e428d71a0b8a314b7853995a51c3099f9a3961a8faabff9797d8d

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 7223
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/structured-field-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-tier-classification.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/quick-action.md
failed_pages: []
---

## サマリ

抽出アーキテクチャ仕様書は、Paperwork Navigator の構造化フィールド抽出（MF-02）における ML Kit + Gemma 4 の二段階抽出設計を詳述したものである。context_label を中心概念とした意味付けの仕組み、全32項目の抽出フィールドカタログ、PII Tier の静的判定ロジック（computePiiTier()）、DetectedEntity/EntityMetadata のデータモデル、レビュー画面（S-02）のクイックアクション仕様、mergeEntities による piiSpans 構築ルールが記載されている。

