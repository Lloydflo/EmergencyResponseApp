# Coordination Feature Guide

Build identifier: **version 17.4 (code 27)**. Rebuild and run this source from
Android Studio; do not install the stale APK previously stored under `app/release`.

## Feature set for the project review

The Coordination Portal provides a responder-focused messaging experience without the operational risk and infrastructure cost of voice or video calling.

| Capability | Private responder chat | Department channel |
| --- | --- | --- |
| Text messages | Yes | Yes |
| Images and files | Yes | Yes |
| Voice notes and playback | Yes | Yes |
| Quoted replies | Yes | Yes |
| Copy message | Yes | Yes |
| Emoji reactions | Yes | Deferred until the group API supports reactions |
| Typing status | Yes | Deferred until the group service supports ephemeral presence |
| Sent/delivered/read indicators | Yes | Server-provided group read state |
| Draft autosave per conversation | Yes | Yes |
| Search messages | Yes | Yes |
| Shared media and files | Yes | Yes |
| Quick responder replies | Yes | Yes |
| Structured incident tips | Yes | Yes |
| Presence and last seen | Yes | Not applicable to a channel |
| Notification deep links | Yes | Yes |

## New interaction flow

Long-press a message to open one action tray. Direct chats show reactions, Reply, and Copy. Department messages show Reply and Copy only, because department history is owned by the PHP/MySQL service and does not currently return reaction data.

Selecting Reply shows a cancelable quote above the composer. The outgoing text stores a readable, versioned quote after the responder's reply. This keeps replies compatible with the current Firebase and PHP/MySQL message fields, and older clients can still understand the message.

Unsent text is stored under the current conversation only. Switching responders or opening a notification for another thread no longer transfers a draft into the wrong chat.

Private typing entries expire after 2.5 seconds of inactivity and are removed when the responder sends, changes thread, leaves Coordination, disconnects, or the Firebase socket closes.

## Why calls and video are deferred

Reliable emergency calling needs signaling, media servers or a managed RTC provider, foreground-service handling, device and network testing, permissions, encryption, participant authorization, call state recovery, and audit/privacy policies. Adding a decorative call button without that infrastructure would create a false operational promise. Voice notes provide asynchronous audio coordination while the dispatch and backend teams complete higher-priority integrations.

## Required future backend work

- Authenticate Firebase and replace public read/write rules.
- Add an authorized SQL reaction table and toggle endpoint for department messages.
- Return department reaction aggregates with group-message history.
- Add server-enforced audit fields before supporting edit or unsend; operational messages should use auditable soft deletion, never client-only hard deletion.
- Add pagination to private and department message history before production-scale use.
