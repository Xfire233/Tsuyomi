/**
 * headers
 */
type ValueType = 'string' | 'uint'
type HeaderValue<T extends ValueType> = [number, number, T]

export interface PdbHeader {
  name: HeaderValue<'string'>
  type: HeaderValue<'string'>
  creator: HeaderValue<'string'>
  numRecords: HeaderValue<'uint'>
}

export interface PalmdocHeader {
  compression: HeaderValue<'uint'>
  numTextRecords: HeaderValue<'uint'>
  recordSize: HeaderValue<'uint'>
  encryption: HeaderValue<'uint'>
}

export interface MobiHeader {
  magic: HeaderValue<'string'>
  length: HeaderValue<'uint'>
  type: HeaderValue<'uint'>
  encoding: HeaderValue<'uint'>
  uid: HeaderValue<'uint'>
  version: HeaderValue<'uint'>
  titleOffset: HeaderValue<'uint'>
  titleLength: HeaderValue<'uint'>
  localeRegion: HeaderValue<'uint'>
  localeLanguage: HeaderValue<'uint'>
  resourceStart: HeaderValue<'uint'>
  huffcdic: HeaderValue<'uint'>
  numHuffcdic: HeaderValue<'uint'>
  exthFlag: HeaderValue<'uint'>
  trailingFlags: HeaderValue<'uint'>
  indx: HeaderValue<'uint'>
}

export interface MobiHeaderExtends {
  title: string
  language: string
}

export interface Kf8Header {
  resourceStart: HeaderValue<'uint'>
  fdst: HeaderValue<'uint'>
  numFdst: HeaderValue<'uint'>
  frag: HeaderValue<'uint'>
  skel: HeaderValue<'uint'>
  guide: HeaderValue<'uint'>
}

export interface ExthHeader {
  magic: HeaderValue<'string'>
  length: HeaderValue<'uint'>
  count: HeaderValue<'uint'>
}

export interface IndxHeader {
  magic: HeaderValue<'string'>
  length: HeaderValue<'uint'>
  type: HeaderValue<'uint'>
  idxt: HeaderValue<'uint'>
  numRecords: HeaderValue<'uint'>
  encoding: HeaderValue<'uint'>
  language: HeaderValue<'uint'>
  total: HeaderValue<'uint'>
  ordt: HeaderValue<'uint'>
  ligt: HeaderValue<'uint'>
  numLigt: HeaderValue<'uint'>
  numCncx: HeaderValue<'uint'>
}

export interface TagxHeader {
  magic: HeaderValue<'string'>
  length: HeaderValue<'uint'>
  numControlBytes: HeaderValue<'uint'>
}

export interface HuffHeader {
  magic: HeaderValue<'string'>
  offset1: HeaderValue<'uint'>
  offset2: HeaderValue<'uint'>
}

export interface CdicHeader {
  magic: HeaderValue<'string'>
  length: HeaderValue<'uint'>
  numEntries: HeaderValue<'uint'>
  codeLength: HeaderValue<'uint'>
}

export interface FdstHeader {
  magic: HeaderValue<'string'>
  numEntries: HeaderValue<'uint'>
}

export interface FontHeader {
  flags: HeaderValue<'uint'>
  dataStart: HeaderValue<'uint'>
  keyLength: HeaderValue<'uint'>
  keyStart: HeaderValue<'uint'>
}

export type Header = PdbHeader | PalmdocHeader | MobiHeader
  | Kf8Header | ExthHeader | IndxHeader | TagxHeader | HuffHeader
  | CdicHeader | FdstHeader | FontHeader

export type GetStruct<T extends Header> = {
  [K in keyof T]: T[K] extends HeaderValue<ValueType>
    ? (T[K][2] extends 'string' ? string : number)
    : never
}

export const pdbHeader: PdbHeader = {
  name: [0, 32, 'string'],
  type: [60, 4, 'string'],
  creator: [64, 4, 'string'],
  numRecords: [76, 2, 'uint'],
}

export const palmdocHeader: PalmdocHeader = {
  compression: [0, 2, 'uint'],
  numTextRecords: [8, 2, 'uint'],
  recordSize: [10, 2, 'uint'],
  encryption: [12, 2, 'uint'],
}

export const mobiHeader: MobiHeader = {
  magic: [16, 4, 'string'],
  length: [20, 4, 'uint'],
  type: [24, 4, 'uint'],
  encoding: [28, 4, 'uint'],
  uid: [32, 4, 'uint'],
  version: [36, 4, 'uint'],
  titleOffset: [84, 4, 'uint'],
  titleLength: [88, 4, 'uint'],
  localeRegion: [94, 1, 'uint'],
  localeLanguage: [95, 1, 'uint'],
  resourceStart: [108, 4, 'uint'],
  huffcdic: [112, 4, 'uint'],
  numHuffcdic: [116, 4, 'uint'],
  exthFlag: [128, 4, 'uint'],
  trailingFlags: [240, 4, 'uint'],
  indx: [244, 4, 'uint'],
}

export const kf8Header: Kf8Header = {
  resourceStart: [108, 4, 'uint'],
  fdst: [192, 4, 'uint'],
  numFdst: [196, 4, 'uint'],
  frag: [248, 4, 'uint'],
  skel: [252, 4, 'uint'],
  guide: [260, 4, 'uint'],
}

export const exthHeader: ExthHeader = {
  magic: [0, 4, 'string'],
  length: [4, 4, 'uint'],
  count: [8, 4, 'uint'],
}

export const indxHeader: IndxHeader = {
  magic: [0, 4, 'string'],
  length: [4, 4, 'uint'],
  type: [8, 4, 'uint'],
  idxt: [20, 4, 'uint'],
  numRecords: [24, 4, 'uint'],
  encoding: [28, 4, 'uint'],
  language: [32, 4, 'uint'],
  total: [36, 4, 'uint'],
  ordt: [40, 4, 'uint'],
  ligt: [44, 4, 'uint'],
  numLigt: [48, 4, 'uint'],
  numCncx: [52, 4, 'uint'],
}

export const tagxHeader: TagxHeader = {
  magic: [0, 4, 'string'],
  length: [4, 4, 'uint'],
  numControlBytes: [8, 4, 'uint'],
}

export const huffHeader: HuffHeader = {
  magic: [0, 4, 'string'],
  offset1: [8, 4, 'uint'],
  offset2: [12, 4, 'uint'],
}

export const cdicHeader: CdicHeader = {
  magic: [0, 4, 'string'],
  length: [4, 4, 'uint'],
  numEntries: [8, 4, 'uint'],
  codeLength: [12, 4, 'uint'],
}

export const fdstHeader: FdstHeader = {
  magic: [0, 4, 'string'],
  numEntries: [8, 4, 'uint'],
}

export const fontHeader: FontHeader = {
  flags: [8, 4, 'uint'],
  dataStart: [12, 4, 'uint'],
  keyLength: [16, 4, 'uint'],
  keyStart: [20, 4, 'uint'],
}
