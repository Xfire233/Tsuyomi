// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

const SOURCE_ID = 'org.tsuyomi.wenku8';
const ORIGIN = 'https://www.wenku8.net';

type NetworkRequest = {
  url: string;
  method: 'GET';
  headers: Record<string, string>;
  decode: 'auto' | 'gb18030';
  cache: 'default' | 'validate';
  semanticCacheKey: string;
  referrerUrl?: string;
};

type BookSummary = {
  sourceId: string;
  remoteBookId: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
  canonicalUrl: string;
};

type Diagnostic = { stage: string; safeCode: string };

const decodeEntities = (value: string): string => value
  .replace(/&#(\d+);/g, (_, decimal: string) => String.fromCodePoint(Number(decimal)))
  .replace(/&#x([0-9a-f]+);/gi, (_, hex: string) => String.fromCodePoint(Number.parseInt(hex, 16)))
  .replace(/&nbsp;/gi, ' ')
  .replace(/&amp;/gi, '&')
  .replace(/&lt;/gi, '<')
  .replace(/&gt;/gi, '>')
  .replace(/&quot;/gi, '"')
  .replace(/&#39;|&apos;/gi, "'");

const stripTags = (value: string): string => decodeEntities(
  value.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' '),
).replace(/\s+/g, ' ').trim();

const attribute = (attributes: string, name: string): string | null => {
  const match = new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i').exec(attributes);
  return match ? decodeEntities(match[1] ?? match[2] ?? match[3] ?? '') : null;
};

const absoluteUrl = (value: string, base = `${ORIGIN}/`): string => {
  const trimmed = value.trim();
  if (/^https:\/\//i.test(trimmed)) return trimmed;
  if (trimmed.startsWith('//')) return `https:${trimmed}`;
  if (trimmed.startsWith('/')) return `${ORIGIN}${trimmed}`;
  const directory = base.slice(0, base.lastIndexOf('/') + 1);
  return `${directory}${trimmed}`;
};

const findContainer = (html: string, ids: string[]): string | null => {
  for (const id of ids) {
    const pattern = new RegExp(`<([a-z0-9]+)\\b[^>]*(?:id|class)\\s*=\\s*["'][^"']*${id}[^"']*["'][^>]*>([\\s\\S]*?)<\\/\\1>`, 'i');
    const match = pattern.exec(html);
    if (match && match[2] !== undefined) return match[2];
  }
  return null;
};

const bookIdentityFromUrl = (href: string): { remoteBookId: string; canonicalUrl: string } | null => {
  const id = /(?:\/book\/(\d+)\.htm|articleinfo\.php\?[^#]*\bid=(\d+))/i.exec(href)?.slice(1).find(Boolean);
  return id ? { remoteBookId: id, canonicalUrl: `${ORIGIN}/book/${id}.htm` } : null;
};

const firstText = (html: string, patterns: RegExp[]): string | null => {
  for (const pattern of patterns) {
    const match = pattern.exec(html);
    const text = match?.[1] ? stripTags(match[1]) : '';
    if (text) return text;
  }
  return null;
};

export const classifyPage = (html: string): 'ok' | 'session-required' | 'verification-required' => {
  const normalized = html.toLowerCase();
  if (/(?:captcha|cf-chl-|challenge-platform|人机验证|安全验证|验证码)/i.test(normalized)) {
    return 'verification-required';
  }
  if (/<form\b[^>]*(?:login|signin)|(?:用户登录|会员登录|请先登录|登录后继续)/i.test(html)) {
    return 'session-required';
  }
  return 'ok';
};

export const buildSearchRequest = (query: string, page = 1): NetworkRequest => {
  const normalized = query.trim();
  if (!normalized || normalized.length > 100 || !Number.isInteger(page) || page < 1 || page > 100) {
    throw new Error('INVALID_SEARCH_INPUT');
  }
  const key = encodeURIComponent(normalized);
  return {
    url: `${ORIGIN}/modules/article/search.php?searchtype=articlename&searchkey=${key}&page=${page}`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'validate',
    semanticCacheKey: `search:${normalized}:${page}`,
  };
};

export const parseSearch = (html: string): { items: BookSummary[]; diagnostics: Diagnostic[] } => {
  const items: BookSummary[] = [];
  const diagnostics: Diagnostic[] = [];
  const seen = new Set<string>();
  const anchors = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  for (let match = anchors.exec(html); match; match = anchors.exec(html)) {
    const href = attribute(match[1] ?? '', 'href');
    const identity = href ? bookIdentityFromUrl(href) : null;
    if (!identity || seen.has(identity.remoteBookId)) continue;
    const title = stripTags(match[2] ?? '') || attribute(match[1] ?? '', 'title')?.trim() || '';
    if (!title) {
      diagnostics.push({ stage: 'search-parse', safeCode: 'malformed-book-card' });
      continue;
    }
    const rowStart = html.lastIndexOf('<tr', match.index);
    const rowEnd = html.indexOf('</tr>', anchors.lastIndex);
    const context = rowStart >= 0 && rowEnd >= anchors.lastIndex
      ? html.slice(rowStart, rowEnd + 5)
      : html.slice(Math.max(0, match.index - 300), Math.min(html.length, anchors.lastIndex + 300));
    const author = firstText(context, [/(?:小说作者|作者)\s*[：:]\s*([^<\n]+)/i]);
    const image = /<img\b([^>]*)>/i.exec(context);
    const cover = image ? attribute(image[1] ?? '', 'src') : null;
    items.push({
      sourceId: SOURCE_ID,
      remoteBookId: identity.remoteBookId,
      title,
      author,
      coverUrl: cover ? absoluteUrl(cover, identity.canonicalUrl) : null,
      canonicalUrl: identity.canonicalUrl,
    });
    seen.add(identity.remoteBookId);
  }
  if (!items.length && stripTags(html)) diagnostics.push({ stage: 'search-parse', safeCode: 'no-valid-book-cards' });
  return { items, diagnostics };
};

export const buildDetailRequest = (remoteBookId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId)) throw new Error('INVALID_BOOK_ID');
  return {
    url: `${ORIGIN}/book/${remoteBookId}.htm`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'validate',
    semanticCacheKey: `detail:${remoteBookId}`,
  };
};

export const parseDetail = (html: string, remoteBookId: string) => {
  const title = firstText(html, [/<h1\b[^>]*>([\s\S]*?)<\/h1>/i, /<div\b[^>]*id=["']title["'][^>]*>([\s\S]*?)<\/div>/i]);
  if (!title) throw new Error('MALFORMED_SOURCE_RESPONSE');
  const author = firstText(html, [/(?:小说作者|作者)\s*[：:]\s*([^<\n]+)/i]);
  const description = findContainer(html, ['content', 'intro', 'description']);
  const coverMatch = /<img\b([^>]*(?:bookcover|cover)[^>]*)>/i.exec(html);
  const cover = coverMatch ? attribute(coverMatch[1] ?? '', 'src') : null;
  const status = firstText(html, [/(?:文章状态|小说状态|状态)\s*[：:]\s*([^<\n]+)/i]);
  const tagsText = firstText(html, [/(?:作品Tags|标签|类型)\s*[：:]\s*([^<\n]+)/i]);
  const tags = tagsText ? tagsText.split(/[\s,，/|]+/).map((tag) => tag.trim()).filter(Boolean).slice(0, 128) : [];
  return {
    summary: {
      sourceId: SOURCE_ID,
      remoteBookId,
      title,
      author,
      coverUrl: cover ? absoluteUrl(cover, `${ORIGIN}/book/${remoteBookId}.htm`) : null,
      canonicalUrl: `${ORIGIN}/book/${remoteBookId}.htm`,
    },
    description: description ? stripTags(description) : null,
    tags: [...new Set(tags)],
    status,
  };
};

export const buildDirectoryRequest = (remoteBookId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId)) throw new Error('INVALID_BOOK_ID');
  return {
    url: `${ORIGIN}/novel/${remoteBookId}/index.htm`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'validate',
    semanticCacheKey: `directory:${remoteBookId}`,
    referrerUrl: `${ORIGIN}/book/${remoteBookId}.htm`,
  };
};

export const parseDirectory = (html: string, remoteBookId: string) => {
  const chapters: Array<{ chapterId: string; title: string; url: string }> = [];
  const seen = new Set<string>();
  const anchors = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  for (let match = anchors.exec(html); match; match = anchors.exec(html)) {
    const href = attribute(match[1] ?? '', 'href');
    const title = stripTags(match[2] ?? '');
    if (!href || !title) continue;
    const chapterId = /(?:^|\/)(\d+)\.htm(?:$|[?#])/i.exec(href)?.[1];
    if (!chapterId || chapterId === remoteBookId || seen.has(chapterId) || /index\.htm/i.test(href)) continue;
    const url = absoluteUrl(href, `${ORIGIN}/novel/${remoteBookId}/index.htm`);
    if (!url.startsWith(`${ORIGIN}/novel/`)) continue;
    chapters.push({ chapterId, title, url });
    seen.add(chapterId);
  }
  if (!chapters.length) throw new Error('EMPTY_SOURCE_RESPONSE');
  return { sourceId: SOURCE_ID, remoteBookId, chapters };
};

export const buildChapterRequest = (url: string, remoteBookId: string, chapterId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId) || !/^\d{1,16}$/.test(chapterId)) throw new Error('INVALID_CHAPTER_ID');
  const normalized = absoluteUrl(url, `${ORIGIN}/novel/${remoteBookId}/index.htm`);
  if (!normalized.startsWith(`${ORIGIN}/novel/`)) throw new Error('ORIGIN_NOT_GRANTED');
  return {
    url: normalized,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'validate',
    semanticCacheKey: `chapter:${remoteBookId}:${chapterId}`,
    referrerUrl: `${ORIGIN}/novel/${remoteBookId}/index.htm`,
  };
};

export const parseChapter = (html: string, remoteBookId: string, chapterId: string, fallbackTitle: string) => {
  const container = findContainer(html, ['content', 'htmlContent', 'chapter-content']);
  if (container === null) throw new Error('MALFORMED_SOURCE_RESPONSE');
  const title = firstText(html, [/<h1\b[^>]*>([\s\S]*?)<\/h1>/i, /<div\b[^>]*id=["']title["'][^>]*>([\s\S]*?)<\/div>/i]) ?? fallbackTitle;
  const paragraphMatches = [...container.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)].map((match) => stripTags(match[1] ?? ''));
  const paragraphs = (paragraphMatches.length ? paragraphMatches : container.split(/<br\s*\/?\s*>/i).map(stripTags))
    .filter((text) => text && !/^(?:上一章|下一章|返回目录)$/.test(text));
  if (!paragraphs.length) throw new Error('EMPTY_SOURCE_RESPONSE');
  return {
    sourceId: SOURCE_ID,
    remoteBookId,
    contentId: chapterId,
    revision: null,
    title,
    blocks: paragraphs.map((text, index) => ({
      kind: 'paragraph',
      blockId: `p-${String(index + 1).padStart(4, '0')}`,
      text,
    })),
  };
};

const api = {
  sourceId: SOURCE_ID,
  classifyPage,
  buildSearchRequest,
  parseSearch,
  buildDetailRequest,
  parseDetail,
  buildDirectoryRequest,
  parseDirectory,
  buildChapterRequest,
  parseChapter,
};

declare global {
  var tsuyomiExtension: typeof api | undefined;
}
globalThis.tsuyomiExtension = api;
export default api;
