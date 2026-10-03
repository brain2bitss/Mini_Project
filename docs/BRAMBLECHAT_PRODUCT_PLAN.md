<USER_REQUEST>
# BrambleChat — Full Product Plan: Beautiful UI + Extraordinary Backend

You've earned this. The hardest infrastructure is done. Now let's design the product properly: what the app should look like, and how the backend should actually work.

---

## Part 1 — The UI: Beautiful, Not Generic

Most "secure messenger" apps look like they were designed by cryptographers. Yours shouldn't. Privacy-first apps can look as good as anything from a top design studio.

### Visual Identity

**Name:** BrambleChat
**Tagline:** "Your conversations, off the grid."
**Feeling:** Calm, private, handmade. Think "Field Notes notebook meets Snowden's iPhone."

**Palette (dark by default, light optional):**

| Token | Dark | Light |
|---|---|---|
| `background` | `#0B0D10` — near-black | `#FAFAF7` — warm white |
| `surface` | `#131720` — deep slate | `#FFFFFF` |
| `surfaceVariant` | `#1C2230` | `#F0EEE8` |
| `primary` | `#7DFFCB` — electric mint | `#0A6E4B` — forest |
| `accent` | `#FFB27A` — warm amber | `#C24E1E` |
| `danger` | `#FF5C7A` — hot rose | `#B00020` |
| `text` | `#E8EAF0` | `#1A1A1A` |
| `textMuted` | `#8892A6` | `#666666` |

**Typography:**
- **Display:** "Fraunces" (serif, expressive) for headers
- **Body:** "Inter" for everything else
- **Mono:** "JetBrains Mono" for `.onion` addresses and crypto fingerprints

**Motion:**
- Screen transitions: 180ms slide + fade
- Success moments (connection established, message sent): subtle ripple + soft haptic
- Never spinners. Use skeleton states or shimmer.

**Iconography:**
- Hand-drawn line icons, 2px stroke, rounded caps
- Feature a small "leaf" motif as the app's logo — Bramble is a thorny vine

### Screen-by-Screen

---

#### 1. Onboarding

A single vertical scroll. Four cards, each telling the story.

**Card 1 — Welcome**
> **"Hi. We're Bramble."**
> A messenger that works when the internet doesn't. No phone number. No email. No servers. Just you and the people you talk to.

Small illustration: two phones connected by a vine.

**Card 2 — Pick a nickname**
Simple text field, monospace font. Autofocus. Placeholder: `e.g. finch, moss, charlie`
No email, no phone, no "verify".

**Card 3 — Master passphrase**
Two password fields. A visible strength meter (a growing vine, not a bar).
Warning below: *"If you forget this, your messages are lost. Write it down."*

**Card 4 — Ready**
Big button: **"Enter Bramble"**
Animates a vine growing down the screen, ending at the home screen.

No "Terms of Service". No "Accept cookies". No data collection permissions prompt. Because there's nothing to accept.

---

#### 2. Home — Contacts

**Layout:**
- Top: nickname + small `.onion` address chip (tap to copy, long-press to share)
- Middle: contact list, cards with:
  - Left: circular avatar (initials, colored by hash of pubkey)
  - Center: name + last message preview (encrypted preview, decrypted locally)
  - Right: time + unread count badge
- Bottom: floating action button — **large, mint green, "Add" with a QR glyph**

**Empty state:**
A gentle illustration of an unopened door. Text: *"No contacts yet. Add someone nearby or share your link."*
Two buttons: **"Scan QR"** and **"Show my link"**

**Connection status indicator (per contact):**
- Green dot: online (Tor reachable, handshake completed recently)
- Amber dot: reachable via mesh only (Bluetooth range)
- Grey dot: offline (nothing you can do right now)
- Blue dot: relaying (a mutual contact is carrying messages for you)

---

#### 3. Add Contact

Three tabs:
1. **In person** — full-screen camera, QR scanner. Overlay: crosshair in mint green. Text: *"Line up the QR code"*
2. **Share my link** — big QR code on white background, plus `briar://` URL below. Buttons: "Copy link", "Share via..."
3. **Paste a link** — text field where user can paste a `briar://` URL received via another channel

When scan succeeds: the screen flashes mint, phone vibrates, and a handshake animation plays. Then: *"Contact added."*

---

#### 4. Chat Screen

**Header:**
- Back chevron
- Avatar + name (tappable → contact detail)
- Status dot (green/amber/grey/blue as above)
- Menu (⋮): "Verify identity", "Set nickname", "Remove contact", "View raw transport log"

**Message list:**
- Outgoing: right-aligned, `primary` background, `background` text, rounded 18px except bottom-right (4px)
- Incoming: left-aligned, `surface` background, `text` color
- Timestamps in `textMuted`, small, on every 5th message or when gap > 10 min
- Group messages by day with a subtle divider
- When a message is queued for delivery, a small clock icon next to it
- When delivered, the clock becomes a checkmark
- When read, it becomes a double checkmark in `primary`

**Ephemeral messages (optional, per chat):**
- Toggle in contact settings: "Disappearing messages"
- Options: 5 min, 1 hour, 24 hours, 7 days
- Messages with a fire icon in the corner

**Input bar:**
- Rounded, dark surface
- Left: attachment glyph (📎)
- Center: text field, expands for multi-line
- Right: mic glyph (audio), then send button
- **Send button is mint green when the peer is online, amber when only mesh, grey when offline (but still sends, queued for relay)**

---

#### 5. Contact Detail

Top: large avatar, nickname (editable), `.onion` address in mono
Below:
- **Identity fingerprint** — hex string split into 4-char groups, monospace, with a "Verify in person" button that opens a side-by-side comparison view
- **Connection** — current transport (Tor / Bluetooth / Wi-Fi Direct / Relay / Mailbox), last successful connection timestamp
- **Statistics** — messages sent, received, bytes transferred, time spent online
- **Settings** — disappearing messages, notifications, mute
- **Danger zone** — remove contact (with confirmation), block

---

#### 6. Group Forum

Threads instead of a single stream.
- Top: forum title, member count
- Middle: list of threads, each with subject, last poster, unread count
- Each thread is a chat-like view with forum-specific styling (subtle background pattern)

Forums sync incrementally — when two members connect, they exchange missing posts. Progress bar shows sync status when the app is actively syncing.

---

#### 7. Blog

A contact's blog appears in a separate screen reachable from their profile.
- Header: title, author, published date
- Body: rendered markdown
- Footer: "Signed by `<fingerprint>`" with a verify button

To publish your own blog:
- Composer screen with markdown editing
- Live preview
- "Publish" broadcasts to all contacts, who replicate to their contacts

---

#### 8. Settings

- **Identity** — nickname, `.onion` address, QR code, backup key
- **Security** — master passphrase change, screen lock timeout, "quick lock" toggle, panic wipe
- **Network** — Tor on/off, mesh on/off, Wi-Fi Direct on/off, Bluetooth on/off, relay hop limit
- **Appearance** — theme, font size, animations
- **Advanced** — log level, dump database, export diagnostics
- **About** — version, licenses, source code link

---

#### 9. Lock Screen

When the app is reopened after the timeout:
- Full-screen lock with a blurred snapshot of the last screen
- A single passphrase field, large, monospace
- Five failed attempts → 30-second cooldown
- Ten failed attempts → optional full wipe (configurable)

Biometric unlock: fingerprint/face, with the passphrase as fallback.

---

### Design System Implementation

- **Compose Material 3** with custom `BrambleTheme`
- **`BrambleColors`** object — one source of truth for every color
- **`BrambleTypography`** — FontFamily + TextStyles
- **Components** folder with `BrambleButton`, `BrambleCard`, `BrambleAvatar`, `BrambleText`, `BrambleIcon`, `BrambleDivider`, `BrambleChip`
- **Motion** in a `BrambleMotion` object — durations, easings, spring specs
- **Screens** organized as `feature/<name>/<Name>Screen.kt` + `<Name>ViewModel.kt`

---

## Part 2 — The Backend: Extraordinary, Not Just Working

The current backend is a skeleton. Here's what it needs to become.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  Compose Screens ← ViewModels ← Repository                  │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer                              │
│  Use Cases: SendMessage, AddContact, SyncForum, PublishBlog │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  ContactRepo, MessageRepo, ForumRepo, BlogRepo, TorRepo     │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                    Transport Layer                           │
│  Tor  ┃  Bluetooth  ┃  Wi-Fi Direct  ┃  Relay  ┃  Mailbox   │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│              BTP — Bramble Transport Protocol                │
│  Session ┃ Framing ┃ Chunking ┃ Ratchet ┃ Acks ┃ Rekeying   │
└─────────────────────────────────────────────────────────────┘
```

### The BTP Stack (what we have + what we need)

**Already built:**
- `CryptoManager` — RFC-verified primitives
- `BtpHandshake` — X25519 + HKDF + directional keys + transcript

**To build:**

#### 1. Session Lifecycle Manager

```kotlin
class BtpSessionManager(
    private val db: SessionDao,
    private val transport: BtpTransport
) {
    suspend fun openSession(peer: Contact): BtpSession
    suspend fun resumeSession(peer: Contact, cachedKey: ByteArray): BtpSession
    suspend fun closeSession(session: BtpSession)
    fun activeSession(peerId: String): BtpSession?
}
```

Sessions are cached in the DB keyed by peer pubkey + session ID. Reopening uses the cached key to avoid a full handshake.

#### 2. Framing + Chunking

We outlined this earlier (frame type, sequence number, nonce, payload). Now we extend it:

- **Frame types:** `DATA`, `ACK`, `PING`, `PONG`, `REKEY`, `CLOSE`, `ERROR`
- **Max payload:** tunable per transport. Bluetooth: 512 bytes. Wi-Fi Direct: 4 KB. Tor: 16 KB.
- **Chunking:** A single message splits into N frames, each with `msgId` and `chunkIndex`. Reassembly is buffered by msgId.
- **Acks:** After N frames in flight, require an ACK before sending more. Prevents buffer overrun on slow transports.

#### 3. Double Ratchet (per-message forward secrecy)

After the handshake, each message advances a symmetric ratchet:

```
Root Key ──► Chain Key ──► Message Key (per message)
                │
                └─► next Chain Key
```

Every N messages or every M minutes, a DH ratchet step runs:
- Each side generates a fresh X25519 keypair
- Exchanges the pubkey in a REKEY frame
- Mixes the new DH output into the Root Key

This gives you:
- **Forward secrecy:** compromise of the current key doesn't reveal past messages
- **Post-compromise security:** after a compromise, future messages heal

#### 4. Message Queue + Delivery Guarantees

Every outgoing message is written to `outgoing_queue` in SQLite before being sent. States:

- `DRAFT` — just created
- `QUEUED` — waiting for transport
- `SENT` — handed to transport
- `ACKED` — recipient confirmed receipt
- `DELIVERED` — recipient confirmed display
- `FAILED` — retries exhausted

Retries: exponential backoff, max 7 days, then move to `FAILED`.

#### 5. Relay Protocol

For store-and-forward over mutual contacts:
- Message encrypted with final recipient's key (not the relay's)
- Relay header (unencrypted): `destOnion`, `hopCount`, `ttl`
- Relay cannot read the message — only forward it
- Relay stores pending messages in its own `relay_queue`
- On next peer connect, relay drains the queue for that peer

#### 6. Mailbox Protocol

A dedicated Android device acts as an always-on mailbox:
- Registers with the sender's contacts
- Accepts messages addressed to the primary device
- Stores them encrypted
- Primary device retrieves when it next connects

Mailbox only sees: destination onion, timestamp, ciphertext. No metadata.

#### 7. Mesh Discovery

Bluetooth Low Energy advertising + Wi-Fi Direct service discovery:
- Advertise a rotating service UUID derived from the contact list
- Peers within range respond with a handshake request
- Session opens over RFCOMM or Wi-Fi Direct socket

#### 8. Transport Multiplexer

A single interface that all transports implement:

```kotlin
interface BtpTransport {
    val type: TransportType
    fun canReach(peer: Contact): Boolean
    suspend fun connect(peer: Contact): BtpConnection
    suspend fun accept(): BtpConnection
}

enum class TransportType { TOR, BLUETOOTH, WIFI_DIRECT, RELAY, MAILBOX }
```

A `TransportRouter` picks the best available transport per peer, falls back to the next, and hands the socket to `BtpSessionManager`.

### The Data Layer

#### SQLite Schema (SQLCipher-encrypted)

```sql
-- Identity
CREATE TABLE identity (
    id INTEGER PRIMARY KEY,
    nickname TEXT NOT NULL,
    pubkey BLOB NOT NULL,          -- Ed25519 public
    privkey_encrypted BLOB NOT NULL, -- encrypted with master passphrase
    onion_address TEXT,
    created_at INTEGER NOT NULL
);

-- Contacts
CREATE TABLE contacts (
    id INTEGER PRIMARY KEY,
    alias TEXT NOT NULL,
    pubkey BLOB NOT NULL UNIQUE,   -- Ed25519 public
    onion_address TEXT,
    relay_enabled INTEGER DEFAULT 0,
    mailbox_address TEXT,
    last_seen_at INTEGER,
    created_at INTEGER NOT NULL
);

-- Sessions
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY,
    contact_id INTEGER NOT NULL,
    session_id BLOB NOT NULL,
    send_chain_key BLOB NOT NULL,
    receive_chain_key BLOB NOT NULL,
    root_key BLOB NOT NULL,
    send_count INTEGER DEFAULT 0,
    receive_count INTEGER DEFAULT 0,
    opened_at INTEGER NOT NULL,
    FOREIGN KEY (contact_id) REFERENCES contacts(id)
);

-- Messages
CREATE TABLE messages (
    id INTEGER PRIMARY KEY,
    contact_id INTEGER,
    forum_thread_id INTEGER,
    direction INTEGER NOT NULL,    -- 0=in, 1=out
    state INTEGER NOT NULL,        -- DRAFT..FAILED
    body_encrypted BLOB NOT NULL,  -- encrypted at rest with DB key
    timestamp INTEGER NOT NULL,
    delivered_at INTEGER,
    read_at INTEGER,
    expiry_at INTEGER,             -- for ephemeral messages
    FOREIGN KEY (contact_id) REFERENCES contacts(id)
);

CREATE INDEX idx_messages_contact_ts ON messages(contact_id, timestamp DESC);
CREATE INDEX idx_messages_expiry ON messages(expiry_at) WHERE expiry_at IS NOT NULL;

-- Forums (threaded)
CREATE TABLE forums (id INTEGER PRIMARY KEY, title TEXT, creator_pubkey BLOB);
CREATE TABLE forum_threads (id INTEGER PRIMARY KEY, forum_id INTEGER, subject TEXT);
CREATE TABLE forum_posts (id INTEGER PRIMARY KEY, thread_id INTEGER, author_pubkey BLOB, body_encrypted BLOB, timestamp INTEGER);

-- Blogs
CREATE TABLE blogs (id INTEGER PRIMARY KEY, author_pubkey BLOB, title TEXT, subscribed INTEGER DEFAULT 0);
CREATE TABLE blog_posts (id INTEGER PRIMARY KEY, blog_id INTEGER, title TEXT, body_md TEXT, timestamp INTEGER, signature BLOB);

-- Queues
CREATE TABLE outgoing_queue (id INTEGER PRIMARY KEY, contact_id INTEGER, payload BLOB, state INTEGER, attempts INTEGER, next_attempt_at INTEGER, created_at INTEGER);
CREATE TABLE relay_queue (id INTEGER PRIMARY KEY, dest_onion TEXT, hop_count INTEGER, ttl INTEGER, payload BLOB, created_at INTEGER);
CREATE TABLE mailbox_queue (id INTEGER PRIMARY KEY, dest_onion TEXT, payload BLOB, created_at INTEGER);

-- Transport state
CREATE TABLE transports (id INTEGER PRIMARY KEY, contact_id INTEGER, type TEXT, last_connected_at INTEGER, last_error TEXT);

-- Diagnostics (optional, off by default)
CREATE TABLE transport_log (id INTEGER PRIMARY KEY, ts INTEGER, transport TEXT, event TEXT, detail TEXT);
```

#### Repositories

Each repository:
- Returns Flow<T> for reactive UI updates
- Writes go through DAOs which run on `Dispatchers.IO`
- Encryption at rest is handled at the DAO layer (SQLCipher key is per-session)

```kotlin
class MessageRepository(
    private val db: Daos,
    private val btp: BtpSessionManager,
    private val queue: OutgoingQueue
) {
    fun messagesFor(contactId: Long): Flow<List<Message>>
    suspend fun send(contactId: Long, body: String, ephemeral: Duration? = null)
    suspend fun receive(contactId: Long, encryptedFrame: ByteArray)
    suspend fun markRead(messageId: Long)
}
```

### The Domain Layer

Use cases are small and single-purpose:

- `SendMessageUseCase` — encrypt + queue + attempt send
- `AddContactUseCase` — verify QR signature + insert + open first session
- `SyncForumUseCase` — exchange missing posts with a peer
- `PublishBlogUseCase` — sign + broadcast to contacts
- `UnlockVaultUseCase` — derive DB key from passphrase + unlock
- `RotateOnionKeyUseCase` — generate new onion, migrate contacts

### The Transport Layer

#### TorTransport (mostly done)

Now with `BtpHandshake` wired into the listener:

```kotlin
class TorTransport(context: Context) : BtpTransport {
    override suspend fun accept(): BtpConnection {
        val socket = listener.accept()
        val session = BtpHandshake().performHandshake(
            socket.inputStream, socket.outputStream, isAlice = false
        )
        return BtpConnection(socket, session)
    }

    override suspend fun connect(peer: Contact): BtpConnection {
        val socket = torManager.connectToPeer(peer.onionAddress!!, 80)
        val session = BtpHandshake().performHandshake(
            socket.inputStream, socket.outputStream, isAlice = true
        )
        return BtpConnection(socket, session)
    }
}
```

#### BluetoothTransport

Uses BLE advertising + RFCOMM for actual data. Advertises a rotating service UUID per contact so random devices don't discover you.

#### Wi-FiDirectTransport

Peer discovery via Wi-Fi Direct service discovery, then a standard TCP socket over the local Wi-Fi Direct link.

#### RelayTransport

Wraps another transport (typically Tor). When direct delivery fails, wraps the payload in a relay envelope and hands it to a mutual contact.

#### MailboxTransport

Configured per-contact. Sends to the contact's mailbox onion address instead of their primary. The mailbox relays when the primary comes online.

### Reliability Layer

#### Connection Manager

A single coroutine scope manages all peer connections:

- For each contact, tries each transport in priority order
- Maintains a long-lived connection when possible
- Retries on disconnection with backoff
- Notifies the UI of state changes

#### Background Service

A foreground service (with a persistent notification) runs the connection manager while the app is backgrounded:

```kotlin
class BrambleService : LifecycleService() {
    override fun onStartCommand(...): Int {
        connectionManager.start()
        return START_STICKY
    }
}
```

Foreground type: `dataSync`. Notification: minimal, permission-friendly.

#### Battery Optimization

- Tor connection polls every 30s when active, 5min when idle
- Bluetooth scanning paused when screen off
- Wi-Fi Direct only active during sync windows
- All tunable per-user in Settings

### Security Layer

- **Master passphrase** — Argon2id, 256 MB memory, 3 iterations, 4 lanes
- **DB key** — derived from master passphrase + hardware-backed random salt
- **In-memory key material** — zeroized after use, held in `CharBuffer`s not `String`s
- **Screen capture blocking** — `FLAG_SECURE` on all screens showing messages
- **Root detection** — warn but don't block (Briar does the same)
- **Safety numbers** — after contact exchange, both sides derive a 60-digit fingerprint from pubkeys; users can compare out of band
- **Panic wipe** — triggered by 10 wrong passphrases or a custom PIN; wipes DB, keys, Tor state

### Testing Strategy

- **KATs** — every crypto primitive, still passing
- **BTP handshake** — tests with two threads + pipes
- **BTP framing** — round-trip, replay protection, chunking
- **Session lifecycle** — open, use, close, resume
- **Tor reachability** — the current device test, now automated into a nightly "self-test"
- **Relay** — mocked transport that can drop, reorder, delay
- **Mailbox** — two-device test, one acts as mailbox
- **Mesh** — emulator pair with Bluetooth forwarding

### Observability

A diagnostics screen (hidden in Advanced settings) shows:

- Live log stream filtered by severity
- Per-transport connection state
- Current session keys' fingerprints (not the keys)
- Queue depths
- Tor circuit state
- Recent errors with stack traces

Plus a "Export diagnostics" button that writes a redacted zip: logs, config, no message content, no keys.

---

## Part 3 — The Build Order

You have Tor working. Here's the actual sequence from here:

| Milestone | What It Delivers | Estimated Turns |
|---|---|---|
| **1. Wire BtpHandshake into the Tor listener** | Encrypted BTP session over Tor | 2 |
| **2. BTP framing + chunking** | Send arbitrary-length messages | 2 |
| **3. Double ratchet** | Forward secrecy, post-compromise security | 3 |
| **4. Message persistence + queue** | Reliable delivery with retries | 2 |
| **5. Session lifecycle manager** | Reuse sessions, resume after kill | 2 |
| **6. Real UI — home + contacts + chat** | Design system + 4 screens | 4–6 |
| **7. Contact exchange — QR + link** | Add contacts in-person and remotely | 3 |
| **8. Group forums** | Threaded sync over Tor | 3 |
| **9. Blogs** | Publish + subscribe | 2 |
| **10. Mesh transport (Bluetooth)** | Offline mode | 3 |
| **11. Relay protocol** | Store-and-forward | 3 |
| **12. Mailbox** | Always-on secondary device | 2 |
| **13. Polish + release** | App icon, splash, Play Store prep | 3 |

**~35 turns** to a genuinely usable app. That's real, but achievable.

---

## Part 4 — What Makes This "Extraordinary"

Not the features. These:

1. **Verified crypto, not aspirational.** Every primitive has a KAT. Every handshake has a test. You can point at a green test suite and say "this is provably correct."

2. **Real Tor, on a real phone, with a real `.onion`.** Most projects stop at "integrate Tor." Yours publishes a hidden service that responds to requests over the network.

3. **Design as good as the security.** Dark mode with hand-picked palette. Custom typography. Motion that feels alive. No "accept cookies" banners because there are no cookies to accept.

4. **No servers, no accounts, no phone numbers.** Everything either runs on-device or over Tor. There is no Bramble server to subpoena, because there is no Bramble server.

5. **Offline-first, not offline-optional.** Store-and-forward, mesh, mailbox — the app works when the internet doesn't. That's the actual pitch.

6. **Provably buildable.** Docker-based reproducible builds. Anyone can verify the released binary matches the source.

That's the product. It's real, it's buildable, and everything hard has already been proven to work.

---

## Send This to the Agent

When you're ready to start building the UI and continuing the backend, here's the next prompt:

---

```
Milestone: wire BtpHandshake into the Tor listener, so the Onion Service runs an encrypted handshake instead of an HTTP responder.

## Step 1: Replace the HTTP responder with a BTP handshake responder

In TorManager.kt, keep `listenForPeers` and `startSimpleHttpListener` as-is (they're useful for diagnostics).

Add a new method `startBtpListener()` that:

1. Opens a ServerSocket on port 7654 (same port)
2. For each accepted connection, spawns a thread that:
   a. Runs `BtpHandshake().performHandshake(socket.input, socket.output, isAlice = false)` — the phone is the responder
   b. Receives a `BtpSession` with sendKey, receiveKey, transcriptHash
   c. Logs the transcript hash in hex (first 8 bytes)
   d. Reads one framed message from the socket, decrypts it with `receiveKey`
   e. Writes back an echo encrypted with `sendKey`
   f. Closes the socket

For now, do not integrate BtpSessionCipher yet — just read/write raw bytes and log them. The point is to prove the handshake works over the Tor network.

## Step 2: Update DebugTorActivity

Add a third button "Start BTP Listener" that calls `torManager.startBtpListener()`. Wire it up in the layout between "Start Tor" and "Stop Tor".

Also, when Start Tor is tapped, do NOT call `startSimpleHttpListener`. Only start the BTP listener.

## Step 3: Build

    $env:JAVA_HOME="C:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\jdk-17.0.2"
    ./gradlew.bat :ui:assembleDebug --no-daemon --rerun-tasks > stage5_btp.log 2>&1
    Get-Content stage5_btp.log -Tail 60

Paste raw output.

## Step 4: Do NOT install

We install in the next turn after reviewing the build.

## Hard rules
- Do NOT modify core-crypto or BtpHandshake.
- Do NOT remove the simple HTTP listener method (keep it for diagnostics).
- Paste the FULL contents of TorManager.kt and DebugTorActivity.kt after modification.
- Do NOT run gradle until both file changes are pasted.
- Forbidden words unless raw output proving them is in the same message: "successfully", "works", "green", "passing", "verified", "correct".
- If BtpHandshake's API doesn't match how you're calling it (wrong parameter order, wrong types), say so and STOP.
- Create a plan

Your Role is Developer and Tester, be honest and be real don't makeup the things, end up with full shipping ready android chat app

</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-10-02T22:47:47+05:30.

The user's current state is as follows:
Active Document: c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\docs\btp-spec.md (LANGUAGE_MARKDOWN)
Cursor is on line: 1
Other open documents:
- c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\transport-relay\build.gradle.kts (LANGUAGE_UNSPECIFIED)
- c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\mailbox\build.gradle.kts (LANGUAGE_UNSPECIFIED)
- c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\tor_compile.log (LANGUAGE_UNSPECIFIED)
- c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\core-crypto\src\main\kotlin\com\miniproject\core\crypto\CryptoManager.kt (LANGUAGE_KOTLIN)
- c:\Users\Adhi\Desktop\Mini_Projecttt\Mini_Project\build.gradle.kts (LANGUAGE_UNSPECIFIED)
</ADDITIONAL_METADATA>