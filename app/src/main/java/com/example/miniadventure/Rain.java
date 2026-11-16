package com.example.miniadventure;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.List;
import java.util.Random;

// Efek hujan + kabut acak. Hujan tidak turun terus-menerus:
// - jeda kering panjang (acak) antar hujan
// - setiap jeda selesai, hanya ada peluang tertentu hujan benar-benar turun
// - sekali hujan, lamanya RAIN_SEC detik (sudah termasuk menguat dan mereda)
// Tetes hujan berhenti di tanah, dan hanya terlihat jatuh lewat jurang.
public class Rain {

    // ====== Atur frekuensi hujan di sini ======
    private static final float DRY_MIN_SEC = 25f;     // jeda kering minimum
    private static final float DRY_MAX_SEC = 70f;     // jeda kering maksimum
    private static final float RAIN_CHANCE = 0.55f;   // peluang hujan turun setelah jeda
    private static final float RAIN_SEC = 20f;        // lama hujan (detik)
    private static final float FADE_IN_SEC = 3f;      // hujan dan kabut menguat pelan
    private static final float FADE_OUT_SEC = 4f;     // hujan dan kabut mereda pelan
    // ==========================================

    private static final int MAX_DROPS = 140;
    private static final int MAX_DIM_ALPHA = 40;      // gelap tipis saat hujan deras

    // Tanah ada di y = 9 satuan (sama dengan World). Tetes berhenti sedikit
    // di bawah permukaan supaya terlihat jatuh ke rumput.
    private static final float GROUND_TOP_UNITS = 9f;
    private static final float GROUND_SINK_UNITS = 0.1f;
    private static final int MAX_GROUND = 32;

    // Kabut: lapisan tipis di seluruh layar + gumpalan besar yang melayang pelan.
    private static final int FOG_BLOBS = 6;

    private final Random random = new Random();
    private final Paint dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint hazePaint = new Paint();
    private final Paint[] blobPaint = new Paint[FOG_BLOBS];

    // Tiap tetes: x, y, kecepatan, panjang.
    private final float[] x = new float[MAX_DROPS];
    private final float[] y = new float[MAX_DROPS];
    private final float[] speed = new float[MAX_DROPS];
    private final float[] len = new float[MAX_DROPS];
    private final float[] lines = new float[MAX_DROPS * 4];

    // Tanah yang sedang terlihat di layar (kiri, kanan), diisi tiap frame.
    private final float[] groundLeft = new float[MAX_GROUND];
    private final float[] groundRight = new float[MAX_GROUND];

    // Tiap gumpalan kabut: posisi awal, tinggi, radius, kecepatan melayang.
    private final float[] blobX = new float[FOG_BLOBS];
    private final float[] blobY = new float[FOG_BLOBS];
    private final float[] blobR = new float[FOG_BLOBS];
    private final float[] blobSpeed = new float[FOG_BLOBS];

    private int sw, sh;
    private float u;
    private float slant;          // kemiringan hujan (miring ditiup angin)
    private float fogTime;

    private boolean raining;
    private float timer;          // sisa waktu fase sekarang (detik)
    private float phaseTime;      // lama fase hujan yang sedang berjalan
    private float intensity;      // 0..1

    public void layout(int sw, int sh, float u) {
        this.sw = sw;
        this.sh = sh;
        this.u = u;

        dropPaint.setStrokeCap(Paint.Cap.ROUND);
        dropPaint.setStrokeWidth(Math.max(1.5f, u * 0.045f));
        dropPaint.setColor(Color.argb(150, 210, 230, 255));

        dimPaint.setStyle(Paint.Style.FILL);

        // Lapisan kabut: tipis di atas, makin pekat ke arah tanah.
        hazePaint.setShader(new LinearGradient(
                0, 0, 0, sh,
                new int[]{
                        Color.argb(35, 215, 224, 236),
                        Color.argb(85, 215, 224, 236),
                        Color.argb(150, 215, 224, 236)
                },
                new float[]{0f, 0.65f, 1f},
                Shader.TileMode.CLAMP
        ));

        // Gumpalan kabut berbentuk lingkaran lembut (pekat di tengah, hilang di tepi).
        for (int i = 0; i < FOG_BLOBS; i++) {
            blobR[i] = sh * randomRange(0.3f, 0.55f);
            blobX[i] = random.nextFloat() * (sw + 2f * blobR[i]);
            blobY[i] = sh * randomRange(0.35f, 0.85f);
            blobSpeed[i] = u * randomRange(0.3f, 0.9f);

            blobPaint[i] = new Paint();
            blobPaint[i].setShader(new RadialGradient(
                    0, 0, blobR[i],
                    Color.argb(95, 222, 230, 240),
                    Color.argb(0, 222, 230, 240),
                    Shader.TileMode.CLAMP
            ));
        }

        reset();
    }

    // Dipanggil saat permainan baru: mulai dari cuaca cerah.
    public void reset() {
        raining = false;
        intensity = 0f;
        timer = randomRange(DRY_MIN_SEC * 0.5f, DRY_MAX_SEC * 0.6f);

        for (int i = 0; i < MAX_DROPS; i++) {
            respawn(i, true);
        }
    }

    public void update(float dt) {
        if (sw == 0 || sh == 0) {
            return;
        }

        timer -= dt;
        fogTime += dt;

        if (raining) {
            float elapsed = phaseTime - timer;

            if (timer <= FADE_OUT_SEC) {
                // Mereda.
                intensity = Math.max(0f, timer / FADE_OUT_SEC);
            } else if (elapsed < FADE_IN_SEC) {
                // Menguat.
                intensity = Math.min(1f, elapsed / FADE_IN_SEC);
            } else {
                intensity = 1f;
            }

            if (timer <= 0f) {
                raining = false;
                intensity = 0f;
                timer = randomRange(DRY_MIN_SEC, DRY_MAX_SEC);
            }
        } else if (timer <= 0f) {
            if (random.nextFloat() < RAIN_CHANCE) {
                raining = true;
                phaseTime = RAIN_SEC;
                timer = phaseTime;
                intensity = 0f;

                // Arah dan kemiringan angin diacak tiap hujan.
                slant = randomRange(-0.25f, 0.25f);
            } else {
                // Tidak jadi hujan: tunggu lagi.
                timer = randomRange(DRY_MIN_SEC, DRY_MAX_SEC);
            }
        }

        if (intensity <= 0f) {
            return;
        }

        int active = Math.round(MAX_DROPS * intensity);

        for (int i = 0; i < active; i++) {
            x[i] += slant * speed[i] * dt;
            y[i] += speed[i] * dt;

            if (y[i] > sh || x[i] < -u || x[i] > sw + u) {
                respawn(i, false);
            }
        }
    }

    // camX dan platforms dipakai supaya hujan tahu di mana ada tanah.
    public void draw(Canvas c, float camX, List<RectF> platforms) {
        if (intensity <= 0f) {
            return;
        }

        int alpha = Math.round(255f * intensity);

        // Layar sedikit meredup saat hujan.
        dimPaint.setColor(Color.argb(Math.round(MAX_DIM_ALPHA * intensity), 20, 30, 60));
        c.drawRect(0, 0, sw, sh, dimPaint);

        // Kabut: lapisan tipis lalu gumpalan yang melayang pelan
        // (sedikit bergeser mengikuti kamera supaya terasa ada kedalaman).
        hazePaint.setAlpha(alpha);
        c.drawRect(0, 0, sw, sh, hazePaint);

        for (int i = 0; i < FOG_BLOBS; i++) {
            float span = sw + 2f * blobR[i];
            float px = (blobX[i] + fogTime * blobSpeed[i] - camX * 0.25f) % span;

            if (px < 0f) {
                px += span;
            }

            blobPaint[i].setAlpha(alpha);

            c.save();
            c.translate(px - blobR[i], blobY[i]);
            c.drawCircle(0, 0, blobR[i], blobPaint[i]);
            c.restore();
        }

        // Cari tanah yang sedang terlihat (posisi layar = posisi dunia - camX).
        int ground = 0;

        for (RectF platform : platforms) {
            boolean isGround = platform.height() > 1.5f * u;

            if (isGround
                    && ground < MAX_GROUND
                    && platform.right - camX > 0f
                    && platform.left - camX < sw) {
                groundLeft[ground] = platform.left - camX;
                groundRight[ground] = platform.right - camX;
                ground++;
            }
        }

        float stopY = (GROUND_TOP_UNITS + GROUND_SINK_UNITS) * u;

        int active = Math.round(MAX_DROPS * intensity);
        int n = 0;

        for (int i = 0; i < active; i++) {
            float y1 = y[i];
            float y2 = y1 + len[i];

            // Tetes di atas tanah berhenti di permukaan tanah.
            // Tetes di atas jurang tidak terhalang, jadi jatuh terus ke bawah.
            if (isOverGround(x[i], ground)) {
                if (y1 >= stopY) {
                    continue;
                }

                if (y2 > stopY) {
                    y2 = stopY;
                }
            }

            lines[n++] = x[i];
            lines[n++] = y1;
            lines[n++] = x[i] + slant * (y2 - y1);
            lines[n++] = y2;
        }

        c.drawLines(lines, 0, n, dropPaint);
    }

    private boolean isOverGround(float px, int ground) {
        for (int g = 0; g < ground; g++) {
            if (px >= groundLeft[g] && px <= groundRight[g]) {
                return true;
            }
        }

        return false;
    }

    private void respawn(int i, boolean anywhere) {
        x[i] = random.nextFloat() * (sw + 2f * u) - u;
        y[i] = anywhere ? random.nextFloat() * sh : -random.nextFloat() * u * 2f;
        speed[i] = u * randomRange(14f, 20f);
        len[i] = u * randomRange(0.35f, 0.7f);
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
