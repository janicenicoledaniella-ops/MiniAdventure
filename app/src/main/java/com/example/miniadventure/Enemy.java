package com.example.miniadventure; // samakan dengan package project kamu

import android.graphics.RectF;

/** MILIK: Orang 2. Satu musuh yang berpatroli bolak-balik di atas tanah. */
public class Enemy {
    public final RectF rect;
    private final float minX, maxX;
    private float dir = 1;

    public Enemy(float x, float u, float minX, float maxX) {
        this.rect = new RectF(x, 8.2f * u, x + 0.8f * u, 9 * u);
        this.minX = minX;
        this.maxX = maxX;
    }

    public void update(float dt, float u) {
        rect.offset(dir * 2f * u * dt, 0);
        if (rect.left < minX) dir = 1;
        if (rect.right > maxX) dir = -1;
    }
}