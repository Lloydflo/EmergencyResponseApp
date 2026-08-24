# Full responder-workflow update

This project contains the completed Android-side implementation requested for responder alerts, backup requests, Coordination, and Reviews.

## Included

- Background/cold-process Firebase Cloud Messaging handling for private chat, department chat, emergency broadcasts, and newly assigned incidents.
- Dedicated high-importance Android notification channels, event-specific vibration patterns, notification permission handling, duplicate suppression, and destination routing.
- Operational backup selection on Home, with exact response team/unit/capability choices and a quantity or dispatch-details field.
- Equipment-and-supply-only requests under Reviews; responder personnel, response teams, and emergency vehicles are directed to Home > Backup Requests.
- Coordination Portal with Chats and Departments as the two persistent tabs; the pencil button opens the searchable responder directory in a bottom sheet.
- Local per-field quick text templates for After-Action Reports, service reviews, and equipment/supply request notes. Templates can be applied, added, edited, deleted, and restored.

The Login/OTP source was not modified as part of this update.

## Required server action for true outside-app alerts

The Android client cannot discover a new server event after its process has been removed unless the server sends Firebase Cloud Messaging. Deploy the high-priority data payloads in `FCM_NOTIFICATION_PAYLOADS.md` from the PHP/dispatch backend after each chat, broadcast, or assignment record is committed.

No Firebase service-account credential belongs in this Android project or APK.

## Device validation checklist

1. Sign in, grant Notifications permission, then open Account Settings > Alerts & vibration and verify all four channels are enabled.
2. Put the app in the background and send one private message, department message, broadcast, and incident assignment from another account/dispatch console.
3. Swipe the app away and repeat. Verify each notification vibrates and opens the intended destination.
4. Confirm no alert is duplicated when FCM and active-app polling receive the same event.
5. Create an operational backup request from Home and verify its exact unit/capability and details appear in request history.
6. Create an equipment/supply request from Reviews and confirm no responder or emergency-vehicle categories appear.
7. Open Coordination, tap the pencil button, search for a responder, and open a private chat.
8. In every narrative Reviews field, apply a default template, add a custom template, edit it, and restore defaults.

Android does not deliver FCM to a manually Force-stopped app until the user opens it again. Users and device manufacturers may also disable or delay notifications and vibration through system settings or battery-management policies.

## Image-based tutorial guide update

The previous Compose-drawn tutorial previews have been replaced by an image-driven guide. The owner can now design the tutorial artwork independently and add it under `app/src/main/res/drawable-nodpi/`.

Supported naming:

- One long image: `how_to_tutorial.webp`
- Multiple pages: `how_to_01.webp`, `how_to_02.webp`, and so on

The screen discovers the images automatically. Users can tap an image to open it full screen, pinch to zoom, and drag to inspect labels. See `HOW_TO_TUTORIAL_IMAGES.md` for preparation and naming rules.

## Presence policy update

- Pressing Home or Back no longer marks the responder offline.
- A signed-in responder remains online/assignable for up to 60 minutes in the background.
- Returning to the app cancels the pending timeout and renews presence immediately.
- Foreground presence is renewed every five minutes.
- Active route monitoring remains operationally busy/en route; it is never converted into a false available state by app backgrounding.
- Swiping the task from Recents requests a best-effort immediate offline transition. WorkManager and the server cron remain fallbacks when Android suppresses that callback.
- Background timeout does not log out the user and does not unregister the FCM token. Private chat, department chat, and broadcast notifications can continue to arrive. Explicit Logout is the only normal flow that unregisters the token.
- See `PRESENCE_BACKGROUND_NOTIFICATIONS.md` for the required API files, cron setup, dispatcher eligibility rule, and assigned-incident FCM hook.
