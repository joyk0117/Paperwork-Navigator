---
source:
  type: file
  path: 'docs/privacy-spec_ja.md'
  hash: sha256:53dd18ffcce0a1e42958f5d057dcf4cca7021a4b13aab110f8450765cfda1caf

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 2977
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-tier-classification.md
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
failed_pages: []
---

## サマリ

本仕様書は、Paperwork Navigator が扱う PII を Tier 1〜3 に分類し、取得から削除までのライフサイクルを通じてどのように保護するかを定めている。オンデバイス推論・最小権限・ユーザー同意といった設計原則のもと、各 LLM 呼び出しへの入力データポリシー、UI 上でのマスク表示・透明性、氏名バリアントマスクのルールを詳細に規定する。MVP ではストレージ暗号化や自動削除（TTL）、共有前確認ダイアログは未実装であり、Phase 2 での対応が計画されている。

