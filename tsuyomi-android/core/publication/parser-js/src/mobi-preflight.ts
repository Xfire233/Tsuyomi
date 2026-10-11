/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { MAX_EXPANDED_TEXT_BYTES, MAX_INPUT_BYTES, MAX_PDB_RECORDS } from './mobi-limits'

export type MobiKind = 'mobi7' | 'kf8' | 'hybrid'

export interface ComponentInfo {
  startRecord: number
  version: number
  textRecordCount: number
  textLength: number
  boundary?: number
}

export interface AcceptedPreflight {
  accepted: true
  kind: MobiKind
  recordCount: number
  component: ComponentInfo
  protectedComponent?: ComponentInfo
}

export interface RejectedPreflight {
  accepted: false
  code: string
}

export type PreflightResult = AcceptedPreflight | RejectedPreflight

function reject(code: string): RejectedPreflight {
  return { accepted: false, code }
}

export function readUint16(bytes: Uint8Array, offset: number): number {
  return (bytes[offset] << 8) | bytes[offset + 1]
}

export function readUint32(bytes: Uint8Array, offset: number): number {
  return bytes[offset] * 0x1000000 + (bytes[offset + 1] << 16) + (bytes[offset + 2] << 8) + bytes[offset + 3]
}

function hasAscii(bytes: Uint8Array, offset: number, text: string): boolean {
  if (offset < 0 || offset + text.length > bytes.length) return false
  for (let index = 0; index < text.length; index++) {
    if (bytes[offset + index] !== text.charCodeAt(index)) return false
  }
  return true
}

function recordEnd(offsets: number[], recordCount: number, bytesLength: number, index: number): number {
  return index + 1 < recordCount ? offsets[index + 1] : bytesLength
}

function readBoundary(bytes: Uint8Array, recordStart: number, end: number, headerLength: number): number | undefined | null {
  if ((readUint32(bytes, recordStart + 128) & 0x40) === 0) return undefined
  const exthStart = recordStart + 16 + headerLength
  if (!hasAscii(bytes, exthStart, 'EXTH') || exthStart + 12 > end) return null
  const exthLength = readUint32(bytes, exthStart + 4)
  const entryCount = readUint32(bytes, exthStart + 8)
  if (exthLength < 12 || exthLength > end - exthStart || entryCount > MAX_PDB_RECORDS) return null
  const exthEnd = exthStart + exthLength
  let cursor = exthStart + 12
  let boundary: number | undefined
  for (let index = 0; index < entryCount; index++) {
    if (cursor + 8 > exthEnd) return null
    const tag = readUint32(bytes, cursor)
    const entryLength = readUint32(bytes, cursor + 4)
    if (entryLength < 8 || entryLength > exthEnd - cursor) return null
    if (tag === 121) {
      if (entryLength !== 12 || boundary !== undefined) return null
      boundary = readUint32(bytes, cursor + 8)
    }
    cursor += entryLength
  }
  if (cursor !== exthEnd) return null
  return boundary
}

function inspectComponent(
  bytes: Uint8Array,
  offsets: number[],
  recordCount: number,
  startRecord: number,
): ComponentInfo | RejectedPreflight {
  if (!Number.isInteger(startRecord) || startRecord < 0 || startRecord >= recordCount) return reject('malformed-component-boundary')
  const start = offsets[startRecord]
  const end = recordEnd(offsets, recordCount, bytes.length, startRecord)
  if (end - start < 16 + 232 || !hasAscii(bytes, start + 16, 'MOBI')) return reject('missing-mobi-record0')
  if (readUint16(bytes, start + 12) !== 0) return reject('protected-content')

  const headerLength = readUint32(bytes, start + 20)
  if (headerLength < 232 || headerLength > end - start - 16) return reject('malformed-mobi-header')
  const version = readUint32(bytes, start + 36)
  if (version >= 8 && headerLength < 248) return reject('malformed-kf8-header')
  // EXTH commerce/book identifiers are metadata, not an encryption signal. The DRM table is.
  if (headerLength >= 0xA0) {
    const drmOffset = readUint32(bytes, start + 0xA8)
    const drmCount = readUint32(bytes, start + 0xAC)
    if (drmOffset !== 0xFFFFFFFF && drmCount > 0 && drmCount !== 0xFFFFFFFF) return reject('protected-content')
  }
  const textRecordCount = readUint16(bytes, start + 8)
  const textLength = readUint32(bytes, start + 4)
  if (textRecordCount === 0 || textRecordCount > recordCount - startRecord - 1) return reject('malformed-text-records')
  if (textLength > MAX_EXPANDED_TEXT_BYTES) return reject('expanded-text-limit')
  const compression = readUint16(bytes, start)
  if (compression !== 1 && compression !== 2 && compression !== 0x4448) return reject('unsupported-compression')

  const firstTextIndex = startRecord + 1
  const firstTextStart = offsets[firstTextIndex]
  const firstTextEnd = recordEnd(offsets, recordCount, bytes.length, firstTextIndex)
  if (firstTextEnd - firstTextStart < 1) return reject('malformed-text-records')
  if (firstTextEnd - firstTextStart >= 4 && hasAscii(bytes, firstTextStart, '%MOP')) return reject('print-replica')

  if (version >= 8) {
    const fdst = readUint32(bytes, start + 192)
    const fragment = readUint32(bytes, start + 248)
    const skeleton = readUint32(bytes, start + 252)
    if (fdst === 0xFFFFFFFF || fragment === 0xFFFFFFFF || skeleton === 0xFFFFFFFF) return reject('malformed-kf8-index')
    if (fdst >= recordCount - startRecord || fragment >= recordCount - startRecord || skeleton >= recordCount - startRecord) {
      return reject('malformed-kf8-index')
    }
    const fdstStart = offsets[startRecord + fdst]
    const fdstEnd = recordEnd(offsets, recordCount, bytes.length, startRecord + fdst)
    if (fdstEnd - fdstStart < 12 || !hasAscii(bytes, fdstStart, 'FDST')) return reject('malformed-kf8-fdst')
    const fdstEntries = readUint32(bytes, fdstStart + 8)
    if (fdstEntries === 0 || fdstEntries > MAX_PDB_RECORDS || 12 + fdstEntries * 8 > fdstEnd - fdstStart) {
      return reject('malformed-kf8-fdst')
    }
  }

  const info: ComponentInfo = { startRecord, version, textRecordCount, textLength }
  if (startRecord === 0) {
    const boundary = readBoundary(bytes, start, end, headerLength)
    if (boundary === null) return reject('malformed-exth')
    if (boundary !== undefined && boundary !== 0xFFFFFFFF) info.boundary = boundary
  }
  return info
}

function readOffsets(bytes: Uint8Array, recordCount: number): number[] | RejectedPreflight {
  const tableEnd = 78 + recordCount * 8
  if (tableEnd > bytes.length) return reject('malformed-pdb-record-table')
  const offsets: number[] = []
  let previous = tableEnd - 1
  for (let index = 0; index < recordCount; index++) {
    const offset = readUint32(bytes, 78 + index * 8)
    if (offset <= previous || offset >= bytes.length) return reject('malformed-pdb-record-table')
    offsets.push(offset)
    previous = offset
  }
  return offsets
}

export function preflightMobi(bytes: Uint8Array): PreflightResult {
  if (bytes.byteLength > MAX_INPUT_BYTES) return reject('input-too-large')
  if (bytes.byteLength < 78 || !hasAscii(bytes, 60, 'BOOKMOBI')) return reject('unsupported-format')
  const recordCount = readUint16(bytes, 76)
  if (recordCount < 2 || recordCount > MAX_PDB_RECORDS) return reject('malformed-pdb-record-table')
  const offsets = readOffsets(bytes, recordCount)
  if (!Array.isArray(offsets)) return offsets

  const primary = inspectComponent(bytes, offsets, recordCount, 0)
  if ('accepted' in primary) return primary
  if (primary.version >= 8) return { accepted: true, kind: 'kf8', recordCount, component: primary }
  if (primary.boundary !== undefined && primary.boundary > primary.textRecordCount) {
    const protectedComponent = inspectComponent(bytes, offsets, recordCount, primary.boundary)
    if ('accepted' in protectedComponent) return protectedComponent
    if (protectedComponent.version < 8) return reject('malformed-kf8-header')
    return { accepted: true, kind: 'hybrid', recordCount, component: primary, protectedComponent }
  }
  return { accepted: true, kind: 'mobi7', recordCount, component: primary }
}
