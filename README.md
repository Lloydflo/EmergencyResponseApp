# EmergencyResponseApp

Android responder application for live emergency-response operations.

## Real data sources

- Hostinger PHP/MySQL: authentication, assigned and active incidents, assignment status, incident completion, route history/analytics, backup and resource requests, service reviews, After-Action Reports, coordination tips, department chat, and emergency broadcasts.
- Firebase Realtime Database: private responder chat, responder presence, private-thread unread state, and live locations.
- Firebase Cloud Messaging: private-message, department-message, and emergency-broadcast notifications.

No generated incident feed or device-only operational report is used as an authoritative record.

## Notification integration

The current project includes:

- PM and department chat notifications;
- emergency broadcast notifications and acknowledgements;
- notification deep links to the exact chat or broadcast;
- FCM token registration and logout deactivation;
- pull-to-refresh on Home, Coordination, Post-Incident/Reviews, and Route Analytics.

The PHP notification patch and `002_notification_delivery.sql` must be deployed on the live server, and the server must be configured with a private Firebase service-account path.

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

A **How to use this app** action opens a six-step, scrollable responder guide with labeled phone previews for sign-in, assignments, navigation, coordination, backup requests, and incident completion. Signed-in users can reopen the same guide from the help icon in **Account Settings**.

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
- The inactivity timer does not force an offline responder back to OTP login. Normal timeout enforcement resumes after connectivity returns.
- Local logout and cache clearing always continue even if push-token, API logout, or presence cleanup cannot reach the server.

This is a **safe read-only fallback**, not a full offline operations queue. New assignments, chat, push delivery, map tiles/routing, OTP, uploads, and dispatch mutations still require internet. Cached incident information is labeled as last known and must not be treated as a live dispatch confirmation while offline.

## Connectivity implementation files

- `app/src/main/java/com/ers/emergencyresponseapp/network/ConnectivityObserver.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/ui/components/ConnectivityStatusBanner.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/data/HomeDataCache.kt`
- `app/src/main/java/com/ers/emergencyresponseapp/features/assigned/AssignedIncidentsViewModel.kt`

`ACCESS_NETWORK_STATE` is declared in `AndroidManifest.xml`, and Gson is explicitly included for the small JSON cache.
