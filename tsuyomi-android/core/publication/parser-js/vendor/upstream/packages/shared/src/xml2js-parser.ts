/**
 * The code comes from https://github.com/Leonidas-from-XIV/node-xml2js/blob/master/src/parser.coffee
 */
// @eslint-ignore
import { EventEmitter } from 'events'
import type { Buffer } from 'node:buffer'
import type { SAXParser } from 'sax'
import sax from 'sax'

function stripBOM(str: string): string {
  if (str.charCodeAt(0) === 0xFEFF) {
    return str.slice(1)
  }
  return str
}

const defaults: Record<string, Record<string, any>> = {
  0.1: {
    explicitCharkey: false,
    trim: true,
    // normalize implicates trimming, just so you know
    normalize: true,
    // normalize tag names to lower case
    normalizeTags: false,
    // set default attribute object key
    attrkey: '@',
    // set default char object key
    charkey: '#',
    // always put child nodes in an array
    explicitArray: false,
    // ignore all attributes regardless
    ignoreAttrs: false,
    // merge attributes and child elements onto parent object.  this may cause collisions.
    mergeAttrs: false,
    explicitRoot: false,
    validator: null,
    xmlns: false,
    // fold children elements into dedicated property (works only in 0.2)
    explicitChildren: false,
    childkey: '@@',
    charsAsChildren: false,
    // include white-space only text nodes
    includeWhiteChars: false,
    // callbacks are async? not in 0.1 mode
    async: false,
    strict: true,
    attrNameProcessors: null,
    attrValueProcessors: null,
    tagNameProcessors: null,
    valueProcessors: null,
    emptyTag: '',
  },

  0.2: {
    explicitCharkey: false,
    trim: false,
    normalize: false,
    normalizeTags: false,
    attrkey: '$',
    charkey: '_',
    explicitArray: true,
    ignoreAttrs: false,
    mergeAttrs: false,
    explicitRoot: true,
    validator: null,
    xmlns: false,
    explicitChildren: false,
    preserveChildrenOrder: false,
    childkey: '$$',
    charsAsChildren: false,
    // include white-space only text nodes
    includeWhiteChars: false,
    // not async in 0.2 mode either
    async: false,
    strict: true,
    attrNameProcessors: null,
    attrValueProcessors: null,
    tagNameProcessors: null,
    valueProcessors: null,
    // xml building options
    rootName: 'root',
    xmldec: { version: '1.0', encoding: 'UTF-8', standalone: true },
    doctype: null,
    renderOpts: { pretty: true, indent: '  ', newline: '\n' },
    headless: false,
    chunkSize: 10000,
    emptyTag: '',
    cdata: false,
  },
}

function normalize(str: string): string {
  return str.toLowerCase()
}

// Underscore has a nice function for this, but we try to go without dependencies
function isEmpty(thing: any): boolean {
  return typeof thing === 'object' && thing !== null && Object.keys(thing).length === 0
}

function processItem(processors: Function[], item: any, key?: string): any {
  for (const process of processors) {
    item = process(item, key)
  }
  return item
}

function defineProperty(obj: any, key: string, value: any): void {
  // make sure the descriptor hasn't been prototype polluted
  const descriptor = Object.create(null)
  descriptor.value = value
  descriptor.writable = true
  descriptor.enumerable = true
  descriptor.configurable = true
  Object.defineProperty(obj, key, descriptor)
}

export interface ParserOptions {
  strict?: boolean
  xmlns?: boolean
  normalizeTags?: boolean
  attrkey?: string
  tagNameProcessors?: Function[]
  chunkSize?: number
  explicitArray?: boolean
  attrValueProcessors?: Function[]
  attrNameProcessors?: Function[]
  trim?: boolean
  normalize?: boolean
  mergeAttrs?: boolean
  explicitCharkey?: boolean
  emptyTag?: any
  validator?: Function
  explicitChildren?: boolean
  preserveChildrenOrder?: boolean
  charsAsChildren?: boolean
  includeWhiteChars?: boolean
  childkey?: string
  explicitRoot?: boolean
  [key: string]: any
}

export class Parser extends EventEmitter {
  options: ParserOptions
  remaining: string = ''
  saxParser!: SAXParser
  resultObject: any = null
  EXPLICIT_CHARKEY!: boolean

  // saxParser status
  errThrown: boolean = false
  ended: boolean = false

  constructor(opts?: ParserOptions) {
    super()

    // copy this versions default options
    this.options = {}
    Object.keys(defaults['0.2']).forEach((key) => {
      this.options[key] = defaults['0.2'][key]
    })

    // overwrite them with the specified options, if any
    if (opts) {
      Object.keys(opts).forEach((key) => {
        this.options[key] = opts[key]
      })
    }

    // define the key used for namespaces
    if (this.options.xmlns) {
      this.options.xmlnskey = `${this.options.attrkey}ns`
    }

    if (this.options.normalizeTags) {
      if (!this.options.tagNameProcessors) {
        this.options.tagNameProcessors = []
      }
      this.options.tagNameProcessors.unshift(normalize)
    }

    this.reset()
  }

  processAsync = (): void => {
    try {
      if (this.remaining.length <= this.options.chunkSize!) {
        const chunk = this.remaining
        this.remaining = ''
        this.saxParser = this.saxParser.write(chunk)
        this.saxParser.close()
      }
      else {
        const chunk = this.remaining.slice(0, this.options.chunkSize)
        this.remaining = this.remaining.slice(this.options.chunkSize)
        this.saxParser = this.saxParser.write(chunk)
        setTimeout(this.processAsync, 0)
      }
    }
    catch (err) {
      if (!this.errThrown) {
        this.errThrown = true
        this.emit('error', err)
      }
    }
  }

  assignOrPush = (obj: any, key: string, newValue: any): void => {
    if (!(key in obj)) {
      if (!this.options.explicitArray) {
        defineProperty(obj, key, newValue)
      }
      else {
        defineProperty(obj, key, [newValue])
      }
    }
    else {
      if (!(Array.isArray(obj[key]))) {
        defineProperty(obj, key, [obj[key]])
      }
      obj[key].push(newValue)
    }
  }

  reset = (): void => {
    this.removeAllListeners()

    // make the SAX parser. tried trim and normalize, but they are not very helpful
    this.saxParser = sax.parser(this.options.strict, {
      trim: false,
      normalize: false,
      xmlns: this.options.xmlns,
    })

    // emit one error event if the sax parser fails
    this.errThrown = false
    this.saxParser.onerror = (error: Error) => {
      this.saxParser.resume()
      if (!this.errThrown) {
        this.errThrown = true
        this.emit('error', error)
      }
    }

    this.saxParser.onend = () => {
      if (!this.ended) {
        this.ended = true
        this.emit('end', this.resultObject)
      }
    }

    // another hack to avoid throwing exceptions when the parsing has ended
    // but the user-supplied callback throws an error
    this.ended = false

    // always use the '#' key, even if there are no subkeys
    this.EXPLICIT_CHARKEY = this.options.explicitCharkey!
    this.resultObject = null
    const stack: any[] = []

    // aliases, so we don't have to type so much
    const attrkey = this.options.attrkey
    const charkey = this.options.charkey

    this.saxParser.onopentag = (node: any) => {
      const obj: any = {}
      obj[charkey] = ''

      if (!this.options.ignoreAttrs) {
        Object.keys(node.attributes).forEach((key) => {
          if (!(attrkey! in obj) && !this.options.mergeAttrs) {
            obj[attrkey!] = {}
          }
          const newValue = this.options.attrValueProcessors
            ? processItem(this.options.attrValueProcessors, node.attributes[key], key)
            : node.attributes[key]
          const processedKey = this.options.attrNameProcessors
            ? processItem(this.options.attrNameProcessors, key)
            : key

          if (this.options.mergeAttrs) {
            this.assignOrPush(obj, processedKey, newValue)
          }
          else {
            defineProperty(obj[attrkey!], processedKey, newValue)
          }
        })
      }

      // need a place to store the node name
      obj['#name'] = this.options.tagNameProcessors
        ? processItem(this.options.tagNameProcessors, node.name)
        : node.name

      if (this.options.xmlns) {
        obj[this.options.xmlnskey] = { uri: node.uri, local: node.local }
      }

      stack.push(obj)
    }

    this.saxParser.onclosetag = () => {
      let obj = stack.pop()
      const nodeName = obj['#name']
      if (!this.options.explicitChildren || !this.options.preserveChildrenOrder) {
        delete obj['#name']
      }

      let cdata: any
      if (obj.cdata === true) {
        cdata = obj.cdata
        delete obj.cdata
      }

      const s = stack[stack.length - 1]

      let emptyStr: string = ''
      // remove the '#' key altogether if it's blank
      if (obj[charkey].match(/^\s*$/) && !cdata) {
        emptyStr = obj[charkey]
        delete obj[charkey]
      }
      else {
        if (this.options.trim) {
          obj[charkey] = obj[charkey].trim()
        }
        if (this.options.normalize) {
          obj[charkey] = obj[charkey].replace(/\s{2,}/g, ' ').trim()
        }
        obj[charkey] = this.options.valueProcessors
          ? processItem(this.options.valueProcessors, obj[charkey], nodeName)
          : obj[charkey]

        // also do away with '#' key altogether, if there's no subkeys
        // unless EXPLICIT_CHARKEY is set
        if (Object.keys(obj).length === 1 && charkey in obj && !this.EXPLICIT_CHARKEY) {
          obj = obj[charkey]
        }
      }

      if (isEmpty(obj)) {
        if (typeof this.options.emptyTag === 'function') {
          obj = this.options.emptyTag()
        }
        else {
          obj = this.options.emptyTag !== '' ? this.options.emptyTag : emptyStr
        }
      }

      if (this.options.validator) {
        const xpath = `/${stack.map(node => node['#name']).concat(nodeName).join('/')}`
          // Wrap try/catch with an inner function to allow V8 to optimise the containing function
          ; (() => {
          try {
            obj = this.options.validator(xpath, s && s[nodeName], obj)
          }
          catch (err) {
            this.emit('error', err)
          }
        })()
      }

      // put children into <childkey> property and unfold chars if necessary
      if (this.options.explicitChildren && !this.options.mergeAttrs && typeof obj === 'object') {
        if (!this.options.preserveChildrenOrder) {
          const node: any = {}
          // separate attributes
          if (this.options.attrkey! in obj) {
            node[this.options.attrkey!] = obj[this.options.attrkey!]
            delete obj[this.options.attrkey!]
          }
          // separate char data
          if (!this.options.charsAsChildren && this.options.charkey in obj) {
            node[this.options.charkey] = obj[this.options.charkey]
            delete obj[this.options.charkey]
          }

          if (Object.getOwnPropertyNames(obj).length > 0) {
            node[this.options.childkey!] = obj
          }

          obj = node
        }
        else if (s) {
          // append current node onto parent's <childKey> array
          s[this.options.childkey!] = s[this.options.childkey!] || []
          // push a clone so that the node in the children array can receive the #name property
          const objClone: any = {}
          Object.keys(obj).forEach((key) => {
            defineProperty(objClone, key, obj[key])
          })
          s[this.options.childkey!].push(objClone)
          delete obj['#name']
          // re-check whether we can collapse the node now to just the charkey value
          if (Object.keys(obj).length === 1 && charkey in obj && !this.EXPLICIT_CHARKEY) {
            obj = obj[charkey]
          }
        }
      }

      // check whether we closed all the open tags
      if (stack.length > 0) {
        this.assignOrPush(s, nodeName, obj)
      }
      else {
        // if explicitRoot was specified, wrap stuff in the root tag name
        if (this.options.explicitRoot) {
          // avoid circular references
          const old = obj
          const newObj: any = {}
          defineProperty(newObj, nodeName, old)
          obj = newObj
        }

        this.resultObject = obj
        // parsing has ended, mark that so we won't throw exceptions from here anymore
        this.ended = true
        this.emit('end', this.resultObject)
      }
    }

    const ontext = (text: string) => {
      const s = stack[stack.length - 1]
      if (s) {
        s[charkey] += text

        if (this.options.explicitChildren
          && this.options.preserveChildrenOrder
          && this.options.charsAsChildren
          && (this.options.includeWhiteChars || text.replace(/\\n/g, '').trim() !== '')) {
          s[this.options.childkey!] = s[this.options.childkey!] || []
          const charChild: any = {
            '#name': '__text__',
          }
          charChild[charkey] = text
          if (this.options.normalize) {
            charChild[charkey] = charChild[charkey].replace(/\s{2,}/g, ' ').trim()
          }
          s[this.options.childkey!].push(charChild)
        }
      }
      return s
    }

    this.saxParser.ontext = ontext
    this.saxParser.oncdata = (text: string) => {
      const s = ontext(text)
      if (s) {
        s.cdata = true
      }
    }
  }

  parseString = (str: string | Uint8Array, cb?: CallBack): any => {
    if (cb && typeof cb === 'function') {
      this.on('end', (result) => {
        this.reset()
        cb(null, result)
      })
      this.on('error', (err) => {
        this.reset()
        cb(err)
      })
    }

    try {
      str = str.toString()
      if (str.trim() === '') {
        this.emit('end', null)
        return true
      }

      str = stripBOM(str)
      if (this.options.async) {
        this.remaining = str
        setTimeout(this.processAsync, 0)
        return this.saxParser
      }
      return this.saxParser.write(str).close()
    }
    catch (err) {
      if (!this.errThrown && !this.ended) {
        this.emit('error', err)
        this.errThrown = true
      }
      else if (this.ended) {
        throw err
      }
    }
  }

  parseStringPromise = (str: string | Uint8Array): Promise<any> => {
    return new Promise((resolve, reject) => {
      this.parseString(str, (err, value) => {
        if (err) {
          reject(err)
        }
        else {
          resolve(value)
        }
      })
    })
  }
}

type CallBack = (err: Error | null, result?: any) => void

export function parseString(str: string | Buffer, a?: ParserOptions | CallBack, b?: CallBack): void {
  // let's determine what we got as arguments
  let options: ParserOptions = {}
  let cb: CallBack | undefined

  if (b !== undefined) {
    if (typeof b === 'function') {
      cb = b
    }
    if (typeof a === 'object') {
      options = a as ParserOptions
    }
  }
  else {
    // well, b is not set, so a has to be a callback
    if (typeof a === 'function') {
      cb = a
    }
    // and options should be empty - default
    options = {}
  }

  // the rest is super-easy
  const parser = new Parser(options)
  parser.parseString(str, cb)
}

export function parseStringPromise(str: string | Uint8Array, a?: ParserOptions): Promise<any> {
  let options: ParserOptions = {}
  if (typeof a === 'object') {
    options = a
  }

  const parser = new Parser(options)
  return parser.parseStringPromise(str)
}
