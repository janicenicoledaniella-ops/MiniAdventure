package com.example.miniadventure;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements Runnable {

    private static final int PLAYING = 0;
    private static final int GAME_OVER = 1;

    private volatile int state = PLAYING;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean restartRequested;

    private final SurfaceHolder holder;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Player player = new Player();
    private final World world = new World();

    private final SharedPreferences preferences;

    // Menghubungkan input layar dengan game thread.
    private final Object inputLock = new Object();

    private boolean left;
    private boolean right;
    private boolean jumpHeld;
    private boolean jumpRequested;

    private float u;
    private int sw, sh;

    private boolean levelBuilt;

    private int lives;
    private int round;
    private int highScore;

    private float camX;
    private float speedMultiplier = 1f;

    public GameView(Context context) {
        super(context);

        holder = getHolder();

        preferences = context.getSharedPreferences(
                "MiniAdventureData",
                Context.MODE_PRIVATE
        );

        highScore = preferences.getInt("highScore", 0);
    }

    private void clearInput() {
        synchronized (inputLock) {
            left = false;
            right = false;
            jumpHeld = false;
            jumpRequested = false;
        }
    }

    // Permainan baru: skor, nyawa, dan putaran direset.
    private void startNewGame() {
        world.resetScore();

        lives = 3;
        round = 1;
        speedMultiplier = 1f;

        buildRound();

        state = PLAYING;
    }

    // Putaran baru: skor dan nyawa tidak direset.
    private void buildRound() {
        // World membuat susunan baru berdasarkan nomor putaran.
    // Kecepatan musuh juga diatur oleh World.
        world.buildLevel(u, round);

        resetPlayer();
    }

    private void resetPlayer() {
        clearInput();

        player.reset(u);
        player.setSpeedMultiplier(1f);

        camX = 0;
    }

    private void nextRound() {
        round++;

        // Kecepatan naik 10% dari kecepatan dasar setiap putaran.
        speedMultiplier = 1f + (round - 1) * 0.08f;

        buildRound();
    }

    @Override
    public void run() {
        long last = System.nanoTime();

        while (running) {
            if (!holder.getSurface().isValid()) {
                last = System.nanoTime();
                continue;
            }

            if (!levelBuilt) {
                sw = getWidth();
                sh = getHeight();

                if (sw == 0 || sh == 0) {
                    last = System.nanoTime();
                    continue;
                }

                u = sh / 12f;
                levelBuilt = true;

                startNewGame();

                last = System.nanoTime();
            }

            long now = System.nanoTime();

            float dt = Math.min(
                    (now - last) / 1_000_000_000f,
                    0.033f
            );

            last = now;

            if (restartRequested) {
                restartRequested = false;
                startNewGame();
            }

            if (state == PLAYING) {
                // Langkah kecil untuk menjaga ketelitian tabrakan.
                int steps = Math.max(
                        1,
                        (int) Math.ceil(
                                dt * speedMultiplier / (1f / 120f)
                        )
                );

                float stepDt = dt / steps;

                for (int i = 0; i < steps; i++) {
                    if (state != PLAYING) {
                        break;
                    }

                    if (update(stepDt)) {
                        break;
                    }
                }
            }

            draw();
        }
    }

    // True berarti terjadi perpindahan putaran atau kehilangan nyawa.
    private boolean update(float dt) {
        int direction;
        boolean jump;

        synchronized (inputLock) {
            direction = (right ? 1 : 0) - (left ? 1 : 0);

            jump = jumpRequested;
            jumpRequested = false;
        }

        player.setDirection(direction);

        if (jump) {
            player.requestJump();
        }

        float previousBottom = player.y + player.h;

        player.update(dt, u, world.platforms);

        RectF p = player.getHitbox(u);

        if (player.hasFallen(u)) {
            loseLife();
            return true;
        }

        world.collectCoins(p);
        world.updateEnemies(dt, u);

        // Injak dari atas: musuh hilang dan pemain memantul.
        Enemy stomped = world.stompEnemy(
                p,
                previousBottom,
                p.bottom > previousBottom
        );

        if (stomped != null) {
            player.y = stomped.rect.top - player.h;
            player.vy = -10f * u;
            player.onGround = false;

            p = player.getHitbox(u);
        }

        // Tabrak samping atau bawah: nyawa berkurang.
        if (world.hitsEnemy(p)) {
            loseLife();
            return true;
        }

        // Bendera langsung memulai putaran berikutnya.
        if (RectF.intersects(p, world.goal)) {
            nextRound();
            return true;
        }

        camX = player.getCameraX(sw);

        return false;
    }

    private void loseLife() {
        lives--;

        if (lives <= 0) {
            lives = 0;
            finishGame();
        } else {
            // Skor, putaran, dan kecepatan tetap.
            // Koin yang diambil dan musuh yang kalah tetap hilang.
            resetPlayer();
        }
    }

    private void finishGame() {
        int totalScore = world.getScore();

        if (totalScore > highScore) {
            highScore = totalScore;

            preferences.edit()
                    .putInt("highScore", highScore)
                    .apply();
        }

        clearInput();
        state = GAME_OVER;
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

            // Tanah dan platform.
            paint.setColor(Color.rgb(76, 160, 60));

            for (RectF platform : world.platforms) {
                c.drawRect(platform, paint);
            }

            // Koin.
            paint.setColor(Color.YELLOW);

            for (RectF coin : world.coins) {
                c.drawCircle(
                        coin.centerX(),
                        coin.centerY(),
                        coin.width() / 2,
                        paint
                );
            }

            // Musuh.
            paint.setColor(Color.RED);

            for (Enemy enemy : world.enemies) {
                c.drawRect(enemy.rect, paint);
            }

            // Bendera.
            paint.setColor(Color.WHITE);
            c.drawRect(world.goal, paint);

            paint.setColor(Color.MAGENTA);

            c.drawRect(
                    world.goal.left,
                    world.goal.top,
                    world.goal.right + u,
                    world.goal.top + u,
                    paint
            );

            // Pemain.
            paint.setColor(Color.rgb(30, 80, 220));
            c.drawRect(player.getRect(), paint);

            c.restore();

            drawHud(c);
            drawControls(c);

            if (state == GAME_OVER) {
                drawGameOver(c);
            }
        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }

    private void drawHud(Canvas c) {
        paint.setColor(Color.BLACK);
        paint.setTextSize(u * 0.55f);
        paint.setTextAlign(Paint.Align.LEFT);

        c.drawText(
                "Skor: " + world.getScore()
                        + "   Nyawa: " + lives
                        + "   Putaran: " + round,
                u * 0.5f,
                u,
                paint
        );
    }

    private void drawControls(Canvas c) {
        paint.setColor(Color.argb(75, 0, 0, 0));

        c.drawRect(
                0,
                sh - 2.5f * u,
                sw * 0.25f,
                sh,
                paint
        );

        c.drawRect(
                sw * 0.25f,
                sh - 2.5f * u,
                sw * 0.5f,
                sh,
                paint
        );

        c.drawRect(
                sw * 0.5f,
                sh - 2.5f * u,
                sw,
                sh,
                paint
        );

        paint.setColor(Color.WHITE);
        paint.setTextSize(u * 0.7f);
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
    }

    private void drawGameOver(Canvas c) {
        paint.setColor(Color.argb(190, 0, 0, 0));
        c.drawRect(0, 0, sw, sh, paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(u * 1.1f);

        c.drawText(
                "GAME OVER",
                sw / 2f,
                sh / 2f - 1.5f * u,
                paint
        );

        paint.setTextSize(u * 0.7f);

        c.drawText(
                "Total Score: " + world.getScore(),
                sw / 2f,
                sh / 2f,
                paint
        );

        c.drawText(
                "High Score: " + highScore,
                sw / 2f,
                sh / 2f + u,
                paint
        );

        paint.setTextSize(u * 0.55f);

        c.drawText(
                "Tap untuk main lagi",
                sw / 2f,
                sh / 2f + 2.2f * u,
                paint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action;
        action = e.getActionMasked();

        if (state == GAME_OVER) {
            if (action == MotionEvent.ACTION_DOWN) {
                restartRequested = true;
            }

            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            clearInput();
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
                        action == MotionEvent.ACTION_POINTER_UP
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

        synchronized (inputLock) {
            if (newJumpPress && j && !jumpHeld) {
                jumpRequested = true;
            }

            left = l;
            right = r;
            jumpHeld = j;
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

        clearInput();
    }
}