/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 * Parser input is a host-owned byte view. The book is never treated as executable code.
 */
import './quickjs-codecs'
import { initKf8File, initMobiFile } from '@lingo-reader/mobi-parser-src'
import {
  beginResources,
  clearResources,
  readResourceText,
  resourceBytes as readResourceBytes,
  resourceIdFromReference,
  resourceMetadata as findResourceMetadata,
} from './resource-channel'
import { preflightMobi, type MobiKind } from './mobi-preflight'
import {
  MAX_AUTHORS,
  MAX_CONTENT_ID_CHARS,
  MAX_EXPANDED_TEXT_BYTES,
  MAX_FRAGMENT_CHARS,
  MAX_HREF_CHARS,
  MAX_INPUT_BYTES,
  MAX_LINKS,
  MAX_METADATA_CHARS,
  MAX_RESOURCES,
  MAX_SECTIONS,
  MAX_SECTION_TEXT_BYTES,
  MAX_TITLE_CHARS,
  MAX_TOC_DEPTH,
  MAX_TOC_ITEMS,
} from './mobi-limits'
import { utf8ByteLength } from './quickjs-codecs'

interface ResolvedTarget {
  contentId: string
  fragment: string | null
}

interface ParserApi {
  getMetadata(): Record<string, unknown>
  getSpine(): Array<{ id: string }>
  getToc(): Array<Record<string, unknown>>
  loadChapter(id: string): { html: string, css: Array<{ id: string, href: string }> } | undefined
  resolveHref(href: string): { id: string, selector: string } | undefined
  getCoverImage?: () => string
  destroy(): void
}

interface CatalogSection {
  contentId: string
  title: string
  hasAuthoredTitle: boolean
  linear: boolean
}

interface TocEntry {
  title: string
  hasAuthoredTitle: boolean
  contentId: string
  fragment: string | null
  children: TocEntry[]
}

const ATTRIBUTE_NAME = /(?:^|\s)([a-z_:][-a-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))/i
const HTML_ENTITY = /&(?:#([0-9]{1,8})|#x([0-9a-f]{1,8})|([a-z][a-z0-9]{1,31}));?/gi
const NAMED_ENTITIES: Record<string, string> = {
  amp: '&',
  apos: "'",
  gt: '>',
  lt: '<',
  nbsp: '\u00A0',
  quot: '"',
}

let activeParser: ParserApi | undefined
let activeSpineIds = new Set<string>()
let activeSections: CatalogSection[] = []
let returnedSectionBytes = new Map<string, number>()
let totalExpandedTextBytes = 0

function throwPublication(code: string): never {
  throw new Error(`TSUYOMI_PUB_ERROR:${code}`)
}

function decodeHtmlAttribute(value: string): string {
  return value.replace(HTML_ENTITY, (source, decimal: string | undefined, hexadecimal: string | undefined, named: string | undefined) => {
    if (decimal !== undefined || hexadecimal !== undefined) {
      const number = Number.parseInt(decimal ?? hexadecimal, decimal === undefined ? 16 : 10)
      if (!Number.isSafeInteger(number) || number <= 0 || number > 0x10FFFF || (number >= 0xD800 && number <= 0xDFFF)) return '\uFFFD'
      return String.fromCodePoint(number)
    }
    return NAMED_ENTITIES[named.toLowerCase()] ?? source
  })
}

function attributeValue(tag: string, name: string): string | undefined {
  const match = ATTRIBUTE_NAME.exec(tag)
  if (match === null || match[1].toLowerCase() !== name.toLowerCase()) {
    const tags = tag.match(/(?:^|\s)[a-z_:][-a-z0-9_:.]*\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'=<>`]+)/gi) ?? []
    for (const candidate of tags) {
      const parsed = ATTRIBUTE_NAME.exec(candidate.trim())
      if (parsed !== null && parsed[1].toLowerCase() === name.toLowerCase()) {
        return decodeHtmlAttribute(parsed[2] ?? parsed[3] ?? parsed[4] ?? '')
      }
    }
    return undefined
  }
  return decodeHtmlAttribute(match[2] ?? match[3] ?? match[4] ?? '')
}

function selectorFragment(selector: string): string | null {
  const match = /^\[(id|aid)=("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')\]$/.exec(selector)
  if (match === null) return null
  const quoted = match[2]
  let value: string
  if (quoted.startsWith('"')) {
    try {
      value = JSON.parse(quoted) as string
    } catch {
      return null
    }
  } else {
    value = quoted.slice(1, -1).replace(/\\(['\\])/g, '$1')
  }
  return match[1] === 'aid' ? `kindle-aid:${value}` : value
}

function resolveTarget(parser: ParserApi, href: string, currentContentId?: string): ResolvedTarget | null {
  if (href.length > MAX_HREF_CHARS) throwPublication('SECTION_LIMIT')
  if (href.startsWith('#') && currentContentId !== undefined) {
    let fragment: string
    try {
      fragment = decodeURIComponent(href.slice(1))
    } catch {
      return null
    }
    if (fragment.length > MAX_FRAGMENT_CHARS) throwPublication('SECTION_LIMIT')
    return fragment ? { contentId: currentContentId, fragment } : null
  }
  if (!href.startsWith('filepos:') && !href.startsWith('kindle:pos:')) return null
  const resolved = parser.resolveHref(href)
  if (resolved === undefined) return null
  const contentId = String(resolved.id)
  if (!activeSpineIds.has(contentId)) return null
  if (contentId.length > MAX_CONTENT_ID_CHARS) throwPublication('SECTION_LIMIT')
  const fragment = selectorFragment(String(resolved.selector))
  if (fragment === null || fragment.length === 0) return null
  if (fragment.length > MAX_FRAGMENT_CHARS) throwPublication('SECTION_LIMIT')
  return { contentId, fragment }
}

function plainMetadata(parser: ParserApi): { title: string, authors: string[], language: string | null, identifier: string | null } {
  const metadata = parser.getMetadata()
  const rawTitle = metadata.title
  if (typeof rawTitle === 'string' && rawTitle.length > MAX_METADATA_CHARS) throwPublication('TEXT_LIMIT')
  const title = typeof rawTitle === 'string' ? rawTitle.trim().replace(/\s+/gu, ' ') : ''
  if (title.length > MAX_TITLE_CHARS) throwPublication('TEXT_LIMIT')
  const rawAuthors = metadata.author
  const authorValues = typeof rawAuthors === 'string'
    ? [rawAuthors]
    : Array.isArray(rawAuthors) && rawAuthors.every((item) => typeof item === 'string')
      ? rawAuthors as string[]
      : []
  if (authorValues.length > MAX_AUTHORS) throwPublication('SECTION_LIMIT')
  const authors: string[] = []
  for (const author of authorValues) {
    if (author.length > MAX_TITLE_CHARS) throwPublication('TEXT_LIMIT')
    const normalized = author.trim()
    if (normalized.length > 0) authors.push(normalized)
  }
  const rawLanguage = metadata.language
  const rawIdentifier = metadata.identifier
  if (typeof rawLanguage === 'string' && rawLanguage.length > MAX_METADATA_CHARS) throwPublication('TEXT_LIMIT')
  if ((typeof rawIdentifier === 'string' && rawIdentifier.length > MAX_METADATA_CHARS) ||
      (typeof rawIdentifier === 'number' && String(rawIdentifier).length > MAX_METADATA_CHARS)) {
    throwPublication('TEXT_LIMIT')
  }
  return {
    title: title || 'Untitled',
    authors,
    language: typeof rawLanguage === 'string' && rawLanguage.length > 0 ? rawLanguage : null,
    identifier: typeof rawIdentifier === 'string' || typeof rawIdentifier === 'number' ? String(rawIdentifier) : null,
  }
}

function mapParserFailure(error: unknown, fallback: string): never {
  const message = error instanceof Error ? error.message : ''
  if (message.startsWith('TSUYOMI_PUB_ERROR:')) throw error
  if (message === 'EXPANDED_TEXT_LIMIT') throwPublication('TEXT_LIMIT')
  if (message === 'RESOURCE_LIMIT' || message === 'INVALID_RESOURCE_MIME') throwPublication('RESOURCE_LIMIT')
  if (message === 'UNSUPPORTED_COMPRESSION') throwPublication('UNSUPPORTED_COMPRESSION')
  if (message === 'MISSING_RESOURCE' || message === 'MISSING_CSS_RESOURCE') throwPublication('MISSING_RESOURCE')
  if (message === 'MALFORMED_XML') throwPublication('MALFORMED_XML')
  if (message === 'INVALID_TOC') throwPublication('MALFORMED_TOC')
  throwPublication(fallback)
}

function mapPreflightFailure(code: string): never {
  if (code === 'protected-content') throwPublication('PROTECTED_CONTENT')
  if (code === 'unsupported-compression') throwPublication('UNSUPPORTED_COMPRESSION')
  if (code === 'print-replica') throwPublication('PRINT_REPLICA')
  if (code === 'input-too-large') throwPublication('INPUT_LIMIT')
  if (code === 'unsupported-format') throwPublication('UNSUPPORTED_FORMAT')
  if (code === 'expanded-text-limit') throwPublication('TEXT_LIMIT')
  throwPublication('MALFORMED_INPUT')
}

function plainToc(
  parser: ParserApi,
  input: Array<Record<string, unknown>>,
  count: { value: number },
  depth: number,
): TocEntry[] {
  if (depth > MAX_TOC_DEPTH) throwPublication('SECTION_LIMIT')
  const output: TocEntry[] = []
  for (const item of input) {
    if (++count.value > MAX_TOC_ITEMS) throwPublication('SECTION_LIMIT')
    const label = item.label
    const href = item.href
    const children = item.children
    if (typeof label !== 'string' || typeof href !== 'string' || (children !== undefined && !Array.isArray(children))) {
      throwPublication('MALFORMED_TOC')
    }
    if (label.length > MAX_TITLE_CHARS) throwPublication('TEXT_LIMIT')
    const title = label.trim()
    const nested = plainToc(parser, (children ?? []) as Array<Record<string, unknown>>, count, depth + 1)
    const target = resolveTarget(parser, href)
    if (target === null) {
      if (nested.length > 0) output.push({ ...nested[0], title, hasAuthoredTitle: title.length > 0, children: nested })
      continue
    }
    output.push({ title, hasAuthoredTitle: title.length > 0, ...target, children: nested })
  }
  return output
}

function addTocTitles(entries: TocEntry[], titles: Map<string, string>): void {
  for (const entry of entries) {
    if (!titles.has(entry.contentId) && entry.hasAuthoredTitle) titles.set(entry.contentId, entry.title)
    addTocTitles(entry.children, titles)
  }
}

function chapterLinks(parser: ParserApi, html: string, currentContentId: string): Array<{
  href: string
  contentId: string | null
  fragment: string | null
}> {
  const output: Array<{ href: string, contentId: string | null, fragment: string | null }> = []
  const tags = html.match(/<a\b(?:[^"'<>]|"[^"]*"|'[^']*')*>/gi) ?? []
  for (const tag of tags) {
    const href = attributeValue(tag, 'href')
    if (href === undefined) continue
    if (output.length >= MAX_LINKS) throwPublication('SECTION_LIMIT')
    const target = resolveTarget(parser, href, currentContentId)
    output.push({ href, contentId: target?.contentId ?? null, fragment: target?.fragment ?? null })
  }
  return output
}

function installKf8AnchorIds(html: string): string {
  return html.replace(/<([a-z][^\s/>]*)(?:[^"'<>]|"[^"]*"|'[^']*')*>/gi, (tag: string, name: string) => {
    const aid = attributeValue(tag, 'aid')
    if (aid === undefined) return tag
    const fragment = `kindle-aid:${aid}`
    const authoredId = attributeValue(tag, 'id')
    if (authoredId === fragment) return tag
    if (authoredId !== undefined && /^(?:head|title|meta|link|style|script|iframe|object|embed|canvas|form|audio|video|input|button)$/i.test(name)) return tag
    const escaped = fragment.replace(/[&"<>]/g, character => `&#${character.charCodeAt(0)};`)
    const selfClosing = /\/\s*>$/.test(tag)
    if (authoredId !== undefined) {
      const alias = `<span id="${escaped}"></span>`
      // Inline aliases share the existing element's block and start offset; they add no visible text.
      const isVoid = /^(?:area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)$/i.test(name)
      return isVoid || selfClosing ? alias + tag : tag + alias
    }
    const insertion = selfClosing ? tag.lastIndexOf('/') : tag.lastIndexOf('>')
    return `${tag.slice(0, insertion)} id="${escaped}"${tag.slice(insertion)}`
  })
}

function collectResourceIds(html: string): string[] {
  const resourceIds = new Set<string>()
  const imageTags = html.match(/<img\b(?:[^"'<>]|"[^"]*"|'[^']*')*>/gi) ?? []
  for (const tag of imageTags) {
    const source = attributeValue(tag, 'src')
    if (source === undefined) continue
    const match = /^tsuyomi-resource:\/\/([A-Za-z0-9_-]{1,32})$/.exec(source)
    if (match === null) continue
    resourceIds.add(match[1])
    if (resourceIds.size > MAX_RESOURCES) throwPublication('RESOURCE_LIMIT')
  }
  return [...resourceIds]
}

function inspectPublication(bytes: unknown): unknown {
  activeParser?.destroy()
  activeParser = undefined
  clearResources()
  beginResources()
  activeSpineIds = new Set<string>()
  activeSections = []
  returnedSectionBytes = new Map<string, number>()
  totalExpandedTextBytes = 0

  if (!(bytes instanceof Uint8Array)) throwPublication('MALFORMED_INPUT')
  if (bytes.byteLength > MAX_INPUT_BYTES) throwPublication('INPUT_LIMIT')
  const preflight = preflightMobi(bytes)
  if (!preflight.accepted) mapPreflightFailure(preflight.code)

  const kind: MobiKind = preflight.kind
  let parser: ParserApi | undefined
  try {
    parser = (kind === 'mobi7' ? initMobiFile(bytes) : initKf8File(bytes)) as unknown as ParserApi
    const spine = parser.getSpine()
    if (!Array.isArray(spine) || spine.length === 0) throwPublication('MALFORMED_INPUT')
    if (spine.length > MAX_SECTIONS) throwPublication('SECTION_LIMIT')
    for (const item of spine) {
      if (typeof item.id !== 'string' || item.id.length === 0 || activeSpineIds.has(item.id)) {
        throwPublication('MALFORMED_INPUT')
      }
      if (item.id.length > MAX_CONTENT_ID_CHARS) throwPublication('SECTION_LIMIT')
      activeSpineIds.add(item.id)
    }

    const metadata = plainMetadata(parser)
    const tocInput = parser.getToc()
    if (!Array.isArray(tocInput)) throwPublication('MALFORMED_TOC')
    const tableOfContents = plainToc(parser, tocInput, { value: 0 }, 0)
    const titleBySection = new Map<string, string>()
    addTocTitles(tableOfContents, titleBySection)
    activeSections = spine.map((item, ordinal) => {
      const authoredTitle = titleBySection.get(item.id)
      return {
        contentId: item.id,
        title: authoredTitle ?? `Section ${ordinal + 1}`,
        hasAuthoredTitle: authoredTitle !== undefined,
        linear: true,
      }
    })
    const coverReference = parser.getCoverImage?.() ?? ''
    const coverResourceId = resourceIdFromReference(coverReference) ?? null
    activeParser = parser
    return {
      format: kind === 'mobi7' ? 'MOBI' : 'AZW3',
      kind,
      title: metadata.title,
      authors: metadata.authors,
      language: metadata.language,
      identifier: metadata.identifier,
      sections: activeSections,
      tableOfContents,
      coverResourceId,
    }
  } catch (error) {
    try {
      parser?.destroy()
    } finally {
      clearResources()
      activeParser = undefined
      activeSpineIds = new Set<string>()
      activeSections = []
    }
    mapParserFailure(error, 'MALFORMED_INPUT')
  }
}

function loadSection(contentId: unknown): unknown {
  const parser = activeParser
  if (parser === undefined) throwPublication('MALFORMED_INPUT')
  if (typeof contentId !== 'string' || !activeSpineIds.has(contentId)) throwPublication('MISSING_SECTION')
  try {
    const loaded = parser.loadChapter(contentId)
    if (loaded === undefined || typeof loaded.html !== 'string' || !Array.isArray(loaded.css)) {
      throwPublication('MISSING_SECTION')
    }
    const html = installKf8AnchorIds(loaded.html)
    const stylesheets: Array<{ sourceName: string, text: string }> = []
    for (const stylesheet of loaded.css) {
      if (typeof stylesheet.id !== 'string' || typeof stylesheet.href !== 'string') throwPublication('MALFORMED_INPUT')
      const resourceId = resourceIdFromReference(stylesheet.href)
      if (resourceId === undefined) throwPublication('MISSING_RESOURCE')
      const text = readResourceText(stylesheet.href)
      if (text === undefined) throwPublication('MISSING_RESOURCE')
      stylesheets.push({ sourceName: `mobi-style-${resourceId}`, text })
    }
    let sectionTextBytes = utf8ByteLength(html)
    for (const stylesheet of stylesheets) sectionTextBytes += utf8ByteLength(stylesheet.text)
    if (sectionTextBytes > MAX_SECTION_TEXT_BYTES) throwPublication('TEXT_LIMIT')
    if (!returnedSectionBytes.has(contentId)) {
      if (totalExpandedTextBytes > MAX_EXPANDED_TEXT_BYTES - sectionTextBytes) throwPublication('TEXT_LIMIT')
      totalExpandedTextBytes += sectionTextBytes
      returnedSectionBytes.set(contentId, sectionTextBytes)
    }
    const links = chapterLinks(parser, html, contentId)
    const section = activeSections.find((item) => item.contentId === contentId)
    if (section === undefined) throwPublication('MISSING_SECTION')
    return {
      contentId,
      html,
      stylesheets,
      links,
      resourceIds: collectResourceIds(html),
    }
  } catch (error) {
    mapParserFailure(error, 'MALFORMED_INPUT')
  }
}

function resourceMetadata(resourceId: unknown): unknown {
  if (typeof resourceId !== 'string') throwPublication('MISSING_RESOURCE')
  const resource = findResourceMetadata(resourceId)
  if (resource === undefined) throwPublication('MISSING_RESOURCE')
  return resource
}

function resourceBytes(resourceId: unknown): Uint8Array {
  if (typeof resourceId !== 'string') throwPublication('MISSING_RESOURCE')
  const bytes = readResourceBytes(resourceId)
  if (bytes === undefined) throwPublication('MISSING_RESOURCE')
  return bytes
}

;(globalThis as typeof globalThis & { tsuyomiMobi?: Record<string, unknown> }).tsuyomiMobi = {
  inspectPublication,
  loadSection,
  resourceMetadata,
  resourceBytes,
}
