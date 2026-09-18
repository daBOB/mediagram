# Phase 2 — Writing over HTTP

The router answers `GET`/`HEAD`, plus `DELETE` on an HLS session. It now has
to take writes, and it is the one surface with no authentication in front of
it, so the check comes before the routing rather than inside each handler.

- `GET /api/state` — everything at once, the way `/api/sets` is fetched once.
- `PUT|DELETE /api/progress/<setId>`
- `PUT|DELETE /api/watchlist/<setId>`
- `POST /api/collections`, `PATCH|DELETE /api/collections/<id>`
- `PUT|DELETE /api/collections/<id>/items/<setId>`

Every write must carry `content-type: application/json` and, where the browser
sends one, an `Origin` that matches the host it was served from. Neither is
authentication. Both stop a form on another page from silently deleting a
collection, which is the realistic drive-by against a loopback service.

A `setId` is checked against the catalog before it is stored, so state cannot
accumulate rows for titles that do not exist.

Success: a cross-origin write is refused; a form-encoded write is refused; a
write for an unknown set is a 404; `GET /api/state` round-trips everything.
