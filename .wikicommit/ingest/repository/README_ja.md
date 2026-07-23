---
source:
  type: file
  path: 'README_ja.md'
  hash: sha256:3a8db998c216c576c641a151a9a2bcad45b3ce95bec627233dc53dbd50f41719

schema:
status: generated
last_generated_at: "2026-07-23"
extracted_tokens: 1515
generated_pages:
  - .wikicommit/wiki/ja/SoftwareApplication/paperwork-navigator.md
  - .wikicommit/wiki/ja/HowTo/paperwork-navigator-usage.md
  - .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
  - .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
failed_pages: []
---

## サマリ

Paperwork Navigator は、母語以外の言語で書かれた行政・医療・生活書類を端末内で読み取り・解析・翻訳し、PII をマスクした上で外部 AI や専門家への相談コンテキストを生成する、プライバシーファーストな Android アプリである。Gemma 4 と ML Kit を用いたオンデバイス推論により、書類の取り込みから期限管理・多言語翻訳・書類チャットまで一貫して端末外に個人情報を送信しない設計となっている。README では機能一覧（MF-01〜MF-07）、処理パイプライン、プライバシー階層（Tier 1〜3）、技術スタックが詳述されている。

「Paperwork Navigator」: ライセンス（Apache License 2.0）や検証端末（Google Pixel 9）の情報は SoftwareApplication.md の recommended フィールドに受け皿がないため本文にのみ記載。

