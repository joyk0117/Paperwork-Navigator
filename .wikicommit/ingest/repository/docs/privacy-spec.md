---
source:
  type: file
  path: 'docs/privacy-spec.md'
  hash: sha256:aed644ff5174a945730801d0d7037ebbe8e502975d4d4219730f261e5cf3ae07

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 5009
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/pii-tier-classification.md
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
failed_pages: []
---

## サマリ

プライバシー仕様書（英語版）は、Paperwork Navigator のPII外部送信ポリシーとライフサイクル管理を定義したものである。設計原則、データ分類（Tier 1〜3、詳細は抽出アーキテクチャ仕様書に委譲）、PIIライフサイクル図とフェーズ別ポリシー、各LLM呼び出しのデータ送信ポリシー、UI/UXでのプライバシー配慮、MVPの制限（ストレージ暗号化・バックアップ無効化・自動削除・共有前確認・クラウドLLM送信）と今後の計画が記載されている。日本語版（privacy-spec_ja.md）と同一バージョン（0.2.3）で内容もほぼ一致する。

