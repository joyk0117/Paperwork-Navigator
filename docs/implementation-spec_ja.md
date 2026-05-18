# Paperwork Navigator 実装仕様書

> バージョン: 0.3.0
> 作成日: 2026-05-07
> 最終更新: 2026-05-18（PiiMasker スパンマッチング: 各文字を Regex.escape() でエスケープ・空白のみ spanText を unmatchedSpans に記録 — §7.5）
> 対象: MVP（P1 機能 MF-01〜MF-07）

---

## 目次

1. [概要](#1-概要)
2. [アーキテクチャ概要](#2-アーキテクチャ概要)
3. [画面設計・遷移](#3-画面設計遷移)
4. [処理フロー](#4-処理フロー)
5. [データモデル（JSON スキーマ）](#5-データモデルjson-スキーマ)
6. [Gemma 4 プロンプト設計](#6-gemma-4-プロンプト設計)
7. [処理ステップ（Skill）インターフェース](#7-処理ステップskillインターフェース)
8. [ローカル保存](#8-ローカル保存)
9. [非機能要件](#9-非機能要件)

---

## 1. 概要

### 1.1 プロジェクト

**Paperwork Navigator** は、受け取った書類（言語・種別を問わない）を端末上で読み取り・解析・翻訳し、PII をマスクしてから必要に応じて高性能 LLM または専門家へエスカレーションする privacy-first document navigator。

### 1.2 評価・デモ用サンプル書類

MVP の評価・デモには日本語行政書類を主なサンプルとして使用するが、アプリはあらゆる言語・種別の書類を対象とする。

- **サンプル**: 児童手当現況届（江戸川区 令和7年度記入例）
- **ファイル**: `edogawa_R7_genkyo_kinyuurei.pdf`

### 1.3 MVP の機能範囲（P1）

| ID | 機能 |
|----|------|
| MF-01 | テキスト抽出（テキスト PDF / テキストファイル / カメラ撮影 / ギャラリー画像） |
| MF-02 | 構造化 JSON 抽出 |
| MF-03 | 多言語翻訳 |
| MF-04 | レビュー画面 UI |
| MF-05 | 端末上 PII マスク |
| MF-06 | 問い合わせコンテキスト生成（ウィザード形式） |
| MF-07 | 書類理解チャット（オンデバイス、レビュー画面に統合） |

### 1.4 関連仕様書

本仕様書に加えて、以下の2つの仕様書を参照すること。

#### [プロンプト仕様書](プロンプト仕様書_ja.md)

MF-02・MF-03・MF-06・MF-07 の各 LLM 呼び出しで使用する System Prompt・User Message の全文と、実装時に必要な変数リファレンスをまとめた仕様書。本仕様書の §6 はプロンプト設計の方針と概要を記載しており、実際のプロンプト文字列・変数定義・few-shot example・評価基準の詳細はプロンプト仕様書を参照する。

| 内容 | 場所 |
|------|------|
| 設計方針（英語 System Prompt、structured output、コンテキスト長） | §1 |
| MF-02 フィールド抽出プロンプト・リトライ方針 | §2 |
| MF-03 翻訳プロンプト・言語コード対応表 | §3 |
| MF-06 エスカレーション生成プロンプト・変数一覧 | §4 |
| MF-07 チャット用 System Prompt・初回メッセージ | §5 |
| Few-shot example（児童手当現況届 サンプル） | §6 |
| 全変数と生成元クラスの対応表 | §7 |
| 各 MF の評価基準・E2B vs E4B 切り替え基準 | §8 |

#### [プライバシー仕様書](プライバシー仕様書_ja.md)

Paperwork Navigator が扱う PII（個人識別情報）をどのように分類し、取得から削除までのライフサイクルを通じてどう保護するかを定めた仕様書。「外部に出力してよいデータ・してはならないデータ」の判断基準と、各 LLM 呼び出しでどのデータを渡すかを規定する。実装時は本仕様書の §8（ローカル保存）と合わせて参照する。

| 内容 | 場所 |
|------|------|
| 設計原則（オンデバイス推論・最小権限・ユーザー同意） | §1 |
| データ分類（Tier 1〜3） | §2 |
| PII ライフサイクル（入力→推論→マスク→保存→出力） | §3 |
| LLM 呼び出しごとの入力データポリシー | §4 |
| UI・UX のプライバシー配慮（マスク表示・透明性） | §5 |
| MVP の制限と今後の計画 | §6 |

---

## 2. アーキテクチャ概要

### 2.1 技術スタック

| 要素 | 採用技術 |
|------|---------|
| ベース | AI Edge Gallery fork |
| LLM ランタイム | LiteRT-LM 0.11.0 |
| モデル（推奨） | Gemma 4 E2B |
| モデル（高精度オプション） | Gemma 4 E4B |
| UI | Jetpack Compose |
| PDF テキスト抽出 | Android 標準 PdfRenderer |
| OCR（画像・カメラ） | ML Kit Text Recognition Japanese 16.0.1 |
| 言語識別 | ML Kit Language Identification 17.0.6 |
| カメラスキャン | ML Kit Document Scanner 16.0.0-beta1 |
| カメラプレビュー | CameraX 1.4.2 |
| DI | Hilt |
| 状態管理 | ViewModel + StateFlow |

### 2.2 既存フレームワーク

AI Edge Gallery の次のインターフェースを活用する。

- **`CustomTask`** インターフェース: タスク登録・モデル初期化・クリーンアップ（`io.github.joyk0117.privatepaperwork.customtasks.CustomTask`）
- **`LlmModelHelper`** インターフェース: LiteRT-LM 推論の抽象化
- **`LlmChatModelHelper`**: `LlmModelHelper` の LiteRT-LM 実装（既存）
- **ModelManagerViewModel**: モデルのダウンロード管理（既存）

### 2.3 追加するコンポーネント

```
customtasks/
└── documentreview/                   ← 新規追加
    ├── DocumentReviewTask.kt         ← CustomTask 実装（タスク登録）
    ├── DocumentReviewViewModel.kt    ← 状態管理・推論オーケストレーション
    ├── DocumentReviewScreen.kt       ← メイン Compose 画面
    ├── model/
    │   └── DocumentReview.kt        ← ReviewResult, PiiSpan, DocumentInput など
    ├── processing/                  ← 処理ステップ（企画メモの "Skill" に対応）
    │   ├── TextExtractor.kt         ← MF-01a: PDF/テキスト抽出
    │   ├── ImageTextExtractor.kt    ← MF-01b: ML Kit OCR（カメラ・ギャラリー画像）
    │   ├── LanguageIdentifier.kt    ← MF-01 直後: ML Kit 言語識別
    │   ├── EntityExtractor.kt       ← MF-02 前処理: ML Kit Entity Extraction（全 11 種）
    │   ├── FieldExtractor.kt        ← MF-02: 構造化フィールド抽出（Gemma 4、9 フィールド）
    │   ├── EntityAnnotator.kt       ← MF-02 後処理: Gemma 4 による context_label 付与
    │   ├── Translator.kt            ← MF-03: 翻訳
    │   ├── PiiMasker.kt             ← MF-05: PII マスク（非 LLM）
    │   ├── InquiryContextBuilder.kt      ← MF-06: 問い合わせコンテキスト生成（ウィザード）
    │   └── DocumentChatSession.kt   ← MF-07: 書類理解チャット
    └── util/
        └── DocumentIntentBuilder.kt ← Android Intent 生成ユーティリティ
```

> **なぜ AgentChat ではなく専用画面か**
> AgentChat の JS Skill はチャット UI 前提で、レビュー画面（色分け・マスク編集・日英並列）を作るには
> Compose 画面を独自に持つ `CustomTask` として実装するほうが適切。
> LiteRT-LM の推論自体は既存の `LlmChatModelHelper` を `LlmModelHelper` 経由で呼ぶため、
> 推論コアは共通化される。

---

## 3. 画面設計・遷移

### 3.1 画面一覧

| 画面 ID | 画面名 | 概要 |
|---------|--------|------|
| S-01 | 入力画面（起動画面） | ファイル選択 / テキスト貼り付け / 他アプリからの Intent 受け取り。解析中はボタンをプログレス表示に切り替え |
| S-02 | レビュー画面 | メイン画面。日英並列・色分け・マスク編集・書類理解チャット |
| S-03 | 問い合わせコンテキスト確認画面 | ウィザードで収集した情報をコンテキストテキストとして確認・コピー・共有。外部 AI アシスタントへの貼り付けを想定 |
| S-04 | 問い合わせ文書作成画面 | 問い合わせ目的・あて先入力、不足情報 Q&A（Step 1/2）、コンテキストテキスト生成 |
| S-M | モデルマネージャー（既存） | Gemma 4 のダウンロード管理。S-01 からアクセス |

### 3.2 遷移図

```
起動（直接起動）
  │
他アプリからの Intent（ACTION_VIEW PDF / ACTION_SEND PDF・テキスト・画像）
  │
  ▼
S-01 入力（起動画面）
  │                          ┌─────────────────────────┐
  ├─[モデル未ダウンロード]──► S-M モデルマネージャー     │
  │                          └─────────────────────────┘
  │
  ├─[解析開始]── ボタンがプログレス表示に切り替わる
  │              （解析中は入力・言語選択を無効化）
  │
  ├─[解析成功]──► S-02 レビュー
  │                   │
  │         ┌─────────────────────┴──────────┐
  │  [問い合わせ文書を作成]              [←戻る]
  │         │                               │
  │         ▼                          S-01 に戻る
  │      S-04 問い合わせ文書作成（基本情報入力）
  │         │
  │      [コンテキストを確認する]
  │         │                   ┌──────────────────────┐
  │         ▼             [← 戻る]  → S-04 に戻る      │
  │      S-03 問い合わせコンテキスト確認                 │
  │         │                                           │
  │      ┌──┴──────────────┐                           │
  │  [コピー]  [共有]  [← 戻る]                         │
  │      │       │       │                             │
  │  クリップ 共有シート S-04 に戻る                     │
  │  ボードへ  を開く                                   │
  │                                                     │
  └─[解析失敗]── S-01 上でエラーメッセージ表示、再試行可能
```

> **戻るナビゲーション時の状態管理**: S-02 → S-01 へ戻る際は `ReviewResult`・`MaskResult`・チャット履歴をすべてクリアして `Idle` 状態に戻す。S-04 → S-02 へ戻る際は `InquiryWizard` から `Review` 状態に復帰し、`ReviewResult`・`MaskResult`・チャット履歴は保持する。S-03 → S-04 へ戻る際は `InquiryPreview` から `InquiryWizard` に復帰し、データは保持する（コンテキストテキストの再生成は不要・即時）。

### 3.3 各画面の詳細

#### S-01 入力画面（起動画面）

待機中と解析中で同じ画面を使い、ボタン部分だけ切り替える。

**待機中（テキスト未入力 / 200 文字未満）:**
```
┌─────────────────────────────────┐
│  Paperwork Navigator            │
│  書類を選択してください          │
├─────────────────────────────────┤
│                                 │
│  ┌───────────────────────────┐  │
│  │  📄 PDFファイルを開く      │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  📷 カメラで撮影する       │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  🖼 ギャラリーから選ぶ     │  │
│  └───────────────────────────┘  │
│                                 │
│  ─────────── または ──────────  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  テキストをここに貼り付け  │  │
│  │                           │  │
│  │                           │  │
│  └───────────────────────────┘  │
│  ← 200 文字未満は展開ボタンなし  │
│                                 │
│        [解析を開始する]          │
└─────────────────────────────────┘
```

**待機中（テキスト 200 文字以上・折りたたみ状態）:**
```
┌─────────────────────────────────┐
│  ...（ファイル選択ボタン群）     │
│  ─────────── または ──────────  │
│  ┌───────────────────────────┐  │
│  │  テキストをここに貼り付け  │  │
│  │  令和7年度 児童手当現況届  │  │  ← 先頭 3〜4 行を表示
│  │  江戸川区長 様...          │  │
│  └───────────────────────────┘  │
│               ▼ 全文を表示（N 文字）│  ← 右寄せボタン
│                                 │
│        [解析を開始する]          │
└─────────────────────────────────┘
```

**待機中（テキスト 200 文字以上・展開状態）:**
```
┌─────────────────────────────────┐
│  ...（ファイル選択ボタン群）     │
│  ─────────── または ──────────  │
│  ┌───────────────────────────┐  │
│  │  テキストをここに貼り付け  │  │
│  │  令和7年度 児童手当現況届  │  │  ← スクロール可能な
│  │  江戸川区長 様             │  │    大きいエリア
│  │  受給者氏名: ○○ 太郎     │  │    （最大 350dp）
│  │  ...全文表示...            │  │
│  └───────────────────────────┘  │
│                      ▲ 折りたたむ│  ← 右寄せボタン
│                                 │
│        [解析を開始する]          │
└─────────────────────────────────┘
```

**テキストエリアの折りたたみ/展開ルール:**

| 条件 | テキストエリア高さ | 展開ボタン |
|------|-----------------|-----------|
| テキスト < 200 文字 | 制限なし（全文表示） | 非表示 |
| テキスト ≥ 200 文字・折りたたみ | 100dp 固定（約 3〜4 行） | `▼ 全文を表示（N 文字）` |
| テキスト ≥ 200 文字・展開 | 200〜350dp（スクロール可） | `▲ 折りたたむ` |

Processing 状態ではテキストエリアは折りたたまれた状態になる（Processing 状態は別コンポーザブルで表示されるため、Idle に戻ると折りたたみ状態にリセットされる）。

**Intent 受け取り時（他アプリからの共有・「このアプリで開く」）:**

| Intent | MIME type | 動作 |
|--------|-----------|------|
| `ACTION_SEND` / `ACTION_VIEW` | `application/pdf` | テキスト抽出を開始し、完了後にテキストエリアへ反映（ファイル名は抽出中もボタン領域に表示） |
| `ACTION_SEND` | `text/plain` | テキストエリアに内容をセット |
| `ACTION_SEND` | `image/*` | 台形補正画面に遷移し、確定後に OCR でテキストエリアへ反映 |

自動解析は行わない。受け取り後はユーザーが「解析を開始する」ボタンを押す通常フローに従う。
アプリがすでに起動中の場合（`onNewIntent`）、現在の解析結果をリセットして S-01 に戻り、新しい入力をセットする。

**カメラ・ギャラリー入力時の言語選択 UI:**

カメラ・ギャラリー入力では OCR 実行前に書類言語が不明なため、画像取得完了後に OCR 言語選択ダイアログを表示する。選択した言語コードを `ImageTextExtractor.extract()` / `ImageTextExtractor.extractFromBitmap()` に渡し、適切な ML Kit OCR モデルを選択する。

- カメラ: スキャン完了後に言語選択ダイアログを表示
- ギャラリー（1 枚）: パース補正完了後に言語選択ダイアログを表示
- ギャラリー（2 枚以上）: 全枚のパース補正完了後に言語選択ダイアログを 1 回表示

対応 12 言語（`SupportedLanguage.supportsOcr = true`）:

| スクリプト | 言語 |
|-----------|------|
| Japanese | 日本語 (`ja`) |
| Chinese | 中文 (`zh`) |
| Korean | 한국어 (`ko`) |
| Latin | English (`en`) / Español (`es`) / Français (`fr`) / Deutsch (`de`) / Italiano (`it`) / Português (`pt`) / Polski (`pl`) / Nederlands (`nl`) / Türkçe (`tr`) |

`ru` / `ar` / `th` は ML Kit に対応する OCR モデルがないため選択肢から除外する。PDF・テキストファイル入力パスへの変更はなし（言語判定は `LanguageIdentifier` が担当するため言語選択 UI 不要）。

**解析中（ボタン差し替え、入力無効化）:**
```
┌─────────────────────────────────┐
│  Paperwork Navigator            │
│  書類を選択してください          │
├─────────────────────────────────┤
│                                 │
│  ┌───────────────────────────┐  │
│  │  📄 ○○○.pdf               │  │  ← 選択済みファイル名
│  └───────────────────────────┘  │
│                                 │
│  ⟳ 重要項目を抽出中...          │  ← 現在のステップ
│  [========>      ] 60%          │
│  Gemma 4（端末内）              │
│                                 │
└─────────────────────────────────┘
```

**エラー時（再試行可能）:**
```
│  ⚠️ 解析に失敗しました           │
│  JSONの解析に失敗しました        │
│        [再試行する]              │
```

#### S-02 レビュー画面

画面は縦にスクロールする。上から順に「翻訳バー」「レビュー（原文のみ or 原文・翻訳並列）」「PII」「チャット」の4セクション。

**翻訳前:**
```
┌────────────────────────────────────────┐
│  [←]  児童手当現況届                   │
├────────────────────────────────────────┤
│  🌐 [English ▼]  [翻訳する]           │  ← 翻訳バー
├────────────────────────────────────────┤
│  原文                                  │  ← 1列表示
├────────────────────────────────────────┤
│  ⚠️ 締め切り                           │
│  6月30日（月）                         │
│                                        │
│  📋 必要な行動                         │
│  現況届を記入して提出する              │
│  ...
```

**翻訳中:**
```
├────────────────────────────────────────┤
│  🌐 ⟳ English に翻訳中...             │  ← 翻訳バー（進捗表示）
├────────────────────────────────────────┤
│  原文のみ表示（翻訳列は非表示）        │
```

**翻訳後:**
```
┌────────────────────────────────────────┐
│  [←]  児童手当現況届                   │
├────────────────────────────────────────┤
│  🌐 English ✓  [再翻訳 ▼]             │  ← 翻訳バー（完了状態）
├───────────────────┬────────────────────┤
│  原文             │  English           │  ← 2列表示
├───────────────────┼────────────────────┤
│  ⚠️ 締め切り      │  ⚠️ Deadline        │
│  6月30日（月）    │  June 30 (Mon)     │
│                   │                    │
│  📋 必要な行動    │  📋 Required action │
│  現況届を記入     │  Fill in and       │
│  して提出する     │  submit the form   │
│                   │                    │
│  📎 持ち物        │  📎 Required docs   │
│  ・健康保険証    │  ・Health ins. card │
│  ・印鑑          │  ・Personal seal   │
│                   │                    │
│  ⚠️ 注意          │  ⚠️ Warning         │
│  未提出の場合     │  Failure to submit │
│  支給停止         │  stops payments    │
├───────────────────┴────────────────────┤
│  [📅 期限をカレンダーに追加] [🗺 地図で見る] │  ← クイックアクション
├────────────────────────────────────────┤
│  💬 Gemma 4 に質問する                 │
│  ┌──────────────────────────────────┐  │
│  │ 🤖 こんにちは。書類について何で  │  │  ← AI最初のメッセージ
│  │    も聞いてください。            │  │
│  │                                  │  │
│  │          👤 締め切りが過ぎた      │  │  ← ユーザー
│  │             場合はどうなる？     │  │
│  │                                  │  │
│  │ 🤖 未提出の場合、翌月以降の支    │  │  ← AI回答
│  │    給が停止されます。また2年間   │  │
│  │    未提出だと受給資格が消滅し    │  │
│  │    ます。                        │  │
│  │                                  │  │
│  │             ⟳ 回答を生成中...   │  │  ← ストリーミング中
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────┬────┐  │
│  │ 質問を入力...                │ ▶  │  │
│  └──────────────────────────────┴────┘  │
├────────────────────────────────────────┤
│  [問い合わせ文書を作成]                │
└────────────────────────────────────────┘
```

**クイックアクション:**

| ボタン | Intent | 表示条件 |
|--------|--------|---------|
| 📅 期限をカレンダーに追加 | `CalendarContract.Events.INSERT`（終日イベント、イベント名: `{doc_name} - 提出期限`、説明: `deadline.note_ja`） | `deadline.date != null` |
| 🗺 地図で見る | `geo:0,0?q={query}`（`location.address_ja` 優先、なければ `location.name_ja`） | `location.name_ja != null` または `location.address_ja != null` |

対応データがない場合はボタン自体を非表示にする。Intent を処理できるアプリが端末にない場合は `ActivityNotFoundException` をキャッチしてクラッシュを防ぐ。
Intent 生成は `util/DocumentIntentBuilder.kt` に集約する。

**翻訳バーの動作:**

| 状態 | 表示 |
|------|------|
| 未翻訳 | 言語選択ドロップダウン（デフォルト: English）+ 「翻訳する」ボタン |
| 翻訳中（`isTranslating = true`） | `⟳ {言語} に翻訳中...`（ドロップダウン・ボタン無効化） |
| 翻訳済み | `{言語} ✓` + 「再翻訳 ▼」ドロップダウン（別言語を選んで再翻訳可能） |

**翻訳先言語コード一覧（`selectedLanguage` に渡す値）:**

| UI 表示 | 言語コード | LLM プロンプト内表記 |
|---------|-----------|---------------------|
| 日本語 | `ja` | Japanese |
| English | `en` | English |
| 中文 | `zh` | Chinese (Simplified) |
| 한국어 | `ko` | Korean |
| Español | `es` | Spanish |
| Français | `fr` | French |
| Deutsch | `de` | German |
| Italiano | `it` | Italian |
| Português | `pt` | Portuguese |
| Русский | `ru` | Russian |
| Polski | `pl` | Polish |
| Nederlands | `nl` | Dutch |
| العربية | `ar` | Arabic |
| ภาษาไทย | `th` | Thai |
| Türkçe | `tr` | Turkish |

> LLM プロンプト内の `{target_language}` にはコードではなく「LLM プロンプト内表記」の値を埋め込む。アプリ内部では常に言語コードで保持し、プロンプト生成時に変換する。MVP では English を主な検証対象とする。

**チャット言語:**
- 翻訳前: チャットは書類の原文言語（`reviewResult.sourceLanguage`）で初期化
- 翻訳完了時: チャットセッションを翻訳言語で**再初期化**（履歴クリア）。スナックバーで「翻訳が完了しました。チャットをリセットしました。」を通知
- チャット言語 = `reviewResult.translation?.language ?: reviewResult.sourceLanguage`

> **「問い合わせ文書を作成」ボタンのタップ時挙動:**
> - **翻訳なし**: ダイアログなしで `reviewResult.sourceLanguage` を `targetLanguage` として `startInquiryWizard()` を呼び出し、S-04 へ遷移する。
> - **翻訳あり**: 2 択ダイアログを表示する。選択肢1は原文言語（`reviewResult.sourceLanguage`）、選択肢2は翻訳言語（`reviewResult.translation.language`）。ダイアログ内のそれぞれの言語名は `SupportedLanguage.displayName` で表示する。選択後に `startInquiryWizard(targetLanguage = 選択言語)` を呼ぶ。キャンセル時は S-02 に留まる。
>
> チャット入力は S-04 遷移後も S-02 に戻れば引き続き利用可能。チャット生成中（`chatIsGenerating = true`）はボタンを無効化する。

**色分けルール:**

| 色 | 意味 |
|----|------|
| 赤バッジ | 締め切り・警告（`severity: high`） |
| 橙バッジ | 必要書類・行動（`action_items`） |
| 青バッジ | 持ち物・連絡先 |
| 灰色 | 注意事項・補足 |
| 黄ハイライト | PII 候補（タップで選択解除） |

#### S-03 問い合わせコンテキスト確認画面

ウィザードで収集した情報を構造化コンテキストテキストとして表示する。LLM 呼び出しは不要で即時表示。ユーザーはこのテキストをコピーして Claude.ai / ChatGPT 等の外部 AI アシスタントに貼り付け、問い合わせ文書の生成を依頼する。

```
┌─────────────────────────────────┐
│  [←]  問い合わせコンテキスト     │
├─────────────────────────────────┤
│  あて先: 江戸川区役所 子育て支援課 │
│  目的: 期限の確認について         │
├─────────────────────────────────┤
│  ┌───────────────────────────┐  │
│  │ 📄 書類: 令和7年度         │  │
│  │    児童手当現況届           │  │
│  │                           │  │
│  │ 概要: 毎年6月に提出が必要  │  │
│  │ な現況届です。期限は令和7  │  │
│  │ 年6月30日（月）まで。      │  │
│  │                           │  │
│  │ 必要なアクション:          │  │
│  │ 1. 現況届を記入して提出    │  │
│  │                           │  │
│  │ 注意事項:                  │  │
│  │ - 未提出で支給停止（高）   │  │
│  │                           │  │
│  │ ---                       │  │
│  │ 問い合わせの目的:          │  │
│  │ 期限の確認について         │  │
│  │                           │  │
│  │ Q: 昨年も同じ書類を提出    │  │
│  │    しましたか？            │  │
│  │ A: はい                   │  │
│  │                           │  │
│  │ ※ 個人情報はマスク済みです │  │
│  └───────────────────────────┘  │
│                                 │
│  [📋 コピー]  [↑ 共有]          │
└─────────────────────────────────┘
```

- **コピー**: `InquiryContext.toContextText()` の出力をクリップボードにコピーする。完了後にスナックバーを表示
- **共有**: Android の共有シート（ACTION_SEND）を起動し、ユーザーが送信先を選ぶ

#### S-04 問い合わせ文書作成画面

**基本情報入力**

```
┌─────────────────────────────────┐
│  [←]  問い合わせ文書を作成       │
├─────────────────────────────────┤
│  📧 問い合わせの目的             │
│  ┌───────────┐ ┌──────────────┐ │
│  │ 期限の確認 │ │書き方の確認   │ │  ← LLM候補ボタン
│  └───────────┘ └──────────────┘ │
│  ┌──────────────────────────────┐│
│  │ または自由に記入...          ││  ← フリーテキスト入力
│  └──────────────────────────────┘│
│                                 │
│  📮 あて先                      │
│  組織名: [江戸川区役所 子育て支援課]│
│  担当者: [                     ] │
│  メール: [                     ] │
│  電話:   [                     ] │
│                                 │
│  🔒 マスクする個人情報            │
│  ■ Applicant name [Applicant name]│  ← Tier 1: デフォルトでチェック（マスク）
│  □ Issuer name [江戸川区役所]    │  ← Tier 2: デフォルトでチェックなし（実値を表示）
│  □ Issuer address [江戸川区中央1-2-3]│ ← Tier 2: タップでチェックするとマスクされる
│                                 │
│  [コンテキストを確認する]         │
└─────────────────────────────────┘
```

> **言語選択はS-02ボタンタップ時に完了**: S-04 には言語選択 UI を持たない。問い合わせ言語は S-02 の「問い合わせ文書を作成」ボタンタップ時に確定し、`InquiryWizard.targetLanguage` として S-04 に渡される（§3.3 S-02 参照）。

**目的候補の動作:**
- `InquiryWizard` 遷移と同時に LLM が目的候補を生成し始める（`purposeSuggestionsLoading = true`）
- 候補生成中はスピナーを表示。完了後に候補ボタンを表示
- 候補生成失敗時はボタンを非表示にし、フリーテキスト入力のみ表示する

**あて先自動入力:**
- `ReviewResult.issuer` を組織名に、`ReviewResult.contact` の各フィールドを対応するフォームにプリセット
- すべてのフィールドは手動で上書き可能

---

## 4. 処理フロー

### 4.1 全体フロー

```
入力（テキスト or PDF or 画像）
  │
  ├─[PDF / テキスト]──► [MF-01a] TextExtractor.extract()
  │                       テキスト文字列を返す
  │
  └─[カメラ / ギャラリー画像]
      │
      ├─(カメラ) GmsDocumentScanner → JPEG
      └─(ギャラリー) 画像選択 → パース補正（任意）
          │
          ▼
         [MF-01b] ImageTextExtractor.extract() / extractFromBitmap()
           ML Kit Japanese OCR でテキスト文字列を返す
           タイムアウト: 30 秒
          │
          ▼
         [MF-01c] OcrCorrector.correct(ocrText, images)  ← Gemma 4 マルチモーダル推論（新規）
           OCR テキストと元画像を比較し、誤認識を置換ペアで取得・適用する
           タイムアウト: 60 秒。失敗時は ocrText をそのまま使用（クラッシュしない）
  │
  ▼（MF-01a の場合は MF-01c をスキップして以下に合流）
LanguageIdentifier.identify(text.take(500))              ← ML Kit Language ID（< 1 秒）
  source_language（BCP-47 コード）を返す。信頼度低→ "und"
  │
  ▼
[MF-02] FieldExtractor.extract(text, sourceLanguage)     ← Gemma 4 推論 #1（9 フィールド）
  ReviewResult（issuerName / applicantName / otherName 等）を返す
  │
  ▼
EntityAnnotator.annotate(model, entities, issuerName, applicantName, otherName)  ← Gemma 4 推論 #2
  DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY に context_label を付与
  │
  ▼
EntityExtractor.mergeEntities(gemmaResult, annotatedEntities)
  context_label から deadline / docDate / issuerAddress / locations / eventDates を導出
  piiTier ∈ {1,2} のエンティティを piiSpans に収集
  │
  ├──► [MF-05] PiiMasker.mask(sourceText, piiSpans)      ← mergeEntities 完了後すぐ実行可能
  │      マスク済みテキストを返す（翻訳不要・LLM 不要）
  │
  ├──► [MF-04] レビュー画面表示（S-02）← MF-01/02/08/05 完了後すぐ
  │      原文言語で UI を描画する
  │
  ├──► [MF-07] DocumentChatSession.initialize(language=sourceLanguage)
  │      ReviewResult を原文言語でシステムコンテキストに設定
  │      ユーザー操作ごとに sendMessage() を呼ぶ
  │
  ├──► [MF-03] Translator.translate(reviewResult, lang)  ← ユーザーが「翻訳する」タップ時
  │      翻訳フィールドを ReviewResult.translation に追加
  │      完了後: S-02 を2列表示に更新
  │             DocumentChatSession を翻訳言語で再初期化（履歴クリア）
  │
  └──► [MF-06] InquiryContextBuilder（S-04 ウィザード）← Gemma 4 推論 #3
                「問い合わせ文書を作成」ボタン押下後に S-04 を表示
                  ・目的候補提示（推論 #3、S-04 表示と同時）
                  ・コンテキストテキスト生成（LLM 不要・即時）
                InquiryContext（コンテキストテキスト）を返す → S-03 へ遷移
```

### 4.2 DocumentReviewViewModel の状態遷移

S-01 の UI は `uiState` を監視し、`Processing` 中はボタンをプログレス表示に切り替える。
`Review` になったら S-02 へ遷移し（チャットセッションは原文言語で初期化）、`InquiryWizard` になったら S-04 へ、`InquiryPreview` になったら S-03 へ遷移する。
翻訳完了時はチャットセッションを翻訳言語で再初期化する。`chatIsGenerating = true` 中は「問い合わせ文書を作成」ボタンを無効化する。
「問い合わせ文書を作成」ボタンは S-02 側でタップ時に言語を確定し `startInquiryWizard(targetLanguage)` へ渡す。翻訳ありの場合は 2 択ダイアログを経由する（§3.3 S-02 参照）。

```kotlin
sealed class DocumentReviewUiState {
  // S-01: 待機中
  data object Idle : DocumentReviewUiState()

  // S-01: 解析中（ボタンをプログレス表示に切り替え）
  data class Processing(
    val step: ProcessingStep,
    val progress: Float,      // 0.0〜1.0
    val partialFields: List<Pair<String, String>> = emptyList(), // MF-02 ストリーミング中の部分表示
  ) : DocumentReviewUiState()

  // S-01: エラー（再試行可能）
  data class Error(@StringRes val messageRes: Int) : DocumentReviewUiState()

  // S-02: レビュー画面
  data class Review(
    val reviewResult: ReviewResult,
    val piiSpans: List<PiiSpan>,               // mergeEntities が構築した PII スパン一覧
    val sourceText: String = "",               // masking 用に保持（Tier-1、UI には表示しない）
    val selectedLanguage: String = "en",       // 翻訳バーの選択言語（翻訳前・後ともに保持）
    val isTranslating: Boolean = false,        // MF-03 実行中
    val translationError: Boolean = false,     // MF-03 失敗フラグ（翻訳バーにエラー表示）
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatIsGenerating: Boolean = false,     // チャット回答ストリーミング中
    val partialChatResponse: String? = null,   // ストリーミング中の応答（UI のみ）
    @StringRes val chatErrorRes: Int? = null,  // チャットエラーメッセージ
    val chatLimitReached: Boolean = false,     // チャット上限到達フラグ
    val chatAvailable: Boolean = true,         // セッション初期化失敗時に false
  ) : DocumentReviewUiState()
  // reviewResult.translation が null → 未翻訳（原文のみ1列表示）
  // reviewResult.translation != null → 翻訳済み（2列表示、チャットは翻訳言語）

  // S-02 → S-03（旧形式エスカレーション。エスカレーション生成中）
  data class GeneratingEscalation(
    val piiSpans: List<PiiSpan>,               // mergeEntities が構築した PII スパン一覧
    val reviewResult: ReviewResult,
    val sourceText: String,
    val userNotes: String,
    val chatMessages: List<ChatMessage>,
  ) : DocumentReviewUiState()

  // S-03（旧形式エスカレーション）: EscalationPackage 確認・コピー・共有
  data class OutputPreview(val pkg: EscalationPackage) : DocumentReviewUiState()

  // S-04: 問い合わせ文書作成ウィザード
  data class InquiryWizard(
    val reviewResult: ReviewResult,
    val piiSpans: List<PiiSpan>,               // 全 PII スパン（マスク設定の選択元）
    val purposeSuggestions: List<String> = emptyList(),
    val purposeSuggestionsLoading: Boolean = false,
    val userPurpose: String = "",
    val recipient: InquiryRecipient,
    val targetLanguage: String,
    val maskedPiiSpans: List<PiiSpan> = emptyList(),  // チェック済み（マスクする）スパン
  ) : DocumentReviewUiState()

  // S-03: 問い合わせコンテキスト確認・コピー（LLM 呼び出しなし・即時遷移）
  data class InquiryPreview(
    val contextText: String,
  ) : DocumentReviewUiState()
}

enum class ProcessingStep(@StringRes val labelRes: Int) {
  EXTRACTING_TEXT(R.string.doc_review_step_extracting_text),    // MF-01
  EXTRACTING_FIELDS(R.string.doc_review_step_extracting_fields), // MF-02（PII は EXTRA_PII フィールドで同時抽出）
  // 翻訳（MF-03）はユーザーが S-02 の翻訳バーから手動で実行するため Processing に含まない
}
```

> **LLM 推論のシリアル実行**: MF-02・MF-03・MF-06a・MF-07 の各推論は排他実行する。`DocumentReviewViewModel` が内部 `Mutex` で制御し、複数の推論が同時に発行されないことを保証する。MF-07 チャット生成中（`chatIsGenerating = true`）は「問い合わせ文書を作成」ボタンを無効化する。`Processing` 状態（MF-02/03）中はチャット入力・問い合わせボタンともに非表示のため競合は発生しない。`InquiryPreview` への遷移は LLM 呼び出しを伴わないため Mutex 不要。MF-01c（OCR 補正）はユーザー手動トリガーで `Mutex` 経由で実行し、`correctOcr()` / `cancelCorrection()` で制御する。

### 4.3 エラーハンドリング

| エラー | 対処 |
|--------|------|
| PDF にテキスト層なし |「このPDFはテキストを読み取れません」を S-01 上に表示 |
| OCR 失敗（`ExtractionError.OcrFailed`） | 「画像からテキストを読み取れませんでした」を S-01 上に表示。再試行可能 |
| OCR タイムアウト（30 秒） | `ExtractionError.OcrFailed` として扱い、同上のメッセージを表示 |
| MF-02 Gemma 4 推論タイムアウト（150秒） | 「解析に失敗しました。再試行してください」を表示 |
| MF-06 目的候補生成失敗（推論 #3） | 候補ボタンを非表示にし、フリーテキスト入力のみ表示する |
| MF-02 の行形式パースエラー | 最大2回リトライ（合計3回）。失敗時はエラー表示 |
| モデル未ダウンロード | ModelManager 画面へ誘導 |
| 入力テキスト上限超過 | 「書類が長すぎます（上限 8,000 文字）」を表示 |
| MF-03 翻訳失敗 | `isTranslating` を false に戻し、翻訳バーに「翻訳に失敗しました。再試行してください」を表示する。日本語のみの表示を維持する |
| MF-07 チャット推論タイムアウト（20秒） | チャット入力欄の上に「回答の生成に失敗しました。もう一度送信してください」を表示。失敗したアシスタントメッセージは履歴に残さない |
| MF-07 チャットセッション初期化失敗 | チャットセクションを非表示にし、S-02 のレビュー・問い合わせ機能は引き続き使用可能にする |

---

## 5. データモデル（JSON スキーマ）

### 5.1 ReviewResult（MF-02 + mergeEntities の出力）

FieldExtractor（Gemma 4 MF-02）が 9 フィールドを行形式で出力し、EntityAnnotator による `context_label` 付与後に `mergeEntities()` が ML Kit エンティティ由来のフィールドを補完する data class の完全定義。

```kotlin
@Serializable
data class ReviewResult(
    @SerialName("doc_name") val docName: String,           // 書類タイトル（MF-02）
    @SerialName("doc_date") val docDate: String? = null,   // 書類発行日（document_date エンティティから導出）
    @SerialName("issuer_name") val issuerName: String? = null, // 発行元組織・担当者（MF-02）
    @SerialName("applicant_name") val applicantName: String? = null, // 宛先の人物（MF-02）
    @SerialName("other_name") val otherName: String? = null,         // その他の人物（MF-02）
    val importance: String,                                // "high" | "medium" | "low"（MF-02）
    @SerialName("summary_ja") val summaryJa: String,       // 原文言語による要約（MF-02）
    val deadline: DeadlineInfo,                            // deadline エンティティから導出
    @SerialName("issuer_address") val issuerAddress: String? = null, // issuer_address エンティティから導出
    val locations: List<LocationEntry> = emptyList(),      // issuer_address + other_address エンティティから導出
    @SerialName("action_items") val actionItems: List<ActionItem> = emptyList(), // MF-02
    @SerialName("required_items") val requiredItems: List<RequiredItem> = emptyList(), // MF-02
    val warning: Warning? = null,                          // MF-02（最も重要な警告1件のみ）
    @SerialName("event_dates") val eventDates: List<EventDate> = emptyList(), // event_date エンティティから導出
    val translation: Translation? = null,                  // MF-03 後に追加
    @SerialName("source_language") val sourceLanguage: String = "ja", // BCP-47 言語コード
    @SerialName("detected_entities") val detectedEntities: List<DetectedEntity> = emptyList(), // ML Kit 全 11 種
)

// 連絡先はdetectedEntitiesから拡張関数で導出する
fun ReviewResult.issuerPhone(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_phone" }?.rawText
fun ReviewResult.issuerEmail(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_email" }?.rawText

@Serializable
data class DeadlineInfo(
    val date: String? = null,                              // ISO 8601 形式 or null
    @SerialName("note_ja") val noteJa: String? = null,    // deadline エンティティの rawText
)

@Serializable
data class LocationEntry(
    @SerialName("name_ja") val nameJa: String? = null,
    @SerialName("address_ja") val addressJa: String? = null,
)

@Serializable
data class ActionItem(
    val id: String,
    @SerialName("description_ja") val descriptionJa: String = "",
    val priority: Int = 1,
)

@Serializable
data class RequiredItem(
    val id: String,
    @SerialName("name_ja") val nameJa: String = "",
    @SerialName("note_ja") val noteJa: String? = null,
)

@Serializable
data class Warning(
    val id: String,
    @SerialName("description_ja") val descriptionJa: String = "",
    val severity: String = "medium",                       // "high" | "medium" | "low"
)

@Serializable
data class EventDate(
    val date: String? = null,
    @SerialName("description_ja") val descriptionJa: String = "",
)

@Serializable
data class DetectedEntity(
    val type: String,                                      // "PHONE" | "EMAIL" | "ADDRESS" | "DATE_TIME" | "URL" | "MONEY" | "IBAN" | "PAYMENT_CARD" | "FLIGHT_NUMBER" | "ISBN" | "TRACKING_NUMBER"
    @SerialName("raw_text") val rawText: String,
    @SerialName("context_label") val contextLabel: String? = null, // EntityAnnotator または静的設定
    @SerialName("pii_tier") val piiTier: Int? = null,             // computePiiTier() で静的導出
    val metadata: EntityMetadata? = null,
)

@Serializable
data class EntityMetadata(
    val timestampMillis: Long? = null,   // DATE_TIME
    val granularity: String? = null,     // DATE_TIME
    val currency: String? = null,        // MONEY
    val integerPart: Long? = null,       // MONEY
    val ibanCountryCode: String? = null, // IBAN
    val cardNetwork: String? = null,     // PAYMENT_CARD
    val carrier: String? = null,         // TRACKING_NUMBER（ML Kit ParcelCarrier int の toString）
    val airlineCode: String? = null,     // FLIGHT_NUMBER（IATA 2文字コード）
)
```

> **`_ja` サフィックス**: 実際には書類の原文言語（`sourceLanguage`）のテキストを保持する。日本語書類に限定されない。
>
> **PiiSpan の管理**: PII スパンは `ReviewResult` 内フィールドではなく `mergeEntities()` が `Pair<ReviewResult, List<PiiSpan>>` として返す。`piiTier ∈ {1, 2}` の全 `DetectedEntity` と `applicantName` / `otherName` から構築される。詳細は §5.3 と [抽出アーキテクチャ仕様書 §7.2](抽出アーキテクチャ仕様書_ja.md) 参照。
>
> **context_label と PII Tier の詳細**: [抽出アーキテクチャ仕様書](抽出アーキテクチャ仕様書_ja.md) §2〜§4 を参照。

### 5.2 Translation（MF-03 追加フィールド）

MF-03 完了後に `reviewResult.copy(translation = ...)` で設定される。

```kotlin
@Serializable
data class Translation(
    val language: String,                                       // 翻訳先言語コード（例: "en", "zh"）
    val summary: String,
    @SerialName("deadline_note") val deadlineNote: String? = null,
    @SerialName("action_items") val actionItems: List<TranslatedActionItem> = emptyList(),
    @SerialName("required_items") val requiredItems: List<TranslatedRequiredItem> = emptyList(),
    val warning: TranslatedWarning? = null,                    // 単一警告（Warning に対応）
)

@Serializable
data class TranslatedActionItem(val id: String, val description: String)

@Serializable
data class TranslatedRequiredItem(val id: String, val name: String, val note: String? = null)

@Serializable
data class TranslatedWarning(val description: String)
```

### 5.3 MaskResult / PiiSpan（MF-05 の出力）

LLM を使わずアプリ内で生成する。`PiiSpan` は `FieldExtractor.extract()` が `ReviewResult` とともに返し、`PiiMasker.mask()` に渡す。

```kotlin
data class MaskResult(
    val maskedText: String,             // マスク後のテキスト
    val appliedSpans: List<PiiSpan>,    // 実際にマスクしたスパン
    val skippedSpans: List<PiiSpan>,    // マスク対象外（userOverride=false or maskRecommended=false）
    val unmatchedSpans: List<PiiSpan>,  // 原文にマッチしなかったスパン（UI で通知）
)

@Serializable
data class PiiSpan(
    val id: String,
    @SerialName("span_text") val spanText: String = "",
    val category: String = "other",    // "name" | "address" | "phone" | "account" | "dob" | "id_number" | "other"
    @SerialName("source_field") val sourceField: String? = null, // "applicant_name" / "issuer_name" / "other_name" / contextLabel 等
    @SerialName("mask_recommended") val maskRecommended: Boolean = true,
    @SerialName("user_override") val userOverride: Boolean? = null, // true=強制マスク, false=除外, null=デフォルト
)

// カテゴリラベルの多言語表示は categoryLabel(lang: String) extension 関数で動的生成する
// fun PiiSpan.categoryLabel(lang: String): String { ... }

// マスクトークン: sourceField に基づいて "[Applicant name]" 等を返す。sourceField が null の場合は "[■■■]"
// fun PiiSpan.maskToken(): String { ... }
```

**PiiSpan の構築元と `maskRecommended` の設定ルール:**

| 構築元 | Tier | `maskRecommended` |
|--------|------|-------------------|
| `applicantName`（FieldExtractor 出力） | Tier 1 | `true` |
| `otherName`（FieldExtractor 出力） | Tier 2 | `false` |
| `issuerName`（FieldExtractor 出力） | Tier 2 | `false` |
| ML Kit エンティティ（`piiTier == 1`） | Tier 1 | `true` |
| ML Kit エンティティ（`piiTier == 2`） | Tier 2 | `false` |

- ML Kit エンティティ → `sourceField = entity.contextLabel`（例: `"issuer_address"`, `"applicant_phone"` 等）

### 5.4 ChatMessage（MF-07）

```kotlin
enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
  val id: String,                                    // UUID
  val role: ChatRole,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
)
```

### 5.5 InquiryContext（MF-06 の出力）

ウィザードで収集した問い合わせコンテキスト。LLM による文書生成は行わず、構造化テキストとして出力する。ユーザーが外部 AI アシスタントに貼り付けて問い合わせ文書の生成を依頼することを想定。

```kotlin
data class InquiryContext(
  val language: String,                              // 言語コード（en, ja, zh, ko）
  val recipient: InquiryRecipient,                   // あて先
  val purpose: String,                               // 問い合わせ目的（ユーザー入力）
  val documentSummary: String,                       // ReviewResult から生成したサマリーテキスト
  val maskedPiiSpans: List<PiiSpan>,                 // ユーザーがマスクを選んだ PII スパン（S-04 でチェック済み）
  val allPiiSpans: List<PiiSpan>,                    // 全 PII スパン（マスク除外スパンを計算するために保持）
  val reviewResult: ReviewResult,                    // 元の ReviewResult（toContextText() 用）
  val maskedSourceText: String = "",                 // PiiMasker.mask() の出力（Tier 2 データ）
)

data class InquiryRecipient(
  val organizationName: String,
  val contactName: String? = null,
  val email: String? = null,
  val phone: String? = null,
)
```

`InquiryContext.toContextText(): String` でクリップボードコピー・`ACTION_SEND` インテントに渡す構造化テキストを生成する。

**`toContextText()` 出力フォーマット:**

```
{intro_text}           ← 相談文スタイル。言語別に多言語化

━━━━━━━━━━━━━━━━━━━━━━━━

問い合わせの目的: {purpose}
あて先: {organizationName}

送信者情報（任意）:    ← allPiiSpans から maskedPiiSpans を除いたスパンが空の場合このセクションは省略
- {label}: {span_text}
...

---

📄 書類: {doc_name}
重要度: {importance}

📝 概要
{summary}

📅 期限
{deadline_note}        ← deadline が null の場合このセクションは省略

✅ 必要なアクション
1. {action_item_1}
...

📎 必要書類            ← 空の場合省略
- {required_doc_1}（{note}）
...

⚠️ 注意事項            ← 空の場合省略
- {warning_1}（{severity}）
...

※ 個人情報はマスク済みです

━━━━━━━━━━━━━━━━━━━━━━━━
📄 原文（個人情報マスク済み）   ← maskedSourceText が空の場合このセクションは省略
上記の抽出内容と照合し、不足・誤りがあれば補正してください。

{masked_source_text}
```

**コンテンツフィールドの言語選択ロジック:** 見出し・ラベルおよびコンテンツフィールドはすべて `InquiryContext.language` を基準にする。翻訳フィールド（summary・action_items・required_items・warning・deadline_note）は、`reviewResult.translation` が存在しかつ `translation.language == InquiryContext.language` の場合のみ翻訳版を使用し、それ以外は原文フィールド（`summaryJa` 等）を使用する。これにより原文言語を選択した場合に翻訳版が混入しない。

マスクされなかった PII スパン（`allPiiSpans` から `maskedPiiSpans` を差し引いたもの）を送信者情報として出力する。マスクされたスパンの `spanText` はこのテキストに含めない。

見出し・ラベルはすべて `InquiryContext.language` に応じて多言語化する（`ja` / `en` / `zh` / `ko` / `es` / `fr` / `de` / `it` / `pt` / `ru` / `pl` / `nl` / `ar` / `th` / `tr` に対応。未対応言語は日本語にフォールバック）。

---

## 6. Gemma 4 プロンプト設計

各 LLM 呼び出しのプロンプト全文・変数定義・評価基準は **[プロンプト仕様書](プロンプト仕様書_ja.md)** を参照。本節では実装上の判断に必要な方針と概要を記載する。

### 6.1 設計方針

- System Prompt は英語で記述する（LLM の多言語能力を活かし、あらゆる言語の書類に対して均質な出力品質を保つ）
- ユーザーメッセージに書類テキスト（任意言語）を渡し、出力は **行形式（Key-Value lines）** に固定する（MF-02 / MF-03 共通）
- 実機検証により LiteRT-LM 0.11.0 Kotlin API には Constrained Decoding が存在せず、複雑なネスト JSON では key-as-value 崩壊が発生することが確認された。MF-02・MF-03 ともに行形式を採用する
- few-shot example を含めることで精度を安定させる
- コンテキスト長の上限: 入力テキストは 8,000 文字以内にトリム（§9.4）

### 6.2 各 MF のプロンプト概要

| 推論 | 入力 | 出力 | 特記事項 |
|------|------|------|---------|
| MF-02 FieldExtractor | 書類テキスト（8,000 字以内） | 行形式（16 フィールド）→ ReviewResult + List\<PiiSpan\> | 行形式パースエラー時は最大 2 回リトライ |
| MF-03 Translator | ReviewResult の `_ja` フィールド群（原文言語テキスト） | 行形式（5 フィールド）→ Translation | PiiSpan・id 系フィールドは翻訳しない |
| MF-06a InquiryContextBuilder（目的候補） | ReviewResult の要約・action_items | 目的候補リスト（JSON array） | 失敗時は空リストでフォールバック |
| MF-07 DocumentChatSession | ReviewResult の構造化フィールド（PII 原文を除く） | チャット応答（ストリーミング） | システムプロンプトに PII spanText は含めない |

---

## 7. 処理ステップ（Skill）インターフェース

各処理ステップは `processing/` パッケージの独立クラスとして実装する。
`DocumentReviewViewModel` がオーケストレーションし、各ステップを順番に呼ぶ。

### 7.1 TextExtractor（MF-01a）

```kotlin
object TextExtractor {
  /**
   * PDF またはテキストファイルの URI からテキストを抽出する。
   * テキスト層あり PDF: PdfRenderer でページごとに抽出。
   * テキストファイル: InputStream で読み込み。
   *
   * @throws ExtractionError.NoPdfTextLayer テキスト層がない場合
   */
  suspend fun extract(context: Context, uri: Uri): String
}

sealed class ExtractionError : Exception() {
  object NoPdfTextLayer : ExtractionError()
  object UnsupportedFormat : ExtractionError()
  data class IoError(override val cause: Throwable?) : ExtractionError()
  object OcrFailed : ExtractionError()
}
```

**実装方針:**
- `android.graphics.pdf.PdfRenderer`（API 35）の `Page.getPageContent()` でテキストを取得
- minSdk = 35 のため API 分岐は不要
- ページ間は `\n\n` で連結
- 抽出結果の上限: 8,000 文字（超過時は先頭を優先）

### 7.2 ImageTextExtractor（MF-01b）

```kotlin
object ImageTextExtractor {
  /**
   * 画像ファイルの URI から ML Kit OCR でテキストを抽出する。
   * sourceLanguage に応じて OCR モデルを切り替える。
   * GmsDocumentScanner 経由のカメラ撮影結果（JPEG URI）にも対応。
   *
   * @param sourceLanguage BCP-47 言語コード（"ja", "zh", "ko", "en" 等）
   * @throws ExtractionError.OcrFailed OCR 失敗またはタイムアウト（30 秒）
   */
  suspend fun extract(context: Context, uri: Uri, sourceLanguage: String): String

  /**
   * Bitmap から直接 OCR を実行する。
   * ギャラリー画像のパース補正（perspective correction）後に使用。
   *
   * @param sourceLanguage BCP-47 言語コード（"ja", "zh", "ko", "en" 等）
   * @throws ExtractionError.OcrFailed OCR 失敗またはタイムアウト（30 秒）
   */
  suspend fun extractFromBitmap(bitmap: Bitmap, sourceLanguage: String): String
}
```

**対応言語と OCR モデル（12 言語）:**

| スクリプト | 言語コード | ML Kit モデル |
|-----------|-----------|--------------|
| Japanese | `ja` | `JapaneseTextRecognizerOptions` |
| Chinese | `zh` | `ChineseTextRecognizerOptions` |
| Korean | `ko` | `KoreanTextRecognizerOptions` |
| Latin | `en`, `es`, `fr`, `de`, `it`, `pt`, `nl`, `pl`, `tr` | `TextRecognizerOptions.DEFAULT_OPTIONS` |

`ru` / `ar` / `th` は ML Kit に対応する OCR モデルが存在しないため、カメラ・ギャラリー入力時の言語選択 UI から除外する（`SupportedLanguage.supportsOcr = false`）。

**実装方針:**
- `resolveRecognizerOptions(sourceLanguage)` で言語コードから `TextRecognizerOptionsInterface` を選択
- `Tasks.await()` で最大 30 秒待機。タイムアウト時は `ExtractionError.OcrFailed` をスロー
- テキストブロック間は `\n` で連結
- 抽出結果の上限: 8,000 文字（`TextExtractor.MAX_CHARS` を共有）
- 抽出結果が空（blank）の場合も `ExtractionError.OcrFailed` をスロー

**カメラ入力の流れ（S-01）:**
1. ユーザーが「カメラで撮影する」をタップ → `GmsDocumentScanner`（SCANNER_MODE_FULL）を起動
2. スキャン完了後、全ページの JPEG URI を取得 → 言語選択ダイアログを表示（12 言語）
3. 言語選択後 → 各 URI を `ImageTextExtractor.extract(context, uri, selectedOcrLanguage)` で順番に OCR し、`\n\n` で連結して `TextExtractor.MAX_CHARS` でトリム
4. OCR 結果を `pastedText` に格納し、「解析を開始する」を有効化

**ギャラリー画像入力の流れ（S-01）:**
1. ユーザーが「ギャラリーから選ぶ」をタップ → 画像ピッカーを起動（`OpenMultipleDocuments`、JPEG / PNG）
2. 選択した枚数に応じて分岐:
   - **1 枚**: パース補正画面（corner drag）を経由して perspective transformation を適用 → OCR 言語選択ダイアログを表示
   - **2 枚以上**: 1 枚ずつ順番にパース補正画面を表示し perspective transformation を適用 → 全枚完了後に OCR 言語選択ダイアログを 1 回表示
3. 言語選択後 → 各補正済み Bitmap を `ImageTextExtractor.extractFromBitmap(bitmap, selectedOcrLanguage)` で順番に OCR し、`\n\n` で連結して `TextExtractor.MAX_CHARS` でトリム
4. OCR 結果を `pastedText` に格納し、「解析を開始する」を有効化

> **注意**: いずれか 1 枚でも OCR 失敗した場合は `ExtractionError.OcrFailed` を投げてエラー表示する

### 7.3 OcrCorrector（MF-01c）

```kotlin
class OcrCorrector(private val llmHelper: LlmModelHelper) {

  /**
   * OCR テキストと元画像を Gemma 4 マルチモーダル推論に渡し、
   * 置換ペア形式で誤りを取得して補正済みテキストを返す。
   * 補正が不要または失敗した場合は ocrText をそのまま返す。
   * タイムアウト: 60 秒
   *
   * @param images 元ページ画像（1 ページ 1 枚）。空の場合は補正をスキップする
   */
  suspend fun correct(model: Model, ocrText: String, images: List<Bitmap>): String
}
```

**実装方針:**
- `llmHelper.resetConversation(supportImage = true, systemInstruction = ...)` で画像入力セッションを初期化
- `llmHelper.runInference(images = images)` で Gemma 4 にページ画像 + OCR テキストを渡す
- 出力形式: `CORRECT: <wrong>|<right>` 行（1 件 1 行）または `(none)`
- パース失敗・タイムアウト時は例外をキャッチして `ocrText` をそのまま返す（リトライなし）
- PDF 入力（`DocumentInput.PdfUri`）では呼ばれない

### 7.4 FieldExtractor（MF-02）

```kotlin
class FieldExtractor(private val llmHelper: LlmModelHelper) {

  /**
   * ドキュメントテキストから構造化情報を抽出する。
   * Gemma 4 に JSON structured output で出力させる。
   *
   * @param onProgress 推論の進捗コールバック（ストリーミングトークン）
   * @return ReviewResult（失敗時は FieldExtractionError を throw）
   */
  suspend fun extract(
    model: Model,
    text: String,
    onProgress: (String) -> Unit,
  ): ReviewResult
}

sealed class FieldExtractionError : Exception() {
  object JsonParseError : FieldExtractionError()
  object ModelNotInitialized : FieldExtractionError()
  data class InferenceError(override val message: String?) : FieldExtractionError()
}
```

**実装方針:**
- `llmHelper.runInference()` を呼ぶ
- 出力をストリームで受け取り、完了後に JSON パース
- パースエラー時は最大2回リトライ（エラー情報をプロンプトに追加してリトライ）

### 7.4 Translator（MF-03）

```kotlin
class Translator(private val llmHelper: LlmModelHelper) {

  /**
   * ReviewResult の日本語フィールドを targetLanguage に翻訳し、
   * translation フィールドを埋めた ReviewResult を返す。
   */
  suspend fun translate(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  ): ReviewResult
}
```

**翻訳対象フィールド:**
`summary_ja`, `action_items[].description_ja`, `required_items[].name_ja`,
`required_items[].note_ja`, `warning.description_ja`, `deadline.note_ja`

**翻訳しないフィールド:**
PiiSpan（スパン位置が変わるため）、`id` 系フィールド

### 7.5 PiiMasker（MF-05）

```kotlin
object PiiMasker {

  /**
   * テキスト内の PII スパンをマスクする。LLM を使用しない。
   * span_text で原文を検索し、マッチした箇所を PiiSpan.maskToken() のトークンに置換する。
   */
  fun mask(text: String, spans: List<PiiSpan>): MaskResult

  /**
   * ユーザーがスパンの userOverride を変更した後に再マスクする。
   */
  fun remask(text: String, spans: List<PiiSpan>): MaskResult
}
```

**マスクトークン:** `PiiSpan.maskToken()` が `sourceField` に基づいてラベル付きトークンを返す（例: `[Applicant name]`、`[Issuer address]`）。`sourceField` が null の場合は `[■■■]` にフォールバックする。これにより、マスク済みテキストを読んでも文脈が分かる（例: `受給者 [Applicant name] 様`）。

**マスク優先順位:**
1. `userOverride == true` → 強制マスク
2. `userOverride == false` → 強制除外
3. `userOverride == null && maskRecommended == true` → マスク（デフォルト）
4. `userOverride == null && maskRecommended == false` → 除外

**スパンマッチング:**
- `spanText` から全空白を除去し、各文字を `Regex.escape()` でエスケープしたうえで `[\s　]*` で連結した正規表現を生成して検索する（PDF テキスト抽出のスペース差異・全角スペースも吸収。`.` `(` `)` `+` 等の正規表現メタ文字はリテラルとして扱われる）
- 全空白除去後に空文字になる spanText（空白のみのスパン）は `buildMatchRegex()` が `null` を返し、呼び出し元が `unmatchedSpans` に記録する
- マッチした箇所をすべて `span.maskToken()` のトークンに置換する
- マッチしなかったスパンは `appliedSpans` に含めず `unmatchedSpans` に記録して UI 上でユーザーに通知する

### 7.6 InquiryContextBuilder（MF-06）

```kotlin
class InquiryContextBuilder(private val llmHelper: LlmModelHelper) {

  /**
   * 書類内容から問い合わせ目的の候補を提案する（推論 #3）。
   * 失敗時は空リストを返す（ユーザーはフリーテキスト入力に切り替え）。
   */
  suspend fun suggestPurposes(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  ): List<String>

  /**
   * ウィザードで収集した情報を InquiryContext に組み立てる（LLM 呼び出しなし）。
   * ReviewResult + ウィザード入力から toContextText() で出力するコンテキストを生成する。
   */
  fun buildContext(
    reviewResult: ReviewResult,
    purpose: String,
    recipient: InquiryRecipient,
    maskedPiiSpans: List<PiiSpan>,  // S-04 でユーザーがマスクを選択したスパン
    allPiiSpans: List<PiiSpan>,     // 全 PII スパン（未マスクスパンを送信者情報として出力）
    targetLanguage: String,
    sourceText: String,             // PiiMasker.mask() 呼び出し用（内部で maskedSourceText を生成）
  ): InquiryContext
}
```

### 7.7 DocumentChatSession（MF-07）

```kotlin
class DocumentChatSession(private val llmHelper: LlmModelHelper) {

  /**
   * ReviewResult の構造化データをシステムコンテキストとして設定し、チャットセッションを開始する。
   * システムプロンプトに PII 原文は含めない（ReviewResult の抽出フィールドのみ）。
   */
  suspend fun initialize(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  )

  /**
   * ユーザーメッセージを送信し、ストリーミングでトークンを受け取る。
   * チャット履歴は内部で保持し、毎回のリクエストに含める。
   *
   * @param onToken ストリーミングトークンのコールバック
   * @return 完成したアシスタントの ChatMessage
   */
  suspend fun sendMessage(
    model: Model,
    userMessage: String,
    onToken: (String) -> Unit,
  ): ChatMessage

  /** セッションのチャット履歴を返す（S-04 Q&A コンテキストとして渡す場合などに使用） */
  fun getChatHistory(): List<ChatMessage>

  /** セッションをリセットする（新しい書類を読み込む際） */
  fun clear()
}
```

**実装方針:**
- `LlmChatModelHelper` の既存チャット機能を流用し、システムプロンプトのみ差し替える
- ユーザーメッセージ送信中（`chatIsGenerating = true`）は入力フィールドと送信ボタンを無効化する
- チャット履歴の正本は `DocumentChatSession` 内部で管理する。ViewModel は `sendMessage()` が返した `ChatMessage` を都度 `Review.chatMessages` に追記して UI を更新する（`Review.chatMessages` は UI 表示専用）。

### 7.8 LanguageIdentifier（MF-01 直後）

```kotlin
object LanguageIdentifier {

  /**
   * テキストの先頭 500 文字から ML Kit Language Identification で言語を識別する。
   * 信頼度が低い場合は "und" を返す。
   *
   * @return BCP-47 言語コード（例: "ja", "en", "zh"）または "und"
   */
  suspend fun identify(text: String): String
}
```

**実装方針:**
- `LanguageIdentification.getClient()` でクライアントを生成し、`identifyLanguage()` を実行する
- `suspendCancellableCoroutine` でコールバックをコルーチンにブリッジする
- クライアントのライフサイクルは `try/finally` で管理し、`finally { identifier.close() }` で確実に解放する
- 識別失敗・例外はすべて `"und"` にフォールバックする（MF-02 の処理を止めない）
- MF-01 完了直後、LLM Mutex の外側で呼ぶ（ML Kit 識別は LLM リソースを使用しないため）

---

## 8. ローカル保存

### 8.1 保存先

`context.filesDir` 配下のプライベートストレージに保存する。外部ストレージは使用しない。

```
{filesDir}/documents/
└── doc_{yyyyMMdd_HHmmss}_{shortUuid}/
    ├── meta.json           ← ReviewResult + PiiSpan 情報（PII を含む）
    ├── source.txt          ← 抽出済みテキスト（PII を含む）
    ├── escalation.json     ← EscalationPackage（マスク済み・ユーザーが MF-06 を実行した場合のみ）
    └── inquiry.json        ← InquiryContext（PII 含む可能性あり・ユーザーが明示的に含めた場合）
```

### 8.2 セキュリティ方針

- `source.txt` と `meta.json` は端末外に送信しない
- `escalation.json`（EscalationPackage）はマスク済みデータのみを含む。アプリは自動送信せず、ユーザーが共有シート経由で送信先を選ぶ
- `inquiry.json`（InquiryContext）の内容は、ユーザーが S-04 で明示的に含めることを選んだ PII を含む可能性がある。アプリは自動送信せず、ユーザーが共有シート経由で送信先を選ぶ
- MVP では暗号化なし（Phase 2 で Keystore を検討）

### 8.3 DocumentRepository

```kotlin
class DocumentRepository(private val filesDir: File) {
  suspend fun save(
    docId: String,
    reviewResult: ReviewResult,
    sourceText: String,
  ): Boolean

  suspend fun saveEscalation(docId: String, pkg: EscalationPackage): Boolean
  suspend fun saveInquiry(docId: String, context: InquiryContext): Boolean
  suspend fun list(): List<DocumentMeta>
  suspend fun load(docId: String): DocumentBundle?
  suspend fun delete(docId: String): Boolean
}

data class DocumentMeta(
  val docId: String,
  val docName: String,           // 書類名（ReviewResult.docName）
  val importanceLevel: String,   // "high" | "medium" | "low"
  val createdAt: Long,           // Unix ミリ秒
  val hasEscalation: Boolean,    // escalation.json が存在するか
  val hasInquiry: Boolean = false,
)

data class DocumentBundle(
  val reviewResult: ReviewResult,
  val sourceText: String,
  val escalationPackage: EscalationPackage?,
  val inquiryContext: InquiryContext?,
)
```

---

## 9. 非機能要件

### 9.1 ターゲット端末

| 項目 | 値 |
|------|----|
| デバイス | Google Pixel 9 |
| RAM | 12 GB |
| OS | Android 15 |
| minSdk | 35（Android 15 PdfRenderer テキスト抽出 API の要件） |

### 9.2 モデル選択

| モデル | サイズ | 説明 |
|--------|--------|------|
| Gemma 4 E2B | 2.58 GB | 推奨（ダウンロードサイズが小さい） |
| Gemma 4 E4B | 3.65 GB | 高精度オプション（モデルマネージャーで手動選択） |

### 9.3 推論時間目安（Pixel 9 想定）

| ステップ | 目安 |
|----------|------|
| MF-01a テキスト抽出（PDF） | < 1 秒 |
| MF-01b OCR（ML Kit、画像） | < 5 秒 |
| MF-02 フィールド抽出（Gemma 4） | 60〜150 秒 |
| MF-03 翻訳（Gemma 4） | 15〜60 秒 |
| MF-06a 目的候補生成（Gemma 4） | 5〜15 秒 |
| MF-06b Q&A 質問リスト生成（Gemma 4） | 5〜15 秒 |
| MF-06 コンテキストテキスト生成（LLM 不要） | < 1 秒 |
| MF-07 チャット返答（Gemma 4、1ターン） | 3〜10 秒（ストリーミング） |

推論時間は実機検証で計測し調整する。

### 9.4 文字数制限

| 項目 | 上限 | 理由 |
|------|------|------|
| MF-01 抽出テキスト | 8,000 文字 | 後段にトリムしてから渡す |
| MF-02 入力テキスト | 8,000 文字 | Gemma 4 の実コンテキスト上限 32,000 tokens に対し、8,000 文字 ≈ 5,333 tokens + システムプロンプト約 850 tokens = 約 6,200 tokens で余裕あり |
| MF-06c 文書生成の入力 | 4,000 文字（Q&A 結果含む） | Gemma 4 のコンテキスト長と生成品質のバランス |
| MF-07 チャット履歴 | 20 ターン（Q&A 10往復）または累計 4,000 文字のいずれか先に達した方 | システムプロンプトと合算してコンテキスト長を超えないようにする。上限に達したら「チャット履歴の上限に達しました」を表示し、新規入力を無効化する |

### 9.5 MVP で対応しないこと（P2 以降）

- ウェブ検索による制度情報補完（OF-10）
- クラウド LLM へのエスカレーション（OF-09）
- 複数書類横断の Agent RAG（Phase 2）
- 法的相談・制度解釈
- 暗号化ストレージ
