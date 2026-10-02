# Firebase Cloud Messaging payload contract

The Android client can display and deep-link **private chat**, **department chat**, **emergency broadcast**, and **new assigned incident** alerts while the app is in the foreground, background, or not currently open.

## Server requirement

For reliable custom vibration, channel selection, duplicate suppression, and deep links, the PHP/dispatch server must send an **FCM HTTP v1 high-priority data message** to every active device token registered for the responder.

Do not add a top-level `notification` object to operational messages. On Android, a background message containing a `notification` object may be rendered directly by the operating system without running `EmergencyFirebaseMessagingService`, which bypasses the app's event parser and exact destination routing.

All values inside `data` must be strings.

```json
{
  "message": {
    "token": "RESPONDER_DEVICE_FCM_TOKEN",
    "data": {
      "type": "assigned_incident",
      "responder_id": "42",
      "assignment_id": "815",
      "incident_id": "3102",
      "reference_no": "INC-2026-03102",
      "incident_type": "fire",
      "priority": "critical",
      "location": "Barangay Central, Quezon City",
      "body": "Structure fire with possible occupants"
    },
    "android": {
      "priority": "high",
      "ttl": "300s"
    }
  }
}
```

The service also accepts the aliases listed below, but the canonical event names are recommended.

## 1. New assigned incident

Canonical type: `assigned_incident`

Required routing fields:

- `responder_id`: intended responder ID;
- at least one of `assignment_id` or `incident_id`.

Recommended display fields:

- `reference_no`;
- `incident_type`;
- `priority`;
- `location`;
- `body`.

Accepted type aliases: `new_assigned_incident`, `incident_assigned`, `new_assignment`, `dispatch_assignment`, and `assignment`.

Send this event only after the assignment transaction has committed. Fetch every active token belonging to that responder and send the same assignment ID to all devices. The client uses `assigned:<assignment_id>` as its stable duplicate-suppression key; when no assignment ID exists, it falls back to the incident ID.

The companion API package now includes `send-assignment-notification.php`. Call it after commit with `assignment_id` and the `X-Assignment-Key` header. Details are in `PRESENCE_BACKGROUND_NOTIFICATIONS.md`.

## 2. Private responder chat

Canonical type: `private_chat`

```json
{
  "message": {
    "token": "RECIPIENT_DEVICE_FCM_TOKEN",
    "data": {
      "type": "private_chat",
      "recipient_id": "42",
      "sender_id": "17",
      "sender_name": "Responder Santos",
      "thread_id": "17_42",
      "message_id": "-Oabc123",
      "body": "Unit 12 is approaching from the north entrance."
    },
    "android": {
      "priority": "high",
      "ttl": "86400s"
    }
  }
}
```

Required fields: `recipient_id`, `sender_id`, `thread_id`, and `message_id`. Accepted type aliases: `private_message`, `new_private_message`, and `chat_message`.

The existing Android sender calls `notify-private-message.php` after the Firebase Realtime Database write succeeds. That endpoint must send this data payload to the recipient's active tokens.

## 3. Department chat

Canonical type: `department_chat`

```json
{
  "message": {
    "token": "MEMBER_DEVICE_FCM_TOKEN",
    "data": {
      "type": "department_chat",
      "recipient_id": "42",
      "group_id": "6",
      "group_name": "Fire Operations",
      "sender_name": "Incident Command",
      "message_id": "9231",
      "body": "All units stage at Sector Bravo."
    },
    "android": {
      "priority": "high",
      "ttl": "86400s"
    }
  }
}
```

Required fields: `group_id` and `message_id`. `recipient_id` is strongly recommended when sending per member. Accepted type aliases: `department_message`, `group_chat`, and `group_message`.

Do not send a department alert back to the sender. Send only to approved group members with active device tokens.

## 4. Emergency broadcast

Canonical type: `broadcast`

```json
{
  "message": {
    "token": "RESPONDER_DEVICE_FCM_TOKEN",
    "data": {
      "type": "broadcast",
      "recipient_id": "42",
      "broadcast_id": "114",
      "incident_id": "3102",
      "priority": "critical",
      "title": "Evacuate Sector Bravo",
      "body": "HazMat readings are above the safe threshold."
    },
    "android": {
      "priority": "high",
      "ttl": "900s"
    }
  }
}
```

Required field: `broadcast_id`. Recommended fields: `recipient_id`, `incident_id`, `priority`, `title`, and `body`. Accepted type aliases: `emergency_broadcast`, `new_broadcast`, and `operational_broadcast`.

## 5. Dispatcher protocol alert (Emergency Broadcast / Lockdown / Mass Casualty)

Canonical type: `protocol_alert`

```json
{
  "message": {
    "token": "RESPONDER_DEVICE_FCM_TOKEN",
    "data": {
      "type": "protocol_alert",
      "protocol": "lockdown",
      "alert_id": "5231",
      "title": "Lockdown Protocol",
      "body": "🔴 LOCKDOWN\n\nArea:\n\nBarangay Holy Spirit\n\nReason:\n\nArmed suspect...",
      "priority": "critical"
    },
    "android": {
      "priority": "high",
      "ttl": "86400s"
    }
  }
}
```

Required field: `protocol`, one of `broadcast`, `lockdown`, or `mci`. Recommended fields: `alert_id`, `title`, `body`, `priority`.

This is sent to **every** active responder device (`ers_fcm_send_to_all_responders`) whenever a dispatcher fires one of the Dispatch Center quick actions — Emergency Broadcast, Lockdown Protocol, or Mass Casualty. Unlike the other event types, the Android client does not treat this as a routine, swipe-away notification: it also raises a blocking in-app overlay on top of whatever screen the responder is looking at, and the system-tray notification stays (`ongoing`) until the responder acknowledges it in-app. See `CriticalAlertCenter` / `CriticalAlertOverlay` in the Android project.

The companion PHP endpoint is `api/api_app/_protocol_alert.php` (`ers_send_protocol_alert`), called automatically by `api/activity_event.php` whenever a `dispatch_protocol` activity event is logged from `dispatcher/dispatch.php`.

## Delivery sequence on the PHP server

For each operational event:

1. Commit the chat, broadcast, or assignment record first.
2. Resolve the exact intended responder or approved group members.
3. Read only active Android FCM tokens for those users.
4. Send one HTTP v1 data message per token using a Firebase service account kept on the server.
5. Log the FCM message ID, event key, token result, and failure reason.
6. Deactivate tokens reported as unregistered or invalid.

Never place a Firebase service-account JSON file, OAuth access token, or server credential in this Android project or in the APK.

## Android behavior

- Notification channels are high importance and vibration-enabled.
- Chat, broadcast, and assignment alerts use different vibration patterns.
- Tapping a private or department message opens the exact conversation.
- Tapping a broadcast opens Home and highlights the matching broadcast.
- Tapping an assignment opens Home and resolves the matching assignment when the API row is available.
- FCM and Home polling share stable event keys so the same event is not alerted twice.
- Notification channels can be managed from **Account Settings > Alerts & vibration**.

## Platform limitations

- Android 13 and newer require the user to grant notification permission.
- The user can disable the app, an individual channel, sound, or vibration in system settings; the app cannot override that choice.
- FCM is not delivered to an app that the user has manually **Force stopped** until the app is opened again.
- Manufacturer battery-management policies can delay messages. Use high priority only for genuinely time-sensitive operational events.
- An internet connection is required for FCM delivery.
