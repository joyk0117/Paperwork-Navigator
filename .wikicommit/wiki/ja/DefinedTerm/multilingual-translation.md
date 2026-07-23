---
title: "多言語翻訳"
type: "schema:DefinedTerm"
lang: ja
description: "ReviewResult の原文言語フィールドを Gemma 4 でユーザー選択言語に翻訳する処理（Translator）"
termCode: "MF-03"
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

多言語翻訳（MF-03）は、`Translator` クラスがユーザーの「翻訳する」タップをトリガーに `ReviewResult` の原文言語フィールドを翻訳し、`translation` フィールドを埋めた `ReviewResult` を返す処理である。翻訳対象は `summary_ja`、`action_items[].description_ja`、`required_items[].name_ja` / `note_ja`、`warning.description_ja`、`deadline.note_ja` であり、`PiiSpan`（スパン位置が変わるため）と `id` 系フィールドは翻訳しない。

## Usage

翻訳先言語は 15 言語（`ja` / `en` / `zh` / `ko` / `es` / `fr` / `de` / `it` / `pt` / `ru` / `pl` / `nl` / `ar` / `th` / `tr`）に対応し、UI 表示用の言語コードと LLM プロンプト内表記（例: `zh` → "Chinese (Simplified)"）は別々に管理される。出力形式は MF-02 と同様に行形式（5フィールド）を採用する。

翻訳バーは「未翻訳」（言語選択ドロップダウン + 翻訳ボタン）→「翻訳中」（`isTranslating = true`、ドロップダウン・ボタン無効化）→「翻訳済み」（`{言語} ✓` + 再翻訳ドロップダウン）と状態遷移する。翻訳完了後はレビュー画面（S-02）が原文・翻訳の2列表示に切り替わり、[[DefinedTerm/document-chat]] のセッションが翻訳言語で再初期化される（履歴クリア、スナックバー通知）。翻訳失敗時は `isTranslating` を false に戻し、翻訳バーに「翻訳に失敗しました。再試行してください」を表示する。

### Translation データモデル

`reviewResult.copy(translation = ...)` により MF-03 完了後に付与される `Translation` は `language`（翻訳先言語コード）/ `summary` / `deadlineNote` / `actionItems`（`TranslatedActionItem`: id・description）/ `requiredItems`（`TranslatedRequiredItem`: id・name・note）/ `warning`（`TranslatedWarning`: description、`Warning` に対応する1件のみ）で構成される。`warning.severity`（high/medium/low）は多言語化不要のため翻訳対象から除外される。

### 事前マスクとプロンプト設計

翻訳対象フィールドを Gemma 4 に渡す前に `PiiMasker.mask()` による事前マスクが適用され、`[Applicant name]` 等の角括弧のプレースホルダーは不透明な文字列としてそのまま出力に保持するよう System Prompt で明示的に指示される。これにより翻訳結果は自動的にマスク済みの状態になる。出力形式は MF-02 と同様の行形式（`SUMMARY` / `DEADLINE_NOTE` / `ACTION_ITEMS` / `REQUIRED_ITEMS` / `WARNING` の5フィールド）を採用し、パースエラー時はエラー情報を追加したうえで再試行する。

### 翻訳先言語ドロップダウン

翻訳先言語のドロップダウン（`TranslationBar` / `DropdownMenuLanguageSelector`）は、対応 15 言語のうち原文言語（`sourceLanguage`）を候補から除外して表示する。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
- [[DefinedTerm/document-chat]]
- [[DefinedTerm/review-screen]]
