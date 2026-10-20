# Tiku (Tikuu)

A private, end-to-end encrypted 1:1 Messenger for Android. Kotlin + Jetpack Compose
UI ported 1:1 from the owner's design prototype, built on Firebase
(Auth + Firestore) with a client-side E2EE engine. The server only ever stores
ciphertext.

- **Package / applicationId:** `tiku.app` · namespace `com.tiku.app`
- **Firebase project:** `tiku-chat` (free Spark plan — no Firebase Storage is used)
- **Signing:** stable release cert `tiku.keystore` (repo root, alias `tiku`),
  SHA-1 `27:31:80:75:8E:7F:6F:9E:D6:D5:06:A3:B7:0C:B4:35:84:14:55:95`,
  password supplied to CI via the `TIKU_KEYSTORE_PASSWORD` Actions secret.

## Features (v1)

- Google sign-in (Credential Manager) → create password → profile (username claim)
- E2EE text, photo and voice messages; reactions, replies (quoted), star,
  delete-for-me, clear-chat, typing indicators, read receipts, presence
- In-chat media without Cloud Storage: photos and voice notes are AES-GCM
  encrypted on-device, then stored inside Firestore as ≤700 KB chunks
- Calls screens (full UI + real call logs; the WebRTC engine lands next round)
- Moments (read-only viewer fed by `moments/{uid}/items`), settings with
  working accent/wallpaper/text-size/privacy toggles (light theme is locked —
  the design's dark palette is intentionally not shipped)

## Crypto design

- **Identity:** EC P-256 keypair per user, generated on-device.
- **Device storage:** the private key is wrapped with an Android Keystore
  AES-256-GCM master key (`tiku_master`) and stored app-private.
- **Backup (chats must never disappear):** the private key is ALSO wrapped with
  AES-256-GCM under a key derived from the user's password via
  **PBKDF2WithHmacSHA256, 310,000 iterations**, random 16-byte salt, and stored at
  `users/{uid}.keyBackup {salt, iv, ct}`. On a new phone: Google sign-in →
  enter the backup password → key unwraps → full history decrypts again.
  A wrong password simply fails to unwrap: **without the password the backup
  is unrecoverable — nobody (including Google) can read the chats.**
- **Conversation key:** ECDH(myPrivate, peerPublic) → HKDF-SHA256
  (salt = the sorted uid pair joined, info = `tiku-chat-v1`) → 32-byte AES key.
- **Payloads:** AES-256-GCM, fresh random 12-byte IV per message. Message docs
  in Firestore hold only `{senderId, kind, ct, iv, …}` where `ct` is the
  ciphertext (for media, `iv` covers the whole blob and the ciphertext itself
  lives in `messages/{id}/chunks/{i}` docs as Base64 chunks).
- **App lock:** the same user password gates the app via a local PBKDF2 hash
  (verification only; the password itself is never stored).
- **Deletes are per-user, never destructive:** "Delete" appends your uid to a
  message's `deletedFor`; "Clear chat" sets your `clearedBefore` timestamp in
  the chat doc. Ciphertext remains in Firestore permanently.

## Firestore data model

```
users/{uid}                  {username, displayName, photoData, publicKey, keyBackup, online, lastSeen}
usernames/{username}         {uid}                      (create-only claim)
chats/{chatId}               {members: [uidA, uidB], typing, clearedBefore, updatedAt}
chats/{chatId}/messages/{id} {senderId, kind, ct, iv, mediaChunks, …}
chats/{chatId}/messages/{id}/chunks/{i}   {d: base64 chunk}
chats/{chatId}/calls/{id}    {by, peerUid, kind, direction, at}
moments/{uid}/items/{id}     {quote, ts}
```

## Firestore security rules

The rules below are the exact text of `firestore.rules` in this repo. **Keep the
repo file and the Firebase Console copy in sync** — when the rules change, edit
`firestore.rules` here and paste the same text into Firebase Console →
Firestore Database → Rules → Publish.

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() {
      return request.auth != null;
    }
    function isUser(uid) {
      return signedIn() && request.auth.uid == uid;
    }
    // Chat membership gates everything inside a chat.
    function isMember(chatId) {
      return signedIn() && request.auth.uid in
        get(/databases/$(database)/documents/chats/$(chatId)).data.members;
    }

    // ---- Users: profile/publicKey/keyBackup/presence. Readable by any
    // signed-in user; writable ONLY by the doc's owner. ----
    match /users/{uid} {
      allow read: if signedIn();
      allow write: if isUser(uid);
    }

    // ---- Username claims: create-only, exactly once per name. ----
    match /usernames/{username} {
      allow get: if signedIn();
      allow list: if false;
      allow create: if signedIn();
      allow update, delete: if false;
    }

    // ---- Chats: gated on members membership (both uids, exactly 2).
    // The chat doc carries members, typing map and clearedBefore per-user map. ----
    match /chats/{chatId} {
      allow get: if isMember(chatId);
      allow list: if signedIn();
      allow create: if signedIn()
        && request.auth.uid in request.resource.data.members
        && request.resource.data.members.size() == 2;
      allow update: if isMember(chatId);
      allow delete: if false;

      // ---- messages + their media chunks + per-chat call logs:
      // members only. ----
      match /messages/{msgId} {
        allow read, write: if isMember(chatId);

        match /chunks/{index} {
          allow read, write: if isMember(chatId);
        }
      }
      match /calls/{callId} {
        allow read, write: if isMember(chatId);
      }
    }

    // ---- Moments: owner writes, signed-in users read. ----
    match /moments/{uid} {
      allow read: if signedIn();
      allow write: if isUser(uid);

      match /items/{itemId} {
        allow read: if signedIn();
        allow write: if isUser(uid);
      }
    }

    // ---- Default deny: nothing outside this contract. ----
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Note: the `whereArrayContains("members", uid)` chat-list query is allowed by
`allow list: if signedIn();` on chats (results are additionally re-filtered
client-side); creating a chat requires exactly 2 members including yourself.

## v1 limitations

- **Calls engine is next round.** Voice/video call buttons open the designed
  call screen, log the attempt, and show "Calls are coming in the next round 📞".
- **Notifications only fire while the app process is alive** (a Firestore
  listener posts them locally). True push needs an FCM sender server — later.
- **Lost backup password = backup unrecoverable.** Chats on the original
  device still open; a new phone can never restore them without the password.
- **Media lives in Firestore** (Spark free tier: 1 GiB stored, 50k reads/day —
  plenty for a private 2-person chat). Photos are downscaled to ≤1280px /
  JPEG q80 before encryption, voice notes cap at 3 minutes, no video messages.
- Moments are read-only in v1 (no posting UI in the design).

## Firebase console checklist (owner — all done ✅)

1. ✅ Firebase project **Tiku** (`tiku-chat`) created.
2. ✅ Android app added with package **`tiku.app`**.
3. ✅ SHA-1 fingerprint added to the app:
   `27:31:80:75:8E:7F:6F:9E:D6:D5:06:A3:B7:0C:B4:35:84:14:55:95`
4. ✅ Authentication → Sign-in method → **Google enabled**.
5. ✅ **Firestore Database** created (Standard, production mode) and the rules
   above pasted + published.
6. ✅ Final `google-services.json` downloaded and placed at `app/google-services.json`
   (it contains the OAuth clients incl. the Web client used in `Config.kt`).
7. ℹ️ **Firebase Storage is intentionally NOT used** — it needs the Blaze plan;
   media goes through Firestore chunks instead (see above).

Nothing else is needed on the console for this build: download the APK from the
latest GitHub Release, install, sign in with Google, and chat.
