        // ground.png punya area transparan di atasnya, jadi kita pakai gambar
        // yang sudah dipotong dari bagian bawah agar menempel ke tanah.
package com.example.miniadventure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Assets {

    private final Context context;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap[] backgrounds = new Bitmap[3];
    private int currentBgIndex = 0;

    public Bitmap flagBmp;

    public Bitmap leftArrowBmp;
    public Bitmap rightArrowBmp;
    public Bitmap jumpArrowBmp;

    public Bitmap heart1Bmp;
    public Bitmap heart2Bmp;
    public Bitmap heart3Bmp;


    private Bitmap brickBmp;
    private Bitmap coinBmp;
    private Bitmap groundBmp;
    private Bitmap playerBmp;

    public Assets(Context context) {
        this.context = context;
    }

    public void load(float u) {
        backgrounds[0] = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.bg_pagi
        );
        backgrounds[1] = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.bg_sore
        );
        backgrounds[2] = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.bg_malam
        );

        brickBmp = decodeScaled(
                R.drawable.brick,
                Math.round(u * 1.2f),
                Math.round(u)
        );
        coinBmp = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.coin
        );

        heart1Bmp = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.heart1
        );

        heart2Bmp = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.heart2
        );

        heart3Bmp = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.heart3
        );

        leftArrowBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.left_arrow
        );

        rightArrowBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.right_arrow
        );

        jumpArrowBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.jump_arrow
        );

        Bitmap rawFlag = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.flag
        );

        if (rawFlag != null) {
            flagBmp = Bitmap.createBitmap(
                    rawFlag,
                    62,
                    44,
                    157,
                    193
            );
        }

        coinBmp = decodeScaled(
                R.drawable.coin,
                Math.round(u * 0.8f),
                Math.round(u * 0.8f)
        );


        Bitmap originalGround = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.ground
        );

        if (originalGround != null) {
            int cropTop = 321;

            Bitmap croppedGround = Bitmap.createBitmap(
                    originalGround,
                    0,
                    cropTop,
                    originalGround.getWidth(),
                    originalGround.getHeight() - cropTop
            );

            groundBmp = croppedGround;

            if (croppedGround != originalGround) {
                originalGround.recycle();
            }
        }

    }

    public void setBackgroundByRound(int round) {
        currentBgIndex = (round - 1) % backgrounds.length;
        if (currentBgIndex < 0) {
            currentBgIndex = 0;
        }
    }

    private Bitmap decodeScaled(int resId, int targetW, int targetH) {
        Bitmap bitmap = BitmapFactory.decodeResource(
                context.getResources(),
                resId
        );
        if (bitmap == null) return null;
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
    }

    public void drawBackground(Canvas c, int sw, int sh, float camX) {
        Bitmap bg = backgrounds[currentBgIndex];

        if (bg != null) {
            float scale = (float) sh / bg.getHeight();

            float bgWidth = bg.getWidth() * scale;
            float offsetX = -(camX * 0.18f) % bgWidth;

            for (float x = offsetX - bgWidth; x < sw + bgWidth; x += bgWidth) {
                RectF dst = new RectF(
                        x,
                        0,
                        x + bgWidth,
                        sh
                );

                c.drawBitmap(bg, null, dst, paint);
            }

            return;
        }

        c.drawColor(Color.rgb(135, 206, 235));
    }


    public void drawPlatform(Canvas c, RectF r, float u) {
        if (r.height() > u * 1.5f) {
            if (groundBmp != null) {
                float tileWidth = u * 3f;

                for (float x = r.left; x < r.right; x += tileWidth) {
                    float right = Math.min(x + tileWidth, r.right);
                    float fraction = (right - x) / tileWidth;

                    android.graphics.Rect src = new android.graphics.Rect(
                            0,
                            0,
                            Math.max(1, Math.round(groundBmp.getWidth() * fraction)),
                            groundBmp.getHeight()
                    );

                    RectF dst = new RectF(
                            x,
                            r.top,
                            right,
                            r.bottom
                    );

                    paint.setColor(Color.WHITE);
                    c.drawBitmap(groundBmp, src, dst, paint);
                }

                return;
            }

            paint.setColor(Color.rgb(76, 160, 60));
            c.drawRect(r, paint);
            return;
        }

        if (brickBmp != null) {
            paint.setColor(Color.WHITE);
            c.drawBitmap(brickBmp, null, r, paint);
            return;
        }

        paint.setColor(Color.rgb(120, 90, 60));
        c.drawRect(r, paint);
    }


    public void drawCoin(Canvas c, RectF coin) {
        if (coinBmp != null) {
            float width = coin.width() * 1.3f;
            float height = coin.height() * 1.3f;

            RectF dst = new RectF(
                    coin.centerX() - width / 2f,
                    coin.centerY() - height / 2f,
                    coin.centerX() + width / 2f,
                    coin.centerY() + height / 2f
            );

            c.drawBitmap(coinBmp, null, dst, paint);
        }
    }

    public void drawCoinIcon(Canvas c, RectF dst) {
        if (coinBmp != null) {
            c.drawBitmap(coinBmp, null, dst, paint);
        }
    }

    public void drawEnemy(Canvas c, Enemy e) {
        paint.setColor(Color.RED);
        c.drawRect(e.rect, paint);
    }

    public void drawPlayer(Canvas c, Player p) {
        if (playerBmp != null) {
            c.drawBitmap(playerBmp, p.x, p.y, paint);
            return;
        }

        paint.setColor(Color.rgb(30, 80, 220));
        c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint);
    }
}
