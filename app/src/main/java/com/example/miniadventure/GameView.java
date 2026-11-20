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
    private final Rain rain = new Rain();
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

    // ---- Treasure (peti harta) ----
    private static final int MAX_LIVES = 3;               // batas nyawa (sama dengan 3 hati di HUD)
    // Speed treasure = mode dash: 5 detik tanpa jurang, musuh, dan batu; kecepatan terus naik.
    private static final float DASH_SECONDS = 5f;        // lama mode dash
    private static final float DASH_START_MULT = 1.5f;   // kecepatan awal dash
    private static final float DASH_MAX_MULT = 2.5f;     // kecepatan maksimal dash
    private static final float DASH_ACCEL = 0.2f;        // tambahan kecepatan per detik saat dash
    private static final float DASH_GRACE_SECONDS = 1f;  // kebal sebentar setelah dash selesai
    private static final int COIN_REWARD = 100;           // koin dari coin treasure
    private static final long TOAST_NS = 2_600_000_000L;  // lama tampilan "kamu dapat ..."

    private final java.util.Random treasureRandom = new java.util.Random();
    private float dashTimeLeft;          // sisa detik dash (0 = tidak dash)
    private float dashMult = DASH_START_MULT;
    private float graceLeft;             // sisa detik kebal setelah dash
    private boolean virtualGround;       // tanah lurus pengganti jurang (aktif saat dash)
    private boolean hasFlashlight;
    private int playerFacing = 1;  // arah hadap pemain: 1 kanan, -1 kiri
    private Bitmap flashlightBmp;
    private int toastType = -1;
    private long toastStartNs;
    private String toastMain = "";
    private String toastSub = "";

    // Layar loading + pemuatan gambar di thread terpisah.
    private final LoadingScreen loadingScreen;
    private Thread loaderThread;
    private volatile boolean assetsLoaded;
    private volatile Throwable loadError;

    public GameView(Context context) {
        super(context);

        holder = getHolder();
        assets = new Assets(context);
        sounds = new SoundManager(context);
        menu = new CharacterSelectScreen(assets);
        loadingScreen = new LoadingScreen(context);

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
        rain.reset();
        hasFlashlight = false;
        lives = 3;
        round = 1;
        speedMultiplier = 1f;
        toastType = -1;

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
        world.setFlashlightChestWanted(rain.wantsFlashlight() && !hasFlashlight);
        world.buildLevel(u, round);

        resetPlayer();
    }

    private void resetPlayer() {
        clearInput();
        world.clearRocks();
        playerFacing = 1;

        player.reset(u);
        player.setSpeedMultiplier(1f);

        // Dash selalu berhenti saat pemain diulang dari awal / ganti putaran.
        dashTimeLeft = 0f;
        dashMult = DASH_START_MULT;
        graceLeft = 0f;
        virtualGround = false;

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
                if (loaderThread == null) {
                    sw = getWidth();
                    sh = getHeight();

                    if (sw == 0 || sh == 0) {
                        last = System.nanoTime();
                        sleepQuiet(5);
                        continue;
                    }

                    u = sh / 12f;

                    // Mulai layar loading, lalu muat gambar di thread terpisah
                    // supaya animasi loading tetap berjalan mulus.
                    loadingScreen.begin(sw, sh, u);

                    loaderThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                assets.load(u);
                            } catch (Throwable t) {
                                Log.e(TAG, "Gagal memuat gambar", t);
                                loadError = t;
                            }

                            assetsLoaded = true;
                        }
                    }, "asset-loader");

                    loaderThread.start();
                }

                drawLoadingFrame();

                if (assetsLoaded) {
                    loadingScreen.markDone();
                }

                if (assetsLoaded && loadingScreen.isFinished()) {
                    try {
                        loaderThread.join();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }

                    if (loadError != null) {
                        throw new RuntimeException(loadError);
                    }

                    loadingScreen.release();

                    menu.layout(sw, sh, u);
                    rain.layout(sw, sh, u);
                    state = SELECT_CHARACTER;
                    levelBuilt = true;
                    Log.d(TAG, "menu siap");

                    last = System.nanoTime();
                } else {
                    last = System.nanoTime();
                    continue;
                }
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
                float stepScale = Math.max(
                        speedMultiplier,
                        dashTimeLeft > 0f ? dashMult : 1f
                );

                int steps = Math.max(
                        1,
                        (int) Math.ceil(
                                dt * stepScale / (1f / 120f)
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
            if (state != SELECT_CHARACTER) {
                rain.update(dt, hasFlashlight, playerFacing); // atur cuaca, senter, dan arah sorotan
                sounds.setRainVolume(rain.getIntensity()); // suara hujan mengikuti kekuatan hujan

                if (rain.consumeDarkFogEnded()) {
                    hasFlashlight = false; // kabut gelap reda, senter hilang
                }
            } else {
                sounds.setRainVolume(0f); // di menu pilih karakter tidak ada suara hujan
            }
            draw();
        }
    }

    // Menggambar satu frame layar loading.
    private void drawLoadingFrame() {
        Canvas c = holder.lockCanvas();

        if (c == null) {
            return;
        }

        try {
            loadingScreen.draw(c);
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

        // Timer dash: kecepatan naik terus selama dash, lalu kembali normal.
        if (dashTimeLeft > 0f) {
            dashTimeLeft -= dt;
            dashMult = Math.min(DASH_MAX_MULT, dashMult + DASH_ACCEL * dt);
            player.setSpeedMultiplier(dashMult);

            if (dashTimeLeft <= 0f) {
                dashTimeLeft = 0f;
                dashMult = DASH_START_MULT;
                player.setSpeedMultiplier(1f);
                graceLeft = DASH_GRACE_SECONDS;
            }
        } else if (graceLeft > 0f) {
            graceLeft -= dt;
        }

        boolean dashing = dashTimeLeft > 0f;

        synchronized (inputLock) {
            direction = (right ? 1 : 0) - (left ? 1 : 0);

            jump = jumpRequested;
            jumpRequested = false;
        }

        player.setDirection(direction);
        if (direction != 0) {
            playerFacing = direction; // arah sorotan senter mengikuti arah hadap pemain
        }

        if (jump) {
            // Suara lompat hanya bila lompat benar-benar terjadi (pemain di tanah).
            if (player.onGround) {
                sounds.playJump();
            }

            player.requestJump();
        }

        float previousBottom = player.y + player.h;

        player.update(dt, u, world.platforms);

        // Tanah lurus pengganti jurang selama dash. Setelah dash selesai tanah ini
        // baru hilang kalau pemain sudah berdiri di tanah/platform asli (tidak jatuh di tengah jurang).
        if (virtualGround) {
            float groundTop = 9f * u;

            if (!dashing && player.onGround) {
                virtualGround = false;
            } else if (!player.onGround && player.vy >= 0f
                    && player.y + player.h >= groundTop) {
                player.y = groundTop - player.h - 0.001f * u;
                player.vy = 0f;
                player.onGround = true;
            }
        }

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

        // Peti harta: disentuh = terbuka, hadiah diacak (heart / speed / coin).
        Treasure openedTreasure = world.openTreasure(p);

        if (openedTreasure != null) {
            giveTreasureReward(openedTreasure);
        }

        // Saat dash musuh dan batu "tidak ada": tidak bergerak, tidak muncul, tidak menabrak.
        if (!dashing) {
            world.updateEnemies(dt, u);
            world.updateRocks(dt, u, camX, sw);
        }

        // Injak dari atas: musuh hilang dan pemain memantul.
        Enemy stomped = dashing ? null : world.stompEnemy(
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
        boolean safe = dashing || graceLeft > 0f;

        if (!safe && world.hitsEnemy(p)) {
            loseLife(false);  // false = kena musuh
            return true;
        }
        if (!safe && world.hitsRock(p, u)) {
            loseLife(false);
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

    // Hadiah peti diacak. Heart dilewati kalau nyawa sudah penuh supaya tidak ada hadiah yang sia-sia.
    private void giveTreasureReward(Treasure treasure) {
        if (treasure.reward == Treasure.FLASHLIGHT
                && !hasFlashlight
                && rain.wantsFlashlight()) {
            hasFlashlight = true;

            treasure.type = Treasure.FLASHLIGHT;

            toastMain = "SENTER";
            toastSub = "Lihat lebih jelas saat kabut gelap";
            toastType = Treasure.FLASHLIGHT;
            toastStartNs = System.nanoTime();

            sounds.playCoin();
            return;
        }
        int[] pool = new int[3];
        int count = 0;

        if (lives < MAX_LIVES) {
            pool[count++] = Treasure.HEART;
        }

        pool[count++] = Treasure.SPEED;

        pool[count++] = Treasure.COIN;

        int type = pool[treasureRandom.nextInt(count)];

        if (type == Treasure.HEART) {
            lives++;

            toastMain = "HEART +1";
            toastSub = "Nyawa bertambah";
        } else if (type == Treasure.SPEED) {
            startDash();

            toastMain = "SPEED BOOST!";
            toastSub = "Tanpa rintangan selama " + Math.round(DASH_SECONDS) + " detik";
        } else {
            world.addScore(COIN_REWARD);

            toastMain = "COIN +" + COIN_REWARD;
            toastSub = "Total koin: " + world.getScore();
        }

        treasure.type = type;

        toastType = type;
        toastStartNs = System.nanoTime();

        sounds.playCoin();
    }

    // Mulai (atau perpanjang) mode dash dari speed treasure.
    private void startDash() {
        if (dashTimeLeft <= 0f) {
            dashMult = DASH_START_MULT;
            world.clearRocks();   // batu yang sudah ada hilang, tidak muncul tiba-tiba setelah dash
        }

        dashTimeLeft = DASH_SECONDS;
        graceLeft = 0f;
        virtualGround = true;

        player.setSpeedMultiplier(dashMult);
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

            // Saat dash jurang tertutup tanah lurus.
            if (virtualGround) {
                drawDashGround(c);
            }

            // Koin.
            for (RectF coin : world.coins) {
                assets.drawCoin(c, coin);
            }

            // Peti harta (tertutup sampai disentuh).
            for (Treasure treasure : world.treasures) {
                assets.drawTreasure(c, treasure, u);
            }

            // Musuh.
            if (dashTimeLeft <= 0f) {
                for (Enemy enemy : world.enemies) {
                    assets.drawEnemy(c, enemy);
                }
                //Batu
                for (Rock rock : world.rocks) {
                    assets.drawRock(c, rock);
                }
            }
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

            RectF playerBox = player.getHitbox(u); // pusat lingkaran pandang saat kabut gelap
            rain.draw(c, camX, world.platforms, playerBox.centerX() - camX, playerBox.centerY());

            drawHud(c);
            drawDashBar(c);
            drawControls(c);
            drawTreasureToast(c);

            if (state == GAME_OVER) {
                drawGameOver(c);
            }
        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }

    // Menutup semua celah jurang yang terlihat di layar dengan potongan tanah.
    private void drawDashGround(Canvas c) {
        float groundTop = 9f * u;
        float viewLeft = camX - u;
        float viewRight = camX + sw + u;

        java.util.List<RectF> ground = new java.util.ArrayList<>();

        for (RectF platform : world.platforms) {
            if (Math.abs(platform.top - groundTop) < 0.01f * u
                    && platform.height() > 1.5f * u) {
                ground.add(platform);
            }
        }

        java.util.Collections.sort(ground, (a, b) -> Float.compare(a.left, b.left));

        float cursor = viewLeft;

        for (RectF platform : ground) {
            if (platform.left > cursor && cursor < viewRight) {
                assets.drawPlatform(
                        c,
                        new RectF(cursor, groundTop, Math.min(platform.left, viewRight), 12f * u),
                        u
                );
            }

            cursor = Math.max(cursor, platform.right);
        }
    }

    // Bar sisa waktu dash di tengah atas layar.
    private void drawDashBar(Canvas c) {
        if (dashTimeLeft <= 0f) {
            return;
        }

        float barW = u * 4f;
        float barH = u * 0.3f;
        float left = (sw - barW) / 2f;
        float top = u * 0.25f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 0, 0, 0));
        c.drawRoundRect(new RectF(left, top, left + barW, top + barH),
                barH / 2f, barH / 2f, paint);

        paint.setColor(Color.rgb(255, 190, 40));
        float fill = barW * Math.max(0f, Math.min(1f, dashTimeLeft / DASH_SECONDS));
        c.drawRoundRect(new RectF(left, top, left + fill, top + barH),
                barH / 2f, barH / 2f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(u * 0.34f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("SPEED " + String.format(java.util.Locale.US, "%.1f", dashTimeLeft) + "s",
                sw / 2f, top + barH + u * 0.38f, paint);
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
        if (hasFlashlight) {
            drawFlashlightBadge(c);
        }
    }
    // Lencana kecil di HUD selama senter aktif.
    private void drawFlashlightBadge(Canvas c) {
        float x = u * 8.1f;
        float y = u * 0.15f;
        float w = u * 2.6f;
        float h = u * 0.85f;

        RectF badge = new RectF(x, y, x + w, y + h);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(139, 90, 43));
        c.drawRoundRect(badge, u * 0.18f, u * 0.18f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.045f);
        paint.setColor(Color.rgb(85, 50, 25));
        c.drawRoundRect(badge, u * 0.18f, u * 0.18f, paint);

        paint.setStyle(Paint.Style.FILL);

        float iconSize = u * 0.75f;

        RectF iconRect = new RectF(
                x + u * 0.08f,
                y + (h - iconSize) / 2f,
                x + u * 0.08f + iconSize,
                y + (h + iconSize) / 2f
        );

        assets.drawFlashlightIcon(c, iconRect); // senter polos, lebih jelas di HUD

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(u * 0.30f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        c.drawText("SENTER", x + u * 1.65f, badge.centerY() + u * 0.11f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
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

    // Teks berwarna dengan garis tepi gelap supaya mudah dibaca.
    private void drawOutlinedText(
            Canvas c,
            String text,
            float x,
            float y,
            int fill,
            int alpha
    ) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.09f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.argb(alpha, 70, 38, 12));
        c.drawText(text, x, y, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(
                alpha,
                Color.red(fill),
                Color.green(fill),
                Color.blue(fill)
        ));
        c.drawText(text, x, y, paint);
    }

    // Setelah membuka peti: tampilkan peti terbuka berisi hadiahnya + keterangan.
    private void drawTreasureToast(Canvas c) {
        if (toastType < 0) {
            return;
        }

        long elapsed = System.nanoTime() - toastStartNs;

        if (elapsed < 0 || elapsed > TOAST_NS) {
            toastType = -1;
            return;
        }

        float sec = elapsed / 1_000_000_000f;
        float total = TOAST_NS / 1_000_000_000f;

        // Muncul cepat, memudar di akhir.
        float fadeIn = Math.min(1f, sec / 0.18f);
        float fadeOut = Math.min(1f, (total - sec) / 0.45f);
        float fade = Math.max(0f, Math.min(fadeIn, fadeOut));
        int alpha = Math.round(255f * fade);

        // Sedikit membesar saat muncul.
        float pop = sec < 0.3f ? 1f - sec / 0.3f : 0f;
        float scale = 0.8f + 0.2f * fadeIn + 0.06f * pop;

        float panelW = Math.min(sw * 0.7f, 7.2f * u);
        float panelH = 2.1f * u;
        float cx = sw / 2f;
        float top = 1.6f * u;

        RectF panel = new RectF(
                cx - panelW / 2f,
                top,
                cx + panelW / 2f,
                top + panelH
        );

        c.save();
        c.scale(scale, scale, cx, panel.centerY());

        // Papan kayu (warna sama dengan badge level).
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha * 0.93f), 139, 90, 43));
        c.drawRoundRect(panel, 0.25f * u, 0.25f * u, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(u * 0.06f);
        paint.setColor(Color.argb(alpha, 85, 50, 25));
        c.drawRoundRect(panel, 0.25f * u, 0.25f * u, paint);

        paint.setStyle(Paint.Style.FILL);

        // Peti terbuka di kiri, sedikit menonjol ke atas panel.
        RectF iconBox = new RectF(
                panel.left + 0.25f * u,
                panel.bottom - 0.1f * u - 2.5f * u,
                panel.left + 0.25f * u + 2.3f * u,
                panel.bottom - 0.1f * u
        );

        assets.drawTreasureIcon(c, toastType, iconBox, alpha);

        // Teks di kanan peti.
        float textX = iconBox.right + 0.3f * u;
        float textMaxW = panel.right - 0.25f * u - textX;

        int mainColor;

        if (toastType == Treasure.HEART) {
            mainColor = Color.rgb(255, 110, 120);
        } else if (toastType == Treasure.SPEED) {
            mainColor = Color.rgb(255, 225, 70);
        } else {
            mainColor = Color.rgb(255, 215, 90);
        }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        // Judul kecil.
        paint.setTextSize(u * 0.32f);
        drawOutlinedText(
                c,
                "KAMU DAPAT",
                textX,
                panel.top + 0.62f * u,
                Color.rgb(255, 241, 194),
                alpha
        );

        // Hadiah (diperkecil otomatis kalau terlalu lebar).
        float mainSize = u * 0.62f;
        paint.setTextSize(mainSize);

        float mainWidth = paint.measureText(toastMain);

        if (mainWidth > textMaxW && mainWidth > 0f) {
            mainSize *= textMaxW / mainWidth;
            paint.setTextSize(mainSize);
        }

        drawOutlinedText(
                c,
                toastMain,
                textX,
                panel.top + 1.3f * u,
                mainColor,
                alpha
        );

        // Keterangan.
        paint.setTextSize(u * 0.34f);

        float subWidth = paint.measureText(toastSub);

        if (subWidth > textMaxW && subWidth > 0f) {
            paint.setTextSize(u * 0.34f * textMaxW / subWidth);
        }

        drawOutlinedText(
                c,
                toastSub,
                textX,
                panel.top + 1.78f * u,
                Color.WHITE,
                alpha
        );

        c.restore();

        paint.setStyle(Paint.Style.FILL);
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