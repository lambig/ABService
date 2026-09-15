import { describe, expect, it } from "vitest";
import {
  renderArticle,
  renderPageFrame,
  renderSiteNav,
  type ArticlePresentation,
  type AlbumPresentation,
  renderAlbum,
} from "./index";

const article: ArticlePresentation = {
  title: "<script>title</script>",
  body: "<img src=x onerror=alert(1)>",
  bodyFormat: "PLAIN_TEXT",
  publishedAt: null,
  tags: ["<tag>"],
  album: null,
};
const album: AlbumPresentation = {
  albumId: "a/b",
  title: "<album>",
  artistDisplayName: "<artist>",
  originalWorkNote: null,
  releaseDate: "2026-08-14",
  catalogNumber: null,
  isdn: null,
  description: "**作品の説明**",
  descriptionFormat: "MARKDOWN",
  tracks: [
    { trackNo: 1, title: "<track>", artistDisplayName: null, tunes: [] },
  ],
  externalAudios: [],
  coverImageUrl: "/assets/cover.png",
  eventName: "イベント",
  eventDate: "2026-08-15",
  eventPlace: "会場",
  eventSpaceNumber: "A-01",
  eventNote: "<note>",
  basePrice: { amount: 1200, currency: "JPY" },
};

describe("公開記事の描画", () => {
  it("未公開の日時は作らず、タイトル・タグ・平文をエスケープする", () => {
    const html = renderArticle(article, "/assets");
    expect(html).toContain("&lt;script&gt;title&lt;/script&gt;");
    expect(html).toContain("&lt;tag&gt;");
    expect(html).toContain("&lt;img src=x onerror=alert(1)&gt;");
    expect(html).not.toContain("<time");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("この記事の作品");
  });
  it("公開日時と作品詳細を描画し、曲目の後ろにイベントと額を置く", () => {
    const html = renderArticle(
      { ...article, publishedAt: "2026-08-15T16:00:00Z", album },
      "/assets",
    );
    expect(html).toContain("2026年8月16日");
    expect(html).toContain("2026年8月15日");
    expect(html).toContain("data-public-album");
    expect(html).toContain("&lt;track&gt;");
    expect(html).toContain("<strong>作品の説明</strong>");
    expect(html.indexOf("data-album-tracks")).toBeLessThan(
      html.indexOf("data-album-event"),
    );
    expect(html.indexOf("data-album-event")).toBeLessThan(
      html.indexOf("data-album-price"),
    );
    expect(html).toContain('src="/assets/cover.png"');
    expect(html).toContain("会場 A-01");
    expect(html).toContain("￥1,200");
    expect(html).toContain("&lt;note&gt;");
    expect(html).toContain('translate="no"');
  });
  it.each([
    "javascript:alert(1)",
    "data:text/html,test",
    "//other.invalid/image",
    "/\\other.invalid/image",
  ])("危険な画像URL %s を描画しない", (url) => {
    expect(
      renderArticle(
        { ...article, album: { ...album, coverImageUrl: url } },
        "/assets",
      ),
    ).not.toContain("<img");
  });
  it("Markdown は共有の記法と画像配信ベース判定を通す", () => {
    const html = renderArticle(
      {
        ...article,
        bodyFormat: "MARKDOWN",
        body: "## 見出し\n\n![ok](/assets/image.png)\n\n![bad](/elsewhere/image.png)\n\n<script>alert(1)</script>",
      },
      "/assets",
    );
    expect(html).toContain("<h2>見出し</h2>");
    expect(html).toContain('src="/assets/image.png"');
    expect(html).not.toContain('src="/elsewhere');
    expect(html).not.toContain("<script");
  });
  it("任意項目を持たない作品は空の行や壊れた画像を出さない", () => {
    const html = renderArticle(
      {
        ...article,
        album: {
          ...album,
          eventName: null,
          coverImageUrl: null,
          basePrice: null,
        },
      },
      "/assets",
    );
    expect(html).not.toContain("data-album-event");
    expect(html).not.toContain("<img");
    expect(html).not.toContain("￥");
    expect(html).not.toContain("会場");
  });
  it("プレイヤーの後ろに記事本文を置き、作品ページでは価格を出さない", () => {
    const withAudio = {
      ...album,
      externalAudios: [{ url: "https://soundcloud.com/example/test" }],
    };
    const html = renderArticle({ ...article, album: withAudio }, "/assets");
    expect(html.indexOf("data-album-audio")).toBeLessThan(
      html.indexOf("data-article-body"),
    );
    expect(html.indexOf("data-article-body")).toBeLessThan(
      html.indexOf("data-album-tracks"),
    );
    expect(html).not.toContain("<img");
    const standalone = renderAlbum(withAudio, "/assets");
    expect(standalone).toContain("<h1");
    expect(standalone).not.toContain("data-article-body");
    expect(standalone).not.toContain("data-album-price");
  });
  it("作品の概要と曲目クレジットも安全に描画する", () => {
    const html = renderAlbum(
      {
        ...album,
        description: "<script>alert(1)</script>",
        tracks: [
          {
            trackNo: 1,
            title: "<track>",
            artistDisplayName: "<artist>",
            tunes: [
              {
                tuneTitle: "<tune>",
                composerCreditOverride: "<composer>",
                arrangerCreditOverride: "<arranger>",
              },
            ],
          },
        ],
      },
      "/assets",
    );
    expect(html).not.toContain("<script");
    expect(html).toContain("&lt;tune&gt;");
    expect(html).toContain("&lt;composer&gt;");
    expect(html).toContain("&lt;arranger&gt;");
  });
  it("外枠のサイト文言をエスケープし、フッターの有無と見出しの役割を保つ", () => {
    const html = renderPageFrame(
      {
        name: "<site>",
        isHome: false,
        copyrightHolder: "<holder>",
        year: 2026,
      },
      renderArticle(article, "/assets"),
      renderSiteNav(),
    );
    expect(html).toContain("&lt;site&gt;");
    expect(html).toContain("© 2026 &lt;holder&gt;");
    expect(html).toContain("<main");
    expect(html).toContain('aria-label="主要な導線"');
    expect(
      renderPageFrame(
        { name: "Site", isHome: true, copyrightHolder: null, year: 2026 },
        "",
        "",
      ),
    ).not.toContain("<footer");
  });
});
