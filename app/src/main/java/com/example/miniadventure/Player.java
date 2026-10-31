
package com.example.miniadventure;

import android.graphics.RectF;
import java.util.List;

public class Player {
    public float x, y, w, h;
    public float vx, vy;
    public boolean onGround;

    private int direction;
    private boolean jumpRequested;

    private static final float BASE_SPEED = 6.0f;
    private static final float MAX_EXTRA_SPEED = 2.0f;
    private static final float SPEED_PER_METER = 0.015f;
    private static final float GRAVITY = 32.0f;
    private static final float JUMP_POWER = 15.0f;

    public void reset(float unit) {
        w = 0.8f * unit;
        h = 1.0f * unit;
        x = 1.0f * unit;
        y = 7.0f * unit;
        vx = 0;
        vy = 0;
        direction = 0;
        onGround = false;
        jumpRequested = false;
    }

    public void setDirection(int value) {
        direction = Math.max(-1, Math.min(1, value));
    }

    public void requestJump() {
        jumpRequested = true;
    }

    public void update(float dt, float unit, List<RectF> platforms) {
        float distance = Math.max(0, x / unit);
        float extraSpeed = Math.min(
                MAX_EXTRA_SPEED,
                distance * SPEED_PER_METER
        );

        float moveSpeed = (BASE_SPEED + extraSpeed) * unit;
        vx = direction * moveSpeed;

        if (jumpRequested && onGround) {
            vy = -JUMP_POWER * unit;
            onGround = false;
        }

        jumpRequested = false;

        x += vx * dt;

        if (x < 0) {
            x = 0;
        }

        RectF horizontalRect = getRect();

        for (RectF platform : platforms) {
            boolean verticalOverlap =
                    horizontalRect.bottom > platform.top &&
                            horizontalRect.top < platform.bottom;

            if (!verticalOverlap ||
                    !RectF.intersects(horizontalRect, platform)) {
                continue;
            }

            if (vx > 0) {
                x = platform.left - w;
            } else if (vx < 0) {
                x = platform.right;
            }

            horizontalRect = getRect();
        }

        float oldTop = y;
        float oldBottom = y + h;

        vy += GRAVITY * unit * dt;
        y += vy * dt;

        onGround = false;

        RectF verticalRect = getRect();

        for (RectF platform : platforms) {
            boolean horizontalOverlap =
                    verticalRect.right > platform.left &&
                            verticalRect.left < platform.right;

            if (!horizontalOverlap) {
                continue;
            }

            if (vy >= 0 &&
                    oldBottom <= platform.top &&
                    verticalRect.bottom >= platform.top) {

                y = platform.top - h;
                vy = 0;
                onGround = true;
                break;
            }

            if (vy < 0 &&
                    oldTop >= platform.bottom &&
                    verticalRect.top <= platform.bottom) {

                y = platform.bottom;
                vy = 0;
                break;
            }
        }
    }

    public RectF getRect() {
        return new RectF(x, y, x + w, y + h);
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
        return Math.max(0, x - screenWidth * 0.35f);
    }
}
