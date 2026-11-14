package com.example.miniadventure;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Layar loading: langit malam bergradasi, bulan, bintang + bintang jatuh, sinar berputar,
 * awan, bukit berlapis yang bergeser (parallax), kunang-kunang, logo dengan kilau,
 * koin melayang, dan progress bar emas dengan karakter berlari + jejak percikan.
 * Semua animasi digambar lewat kode di Canvas. Tidak ada tulisan selain persen / "GO!".
 */
public class LoadingScreen {

    private static final long MIN_NS = 1_800_000_000L;   // lama minimal layar tampil
    private static final long HOLD_NS = 550_000_000L;    // jeda setelah 100% ("GO!" + fade)
    private static final float LOGO_H = 5.8f;            // tinggi logo (satuan u)
    private static final float LOGO_TOP = 1.9f;          // posisi atas logo (satuan u)
    private static final float RUNNER_H = 1.5f;          // tinggi karakter = jarak logo ke bar
    private static final float FLAG_H = 1.7f;            // tinggi bendera di ujung bar

    private static final int[] RUN_FRAMES = {
            R.drawable.ryka_run1,
            R.drawable.ryka_run2,
            R.drawable.ryka_run3,
            R.drawable.ryka_run4,
            R.drawable.ryka_run5
    };

    private static final float[][] COIN_POS = {
            {0.14f, 0.30f}, {0.86f, 0.36f}, {0.22f, 0.54f}, {0.78f, 0.58f}
    };

    // Bukit berlapis (satuan u): tinggi dasar, amplitudo, kecepatan geser, warna.
    private static final float[] HILL_BASE = {8.7f, 9.6f, 10.6f};
    private static final float[] HILL_AMP = {0.9f, 0.7f, 0.5f};
    private static final float[] HILL_SPEED = {0.15f, 0.35f, 0.7f};
    private static final int[] HILL_COLOR = {0xFF1B3578, 0xFF112656, 0xFF0A1739};

    private final Context context;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint layerPaint = new Paint();
    private final RectF r = new RectF();
    private final Path trackPath = new Path();
    private final Path stripe = new Path();
    private final Path hill = new Path();
    private final Path ray = new Path();
    private final PorterDuffXfermode atop = new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);
    private final Typeface face = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    private Bitmap logo;
    private Bitmap coin;
    private Bitmap flag;
    private Bitmap[] runner;

    private LinearGradient bgShader;
    private RadialGradient glowShader;
    private RadialGradient moonShader;
    private LinearGradient fillShader;

    private float[] starX, starY, starR, starPhase;
    private float[] flyX, flyY, flySpeed, flyPhase, flySize;
    private float[] cloudX, cloudY, cloudScale, cloudSpeed;

    private int sw, sh;
    private float u;
    private float barLeft, barTop, barW, barH;
    private float logoCy;

    private boolean ready;
    private long startNs, lastNs;
    private volatile long doneNs = -1;
    private long finishNs = -1;
    private float shown;

    public LoadingScreen(Context context) {
        this.context = context;
    }

    // Dipanggil sekali sebelum loading dimulai.
    public void begin(int sw, int sh, float u) {
        this.sw = sw;
        this.sh = sh;
        this.u = u;

        startNs = System.nanoTime();
        lastNs = startNs;
        doneNs = -1;
        finishNs = -1;
        shown = 0f;

        Resources res = context.getResources();

        logo = decode(res, R.drawable.logo_loading, Math.round(LOGO_H * u));
        coin = trim(decode(res, R.drawable.coin, Math.round(u * 1.2f)));
        flag = trim(decode(res, R.drawable.flag, Math.round(u * 2.6f)));
        runner = decodeRunner(res, Math.round(u * 2.6f));

        barW = Math.min(sw * 0.5f, 13f * u);
        barH = 0.85f * u;
        barLeft = (sw - barW) / 2f;
        barTop = (LOGO_TOP + LOGO_H + RUNNER_H) * u;

        logoCy = (LOGO_TOP + LOGO_H / 2f) * u;

        bgShader = new LinearGradient(0, 0, 0, sh,
                new int[]{0xFF070F33, 0xFF1E3F95, 0xFF3B6FD8},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(sw / 2f, logoCy, Math.max(sw, sh) * 0.55f,
                0x773E7BFF, 0x003E7BFF, Shader.TileMode.CLAMP);
        moonShader = new RadialGradient(sw * 0.1f, sh * 0.15f, 2.8f * u,
                0x66FFF3C4, 0x00FFF3C4, Shader.TileMode.CLAMP);
        fillShader = new LinearGradient(0, barTop, 0, barTop + barH,
                0xFFFFE680, 0xFFFFA800, Shader.TileMode.CLAMP);

        Random rnd = new Random(7);

        int n = 60;
        starX = new float[n];
        starY = new float[n];
        starR = new float[n];
        starPhase = new float[n];

        for (int i = 0; i < n; i++) {
            starX[i] = rnd.nextFloat() * sw;
            starY[i] = rnd.nextFloat() * sh * 0.75f;
            starR[i] = u * (0.025f + rnd.nextFloat() * 0.055f);
            starPhase[i] = rnd.nextFloat() * 6.28f;
        }

        int f = 26;
        flyX = new float[f];
        flyY = new float[f];
        flySpeed = new float[f];
        flyPhase = new float[f];
        flySize = new float[f];

        for (int i = 0; i < f; i++) {
            flyX[i] = rnd.nextFloat() * sw;
            flyY[i] = rnd.nextFloat() * sh;
            flySpeed[i] = u * (0.25f + rnd.nextFloat() * 0.5f);
            flyPhase[i] = rnd.nextFloat() * 6.28f;
            flySize[i] = u * (0.04f + rnd.nextFloat() * 0.05f);
        }

        int cn = 5;
        cloudX = new float[cn];
        cloudY = new float[cn];
        cloudScale = new float[cn];
        cloudSpeed = new float[cn];

        for (int i = 0; i < cn; i++) {
            cloudX[i] = rnd.nextFloat() * sw;
            cloudY[i] = sh * (0.12f + rnd.nextFloat() * 0.5f);
            cloudScale[i] = 0.8f + rnd.nextFloat() * 0.9f;
            cloudSpeed[i] = u * (0.12f + rnd.nextFloat() * 0.18f);
        }

        ready = true;
    }

    // Dipanggil ketika semua gambar game selesai dimuat.
    public void markDone() {
        if (doneNs < 0) {
            doneNs = System.nanoTime();
        }
    }

    public boolean isFinished() {
        return finishNs >= 0 && System.nanoTime() - finishNs >= HOLD_NS;
    }

    // Lepaskan gambar loading setelah selesai.
    public void release() {
        ready = false;
        logo = null;
        coin = null;
        flag = null;
        runner = null;
    }

    public void draw(Canvas c) {
        if (!ready) {
            c.drawColor(Color.BLACK);
            return;
        }

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float t = (now - startNs) / 1_000_000_000f;

        // Progress dihaluskan: naik pelan ke 90%, lalu penuh saat loading selesai.
        boolean done = doneNs >= 0;
        float target = done ? 1f : 0.9f * (1f - (float) Math.exp(-t * 1.1f));
        float speed = done ? 6f : 3f;
        shown += (target - shown) * Math.min(1f, dt * speed);

        if (done && finishNs < 0 && shown >= 0.99f && now - startNs >= MIN_NS) {
            shown = 1f;
            finishNs = now;
        }

        if (finishNs >= 0) {
            shown = 1f;
        }

        drawSky(c, t);
        drawRays(c, t);
        drawClouds(c, t);
        drawHills(c, t);
        drawFireflies(c, t);
        drawCoins(c, t);
        drawLogo(c, t);
        drawBar(c, t, now);
        drawOverlays(c, t, now);
    }

    // ---------------- Langit ----------------

    private void drawSky(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(bgShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(glowShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(moonShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(null);

        // Bulan.
        float mx = sw * 0.1f;
        float my = sh * 0.15f;
        p.setColor(0xFFFFF1B8);
        c.drawCircle(mx, my, 0.8f * u, p);
        p.setColor(0x33D9C27A);
        c.drawCircle(mx - 0.25f * u, my - 0.2f * u, 0.18f * u, p);
        c.drawCircle(mx + 0.3f * u, my + 0.1f * u, 0.24f * u, p);
        c.drawCircle(mx - 0.1f * u, my + 0.38f * u, 0.12f * u, p);

        // Bintang berkedip.
        for (int i = 0; i < starX.length; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * 2f + starPhase[i]);
            p.setColor(Color.WHITE);
            p.setAlpha((int) (60 + 160 * a));
            c.drawCircle(starX[i], starY[i], starR[i], p);
        }

        p.setAlpha(255);

        // Bintang jatuh tiap 5 detik.
        float s = (t - 1f) % 5f;

        if (t > 1f && s < 0.9f) {
            float prog = s / 0.9f;
            float hx = sw * (0.92f - 0.45f * prog);
            float hy = sh * (0.04f + 0.3f * prog);
            float fade = 1f - prog * prog * prog;
            float dx = 0.8f, dy = 0.55f;

            for (int k = 0; k < 10; k++) {
                float a = (1f - k / 10f) * fade;
                p.setColor(Color.WHITE);
                p.setAlpha((int) (255 * a));
                c.drawCircle(hx + dx * k * 0.35f * u, hy - dy * k * 0.35f * u,
                        (0.09f - 0.007f * k) * u, p);
            }

            p.setAlpha(255);
        }
    }

    // Sinar berputar di belakang logo.
    private void drawRays(Canvas c, float t) {
        float cx = sw / 2f;
        float radius = Math.max(sw, sh);
        float rot = t * 0.12f;
        int count = 14;
        float half = 0.07f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x16BFD7FF);

        for (int i = 0; i < count; i++) {
            float a = rot + i * (6.2832f / count);

            ray.reset();
            ray.moveTo(cx, logoCy);
            ray.lineTo(cx + radius * (float) Math.cos(a - half),
                    logoCy + radius * (float) Math.sin(a - half));
            ray.lineTo(cx + radius * (float) Math.cos(a + half),
                    logoCy + radius * (float) Math.sin(a + half));
            ray.close();
            c.drawPath(ray, p);
        }
    }

    private void drawClouds(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x26B4CCFF);

        float span = sw + 8f * u;

        for (int i = 0; i < cloudX.length; i++) {
            float x = ((cloudX[i] + t * cloudSpeed[i]) % span) - 4f * u;
            float y = cloudY[i];
            float k = cloudScale[i] * u;

            r.set(x - 1.4f * k, y - 0.1f * k, x + 1.4f * k, y + 0.5f * k);
            c.drawRoundRect(r, 0.4f * k, 0.4f * k, p);
            c.drawCircle(x - 0.4f * k, y - 0.1f * k, 0.55f * k, p);
            c.drawCircle(x + 0.35f * k, y - 0.2f * k, 0.7f * k, p);
        }
    }

    // Bukit berlapis yang bergeser pelan ke kiri.
    private void drawHills(Canvas c, float t) {
        float step = Math.max(6f, u * 0.25f);

        p.setStyle(Paint.Style.FILL);

        for (int l = 0; l < HILL_BASE.length; l++) {
            hill.reset();
            hill.moveTo(0, sh);

            for (float x = 0; x <= sw + step; x += step) {
                float xx = x / u + t * HILL_SPEED[l];
                float y = (HILL_BASE[l] + HILL_AMP[l]
                        * (0.5f * (float) Math.sin(xx * 0.55f + l * 2f)
                        + 0.3f * (float) Math.sin(xx * 1.3f + l))) * u;
                hill.lineTo(x, y);
            }

            hill.lineTo(sw + step, sh);
            hill.close();

            p.setColor(HILL_COLOR[l]);
            c.drawPath(hill, p);
        }
    }

    // Kunang-kunang emas yang naik pelan.
    private void drawFireflies(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);

        for (int i = 0; i < flyX.length; i++) {
            float x = flyX[i] + (float) Math.sin(t * 0.8f + flyPhase[i]) * 0.5f * u;
            float y = sh - ((flyY[i] + t * flySpeed[i]) % (sh * 1.05f));
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 3f + flyPhase[i]);

            p.setColor(0xFFFFE680);
            p.setAlpha((int) (35 * tw));
            c.drawCircle(x, y, flySize[i] * 3.2f, p);
            p.setAlpha((int) (110 + 140 * tw));
            c.drawCircle(x, y, flySize[i], p);
        }

        p.setAlpha(255);
    }

    private void drawCoins(Canvas c, float t) {
        if (coin == null) {
            return;
        }

        for (int i = 0; i < COIN_POS.length; i++) {
            float cx = sw * COIN_POS[i][0];
            float cy = sh * COIN_POS[i][1]
                    + (float) Math.sin(t * 2f + i * 1.7f) * 0.25f * u;
            float size = u * (i % 2 == 0 ? 1.0f : 0.8f);
            float spin = Math.max(0.12f,
                    Math.abs((float) Math.cos(t * 2.6f + i)));

            r.set(cx - size * spin / 2f, cy - size / 2f,
                    cx + size * spin / 2f, cy + size / 2f);
            c.drawBitmap(coin, null, r, p);
        }
    }

    // ---------------- Logo ----------------

    private void drawLogo(Canvas c, float t) {
        if (logo == null) {
            return;
        }

        float pop = Math.min(1f, t / 0.8f);
        float x = pop - 1f;
        float ease = 1f + 2.70158f * x * x * x + 1.70158f * x * x;   // easeOutBack
        float scale = (0.6f + 0.4f * ease)
                * (1f + 0.015f * (float) Math.sin(t * 2.2f));
        float drop = -(1f - pop) * (1f - pop) * 2f * u;   // jatuh dari atas
        float tilt = (float) Math.sin(t * 1.3f) * 1.4f;   // goyang pelan

        float lh = LOGO_H * u;
        float lw = lh * logo.getWidth() / logo.getHeight();
        float cx = sw / 2f;
        float cy = logoCy + (float) Math.sin(t * 1.6f) * 0.08f * u + drop;

        // Bayangan di bawah logo.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44000000);
        p.setAlpha((int) (68 * pop));
        float sy = logoCy + lh / 2f + 0.05f * u;
        r.set(cx - lw * 0.32f, sy, cx + lw * 0.32f, sy + 0.4f * u);
        c.drawOval(r, p);
        p.setAlpha(255);

        c.save();
        c.translate(cx, cy);
        c.rotate(tilt);
        c.scale(scale, scale);

        r.set(-lw / 2f, -lh / 2f, lw / 2f, lh / 2f);

        layerPaint.setAlpha((int) (255 * pop));
        int layer = c.saveLayer(r, layerPaint);

        p.setAlpha(255);
        c.drawBitmap(logo, null, r, p);

        // Kilau yang menyapu logo tiap 3,2 detik (hanya mengenai piksel logo).
        float cycle = t % 3.2f;

        if (t > 0.9f && cycle < 1.4f) {
            float sweep = cycle / 1.4f;
            float bandW = lw * 0.18f;
            float skew = lh * 0.3f;
            float bx = -lw / 2f - bandW - skew + sweep * (lw + bandW * 2f + skew);

            p.setXfermode(atop);
            p.setStyle(Paint.Style.FILL);

            p.setColor(0x33FFFFFF);
            drawBand(c, bx, bandW, -lh / 2f, lh / 2f, skew);

            p.setColor(0x55FFFFFF);
            drawBand(c, bx + bandW * 0.3f, bandW * 0.4f, -lh / 2f, lh / 2f, skew);

            p.setXfermode(null);
        }

        c.restoreToCount(layer);
        c.restore();
    }

    private void drawBand(Canvas c, float x, float w, float top, float bottom, float skew) {
        stripe.reset();
        stripe.moveTo(x + skew, top);
        stripe.lineTo(x + skew + w, top);
        stripe.lineTo(x + w, bottom);
        stripe.lineTo(x, bottom);
        stripe.close();
        c.drawPath(stripe, p);
    }

    // ---------------- Progress bar ----------------

    private void drawBar(Canvas c, float t, long now) {
        float bl = barLeft, bt = barTop, bw = barW, bh = barH;

        // Cahaya di sekitar bar.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFC933);
        p.setAlpha((int) (38 + 24 * Math.sin(t * 3f)));
        r.set(bl - 0.18f * u, bt - 0.18f * u, bl + bw + 0.18f * u, bt + bh + 0.18f * u);
        c.drawRoundRect(r, bh, bh, p);
        p.setAlpha(255);

        // Bayangan.
        p.setColor(0x66000000);
        r.set(bl, bt + 0.14f * u, bl + bw, bt + bh + 0.14f * u);
        c.drawRoundRect(r, bh / 2f, bh / 2f, p);

        // Jalur gelap.
        p.setColor(0xFF0A1330);
        r.set(bl, bt, bl + bw, bt + bh);
        c.drawRoundRect(r, bh / 2f, bh / 2f, p);

        // Isi emas, dipotong mengikuti bentuk jalur.
        float fw = Math.max(bh, bw * shown);

        trackPath.reset();
        trackPath.addRoundRect(r, bh / 2f, bh / 2f, Path.Direction.CW);

        c.save();
        c.clipPath(trackPath);
        c.clipRect(bl, bt, bl + fw, bt + bh);

        p.setShader(fillShader);
        c.drawRect(bl, bt, bl + bw, bt + bh, p);
        p.setShader(null);

        // Garis miring yang bergerak.
        float stripeW = bh * 0.9f;
        float off = (t * bh * 1.2f) % (stripeW * 2f);
        p.setColor(0x30FFFFFF);

        for (float sx = bl - stripeW * 2f + off; sx < bl + fw; sx += stripeW * 2f) {
            stripe.reset();
            stripe.moveTo(sx, bt + bh);
            stripe.lineTo(sx + stripeW, bt + bh);
            stripe.lineTo(sx + stripeW + bh * 0.6f, bt);
            stripe.lineTo(sx + bh * 0.6f, bt);
            stripe.close();
            c.drawPath(stripe, p);
        }

        // Kilau di bagian atas.
        p.setColor(0x66FFFFFF);
        c.drawRect(bl, bt + bh * 0.1f, bl + fw, bt + bh * 0.4f, p);

        // Kilat putih saat selesai.
        if (finishNs >= 0) {
            float gt = (now - finishNs) / 1_000_000_000f;
            float flash = Math.max(0f, 1f - gt / 0.3f);
            p.setColor(Color.argb((int) (190 * flash), 255, 255, 255));
            c.drawRect(bl, bt, bl + bw, bt + bh, p);
        }

        c.restore();

        // Bingkai cokelat.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.1f * u);
        p.setColor(0xFF4A2A12);
        c.drawRoundRect(r, bh / 2f, bh / 2f, p);
        p.setStyle(Paint.Style.FILL);

        // Bendera di ujung bar.
        if (flag != null) {
            float fh = FLAG_H * u;
            float fwid = fh * flag.getWidth() / flag.getHeight();
            float fx = bl + bw;
            r.set(fx - fwid / 2f, bt + 0.2f * u - fh, fx + fwid / 2f, bt + 0.2f * u);
            c.drawBitmap(flag, null, r, p);
        }

        // Karakter berlari mengikuti ujung isi bar.
        float hx = Math.max(bl, Math.min(bl + bw, bl + bw * shown));
        float bounce = Math.abs((float) Math.sin(t * 12f)) * 0.12f * u;
        float feet = bt + 0.15f * u - bounce;

        // Debu dan percikan di belakang karakter.
        if (shown > 0.02f && shown < 1f) {
            for (int i = 0; i < 6; i++) {
                float dx = hx - 0.4f * u - i * 0.35f * u;
                p.setColor(0xFFB8C7FF);
                p.setAlpha(70 - i * 10);
                c.drawCircle(dx, bt + 0.05f * u - i * 0.04f * u, (0.1f + 0.05f * i) * u, p);
            }

            for (int i = 0; i < 6; i++) {
                float sx = hx - (0.3f + i * 0.3f) * u;
                float sy = feet - 0.7f * u + (float) Math.sin(t * 9f + i * 1.3f) * 0.25f * u;
                p.setColor(0xFFFFE680);
                p.setAlpha(210 - i * 32);
                c.drawCircle(sx, sy, (0.14f - 0.02f * i) * u, p);
            }

            p.setAlpha(255);
        }

        if (runner != null && runner.length > 0 && runner[0] != null) {
            Bitmap frame = runner[((int) (t * 12f)) % runner.length];
            float rh = RUNNER_H * u;
            float rw = rh * frame.getWidth() / frame.getHeight();

            r.set(hx - rw / 2f, feet - rh, hx + rw / 2f, feet);
            c.drawBitmap(frame, null, r, p);
        }

        // Persen di tengah bar, berubah jadi "GO!" saat selesai.
        String label;
        float size = bh * 0.62f;

        if (finishNs >= 0) {
            float gt = (now - finishNs) / 1_000_000_000f;
            size *= 1f + 0.35f * Math.max(0f, 1f - gt / 0.25f);
            label = "GO!";
        } else {
            label = ((int) (shown * 100f)) + "%";
        }

        drawOutlined(c, label, bl + bw / 2f, bt + bh * 0.5f + size * 0.35f,
                size, Color.WHITE, 0xFF4A2A12, Paint.Align.CENTER);
    }

    // Transisi masuk dari hitam dan keluar ke navy (menuju layar pilih karakter).
    private void drawOverlays(Canvas c, float t, long now) {
        p.setStyle(Paint.Style.FILL);

        if (t < 0.45f) {
            p.setColor(Color.argb((int) (255 * (1f - t / 0.45f)), 0, 0, 0));
            c.drawRect(0, 0, sw, sh, p);
        }

        if (finishNs >= 0) {
            float gt = (now - finishNs) / 1_000_000_000f;
            float fade = Math.max(0f, Math.min(1f, (gt - 0.35f) / 0.2f));

            if (fade > 0f) {
                p.setColor(Color.argb((int) (255 * fade), 8, 19, 56));
                c.drawRect(0, 0, sw, sh, p);
            }
        }

        p.setAlpha(255);
    }

    private void drawOutlined(Canvas c, String s, float x, float y, float size,
                              int fill, int stroke, Paint.Align align) {
        p.setTextAlign(align);
        p.setTextSize(size);
        p.setTypeface(face);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.2f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(stroke);
        c.drawText(s, x, y, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawText(s, x, y, p);

        p.setTextAlign(Paint.Align.LEFT);
    }

    // ---- Memuat gambar kecil agar hemat memori ----

    private static Bitmap decode(Resources res, int id, int targetH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        o.inScaled = false;
        BitmapFactory.decodeResource(res, id, o);

        int s = 1;
        while (o.outHeight / (s * 2) >= targetH) {
            s *= 2;
        }

        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        return BitmapFactory.decodeResource(res, id, o);
    }

    // Batas area tidak transparan: {minX, minY, maxX, maxY} atau null.
    private static int[] bounds(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 12) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < 0) {
            return null;
        }

        return new int[]{minX, minY, maxX, maxY};
    }

    private static Bitmap trim(Bitmap b) {
        if (b == null) {
            return null;
        }

        int[] bb = bounds(b);

        if (bb == null) {
            return b;
        }

        return Bitmap.createBitmap(b, bb[0], bb[1],
                bb[2] - bb[0] + 1, bb[3] - bb[1] + 1);
    }

    // Semua frame dipotong dengan batas yang sama supaya tidak bergetar.
    private static Bitmap[] decodeRunner(Resources res, int targetH) {
        Bitmap[] frames = new Bitmap[RUN_FRAMES.length];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = -1, maxY = -1;

        for (int i = 0; i < frames.length; i++) {
            frames[i] = decode(res, RUN_FRAMES[i], targetH);

            if (frames[i] == null) {
                continue;
            }

            int[] bb = bounds(frames[i]);

            if (bb != null) {
                minX = Math.min(minX, bb[0]);
                minY = Math.min(minY, bb[1]);
                maxX = Math.max(maxX, bb[2]);
                maxY = Math.max(maxY, bb[3]);
            }
        }

        if (maxX < 0) {
            return frames;
        }

        for (int i = 0; i < frames.length; i++) {
            if (frames[i] != null) {
                frames[i] = Bitmap.createBitmap(frames[i], minX, minY,
                        maxX - minX + 1, maxY - minY + 1);
            }
        }

        return frames;
    }
}