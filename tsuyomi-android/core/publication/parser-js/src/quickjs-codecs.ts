/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 * Bounded UTF-8 and Windows-1252 codecs required by the pinned parser.
 */
const WINDOWS_1252 = [
  0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
  0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F,
  0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
  0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178,
]

class Utf16Output {
  private readonly chunks: string[] = []
  private readonly units = new Uint16Array(4096)
  private length = 0

  append(codePoint: number): void {
    if (codePoint <= 0xFFFF) this.units[this.length++] = codePoint
    else {
      const scalar = codePoint - 0x10000
      this.units[this.length++] = 0xD800 + (scalar >> 10)
      this.units[this.length++] = 0xDC00 + (scalar & 0x3FF)
    }
    if (this.length >= this.units.length - 1) this.flush()
  }

  finish(): string {
    this.flush()
    return this.chunks.join('')
  }

  private flush(): void {
    if (this.length === 0) return
    this.chunks.push(String.fromCharCode(...this.units.subarray(0, this.length)))
    this.length = 0
  }
}

export function decodeLatin1(bytes: Uint8Array): string {
  const output = new Utf16Output()
  for (const byte of bytes) output.append(byte)
  return output.finish()
}

export function utf8ByteLength(value: string): number {
  let length = 0
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index)
    if (code <= 0x7F) length++
    else if (code <= 0x7FF) length += 2
    else if (code >= 0xD800 && code <= 0xDBFF && index + 1 < value.length) {
      const low = value.charCodeAt(index + 1)
      if (low >= 0xDC00 && low <= 0xDFFF) {
        length += 4
        index++
      } else length += 3
    } else length += 3
  }
  return length
}

export function encodeUtf8(value: string): Uint8Array {
  const bytes = new Uint8Array(utf8ByteLength(value))
  let offset = 0
  for (let index = 0; index < value.length; index++) {
    let code = value.charCodeAt(index)
    if (code >= 0xD800 && code <= 0xDBFF && index + 1 < value.length) {
      const low = value.charCodeAt(index + 1)
      if (low >= 0xDC00 && low <= 0xDFFF) {
        code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00)
        index++
      } else code = 0xFFFD
    } else if (code >= 0xD800 && code <= 0xDFFF) code = 0xFFFD

    if (code <= 0x7F) bytes[offset++] = code
    else if (code <= 0x7FF) {
      bytes[offset++] = 0xC0 | (code >> 6)
      bytes[offset++] = 0x80 | (code & 0x3F)
    } else if (code <= 0xFFFF) {
      bytes[offset++] = 0xE0 | (code >> 12)
      bytes[offset++] = 0x80 | ((code >> 6) & 0x3F)
      bytes[offset++] = 0x80 | (code & 0x3F)
    } else {
      bytes[offset++] = 0xF0 | (code >> 18)
      bytes[offset++] = 0x80 | ((code >> 12) & 0x3F)
      bytes[offset++] = 0x80 | ((code >> 6) & 0x3F)
      bytes[offset++] = 0x80 | (code & 0x3F)
    }
  }
  return bytes
}

function decodeUtf8(bytes: Uint8Array): string {
  const output = new Utf16Output()
  for (let index = 0; index < bytes.length;) {
    const lead = bytes[index]
    if (lead <= 0x7F) {
      output.append(lead)
      index++
      continue
    }

    let needed: number
    let codePoint: number
    let minimum: number
    if (lead >= 0xC2 && lead <= 0xDF) {
      needed = 1
      codePoint = lead & 0x1F
      minimum = 0x80
    } else if (lead >= 0xE0 && lead <= 0xEF) {
      needed = 2
      codePoint = lead & 0x0F
      minimum = 0x800
    } else if (lead >= 0xF0 && lead <= 0xF4) {
      needed = 3
      codePoint = lead & 0x07
      minimum = 0x10000
    } else {
      output.append(0xFFFD)
      index++
      continue
    }

    let consumed = 1
    let valid = true
    for (let step = 1; step <= needed; step++) {
      if (index + step >= bytes.length) {
        valid = false
        consumed = bytes.length - index
        break
      }
      const continuation = bytes[index + step]
      if ((continuation & 0xC0) !== 0x80) {
        valid = false
        break
      }
      codePoint = (codePoint << 6) | (continuation & 0x3F)
      consumed++
    }
    if (valid && (codePoint < minimum || codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF))) {
      valid = false
    }
    output.append(valid ? codePoint : 0xFFFD)
    index += consumed
  }
  return output.finish()
}

function bytesOf(input?: ArrayBuffer | ArrayBufferView): Uint8Array {
  if (input === undefined) return new Uint8Array(0)
  if (input instanceof ArrayBuffer) return new Uint8Array(input)
  if (ArrayBuffer.isView(input)) return new Uint8Array(input.buffer, input.byteOffset, input.byteLength)
  throw new TypeError('TextDecoder input must be an ArrayBuffer or view')
}

export class QuickJsTextDecoder {
  readonly encoding: string

  constructor(encoding = 'utf-8') {
    const normalized = encoding.toLowerCase().replace(/_/g, '-')
    if (normalized === 'utf8' || normalized === 'utf-8') this.encoding = 'utf-8'
    else if (normalized === 'windows-1252' || normalized === 'cp1252' || normalized === 'iso-8859-1' || normalized === 'latin1') {
      this.encoding = 'windows-1252'
    } else throw new RangeError(`Unsupported encoding: ${encoding}`)
  }

  decode(input?: ArrayBuffer | ArrayBufferView): string {
    const bytes = bytesOf(input)
    let value: string
    if (this.encoding === 'windows-1252') {
      const output = new Utf16Output()
      for (const byte of bytes) output.append(byte >= 0x80 && byte <= 0x9F ? WINDOWS_1252[byte - 0x80] : byte)
      value = output.finish()
    } else value = decodeUtf8(bytes)
    if (value.charCodeAt(0) === 0xFEFF) return value.slice(1)
    return value
  }
}

export class QuickJsTextEncoder {
  readonly encoding = 'utf-8'

  encode(value = ''): Uint8Array {
    return encodeUtf8(String(value))
  }
}

const runtime = globalThis as typeof globalThis & {
  TextDecoder?: typeof QuickJsTextDecoder
  TextEncoder?: typeof QuickJsTextEncoder
}
runtime.TextDecoder = QuickJsTextDecoder
runtime.TextEncoder = QuickJsTextEncoder
