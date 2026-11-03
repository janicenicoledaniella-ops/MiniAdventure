package com.example.miniadventure; // samakan dengan package project kamu

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** MILIK: Orang 3. Tampilan skor di layar dan layar Game Over. */
public class Hud {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void drawHud(Canvas c, float u, int score, int coins, int best) {
        paint.setColor(Color.BLACK);
        paint.setTextSize(u * 0.7f);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("Skor: " + score + "   Koin: " + coins + "   Rekor: " + best, u * 0.5f, u, paint);
    }

    public void drawGameOver(Canvas c, int sw, int sh, float u, int score, int best) {
        paint.setColor(Color.argb(160, 0, 0, 0));
        c.drawRect(0, 0, sw, sh, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(u * 1.4f);
        c.drawText("GAME OVER", sw / 2f, sh / 2f, paint);
        paint.setTextSize(u * 0.7f);
        c.drawText("Skor: " + score + "   Rekor: " + best, sw / 2f, sh / 2f + u * 1.1f, paint);
        c.drawText("Tap untuk main lagi", sw / 2f, sh / 2f + u * 2f, paint);
    }
}