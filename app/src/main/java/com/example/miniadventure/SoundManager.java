package com.example.miniadventure;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SoundManager {

    private static final String TAG = "SoundManager";

    private static final boolean SHOW_ERRORS = true;

    private static final float MUSIC_VOLUME = 0.45f;
    private static final float COIN_VOLUME = 1.0f;
    private static final float JUMP_VOLUME = 0.9f;
    private static final float GAME_OVER_VOLUME = 0.8f;
    private static final float FALL_VOLUME = 0.9f;
    private static final float FALL_FINAL_VOLUME = 1.0f;
    private static final float ENEMY_DIE_VOLUME = 1.0f;
    private static final float RAIN_VOLUME = 0.6f;

    private static final float FALL_RATE = 1.3f;

    private static final long GAME_OVER_AFTER_FALL_MS = 600;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable delayedGameOver = new Runnable() {
        @Override
        public void run() {
            playGameOver();
        }
    };

    private volatile SoundPool soundPool;
    private volatile int coinId, jumpId, gameOverId, fallId, fallFinalId, enemyDieId;
    private final Set<Integer> loaded =
            Collections.synchronizedSet(new HashSet<Integer>());

    private volatile MediaPlayer music;
    private MediaPlayer rainSound;
    private float rainLevel;
    private boolean musicWanted;
    private boolean paused;
    private boolean released;

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                init();
            }
        }, "sound-init");
        t.start();
    }

    private void init() {
        initSoundPool();
        initMusic();
        initRain();
    }

    private void initSoundPool() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            SoundPool pool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build();

            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    if (status == 0) {
                        loaded.add(sampleId);
                        Log.d(TAG, "suara siap, id=" + sampleId);
                        if (loaded.size() == 6) {
                            info(music != null
                                    ? "Semua suara berhasil dimuat. Naikkan volume media kalau tidak terdengar."
                                    : "Efek suara OK, tapi musik belum/gagal dimuat.");
                        }
                    } else {
                        report("Efek suara gagal dimuat (id=" + sampleId + ", status=" + status + ")");
                    }
                }
            });

            coinId = pool.load(context, R.raw.coin, 1);
            jumpId = pool.load(context, R.raw.lompat, 1);
            gameOverId = pool.load(context, R.raw.game_over, 1);
            fallId = pool.load(context, R.raw.jatuh, 1);
            fallFinalId = pool.load(context, R.raw.jatuh2, 1);
            enemyDieId = pool.load(context, R.raw.matikan_enemy, 1);

            synchronized (this) {
                if (released) {
                    pool.release();
                    return;
                }
                soundPool = pool;
            }
        } catch (Exception e) {
            soundPool = null;
            report("SoundPool error: " + e);
        }
    }

    private void initMusic() {
        MediaPlayer mp = null;

        try {
            mp = MediaPlayer.create(context, R.raw.waktu_main);

            if (mp == null) {
                report("Musik waktu_main gagal dimuat (MediaPlayer.create mengembalikan null)");
                return;
            }

            mp.setLooping(true);
            mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    report("Musik error: what=" + what + ", extra=" + extra);
                    return true;
                }
            });
        } catch (Exception e) {
            report("Musik error: " + e);

            if (mp != null) {
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            }
            return;
        }

        synchronized (this) {
            if (released) {
                mp.release();
                return;
            }

            music = mp;

            if (musicWanted && !paused) {
                try {
                    mp.start();
                    Log.d(TAG, "musik dimulai (setelah init)");
                } catch (IllegalStateException e) {
                    report("startMusic error: " + e);
                }
            }
        }
    }

    private void initRain() {
        MediaPlayer mp = null;

        try {
            mp = MediaPlayer.create(context, R.raw.hujan);

            if (mp == null) {
                report("Suara hujan gagal dimuat (MediaPlayer.create mengembalikan null)");
                return;
            }

            mp.setLooping(true);
            mp.setVolume(0f, 0f);
        } catch (Exception e) {
            report("Suara hujan error: " + e);

            if (mp != null) {
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            }
            return;
        }

        synchronized (this) {
            if (released) {
                mp.release();
                return;
            }

            rainSound = mp;

            applyRainLevel();
        }
    }

    private void report(final String message) {
        Log.e(TAG, message);

        if (SHOW_ERRORS) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "[Suara] " + message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void info(final String message) {
        Log.d(TAG, message);

        if (SHOW_ERRORS) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "[Suara] " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void play(int id, float volume) {
        play(id, volume, 1f);
    }

    private void play(int id, float volume, float rate) {
        SoundPool pool = soundPool;

        if (pool == null) {
            Log.w(TAG, "SoundPool belum siap, suara tidak dimainkan");
            return;
        }
        if (paused) {
            Log.w(TAG, "aplikasi sedang paused, suara tidak dimainkan");
            return;
        }
        if (!loaded.contains(id)) {
            Log.w(TAG, "suara id=" + id + " belum selesai dimuat");
            return;
        }

        int stream = pool.play(id, volume, volume, 1, 0, rate);
        if (stream == 0) {
            Log.w(TAG, "soundPool.play gagal untuk id=" + id);
        }
    }

    public void playCoin() {
        play(coinId, COIN_VOLUME);
    }

    public void playJump() {
        play(jumpId, JUMP_VOLUME);
    }

    public void playGameOver() {
        play(gameOverId, GAME_OVER_VOLUME);
    }

    public void playGameOverAfterFall() {
        mainHandler.removeCallbacks(delayedGameOver);
        mainHandler.postDelayed(delayedGameOver, GAME_OVER_AFTER_FALL_MS);
    }

    public void playFall() {
        play(fallId, FALL_VOLUME, FALL_RATE);
    }

    public void playFallFinal() {
        play(fallFinalId, FALL_FINAL_VOLUME, FALL_RATE);
    }

    public void playEnemyDie() {
        play(enemyDieId, ENEMY_DIE_VOLUME);
    }

    public synchronized void setRainVolume(float level) {
        level = Math.max(0f, Math.min(1f, level));

        if (Math.abs(level - rainLevel) < 0.01f && (level > 0f) == (rainLevel > 0f)) {
            return;
        }

        rainLevel = level;
        applyRainLevel();
    }

    private void applyRainLevel() {
        if (rainSound == null) {
            return;
        }

        try {
            if (rainLevel <= 0.01f || paused) {
                if (rainSound.isPlaying()) {
                    rainSound.pause();
                }
                return;
            }

            float v = rainLevel * RAIN_VOLUME;
            rainSound.setVolume(v, v);

            if (!rainSound.isPlaying()) {
                rainSound.start();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void startMusic() {
        musicWanted = true;

        mainHandler.removeCallbacks(delayedGameOver);

        if (music == null) {
            Log.w(TAG, "startMusic: musik belum siap, akan diputar begitu siap");
            return;
        }
        if (paused) {
            Log.w(TAG, "startMusic: aplikasi paused, musik ditunda");
            return;
        }

        try {
            if (!music.isPlaying()) {
                music.seekTo(0);
                music.start();
                Log.d(TAG, "musik dimulai");
            }
        } catch (IllegalStateException e) {
            report("startMusic error: " + e);
        }
    }

    public synchronized void stopMusic() {
        musicWanted = false;

        if (music == null) {
            return;
        }

        try {
            if (music.isPlaying()) {
                music.pause();
            }
            music.seekTo(0);
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void onPause() {
        paused = true;
        applyRainLevel();

        if (music == null) {
            return;
        }

        try {
            if (music.isPlaying()) {
                music.pause();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void onResume() {
        paused = false;
        applyRainLevel();

        if (music == null || !musicWanted) {
            return;
        }

        try {
            if (!music.isPlaying()) {
                music.start();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void release() {
        released = true;
        mainHandler.removeCallbacks(delayedGameOver);

        if (rainSound != null) {
            try {
                rainSound.release();
            } catch (Exception ignored) {
            }
            rainSound = null;
        }

        if (music != null) {
            try {
                music.release();
            } catch (Exception ignored) {
            }
            music = null;
        }

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}