# MPD Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android controller (phone + tablet) for the OpenWrt/MPD streamer at `10.0.0.219`, with a Gallery-style now-playing surface, honest signal telemetry, and a VU meter driven by a precomputed loudness envelope.

**Architecture:** Single-activity Compose app. A two-socket MPD client (command socket + `idle` socket) feeds a Room-cached library and a state flow; art and loudness data are cached on disk and memory-mapped. Layout is one `NowPlaying` graph arranged by window width, so phone, split-screen and tablet share behaviour.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3 skeleton), Room, DataStore, Coil 3, kotlinx-coroutines, MediaSessionCompat + MediaStyle. Python 3 + ffmpeg for the loudness scan tool.

**Spec:** `docs/superpowers/specs/2026-11-04-mpd-tablet-controller-design.md`

## Global Constraints

- `minSdk 26`, `targetSdk 35`, Kotlin 2.x, Gradle KTS with a version catalog.
- **No volume control anywhere.** `MpdClient` exposes no `setvol`/`volume` command; no slider, disabled or otherwise.
- Every MPD command carries a timeout: state commands **3 s**, `listallinfo` **20 s**, art requests **15 s**, `idle` **none**. No command may block the UI thread.
- Read MPD replies **by terminator** (`buffer.endsWith("OK\n")` or an `ACK` line), never by substring search.
- Album id = hash of (normalised album-artist, album, edition). Edition is `null` for ≤2 channels, `"5.1"`/`"4.0"` etc. above.
- DSD is always labelled `DSD64 · DoP` (never "native DSD"). No resampling is implied or attempted.
- Dark only: background `#0A0A0C`, warm white type. No WebView. No server config writes.
- Type: Inter variable bundled in `res/font`; pinned weights 300/400/500; seven sizes 64/44/28/20/16/13/11; labels 11 sp at weight 500 and +0.12 em; tabular figures on every numeric role.
- Structure at ≥840 dp: 420 dp cover + 584 dp column + a **152 dp band** pinned to the bottom edge. No rail. Transport is three controls, centred, and its position never changes between states.
- Signal strip cells are Source / Link / Verdict. The output rate appears in the Link cell **only when it differs** from the source (`44.1 → 48 kHz`); on the happy path the cell names the path (`USB · DX5 II`).
- The queue is text-only — no thumbnails — and is refetched wholesale on version change. Shuffle and repeat live in the band's queue header, never in the transport row.
- The level meter exists on the tablet and the medium window only; the phone sheet has none.
- Art request pattern: `binarylimit 4000000` once per connection, then `albumart "<uri>" 0`; `ACK [50@0]` means *no art*.
- Loudness envelope: 5 Hz, `u8 rms_l, u8 rms_r, u8 peak` dBFS-mapped, keyed by MPD URI.
- Unit tests run on the JVM with a local `FakeMpdServer`; UI tests run instrumented on a device (`connectedDebugAndroidTest`).

## Milestones

| Milestone | Tasks | Ends with |
|---|---|---|
| M1 — socket and state | 1–5 ✅ | A connection that survives the streamer going away; transport verified against a fake MPD |
| M2 — library and art | 6–9 | 1,965 tracks browseable by album, artist and folder, with covers; queue editable |
| M3 — the surface | 10–15 | The designed tablet now-playing surface and the phone surfaces, on the device |
| M4 — meter and polish | 16–19 | Meter fed by the loudness envelope; runs in the background; every failure state |

Four, not six: art (one task) and the meter (two) were too thin to end a milestone on.

## Review Focus

1. **Binary payload containing `\nOK\n`** — art bytes may contain the terminator sequence mid-stream; a substring check truncates the JPEG. Pinned in Task 2 (transport) and Task 9 (art).
2. **Server accepts the socket then never replies** (stale SMB share, observed on the real device) — must surface a bounded timeout and an `Unavailable` state, never a hang. Pinned in Task 2 and Task 19.
3. **Queue changed by another client, rows removed** — `plchanges` never reports deletions; a diff-based queue keeps ghosts. Pinned in Task 8.
4. **SACD rows with missing tags** (`Format:` absent, empty `AlbumArtist`, no `Title`) — must not crash grouping or render an empty strip. Pinned in Task 6 and Task 13.
5. **Album with no cover** — `albumart` answers `ACK 50`; must cache the negative, show the initials placeholder, and never retry in a loop. Pinned in Task 9.
6. **Track absent from the loudness envelope** — the meter must show its "no level data" state rather than animate invented levels. Pinned in Task 17.

---

## Task 1: Project scaffold and MPD reply parser

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdReply.kt`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdParser.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/mpd/MpdParserTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface MpdReply { data class Ok(val pairs: List<Pair<String,String>>) : MpdReply; data class Ack(val code: Int, val commandIndex: Int, val command: String, val message: String) : MpdReply }`
  - `object MpdParser { fun parse(text: String): MpdReply; fun pairs(text: String): List<Pair<String,String>> }`
  - `fun Pair<String,String>.intOrNull(): Int?`, `fun Pair<String,String>.doubleOrNull(): Double?` (in `MpdReply.kt`)

- [ ] **Step 1: Write the failing test**

```kotlin
class MpdParserTest {
    @Test fun `parses key value pairs including values containing colons`() {
        val r = MpdParser.parse("state: play\ntime: 0:78\nfile: a:b/c.flac\nOK\n")
        assertEquals(MpdReply.Ok(listOf("state" to "play", "time" to "0:78", "file" to "a:b/c.flac")), r)
    }
    @Test fun `parses ACK with code, command index and message`() {
        val r = MpdParser.parse("ACK [5@0] {setvol} No mixer\n")
        assertEquals(MpdReply.Ack(5, 0, "setvol", "No mixer"), r)
    }
    @Test fun `keeps repeated keys in order`() {
        val p = MpdParser.pairs("file: a\nTime: 10\nfile: b\nTime: 20\nOK\n")
        assertEquals(4, p.size); assertEquals("a", p[0].second); assertEquals("b", p[2].second)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdParserTest*'`
Expected: FAIL — unresolved reference `MpdParser`.

- [ ] **Step 3: Implement the scaffold and parser**

Scaffold: one module `:app`, `namespace "dev.andrew.plinth"`, Compose enabled, `minSdk 26`, `targetSdk 35`, Java 17, catalog entries for Compose BOM, Room, DataStore, Coil 3, lifecycle, kotlinx-coroutines, junit4/truth, media. Manifest: single `MainActivity`, no permissions yet.

`MpdParser.parse` splits on `\n`, returns `Ack` when the first line matches `^ACK \[(\d+)@(\d+)\] \{([^}]*)\} (.*)$`, otherwise `Ok` with every `key: value` line (split on the **first** `": "` only) and drops the trailing `OK`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdParserTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: gradle scaffold and MPD reply parser"
```

---

## Task 2: Socket transport with bounded reads

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdSocket.kt`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdException.kt`
- Create: `app/src/test/java/dev/andrew/plinth/data/mpd/FakeMpdServer.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/mpd/MpdSocketTest.kt`

**Interfaces:**
- Produces:
  - `class MpdSocket(private val factory: (host: String, port: Int) -> Socket = { h, p -> Socket().apply { connect(InetSocketAddress(h, p), 4000) } })`
  - `suspend fun connect(host: String, port: Int)`; `suspend fun command(line: String, timeoutMs: Long = 3_000): MpdReply`; `suspend fun commandBinary(line: String, timeoutMs: Long): BinaryReply`; `fun close()`; `val isOpen: Boolean`
  - `data class BinaryReply(val size: Int, val bytes: ByteArray)` in `MpdReply.kt`
  - `sealed class MpdException(msg: String) : Exception(msg) { class Timeout; class Protocol; class Disconnected }`
  - Test fixture `class FakeMpdServer` with `start()`, `port`, `onCommand(prefix: String, handler: (String) -> String)`, `emitIdle(vararg subsystems: String)`, `var stall: Boolean`, `sendRaw(bytes: ByteArray)`

- [ ] **Step 1: Write the failing tests**

```kotlin
class MpdSocketTest {
    private lateinit var server: FakeMpdServer
    @Before fun setUp() { server = FakeMpdServer().start() }
    @After fun tearDown() { server.close() }

    @Test fun `reads until terminator, not until substring`() = runBlocking {
        // payload contains a fake "OK\n" inside the art bytes
        server.onCommand("albumart") { "size: 12\nbinary: 12\n\u0000OK\n\u0000\u0000\u0000\u0000\u0000OK\n" }
        val s = MpdSocket(); s.connect("127.0.0.1", server.port)
        val r = s.commandBinary("albumart \"x\" 0", 5_000)
        assertEquals(12, r.bytes.size)
    }
    @Test fun `times out when the server accepts and never answers`() = runBlocking {
        server.stall = true
        val s = MpdSocket(); s.connect("127.0.0.1", server.port)
        assertThrows(MpdException.Timeout::class.java) { runBlocking { s.command("status") } }
    }
    @Test fun `surfaces ACK as MpdReply Ack`() = runBlocking {
        server.onCommand("setvol") { "ACK [5@0] {setvol} No mixer\n" }
        val s = MpdSocket(); s.connect("127.0.0.1", server.port)
        assertTrue(s.command("setvol 10") is MpdReply.Ack)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdSocketTest*'`
Expected: FAIL — `MpdSocket` unresolved.

- [ ] **Step 3: Implement transport and fake server**

`MpdSocket`: a `Mutex`-guarded `BufferedInputStream` read loop on `Dispatchers.IO`. `command()` writes `line + "\n"`, reads until the buffer ends with `OK\n` **or** contains a complete `ACK …\n` line at the start of a line, applying `withTimeout(timeoutMs)` and translating `SocketTimeoutException` → `MpdException.Timeout`, EOF → `Disconnected`.

`commandBinary()` reads the two header lines, then `binary` bytes **by length** (never by scanning for `OK`), then the terminating `OK\n`.

`FakeMpdServer`: `ServerSocket(0)` on a daemon thread; per connection, a line loop that consults `onCommand` handlers by prefix; supports `stall` (subscribe, read, never reply) and raw writes.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdSocketTest*'`
Expected: PASS (3 tests). The first test is the regression for Review Focus 1.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: MPD socket transport with bounded reads and fake server fixture"
```

---

## Task 3: Read commands and state models

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/model/PlayerState.kt`, `.../model/Track.kt`, `.../model/OutputInfo.kt`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdClient.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/mpd/MpdClientReadTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class PlaybackState { Play, Pause, Stop, Unknown }
  data class AudioFormat(val sampleRate: Int, val bits: Int, val channels: Int, val isDsd: Boolean, val dsdRate: Int?) { companion object { fun parse(s: String?): AudioFormat? } }
  data class PlayerState(val state: PlaybackState, val songId: Int?, val songPos: Int?, val elapsed: Double?, val duration: Double?,
                         val queueVersion: Int, val random: Boolean, val repeat: Boolean, val single: Boolean, val consume: Boolean,
                         val format: AudioFormat?, val bitrateKbps: Int?, val audioOutRateHz: Int?, val replayGainMode: String?)
  data class Track(val uri: String, val albumId: String, val disc: Int, val trackNo: Int, val title: String, val artist: String,
                   val albumArtist: String, val album: String, val year: Int?, val genre: String?, val durationSec: Int,
                   val format: AudioFormat?, val path: String, val channels: Int)
  data class OutputInfo(val id: Int, val name: String, val enabled: Boolean, val dopAttribute: Boolean)
  class MpdClient(private val socket: MpdSocket) {
      suspend fun status(): PlayerState
      suspend fun currentSong(): Track?
      suspend fun outputs(): List<OutputInfo>
  }
  ```
- `AudioFormat.parse("96000:24:2")` → PCM; `AudioFormat.parse("dsd64:2")` → `isDsd = true, dsdRate = 64`; malformed → `null`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `status maps every field and tolerates missing elapsed`() = runBlocking {
    server.onCommand("status") { "repeat: 0\nrandom: 1\nsingle: 0\nconsume: 0\nplaylist: 7\nplaylistlength: 12\nstate: stop\nsong: 1\nsongid: 2\nOK\n" }
    val st = client().status()
    assertEquals(PlaybackState.Stop, st.state); assertEquals(2, st.songId); assertNull(st.elapsed); assertEquals(7, st.queueVersion)
}
@Test fun `status parses dsd and pcm formats`() { assertEquals(64, AudioFormat.parse("dsd64:2")!!.dsdRate); assertEquals(96000, AudioFormat.parse("96000:24:2")!!.sampleRate) }
@Test fun `currentsong without Format returns a track with null format`() = runBlocking {
    server.onCommand("currentsong") { "file: A/01 - X.dsf\nTitle: X\nArtist: Y\nTime: 200\nOK\n" }
    val t = client().currentSong()!!
    assertNull(t.format); assertEquals("X", t.title); assertEquals(200, t.durationSec)
}
@Test fun `outputs parses dop attribute`() = runBlocking {
    server.onCommand("outputs") { "outputid: 0\noutputname: Topping DX5 II\nplugin: alsa\noutputenabled: 1\nattribute: dop=1\nOK\n" }
    assertTrue(client().outputs().single().dopAttribute)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdClientReadTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the read commands**

Parse defensively: every field optional, `Int`/`Double` via the helpers from Task 1, `state` mapped with `Unknown` fallback. `currentSong` returns `null` on empty payload. `Track.albumId` comes from `AlbumIdentity.key(...)` (Task 6) — until Task 6 lands, call a placeholder that returns `""` and replace it in Task 6.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdClientReadTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: MPD read commands and state models"
```

---

## Task 4: Transport and player options

**Files:**
- Modify: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdClient.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/mpd/MpdClientWriteTest.kt`

**Interfaces:**
- Produces (all `suspend`):
  ```kotlin
  fun play(pos: Int? = null); fun pause(next: Boolean? = null); fun stop(); fun next(); fun previous()
  fun seekCur(seconds: Double); fun setRandom(on: Boolean); fun setRepeat(on: Boolean)
  fun setSingle(on: Boolean); fun setConsume(on: Boolean); fun setCrossfade(seconds: Int)
  ```
- Each sends the exact MPD command (`play 3`, `pause 1`, `seekcur 42.5`, `random 1`, …) and throws `MpdException` on `ACK`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `pause with no argument toggles`() = runBlocking {
    server.onCommand("pause") { "OK\n" }
    client().pause()
    assertEquals("pause", server.lastCommand)
}
@Test fun `seek sends one shot with two decimals`() = runBlocking {
    server.onCommand("seekcur") { "OK\n" }
    client().seekCur(42.5); assertEquals("seekcur 42.50", server.lastCommand)
}
@Test fun `options send 1 for true`() = runBlocking {
    server.onCommand("single") { "OK\n" }
    client().setSingle(true); assertEquals("single 1", server.lastCommand)
}
@Test fun `ACK from a write surfaces as an exception`() = runBlocking {
    server.onCommand("play") { "ACK [2@0] {play} Bad song index\n" }
    assertThrows(MpdException.Protocol::class.java) { runBlocking { client().play(99) } }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdClientWriteTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the write commands**

Add `var lastCommand: String?` to `FakeMpdServer` (records the last received line). Map `Ack` → `MpdException.Protocol(message)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdClientWriteTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: transport and player option commands"
```

---

## Task 5: Idle loop, connection supervisor, backoff

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdIdle.kt`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdConnection.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/mpd/MpdConnectionTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class Subsystem { Player, Playlist, Options, Database, Output, Mixer, StoredPlaylist }
  class MpdIdle(private val socket: MpdSocket) { suspend fun next(): Set<Subsystem> }   // parks in `idle`, returns changed subsystems, re-arms
  sealed interface ConnectionState { data object Connected : ConnectionState; data class Connecting(val attempt: Int) : ConnectionState; data class Unavailable(val reason: String) : ConnectionState }
  class MpdConnection(host: String, port: Int) {
      val state: StateFlow<ConnectionState>
      suspend fun start(); suspend fun stop()
      val client: MpdClient
      val events: SharedFlow<Set<Subsystem>>
  }
  ```
- Backoff: 500 ms doubling to 16 s, jitter ±20 %, resets on success. `start()` is idempotent.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `idle returns subsystems and re-arms`() = runBlocking {
    server.onCommand("idle") { "changed: player\nOK\n" }
    val idle = MpdIdle(socketTo(server))
    assertEquals(setOf(Subsystem.Player), idle.next())
}
@Test fun `connection goes Unavailable after repeated failures with backoff`() = runBlocking {
    val c = MpdConnection("127.0.0.1", 1)   // nothing listening
    c.start()
    advanceTimeBy(2_000)
    assertTrue(c.state.value is ConnectionState.Unavailable || c.state.value is ConnectionState.Connecting)
}
@Test fun `backoff sequence is capped at 16s and reset by success`() { /* pure Backoff class: 500,1000,2000,…,16000,16000 */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdConnectionTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`Backoff` as a pure class (`fun nextDelayMs(): Long`, `fun reset()`), tested without time. `MpdConnection` runs a coroutine that connects, emits `Connected`, starts the idle loop on a second socket, forwards `events`, and on failure emits `Connecting`/`Unavailable` and schedules a retry with `delay(backoff.nextDelayMs())` on a `TestScope`-injectable dispatcher.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*MpdConnectionTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: idle loop and connection supervisor with backoff"
```

---

## Task 6: Album identity and library sync

> **Before this task (from the Phase-1 review):** `MpdClient` holds a single command
> socket behind a mutex, and the default timeout is 3 s. `listallinfo` needs up to
> 20 s (`MpdSocket.LIST_TIMEOUT_MS`) and must not block a transport press behind it:
> give the library sync its own `MpdSocket` (a third connection), and pass
> `LIST_TIMEOUT_MS` explicitly. Also raise the chunk cap once per command connection
> with `MpdClient.binaryLimit(...)` before any `albumart` call — the default 8 KB
> splits a 302 KB cover into ~37 chunks.


**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/model/AlbumIdentity.kt`, `.../model/Album.kt`
- Create: `app/src/main/java/dev/andrew/plinth/data/mpd/LibraryParser.kt`
- Modify: `app/src/main/java/dev/andrew/plinth/data/mpd/MpdClient.kt` (add `suspend fun listAllInfo(): List<Track>`)
- Test: `app/src/test/java/dev/andrew/plinth/data/model/AlbumIdentityTest.kt`, `.../mpd/LibraryParserTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Album(val id: String, val title: String, val artist: String, val year: Int?, val trackCount: Int, val edition: String?)
  object AlbumIdentity {
      fun normalise(s: String): String                       // trim, lowercase, collapse spaces, strip trailing " *"
      fun key(albumArtist: String, artist: String, album: String, channels: Int): String   // sha1 hex of "artist\u0000album\u0000edition"
      fun edition(channels: Int): String?                    // null when <= 2, else "5.1"/"4.0"/"Nch"
      fun dedupe(tracks: List<Track>): List<Track>           // drop repeats of (disc, trackNo, title, duration), keep first
  }
  ```
- `LibraryParser.parse(pairs: List<Pair<String,String>>, fallbackFormatFromExt: Boolean = true): List<Track>` — blocks start at `file:`, no blank-line separators.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `duplicate discographies collapse to one album's worth of tracks`() {
    val tracks = parseFixture("listallinfo_manson_dupes.txt")   // 198 rows, two identical 99-track copies
    assertEquals(99, AlbumIdentity.dedupe(tracks).size)
    assertEquals(1, AlbumIdentity.dedupe(tracks).map { it.albumId }.distinct().size)
}
@Test fun `box sets in several folders merge`() {
    val t = parseFixture("listallinfo_mellon_collie.txt")   // 5 folders, same album tags
    assertEquals(1, t.map { it.albumId }.distinct().size)
}
@Test fun `multichannel mixes get their own edition`() {
    val t = parseFixture("listallinfo_raven.txt")           // stereo + 5.1 + binaural
    assertTrue(t.filter { it.channels > 2 }.all { it.albumId != t.first { x -> x.channels <= 2 }.albumId })
    assertEquals("5.1", AlbumIdentity.edition(6))
}
@Test fun `sacd rows without Format or AlbumArtist do not throw`() {
    val t = LibraryParser.parse(pairsOf("file: A/01.dsf\nTitle: X\nArtist: Y\nTime: 200\nOK\n"))
    assertEquals(1, t.size); assertNull(t[0].format); assertEquals("Y", t[0].albumArtist)
}
@Test fun `dsd format derives from extension when tags are missing`() {
    val t = LibraryParser.parse(pairsOf("file: A/01.dsf\nTitle: X\nTime: 200\nOK\n"))
    assertTrue(t[0].format!!.isDsd)
}
```

Fixtures: copy four real blocks (trimmed) from the device's `listallinfo` into `app/src/test/resources/`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*AlbumIdentityTest*' --tests '*LibraryParserTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`normalise` per signature above. `key` = SHA-1 of `"${normalise(albumArtist.ifBlank { artist })}\u0000${normalise(album)}\u0000${edition ?: ""}"`, hex-encoded, first 16 chars. `dedupe` keeps first occurrence. `LibraryParser` reads `Format:` → `AudioFormat.parse`, else infers from `.dsf/.dff` extension, else `null`; `channels` from `AudioFormat.channels` (2 when unknown). Replace the placeholder `albumId` in `MpdClient.currentSong()` with `AlbumIdentity.key(...)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*AlbumIdentityTest*' --tests '*LibraryParserTest*'`
Expected: PASS. Review Focus 4 is pinned here.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: album identity rules and listallinfo parser"
```

---

## Task 7: Room cache and library queries

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/lib/LibraryDb.kt`, `.../lib/LibraryDao.kt`, `.../lib/LibraryRepository.kt`, `.../lib/Entities.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/lib/LibraryRepositoryTest.kt` (Robolectric + in-memory Room)

**Interfaces:**
- Produces:
  ```kotlin
  @Entity(tableName = "track") data class TrackEntity(@PrimaryKey val uri: String, val albumId: String, val disc: Int, val trackNo: Int,
      val title: String, val artist: String, val albumArtist: String, val album: String, val year: Int?, val durationSec: Int,
      val channels: Int, val sampleRate: Int?, val bits: Int?, val isDsd: Boolean, val path: String)
  @Entity(tableName = "album") data class AlbumEntity(@PrimaryKey val id: String, val title: String, val artist: String, val year: Int?, val trackCount: Int, val edition: String?)
  class LibraryRepository(db: LibraryDb) {
      suspend fun replaceAll(tracks: List<Track>)
      fun albums(): Flow<List<Album>>
      fun artists(): Flow<List<String>>
      fun albumTracks(albumId: String): Flow<List<Track>>
      fun folders(): Flow<List<String>>            // distinct first path segment, sorted
      fun folderTracks(folder: String): Flow<List<Track>>
      fun search(query: String): Flow<List<Track>>  // FTS4 on title/artist/album, prefix match
      suspend fun trackCount(): Int
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `replaceAll is idempotent and keeps one album row`() = runBlocking {
    repo.replaceAll(fixtureManson()); repo.replaceAll(fixtureManson())
    assertEquals(99, repo.trackCount()); assertEquals(1, repo.albums().first().size)
}
@Test fun `search is prefix and case insensitive`() = runBlocking {
    repo.replaceAll(fixtureLibrary())
    assertEquals(1, repo.search("teard").first().size)   // "Teardrop"
}
@Test fun `folders returns the first path segment only`() = runBlocking {
    repo.replaceAll(fixtureLibrary())
    assertEquals(listOf("Mezzanine"), repo.folders().first())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryRepositoryTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Room DB v1 with `track`, `album`, and `track_fts` (FTS4, `contentEntity = TrackEntity`); `replaceAll` in a single `@Transaction` (delete + insert). Album rows derived from `tracks.groupBy { it.albumId }`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryRepositoryTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: room library cache and queries"
```

---

## Task 8: Queue with full refetch and reordering

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/model/QueueItem.kt`, `.../lib/QueueRepository.kt`
- Modify: `.../mpd/MpdClient.kt` (add `playlistInfo`, `add`, `addId`, `deleteId`, `moveId`, `clear`, `savePlaylist`, `listPlaylists`, `loadPlaylist`)
- Test: `app/src/test/java/dev/andrew/plinth/data/lib/QueueRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class QueueItem(val id: Int, val pos: Int, val uri: String, val title: String, val artist: String, val album: String, val durationSec: Int)
  class QueueRepository(private val client: MpdClient) {
      val items: StateFlow<List<QueueItem>>
      suspend fun refresh()                     // playlistinfo, replaces the list wholesale
      suspend fun playAt(pos: Int); suspend fun remove(id: Int); suspend fun move(from: Int, to: Int); suspend fun clearAll()
      suspend fun addToQueue(uri: String); suspend fun saveAs(name: String); suspend fun load(name: String); suspend fun playlists(): List<String>
  }
  ```
- `refresh()` replaces the list; it never diffs.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `refresh drops rows removed by another client`() = runBlocking {
    server.queue(listOf(item(1, "a"), item(2, "b"), item(3, "c")))
    repo.refresh(); assertEquals(3, repo.items.value.size)
    server.queue(listOf(item(2, "b")))          // another client deleted two rows
    repo.refresh(); assertEquals(listOf(2), repo.items.value.map { it.id })
}
@Test fun `move sends moveid with from and to`() = runBlocking {
    repo.move(0, 2); assertEquals("moveid 0 1", server.lastCommand)  // pos -> before position, per MPD semantics
}
@Test fun `addToQueue escapes quotes in uris`() = runBlocking {
    repo.addToQueue("A \"quoted\"/x.flac"); assertEquals("addid \"A \\\"quoted\\\"/x.flac\"", server.lastCommand)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*QueueRepositoryTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Add `server.queue(...)` to the fake (serves `playlistinfo` from a list). `move(from, to)` translates a *list index* to MPD's `moveid <id> <targetPosition>` using the id at `from` and the position of the item currently at `to`. Quote-escaping helper `mpdQuote(s: String)` used by every command that takes a string.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*QueueRepositoryTest*'`
Expected: PASS (3 tests). Review Focus 3 is pinned here.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: queue repository with wholesale refetch"
```

---

## Task 9: Cover art with binarylimit, negative cache and disk cache

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/art/ArtRepository.kt`, `.../art/ArtCache.kt`
- Modify: `.../mpd/MpdClient.kt` (add `suspend fun binaryLimit(bytes: Int)`, `suspend fun albumArt(uri: String, offset: Int, timeoutMs: Long = 15_000): BinaryReply`)
- Test: `app/src/test/java/dev/andrew/plinth/data/art/ArtRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface ArtResult { data class Jpeg(val bytes: ByteArray) : ArtResult; data object None : ArtResult; data class Failed(val reason: String) : ArtResult }
  class ArtRepository(client: MpdClient, cacheDir: File) {
      suspend fun forAlbum(albumId: String, firstTrackUri: String): ArtResult   // caches by albumId
      fun cachedFile(albumId: String): File?
      fun negativeCached(albumId: String): Boolean
  }
  ```
- Behaviour: `binaryLimit(4_000_000)` attempted once per connection (ignore `ACK`); `ACK [50@0]` → `None` and a `none-<albumId>` marker file with a 7-day mtime; a `Timeout`/`Disconnected` → `Failed` and **no** negative marker.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `ack 50 caches a negative that survives a new repository instance`() = runBlocking {
    server.onCommand("albumart") { "ACK [50@0] {albumart} No file exists\n" }
    assertEquals(ArtResult.None, repo.forAlbum("id1", "x.flac"))
    assertTrue(ArtRepository(client(), dir).negativeCached("id1"))
}
@Test fun `timeout does not cache a negative`() = runBlocking {
    server.stall = true
    assertTrue(repo.forAlbum("id2", "x.flac") is ArtResult.Failed)
    assertFalse(repo.negativeCached("id2"))
}
@Test fun `jpeg with terminator bytes inside is stored whole`() = runBlocking {
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + "\nOK\n".toByteArray() + ByteArray(200)
    server.binaryResponse(jpeg)
    val r = repo.forAlbum("id3", "x.flac") as ArtResult.Jpeg
    assertEquals(207, r.bytes.size)
}
@Test fun `negative older than seven days is retried`() { /* marker mtime = 8 days ago -> negativeCached false */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*ArtRepositoryTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Cache layout `art/<albumId>.jpg` and `art/none-<albumId>`. Downscaling to 512 px happens in the UI layer's image loader, not here (keeps this class JVM-testable). `binaryResponse(bytes)` on the fake emits a correct `size:`/`binary:` header and the bytes.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ArtRepositoryTest*'`
Expected: PASS (4 tests). Review Focuses 1 and 5 are pinned here.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: cover art fetch with negative and disk caches"
```

---

## Task 10: Settings and connection screen

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/data/settings/SettingsStore.kt`, `.../data/settings/Settings.kt`
- Create: `app/src/main/java/dev/andrew/plinth/ui/screens/ConnectionScreen.kt`
- Test: `app/src/test/java/dev/andrew/plinth/data/settings/SettingsStoreTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Settings(val host: String = "10.0.0.219", val port: Int = 6600, val stayAwakeWhileCharging: Boolean = false,
                      val meterOffsetPcmMs: Int = 120, val meterOffsetDsdMs: Int = 240, val ambientMeter: Boolean = false)
  class SettingsStore(context: Context) { val flow: Flow<Settings>; suspend fun update(transform: (Settings) -> Settings) }
  ```
- `ConnectionScreen(onConnect: (host: String, port: Int) -> Unit)`: two fields, a Connect button, and the current `ConnectionState` as text. No auth fields.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `defaults are the streamer and persist changes`() = runBlocking {
    val s = SettingsStore(ctx)
    assertEquals("10.0.0.219", s.flow.first().host)
    s.update { it.copy(port = 6601) }
    assertEquals(6601, s.flow.first().port)
}
@Test fun `invalid port text is rejected before update`() { assertNull(ConnectionScreenKt.parsePort("70000")); assertEquals(6600, parsePort("6600")) }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsStoreTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

DataStore Preferences (not proto). `parsePort` as a top-level function returning `Int?` for 1..65535.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsStoreTest*'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: settings store and connection screen"
```

---

## Task 11: App shell, theme and surface breakpoints

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/PlinthApp.kt`, `.../AppContainer.kt`, `.../MainActivity.kt`
- Create: `app/src/main/java/dev/andrew/plinth/ui/theme/{Color,Type,Theme}.kt`
- Create: `app/src/main/java/dev/andrew/plinth/ui/layout/SurfaceSize.kt`
- Test: `app/src/androidTest/java/dev/andrew/plinth/ui/layout/SurfaceSizeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class SurfaceSize { Compact, Medium, Expanded }
  @Composable fun rememberSurfaceSize(): SurfaceSize   // <600dp Compact, 600..839 Medium, >=840 Expanded
  object PlinthColors { val Base = Color(0xFF0A0A0C); val Text = Color(0xFFF6F5F3); val Muted = Color(0x8AF6F5F3); val Accent = Color(0xFF7ED9C3) }
  @Composable fun PlinthTheme(content: @Composable () -> Unit)
  class MainActivity : ComponentActivity()   // sets content to AppRoot(nav) with a NavigationBar for Compact, none for Expanded
  ```

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
@Test fun `width under 600dp is Compact and 900dp is Expanded`() {
    composeRule.setContent { assertEquals(SurfaceSize.Compact, measureSize(600.dp - 1.dp)) }  // helper wrapping BoxWithConstraints
}
```

(Both assertions in one test via a parameterised `measureSize(width: Dp)` helper in the test file.)

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*SurfaceSizeTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Theme with the spec's seven-step ramp (64/44/28/20/16/13/11 at weights 300/400/500), Inter variable bundled from `res/font`, and a Noto Sans Arabic subset behind `:lang(ar)` with tracking and uppercase suppressed for joined scripts. `AppRoot` holds a `NavHost` with routes `now`, `library`, `album/{id}`, `queue`, `search`, `output`, `connection`. `AppContainer` constructs `SettingsStore`, `MpdConnection`, repositories; created in `PlinthApp.onCreate`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*SurfaceSizeTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: app shell, theme and surface breakpoints"
```

---

## Task 12: Now Playing, expanded (tablet) arrangement

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/ui/screens/NowPlayingScreen.kt`, `.../ui/components/{Artwork,Transport,ProgressHairline,QueueBand,LevelMeter}.kt`
- Create: `app/src/main/java/dev/andrew/plinth/ui/vm/PlayerViewModel.kt`
- Test: `app/src/androidTest/java/dev/andrew/plinth/ui/NowPlayingExpandedTest.kt`

**Interfaces:**
- Consumes: `PlayerState`, `QueueItem`, `ArtRepository.cachedFile(albumId)`, `Settings.meterOffsetPcmMs/meterOffsetDsdMs`
- Produces:
  ```kotlin
  @Composable fun NowPlayingScreen(size: SurfaceSize, vm: PlayerViewModel)
  @Composable fun Artwork(albumId: String, firstTrackUri: String, art: ArtRepository, modifier: Modifier)
  @Composable fun ProgressHairline(elapsed: Double?, duration: Double?, onSeek: (Double) -> Unit)
  @Composable fun Transport(isPlaying: Boolean, enabled: Boolean, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit)
  @Composable fun QueueBand(items: List<QueueItem>, onItemClick: (Int) -> Unit, onOpenQueue: () -> Unit, modifier: Modifier)
  class PlayerViewModel(state: StateFlow<PlayerState>, queue: QueueRepository, client: MpdClient) {
      val ui: StateFlow<PlayerUi>
      fun playPause(); fun next(); fun previous(); fun seek(seconds: Double)
  }
  ```

**Structure (expanded, ≥840 dp):** 56 dp insets; a 420 dp cover; a 584 dp column whose bottom edge is pinned to the cover's, containing title (64 sp, 44 sp step for a two-line title), artist 20 sp, position 13 sp, the signal strip, a full-width progress hairline, and a **centred three-control transport**; then a 152 dp band pinned to the window's bottom edge, holding the queue at the start (shuffle/repeat beside its label) and `LevelMeter` at the end. No rail. Gaps only from 8/12/16/24/40/64/96.

- [ ] **Step 1: Write the failing instrumented tests**

```kotlin
@Test fun `expanded shows cover, title, centred transport and the band`() {
    composeRule.setContent { PlinthTheme { NowPlayingScreen(SurfaceSize.Expanded, fakeVm(title = "Teardrop")) } }
    composeRule.onNodeWithTag("cover").assertWidthIsEqualTo(420.dp)
    composeRule.onNodeWithText("Teardrop").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Play").assertIsCenteredInColumn()   // x-centre within 1 dp
    composeRule.onNodeWithText("Up next", substring = true).assertExists()
    composeRule.onNodeWithContentDescription("Stereo level meter").assertExists()
}
@Test fun `transport keeps one position when the title wraps to two lines`() {
    // measure the play button's y with a 1-line title, then a 2-line title; assert equal within 1 dp
}
@Test fun `seek is sent once on release, not per drag`() { /* exactly one seekcur per drag */ }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*NowPlayingExpandedTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

The column uses `heightIn(min = 420.dp)` with the transport pinned to its bottom, so a wrapped title grows upward and the play button never moves. Titles use `maxLines = 2, overflow = Ellipsis`, stepping to 44 sp on **measured width**. `Artwork` crossfades on `albumId` change. `ProgressHairline` emits `onSeek` on drag end only.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*NowPlayingExpandedTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: expanded now playing with cover spine and foot band"
```

---

## Task 13: Signal strip and verdict

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/ui/components/SignalStrip.kt`, `.../data/model/SignalVerdict.kt`
- Modify: `NowPlayingScreen.kt` (place the strip under the artist line)
- Test: `app/src/test/java/dev/andrew/plinth/data/model/SignalVerdictTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface SignalVerdict { data object BitPerfect : SignalVerdict; data object Lossy : SignalVerdict
      data class Transcoded(val from: String, val to: String) : SignalVerdict
      data class Multichannel(val channels: Int) : SignalVerdict; data object Unknown : SignalVerdict }
  object SignalVerdicts { fun of(state: PlayerState, track: Track?, output: OutputInfo?): SignalVerdict }
  data class StripCells(val source: String, val link: String, val verdict: String)
  fun stripCells(state: PlayerState, track: Track?, output: OutputInfo?): StripCells
  @Composable fun SignalStrip(track: Track?, state: PlayerState, output: OutputInfo?)
  ```
- Rules: file format vs `state.format` equal **and** `replayGainMode == "off"` **and** output is the ALSA `hw:0,0` output → `BitPerfect`. Channels > 2 → `Multichannel`. Lossy extension → `Lossy`. Mismatched rate → `Transcoded`. No track/format → `Unknown`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `matching pcm with rg off is bit perfect`() { assertEquals(BitPerfect, of(track("flac", 96000, 24, 2), status("96000:24:2", "off"))) }
@Test fun `rate mismatch is transcoded`() { assertTrue(of(track("flac", 192000, 24, 2), status("96000:24:2", "off")) is Transcoded) }
@Test fun `dsd over dop is bit perfect and labelled`() { assertEquals(BitPerfect, of(track("dsf", 2822400, 1, 2), status("dsd64:2", "off"))); assertEquals("DSD64 · DoP", labelFor(...)) }
@Test fun `six channel file is multichannel, not transcoded`() { assertTrue(of(track("flac", 96000, 24, 6), status("96000:24:6", "off")) is Multichannel) }
@Test fun `missing format is unknown, not a crash`() { assertEquals(Unknown, of(track(null, 0, 0, 2), status(null, "off"))) }
@Test fun `replay gain on downgrades the verdict`() { assertTrue(of(track("flac", 44100, 16, 2), status("44100:16:2", "track")) is Transcoded) }
@Test fun `the link cell names the path, and prints a rate only when it differs`() {
    assertEquals("USB · DX5 II", stripCells(status("96000:24:2", "off"), track("flac", 96000, 24, 2), output).link)
    assertEquals("44.1 → 48 kHz", stripCells(status("48000:24:2", "off"), track("flac", 44100, 16, 2), output).link)
}
@Test fun `the three cells are format, link, verdict — never the rate twice`() {
    val c = stripCells(status("96000:24:2", "off"), track("flac", 96000, 24, 2), output)
    assertEquals(listOf("FLAC · 24/96", "USB · DX5 II", "Bit-perfect"), listOf(c.source, c.link, c.verdict))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SignalVerdictTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`labelFor(format)` renders `FLAC · 24/96`, `DSD64 · DoP`, `MP3 · 320 kbps`, `—` when unknown. Strip layout: three cells — **Source / Link / Verdict** — 11 sp labels at weight 500 and +0.12 em, 16 sp values with tabular figures, verdict in accent when `BitPerfect` and muted otherwise. The output rate appears in the Link cell only on a mismatch (`44.1 → 48 kHz`); on the happy path that cell names the path, so no fact is printed twice.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*SignalVerdictTest*'`
Expected: PASS (6 tests). Review Focus 4 is pinned on the UI side here.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: signal strip with computed bit-perfect verdict"
```

---

## Task 14: Phone surfaces and the medium window

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/ui/screens/{MiniPlayer,NowPlayingSheet,MediumNowPlaying}.kt`
- Modify: `MainActivity.kt` (Compact branch: library root + mini player + navigation bar)
- Test: `app/src/androidTest/java/dev/andrew/plinth/ui/PhoneSurfacesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun MiniPlayer(title: String, artist: String, albumId: String, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit)
  @Composable fun NowPlayingSheet(vm: PlayerViewModel, onDismiss: () -> Unit)   // 12 dp inset, chevron, up-next row, no meter
  @Composable fun MediumNowPlaying(vm: PlayerViewModel)                          // 600–839 dp: stacked spine
  ```

- [ ] **Step 1: Write the failing instrumented tests**

```kotlin
@Test fun `tapping the mini player opens the sheet and the chevron closes it`() {
    composeRule.setContent { PhoneRoot(fakeVm("Teardrop")) }
    composeRule.onNodeWithTag("mini-player").performClick()
    composeRule.onNodeWithTag("now-playing-sheet").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Close now playing").performClick()
    composeRule.onNodeWithTag("now-playing-sheet").assertDoesNotExist()
}
@Test fun `sheet shows an up-next row and no meter`() {
    composeRule.onNodeWithText("Up next", substring = true).assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Stereo level meter").assertDoesNotExist()
}
@Test fun `medium width stacks the spine`() {
    composeRule.setContent { MediumNowPlaying(fakeVm("Teardrop")) }   // hosted at 760 x 980
    composeRule.onNodeWithTag("queue-band").assertExists()
    composeRule.onNodeWithTag("cover").assertWidthIsEqualTo(760.dp - 80.dp)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*PhoneSurfacesTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Sheet = `ModalBottomSheet` with `skipPartiallyExpanded`, 12 dp top inset, 20 dp top radius, a 44 dp chevron, and a bottom `Up next · N` row wired to the queue. `MediumNowPlaying` stacks cover (full width, 400 dp) → title 44 sp → strip → progress → transport, with the band beneath. Mini player inset 24 dp, matching every other block.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*PhoneSurfacesTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: phone sheet with exit and up-next, medium stacked spine"
```

---

## Task 15: Library screens and search

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/ui/screens/{LibraryScreen,AlbumScreen,SearchScreen,QueueScreen,PlaylistsScreen,OutputScreen}.kt`
- Create: `app/src/main/java/dev/andrew/plinth/ui/vm/LibraryViewModel.kt`
- Test: `app/src/androidTest/java/dev/andrew/plinth/ui/LibraryScreenTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun LibraryScreen(repo: LibraryRepository, art: ArtRepository, onAlbum: (String) -> Unit, onPlay: (List<Track>) -> Unit, tabs: List<LibraryTab>)
  enum class LibraryTab { Albums, Artists, Tracks, Folders }
  @Composable fun AlbumScreen(albumId: String, repo: LibraryRepository, art: ArtRepository, onPlayAll: () -> Unit)
  @Composable fun SearchScreen(repo: LibraryRepository, onPlay: (Track) -> Unit)
  ```

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
@Test fun `album grid shows art placeholders and counts`() {
    composeRule.setContent { LibraryScreen(fakeRepo(3 albums), fakeArt(none), {}, {}, listOf(Albums)) }
    composeRule.onAllNodesWithTag("album-tile").assertCountEquals(3)
    composeRule.onNodeWithText("No cover").assertExists()      // placeholder for artless album
}
@Test fun `search filters as you type`() {
    composeRule.onNodeWithTag("search-field").performTextInput("teard")
    composeRule.onNodeWithText("Teardrop").assertIsDisplayed()
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*LibraryScreenTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

Album grid: `LazyVerticalGrid(GridCells.Adaptive(160.dp))`, art via Coil with `ArtRepository.cachedFile`, initials placeholder for `ArtResult.None`. Album screen: art header + track list + "Play all" (excludes `channels > 2`) and "Queue all". Output screen: the single output, DoP flag, bit-perfect explanation, "Volume is the DAC's knob; this streamer has no mixer", Meter sync sliders (Tasks 10/17), Stay-awake toggle.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*LibraryScreenTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: library, album, search, queue, playlists and output screens"
```

---

## Task 16: Loudness envelope — scanner, reader, import

**Files:**
- Create: `tools/vu-scan/vu_scan.py`, `tools/vu-scan/README.md`
- Create: `app/src/main/java/dev/andrew/plinth/data/loudness/LoudnessIndex.kt`, `.../loudness/LoudnessImporter.kt`
- Test: `tools/vu-scan/test_vu_scan.py`, `app/src/test/java/dev/andrew/plinth/data/loudness/LoudnessIndexTest.kt`

**Interfaces:**
- Produces:
  ```python
  # vu_scan.py
  def scan(root: Path, out_path: Path, rate: int = 5, jobs: int = 4) -> int   # returns track count written
  def frame_for(uri: str, elapsed: float, frames: list[int]) -> int | None
  ```
- Kotlin:
  ```kotlin
  data class StereoLevel(val left: Int, val right: Int, val peak: Int)
  class LoudnessIndex private constructor(private val buffer: MappedByteBuffer, private val index: Map<String, IntRange>) {
      companion object { fun open(file: File): LoudnessIndex?; fun parse(header: ByteArray): Boolean }
      fun levelAt(uri: String, positionSec: Double): StereoLevel?    // null when absent or position outside range
      val trackCount: Int; val dbUpdate: Long
  }
  class LoudnessImporter(context: Context) { suspend fun import(uri: Uri): ImportResult }   // copies to filesDir/loudness.bin
  ```

- [ ] **Step 1: Write the failing tests**

```python
def test_writes_expected_size_and_header(tmp_path):
    # 3 s sine at 0 dBFS, 5 Hz -> 15 frames; file = 4+2+8+4 + record(2+len+4+15*3)
    assert scan(tmp_path, out, rate=5) == 1
    data = out.read_bytes()
    assert data[:4] == b"VU01"
def test_loud_track_has_higher_rms_than_quiet(tmp_path):
    # two files, -3 dBFS and -30 dBFS; mean rms of first > second by > 40
```

```kotlin
@Test fun `absent track returns null, not zero`() { assertNull(index.levelAt("nope.flac", 10.0)) }
@Test fun `position maps to the right frame and clamps`() { assertEquals(levelAt5s, index.levelAt("a.flac", 5.0)); assertNull(index.levelAt("a.flac", 9999.0)) }
@Test fun `bad magic returns null from open`() { /* file of zeros -> open returns null */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tools/vu-scan/test_vu_scan.py -q` and `./gradlew :app:testDebugUnitTest --tests '*LoudnessIndexTest*'`
Expected: FAIL — not implemented.

- [ ] **Step 3: Implement**

`vu_scan.py`: walk `*.flac|*.dsf|*.dff|*.mp3|*.ogg|*.m4a`; per file, one `ffmpeg -v error -i FILE -f s16le -ac 2 -ar 8000 pipe:1`, compute per-window RMS in dBFS, map −60..0 → 0..255 (`clip(round((db + 60) / 60 * 255))`), track peak per frame; write header + records; parallel with `concurrent.futures.ThreadPoolExecutor(jobs)`. CLI: `python vu_scan.py --root DIR --out loudness.bin`.

`LoudnessIndex`: read header, build a `Map<String, IntRange>` of frame offsets, expose `levelAt` → `MappedByteBuffer.get(offset + frame*3)`.

`LoudnessImporter`: `contentResolver.openInputStream(uri)` → copy to `filesDir/loudness.bin` on `Dispatchers.IO`; returns `Imported(tracks, dbUpdate)` or `Rejected(reason)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tools/vu-scan/test_vu_scan.py -q` then `./gradlew :app:testDebugUnitTest --tests '*LoudnessIndexTest*'`
Expected: PASS both.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: loudness envelope scanner, reader and importer"
```

---

## Task 17: The VU meter

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/ui/components/VuMeter.kt`
- Modify: `NowPlayingScreen.kt` (the band's trailing block), `MediumNowPlaying.kt`
- Test: `app/src/test/java/dev/andrew/plinth/ui/VuBallisticsTest.kt`, `app/src/androidTest/java/dev/andrew/plinth/ui/VuMeterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class Ballistics(initial: Float = 0f) { fun step(current: Float, target: Float, dtMs: Long): Float }   // attack 30 ms, release 350 ms
  data class MeterFrame(val left: Float, val right: Float, val peakLeft: Float, val peakRight: Float)
  @Composable fun LevelMeter(level: StereoLevel?, active: Boolean, modifier: Modifier)
  ```

**Drawn form:** two 16 dp bars 10 dp apart, 56 dp tall, in the band's trailing block under a header carrying the live value (`LEVEL  −14 dBFS`). No scale rail and no L/R glyphs — the header carries the number. Never rendered on the phone.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `attack is faster than release`() {
    val up = Ballistics().step(0f, 1f, 30); val down = Ballistics(initial = 1f).step(1f, 0f, 30)
    assertTrue(up > 0.5f); assertTrue(down > 0.85f)
}
@Test fun `release reaches near zero after a second of silence`() { /* < 0.1 */ }
```

```kotlin
@Test fun `no data renders the unavailable state, not an animation`() {
    composeRule.setContent { LevelMeter(level = null, active = true) }
    composeRule.onNodeWithContentDescription("No level data").assertIsDisplayed()
}
@Test fun `header prints the live value`() { /* "-14 dBFS" present for (-14,-15) */ }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*VuBallisticsTest*'` and `./gradlew :app:connectedDebugAndroidTest --tests '*VuMeterTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`Ballistics` as an exponential follower (`alpha = 1 - exp(-dt/tau)`). `LevelMeter` ticks only while `active`; `level == null` draws a 1 dp baseline at 15 % alpha plus the content description "No level data". Position = `elapsed − offsetMs/1000`, with `offsetMs` from settings keyed per format class (PCM / DoP).

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*VuBallisticsTest*'` and `./gradlew :app:connectedDebugAndroidTest --tests '*VuMeterTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: level meter in the band with numeric header"
```

---

## Task 18: Playback service, notification, background policy

**Files:**
- Create: `app/src/main/java/dev/andrew/plinth/service/PlaybackService.kt`
- Modify: `AndroidManifest.xml` (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, service declaration with `foregroundServiceType="mediaPlayback"`)
- Test: `app/src/androidTest/java/dev/andrew/plinth/service/PlaybackServiceTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class PlaybackService : Service() {
      companion object { fun start(ctx: Context); fun stop(ctx: Context); const val CHANNEL_ID = "now_playing" }
  }
  class BackgroundPolicy(val stayAwakeWhileCharging: Boolean, val charging: Boolean) {
      fun shouldRun(state: PlaybackState, minutesSinceInactive: Long): Boolean  // playing -> true; paused/stopped -> <= 1 min; charging+awake -> true
      fun shouldKeepScreenOn(state: PlaybackState): Boolean
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `keeps running while playing and for one minute after`() {
    assertTrue(BackgroundPolicy(false, false).shouldRun(Play, 999))
    assertTrue(BackgroundPolicy(false, false).shouldRun(Pause, 0))
    assertFalse(BackgroundPolicy(false, false).shouldRun(Pause, 2))
}
@Test fun `stay awake while charging keeps it alive when idle`() {
    assertTrue(BackgroundPolicy(true, true).shouldRun(Stop, 60))
    assertFalse(BackgroundPolicy(true, false).shouldRun(Stop, 60))
}
@Test fun `screen on only while playing`() { assertTrue(p.shouldKeepScreenOn(Play)); assertFalse(p.shouldKeepScreenOn(Pause)) }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackgroundPolicyTest*'`, then `./gradlew :app:connectedDebugAndroidTest --tests '*PlaybackServiceTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`MediaSessionCompat` with `PlaybackStateCompat` built from `PlayerState` (actions: play, pause, next, prev, stop; `setState(..., elapsed, 1.0f)`), art as the album's cached bitmap. Notification via `NotificationCompat.MediaStyle`; dismissing it stops the service. `shouldKeepScreenOn` drives `window.addFlags(FLAG_KEEP_SCREEN_ON)` on the activity.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackgroundPolicyTest*'` then `./gradlew :app:connectedDebugAndroidTest --tests '*PlaybackServiceTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: media notification and hybrid background policy"
```

---

## Task 19: States and offline behaviour end to end

**Files:**
- Modify: `PlayerViewModel.kt` (map `ConnectionState` → chip), `LibraryScreen.kt`, `NowPlayingScreen.kt`
- Test: `app/src/androidTest/java/dev/andrew/plinth/ui/OfflineTest.kt`

**Interfaces:**
- Produces: `@Composable fun UnavailableChip(reason: String, onRetry: () -> Unit)` and a `PlayerUi.offline: Boolean` flag.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `paused swaps the glyph, drops the fill and flattens the meter`() { /* play icon, 45% fill, no accent */ }
@Test fun `no artwork draws the monogram plate with its label`() { /* "No artwork" present, no spinner */ }
@Test fun `a track outside the envelope keeps the meter's space and says so`() { /* "No level data" */ }
@Test fun `a long title clamps to two lines and still leaves the transport in place`() { /* play y unchanged */ }
@Test fun `stalled server shows the offline chip but keeps cached library browsable`() {
    // fake server accepts, then stalls; open library -> tiles visible, chip displayed
    composeRule.onNodeWithTag("offline-chip").assertIsDisplayed()
    composeRule.onAllNodesWithTag("album-tile").assertCountEquals(3)
}
@Test fun `transport controls are disabled while offline, not removed`() {
    composeRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OfflineTest*'`
Expected: FAIL — chip missing.

- [ ] **Step 3: Implement**

On `ConnectionState.Unavailable`, set `offline = true`, show the chip with the reason (`"Streamer not answering"` for timeout, `"Library share unreachable"` when `lsinfo`/art timeouts dominate), retry button calls `MpdConnection.start()`. Play/pause/next stay visible but disabled. Review Focus 2 is pinned here.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OfflineTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: offline states keep the cached library usable"
```

---

## Final verification

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests green.
- [ ] `./gradlew :app:connectedDebugAndroidTest` — all instrumented tests green on the tablet.
- [ ] `pytest tools/vu-scan/test_vu_scan.py -q` — scanner tests green.
- [ ] Run against the real streamer: cold start → now playing with art < 1.5 s; album grid scrolls smoothly with 135 albums; meter tracks the music within ~80 ms; unplug the SMB share and confirm the app stays usable with the offline chip.
- [ ] `python tools/vu-scan/vu_scan.py --root /home/andrew/1tb/home/andrew/Music --out loudness.bin` produces ~14 MB for 1,965 tracks (951,149 s x 5 Hz x 3 B).
