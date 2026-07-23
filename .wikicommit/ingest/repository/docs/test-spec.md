---
source:
  type: file
  path: 'docs/test-spec.md'
  hash: sha256:984a6406ad2c3428b10b370b2b7038c50e217dd98add9f685efe52b69070b569

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 9744
generated_pages: []
failed_pages: []
---

## サマリ

テスト仕様書（英語版、バージョン0.2.0）は Paperwork Navigator のテスト方針・テスト環境・各機能のテストケース（TC-*）を定義したものである。§1〜§14 の多くのテストケースは `docType`（現行は `docName`）・`pii_candidates`（現行は `piiSpans`）・MF-02 の JSON 出力（現行は行形式）・`EscalationPackageGenerator` 単独の MF-06（現行は S-04 InquiryContext ウィザードも存在）・`GeneratingEscalation`/`OutputPreview` 状態など、他の仕様書（実装仕様書・抽出アーキテクチャ仕様書・プロンプト仕様書）で確認された現行アーキテクチャと矛盾する古い用語を使用しており、既存ウィキ記事との整合性を優先しこれらは反映しなかった。日本語版（test-spec_ja.md）に含まれる §15 EntityExtractor/EntityAnnotator テストと §17 Intent 受け取りテストは現行の用語（piiSpans・computePiiTier 等）と整合しており、そこから得られた安全な情報（onNewIntent の挙動、S01CropContent コンポーネント名、テスト方針・ツール）のみを既存ページに反映した。本ファイル（英語版）自体は §14 で終わっており §15〜17 の内容を含まない（目次には記載があるが本文が欠落している）。

