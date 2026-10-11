export type Offset = [number, number][]

/**
 * exth, or metadata
 */
export type ExthKey = 100 | 101 | 103 | 104 | 105 | 106 | 108 | 109 |
  110 | 112 | 113 | 121 | 122 | 125 | 126 | 127 | 128 | 129 |
  132 | 201 | 202 | 503 | 524 | 527
export type ExthName =
  'creator' | 'publisher' | 'description' | 'isbn' | 'subject' |
  'date' | 'contributor' | 'rights' | 'subjectCode' | 'source' |
  'asin' | 'boundary' | 'fixedLayout' | 'numResources' |
  'originalResolution' | 'zeroGutter' | 'zeroMargin' | 'coverURI' |
  'regionMagnification' | 'coverOffset' | 'thumbnailOffset' |
  'title' | 'language' | 'pageProgressionDirection'
type ExthType = 'string' | 'uint'
type IsMany = true | false
export interface ExthRecord {
  100: ['creator', 'string', true]
  101: ['publisher', 'string', false]
  103: ['description', 'string', false]
  104: ['isbn', 'string', false]
  105: ['subject', 'string', true]
  106: ['date', 'string', false]
  108: ['contributor', 'string', true]
  109: ['rights', 'string', false]
  110: ['subjectCode', 'string', true]
  112: ['source', 'string', true]
  113: ['asin', 'string', false]
  121: ['boundary', 'uint', false]
  122: ['fixedLayout', 'string', false]
  125: ['numResources', 'uint', false]
  126: ['originalResolution', 'string', false]
  127: ['zeroGutter', 'string', false]
  128: ['zeroMargin', 'string', false]
  129: ['coverURI', 'string', false]
  132: ['regionMagnification', 'string', false]
  201: ['coverOffset', 'uint', false]
  202: ['thumbnailOffset', 'uint', false]
  503: ['title', 'string', false]
  524: ['language', 'string', true]
  527: ['pageProgressionDirection', 'string', false]
}
export type ConvertExthRecord<T extends Record<ExthKey, [ExthName, ExthType, IsMany]>> = {
  [K in Extract<T[keyof T], [string, any, any]>[0]]?:
  Extract<T[keyof T], [K, any, any]>[2] extends true
    ? Extract<T[keyof T], [K, any, any]>[1] extends 'uint'
      ? number[]
      : string[]
    : Extract<T[keyof T], [K, any, any]>[1] extends 'uint'
      ? number
      : string
}
export type Exth = ConvertExthRecord<ExthRecord>

export type DecompressFunc = (data: Uint8Array) => Uint8Array

export type LoadRecordFunc = (index: number) => ArrayBuffer

export interface MobiChapter {
  id: string
  text: string
  start: number
  end: number
  size: number
}
export type MobiSpine = MobiChapter[]

export interface MobiTocItem {
  label: string
  href: string
  children?: MobiTocItem[]
}
export type MobiToc = MobiTocItem[]

export interface IndexTableItem {
  name: string
  tagMap: Record<number, number[]>
}
export type IndexTable = IndexTableItem[]
export type Cncx = Record<string, string>
export interface IndexData { table: IndexTable, cncx: Cncx }

export interface MobiMetadata {
  identifier: string
  title: string
  author: string[]
  publisher: string
  language: string
  published: string
  description: string
  subject: string[]
  rights: string
  contributor: string[]
}

export interface MobiFileInfo {
  fileName: string
}

export interface MobiCssPart {
  id: string
  href: string
}
export interface MobiProcessedChapter {
  html: string
  css: MobiCssPart[]
}

export interface MobiResolvedHref {
  id: string
  selector: string
}

/**
 * kf8 types
 */

export interface SkelTableItem {
  index: number
  name: string
  numFrag: number
  offset: number
  length: number
}
export type SkelTable = SkelTableItem[]

export interface FragTableItem {
  insertOffset: number
  selector: string
  index: number
  offset: number
  length: number
}
export type FragTable = FragTableItem[]

export interface Kf8Chapter {
  id: string
  skel: SkelTableItem
  frags: FragTable
  fragEnd: number
  length: number
  totalLength: number
}
export type Kf8Spine = Kf8Chapter[]

export interface NcxItem {
  index: number
  offset: number
  size: number
  label: string
  headingLevel: number
  pos: number[]
  parent: number
  firstChild: number
  lastChild: number
  children?: NcxItem[]
}
export type Ncx = NcxItem[]

export interface Kf8TocItem {
  label: string
  href: string
  children?: Kf8TocItem[]
}
export type Kf8Toc = Kf8TocItem[]

export interface Kf8GuideItem {
  label: string
  type: string[]
  href: string
}
export type Kf8Guide = Kf8GuideItem[]

export interface Kf8CssPart {
  id: string
  href: string
}
export interface Kf8ProcessedChapter {
  html: string
  css: Kf8CssPart[]
}

export type Kf8ResolvedHref = MobiResolvedHref

export type Kf8Metadata = MobiMetadata
export type Kf8FileInfo = MobiFileInfo
