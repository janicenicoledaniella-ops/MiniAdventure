package com.example.miniadventure;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class GameView extends SurfaceView implements Runnable {

    private static final String TAG = "GAMEDBG";
    private volatile String errorText;

    private static final int PLAYING = 0;
    private static final int GAME_OVER = 1;
    private static final int SELECT_CHARACTER = 2;

    private Bitmap gameOverBmp;
    private Bitmap retryBmp;
    private volatile int state = SELECT_CHARACTER;
    private volatile boolean startRequested;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean restartRequested;

    private final SurfaceHolder holder;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Player player = new Player();
    private final World world = new World();

    private final SharedPreferences preferences;

    private final Assets assets;
    private final SoundManager sounds;
    private final CharacterSelectScreen menu;

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

    // Suara jatuh dibunyikan saat pemain mulai tenggelam ke jurang (bukan saat sudah di dasar layar).
    // Tanah ada di y = 9 satuan, badan pemain setinggi 1. Angka lebih kecil = suara lebih awal.
    private static final float FALL_SOUND_Y = 8.6f;
    private boolean fallSoundPlayed;
    private int round;
    private int highScore;

    private float camX;
    private float speedMultiplier = 1f;

    public GameView(Context context) {
        super(context);

        holder = getHolder();
        assets = new Assets(context);
        sounds = new SoundManager(context);
        menu = new CharacterSelectScreen(assets);

        gameOverBmp = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.game_over
        );

        retryBmp = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.retry
        );

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

        assets.setBackgroundByRound(round);
        buildRound();

        state = PLAYING;

        // Musik latar mulai saat permainan dimulai.
        sounds.startMusic();
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

        fallSoundPlayed = false;

        camX = 0;
    }

    private void nextRound() {
        round++;

        // Kecepatan naik 10% dari kecepatan dasar setiap putaran.
        speedMultiplier = 1f + (round - 1) * 0.08f;

        assets.setBackgroundByRound(round);
        buildRound();
    }

    @Override
    public void run() {
        Log.d(TAG, "run: game thread mulai");

        try {
            runLoop();
        } catch (Throwable t) {
            // Tampilkan error di layar supaya tidak hanya hitam.
            Log.e(TAG, "Game thread crash", t);
            errorText = t.toString();
            showErrorForever();
        }
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void runLoop() {
        long last = System.nanoTime();

        while (running) {
            if (!holder.getSurface().isValid()) {
                last = System.nanoTime();
                sleepQuiet(5);
                continue;
            }

            if (!levelBuilt) {
                sw = getWidth();
                sh = getHeight();

                if (sw == 0 || sh == 0) {
                    last = System.nanoTime();
                    sleepQuiet(5);
                    continue;
                }

                u = sh / 12f;

                // Gambar layar loading dulu supaya layar tidak hitam.
                drawLoading("Loading...");

                levelBuilt = true;

                Log.d(TAG, "load gambar mulai, sw=" + sw + " sh=" + sh);
                assets.load(u);
                Log.d(TAG, "load gambar selesai");

                menu.layout(sw, sh, u);
                state = SELECT_CHARACTER;
                Log.d(TAG, "menu siap");

                last = System.nanoTime();
            }

            long now = System.nanoTime();

            float dt = Math.min(
                    (now - last) / 1_000_000_000f,
                    0.033f
            );

            last = now;

            if (startRequested) {
                startRequested = false;

                if (state == SELECT_CHARACTER) {
                    assets.setPlayerCharacter(menu.getSelected());
                    startNewGame();
                }
            }

            // Setelah game over, kembali ke layar pilih karakter.
            if (restartRequested) {
                restartRequested = false;
                clearInput();
                state = SELECT_CHARACTER;
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

    private void drawLoading(String text) {
        Canvas c = holder.lockCanvas();

        if (c == null) {
            return;
        }

        try {
            c.drawColor(Color.rgb(11, 26, 71));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(24f, u * 0.6f));
            c.drawText(text, sw / 2f, sh / 2f, paint);
        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }

    // Menampilkan pesan error di layar sampai app ditutup.
    private void showErrorForever() {
        while (running) {
            if (holder.getSurface().isValid()) {
                Canvas c = holder.lockCanvas();

                if (c != null) {
                    try {
                        c.drawColor(Color.rgb(90, 0, 0));
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(Color.WHITE);
                        paint.setTextAlign(Paint.Align.LEFT);
                        paint.setTextSize(36f);

                        String msg = "ERROR: " + errorText;
                        float y = 80f;

                        for (int i = 0; i < msg.length(); i += 60) {
                            c.drawText(
                                    msg.substring(i, Math.min(msg.length(), i + 60)),
                                    30f,
                                    y,
                                    paint
                            );
                            y += 46f;
                        }
                    } finally {
                        holder.unlockCanvasAndPost(c);
                    }
                }
            }

            sleepQuiet(200);
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
            // Suara lompat hanya bila lompat benar-benar terjadi (pemain di tanah).
            if (player.onGround) {
                sounds.playJump();
            }

            player.requestJump();
        }

        float previousBottom = player.y + player.h;

        player.update(dt, u, world.platforms);

        RectF p = player.getHitbox(u);

        // Pemain sudah lewat tepi tanah dan pasti jatuh: bunyikan suara sekarang.
        if (!fallSoundPlayed && !player.onGround && player.vy > 0
                && player.y > FALL_SOUND_Y * u) {
            fallSoundPlayed = true;

            // Selalu jatuh.mp3 (termasuk di nyawa terakhir).
            // Kalau nyawa terakhir, jatuh2.mp3 menyusul di finishGame().
            sounds.playFall();
        }

        if (player.hasFallen(u)) {
            loseLife(true);   // true = jatuh ke jurang
            return true;
        }

        int scoreBefore = world.getScore();
        world.collectCoins(p);

        if (world.getScore() > scoreBefore) {
            sounds.playCoin();
        }

        world.updateEnemies(dt, u);

        // Injak dari atas: musuh hilang dan pemain memantul.
        Enemy stomped = world.stompEnemy(
                p,
                previousBottom,
                p.bottom > previousBottom
        );

        if (stomped != null) {
            sounds.playEnemyDie();

            player.y = stomped.rect.top - player.h;
            player.vy = -10f * u;
            player.onGround = false;

            p = player.getHitbox(u);
        }

        // Tabrak samping atau bawah: nyawa berkurang.
        if (world.hitsEnemy(p)) {
            loseLife(false);  // false = kena musuh
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

    private void loseLife(boolean fell) {
        lives--;

        if (lives <= 0) {
            lives = 0;
            finishGame(fell);
        } else {
            // Skor, putaran, dan kecepatan tetap.
            // Koin yang diambil dan musuh yang kalah tetap hilang.
            resetPlayer();
        }
    }

    private void finishGame(boolean fell) {
        int totalScore = world.getScore();

        if (totalScore > highScore) {
            highScore = totalScore;

            preferences.edit()
                    .putInt("highScore", highScore)
                    .apply();
        }

        // Musik latar berhenti.
        sounds.stopMusic();

        if (fell) {
            // Urutan: jatuh.mp3 (sudah bunyi saat jatuh) -> jatuh2.mp3 -> game over.
            sounds.playFallFinal();
            sounds.playGameOverAfterFall();
        } else {
            // Kena musuh di nyawa terakhir: langsung suara game over.
            sounds.playGameOver();
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
            if (state == SELECT_CHARACTER) {
                menu.draw(c);
                return;
            }

            c.drawColor(Color.rgb(135, 206, 235));

            // Background
            assets.drawBackground(c, sw, sh, camX);

            c.save();
            c.translate(-camX, 0);

            // Tanah dan platform.
            for (RectF platform : world.platforms) {
                assets.drawPlatform(c, platform, u);
            }

            // Koin.
            for (RectF coin : world.coins) {
                assets.drawCoin(c, coin);
            }

            // Musuh.
            for (Enemy enemy : world.enemies) {
                assets.drawEnemy(c, enemy);
            }

            // Bendera.
            // Bendera.
            if (assets.flagBmp != null) {
                float flagHeight = 2.5f * u;

                float ratio = (float) assets.flagBmp.getWidth()
                        / assets.flagBmp.getHeight();

                float flagWidth = flagHeight * ratio;

                float flagBottom = world.goal.bottom;

                if (Math.abs(world.goal.bottom - 9f * u) < 0.1f) {
                    flagBottom += 0.55f * u;
                }

                float flagLeft = world.goal.centerX() - flagWidth / 2f;

                RectF flagDst = new RectF(
                        flagLeft,
                        flagBottom - flagHeight,
                        flagLeft + flagWidth,
                        flagBottom
                );

                c.drawBitmap(assets.flagBmp, null, flagDst, paint);
            }

            // Pemain.
            assets.drawPlayer(c, player);

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
        float y = u * 0.15f;

        // Ikon koin.
        RectF coinIcon = new RectF(
                u * 0.35f,
                y,
                u * 1.25f,
                y + u * 0.85f
        );

        assets.drawCoinIcon(c, coinIcon);

        // Jumlah koin.
        paint.setColor(Color.WHITE);
        paint.setTextSize(u * 0.48f);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        c.drawText(
                String.valueOf(world.getScore()),
                u * 1.35f,
                y + u * 0.65f,
                paint
        );

        // Ikon hati.
        Bitmap heart;

        if (lives >= 3) {
            heart = assets.heart1Bmp;
        } else if (lives == 2) {
            heart = assets.heart2Bmp;
        } else {
            heart = assets.heart3Bmp;
        }

        if (heart != null) {
            RectF heartRect = new RectF(
                    u * 2.8f,
                    y,
                    u * 5.2f,
                    y + u * 0.85f
            );

            c.drawBitmap(heart, null, heartRect, paint);
        }

        // Badge level di sebelah kanan hati.
        drawLevelBadge(c);
    }

    private void drawLevelBadge(Canvas c) {
        float x = u * 5.45f;
        float y = u * 0.15f;
        float w = u * 2.4f;
        float h = u * 0.85f;

        RectF badge = new RectF(
                x,
                y,
                x + w,
                y + h
        );

        // Background badge.
        // Background warna kayu.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(139, 90, 43));

        c.drawRoundRect(
                badge,
                u * 0.18f,
                u * 0.18f,
                paint
        );

        // Border cokelat tua.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.045f);
        paint.setColor(Color.rgb(85, 50, 25));

        c.drawRoundRect(
                badge,
                u * 0.18f,
                u * 0.18f,
                paint
        );

        paint.setStyle(Paint.Style.FILL);

        // Gambar medali berukuran kecil.
        Bitmap badgeBitmap = assets.getLevelBadge(round);

        if (badgeBitmap != null) {
            float iconSize = u * 0.65f;

            RectF iconRect = new RectF(
                    x + u * 0.10f,
                    y + (h - iconSize) / 2f,
                    x + u * 0.10f + iconSize,
                    y + (h + iconSize) / 2f
            );

            c.drawBitmap(
                    badgeBitmap,
                    null,
                    iconRect,
                    paint
            );
        }

        // Tulisan level.
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(u * 0.30f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        c.drawText(
                "LEVEL " + round,
                x + u * 1.55f,
                badge.centerY() + u * 0.11f,
                paint
        );

        paint.setTextAlign(Paint.Align.LEFT);
    }

    private RectF getLeftButtonRect() {
        float buttonSize = 1.4f * u;
        float centerX = sw * 0.125f;
        float top = sh - 1.75f * u;

        return new RectF(
                centerX - buttonSize / 2f,
                top,
                centerX + buttonSize / 2f,
                top + buttonSize
        );
    }

    private RectF getRightButtonRect() {
        float buttonSize = 1.4f * u;
        float centerX = sw * 0.375f;
        float top = sh - 1.75f * u;

        return new RectF(
                centerX - buttonSize / 2f,
                top,
                centerX + buttonSize / 2f,
                top + buttonSize
        );
    }

    private RectF getJumpButtonRect() {
        float buttonSize = 1.4f * u;
        float centerX = sw * 0.75f;
        float top = sh - 1.75f * u;

        return new RectF(
                centerX - buttonSize / 2f,
                top,
                centerX + buttonSize / 2f,
                top + buttonSize
        );
    }

    private void drawControls(Canvas c) {
        if (assets.leftArrowBmp != null) {
            c.drawBitmap(
                    assets.leftArrowBmp,
                    null,
                    getLeftButtonRect(),
                    paint
            );
        }

        if (assets.rightArrowBmp != null) {
            c.drawBitmap(
                    assets.rightArrowBmp,
                    null,
                    getRightButtonRect(),
                    paint
            );
        }

        if (assets.jumpArrowBmp != null) {
            c.drawBitmap(
                    assets.jumpArrowBmp,
                    null,
                    getJumpButtonRect(),
                    paint
            );
        }
    }

    private void drawGameOver(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(205, 10, 18, 35));
        c.drawRect(0, 0, sw, sh, paint);

        float panelWidth = Math.min(sw * 0.86f, 8.6f * u);
        float panelHeight = 8.0f * u;

        float panelLeft = (sw - panelWidth) / 2f;
        float panelTop = (sh - panelHeight) / 2f;

        RectF panel = new RectF(
                panelLeft,
                panelTop,
                panelLeft + panelWidth,
                panelTop + panelHeight
        );

        paint.setColor(Color.rgb(35, 48, 75));
        c.drawRoundRect(
                panel,
                0.4f * u,
                0.4f * u,
                paint
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.09f * u);
        paint.setColor(Color.rgb(245, 190, 75));

        c.drawRoundRect(
                panel,
                0.4f * u,
                0.4f * u,
                paint
        );

        paint.setStyle(Paint.Style.FILL);

        // Gambar GAME OVER.
        float bannerWidth = Math.min(panelWidth * 0.75f, 5.5f * u);
        float bannerHeight = 1.5f * u;

        RectF bannerRect = new RectF(
                sw / 2f - bannerWidth / 2f,
                panelTop + 0.25f * u,
                sw / 2f + bannerWidth / 2f,
                panelTop + 0.25f * u + bannerHeight
        );

        if (gameOverBmp != null) {
            c.drawBitmap(
                    gameOverBmp,
                    null,
                    bannerRect,
                    paint
            );
        } else {
            paint.setColor(Color.WHITE);
            paint.setTextSize(0.9f * u);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);

            c.drawText(
                    "GAME OVER",
                    sw / 2f,
                    panelTop + 1.3f * u,
                    paint
            );

            paint.setFakeBoldText(false);
        }

        // Badge level terakhir.
        drawGameOverLevelBadge(
                c,
                sw / 2f,
                panelTop + 2.05f * u
        );

        // Total koin.
        float coinSize = 0.85f * u;
        float coinY = panelTop + 3.65f * u;

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(0.48f * u);
        paint.setFakeBoldText(true);

        c.drawText(
                "TOTAL KOIN",
                sw / 2f,
                coinY,
                paint
        );

        RectF coinRect = new RectF(
                sw / 2f - 1.1f * u,
                coinY + 0.25f * u,
                sw / 2f - 1.1f * u + coinSize,
                coinY + 0.25f * u + coinSize
        );

        assets.drawCoinIcon(c, coinRect);

        paint.setColor(Color.rgb(255, 215, 90));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(0.75f * u);

        c.drawText(
                String.valueOf(world.getScore()),
                coinRect.right + 0.2f * u,
                coinRect.centerY() + 0.25f * u,
                paint
        );

        // Highest score.
        float highY = panelTop + 5.35f * u;

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(0.48f * u);

        c.drawText(
                "HIGHEST SCORE",
                sw / 2f,
                highY,
                paint
        );

        paint.setColor(Color.rgb(255, 215, 90));
        paint.setTextSize(0.75f * u);

        c.drawText(
                String.valueOf(highScore),
                sw / 2f,
                highY + 0.8f * u,
                paint
        );

        // Tombol retry.
        float retryWidth = 2.8f * u;
        float retryHeight = 1.15f * u;

        RectF retryRect = new RectF(
                sw / 2f - retryWidth / 2f,
                panelTop + 6.45f * u,
                sw / 2f + retryWidth / 2f,
                panelTop + 6.45f * u + retryHeight
        );

        if (retryBmp != null) {
            c.drawBitmap(
                    retryBmp,
                    null,
                    retryRect,
                    paint
            );
        } else {
            paint.setColor(Color.rgb(220, 155, 40));

            c.drawRoundRect(
                    retryRect,
                    0.2f * u,
                    0.2f * u,
                    paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(0.5f * u);

            c.drawText(
                    "RETRY",
                    sw / 2f,
                    retryRect.centerY() + 0.17f * u,
                    paint
            );
        }

        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGameOverLevelBadge(Canvas c, float centerX, float centerY) {
        float w = u * 3.0f;
        float h = u * 0.85f;

        RectF badge = new RectF(
                centerX - w / 2f,
                centerY - h / 2f,
                centerX + w / 2f,
                centerY + h / 2f
        );

        Bitmap badgeBitmap = assets.getLevelBadge(round);

        if (badgeBitmap != null) {
            c.drawBitmap(badgeBitmap, null, badge, paint);
        }

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(u * 0.38f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        c.drawText("LEVEL " + round, centerX, centerY + u * 0.13f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();

        if (state == SELECT_CHARACTER) {
            if (action == MotionEvent.ACTION_DOWN
                    && menu.onTouch(e.getX(), e.getY())) {
                startRequested = true;
            }
            return true;
        }

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

        RectF leftRect = getLeftButtonRect();
        RectF rightRect = getRightButtonRect();
        RectF jumpRect = getJumpButtonRect();

        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) {
                continue;
            }

            float x = e.getX(i);
            float y = e.getY(i);

            if (leftRect.contains(x, y)) {
                l = true;
            }

            if (rightRect.contains(x, y)) {
                r = true;
            }

            if (jumpRect.contains(x, y)) {
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

        sounds.onResume();

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
        sounds.onPause();
    }

    public void release() {
        sounds.release();
    }
}