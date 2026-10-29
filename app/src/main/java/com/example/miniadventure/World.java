package com.example.miniadventure; // samakan dengan package project kamu

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** MILIK: Orang 2. Generator dunia acak: tanah, jurang, platform, koin, dan musuh. */
public class World {
    public final List<RectF> platforms = new ArrayList<>();
    public final List<RectF> coins = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();

    private final Random rnd = new Random();
    private float genX;            // ujung kanan dunia yang sudah dibuat
    private boolean firstSegment;

    public void reset() {
        platforms.clear();
        coins.clear();
        enemies.clear();
        genX = 0;
        firstSegment = true;
    }

    private float rand(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    /** Buat dunia ke kanan sampai 2 layar di depan kamera. */
    public void generateAhead(float camX, int screenWidth, float u) {
        while (genX < camX + screenWidth * 2f) {
            float width = firstSegment ? 16 * u : rand(7, 14) * u;
            float x0 = genX;
            platforms.add(new RectF(x0, 9 * u, x0 + width, 12 * u)); // tanah

            if (!firstSegment) {
                // musuh di tengah tanah
                if (width >= 9 * u && rnd.nextFloat() < 0.6f) {
                    float ex = x0 + width / 2f;
                    enemies.add(new Enemy(ex, u, x0 + 1.5f * u, x0 + width - 1.5f * u));
                }
                // platform melayang + koin, atau koin di atas tanah
                if (rnd.nextFloat() < 0.5f) {
                    float fx = x0 + rand(1, width / u - 4) * u;
                    float fy = rand(6f, 7.2f) * u;
                    platforms.add(new RectF(fx, fy, fx + 3 * u, fy + 0.5f * u));
                    for (int i = 0; i < 3; i++)
                        coins.add(new RectF(fx + (0.5f + i) * u, fy - u, fx + (1f + i) * u, fy - 0.5f * u));
                } else if (rnd.nextFloat() < 0.5f) {
                    float cx = x0 + rand(1, width / u - 3) * u;
                    for (int i = 0; i < 3; i++)
                        coins.add(new RectF(cx + i * u, 8 * u, cx + i * u + 0.5f * u, 8.5f * u));
                }
            }
            firstSegment = false;
            genX = x0 + width + rand(2.5f, 4f) * u; // jurang
        }
    }

    /** Hapus objek yang sudah jauh di belakang kamera. */
    public void cleanup(float camX, float u) {
        float limit = camX - 2 * u;
        for (Iterator<RectF> it = platforms.iterator(); it.hasNext(); ) if (it.next().right < limit) it.remove();
        for (Iterator<RectF> it = coins.iterator(); it.hasNext(); ) if (it.next().right < limit) it.remove();
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) if (it.next().rect.right < limit) it.remove();
    }

    public void updateEnemies(float dt, float u) {
        for (Enemy e : enemies) e.update(dt, u);
    }

    /** Ambil koin yang tersentuh pemain; mengembalikan jumlah koin yang diambil. */
    public int collectCoins(RectF playerRect) {
        int got = 0;
        for (int i = coins.size() - 1; i >= 0; i--) {
            if (RectF.intersects(playerRect, coins.get(i))) { coins.remove(i); got++; }
        }
        return got;
    }

    public boolean hitsEnemy(RectF hitbox) {
        for (Enemy e : enemies) if (RectF.intersects(hitbox, e.rect)) return true;
        return false;
    }
}