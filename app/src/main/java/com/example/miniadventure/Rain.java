package com.example.miniadventure;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.List;
import java.util.Random;

// Cuaca acak. Ada 3 jenis, dan semuanya tidak turun terus-menerus:
// - NORMAL     : hujan biasa (sering)
// - WHITE_FOG  : hujan berkabut putih (sering)
// - DARK_FOG   : hujan berkabut gelap, pandangan pemain terbatas (jarang)
// Saat kabut gelap, pemain bisa mengambil senter dari peti harta untuk melihat lebih luas.
// Senter hilang begitu kabut gelap reda.
// Tetes hujan berhenti di tanah, dan hanya terlihat jatuh lewat jurang.
public class Rain {

    // ====== Atur frekuensi cuaca di sini ======
    private static final float DRY_MIN_SEC = 25f;        // jeda kering minimum
    private static final float DRY_MAX_SEC = 70f;        // jeda kering maksimum
    private static final float SKIP_CHANCE = 0.45f;      // peluang jeda diperpanjang (hujan tidak jadi turun)
    private static final float DARK_FOG_CHANCE = 0.15f;  // peluang cuaca berikutnya = kabut gelap (jarang)
    private static final float WHITE_FOG_CHANCE = 0.40f; // peluang = hujan berkabut putih. Sisanya hujan biasa.
    private static final float RAIN_SEC = 20f;           // lama hujan biasa / berkabut putih
    private static final float DARK_FOG_SEC = 20f;       // lama kabut gelap
    private static final float FADE_IN_SEC = 3f;         // menguat pelan
    private static final float FADE_OUT_SEC = 4f;        // mereda pelan
    // Peti senter baru muncul kalau kabut gelap tinggal sekian detik lagi (atau sedang turun).
    private static final float FLASHLIGHT_LEAD_SEC = 40f;
    // ==========================================

    // ====== Atur pandangan saat kabut gelap ======
    private static final float VISION_NO_LIGHT = 3.2f;   // radius terlihat tanpa senter (satuan u)
    private static final float VISION_LIGHT = 8.5f;      // radius terlihat dengan senter (satuan u)
    private static final int DARK_NO_LIGHT = 225;        // gelap di luar radius, tanpa senter (0..255)
    private static final int DARK_LIGHT = 135;           // gelap di luar radius, dengan senter (0..255)
    // =============================================

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_WHITE_FOG = 1;
    public static final int TYPE_DARK_FOG = 2;

    private static final int MAX_DROPS = 140;
    private static final int MAX_DIM_ALPHA = 40;         // gelap tipis saat hujan
    private static final int MAX_DIM_ALPHA_DARK = 70;    // gelap tipis saat kabut gelap

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
    private final Paint hazeWhitePaint = new Paint();
    private final Paint hazeDarkPaint = new Paint();
    private final Paint[] blobWhitePaint = new Paint[FOG_BLOBS];
    private final Paint[] blobDarkPaint = new Paint[FOG_BLOBS];

    // Lingkaran pandang kabut gelap + cahaya hangat senter.
    private final Paint visionPaint = new Paint();
    private final Paint glowPaint = new Paint();
    private final Matrix matrix = new Matrix();
    private RadialGradient visionShader;
    private RadialGradient glowShader;

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
    private int currentType;      // jenis cuaca yang sedang turun
    private int nextType;         // jenis cuaca berikutnya (sudah ditentukan dari awal jeda)
    private float timer;          // sisa waktu fase sekarang (detik)
    private float phaseTime;      // lama fase hujan yang sedang berjalan
    private float intensity;      // 0..1
    private float lightLevel;     // 0 = tanpa senter, 1 = pakai senter (berubah halus)
    private boolean darkFogJustEnded;

    public void layout(int sw, int sh, float u) {
        this.sw = sw;
        this.sh = sh;
        this.u = u;

        dropPaint.setStrokeCap(Paint.Cap.ROUND);
        dropPaint.setStrokeWidth(Math.max(1.5f, u * 0.045f));
        dropPaint.setColor(Color.argb(150, 210, 230, 255));

        dimPaint.setStyle(Paint.Style.FILL);

        // Lapisan kabut: tipis di atas, makin pekat ke arah tanah.
        hazeWhitePaint.setShader(new LinearGradient(
                0, 0, 0, sh,
                new int[]{
                        Color.argb(35, 215, 224, 236),
                        Color.argb(85, 215, 224, 236),
                        Color.argb(150, 215, 224, 236)
                },
                new float[]{0f, 0.65f, 1f},
                Shader.TileMode.CLAMP
        ));

        hazeDarkPaint.setShader(new LinearGradient(
                0, 0, 0, sh,
                new int[]{
                        Color.argb(45, 30, 38, 58),
                        Color.argb(95, 30, 38, 58),
                        Color.argb(150, 30, 38, 58)
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

            blobWhitePaint[i] = new Paint();
            blobWhitePaint[i].setShader(new RadialGradient(
                    0, 0, blobR[i],
                    Color.argb(95, 222, 230, 240),
                    Color.argb(0, 222, 230, 240),
                    Shader.TileMode.CLAMP
            ));

            blobDarkPaint[i] = new Paint();
            blobDarkPaint[i].setShader(new RadialGradient(
                    0, 0, blobR[i],
                    Color.argb(105, 42, 50, 72),
                    Color.argb(0, 42, 50, 72),
                    Shader.TileMode.CLAMP
            ));
        }

        // Lingkaran pandang berjari-jari 1, nanti diskalakan lewat matrix.
        // Tengah jernih, makin ke tepi makin gelap, lalu tetap gelap di luar.
        visionShader = new RadialGradient(
                0, 0, 1f,
                new int[]{
                        Color.argb(0, 6, 8, 20),
                        Color.argb(0, 6, 8, 20),
                        Color.argb(150, 6, 8, 20),
                        Color.argb(255, 6, 8, 20)
                },
                new float[]{0f, 0.45f, 0.8f, 1f},
                Shader.TileMode.CLAMP
        );

        glowShader = new RadialGradient(
                0, 0, 1f,
                Color.argb(80, 255, 236, 170),
                Color.argb(0, 255, 236, 170),
                Shader.TileMode.CLAMP
        );

        reset();
    }

    // Dipanggil saat permainan baru: mulai dari cuaca cerah.
    public void reset() {
        raining = false;
        intensity = 0f;
        lightLevel = 0f;
        darkFogJustEnded = false;

        scheduleNext(true);

        for (int i = 0; i < MAX_DROPS; i++) {
            respawn(i, true);
        }
    }

    // Tentukan kapan cuaca berikutnya turun dan jenisnya (jenis diketahui dari awal jeda).
    private void scheduleNext(boolean first) {
        if (first) {
            timer = randomRange(DRY_MIN_SEC * 0.5f, DRY_MAX_SEC * 0.6f);
        } else {
            timer = randomRange(DRY_MIN_SEC, DRY_MAX_SEC);

            // Kadang hujan tidak jadi turun: jedanya diperpanjang.
            while (random.nextFloat() < SKIP_CHANCE) {
                timer += randomRange(DRY_MIN_SEC, DRY_MAX_SEC);
            }
        }

        float roll = random.nextFloat();

        if (roll < DARK_FOG_CHANCE) {
            nextType = TYPE_DARK_FOG;
        } else if (roll < DARK_FOG_CHANCE + WHITE_FOG_CHANCE) {
            nextType = TYPE_WHITE_FOG;
        } else {
            nextType = TYPE_NORMAL;
        }
    }

    // True kalau kabut gelap sedang turun, atau akan turun sebentar lagi.
    // Dipakai untuk memunculkan peti senter.
    public boolean wantsFlashlight() {
        if (raining) {
            return currentType == TYPE_DARK_FOG;
        }

        return nextType == TYPE_DARK_FOG && timer <= FLASHLIGHT_LEAD_SEC;
    }

    // True satu kali saja tepat setelah kabut gelap reda (senter harus dihilangkan).
    public boolean consumeDarkFogEnded() {
        boolean ended = darkFogJustEnded;
        darkFogJustEnded = false;

        return ended;
    }

    public void update(float dt, boolean hasFlashlight) {
        if (sw == 0 || sh == 0) {
            return;
        }

        timer -= dt;
        fogTime += dt;

        // Cahaya senter menyala / mati secara halus.
        float target = hasFlashlight ? 1f : 0f;
        lightLevel += (target - lightLevel) * Math.min(1f, dt * 2.5f);

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
                if (currentType == TYPE_DARK_FOG) {
                    darkFogJustEnded = true;
                }

                raining = false;
                intensity = 0f;
                scheduleNext(false);
            }
        } else if (timer <= 0f) {
            raining = true;
            currentType = nextType;
            phaseTime = currentType == TYPE_DARK_FOG ? DARK_FOG_SEC : RAIN_SEC;
            timer = phaseTime;
            intensity = 0f;

            // Arah dan kemiringan angin diacak tiap hujan.
            slant = randomRange(-0.25f, 0.25f);
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

    // camX dan platforms: supaya hujan tahu di mana ada tanah.
    // playerX, playerY: posisi pemain di layar, pusat lingkaran pandang saat kabut gelap.
    public void draw(Canvas c, float camX, List<RectF> platforms, float playerX, float playerY) {
        if (intensity <= 0f) {
            return;
        }

        int alpha = Math.round(255f * intensity);
        boolean dark = currentType == TYPE_DARK_FOG;
        boolean foggy = currentType != TYPE_NORMAL;

        // Layar sedikit meredup saat hujan.
        int dimMax = dark ? MAX_DIM_ALPHA_DARK : MAX_DIM_ALPHA;
        dimPaint.setColor(Color.argb(Math.round(dimMax * intensity), 20, 30, 60));
        c.drawRect(0, 0, sw, sh, dimPaint);

        // Kabut: lapisan tipis lalu gumpalan yang melayang pelan
        // (sedikit bergeser mengikuti kamera supaya terasa ada kedalaman).
        if (foggy) {
            Paint haze = dark ? hazeDarkPaint : hazeWhitePaint;
            haze.setAlpha(alpha);
            c.drawRect(0, 0, sw, sh, haze);

            for (int i = 0; i < FOG_BLOBS; i++) {
                float span = sw + 2f * blobR[i];
                float px = (blobX[i] + fogTime * blobSpeed[i] - camX * 0.25f) % span;

                if (px < 0f) {
                    px += span;
                }

                Paint blob = dark ? blobDarkPaint[i] : blobWhitePaint[i];
                blob.setAlpha(alpha);

                c.save();
                c.translate(px - blobR[i], blobY[i]);
                c.drawCircle(0, 0, blobR[i], blob);
                c.restore();
            }
        }

        drawDrops(c, camX, platforms);

        // Kabut gelap: layar gelap, hanya sekitar pemain yang terlihat.
        // Pakai senter = lingkaran lebih luas dan tidak segelap tanpa senter.
        if (dark) {
            float radius = (VISION_NO_LIGHT + (VISION_LIGHT - VISION_NO_LIGHT) * lightLevel) * u;
            float outer = DARK_NO_LIGHT + (DARK_LIGHT - DARK_NO_LIGHT) * lightLevel;

            matrix.setScale(radius, radius);
            matrix.postTranslate(playerX, playerY);
            visionShader.setLocalMatrix(matrix);
            visionPaint.setShader(visionShader);
            visionPaint.setAlpha(Math.round(outer * intensity));
            c.drawRect(0, 0, sw, sh, visionPaint);

            // Cahaya hangat senter di sekitar pemain.
            if (lightLevel > 0.01f) {
                float glowRadius = radius * 0.9f;

                matrix.setScale(glowRadius, glowRadius);
                matrix.postTranslate(playerX, playerY);
                glowShader.setLocalMatrix(matrix);
                glowPaint.setShader(glowShader);
                glowPaint.setAlpha(Math.round(255f * lightLevel * intensity));
                c.drawRect(0, 0, sw, sh, glowPaint);
            }
        }
    }

    private void drawDrops(Canvas c, float camX, List<RectF> platforms) {
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
