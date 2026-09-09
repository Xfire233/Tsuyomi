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

for (const { label, schemaPath, fixturePath } of [
  {
    label: 'tsuyomi-transfer v1',
    schemaPath: '../schemas/tsuyomi-transfer-v1.schema.json',
    fixturePath: '../fixtures/transfer/valid-minimal.json',
  },
  {
    label: 'tsuyomi-transfer v2',
    schemaPath: '../schemas/tsuyomi-transfer-v2.schema.json',
    fixturePath: '../fixtures/transfer/valid-v2.json',
  },
  {
    label: 'hxp manifest v1',
    schemaPath: '../schemas/hxp-manifest-v1.schema.json',
    fixturePath: '../fixtures/hxp/valid-minimal-manifest.json',
  },
  {
    label: 'hxp update check v2',
    schemaPath: '../schemas/hxp-update-check-v2.schema.json',
    fixturePath: '../fixtures/hxp/valid-update-check-v2.json',
  },
]) {
  test(`${label} accepts its valid fixture`, async () => {
    const ajv = createAjv();
    const validate = ajv.compile(await loadJson(schemaPath));
    assert.equal(validate(await loadJson(fixturePath)), true, ajv.errorsText(validate.errors));
  });
}

test('tsuyomi-transfer v1 rejects a record without a stable remote identity', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/tsuyomi-transfer-v1.schema.json'));
  const document = await loadJson('../fixtures/transfer/valid-minimal.json');
  delete document.library[0].identity.remoteBookId;
  assert.equal(validate(document), false);
});

test('tsuyomi-transfer v1 rejects v2-only completion and reader fields', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/tsuyomi-transfer-v1.schema.json'));
  const document = await loadJson('../fixtures/transfer/valid-minimal.json');
  document.library[0].completedChapterIds = ['67889'];
  document.preferences = { reader: { horizontalMargin: 32 } };
  assert.equal(validate(document), false);
});

test('hxp manifest v1 rejects non-HTTPS network origins', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  manifest.capabilities.network.origins = ['http://www.wenku8.net'];
  assert.equal(validate(manifest), false);
});

test('hxp manifest v1 rejects undeclared remote-library operations', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  manifest.capabilities.remoteLibrary.writeOperations.push('replace');
  assert.equal(validate(manifest), false);
});

test('hxp manifest v1 keeps source Home optional and rejects source-controlled layout', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const withoutHome = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  delete withoutHome.capabilities.home;
  assert.equal(validate(withoutHome), true, ajv.errorsText(validate.errors));

  const injectedLayout = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  injectedLayout.capabilities.home.layout = 'source-controlled';
  assert.equal(validate(injectedLayout), false);
});

test('hxp update check v2 requires explicit complete source-order evidence', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-update-check-v2.schema.json'));
  assert.equal(validate(await loadJson('../fixtures/hxp/valid-update-check-v2.json')), true, ajv.errorsText(validate.errors));
  for (const fixture of [
    'invalid-update-check-v2-raw-url.json',
    'invalid-update-check-v2-incomplete.json',
    'invalid-update-check-v2-missing-order.json',
  ]) {
    assert.equal(validate(await loadJson(`../fixtures/hxp/${fixture}`)), false, fixture);
  }
});

test('hxp manifest v1 permits only the signed read-only update check grammar', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  manifest.capabilities.updateCheck = {
    version: 2,
    origin: 'https://www.wenku8.net',
    method: 'GET',
    path: '/modules/article/reader.php',
    parameters: { aid: { kind: 'remoteBookId' } },
  };
  assert.equal(validate(manifest), true, ajv.errorsText(validate.errors));
  manifest.capabilities.updateCheck.method = 'POST';
  assert.equal(validate(manifest), false);
  manifest.capabilities.updateCheck.method = 'GET';
  manifest.capabilities.updateCheck.parameters.aid = { kind: 'cursor' };
  assert.equal(validate(manifest), false);
});

test('hxp manifest v1 requires signed policies for remote read, targets, and writes', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  for (const policy of ['read', 'targets', 'add', 'remove', 'move']) {
    const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
    delete manifest.capabilities.remoteLibrary.policies[policy];
    assert.equal(validate(manifest), false, `${policy} policy must be required`);
  }
});

test('hxp remote fixed parameter rule requires its exact literal', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  delete manifest.capabilities.remoteLibrary.policies.add.parameters.action.value;
  assert.equal(validate(manifest), false);
});

test('hxp remote redirect aliases are fixed GET destinations', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  assert.equal(validate(manifest), true, ajv.errorsText(validate.errors));
  manifest.capabilities.remoteLibrary.policies.add.redirects[0].method = 'POST';
  assert.equal(validate(manifest), false);
  manifest.capabilities.remoteLibrary.policies.add.redirects[0].method = 'GET';
  manifest.capabilities.remoteLibrary.policies.add.redirects[0].parameters.aid = { kind: 'remoteBookId' };
  assert.equal(validate(manifest), false);
});

test('hxp remote parameter names are nonblank and bounded', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));

  const blankRedirect = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  blankRedirect.capabilities.remoteLibrary.policies.add.redirects[0].parameters['   '] = { kind: 'fixed', value: 'added' };
  assert.equal(validate(blankRedirect), false);

  const longRedirect = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  longRedirect.capabilities.remoteLibrary.policies.add.redirects[0].parameters['a'.repeat(257)] = { kind: 'fixed', value: 'added' };
  assert.equal(validate(longRedirect), false);

  const blankOperation = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  blankOperation.capabilities.remoteLibrary.policies.add.parameters['\t'] = { kind: 'fixed', value: 'added' };
  assert.equal(validate(blankOperation), false);
});

test('hxp parameter-name length counts astral Unicode code points', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));

  const maximum = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  maximum.capabilities.remoteLibrary.policies.add.redirects[0].parameters['😀'.repeat(256)] = { kind: 'fixed', value: 'added' };
  assert.equal(validate(maximum), true, ajv.errorsText(validate.errors));

  const oversized = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  oversized.capabilities.remoteLibrary.policies.add.redirects[0].parameters['😀'.repeat(257)] = { kind: 'fixed', value: 'added' };
  assert.equal(validate(oversized), false);
});

test('hxp manifest v1 validates remove and move remote operation policies', async () => {
  const ajv = createAjv();
  const validate = ajv.compile(await loadJson('../schemas/hxp-manifest-v1.schema.json'));
  const manifest = await loadJson('../fixtures/hxp/valid-minimal-manifest.json');
  manifest.capabilities.remoteLibrary.policies.remove = {
    origin: 'https://www.wenku8.net',
    method: 'POST',
    path: '/modules/article/bookcase.php',
    parameters: { action: { kind: 'fixed', value: 'remove' }, aid: { kind: 'remoteBookId' } },
  };
  manifest.capabilities.remoteLibrary.policies.move = {
    origin: 'https://www.wenku8.net',
    method: 'POST',
    path: '/modules/article/bookcase.php',
    parameters: {
      action: { kind: 'fixed', value: 'move' },
      aid: { kind: 'remoteBookId' },
      target: { kind: 'targetId' },
    },
  };
  assert.equal(validate(manifest), true, ajv.errorsText(validate.errors));
});
