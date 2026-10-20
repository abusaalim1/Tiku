# Tiku

**Tiku opens as a Class 11 study app** — a focus timer, CBSE chapter notes,
daily questions and revision cards — and, behind a password-gated "Tik" study
tool, it is a private end-to-end encrypted 1:1 messenger ("Tikuu" in-app).

Kotlin + Jetpack Compose. The hidden messenger UI is ported 1:1 from the
owner's design prototype; the engine is Firebase (Auth + Firestore) with a
client-side E2EE engine. The server only ever stores ciphertext.

- **Package / applicationId:** `tiku.app` · namespace `com.tiku.app`
- **Firebase project:** `tiku-chat` (free Spark plan — Firebase Storage is NOT used)
- **Signing:** stable release cert `tiku.keystore` (repo root, alias `tiku`),
  SHA-1 `27:31:80:75:8E:7F:6F:9E:D6:D5:06:A3:B7:0C:B4:35:84:14:55:95`,
  password supplied to CI via the `TIKU_KEYSTORE_PASSWORD` Actions secret.
- **Keystore backup:** kept by the owner outside this repo.

## The study facade (default surface)

What anyone sees when they open the app. Fully offline, fully local:

- Greeting header, focus-timer card (today's minutes ring + day streak)
- **Study tools row:** Notes / Timer / Formulas / **Tik**
- Subjects grid — Mathematics, Physics, Chemistry, Biology (CBSE Class 11)
- Question of the Day (30 rotating MCQs with explanations)
- Sub-pages: full focus timer (25/45/60 presets, sessions stored locally),
  local notes (create/edit/delete per subject), Formulas & key points,
  revision cards (24 flip cards), subject → chapter pages with key points
  and practice questions with tap-to-reveal answers

Study content is bundled in `app/src/main/assets/study-content.json`
(56 chapters, 30 daily questions, 24 revision cards; an optional `src` tag
per question is rendered when present). Facade data never touches Firestore.

## The hidden messenger (ChatRoot)

Behind the **Tik** tool sits a password gate ("Your journal is locked").
The password is the app's own onboarding password — PBKDF2-verified locally —
never the device lock or biometrics, and it is asked on **every** entry.

Inside: Google sign-in onboarding (study-framed copy), Chats / Calls / Me
home, full chat (text, photos, voice notes, reactions, replies, star,
typing indicators, read receipts, presence), call screens (full UI + real
call logs; the WebRTC engine lands next round), Moments viewer, and the
complete settings set (accent / wallpaper / text size / privacy toggles —
light theme is locked; the design's dark palette is intentionally not
shipped).

## Privacy & lock rules (by design)

- **Sessions exist only while unlocked.** Exiting the Tik section in any way
  — Back at chat home, the discreet quick-exit control (long-press the chat
  home title), or the app going to background — instantly returns to the
  study facade, zeroes conversation keys and drops all decrypted
  messages/media from RAM. The password is required again on the next entry.
  No grace period.
- **No chat notifications by design.** No message notification of any kind:
  no sound, no heads-up, no shade entry, no unread badge anywhere on the
  facade. New messages are discoverable only after unlocking. (The app does
  not even request the notification permission.)
- **Recents protection:** `FLAG_SECURE` is set on the window while any gated
  screen is visible, so screenshots and the Android recents preview can
  never show gated content; it is cleared on the study facade.
- Facade vocabulary is study-only — audited to contain no messaging hints.

## Crypto design

- **Identity:** EC P-256 keypair per user, generated on-device.
- **Device storage:** the private key is wrapped with an Android Keystore
  AES-256-GCM master key (`tiku_master`) and stored app-private.
- **Backup (chats must never disappear):** the private key is ALSO wrapped
  with AES-256-GCM under a key derived from the user's password via
  **PBKDF2WithHmacSHA256, 310,000 iterations**, random 16-byte salt, stored
  at `users/{uid}.keyBackup {salt, iv, ct}`. On a new phone: Google sign-in
  → enter the journal password → key unwraps → full history decrypts again.
  A wrong password simply fails to unwrap: **without the password the backup
  is unrecoverable — nobody (including Google) can read the chats.** Chats
  on the original device keep working regardless.
- **Conversation key:** ECDH(myPrivate, peerPublic) → HKDF-SHA256
  (salt = the sorted uid pair joined, info = `tiku-chat-v1`) → 32-byte AES key.
  Conversation keys are ephemeral: derived on unlock, zeroed on exit.
- **Payloads:** AES-256-GCM, fresh random 12-byte IV per message.
- **Media without Cloud Storage:** photos (downscaled ≤1280px, JPEG q80)
  and voice notes (AAC .m4a, ≤3 min) are encrypted whole-blob with the
  conversation key, then split into ≤700 KB Base64 chunks stored at
  `chats/{chatId}/messages/{msgId}/chunks/{index}`; the message doc carries
  `mediaChunks` + the whole-blob `iv`. Firestore free tier (1 GiB stored,
  50k reads/day) is generous for this. No video messages in v1.
- **Avatars:** compressed JPEG (512px, q70) stored as a Base64 field in the
  user doc.
- **Delete means delete-for-me only** (`deletedFor` arrayUnion); messages
  are never hard-deleted. "Clear chat" sets a per-user `clearedBefore`
  timestamp on the chat doc.
- **Usernames:** unique, create-only claim via a Firestore transaction on
  `usernames/{username}` → uid.

## Firestore security rules

The rules file lives at the repo root as **`firestore.rules`** and is the
source of truth — **keep the repo file and the Firebase console in sync**
(edit here, paste into the console). Contract summary:

- `users/{uid}` — readable by any signed-in user, writable only by the owner.
- `usernames/{username}` — create-only; never updated or deleted by the app.
- `chats/{chatId}` — MUST carry `members: [uidA, uidB]` (exactly 2); the chat
  doc also holds the typing map and per-user `clearedBefore`. Messages,
  media chunks and per-chat call logs (`chats/{chatId}/calls/{callId}`) are
  gated on membership. No top-level `calls` collection exists.
- `moments/{uid}/items/{itemId}` — owner writes, signed-in users read.
- Everything else: default deny.

## Owner setup status

Completed (2026-10-20): Firebase project `tiku-chat` created; Android app
`tiku.app` registered; SHA-1 fingerprint added; Google sign-in enabled;
Firestore created (production mode) with the rules above published;
final `google-services.json` in place. **No remaining console steps.**
Firebase Storage is intentionally not used (it requires the Blaze plan).

## Build

CI (`Build and Publish Release APK`) builds `assembleRelease` with the
stable keystore, asserts the APK is not debuggable, verifies the signer
SHA-1 equals `27:31:80:75:8E:7F:6F:9E:D6:D5:06:A3:B7:0C:B4:35:84:14:55:95`,
and publishes a GitHub Release with the APK.

`Config.PREVIEW_UI_ONLY` is `false` — this build is the real app. A demo
preview store remains in the codebase behind the flag for UI testing only
(it is unreachable in this build; real mode starts empty: "Search a
username to start a chat").

## v1 limitations

- Calls are UI + call logs only; the WebRTC calling engine lands next round.
- No push notifications (by design — see Privacy above).
- Losing the journal password makes the key backup unrecoverable.
