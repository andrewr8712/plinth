# MPD controller for phone and tablet — design

**Date:** 2026-11-04
**Status:** design for approval
**Working name:** Plinth (package `dev.andrew.plinth`) — cosmetic, change freely
**Target:** the custom OpenWrt streamer at `10.0.0.219` (repo: `andrewr8712/rt-ac88u-openwrt-streamer`)

---

## 1. Intent

A native Android controller for the living-room streamer, built to look and feel
like a piece of hi-fi gear rather than a utility. The existing options are
functional and unpleasant to use: **M.A.L.P.** works but looks like a settings
app, **myMPD** is a web page with a browser's manners.

Two surfaces, two jobs:

- **Tablet** — the now-playing surface. Sits on a stand, shows the record that is
  playing, the signal going to the DAC, and what's coming next.
- **Phone** — the browsing surface. Finding something to play, and the fiddly
  options, happen here.

Both can be on screen at once (multi-window), and the tablet is often in
split-screen next to something else, so no layout may assume it owns the screen.

This is a personal app built by vibecoding: it does not need to generalise to
other people's streamers, but it does need to be honest about what the hardware
can and cannot do.

---

## 2. What the server actually offers (measured, not assumed)

Probed live on 2026-11-04 against `10.0.0.219:6600`.

| Fact | Measurement |
|---|---|
| Protocol | `OK MPD 0.23.5` — that is the **protocol** version; the daemon is 0.23.16 |
| Auth | none; binds all interfaces; no firewall in the image |
| Library | 1,965 tracks / 135 albums / 53 artists / 264 h (`db_playtime` 951,149 s) |
| Full library dump | `listallinfo` → 865,433 B in **0.09 s** |
| Cover art | `albumart` → `size: 301900`, 8 KB chunks, **~60 ms** warm; `readpicture` returns bare `OK` (art is folder images, not embedded) |
| Output | id 0 `Topping DX5 II`, ALSA, `dop=1` |
| Volume | **none** — `setvol`/`volume` → `ACK [5@0] {setvol} No mixer`; `getvol` → bare `OK` |
| WiFi RTT | 3–18 ms (ping avg 8.8 ms) |
| Tags | rich incl. `MUSICBRAINZ_*`, `Composer`, `Work`, `Movement`, `Label` |
| DSD | `dsd64:2` in status; DoP only, DSD512 unsupported |
| Missing formats | SACD/DVD-A rips can report `Format:` absent — derive from extension |

Two failure modes found while probing, both of which shape the client:

1. **File-touching commands block when the SMB mount is stale.** Mid-session the
   share went away: `status` and `ping` answered in <20 ms while `readcomments`,
   `lsinfo` and `albumart` hung past 8 s. The DB lives on the router's flash, but
   art and tag reads go to the network share.
2. **No server-side compute.** The image ships `alsa-lib`, `alsa-utils`, `mpd`,
   `mympd` only — no ffmpeg, python, sox or cava. Nothing that costs CPU on a
   2× Cortex-A9 @ 1.4 GHz belongs there.

Consequence: the client must time-bound every file-touching command, keep the
library and art cached locally, and never let a stale share freeze the UI.

---

## 3. Goals

1. A tablet now-playing surface that looks like the reference apps (FiiO Music,
   PowerAmp, Volumio) without copying any of them.
2. A phone library browser fast enough to feel local: 2,000 tracks browsable,
   searchable, queueable with no spinner.
3. The signal chain shown truthfully — source format, rate to the DAC, DoP,
   bit-perfect status — because that is the interesting thing this box does.
4. A VU meter that shows the **actual** loudness of what is playing, not an
   animation pretending to be one.
5. Both form factors from one APK, correct in split-screen and after rotation.
6. Works when the streamer is unreachable: cached library browses, art renders,
   state is shown as offline instead of spinning.

### Non-goals

- Playing audio on the phone or tablet (the DAC does that; this is a remote).
- iOS, desktop, web.
- UPnP/OpenHome control (`upmpdcli` exists; out of scope).
- Editing tags, ripping, rescanning from the app.
- Volume. There is no mixer; a fader would be a lie.
- Accounts, TLS, multi-room, other people's hardware.

### Anti-goals — these make it a failure even if it runs

- Any visualisation that invents audio data and doesn't say so.
- A settings screen with more than a handful of rows.
- A WebView anywhere.
- Anything that inserts itself into the audio path on the router.
- Blocking UI on a network call, ever.

---

## 4. Approaches considered

### 4.1 Visual direction (chosen in the browser companion, real library art)

| | Direction | Verdict |
|---|---|---|
| A | **Gallery** — blurred cover wash fills the tablet, thin 44 px type, one hairline transport circle, queue in a narrow right rail; phone is a dense album grid | **Chosen** — quiet, room-like, ages well |
| B | **Deck** — FiiO-style two panes: cover + spec left, queue permanently right | Kept in part: the always-visible queue rail is borrowed into A |
| C | **Console** — PowerAmp-style plinth, reflection, brushed panels, amber controls, telemetry block | Kept in part: the telemetry strip is borrowed into A; the full skeuomorphic skin is dropped (fails loudly when details slip) |

The result is **A's layout with C's honesty**: a signal strip under the title and
a level meter in the rail.

### 4.2 How it gets built

| | Approach | Verdict |
|---|---|---|
| 1 | **Kotlin + Jetpack Compose, raw MPD protocol over TCP** | **Chosen** |
| 2 | Flutter / React Native | Dropped — cross-platform buys nothing (iOS not wanted), and the platform's media/notification/adaptive machinery is better in Compose |
| 3 | Wrap myMPD or the MPD web API in a native shell | Dropped — it is the thing being replaced; a WebView also can't do the meter or the art cache well |

### 4.3 How the meter gets real levels

| | Approach | Verdict |
|---|---|---|
| 1 | Decorative animation | Fallback only, for tracks with no envelope |
| 2 | **Precomputed loudness envelope**, scanned once off-device, imported into the app | **Chosen** |
| 3 | Live ALSA loopback tap on the router | Rejected — breaks bit-perfect, adds CPU, needs packages the image doesn't have |

---

## 5. The design

### 5.1 Stack

- Kotlin 2.x, Jetpack Compose (Material 3 as a skeleton only — theming is custom),
  single activity, single Gradle module, manual dependency wiring in one
  `AppContainer` (no Hilt).
- `minSdk 26`, `targetSdk 35`. No orientation lock; `configChanges` handled.
- Room for the library cache, DataStore for connection settings, Coil 3 for art
  with a disk cache, kotlinx-serialization for the few JSON bits.
- Coroutines + Flow throughout; a single `MpdConnection` actor per socket.
- No WebView, no JS engine, no image loading over HTTP.

### 5.2 MPD client layer

```
MpdClient        socket → line protocol, one in-flight command queue
  ├ MpdCommand   write + read-until-OK/ACK with per-command timeout
  ├ MpdIdle      1 long-lived connection parked in `idle player playlist options database`
  └ MpdState     Flow<PlayerState>, Flow<Queue>
```

Rules that come straight from the measurements:

- **Read by terminator, never substring.** Read until buffer ends `OK\n`, or
  stop at `ACK`. Art payloads contain arbitrary bytes.
- **Two sockets**: one for commands, one parked on `idle`. Re-armed after every
  event; `idle` returns subsystems, and the client issues the matching refresh
  (`status`, `playlistinfo` on `playlist`, `listallinfo` on `database`).
- **Refetch the queue, don't diff it.** `plchanges <version>` reports additions
  and moves but never removals, so a queue view built on diffs keeps deleted
  rows forever. The queue is 12–200 rows and `playlistinfo` costs ~0.1 s; refetch
  on version change and keep the local list only for optimistic reorder.
- **Timeouts, differentiated:** state commands 3 s; `listallinfo` 20 s; art 15 s
  per chunk; `idle` no timeout (server holds it). On timeout the client marks the
  relevant capability `Unavailable` and stops retrying that class for 30 s.
- **Reconnect**: exponential backoff 0.5 → 16 s, jittered, paused while the app
  is in the background. State is reconciled with a full `status` on reconnect.
- **Optimistic transport**: play/pause/next flip local state immediately, then
  reconcile on the next `status`. MPD's own echo arrives within ~20 ms on this
  LAN, so a wrong guess is corrected before it's noticed.
- **No volume code path.** The command set exposed to the UI has no `setvol`.

### 5.3 Library cache

- First run (or `database` idle event): `listallinfo` → parsed into SQLite.
  865 KB and ~0.1 s, so a full replace is cheap; no incremental diffing needed.
- Tables: `track(uri PK, album_id, disc, track_no, title, artist, album_artist,
  album, year, genre, duration, format, bit_depth, sample_rate, path)` and
  `album(id PK, title, artist, year, track_count, disc_count)`.
- **Album identity** (settled in review, after the library was inspected):
  - group by normalised (album artist, album); empty `AlbumArtist` falls back to
    `Artist`; folder name is never the key.
  - collapse **exact duplicates** within a group — same (disc, track, title,
    duration) — keeping the first by folder order. The library has real
    duplicates: *Antichrist Superstar* appears twice (album folder + discography
    folder) giving 198 rows for a 99-track album, and *The Beautiful People*
    spans two singles.
  - split **non-stereo mixes** into their own edition inside the album
    (*The Raven…* has Stereo / 5.1 / Binaural, and 6 of its tracks are
    `96000:24:6`). Multichannel rows are shown with a `5.1` chip and are excluded
    from "play all" — this DAC is a stereo path and they will fail to open.
  - box sets that legitimately span folders (*Mellon Collie*, 5 dirs; *RAM*,
    2 discs) merge naturally under the tag key, which is why folder-splitting is
    the wrong rule.
  - `album_id` = hash of the normalised tag key + edition. It is the Room key,
    the art filename and the `currentsong` lookup, so it must not change later.
- Browse and search run **locally** against SQLite (FTS4 on title/artist/album).
  No `search` round-trips for typing; `search` is only used for "find on server".
- The cache survives disconnection. A cached library plus an offline state is
  strictly better than an empty screen.

### 5.4 Cover art

- Fetched per album, lazily, when the album is first rendered; stored under
  `files/art/<album_id>.jpg`; downscaled to 512×512 on decode before writing.
- Raise `binarylimit 4000000` once per connection, then fetch with
  `albumart "<first track uri>" 0` — one request per cover (~70 ms for a 300 KB
  JPEG). Without raising the limit MPD caps each reply at 8 KB and a cover costs
  37–158 round trips.
- "No art" is `ACK [50@0] {albumart} No file exists`, **not** a bare `OK` (bare
  `OK` is what `readpicture` returns for a file with no embedded picture, and
  `albumart` is the only command that also finds a cover sitting next to the
  tracks). Cache the negative answer per album for 7 days, keyed off ACK 50.
- The same ACK 50 arrives when the SMB mount is stale rather than the album
  being artless, so a negative is only cached after a *successful* `status`
  round-trip in the same session.
- Bounded: 15 s per album, 2 concurrent fetches, cancelled when the row scrolls
  off. A stale SMB mount must cost one placeholder, never a frozen list.
- Placeholder: the album's initials set in the app's typography on a muted card,
  so a missing cover looks deliberate rather than broken. (SACD rips are the
  likely offenders — art extraction on `.dsf` timed out repeatedly in probing.)

### 5.5 Layout system

Spatial thesis (set in the layout pass, measured against the old frame):

- **The cover leads.** 420 dp, 53.8 % of canvas height, bottom edge shared with the
  column so the record and its label read as one object.
- **The queue stops borrowing column width.** The old 200 dp rail was 15 %
  occupied and cost the cover 160 dp; the queue moves to a foot band, where it
  stays in peripheral vision without competing for the spine.
- **Nothing moves between states.** The column is bottom-anchored and the
  transport is centred under a full-width progress bar, so a two-line title grows
  upward and the play button keeps one position (the old frame drifted 64 dp).
- **Spacing scale:** 8 · 12 · 16 · 24 · 40 · 64 · 96. Every gap inside the frame
  comes from it; the 56 dp inset and 112 dp top margin are the device constants.

One `NowPlaying` composable, three arrangements by window width:

| Width | Arrangement |
|---|---|
| `< 600 dp` (phone) | Browse-first. Library list/grid, mini player pinned above the tab bar; tapping it opens a full-height sheet inset 12 dp (so the library shows behind it), a 44 dp chevron to close, the signal strip, three-control transport and an **Up next · N** row. **No meter** — the phone is the control surface; the tablet is the instrument. |
| `600–840 dp` (tablet in split-screen, phone landscape) | **Stacked spine.** Cover fills the width at 400 dp, type steps down one (44 display), strip/progress/transport in one column, band keeps queue + meter. Same components as the tablet, one column instead of three. |
| `≥ 840 dp` (tablet) | **One spine, one band.** 420 dp cover on the left; a 584 dp column (title, artist, position, signal strip, full-width progress, centred three-control transport) whose bottom edge is pinned to the cover's; a 132 dp band across the foot holding *up next* at the start and the level meter at the end. Blurred art wash behind. |

Components shared across widths so there is one behaviour, not three:

- **Signal strip** — three cells, one fact each: `FLAC 24/96` → `USB · DX5 II`
  → verdict. The output rate is *not* shown on the happy path, because it equals
  the source rate by construction at this build; it appears in the Link cell
  only when it differs (`44.1 → 48 kHz`) and the verdict turns. Built from
  `status` (`Format: 96000:24:2`, or `dsd64:2` → `DSD64 · DoP`) compared against
  the track's own format from the library cache.
  - The verdict is **computed, not decorative**: `bit-perfect` only when the
    status format matches the file's format *and* `replay_gain_mode` is `off`
    *and* the output is the ALSA `hw:0,0` output with no mixer.
  - Otherwise `transcoded` (rate/format differ), `lossy` (source is mp3/aac/ogg),
    `5.1 → stereo path` for the six-channel rows, or `unknown` while the status
    is still settling. Stopped state shows the last known chain, dimmed.
  - Because this build has no resampler, `transcoded` should be unreachable —
    which is the point: if it ever appears, something changed on the server and
    the strip is the thing that tells you.
- **Progress hairline** — thin, draggable, with elapsed/remaining either side.
  Scrub sends `seekcur` on release, not on every drag event.
- **Queue** — `plchanges <version>` diffs applied to a local list; drag to
  reorder sends `moveid`; swipe removes via `deleteid`. Queue rows show disc/
  track number, title, duration, and a now-playing marker.
- **Transport** — prev / play-pause / next, plus shuffle (random) and repeat in
  the rail header. `single` and `consume` live in the overflow, not the main row.

### 5.6 The VU meter

**Data source.** A precomputed loudness envelope per track, generated off-device.

- **Generator:** `tools/vu-scan/` — a Python 3 + ffmpeg tool that walks the
  library **on the machine that holds it**: `/home/andrew/1tb/home/andrew/Music`
  on this workstation (ffmpeg 9.0.2 present, 667 GB volume, same tree the router
  mounts over CIFS), decodes
  each file to mono PCM, computes RMS and peak per 200 ms window, and writes one
  `loudness.bin` for the whole library. DSD is decoded through ffmpeg's DSF
  demuxer; anything it cannot read is skipped and logged.
- **Size:** 264 h at 5 frames/s × 3 bytes/frame ≈ **10 MB**. Measured library,
  not an estimate of a hypothetical one.
- **Delivery:** the app imports `loudness.bin` through the system file picker
  (Settings → Loudness data). No server changes, no HTTP host, no rebuild to
  refresh. A small manifest inside the file records the library's
  `db_update` timestamp so the app can say "envelope is from an older library"
  rather than silently mismatching.

**File format** (little-endian):

```
magic     4B  "VU01"
version   u16
db_update u64   (MPD stats db_update at scan time)
count     u32   (tracks)
per track:
  uri_len u16, uri (UTF-8, MPD path as in listallinfo)
  frames  u32
  frames × { u8 rms_l, u8 rms_r, u8 peak }   (dBFS mapped -60..0 → 0..255)
```

**Rendering.** The meter is a stereo pair of bars in the tablet rail (above
*Up next*) and in the phone's now-playing sheet.

- The meter must land on what is *audible*, which lags MPD's `elapsed` by the
  ALSA buffer plus USB and DAC latency. So: `index = (elapsed − L) × 5`, floored
  at 0. (An earlier draft added the offset — wrong direction: that runs the
  meter ahead of the sound.)
- `L` defaults to **120 ms**, is calibrated once per output with a one-shot
  measurement at first play (compare the local monotonic clock at `play` with the
  first `status` showing non-zero `elapsed`), and is adjustable ±500 ms with a
  2 dp slider under Output → "Meter sync", next to the loudness-data row. DSD/DoP
  buffers are longer than PCM, so the value is stored per output *and* per
  format class (PCM / DoP).
- Target accuracy: within ~80 ms of the DAC's own display. Anything worse reads
  as fake, which is the one failure this feature exists to avoid.
- The meter's header carries the live value (`−14 dBFS`) rather than a bare
  label, and the row is suppressed to `—` whenever the level is unknown.
- Ballistics: attack 30 ms, release 350 ms, so it moves like a needle rather
  than a bar graph.
- dBFS mapped to bar height with a −60 dB floor; peaks shown as a 1 px cap that
  decays slower.
- **Honest fallback:** if a track has no envelope entry, the meter renders a
  subdued "no level data" state — a still baseline with a small label — not a
  fake animation. The decorative mode exists only as an explicit toggle
  ("Ambient motion") in settings for people who want the room lit up.

### 5.6b States, drawn and specified

The surface has twelve states, drawn in `mockups/states.html` (review artifact, not shipped):

| State | Trigger | What changes |
|---|---|---|
| Playing | `state: play` | Baseline |
| Paused | `state: pause` | Play glyph; progress fill 45 %; meter flat and grey; status dot idle |
| Unreachable | two consecutive state timeouts | Device name replaced by `Streamer unreachable` + red dot; art at 55 %; transport 30 % and `aria-disabled`; strip prints `—`; `Retrying in Ns` + **Retry now** |
| No level data | URI absent from the envelope | Meter box kept at full size, reads `No level data` / `Not in the loudness scan`; never animates |
| Stale envelope | `db_update` newer than the import | Same box, reads `Loudness data is older than the library` |
| No artwork | `ACK 50` from `albumart` | Plate on `#17171B` with a 152 sp monogram and a `No artwork` label at the 11 sp label tier, no wash, no spinner, negative cached 7 days |
| Long title | > 28 chars | 2-line clamp, 64→48 sp, `overflow-wrap: anywhere`; artist 1 line + ellipsis |
| Connecting | first launch / reconnect | Skeletons plus the host being dialled |
| Empty library | `stats` reports 0 songs | "Library is empty" naming the SMB mount as the likely cause, plus Retry |
| Empty queue | `playlistlength: 0` | "Nothing queued", mini player stays if audio is playing |
| No search results | local FTS empty | Echoes the query, names what is searchable, offers Clear |
| Sheet, no level data | phone, envelope miss | Fixed-height meter row so the sheet does not jump between tracks |

Behaviour the pictures cannot show, fixed here:

- **Timeouts:** state 3 s, `listallinfo` 20 s, art 15 s, `idle` unbounded. Two consecutive state failures flip to Unreachable; the UI always renders last-known state with a stale marker rather than waiting on the socket.
- **Reconnect:** 0.5 s → 16 s with ±20 % jitter, reset on success; **Retry now** cancels the wait; no attempts while backgrounded with no service.
- **Seek gestures:** one pointer owns a drag; `pointercancel`, `lostpointercapture`, window blur, or a second pointer abandons it, restores the last confirmed position and sends nothing.
- **Transport double-tap:** send once, ignore repeats for 250 ms or until `status` returns.
- **i18n:** all containers flex (+35 % width budget), logical properties throughout, digits and durations through locale formatting.
- **Accessibility:** 48 dp targets, accessible names on every transport control, `aria-live="polite"` on state text, meter exposes dB values, disabled transport is `aria-disabled` rather than hidden.

### 5.7 Screens

| Screen | Contents |
|---|---|
| Now playing | Art, title/artist, signal strip, progress, transport, meter, queue rail (tablet); expands from mini player (phone) |
| Library | Segmented: Albums / Artists / Tracks / Folders. Album grid with art, artist list, track list, folder tree straight from the MPD URI layout |
| Album | Art, track list, "play all", "queue all", format of the album as a group |
| Search | Local FTS, grouped results (albums, tracks), instant |
| Queue | Full-screen on phone, the rail's expanded form on tablet; reorder, remove, save as playlist |
| Playlists | `listplaylists`, load appends to queue, `save` from queue, `rm` |
| Output | The single output, DoP flag, "bit-perfect" explanation, connection settings, loudness data import |

Navigation: `NavigationBar` on phones, no chrome at all on expanded tablets
beyond a top bar that fades with inactivity (a now-playing surface shouldn't
wear a menu). Back from library returns to now playing, not out of the app.

### 5.8 Theming

- Dark only, at least for v1. Black `#0A0A0C` base, warm white type, one accent.
- **Type: Inter variable, bundled** (`res/font`, ~250 KB TTF subset). Pinned
  300 / 400 / 500 — no 600, no 700; emphasis is size and colour, not weight.
  Chosen over Manrope (axis floor is 200, so the hairline cannot be tuned) and
  Space Grotesk (cannot draw below 300 at all); the system stack was measured
  rendering 200, 250, 300 and 350 at identical widths, so the requested 200 was
  never real. Proven in `mockups/typeset.html`.
- **Seven sizes, not twelve:** 64 display / 44 display-compact / 28 title /
  20 heading · artist / 16 row · body · data / 13 meta · numeric / 11 label.
  Leading rises as size falls: 1.06 → 1.12 → 1.18 → 1.30 → 1.45.
- Labels are 11 sp at **weight 500 and +0.12 em tracking** — the old 400 at
  +0.20–0.22 em disintegrated on a dark surface. `text-transform: uppercase`
  is a no-op in Arabic, which is why the label tier must not depend on it.
- Numeric roles (times, sample rate, dB, counters) carry **tabular figures**;
  a readout that jitters as digits change forfeits the instrument idea.
- Long titles use the 44 px step, triggered by **measured width**, not a
  character count; `overflow-wrap: break-word`, never `anywhere`.
- RTL drops tracking and uppercase for joined scripts, and isolates the
  countdown LTR so the minus leads its digits. An Arabic companion (Noto Sans
  Arabic) is the one font asset still to add.
- 8 dp spacing grid; art radii at 5 dp (A's gallery feel) with no shadows
  beyond a single large soft drop under the cover.
- Blurred backdrop = the same bitmap, 40 dp blur, 50 % opacity, under a radial
  gradient scrim. Pre-blur once per album, cache the result; never blur on every
  frame.

### 5.9 Notifications and background

- A `MediaSession` + `MediaStyle` notification (art, title, artist, transport)
  so the phone's lockscreen and quick settings can drive it. The session is
  bound to the remote state, not to a local `ExoPlayer`.
- **Background policy — hybrid (chosen).** The tablet is an appliance while it
  is playing and an app otherwise:
  - `playing` → foreground service (`mediaPlayback`), keep-screen-on while
    charging, reconnect with infinite backoff, notification live.
  - `paused` / `stopped` → stay connected for 60 s (so a quick pause-and-resume
    doesn't tear the socket down), then release the idle socket, stop the
    service and let the screen time out normally.
  - Settings toggle **"Stay awake while charging"** under Output: when on, the
    service stays up and the screen stays lit while a charger is attached,
    regardless of playback state. Off by default.
  - App backgrounded with no service → reconnect is suspended, not retried;
    state is refreshed on next foreground. This resolves the earlier
    contradiction between "pause backoff when backgrounded" and "keep state
    fresh with the screen off": the service, not the app, owns the connection.
  - A notification always exists while the service runs; dismissing it stops the
    service (and the keep-awake), which is the honest way out of appliance mode.
- Stretch, not v1: a Glance home-screen widget (art + transport + meter) for the
  tablet stand.

---

## 6. Constraints the build must respect

- **No volume control anywhere.** Not disabled, not greyed — absent. A one-line
  note in Output: "Volume is the DAC's knob; this streamer has no mixer."
- **Never block the audio path.** Nothing in the app changes server config. The
  app is a client and stays one.
- **Art and tags can hang.** Every file-touching call is bounded; UI shows
  cached data and an "unreachable" chip meanwhile.
- **No auth.** Host + port is the entire connection config. Don't build a login.
- **DSD labels** are `DSD64 · DoP` — never "native DSD".
- **No resampling exists.** An unsupported rate fails on the server; the app
  must show MPD's error text, not invent a papered-over retry.
- Library path layout is `Album Dir/NN - Track.flac` with no reliable artist
  folders, and some albums have an empty `AlbumArtist` — browsing by folder is a
  first-class view, not a fallback.

---

## 7. What's left to the builder

- Exact type ramp (sizes, line heights) and the final accent hue.
- Icon set (Material Symbols vs hand-drawn hairlines) and transport glyph weight.
- Meter geometry: bar width, gap, rail placement, and whether the peak cap is
  drawn at all.
- Motion: sheet transitions, art crossfade on track change, rail slide-in.
- The empty and error states' copy.
- Whether `single`/`consume` get their own controls or live in the overflow.
- Test harness shape: a scriptable fake MPD server (Python, ~150 lines) that the
  UI tests and manual demos run against, so development doesn't need the router.
- Whether the envelope import needs a progress UI for 10 MB.
- Naming (currently "Plinth" as a placeholder).

---

## 8. Build order

1. **M1 — socket and state.** Connect, `status`/`currentsong`/`outputs`, transport
   controls, idle loop, reconnect. Verified against the real device.
2. **M2 — library.** `listallinfo` → SQLite; album/artist/track/folder browsing;
   local search; queue add/remove/reorder.
3. **M3 — art.** Lazy fetch, chunk loop, disk cache, placeholders, timeouts.
4. **M4 — the surface.** Tablet layout A, phone layout, signal strip, progress,
   rail, theming.
5. **M5 — meter.** Generator tool, format, import, ballistics, fallback state.
6. **M6 — polish.** MediaSession notification, playlists, offline states,
   final motion and type.

Each milestone ends on the real device at `10.0.0.219`, not a mock.

---

## 9. How we'll know it worked

- Album grid scrolls at 60 fps with 135 albums of art, on the tablet.
- Cold app start → now playing with art in under 1.5 s on the LAN.
- Pulling the SMB share or the network leaves a usable app with a visible
  offline chip; nothing freezes.
- The meter's bars rise when the music is loud and fall in quiet passages, at
  the right moment in the track, in a blind check against the DAC's display.
- The phone and tablet can be open at once, and transport changes land on both
  within ~100 ms.
