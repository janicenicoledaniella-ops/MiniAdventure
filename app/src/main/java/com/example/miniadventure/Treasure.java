package com.example.miniadventure;

import android.graphics.RectF;

/**
 * Peti harta di dalam level. Awalnya tertutup. Saat disentuh pemain, peti terbuka
 * dan isinya (heart / speed / coin) ditentukan secara acak oleh GameView.
 */
public class Treasure {

    // Isi peti.
    public static final int HEART = 0;
    public static final int SPEED = 1;
    public static final int COIN = 2;
    public static final int FLASHLIGHT = 3; // senter, khusus saat kabut gelap

    // Posisi peti di level.
    public static final int PLACE_GROUND = 0;   // di atas tanah
    public static final int PLACE_BRICK = 1;    // di atas brick (pijakan melayang)
    public static final int PLACE_AIR = 2;      // melayang di udara

    // Area sentuh peti.
    public final RectF rect;
    public final int place;

    public boolean opened;
    public int type = -1;        // isi peti, baru diketahui setelah dibuka
    public int reward = -1;      // isi yang sudah ditentukan dari awal (senter). -1 = diacak
    public long openedAtNs;      // untuk animasi "pop" sesaat setelah dibuka

    public Treasure(RectF rect, int place) {
        this.rect = rect;
        this.place = place;
    }
}