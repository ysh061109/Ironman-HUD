package io.github.ysh061109.ironhud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline speech recognition for "Modus". The Vosk library and a small speech model are downloaded on demand
 * (/modus install) into config/modus and used through reflection, so the mod itself has no native dependency
 * and a failure here can never prevent the game from starting.
 */
public final class Mic {
    private static final String DEFAULT_JAR =
            "https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar";
    private static final String MODEL_EN = "vosk-model-small-en-us-0.15";
    private static final String MODEL_KO = "vosk-model-small-ko-0.22";

    private static volatile boolean running = false;
    private static Thread thread;
    private static volatile boolean ptt = false;
    public static volatile String status = "off";

    private Mic() {}

    public static boolean isRunning() {
        return running;
    }

    public static void setPushToTalk(boolean held) {
        ptt = held;
    }

    // ---------------------------------------------------------------- paths

    private static Path base() {
        return FMLPaths.CONFIGDIR.get().resolve("modus");
    }

    private static Path jarPath() {
        return base().resolve("lib").resolve("vosk.jar");
    }

    private static String modelName() {
        return HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO ? MODEL_KO : MODEL_EN;
    }

    private static Path modelDir() {
        return base().resolve("models").resolve(modelName());
    }

    public static boolean installed() {
        return Files.isRegularFile(jarPath()) && Files.isDirectory(modelDir());
    }

    // -------------------------------------------------------------- install

    public static void install(Consumer<String> say) {
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(jarPath().getParent());
                if (!Files.isRegularFile(jarPath())) {
                    String url = HudConfig.VOSK_JAR_URL.get().isBlank() ? DEFAULT_JAR : HudConfig.VOSK_JAR_URL.get();
                    say.accept("Downloading speech library...");
                    download(url, jarPath(), say);
                }
                if (!Files.isDirectory(modelDir())) {
                    String url = HudConfig.VOSK_MODEL_URL.get().isBlank()
                            ? "https://alphacephei.com/vosk/models/" + modelName() + ".zip"
                            : HudConfig.VOSK_MODEL_URL.get();
                    Path zip = base().resolve("models").resolve(modelName() + ".zip");
                    Files.createDirectories(zip.getParent());
                    say.accept("Downloading speech model (" + modelName() + ")...");
                    download(url, zip, say);
                    say.accept("Unpacking model...");
                    unzip(zip, base().resolve("models"));
                    Files.deleteIfExists(zip);
                }
                say.accept("Voice input installed. Use /modus mic on");
            } catch (Throwable e) {
                say.accept("Install failed: " + e);
            }
        }, "Modus-Install");
        t.setDaemon(true);
        t.start();
    }

    private static void download(String url, Path target, Consumer<String> say) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        long total = c.getContentLengthLong();
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long done = 0, lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0 && done - lastReport > total / 5) {
                    lastReport = done;
                    say.accept(String.format(Locale.ROOT, "  %d%%", done * 100 / total));
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
                if (!out.startsWith(dest.normalize())) continue; // zip-slip guard
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

    // ------------------------------------------------------------- lifecycle

    public static synchronized void start() {
        if (running) return;
        if (!installed()) {
            status = "not installed (run /modus install)";
            return;
        }
        running = true;
        thread = new Thread(Mic::loop, "Modus-Mic");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (status.startsWith("listening")) status = "off";
    }

    // ---------------------------------------------------------------- natives

    private static Path extractNatives(Path jar) throws Exception {
        Path dir = base().resolve("native");
        Files.createDirectories(dir);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean armSys = arch.contains("aarch64") || arch.contains("arm64");
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory() || !(n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib"))) continue;
                boolean osOk = os.contains("win") ? n.contains("win")
                        : os.contains("mac") ? (n.contains("darwin") || n.contains("mac"))
                        : n.contains("linux");
                if (!osOk) continue;
                boolean armEntry = n.contains("aarch64") || n.contains("arm64") || n.contains("armv");
                if (armEntry != armSys) continue;
                String file = n.substring(n.lastIndexOf('/') + 1);
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return dir;
    }

    // ------------------------------------------------------------------ loop

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void loop() {
        TargetDataLine line = null;
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Object model = null, rec = null;
        try {
            status = "loading model";
            Path nat = extractNatives(jarPath());
            String old = System.getProperty("jna.library.path");
            System.setProperty("jna.library.path", nat.toString() + (old == null ? "" : java.io.File.pathSeparator + old));

            URLClassLoader cl = new URLClassLoader(new URL[]{jarPath().toUri().toURL()}, Mic.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> modelC = cl.loadClass("org.vosk.Model");
            Class<?> recC = cl.loadClass("org.vosk.Recognizer");
            try {
                Class<?> ll = cl.loadClass("org.vosk.LogLevel");
                cl.loadClass("org.vosk.LibVosk").getMethod("setLogLevel", ll).invoke(null, Enum.valueOf((Class<Enum>) ll, "WARNINGS"));
            } catch (Throwable ignored) {
            }
            model = modelC.getConstructor(String.class).newInstance(modelDir().toString());
            rec = recC.getConstructor(modelC, float.class).newInstance(model, 16000f);
            Method accept = recC.getMethod("acceptWaveForm", byte[].class, int.class);
            Method result = recC.getMethod("getResult");
            Method reset = recC.getMethod("reset");
            Method finalRes = recC.getMethod("getFinalResult");

            AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
            line = AudioSystem.getTargetDataLine(fmt);
            line.open(fmt, 16000);
            line.start();
            status = "listening";

            byte[] buf = new byte[3200];
            boolean wasMuted = false, prevPtt = false;
            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                boolean muted = Voice.micMuted();
                if (muted) {
                    wasMuted = true;
                    continue;
                }
                if (wasMuted) {
                    wasMuted = false;
                    reset.invoke(rec);
                }
                boolean pttOnly = HudConfig.MIC_PTT_ONLY.get() || !HudConfig.MIC_ENABLED.get();
                if (pttOnly && prevPtt && !ptt) {
                    prevPtt = false;
                    String json = (String) finalRes.invoke(rec);
                    String text = JsonParser.parseString(json).getAsJsonObject().get("text").getAsString().trim();
                    if (!text.isEmpty()) Modus.onTranscript(text, true);
                    reset.invoke(rec);
                    continue;
                }
                prevPtt = ptt;
                if (pttOnly && !ptt) continue;
                boolean fin = (boolean) accept.invoke(rec, buf, n);
                if (fin) {
                    String json = (String) result.invoke(rec);
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    String text = o.has("text") ? o.get("text").getAsString().trim() : "";
                    if (!text.isEmpty()) Modus.onTranscript(text, ptt);
                }
            }
        } catch (Throwable t) {
            Throwable r = t.getCause() != null ? t.getCause() : t;
            status = "error: " + r.getClass().getSimpleName() + ": " + r.getMessage();
            Modus.sysLater("Voice input failed: " + status);
        } finally {
            running = false;
            Thread.currentThread().setContextClassLoader(prev);
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (rec instanceof AutoCloseable c) c.close();
                if (model instanceof AutoCloseable c) c.close();
            } catch (Throwable ignored) {
            }
            if (!status.startsWith("error")) status = "off";
        }
    }
}
