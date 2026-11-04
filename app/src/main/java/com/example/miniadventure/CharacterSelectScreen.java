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

    public static final int COUNT = 2;

    private static final long POP_NS = 300_000_000L;
    private static final long FLASH_NS = 220_000_000L;

    // Posisi vertikal (dalam satuan u = tinggi layar / 12).
    private static final float TITLE_Y = 1.75f;
    private static final float CHAR_TOP = 2.7f;
    private static final float PEDESTAL_Y = 7.65f;
    private static final float ARROW_Y = 5.3f;
    private static final float ARROW_X = 4.3f;

    private static final int[] BOKEH = {0xFFFFD36B, 0xFF7FB2FF, 0xFFFF8FB8};

    private final Assets assets;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private Typeface titleFace;
    private CornerPathEffect cornerEffect;

    private LinearGradient bgShader;
    private LinearGradient beamShader;
    private LinearGradient pedestalShader;
    private LinearGradient titleShader;
    private LinearGradient arrowShader;
    private LinearGradient buttonShader;
    private RadialGradient horizonShader;
    private RadialGradient spotShader;
    private RadialGradient vignetteShader;

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

        // Langit malam: biru tua ke ungu.
        bgShader = new LinearGradient(
                0, 0, 0, sh,
                new int[]{0xFF080C28, 0xFF17205A, 0xFF3B2A78},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);

        // Cahaya hangat dari bawah.
        horizonShader = new RadialGradient(
                cx, sh, sw * 0.5f,
                0x66FF9A4D, 0x00FF9A4D,
                Shader.TileMode.CLAMP);

        vignetteShader = new RadialGradient(
                cx, sh / 2f, sw * 0.62f,
                new int[]{0x00000000, 0xB0000000},
                new float[]{0.45f, 1f},
                Shader.TileMode.CLAMP);

        // Lampu sorot dan cahaya di belakang karakter.
        beamShader = new LinearGradient(
                0, 0, 0, PEDESTAL_Y * u,
                0x44FFF2C8, 0x08FFF2C8,
                Shader.TileMode.CLAMP);

        spotShader = new RadialGradient(
                cx, ARROW_Y * u, 4.6f * u,
                0xCCFFE08A, 0x00FFE08A,
                Shader.TileMode.CLAMP);

        pedestalShader = new LinearGradient(
                0, (PEDESTAL_Y - 0.42f) * u, 0, (PEDESTAL_Y + 0.42f) * u,
                0xFF7480CC, 0xFF343C80,
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
            c.drawColor(0xFF0B1030);
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
        drawBeamAndGlow(c, t);
        drawPedestal(c, t);
        drawOrbit(c, t, true);
        drawCharacter(c, t, sel);
        drawOrbit(c, t, false);
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
        p.setShader(bgShader);
        c.drawRect(0, 0, sw, sh, p);

        p.setShader(horizonShader);
        c.drawRect(0, sh * 0.4f, sw, sh, p);
        p.setShader(null);

        // Bulatan cahaya yang naik pelan.
        for (int i = 0; i < 14; i++) {
            float r = u * (0.12f + 0.28f * ((i * 37) % 10) / 10f);
            float span = sh + 2 * r;
            float speed = u * (0.25f + 0.08f * (i % 4));
            float off = ((i * 131) % 1000) / 1000f * span;
            float y = sh + r - ((t * speed + off) % span);
            float x = ((i * 3571) % 1000) / 1000f * sw
                    + (float) Math.sin(t * 0.6f + i) * 0.3f * u;

            p.setColor((BOKEH[i % 3] & 0x00FFFFFF) | (36 << 24));
            c.drawCircle(x, y, r, p);
        }

        // Bintang berkedip.
        p.setColor(Color.WHITE);
        for (int i = 0; i < 36; i++) {
            float sx = ((i * 7919) % 1000) / 1000f * sw;
            float sy = ((i * 6007) % 1000) / 1000f * sh * 0.7f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 2f + i * 1.7f);

            p.setAlpha((int) (40 + 190 * tw));
            c.drawCircle(sx, sy, u * (0.03f + 0.04f * tw), p);
        }

        // Pinggir layar digelapkan.
        p.setShader(vignetteShader);
        p.setAlpha(255);
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(null);
    }

    private void drawBeamAndGlow(Canvas c, float t) {
        float cx = sw / 2f;

        path.reset();
        path.moveTo(cx - 1.1f * u, 0);
        path.lineTo(cx + 1.1f * u, 0);
        path.lineTo(cx + 3.4f * u, PEDESTAL_Y * u);
        path.lineTo(cx - 3.4f * u, PEDESTAL_Y * u);
        path.close();

        p.setShader(beamShader);
        p.setAlpha(255);
        c.drawPath(path, p);

        p.setShader(spotShader);
        p.setAlpha((int) (170 + 40 * Math.sin(t * 2f)));
        c.drawCircle(cx, ARROW_Y * u, 4.6f * u, p);
        p.setShader(null);
    }

    private void drawPedestal(Canvas c, float t) {
        float cx = sw / 2f;
        float cy = PEDESTAL_Y * u;
        float rx = 2.8f * u;
        float ry = 0.42f * u;
        float thick = 0.28f * u;

        // Cahaya emas di sekeliling panggung.
        p.setStyle(Paint.Style.STROKE);
        float glow = 0.6f + 0.4f * (float) Math.sin(t * 2f);
        float[] widths = {0.55f, 0.32f, 0.16f};
        int[] alphas = {16, 28, 50};
        for (int i = 0; i < 3; i++) {
            p.setStrokeWidth(widths[i] * u);
            p.setColor(Color.argb((int) (alphas[i] * (0.6f + glow)), 255, 200, 90));
            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(rect, p);
        }
        p.setStyle(Paint.Style.FILL);

        // Sisi panggung (tebal).
        p.setColor(0xFF1B2257);
        rect.set(cx - rx, cy - ry + thick, cx + rx, cy + ry + thick);
        c.drawOval(rect, p);
        rect.set(cx - rx, cy, cx + rx, cy + thick);
        c.drawRect(rect, p);

        // Permukaan atas.
        p.setShader(pedestalShader);
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(rect, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.07f * u);
        p.setColor(Color.argb(230, 255, 214, 90));
        c.drawOval(rect, p);

        p.setStrokeWidth(0.04f * u);
        p.setColor(Color.argb(110, 255, 255, 255));
        rect.set(cx - rx * 0.72f, cy - ry * 0.72f, cx + rx * 0.72f, cy + ry * 0.72f);
        c.drawOval(rect, p);
        p.setStyle(Paint.Style.FILL);
    }

    // Percikan cahaya yang berputar mengelilingi panggung.
    private void drawOrbit(Canvas c, float t, boolean back) {
        float cx = sw / 2f;
        float cy = PEDESTAL_Y * u;

        for (int i = 0; i < 8; i++) {
            float a = t * 0.9f + i * (float) Math.PI / 4f;
            float s = (float) Math.sin(a);

            if ((s < 0) != back) {
                continue;
            }

            float x = cx + 3.0f * u * (float) Math.cos(a);
            float y = cy + 0.52f * u * s;
            float size = u * (0.06f + 0.04f * (0.5f + 0.5f * (float) Math.sin(t * 4f + i)));

            p.setColor(Color.argb(70, 255, 214, 90));
            c.drawCircle(x, y, size * 2.4f, p);
            p.setColor(Color.argb(240, 255, 244, 190));
            c.drawCircle(x, y, size, p);
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

    private void drawNameAndDots(Canvas c, int sel) {
        float cx = sw / 2f;

        // Label nama.
        float pillW = 4.4f * u;
        float pillH = 0.78f * u;
        float pillTop = 8.5f * u;

        rect.set(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH);
        p.setColor(Color.argb(215, 16, 22, 56));
        c.drawRoundRect(rect, pillH / 2f, pillH / 2f, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.05f * u);
        p.setColor(Color.rgb(255, 214, 90));
        c.drawRoundRect(rect, pillH / 2f, pillH / 2f, p);
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(titleFace);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(0.46f * u);
        p.setLetterSpacing(0.18f);
        p.setColor(Color.WHITE);
        c.drawText(Assets.CHAR_NAMES[sel].toUpperCase(),
                cx, pillTop + pillH / 2f + 0.16f * u, p);
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
        c.drawCircle(cx, cy, 0.9f * u * scale, p);

        float w = 0.5f * u;
        float h = 0.62f * u;

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