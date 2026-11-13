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
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Layar loading: latar navy + bintang, logo Mini Adventure, koin melayang,
 * progress bar emas dengan karakter berlari menuju bendera, dan tips bergantian.
 * Semua animasi digambar lewat kode di Canvas.
 */
public class LoadingScreen {

    private static final long MIN_NS = 1_600_000_000L;   // lama minimal layar tampil
    private static final long HOLD_NS = 250_000_000L;    // jeda singkat setelah 100%
    private static final float TIP_SECONDS = 2.4f;
    private static final float LOGO_H = 6.2f;            // tinggi logo (satuan u)

    private static final String[] TIPS = {
            "Kumpulkan koin sebanyak-banyaknya!",
            "Injak musuh dari atas untuk mengalahkannya.",
            "Hati-hati dengan jurang di depanmu!",
            "Capai bendera untuk naik level.",
            "Setiap level, musuh bergerak makin cepat."
    };

    private static final int[] RUN_FRAMES = {
            R.drawable.ryka_run1,
            R.drawable.ryka_run2,
            R.drawable.ryka_run3,
            R.drawable.ryka_run4,
            R.drawable.ryka_run5
    };

    private static final float[][] COIN_POS = {
            {0.10f, 0.30f}, {0.90f, 0.22f}, {0.16f, 0.64f}, {0.86f, 0.60f}
    };

    private final Context context;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path trackPath = new Path();
    private final Path stripe = new Path();
    private final Typeface face = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    private Bitmap logo;
    private Bitmap coin;
    private Bitmap flag;
    private Bitmap[] runner;

    private LinearGradient bgShader;
    private RadialGradient glowShader;
    private LinearGradient fillShader;

    private float[] starX, starY, starR, starPhase;

    private int sw, sh;
    private float u;
    private float barLeft, barTop, barW, barH;

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

        logo = decode(res, R.drawable.logo, Math.round(LOGO_H * u));
        coin = trim(decode(res, R.drawable.coin, Math.round(u * 1.2f)));
        flag = trim(decode(res, R.drawable.flag, Math.round(u * 2.6f)));
        runner = decodeRunner(res, Math.round(u * 2.6f));

        barW = Math.min(sw * 0.62f, 16f * u);
        barH = 0.8f * u;
        barLeft = (sw - barW) / 2f;
        barTop = 9.5f * u;

        bgShader = new LinearGradient(0, 0, 0, sh,
                0xFF22479E, 0xFF081338, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(sw / 2f, LOGO_H * u * 0.5f,
                Math.max(sw, sh) * 0.5f,
                0x663E7BFF, 0x003E7BFF, Shader.TileMode.CLAMP);
        fillShader = new LinearGradient(0, barTop, 0, barTop + barH,
                0xFFFFE680, 0xFFFFA800, Shader.TileMode.CLAMP);

        Random rnd = new Random(7);
        int n = 40;
        starX = new float[n];
        starY = new float[n];
        starR = new float[n];
        starPhase = new float[n];

        for (int i = 0; i < n; i++) {
            starX[i] = rnd.nextFloat() * sw;
            starY[i] = rnd.nextFloat() * sh * 0.9f;
            starR[i] = u * (0.03f + rnd.nextFloat() * 0.06f);
            starPhase[i] = rnd.nextFloat() * 6.28f;
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

        drawBackground(c, t);
        drawCoins(c, t);
        drawLogo(c, t);
        drawBar(c, t);
        drawTexts(c, t);
    }

    private void drawBackground(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(bgShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(glowShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(null);

        for (int i = 0; i < starX.length; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(t * 2f + starPhase[i]);
            p.setColor(Color.WHITE);
            p.setAlpha((int) (70 + 150 * a));
            c.drawCircle(starX[i], starY[i], starR[i], p);
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

    private void drawLogo(Canvas c, float t) {
        if (logo == null) {
            return;
        }

        float pop = Math.min(1f, t / 0.7f);
        float x = pop - 1f;
        float ease = 1f + 2.70158f * x * x * x + 1.70158f * x * x;   // easeOutBack
        float scale = (0.7f + 0.3f * ease)
                * (1f + 0.015f * (float) Math.sin(t * 2.2f));

        float lh = LOGO_H * u;
        float lw = lh * logo.getWidth() / logo.getHeight();
        float cx = sw / 2f;
        float cy = 0.25f * u + lh / 2f + (float) Math.sin(t * 1.6f) * 0.07f * u;

        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        p.setAlpha((int) (255 * pop));
        r.set(-lw / 2f, -lh / 2f, lw / 2f, lh / 2f);
        c.drawBitmap(logo, null, r, p);
        c.restore();
        p.setAlpha(255);
    }

    private void drawBar(Canvas c, float t) {
        float bl = barLeft, bt = barTop, bw = barW, bh = barH;

        // Bayangan.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        r.set(bl, bt + 0.12f * u, bl + bw, bt + bh + 0.12f * u);
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
        p.setColor(0x55FFFFFF);
        c.drawRect(bl, bt + bh * 0.12f, bl + fw, bt + bh * 0.42f, p);
        c.restore();

        // Bingkai cokelat.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.1f * u);
        p.setColor(0xFF4A2A12);
        c.drawRoundRect(r, bh / 2f, bh / 2f, p);
        p.setStyle(Paint.Style.FILL);

        // Bendera di ujung bar.
        if (flag != null) {
            float fh = 2.1f * u;
            float fwid = fh * flag.getWidth() / flag.getHeight();
            float fx = bl + bw;
            r.set(fx - fwid / 2f, bt + 0.2f * u - fh, fx + fwid / 2f, bt + 0.2f * u);
            c.drawBitmap(flag, null, r, p);
        }

        // Karakter berlari mengikuti ujung isi bar.
        if (runner != null && runner.length > 0 && runner[0] != null) {
            Bitmap frame = runner[((int) (t * 12f)) % runner.length];
            float rh = 1.9f * u;
            float rw = rh * frame.getWidth() / frame.getHeight();
            float hx = Math.max(bl, Math.min(bl + bw, bl + bw * shown));
            float feet = bt + 0.15f * u
                    - Math.abs((float) Math.sin(t * 12f)) * 0.12f * u;

            r.set(hx - rw / 2f, feet - rh, hx + rw / 2f, feet);
            c.drawBitmap(frame, null, r, p);
        }

        // Persen di tengah bar.
        drawOutlined(c, ((int) (shown * 100f)) + "%",
                bl + bw / 2f, bt + bh * 0.5f + bh * 0.22f,
                bh * 0.62f, Color.WHITE, 0xFF4A2A12, Paint.Align.CENTER);
    }

    private void drawTexts(Canvas c, float t) {
        float base = barTop + barH;

        // "MEMUAT PETUALANGAN..." dengan titik bergerak.
        String full = "MEMUAT PETUALANGAN...";
        int dots = ((int) (t * 2.5f)) % 4;
        String text = "MEMUAT PETUALANGAN" + "...".substring(0, dots);

        p.setTypeface(face);
        p.setTextSize(0.5f * u);
        p.setStyle(Paint.Style.FILL);
        float fullW = p.measureText(full);

        drawOutlined(c, text, sw / 2f - fullW / 2f, base + 0.95f * u,
                0.5f * u, 0xFFFFD34D, 0xFF3B1F0E, Paint.Align.LEFT);

        // Tips yang berganti dengan efek fade.
        int idx = ((int) (t / TIP_SECONDS)) % TIPS.length;
        float phase = (t % TIP_SECONDS) / TIP_SECONDS;
        float a = Math.min(1f, Math.min(phase / 0.15f, (1f - phase) / 0.15f));

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(face);
        p.setTextSize(0.38f * u);
        p.setColor(Color.argb((int) (255 * a), 207, 227, 255));
        c.drawText(TIPS[idx], sw / 2f, base + 1.55f * u, p);

        p.setTextAlign(Paint.Align.LEFT);
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