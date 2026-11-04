"""Tests for the loudness scanner. Standard library only, so no install step:

    python3 -m unittest discover -s tools/vu-scan -p 'test_*.py'

pytest collects the same classes if you prefer it.
"""

import math
import struct
import unittest
import wave
from pathlib import Path
from tempfile import TemporaryDirectory

import vu_scan


def write_sine(path: Path, seconds: float, dbfs: float, rate: int = 44100) -> None:
    """A mono sine at a known level, written as 16-bit PCM WAV."""
    amplitude = (10 ** (dbfs / 20.0)) * 32767
    total = int(rate * seconds)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        frames = bytearray()
        for i in range(total):
            value = int(amplitude * math.sin(2 * math.pi * 440 * i / rate))
            frames += struct.pack("<h", max(-32768, min(32767, value)))
        wav.writeframes(bytes(frames))


class ScanTest(unittest.TestCase):

    def test_header_and_size_are_what_the_app_reads(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "music"
            (root / "Album").mkdir(parents=True)
            write_sine(root / "Album" / "01 - loud.wav", 3.0, 0.0)
            out = Path(tmp) / "loudness.bin"

            count = vu_scan.scan(root, out, rate=5, quiet=True)

            self.assertEqual(1, count)
            data = out.read_bytes()
            self.assertEqual(b"VU01", data[:4])
            version, db_update, tracks = struct.unpack_from("<HQL", data, 4)
            self.assertEqual(1, version)
            self.assertEqual(0, db_update)
            self.assertEqual(1, tracks)
            # 3 s at 5 Hz = 15 frames, 3 bytes each, plus the uri record
            uri_len = struct.unpack_from("<H", data, 18)[0]
            uri = data[20:20 + uri_len].decode()
            self.assertEqual("Album/01 - loud.wav", uri)
            frames = struct.unpack_from("<I", data, 20 + uri_len)[0]
            self.assertEqual(15, frames)
            self.assertEqual(20 + uri_len + 4 + 15 * 3, len(data))

    def test_loud_track_has_higher_rms_than_quiet(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "music"
            root.mkdir()
            write_sine(root / "loud.wav", 2.0, -3.0)
            write_sine(root / "quiet.wav", 2.0, -30.0)
            out = Path(tmp) / "loudness.bin"
            vu_scan.scan(root, out, quiet=True)

            _, records = vu_scan.read_envelope(out)
            loud = records["loud.wav"].frames
            quiet = records["quiet.wav"].frames
            mean_loud = sum(loud[0::3]) / len(loud[0::3])
            mean_quiet = sum(quiet[0::3]) / len(quiet[0::3])
            self.assertGreater(mean_loud, mean_quiet + 40)

    def test_silence_maps_to_the_floor_and_full_scale_to_the_top(self):
        self.assertEqual(0, vu_scan.to_byte(-60.0))
        self.assertEqual(0, vu_scan.to_byte(-90.0))
        self.assertEqual(255, vu_scan.to_byte(0.0))
        self.assertEqual(128, vu_scan.to_byte(-30.0))

    def test_missing_folder_writes_zero_tracks(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "empty"
            root.mkdir()
            out = Path(tmp) / "loudness.bin"
            self.assertEqual(0, vu_scan.scan(root, out, quiet=True))
            self.assertEqual(0, vu_scan.read_envelope(out)[1].__len__())

    def test_uri_is_relative_to_the_library_root(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "music"
            (root / "Marilyn Manson  The Pale Emperor DSD").mkdir(parents=True)
            write_sine(root / "Marilyn Manson  The Pale Emperor DSD" / "A1.dff.wav", 0.6, -6.0)
            out = Path(tmp) / "loudness.bin"
            vu_scan.scan(root, out, quiet=True)
            _, records = vu_scan.read_envelope(out)
            self.assertIn("Marilyn Manson  The Pale Emperor DSD/A1.dff.wav", records)


class FrameForTest(unittest.TestCase):

    def test_position_maps_to_a_frame(self):
        self.assertEqual(0, vu_scan.frame_for("a", 0.0, [1] * 10))
        self.assertEqual(5, vu_scan.frame_for("a", 1.0, [1] * 10))

    def test_past_the_end_is_none_not_the_last_frame(self):
        self.assertIsNone(vu_scan.frame_for("a", 9.0, [1] * 10))

    def test_empty_or_negative_is_none(self):
        self.assertIsNone(vu_scan.frame_for("a", 1.0, []))
        self.assertIsNone(vu_scan.frame_for("a", -1.0, [1] * 10))


if __name__ == "__main__":
    unittest.main()
