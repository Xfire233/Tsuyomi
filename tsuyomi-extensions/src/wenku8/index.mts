// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

const SOURCE_ID = 'org.tsuyomi.wenku8';
const ORIGIN = 'https://www.wenku8.net';

type NetworkRequest = {
  url: string;
  method: 'GET' | 'POST';
  headers: Record<string, string>;
  decode: 'auto' | 'gb18030';
  cache: 'default' | 'validate' | 'network-only';
  semanticCacheKey?: string;
  form?: Record<string, string>;
  query?: Array<{ name: string; value: string }>;
  queryEncoding?: 'utf-8' | 'gb18030';
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
type HomeFilterSelection = Record<string, string>;

const HOME_VIEWS = {
  recommend: '推荐',
  category: '分类',
  ranking: '排行',
  completed: '完结',
} as const;


const HOME_RANKINGS = {
  lastupdate: '最近更新',
  postdate: '最新入库',
  allvisit: '总排行',
  monthvisit: '月排行',
  weekvisit: '周排行',
  dayvisit: '日排行',
  size: '字数排行',
  animated: '动画化',
  notanimated: '未动画化',
} as const;

const HOME_CATEGORY_SORTS = {
  '0': '按更新',
  '1': '按热门',
  '2': '只看完结',
  '3': '只看动画化',
} as const;

const HOME_CATEGORY_TAGS = {
  school: '校园',
  youth: '青春',
  love: '恋爱',
  healing: '治愈',
  group_portrait: '群像',
  sports: '竞技',
  music: '音乐',
  food: '美食',
  travel: '旅行',
  joy: '欢乐向',
  workplace: '职场',
  battle_of_wits: '斗智',
  brain_cavity: '脑洞',
  otaku_culture: '宅文化',
  pass_through: '穿越',
  fantasy: '奇幻',
  magic: '魔法',
  supernatural_ability: '异能',
  fighting: '战斗',
  science_fiction: '科幻',
  machine_warfare: '机战',
  warfare: '战争',
  adventure: '冒险',
  suspense: '悬疑',
  crime: '犯罪',
  revenge: '复仇',
  darkness: '黑暗',
  thrilling: '惊悚',
  apocalypse: '末日',
  game: '游戏',
  harem: '后宫',
  lily: '百合',
} as const;


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
const absoluteMediaUrl = (value: string, base: string): string => {
  const trimmed = value.trim();
  if (trimmed.startsWith('/files/article/image/')) return `https://pic.wenku8.com${trimmed}`;
  if (trimmed.startsWith('/')) return `https://img.wenku8.com${trimmed}`;
  if (/^https?:\/\//i.test(trimmed)) return trimmed.replace(/^http:\/\//i, 'https://');
  return absoluteUrl(trimmed, base);
};


const matchesContainerIdentity = (attributes: string, identity: string): boolean => {
  const elementId = attribute(attributes, 'id')?.trim();
  if (elementId === identity) return true;
  const classes = attribute(attributes, 'class')?.split(/\s+/u).filter(Boolean) ?? [];
  return classes.includes(identity);
};

const balancedElementBody = (html: string, openingEnd: number, tagName: string): string | null => {
  const tags = new RegExp(`<\\/?${tagName}\\b[^>]*>`, 'gi');
  tags.lastIndex = openingEnd;
  let depth = 1;
  for (let match = tags.exec(html); match; match = tags.exec(html)) {
    const token = match[0] ?? '';
    if (/^<\//.test(token)) {
      depth -= 1;
      if (depth === 0) return html.slice(openingEnd, match.index);
    } else if (!/\/\s*>$/.test(token)) {
      depth += 1;
    }
  }
  return null;
};

const findContainer = (html: string, ids: string[]): string | null => {
  for (const id of ids) {
    const pattern = new RegExp(`<([a-z0-9]+)\\b[^>]*(?:id|class)\\s*=\\s*["'][^"']*${id}[^"']*["'][^>]*>([\\s\\S]*?)<\\/\\1>`, 'i');
    const match = pattern.exec(html);
    if (match && match[2] !== undefined) return match[2];
  }
  return null;
};

const findBalancedContainer = (html: string, identities: string[]): string | null => {
  for (const identity of identities) {
    const openings = /<([a-z0-9]+)\b([^>]*)>/gi;
    for (let match = openings.exec(html); match; match = openings.exec(html)) {
      if (!matchesContainerIdentity(match[2] ?? '', identity)) continue;
      const body = balancedElementBody(html, openings.lastIndex, (match[1] ?? '').toLowerCase());
      if (body !== null) return body;
    }
  }
  return null;
};

const bookIdentityFromUrl = (href: string): { remoteBookId: string; canonicalUrl: string } | null => {
  const id = /(?:\/book\/(\d+)\.htm|articleinfo\.php\?[^#]*(?:\bid|\baid|\bbid)=(\d+))/i
    .exec(decodeEntities(href))?.slice(1).find(Boolean);
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
const detailIntroduction = (html: string): string | null => {
  const labelled = /<span\b[^>]*>\s*(?:内容简介|作品简介|小说简介)\s*[：:]\s*<\/span>\s*(?:<br\s*\/?\s*>\s*)*<span\b[^>]*>([\s\S]*?)<\/span>/i.exec(html)?.[1];
  const exactContainer = findBalancedContainer(html, ['intro', 'introduce', 'description', 'bookintro', 'book-intro']);
  const text = stripTags((labelled ?? exactContainer ?? '').replace(/<\/?(?:strong|b|em|i|u)\b[^>]*>/gi, ''));
  return text || null;
};

const admittedIllustration = (attributes: string, base: string): { url: string; altText: string | null; width: number | null; height: number | null } | null => {
  const src = attribute(attributes, 'src');
  if (!src) return null;
  const url = absoluteMediaUrl(src, base);
  if (!/^https:\/\/(?:img\.wenku8\.com|pic\.wenku8\.com|pic\.777743\.xyz)\//i.test(url)) return null;
  const classNames = attribute(attributes, 'class')?.split(/\s+/u).filter(Boolean) ?? [];
  if (classNames.some((name) => /^(?:logo|icon|avatar|advert|banner)$/i.test(name))) return null;
  const parseDimension = (name: string): number | null => {
    const raw = attribute(attributes, name);
    const value = raw ? Number.parseInt(raw, 10) : Number.NaN;
    return Number.isFinite(value) && value > 0 ? value : null;
  };
  return {
    url,
    altText: attribute(attributes, 'alt')?.trim() || null,
    width: parseDimension('width'),
    height: parseDimension('height'),
  };
};
const documentTitle = (html: string): string | null => {
  const raw = firstText(html, [/<title\b[^>]*>([\s\S]*?)<\/title>/i]);
  if (!raw) return null;
  const title = raw.split(/\s+(?:-|–|\|)\s+/u, 1)[0]?.trim() ?? '';
  if (!title || /^(?:轻小说文库|文库8|wenku8)$/i.test(title) || /(?:登录|验证码|安全验证|人机验证|captcha)/i.test(title)) return null;
  return title;
};

const hasConcreteDetailDocument = (html: string): boolean => documentTitle(html) !== null &&
  /<title\b[^>]*>\s*[\s\S]+?(?:\s*-\s*[^<]*?文库|\s*\|\s*[^<]*?文库)[\s\S]*?<\/title>/i.test(html);

const hasConcreteBookAnchor = (html: string): boolean => {
  const anchors = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  for (let match = anchors.exec(html); match; match = anchors.exec(html)) {
    const href = attribute(match[1] ?? '', 'href');
    const title = stripTags(match[2] ?? '') || attribute(match[1] ?? '', 'title')?.trim() || '';
    if (href && title && bookIdentityFromUrl(href) !== null) return true;
  }
  return false;
};

const readerIdentityFromUrl = (value: string | undefined): { remoteBookId: string; chapterId: string | null } | null => {
  if (!value) return null;
  const decoded = decodeEntities(value);
  const queryAid = /[?&]aid=(\d{1,12})(?:[&#]|$)/i.exec(decoded)?.[1];
  const queryCid = /[?&]cid=(\d{1,16})(?:[&#]|$)/i.exec(decoded)?.[1] ?? null;
  if (queryAid) return { remoteBookId: queryAid, chapterId: queryCid };
  const staticMatch = /\/novel\/(?:\d+\/)?(\d{1,12})\/(?:index\.htm|(\d{1,16})\.htm)(?:$|[?#])/i.exec(decoded);
  return staticMatch ? { remoteBookId: staticMatch[1] ?? '', chapterId: staticMatch[2] ?? null } : null;
};

const sessionRemediation = (html: string): 'session-required' | 'verification-required' | null => {
  if (/欢迎您/i.test(html) && /(?:退出登录|logout(?:\.php)?)/i.test(html)) return null;
  if (/(?:captcha|cf-chl-|challenge-platform|人机验证|安全验证|验证码)/i.test(html)) {
    return 'verification-required';
  }
  if (/<form\b[^>]*(?:login|signin)|(?:用户登录|会员登录|请先登录|登录后继续)/i.test(html)) {
    return 'session-required';
  }
  return null;
};

const looksLikeDetail = (html: string): boolean => {
  const hasTitle = /<h1\b[^>]*>[\s\S]*?<\/h1>/i.test(html) || hasConcreteDetailDocument(html);
  const hasAuthor = /(?:小说作者|文章作者|作者)\s*[：:]/i.test(html);
  const hasSupportingDetail = /(?:写作进程|文章状态|小说状态|作品Tags|小说标签|bookcover|class=["'][^"']*(?:cover|image|article))/i.test(html) ||
    findBalancedContainer(html, ['intro', 'introduce', 'description']) !== null;
  return hasTitle && hasAuthor && hasSupportingDetail;
};

const looksLikeDirectory = (html: string, remoteBookId: string): boolean => {
  const staticChapter = new RegExp(`/novel/(?:\\d+/)?${remoteBookId}/\\d{1,16}\\.htm`, 'i').test(html);
  const dynamicChapter = new RegExp(`reader\\.php\\?[^"'< >]*aid=${remoteBookId}(?:&amp;|&)cid=`, 'i').test(html);
  return (/(?:class=["'](?:ccss|vcss)["'])/i.test(html) || staticChapter || dynamicChapter) && (staticChapter || dynamicChapter);
};

const looksLikeChapter = (html: string, fallbackTitle: string | undefined = undefined): boolean => {
  const content = findBalancedContainer(html, ['content', 'contentmain', 'chapter-content']);
  if (content === null) return false;
  const chapterLinks = content.match(
    /(?:\/novel\/(?:\d+\/)?\d{1,12}\/\d{1,16}\.htm|reader\.php\?[^"'< >]*aid=\d{1,12}(?:&amp;|&)cid=\d{1,16})/gi,
  )?.length ?? 0;
  if (chapterLinks >= 2) return false;
  const semantic = content
    .replace(/<ul\b[^>]*(?:id|class)=["'][^"']*contentdp[^"']*["'][^>]*>[\s\S]*?<\/ul>/gi, ' ')
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ');
  const prose = stripTags(semantic).replace(fallbackTitle ?? '', '').trim();
  const image = /<img\b([^>]*)>/gi;
  const base = `${ORIGIN}/`;
  const hasIllustration = [...semantic.matchAll(image)].some((match) => admittedIllustration(match[1] ?? '', base) !== null);
  return [...prose].length >= 8 || hasIllustration;
};

export const classifyPage = (
  html: string,
  finalUrl?: string,
  operation: 'generic' | 'search' | 'home' | 'detail' | 'directory' | 'chapter' | 'remote-library' = 'generic',
  remoteBookId?: string,
  chapterId?: string,
): 'ok' | 'session-required' | 'verification-required' | 'malformed' => {
  if (operation === 'generic') {
    if (hasConcreteBookAnchor(html) || (finalUrl !== undefined && bookIdentityFromUrl(finalUrl) !== null && documentTitle(html) !== null) || hasConcreteDetailDocument(html)) {
      return 'ok';
    }
    return sessionRemediation(html) ?? 'ok';
  }
  const remediation = sessionRemediation(html);
  if (remediation !== null) return remediation;
  if (operation === 'search') {
    const redirected = finalUrl ? bookIdentityFromUrl(finalUrl) : null;
    return (redirected !== null && looksLikeDetail(html)) || hasConcreteBookAnchor(html) ? 'ok' : 'malformed';
  }
  if (operation === 'home') {
    return hasConcreteBookAnchor(html) ? 'ok' : 'malformed';
  }
  if (operation === 'remote-library') {
    return /data-complete=["'](?:true|false)["']/i.test(html) && (hasConcreteBookAnchor(html) || /(?:bookcase|收藏|书架)/i.test(html)) ? 'ok' : 'malformed';
  }
  if (!remoteBookId) return 'malformed';
  if (operation === 'detail') {
    return bookIdentityFromUrl(finalUrl ?? '')?.remoteBookId === remoteBookId && looksLikeDetail(html) ? 'ok' : 'malformed';
  }
  if (operation === 'directory') {
    const identity = readerIdentityFromUrl(finalUrl);
    return identity?.remoteBookId === remoteBookId && identity.chapterId === null && looksLikeDirectory(html, remoteBookId) ? 'ok' : 'malformed';
  }
  const identity = readerIdentityFromUrl(finalUrl);
  return identity?.remoteBookId === remoteBookId && identity.chapterId === chapterId && looksLikeChapter(html) ? 'ok' : 'malformed';
};
export const buildSearchRequest = (query: string, page = 1): NetworkRequest => {
  const normalized = query.trim();
  if (!normalized || normalized.length > 100 || !Number.isInteger(page) || page < 1 || page > 100) {
    throw new Error('INVALID_SEARCH_INPUT');
  }
  return {
    url: `${ORIGIN}/modules/article/search.php`,
    query: [
      { name: 'searchtype', value: 'articlename' },
      { name: 'searchkey', value: normalized },
      { name: 'page', value: String(page) },
    ],
    queryEncoding: 'gb18030',
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
  };
};

export const parseSearch = (
  html: string,
  finalUrl?: string,
): { items: BookSummary[]; diagnostics: Diagnostic[] } => {
  if (finalUrl) {
    const redirectedIdentity = bookIdentityFromUrl(finalUrl);
    if (redirectedIdentity) {
      try {
        return { items: [parseDetail(html, redirectedIdentity.remoteBookId).summary], diagnostics: [] };
      } catch {
        // Fall through to result-card parsing for non-detail documents.
      }
    }
  }
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
    const rowStart = Math.max(html.lastIndexOf('<tr', match.index), html.lastIndexOf('<div', match.index));
    const rowEndCandidates = [html.indexOf('</tr>', anchors.lastIndex), html.indexOf('</div>', anchors.lastIndex)]
      .filter((index) => index >= anchors.lastIndex);
    const rowEnd = rowEndCandidates.length ? Math.min(...rowEndCandidates) : -1;
    const context = rowStart >= 0 && rowEnd >= anchors.lastIndex
      ? html.slice(rowStart, rowEnd + 6)
      : html.slice(Math.max(0, match.index - 600), Math.min(html.length, anchors.lastIndex + 600));
    const author = firstText(context, [/(?:小说作者|作者)\s*[：:]\s*([^<\n]+)/i]);
    const image = /<img\b([^>]*)>/i.exec(context);
    const cover = image ? attribute(image[1] ?? '', 'src') : null;
    items.push({
      sourceId: SOURCE_ID,
      remoteBookId: identity.remoteBookId,
      title,
      author,
      coverUrl: cover ? absoluteMediaUrl(cover, identity.canonicalUrl) : null,
      canonicalUrl: identity.canonicalUrl,
    });
    seen.add(identity.remoteBookId);
  }
  if (!items.length && stripTags(html)) diagnostics.push({ stage: 'search-parse', safeCode: 'no-valid-book-cards' });
  return { items, diagnostics };
};

const parseHomepageRecommendationSections = (html: string) => {
  const centers = findBalancedContainer(html, ['centers']);
  if (!centers) return [];
  const sections: Array<{ id: string; title: string; items: BookSummary[] }> = [];
  const blocks = /<([a-z0-9]+)\b([^>]*)>/gi;
  for (let match = blocks.exec(centers); match; match = blocks.exec(centers)) {
    const tagName = (match[1] ?? '').toLowerCase();
    if (tagName !== 'div' || !matchesContainerIdentity(match[2] ?? '', 'block')) continue;
    const body = balancedElementBody(centers, blocks.lastIndex, tagName);
    if (body === null) continue;
    const titleBody = findBalancedContainer(body, ['blocktitle']);
    const titleMarkup = (titleBody ?? '').replace(
      /<a\b[^>]*href=["'][^"']*\/zt\/sugoi\/20\d{2}\.php[^"']*["'][^>]*>[\s\S]*?<\/a>/gi,
      '',
    );
    const sourceTitle = stripTags(titleMarkup)
      .replace(/\s*[（(]\s*[)）]\s*$/u, '')
      .replace(/\s*[（(]\s*\d+\s*本?\s*[)）]\s*$/u, '')
      .trim();
    const title = /^\d{1,2}月新番/u.exec(sourceTitle)?.[0] ?? sourceTitle;
    const items = parseSearch(body).items;
    if (!title || !items.length) continue;
    sections.push({ id: `homepage-${sections.length + 1}`, title, items });
    if (sections.length === 3) break;
  }
  return sections;
};
const parseHomepageFeatures = (html: string) => {
  let latestYear: string | null = null;
  const anchors = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  for (let match = anchors.exec(html); match; match = anchors.exec(html)) {
    const href = decodeEntities(attribute(match[1] ?? '', 'href') ?? '');
    const year = /\/zt\/sugoi\/(20\d{2})\.php(?:[?#].*)?$/i.exec(href)?.[1];
    if (year && (latestYear === null || year > latestYear)) latestYear = year;
  }
  if (latestYear === null) return [];
  return [{
    id: `sugoi-${latestYear}`,
    title: `这本轻小说真厉害！${latestYear}`,
    supportingText: 'TOP20 榜单',
    selectedFilters: { view: 'recommend', feature: `sugoi-${latestYear}` },
  }];
};

const parseAwardSections = (html: string) => {
  const sections: Array<{ id: string; title: string; items: BookSummary[] }> = [];
  const tables = /<table\b([^>]*)>/gi;
  for (let match = tables.exec(html); match; match = tables.exec(html)) {
    if (!matchesContainerIdentity(match[1] ?? '', 'grid')) continue;
    const body = balancedElementBody(html, tables.lastIndex, 'table');
    if (body === null) continue;
    const caption = firstText(body, [/<caption\b[^>]*>([\s\S]*?)<\/caption>/i]);
    const title = caption?.replace(/^这本轻小说真厉害！\s*20\d{2}\s*/u, '').trim() ?? null;
    const items = parseSearch(body).items;
    if (!title || !items.length) continue;
    sections.push({ id: `award-${sections.length + 1}`, title, items });
  }
  return sections;
};


const normalizeHomeSelection = (selectedFilters: HomeFilterSelection) => {
  const knownFilterIds = new Set(['view', 'tag', 'sort', 'ranking', 'feature']);
  if (Object.keys(selectedFilters).some((key) => !knownFilterIds.has(key))) throw new Error('INVALID_HOME_FILTER');
  const optionOrDefault = (value: string | undefined, options: Record<string, string>, fallback: string): string => {
    const selected = value ?? fallback;
    if (!(selected in options)) throw new Error('INVALID_HOME_FILTER');
    return selected;
  };
  const view = optionOrDefault(selectedFilters.view, HOME_VIEWS, 'recommend');
  const feature = selectedFilters.feature;
  if (feature !== undefined && !/^sugoi-20\d{2}$/.test(feature)) throw new Error('INVALID_HOME_FILTER');
  if (feature !== undefined && view !== 'recommend') throw new Error('INVALID_HOME_FILTER');
  return {
    view,
    tag: optionOrDefault(selectedFilters.tag, HOME_CATEGORY_TAGS, 'school'),
    sort: optionOrDefault(selectedFilters.sort, HOME_CATEGORY_SORTS, '0'),
    ranking: optionOrDefault(selectedFilters.ranking, HOME_RANKINGS, 'allvisit'),
    feature,
  };
};

const homePageFromCursor = (cursor: string | null): number => {
  const page = cursor === null ? 1 : Number.parseInt(/^page-(\d{1,3})$/.exec(cursor)?.[1] ?? '', 10);
  if (!Number.isInteger(page) || page < 1 || page > 999) throw new Error('INVALID_HOME_CURSOR');
  return page;
};

export const buildHomeRequest = (
  cursor: string | null,
  selectedFilters: HomeFilterSelection = {},
): NetworkRequest => {
  const selection = normalizeHomeSelection(selectedFilters);
  const common = {
    method: 'GET' as const,
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030' as const,
    cache: 'network-only' as const,
    referrerUrl: `${ORIGIN}/`,
  };
  if (selection.view === 'recommend') {
    if (cursor !== null) throw new Error('INVALID_HOME_CURSOR');
    const awardYear = selection.feature?.match(/^sugoi-(20\d{2})$/)?.[1];
    return {
      url: awardYear ? `${ORIGIN}/zt/sugoi/${awardYear}.php` : `${ORIGIN}/index.php`,
      ...common,
    };
  }
  const page = homePageFromCursor(cursor);
  if (selection.view === 'category') {
    return {
      url: `${ORIGIN}/modules/article/tags.php`,
      query: [
        { name: 't', value: HOME_CATEGORY_TAGS[selection.tag as keyof typeof HOME_CATEGORY_TAGS] },
        { name: 'v', value: selection.sort },
        { name: 'page', value: String(page) },
      ],
      queryEncoding: 'gb18030',
      ...common,
    };
  }
  const ranking = selection.view === 'ranking' ? selection.ranking : 'fullflag';
  return {
    url: `${ORIGIN}/modules/article/toplist.php`,
    query: [
      { name: 'sort', value: ranking },
      { name: 'page', value: String(page) },
    ],
    queryEncoding: 'utf-8',
    ...common,
  };
};

export const parseHome = (
  html: string,
  cursor: string | null,
  selectedFilters: HomeFilterSelection = {},
) => {
  const selection = normalizeHomeSelection(selectedFilters);
  const filters: Array<{ id: string; label: string; options: Array<{ value: string; label: string }> }> = [{
    id: 'view',
    label: '栏目',
    options: Object.entries(HOME_VIEWS).map(([value, label]) => ({ value, label })),
  }];
  const normalizedSelection: HomeFilterSelection = { view: selection.view };
  if (selection.view === 'recommend') {
    if (cursor !== null) throw new Error('INVALID_HOME_CURSOR');
    if (selection.feature !== undefined) {
      const sections = parseAwardSections(html);
      if (!sections.length) throw new Error('EMPTY_SOURCE_RESPONSE');
      const year = selection.feature.slice('sugoi-'.length);
      return {
        schemaVersion: 1,
        title: `这本轻小说真厉害！${year}`,
        filters,
        selectedFilters: normalizedSelection,
        sections,
        nextCursor: null,
        complete: true,
      };
    }
    const sections = parseHomepageRecommendationSections(html);
    if (!sections.length) throw new Error('EMPTY_SOURCE_RESPONSE');
    return {
      schemaVersion: 1,
      title: 'Wenku8 书库',
      filters,
      selectedFilters: normalizedSelection,
      sections,
      features: parseHomepageFeatures(html),
      nextCursor: null,
      complete: true,
    };
  }

  const currentPage = homePageFromCursor(cursor);
  const parsed = parseSearch(html);
  if (!parsed.items.length) throw new Error('EMPTY_SOURCE_RESPONSE');
  const pageMatches = [...html.matchAll(/[?&](?:amp;)?page=(\d{1,3})/gi)]
    .map((match) => Number.parseInt(match[1] ?? '', 10))
    .filter((page) => Number.isInteger(page) && page > currentPage);
  const nextPage = pageMatches.length ? Math.min(...pageMatches) : null;
  let sectionTitle: string;
  if (selection.view === 'category') {
    filters.push(
      {
        id: 'tag',
        label: '题材',
        options: Object.entries(HOME_CATEGORY_TAGS).map(([value, label]) => ({ value, label })),
      },
      {
        id: 'sort',
        label: '排序',
        options: Object.entries(HOME_CATEGORY_SORTS).map(([value, label]) => ({ value, label })),
      },
    );
    normalizedSelection.tag = selection.tag;
    normalizedSelection.sort = selection.sort;
    sectionTitle = `${HOME_CATEGORY_TAGS[selection.tag as keyof typeof HOME_CATEGORY_TAGS]} · ${HOME_CATEGORY_SORTS[selection.sort as keyof typeof HOME_CATEGORY_SORTS]}`;
  } else if (selection.view === 'ranking') {
    filters.push({
      id: 'ranking',
      label: '榜单',
      options: Object.entries(HOME_RANKINGS).map(([value, label]) => ({ value, label })),
    });
    normalizedSelection.ranking = selection.ranking;
    sectionTitle = HOME_RANKINGS[selection.ranking as keyof typeof HOME_RANKINGS];
  } else {
    sectionTitle = '已完结';
  }
  return {
    schemaVersion: 1,
    title: 'Wenku8 书库',
    filters,
    selectedFilters: normalizedSelection,
    sections: [{ id: 'catalog', title: sectionTitle, items: parsed.items }],
    nextCursor: nextPage === null ? null : `page-${nextPage}`,
    complete: nextPage === null,
  };
};

export const buildDetailRequest = (remoteBookId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId)) throw new Error('INVALID_BOOK_ID');
  return {
    url: `${ORIGIN}/book/${remoteBookId}.htm`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: `${ORIGIN}/`,
  };
};

export const parseDetail = (html: string, remoteBookId: string) => {
  const title = firstText(html, [
    /<h1\b[^>]*>([\s\S]*?)<\/h1>/i,
    /<div\b[^>]*id=["']title["'][^>]*>([\s\S]*?)<\/div>/i,
    /<title\b[^>]*>\s*([\s\S]*?)(?:\s*-\s*[^<]*?文库|\s*\|\s*[^<]*?文库)[\s\S]*?<\/title>/i,
  ]) ?? documentTitle(html);
  if (!title) throw new Error('MALFORMED_SOURCE_RESPONSE');
  const author = firstText(html, [/(?:小说作者|文章作者|作者)\s*[：:]\s*([^<\n]+)/i]);
  const description = detailIntroduction(html);
  const cover = [...html.matchAll(/<img\b([^>]*)>/gi)]
    .map((match) => attribute(match[1] ?? '', 'src'))
    .find((src): src is string => src !== null && new RegExp(`/(?:files/article/image/\\d+/${remoteBookId}/|image/\\d+/${remoteBookId}/${remoteBookId}s?\\.)`, 'i').test(src)) ?? null;
  const status = firstText(html, [/(?:写作进程|文章状态|小说状态|状态)\s*[：:]\s*([^<\n]+)/i]);
  const tagsText = firstText(html, [/(?:小说Tags|小说标签|作品Tags|标签|小说类别|文章类别|类型)\s*[：:]\s*([^<\n]+)/i]);
  const tags = tagsText ? tagsText.split(/[\s,，/|]+/).map((tag) => tag.trim()).filter(Boolean) : [];
  return {
    summary: {
      sourceId: SOURCE_ID,
      remoteBookId,
      title,
      author,
      coverUrl: cover ? absoluteMediaUrl(cover, `${ORIGIN}/book/${remoteBookId}.htm`) : null,
      canonicalUrl: `${ORIGIN}/book/${remoteBookId}.htm`,
    },
    description,
    tags: [...new Set(tags)],
    status,
  };
};

export const buildDirectoryRequest = (remoteBookId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId)) throw new Error('INVALID_BOOK_ID');
  return {
    url: `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: `${ORIGIN}/book/${remoteBookId}.htm`,
  };
};

export const parseDirectory = (html: string, remoteBookId: string) => {
  const chapters: Array<{ chapterId: string; title: string; url: string; volumeTitle: string | null }> = [];
  const seen = new Set<string>();
  let volumeTitle: string | null = null;
  const tokens = /<td\b([^>]*(?:id|class)\s*=\s*["'][^"']*vcss[^"']*["'][^>]*)>([\s\S]*?)<\/td>|<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  for (let match = tokens.exec(html); match; match = tokens.exec(html)) {
    if (match[1] !== undefined) {
      volumeTitle = stripTags(match[2] ?? '') || null;
      continue;
    }
    const href = attribute(match[3] ?? '', 'href');
    const title = stripTags(match[4] ?? '');
    if (!href || !title) continue;
    const decodedHref = decodeEntities(href);
    const queryAid = /[?&]aid=(\d{1,12})(?:[&#]|$)/i.exec(decodedHref)?.[1];
    const queryCid = /[?&]cid=(\d{1,16})(?:[&#]|$)/i.exec(decodedHref)?.[1];
    const staticMatch = /\/novel\/(?:\d+\/)?(\d{1,12})\/(\d{1,16})\.htm(?:$|[?#])/i.exec(decodedHref);
    const relativeChapterId = /^(\d{1,16})\.htm(?:$|[?#])/i.exec(decodedHref)?.[1];
    const chapterId = queryCid ?? staticMatch?.[2] ?? relativeChapterId;
    const addressedBookId = queryAid ?? staticMatch?.[1] ?? (relativeChapterId ? remoteBookId : undefined);
    if (!chapterId || addressedBookId !== remoteBookId || seen.has(chapterId)) continue;
    const url = relativeChapterId
      ? `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}&cid=${chapterId}`
      : absoluteUrl(decodedHref, `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}`);
    if (!url.startsWith(`${ORIGIN}/`)) continue;
    chapters.push({ chapterId, title, url, volumeTitle });
    seen.add(chapterId);
  }
  if (!chapters.length) throw new Error('EMPTY_SOURCE_RESPONSE');
  return { sourceId: SOURCE_ID, remoteBookId, chapters };
};

export const buildChapterRequest = (url: string, remoteBookId: string, chapterId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId) || !/^\d{1,16}$/.test(chapterId)) throw new Error('INVALID_CHAPTER_ID');
  const normalized = absoluteUrl(url, `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}`);
  if (!normalized.startsWith(`${ORIGIN}/`)) throw new Error('ORIGIN_NOT_GRANTED');
  const queryAid = /[?&]aid=(\d{1,12})(?:[&#]|$)/i.exec(normalized)?.[1];
  const queryCid = /[?&]cid=(\d{1,16})(?:[&#]|$)/i.exec(normalized)?.[1];
  const staticMatch = /\/novel\/(?:\d+\/)?(\d{1,12})\/(\d{1,16})\.htm(?:$|[?#])/i.exec(normalized);
  if (!((queryAid === remoteBookId && queryCid === chapterId) ||
    (staticMatch?.[1] === remoteBookId && staticMatch?.[2] === chapterId))) throw new Error('CHAPTER_IDENTITY_MISMATCH');
  return {
    url: normalized,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
    referrerUrl: `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}`,
  };
};

export const parseChapter = (html: string, remoteBookId: string, chapterId: string, fallbackTitle: string) => {
  const container = findBalancedContainer(html, ['content', 'contentmain', 'chapter-content']);
  if (container === null) throw new Error('MALFORMED_SOURCE_RESPONSE');
  const cleaned = container
    .replace(/<ul\b[^>]*(?:id|class)=["'][^"']*contentdp[^"']*["'][^>]*>[\s\S]*?<\/ul>/gi, ' ')
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ');
  const title = firstText(html, [
    /<h1\b[^>]*>([\s\S]*?)<\/h1>/i,
    /<div\b[^>]*id=["']title["'][^>]*>([\s\S]*?)<\/div>/i,
  ]) ?? fallbackTitle;
  const blocks: Array<Record<string, string | number | null>> = [];
  let paragraphIndex = 0;
  let imageIndex = 0;
  const appendText = (fragment: string) => {
    const paragraphMatches = [...fragment.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)].map((match) => stripTags(match[1] ?? ''));
    const paragraphs = (paragraphMatches.length
      ? paragraphMatches
      : fragment.replace(/<br\s*\/?\s*>/gi, '\n').split(/\n\s*\n|\r?\n/u).map(stripTags))
      .filter((text) => text && text !== title && !/^(?:上一章|下一章|返回目录|返回书目|章节列表|加入书签|添加书签)$/u.test(text));
    for (const text of paragraphs) {
      paragraphIndex += 1;
      blocks.push({ kind: 'paragraph', blockId: `p-${String(paragraphIndex).padStart(4, '0')}`, text });
    }
  };
  const images = /<img\b([^>]*)>/gi;
  let cursor = 0;
  for (let match = images.exec(cleaned); match; match = images.exec(cleaned)) {
    appendText(cleaned.slice(cursor, match.index));
    const illustration = admittedIllustration(match[1] ?? '', `${ORIGIN}/modules/article/reader.php?aid=${remoteBookId}&cid=${chapterId}`);
    if (illustration !== null) {
      imageIndex += 1;
      blocks.push({
        kind: 'image',
        blockId: `i-${String(imageIndex).padStart(4, '0')}`,
        url: illustration.url,
        altText: illustration.altText ?? `${title} 插图 ${imageIndex}`,
        width: illustration.width,
        height: illustration.height,
      });
    }
    cursor = images.lastIndex;
  }
  appendText(cleaned.slice(cursor));
  if (!blocks.length) throw new Error('EMPTY_SOURCE_RESPONSE');
  return {
    sourceId: SOURCE_ID,
    remoteBookId,
    contentId: chapterId,
    revision: null,
    title,
    blocks,
  };
};

export const buildRemoteLibraryRequest = (cursor: string | null): NetworkRequest => {
  if (cursor !== null && !/^page-[2-9][0-9]{0,2}$/.test(cursor)) throw new Error('INVALID_REMOTE_CURSOR');
  const suffix = cursor === null ? '' : `&cursor=${encodeURIComponent(cursor)}`;
  return {
    url: `${ORIGIN}/modules/article/bookcase.php?action=list${suffix}`,
    method: 'GET',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    decode: 'gb18030',
    cache: 'network-only',
  };
};

export const parseRemoteLibrary = (html: string): { items: BookSummary[]; nextCursor: string | null; complete: boolean } => {
  const parsed = parseSearch(html);
  const cursor = /data-next-cursor=["']([^"']+)["']/i.exec(html)?.[1] ?? null;
  if (cursor !== null && !/^page-[2-9][0-9]{0,2}$/.test(cursor)) throw new Error('INVALID_REMOTE_CURSOR');
  const complete = /data-complete=["']true["']/i.test(html);
  if (!complete && cursor === null) throw new Error('INCOMPLETE_REMOTE_LIBRARY');
  return { items: parsed.items, nextCursor: cursor, complete };
};

export const buildRemoteLibraryAddRequest = (remoteBookId: string): NetworkRequest => {
  if (!/^\d{1,12}$/.test(remoteBookId)) throw new Error('INVALID_BOOK_ID');
  return {
    url: `${ORIGIN}/modules/article/bookcase.php`,
    method: 'POST',
    headers: { Accept: 'text/html,application/xhtml+xml' },
    form: { action: 'add', aid: remoteBookId },
    decode: 'gb18030',
    cache: 'network-only',
  };
};

export const parseRemoteLibraryAdd = (html: string, remoteBookId: string) => {
  const outcome = /data-outcome=["'](applied|already-present)["']/i.exec(html)?.[1];
  if (outcome !== 'applied' && outcome !== 'already-present') throw new Error('AMBIGUOUS_REMOTE_ADD');
  return { sourceId: SOURCE_ID, remoteBookId, outcome };
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
  buildRemoteLibraryRequest,
  parseRemoteLibrary,
  buildRemoteLibraryAddRequest,
  parseRemoteLibraryAdd,
  buildHomeRequest,
  parseHome,
};

declare global {
  var tsuyomiExtension: typeof api | undefined;
}
globalThis.tsuyomiExtension = api;
export default api;
