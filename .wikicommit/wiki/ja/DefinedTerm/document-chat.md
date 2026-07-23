---
title: "書類理解チャット"
type: "schema:DefinedTerm"
lang: ja
description: "レビュー画面に統合された、書類内容についてオンデバイス Gemma 4 に質問できるチャット機能（DocumentChatSession）"
termCode: "MF-07"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [オンデバイス処理]
sources:
  - type: file
    path: 'docs/implementation-spec_ja.md'
    hash: sha256:9cd6aa649fae3a83092bfb33b8e22074da8684515e6fb67436bbeed610d90312
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

書類理解チャット（MF-07）は、`DocumentChatSession` がレビュー画面（S-02）に統合されたチャットで、`ReviewResult` の構造化フィールドをシステムコンテキストとして設定し、ユーザーが書類内容について Gemma 4 に質問できる機能である。システムプロンプトには PII 原文（`piiSpans` の spanText）は含めない。

## Usage

`initialize()` はレビュー画面表示直後に原文言語でセッションを開始し、翻訳完了時（MF-03）には翻訳言語でセッションが再初期化される（履歴クリア）。`sendMessage()` はユーザーメッセージを送信し、`onToken` コールバックでストリーミングにトークンを受け取って完成した `ChatMessage` を返す。チャット履歴の正本は `DocumentChatSession` 内部で管理し、`LlmChatModelHelper` の既存チャット機能を流用してシステムプロンプトのみ差し替える。

チャット履歴には上限があり、20 ターン（Q&A 10往復）または累計 4,000 文字のいずれか先に達した時点で「チャット履歴の上限に達しました」を表示し、新規入力を無効化する。推論タイムアウトは60秒で、タイムアウト時はチャット入力欄の上に「回答の生成に失敗しました。もう一度送信してください」を表示し、失敗したアシスタントメッセージは履歴に残さない。チャットセッション初期化に失敗した場合はチャットセクションを非表示にし、レビュー・問い合わせ機能は引き続き使用可能にする。チャット生成中（`chatIsGenerating = true`）は「問い合わせ文書を作成」ボタンを無効化する。

`DocumentChatSession` は `initialize()` / `sendMessage()` のほか、`getChatHistory()`（S-04 の Q&A コンテキスト渡しなどに利用するチャット履歴の取得）と `clear()`（新規書類読み込み時のセッションリセット）を提供する。MF-02・MF-03・MF-06a・MF-07 の推論は `DocumentReviewViewModel` 内部の `Mutex` により相互排他で実行され、複数の推論が同時に発行されないことが保証される。

### 入力欄の非表示（チャット履歴上限到達時）

チャット履歴が上限に達すると、UI 実装上は入力欄が無効化されるのではなく行自体が描画されなくなる（`ChatSection` は `chatLimitReached = true` の場合に入力 `Row` 全体を条件分岐でスキップする）。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/multilingual-translation]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/review-screen]]
