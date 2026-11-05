# Plinth — project status

What is built, what is left, and the traps worth knowing before you touch the
code. Read this before changing anything; the design lives in
`docs/superpowers/specs/2026-11-04-mpd-tablet-controller-design.md` and the
task-by-task plan in `docs/superpowers/plans/2026-11-04-mpd-controller.md`
(19 tasks across M1–M4).

Plinth is a personal Android controller for an OpenWrt/MPD streamer. It replaces
M.A.L.P. and the myMPD web page: a tablet on a stand shows what is playing, and a
phone does the browsing. Aesthetic references are FiiO Music, PowerAmp and
Volumio. `README.md` covers building, testing and installing.

## Status

- **M1 (MPD protocol), M2 (library and art) and M3 (surfaces) are complete. M4 is
  half done: the loudness envelope and the meter are built (tasks 16–17), the
  background policy exists without the service that consumes it (task 18), and the
  failure states are not started (task 19).**
- 93 unit tests pass, and 15 instrumented tests pass on a tablet
  (`NowPlayingExpandedTest` 2, `PhoneSurfacesTest` 3, `LibrarySurfacesTest` 7,
  `VuMeterTest` 3). Nine Python tests cover the scanner.
- The meter is verified reading a real envelope on the tablet. The one thing not
  verified is the bars moving: that needs playback, which is started by ear, not by
  a test.
- The debug APK runs on both the reference tablet and phone.
- **`feat/task-15-screens` is merged into `master`** (fast-forward, 2026-11-05).
  Master now holds the library screens; the branch can be deleted.
- No known bugs. Two album-handling defects were found and fixed; see
  "Closed bugs" below for the evidence, because both were subtle.

## Build and install

The Gradle wrapper is committed, so no local Gradle install is needed. Export
`JAVA_HOME` and `ANDROID_HOME` first — `tools/setup-android.sh` prints the ones
it installs under `~/android-dev`.

```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:assembleDebug --console=plain

adb -s TABLET_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Replace `TABLET_SERIAL` and `PHONE_SERIAL` with the serials `adb devices` prints.

The instrumented tests need `ANDROID_SERIAL` set to a connected device:

```bash
export ANDROID_SERIAL=TABLET_SERIAL
./gradlew :app:connectedDebugAndroidTest
```

## The reference setup

| What | Where |
|---|---|
| Streamer | `10.0.0.219` — MPD `6600` (no auth), myMPD `8080`, LuCI `80` |
| Tablet | `10.0.0.220:41219` — TB520FU, 1840 px at 340 dpi, 866 dp wide, so the Expanded layout |
| Phone | `10.0.0.213:44921` — 1220×2712 at 450 dpi, about 434 dp, so the Compact layout |

SSH to the streamer is refused, so everything goes through MPD on port 6600. Both
`adb` targets are wireless: run `adb connect` first if `adb devices` is empty.

Transport has only ever been exercised against the fake MPD server in the test
sources. Real playback is verified by ear, by whoever is listening — starting
audio on the streamer to test a button is not part of the loop.

## Closed bugs

### Album detail merged three unrelated records

Three folders in this library — *Symphonic Suite AKIRA*, *The Pale Emperor DSD*
and *Breakfast In America SACD* — carry no tags at all: `lsinfo` returns only
`file`, `Last-Modified`, `Time` and `duration`. With `Album`, `AlbumArtist`,
`Artist` and `Track` all absent, all 33 rows keyed on blank plus blank and became
a single album with a blank header, and every row rendered as track `00`.

The parser now keys a blank `Album` on the folder name, so each folder is its own
album. Verified from a cleared database on the tablet: 139 albums, three separate
tiles titled by folder, album detail showing 10, 13 and 10 rows.

The instructive part: the fix was committed, the symptom did not change, and the
fix was not at fault — the APK under test had been assembled two minutes *before*
the commit. The database said so outright, because the merged bucket's id was
`1489f923c4dca729`, which is `sha1("" + NUL + "" + NUL)` truncated — the pre-fix
blank key. Compare the commit time, the APK's modification time and
`dumpsys package dev.andrew.plinth | grep lastUpdateTime` before you reopen a
"fix that did not work".

Tests pinning it: `LibraryParserTest.untagged folders are separate albums keyed on
the folder name` and `LibraryRepositoryTest.untagged folders stay separate all the
way into the album table`, both against `listallinfo_untagged.txt`, the 33 real
blocks copied off the streamer.

### `dedupe()` deleted tracks that belonged to other albums

`AlbumIdentity.dedupe()` keyed on `(disc, track, title, duration)` across the whole
library, so a song that legitimately appears on two albums was dropped from the
second one. The damage was invisible until you counted: *The Pale Emperor* held 3
of its 13 tracks, *Hand. Cannot. Erase.* 17 of 25, *Sweet Dreams* 3 of 4, *Lest We
Forget* 17 of 18, *Live* 6 of 7, and *The Dripping Tap* lost its only track, so
its album row vanished.

`albumId` is now part of the dedupe key. Duplicate copies of one rip share an
album id and still collapse — the Manson fixture still goes from 198 rows to 99.
The cache went from 1,844 tracks and 136 albums to 1,866 and 139, which is also
why the old "136 in the grid, 137 by identity" note is resolved: the missing album
was being emptied, not merged.

Two near-identical *Pale Emperor* tiles remain (13 tracks each). The discography
rip and the 24-bit rip carry different `Album` tags, so by the design's rules they
are different albums. That is a tagging problem in the library, not something
dedupe should paper over.

## What is left

### Gaps in the shipped screens

- The tablet's top bar is a static row; the design asks for one that fades with
  inactivity (spec §5.7).
- Output has no meter-sync sliders yet. They belong with the meter (Task 17), not
  before it — a slider that moves nothing is a dead control.
- Search is a prefix `LIKE` query, not FTS. At 1,866 rows the scan is instant;
  albums are matched from the cached list.

### M4 — meter, service, offline states (plan tasks 16–19)

- **Loudness envelope — done.** `tools/vu-scan/vu_scan.py` writes the file
  (1,837 tracks, 7.7 MB for this library); import it under Output → Loudness data.
  It keys records by MPD's URI, so the 187 tracks whose database paths are stale
  SMB 8.3 names have no level — see `tools/vu-scan/README.md`.
- **Meter — done.** Two bars with a live dBFS header in the band, 30 ms attack and
  350 ms release, per-format offset adjustable under Output. Medium has it too; the
  phone does not.
- **Playback service — policy only.** `BackgroundPolicy` decides when the app should
  hold a connection (playing, a one-minute grace after pause, or charging with the
  stay-awake setting). The `mediaPlayback` service, its MediaStyle notification and
  the keep-screen-on flag are not written: a foreground service that misses its
  `startForeground` call crashes on launch, and there was no device on `adb` while
  that task was in scope.
- **Failure states — not started.** The eleven states in `mockups/states.tpl.html`
  are designed but not implemented.

## Pitfalls

- **`LaunchedEffect` keys must include late-bound inputs.** `Artwork` keyed on
  `(albumId, uri)` only, so on a cold start it ran while the art repository was
  still null and never retried; the large cover stayed blank forever while the
  mini player showed the same art. Put every nullable input in the key, or hoist
  the load into the state holder.
- **No regex on a hot path.** `MpdParser` parsed `ACK` replies with a `Regex` that
  Android's ICU engine rejects with `PatternSyntaxException` at class
  initialization. Every command then failed silently while all JVM tests passed.
  `ACK` parsing is hand-rolled now; any new regex needs a check on a device.
- **Espresso 3.6.1 crashes on Android 17.** `InputManager.getInstance` was
  removed. The project pins 3.7.0, with androidx.test 1.3.0 and test-core 1.7.0.
  Do not downgrade.
- **MPD's `plchanges` never reports removals**, so the queue is refetched
  wholesale whenever `queueVersion` changes. `moveid` takes a song id, not a
  position.
- **`setvol` fails** with `ACK [5@0] {setvol} No mixer`. There is no mixer on this
  streamer, which is why the app has no volume control at all.
- **`albumart` is one request per cover.** Raise `binarylimit` once per connection
  rather than paging at 8 KB. `ACK [50@0]` means "no art" and is cached as a
  negative for seven days; any other error is not evidence of anything and must
  not be cached.
- **A frame loop in a composable hangs every instrumented test.** `withFrameNanos`
  keeps Compose permanently busy, so the test rule times out on idling with
  `ComposeNotIdleException` — a failure that looks like an infinite recomposition
  because it is one. Use `withInfiniteAnimationFrameNanos` for animation loops; the
  test clock ignores it.
- **A latency offset makes position negative at the start of a track.** Subtracting
  120 ms from `elapsed = 0` and rejecting negative positions turned "paused at 0:00"
  into "no level data". The lookup floors at the first frame; only a position past
  the end is unknown.
- **A missing tag must not become a printed value.** Untagged rows carry
  `trackNo = 0` and a blank artist; render nothing, or omit the separator, rather
  than printing `00` and a dangling `·`.
- **Take a screenshot.** Several bugs here — the blank cover on a cold start, the
  wrong layout at 731 dp, a title clipped mid-word, a status bar drawn over the top
  bar — were found by looking at the device and none by the test suite:

  ```bash
  adb -s TABLET_SERIAL exec-out screencap -p > /tmp/screen.png
  ```

- **A test that passes before the fix exists proves nothing.** The two album tests
  were run against the pre-fix parser first and failed; only then did the fix go
  in.

## Numbers worth keeping

1,965 rows from `listallinfo` · 1,866 tracks cached · 139 albums, matching the 139
album identities · 42 album artists, blank ones excluded · 91 folders ·
`listallinfo` is 865 KB, 90 ms over the socket and 809 ms to parse cold · a cover
is 301,900 bytes at 1280² and about 60 ms warm · the loudness envelope is
7,703,779 bytes for 1,837 tracks and covers 1,679 of the 1,866 cached tracks.
