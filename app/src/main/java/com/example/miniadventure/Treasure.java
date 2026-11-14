package com.example.miniadventure;

import android.graphics.RectF;

/**
 * Peti harta di dalam level. Awalnya tertutup. Saat disentuh pemain, peti terbuka
 * dan isinya (heart / speed / coin) ditentukan secara acak oleh GameView.
 */
public class Treasure {

    public static final int HEART = 0;
    public static final int SPEED = 1;
    public static final int COIN = 2;

    // Area sentuh peti: berdiri di atas tanah (permukaan tanah = 9 satuan).
    public final RectF rect;

    public boolean opened;
    public int type = -1;        // isi peti, baru diketahui setelah dibuka
    public long openedAtNs;      // untuk animasi "pop" sesaat setelah dibuka

    // x dalam satuan (bukan piksel), u = ukuran satu satuan dalam piksel.
    public Treasure(float x, float u) {
        rect = new RectF(
                x * u,
                8f * u,
                (x + 1.2f) * u,
                9f * u
        );
    }
}