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
  eventCircleName: "<circle>",
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
    expect(html).not.toContain("<strong>作品の説明</strong>");
    expect(renderAlbum(album, "/assets")).toContain("<strong>作品の説明</strong>");
    expect(html.indexOf("data-album-tracks")).toBeLessThan(
      html.indexOf("data-album-event"),
    );
    expect(html.indexOf("data-album-event")).toBeLessThan(
      html.indexOf("data-album-price"),
    );
    expect(html).toContain('src="/assets/cover.png"');
    expect(html).toContain("頒布価格 1,200円");
    expect(html).toContain("&lt;note&gt;");
    expect(html).toContain('translate="no"');
  });
  it("頒布情報の節に、日付・名・会場の行とスペース・サークル名の行、価格を置き、スペース番号だけを立てる", () => {
    const html = renderArticle({ ...article, album }, "/assets");
    expect(html).toContain(
      '<section data-album-distribution class="space-y-2"><h3 class="text-lg font-medium">頒布情報</h3>',
    );
    expect(html).toContain(
      '<p><time datetime="2026-08-15">2026年8月15日</time> <span translate="no" class="notranslate">イベント</span> 会場<br />\n<strong class="text-foreground font-semibold">A-01</strong> <span translate="no" class="notranslate">&lt;circle&gt;</span></p>',
    );
    expect(html).not.toMatch(/<strong[^>]*>会場/u);
    /* 価格は頒布情報の子。節の中に入り、節の閉じより前にある */
    const section = /<section data-album-distribution[\s\S]*?<\/section>/u.exec(html)?.[0];
    expect(section).toContain("data-album-price");
    /* 作品ページは価格を持たないが、節の見出しは同じ */
    expect(renderAlbum(album, "/assets")).toContain(
      '<h2 class="text-lg font-medium">頒布情報</h2>',
    );
  });
  it("記事へ展開した作品は、見出し・発売日・品番を出さず、曲目を畳み、原作の出典を曲目の後ろに置く", () => {
    const note = {
      ...album,
      originalWorkNote: "「原作」より各曲",
      catalogNumber: "CAT-001",
    };
    const embedded = renderArticle({ ...article, album: note }, "/assets");
    expect(embedded).not.toContain('<h2 class="text-3xl');
    expect(embedded).not.toContain("2026年8月14日");
    expect(embedded).not.toContain("CAT-001");
    expect(embedded).toContain('aria-label="&lt;album&gt;"');
    expect(embedded).toContain("<details data-album-tracks");
    expect(embedded).not.toContain("<details data-album-tracks open");
    expect(embedded.indexOf("data-album-tracks")).toBeLessThan(
      embedded.indexOf("data-album-original-work"),
    );
    expect(embedded.indexOf("data-album-original-work")).toBeLessThan(
      embedded.indexOf("data-album-event"),
    );
    expect(embedded).toContain("「原作」より各曲");

    const standalone = renderAlbum(note, "/assets");
    expect(standalone).toContain("&lt;album&gt;</span></h1>");
    expect(standalone).toContain("2026年8月14日");
    expect(standalone).toContain("CAT-001");
    expect(standalone.indexOf("data-album-tracks")).toBeLessThan(
      standalone.indexOf("data-album-original-work"),
    );
  });
  it("試聴の枠は正方形で上限の高さを持ち、アートワークを出す visual 表示で埋め込む", () => {
    const html = renderAlbum(
      {
        ...album,
        externalAudios: [{ url: "https://soundcloud.com/example/test" }],
      },
      "/assets",
    );
    expect(html).toContain("mx-auto block aspect-square w-full max-w-[700px]");
    expect(html).not.toContain("max-h-[700px]");
    expect(html).toContain("visual=true");
    expect(html).not.toContain('height="166"');
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
    expect(html).not.toContain("頒布価格");
    expect(html).not.toContain("頒布情報");
    expect(html).not.toContain("会場");
  });
  it("記事は本文を一度だけ表示し、説明の直後に曲目、補足を置く", () => {
    const withAudio = {
      ...album,
      externalAudios: [{ url: "https://soundcloud.com/example/test" }],
    };
    const html = renderArticle({ ...article, body: "説明文\n\n## 頒布の案内\n\n補足本文", bodyFormat: "MARKDOWN", album: withAudio }, "/assets");
    expect(html.indexOf("data-album-audio")).toBeLessThan(
      html.indexOf("prose-body"),
    );
    expect(html.indexOf("data-article-body")).toBeLessThan(
      html.indexOf("data-album-tracks"),
    );
    expect(html.indexOf("data-album-tracks")).toBeLessThan(html.indexOf("data-article-details"));
    expect(html.match(/説明文/gu)).toHaveLength(1);
    expect(html.match(/補足本文/gu)).toHaveLength(1);
    expect(html).not.toContain("作品の説明");
    expect(html).not.toContain("<img");
    expect(html).not.toContain("試聴</");
    const standalone = renderAlbum(withAudio, "/assets");
    expect(standalone).toContain("<h1");
    expect(standalone).not.toContain("data-article-body");
    expect(standalone).not.toContain("data-album-price");
  });
  it("作品単独でも説明、曲目、見出し以降の補足の順を保つ", () => {
    const html = renderAlbum({ ...album, description: "説明文\n\n## 注意事項\n\n大切な注記" }, "/assets");
    expect(html.indexOf("説明文")).toBeLessThan(html.indexOf("data-album-tracks"));
    expect(html.indexOf("data-album-tracks")).toBeLessThan(html.indexOf("注意事項"));
    expect(html).toContain("大切な注記");
  });
  it("説明文が無く注意事項から始まる場合も、注意事項は曲目より下に置く", () => {
    const body = "## 注意事項\n\n保持する注記\n\n## 頒布案内\n\n店舗への案内";
    const standalone = renderAlbum({ ...album, description: body }, "/assets");
    const embedded = renderArticle({ ...article, body, bodyFormat: "MARKDOWN", album }, "/assets");
    for (const html of [standalone, embedded]) {
      expect(html.indexOf("data-album-tracks")).toBeLessThan(html.indexOf("注意事項"));
      expect(html.indexOf("注意事項")).toBeLessThan(html.indexOf("頒布案内"));
      expect(html).toContain("保持する注記");
      expect(html).toContain("店舗への案内");
    }
  });
  it("音源を持たない作品は、カバー画像をプレイヤーと同じ枠に置く", () => {
    const html = renderAlbum(album, "/assets");
    expect(html).toContain(
      '<img data-album-cover class="border-border mx-auto block aspect-square w-full max-w-[700px] rounded-md border object-cover"',
    );
    expect(html.indexOf("</header>")).toBeLessThan(html.indexOf("data-album-cover"));
    expect(html.indexOf("data-album-cover")).toBeLessThan(html.indexOf("prose-body"));
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
