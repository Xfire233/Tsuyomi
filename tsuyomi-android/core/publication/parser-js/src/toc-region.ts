/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
const MAX_TOC_REGION_BYTES = 32_768
const PAGE_BREAK = /<\s*(?:mbp:)?pagebreak\b[^>]*>/gi
const RAW_TEXT_TAGS = new Set(['script', 'style', 'textarea', 'title', 'xmp', 'iframe', 'noembed', 'noframes', 'plaintext'])

interface MarkupToken {
  end: number
  kind: 'tag' | 'ignored'
  name: string
  closing: boolean
  selfClosing: boolean
}

interface PageRegion {
  start: number
  end: number
  position: number
}

interface NavStart {
  index: number
  token: MarkupToken
}

function scanMarkup(source: string, start: number, limit = source.length): MarkupToken | undefined {
  if (source.charCodeAt(start) !== 0x3C) return undefined
  if (source.startsWith('<!--', start)) {
    const close = source.indexOf('-->', start + 4)
    if (close < 0 || close + 3 > limit) return { end: limit, kind: 'ignored', name: '', closing: false, selfClosing: false }
    return { end: close + 3, kind: 'ignored', name: '', closing: false, selfClosing: false }
  }
  if (source.startsWith('<![CDATA[', start)) {
    const close = source.indexOf(']]>', start + 9)
    if (close < 0 || close + 3 > limit) return { end: limit, kind: 'ignored', name: '', closing: false, selfClosing: false }
    return { end: close + 3, kind: 'ignored', name: '', closing: false, selfClosing: false }
  }
  if (source.startsWith('<?', start)) {
    const close = source.indexOf('?>', start + 2)
    if (close < 0 || close + 2 > limit) return { end: limit, kind: 'ignored', name: '', closing: false, selfClosing: false }
    return { end: close + 2, kind: 'ignored', name: '', closing: false, selfClosing: false }
  }
  if (source.startsWith('<!', start)) {
    let quote = 0
    let subsetDepth = 0
    for (let cursor = start + 2; cursor < limit; cursor++) {
      const code = source.charCodeAt(cursor)
      if (quote !== 0) {
        if (code === quote) quote = 0
      } else if (code === 0x22 || code === 0x27) quote = code
      else if (code === 0x5B) subsetDepth++
      else if (code === 0x5D && subsetDepth > 0) subsetDepth--
      else if (code === 0x3E && subsetDepth === 0) {
        return { end: cursor + 1, kind: 'ignored', name: '', closing: false, selfClosing: false }
      }
    }
    return { end: limit, kind: 'ignored', name: '', closing: false, selfClosing: false }
  }

  let cursor = start + 1
  let closing = false
  if (source.charCodeAt(cursor) === 0x2F) {
    closing = true
    cursor++
  }
  const nameStart = cursor
  while (cursor < limit) {
    const code = source.charCodeAt(cursor)
    if ((code >= 0x41 && code <= 0x5A) || (code >= 0x61 && code <= 0x7A) ||
      (code >= 0x30 && code <= 0x39) || code === 0x3A || code === 0x2D || code === 0x5F) cursor++
    else break
  }
  if (cursor === nameStart) return undefined
  const name = source.slice(nameStart, cursor).toLowerCase()
  let quote = 0
  let lastNonWhitespace = cursor - 1
  for (; cursor < limit; cursor++) {
    const code = source.charCodeAt(cursor)
    if (quote !== 0) {
      if (code === quote) quote = 0
    } else if (code === 0x22 || code === 0x27) quote = code
    else if (code === 0x3E) {
      const selfClosing = !closing && lastNonWhitespace >= nameStart && source.charCodeAt(lastNonWhitespace) === 0x2F
      return { end: cursor + 1, kind: 'tag', name, closing, selfClosing }
    }
    if (code !== 0x20 && code !== 0x09 && code !== 0x0A && code !== 0x0D) lastNonWhitespace = cursor
  }
  return { end: limit, kind: 'ignored', name: '', closing: false, selfClosing: false }
}

function skipRawText(source: string, tagName: string, from: number, limit: number): number | undefined {
  if (tagName === 'plaintext') return undefined
  const close = new RegExp(`<\\/\\s*${tagName}\\s*>`, 'ig')
  close.lastIndex = from
  const match = close.exec(source)
  if (!match || match.index + match[0].length > limit) return undefined
  return match.index + match[0].length
}

function bytesFromRawText(rawText: string): Uint8Array {
  const bytes = new Uint8Array(rawText.length)
  for (let index = 0; index < rawText.length; index++) bytes[index] = rawText.charCodeAt(index) & 0xFF
  return bytes
}

function pageRegion(rawText: string, bytePosition: number): PageRegion | undefined {
  let start = 0
  let end = rawText.length
  let position = bytePosition
  PAGE_BREAK.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = PAGE_BREAK.exec(rawText)) !== null) {
    const afterBreak = match.index + match[0].length
    if (match.index <= bytePosition && bytePosition < afterBreak) {
      start = afterBreak
      position = afterBreak
    } else if (afterBreak <= bytePosition) start = afterBreak
    else {
      end = match.index
      break
    }
  }
  if (position < start || position > end) return undefined
  return { start, end, position }
}

function findNavEnd(rawText: string, open: NavStart, regionEnd: number): { start: number, end: number } | undefined {
  const stopAt = Math.min(regionEnd, open.index + MAX_TOC_REGION_BYTES)
  let cursor = open.token.end
  let depth = 1
  while (cursor < stopAt) {
    const start = rawText.indexOf('<', cursor)
    if (start < 0 || start >= stopAt) return undefined
    const token = scanMarkup(rawText, start, stopAt)
    if (!token) {
      cursor = start + 1
      continue
    }
    if (token.kind === 'tag' && !token.closing && RAW_TEXT_TAGS.has(token.name)) {
      const afterRawText = skipRawText(rawText, token.name, token.end, stopAt)
      if (afterRawText === undefined) return undefined
      cursor = afterRawText
      continue
    }
    if (token.kind === 'tag' && token.name === 'nav') {
      if (token.closing) depth--
      else if (!token.selfClosing) depth++
      if (depth === 0) return { start, end: token.end }
    }
    cursor = token.end
  }
  return undefined
}

function extractNavContent(
  rawText: string,
  open: NavStart,
  bytePosition: number,
  regionEnd: number,
): Uint8Array | undefined {
  const close = findNavEnd(rawText, open, regionEnd)
  if (!close || bytePosition < open.index || bytePosition >= close.end) return undefined
  return bytesFromRawText(rawText.slice(open.token.end, close.start))
}

function findEnclosingNav(rawText: string, region: PageRegion): Uint8Array | null | undefined {
  const atPosition = scanMarkup(rawText, region.position, Math.min(region.end, region.position + MAX_TOC_REGION_BYTES))
  if (atPosition?.kind === 'tag' && !atPosition.closing && RAW_TEXT_TAGS.has(atPosition.name)) return null
  if (atPosition?.kind === 'tag' && atPosition.name === 'nav' && !atPosition.closing && !atPosition.selfClosing) {
    return extractNavContent(rawText, { index: region.position, token: atPosition }, region.position, region.end)
  }

  const stack: NavStart[] = []
  let cursor = region.start
  while (cursor < region.position) {
    const start = rawText.indexOf('<', cursor)
    if (start < 0 || start >= region.position) break
    const token = scanMarkup(rawText, start, region.position)
    if (!token) {
      cursor = start + 1
      continue
    }
    if (token.kind === 'tag' && !token.closing && RAW_TEXT_TAGS.has(token.name)) {
      const afterRawText = skipRawText(rawText, token.name, token.end, region.position)
      if (afterRawText === undefined) return null
      cursor = afterRawText
      continue
    }
    if (token.kind === 'tag' && token.name === 'nav') {
      if (token.closing) stack.pop()
      else if (!token.selfClosing) {
        if (stack.length >= 32) return undefined
        stack.push({ index: start, token })
      }
    }
    cursor = token.end
  }
  for (let index = stack.length - 1; index >= 0; index--) {
    const content = extractNavContent(rawText, stack[index], region.position, region.end)
    if (content) return content
  }
  return undefined
}

function findFollowingNav(rawText: string, region: PageRegion): Uint8Array | undefined {
  const stopAt = Math.min(region.end, region.position + MAX_TOC_REGION_BYTES)
  let cursor = region.position
  while (cursor < stopAt) {
    const start = rawText.indexOf('<', cursor)
    if (start < 0 || start >= stopAt) return undefined
    const token = scanMarkup(rawText, start, stopAt)
    if (!token) {
      cursor = start + 1
      continue
    }
    if (token.kind === 'tag' && !token.closing && RAW_TEXT_TAGS.has(token.name)) {
      const afterRawText = skipRawText(rawText, token.name, token.end, stopAt)
      if (afterRawText === undefined) return undefined
      cursor = afterRawText
      continue
    }
    if (token.kind === 'tag' && token.name === 'nav' && !token.closing && !token.selfClosing) {
      return extractNavContent(rawText, { index: start, token }, region.position, region.end)
    }
    cursor = token.end
  }
  return undefined
}

function bodyRegion(rawText: string, region: PageRegion): { start: number, end: number } | undefined {
  let cursor = region.start
  let bodyStart = -1
  let bodyEnd = region.end
  while (cursor < region.end) {
    const start = rawText.indexOf('<', cursor)
    if (start < 0 || start >= region.end) break
    const token = scanMarkup(rawText, start, region.end)
    if (!token) {
      cursor = start + 1
      continue
    }
    if (token.kind === 'tag' && !token.closing && RAW_TEXT_TAGS.has(token.name)) {
      const afterRawText = skipRawText(rawText, token.name, token.end, region.end)
      if (afterRawText === undefined) return undefined
      cursor = afterRawText
      continue
    }
    if (token.kind === 'tag' && token.name === 'body') {
      if (!token.closing && bodyStart < 0) bodyStart = token.end
      else if (token.closing && bodyStart >= 0) {
        bodyEnd = start
        break
      }
    }
    cursor = token.end
  }
  if (bodyStart < 0) return { start: region.start, end: region.end }
  if (region.position > bodyEnd) return undefined
  return { start: bodyStart, end: bodyEnd }
}

export function extractDocToc(rawText: string, bytePosition: number): Uint8Array | undefined {
  if (!Number.isSafeInteger(bytePosition) || bytePosition < 0 || bytePosition >= rawText.length) return undefined
  const region = pageRegion(rawText, bytePosition)
  if (!region) return undefined
  const enclosingNav = findEnclosingNav(rawText, region)
  if (enclosingNav === null) return undefined
  if (enclosingNav) return enclosingNav
  const boundedNav = findFollowingNav(rawText, region)
  if (boundedNav) return boundedNav

  const body = bodyRegion(rawText, region)
  if (!body) return undefined
  const tocStart = Math.max(region.position, body.start)
  if (body.end - tocStart > MAX_TOC_REGION_BYTES) return undefined
  const prefix = rawText.slice(body.start, tocStart)
  const toc = rawText.slice(tocStart, body.end)
  if (/<a\b/i.test(prefix) || !/<a\b/i.test(toc)) return undefined
  return bytesFromRawText(toc)
}
