package com.example.miniadventure; // ganti sesuai package project kamu

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

public class GameView extends SurfaceView implements Runnable {

    // ====== STATE GAME ======
    private static final int PLAYING = 0, GAME_OVER = 1, WIN = 2;
    private int state = PLAYING;

    private Thread thread;
    private volatile boolean running;
    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ====== UKURAN ======
    private float u;          // 1 unit = tinggi layar / 12 (supaya game sama di semua HP)
    private int sw, sh;       // lebar & tinggi layar
    private boolean levelBuilt = false;

    // ====== PLAYER ======
    private float px, py, vx, vy, pw, ph;
    private boolean onGround;
    private volatile boolean left, right, jumpPressed;
    private int score, lives;
    private float camX;

    // ====== DUNIA ======
    private final List<RectF> platforms = new ArrayList<>();
    private final List<RectF> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private RectF goal;

    private static class Enemy {
        RectF rect; float minX, maxX, dir = 1; boolean alive = true;
        Enemy(RectF r, float minX, float maxX) { rect = r; this.minX = minX; this.maxX = maxX; }
    }

    public GameView(Context context) {
        super(context);
        holder = getHolder();
    }

    // ====== LEVEL (semua angka dalam satuan 'unit', edit di sini untuk bikin level baru) ======
    private void buildLevel() {
        platforms.clear(); coins.clear(); enemies.clear();

        // Tanah (x, lebar). Celah di antaranya = jurang
        float[][] ground = {{0, 15}, {18, 12}, {34, 16}, {53, 20}};
        for (float[] g : ground)
            platforms.add(new RectF(g[0] * u, 9 * u, (g[0] + g[1]) * u, 12 * u));

        // Platform melayang (x, y, lebar)
        float[][] floating = {{8, 6.5f, 4}, {20, 6, 3}, {24, 4.5f, 3}, {38, 6.5f, 4}, {44, 5, 3}, {58, 6, 4}};
        for (float[] f : floating)
            platforms.add(new RectF(f[0] * u, f[1] * u, (f[0] + f[2]) * u, (f[1] + 0.5f) * u));

        // Koin (x, y)
        float[][] coinPos = {{5, 8}, {9, 5.5f}, {10, 5.5f}, {16.5f, 6}, {21, 5}, {25, 3.5f},
                {26, 3.5f}, {32, 6}, {39, 5.5f}, {45, 4}, {59, 5}, {60, 5}};
        for (float[] c : coinPos)
            coins.add(new RectF(c[0] * u, c[1] * u, (c[0] + 0.5f) * u, (c[1] + 0.5f) * u));

        // Musuh (x awal, batas kiri, batas kanan) -- berjalan di atas tanah
        float[][] en = {{12, 10, 14}, {24, 20, 28}, {42, 36, 48}, {60, 55, 70}};
        for (float[] e : en)
            enemies.add(new Enemy(new RectF(e[0] * u, 8.2f * u, (e[0] + 0.8f) * u, 9 * u), e[1] * u, e[2] * u));

        goal = new RectF(71 * u, 6 * u, 72 * u, 9 * u); // bendera finish
        resetPlayer();
    }

    private void resetPlayer() {
        pw = 0.8f * u; ph = 1.0f * u;
        px = 1 * u; py = 7 * u; vx = vy = 0; camX = 0;
    }

    private void restartGame() {
        score = 0; lives = 3; state = PLAYING;
        buildLevel();
    }

    // ====== GAME LOOP ======
    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            if (!holder.getSurface().isValid()) continue;

            if (!levelBuilt) { // tunggu ukuran layar siap
                sw = getWidth(); sh = getHeight();
                if (sw == 0) continue;
                u = sh / 12f;
                levelBuilt = true;
                restartGame();
            }

            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1_000_000_000f, 0.033f);
            last = now;

            if (state == PLAYING) update(dt);
            draw();
        }
    }

    // ====== UPDATE ======
    private void update(float dt) {
        float speed = 6f * u, gravity = 30f * u, jumpPower = 15f * u;

        // input horizontal
        vx = 0;
        if (left) vx = -speed;
        if (right) vx = speed;

        // lompat
        if (jumpPressed && onGround) { vy = -jumpPower; onGround = false; }
        vy += gravity * dt;

        // gerak sumbu X lalu cek tabrakan
        px += vx * dt;
        if (px < 0) px = 0;
        RectF p = playerRect();
        for (RectF plat : platforms) {
            if (RectF.intersects(p, plat)) {
                if (vx > 0) px = plat.left - pw;
                else if (vx < 0) px = plat.right;
                p = playerRect();
            }
        }

        // gerak sumbu Y lalu cek tabrakan
        py += vy * dt;
        onGround = false;
        p = playerRect();
        for (RectF plat : platforms) {
            if (RectF.intersects(p, plat)) {
                if (vy > 0) { py = plat.top - ph; onGround = true; }
                else if (vy < 0) py = plat.bottom;
                vy = 0;
                p = playerRect();
            }
        }

        // jatuh ke jurang
        if (py > 14 * u) loseLife();

        // koin
        for (int i = coins.size() - 1; i >= 0; i--)
            if (RectF.intersects(p, coins.get(i))) { coins.remove(i); score += 10; }

        // musuh
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            e.rect.offset(e.dir * 2f * u * dt, 0);
            if (e.rect.left < e.minX) e.dir = 1;
            if (e.rect.right > e.maxX) e.dir = -1;

            if (RectF.intersects(p, e.rect)) {
                if (vy > 0 && p.bottom < e.rect.centerY()) { // diinjak dari atas
                    e.alive = false; vy = -10f * u; score += 50;
                } else {
                    loseLife(); break;
                }
            }
        }

        // finish
        if (RectF.intersects(p, goal)) state = WIN;

        // kamera mengikuti pemain
        camX = Math.max(0, px - sw * 0.35f);
    }

    private RectF playerRect() { return new RectF(px, py, px + pw, py + ph); }

    private void loseLife() {
        lives--;
        if (lives <= 0) state = GAME_OVER;
        else resetPlayer();
    }

    // ====== GAMBAR ======
    private void draw() {
        Canvas c = holder.lockCanvas();
        if (c == null) return;
        try {
            c.drawColor(Color.rgb(135, 206, 235)); // langit

            c.save();
            c.translate(-camX, 0);

            paint.setColor(Color.rgb(76, 160, 60)); // platform
            for (RectF r : platforms) c.drawRect(r, paint);

            paint.setColor(Color.YELLOW); // koin
            for (RectF r : coins) c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, paint);

            paint.setColor(Color.RED); // musuh
            for (Enemy e : enemies) if (e.alive) c.drawRect(e.rect, paint);

            paint.setColor(Color.WHITE); // bendera
            c.drawRect(goal, paint);
            paint.setColor(Color.MAGENTA);
            c.drawRect(goal.left, goal.top, goal.right + u, goal.top + u, paint);

            paint.setColor(Color.rgb(30, 80, 220)); // pemain
            c.drawRect(px, py, px + pw, py + ph, paint);

            c.restore();

            // HUD
            paint.setColor(Color.BLACK);
            paint.setTextSize(u * 0.7f);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText("Skor: " + score + "   Nyawa: " + lives, u * 0.5f, u, paint);

            // petunjuk tombol (kiri bawah = gerak, kanan = lompat)
            paint.setColor(Color.argb(60, 0, 0, 0));
            c.drawRect(0, sh - 2.5f * u, sw * 0.25f, sh, paint);
            c.drawRect(sw * 0.25f, sh - 2.5f * u, sw * 0.5f, sh, paint);
            c.drawRect(sw * 0.5f, 0, sw, sh, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("<", sw * 0.125f, sh - u, paint);
            c.drawText(">", sw * 0.375f, sh - u, paint);
            c.drawText("LOMPAT", sw * 0.75f, sh - u, paint);

            if (state != PLAYING) {
                paint.setColor(Color.argb(160, 0, 0, 0));
                c.drawRect(0, 0, sw, sh, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(u * 1.4f);
                c.drawText(state == WIN ? "KAMU MENANG!" : "GAME OVER", sw / 2f, sh / 2f, paint);
                paint.setTextSize(u * 0.7f);
                c.drawText("Skor: " + score + "  -  Tap untuk main lagi", sw / 2f, sh / 2f + u * 1.2f, paint);
            }
        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }

    // ====== INPUT SENTUH (multi-touch) ======
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();

        if (state != PLAYING) {
            if (action == MotionEvent.ACTION_DOWN) restartGame();
            return true;
        }

        boolean l = false, r = false, j = false;
        int skip = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) ? e.getActionIndex() : -1;

        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            float x = e.getX(i);
            if (x < sw * 0.25f) l = true;
            else if (x < sw * 0.5f) r = true;
            else j = true;
        }
        left = l; right = r; jumpPressed = j;
        return true;
    }

    // ====== LIFECYCLE ======
    public void resume() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException ignored) { }
    }
}