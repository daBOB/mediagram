# Probe: history read of fresh unpinned message (2026-10-09, Europe/Berlin)

## Answer
YES. Plain `getMessages(peer, {limit:100})`, no search, no pin, returns a message sent 1 s earlier, and shows edits immediately.

## Evidence (one teleproto client on the web player's session; 1 message, 2 edits, 0 pins)
| step | result |
|---|---|
| send (silent, `#mlib-together v=1\n{"probe":true,"n":0}`) | id 28197, 2026-10-09T00:26:28.078Z (02:26:28 Berlin), 110 ms |
| +1 s list(limit 100) | count 100, present, index 0 (newest), text n=0, 821 ms (first call on connection) |
| edit1 (n=1) | ok, 87 ms, same id 28197 |
| list right after edit1 | present, index 0, text n=1 (edit visible), 158 ms |
| edit2 (n=2, 2 s after edit1) | ok, 49 ms |
| list right after edit2 | present, index 0, text n=2 (edit visible), 119 ms |
| by id `getMessages({ids:[28197]})` | len 1, found, text n=2 (current edit) |
| delete `{revoke:true}` | messages.AffectedMessages pts 82392 ptsCount 1 |
| list after delete (+1 s) | not present |
| by id after delete | `[null]` |

## Errors
None. No FLOOD_WAIT, no RPC errors. Only teleproto INFO log lines at connect.

## Deletion
Confirmed: message 28197 deleted (list absent, by-id null). Nothing else touched; live player (port 8770) not touched.

## Notes
- Edits read back with no lag at all in these samples (reads issued immediately after the edit RPC returned).
- Caveat: n=1 sample each; read was by the same client that wrote. A second session's view is covered by the push test.
- Push latency to another session NOT measured (needs second login); deferred to the device walk.
