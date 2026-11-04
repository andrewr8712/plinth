# Plinth — handoff

A personal Android controller for the OpenWrt/MPD streamer at `10.0.0.219`, replacing M.A.L.P. and the myMPD web page. Two surfaces: a tablet on a stand as the now-playing instrument, and a phone for browsing. Aesthetic references are FiiO Music, PowerAmp and Volumio.

**Read this first, then `docs/superpowers/specs/2026-11-04-mpd-tablet-controller-design.md` (canonical design) and `docs/superpowers/plans/2026-11-04-mpd-controller.md` (19 tasks, M1–M6).** Progress notes, rulings and deviations live in `.superpowers/sdd/2026-11-04-mpd-controller/progress.md` — read that third, it records why things are the way they are.

## State at handoff

- **M1 (MPD protocol) complete. M2 (library, art) complete. M3 (surfaces) complete — Task 15's screens landed this session. M4 (meter, service, polish — plan tasks 16–19) not started.**
- `78` unit tests green. `12` instrumented tests green, run on a real tablet: `NowPlayingExpandedTest` (2), `PhoneSurfacesTest` (3), `LibrarySurfacesTest` (7).
- Debug APK installs and runs on both the tablet and the phone. **Task 15 was built on branch `feat/task-15-screens` (off `build/phase-1`) and is not merged** — `master` is still the state described by the commit before it.
- The album-merge bug and a second, larger dedupe bug are closed and verified on the tablet from a cleared database — see "Closed bugs" below. Nothing else is known broken.

## Build and install

```bash
export JAVA_HOME=$HOME/android-dev/jdk-21
export ANDROID_HOME=$HOME/android-dev/sdk
cd /home/andrew/projects/mpd-tablet-controller

$HOME/android-dev/gradle-8.13/bin/gradle :app:testDebugUnitTest --console=plain
$HOME/android-dev/gradle-8.13/bin/gradle :app:assembleDebug --console=plain

adb -s 10.0.0.220:41219 install -r app/build/outputs/apk/debug/app-debug.apk   # tablet
adb -s 10.0.0.213:44921 install -r app/build/outputs/apk/debug/app-debug.apk   # phone
```

Instrumented tests need `ANDROID_SERIAL` set to a connected device. Toolchain is self-contained under `~/android-dev` (JDK 21, SDK 35/36, Gradle 8.13) — `tools/setup-android.sh` rebuilds it from nothing.

## Devices and network

| What | Where |
|---|---|
| Streamer | `10.0.0.219` — MPD `6600` (no auth), myMPD `8080`, LuCI `80` |
| Tablet | `10.0.0.220:41219` — TB520FU, 1840 px @ 340 dpi → Expanded |
| Phone | `10.0.0.213:44921` — 1220×2712 @ 450 dpi → Compact (~434 dp) |

SSH to the streamer is refused (publickey and password), so everything goes through MPD on 6600. Both `adb` targets are wireless; `adb connect` first if `adb devices` is empty.

## Closed bugs

### 1. Album detail merged unrelated records (the one that was open)

The three DFF folders in this library — *Symphonic Suite AKIRA*, *The Pale Emperor DSD*, *Breakfast In America SACD* — carry **no tags at all**: `lsinfo` returns only `file`, `Last-Modified`, `Time`, `duration`. With `Album`, `AlbumArtist`, `Artist` and `Track` all absent, all 33 rows keyed on blank + blank and became one album with a blank header (which is also why every row read `00`).

**Root cause of "the fix did not work": the fix was never on the device.** `LibraryParser` keys a blank `Album` on the folder name (commit `54b15dd`, 00:58:15), but the APK under test was assembled at 00:56:40 and installed at 00:57:58 — both *before* the commit. The database proved which code wrote it: the merged bucket's id was `1489f923c4dca729` = `sha1("" + NUL + "" + NUL)[0..16]`, the pre-fix blank key. `pm clear` + resync could not have helped.

Now verified from a cleared database on the tablet: 139 albums, three separate tiles (folder-name titles), album detail shows 10 / 13 / 10 rows, header reads "10 tracks" with no leading separator, no fabricated track numbers.

Tests pinning it: `LibraryParserTest.untagged folders are separate albums keyed on the folder name` and `LibraryRepositoryTest.untagged folders stay separate all the way into the album table`, both against `listallinfo_untagged.txt` (the 33 real blocks copied off the streamer, not invented).

### 2. `dedupe()` deleted tracks that belonged to other albums

Found while chasing the old "136 albums in the grid, 137 by identity" note — it was not an id collapse. `AlbumIdentity.dedupe()` keyed on `(disc, track, title, duration)` **globally**, so a song that legitimately appears on two albums was dropped from the second one. Live damage: *The Pale Emperor* held 3 of 13 tracks, *Hand. Cannot. Erase.* 17 of 25, *Sweet Dreams* 3 of 4, *Lest We Forget* 17 of 18, *Live* 6 of 7, and *The Dripping Tap* lost its only track so its album row vanished entirely.

`albumId` is now in the dedupe key. Duplicate copies of one rip share an album id, so they still collapse (Manson fixture: 198 rows → 99, unchanged). Library: **1,844 tracks / 136 albums → 1,866 / 139**, and the grid count now equals the identity count, which is what the old 137-vs-136 note was pointing at.

Note this leaves two near-identical *Pale Emperor* tiles (13 + 13): the discography rip and the 24-bit rip carry different `Album` tags, so by the spec's rules they are different albums. That is a tagging problem in the library, not an app bug — dedupe is not the place to fix it.

## What is left

**Task 15 is done** — the library's four tabs, cover art on tiles, album detail with play/queue all, search, queue with reorder/remove/save/load, and output/settings all exist and are verified on the tablet. What it does *not* have, recorded rather than forgotten:
- The tablet's top bar does not fade with inactivity (spec §5.7 wants it to). It is a static row.
- No meter-sync sliders on Output — they land with the meter (Task 17), not before.
- Search is prefix `LIKE`, not FTS (ruled in Task 7); albums are matched from the cached list.

**M4 (meter, service, polish) — not started.** Plan tasks 16–19:
- VU meter: precomputed 5 Hz stereo RMS envelope (~14 MB), `index = (elapsed − L)`, `L` default 120 ms PCM / 240 ms DoP, offset slider ±500 ms. Tablet and Medium only, never on the phone sheet. `QueueBand` reserves the 152 dp band and currently draws the meter at rest.
- Foreground service (`mediaPlayback`) while playing, keep-screen-on while charging, infinite reconnect backoff, 60 s grace after pause/stop. Chosen as "hybrid" by the user.
- The eleven failure states in `mockups/states.tpl.html` are designed but not implemented.

## Pitfalls that cost real time — do not rediscover these

- **Prove the APK you tested was built from the commit you think you fixed it with.** The album-merge fix was declared "committed and did not work" for a day; it had simply never been compiled. Compare `git log -1 --format=%ci <commit>`, the APK's mtime and `dumpsys package dev.andrew.plinth | grep lastUpdateTime`. Better: pull the app's own database (`adb exec-out run-as dev.andrew.plinth cat databases/library.db > /tmp/library.db`, plus `-wal`) and read what the running code actually produced — the merged bucket's id matched `sha1("\0\0")` byte for byte, which ended the hypothesis hunt immediately.
- **No regex on the hot path.** `MpdParser` originally parsed ACK replies with a `Regex`; Android's ICU rejects the pattern and threw `PatternSyntaxException` at class-init, so *every* command failed silently while all JVM tests passed. ACK parsing is hand-rolled now. Any new regex needs an on-device check.
- **Espresso 3.6.1 crashes on Android 17** (`InputManager.getInstance` removed). Pinned to 3.7.0 with androidx.test 1.3.0 / test-core 1.7.0. Do not downgrade.
- **`LaunchedEffect` keys must include the repository.** `Artwork` keyed on `(albumId, uri)` only, so on a cold start it ran while `art` was still null and never retried — the big cover stayed blank forever while the mini player showed the same art.
- **MPD `plchanges` never reports removals**, so the queue is refetched wholesale on `queueVersion` change. `moveid` takes a song id, not a position (the plan was wrong about this).
- **`setvol` fails** — `ACK [5@0] {setvol} No mixer`. There is no mixer; there is no volume control anywhere in the UI, by design.
- **`albumart` is one request** with `binarylimit` raised once per connection, not 8 KB chunks. `ACK [50@0]` means "no art" and is negatively cached for 7 days; anything else is an error.
- **Never write to the streamer during probing** — no `play`/`stop`/`setvol`/`toggleoutput`. Transport is exercised against `FakeMpdServer` only; real playback was never started by the agent, and the plan of record is that the user verifies playback while listening.
- **`write_file` refuses to overwrite files it has not read** in the current turn. Read first, or use `patch`.
- **A missing tag must not become a printed value.** Untagged rows have `trackNo = 0` and a blank artist; render nothing (or omit the separator) rather than `00` and a dangling `·`.
- Tablet screenshots: `adb -s 10.0.0.220:41219 exec-out screencap -p > /tmp/x.png`, then look at them. Five bugs in this project were found by looking at a screenshot and none by the test suite.

## Numbers worth keeping

1,965 rows returned by `listallinfo` · 1,866 tracks cached · **139 albums in the grid = 139 by identity** · 42 album artists (blank ones excluded) · 91 folders · `listallinfo` 865 KB / 90 ms raw / 809 ms cold parse · cover 301,900 B at 1280², ~60 ms warm.
