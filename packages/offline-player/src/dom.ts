export const element = <T extends HTMLElement>(
  selector: string,
  type: { new (): T },
): T => {
  const node = document.querySelector(selector);
  return node instanceof type
    ? node
    : (() => {
        throw new Error(`Missing ${selector}`);
      })();
};

export const when = (condition: boolean, action: () => void): void => {
  (condition ? action : () => undefined)();
};
