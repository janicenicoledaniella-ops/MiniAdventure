package com.example.miniadventure; // samakan dengan package project kamu

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * MILIK: Orang 3. Semua cara menggambar objek game ada di sini.
 * Sekarang masih kotak/lingkaran berwarna. Ganti dengan gambar (Bitmap) dan animasi.
 * Panggil load() sekali (sudah dilakukan di GameView), jangan memuat gambar di draw*().
 */
public class Assets {
    private final Context context;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // TODO (Orang 3): tambahkan Bitmap di sini, misalnya:
    // private Bitmap idleBmp, jumpBmp, enemyBmp, coinBmp, groundBmp, backgroundBmp;
    // private Bitmap[] runBmp;

    public Assets(Context context) { this.context = context; }

    /** Dipanggil sekali setelah ukuran layar diketahui. u = satuan dasar. */
    public void load(float u) {
        // TODO (Orang 3): muat & skala semua gambar di sini
        // contoh: idleBmp = decodeScaled(R.drawable.player_idle, 0.8f * u, u);
    }

    public void drawBackground(Canvas c, int sw, int sh, float camX) {
        c.drawColor(Color.rgb(135, 206, 235)); // TODO: background bergeser (parallax)
    }

    public void drawPlatform(Canvas c, RectF r) {
        paint.setColor(Color.rgb(76, 160, 60)); // TODO: tile tanah
        c.drawRect(r, paint);
    }

    public void drawCoin(Canvas c, RectF r) {
        paint.setColor(Color.YELLOW); // TODO: gambar koin
        c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, paint);
    }

    public void drawEnemy(Canvas c, Enemy e) {
        paint.setColor(Color.RED); // TODO: gambar musuh
        c.drawRect(e.rect, paint);
    }

    public void drawPlayer(Canvas c, Player p) {
        paint.setColor(Color.rgb(30, 80, 220)); // TODO: animasi idle/lari/lompat
        c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint);
    }
}