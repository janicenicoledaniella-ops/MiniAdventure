package com.example.miniadventure;

import android.graphics.RectF;

public class Enemy {

    public final RectF rect;

    private final float minX;
    private final float maxX;

    private float dir = 1f;
    private float speedMultiplier = 1f;

    public Enemy(
            float x,
            float u,
            float minX,
            float maxX
    ) {
        this.minX = minX;
        this.maxX = maxX;

        rect = new RectF(
                x,
                8.2f * u,
                x + 0.8f * u,
                9 * u
        );
    }

    public void setSpeedMultiplier(float multiplier) {
        speedMultiplier = Math.max(1f, multiplier);
    }

    public void update(float dt, float u) {
        rect.offset(
                dir * 2f * speedMultiplier * u * dt,
                0
        );

        if (rect.left < minX) {
            rect.offset(minX - rect.left, 0);
            dir = 1f;
        }

        if (rect.right > maxX) {
            rect.offset(maxX - rect.right, 0);
            dir = -1f;
        }
    }
}