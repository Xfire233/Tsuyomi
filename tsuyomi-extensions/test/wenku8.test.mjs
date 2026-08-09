// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  classifyPage,
  buildChapterRequest,
  buildSearchRequest,
  parseChapter,
  parseDetail,
  parseDirectory,
  parseSearch,
} from '../dist/modules/wenku8/index.mjs';

const fixture = (name) => readFile(new URL(`../fixtures/wenku8/${name}.html`, import.meta.url), 'utf8');

test('search normalizes stable book identities and skips malformed cards', async () => {
  const result = parseSearch(await fixture('search'));
  assert.deepEqual(result.items, [
    {
      sourceId: 'org.tsuyomi.wenku8',
      remoteBookId: '1234',
      title: '雾港纪事',
      author: '林川',
      coverUrl: 'https://www.wenku8.net/files/article/image/12/1234/1234s.jpg',
      canonicalUrl: 'https://www.wenku8.net/book/1234.htm',
    },
    {
      sourceId: 'org.tsuyomi.wenku8',
      remoteBookId: '5678',
      title: '星环邮差',
      author: '苏遥',
      coverUrl: 'https://www.wenku8.net/files/article/image/56/5678/5678s.jpg',
      canonicalUrl: 'https://www.wenku8.net/book/5678.htm',
    },
  ]);
  assert.deepEqual(result.diagnostics, [{ stage: 'search-parse', safeCode: 'malformed-book-card' }]);
});

test('detail emits presentation-neutral metadata without source HTML', async () => {
  const detail = parseDetail(await fixture('detail'), '1234');
  assert.equal(detail.summary.title, '雾港纪事');
  assert.equal(detail.summary.author, '林川');
  assert.equal(detail.description, '一名邮差在雾港追寻遗失的航线。此文本为测试用虚构简介。');
  assert.deepEqual(detail.tags, ['奇幻', '冒险']);
  assert.equal(detail.status, '连载中');
  assert.equal('rawHtml' in detail, false);
});

test('directory preserves order and deduplicates by stable chapter identity', async () => {
  const directory = parseDirectory(await fixture('directory'), '1234');
  assert.deepEqual(directory.chapters, [
    { chapterId: '10001', title: '第一章 雾中的灯塔', url: 'https://www.wenku8.net/novel/1234/10001.htm' },
    { chapterId: '10002', title: '第二章 旧船票', url: 'https://www.wenku8.net/novel/1234/10002.htm' },
  ]);
});

test('chapter emits ordered structured paragraphs and excludes navigation chrome', async () => {
  const document = parseChapter(await fixture('chapter'), '1234', '10001', 'fallback');
  assert.equal(document.contentId, '10001');
  assert.equal(document.title, '第一章 雾中的灯塔');
  assert.deepEqual(document.blocks, [
    { kind: 'paragraph', blockId: 'p-0001', text: '清晨的海雾漫过石阶，灯塔只剩一圈微光。' },
    { kind: 'paragraph', blockId: 'p-0002', text: '邮差把未署名的信收入防水袋，沿着旧轨道继续前行。' },
  ]);
  assert.throws(() => parseChapter('<html><div id="content"></div></html>', '1234', '10001', 'fallback'), /EMPTY_SOURCE_RESPONSE/);
});

test('request builders are HTTPS-only and reject untrusted chapter origins', () => {
  const search = buildSearchRequest('雾港', 1);
  assert.match(search.url, /^https:\/\/www\.wenku8\.net\//);
  assert.equal(search.decode, 'gb18030');
  assert.throws(() => buildChapterRequest('https://outside.example/10001.htm', '1234', '10001'), /ORIGIN_NOT_GRANTED/);
});

test('login and challenge fixtures become typed remediation states', async () => {
  assert.equal(classifyPage(await fixture('login')), 'session-required');
  assert.equal(classifyPage(await fixture('challenge')), 'verification-required');
  assert.equal(classifyPage(await fixture('search')), 'ok');
});
