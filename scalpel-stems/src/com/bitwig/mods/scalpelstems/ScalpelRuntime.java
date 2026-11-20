package com.bitwig.mods.scalpelstems;

import static com.bitwig.mods.scalpelstems.ScalpelActions.MOD_NAME;

import bml.api.Bml;
import com.bitwig.mods.audioanalysis.AudioAnalysis;
import com.bitwig.mods.audioanalysis.Commands;
import com.bitwig.mods.audioanalysis.Download;
import com.bitwig.mods.audioanalysis.Installer;
import com.bitwig.mods.audioanalysis.Jobs;
import com.bitwig.mods.audioanalysis.Platform;
import com.bitwig.mods.audioanalysis.PythonBuild;
import com.bitwig.mods.audioanalysis.RuntimePaths;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ScalpelRuntime {
    private static final Bml.Mod MOD = Bml.mod("scalpel-stems");
    private static final String HTDEMUCS = "955717e8.safetensors";
    private static final String DRUMSEP = "aufr33-jarredou_DrumSep_model_mdx23c_ep_141_sdr_10.8059";
    private static final String MDX_SOURCE = "mdxnet-infer-source.zip";
    private static final String CPU_WHEELS = "https://download.pytorch.org/whl/cpu";
    private static final String CUDA_WHEELS = "https://download.pytorch.org/whl/cu128";
    private static final String CUDA_TORCH = "torch==2.11.0+cu128";
    private static final String CUDA_DIRECTORY = "scalpel-stems-torch-2.11.0-cu128";
    private static final String CUDA_READY = ".scalpel-stems-cuda-ready";
    private static final long WINDOWS_CUDA_SETUP_BYTES = 7_200_000_000L;
    private static final String PYPI = "https://pypi.org/simple";
    private static final Duration SETUP_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration SEPARATION_TIMEOUT = Duration.ofHours(2);

    private final Path home = MOD.resource("runtime");
    private final Path engine = MOD.resource("engine");
    private final Path cache = home.resolve("results");
    private final RuntimePaths paths = new RuntimePaths(home.resolve("v1"), engine, Platform.current());
    private final PythonBuild python = PythonBuild.of(paths.os(), Platform.currentArch());
    private final Path legacyCudaPackages = paths.home().resolve("torch-2.11.0-cu128");
    private final Path cudaLocation = paths.home().resolve("cuda.location");
    private final Path gpuEnabled = paths.home().resolve("gpu.enabled");

    List<String> setupDescription(ScalpelActions.Mode mode) {
        if (python == null) throw new IllegalStateException(MOD_NAME + " has no runtime for this computer");
        List<String> lines = new ArrayList<>();
        baseDownloads(lines);
        if (mode.song && !modelReady("song")) {
            lines.add("HTDemucs from Hugging Face (84 MB) and Demucs packages (under 10 MB)");
        }
        if (mode.drums && !modelReady("drums")) {
            lines.add("DrumSep from GitHub (418 MiB) and MDX23C packages (about 60–120 MB)");
        }
        if (!lines.isEmpty()) {
            lines.add("");
            lines.add("Install in " + paths.home());
            lines.add("Separation runs offline after setup and keeps the source file intact.");
            if (gpuSupported() && !gpuEnabled()) {
                lines.add("Optional GPU setup is available from the clip menu under Enable GPU Acceleration.");
            }
        }
        return lines;
    }

    private void baseDownloads(List<String> lines) {
        if (baseReady()) return;
        if (!Files.isRegularFile(paths.basePython())) lines.add("Portable Python 3.12 from GitHub (" + python.sizeText() + ")");
        lines.add("PyTorch, FFmpeg and Python packages (about 110–220 MB)");
    }

    private String accelerator() {
        if (python == null) return "cpu";
        if (paths.os() == Platform.Os.MACOS) {
            return Platform.currentArch() == Platform.Arch.X86_64 ? "cpu" : "mps";
        }
        return Platform.currentArch() == Platform.Arch.X86_64 ? "cuda" : "cpu";
    }

    boolean gpuSupported() {
        return !accelerator().equals("cpu");
    }

    boolean gpuEnabled() {
        return gpuSupported() && Files.isRegularFile(gpuEnabled);
    }

    boolean usesCuda() {
        return accelerator().equals("cuda");
    }

    Path cudaDialogJar() {
        return MOD.resource("cuda-dialog");
    }

    Path cudaFolder() {
        try {
            return cudaPackages().getParent();
        } catch (IOException error) {
            return paths.home();
        }
    }

    private Path cudaPackages() throws IOException {
        try {
            Path stored = Path.of(Files.readString(cudaLocation));
            if (!stored.isAbsolute() || stored.getParent() == null || (!stored.equals(legacyCudaPackages)
                && !stored.getFileName().toString().equals(CUDA_DIRECTORY))) {
                throw new IOException("The saved CUDA installation folder is invalid");
            }
            return stored;
        } catch (NoSuchFileException missing) {
            return legacyCudaPackages;
        } catch (InvalidPathException error) {
            throw new IOException("The saved CUDA installation folder is invalid", error);
        }
    }

    List<String> gpuDescription(boolean enable, Path folder) {
        if (!enable) return List.of("Use the CPU for all separations.",
            "Any downloaded GPU packages stay available if you enable acceleration again.");
        List<String> lines = new ArrayList<>();
        baseDownloads(lines);
        if (folder != null) {
            lines.add("Use an NVIDIA GPU with CUDA 12.8 for songs and drum elements.");
            lines.add("CUDA packages and temporary files: " + folder);
            lines.add(paths.os() == Platform.Os.WINDOWS
                ? "A new CUDA install downloads 2.75 GB and needs about 7.2 GB of free space."
                : "A new CUDA install downloads several GB and needs extra temporary space.");
        } else {
            lines.add("Use Apple Metal (MPS) for song stems. Drum elements run on the CPU.");
        }
        lines.add("Separation uses the CPU if GPU acceleration is unavailable or fails.");
        return lines;
    }

    private boolean baseReady() {
        return Files.isRegularFile(paths.basePython()) && Files.isRegularFile(paths.python())
            && Files.isRegularFile(marker("base"));
    }

    private boolean modelReady(String kind) {
        if (!Files.isRegularFile(marker(kind))) return false;
        if (kind.equals("song")) return Files.isRegularFile(paths.models().resolve(HTDEMUCS));
        return Files.isRegularFile(paths.models().resolve(DRUMSEP + ".ckpt"))
            && Files.isRegularFile(paths.models().resolve(DRUMSEP + ".yaml"));
    }

    private Path marker(String kind) {
        return paths.home().resolve(kind + ".ready");
    }

    List<Path> separate(Path source, ScalpelActions.Mode mode, boolean install, Jobs.Control control)
            throws IOException, InterruptedException {
        return withRuntime(control, (work, env) -> {
            if (install) setup(mode, control, work, env);
            if (!setupDescription(mode).isEmpty()) {
                throw new IllegalStateException(MOD_NAME + " needs setup. Run the same action again.");
            }
            Path output = work.resolve("tracks.txt");
            control.progress(0.0);
            control.phase(mode == ScalpelActions.Mode.DRUMS ? "Separating drum elements"
                : mode == ScalpelActions.Mode.SONG ? "Separating song stems" : "Separating song stems and drum elements");
            String device = gpuEnabled() ? accelerator() : "cpu";
            if (device.equals("mps") && !mode.song) device = "cpu";
            if (!device.equals("cpu")) {
                try {
                    Path packages = device.equals("cuda") ? cudaPackages() : null;
                    if (packages != null && !cudaInstalled(packages)) {
                        throw new IOException("The CUDA installation is unavailable at " + packages);
                    }
                    split(source, mode, control, work, acceleratedEnvironment(env, device, packages), output, device);
                } catch (IOException error) {
                    control.check();
                    control.log(device.toUpperCase(java.util.Locale.ROOT) + " separation failed: " + error.getMessage());
                    control.phase("GPU separation failed. Retrying on the CPU");
                    control.progress(0.0);
                    split(source, mode, control, work, env, output, "cpu");
                }
            } else {
                split(source, mode, control, work, env, output, "cpu");
            }
            control.check();
            return tracks(output, mode);
        });
    }

    private void split(Path source, ScalpelActions.Mode mode, Jobs.Control control, Path work,
                       Map<String, String> env, Path output, String device) throws IOException, InterruptedException {
        worker(control, env, work, device.equals("cpu") ? "separate" : "separate-" + device, SEPARATION_TIMEOUT,
               "split", mode.id, source.toAbsolutePath().toString(), cache.toString(), output.toString(),
               "--accelerator", device);
    }

    void configureGpu(boolean enable, Path folder, Jobs.Control control) throws IOException, InterruptedException {
        withRuntime(control, (work, env) -> {
            if (!enable) {
                control.check();
                Files.deleteIfExists(gpuEnabled);
                return null;
            }
            if (!gpuSupported()) throw new IllegalStateException("GPU acceleration is unavailable on this platform");
            String device = accelerator();
            if (device.equals("cuda")) {
                control.phase("Checking for an NVIDIA GPU");
                checked(control, env, work, "detect-cuda", Duration.ofSeconds(15),
                    List.of("nvidia-smi", "--query-gpu=name", "--format=csv,noheader"));
            }
            setupBase(control, work, env);
            if (device.equals("cuda")) {
                Path packages = setupCuda(folder, control, work, env);
                control.check();
                Path location = Files.writeString(work.resolve("cuda.location"), packages.toString());
                Files.move(location, cudaLocation, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                control.phase("Checking Apple Metal (MPS)");
                worker(control, env, work, "verify-mps", SETUP_TIMEOUT, "verify", "mps");
            }
            control.check();
            Files.writeString(gpuEnabled, "enabled\n");
            return null;
        });
    }

    @FunctionalInterface
    private interface RuntimeJob<T> {
        T run(Path work, Map<String, String> env) throws IOException, InterruptedException;
    }

    private <T> T withRuntime(Jobs.Control control, RuntimeJob<T> job) throws IOException, InterruptedException {
        Files.createDirectories(home);
        try (var channel = FileChannel.open(home.resolve("runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException(MOD_NAME + " is running in another Bitwig process");
            Path work = Files.createTempDirectory(home, "job-");
            try {
                return job.run(work, environment(work));
            } finally {
                try {
                    Installer.deleteInside(home, work);
                } catch (IOException error) {
                    control.log("Could not remove temporary files: " + error.getMessage());
                }
            }
        }
    }

    private List<Path> tracks(Path output, ScalpelActions.Mode mode) throws IOException {
        List<String> lines = Files.readAllLines(output);
        if (lines.size() != mode.tracks.size()) throw new IOException("The separator returned the wrong track count");
        Path cacheRoot = cache.toRealPath();
        List<Path> files = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Path file = Path.of(lines.get(index)).toRealPath();
            if (!file.startsWith(cacheRoot) || !Files.isRegularFile(file)
                || !file.getFileName().toString().equals(mode.tracks.get(index) + ".wav")) {
                throw new IOException("The separator returned an invalid stem path");
            }
            files.add(file);
        }
        return files;
    }

    private Map<String, String> environment(Path work) {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.keySet().removeIf(key -> {
            String upper = key.toUpperCase(java.util.Locale.ROOT);
            return upper.startsWith("PIP_") || upper.startsWith("PYTHON") || upper.startsWith("AUDIO_DNA_")
                || upper.equals("IMAGEIO_FFMPEG_EXE") || upper.equals("PYTORCH_ENABLE_MPS_FALLBACK")
                || upper.equals("TEMP") || upper.equals("TMP") || upper.equals("TMPDIR");
        });
        env.put("PYTHONPATH", AudioAnalysis.paths().engine().toString());
        env.put("PYTHONNOUSERSITE", "1");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PIP_CONFIG_FILE", paths.os() == Platform.Os.WINDOWS ? "nul" : "/dev/null");
        env.put("PIP_NO_CACHE_DIR", "1");
        env.put("HF_HUB_OFFLINE", "1");
        env.put("TEMP", work.toString());
        env.put("TMP", work.toString());
        env.put("TMPDIR", work.toString());
        return env;
    }

    private Map<String, String> acceleratedEnvironment(Map<String, String> base, String device, Path packages) {
        Map<String, String> env = new HashMap<>(base);
        if (device.equals("cuda")) {
            env.put("PYTHONPATH", packages + java.io.File.pathSeparator + env.get("PYTHONPATH"));
        } else {
            env.put("PYTORCH_ENABLE_MPS_FALLBACK", "1");
        }
        return env;
    }

    private boolean cudaInstalled(Path packages) {
        return Files.isDirectory(packages) && (packages.equals(legacyCudaPackages)
            || Files.isRegularFile(packages.resolve(CUDA_READY)));
    }

    private Path setupCuda(Path folder, Jobs.Control control, Path work, Map<String, String> env)
            throws IOException, InterruptedException {
        if (folder == null || !Files.isDirectory(folder)) throw new IOException("Choose an available CUDA installation folder");
        Path root = folder.toRealPath();
        if (root.toString().indexOf(java.io.File.pathSeparatorChar) >= 0) {
            throw new IOException("Choose a CUDA folder without " + java.io.File.pathSeparatorChar + " in its path");
        }
        boolean legacy = root.equals(paths.home().toRealPath()) && Files.isDirectory(legacyCudaPackages);
        Path cudaPackages = legacy ? legacyCudaPackages : root.resolve(CUDA_DIRECTORY);
        if (cudaInstalled(cudaPackages)) {
            control.phase("Checking installed CUDA PyTorch");
            try {
                worker(control, acceleratedEnvironment(env, "cuda", cudaPackages), work, "verify-cuda",
                       SETUP_TIMEOUT, "verify", "cuda");
                return cudaPackages;
            } catch (IOException error) {
                control.check();
                control.log("CUDA check failed: " + error.getMessage());
                throw new IOException("CUDA PyTorch failed its check. Check the NVIDIA driver or use the CPU.", error);
            }
        }
        if (Files.exists(cudaPackages, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A file or incomplete installation already exists at " + cudaPackages
                + ". Choose another folder.");
        }
        if (paths.os() == Platform.Os.WINDOWS && Files.getFileStore(root).getUsableSpace() < WINDOWS_CUDA_SETUP_BYTES) {
            throw new IOException("CUDA setup needs about 7.2 GB of free space. Choose another folder.");
        }
        // Both pip's temporary download and its final package directory live on the selected drive.
        Path staging = Files.createTempDirectory(root, ".scalpel-cuda-install-");
        try {
            control.phase("Installing CUDA PyTorch");
            Path packages = staging.resolve("packages");
            Map<String, String> installEnv = environment(staging);
            boolean linux = paths.os() == Platform.Os.LINUX;
            List<String> args = new ArrayList<>(List.of("--only-binary=:all:", "--index-url", CUDA_WHEELS,
                "--target", packages.toString()));
            if (linux) {
                args.addAll(List.of("--extra-index-url", PYPI, "--constraint", engine.resolve("requirements-base.txt").toString()));
            }
            args.add(CUDA_TORCH);
            pipInstall(control, installEnv, staging, "install-cuda", linux, args.toArray(String[]::new));
            control.phase("Checking CUDA PyTorch");
            worker(control, acceleratedEnvironment(installEnv, "cuda", packages), staging, "verify-cuda",
                   SETUP_TIMEOUT, "verify", "cuda");
            control.check();
            Files.writeString(packages.resolve(CUDA_READY), "ready\n");
            Files.move(packages, cudaPackages, StandardCopyOption.ATOMIC_MOVE);
            return cudaPackages;
        } finally {
            try {
                Installer.deleteInside(root, staging);
            } catch (IOException error) {
                control.log("Could not remove temporary CUDA files: " + error.getMessage());
            }
        }
    }

    private void setupBase(Jobs.Control control, Path work, Map<String, String> env)
            throws IOException, InterruptedException {
        if (baseReady()) return;
        if (paths.os() == Platform.Os.WINDOWS) {
            var missing = Platform.missingWindowsRuntime(Platform.system32(env));
            if (!missing.isEmpty()) throw new IllegalStateException(Installer.windowsRuntimeProblem(missing));
        }
        if (!Files.isRegularFile(paths.basePython())) Installer.installPython(paths, python, control);
        if (!Files.isRegularFile(paths.python())) {
            control.phase("Creating the " + MOD_NAME + " environment");
            checked(control, env, work, "venv", SETUP_TIMEOUT, Commands.createVenv(paths.basePython(), paths.venv()));
        }
        control.phase("Installing PyTorch and FFmpeg");
        boolean mac = paths.os() == Platform.Os.MACOS;
        String torch = mac && Platform.currentArch() == Platform.Arch.X86_64
            ? "torch==2.2.2" : mac ? "torch==2.11.0" : "torch==2.11.0+cpu";
        pip(control, env, work, "torch", "--only-binary=:all:", "--index-url", mac ? PYPI : CPU_WHEELS, torch);
        requirements(control, env, work, "base");
        verify(control, env, work, "base");
    }

    private void setup(ScalpelActions.Mode mode, Jobs.Control control, Path work, Map<String, String> env)
            throws IOException, InterruptedException {
        setupBase(control, work, env);
        if (mode.song && !modelReady("song")) {
            control.phase("Installing Demucs");
            requirements(control, env, work, "song");
            fetch(control, HTDEMUCS, paths.models());
            verify(control, env, work, "song");
        }
        if (mode.drums && !modelReady("drums")) {
            control.phase("Installing MDX23C DrumSep");
            requirements(control, env, work, "drums");
            Path archive = fetch(control, MDX_SOURCE, work);
            pip(control, env, work, "mdx23c", "--no-index", "--no-build-isolation", archive.toString());
            fetch(control, DRUMSEP + ".ckpt", paths.models());
            fetch(control, DRUMSEP + ".yaml", paths.models());
            verify(control, env, work, "drums");
        }
    }

    private void verify(Jobs.Control control, Map<String, String> env, Path work, String kind)
            throws IOException, InterruptedException {
        control.phase("Checking " + kind + " setup");
        worker(control, env, work, "verify-" + kind, SETUP_TIMEOUT, "verify", kind);
        control.check();
        Files.writeString(marker(kind), "ready\n");
    }

    private Path fetch(Jobs.Control control, String name, Path directory) throws IOException {
        for (String line : Files.readAllLines(engine.resolve("assets.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\s+");
            if (!fields[0].equals(name)) continue;
            Path file = directory.resolve(name);
            String sha256 = fields[1];
            long bytes = Long.parseLong(fields[2]);
            if (Files.isRegularFile(file) && Files.size(file) == bytes && Download.sha256(file).equals(sha256)) return file;
            control.phase("Downloading " + (name.equals(HTDEMUCS) ? "HTDemucs (84 MB)"
                : name.endsWith(".ckpt") ? "DrumSep (418 MiB)" : name));
            control.progress(0.0);
            Download.fetch(fields[3], file, sha256, bytes, control::cancelled,
                           count -> control.progress((double) count / bytes));
            control.check();
            return file;
        }
        throw new IOException("No download entry for " + name);
    }

    private void requirements(Jobs.Control control, Map<String, String> env, Path work, String kind)
            throws IOException, InterruptedException {
        pip(control, env, work, "packages-" + kind, "--only-binary=:all:", "--index-url", PYPI,
            "-r", engine.resolve("requirements-" + kind + ".txt").toString());
    }

    private void pip(Jobs.Control control, Map<String, String> env, Path work, String log, String... args)
            throws IOException, InterruptedException {
        pipInstall(control, env, work, log, false, args);
    }

    private void pipInstall(Jobs.Control control, Map<String, String> env, Path work, String log,
                            boolean dependencies, String... args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(paths.python().toString(), "-m", "pip", "install",
            "--disable-pip-version-check", "--no-input"));
        if (!dependencies) command.add("--no-deps");
        command.addAll(List.of(args));
        checked(control, env, work, log, SETUP_TIMEOUT, command);
    }

    private void worker(Jobs.Control control, Map<String, String> env, Path work, String log, Duration timeout, String... args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(paths.python().toString(), "-P",
            engine.resolve("scalpel_stems.py").toString(), "--runtime", paths.home().toString()));
        command.addAll(List.of(args));
        checked(control, env, work, log, timeout, command);
    }

    private void checked(Jobs.Control control, Map<String, String> env, Path work, String log,
                         Duration timeout, List<String> command) throws IOException, InterruptedException {
        var result = control.run(command, env, work, "scalpel-stems-" + log, timeout);
        if (result.ok()) return;
        if (result.timedOut()) throw new IOException(MOD_NAME + " " + log + " timed out");
        String detail = result.output().lines()
            .filter(line -> line.startsWith("Scalpel Stems error: ") || line.startsWith("ERROR:"))
            .reduce((first, last) -> last).orElse("The worker exited with status " + result.exitCode())
            .replace("Scalpel Stems", MOD_NAME);
        throw new IOException(detail.length() > 260 ? detail.substring(0, 260) + "…" : detail);
    }
}
