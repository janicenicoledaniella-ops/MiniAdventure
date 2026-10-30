package com.example.miniadventure;

import android.graphics.RectF;
import java.util.List;

public class Player {
    public float x, y, w, h;
    public float vx, vy;
    public boolean onGround;
    private volatile boolean jumpRequested;
    public void reset(float u) {
        w = 0.8f * u;
        h = 1.0f * u;

        x = 1.0f * u;
        y = 7.0f * u;

        vx = 0;
        vy = 0;

        onGround = false;
        jumpRequested = false;
    }
    public void requestJump() {
        jumpRequested = true;
    }


    public void update(float dt, float u, List<RectF> platforms) {
        float meters = x / u;
        float speed = (5f + Math.min(4f, meters * 0.02f)) * u;
        float gravity = 34f * u;
        float jumpPower = 16f * u;

        vx = speed;
        if (jumpRequested && onGround) {
            vy = -jumpPower;
            onGround = false;
        }

        jumpRequested = false;

        float oldTop = y;
        float oldBottom = y + h;

        vy += gravity * dt;

        x += vx * dt;
        y += vy * dt;

        onGround = false;

        RectF player = getRect();

        for (RectF platform : platforms) {

            boolean horizontalOverlap =
                    player.right > platform.left &&
                            player.left < platform.right;

            if (!horizontalOverlap) {
                continue;
            }

            if (vy >= 0 &&
                    oldBottom <= platform.top &&
                    player.bottom >= platform.top) {

                y = platform.top - h;
                vy = 0;
                onGround = true;
                break;
            }

            if (vy < 0 &&
                    oldTop >= platform.bottom &&
                    player.top <= platform.bottom) {

                y = platform.bottom;
                vy = 0;
                break;
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
    public RectF getHitbox(float u) {
        return new RectF(
                x + 0.1f * u,
                y + 0.1f * u,
                x + w - 0.1f * u,
                y + h
        );
    }

    public float getMeters(float u) {
        return x / u;
    }
    public boolean hasFallen(float u) {
        return y > 14 * u;
    }

    public float getCameraX(int screenWidth) {
        return Math.max(0, x - screenWidth * 0.25f);
    }
}
