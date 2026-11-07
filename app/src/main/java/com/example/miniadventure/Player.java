
package com.example.miniadventure;

import android.graphics.RectF;

import java.util.List;

public class Player {

    public float x, y, w, h;
    public float vx, vy;
    public boolean onGround;

    private int direction;
    private boolean jumpRequested;
    private float speedMultiplier = 1f;

    private static final float BASE_SPEED = 6.0f;
    private static final float MAX_EXTRA_SPEED = 2.0f;
    private static final float SPEED_PER_METER = 0.015f;
    private static final float GRAVITY = 32.0f;
    private static final float JUMP_POWER = 15.0f;

    public void reset(float unit) {
        w = 0.8f * unit;
        h = 1.0f * unit;

        x = 1.0f * unit;
        y = 8.0f * unit;

        vx = 0;
        vy = 0;

        direction = 0;
        onGround = false;
        jumpRequested = false;
    }

    public void setSpeedMultiplier(float multiplier) {
        speedMultiplier = Math.max(1f, multiplier);
    }

    public void setDirection(int value) {
        direction = Math.max(-1, Math.min(1, value));
    }

    public void requestJump() {
        jumpRequested = true;
    }



    public void update(
            float dt,
            float unit,
            List<RectF> platforms
    ) {
        float distance = Math.max(0, x / unit);

        float extraSpeed = Math.min(
                MAX_EXTRA_SPEED,
                distance * SPEED_PER_METER
        );

        float moveSpeed =
                (BASE_SPEED + extraSpeed) * unit * speedMultiplier;

        vx = direction * moveSpeed;

        if (jumpRequested && onGround) {
            vy = -JUMP_POWER * unit;
            onGround = false;
        }

        jumpRequested = false;

        float previousY = y;
        float previousBottom = y + h;

        // Tabrakan horizontal.
        float targetX = x + vx * dt;

        for (RectF platform : platforms) {
            boolean verticalOverlap =
                    previousY + h > platform.top &&
                            previousY < platform.bottom;

            if (!verticalOverlap) {
                continue;
            }

            // Menghalangi pemain dari sisi kiri.
            if (vx > 0 &&
                    x + w <= platform.left &&
                    targetX + w > platform.left) {

                targetX = platform.left - w;
            }

            // Menghalangi pemain dari sisi kanan.
            else if (vx < 0 &&
                    x >= platform.right &&
                    targetX < platform.right) {

                targetX = platform.right;
            }
        }

        x = Math.max(0, targetX);

        // Gravitasi dan gerakan vertikal.
        vy += GRAVITY * unit * dt;
        y += vy * dt;

        float currentTop = y;
        float currentBottom = y + h;

        onGround = false;

        for (RectF platform : platforms) {
            boolean horizontalOverlap =
                    x + w > platform.left &&
                            x < platform.right;

            if (!horizontalOverlap) {
                continue;
            }

            // Mendarat di atas platform.
            if (vy >= 0 &&
                    previousBottom <= platform.top &&
                    currentBottom >= platform.top) {

                y = platform.top - h;
                vy = 0;
                onGround = true;
            }

            // Kepala menyentuh bagian bawah platform.
            else if (vy < 0 &&
                    previousY >= platform.bottom &&
                    currentTop <= platform.bottom) {

                y = platform.bottom;
                vy = 0;
            }
        }
    }



    public RectF getRect() {
        return new RectF(
                x,
                y,
                x + w,
                y + h
        );
    }

    public RectF getHitbox(float unit) {
        return new RectF(
                x + 0.1f * unit,
                y + 0.1f * unit,
                x + w - 0.1f * unit,
                y + h
        );
    }

    public float getMeters(float unit) {
        return x / unit;
    }

    public boolean hasFallen(float unit) {
        return y > 14 * unit;
    }

    public float getCameraX(int screenWidth) {
        return Math.max(
                0,
                x - screenWidth * 0.35f
        );
    }
}
