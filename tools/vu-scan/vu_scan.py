#!/usr/bin/env python3
"""Build a loudness envelope for the whole library, for Plinth's VU meter.

The streamer has a 2x Cortex-A9 and no ffmpeg, so the scan runs here, on the
machine that holds the files, and the app imports the result.

Format (little endian), one record per track keyed by its MPD URI:

    magic     4B  b"VU01"
    version   u16 1
    db_update u64 MPD stats db_update at scan time (0 when unknown)
    count     u32 number of records
    per record:
      uri_len u16, uri (UTF-8, the path as MPD reports it)
      frames  u32
      frames x { u8 rms_l, u8 rms_r, u8 peak }

Each frame covers 1/rate seconds. Levels are dBFS mapped from -60..0 onto
0..255, so the file is 3 bytes per frame and about 14 MB for 264 hours at 5 Hz.
"""

from __future__ import annotations

import argparse
import math
import os
import struct
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

MAGIC = b"VU01"
VERSION = 1
SAMPLE_RATE = 8_000
CHANNELS = 2
BYTES_PER_SAMPLE = 2
EXTENSIONS = {".flac", ".dsf", ".dff", ".mp3", ".ogg", ".m4a", ".wav", ".aiff", ".aif", ".opus"}
FLOOR_DB = -60.0


def to_byte(dbfs: float) -> int:
    """Map -60..0 dBFS onto 0..255, clipping anything below the floor."""
    value = (dbfs - FLOOR_DB) / -FLOOR_DB * 255.0
    return max(0, min(255, int(round(value))))


def frame_bytes(rate: int) -> int:
    return int(SAMPLE_RATE / rate) * CHANNELS * BYTES_PER_SAMPLE


def rms_dbfs(samples: Sequence[int]) -> float:
    if not samples:
        return FLOOR_DB
    energy = sum(s * s for s in samples) / len(samples)
    if energy <= 0:
        return FLOOR_DB
    return max(FLOOR_DB, 20.0 * math.log10(math.sqrt(energy) / 32768.0))


def peak_dbfs(samples: Sequence[int]) -> float:
    if not samples:
        return FLOOR_DB
    peak = max(abs(s) for s in samples)
    if peak <= 0:
        return FLOOR_DB
    return max(FLOOR_DB, 20.0 * math.log10(peak / 32768.0))


def decode_frames(path: Path, rate: int = 5, ffmpeg: str = "ffmpeg"):
    """Yield (rms_l_db, rms_r_db, peak_db) for each window of the file."""
    span = frame_bytes(rate)
    cmd = [
        ffmpeg, "-v", "error", "-nostdin", "-i", str(path),
        "-f", "s16le", "-ac", str(CHANNELS), "-ar", str(SAMPLE_RATE), "pipe:1",
    ]
    with subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as proc:
        stdout = proc.stdout
        if stdout is None:
            raise RuntimeError(f"could not read ffmpeg output for {path}")
        pending = b""
        while True:
            chunk = stdout.read(span)
            if not chunk:
                break
            pending = chunk if not pending else pending + chunk
            while len(pending) >= span:
                window, pending = pending[:span], pending[span:]
                samples = struct.unpack(f"<{span // 2}h", window)
                left = samples[0::2]
                right = samples[1::2]
                yield (rms_dbfs(left), rms_dbfs(right), peak_dbfs(samples))
        if pending:
            samples = struct.unpack(f"<{len(pending) // 2}h", pending[: len(pending) // 2 * 2])
            yield (rms_dbfs(samples[0::2]), rms_dbfs(samples[1::2]), peak_dbfs(samples))
        if proc.wait() != 0:
            raise RuntimeError(f"ffmpeg failed on {path}")


def header_problem(path: Path) -> str | None:
    """A reason to skip a file before handing it to ffmpeg, or None to try it.

    Reference libraries contain half-copied downloads whose first bytes are nulls;
    ffmpeg's "Cannot determine format of input after EOF" says nothing a person can
    act on, so name the actual defect here.
    """
    try:
        with open(path, "rb") as handle:
            head = handle.read(12)
    except OSError as exc:
        return f"unreadable: {exc}"
    if not head:
        return "empty file"
    if head.count(0) == len(head):
        return "starts with null bytes, so the header is damaged (a truncated copy)"
    return None


def iter_files(root: Path):
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.suffix.lower() in EXTENSIONS:
            yield path


def mpd_uri(root: Path, path: Path) -> str:
    """The path as MPD reports it: relative to the library root, '/'-separated."""
    return path.relative_to(root).as_posix()


def scan(root: Path, out_path: Path, rate: int = 5, jobs: int = 4, ffmpeg: str = "ffmpeg",
         db_update: int = 0, quiet: bool = False) -> int:
    """Scan every file under `root`; write the envelope to `out_path`; return the count."""
    root = Path(root)
    out_path = Path(out_path)
    files = list(iter_files(root))
    if not quiet:
        print(f"{len(files)} files under {root}", file=sys.stderr)

    records: list[tuple[str, bytes]] = []
    skipped: list[str] = []
    lock = __import__("threading").Lock()

    def work(path: Path):
        uri = mpd_uri(root, path)
        problem = header_problem(path)
        if problem is not None:
            with lock:
                skipped.append(f"{uri}: {problem}")
            return
        try:
            frames = bytearray()
            for rms_l, rms_r, peak in decode_frames(path, rate, ffmpeg):
                frames += bytes((to_byte(rms_l), to_byte(rms_r), to_byte(peak)))
            if not frames:
                raise RuntimeError("no audio decoded")
            with lock:
                records.append((uri, bytes(frames)))
            if not quiet:
                with lock:
                    done = len(records) + len(skipped)
                if done % 100 == 0:
                    print(f"  {done}/{len(files)}", file=sys.stderr)
        except Exception as exc:  # one bad file must not stop the scan
            with lock:
                skipped.append(f"{uri}: {exc}")

    with ThreadPoolExecutor(max_workers=max(1, jobs)) as pool:
        list(pool.map(work, files))

    records.sort(key=lambda item: item[0])
    spool = tempfile.NamedTemporaryFile(delete=False, dir=str(out_path.parent or Path(".")))
    try:
        with spool:
            for uri, frames in records:
                raw = uri.encode("utf-8")
                count = len(frames) // 3
                spool.write(struct.pack("<H", len(raw)) + raw + struct.pack("<I", count) + frames)
        with open(out_path, "wb") as out:
            out.write(MAGIC)
            out.write(struct.pack("<HQL", VERSION, db_update, len(records)))
            with open(spool.name, "rb") as body:
                while True:
                    chunk = body.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
    finally:
        os.unlink(spool.name)

    if not quiet:
        print(f"wrote {out_path} ({out_path.stat().st_size} B), {len(records)} tracks, "
              f"{len(skipped)} skipped", file=sys.stderr)
        for line in skipped[:20]:
            print(f"  skipped {line}", file=sys.stderr)
    return len(records)


@dataclass
class Record:
    uri: str
    frames: bytes

    def level(self, frame: int) -> tuple[int, int, int]:
        offset = frame * 3
        return (self.frames[offset], self.frames[offset + 1], self.frames[offset + 2])


def read_envelope(path: Path) -> tuple[int, dict[str, Record]]:
    """Read an envelope file back: (db_update, records by URI). Used by tests and tools."""
    data = Path(path).read_bytes()
    if data[:4] != MAGIC:
        raise ValueError("not a Plinth loudness file")
    version, db_update, count = struct.unpack_from("<HQL", data, 4)
    if version != VERSION:
        raise ValueError(f"unsupported version {version}")
    pos = 4 + 2 + 8 + 4
    records: dict[str, Record] = {}
    for _ in range(count):
        (uri_len,) = struct.unpack_from("<H", data, pos)
        pos += 2
        uri = data[pos:pos + uri_len].decode("utf-8")
        pos += uri_len
        (frames,) = struct.unpack_from("<I", data, pos)
        pos += 4
        records[uri] = Record(uri, data[pos:pos + frames * 3])
        pos += frames * 3
    return db_update, records


def frame_for(uri: str, elapsed: float, frames: list[int]) -> int | None:
    """Index into a track's frame list for a playback position, or None if outside it."""
    if not frames or elapsed < 0:
        return None
    index = int(elapsed * 5)
    if index >= len(frames):
        return None
    return index


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build Plinth's loudness envelope.")
    parser.add_argument("--root", required=True, type=Path, help="library root on this machine")
    parser.add_argument("--out", required=True, type=Path, help="output loudness.bin")
    parser.add_argument("--rate", type=int, default=5, help="frames per second (default 5)")
    parser.add_argument("--jobs", type=int, default=4, help="parallel ffmpeg processes")
    parser.add_argument("--db-update", type=int, default=0,
                        help="MPD stats db_update at scan time, for staleness detection")
    parser.add_argument("--ffmpeg", default="ffmpeg", help="ffmpeg binary")
    args = parser.parse_args(argv)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    count = scan(args.root, args.out, args.rate, args.jobs, args.ffmpeg, args.db_update)
    return 0 if count else 1


if __name__ == "__main__":
    raise SystemExit(main())
