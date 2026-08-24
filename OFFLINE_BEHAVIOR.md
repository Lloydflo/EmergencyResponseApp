# Offline and connectivity behavior

## Runtime scenarios

| Scenario | App behavior |
|---|---|
| Fresh install or logged-out device, no internet | Entry and guide can open. Email login is visible, but **Send OTP** is disabled until validated internet returns. |
| Existing valid session, no internet | Home opens in read-only fallback mode and displays cached assigned incidents, active incidents, and backup requests when available. |
| Internet is validated, but the dispatch API fails | Home reports **Dispatch service unavailable** or **Unable to refresh** instead of claiming there are no incidents. Last-known data remains visible. |
| Internet reconnects | Polling, push-token registration, and presence synchronization resume automatically. Fresh API data replaces the cache. |
| User presses Home/Back or opens another app | The verified session and FCM token remain active. Dispatch presence stays online for a 60-minute background grace period. |
| App stays backgrounded for 60 minutes without an active route | Presence changes to offline, but the user is not logged out and chat/broadcast push notifications remain enabled. Reopening the app renews online presence without OTP. |
| Active route is backgrounded | The unit remains busy/en route and route monitoring continues; it is not made falsely available. |
| Server successfully returns an empty list | A previously non-empty incident/request list is retained for one confirmation cycle, then cleared after a second consecutive successful empty response. |
| Responder attempts a server mutation offline | The action is disabled or stopped with an explicit reconnect message; the app does not pretend the change was submitted. |
| Responder logs out while offline | Local session data and the responder-scoped cache are still cleared; remote cleanup is best-effort and cannot block logout. |

## Data treated as last-known-good

The device cache is scoped by responder ID and stores:

- assigned incident DTOs;
- active incident DTOs;
- backup-request DTOs;
- the successful sync timestamp for each list.

The cache is advisory, is cleared during logout, and never substitutes for a live dispatch acknowledgement.

## Deliberate limitations

There is no durable offline write queue in this patch. The app does not queue incident completion, on-scene reports, backup requests, chat messages, broadcast acknowledgements, profile uploads, route points, or reviews for later submission. Those operations must be retried after reconnecting.

A production offline-first responder workflow would additionally require conflict resolution, encrypted operational storage, server-issued sequence/version numbers, idempotency keys for every queued mutation, and auditable synchronization outcomes.
