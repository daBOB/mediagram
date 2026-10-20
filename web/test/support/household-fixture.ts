/** One household for the profile tests: the admin, a parent, her kid at FSK 6, and a kid nobody made here. */

export const ANDRE = { id: "andre", name: "andre", createdAt: 1, kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true };
export const MAJA = { id: "maja", name: "Maja", createdAt: 2, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true };
export const TIM = { id: "tim", name: "Tim", createdAt: 3, kids: true, kidsAge: 6, parentId: "maja", admin: false, hasPin: false };
export const LEA = { id: "lea", name: "Lea", createdAt: 4, kids: true, kidsAge: 12, parentId: null, admin: false, hasPin: false };

/** Every write a test's stub server was sent, as `[method, url, body]`. */
export const writesOf = (requests: { url: string; options: RequestInit | undefined }[]) =>
  requests.filter((request) => request.options?.method)
    .map((request) => [request.options!.method, request.url, JSON.parse(String(request.options!.body))]);

/** Two households, as `GET /api/profiles` lists them, by name: Maja runs the flat, andre (the owner) runs home. */
export const FLAT = { id: "flat", name: "Flat", adminId: "maja" };
export const AT_HOME = { id: "home", name: "Home", adminId: "andre" };
/** The four placed over those two: andre and Lea at home, Maja and her Tim in the flat. */
export const SPLIT = [
  { ...ANDRE, owner: true, household: "home" },
  { ...MAJA, admin: true, household: "flat" },
  { ...TIM, household: "flat" },
  { ...LEA, household: "home" },
];

/** A browser's `localStorage` that keeps what it is given, starting with `entries`. */
export function storage(entries: Record<string, string> = {}) {
  const kept = new Map(Object.entries(entries));
  return {
    getItem: (key: string) => kept.get(key) ?? null,
    setItem: (key: string, value: string) => void kept.set(key, value),
    removeItem: (key: string) => void kept.delete(key),
  };
}
