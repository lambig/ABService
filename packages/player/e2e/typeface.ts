/* eslint-disable functional/immutable-data -- Test-only probe elements are created, measured and removed inside the browser. */
import type { Page } from "@playwright/test";

/** 同梱書体の面。`document.fonts` から読んだ観測値 */
export type Typeface = Readonly<{
  weight: string;
  unicodeRange: string;
  status: FontFaceLoadStatus;
}>;

const FAMILY = "Klee One";

/** 日本語の面にだけ当たる見本（画面の文言） */
export const JAPANESE_SAMPLE = "作品を選んで、音を聴く";

/** ラテンの面と日本語の面の両方に当たる見本（画面の見出し） */
export const LATIN_SAMPLE = "A moment to listen.";

/**
 * 見本の文字が属する面を両方のウェイトで要求し、family の全ての面の状態を返す。
 *
 * 面が届かない場合（通信なしで shell に無い等）は `load` が拒否するため、拒否を握って状態の観測だけを
 * 続ける。失敗そのものは `status` が `error` で表す。
 */
export const loadTypefaces = (page: Page): Promise<readonly Typeface[]> =>
  page.evaluate(
    async ({ family, samples }) => {
      await Promise.all(
        samples.flatMap((sample) =>
          ["400", "600"].map((weight) =>
            document.fonts
              .load(`${weight} 1em "${family}"`, sample)
              .catch(() => []),
          ),
        ),
      );
      await document.fonts.ready;
      return Array.from(document.fonts)
        .filter((face) => face.family.replaceAll('"', "") === family)
        .map((face) => ({
          weight: face.weight,
          unicodeRange: face.unicodeRange,
          status: face.status,
        }));
    },
    { family: FAMILY, samples: [JAPANESE_SAMPLE, LATIN_SAMPLE] },
  );

/**
 * 見本が同梱書体の字形で描かれているか。
 *
 * 同じ文字列を「同梱書体、次いで等幅」で指定したときと「等幅だけ」のときとで幅を比べる。面に字形が無い、
 * または面が届いていないと等幅へ落ちて同じ幅になる。`document.fonts.check` は面が無いときも真を返すため、
 * 字形の有無はここで見る。
 */
export const drawnWithTypeface = (
  page: Page,
  sample: string,
): Promise<boolean> =>
  page.evaluate(
    ({ family, text }) => {
      const widthOf = (fontFamily: string): number => {
        const probe = document.createElement("span");
        probe.style.cssText = `position:absolute;visibility:hidden;white-space:nowrap;font-size:40px;font-family:${fontFamily}`;
        probe.textContent = text;
        document.body.append(probe);
        const width = probe.getBoundingClientRect().width;
        probe.remove();
        return width;
      };
      return widthOf(`"${family}", monospace`) !== widthOf("monospace");
    },
    { family: FAMILY, text: sample },
  );
