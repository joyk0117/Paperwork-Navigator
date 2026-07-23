---
source:
  type: file
  path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
  hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 33942
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/review-screen.md
  - .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
  - .wikicommit/wiki/ja/DefinedTerm/quick-action.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/document-chat.md
  - .wikicommit/wiki/ja/DefinedTerm/multilingual-translation.md
failed_pages: []
---

## サマリ

DocumentReviewScreen.kt は Paperwork Navigator の書類レビュー機能を構成する Jetpack Compose 画面群（S-01 入力・S-02 レビュー・S-03 プレビュー・S-04 問い合わせウィザード）の実装であり、書類の取り込み（PDF・カメラスキャン・ギャラリー画像・テキスト貼り付け・他アプリからの共有）から遠近補正・OCR言語選択、レビュー画面でのバッジ表示・クイックアクション・チャット・翻訳、問い合わせコンテキスト生成とエスカレーションパッケージ出力までの UI ロジックを実装している。既存の7つの DefinedTerm ページ（レビュー画面・テキスト抽出・問い合わせコンテキスト生成・クイックアクション・PIIマスク・書類理解チャット・多言語翻訳）に、各機能の具体的な UI 実装詳細を追記した。

