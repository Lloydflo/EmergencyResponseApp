# Presence and outside-app notification update (v17)

## What changed

- Home, Back, screen changes, and opening another app no longer write the responder offline.
- A signed-in responder remains online/assignable for up to 60 minutes in the background.
- Reopening the app cancels the pending timeout and renews the online lease without OTP.
- Swiping the task from Recents requests a best-effort immediate offline transition.
- Android WorkManager and the server cron endpoint provide the one-hour fallback.
- Background timeout changes presence only. It does not clear authentication and does not unregister FCM.
- Explicit Logout is the only normal flow that unregisters the device token.
- Firebase socket disconnect no longer writes `isOnline=false`; it records only disconnect metadata.
- Leaving Coordination removes listeners only and no longer changes responder availability.
- Idle live-location socket disconnect is now marked as a stale/disconnected feed rather than changing dispatch presence.
- Assigned responders retain `busy`, `en_route`, or `on_scene` status even if presence later expires.
- The assignment push endpoint reasserts the operational busy lock and sends a high-priority data-only FCM event.

## Correct state model

`online` means the responder is reachable and eligible only when the unit is also `available`.
An active incident must use `busy`, `en_route`, or `on_scene` rather than `offline`.
This prevents duplicate assignment while keeping chat, navigation, location tracking, and push alerts working.

See `PRESENCE_BACKGROUND_NOTIFICATIONS.md` for server deployment and testing.
