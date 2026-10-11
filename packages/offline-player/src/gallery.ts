/* eslint-disable functional/immutable-data -- This view owns its DOM and artwork object URLs for the page lifetime. */
import type { PresentationData } from "abservice-listening-presentation";
import { element } from "./dom";

export const gallery = (
  data: PresentationData,
  select: (id: string) => void,
): void => {
  const library = element("#library", HTMLElement);
  const urls: string[] = [];
  data.albums
    .filter((album) =>
      data.playbackItems.some((item) => item.albumId === album.albumId),
    )
    .forEach((album) => {
      const items = data.playbackItems.filter(
        (item) => item.albumId === album.albumId,
      );
      const primary = (items.find((item) => item.kind === "album-crossfade") ??
        items[0]) as (typeof items)[number];
      const card = document.createElement("section");
      card.className = "album-card";
      const button = document.createElement("button");
      button.className = "album-choice";
      button.dataset["playbackItemId"] = primary.playbackItemId;
      button.setAttribute("aria-label", primary.title);
      button.setAttribute("aria-pressed", "false");
      const cover = document.createElement("span");
      cover.className = "album-cover";
      cover.setAttribute("aria-hidden", "true");
      const artwork = album.artwork;
      (artwork !== undefined
        ? () => {
            const image = document.createElement("img");
            image.src = URL.createObjectURL(artwork);
            urls.push(image.src);
            image.alt = "";
            image.loading = "lazy";
            cover.append(image);
          }
        : () => {
            cover.classList.add("cover-placeholder");
            cover.textContent = "♪";
          })();
      const title = document.createElement("span");
      title.className = "album-name";
      title.title = album.title;
      const titleText = document.createElement("span");
      titleText.className = "album-name-text";
      titleText.textContent = album.title;
      title.append(titleText);
      button.append(cover, title);
      button.addEventListener("click", () => {
        select(primary.playbackItemId);
      });
      card.append(button);
      /* Track-only and mixed packages retain their actual playable items, never inferred track boundaries. */
      items
        .filter((item) => item !== primary)
        .forEach((item) => {
          const track = document.createElement("button");
          track.className = "track-choice";
          track.textContent = item.title;
          track.dataset["playbackItemId"] = item.playbackItemId;
          track.setAttribute("aria-pressed", "false");
          track.addEventListener("click", () => {
            select(item.playbackItemId);
          });
          card.append(track);
        });
      library.append(card);
    });
  const previous = element("#albums-previous", HTMLButtonElement);
  const next = element("#albums-next", HTMLButtonElement);
  const layout = matchMedia("(max-width: 700px), (orientation: portrait)");
  const horizontal = () => layout.matches;
  const updateTitles = (): void => {
    library.querySelectorAll<HTMLElement>(".album-name").forEach((title) => {
      const text = title.firstElementChild as HTMLElement;
      const distance = Math.max(0, text.scrollWidth - title.clientWidth);
      title.dataset["overflow"] = String(distance > 1);
      title.style.setProperty("--title-overflow", `${String(-distance)}px`);
      title.style.setProperty(
        "--title-duration",
        `${String(Math.max(8, (distance / 24) * 2 + 4))}s`,
      );
    });
  };
  const update = () => {
    previous.textContent = horizontal() ? "‹" : "↑";
    next.textContent = horizontal() ? "›" : "↓";
    const position = horizontal() ? library.scrollLeft : library.scrollTop;
    const end = horizontal()
      ? library.scrollWidth - library.clientWidth
      : library.scrollHeight - library.clientHeight;
    previous.disabled = position <= 1;
    next.disabled = position >= end - 1;
  };
  const move = (direction: number) => {
    const distance =
      direction *
      (horizontal() ? library.clientWidth : library.clientHeight) *
      0.8;
    library.scrollBy({
      left: horizontal() ? distance : 0,
      top: horizontal() ? 0 : distance,
      behavior: "smooth",
    });
  };
  previous.addEventListener("click", () => {
    move(-1);
  });
  next.addEventListener("click", () => {
    move(1);
  });
  library.addEventListener("scroll", update);
  layout.addEventListener("change", update);
  const observer = new ResizeObserver(() => {
    update();
    updateTitles();
  });
  observer.observe(library);
  library.querySelectorAll(".album-name").forEach((title) => {
    observer.observe(title);
  });
  void document.fonts.ready.then(updateTitles);
  window.addEventListener(
    "pagehide",
    () => {
      observer.disconnect();
      layout.removeEventListener("change", update);
      urls.forEach((url) => {
        URL.revokeObjectURL(url);
      });
    },
    { once: true },
  );
};
