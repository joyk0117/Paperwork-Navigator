---
source:
  type: file
  path: 'docs/implementation-spec.md'
  hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 17361
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/review-screen.md
  - .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/structured-field-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/multilingual-translation.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
  - .wikicommit/wiki/ja/DefinedTerm/document-chat.md
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
failed_pages: []
---

## サマリ

実装仕様書（英語版）は、Paperwork Navigator の画面設計（S-01〜S-04・S-M）、処理フロー、データモデル（ReviewResult / Translation / PiiSpan / InquiryContext / ChatMessage 等の JSON Schema）、Gemma 4 プロンプト設計方針、処理ステップ（Skill）インターフェース、ローカルストレージ構成、非機能要件（推論時間見込み・文字数制限・MVP対象外機能）を網羅的に記述したものである。日本語版（implementation-spec_ja.md、バージョン0.4.0）より版が古い（バージョン0.3.0）が、本文中の文字数上限（16,000文字）やMF-07タイムアウト（60秒）等の数値は日本語版と一致しており、既存ウィキ記事との矛盾は確認されなかった。レビュー画面（MF-04）の新規ページを含む8ページを生成・更新した。

