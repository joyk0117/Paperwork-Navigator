// Fork of github:quartz-community/explorer's Explorer.tsx. Neither the
// explorer nor the breadcrumbs plugin offers a YAML-config option to hide a
// path segment (Quartz only exposes filterFn/mapFn-style customization via a
// quartz.ts TypeScript override, which WikiCommit deliberately doesn't use —
// see quartz.config.yaml's own comment on being the sole config surface), so
// this fork exists to fold away the .wikicommit/wiki/<lang>/ folder level
// that would otherwise have to be expanded before any Type directory becomes
// visible (Issue #209). Everything below is intentionally kept identical to
// upstream — including ExplorerOptions and defaultOptions, which are NOT
// used to carry the fold behavior (composing them via filterFn/mapFn can't
// express "promote this folder's children up a level"; see foldLang.ts) —
// except the afterDOMLoaded script, swapped for the wikicommit-explorer.inline.ts
// fork of explorer.inline.ts.
import type {
  QuartzComponent,
  QuartzComponentConstructor,
  QuartzComponentProps,
} from "@quartz-community/types"
import OverflowListFactory from "./OverflowList"
import { classNames } from "../util/lang"
import { i18n } from "../i18n"
import style from "./styles/wikicommit-explorer.scss"
// @ts-expect-error - Inline script loaded as text by esbuild plugin
import script from "./scripts/wikicommit-explorer.inline.ts"

interface FileTrieNode {
  slugSegment?: string
  slugSegments?: string[]
  displayName?: string
  isFolder: boolean
  data: Record<string, unknown> | null
  children: FileTrieNode[]
}

export interface ExplorerOptions {
  title?: string
  folderDefaultState: "collapsed" | "open"
  folderClickBehavior: "collapse" | "link"
  useSavedState: boolean
  sortFn?: (a: FileTrieNode, b: FileTrieNode) => number
  filterFn?: (node: FileTrieNode) => boolean
  mapFn?: (node: FileTrieNode) => FileTrieNode
  order?: Array<"filter" | "map" | "sort">
}

const defaultOptions: ExplorerOptions = {
  folderDefaultState: "collapsed",
  folderClickBehavior: "link",
  useSavedState: true,
  mapFn: (node: FileTrieNode) => {
    return node
  },
  sortFn: (a: FileTrieNode, b: FileTrieNode) => {
    if ((!a.isFolder && !b.isFolder) || (a.isFolder && b.isFolder)) {
      return (a.displayName || "").localeCompare(b.displayName || "", undefined, {
        numeric: true,
        sensitivity: "base",
      })
    }

    if (!a.isFolder && b.isFolder) {
      return 1
    }
    return -1
  },
  filterFn: (node: FileTrieNode) => node.slugSegment !== "tags",
  order: ["filter", "map", "sort"],
}

let numExplorers = 0

function concatenateResources(...resources: (string | undefined)[]): string {
  return resources.filter((r): r is string => !!r).join("\n")
}

export default ((userOpts?: Partial<ExplorerOptions>) => {
  const opts: ExplorerOptions = { ...defaultOptions, ...userOpts }
  const { OverflowList, overflowListAfterDOMLoaded } = OverflowListFactory()

  const WikiCommitExplorer: QuartzComponent = (props: QuartzComponentProps) => {
    const { cfg } = props
    const displayClass = (props as { displayClass?: "mobile-only" | "desktop-only" }).displayClass
    const id = `explorer-${numExplorers++}`
    const locale = cfg?.locale ?? "en-US"

    const title = opts.title ?? i18n(locale).components.explorer.title

    return (
      <div
        class={classNames(displayClass, "explorer", "nav-files-container")}
        data-behavior={opts.folderClickBehavior}
        data-collapsed={opts.folderDefaultState}
        data-savestate={opts.useSavedState}
        data-data-fns={JSON.stringify({
          order: opts.order,
          sortFn: opts.sortFn?.toString(),
          filterFn: opts.filterFn?.toString(),
          mapFn: opts.mapFn?.toString(),
        })}
      >
        <button
          type="button"
          class="explorer-toggle mobile-explorer hide-until-loaded"
          data-mobile={true}
          aria-controls={id}
          aria-label={i18n(cfg?.locale ?? "en-US").components.explorer.title}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="24"
            height="24"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="lucide-menu"
          >
            <line x1="4" x2="20" y1="12" y2="12" />
            <line x1="4" x2="20" y1="6" y2="6" />
            <line x1="4" x2="20" y1="18" y2="18" />
          </svg>
        </button>
        <button
          type="button"
          class="title-button explorer-toggle desktop-explorer"
          data-mobile={false}
          aria-expanded={true}
        >
          <h2>{title}</h2>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="14"
            height="14"
            viewBox="5 8 14 8"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="fold"
          >
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </button>
        <div id={id} class="explorer-content" aria-expanded={false} role="group">
          <OverflowList class="explorer-ul" />
        </div>
        <template id="template-file">
          <li>
            <a href="#" class="nav-file-title tree-item-self"></a>
          </li>
        </template>
        <template id="template-folder">
          <li>
            <div class="folder-container nav-folder-title tree-item-self">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="12"
                height="12"
                viewBox="5 8 14 8"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="folder-icon nav-folder-collapse-indicator collapse-icon"
              >
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
              <div>
                <button class="folder-button">
                  <span class="folder-title"></span>
                </button>
              </div>
            </div>
            <div class="folder-outer">
              <ul class="content tree-item-children"></ul>
            </div>
          </li>
        </template>
      </div>
    )
  }

  WikiCommitExplorer.css = style
  WikiCommitExplorer.afterDOMLoaded = concatenateResources(script, overflowListAfterDOMLoaded)
  return WikiCommitExplorer
}) satisfies QuartzComponentConstructor
