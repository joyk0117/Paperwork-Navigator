---
title: "問い合わせコンテキスト生成"
type: "schema:DefinedTerm"
lang: ja
description: "PIIマスク済みのテキストをウィザード形式で組み立て、外部AIや専門家への相談に使う問い合わせコンテキストを生成する機能"
termCode: "MF-06"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
sources:
  - type: file
    path: 'README_ja.md'
    hash: sha256:3a8db998c216c576c641a151a9a2bcad45b3ce95bec627233dc53dbd50f41719
  - type: file
    path: 'docs/implementation-spec_ja.md'
    hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312
  - type: file
    path: 'docs/implementation-spec.md'
    hash: sha256:0077574008eb15e701f7cb37ff341d0f244295b725ee6b130259940852db9523
  - type: file
    path: 'docs/prompt-spec.md'
    hash: sha256:144c3266a5a9c7f51265c174bccc2f3bc91093f079f90460614d6466d639087a
  - type: file
    path: 'docs/prompt-spec_ja.md'
    hash: sha256:2a73881ef8f5e964725c85c69fe787cfdee469e1957cfced836774474e3e283d
  - type: file
    path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
    hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

問い合わせコンテキスト生成（MF-06）は、[[SoftwareApplication/paperwork-navigator]] のレビュー画面からウィザード形式でコンテキストを組み立て、[[DefinedTerm/pii-masking]] 済みのテキストを生成する機能である。生成されたテキストは外部 AI や専門家への相談を補助する。`InquiryContextBuilder` がこの処理を担当し、`suggestPurposes()`（Gemma 4 推論 #3）と `buildContext()`（LLM 呼び出しなし）の2つの関数で構成される。

## Usage

レビュー画面（MF-04）で「問い合わせ文書を作成」ボタンをタップするとウィザード（S-04）に遷移し、`InquiryContextBuilder.suggestPurposes()` が書類の要約・action_items から問い合わせ目的の候補を提案する（生成中はスピナー表示、失敗時は空リストにフォールバックしフリーテキスト入力のみ表示）。あて先は `ReviewResult.issuer` や `ReviewResult.contact` から自動入力され、手動で上書きできる。マスクする個人情報はスパンごとにチェックボックスで選択でき、Tier 1（`maskRecommended = true`）のスパンはデフォルトでチェック済みになっている。

`buildContext()` は `ReviewResult` とウィザード入力から `InquiryContext` を組み立て、`toContextText()` が最終的な構造化テキストを生成する。出力には相談文・問い合わせの目的・あて先に加え、マスクされなかった PII スパン（`allPiiSpans` から `maskedPiiSpans` を除いたもの）が「送信者情報」として含まれる（空の場合はセクション省略）。書類概要・期限・必要なアクション・必要書類・注意事項は、`reviewResult.translation` が存在し翻訳言語がコンテキスト言語と一致する場合のみ翻訳版を使用し、それ以外は原文フィールドを使う。見出し・ラベルは `InquiryContext.language` に応じて多言語化され、未対応言語は日本語にフォールバックする。

生成されたコンテキストは問い合わせコンテキスト確認画面（S-03）に即時表示され（LLM 呼び出し不要）、Android 共有シートを通じてコピー・共有でき、外部 AI や専門家に送信できる。

### InquiryContext データモデル

`InquiryContext` は `language` / `recipient`（`InquiryRecipient`: `organizationName` / `contactName` / `email` / `phone`）/ `purpose` / `documentSummary` / `maskedPiiSpans`（ユーザーがマスク選択したスパン）/ `allPiiSpans`（未マスクスパン計算用に全件保持）/ `reviewResult` / `maskedSourceText` で構成される。`toContextText()` は、相談文（`InquiryContext.language` に応じ多言語）・問い合わせ目的・あて先・送信者情報（マスクされなかった PII、空の場合は省略）・書類概要・期限（`null` の場合省略）・必要なアクション・必要書類（空の場合省略）・注意事項（空の場合省略）・マスク済み原文（`maskedSourceText` が空の場合省略）の順で構造化テキストを出力する。

### suggestPurposes() のプロンプト設計

`suggestPurposes()`（Gemma 4 推論）は書類の要約・必要なアクション・原文テキスト（16,000文字以内）に加え、それまでの[[DefinedTerm/document-chat]]の履歴があればユーザーの関心事を汲み取る材料として渡し、5〜15語程度の目的候補を3〜5件、JSON配列で生成する。チャット履歴が空（ユーザー発言なし）の場合はプロンプトからその項目自体を省略する。JSONパース失敗時やタイムアウト（15秒）時はリトライせず即座に空リストにフォールバックする。

### MF-06 の変遷とエスカレーションパッケージ生成

MF-06 は当初、目的候補提案（MF-06a）・不足情報の質問リスト生成（MF-06b）・問い合わせ文書生成（MF-06c）の3段階構成だったが、MF-06b はオンデバイス検証の結果有用性が低いと判断され削除され、MF-06c はオンデバイス推論による文書生成の品質懸念（文体・敬語・言語不一致）から Phase 2 に先送りされた。プロンプト仕様書によれば、現在の MF-06 は目的候補提案（MF-06a）・エスカレーションパッケージ生成（MF-06）・コンテキストテキスト組み立て（LLM 不要）の3要素で構成される。エスカレーションパッケージ生成は「Create Handoff File」ボタン押下時に実行される想定のプロンプトで、マスク済みテキストから `consultation_summary`（相談内容の要約）・`timeline`（日付とイベントの一覧）・`ai_hypotheses`（書類だけでは判断できない点や解釈の仮説）を含む JSON を生成する。

### エスカレーションパッケージ出力画面（S03OutputPreviewContent）

プロンプト仕様書で「想定」とされていたエスカレーションパッケージ生成は、`DocumentReviewScreen.kt` では `GeneratingEscalation` → `OutputPreview` という UI 状態遷移として実装されている。`onGenerateEscalation(userNotes: String)` コールバック（ユーザーメモを任意で受け取る）が呼び出されると生成が始まり、完了後 `S03OutputPreviewContent` が `EscalationPackage.toPlainText()` を等幅フォントでスクロール表示する。画面上部には `pkg.maskedFields` の有無に応じてマスクされたフィールド名を含む／含まないバナーが表示され、下部の「コピー」「共有」ボタンは問い合わせコンテキスト確認画面（`S03InquiryPreviewContent`）と同じ `copyToClipboard()` / `shareText()` ヘルパーを再利用する。`GeneratingEscalation` 中の画面本体は `Review` 状態の内容（`reviewResult` / `piiSpans` / `sourceText` / `chatMessages` 等）を複製した状態で `S02ReviewContent` を再利用し続けるため、生成中もレビュー画面がそのまま表示され続け、別画面への遷移や再マウントは発生しない。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/pii-masking]]
- [[DefinedTerm/document-chat]]
