# EmergencyResponseApp

Android responder application for live emergency-response operations.

## Real data sources

- Hostinger PHP/MySQL: authentication, assigned and active incidents, assignment status, incident completion, route history/analytics, backup and resource requests, After-Action Reports, coordination tips, department chat, and emergency broadcasts.
- Firebase Realtime Database: private responder chat, responder presence, private-thread unread state, and live locations.
- Firebase Cloud Messaging: private-message, department-message, emergency-broadcast, and assigned-incident notifications.

No generated incident feed or device-only operational report is used as an authoritative record.

## Notification integration

The current project includes:

- private and department chat notifications;
- emergency broadcast notifications and acknowledgements;
- assigned-incident notifications;
- high-importance channels with separate chat, broadcast, and assignment vibration patterns;
- notification deep links to the exact chat, broadcast, or Home assignment destination;
- FCM token registration and logout deactivation;
- stable event-key deduplication between FCM and Home polling;
- a direct system-channel shortcut in **Account Settings > Alerts & vibration**;
- pull-to-refresh on Home, Coordination, Post-Incident Reports, and Route Analytics.

The Android client is ready to receive all four events. The live PHP/dispatch server must send high-priority data messages using the contract in `FCM_NOTIFICATION_PAYLOADS.md`. Server deployment code, database migrations, and Firebase service-account credentials are not bundled in this Android archive.

## Operational workflow refinements

- **Reports** is now reports-only: responder-facing service reviews and star ratings are removed, while completed incidents, **Create Report**, report drafts/submission, PDF export, and equipment/supply requests remain. See `SERVICE_REVIEW_REMOVAL.md`.
- **Coordination voice messages** work in private responder chats and department channels. Tap the microphone with an empty composer, record for up to two minutes, then send or discard. Voice notes support in-app play, pause, seeking, notifications, and Shared Media & Files. Deploy the matching API package first; see `VOICE_MESSAGE_IMPLEMENTATION.md`.
- **Home > Backup Requests** now requests response personnel, teams, units, or operational capabilities. The responder chooses the exact backup option and can add quantity, staging, transport, or other dispatch details.
- **Reports > Equipment** is reserved for equipment, PPE, consumables, medical supplies, communications/power, and logistics replenishment. It no longer presents responders or emergency vehicles as post-incident resource categories.
- The Coordination Portal has only **Chats** and **Departments** as persistent tabs. The pencil action opens a searchable responder-directory bottom sheet for starting a private chat.
- Narrative fields in After-Action Reports and equipment/supply request notes include per-field quick templates. Responders can apply, add, edit, delete, or restore templates; custom templates are stored locally on the device.

## Build

```bash
./gradlew clean assembleDebug
```

The project requires Android Studio/Gradle access to the configured Android and Maven dependencies.

## Security note

The current project-provided Firebase Realtime Database rules are public read/write. Firebase Authentication or another server-verified bearer session and restrictive rules are still required before the complete system should be considered production-secure.

## Entry and onboarding experience

The entry screen now includes a muted five-second emergency-response motion clip that loops continuously above the **Proceed** button. The bundled asset is:

`app/src/main/res/raw/emergency_response_intro.mp4`

It is rendered through `LoopingVideoView`, which uses a `TextureView` so the clip follows the rounded Compose card shape. To use a different clip, replace that MP4 while keeping the same lowercase resource filename. Use an Android-compatible H.264/AVC MP4 with no required audio track.

A **How to use this app** action opens an image-driven, scrollable responder guide. Add one long tutorial image or numbered tutorial pages under `app/src/main/res/drawable-nodpi/`; users can open each page full screen and pinch to zoom. See `HOW_TO_TUTORIAL_IMAGES.md`. Signed-in users can reopen the guide from the help icon in **Account Settings**.

The Assigned Incidents empty state now presents a clear, lightly animated “waiting for an incident assignment” state rather than a content-heavy incident-style card.

## Connectivity and safe offline behavior

The responder app is intentionally **not blocked at launch** when the device has no internet. It now distinguishes validated internet access, a reachable network with a dispatch-server failure, and a genuine empty incident list.

- Connectivity is considered online only when Android reports both `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`.
- A signed-in responder can reopen the app offline. A fresh sign-in still requires internet because OTP delivery and verification are server operations.
- The email step disables **Send OTP** while offline and explains why. The existing OTP verification UI and verification flow are unchanged.
- Server-dependent screens show an app-wide offline banner. Home also displays truthful offline, checking, server-unavailable, and online/waiting states; it does not claim a true empty state before the first request completes.
- Assigned incidents, active incidents, and backup requests use a responder-scoped last-known-good cache with a visible last-sync time. The cache is cleared on logout.
- A previously non-empty list is cleared only after two consecutive successful empty responses. This protects responders from one transient empty API response while still allowing completed/removed records to disappear promptly.
- Polling pauses while connectivity is unavailable and resumes automatically after Android validates the connection again.
- Incident-status changes, incident completion, backup-request creation/cancellation, broadcast acknowledgement, and other server writes are disabled or rejected with a clear message while offline. Profile edits and photos remain local while offline and must be saved/uploaded again after reconnecting.
- Authentication is separate from presence. Pressing Home/Back does not clear the session or set the responder offline. The responder remains online and assignable for a 60-minute background grace period, then presence changes to offline without forcing another OTP login.
- Foreground presence is renewed every five minutes. Active route monitoring extends the background lease and keeps the operational unit state busy/en route rather than falsely available or offline.
- FCM registration remains active after a background timeout, so chat and broadcast notifications can still reach the signed-in device. Only explicit Logout unregisters the device token.
- Local logout and cache clearing always continue even if push-token, API logout, or presence cleanup cannot reach the server.

This is a **safe read-only fallback**, not a full offline operations queue. New assignments, chat, push delivery, map tiles/routing, OTP, uploads, and dispatch mutations still require internet. Cached incident information is labeled as last known and must not be treated as a live dispatch confirmation while offline.

## Connectivity implementation files

- `app/src/main/java/com/ers/emergencyresponseapp/network/ConnectivityObserver.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/ui/components/ConnectivityStatusBanner.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/data/HomeDataCache.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/features/assigned/AssignedIncidentsViewModel.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/presence/ResponderPresenceManager.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/presence/PresenceTimeoutWorker.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/presence/PresenceTaskRemovalService.kt`

Deployment details, the assignment push hook, and the server cron fallback are documented in `PRESENCE_BACKGROUND_NOTIFICATIONS.md`.

`ACCESS_NETWORK_STATE` is declared in `AndroidManifest.xml`, and Gson is explicitly included for the small JSON cache.
