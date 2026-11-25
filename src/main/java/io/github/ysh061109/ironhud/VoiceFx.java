package io.github.ysh061109.ironhud;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;

/** Loads a TTS wav and gives it a "helmet radio" character: band-limit, resonance, tiny reverb, soft clip. */
public final class VoiceFx {
    public record Pcm(short[] data, int rate) {}

    private VoiceFx() {}

    public static Pcm load(File wav, boolean fx, float volume) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(wav)) {
            AudioFormat src = raw.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(pcm, raw)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                byte[] bytes = bos.toByteArray();
                int ch = Math.max(1, pcm.getChannels());
                int frames = bytes.length / (2 * ch);
                float[] x = new float[frames];
                for (int i = 0; i < frames; i++) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) {
                        int idx = (i * ch + c) * 2;
                        sum += (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8));
                    }
                    x[i] = sum / ch / 32768f;
                }
                int rate = Math.round(pcm.getSampleRate());
                if (fx) x = process(x, rate);
                return new Pcm(finish(x, volume, rate), rate);
            }
        }
    }

    private static float[] process(float[] in, int rate) {
        int pad = (int) (rate * 0.25);
        float[] x = new float[in.length + pad];
        System.arraycopy(in, 0, x, 0, in.length);

        // band-limit: 1-pole high-pass ~130 Hz, low-pass ~6.5 kHz
        float hpA = (float) Math.exp(-2.0 * Math.PI * 130.0 / rate);
        float lpA = (float) Math.exp(-2.0 * Math.PI * 6500.0 / rate);
        float prevIn = 0, prevHp = 0, lp = 0;
        for (int i = 0; i < x.length; i++) {
            float hp = hpA * (prevHp + x[i] - prevIn);
            prevIn = x[i];
            prevHp = hp;
            lp = (1f - lpA) * hp + lpA * lp;
            x[i] = lp;
        }

        // helmet resonance (short comb) + small room tail
        float[] y = new float[x.length];
        int d1 = (int) (rate * 0.011), d2 = (int) (rate * 0.019);
        int r1 = (int) (rate * 0.041), r2 = (int) (rate * 0.067), r3 = (int) (rate * 0.097);
        for (int i = 0; i < x.length; i++) {
            float v = x[i];
            if (i >= d1) v += 0.26f * x[i - d1];
            if (i >= d2) v += 0.15f * x[i - d2];
            if (i >= r1) v += 0.10f * y[i - r1];
            if (i >= r2) v += 0.08f * y[i - r2];
            if (i >= r3) v += 0.06f * y[i - r3];
            y[i] = v;
        }
        // soft clip for a slightly compressed, "processed" sound
        for (int i = 0; i < y.length; i++) y[i] = (float) Math.tanh(1.5 * y[i]);
        return y;
    }

    private static short[] finish(float[] x, float volume, int rate) {
        float peak = 1e-6f;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        float g = 0.92f / peak * Math.max(0f, Math.min(1f, volume));
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x[i] * g * 32767f)));
        }
        return out;
    }
}
