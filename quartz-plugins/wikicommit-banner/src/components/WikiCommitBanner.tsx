import type {
  QuartzComponent,
  QuartzComponentConstructor,
  QuartzComponentProps,
} from "@quartz-community/types"
import { i18n } from "../i18n"
import style from "./styles/wikicommit-banner.scss"

const WikiCommitBanner: QuartzComponent = ({ fileData, cfg }: QuartzComponentProps) => {
  const frontmatter = fileData.frontmatter
  // WikiCommitSources / WikiCommitJsonLD と同様、removed ページには何も表示しない
  // （報告リンクを常時表示にしたことで、除外しないと存在しないページへのリンクを
  // 出してしまう。Issue #245）。
  if (frontmatter?.status === "removed") return null

  const reviewStatus = (frontmatter?.review_status as string | undefined) ?? "pending"
  const isPending = reviewStatus === "pending"

  const t = i18n(cfg?.locale ?? "en-US").components.wikicommitBanner

  const generatedAt = (frontmatter?.generated_at as string | undefined) ?? t.unknown
  const generatedBy = (frontmatter?.generated_by as string | undefined) ?? t.unknown

  // Issue報告リンクは reviewed 後もページの誤りを指摘できるよう review_status に
  // 関係なく常時表示する（Issue #245）。後レビュー用 PR の URL は frontmatter に
  // 格納しない（動的に生成しない）。ユーザーが GitHub の PR ページで確認する（Phase 2 スコープ外）。
  const repo = process.env.GITHUB_REPOSITORY
  const title = (frontmatter?.title as string | undefined) ?? ""
  const type =
    typeof frontmatter?.type === "string" ? frontmatter.type.replace(/^schema:/, "") : undefined
  const lang = frontmatter?.lang as string | undefined
  const pageUrl =
    cfg?.baseUrl && fileData.slug
      ? `https://${cfg.baseUrl.replace(/\/+$/, "")}/${fileData.slug}`
      : undefined

  const reportTitle = type ? `${t.reportTitlePrefix} ${type}: ${title}` : `${t.reportTitlePrefix} ${title}`
  const reportBody = [
    pageUrl ? `${t.reportBodyPage} ${pageUrl}` : null,
    lang ? `${t.reportBodyLanguage} ${lang}` : null,
  ]
    .filter((line): line is string => line !== null)
    .join("\n")

  const reportUrl = repo
    ? `https://github.com/${repo}/issues/new?template=report.md&title=${encodeURIComponent(reportTitle)}${
        reportBody ? `&body=${encodeURIComponent(reportBody)}` : ""
      }`
    : "#"

  if (!isPending) {
    return (
      <div class="wikicommit-banner__report">
        <a href={reportUrl} class="wikicommit-banner__link">
          {t.reportLink}
        </a>
      </div>
    )
  }

  return (
    <div class="wikicommit-banner wikicommit-banner--pending">
      <span class="wikicommit-banner__icon">⚠️</span>
      <div class="wikicommit-banner__body">
        <strong>{t.title}</strong>
        <p>{t.body}</p>
        <p>
          {t.generatedAt} {generatedAt}&nbsp;&nbsp;{t.generatedBy} {generatedBy}
        </p>
        <div class="wikicommit-banner__actions">
          <a href={reportUrl} class="wikicommit-banner__link">
            {t.reportLink}
          </a>
        </div>
      </div>
    </div>
  )
}

WikiCommitBanner.css = style

export default (() => WikiCommitBanner) satisfies QuartzComponentConstructor
