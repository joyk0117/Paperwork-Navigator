---
title: "レビュー画面"
type: "schema:DefinedTerm"
lang: ja
description: "書類解析結果を原文・翻訳の2列表示で確認し、クイックアクション・PIIマスク編集・書類理解チャットを行うPaperwork Navigatorのメイン画面（S-02）"
termCode: "MF-04"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
sources:
  - type: file
    path: 'docs/implementation-spec.md'
    hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523
  - type: file
    path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
    hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

レビュー画面（MF-04、画面ID S-02）は、[[SoftwareApplication/paperwork-navigator]] が解析した書類の結果を表示するメイン画面であり、翻訳バー・レビュー内容（原文のみ、または原文・翻訳の2列並列表示）・クイックアクション・[[DefinedTerm/document-chat]] セクションを縦スクロールで構成する。締め切り・警告（赤バッジ）・必要書類/行動（橙バッジ）・書類/連絡先（青バッジ）・注意事項（灰色）はカテゴリ別の色分けバッジで表示され、PII候補は黄色ハイライトでタップにより選択解除できる。

## Usage

### 画面遷移

Paperwork Navigator の画面は S-01（入力画面）・S-02（レビュー画面）・S-03（問い合わせコンテキスト確認画面）・S-04（問い合わせ文書作成画面）・S-M（モデルマネージャー）の5つで構成される。S-01 で解析が成功すると S-02 に遷移し、S-02 で「問い合わせ文書を作成」をタップすると S-04（基本情報入力）→「コンテキストを確認する」で S-03（コピー・共有）に進む。S-02 → S-01 に戻る操作では `ReviewResult` / `MaskResult` / チャット履歴をすべてクリアして `Idle` 状態に戻り、S-04 → S-02 に戻る操作では `ReviewResult` / `MaskResult` / チャット履歴を保持したまま `Review` 状態に戻る。

### 状態管理（DocumentReviewUiState）

`DocumentReviewViewModel` は `Idle`（S-01 待機）→ `Processing`（解析中、ステップとプログレスを保持）→ `Review`（S-02、`reviewResult` / `piiSpans` / 翻訳状態 / チャット状態を保持）→ `InquiryWizard`（S-04）→ `InquiryPreview`（S-03）と遷移する `sealed class` で状態を管理する。`Review.translation` が `null` の場合は未翻訳（原文のみ、1列表示）、非 `null` の場合は翻訳済み（2列表示、チャットは翻訳言語）を表す。MF-02・MF-03・MF-06a・MF-07 の推論は `DocumentReviewViewModel` 内部の `Mutex` により相互排他で実行され、複数の推論が同時に発行されないことが保証される。`Processing` 状態（MF-02/03 実行中）ではチャット入力・問い合わせボタンの両方が非表示になるため競合は発生しない。

### クイックアクション

| ボタン | Intent | 表示条件 |
|--------|--------|----------|
| 📅 期限をカレンダーに追加 | `CalendarContract.Events.INSERT`（終日イベント、イベント名: `{doc_name} - 提出期限`） | `deadline.date != null` |
| 🗺 地図で見る | `geo:0,0?q={query}`（`location.address_ja` 優先、なければ `location.name_ja`） | `location.name_ja` または `location.address_ja` が存在する場合 |

該当データがない場合はボタンを非表示にし、Intent を処理できるアプリが端末にない場合は `ActivityNotFoundException` をキャッチしてクラッシュを防ぐ。Intent 生成は `util/DocumentIntentBuilder.kt` に集約される。

### 翻訳バー

未翻訳（言語選択ドロップダウン、初期値 English + 「翻訳する」ボタン）→ 翻訳中（`isTranslating = true`、`⟳ {言語}に翻訳中...`、ドロップダウン・ボタン無効化）→ 翻訳済み（`{言語} ✓` + 再翻訳ドロップダウン）と状態遷移する。翻訳完了前はチャットは原文言語（`reviewResult.sourceLanguage`）で初期化され、翻訳完了後はチャットセッションが翻訳言語で再初期化（履歴クリア、スナックバー通知）される。「問い合わせ文書を作成」ボタンをタップした際、未翻訳なら原文言語のまま S-04 に遷移し、翻訳済みなら原文言語／翻訳言語のどちらでウィザードを進めるかを選ぶ2択ダイアログを表示する。

### 原文表示セクション（SourceTextSection）

翻訳バーより上部には、`state.sourceText` が空でない場合にのみ表示される原文表示セクションがあり、トグルボタンで折りたたみ／展開を切り替えられる。展開時は `SelectionContainer` でラップされた等幅フォントのテキストとして原文全体が表示され、ユーザーはテキストを選択してコピーできる。

### バッジ表示の共通コンポーネント（ReviewBadgeItem / EventDatesSection）

レビュー内容の各情報項目は `ReviewBadgeItem`（アイコン絵文字・ラベルバッジ・本文・任意の末尾ボタンで構成される共通コンポーネント）により統一表示される。バッジ色は `ReviewBadgeColor`（RED / ORANGE / BLUE / GRAY の4種）で表現され、高重要度の締め切り・警告は RED、行動項目は ORANGE、必要書類は BLUE、それ以外は GRAY が使われる。イベント日程（`EventDatesSection`）は原文カラム・翻訳カラムの両方から呼び出される共有コンポーネントとして実装され、翻訳対象に含まれない。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
- [[DefinedTerm/multilingual-translation]]
- [[DefinedTerm/document-chat]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/quick-action]]
