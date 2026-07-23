---
title: "Paperwork Navigator"
type: "schema:SoftwareApplication"
lang: ja
description: "母語以外の言語で書かれた行政・医療・生活書類を端末内だけで読み取り・解析・翻訳し、PIIをマスクした相談コンテキストを生成するプライバシーファーストなAndroidアプリ"
applicationCategory: "Productivity"
operatingSystem: "Android 15+"
softwareVersion: ""
downloadUrl: "https://github.com/joyk0117/Paperwork-Navigator/releases/latest/download/app-debug.apk"
featureList:
  - "テキスト抽出（MF-01）"
  - "OCR補正（MF-01c）"
  - "構造化フィールド抽出（MF-02）"
  - "多言語翻訳（MF-03）"
  - "レビュー画面（MF-04）"
  - "PIIマスク（MF-05）"
  - "問い合わせコンテキスト生成（MF-06）"
  - "書類理解チャット（MF-07）"
tags: [オンデバイスAI, プライバシーファースト設計]
sources:
  - type: file
    path: 'README_ja.md'
    hash: sha256:3a8db998c216c576c641a151a9a2bcad45b3ce95bec627233dc53dbd50f41719
  - type: file
    path: 'docs/privacy-spec_ja.md'
    hash: sha256:53dd18ffcce0a1e42958f5d057dcf4cca7021a4b13aab110f8450765cfda1caf
  - type: file
    path: 'docs/implementation-spec_ja.md'
    hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312
  - type: file
    path: 'docs/Kaggle-Writeup.md'
    hash: sha256:3d4c36e871275cde2aa191721b8cd760699c8a3e7957d2ec372cadedc956abf9
  - type: file
    path: 'docs/implementation-spec.md'
    hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523
  - type: file
    path: 'docs/privacy-spec.md'
    hash: sha256:aed644ff5174a945730801d0d7037ebbe8e502975d4d4219730f261e5cf3ae07
  - type: file
    path: 'docs/test-spec_ja.md'
    hash: sha256:c66a33e49f6591d28e37f1344a38c21ccc3fc4b3ae1e3c59fe9840116dea63bd
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

Paperwork Navigator は、海外移住者・留学生・駐在員・難民など、母語以外の言語で書かれた行政・医療・生活書類を受け取ったすべての人のためのプライバシーファーストな書類ナビゲーターである。Google AI Edge Gallery をベースにフォークし、Document Review タスクを追加実装している。受け取った書類を端末上だけで読み取り・解析・翻訳し、PII（個人を特定できる情報）をマスクしたうえで専門家や外部 AI への相談コンテキストを生成する。個人情報は一切端末の外に出ない設計となっている。

## Overview

対象とするのは、期限・必要書類・罰則が外国語で書かれていて何をすべきか分からない、オンライン翻訳サービスに書類を貼り付けると氏名・住所・ID 番号などの機密情報が外部サーバーに送信されてしまう、翻訳できても重要度・締め切り・必要アクションの整理が難しい、専門家や AI に相談するにも個人情報を含む書類をそのまま渡すのはリスクがある、といった課題を抱える人々である。Paperwork Navigator はすべての推論を端末内で完結させることで、この「相談したいが個人情報は渡したくない」というトレードオフを解消する。

処理パイプラインは、書類の取り込み（PDF・カメラ・ギャラリー・他アプリ共有）→ テキスト抽出（PdfRenderer / ML Kit OCR）→ ML Kit エンティティ抽出と Gemma 4 フィールド抽出 → Gemma 4 によるエンティティ意味付け → PII マスク（非 LLM・端末内）→ レビュー画面、という流れで構成される。レビュー画面からはさらに書類チャット・多言語翻訳・問い合わせコンテキスト生成につながる。初回のモデルダウンロード以降は、AI 推論処理はすべて端末内で完結し、外部送信が発生するのはモデルの初回ダウンロード時とユーザーが共有を明示的に実行した場合のみである。

プライバシー設計は [[DefinedTerm/pii-tier-classification]] と呼ぶ 3 段階の Tier に分かれており、Tier 1（氏名・住所・生年月日・マイナンバー・口座番号など）は端末外に一切出さず、Tier 2（発行者連絡先・期限・金額など）はユーザー同意のもとマスク済み形式でのみ外部出力可能、Tier 3（書類タイトル・重要度フラグ・翻訳テキストなど）は PII を含まない。すべての Gemma 4 推論は LiteRT-LM により端末内で完結し、外部ストレージの読み書き権限は要求しない。

MVP では、ストレージ暗号化（OS サンドボックスのみで Keystore + AES-256-GCM は未実装）・自動削除（TTL）・共有前確認ダイアログ・クラウド LLM へのデータ送信はいずれも未対応であり、これらは Phase 2 の計画に位置づけられている。バックアップ無効化（`android:allowBackup="false"`）についても AndroidManifest への設定が求められている。書類の削除は `DocumentRepository.delete(docId)` によるドキュメント単位の削除、またはアプリのアンインストールによる `filesDir` 全体の削除に限られ、MVP では書類管理 UI（一覧・削除画面）が未実装である。MVP の対象外（P2以降）としては、他にポリシー情報を補うための Web 検索・クラウド LLM へのエスカレーション・複数書類を横断する Agent RAG・法律相談や制度解釈・複数ユーザーアカウントが挙げられている。

Gemma 4 の採用理由は主に4点である。第一に、翻訳だけでなく要約・締め切り抽出・アクション項目・警告・OCR補正・書類理解チャットの応答まで、書類内容を構造化された実用的な情報に変換する高品質なオンデバイス推論を可能にする点。第二に、OCR やエンティティ抽出などの決定的なツールを置き換えるのではなく、それらと組み合わせて文脈・意味付けを担う、マルチモーダル・ツール活用型のワークフローに適している点。第三に、機密書類を扱う製品として重要な、一貫した構造化出力と予測可能な挙動を実現する制御性の高さ。第四に、プライバシー・エッジ・低接続環境という制約下でも役立つ AI という本プロジェクトの中心的なストーリーを支える点である。

端末内推論へのこだわりは Paperwork Navigator のユーザー価値そのものであり、LiteRT-LM 上で Gemma 4 を実行することでサーバーラウンドトリップやモデル初回ダウンロード後の書類送信を不要にしている。これは技術的には不安定なネットワーク環境でもアプリを利用可能にし、社会的には行政書類・給付金・身元情報・家族情報など機微なワークフローに対する製品としての信頼性を高める。モバイルであること自体は表現上の選択ではなく、プライバシーとアクセシビリティの利点が現実のものになる場所だからである。

現在のプロトタイプは日本語の行政書類（外国人在住者にとって特にニーズの高いユースケース）を中心に、製品の全体像を Android 上で実証している。今後は、より広範な多言語評価、対応書類種別の拡大、ベンチマーク検証の強化、実際に外国語で行政書類を扱うユーザーによるユーザビリティテストが計画されている。また、医療書類・学校関連書類・移民関連書類・社会保障申請など、プライバシー・理解しやすさ・アクセスが交差する他分野への応用も検討している。

## Features

| ID | 機能 | 詳細 |
|----|------|------|
| MF-01 | テキスト抽出 | テキスト PDF・テキストファイル・カメラ撮影・ギャラリー画像（12 言語 OCR 対応）。他アプリからの `ACTION_SEND` / `ACTION_VIEW` でも直接起動可。詳細は [[DefinedTerm/text-extraction]] を参照 |
| MF-01c | OCR 補正 | カメラ/画像入力後、Gemma 4 マルチモーダル推論で元画像と OCR テキストを照合し誤認識を修正（任意実行） |
| MF-02 | 構造化フィールド抽出 | ML Kit Entity Extraction で日時・住所・電話・金額などを抽出し、Gemma 4（EntityAnnotator）が文脈ラベルを付与、Gemma 4 フィールド抽出で期限・アクション・警告を取得。詳細は [[DefinedTerm/structured-field-extraction]] を参照 |
| MF-03 | 多言語翻訳 | 15 言語対応。原文・翻訳を2列並列表示。詳細は [[DefinedTerm/multilingual-translation]] を参照 |
| MF-04 | レビュー画面 | 期限・必要書類・警告をカテゴリ別にカラーバッジで表示。カレンダー追加（`CalendarContract`）・地図表示（`geo:` URI）のクイックアクションを提供。詳細は [[DefinedTerm/review-screen]] を参照 |
| MF-05 | PIIマスク | 詳細は [[DefinedTerm/pii-masking]] を参照 |
| MF-06 | 問い合わせコンテキスト生成 | 詳細は [[DefinedTerm/inquiry-context-generation]] を参照 |
| MF-07 | 書類理解チャット | ReviewResult をコンテキストとして Gemma 4 に渡し、書類に関する Q&A をオンデバイスで実行。詳細は [[DefinedTerm/document-chat]] を参照 |

技術スタックは、LLM ランタイムに LiteRT-LM、モデルに Gemma 4 E2B（2.58 GB、推奨）/ E4B（3.65 GB、高精度オプション）、UI に Jetpack Compose、DI に Hilt、PDF テキスト抽出に Android 標準 PdfRenderer（API 35）、OCR（画像・カメラ）に ML Kit Text Recognition、OCR 補正に Gemma 4 マルチモーダル推論（MF-01c）、エンティティ抽出に ML Kit Entity Extraction + Gemma 4、エンティティ注釈に Gemma 4、言語識別に ML Kit Language Identification、カメラスキャンに ML Kit Document Scanner、カメラプレビューに CameraX、状態管理に ViewModel + StateFlow を採用している。検証環境は Min SDK 35（Android 15）、Google Pixel 9（RAM 12 GB）である。入力テキストは 16,000 文字を上限にトリムされ、評価・デモには日本語行政書類（児童手当現況届、江戸川区 令和7年度記入例）を主なサンプルとして使用するが、あらゆる言語・種別の書類を対象とする。ライセンスは Apache License 2.0。

Pixel 9 での推論時間目安は、MF-01a テキスト抽出（PDF）が1秒未満、MF-01b OCR（ML Kit）が5秒未満、MF-02 フィールド抽出（Gemma 4）が60〜150秒、MF-03 翻訳（Gemma 4）が15〜60秒、MF-06a 問い合わせ目的候補生成（Gemma 4）が5〜15秒、MF-06 コンテキストテキスト生成（LLM不要）が1秒未満、MF-07 チャット応答（Gemma 4、1ターン、ストリーミング）が3〜10秒であり、これらは実機での計測・調整を前提とした見込み値である。

テストにはユニット・結合・UI・E2Eの4段階を設け、JUnit 4/5・Kotlin Coroutines Test・Robolectric（結合テスト）・Compose UI Testing / Espresso（UIテスト）・MockK または Mockito-Kotlin（モック）を使用する。MF-02/03/06/07 のような LLM 依存処理は `LlmModelHelper` をモック化してユニットテストし、実機 E2E テストでのみ実際の Gemma 4 モデルを使用する方針である。

使い方の詳細は [[HowTo/paperwork-navigator-usage]] を参照。
