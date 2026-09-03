// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  classifyPage,
  buildChapterRequest,
  buildDetailRequest,
  buildDirectoryRequest,
  buildSearchRequest,
  buildRemoteLibraryAddRequest,
  buildRemoteLibraryRequest,
  buildHomeRequest,
  parseChapter,
  parseDetail,
  parseDirectory,
  parseSearch,
  parseRemoteLibrary,
  parseRemoteLibraryAdd,
  parseHome,
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
      coverUrl: 'https://pic.wenku8.com/files/article/image/12/1234/1234s.jpg',
      canonicalUrl: 'https://www.wenku8.net/book/1234.htm',
    },
    {
      sourceId: 'org.tsuyomi.wenku8',
      remoteBookId: '5678',
      title: '星环邮差',
      author: '苏遥',
      coverUrl: 'https://pic.wenku8.com/files/article/image/56/5678/5678s.jpg',
      canonicalUrl: 'https://www.wenku8.net/book/5678.htm',
    },
  ]);
  assert.deepEqual(result.diagnostics, [{ stage: 'search-parse', safeCode: 'malformed-book-card' }]);
});

test('search accepts a same-origin exact-match redirect to the canonical detail page', async () => {
  const result = parseSearch(
    await fixture('detail'),
    'https://www.wenku8.net/modules/article/articleinfo.php?id=1234',
  );
  assert.deepEqual(result.items, [{
    sourceId: 'org.tsuyomi.wenku8',
    remoteBookId: '1234',
    title: '雾港纪事',
    author: '林川',
    coverUrl: 'https://pic.wenku8.com/files/article/image/12/1234/1234.jpg',
    canonicalUrl: 'https://www.wenku8.net/book/1234.htm',
  }]);
  assert.deepEqual(result.diagnostics, []);
});

test('redirected detail ignores earlier self-link chrome labels', () => {
  const html = '<html><head><title>文学少女 - 野村美月 - 轻小说文库</title></head><body><b>用户帮助</b><a href="/book/1.htm">繁體版</a></body></html>';
  const result = parseSearch(html, 'https://www.wenku8.net/book/1.htm');
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0]?.remoteBookId, '1');
  assert.equal(result.items[0]?.title, '文学少女');
});

test('authenticated page markers override stale login chrome', () => {
  const html = `<div>${'<nav>item</nav>'.repeat(300)}<span>欢迎您，xfire233 [</span>${'<section>book</section>'.repeat(300)}<a href="logout.php">退出登录</a>]</div><form id="login">用户登录 验证码 captcha</form>`;
  assert.equal(classifyPage(html), 'ok');
});

test('concrete book anchors override stale login chrome', () => {
  const html = '<a href="/book/1300.htm">文学少女</a><form id="login">用户登录</form>';
  assert.equal(classifyPage(html, 'https://www.wenku8.net/modules/article/search.php'), 'ok');
});

test('admitted canonical detail URL overrides stale login chrome for a usable document title', () => {
  const html = '<html><head><title>文学少女</title></head><body><form id="login">用户登录</form></body></html>';
  assert.equal(classifyPage(html, 'https://www.wenku8.net/book/1300.htm'), 'ok');
  assert.equal(classifyPage(html, 'https://www.wenku8.net/modules/article/search.php'), 'session-required');
  assert.equal(parseSearch(html, 'https://www.wenku8.net/book/1300.htm').items[0]?.title, '文学少女');
  assert.equal(classifyPage('<title>用户登录</title><form id="login">用户登录</form>', 'https://www.wenku8.net/book/1300.htm'), 'session-required');
});

test('redirected live detail title accepts author and imprint suffixes', () => {
  const result = parseSearch(
    '<html><head><title>文学少女 - 野村美月 - Fami通文库 - 轻小说文库</title></head><body></body></html>',
    'https://www.wenku8.net/book/1300.htm',
  );
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].remoteBookId, '1300');
  assert.equal(result.items[0].title, '文学少女');
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

test('detail keeps nested live page chrome outside the normalized description', () => {
  const detail = parseDetail(`
    <html><head><title>雾港纪事 - 轻小说文库</title></head><body>
      <div id="content"><div id="intro">受控的作品简介。</div><div class="page-chrome">登录、搜索和评论区。</div></div>
    </body></html>
  `, '1234');
  assert.equal(detail.description, '受控的作品简介。');
});

test('detail extracts only the labelled live introduction and exact cover', async () => {
  const detail = parseDetail(await fixture('detail-labelled-introduction'), '1234');
  assert.equal(detail.description, '一名邮差在雾港追寻遗失的航线。');
  assert.equal(detail.summary.coverUrl, 'https://img.wenku8.com/image/12/1234/1234s.jpg');
  assert.equal(detail.description.includes('同分类推荐'), false);
});

test('directory preserves order and deduplicates by stable chapter identity', async () => {
  const directory = parseDirectory(await fixture('directory'), '1234');
  assert.deepEqual(directory.chapters, [
    { chapterId: '10001', title: '第一章 雾中的灯塔', url: 'https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001', volumeTitle: '第一卷' },
    { chapterId: '10002', title: '第二章 旧船票', url: 'https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10002', volumeTitle: '第一卷' },
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

test('chapter balances nested live containers and excludes the duplicated title paragraph', () => {
  const document = parseChapter(`
    <html><body><div id="contentmain">
      <div class="chapter-heading"><p>第一章 雾中的灯塔</p></div>
      <div class="chapter-body">
        <p>第一段测试正文。</p>
        <p>第二段测试正文。</p>
      </div>
      <ul id="contentdp"><li>下一章</li></ul>
    </div></body></html>
  `, '1234', '10001', '第一章 雾中的灯塔');
  assert.deepEqual(document.blocks, [
    { kind: 'paragraph', blockId: 'p-0001', text: '第一段测试正文。' },
    { kind: 'paragraph', blockId: 'p-0002', text: '第二段测试正文。' },
  ]);
});

test('image-only chapter is admitted and emits normalized illustration blocks', async () => {
  const html = await fixture('chapter-illustrations');
  assert.equal(
    classifyPage(html, 'https://www.wenku8.net/novel/12/1234/10003.htm', 'chapter', '1234', '10003'),
    'ok',
  );
  const document = parseChapter(html, '1234', '10003', '第一卷 插图');
  assert.deepEqual(document.blocks, [
    {
      kind: 'image',
      blockId: 'i-0001',
      url: 'https://pic.777743.xyz/12/1234/10003/1.jpg',
      altText: '港口插图',
      width: 1073,
      height: 1600,
    },
    {
      kind: 'image',
      blockId: 'i-0002',
      url: 'https://pic.777743.xyz/12/1234/10003/2.jpg',
      altText: '第一卷 插图 插图 2',
      width: null,
      height: null,
    },
  ]);
});

test('mixed chapter preserves prose and illustration order', () => {
  const document = parseChapter(`
    <div id="content">
      <p>插图前的正文。</p>
      <img class="imagecontent" src="https://pic.777743.xyz/12/1234/10004/1.jpg" alt="场景">
      <p>插图后的正文。</p>
    </div>
  `, '1234', '10004', '混合章节');
  assert.deepEqual(document.blocks.map((block) => block.kind), ['paragraph', 'image', 'paragraph']);
});

test('request builders use live Wenku8 routes without admitting raw pages to durable cache', () => {
  const search = buildSearchRequest('文学少女', 1);
  assert.equal(search.url, 'https://www.wenku8.net/modules/article/search.php');
  assert.deepEqual(search.query, [
    { name: 'searchtype', value: 'articlename' },
    { name: 'searchkey', value: '文学少女' },
    { name: 'page', value: '1' },
  ]);
  assert.equal(search.queryEncoding, 'gb18030');
  assert.equal(search.decode, 'gb18030');
  assert.equal(search.cache, 'network-only');
  assert.equal(buildDetailRequest('1234').url, 'https://www.wenku8.net/book/1234.htm');
  assert.equal(buildDirectoryRequest('1234').url, 'https://www.wenku8.net/modules/article/reader.php?aid=1234');
  assert.equal(
    buildChapterRequest('/modules/article/reader.php?aid=1234&cid=10001', '1234', '10001').cache,
    'network-only',
  );
  assert.throws(() => buildChapterRequest('https://outside.example/10001.htm', '1234', '10001'), /ORIGIN_NOT_GRANTED/);
  assert.throws(
    () => buildChapterRequest('/modules/article/reader.php?aid=9999&cid=10001', '1234', '10001'),
    /CHAPTER_IDENTITY_MISMATCH/,
  );
});

test('source Home exposes source-ordered homepage recommendations, category tags, and bounded catalog data', async () => {
  assert.deepEqual(buildHomeRequest(null, {}), {
    url: 'https://www.wenku8.net/index.php',
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: 'https://www.wenku8.net/',
  });
  assert.deepEqual(buildHomeRequest(null, { view: 'category', tag: 'fantasy', sort: '2' }), {
    url: 'https://www.wenku8.net/modules/article/tags.php',
    query: [
      { name: 't', value: '奇幻' },
      { name: 'v', value: '2' },
      { name: 'page', value: '1' },
    ],
    queryEncoding: 'gb18030',
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: 'https://www.wenku8.net/',
  });
  assert.deepEqual(buildHomeRequest(null, { view: 'recommend', feature: 'sugoi-2026' }), {
    url: 'https://www.wenku8.net/zt/sugoi/2026.php',
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: 'https://www.wenku8.net/',
  });

  const page = parseHome(await fixture('home-index'), null, {});
  assert.equal(page.schemaVersion, 1);
  assert.equal(page.title, 'Wenku8 书库');
  assert.deepEqual(page.selectedFilters, { view: 'recommend' });
  assert.deepEqual(page.filters[0].options.map((option) => option.label), ['推荐', '分类', '排行', '完结']);
  assert.deepEqual(page.sections.map((section) => section.title), ['7月新番', '新书风云榜', '本周会员推荐榜']);
  assert.deepEqual(page.sections.map((section) => section.items.map((book) => book.remoteBookId)), [
    ['1234', '5678'],
    ['9012', '3456'],
    ['7890', '2468'],
  ]);
  assert.deepEqual(page.features, [{
    id: 'sugoi-2026',
    title: '这本轻小说真厉害！2026',
    supportingText: 'TOP20 榜单',
    selectedFilters: { view: 'recommend', feature: 'sugoi-2026' },
  }]);
  const award = parseHome(
    await fixture('home-sugoi-2026'),
    null,
    { view: 'recommend', feature: 'sugoi-2026' },
  );
  assert.equal(award.title, '这本轻小说真厉害！2026');
  assert.deepEqual(award.selectedFilters, { view: 'recommend' });
  assert.deepEqual(award.sections.map((section) => section.title), [
    '文库部门 TOP10',
    '单行本部门 TOP10',
  ]);
  assert.deepEqual(award.sections.map((section) => section.items.map((book) => book.remoteBookId)), [
    ['3988', '2580', '3057', '2930'],
    ['2964', '2767', '2853', '1787'],
  ]);
  assert.equal(award.features, undefined);
  assert.equal(page.nextCursor, null);
  assert.equal(page.complete, true);

  const category = parseHome(await fixture('home'), null, { view: 'category', tag: 'fantasy', sort: '2' });
  assert.deepEqual(category.selectedFilters, { view: 'category', tag: 'fantasy', sort: '2' });
  assert.equal(category.filters[1].label, '题材');
  assert.equal(category.filters[1].options.length, 32);
  assert.equal(category.sections[0].title, '奇幻 · 只看完结');
  assert.equal(classifyPage(await fixture('home-index'), 'https://www.wenku8.net/index.php', 'home'), 'ok');
  assert.equal(classifyPage(await fixture('detail'), 'https://www.wenku8.net/book/1234.htm', 'home'), 'malformed');
  assert.throws(() => buildHomeRequest('page-2', {}), /INVALID_HOME_CURSOR/);
  assert.throws(() => buildHomeRequest(null, { recommendation: 'allvote' }), /INVALID_HOME_FILTER/);
  assert.throws(() => buildHomeRequest(null, { arbitrary: 'value' }), /INVALID_HOME_FILTER/);
  assert.throws(() => buildHomeRequest(null, { view: 'category', feature: 'sugoi-2026' }), /INVALID_HOME_FILTER/);
  assert.throws(() => buildHomeRequest(null, { view: 'recommend', feature: 'outside' }), /INVALID_HOME_FILTER/);
});

test('directory accepts live reader query links and keeps exact book identity', () => {
  const directory = parseDirectory(`
    <table class="css"><tr><td class="ccss">
      <a href="/modules/article/reader.php?aid=1234&amp;cid=10001">第一章</a>
      <a href="/modules/article/reader.php?aid=9999&amp;cid=10002">其他书</a>
    </td></tr></table>
  `, '1234');
  assert.deepEqual(directory.chapters, [{
    chapterId: '10001',
    title: '第一章',
    url: 'https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001',
    volumeTitle: null,
  }]);
});

test('login and challenge fixtures become typed remediation states', async () => {
  assert.equal(classifyPage(await fixture('login')), 'session-required');
  assert.equal(classifyPage(await fixture('challenge')), 'verification-required');
  assert.equal(classifyPage(await fixture('search')), 'ok');
});

test('operation-specific admission rejects wrong Wenku8 page shapes and identities', async () => {
  const search = await fixture('search');
  const detail = await fixture('detail');
  const directory = await fixture('directory');
  const chapter = await fixture('chapter');
  const remoteLibrary = await fixture('remote-library-page-1');

  assert.equal(classifyPage(search, 'https://www.wenku8.net/modules/article/search.php', 'search'), 'ok');
  assert.equal(classifyPage(detail, 'https://www.wenku8.net/book/1234.htm', 'detail', '1234'), 'ok');
  assert.equal(classifyPage(directory, 'https://www.wenku8.net/modules/article/reader.php?aid=1234', 'directory', '1234'), 'ok');
  assert.equal(classifyPage(chapter, 'https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001', 'chapter', '1234', '10001'), 'ok');
  assert.equal(classifyPage(directory, 'https://www.wenku8.net/modules/article/reader.php?aid=1234', 'chapter', '1234', '10001'), 'malformed');
  assert.equal(classifyPage(detail, 'https://www.wenku8.net/book/5678.htm', 'detail', '1234'), 'malformed');
  assert.equal(classifyPage(await fixture('login'), 'https://www.wenku8.net/modules/article/bookcase.php', 'remote-library'), 'session-required');
  assert.equal(classifyPage(remoteLibrary, 'https://www.wenku8.net/modules/article/bookcase.php', 'remote-library'), 'ok');
});

test('chapter admission ignores bounded navigation chrome outside semantic prose', async () => {
  const chapter = await fixture('chapter-navigation-chrome');
  assert.equal(
    classifyPage(
      chapter,
      'https://www.wenku8.net/modules/article/reader.php?aid=471&cid=20001',
      'chapter',
      '471',
      '20001',
    ),
    'ok',
  );
  assert.equal(parseChapter(chapter, '471', '20001', '第一章').blocks.length, 2);
});

test('chapter admission accepts short non-empty semantic content', async () => {
  const chapter = await fixture('chapter-short');
  assert.equal(
    classifyPage(
      chapter,
      'https://www.wenku8.net/modules/article/reader.php?aid=471&cid=20464',
      'chapter',
      '471',
      '20464',
    ),
    'ok',
  );
  assert.equal(parseChapter(chapter, '471', '20464', '第一章').blocks.length, 1);
});

test('remote favourites pagination is explicit bounded and complete', async () => {
  assert.deepEqual(buildRemoteLibraryRequest(null), {
    url: 'https://www.wenku8.net/modules/article/bookcase.php?action=list',
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
  });
  const first = parseRemoteLibrary(await fixture('remote-library-page-1'));
  assert.equal(first.complete, false);
  assert.equal(first.nextCursor, 'page-2');
  assert.equal(first.items[0].remoteBookId, '1234');
  const second = parseRemoteLibrary(await fixture('remote-library-page-2'));
  assert.equal(second.complete, true);
  assert.equal(second.nextCursor, null);
  assert.throws(() => buildRemoteLibraryRequest(''), /INVALID_REMOTE_CURSOR/);
});

test('remote add is an exact idempotent typed operation', async () => {
  assert.deepEqual(buildRemoteLibraryAddRequest('1234'), {
    url: 'https://www.wenku8.net/modules/article/bookcase.php',
    method: 'POST',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    form: { action: 'add', aid: '1234' },
    decode: 'gb18030',
    cache: 'network-only',
  });
  assert.deepEqual(parseRemoteLibraryAdd(await fixture('remote-add-applied'), '1234'), {
    sourceId: 'org.tsuyomi.wenku8', remoteBookId: '1234', outcome: 'applied',
  });
  assert.equal(parseRemoteLibraryAdd(await fixture('remote-add-already-present'), '1234').outcome, 'already-present');
  assert.throws(() => parseRemoteLibraryAdd('<html>ok</html>', '1234'), /AMBIGUOUS_REMOTE_ADD/);
});
