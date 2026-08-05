# Firebase Realtime Database rules and voice messages

The currently supplied rules are functionally permissive enough for private voice-message metadata:

- the app writes private message data under `messages/<threadId>/<messageId>`;
- the app updates thread previews under `threads/<threadId>`;
- the audio binary is uploaded to the PHP server and is not stored in Firebase;
- department/inter-agency message persistence is handled by PHP/MySQL.

Therefore, no `audio` Firebase node or additional rule is required to fix playback.

The supplied rules use `.read: true` and `.write: true`. This means anyone who obtains the Firebase Database URL can read, alter, or delete operational chat data. They should not be considered production-secure.

The Android app currently authenticates through the PHP backend and does not sign in to Firebase Authentication. Replacing the rules immediately with `auth != null` would block the current private-chat implementation. The correct hardening sequence is:

1. PHP verifies the responder login/session;
2. PHP issues a Firebase custom token containing the responder user ID and claims;
3. Android signs in with `signInWithCustomToken`;
4. data is migrated to user/thread membership paths that can be checked by rules;
5. only then are public `.read/.write` rules removed.

Until that migration is implemented, keep the current rules only as a temporary compatibility measure and restrict the Firebase project/database through operational controls as much as possible.
