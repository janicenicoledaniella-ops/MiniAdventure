
package com.example.miniadventure;

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

    private static final int PLAYING = 0;
    private static final int GAME_OVER = 1;
    private static final int WIN = 2;

    private int state = PLAYING;

    private Thread thread;
    private volatile boolean running;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float u;
    private int sw, sh;
    private boolean levelBuilt = false;

    private final Player player = new Player();

    private volatile boolean left;
    private volatile boolean right;
    private boolean jumpHeld;

    private int score, lives;
    private float camX;

    private final List<RectF> platforms = new ArrayList<>();
    private final List<RectF> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();

    private RectF goal;

    private static class Enemy {
        RectF rect;
        float minX, maxX, dir = 1;
        boolean alive = true;

        Enemy(RectF r, float minX, float maxX) {
            rect = r;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    public GameView(Context context) {
        super(context);
        holder = getHolder();
    }

    private void buildLevel() {
        platforms.clear();
        coins.clear();
        enemies.clear();

        float[][] ground = {
                {0, 15},
                {18, 12},
                {34, 16},
                {53, 20}
        };

        for (float[] g : ground) {
            platforms.add(new RectF(
                    g[0] * u,
                    9 * u,
                    (g[0] + g[1]) * u,
                    12 * u
            ));
        }

        float[][] floating = {
                {8, 6.5f, 4},
                {20, 6, 3},
                {24, 4.5f, 3},
                {38, 6.5f, 4},
                {44, 5, 3},
                {58, 6, 4}
        };

        for (float[] f : floating) {
            platforms.add(new RectF(
                    f[0] * u,
                    f[1] * u,
                    (f[0] + f[2]) * u,
                    (f[1] + 0.5f) * u
            ));
        }

        float[][] coinPos = {
                {5, 8}, {9, 5.5f}, {10, 5.5f},
                {16.5f, 6}, {21, 5}, {25, 3.5f},
                {26, 3.5f}, {32, 6}, {39, 5.5f},
                {45, 4}, {59, 5}, {60, 5}
        };

        for (float[] c : coinPos) {
            coins.add(new RectF(
                    c[0] * u,
                    c[1] * u,
                    (c[0] + 0.5f) * u,
                    (c[1] + 0.5f) * u
            ));
        }

        float[][] en = {
                {12, 10, 14},
                {24, 20, 28},
                {42, 36, 48},
                {60, 55, 70}
        };

        for (float[] e : en) {
            enemies.add(new Enemy(
                    new RectF(
                            e[0] * u,
                            8.2f * u,
                            (e[0] + 0.8f) * u,
                            9 * u
                    ),
                    e[1] * u,
                    e[2] * u
            ));
        }

        goal = new RectF(
                71 * u,
                6 * u,
                72 * u,
                9 * u
        );

        resetPlayer();
    }

    private void resetPlayer() {
        player.reset(u);

        left = false;
        right = false;
        jumpHeld = false;

        camX = 0;
    }

    private void restartGame() {
        score = 0;
        lives = 3;
        state = PLAYING;

        buildLevel();
    }

    @Override
    public void run() {
        long last = System.nanoTime();

        while (running) {
            if (!holder.getSurface().isValid()) {
                continue;
            }

            if (!levelBuilt) {
                sw = getWidth();
                sh = getHeight();

                if (sw == 0 || sh == 0) {
                    continue;
                }

                u = sh / 12f;
                levelBuilt = true;

                restartGame();
            }

            long now = System.nanoTime();

            float dt = Math.min(
                    (now - last) / 1_000_000_000f,
                    0.033f
            );

            last = now;

            if (state == PLAYING) {
                update(dt);
            }

            draw();
        }
    }

    private void update(float dt) {
        int direction = (right ? 1 : 0) - (left ? 1 : 0);

        player.setDirection(direction);
        player.update(dt, u, platforms);

        RectF p = player.getHitbox(u);

        if (player.hasFallen(u)) {
            loseLife();
            return;
        }

        for (int i = coins.size() - 1; i >= 0; i--) {
            if (RectF.intersects(p, coins.get(i))) {
                coins.remove(i);
                score += 10;
            }
        }

        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }

            e.rect.offset(e.dir * 2f * u * dt, 0);

            if (e.rect.left < e.minX) {
                e.dir = 1;
            }

            if (e.rect.right > e.maxX) {
                e.dir = -1;
            }

            if (RectF.intersects(p, e.rect)) {
                if (player.vy > 0 &&
                        p.bottom < e.rect.centerY()) {

                    e.alive = false;
                    player.vy = -10f * u;
                    score += 50;

                } else {
                    loseLife();
                    return;
                }
            }
        }

        if (RectF.intersects(p, goal)) {
            state = WIN;
        }

        camX = player.getCameraX(sw);
    }

    private void loseLife() {
        lives--;

        if (lives <= 0) {
            state = GAME_OVER;
        } else {
            resetPlayer();
        }
    }

    private void draw() {
        Canvas c = holder.lockCanvas();

        if (c == null) {
            return;
        }

        try {
            c.drawColor(Color.rgb(135, 206, 235));

            c.save();
            c.translate(-camX, 0);

            paint.setColor(Color.rgb(76, 160, 60));

            for (RectF r : platforms) {
                c.drawRect(r, paint);
            }

            paint.setColor(Color.YELLOW);

            for (RectF r : coins) {
                c.drawCircle(
                        r.centerX(),
                        r.centerY(),
                        r.width() / 2,
                        paint
                );
            }

            paint.setColor(Color.RED);

            for (Enemy e : enemies) {
                if (e.alive) {
                    c.drawRect(e.rect, paint);
                }
            }

            paint.setColor(Color.WHITE);
            c.drawRect(goal, paint);

            paint.setColor(Color.MAGENTA);

            c.drawRect(
                    goal.left,
                    goal.top,
                    goal.right + u,
                    goal.top + u,
                    paint
            );

            paint.setColor(Color.rgb(30, 80, 220));
            c.drawRect(player.getRect(), paint);

            c.restore();

            paint.setColor(Color.BLACK);
            paint.setTextSize(u * 0.7f);
            paint.setTextAlign(Paint.Align.LEFT);

            c.drawText(
                    "Skor: " + score + "   Nyawa: " + lives,
                    u * 0.5f,
                    u,
                    paint
            );

            paint.setColor(Color.argb(75, 0, 0, 0));

            c.drawRect(
                    0, sh - 2.5f * u,
                    sw * 0.25f, sh,
                    paint
            );

            c.drawRect(
                    sw * 0.25f, sh - 2.5f * u,
                    sw * 0.5f, sh,
                    paint
            );

            c.drawRect(
                    sw * 0.5f, sh - 2.5f * u,
                    sw, sh,
                    paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);

            c.drawText(
                    "<",
                    sw * 0.125f,
                    sh - u,
                    paint
            );

            c.drawText(
                    ">",
                    sw * 0.375f,
                    sh - u,
                    paint
            );

            c.drawText(
                    "LOMPAT",
                    sw * 0.75f,
                    sh - u,
                    paint
            );

            if (state != PLAYING) {
                paint.setColor(Color.argb(160, 0, 0, 0));
                c.drawRect(0, 0, sw, sh, paint);

                paint.setColor(Color.WHITE);
                paint.setTextSize(u * 1.4f);

                c.drawText(
                        state == WIN ? "KAMU MENANG!" : "GAME OVER",
                        sw / 2f,
                        sh / 2f,
                        paint
                );

                paint.setTextSize(u * 0.7f);

                c.drawText(
                        "Skor: " + score + " - Tap untuk main lagi",
                        sw / 2f,
                        sh / 2f + u * 1.2f,
                        paint
                );
            }

        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();

        if (state != PLAYING) {
            if (action == MotionEvent.ACTION_DOWN) {
                restartGame();
            }

            return true;
        }

        boolean newJumpPress =
                action == MotionEvent.ACTION_DOWN ||
                        action == MotionEvent.ACTION_POINTER_DOWN;

        boolean l = false;
        boolean r = false;
        boolean j = false;

        int skip = (
                action == MotionEvent.ACTION_UP ||
                        action == MotionEvent.ACTION_POINTER_UP ||
                        action == MotionEvent.ACTION_CANCEL
        ) ? e.getActionIndex() : -1;

        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) {
                continue;
            }

            float x = e.getX(i);

            if (x < sw * 0.25f) {
                l = true;
            } else if (x < sw * 0.5f) {
                r = true;
            } else {
                j = true;
            }
        }

        if (newJumpPress && j && !jumpHeld) {
            player.requestJump();
        }

        left = l;
        right = r;
        jumpHeld = j;

        if (action == MotionEvent.ACTION_CANCEL) {
            left = false;
            right = false;
            jumpHeld = false;
        }

        return true;
    }

    public void resume() {
        if (running) {
            return;
        }

        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;

        try {
            if (thread != null) {
                thread.join();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
