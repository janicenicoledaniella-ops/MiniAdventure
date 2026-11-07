package com.example.miniadventure;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Layar pilih karakter: tanpa gambar background, semuanya digambar lewat kode.
 * Panah kiri/kanan untuk ganti karakter, tombol START GAME untuk mulai.
 */
public class CharacterSelectScreen {

    public static final int COUNT = 5;

    private static final long POP_NS = 300_000_000L;
    private static final long FLASH_NS = 220_000_000L;

    // Posisi vertikal (dalam satuan u = tinggi layar / 12).
    private static final float TITLE_Y = 1.75f;
    private static final float CHAR_TOP = 2.7f;
    private static final float PEDESTAL_Y = 7.65f;
    private static final float ARROW_Y = 5.3f;
    private static final float ARROW_X = 4.3f;
    private static final float SIDE_GAP = 0.35f;   // jarak karakter samping ke lingkaran panah
    private static final float SIDE_SCALE = 1.0f;  // 1.0 = sama tinggi dengan karakter utama
    private static final int GHOST_ALPHA = 135;    // opacity karakter samping (0..255)

    // Warna latar: navy blue, terang di tengah dan makin gelap ke pinggir (gradasi melingkar).
    private static final int NAVY = 0xFF0B1A47;
    private static final int NAVY_CENTER = 0xFF1C3D8C;
    private static final int NAVY_EDGE = 0xFF040A22;

    private final Assets assets;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private Typeface titleFace;
    private CornerPathEffect cornerEffect;

    private RadialGradient bgShader;
    private LinearGradient beamShader;
    private RadialGradient poolShader;
    private RadialGradient lampShader;
    private LinearGradient titleShader;
    private LinearGradient arrowShader;
    private LinearGradient buttonShader;
    private RadialGradient spotShader;

    private float u;
    private int sw, sh;
    private boolean ready;

    private volatile int selected = 0;
    private volatile long changedAt = 0;
    private volatile int lastDir = 0;

    public CharacterSelectScreen(Assets assets) {
        this.assets = assets;
    }

    public int getSelected() {
        return selected;
    }

    // Dipanggil sekali setelah ukuran layar diketahui.
    public void layout(int width, int height, float unit) {
        sw = width;
        sh = height;
        u = unit;

        float cx = sw / 2f;

        titleFace = Typeface.create("sans-serif-black", Typeface.NORMAL);
        cornerEffect = new CornerPathEffect(0.14f * u);

        // Lampu sorot dan cahaya di belakang karakter.

        bgShader = new RadialGradient(
                cx, sh * 0.5f,
                Math.max(sw * 0.58f, sh * 1.0f),
                new int[]{
                        NAVY_CENTER,
                        NAVY_CENTER,
                        NAVY,
                        NAVY,
                        NAVY_EDGE
                },
                new float[]{
                        0f,
                        0.25f,
                        0.50f,
                        0.78f,
                        1f
                },
                Shader.TileMode.CLAMP
        );


        // Kerucut lampu sorot: terang di atas, memudar ke bawah.
        beamShader = new LinearGradient(
                0, 0, 0, PEDESTAL_Y * u,
                0x88FFF4D0, 0x00FFF4D0,
                Shader.TileMode.CLAMP);

        // Cahaya hangat tepat di belakang karakter.
        spotShader = new RadialGradient(
                cx, ARROW_Y * u, 4.6f * u,
                0x99FFE08A, 0x00FFE08A,
                Shader.TileMode.CLAMP);

        // Kolam cahaya di lantai (digepengkan saat digambar).
        poolShader = new RadialGradient(
                cx, PEDESTAL_Y * u, 3.7f * u,
                new int[]{0xE6FFF6D8, 0x66FFE9A8, 0x00FFE9A8},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);

        // Sumber lampu di tepi atas layar.
        lampShader = new RadialGradient(
                cx, 0, 2.4f * u,
                0xCCFFFBE8, 0x00FFFBE8,
                Shader.TileMode.CLAMP);

        titleShader = new LinearGradient(
                0, (TITLE_Y - 0.55f) * u, 0, (TITLE_Y + 0.08f) * u,
                0xFFFFF3A8, 0xFFFFB300,
                Shader.TileMode.CLAMP);

        arrowShader = new LinearGradient(
                0, (ARROW_Y - 0.65f) * u, 0, (ARROW_Y + 0.65f) * u,
                0xFFFFE27A, 0xFFFF9F1C,
                Shader.TileMode.CLAMP);

        RectF b = startRect();
        buttonShader = new LinearGradient(
                0, b.top, 0, b.bottom,
                0xFFFFD35A, 0xFFF2800C,
                Shader.TileMode.CLAMP);

        ready = true;
    }

    // ---------- Area sentuh ----------

    private RectF arrowRect(boolean leftSide) {
        float size = 1.8f * u;
        float cx = sw / 2f + (leftSide ? -ARROW_X : ARROW_X) * u;
        float cy = ARROW_Y * u;

        return new RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }

    private RectF startRect() {
        float w = 6f * u;
        float h = 1.1f * u;
        float top = 10.0f * u;

        return new RectF(sw / 2f - w / 2f, top, sw / 2f + w / 2f, top + h);
    }

    /**
     * Dipanggil saat layar disentuh.
     * Mengembalikan true kalau tombol START GAME ditekan.
     */
    public boolean onTouch(float x, float y) {
        if (!ready) {
            return false;
        }

        RectF left = arrowRect(true);
        RectF right = arrowRect(false);
        left.inset(-0.6f * u, -0.6f * u);
        right.inset(-0.6f * u, -0.6f * u);

        if (left.contains(x, y)) {
            change(-1);
            return false;
        }

        if (right.contains(x, y)) {
            change(1);
            return false;
        }

        return startRect().contains(x, y);
    }

    private void change(int dir) {
        selected = (selected + COUNT + dir) % COUNT;
        lastDir = dir;
        changedAt = System.nanoTime();
    }

    // ---------- Gambar ----------

    public void draw(Canvas c) {
        if (!ready) {
            c.drawColor(NAVY);
            return;
        }

        float t = (float) ((System.nanoTime() / 1e9) % 628.0);
        float cx = sw / 2f;
        int sel = selected;

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShader(null);
        p.setPathEffect(null);

        drawBackground(c, t);
        drawSpotlight(c, t);
        drawSideGhosts(c, t, sel);
        drawCharacter(c, t, sel);
        drawNameAndDots(c, sel);

        drawArrow(c, true, t);
        drawArrow(c, false, t);

        drawTitle(c, t);
        drawStartButton(c, t);

        p.setLetterSpacing(0f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShader(null);
        p.setPathEffect(null);
    }

    private void drawBackground(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(bgShader);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(null);
    }

    // Lampu sorot dari atas: kerucut cahaya berlapis, lampu di atas, dan kolam cahaya di lantai.

    private void drawSpotlight(Canvas c, float t) {
        float cx = sw / 2f;
        float floorY = PEDESTAL_Y * u;

        float flicker = 0.96f + 0.04f * (float) Math.sin(t * 1.5f);

        // Cahaya utama di belakang karakter, menyebar sangat halus.
        p.setStyle(Paint.Style.FILL);
        p.setShader(spotShader);
        p.setAlpha((int) (180 * flicker));
        c.drawCircle(cx, ARROW_Y * u, 4.6f * u, p);

        // Satu kerucut cahaya dengan tepi transparan.
        p.setShader(new RadialGradient(
                cx, 0,
                8.5f * u,
                new int[]{
                        0x20FFF4D0,
                        0x12FFF4D0,
                        0x00FFF4D0
                },
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));

        path.reset();
        path.moveTo(cx - 1.5f * u, 0);
        path.lineTo(cx + 1.5f * u, 0);
        path.lineTo(cx + 5.0f * u, floorY);
        path.lineTo(cx - 5.0f * u, floorY);
        path.close();

        c.drawPath(path, p);

        // Lampu bagian atas dibuat lebih lembut.
        p.setShader(lampShader);
        p.setAlpha(180);
        c.drawCircle(cx, 0, 2.4f * u, p);

        // Cahaya lantai dengan transisi transparan.
        c.save();
        c.scale(1f, 0.24f, cx, floorY);

        p.setShader(poolShader);
        p.setAlpha((int) (180 * flicker));
        c.drawCircle(cx, floorY, 3.7f * u, p);

        c.restore();

        p.setShader(null);
        p.setAlpha(255);
    }

    // Gambar karakter sebelumnya (kiri) dan berikutnya (kanan) dengan opacity rendah,
    // di samping tombol panah.
    private void drawSideGhosts(Canvas c, float t, int sel) {
        float cx = sw / 2f;
        float feetY = PEDESTAL_Y * u;
        float arrowEdge = (ARROW_X + 0.95f) * u;   // tepi luar lingkaran panah

        for (int side = -1; side <= 1; side += 2) {
            int idx = (sel + COUNT + side) % COUNT;

            // Posisi dihitung dari tepi panah, jadi jaraknya sama untuk semua karakter.
            float halfW = assets.getCharacterPreviewWidth(idx) * SIDE_SCALE / 2f;
            float x = cx + side * (arrowEdge + SIDE_GAP * u + halfW);

            // Lewati kalau keluar layar.
            if (x - halfW < 0 || x + halfW > sw) {
                continue;
            }

            float bob = (float) Math.sin(t * 1.8f + (side > 0 ? 1.2f : 0f)) * 0.07f * u;
            assets.drawCharacterGhost(c, idx, x, feetY - bob, SIDE_SCALE, GHOST_ALPHA);
        }
    }

    private void drawCharacter(Canvas c, float t, int sel) {
        float cx = sw / 2f;
        float feetY = PEDESTAL_Y * u;
        float bob = (float) Math.sin(t * 2.2f) * 0.1f * u;

        // Bayangan di panggung.
        float shadowRx = 1.4f * u * (1f - bob / u * 0.8f);
        p.setColor(Color.argb(95, 0, 0, 0));
        rect.set(cx - shadowRx, feetY - 0.18f * u, cx + shadowRx, feetY + 0.2f * u);
        c.drawOval(rect, p);

        // "Pop" sebentar saat karakter diganti.
        long elapsed = System.nanoTime() - changedAt;
        float pop = (changedAt != 0 && elapsed >= 0 && elapsed < POP_NS)
                ? 1f - elapsed / (float) POP_NS : 0f;
        float scale = 1f + 0.2f * pop * pop;

        c.save();
        c.scale(scale, scale, cx, feetY);
        assets.drawCharacterPreview(c, sel, cx, CHAR_TOP * u - bob);
        c.restore();
    }

    // Papan kayu: bayangan, serat, tepi cokelat tua, dan paku di kiri-kanan.
    private void drawWoodSign(Canvas c, float left, float top, float right, float bottom) {
        float rad = 0.2f * u;

        rect.set(left, top + 0.1f * u, right, bottom + 0.1f * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(rect, rad, rad, p);

        rect.set(left, top, right, bottom);
        p.setColor(0xFF9A6234);
        c.drawRoundRect(rect, rad, rad, p);

        c.save();
        path.reset();
        path.addRoundRect(rect, rad, rad, Path.Direction.CW);
        c.clipPath(path);
        float h = bottom - top;
        p.setColor(0x26000000);
        c.drawRect(left, top + h * 0.34f, right, top + h * 0.34f + 0.04f * u, p);
        c.drawRect(left, top + h * 0.68f, right, top + h * 0.68f + 0.04f * u, p);
        p.setColor(0x33FFFFFF);
        c.drawRect(left, top, right, top + 0.09f * u, p);
        c.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.07f * u);
        p.setColor(0xFF4A2A0C);
        c.drawRoundRect(rect, rad, rad, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(0xFFE6C27A);
        c.drawCircle(left + 0.28f * u, (top + bottom) / 2f, 0.07f * u, p);
        c.drawCircle(right - 0.28f * u, (top + bottom) / 2f, 0.07f * u, p);
    }

    private void drawNameAndDots(Canvas c, int sel) {
        float cx = sw / 2f;

        // Label nama: papan kayu.
        float pillW = 4.4f * u;
        float pillH = 0.78f * u;
        float pillTop = 8.5f * u;

        drawWoodSign(c, cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH);

        p.setTypeface(titleFace);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(0.46f * u);
        p.setLetterSpacing(0.18f);

        String name = Assets.CHAR_NAMES[sel].toUpperCase();
        float ty = pillTop + pillH / 2f + 0.16f * u;

        p.setColor(0x88000000);
        c.drawText(name, cx + 0.03f * u, ty + 0.04f * u, p);
        p.setColor(0xFFFFF1C2);
        c.drawText(name, cx, ty, p);
        p.setLetterSpacing(0f);

        // Titik penanda karakter ke berapa.
        float dotsY = 9.58f * u;
        float gap = 0.5f * u;

        for (int i = 0; i < COUNT; i++) {
            float dx = cx + (i - (COUNT - 1) / 2f) * gap;
            boolean on = i == sel;

            p.setColor(on ? Color.rgb(255, 214, 90) : Color.argb(110, 255, 255, 255));
            c.drawCircle(dx, dotsY, u * (on ? 0.13f : 0.09f), p);
        }
    }

    private void drawArrow(Canvas c, boolean left, float t) {
        RectF hit = arrowRect(left);

        float wave = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        float nudge = (left ? -1f : 1f) * 0.08f * u * wave;
        float cx = hit.centerX() + nudge;
        float cy = hit.centerY();

        // Menyala sebentar saat panah ini baru ditekan.
        long elapsed = System.nanoTime() - changedAt;
        boolean mine = lastDir == (left ? -1 : 1);
        float flash = (mine && changedAt != 0 && elapsed >= 0 && elapsed < FLASH_NS)
                ? 1f - elapsed / (float) FLASH_NS : 0f;

        float scale = (1f + 0.04f * (float) Math.sin(t * 3f)) * (1f + 0.25f * flash);

        // Cahaya bulat di belakang.
        p.setColor(Color.argb((int) (38 + 90 * flash), 255, 214, 100));
        c.drawCircle(cx, cy, 0.75f * u * scale, p);

        float w = 0.4f * u;
        float h = 0.5f * u;

        path.reset();
        if (left) {
            path.moveTo(cx - w, cy);
            path.lineTo(cx + w, cy - h);
            path.lineTo(cx + w, cy + h);
        } else {
            path.moveTo(cx + w, cy);
            path.lineTo(cx - w, cy - h);
            path.lineTo(cx - w, cy + h);
        }
        path.close();

        c.save();
        c.scale(scale, scale, cx, cy);
        p.setPathEffect(cornerEffect);

        // Bayangan.
        c.save();
        c.translate(0, 0.06f * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(120, 0, 0, 0));
        c.drawPath(path, p);
        c.restore();

        // Garis tepi cokelat tua.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.15f * u);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF5A2D00);
        c.drawPath(path, p);

        // Isi emas bergradasi.
        p.setStyle(Paint.Style.FILL);
        p.setShader(arrowShader);
        c.drawPath(path, p);
        p.setShader(null);

        // Kilau tipis di tepi.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.04f * u);
        p.setColor(Color.argb(150, 255, 255, 255));
        c.drawPath(path, p);

        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private void drawTitle(Canvas c, float t) {
        float cx = sw / 2f;
        float ty = TITLE_Y * u;
        String title = "CHOOSE YOUR CHARACTER";

        p.setTypeface(titleFace);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(0.68f * u);
        p.setLetterSpacing(0.06f);

        // Bayangan, outline, lalu isi emas.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(150, 0, 0, 0));
        c.drawText(title, cx + 0.05f * u, ty + 0.07f * u, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.15f * u);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF5A2D00);
        c.drawText(title, cx, ty, p);

        p.setStyle(Paint.Style.FILL);
        p.setShader(titleShader);
        c.drawText(title, cx, ty, p);
        p.setShader(null);

        // Garis hiasan di kiri-kanan judul.
        float halfW = p.measureText(title) / 2f;
        float lineY = ty - 0.22f * u;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.05f * u);
        p.setColor(Color.argb(200, 255, 214, 90));
        c.drawLine(cx - halfW - 3f * u, lineY, cx - halfW - 0.5f * u, lineY, p);
        c.drawLine(cx + halfW + 0.5f * u, lineY, cx + halfW + 3f * u, lineY, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - halfW - 3f * u, lineY, 0.1f * u, p);
        c.drawCircle(cx + halfW + 3f * u, lineY, 0.1f * u, p);
        p.setLetterSpacing(0f);
    }

    private void drawStartButton(Canvas c, float t) {
        RectF btn = startRect();
        float rad = 0.35f * u;
        float pulse = 1f + 0.03f * (float) Math.sin(t * 3.5f);

        c.save();
        c.scale(pulse, pulse, btn.centerX(), btn.centerY());

        // Bayangan.
        rect.set(btn);
        rect.offset(0, 0.1f * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(130, 0, 0, 0));
        c.drawRoundRect(rect, rad, rad, p);

        // Badan tombol.
        p.setShader(buttonShader);
        c.drawRoundRect(btn, rad, rad, p);
        p.setShader(null);

        // Kilatan cahaya yang lewat tiap beberapa detik.
        path.reset();
        path.addRoundRect(btn, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(path);

        float phase = (t % 2.6f) / 2.6f;
        if (phase < 0.5f) {
            float sx = btn.left - 1.2f * u + (phase / 0.5f) * (btn.width() + 2.4f * u);

            path.reset();
            path.moveTo(sx + 0.4f * u, btn.top);
            path.lineTo(sx + 1.0f * u, btn.top);
            path.lineTo(sx + 0.6f * u, btn.bottom);
            path.lineTo(sx, btn.bottom);
            path.close();

            p.setColor(Color.argb(95, 255, 255, 255));
            c.drawPath(path, p);
        }
        c.restore();

        // Kilau bagian atas.
        rect.set(btn.left + 0.1f * u, btn.top + 0.06f * u,
                btn.right - 0.1f * u, btn.centerY());
        p.setColor(Color.argb(60, 255, 255, 255));
        c.drawRoundRect(rect, 0.28f * u, 0.28f * u, p);

        // Garis tepi putih.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.055f * u);
        p.setColor(Color.argb(235, 255, 255, 255));
        c.drawRoundRect(btn, rad, rad, p);
        p.setStyle(Paint.Style.FILL);

        // Ikon play + tulisan START GAME.
        p.setTypeface(titleFace);
        p.setTextSize(0.5f * u);
        p.setLetterSpacing(0.12f);

        String label = "START GAME";
        float textW = p.measureText(label);
        float iconW = 0.38f * u;
        float gap = 0.3f * u;
        float startX = btn.centerX() - (iconW + gap + textW) / 2f;
        float baseY = btn.centerY() + 0.18f * u;
        float cy = btn.centerY();

        p.setTextAlign(Paint.Align.LEFT);

        for (int pass = 0; pass < 2; pass++) {
            float o = pass == 0 ? 0.035f * u : 0f;
            p.setColor(pass == 0 ? Color.argb(150, 90, 40, 0) : Color.WHITE);

            path.reset();
            path.moveTo(startX + o, cy - 0.22f * u + o);
            path.lineTo(startX + iconW + o, cy + o);
            path.lineTo(startX + o, cy + 0.22f * u + o);
            path.close();
            c.drawPath(path, p);

            c.drawText(label, startX + iconW + gap + o, baseY + o, p);
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0f);

        c.restore();
    }
}