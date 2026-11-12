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

// Mengelola suara game:
//  - efek pendek (koin, lompat, game over) lewat SoundPool
//  - musik latar (waktu main) lewat MediaPlayer, diulang terus
// File suara ada di res/raw: coin.mp3, lompat.mp3, game_over.mp3, waktu_main.mp3,
//  jatuh.mp3, jatuh2.mp3, matikan_enemy.mp3
//
// Semua inisialisasi suara dikerjakan di background thread supaya
// tidak memblokir main thread (penyebab layar hitam saat startup).
public class SoundManager {

    private static final String TAG = "SoundManager";

    // true = kalau suara gagal dimuat, tampilkan pesan (Toast) di layar. Matikan kalau sudah beres.
    private static final boolean SHOW_ERRORS = true;

    // Atur volume di sini (0.0 sampai 1.0).
    private static final float MUSIC_VOLUME = 0.45f;
    private static final float COIN_VOLUME = 1.0f;
    private static final float JUMP_VOLUME = 0.9f;
    private static final float GAME_OVER_VOLUME = 0.8f;
    private static final float FALL_VOLUME = 0.9f;
    private static final float FALL_FINAL_VOLUME = 1.0f;
    private static final float ENEMY_DIE_VOLUME = 1.0f;

    // Kecepatan putar suara jatuh (0.5 sampai 2.0). 1.0 = normal.
    // Makin besar = makin cepat & pendek. Naikkan kalau masih terasa lambat.
    private static final float FALL_RATE = 1.3f;

    // Jeda (ms) dari jatuh2.mp3 mulai sampai suara game over boleh bunyi.
    // Kira-kira panjang jatuh2 setelah dipercepat. Naikkan kalau masih bertumpuk.
    private static final long GAME_OVER_AFTER_FALL_MS = 600;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Suara game over yang ditunda (dipakai setelah jatuh2.mp3 selesai).
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
    private boolean musicWanted;   // musik seharusnya berbunyi (sedang bermain)
    private boolean paused;        // aplikasi di latar belakang
    private boolean released;      // SoundManager sudah dilepas

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();

        // Jangan inisialisasi di sini: MediaPlayer.create() dan SoundPool.load()
        // bisa lama dan akan memblokir main thread.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                init();
            }
        }, "sound-init");
        t.start();
    }

    // Berjalan di background thread.
    private void init() {
        initSoundPool();
        initMusic();
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

                        // Semua efek sudah dimuat (hanya tampil selama SHOW_ERRORS = true).
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

            // Game sudah mulai sebelum musik siap: langsung putar sekarang.
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

    // Tulis ke Logcat dan (opsional) tampilkan Toast supaya masalahnya kelihatan.
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

    // Game over setelah jatuh: tunggu jatuh2.mp3 selesai dulu.
    public void playGameOverAfterFall() {
        mainHandler.removeCallbacks(delayedGameOver);
        mainHandler.postDelayed(delayedGameOver, GAME_OVER_AFTER_FALL_MS);
    }

    // Jatuh ke jurang.
    public void playFall() {
        play(fallId, FALL_VOLUME, FALL_RATE);
    }

    // Jatuh ke jurang di nyawa terakhir (heart habis).
    public void playFallFinal() {
        play(fallFinalId, FALL_FINAL_VOLUME, FALL_RATE);
    }

    // Musuh jamur mati diinjak.
    public void playEnemyDie() {
        play(enemyDieId, ENEMY_DIE_VOLUME);
    }

    // Musik dimulai dari awal setiap permainan baru.
    public synchronized void startMusic() {
        musicWanted = true;

        // Permainan baru: batalkan suara game over yang masih menunggu.
        mainHandler.removeCallbacks(delayedGameOver);

        if (music == null) {
            // Belum siap: akan diputar otomatis di akhir initMusic().
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

    // Aplikasi ke latar belakang: musik berhenti sementara.
    public synchronized void onPause() {
        paused = true;

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

    // Aplikasi kembali: musik lanjut kalau memang sedang bermain.
    public synchronized void onResume() {
        paused = false;

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