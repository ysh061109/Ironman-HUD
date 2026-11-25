package io.github.ysh061109.ironhud;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloaded neural voice for Modus: the Piper TTS executable plus a voice model, fetched on demand into
 * config/modus/piper. Nothing here is bundled with the mod.
 */
public final class Piper {
    private Piper() {}

    static final String RELEASE = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/";
    static final String VOICES = "https://huggingface.co/rhasspy/piper-voices/resolve/main/";
    static final String EN_VOICE = "en_GB-alan-medium";
    static final String EN_PATH = "en/en_GB/alan/medium/";
    static final String KO_VOICE = "ko_KR-kss-medium";
    static final String KO_PATH = "ko/ko_KR/kss/medium/";

    private static Path base() {
        return FMLPaths.CONFIGDIR.get().resolve("modus").resolve("piper");
    }

    private static boolean ko() {
        return HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO;
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private static String archName() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = a.contains("aarch64") || a.contains("arm64");
        if (os().contains("win")) return "piper_windows_amd64.zip";
        if (os().contains("mac")) return arm ? "piper_macos_aarch64.tar.gz" : "piper_macos_x64.tar.gz";
        return arm ? "piper_linux_aarch64.tar.gz" : "piper_linux_x86_64.tar.gz";
    }

    public static Path exe() {
        return base().resolve("runtime").resolve("piper").resolve(os().contains("win") ? "piper.exe" : "piper");
    }

    public static String voiceName() {
        return ko() ? KO_VOICE : EN_VOICE;
    }

    public static Path model() {
        return base().resolve("voices").resolve(voiceName() + ".onnx");
    }

    public static boolean runtimeReady() {
        return Files.isRegularFile(exe());
    }

    public static boolean voiceReady() {
        return Files.isRegularFile(model()) && Files.isRegularFile(Path.of(model() + ".json"));
    }

    public static boolean ready() {
        return runtimeReady() && voiceReady();
    }

    public static void install(Consumer<String> say) {
        Thread t = new Thread(() -> {
            try {
                installNow(say);
            } catch (Throwable e) {
                say.accept("Neural voice install failed: " + e + " (Modus keeps using the system voice)");
            }
        }, "Modus-PiperInstall");
        t.setDaemon(true);
        t.start();
    }

    public static void installNow(Consumer<String> say) throws Exception {
        Files.createDirectories(base().resolve("voices"));
        if (!runtimeReady()) {
            String url = HudConfig.PIPER_URL.get().isBlank() ? RELEASE + archName() : HudConfig.PIPER_URL.get();
            Path arc = base().resolve(url.endsWith(".zip") ? "piper.zip" : "piper.tar.gz");
            say.accept("Downloading Piper neural voice engine...");
            download(url, arc, say);
            say.accept("Unpacking engine...");
            Path rt = base().resolve("runtime");
            Files.createDirectories(rt);
            if (arc.toString().endsWith(".zip")) unzip(arc, rt);
            else untar(arc, rt);
            Files.deleteIfExists(arc);
            if (!os().contains("win")) makeExecutable(rt);
        }
        if (!voiceReady()) {
            String custom = HudConfig.PIPER_VOICE_URL.get();
            String onnx = !custom.isBlank() ? custom : VOICES + (ko() ? KO_PATH + KO_VOICE : EN_PATH + EN_VOICE) + ".onnx";
            String json = onnx + ".json";
            say.accept("Downloading voice model " + voiceName() + " ...");
            download(json, Path.of(model() + ".json"), say);
            download(onnx, model(), say);
        }
        say.accept("Neural voice ready: " + voiceName());
        Voice.clearStatus();
        Modus.prewarm();
    }

    private static void makeExecutable(Path dir) throws Exception {
        try (var s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p)) continue;
                try {
                    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(p);
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(p, perms);
                } catch (Exception ignored) {
                }
            }
        }
    }

    static void download(String url, Path target, Consumer<String> say) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "IronHud/1.0");
        long total = c.getContentLengthLong();
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long done = 0, last = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0 && done - last > total / 4) {
                    last = done;
                    say.accept("  " + (done * 100 / total) + "%");
                }
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void unzip(Path zip, Path dest) throws Exception {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest.normalize())) continue;
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /** Minimal tar.gz reader (regular files and directories; symlinks are skipped). */
    private static void untar(Path tgz, Path dest) throws Exception {
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(tgz)))) {
            byte[] h = new byte[512];
            String longName = null;
            while (in.readNBytes(h, 0, 512) == 512) {
                if (h[0] == 0) break;
                String name = str(h, 0, 100);
                String prefix = str(h, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = 0;
                String sz = str(h, 124, 12).trim();
                if (!sz.isEmpty()) size = Long.parseLong(sz, 8);
                char type = (char) h[156];
                if (longName != null && type != 'L') {
                    name = longName;
                    longName = null;
                }
                if (type == 'L') { // GNU long name
                    longName = new String(in.readNBytes((int) size), StandardCharsets.UTF_8).trim().replace("\0", "");
                    in.skipNBytes((512 - size % 512) % 512);
                    continue;
                }
                Path out = dest.resolve(name).normalize();
                boolean safe = out.startsWith(dest.normalize());
                if (type == '5') {
                    if (safe) Files.createDirectories(out);
                } else if (type == '0' || type == 0) {
                    if (safe) {
                        Files.createDirectories(out.getParent());
                        try (OutputStream os = Files.newOutputStream(out)) {
                            long left = size;
                            byte[] buf = new byte[65536];
                            while (left > 0) {
                                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                                if (n < 0) throw new java.io.EOFException();
                                os.write(buf, 0, n);
                                left -= n;
                            }
                        }
                        in.skipNBytes((512 - size % 512) % 512);
                        continue;
                    }
                }
                in.skipNBytes(size + (512 - size % 512) % 512);
            }
        }
    }

    private static String str(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        return new String(b, off, e - off, StandardCharsets.UTF_8);
    }

    /** Runs Piper for one line of text. Returns true if a wav was written. */
    public static boolean synth(String text, Path out) throws Exception {
        // length_scale > 1 is slower; voiceRate -10..10 maps to roughly 1.3..0.7
        double ls = 1.0 - HudConfig.VOICE_RATE.get() * 0.03;
        ProcessBuilder pb = new ProcessBuilder(exe().toString(), "--model", model().toString(),
                "--output_file", out.toString(), "--length_scale", String.format(Locale.ROOT, "%.2f", ls),
                "--sentence_silence", "0.15");
        pb.directory(exe().getParent().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (OutputStream os = p.getOutputStream()) {
            os.write((text.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Thread drain = new Thread(() -> {
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            } catch (Exception ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();
        if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return false;
        }
        return p.exitValue() == 0 && Files.isRegularFile(out) && Files.size(out) > 100;
    }
}
