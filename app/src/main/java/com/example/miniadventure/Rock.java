package com.example.miniadventure;

import android.graphics.RectF;

import java.util.List;

public class Rock {
    public static final float SIZE = 0.8f;
    public static final float DRAW_W = 0.9f;

    private static final float GRAVITY = 32f;
    private static final float ROLL_RADIUS = 0.45f;

    public final RectF rect;
    public float vx;
    public float vy;
    public float angle;
    public boolean onGround;

    private float stuckTime;

    public Rock(float left, float bottom, float speed, float u) {
        float size = SIZE * u;

        rect = new RectF(left, bottom - size, left + size, bottom);
        vx = -Math.abs(speed);
    }

    public boolean isStuck() {
        return stuckTime > 0.7f;
    }

    public void update(float dt, float u, List<RectF> platforms) {
        float startLeft = rect.left;
        float prevLeft = rect.left;
        float prevBottom = rect.bottom;

        rect.offset(vx * dt, 0);

        for (RectF platform : platforms) {
            if (platform.height() <= 1.5f * u) {
                continue;
            }

            boolean sameHeight = rect.bottom > platform.top + 0.15f * u
                    && rect.top < platform.bottom;

            if (sameHeight
                    && vx < 0
                    && prevLeft >= platform.right - 0.001f
                    && rect.left < platform.right) {
                rect.offset(platform.right - rect.left, 0);
            }
        }

        vy += GRAVITY * u * dt;
        rect.offset(0, vy * dt);

        onGround = false;

        for (RectF platform : platforms) {
            boolean supported = rect.centerX() >= platform.left
                    && rect.centerX() <= platform.right;

            if (supported
                    && vy >= 0
                    && prevBottom <= platform.top + 0.02f * u
                    && rect.bottom >= platform.top) {

                rect.offset(0, platform.top - rect.bottom);
                vy = 0;
                onGround = true;
            }
        }

        if (Math.abs(rect.left - startLeft) < Math.abs(vx) * dt * 0.3f) {
            stuckTime += dt;
        } else {
            stuckTime = 0f;
        }

        angle += (vx * dt) / (ROLL_RADIUS * u) * (180f / (float) Math.PI);
        angle %= 360f;
    }

    public RectF getHitbox(float u) {
        return new RectF(
                rect.left + 0.12f * u,
                rect.top + 0.15f * u,
                rect.right - 0.12f * u,
                rect.bottom
        );
    }
}
