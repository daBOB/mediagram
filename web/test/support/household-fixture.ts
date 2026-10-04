/** One household for the profile tests: the admin, a parent, her kid at FSK 6, and a kid nobody made here. */

export const ANDRE = { id: "andre", name: "andre", createdAt: 1, kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true };
export const MAJA = { id: "maja", name: "Maja", createdAt: 2, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true };
export const TIM = { id: "tim", name: "Tim", createdAt: 3, kids: true, kidsAge: 6, parentId: "maja", admin: false, hasPin: false };
export const LEA = { id: "lea", name: "Lea", createdAt: 4, kids: true, kidsAge: 12, parentId: null, admin: false, hasPin: false };

/** Every write a test's stub server was sent, as `[method, url, body]`. */
export const writesOf = (requests: { url: string; options: RequestInit | undefined }[]) =>
  requests.filter((request) => request.options?.method)
    .map((request) => [request.options!.method, request.url, JSON.parse(String(request.options!.body))]);
