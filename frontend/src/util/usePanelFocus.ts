import { useEffect, useRef, type RefObject } from "react";

/**
 * Focus handling for a panel that opens over the map: it takes focus when it opens and whenever
 * `contentKey` changes (so keyboard and screen-reader users land on it and Escape works), and
 * on close focus returns to what had it on open. A click on the map leaves focus on <body>,
 * which can't take it back, so then `returnFocusTo` gets it instead.
 * Returns the ref to put on the panel, which needs tabIndex={-1}.
 *
 * `contentKey` is compared by identity, so pass a value (an id, a string), not an object built
 * during render, or the panel would take focus on every render.
 */
export function usePanelFocus<T extends HTMLElement>(
  contentKey: unknown,
  returnFocusTo?: RefObject<HTMLElement | null>,
): RefObject<T | null> {
  const panel = useRef<T>(null);

  // Declared first so it captures the opener before the effect below moves focus. Its cleanup
  // must run only on unmount, so `contentKey` stays out of its dependencies.
  useEffect(() => {
    const opener = document.activeElement;
    return () => {
      if (opener instanceof HTMLElement && opener !== document.body && opener.isConnected) {
        opener.focus();
      } else {
        returnFocusTo?.current?.focus();
      }
    };
  }, [returnFocusTo]);

  useEffect(() => {
    panel.current?.focus();
  }, [contentKey]);

  return panel;
}
