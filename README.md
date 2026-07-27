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
