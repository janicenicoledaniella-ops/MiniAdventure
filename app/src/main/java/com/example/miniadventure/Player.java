package com.example.miniadventure; // samakan dengan package project kamu

import android.graphics.RectF;

import java.util.List;

/**
 * MILIK: Orang 1. Fisika pemain, lompat, tabrakan platform, kecepatan, dan kamera.
 * Semua posisi dalam piksel; 'u' = satuan dasar (tinggi layar / 12).
 */
public class Player {
    public float x, y, w, h, vx, vy;
    public boolean onGround;
    private volatile boolean jumpRequested;

    public void reset(float u) {
        w = 0.8f * u;
        h = u;
        x = 1 * u;
        y = 7 * u;
        vx = vy = 0;
        onGround = false;
        jumpRequested = false;
    }

    /** Dipanggil dari input sentuh. */
    public void requestJump() { jumpRequested = true; }

    public void update(float dt, float u, List<RectF> platforms) {
        float meters = x / u;
        float speed = (5f + Math.min(4f, meters * 0.02f)) * u; // makin jauh makin cepat
        float gravity = 30f * u, jumpPower = 15f * u;

        vx = speed;
        if (jumpRequested && onGround) { vy = -jumpPower; onGround = false; }
        jumpRequested = false;
        vy += gravity * dt;

        // gerak sumbu X
        x += vx * dt;
        RectF p = getRect();
        for (RectF plat : platforms) {
            if (RectF.intersects(p, plat)) { x = plat.left - w; p = getRect(); }
        }

        // gerak sumbu Y
        y += vy * dt;
        onGround = false;
        p = getRect();
        for (RectF plat : platforms) {
            if (RectF.intersects(p, plat)) {
                if (vy > 0) { y = plat.top - h; onGround = true; }
                else if (vy < 0) y = plat.bottom;
                vy = 0;
                p = getRect();
            }
        }
    }

    public RectF getRect() { return new RectF(x, y, x + w, y + h); }

    /** Hitbox untuk tabrakan musuh (dikecilkan sedikit supaya adil). */
    public RectF getHitbox(float u) {
        return new RectF(x + 0.1f * u, y + 0.1f * u, x + w - 0.1f * u, y + h);
    }

    public float getMeters(float u) { return x / u; }

    public boolean hasFallen(float u) { return y > 14 * u; }

    /** Posisi kamera: pemain berada di 25% dari kiri layar. */
    public float getCameraX(int screenWidth) { return Math.max(0, x - screenWidth * 0.25f); }
}