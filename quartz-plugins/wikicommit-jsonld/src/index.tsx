import type { QuartzTransformerPlugin } from "@quartz-community/types"

type Frontmatter = Record<string, unknown>

function generateJsonLD(pageData: { frontmatter?: Frontmatter }) {
  const frontmatter = pageData.frontmatter
  if (!frontmatter?.type) return null
  if (frontmatter.status === "removed") return null

  const typeStr = String(frontmatter.type).replace(/^schema:/, "")
  // custom/ types have no Schema.org equivalent; omit rather than emit invalid JSON-LD
  if (typeStr.includes("/")) return null

  const jsonld: Record<string, unknown> = {
    "@context": "https://schema.org",
    "@type": typeStr,
  }

  if (frontmatter.title) jsonld.name = frontmatter.title
  if (frontmatter.description) jsonld.description = frontmatter.description
  if (frontmatter.tags) jsonld.keywords = frontmatter.tags

  // wikidata → sameAs conversion: "wd:Q12345" → "https://www.wikidata.org/wiki/Q12345"
  const sameAs: string[] = Array.isArray(frontmatter.sameAs)
    ? frontmatter.sameAs.filter((x): x is string => typeof x === "string")
    : []
  if (frontmatter.wikidata) {
    const qid = String(frontmatter.wikidata).replace(/^wd:/, "")
    sameAs.push(`https://www.wikidata.org/wiki/${qid}`)
  }
  if (sameAs.length > 0) jsonld.sameAs = sameAs

  if (typeStr === "Person") {
    if (frontmatter.affiliation && !String(frontmatter.affiliation).startsWith("[["))
      jsonld.affiliation = frontmatter.affiliation
    if (frontmatter.jobTitle) jsonld.jobTitle = frontmatter.jobTitle
    if (frontmatter.birthDate) jsonld.birthDate = frontmatter.birthDate
  }

  if (typeStr === "Event") {
    if (frontmatter.startDate) jsonld.startDate = frontmatter.startDate
    if (frontmatter.endDate) jsonld.endDate = frontmatter.endDate
    if (frontmatter.location) jsonld.location = frontmatter.location
  }

  // Escape </script> sequences to prevent script-tag breakout XSS in inline JSON-LD
  const jsonString = JSON.stringify(jsonld, null, 2)
    .replace(/</g, "\\u003c")
    .replace(/>/g, "\\u003e")
    .replace(/&/g, "\\u0026")

  // JSX (not a direct h() call) so tsup/esbuild's automatic JSX runtime
  // inlines the vnode-creation helper, keeping dist/index.js free of a
  // bare `import ... from "preact"` that this standalone plugin directory
  // (outside any node_modules tree) cannot resolve at runtime (Issue #186).
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: jsonString }}
    />
  )
}

export const WikiCommitJsonLD: QuartzTransformerPlugin = () => {
  return {
    name: "WikiCommitJsonLD",
    // No-op: Quartz's transformer category validation (config-loader.ts validateCategory)
    // only recognizes textTransform/markdownPlugins/htmlPlugins as evidence of a transformer,
    // so a plugin that only implements externalResources gets skipped as invalid. This
    // satisfies that check without altering the HTML AST; the actual injection happens
    // via externalResources below (see Issue #62).
    htmlPlugins() {
      return []
    },
    externalResources() {
      return {
        // additionalHead accepts (pageData) => JSX.Element functions;
        // Head.tsx calls each function with per-page fileData at render time.
        additionalHead: [generateJsonLD as (pageData: unknown) => unknown],
      }
    },
  }
}

export default WikiCommitJsonLD
