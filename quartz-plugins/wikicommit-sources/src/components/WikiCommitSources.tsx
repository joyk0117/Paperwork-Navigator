import type {
  FullSlug,
  QuartzComponent,
  QuartzComponentConstructor,
  QuartzComponentProps,
} from "@quartz-community/types"
// Import from the "./path" subpath (not the package root) to avoid pulling in
// the unrelated jsx.ts module, whose hast-util-to-jsx-runtime dependency is
// missing from @quartz-community/utils's own package.json.
import { resolveRelative } from "@quartz-community/utils/path"
import { i18n } from "../i18n"
import style from "./styles/wikicommit-sources.scss"

interface SourceEntry {
  type?: string
  path?: string
  url?: string
  author?: string
  created_at?: unknown
}

interface ResolvedSources {
  sources: SourceEntry[]
  inherited: boolean
  parentTitle?: string
  parentHref?: string
}

function asSourceList(value: unknown): SourceEntry[] {
  return Array.isArray(value) ? (value as SourceEntry[]) : []
}

// YAML parses an unquoted YYYY-MM-DD scalar (e.g. `created_at: 2026-06-21`) as
// a Date, not a string, so `created_at` must be coerced defensively here.
function formatDate(value: unknown): string | undefined {
  if (value instanceof Date) return value.toISOString().slice(0, 10)
  if (typeof value === "string" && value) return value
  return undefined
}

// The Quartz build mirrors .wikicommit/wiki/<lang>/<Type>/<slug>.md 1:1 into
// content/<lang>/<Type>/<slug>.md (see scripts/convert_wikilinks.py), so a
// translated_from path maps to a Quartz relativePath by dropping the
// .wikicommit/wiki/ prefix. Matching must use relativePath (the path relative
// to the content root, set from vfile.data.relativePath), not slug or
// filePath: Quartz lowercases slug (e.g. "ja/person/yamada-taro") but
// relativePath preserves the original path casing (e.g.
// "ja/Person/yamada-taro.md") like translated_from does, while filePath on
// component props is the absolute filesystem path of the source file.
function translatedFromToRelativePath(translatedFrom: string): string {
  return translatedFrom.replace(/^\.wikicommit\/wiki\//, "")
}

// Kept in sync by hand with resolve_page_sources() in
// .wikicommit/scripts/convert_wikilinks.py (Issue #212): that script builds
// content/<lang>/sources.md at Quartz build time (Python), this component
// renders the per-page sources box at render time (TSX/Quartz), and the two
// run in different languages/build stages so the inheritance rule can't be
// shared as one function. If you change this, change that too.
function resolveSources(
  currentSlug: FullSlug,
  frontmatter: Record<string, unknown> | undefined,
  allFiles: QuartzComponentProps["allFiles"],
): ResolvedSources {
  const ownSources = asSourceList(frontmatter?.sources)
  if (ownSources.length > 0) return { sources: ownSources, inherited: false }

  const translatedFrom = frontmatter?.translated_from
  if (typeof translatedFrom !== "string" || translatedFrom.length === 0) {
    return { sources: [], inherited: false }
  }

  const parentRelativePath = translatedFromToRelativePath(translatedFrom)
  const parent = allFiles.find((f) => f.relativePath === parentRelativePath)
  if (parent?.frontmatter?.status === "removed") return { sources: [], inherited: false }
  const parentSources = asSourceList(parent?.frontmatter?.sources)
  if (parentSources.length === 0) return { sources: [], inherited: false }

  return {
    sources: parentSources,
    inherited: true,
    parentTitle: (parent?.frontmatter?.title as string | undefined) ?? parentRelativePath,
    parentHref: parent?.slug
      ? resolveRelative(currentSlug, parent.slug as FullSlug)
      : undefined,
  }
}

// Kept in sync by hand with file_href() in
// .wikicommit/scripts/convert_wikilinks.py (Issue #212), including the
// `main` branch assumption. See resolveSources() above for why this can't
// be a shared function.
function fileHref(path: string): string | undefined {
  const repo = process.env.GITHUB_REPOSITORY
  if (!repo) return undefined
  return `https://github.com/${repo}/blob/main/${path
    .split("/")
    .map(encodeURIComponent)
    .join("/")}`
}

function renderSource(source: SourceEntry, index: number, t: ReturnType<typeof i18n>["components"]["wikicommitSources"]) {
  switch (source.type) {
    case "file": {
      if (typeof source.path !== "string" || !source.path) return null
      const href = fileHref(source.path)
      return (
        <li key={index} class="wikicommit-sources__item">
          {href ? (
            <a href={href} class="wikicommit-sources__link" target="_blank" rel="noopener noreferrer">
              {source.path}
            </a>
          ) : (
            <span class="wikicommit-sources__text">{source.path}</span>
          )}
        </li>
      )
    }
    case "url":
    case "wikicommit": {
      if (typeof source.url !== "string" || !source.url) return null
      return (
        <li key={index} class="wikicommit-sources__item">
          <a href={source.url} class="wikicommit-sources__link" target="_blank" rel="noopener noreferrer">
            {source.url}
          </a>
        </li>
      )
    }
    case "manual": {
      const author = source.author ?? t.unknownAuthor
      const createdAt = formatDate(source.created_at)
      return (
        <li key={index} class="wikicommit-sources__item">
          <span class="wikicommit-sources__text">
            {t.addedBy} {author}
            {createdAt ? ` (${createdAt})` : ""}
          </span>
        </li>
      )
    }
    default:
      return null
  }
}

const WikiCommitSources: QuartzComponent = ({ fileData, allFiles, cfg }: QuartzComponentProps) => {
  const frontmatter = fileData.frontmatter
  if (frontmatter?.status === "removed") return null
  const currentSlug = fileData.slug as FullSlug
  const { sources, inherited, parentTitle, parentHref } = resolveSources(
    currentSlug,
    frontmatter,
    allFiles,
  )

  if (sources.length === 0) return null

  const t = i18n(cfg?.locale ?? "en-US").components.wikicommitSources
  const items = sources
    .map((source, index) => renderSource(source, index, t))
    .filter((item) => item !== null)

  if (items.length === 0) return null

  return (
    <div class="wikicommit-sources">
      <h3 class="wikicommit-sources__title">{t.title}</h3>
      {inherited && (
        <p class="wikicommit-sources__inherited">
          {t.inheritedFrom}{" "}
          {parentHref ? <a href={parentHref}>{parentTitle}</a> : parentTitle}
        </p>
      )}
      <ul class="wikicommit-sources__list">{items}</ul>
    </div>
  )
}

WikiCommitSources.css = style

export default (() => WikiCommitSources) satisfies QuartzComponentConstructor
