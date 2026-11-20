"""Scalpel Stems worker. All model and package downloads happen in setup."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from uuid import uuid4


RATE = 44100
SONG = ("Vocals", "Bass", "Music", "Drums")
DRUMS = ("Kick", "Snare", "Hi-Hat", "Toms", "Ride", "Crash", "Drum Remainder")
DRUM_HEADS = ("kick", "snare", "hh", "toms", "ride", "crash")
ENGINE = Path(__file__).resolve().parent
MODELS = {
    "song": ("955717e8.safetensors",),
    "drums": (
        "aufr33-jarredou_DrumSep_model_mdx23c_ep_141_sdr_10.8059.ckpt",
        "aufr33-jarredou_DrumSep_model_mdx23c_ep_141_sdr_10.8059.yaml",
    ),
}
MODES = {
    "song": SONG,
    "song-drums": SONG[:-1] + DRUMS,
    "drums": DRUMS,
}


def digest(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def assets() -> dict[str, str]:
    return {
        name: sha256
        for line in (ENGINE / "assets.tsv").read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#")
        for name, sha256, _, _ in [line.split()]
    }


def model_paths(runtime: Path, kind: str) -> list[Path]:
    paths = [runtime / "models" / name for name in MODELS[kind]]
    hashes = assets()
    for path in paths:
        if not path.is_file() or digest(path) != hashes[path.name]:
            (runtime / f"{kind}.ready").unlink(missing_ok=True)
            raise ValueError(f"{path.name} is missing or damaged. Run the same action to repair setup.")
    return paths


def ffmpeg() -> str:
    import imageio_ffmpeg

    exe = Path(imageio_ffmpeg.get_ffmpeg_exe()).resolve()
    if exe.parent != Path(imageio_ffmpeg.__file__).resolve().parent / "binaries":
        raise ValueError("The packaged FFmpeg executable is missing from the Scalpel Stems runtime")
    return str(exe)


def decode(source: Path, rate: int):
    import numpy as np

    process = subprocess.run(
        [ffmpeg(), "-v", "error", "-nostdin", "-protocol_whitelist", "file,pipe", "-i", str(source), "-vn",
         "-ac", "2", "-ar", str(rate), "-f", "f32le", "-"],
        stdin=subprocess.DEVNULL, capture_output=True, check=False,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    if process.returncode:
        raise ValueError(f"Could not decode {source.name}: {process.stderr.decode('utf-8', 'replace').strip()}")
    audio = np.frombuffer(process.stdout, dtype="<f4").reshape(-1, 2)
    if not len(audio) or not np.isfinite(audio).all():
        raise ValueError("The audio file is empty or contains invalid samples")
    return audio


def load_song(runtime: Path):
    from demucs.apply import BagOfModels
    from demucs.hf import load_safetensors_model

    model, = model_paths(runtime, "song")
    return BagOfModels([load_safetensors_model(model)]).eval()


def separate_song(source: Path, out: Path, runtime: Path, device: str) -> None:
    import numpy as np
    import torch
    from demucs.apply import apply_model
    from audio_dna_engine.separate import report_segments, write_wav

    model = load_song(runtime)
    # Audio Analysis's Demucs inference with a verified local checkpoint and bundled decoder.
    wave = torch.from_numpy(np.ascontiguousarray(decode(source, model.samplerate).T))
    reference = wave.mean(0)
    mean = reference.mean()
    std = reference.std(unbiased=False) + 1e-8
    report_segments(model, tracks=1, shifts=1)
    with torch.no_grad():
        result = apply_model(model, ((wave - mean) / std)[None], shifts=1, split=True,
                             overlap=0.25, progress=True, device=device)[0].cpu()
        result = result * std + mean
    for label, head in zip(SONG, ("vocals", "bass", "other", "drums"), strict=True):
        samples = result[model.sources.index(head)].numpy()
        if samples.shape != tuple(wave.shape) or not np.isfinite(samples).all():
            raise ValueError(f"Demucs returned invalid audio for {label}")
        write_wav(out / f"{label}.wav", samples, model.samplerate)


def prepare_drums(source: Path):
    """Scalpel's WAV input path preserves gain and duplicates mono at unity."""
    import math

    import numpy as np
    import soundfile as sf
    from scipy.signal import resample_poly

    try:
        audio, rate = sf.read(source, dtype="float32", always_2d=True)
    except sf.LibsndfileError:
        return decode(source, RATE)
    if not len(audio) or audio.shape[1] not in (1, 2) or not np.isfinite(audio).all():
        raise ValueError("Expected non-empty mono or stereo audio with finite samples")
    if rate != RATE:
        divisor = math.gcd(rate, RATE)
        audio = resample_poly(audio, RATE // divisor, rate // divisor, axis=0)
    return np.repeat(audio, 2, axis=1) if audio.shape[1] == 1 else audio


def load_drums(runtime: Path, device: str = "cpu"):
    from mdxnet_infer import MDX23CInference

    checkpoint, config = model_paths(runtime, "drums")
    return MDX23CInference(model_path=checkpoint, config_path=config, device=device,
                           model_name="drumsep-6stem")


def drum_elements(audio, prediction):
    """Scalpel's six heads and the difference between their sum and the drum input."""
    import numpy as np

    if set(prediction) != set(DRUM_HEADS):
        raise ValueError(f"Unexpected MDX23C output heads: {sorted(prediction)}")
    elements = []
    for head in DRUM_HEADS:
        data = np.asarray(prediction[head], dtype=np.float32)
        if data.ndim != 2 or data.shape[1] != 2 or not np.isfinite(data).all():
            raise ValueError(f"MDX23C returned invalid audio for {head}")
        delta = len(audio) - len(data)
        if abs(delta) > 512:
            raise ValueError(f"MDX23C output {head} differs in length by {delta} samples")
        elements.append(np.pad(data[:len(audio)], ((0, max(0, delta)), (0, 0))))
    return elements + [audio - sum(elements)]


def separate_drums(source: Path, out: Path, runtime: Path, device: str) -> None:
    import soundfile as sf
    from audio_dna_engine import progress
    from mdxnet_infer import inference
    from tqdm import tqdm

    class DrumProgress(tqdm):
        def update(self, count=1):
            super().update(count)
            if self.total:
                progress.report(0.12 + 0.83 * self.n / self.total)

    audio = prepare_drums(source)
    engine = load_drums(runtime, device)
    progress.report(0.12)
    # This is a single-purpose worker process. Restore the adapter's progress callback afterwards.
    original_tqdm = inference.tqdm
    inference.tqdm = DrumProgress
    try:
        prediction = engine.separate(audio, sample_rate=RATE, batch_size=1)
    finally:
        inference.tqdm = original_tqdm
    for label, data in zip(DRUMS, drum_elements(audio, prediction), strict=True):
        sf.write(out / f"{label}.wav", data, RATE, subtype="FLOAT")


def fingerprint(path: Path) -> tuple[int, int, int]:
    stat = path.stat()
    return stat.st_size, stat.st_mtime_ns, stat.st_ino


def cached(cache: Path, marker: Path, labels: tuple[str, ...]) -> list[Path] | None:
    try:
        entry = json.loads(marker.read_text(encoding="utf-8"))
        generation = entry["generation"]
        if len(generation) != 32 or any(char not in "0123456789abcdef" for char in generation):
            return None
        files = [cache / generation / f"{label}.wav" for label in labels]
        if any(file.stat().st_size != entry["sizes"][file.name] or file.stat().st_size <= 44
               for file in files):
            return None
        return files
    except (OSError, TypeError, ValueError, KeyError):
        return None


def device_for(kind: str, accelerator: str) -> str:
    import torch

    if accelerator == "cuda" and torch.cuda.is_available():
        return "cuda"
    if accelerator == "mps" and kind == "song" and torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def stage(kind: str, source: Path, cache: Path, runtime: Path, work: Path, accelerator: str) -> list[Path]:
    from audio_dna_engine import progress

    initial = fingerprint(source)
    content = digest(source)
    hashes = assets()
    requirements = digest(ENGINE / f"requirements-{kind}.txt")
    engine_version = digest(ENGINE / "scalpel_stems.py")
    key = hashlib.sha256("|".join(
        (kind, content, requirements, engine_version, *(hashes[name] for name in MODELS[kind]))
    ).encode()).hexdigest()
    labels, separate = (SONG, separate_song) if kind == "song" else (DRUMS, separate_drums)
    marker = cache / f"{key}.json"
    hit = cached(cache, marker, labels)
    if hit is not None:
        if fingerprint(source) != initial:
            raise ValueError("The source file changed during separation")
        progress.report(1.0)
        return hit

    generation = uuid4().hex
    with TemporaryDirectory(dir=work) as folder:
        scratch = Path(folder)
        audio = scratch / "audio"
        audio.mkdir()
        device = device_for(kind, accelerator)
        print(f"Scalpel Stems: {kind} on {device} (requested {accelerator})", flush=True)
        separate(source, audio, runtime, device)
        if fingerprint(source) != initial:
            raise ValueError("The source file changed during separation")
        sizes = {f"{label}.wav": (audio / f"{label}.wav").stat().st_size for label in labels}
        if any(size <= 44 for size in sizes.values()):
            raise ValueError("The separator returned an empty WAV")
        index = scratch / "entry.json"
        index.write_text(json.dumps({"generation": generation, "sizes": sizes}), encoding="utf-8")
        audio.rename(cache / generation)
        os.replace(index, marker)
    progress.report(1.0)
    return [cache / generation / f"{label}.wav" for label in labels]


def split(mode: str, source: Path, runtime: Path, cache: Path, result: Path, accelerator: str) -> None:
    from audio_dna_engine import progress

    initial = fingerprint(source)
    cache.mkdir(parents=True, exist_ok=True)
    if mode == "song-drums":
        with progress.span(0.0, 0.45):
            song = stage("song", source, cache, runtime, result.parent, accelerator)
        with progress.span(0.45, 1.0):
            drums = stage("drums", song[-1], cache, runtime, result.parent, accelerator)
        files = song[:-1] + drums
    else:
        files = stage(mode, source, cache, runtime, result.parent, accelerator)
    if fingerprint(source) != initial:
        raise ValueError("The source file changed during separation")
    result.write_text("\n".join(str(path.resolve()) for path in files) + "\n", encoding="utf-8")


def verify(kind: str, runtime: Path) -> None:
    if kind in ("cuda", "mps"):
        import torch

        if device_for("song", kind) != kind:
            raise RuntimeError(f"PyTorch could not initialize {kind.upper()}")
        if torch.ones(1, device=kind).add_(1).cpu().item() != 2:
            raise RuntimeError(f"PyTorch returned an invalid result on {kind.upper()}")
        print(f"Scalpel Stems: verified {kind.upper()}", flush=True)
    elif kind == "base":
        import numpy
        import torch
        numpy.zeros(1)
        torch.ones(1)
        subprocess.run([ffmpeg(), "-version"], check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    elif kind == "song":
        from audio_dna_engine.separate import report_segments, write_wav
        load_song(runtime)
    else:
        import librosa
        import soundfile
        import scipy.signal
        load_drums(runtime)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", type=Path, required=True)
    command = parser.add_subparsers(dest="command", required=True)
    check = command.add_parser("verify")
    check.add_argument("kind", choices=("base", "cuda", "mps", *MODELS))
    run = command.add_parser("split")
    run.add_argument("mode", choices=MODES)
    run.add_argument("source", type=Path)
    run.add_argument("cache", type=Path)
    run.add_argument("result", type=Path)
    run.add_argument("--accelerator", choices=("cpu", "cuda", "mps"), default="cpu")
    args = parser.parse_args()

    try:
        import torch

        torch.set_num_threads(min(4, max(1, (os.cpu_count() or 1) // 2)))
        if args.command == "verify":
            verify(args.kind, args.runtime)
        else:
            split(args.mode, args.source, args.runtime, args.cache, args.result, args.accelerator)
    except Exception as error:
        print(f"Scalpel Stems error: {error}", file=sys.stderr)
        raise


if __name__ == "__main__":
    main()
