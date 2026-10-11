/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { encodeUtf8, QuickJsTextDecoder, utf8ByteLength } from './quickjs-codecs'
import { MAX_RESOURCES, MAX_RESOURCE_BYTES, MAX_TOTAL_RESOURCE_BYTES } from './mobi-limits'

export interface HostResourceMetadata {
  resourceId: string
  mimeType: string
  sizeBytes: number
}

interface StoredResource extends HostResourceMetadata {
  bytes: Uint8Array
}

const RESOURCE_PREFIX = 'tsuyomi-resource://'
const resources = new Map<string, StoredResource>()
const utf8Decoder = new QuickJsTextDecoder('utf-8')
let totalResourceBytes = 0

export function beginResources(): void {
  resources.clear()
  totalResourceBytes = 0
}

export function clearResources(): void {
  resources.clear()
  totalResourceBytes = 0
}

// FNV-1a tags only the immutable MIME, not rewritten HTML/CSS or resource bytes.
// Keep the original address visible in the ID; check full MIME on any tag collision.
function stableResourceId(filename: string, mimeType: string): string {
  let address: string
  if (filename === 'cover') {
    address = 'c'
  } else {
    const mobi = /^[0-9]+$/.test(filename)
    const kf8 = /^(flow|embed):([0-9]+)$/.exec(filename)
    if (!mobi && kf8 === null) throw new TypeError('INVALID_RESOURCE_ID')
    const index = Number(mobi ? filename : kf8![2])
    const kind = mobi ? 'm' : kf8![1] === 'flow' ? 'f' : 'e'
    if (!Number.isSafeInteger(index) || index < (kind === 'f' ? 0 : 1)) {
      throw new TypeError('INVALID_RESOURCE_ID')
    }
    address = kind + index.toString(32)
  }
  let high = 0xcbf29ce4
  let low = 0x84222325
  for (let i = 0; i < mimeType.length; i++) {
    low = (low ^ mimeType.charCodeAt(i)) >>> 0
    const product = low * 0x1b3
    high = (Math.imul(high, 0x1b3) + Math.imul(low, 0x100) + Math.floor(product / 0x100000000)) >>> 0
    low = product >>> 0
  }
  return `r${address}_${high.toString(16).padStart(8, '0')}${low.toString(16).padStart(8, '0')}`
}

export function saveResource(data: Uint8Array | string, type: string, filename: string): string {
  const byteLength = typeof data === 'string' ? utf8ByteLength(data) : data.byteLength
  if (byteLength === 0 || byteLength > MAX_RESOURCE_BYTES) throw new RangeError('RESOURCE_LIMIT')
  // Kindle resource URIs may use image/jpg; the native verifier uses canonical JPEG MIME.
  const mimeType = type === 'unknown' ? 'application/octet-stream' : type === 'image/jpg' ? 'image/jpeg' : type
  if (!/^[a-z0-9][a-z0-9!#$&^_.+-]*\/[a-z0-9][a-z0-9!#$&^_.+-]*$/i.test(mimeType)) {
    throw new TypeError('INVALID_RESOURCE_MIME')
  }
  const resourceId = stableResourceId(filename, mimeType)
  const reference = `${RESOURCE_PREFIX}${resourceId}`
  const existing = resources.get(reference)
  if (existing !== undefined) {
    if (existing.mimeType !== mimeType || existing.sizeBytes !== byteLength) throw new TypeError('INVALID_RESOURCE_ID')
    return reference
  }
  if (resources.size >= MAX_RESOURCES || totalResourceBytes > MAX_TOTAL_RESOURCE_BYTES - byteLength) {
    throw new RangeError('RESOURCE_LIMIT')
  }
  const bytes = typeof data === 'string' ? encodeUtf8(data) : data.slice()
  resources.set(reference, { resourceId, mimeType, sizeBytes: bytes.byteLength, bytes })
  totalResourceBytes += bytes.byteLength
  return reference
}

export function readResourceText(reference: string): string | undefined {
  const resource = resources.get(reference)
  if (resource === undefined) return undefined
  return utf8Decoder.decode(resource.bytes)
}

export function resourceIdFromReference(reference: string): string | undefined {
  if (!reference.startsWith(RESOURCE_PREFIX)) return undefined
  return resources.get(reference)?.resourceId
}

export function resourceMetadata(resourceId: string): HostResourceMetadata | undefined {
  if (!/^[A-Za-z0-9_-]{1,32}$/.test(resourceId)) return undefined
  for (const resource of resources.values()) {
    if (resource.resourceId === resourceId) {
      return { resourceId: resource.resourceId, mimeType: resource.mimeType, sizeBytes: resource.sizeBytes }
    }
  }
  return undefined
}

export function resourceBytes(resourceId: string): Uint8Array | undefined {
  if (!/^[A-Za-z0-9_-]{1,32}$/.test(resourceId)) return undefined
  for (const resource of resources.values()) {
    if (resource.resourceId === resourceId) return resource.bytes
  }
  return undefined
}
