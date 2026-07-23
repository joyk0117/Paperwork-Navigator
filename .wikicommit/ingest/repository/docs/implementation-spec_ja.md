---
source:
  type: file
  path: 'docs/implementation-spec_ja.md'
  hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 19313
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/structured-field-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/multilingual-translation.md
  - .wikicommit/wiki/ja/DefinedTerm/document-chat.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
  - .wikicommit/wiki/ja/HowTo/paperwork-navigator-usage.md
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
failed_pages: []
---

## サマリ

本仕様書は、Paperwork Navigator の MVP機能範囲（MF-01〜MF-07）における画面設計・処理フロー・データモデル・Gemma 4 プロンプト設計方針・各処理ステップ（Skill）のインターフェースを定義する実装仕様書である。テキスト抽出・OCR補正・構造化フィールド抽出・翻訳・PIIマスク・問い合わせコンテキスト生成・書類理解チャットの各処理をどのクラス・データ構造で実装するかを規定し、対象端末（Google Pixel 9、Android 15）や文字数上限などの非機能要件も含む。

