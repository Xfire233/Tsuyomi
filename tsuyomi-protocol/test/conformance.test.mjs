// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const loadJson = async (path) => JSON.parse(await readFile(new URL(path, import.meta.url)));
const createAjv = () => {
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  addFormats(ajv);
  return ajv;
};
const compare = (left, right) => (left < right ? -1 : left > right ? 1 : 0);
const transferMaxBytes = 32 * 1024 * 1024;

const transferIssues = (document) => {
  const issues = [];
  if (Buffer.byteLength(JSON.stringify(document), 'utf8') > transferMaxBytes) issues.push('document-size');

  const bookIds = new Set();
  for (const [index, book] of document.library.entries()) {
    const key = `${book.identity.sourceId}\u0000${book.identity.remoteBookId}`;
    if (bookIds.has(key)) issues.push(`duplicate-book:${key}`);
    bookIds.add(key);
    if (index > 0) {
      const previous = document.library[index - 1].identity;
      if (compare(`${previous.sourceId}\u0000${previous.remoteBookId}`, key) > 0) issues.push('library-order');
    }
  }

  const shelves = new Map();
  for (const shelf of document.shelves) {
    if (shelves.has(shelf.id)) issues.push(`duplicate-shelf:${shelf.id}`);
    shelves.set(shelf.id, shelf);
  }
  for (const shelf of document.shelves) {
    if (shelf.parentId && !shelves.has(shelf.parentId)) issues.push(`missing-parent:${shelf.id}`);
    const visited = new Set([shelf.id]);
    let parentId = shelf.parentId;
    while (parentId) {
      if (visited.has(parentId)) {
        issues.push(`shelf-cycle:${shelf.id}`);
        break;
      }
      visited.add(parentId);
      parentId = shelves.get(parentId)?.parentId;
    }
  }
  for (const book of document.library) {
    for (const shelfId of book.shelfIds ?? []) {
      if (!shelves.has(shelfId)) issues.push(`missing-shelf:${shelfId}`);
    }
  }
  return issues;
};

const chooseProgressWinner = ({ stored, incoming, incomingValid }) => {
  if (!incomingValid) return 'stored';
  return Date.parse(incoming.updatedAt) > Date.parse(stored.updatedAt) ? 'incoming' : 'stored';
};

const compareSemver = (left, right) => {
  const parse = (value) => {
    const match = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.exec(value);
    assert.ok(match, `invalid SemVer fixture: ${value}`);
    return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]), prerelease: match[4]?.split('.') ?? [] };
  };
  const compareIdentifier = (leftId, rightId) => {
    const leftNumeric = /^\d+$/.test(leftId);
    const rightNumeric = /^\d+$/.test(rightId);
    if (leftNumeric && rightNumeric) return Number(leftId) - Number(rightId);
    if (leftNumeric) return -1;
    if (rightNumeric) return 1;
    return compare(leftId, rightId);
  };
  const a = parse(left);
  const b = parse(right);
  const core = a.major - b.major || a.minor - b.minor || a.patch - b.patch;
  if (core) return core;
  if (!a.prerelease.length && b.prerelease.length) return 1;
  if (a.prerelease.length && !b.prerelease.length) return -1;
  if (!a.prerelease.length) return 0;
  for (let index = 0; index < Math.max(a.prerelease.length, b.prerelease.length); index += 1) {
    if (a.prerelease[index] === undefined) return -1;
    if (b.prerelease[index] === undefined) return 1;
    const result = compareIdentifier(a.prerelease[index], b.prerelease[index]);
    if (result) return result;
  }
  return 0;
};

const hasCapabilityExpansion = (active, candidate) =>
  candidate.origins.some((origin) => !active.origins.includes(origin)) ||
  (!active.webLogin && candidate.webLogin) ||
  candidate.writes.some((operation) => !active.writes.includes(operation)) ||
  candidate.storageQuota > active.storageQuota;

const packagePolicy = ({ active, candidate, revokedKeyIds, rotationVerified }) => {
  if (revokedKeyIds.includes(candidate.keyId)) return 'rejected-revoked';
  if (compareSemver(candidate.version, active.version) <= 0) return 'rejected-downgrade';
  if (candidate.keyId !== active.keyId && !rotationVerified) return 'rejected-key-rotation';
  return hasCapabilityExpansion(active.capabilities, candidate.capabilities) ? 'requires-grant' : 'accepted';
};

test('transfer semantic conformance accepts the canonical minimal fixture', async () => {
  const document = await loadJson('../fixtures/transfer/valid-minimal.json');
  assert.deepEqual(transferIssues(document), []);
});

test('transfer semantic conformance rejects duplicate stable book identities', async () => {
  const document = await loadJson('../fixtures/transfer/duplicate-book-identity.json');
  assert.ok(transferIssues(document).some((issue) => issue.startsWith('duplicate-book:')));
});

test('transfer semantic conformance requires deterministic library order', async () => {
  const document = await loadJson('../fixtures/transfer/noncanonical-order.json');
  assert.ok(transferIssues(document).includes('library-order'));
});

test('transfer progress conflict cases use newer valid updatedAt only', async () => {
  const fixture = await loadJson('../fixtures/transfer/conformance-progress-conflict.json');
  for (const testCase of fixture.cases) {
    assert.equal(chooseProgressWinner(testCase), testCase.expectedWinner, testCase.name);
  }
});

test('transfer size limit is 32 MiB of UTF-8 JSON', () => {
  const oversized = { library: [], shelves: [], padding: 'x'.repeat(transferMaxBytes) };
  assert.ok(transferIssues(oversized).includes('document-size'));
});

test('HXP host API v1 accepts each valid network value fixture', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-host-api-v1.schema.json'));
  for (const fixturePath of [
    '../fixtures/hxp/valid-network-request.json',
    '../fixtures/hxp/valid-network-response.json',
    '../fixtures/hxp/valid-network-error.json',
  ]) {
    assert.equal(validate(await loadJson(fixturePath)), true, `${fixturePath}: ${ajv.errorsText(validate.errors)}`);
  }
});

test('HXP host API rejects a request body on GET and a semantic key on POST', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-host-api-v1.schema.json'));
  const request = await loadJson('../fixtures/hxp/valid-network-request.json');
  request.utf8Body = 'not permitted';
  assert.equal(validate(request), false);
  delete request.utf8Body;
  request.method = 'POST';
  assert.equal(validate(request), false);
});

test('HXP manifest semantic origins keep cookie and WebView scope within network scope', async () => {
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  const origins = new Set(manifest.capabilities.network.origins);
  assert.ok(manifest.capabilities.cookies.origins.every((origin) => origins.has(origin)));
  assert.ok(manifest.capabilities.webLogin.origins.every((origin) => origins.has(origin)));
  manifest.capabilities.webLogin.origins = ['https://outside.example'];
  assert.equal(manifest.capabilities.webLogin.origins.every((origin) => origins.has(origin)), false);
});

test('HXP package policy conformance covers grants, revocation, rotation, and rollback', async () => {
  const fixture = await loadJson('../fixtures/hxp/package-policy-cases.json');
  for (const testCase of fixture.cases) {
    assert.equal(packagePolicy(testCase), testCase.expected, testCase.name);
  }
});

test('reader document and semantic locator schemas accept their fixtures', async () => {
  const ajv = createAjv();
  for (const [schemaPath, fixturePath] of [
    ['../schemas/reader-document-v1.schema.json', '../fixtures/reader/valid-thread-page-document.json'],
    ['../schemas/reader-locator-v1.schema.json', '../fixtures/reader/valid-reader-locator.json'],
    ['../schemas/forum-navigation-v1.schema.json', '../fixtures/reader/valid-forum-navigation.json'],
  ]) {
    const validate = ajv.compile(await loadJson(schemaPath));
    assert.equal(validate(await loadJson(fixturePath)), true, `${fixturePath}: ${ajv.errorsText(validate.errors)}`);
  }
});

test('reader document rejects raw HTML and locator requires a semantic fallback', async () => {
  const ajv = createAjv();
  const documentValidator = ajv.compile(await loadJson('../schemas/reader-document-v1.schema.json'));
  const document = await loadJson('../fixtures/reader/valid-thread-page-document.json');
  document.rawHtml = '<p>not a reader contract</p>';
  assert.equal(documentValidator(document), false);

  const locatorValidator = ajv.compile(await loadJson('../schemas/reader-locator-v1.schema.json'));
  const locator = await loadJson('../fixtures/reader/valid-reader-locator.json');
  delete locator.blockId;
  delete locator.textAnchorDigest;
  delete locator.characterOffset;
  delete locator.chapterProgress;
  delete locator.bookProgress;
  assert.equal(locatorValidator(locator), false);
});

test('transfer schema excludes arbitrary extension data', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/tsuyomi-transfer-v1.schema.json'));
  const document = await loadJson('../fixtures/transfer/valid-minimal.json');
  document.library[0].extensionData = { unsafe: true };
  assert.equal(validate(document), false);
});

test('HXP package policy honors SemVer prerelease precedence', () => {
  const capabilities = { origins: ['https://a.example'], webLogin: false, writes: [], storageQuota: 1024 };
  assert.equal(packagePolicy({ active: { version: '1.0.0-beta.1', keyId: 'key-a', capabilities }, candidate: { version: '1.0.0', keyId: 'key-a', capabilities }, revokedKeyIds: [], rotationVerified: false }), 'accepted');
  assert.equal(packagePolicy({ active: { version: '1.0.0', keyId: 'key-a', capabilities }, candidate: { version: '1.0.0-beta.2', keyId: 'key-a', capabilities }, revokedKeyIds: [], rotationVerified: false }), 'rejected-downgrade');
});
