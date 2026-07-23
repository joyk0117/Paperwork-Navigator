---
source:
  type: file
  path: 'docs/test-spec_ja.md'
  hash: sha256:c66a33e49f6591d28e37f1344a38c21ccc3fc4b3ae1e3c59fe9840116dea63bd

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 13470
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
failed_pages: []
---

## サマリ

テスト仕様書（日本語版、バージョン0.2.0）は Paperwork Navigator のテスト方針・テスト環境・各機能のテストケース（TC-*、§1〜§17）を定義したものである。§1〜§14 の多くのテストケースは `docType`・`pii_candidates`・MF-02 の JSON 出力・`EscalationPackageGenerator` 単独の MF-06（S-04 ウィザードなし）など、他の仕様書で確認された現行アーキテクチャと矛盾する古い用語を使用しており、既存ウィキ記事との整合性を優先しこれらは反映しなかった。一方、§15 EntityExtractor/EntityAnnotator テストと §17 Intent 受け取りテストは現行の用語（piiSpans・computePiiTier 等）と整合しており、そこから得られた安全な情報（onNewIntent の挙動、S01CropContent コンポーネント名、テスト方針・ツール一式）をテキスト抽出および Paperwork Navigator の各ページに反映した。

