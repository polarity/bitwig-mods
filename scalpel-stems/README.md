# Audio Clip: Scalpel Stem Separation

This Patchbay mod adds local stem separation to the arranger audio clip menu. It
uses HTDemucs for songs and Scalpel's MDX23C DrumSep workflow for individual drum
elements. Install **Helper: Audio Analysis** and **Framework: Actions and Menus**
with the mod. The helper's Audio Analysis setup is independent of Scalpel Stem Separation.

## Use

Right-click one arranger audio clip that plays one file.

| Action | Tracks created |
| --- | --- |
| **Separate Song Stems** | Vocals, Bass, Music, Drums |
| **Separate Song Stems + Drum Elements** | Vocals, Bass, Music, Kick, Snare, Hi-Hat, Toms, Ride, Crash, Drum Remainder |
| **Separate Drum Elements** | Kick, Snare, Hi-Hat, Toms, Ride, Crash, Drum Remainder from the source file |

The new tracks are added below the source track at the clip's start. One Undo
removes the import. Mute the original track to hear just the separation. The
drum tracks sum back to the drum input, including Drum Remainder, which is the
difference between that input and the six model outputs. It can contain
percussion along with other unassigned audio.

The mod separates the whole source file from its beginning. Bounce trimmed,
stretched or edited clips in Bitwig when you want the separation to match how
the clip plays. Use **File > Collect and Save** to make the generated WAVs part
of the project. The mod keeps generated WAVs in its Patchbay user data folder,
where they remain available across Bitwig sessions.

## First use

Choose any action to open **Set Up Scalpel Stem Separation…**. The confirmation shows the
downloads needed for that action and their destination. OK installs portable
Python 3.12, PyTorch, FFmpeg and the required model packages in the
per-user mod folder. Windows may also need the
[Microsoft Visual C++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe).

Song actions require the 84 MB HTDemucs checkpoint. Both drum actions require
the approximately 418 MiB DrumSep checkpoint. The worker checks the pinned
model files before loading them. After setup, separation runs locally offline.
The notification center displays progress and has a Cancel button. Failures
offer access to the helper's log folder. The mod writes
`scalpel-stems-*.log` there.

## Optional GPU acceleration

The CPU is the default. To enable GPU acceleration before or after your first
split, right-click an arranger audio clip and choose **Enable GPU Acceleration…**.
Run a separation action once setup completes.

| System | Song stems (HTDemucs) | Drum elements (MDX23C) |
| --- | --- | --- |
| Windows or Linux x64 with a compatible NVIDIA GPU and driver | CUDA | CUDA |
| Mac with Apple Silicon and PyTorch Metal support | MPS | CPU |
| Intel Mac or other hardware | CPU | CPU |

CUDA setup asks you to choose a folder. Select a drive with room for the
download and unpacking. A fresh Windows install downloads 2.75 GB and needs
about 7.2 GB of free space during setup. The mod makes its own CUDA package
subfolder inside your selection and remembers it. Python, model files and
cached stems stay in the Patchbay user data folder.

On Apple Silicon the standard PyTorch build already includes MPS. If the CUDA
drive is unavailable or GPU inference fails, separation uses the CPU. Choose
**Disable GPU Acceleration…** to switch to CPU, then enable it again to select
another CUDA folder.

## Compatibility

This tester build works with the Actions and Menus framework 0.8.0-dev.1 from
Patchbay 0.36.0-dev.16. Its reference build is Bitwig Studio 6.1.4 on Windows x64.
The 6.1.7 test targets are Windows x64, macOS 12+ on Apple Silicon and Intel,
and Linux x64. See the
[release guide](../docs/releasing.md#scalpel-stem-separation) for validation status.

The Bitwig integration reuses the
[Audio Analysis helper](https://github.com/shaboogen-dev/Mods/tree/main/audio-analysis-runtime)
by kalifrn, based on Bitwig Superpowers. The drum adapter follows Polarity's
Scalpel plugin. [OpenMIRLab's MIT-licensed inference code](https://github.com/openmirlab/mdxnet-infer)
loads the separate DrumSep checkpoint by aufr33 and jarredou. The mod ZIP ships
no model weights. Setup fetches them from their pinned upstream hosts.
