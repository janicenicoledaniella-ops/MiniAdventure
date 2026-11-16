package com.example.miniadventure;

import android.graphics.RectF;

import java.util.List;

// Batu rintangan yang menggelinding ke arah kiri (ke arah pemain).
// Bisa menggelinding di tanah, atau jatuh dari brick lalu menggelinding di tanah.
public class Rock {

    // Ukuran dalam satuan u.
    public static final float SIZE = 0.8f;      // kotak dasar batu
    public static final float DRAW_W = 0.9f;    // lebar gambar batu

    private static final float GRAVITY = 32f;
    private static final float ROLL_RADIUS = 0.45f;  // makin kecil, makin cepat berputar

    public final RectF rect;
    public float vx;
    public float vy;
    public float angle;
    public boolean onGround;

    public Rock(float left, float bottom, float speed, float u) {
        float size = SIZE * u;

        rect = new RectF(left, bottom - size, left + size, bottom);
        vx = -Math.abs(speed);
    }

    public void update(float dt, float u, List<RectF> platforms) {
        float prevLeft = rect.left;
        float prevBottom = rect.bottom;

        // Gerak horizontal.
        rect.offset(vx * dt, 0);

        // Batu tidak menembus sisi tanah saat jatuh ke jurang.
        for (RectF platform : platforms) {
            boolean sameHeight = rect.bottom > platform.top + 0.15f * u
                    && rect.top < platform.bottom;

            if (sameHeight
                    && vx < 0
                    && prevLeft >= platform.right - 0.001f
                    && rect.left < platform.right) {
                rect.offset(platform.right - rect.left, 0);
            }
        }

        // Gravitasi.
        vy += GRAVITY * u * dt;
        rect.offset(0, vy * dt);

        onGround = false;

        for (RectF platform : platforms) {
            // Batu tertahan selama titik tengahnya masih di atas platform,
            // jadi jatuh begitu lewat tepi.
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

        // Putaran mengikuti jarak tempuh (ke kiri = berputar berlawanan arah jarum jam).
        angle += (vx * dt) / (ROLL_RADIUS * u) * (180f / (float) Math.PI);
        angle %= 360f;
    }

    // Area tabrakan dibuat sedikit lebih kecil dari gambar supaya adil buat pemain.
    public RectF getHitbox(float u) {
        return new RectF(
                rect.left + 0.12f * u,
                rect.top + 0.15f * u,
                rect.right - 0.12f * u,
                rect.bottom
        );
    }
}
