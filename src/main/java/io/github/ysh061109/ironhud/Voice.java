package io.github.ysh061109.ironhud;

import net.neoforged.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Modus' voice. Text is turned into a wav by the operating system's speech engine (Windows SAPI via
 * PowerShell, macOS "say", Linux espeak-ng, or a custom command such as Piper), cached on disk,
 * given a helmet-radio filter and played through Java Sound on a worker thread.
 */
public final class Voice {
    public enum Backend { WINDOWS, MAC, ESPEAK, CUSTOM, NONE }

    private record Job(String text) {}

    private static final LinkedBlockingDeque<Job> QUEUE = new LinkedBlockingDeque<>();
    private static Thread worker;
    private static volatile boolean speaking = false;
    private static volatile boolean interrupt = false;
    private static volatile long mutedUntil = 0L;
    public static volatile String status = "idle";

    private Voice() {}

    public static boolean isSpeaking() {
        return speaking;
    }

    /** True while Modus talks (and shortly after) so the microphone does not hear itself. */
    public static boolean micMuted() {
        return speaking || System.currentTimeMillis() < mutedUntil;
    }

    public static synchronized void init() {
        if (worker != null) return;
        worker = new Thread(Voice::run, "Modus-Voice");
        worker.setDaemon(true);
        worker.start();
    }

    public static Path cacheDir() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("modus").resolve("cache");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
        }
        return p;
    }

    public static void speak(String text, boolean urgent) {
        if (text == null || text.isBlank() || !HudConfig.VOICE_ENABLED.get()) return;
        init();
        if (urgent) {
            QUEUE.clear();
            interrupt = true;
        } else {
            while (QUEUE.size() >= 3) QUEUE.pollFirst();
        }
        QUEUE.addLast(new Job(text));
    }

    public static void stop() {
        QUEUE.clear();
        interrupt = true;
    }

    // ---------------------------------------------------------------- backend

    public static Backend backend() {
        if (!HudConfig.TTS_COMMAND.get().isBlank()) return Backend.CUSTOM;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Backend.WINDOWS;
        if (os.contains("mac")) return Backend.MAC;
        if (espeakExe() != null) return Backend.ESPEAK;
        return Backend.NONE;
    }

    private static String espeakExe() {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            for (String n : new String[]{"espeak-ng", "espeak"}) {
                File f = new File(dir, n);
                if (f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static String key(String text) throws Exception {
        String k = backend() + "|" + HudConfig.VOICE_LANG.get() + "|" + HudConfig.VOICE_NAME.get() + "|"
                + HudConfig.VOICE_RATE.get() + "|" + HudConfig.TTS_COMMAND.get() + "|" + text;
        byte[] d = MessageDigest.getInstance("SHA-1").digest(k.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    private static File wavFor(String text) throws Exception {
        return cacheDir().resolve(key(text) + ".wav").toFile();
    }

    /** Synthesizes any texts that are not cached yet (one process for the whole batch on Windows). */
    public static synchronized void synthesize(List<String> texts) {
        try {
            Backend b = backend();
            if (b == Backend.NONE) {
                status = "no TTS engine (install espeak-ng or set ttsCommand)";
                return;
            }
            List<String[]> todo = new ArrayList<>();
            for (String t : texts) {
                File f = wavFor(t);
                if (!f.exists() || f.length() < 100) todo.add(new String[]{key(t), t});
            }
            if (todo.isEmpty()) return;
            if (b == Backend.WINDOWS) {
                synthWindows(todo);
            } else {
                for (String[] it : todo) {
                    switch (b) {
                        case MAC -> synthMac(it[0], it[1]);
                        case ESPEAK -> synthEspeak(it[0], it[1]);
                        case CUSTOM -> synthCustom(it[0], it[1]);
                        default -> {
                        }
                    }
                }
            }
        } catch (Throwable t) {
            status = "tts error: " + t.getMessage();
        }
    }

    public static void prewarm(List<String> texts) {
        Thread t = new Thread(() -> synthesize(texts), "Modus-Prewarm");
        t.setDaemon(true);
        t.start();
    }

    private static final String PS_SCRIPT = """
            [Console]::OutputEncoding = [Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Speech
            $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
            $voices = @($s.GetInstalledVoices() | Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo })
            $pick = $null
            if ($env:MODUS_VOICE) { $pick = $voices | Where-Object { $_.Name -like ('*' + $env:MODUS_VOICE + '*') } | Select-Object -First 1 }
            if (-not $pick) { $pick = $voices | Where-Object { $_.Culture.Name -eq $env:MODUS_CULTURE -and $_.Gender -eq 'Male' } | Select-Object -First 1 }
            if (-not $pick) { $pick = $voices | Where-Object { $_.Culture.Name -like ($env:MODUS_LANG + '*') -and $_.Gender -eq 'Male' } | Select-Object -First 1 }
            if (-not $pick) { $pick = $voices | Where-Object { $_.Culture.Name -like ($env:MODUS_LANG + '*') } | Select-Object -First 1 }
            if ($pick) { $s.SelectVoice($pick.Name) }
            $s.Rate = [int]$env:MODUS_RATE
            foreach ($line in [System.IO.File]::ReadAllLines($env:MODUS_BATCH, [Text.Encoding]::UTF8)) {
              if (-not $line) { continue }
              $p = $line.Split("`t", 2)
              $s.SetOutputToWaveFile((Join-Path $env:MODUS_DIR ($p[0] + '.wav')))
              $s.Speak($p[1])
              $s.SetOutputToNull()
            }
            $s.Dispose()
            """;

    private static boolean ko() {
        return HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO;
    }

    private static void synthWindows(List<String[]> todo) throws Exception {
        Path batch = Files.createTempFile("modus", ".tsv");
        StringBuilder sb = new StringBuilder();
        for (String[] it : todo) sb.append(it[0]).append('\t').append(it[1].replace('\t', ' ').replace('\n', ' ')).append('\n');
        Files.writeString(batch, sb.toString(), StandardCharsets.UTF_8);
        String enc = Base64.getEncoder().encodeToString(PS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
        pb.environment().put("MODUS_BATCH", batch.toString());
        pb.environment().put("MODUS_DIR", cacheDir().toString());
        pb.environment().put("MODUS_VOICE", HudConfig.VOICE_NAME.get());
        pb.environment().put("MODUS_CULTURE", ko() ? "ko-KR" : "en-GB");
        pb.environment().put("MODUS_LANG", ko() ? "ko" : "en");
        pb.environment().put("MODUS_RATE", String.valueOf(HudConfig.VOICE_RATE.get()));
        run(pb, null, 120);
        Files.deleteIfExists(batch);
    }

    private static void synthMac(String hash, String text) throws Exception {
        File out = cacheDir().resolve(hash + ".wav").toFile();
        String voice = HudConfig.VOICE_NAME.get().isBlank() ? (ko() ? "Yuna" : "Daniel") : HudConfig.VOICE_NAME.get();
        int wpm = 175 + HudConfig.VOICE_RATE.get() * 10;
        for (int attempt = 0; attempt < 2; attempt++) {
            List<String> cmd = new ArrayList<>(List.of("say"));
            if (attempt == 0) {
                cmd.add("-v");
                cmd.add(voice);
            }
            cmd.addAll(List.of("-r", String.valueOf(wpm), "-o", out.getAbsolutePath(),
                    "--data-format=LEI16@22050", "-f", "-"));
            int code = run(new ProcessBuilder(cmd), text, 30);
            if (code == 0 && out.exists()) return;
        }
    }

    private static void synthEspeak(String hash, String text) throws Exception {
        File out = cacheDir().resolve(hash + ".wav").toFile();
        String v = HudConfig.VOICE_NAME.get().isBlank() ? (ko() ? "ko" : "en-gb+m3") : HudConfig.VOICE_NAME.get();
        int speed = 150 + HudConfig.VOICE_RATE.get() * 8;
        run(new ProcessBuilder(espeakExe(), "-v", v, "-s", String.valueOf(speed), "-p", "38",
                "-w", out.getAbsolutePath(), "--stdin"), text, 30);
    }

    private static void synthCustom(String hash, String text) throws Exception {
        File out = cacheDir().resolve(hash + ".wav").toFile();
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder pb = win
                ? new ProcessBuilder("cmd", "/c", HudConfig.TTS_COMMAND.get())
                : new ProcessBuilder("sh", "-c", HudConfig.TTS_COMMAND.get());
        pb.environment().put("MODUS_TEXT", text);
        pb.environment().put("MODUS_OUT", out.getAbsolutePath());
        run(pb, null, 60);
    }

    private static int run(ProcessBuilder pb, String stdin, int timeoutSec) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        if (stdin != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            p.getOutputStream().close();
        }
        // drain output so the process cannot block
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream is = p.getInputStream()) {
                is.transferTo(sink);
            } catch (Exception ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    // --------------------------------------------------------------- playback

    private static void run() {
        while (true) {
            Job j;
            try {
                j = QUEUE.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                interrupt = false;
                status = "synthesizing";
                synthesize(List.of(j.text()));
                File f = wavFor(j.text());
                if (!f.exists()) {
                    if (backend() == Backend.NONE) status = "no TTS engine";
                    continue;
                }
                VoiceFx.Pcm pcm = VoiceFx.load(f, HudConfig.VOICE_FX.get(), HudConfig.VOICE_VOLUME.get().floatValue());
                play(pcm);
                status = "ready (" + backend() + ")";
            } catch (Throwable t) {
                status = "voice error: " + t.getMessage();
            } finally {
                speaking = false;
                mutedUntil = System.currentTimeMillis() + 450;
            }
        }
    }

    private static void play(VoiceFx.Pcm pcm) throws Exception {
        AudioFormat fmt = new AudioFormat(pcm.rate(), 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt);
        line.start();
        speaking = true;
        byte[] bytes = new byte[pcm.data().length * 2];
        for (int i = 0; i < pcm.data().length; i++) {
            bytes[i * 2] = (byte) (pcm.data()[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((pcm.data()[i] >> 8) & 0xFF);
        }
        int off = 0;
        while (off < bytes.length && !interrupt) {
            int n = Math.min(2048, bytes.length - off);
            line.write(bytes, off, n);
            off += n;
        }
        if (interrupt) {
            line.stop();
            line.flush();
        } else {
            line.drain();
        }
        line.close();
    }
}
