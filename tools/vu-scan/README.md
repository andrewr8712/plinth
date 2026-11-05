# vu-scan

Builds the loudness envelope that Plinth's VU meter reads.

The streamer is a 2× Cortex-A9 with no ffmpeg, so decoding 264 hours of audio there
is out of the question. This tool runs on the machine that holds the library,
decodes every file once, and writes a single `loudness.bin` that you import into
the app under **Output → Loudness data → Import**.

## Requirements

- Python 3.10 or later. No packages: the standard library only.
- `ffmpeg` on `PATH`, with decoders for FLAC, DSF/DFF and the lossy formats in the
  library.

## Run

```bash
python3 vu_scan.py \
  --root /path/to/Music \
  --out loudness.bin \
  --jobs 4
```

`--root` is the library root as MPD sees it: paths in the envelope are stored
relative to it, so `/path/to/Music/Album/01 - Track.flac` becomes
`Album/01 - Track.flac`. Get this wrong and every lookup misses.

| Flag | Meaning |
|---|---|
| `--root` | Library root on this machine. Required |
| `--out` | Output file. Required |
| `--rate` | Frames per second, default 5 |
| `--jobs` | Parallel ffmpeg processes, default 4 |
| `--db-update` | MPD's `stats` `db_update` at scan time, for staleness detection |
| `--ffmpeg` | Path to the ffmpeg binary, default `ffmpeg` |

The scan prints progress every hundred files and finishes with the file size, the
number of tracks written and the number of files skipped. A file ffmpeg cannot read
is skipped and named; it never stops the scan.

Expect a few minutes per thousand FLAC tracks and noticeably longer for DSD, which
ffmpeg decodes at 2.8 MHz before downsampling.

## Format

Little endian throughout.

```
magic     4B  "VU01"
version   u16 1
db_update u64  MPD stats db_update at scan time, 0 when unknown
count     u32  number of records
per record:
  uri_len u16, uri (UTF-8, the path MPD reports)
  frames  u32
  frames × { u8 rms_l, u8 rms_r, u8 peak }
```

Each frame covers `1/rate` seconds. Levels are dBFS mapped from −60..0 onto 0..255
with `round((db + 60) / 60 × 255)` and clipped, so 264 hours at 5 Hz is about
14 MB.

## Test

```bash
python3 -m unittest discover -s tools/vu-scan -p 'test_*.py'
```

The tests synthesise sine waves at known levels, so they prove the mapping and the
record layout rather than comparing the tool against itself. `pytest` collects the
same classes if you prefer it.

## Coverage: paths must match MPD's

Records are keyed by the URI MPD reports, so the envelope only helps a track whose
path in the database is the path on this disk. Two things break that on the
reference library, and both are visible in the import status line in the app:

- A stale database: if files were renamed after MPD indexed them, the database
  keeps the old path. The reference streamer has 187 of 1,866 tracks under SMB 8.3
  short names (`BDTO99~K/01. Getting Older.flac`) while the filesystem holds the
  long names (`Billie Eilish Happier Than Ever (Explicit) (2021) [24 Bit Hi-Res]
  FLAC [PMEDIA] /01. Getting Older.flac`). Those tracks show "No level data" until
  the streamer re-indexes them.
- Files ffmpeg cannot decode: eight tracks of one 24-bit album fail with
  `Cannot determine format of input after decode`. They are skipped and named in
  the scan output; the rest of the library is unaffected.

The app never guesses a path: a track with no record is reported as having no
level data, which is the truth.

## Rebuilding after a library change

Re-run the scan and import the new file. The envelope records MPD's `db_update` at
scan time, so the app can tell when it is older than the library; pass it with
`--db-update` (from `mpc stats`) to make that check meaningful.

