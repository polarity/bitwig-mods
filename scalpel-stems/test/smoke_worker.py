"""Exercise the shipped worker using an already provisioned runtime."""

from __future__ import annotations

import argparse
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly


def fixture(rate: int, song: bool):
    time = np.arange(2 * rate) / rate
    pulse = np.mod(time, 0.25)
    rng = np.random.default_rng(8)
    kick = 0.6 * np.sin(2 * np.pi * 70 * pulse) * np.exp(-45 * pulse)
    snare = 0.15 * rng.uniform(-1, 1, size=len(time)) * np.exp(-60 * np.mod(time + 0.125, 0.25))
    drums = kick + snare
    if not song:
        return drums.astype(np.float32)
    bass = 0.16 * np.sin(2 * np.pi * 110 * time)
    music = 0.07 * (np.sin(2 * np.pi * 440 * time) + np.sin(2 * np.pi * 550 * time))
    return np.column_stack((drums + bass + music, drums + bass - music)).astype(np.float32)


def verify_drums(files: list[Path], expected) -> None:
    tracks = []
    for file in files:
        audio, rate = sf.read(file, dtype="float32", always_2d=True)
        assert rate == 44100 and audio.shape == expected.shape, file
        assert sf.info(file).subtype == "FLOAT", file
        assert np.isfinite(audio).all(), file
        tracks.append(audio)
    np.testing.assert_allclose(sum(tracks), expected, rtol=0, atol=2e-7)


def smoke(engine: Path, runtime: Path, directory: Path, accelerator: str,
          song_device: str, drum_device: str) -> None:
    cache = directory / "cached audio"
    result = directory / "tracks.txt"
    drum_labels = ["Kick", "Snare", "Hi-Hat", "Toms", "Ride", "Crash", "Drum Remainder"]
    song_labels = ["Vocals", "Bass", "Music", "Drums"]

    def split(mode, source, labels, expected_runs=()):
        run = subprocess.run([sys.executable, "-P", str(engine), "--runtime", str(runtime),
                              "split", mode, str(source), str(cache), str(result),
                              "--accelerator", accelerator], stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        print(run.stdout, end="", flush=True)
        run.check_returncode()
        runs = [line for line in run.stdout.splitlines() if line.startswith("Scalpel Stems: ")]
        assert runs == [f"Scalpel Stems: {kind} on {device} (requested {accelerator})"
                        for kind, device in expected_runs], runs
        paths = [Path(line) for line in result.read_text(encoding="utf-8").splitlines()]
        assert [path.stem for path in paths] == labels, paths
        assert all(path.is_file() and path.is_relative_to(cache) for path in paths)
        return paths

    song = directory / "Song ü.wav"
    sf.write(song, fixture(44100, song=True), 44100, subtype="FLOAT")
    song_files = split("song", song, song_labels, (("song", song_device),))
    for file in song_files:
        assert (sf.info(file).samplerate, sf.info(file).frames, sf.info(file).channels) == (44100, 88200, 2)
    print("SONG_STEMS_OK", flush=True)

    combined_files = split("song-drums", song, song_labels[:-1] + drum_labels, (("drums", drum_device),))
    assert combined_files[:3] == song_files[:3]
    drum_input, _ = sf.read(song_files[-1], dtype="float32", always_2d=True)
    verify_drums(combined_files[3:], drum_input)
    print("SONG_DRUMS_RECONSTRUCTION_OK", flush=True)

    drum_loop = directory / "Loop 48 kHz.wav"
    sf.write(drum_loop, fixture(48000, song=False), 48000, subtype="FLOAT")
    drum_files = split("drums", drum_loop, drum_labels, (("drums", drum_device),))
    mono, _ = sf.read(drum_loop, dtype="float32", always_2d=True)
    expected = np.repeat(resample_poly(mono, 147, 160, axis=0), 2, axis=1)
    verify_drums(drum_files, expected)
    print("DRUM_ONLY_RECONSTRUCTION_OK", flush=True)

    renamed = directory / "Renamed loop.wav"
    shutil.copyfile(drum_loop, renamed)
    assert split("drums", renamed, drum_labels) == drum_files
    assert split("song-drums", song, song_labels[:-1] + drum_labels) == combined_files
    print("CONTENT_CACHE_REUSE_OK", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True, help="Setup folder containing models/")
    parser.add_argument("--engine", type=Path, required=True, help="Path to the packaged scalpel_stems.py")
    parser.add_argument("--accelerator", choices=("cpu", "cuda", "mps"), default="cpu")
    parser.add_argument("--song-device", choices=("cpu", "cuda", "mps"), default="cpu")
    parser.add_argument("--drum-device", choices=("cpu", "cuda"), default="cpu")
    args = parser.parse_args()
    with TemporaryDirectory(prefix="scalpel-stems-smoke-") as scratch:
        smoke(args.engine.resolve(), args.runtime.resolve(), Path(scratch).resolve(),
              args.accelerator, args.song_device, args.drum_device)
