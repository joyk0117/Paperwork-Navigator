---
title: "クイックアクション"
type: "schema:DefinedTerm"
lang: ja
description: "レビュー画面（S-02）で context_label に応じて表示される、カレンダー追加・地図表示・電話・メール・URL起動などのIntentベースのショートカット操作"
termCode: ""
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
sources:
  - type: file
    path: 'docs/extraction-architecture-spec.md'
    hash: sha256:69627bb2df6e428d71a0b8a314b7853995a51c3099f9a3961a8faabff9797d8d
  - type: file
    path: 'docs/extraction-architecture-spec_ja.md'
    hash: sha256:0f0a3a2cf7dab13cb02ca0c0621acc8ced7666550be96ab3243f6a0e4ad61bb6
  - type: file
    path: 'Android/src/app/src/main/java/io/github/joyk0117/paperworknavigator/customtasks/documentreview/DocumentReviewScreen.kt'
    hash: sha256:7d758698f626bed2dec83af782ccebac73e084b0cc2a7ecf4f167f711f1acfac
review_status: pending
generated_at: "2026-07-23"
generated_by: "claude-sonnet-5"
---

クイックアクションは、[[SoftwareApplication/paperwork-navigator]] のレビュー画面（S-02）で `context_label` に応じて表示される Android Intent ベースのショートカット操作であり、[[DefinedTerm/structured-field-extraction]]（MF-02）が導出した `context_label` からアクション種別が静的に決まる。

## Usage

| `context_label` | Intent | 備考 |
|--------------|--------|------|
| `deadline` | `CalendarContract.Events.INSERT` | イベント名: `{doc_name} - 提出期限` |
| `event_date` | `CalendarContract.Events.INSERT` | イベント名: `{doc_name} - {raw_text}` |
| `issuer_address` / `other_address` | `geo:0,0?q={raw_text}` | 地図表示 |
| `issuer_phone` / `other_phone` | `tel:{raw_text}` | 電話発信 |
| `issuer_email` / `other_email` | `mailto:{raw_text}` | メール送信 |
| `url` | `ACTION_VIEW` | |
| `tracking_number` | `ACTION_VIEW` | `metadata.carrier` から追跡 URL を構築 |
| `flight_number` | `ACTION_VIEW` | `metadata.airlineCode` からフライト追跡 URL を構築 |

`applicant_*`（申請者自身の情報）については、それがユーザー自身の情報であるため電話・地図・メールへのクイックアクションを表示しない方針である。Intent を処理できるアプリが端末にない場合は `ActivityNotFoundException` をキャッチしてクラッシュを防ぎ、Intent 生成は `util/DocumentIntentBuilder.kt` に集約されている。

### IntentIconButton

クイックアクションの実行ボタンは `IntentIconButton`（絵文字1文字を表示する共通コンポーネント）で実装され、最小タップ領域は幅32dp・高さ48dpを確保する。対応するアプリが端末にない場合は `ActivityNotFoundException` をキャッチして Toast でエラーメッセージを表示し、アプリのクラッシュを防ぐ。

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
