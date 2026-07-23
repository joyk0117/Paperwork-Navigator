---
source:
  type: file
  path: 'docs/extraction-architecture-spec_ja.md'
  hash: sha256:0f0a3a2cf7dab13cb02ca0c0621acc8ced7666550be96ab3243f6a0e4ad61bb6

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 3792
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/structured-field-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-tier-classification.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/quick-action.md
failed_pages: []
---

## サマリ

抽出アーキテクチャ仕様書（日本語版）は、Paperwork Navigator の構造化フィールド抽出（MF-02）における ML Kit + Gemma 4 の二段階抽出設計を詳述したものである。context_label を中心概念とした意味付けの仕組み、全32項目の抽出フィールドカタログ、PII Tier の静的判定ロジック（computePiiTier()）、DetectedEntity/EntityMetadata のデータモデル、レビュー画面（S-02）のクイックアクション仕様、mergeEntities による piiSpans 構築ルールが記載されている。docs/extraction-architecture-spec.md（英語版）と内容は同一。

