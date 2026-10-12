// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0
// Builds the pinned parser source as one import-free QuickJS module. Run with Node from any cwd.
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const parserRoot = fileURLToPath(new URL('.', import.meta.url))
const upstreamRoot = path.join(parserRoot, 'vendor/upstream')
const parserSourceRoot = path.join(upstreamRoot, 'packages/mobi-parser/src')
const sharedSourceRoot = path.join(upstreamRoot, 'packages/shared/src')
const entryPath = path.join(parserRoot, 'src/entry.ts')
const outputPath = process.env.TSUYOMI_MOBI_OUTPUT ?? path.resolve(parserRoot, '../build/generated/assets/mobi/publication/mobi-quickjs.mjs')
const MAX_MODULE_BYTES = 8 * 1024 * 1024
const require = createRequire(path.join(parserRoot, 'package.json'))
const sourcePins = JSON.parse(await fs.readFile(path.join(parserRoot, 'source-pins.json'), 'utf8'))
const dependencyPrefix = 'node_modules/.pnpm/'
function vendoredPath(relativePath) {
  if (relativePath.startsWith(dependencyPrefix)) {
    return path.join(parserRoot, 'vendor/dependencies', relativePath.slice(dependencyPrefix.length).replace('/node_modules/', '/'))
  }
  return path.join(upstreamRoot, relativePath)
}

const saxSources = Object.keys(sourcePins.files).filter(relativePath => relativePath.endsWith('/sax/lib/sax.js'))
const fflateSources = Object.keys(sourcePins.files).filter(relativePath => relativePath.endsWith('/fflate/esm/browser.js'))
const eventsSources = Object.keys(sourcePins.files).filter(relativePath => relativePath.endsWith('/events/events.js'))
if (saxSources.length !== 1 || fflateSources.length !== 1 || eventsSources.length !== 1) throw new Error('Exactly one pinned SAX, fflate and events source is required')
const saxSourcePath = vendoredPath(saxSources[0])
const fflateSourcePath = vendoredPath(fflateSources[0])
const eventsSourcePath = vendoredPath(eventsSources[0])
if (
  sourcePins.candidate !== '@lingo-reader/mobi-parser@0.4.6' ||
  sourcePins.commit !== 'bc912542d9c09f144257205943c6bd3e421d8743'
) {
  throw new Error('The production lingo-reader source pin is not the reviewed revision')
}

for (const [relativePath, expectedHash] of Object.entries(sourcePins.files)) {
  const sourcePath = vendoredPath(relativePath)
  const actualHash = createHash('sha256').update(await fs.readFile(sourcePath)).digest('hex')
  if (actualHash !== expectedHash) throw new Error(`Pinned source changed: ${relativePath}`)
}

const { rollup } = await import(pathToFileURL(require.resolve('rollup')).href)
const esbuild = require('rollup-plugin-esbuild').default
const { nodeResolve } = await import(pathToFileURL(require.resolve('@rollup/plugin-node-resolve')).href)
const { default: commonjs } = await import(pathToFileURL(require.resolve('@rollup/plugin-commonjs')).href)
const { default: replace } = await import(pathToFileURL(require.resolve('@rollup/plugin-replace')).href)

function replaceOnce(source, before, after, description) {
  const start = source.indexOf(before)
  if (start < 0 || source.indexOf(before, start + before.length) >= 0) {
    throw new Error(`Expected exactly one upstream source match for ${description}`)
  }
  return source.slice(0, start) + after + source.slice(start + before.length)
}

function resolveUpstreamModule(source) {
  if (source === '@lingo-reader/mobi-parser-src') return path.join(parserSourceRoot, 'index.ts')
  if (source === '@tsuyomi/shared/xml2js-parser') return path.join(sharedSourceRoot, 'xml2js-parser.ts')
  if (source === '@tsuyomi/quickjs-resource-channel') return path.join(parserRoot, 'src/resource-channel.ts')
  if (source === '@tsuyomi/quickjs-limits') return path.join(parserRoot, 'src/mobi-limits.ts')
  if (source === '@tsuyomi/quickjs-toc-region') return path.join(parserRoot, 'src/toc-region.ts')
  if (source === '@tsuyomi/quickjs-codecs') return path.join(parserRoot, 'src/quickjs-codecs.ts')
  if (source === '@tsuyomi/quickjs-mobi-positions') return path.join(parserRoot, 'src/mobi-positions.ts')
  if (source === 'fflate') return fflateSourcePath
  if (source === 'sax') return saxSourcePath
  if (source === 'events') return eventsSourcePath
  return null
}

function quickjsSourceAdapter() {
  return {
    name: 'tsuyomi-quickjs-source-adapter',
    resolveId(source) {
      if (source === '@lingo-reader/shared') return '\0tsuyomi-shared-quickjs'
      return resolveUpstreamModule(source)
    },
    load(id) {
      if (id !== '\0tsuyomi-shared-quickjs') return null
      return `
        import { Parser } from '@tsuyomi/shared/xml2js-parser'
        export function parsexml(source, options = {}) {
          const parser = new Parser({ ...options, async: false })
          let value
          let failure
          parser.parseString(source, (error, result) => {
            if (error) failure = error
            else value = result
          })
          if (failure) throw failure
          if (value === undefined) throw new Error('XML_PARSE_FAILED')
          return value
        }
      `
    },
    transform(source, id) {
      const resolved = path.resolve(id)
      if (resolved === path.join(parserSourceRoot, 'mobi.ts')) return { code: adaptMobi(source), map: null }
      if (resolved === path.join(parserSourceRoot, 'kf8.ts')) return { code: adaptKf8(source), map: null }
      if (resolved === path.join(parserSourceRoot, 'book.ts')) return { code: adaptBook(source), map: null }
      if (resolved === path.join(parserSourceRoot, 'mobiFile.ts')) return { code: adaptMobiFile(source), map: null }
      if (resolved === path.join(parserSourceRoot, 'utils.ts')) return { code: adaptMobiUtils(source), map: null }
      if (resolved === saxSourcePath) return { code: adaptBufferedSax(source), map: null }
      return null
    },
  }
}

function adaptBufferedSax(source) {
  // XML input is already decoded and bounded; the adapter uses SAXParser, never Node streams.
  source = replaceOnce(source, '  sax.SAXStream = SAXStream\n', '', 'remove unused SAX stream export')
  source = replaceOnce(source, '  sax.createStream = createStream\n', '', 'remove unused SAX stream factory')
  const startMarker = '  var Stream\n'
  const endMarker = '  // this really needs to be replaced with character classes.\n'
  const start = source.indexOf(startMarker)
  const end = source.indexOf(endMarker, start)
  if (start < 0 || end < start || source.indexOf(startMarker, start + startMarker.length) >= 0) {
    throw new Error('Pinned buffered SAX adapter boundaries changed')
  }
  return source.slice(0, start) + source.slice(end)
}

function adaptMobi(source) {
  source = replaceOnce(
    source,
    "import { existsSync, mkdirSync, unlink } from 'node:fs'\n",
    "import { clearResources } from '@tsuyomi/quickjs-resource-channel'\nimport { extractDocToc } from '@tsuyomi/quickjs-toc-region'\nimport { MAX_TOC_DEPTH, MAX_TOC_ITEMS } from '@tsuyomi/quickjs-limits'\nimport { decodeLatin1 } from '@tsuyomi/quickjs-codecs'\nimport { decodeMobiChapter, mobiPositionTargets } from '@tsuyomi/quickjs-mobi-positions'\n",
    'MOBI filesystem imports',
  )
  source = replaceOnce(source, '  MobiTocItem,\n', '  MobiTocItem,\n  NcxItem,\n', 'MOBI authored NCX item type')
  source = replaceOnce(
    source,
    `export async function initMobiFile(file: InputFile, resourceSaveDir?: string) {
  const mobi = new Mobi(file, resourceSaveDir)
  await mobi.innerLoadFile()
  await mobi.innerInit()

  return mobi
}`,
    `export function initMobiFile(file: InputFile, resourceSaveDir?: string) {
  const mobi = new Mobi(file, resourceSaveDir)
  mobi.innerLoadFile()
  mobi.innerInit()
  return mobi
}`,
    'synchronous MOBI factory',
  )
  source = replaceOnce(
    source,
    `  async innerLoadFile() {
    this.fileArrayBuffer = await toArrayBuffer(this.file)
    this.mobiFile = new MobiFile(this.fileArrayBuffer, this.fileName)
  }`,
    `  innerLoadFile() {
    this.fileArrayBuffer = toArrayBuffer(this.file)
    this.mobiFile = new MobiFile(this.fileArrayBuffer, this.fileName)
  }`,
    'synchronous MOBI input adapter',
  )
  source = replaceOnce(source, '  async innerInit() {', '  innerInit() {', 'synchronous MOBI initializer')
  source = replaceOnce(
    source,
    `    const str = Array.from(
      array,
      val => String.fromCharCode(val),
    ).join('')`,
    `    const str = decodeLatin1(array)`,
    'bounded byte-position string reconstruction',
  )
  source = replaceOnce(
    source,
    '    // split chapters\n',
    `    const ncxPositions: number[] = []
    let ncxCount = 0
    const mapNcx = (entries: NcxItem[], depth = 0): MobiToc => {
      if (depth > MAX_TOC_DEPTH) throw new Error('TSUYOMI_PUB_ERROR:SECTION_LIMIT')
      return entries.map((entry) => {
        if (++ncxCount > MAX_TOC_ITEMS) throw new Error('TSUYOMI_PUB_ERROR:SECTION_LIMIT')
        if (!Number.isSafeInteger(entry.offset) || entry.offset < 0 || entry.offset >= array.length) {
          throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_TOC')
        }
        ncxPositions.push(entry.offset)
        return {
          label: entry.label,
          href: 'filepos:' + entry.offset,
          children: entry.children === undefined ? undefined : mapNcx(entry.children, depth + 1),
        }
      })
    }
    const ncx = this.mobiFile.getNCX()
    const authoredNcxToc = ncx === undefined ? [] : mapNcx(ncx)
    const positionTargets = mobiPositionTargets(str, ncxPositions)
    let positionCursor = 0
    const bodyOpen = /<body\\b[^>]*>/i.exec(str)
    if (!bodyOpen) throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_INPUT')
    const bodyStart = bodyOpen.index + bodyOpen[0].length
    let bodyEnd = array.length
    for (const close of str.matchAll(/<\\/body\\s*>/gi)) bodyEnd = close.index

    // split chapters
`,
    'byte-position targets and body bounds before decoding',
  )
  source = replaceOnce(
    source,
    `      const section = str.slice(start + matched.length, end)
      const buffer = Uint8Array.from(section, c => c.charCodeAt(0))
      const text = this.mobiFile.decode(buffer.buffer)`,
    `      const sectionEnd = end ?? array.length
      const textEnd = Math.min(sectionEnd, bodyEnd)
      const textStart = Math.min(textEnd, Math.max(start + matched.length, bodyStart))
      const chapterTargets: number[] = []
      while (positionCursor < positionTargets.length && positionTargets[positionCursor] < sectionEnd) {
        const position = positionTargets[positionCursor++]
        if (position >= start) {
          if (position > textEnd) throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_INPUT')
          chapterTargets.push(position)
        }
      }
      const buffer = array.subarray(textStart, textEnd)
      const text = decodeMobiChapter(array, textStart, textEnd, chapterTargets, this.mobiFile.decoder)`,
    'decode exact chapter byte view without iterable copies',
  )
  source = replaceOnce(
    source,
    `    // process last chapter. remove trailing </body></html>
    const lastChapterText = chapters[chapters.length - 1].text
    chapters[chapters.length - 1].text = lastChapterText.slice(0, lastChapterText.indexOf('</body>'))

    // process first chapter, remove beginning ...<body>
    const firstChapterText = chapters[0].text
    const bodyOpenTagIndex = firstChapterText.indexOf('<body>')
    chapters[0].text = firstChapterText.slice(bodyOpenTagIndex + '<body>'.length)

    this.chapters = chapters
    this.idToChapter = idToChapter

    // used for parsing toc
    const referenceStr = firstChapterText.slice(0, bodyOpenTagIndex)`,
    `    this.chapters = chapters
    this.idToChapter = idToChapter

    if (authoredNcxToc.length > 0) {
      this.toc = authoredNcxToc
      return
    }

    // Guide positions still address the original unmodified byte stream.
    const referenceStr = str.slice(0, bodyOpen.index)`,
    'preserve inserted position anchors while trimming the original byte ranges',
  )
  source = replaceOnce(source, 'const tocAst = await parsexml(', 'const tocAst = parsexml(', 'synchronous bounded TOC XML parser')
  source = replaceOnce(source, 'const tocChapterStr = this.findTocChapter(referenceStr)', 'const tocRegion = this.findTocRegion(referenceStr, str)', 'byte-position TOC lookup')
  source = replaceOnce(source, 'if (tocChapterStr) {', 'if (tocRegion) {', 'bounded TOC region dispatch')
  source = replaceOnce(source, 'tocChapterStr.text.replace(', 'tocRegion.replace(', 'bounded TOC region XML input')
  source = replaceOnce(
    source,
    `  private findTocChapter(referenceStr: string): MobiChapter | undefined {
    const tocPosReg = /<reference.*\\/>/g
    const refs = referenceStr.match(tocPosReg)
    const typeReg = /type="(.+?)"/
    const fileposReg = /filepos=(.*)/
    if (refs) {
      for (const ref of refs) {
        const type = ref.match(typeReg)?.[1].trim()
        const filepos = ref.match(fileposReg)?.[1].trim()
        if (type === 'toc' && filepos) {
          const tocPos = Number.parseInt(filepos, 10)
          const chapter = this.chapters.find(ch => ch.end > tocPos)
          return chapter
        }
      }
    }
    return undefined
  }`,
    `  private findTocRegion(referenceStr: string, rawText: string): string | undefined {
    const refs = referenceStr.match(/<reference\\b(?:[^"'<>]|"[^"]*"|'[^']*')*>/gi) ?? []
    for (const ref of refs) {
      const type = ref.match(/(?:^|\\s)type\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s"'=<>]+))/i)
      const filepos = ref.match(/(?:^|\\s)filepos\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s"'=<>]+))/i)
      const typeValue = type?.[1] ?? type?.[2] ?? type?.[3]
      const fileposValue = filepos?.[1] ?? filepos?.[2] ?? filepos?.[3]
      if (typeValue?.trim().toLowerCase() !== 'toc' || fileposValue === undefined || !/^\\d+$/.test(fileposValue)) continue
      const tocPosition = Number(fileposValue)
      if (!Number.isSafeInteger(tocPosition)) continue
      const region = extractDocToc(rawText, tocPosition)
      if (region) return this.mobiFile.decode(region.buffer as ArrayBuffer)
    }
    return undefined
  }`,
    'quoted/unquoted guide attributes and byte-pointed doc-toc region',
  )
  source = replaceOnce(
    source,
    `  private parseNavMap(children: any, toc: MobiToc) {
    for (const child of children) {
      const childName = child['#name']
      if (childName === 'p' || childName === 'blockquote') {
        let subItem: MobiTocItem = {
          label: '',
          href: '',
        }
        if (child.a) {
          const a = child.a[0]
          const label = a._
          const filepos = Number(a.$.filepos)
          subItem = {
            label,
            href: \`filepos:\${filepos}\`,
          }
          toc.push(subItem)
        }
        if (child.p || child.blockquote) {
          subItem.children = []
          this.parseNavMap(child.children, subItem.children)
        }
      }
    }
  }`,
    `  private parseNavMap(children: any, toc: MobiToc, depth = 0) {
    if (depth > MAX_TOC_DEPTH) throw new RangeError('TOC_DEPTH_LIMIT')
    for (const child of children ?? []) {
      const childName = child['#name']
      if (['ol', 'ul', 'div', 'section', 'nav'].includes(childName)) {
        this.parseNavMap(child.children, toc, depth + 1)
        continue
      }
      if (childName !== 'p' && childName !== 'blockquote' && childName !== 'li') continue

      const a = child.a?.[0] ?? child.children?.find((entry: any) => entry['#name'] === 'a')
      const href = a?.$?.href
      const rawFilepos = a?.$?.filepos
      const fileposValue = typeof rawFilepos === 'string'
        ? rawFilepos
        : typeof href === 'string' ? href.match(/^filepos:(\\d+)$/i)?.[1] : undefined
      const label = a?._
      if (typeof label !== 'string' || fileposValue === undefined || !/^\\d+$/.test(fileposValue)) {
        this.parseNavMap(child.children, toc, depth + 1)
        continue
      }
      const filepos = Number(fileposValue)
      if (!Number.isSafeInteger(filepos)) continue

      const item: MobiTocItem = { label, href: 'filepos:' + filepos }
      const nested = (child.children ?? []).filter((entry: any) =>
        ['p', 'blockquote', 'li', 'ol', 'ul', 'div', 'section', 'nav'].includes(entry['#name']),
      )
      if (nested.length > 0) {
        item.children = []
        this.parseNavMap(nested, item.children, depth + 1)
      }
      toc.push(item)
    }
  }`,
    'bounded nested MOBI contents chapters and navigation lists',
  )
  source = replaceOnce(
    source,
    `  resolveHref(href: string): MobiResolvedHref | undefined {
    const hrefmatch = href.match(/filepos:(\\d+)/)
    if (!hrefmatch) {
      return undefined
    }
    const filepos = hrefmatch[1]
    const fileposNum = Number(filepos)
    const chapter = this.chapters.find(ch => ch.end > fileposNum)
    if (chapter) {
      return { id: chapter.id, selector: \`[id="filepos:\${filepos}"]\` }
    }
    return undefined
  }`,
    `  resolveHref(href: string): MobiResolvedHref | undefined {
    const hrefmatch = href.match(/^filepos:(\\d+)$/i)
    if (!hrefmatch) return undefined
    const fileposNum = Number(hrefmatch[1])
    if (!Number.isSafeInteger(fileposNum)) return undefined
    const chapter = this.chapters.find(chapter =>
      chapter.start <= fileposNum && (chapter.end === undefined || fileposNum < chapter.end),
    )
    if (!chapter) return undefined
    return { id: chapter.id, selector: \`[id="filepos:\${fileposNum}"]\` }
  }`,
    'canonical MOBI filepos target resolution',
  )
  source = replaceOnce(
    source,
    `  destroy() {
    this.resourceCache.forEach((url) => {
      if (__BROWSER__) {
        URL.revokeObjectURL(url)
      }
      else {
        if (existsSync(url)) {
          unlink(url, () => { })
        }
      }
    })
    this.resourceCache.clear()
  }`,
    `  destroy() {
    this.resourceCache.clear()
    clearResources()
  }`,
    'MOBI resource-channel cleanup',
  )
  source = replaceOnce(
    source,
    `    this.resourceSaveDir = resourceSaveDir
    if (!__BROWSER__ && !existsSync(this.resourceSaveDir)) {
      mkdirSync(this.resourceSaveDir, { recursive: true })
    }`,
    `    this.resourceSaveDir = resourceSaveDir`,
    'MOBI filesystem initialization',
  )
  return source
}

function adaptKf8(source) {
  source = replaceOnce(
    source,
    "import { existsSync, mkdirSync, unlink } from 'node:fs'\n",
    "import { clearResources } from '@tsuyomi/quickjs-resource-channel'\nimport { MAX_RESOURCES } from '@tsuyomi/quickjs-limits'\n",
    'KF8 filesystem imports',
  )
  source = replaceOnce(
    source,
    `export async function initKf8File(file: InputFile, resourceSaveDir?: string) {
  const kf8 = new Kf8(file, resourceSaveDir)
  await kf8.innerLoadFile()
  await kf8.innerInit()

  return kf8
}`,
    `export function initKf8File(file: InputFile, resourceSaveDir?: string) {
  const kf8 = new Kf8(file, resourceSaveDir)
  kf8.innerLoadFile()
  kf8.innerInit()
  return kf8
}`,
    'synchronous KF8 factory',
  )
  source = replaceOnce(
    source,
    `  async innerLoadFile() {
    this.fileArrayBuffer = await toArrayBuffer(this.file)
    this.mobiFile = new MobiFile(this.fileArrayBuffer, this.fileName)
  }`,
    `  innerLoadFile() {
    this.fileArrayBuffer = toArrayBuffer(this.file)
    this.mobiFile = new MobiFile(this.fileArrayBuffer, this.fileName)
  }`,
    'synchronous KF8 input adapter',
  )
  source = replaceOnce(source, '  async innerInit() {', '  innerInit() {', 'synchronous KF8 initializer')
  source = replaceOnce(
    source,
    '  private resourceCache = new Map<string, string>()',
    '  private resourceCache = new Map<string, string>()\n  private resolvingResources = new Set<string>()',
    'track active KF8 resource dependencies',
  )
  source = replaceOnce(
    source,
    `  private replaceResources(str: string): string {
    return str.replace(
      new RegExp(kindleResourceRegex, 'gi'),
      (matched: string, resourceType: string, id: string, type: string) => {
        // cache
        if (this.resourceCache.has(matched)) {
          return this.resourceCache.get(matched)!
        }

        // load resource buffer
        const raw = resourceType === 'flow'
          ? this.loadFlow(Number.parseInt(id))
          : this.mobiFile.loadResource(Number.parseInt(id, 36) - 1).raw

        let blobData: Uint8Array | string = ''

        // TODO?: should handle xml html and xhtml file?
        // get blobData based on type
        if (type === MIME.CSS || type === MIME.SVG) {
          const text = this.mobiFile.decode(raw?.buffer as ArrayBuffer)
          const textReplaced = this.replaceResources(text)
          blobData = textReplaced
        }
        else {
          blobData = raw!
        }

        // convert to blob url
        const url = saveResource(blobData, type as keyof typeof MimeToExt, id, this.resourceSaveDir)

        this.resourceCache.set(matched, url)
        return url
      },
    )
  }`,
    `  private replaceResources(str: string): string {
    return str.replace(
      new RegExp(kindleResourceRegex, 'gi'),
      (matched: string, resourceType: string, id: string, type: string) => {
        if (!/^[0-9a-v]+$/i.test(id)) throw new Error('TSUYOMI_PUB_ERROR:MISSING_RESOURCE')
        // Both flow and embedded resource identifiers use base32, including A..V.
        const index = Number.parseInt(id, 32)
        const kind = resourceType.toLowerCase()
        if (!Number.isSafeInteger(index) || (kind === 'embed' && index < 1)) {
          throw new Error('TSUYOMI_PUB_ERROR:MISSING_RESOURCE')
        }
        const key = kind + ':' + index
        const mime = type?.toLowerCase()
        const cacheKey = key + ':' + mime
        const cached = this.resourceCache.get(cacheKey)
        if (cached) return cached
        if (this.resolvingResources.has(key)) throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_INPUT')
        if (this.resolvingResources.size >= MAX_RESOURCES) throw new Error('TSUYOMI_PUB_ERROR:RESOURCE_LIMIT')
        this.resolvingResources.add(key)
        try {
          const raw = kind === 'flow'
            ? this.loadFlow(index)
            : this.mobiFile.loadResource(index - 1).raw
          if (!raw) throw new Error('TSUYOMI_PUB_ERROR:MISSING_RESOURCE')
          const data = mime === MIME.CSS || mime === MIME.SVG
            ? this.replaceResources(this.mobiFile.decoder.decode(raw))
            : raw
          const url = saveResource(data, mime as keyof typeof MimeToExt, key, this.resourceSaveDir)
          this.resourceCache.set(cacheKey, url)
          return url
        } finally {
          this.resolvingResources.delete(key)
        }
      },
    )
  }`,
    'base32 KF8 resource addressing and cycle-safe expansion',
  )
  source = replaceOnce(
    source,
    `    if (index < 0xFFFFFFFF) {
      return this.loadRaw(this.fdstTable[index][0], this.fdstTable[index][1])
    }`,
    `    if (Number.isSafeInteger(index) && index >= 0 && index < this.fdstTable.length) {
      return this.loadRaw(this.fdstTable[index][0], this.fdstTable[index][1])
    }`,
    'validate KF8 flow indexes before reading ranges',
  )
  source = replaceOnce(
    source,
    `  destroy() {
    this.resourceCache.forEach((url) => {
      if (__BROWSER__) {
        URL.revokeObjectURL(url)
      }
      else {
        if (existsSync(url)) {
          unlink(url, () => { })
        }
      }
    })
  }`,
    `  destroy() {
    this.resourceCache.clear()
    clearResources()
  }`,
    'KF8 resource-channel cleanup',
  )
  source = replaceOnce(
    source,
    `    this.resourceSaveDir = resourceSaveDir
    if (!__BROWSER__ && !existsSync(this.resourceSaveDir)) {
      mkdirSync(this.resourceSaveDir, { recursive: true })
    }`,
    `    this.resourceSaveDir = resourceSaveDir`,
    'KF8 filesystem initialization',
  )
  return source
}

function adaptBook(source) {
  source = replaceOnce(source, "import { readFileSync } from 'node:fs'\n", '', 'Node file input import')
  source = replaceOnce(source, "import path from 'node:path'\n", '', 'Node path import')
  source = replaceOnce(
    source,
    `import {
  mobiEncoding,
} from './utils'\n`,
    `import {
  mobiEncoding,
} from './utils'\nimport { MAX_EXPANDED_TEXT_BYTES, MAX_RESOURCE_BYTES } from '@tsuyomi/quickjs-limits'\n`,
    'bounded decompression limits',
  )
  source = replaceOnce(
    source,
    `export function getMobiFileName(file: InputFile): string {
  let fileName = ''
  if (__BROWSER__) {
    fileName = (file as File).name ?? ''
  }
  else {
    if (typeof file === 'string') {
      fileName = path.basename(file)
    }
  }
  return fileName
}`,
    `export function getMobiFileName(_file: InputFile): string {
  return ''
}`,
    'byte-only parser filename',
  )
  source = replaceOnce(
    source,
    `export async function toArrayBuffer(file: InputFile): Promise<ArrayBuffer> {
  if (__BROWSER__) {
    return file instanceof Uint8Array
      ? bufferToArrayBuffer(file)
      : await (file as File).arrayBuffer()
  }
  else {
    return typeof file === 'string'
      ? bufferToArrayBuffer(new Uint8Array(readFileSync(file)))
      : bufferToArrayBuffer(file as Uint8Array)
  }
}`,
    `export function toArrayBuffer(file: InputFile): ArrayBuffer {
  if (!(file instanceof Uint8Array)) throw new TypeError('A byte-array input is required')
  if (file.byteOffset === 0 && file.byteLength === file.buffer.byteLength) return file.buffer as ArrayBuffer
  return bufferToArrayBuffer(file)
}`,
    'bounded in-memory input adapter',
  )
  source = replaceOnce(
    source,
    `export function decompressPalmDOC(array: Uint8Array): Uint8Array {
  const output: number[] = []
  for (let i = 0; i < array.length; i++) {
    const byte = array[i]
    if (byte === 0) {
      // uncompressed literal, just copy it
      output.push(0)
    }
    else if (byte <= 8) {
      // copy next 1-8 bytes
      for (const x of array.subarray(i + 1, (i += byte) + 1))
        output.push(x)
    }
    else if (byte <= 0b0111_1111) {
      // uncompressed literal
      output.push(byte)
    }
    else if (byte <= 0b1011_1111) {
      // 1st and 2nd bits are 10, meaning this is a length-distance pair
      // read next byte and combine it with current byte
      const bytes = (byte << 8) | array[i++ + 1]
      // the 3rd to 13th bits encode distance
      const distance = (bytes & 0b0011_1111_1111_1111) >>> 3
      // the last 3 bits, plus 3, is the length to copy
      const length = (bytes & 0b111) + 3
      for (let j = 0; j < length; j++)
        output.push(output[output.length - distance])
    }
    else {
      // compressed from space plus char
      output.push(32, byte ^ 0b1000_0000)
    }
  }
  return Uint8Array.from(output)
}`,
    `export function decompressPalmDOC(array: Uint8Array): Uint8Array {
  const output: number[] = []
  const append = (value: number) => {
    if (output.length >= MAX_EXPANDED_TEXT_BYTES) throw new RangeError('EXPANDED_TEXT_LIMIT')
    output.push(value)
  }
  for (let i = 0; i < array.length; i++) {
    const byte = array[i]
    if (byte === 0) {
      // uncompressed literal, just copy it
      append(0)
    }
    else if (byte <= 8) {
      // copy next 1-8 bytes
      for (const x of array.subarray(i + 1, (i += byte) + 1)) append(x)
    }
    else if (byte <= 0b0111_1111) {
      // uncompressed literal
      append(byte)
    }
    else if (byte <= 0b1011_1111) {
      // 1st and 2nd bits are 10, meaning this is a length-distance pair
      // read next byte and combine it with current byte
      const bytes = (byte << 8) | array[i++ + 1]
      // the 3rd to 13th bits encode distance
      const distance = (bytes & 0b0011_1111_1111_1111) >>> 3
      // the last 3 bits, plus 3, is the length to copy
      const length = (bytes & 0b111) + 3
      for (let j = 0; j < length; j++) append(output[output.length - distance])
    }
    else {
      // compressed from space plus char
      append(32)
      append(byte ^ 0b1000_0000)
    }
  }
  return Uint8Array.from(output)
}`,
    'bounded PalmDOC decompressor',
  )
  source = replaceOnce(
    source,
    `  const decompress = (byteArray: Uint8Array): Uint8Array => {
    let output = new Uint8Array()
    const bitLength = byteArray.byteLength * 8`,
    `  const decompress = (byteArray: Uint8Array, depth = 0): Uint8Array => {
    if (depth > 32) throw new RangeError('HUFF_DICTIONARY_DEPTH_LIMIT')
    const output: Uint8Array[] = []
    let outputLength = 0
    const bitLength = byteArray.byteLength * 8`,
    'bounded HUFF/CDIC recursion',
  )
  source = replaceOnce(
    source,
    '      output = concatTypedArrays([output, result]) as any',
    `      if (outputLength + result.length > MAX_EXPANDED_TEXT_BYTES) throw new RangeError('EXPANDED_TEXT_LIMIT')
      if (result.length > 0) {
        output.push(result)
        outputLength += result.length
      }`,
    'linear bounded HUFF/CDIC expansion',
  )
  source = replaceOnce(
    source,
    `    }
    return output
  }
  return decompress`,
    `    }
    const expanded = new Uint8Array(outputLength)
    let offset = 0
    for (const chunk of output) {
      expanded.set(chunk, offset)
      offset += chunk.length
    }
    return expanded
  }
  return decompress`,
    'single-allocation HUFF/CDIC output',
  )
  source = replaceOnce(source, 'result = decompress(result)', 'result = decompress(result, depth + 1)', 'bounded HUFF/CDIC recursion call')
  source = replaceOnce(
    source,
    '      return unzlibSync(array)',
    `      const result = unzlibSync(array, { out: new Uint8Array(MAX_RESOURCE_BYTES + 1) })
      if (result.length > MAX_RESOURCE_BYTES) throw new RangeError('RESOURCE_LIMIT')
      return result`,
    'bounded zlib resource expansion',
  )
  return source
}

function adaptMobiFile(source) {
  source = replaceOnce(
    source,
    "import { getFileMimeType, mobiLang, unescapeHTML } from './utils'\n",
    "import { getFileMimeType, mobiLang, unescapeHTML } from './utils'\nimport { MAX_EXPANDED_TEXT_BYTES } from '@tsuyomi/quickjs-limits'\n",
    'bounded text-record cache limit',
  )
  source = replaceOnce(
    source,
    `  public decompress!: DecompressFunc

  constructor(`,
    `  public decompress!: DecompressFunc
  private expandedTextByIndex = new Map<number, Uint8Array>()
  private expandedTextByteLength = 0

  constructor(`,
    'bounded text-record cache state',
  )
  source = replaceOnce(
    source,
    `  loadTextBuffer(index: number): Uint8Array {
    return this.decompress(
      this.removeTrailingEntries(
        new Uint8Array(
          this.loadRecord(index + 1),
        ),
      ),
    )
  }`,
    `  loadTextBuffer(index: number): Uint8Array {
    const cached = this.expandedTextByIndex.get(index)
    if (cached) return cached
    const expanded = this.decompress(
      this.removeTrailingEntries(new Uint8Array(this.loadRecord(index + 1))),
    )
    if (this.expandedTextByteLength + expanded.byteLength > MAX_EXPANDED_TEXT_BYTES) {
      throw new RangeError('EXPANDED_TEXT_LIMIT')
    }
    this.expandedTextByteLength += expanded.byteLength
    this.expandedTextByIndex.set(index, expanded)
    return expanded
  }`,
    'bounded and cached text-record expansion',
  )
  return source
}

function adaptMobiUtils(source) {
  source = replaceOnce(
    source,
    "import { writeFileSync } from 'node:fs'\nimport { resolve } from 'node:path'\n",
    "import { saveResource as hostSaveResource } from '@tsuyomi/quickjs-resource-channel'\n",
    'filesystem resource imports',
  )
  source = replaceOnce(
    source,
    `export function saveResource(
  data: Uint8Array | string,
  type: FileMimeType,
  filename: string,
  imageSaveDir: string,
): string {
  if (__BROWSER__) {
    return URL.createObjectURL(new Blob([data as Uint8Array<ArrayBuffer> | string], { type }))
  }
  else {
    const fileName = \`${'${filename}'}.${'${MimeToExt[type]}'}\`
    const url = resolve(imageSaveDir, fileName)
    writeFileSync(url, data)
    return url
  }
}`,
    'export { hostSaveResource as saveResource }',
    'host-owned resource channel adapter',
  )
  return source
}

const bundle = await rollup({
  input: entryPath,
  plugins: [
    quickjsSourceAdapter(),
    replace({ preventAssignment: true, values: { __BROWSER__: 'true' } }),
    esbuild({ target: 'es2020', minify: false, sourceMap: false }),
    nodeResolve({ browser: true, preferBuiltins: false }),
    commonjs(),
  ],
  treeshake: { moduleSideEffects: false },
  onwarn(warning, defaultHandler) {
    if (warning.code === 'UNRESOLVED_IMPORT') throw new Error(warning.message)
    defaultHandler(warning)
  },
})

try {
  const generated = await bundle.generate({ format: 'es', inlineDynamicImports: true, sourcemap: false })
  const module = generated.output.find(output => output.type === 'chunk')
  if (!module || module.imports.length !== 0 || module.dynamicImports.length !== 0) {
    throw new Error('The QuickJS parser output must be one import-free module')
  }
  const forbiddenRuntimePaths = [
    /\bnode:(?:fs|path|buffer)\b/,
    /\b(?:readFileSync|writeFileSync|existsSync|mkdirSync)\b/,
    /\bURL\.(?:createObjectURL|revokeObjectURL)\b/,
    /\bnew\s+Blob\b/,
  ]
  if (forbiddenRuntimePaths.some(pattern => pattern.test(module.code))) {
    throw new Error('A Node or DOM resource path leaked into the QuickJS bundle')
  }
  const moduleBytes = Buffer.byteLength(module.code, 'utf8')
  if (moduleBytes === 0 || moduleBytes > MAX_MODULE_BYTES) {
    throw new RangeError('The QuickJS parser module exceeds its 8 MiB asset limit')
  }
  await fs.mkdir(path.dirname(outputPath), { recursive: true })
  await fs.copyFile(path.join(parserRoot, 'THIRD_PARTY_NOTICES.txt'), path.join(path.dirname(outputPath), 'THIRD_PARTY_NOTICES.txt'))
  await fs.writeFile(outputPath, module.code, 'utf8')
  console.log(`Wrote ${path.relative(parserRoot, outputPath)} (${moduleBytes} UTF-8 bytes)`)
} finally {
  await bundle.close()
}
