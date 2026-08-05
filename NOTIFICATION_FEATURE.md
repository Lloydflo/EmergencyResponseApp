# Operational notification implementation

The app now uses Firebase Cloud Messaging and Android notification channels for four operational event classes:

- private responder chat;
- department chat;
- emergency broadcasts;
- newly assigned incidents.

`EmergencyFirebaseMessagingService` processes high-priority data messages even when the activity is not visible. `AppNotificationManager` creates a dedicated channel for each event class, selects a vibration pattern, builds a status-bar-safe monochrome icon, and attaches a deep-link intent. `NotificationNavigation` passes that destination into Compose navigation when the user taps the alert.

The process defaults to a background state so a cold-started Firebase service never mistakes itself for an open Home screen. `MainActivity` updates the screen tracker from the actual navigation route. This suppresses alerts only while the responder is already viewing the exact chat, while still allowing assigned-incident alerts on Reports, Coordination, or outside the app.

Home polling remains a fallback while the process is active. FCM and polling claim the same stable event key; whichever receives the event first owns the single visible alert.

## Vibration channels

- Private and department chat: short two-pulse pattern.
- Emergency broadcast: strong three-pulse pattern.
- Assigned incident: strong dispatch pattern.

On Android 8 and newer the operating system owns channel behavior after creation. Responders can manage each channel from **Account Settings > Alerts & vibration**. The app cannot override a channel that the user has muted or disabled.

## Permission

`POST_NOTIFICATIONS` is requested after the responder reaches Home on Android 13 or newer. `VIBRATE` is declared in the manifest. If permission is denied, system notifications are not shown; in-app Home alerts can still be displayed while Home is active.

## Server integration

The Android side cannot discover a new chat, broadcast, or assignment after its process has been removed unless the server sends FCM. Deploy server-side event delivery according to `FCM_NOTIFICATION_PAYLOADS.md`. The server implementation and Firebase service-account credential are not bundled in this Android archive.
