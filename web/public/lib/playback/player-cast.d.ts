export function mountPlayerCast(parts: {
  button: HTMLButtonElement;
  video: HTMLVideoElement;
  filmTime: () => number;
  setId: () => string | null;
  close: () => void;
}): void;
