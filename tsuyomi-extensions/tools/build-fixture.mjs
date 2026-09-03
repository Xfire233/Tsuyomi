// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import { createHash, createPrivateKey, createPublicKey, sign } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outputDirectory = resolve(root, 'dist');
const fixtureDirectory = resolve(root, 'fixtures/wenku8');
const entryPath = 'index.mjs';
const entryBytes = await readFile(resolve(outputDirectory, 'modules/wenku8/index.mjs'));
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const canonicalize = (value) => {
  if (value === null || typeof value === 'boolean' || typeof value === 'number') return JSON.stringify(value);
  if (typeof value === 'string') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  if (typeof value === 'object') {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
  }
  throw new TypeError('Unsupported canonical JSON value');
};

const files = { [entryPath]: sha256(entryBytes) };
const contentDigest = sha256(Buffer.from(canonicalize(files), 'utf8'));
const manifest = {
  format: 'tsuyomi-hxp',
  manifestVersion: 1,
  id: 'org.tsuyomi.wenku8',
  version: '0.2.25',
  display: {
    name: 'Wenku8',
    summary: 'Wenku8 阅读与显式远程收藏来源（测试发布者）',
    homepage: 'https://www.wenku8.net',
  },
  hostApi: { minInclusive: '1.1.0', maxExclusive: '2.0.0' },
  entry: entryPath,
  integrity: { algorithm: 'sha256', contentDigest, files },
  signing: { algorithm: 'Ed25519', keyId: 'tsuyomi-phase2-fixture', signatureFile: 'signature.ed25519' },
  capabilities: {
    network: {
      origins: ['https://www.wenku8.net', 'https://img.wenku8.com', 'https://pic.wenku8.com', 'https://pic.777743.xyz'],
      maxConcurrentRequests: 2,
      requestTimeoutMs: 15000,
      maxResponseBytes: 2097152,
    },
    cookies: { mode: 'sourceScoped', origins: ['https://www.wenku8.net'] },
    webLogin: { enabled: true, origins: ['https://www.wenku8.net'] },
    home: { enabled: true },
    remoteLibrary: {
      read: true,
      writeOperations: ['add'],
      policies: {
        read: {
          origin: 'https://www.wenku8.net',
          method: 'GET',
          path: '/modules/article/bookcase.php',
          parameters: { action: { kind: 'fixed', value: 'list' }, cursor: { kind: 'cursor' } },
        },
        add: {
          origin: 'https://www.wenku8.net',
          method: 'POST',
          path: '/modules/article/bookcase.php',
          redirects: [
            {
              origin: 'https://www.wenku8.net',
              method: 'GET',
              path: '/modules/article/bookcase-success.php',
              parameters: { status: { kind: 'fixed', value: 'added' } },
            },
          ],
          parameters: { action: { kind: 'fixed', value: 'add' }, aid: { kind: 'remoteBookId' } },
        },
      },
    },
    storage: { quotaBytes: 1048576 },
  },
  resourceLimits: { maxExecutionWallTimeMs: 15000, maxMemoryBytes: 16777216 },
  update: { channel: 'stable' },
};
const canonicalManifest = Buffer.from(canonicalize(manifest), 'utf8');

// Public, deterministic test-only seed. It is not an official or production signing key.
const seed = Buffer.from(Array.from({ length: 32 }, (_, index) => index + 1));
const privateKey = createPrivateKey({
  key: Buffer.concat([Buffer.from('302e020100300506032b657004220420', 'hex'), seed]),
  format: 'der',
  type: 'pkcs8',
});
const publicKeyDer = createPublicKey(privateKey).export({ format: 'der', type: 'spki' });
const publicKey = publicKeyDer.subarray(publicKeyDer.length - 32);
const signatureMessage = Buffer.concat([
  Buffer.from('tsuyomi-hxp-v1\0', 'ascii'),
  canonicalManifest,
  Buffer.from([0]),
  Buffer.from(contentDigest, 'ascii'),
]);
const signature = sign(null, signatureMessage, privateKey);

const crcTable = Array.from({ length: 256 }, (_, value) => {
  let crc = value;
  for (let bit = 0; bit < 8; bit += 1) crc = (crc & 1) ? (0xedb88320 ^ (crc >>> 1)) : (crc >>> 1);
  return crc >>> 0;
});
const crc32 = (bytes) => {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
};
const zipStore = (entries) => {
  const localParts = [];
  const centralParts = [];
  let offset = 0;
  for (const [name, content] of entries) {
    const nameBytes = Buffer.from(name, 'utf8');
    const crc = crc32(content);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0x21, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(content.length, 18);
    local.writeUInt32LE(content.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    local.writeUInt16LE(0, 28);
    localParts.push(local, nameBytes, content);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(0x0314, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(0x21, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(content.length, 20);
    central.writeUInt32LE(content.length, 24);
    central.writeUInt16LE(nameBytes.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34);
    central.writeUInt16LE(0, 36);
    central.writeUInt32LE(0x81a40000, 38);
    central.writeUInt32LE(offset, 42);
    centralParts.push(central, nameBytes);
    offset += local.length + nameBytes.length + content.length;
  }
  const centralBytes = Buffer.concat(centralParts);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBytes.length, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, centralBytes, end]);
};

await mkdir(outputDirectory, { recursive: true });
await mkdir(fixtureDirectory, { recursive: true });
const archive = zipStore([
  ['manifest.json', Buffer.from(JSON.stringify(manifest, null, 2), 'utf8')],
  [entryPath, entryBytes],
  ['signature.ed25519', signature],
]);
await writeFile(resolve(fixtureDirectory, 'wenku8-fixture.hxp'), archive);
await writeFile(resolve(fixtureDirectory, 'wenku8-fixture.sha256'), `${sha256(archive)}  wenku8-fixture.hxp\n`);
await writeFile(resolve(outputDirectory, 'test-publisher-public-key.bin'), publicKey);
