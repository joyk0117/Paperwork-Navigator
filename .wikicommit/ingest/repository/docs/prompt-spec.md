---
source:
  type: file
  path: 'docs/prompt-spec.md'
  hash: sha256:144c3266a5a9c7f51265c174bccc2f3bc91093f079f90460614d6466d639087a

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 10526
generated_pages:
  - .wikicommit/wiki/ja/DefinedTerm/text-extraction.md
  - .wikicommit/wiki/ja/DefinedTerm/multilingual-translation.md
  - .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
failed_pages: []
---

## サマリ

プロンプト仕様書は、Paperwork Navigator の各 LLM 呼び出し（MF-01c OCR補正・MF-02 フィールド抽出・EntityAnnotator・MF-03 翻訳・MF-06 問い合わせ/エスカレーション・MF-07 チャット）の System Prompt・User Message・変数・few-shot例・評価基準を網羅した仕様書である。MF-02 が16フィールドから9フィールドへ削減された経緯、MF-06 が目的候補提案・不足情報質問リスト・文書生成の3段階から現在の目的候補提案＋コンテキスト組み立て＋エスカレーションパッケージ生成に変遷した経緯など、設計判断の背景も記載されている。

