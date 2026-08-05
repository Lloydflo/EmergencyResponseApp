# Responder presence and background-notification policy

## Final behavior

Authentication, dispatch availability, operational unit status, app visibility,
and push reachability are separate states.

| Situation | Dispatch presence | Unit status | Push notifications |
|---|---|---|---|
| App visible with a valid session | Online | Available, busy, en route, or on scene | Enabled |
| Home/Back or another app opened for less than 60 minutes | Online | Preserved | Enabled |
| Active navigation continues in the background | Online | En route/on scene | Enabled |
| App remains in background for 60 minutes with no active route | Offline | Offline only when no active assignment; otherwise busy/en route/on scene is preserved | Enabled |
| App is explicitly swiped from Recents with no active route | Best-effort immediate offline; one-hour timeout remains fallback | Active assignment status is preserved by the API | Enabled |
| Recents is removed while active navigation continues | Online/responding | En route/on scene | Enabled |
| Explicit Logout | Offline | Preserved safely by the API | Device token unregistered |
| Android Force stop | Android blocks app delivery until it is opened again | Server timeout/cron applies | FCM cannot deliver while force-stopped |

Pressing **Back** or **Home** no longer sets the responder offline and no longer
clears the login session. The responder can therefore remain assignable and can
receive new-assignment/chat alerts while outside the UI.

An assigned responder is **busy/responding**, not offline. This prevents a second
assignment while preserving coordination, navigation, location tracking, and
notification delivery.

## Android implementation

The policy is centralized in:

- `presence/ResponderPresenceManager.kt`
- `presence/PresenceTimeoutWorker.kt`
- `presence/PresenceTaskRemovalService.kt`

`MainActivity` renews the foreground lease every five minutes. `onStop()` starts
a one-hour background lease instead of writing offline immediately. Returning to
the app cancels the pending timeout. The timeout worker changes presence only;
it never logs the responder out and never unregisters the FCM token.

Firebase `/users/{id}` now contains:

- `isOnline`: dispatch/chat reachability flag;
- `lastSeen`: server timestamp of the most recent presence write;
- `appState`: `foreground`, `background`, `responding_background`,
  `disconnected`, or `offline`;
- `onlineUntil`: client lease deadline in epoch milliseconds.

Coordination treats an expired `onlineUntil` as offline even if a killed process
could not perform its final Firebase write.

## Required API deployment

Upload the updated API files from the companion API package:

- `set-unit-presence.php`
- `expire-stale-presence.php`
- `send-assignment-notification.php`
- the existing `_fcm.php` and device-token endpoints

The updated `set-unit-presence.php` does not overwrite an active assignment with
`unit_status=offline`. A background responder who is already assigned remains
`busy`, `en_route`, or `on_scene`.

### Server-authoritative one-hour expiration

Set a strong environment variable:

```text
APP_PRESENCE_CRON_KEY=<long-random-secret>
```

The maintenance script can be run directly by local cron without an HTTP key:

```cron
*/5 * * * * /usr/bin/php /absolute/path/to/api/api_app/expire-stale-presence.php >/dev/null 2>&1
```

If the hosting provider supports only URL cron jobs, POST to the endpoint over
HTTPS with:

```text
X-Presence-Cron-Key: <APP_PRESENCE_CRON_KEY>
```

This server job is important because Android WorkManager is intentionally
inexact under Doze and manufacturer battery restrictions.

### Dispatcher eligibility

The dispatcher must use both presence freshness and operational status. The
selection condition should be equivalent to:

```sql
JOIN user_presence p ON p.user_id = u.id
WHERE p.is_online = 1
  AND p.last_seen_at >= NOW() - INTERVAL 60 MINUTE
  AND u.unit_status = 'available'
```

Do not use Activity foreground/background as the assignment condition. Do not
select responders whose unit status is `busy`, `en_route`, `on_scene`,
`maintenance`, or `offline`.

## New-assignment push hook

Set another strong environment variable:

```text
APP_ASSIGNMENT_PUSH_KEY=<different-long-random-secret>
```

Immediately **after the assignment database transaction commits**, call:

```text
POST /api/api_app/send-assignment-notification.php
X-Assignment-Key: <APP_ASSIGNMENT_PUSH_KEY>
Content-Type: application/x-www-form-urlencoded

assignment_id=815
```

The endpoint reads the committed assignment, resolves the exact responder,
loads every active device token, and sends a high-priority data-only FCM event
of type `assigned_incident`. The Android receiver handles vibration, duplicate
suppression, and Home deep-linking.

Example PHP call from the dispatcher after `commit()`:

```php
$curl = curl_init($baseUrl . '/api/api_app/send-assignment-notification.php');
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT => 15,
    CURLOPT_HTTPHEADER => [
        'X-Assignment-Key: ' . getenv('APP_ASSIGNMENT_PUSH_KEY'),
        'Content-Type: application/x-www-form-urlencoded',
    ],
    CURLOPT_POSTFIELDS => http_build_query([
        'assignment_id' => $assignmentId,
    ]),
]);
$response = curl_exec($curl);
curl_close($curl);
```

Push failure must not roll back a committed assignment. Log the failure and let
the app load the assignment through normal API polling when it next runs.

## Verification checklist

1. Sign in and allow Android notification permission.
2. Confirm the responder is online and available in dispatch.
3. Press Home or Back; wait two to five minutes; confirm the responder remains
   assignable.
4. Assign an incident; verify vibration and an assigned-incident notification.
5. Tap the notification; verify Home opens and the assignment card appears.
6. Send a private and department chat while the app is outside the foreground;
   verify both notifications and deep links.
7. Start navigation, background the app, and confirm the unit remains en route
   rather than becoming available/offline.
8. With no active route, keep the app backgrounded for more than one hour; run
   cron and confirm presence becomes offline without logging the user out.
9. Reopen the app; confirm presence returns online without OTP.
10. Explicitly log out; confirm presence is offline and the token is unregistered.
