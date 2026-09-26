/*
 * Applies the viewer's Appearance settings to <html> before the page paints:
 * the theme (dark, light, or auto — which follows the device), the accent
 * colour, and how heroes treat their artwork. A classic script, loaded
 * blocking in <head>, so there is never a flash of the wrong theme.
 *
 * The choices live in this browser's storage (appearance is a property of a
 * screen, not of a profile). Settings writes them and fires
 * `mediagram:appearance`; another tab's change arrives as `storage`.
 */
(() => {
  const root = document.documentElement;
  const system = matchMedia("(prefers-color-scheme: light)");
  const read = (name, fallback) => {
    try { return localStorage.getItem(`mediagram.${name}`) ?? fallback; } catch { return fallback; }
  };
  const apply = () => {
    const theme = read("theme", "auto");
    root.dataset.theme = theme === "auto" ? (system.matches ? "light" : "dark") : theme;
    root.dataset.accent = read("accent", "coral");
    root.dataset.backdrop = read("backdrop", "default");
  };
  apply();
  system.addEventListener("change", apply);
  addEventListener("storage", apply);
  addEventListener("mediagram:appearance", apply);
})();
