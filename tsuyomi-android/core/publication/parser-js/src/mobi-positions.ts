/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 * MOBI filepos values address the decompressed byte stream, not decoded UTF-16 text.
 */
import { MAX_SECTION_TEXT_BYTES } from './mobi-limits'
import { decodeLatin1 } from './quickjs-codecs'

const MAX_POSITION_TARGETS = 100_000

export function mobiPositionTargets(rawText: string, authoredPositions: number[]): number[] {
  const targets = new Set<number>()
  const addPosition = (position: number): void => {
    if (!Number.isSafeInteger(position) || position < 0 || position >= rawText.length) {
      throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_INPUT')
    }
    targets.add(position)
    if (targets.size > MAX_POSITION_TARGETS) throw new Error('TSUYOMI_PUB_ERROR:SECTION_LIMIT')
  }
  for (const position of authoredPositions) addPosition(position)
  const tags = /<(?:a|reference)\b(?:[^"'<>]|"[^"]*"|'[^']*')*>/gi
  for (const tag of rawText.matchAll(tags)) {
    const attribute = /(?:^|\s)(?:filepos\s*=\s*(?:"([0-9]+)"|'([0-9]+)'|([0-9]+)(?=\s|\/?\s*>))|href\s*=\s*(?:"filepos:([0-9]+)"|'filepos:([0-9]+)'|filepos:([0-9]+)(?=\s|\/?\s*>)))/i.exec(tag[0])
    if (attribute === null) continue
    const position = Number(attribute.slice(1).find((value) => value !== undefined))
    addPosition(position)
  }
  return [...targets].sort((left, right) => left - right)
}

export function decodeMobiChapter(
  bytes: Uint8Array,
  start: number,
  end: number,
  positions: number[],
  decoder: TextDecoder,
): string {
  if (positions.length === 0) return decoder.decode(bytes.subarray(start, end))
  const markers: Array<{ offset: number, text: string }> = []
  let markerBytes = 0
  let cursor = start
  let inTag = false
  let quote = 0
  let inEntity = false
  for (const position of positions) {
    // A discarded pagebreak has no visible content: its byte target is the next chapter's start.
    const offset = Math.max(start, position)
    while (cursor < offset) {
      const byte = bytes[cursor++]
      if (inTag) {
        if (quote !== 0) {
          if (byte === quote) quote = 0
        } else if (byte === 34 || byte === 39) quote = byte
        else if (byte === 62) inTag = false
      } else if (byte === 60) {
        inTag = true
        inEntity = false
      } else if (byte === 38) inEntity = true
      else if (byte !== 35 && !(byte >= 48 && byte <= 57) && !(byte >= 65 && byte <= 90) && !(byte >= 97 && byte <= 122)) inEntity = false
    }
    let entityEnd = offset
    if (inEntity) {
      while (entityEnd < Math.min(end, offset + 32)) {
        const byte = bytes[entityEnd]
        if (!(byte >= 48 && byte <= 57) && !(byte >= 65 && byte <= 90) && !(byte >= 97 && byte <= 122)) break
        entityEnd++
      }
    }
    const insideEntity = inEntity && bytes[entityEnd] === 59
    if (inTag || insideEntity || (decoder.encoding === 'utf-8' && (bytes[offset] & 0xC0) === 0x80)) {
      throw new Error('TSUYOMI_PUB_ERROR:MALFORMED_INPUT')
    }
    // Preserve an already-authored exact target instead of introducing a duplicate id.
    if (bytes[offset] === 60) {
      let tagEnd = offset + 1
      let tagQuote = 0
      while (tagEnd < end) {
        const byte = bytes[tagEnd]
        if (tagQuote !== 0) {
          if (byte === tagQuote) tagQuote = 0
        } else if (byte === 34 || byte === 39) tagQuote = byte
        else if (byte === 62) break
        tagEnd++
      }
      const tag = decodeLatin1(bytes.subarray(offset, tagEnd + 1))
      const id = /(?:^|\s)id\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))/i.exec(tag)
      if ((id?.[1] ?? id?.[2] ?? id?.[3]) === `filepos:${position}`) continue
    }
    const text = `<span id="filepos:${position}"></span>`
    markers.push({ offset, text })
    markerBytes += text.length
  }
  const length = end - start + markerBytes
  if (length > MAX_SECTION_TEXT_BYTES) throw new Error('TSUYOMI_PUB_ERROR:TEXT_LIMIT')
  const projected = new Uint8Array(length)
  let sourceOffset = start
  let outputOffset = 0
  for (const marker of markers) {
    const chunk = bytes.subarray(sourceOffset, marker.offset)
    projected.set(chunk, outputOffset)
    outputOffset += chunk.length
    for (let index = 0; index < marker.text.length; index++) projected[outputOffset++] = marker.text.charCodeAt(index)
    sourceOffset = marker.offset
  }
  projected.set(bytes.subarray(sourceOffset, end), outputOffset)
  return decoder.decode(projected)
}
