import { readFileSync } from 'node:fs'
import path from 'node:path'
import { unzlibSync } from 'fflate'
import type { InputFile } from '@lingo-reader/shared'
import {
  cdicHeader,
  exthHeader,
  fontHeader,
  huffHeader,
  indxHeader,
  tagxHeader,
} from './headers'
import type {
  GetStruct,
  Header,
  MobiHeader,
} from './headers'
import {
  mobiEncoding,
} from './utils'
import type { Exth, ExthKey, ExthRecord, IndexData, LoadRecordFunc, Ncx, NcxItem } from './types'

export function getMobiFileName(file: InputFile): string {
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
}

function bufferToArrayBuffer(buffer: Uint8Array): ArrayBuffer {
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength) as ArrayBuffer
}
export async function toArrayBuffer(file: InputFile): Promise<ArrayBuffer> {
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
}

const decoder = new TextDecoder()
export const getString = (buffer: ArrayBuffer): string => decoder.decode(buffer)
export function getUint(buffer: ArrayBuffer): number {
  const l = buffer.byteLength
  const func = l === 4 ? 'getUint32' : l === 2 ? 'getUint16' : 'getUint8'
  return new DataView(buffer)[func](0)
}

export function getStruct<T extends Header>(def: T, buffer: ArrayBuffer): GetStruct<T> {
  const res = {} as GetStruct<T>
  for (const key in def) {
    const [start, len, type] = def[key] as [number, number, string]
    res[key] = (type === 'string'
      ? getString(buffer.slice(start, start + len))
      : getUint(buffer.slice(start, start + len))
    ) as GetStruct<T>[typeof key]
  }
  return res
}

type TypedArr = Int8Array | Uint8Array | Uint8ClampedArray | Int16Array | Uint16Array | Int32Array | Uint32Array | Float32Array | Float64Array
export function concatTypedArrays<T extends TypedArr>(arrays: T[]): T {
  const totalLength = arrays.reduce((sum, arr) => sum + arr.length, 0)
  const result = new (arrays[0].constructor as any)(totalLength)

  let offset = 0
  for (const array of arrays) {
    result.set(array, offset)
    offset += array.length
  }

  return result
}

export const getDecoder = (x: string) => new TextDecoder(mobiEncoding[x])

export function getVarLen(byteArray: Uint8Array, i = 0) {
  let value = 0
  let length = 0
  for (const byte of byteArray.subarray(i, i + 4)) {
    value = (value << 7) | (byte & 0b111_1111) >>> 0
    length++
    if (byte & 0b1000_0000) {
      break
    }
  }
  return { value, length }
}

export function getVarLenFromEnd(byteArray: Uint8Array): number {
  let value = 0
  for (const byte of byteArray.subarray(-4)) {
    if (byte & 0b1000_0000) {
      value = 0
    }
    value = (value << 7) | (byte & 0b111_1111)
  }
  return value
}

export function countBitsSet(x: number): number {
  let count = 0
  for (; x > 0; x = x >> 1) {
    if ((x & 1) === 1) {
      count++
    }
  }
  return count
}

export function countUnsetEnd(x: number): number {
  let count = 0
  while ((x & 1) === 0) {
    x = x >> 1
    count++
  }
  return count
}

export function decompressPalmDOC(array: Uint8Array): Uint8Array {
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
}

export function huffcdic(mobi: GetStruct<MobiHeader>, loadRecord: (index: number) => ArrayBuffer) {
  const huffRecord = loadRecord(mobi.huffcdic)
  const { magic, offset1, offset2 } = getStruct(huffHeader, huffRecord)
  if (magic !== 'HUFF') {
    throw new Error('Invalid HUFF record')
  }

  // table1 is indexed by byte value
  const table1 = Array.from(
    { length: 256 },
    (_, i) => offset1 + i * 4,
  )
    .map(offset => getUint(huffRecord.slice(offset, offset + 4)))
    .map(x => [x & 0b1000_0000, x & 0b1_1111, x >>> 8])

  // table2 is indexed by code length
  const table2 = [[0, 0], ...Array.from(
    { length: 32 },
    (_, i) => offset2 + i * 8,
  )
    .map(offset => [
      getUint(huffRecord.slice(offset, offset + 4)),
      getUint(huffRecord.slice(offset + 4, offset + 8)),
    ])]

  const dictionary: [Uint8Array, number | boolean][] = []
  for (let i = 1; i < mobi.numHuffcdic; i++) {
    const record = loadRecord(mobi.huffcdic + i)
    const cdic = getStruct(cdicHeader, record)
    if (cdic.magic !== 'CDIC') {
      throw new Error('Invalid CDIC record')
    }
    // `numEntries` is the total number of dictionary data across CDIC records
    // so `n` here is the number of entries in *this* record
    const n = Math.min(1 << cdic.codeLength, cdic.numEntries - dictionary.length)
    const buffer = record.slice(cdic.length)
    for (let i = 0; i < n; i++) {
      const offset = getUint(buffer.slice(i * 2, i * 2 + 2))
      const x = getUint(buffer.slice(offset, offset + 2))
      const length = x & 0x7FFF
      const decompressed = x & 0x8000
      const value = new Uint8Array(buffer.slice(offset + 2, offset + 2 + length))
      dictionary.push([value, decompressed])
    }
  }

  const decompress = (byteArray: Uint8Array): Uint8Array => {
    let output = new Uint8Array()
    const bitLength = byteArray.byteLength * 8
    for (let i = 0; i < bitLength;) {
      const bits = Number(read32Bits(byteArray, i))
      let [found, codeLength, value] = table1[bits >>> 24]
      if (!found) {
        while (bits >>> (32 - codeLength) < table2[codeLength][0])
          codeLength += 1
        value = table2[codeLength][1]
      }
      i += codeLength
      if (i > bitLength) {
        break
      }

      const code = value - (bits >>> (32 - codeLength))
      let [result, decompressed] = dictionary[code]
      if (!decompressed) {
        // the result is itself compressed
        result = decompress(result)
        // cache the result for next time
        dictionary[code] = [result, true]
      }
      output = concatTypedArrays([output, result]) as any
    }
    return output
  }
  return decompress
}

export function read32Bits(byteArray: Uint8Array, from: number): bigint {
  const startByte = from >> 3
  const end = from + 32
  const endByte = end >> 3
  let bits = 0n
  for (let i = startByte; i <= endByte; i++) {
    bits = bits << 8n | BigInt(byteArray[i] ?? 0)
  }
  return (bits >> (8n - BigInt(end & 7))) & 0xFFFFFFFFn
}

export function isMOBI(file: ArrayBuffer) {
  const magic = getString(file.slice(60, 68))
  return magic === 'BOOKMOBI'// || magic === 'TEXtREAd'
}

const exthRecordType: ExthRecord = {
  100: ['creator', 'string', true], // many
  101: ['publisher', 'string', false],
  103: ['description', 'string', false],
  104: ['isbn', 'string', false],
  105: ['subject', 'string', true], // many
  106: ['date', 'string', false],
  108: ['contributor', 'string', true], // many
  109: ['rights', 'string', false],
  110: ['subjectCode', 'string', true], // many
  112: ['source', 'string', true], // many
  113: ['asin', 'string', false],
  121: ['boundary', 'uint', false],
  122: ['fixedLayout', 'string', false],
  125: ['numResources', 'uint', false],
  126: ['originalResolution', 'string', false],
  127: ['zeroGutter', 'string', false],
  128: ['zeroMargin', 'string', false],
  129: ['coverURI', 'string', false],
  132: ['regionMagnification', 'string', false],
  201: ['coverOffset', 'uint', false],
  202: ['thumbnailOffset', 'uint', false],
  503: ['title', 'string', false],
  524: ['language', 'string', true], // many
  527: ['pageProgressionDirection', 'string', false],
}

// metadata
export function getExth(buf: ArrayBuffer, encoding: number): Exth {
  const { magic, count } = getStruct(exthHeader, buf)
  if (magic !== 'EXTH') {
    throw new Error('Invalid EXTH header')
  }

  const decoder = getDecoder(encoding.toString())
  const results: Record<string, Exth[keyof Exth]> = {}
  // exthHeader length is 12
  let offset = 12
  for (let i = 0; i < count; i++) {
    const type = getUint(buf.slice(offset, offset + 4)) as ExthKey
    // header value: type, length, data
    // exth record length, include data.
    const length = getUint(buf.slice(offset + 4, offset + 8))
    if (type in exthRecordType) {
      const [name, typ, ismany] = exthRecordType[type]
      const data = buf.slice(offset + 8, offset + length)
      const value = typ === 'uint' ? getUint(data) : decoder.decode(data)
      if (ismany) {
        results[name] ??= [];
        (results[name] as any[]).push(value)
      }
      else {
        results[name] = value
      }
    }
    offset += length
  }

  return results
}

export function getRemoveTrailingEntries(trailingFlags: number) {
  const multibyte = trailingFlags & 1
  const numTrailingEntries = countBitsSet(trailingFlags >>> 1)

  return (array: Uint8Array): Uint8Array => {
    for (let i = 0; i < numTrailingEntries; i++) {
      const length = getVarLenFromEnd(array)
      array = array.subarray(0, -length)
    }
    if (multibyte) {
      const length = (array[array.length - 1] & 0b11) + 1
      array = array.subarray(0, -length)
    }
    return array
  }
}

export function getFont(buf: ArrayBuffer): Uint8Array {
  const { flags, dataStart, keyLength, keyStart } = getStruct(fontHeader, buf)
  const array = new Uint8Array(buf.slice(dataStart))
  // deobfuscate font
  if (flags & 0b10) {
    const bytes = keyLength === 16 ? 1024 : 1040
    const key = new Uint8Array(buf.slice(keyStart, keyStart + keyLength))
    const length = Math.min(bytes, array.length)
    for (let i = 0; i < length; i++) array[i] = array[i] ^ key[i % key.length]
  }
  // decompress font
  if (flags & 1) {
    try {
      return unzlibSync(array)
    }
    catch (e) {
      console.warn(e)
      console.warn('Failed to decompress font')
    }
  }
  return array
}

export function getIndexData(indxIndex: number, loadRecord: LoadRecordFunc): IndexData {
  const indxRecord = loadRecord(indxIndex)
  const indx = getStruct(indxHeader, indxRecord)
  if (indx.magic !== 'INDX')
    throw new Error('Invalid INDX record')
  const decoder = getDecoder(indx.encoding.toString())

  const cncx: Record<string, string> = {}
  let cncxRecordOffset = 0
  for (let i = 0; i < indx.numCncx; i++) {
    const record = loadRecord(indxIndex + indx.numRecords + i + 1)
    const array = new Uint8Array(record)
    for (let pos = 0; pos < array.byteLength;) {
      const index = pos
      const { value, length } = getVarLen(array, pos)
      pos += length
      const result = record.slice(pos, pos + value)
      pos += value
      cncx[cncxRecordOffset + index] = decoder.decode(result)
    }
    cncxRecordOffset += 0x10000
  }

  const tagxBuffer = indxRecord.slice(indx.length)
  const tagx = getStruct(tagxHeader, tagxBuffer)
  if (tagx.magic !== 'TAGX')
    throw new Error('Invalid TAGX section')
  const numTags = (tagx.length - 12) / 4
  const tagTable = Array.from(
    { length: numTags },
    (_, i) => new Uint8Array(tagxBuffer.slice(12 + i * 4, 12 + i * 4 + 4)),
  )
  const table = []
  for (let i = 0; i < indx.numRecords; i++) {
    const record = loadRecord(indxIndex + 1 + i)
    const array = new Uint8Array(record)
    const indx = getStruct(indxHeader, record)
    if (indx.magic !== 'INDX') {
      throw new Error('Invalid INDX record')
    }
    for (let j = 0; j < indx.numRecords; j++) {
      const offsetOffset = indx.idxt + 4 + 2 * j
      const offset = getUint(record.slice(offsetOffset, offsetOffset + 2))

      const length = getUint(record.slice(offset, offset + 1))
      const name = getString(record.slice(offset + 1, offset + 1 + length))

      const tags: number[][] = []
      const startPos = offset + 1 + length
      let controlByteIndex = 0
      let pos = startPos + tagx.numControlBytes
      for (const [tag, numValues, mask, end] of tagTable) {
        if (end & 1) {
          controlByteIndex++
          continue
        }
        const offset = startPos + controlByteIndex
        const value = getUint(record.slice(offset, offset + 1)) & mask
        if (value === mask) {
          if (countBitsSet(mask) > 1) {
            const { value, length } = getVarLen(array, pos)
            tags.push([tag, 0, value, numValues])
            pos += length
          }
          else {
            tags.push([tag, 1, 0, numValues])
          }
        }
        else {
          tags.push([tag, value >> countUnsetEnd(mask), 0, numValues])
        }
      }

      const tagMap: Record<string, number[]> = {}
      for (const [tag, valueCount, valueBytes, numValues] of tags) {
        const values = []
        if (valueCount !== 0) {
          for (let i = 0; i < valueCount * numValues; i++) {
            const { value, length } = getVarLen(array, pos)
            values.push(value)
            pos += length
          }
        }
        else {
          let count = 0
          while (count < valueBytes) {
            const { value, length } = getVarLen(array, pos)
            values.push(value)
            pos += length
            count += length
          }
        }
        tagMap[tag] = values
      }
      table.push({ name, tagMap })
    }
  }
  return { table, cncx }
}

export function getNCX(indxIndex: number, loadRecord: (index: number) => ArrayBuffer): Ncx {
  const { table, cncx } = getIndexData(indxIndex, loadRecord)
  const items: Ncx = table.map(({ tagMap }, index) => ({
    index,
    offset: tagMap[1]?.[0],
    size: tagMap[2]?.[0],
    label: cncx[tagMap[3]?.[0]] ?? '',
    headingLevel: tagMap[4]?.[0],
    pos: tagMap[6],
    parent: tagMap[21]?.[0],
    firstChild: tagMap[22]?.[0],
    lastChild: tagMap[23]?.[0],
  }))
  const getChildren = (item: NcxItem): NcxItem => {
    if (item.firstChild == null)
      return item
    item.children = items.filter(x => x.parent === item.index).map(getChildren)
    return item
  }
  return items.filter(item => item.headingLevel === 0).map(getChildren)
}

export const mbpPagebreakRegex = /<\s*(?:mbp:)?pagebreak[^>]*>/gi

export function makePosURI(fid: number = 0, off: number = 0): string {
  return `kindle:pos:fid:${fid.toString(32).toUpperCase().padStart(4, '0')
    }:off:${off.toString(32).toUpperCase().padStart(10, '0')}`
}

const selectorReg = /\s(id|name|aid)\s*=\s*['"]([^'"]*)['"]/i
export function getFragmentSelector(str: string): string {
  const match = str.match(selectorReg)
  if (!match) {
    return ''
  }
  const [, attr, value] = match
  return `[${attr}="${value}"]`
}

const kindlePosRegex = /kindle:pos:fid:(\w+):off:(\w+)/
export function parsePosURI(str: string) {
  const [fid, off] = str.match(kindlePosRegex)!.slice(1)
  return {
    fid: Number.parseInt(fid, 32),
    off: Number.parseInt(off, 32),
  }
}

export const kindleResourceRegex = /kindle:(flow|embed):(\w+)(?:\?mime=(\w+\/[-+.\w]+))?/
