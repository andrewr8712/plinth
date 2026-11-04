# Plinth — handoff

A personal Android controller for the OpenWrt/MPD streamer at `10.0.0.219`, replacing M.A.L.P. and the myMPD web page. Two surfaces: a tablet on a stand as the now-playing instrument, and a phone for browsing. Aesthetic references are FiiO Music, PowerAmp and Volumio.

**Read this first, then `docs/superpowers/specs/2026-11-04-mpd-tablet-controller-design.md` (canonical design) and `docs/superpowers/plans/2026-11-04-mpd-controller.md` (19 tasks, M1–M6).** Progress notes, rulings and deviations live in `.superpowers/sdd/2026-11-04-mpd-controller/progress.md` — read that third, it records why things are the way they are.

## State at handoff

- **M1 (MPD protocol) complete. M2 (library, art) complete. M3 (surfaces) complete except Task 15's remaining screens. M4–M6 not started.**
- `71` unit tests green. `5` instrumented tests green, run on real devices: `NowPlayingExpandedTest` (2), `PhoneSurfacesTest` (3).
- Debug APK installs and runs on both the tablet and the phone. Last known-good commit is the tip of `main`.
- **One real bug is open** — see "Open bug" below. Nothing else is known broken.

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

## Open bug — album detail merges two unrelated albums

**Symptom.** Opening one album in the library shows 33 tracks from *two* records, with a blank header. Rows 01–10 are a Guilty Gear soundtrack (`KANEDA`, `BATTLE AGAINST CLOWN`, … `REQUIEM`); rows 11+ are Marilyn Manson's *The Pale Emperor (Deluxe Edition)* (`Killing Strangers`, `Deep Six`, …).

**It is not stale cache.** It survived `adb shell pm clear dev.andrew.plinth` followed by a genuine first-run sync (grid rebuilt to 136 albums, same merge). So it reproduces from a clean database.

**What MPD actually reports** for the Manson track, so the data is fine on the server:

```
file: Marilyn Manson  - Discography 1990-2020 (FLAC) 88/2015 - The Pale Emperor (Deluxe Edition)/01 -Killing Strangers.flac
Album: The Pale Emperor (Deluxe Edition)
AlbumArtist: Marilyn Manson
```

The Guilty Gear tracks carry **no `Album` tag** (that is why the header is blank).

**Already tried, did not fix it.** `LibraryParser.kt:42` now falls back to the folder name when `Album` is blank:

```kotlin
val album = t("Album")?.ifBlank { null } ?: folderName(uri)
```

That is committed. Note it deliberately relaxes the spec's "folder name is never the key" rule; the rule exists so box sets merge on their tags, and a folder with no tags has nothing else to identify it.

**Narrowed hypotheses — check these in order:**

1. `LibraryRepository.albumTracks(albumId)` may not be filtering on `albumId` at all (it returned 33 rows, not the whole 1,965 — so it filters on *something*).
2. The album row's `id` (from the album table, used by the route `album/{id}`) and `track.albumId` may be computed by two different paths that disagree — `AlbumIdentity.key(...)` in the parser versus whatever `replaceAll` writes into the album table.
3. `AlbumIdentity.dedupe()` may reassign or share an id across groups.
4. `AlbumIdentity.key(albumArtist, artist, album, channels)` may be ignoring `album` in some branch (e.g. a blank-album early return).

**Fastest way in:** write a failing unit test in `AlbumIdentityTest` with a two-block fixture — one track with blank `Album`, one with a real `Album` — asserting the two keys differ. Then a second test at the `LibraryRepository` level asserting `albumTracks(idA)` does not return the `idB` track. `app/src/test/resources/` already holds real `listallinfo` blocks copied from the device (`listallinfo_{manson_dupes,mellon_collie,raven,sacd}.txt`) and `LibraryParserTest` shows the fixture pattern. The bug will fall out at one of those two levels.

## What is left

**Task 15 (M3), remaining screens.** Data layer is done and tested for all of these:
- Artist list — `LibraryRepository.artists()`
- Folder view — `folders()`
- Search — `search()` (SQL `LIKE` prefix, not FTS; 1,965 rows scan instantly, accepted)
- Queue screen — `QueueRepository`, plus the `queue` route already navigates
- Output / settings screen — `OutputInfo`, `SettingsStore`
- **Cover art on grid tiles** — `LibraryScreen.AlbumTile` draws the album's initial today. `Artwork` already takes `art: ArtRepository?` and caches to disk; this is a swap of the placeholder box for an `Artwork`.

**M4 (meter, service, polish) — not started.**
- VU meter: precomputed 5 Hz stereo RMS envelope (~14 MB), `index = (elapsed − L)`, `L` default 120 ms PCM / 240 ms DoP, offset slider ±500 ms. Tablet and Medium only, never on the phone sheet. `QueueBand` reserves the 152 dp band and currently draws the meter at rest.
- Foreground service (`mediaPlayback`) while playing, keep-screen-on while charging, infinite reconnect backoff, 60 s grace after pause/stop. Chosen as "hybrid" by the user.
- The eleven failure states in `mockups/states.tpl.html` are designed but not implemented.

## Pitfalls that cost real time — do not rediscover these

- **No regex on the hot path.** `MpdParser` originally parsed ACK replies with a `Regex`; Android's ICU rejects the pattern and threw `PatternSyntaxException` at class-init, so *every* command failed silently while all JVM tests passed. ACK parsing is hand-rolled now. Any new regex needs an on-device check.
- **Espresso 3.6.1 crashes on Android 17** (`InputManager.getInstance` removed). Pinned to 3.7.0 with androidx.test 1.3.0 / test-core 1.7.0. Do not downgrade.
- **`LaunchedEffect` keys must include the repository.** `Artwork` keyed on `(albumId, uri)` only, so on a cold start it ran while `art` was still null and never retried — the big cover stayed blank forever while the mini player showed the same art.
- **MPD `plchanges` never reports removals**, so the queue is refetched wholesale on `queueVersion` change. `moveid` takes a song id, not a position (the plan was wrong about this).
- **`setvol` fails** — `ACK [5@0] {setvol} No mixer`. There is no mixer; there is no volume control anywhere in the UI, by design.
- **`albumart` is one request** with `binarylimit` raised once per connection, not 8 KB chunks. `ACK [50@0]` means "no art" and is negatively cached for 7 days; anything else is an error.
- **Never write to the streamer during probing** — no `play`/`stop`/`setvol`/`toggleoutput`. Transport is exercised against `FakeMpdServer` only; real playback was never started by the agent, and the plan of record is that the user verifies playback while listening.
- **`write_file` refuses to overwrite files it has not read** in the current turn. Read first, or use `patch`.
- Tablet screenshots: `adb -s 10.0.0.220:41219 exec-out screencap -p > /tmp/x.png`, then look at them. Four bugs in this project were found by looking at a screenshot and none by the test suite.

## Numbers worth keeping

1,965 tracks · 136 albums in the grid (137 by identity — one collapses in `replaceAll`, unexplained) · 43 album artists · `listallinfo` 865 KB / 90 ms raw / 809 ms cold parse · cover 301,900 B at 1280², ~60 ms warm.
