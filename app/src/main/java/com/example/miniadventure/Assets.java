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

    private Bitmap gameOverBmp;
    private Bitmap retryBmp;
    private Bitmap[] backgrounds = new Bitmap[3];
    private int currentBgIndex = 0;

    public Bitmap flagBmp;

    public Bitmap enemyBmp;
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

    // Karakter yang bisa dipilih: 0 = girl 1, 1 = boy 1, 2 = girl 2, 3 = boy 2.
    private static final int[] CHAR_RES = {
            R.drawable.chara_girl1,
            R.drawable.chara_boy1,
            R.drawable.chara_girl2,
            R.drawable.chara_boy2
    };
    public static final String[] CHAR_NAMES = {"Girl 1", "Boy 1", "Girl 2", "Boy 2"};

    private final Bitmap[] charGameBmp = new Bitmap[CHAR_RES.length];     // ukuran di dalam game
    private final Bitmap[] charPreviewBmp = new Bitmap[CHAR_RES.length];  // ukuran di layar pilih karakter
    private boolean facingLeft = false;

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

        enemyBmp = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.enemy
        );

        loadCharacters(u);

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

    // Memotong bitmap sampai batas piksel yang terlihat (alpha > 20).
    private Bitmap trimTransparent(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) return src;
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return src;

        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // Sprite diperkecil supaya hemat memori.
    private void loadCharacters(float u) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;

        for (int i = 0; i < CHAR_RES.length; i++) {
            Bitmap raw = BitmapFactory.decodeResource(
                    context.getResources(), CHAR_RES[i], opts);
            if (raw == null) continue;

            // Buang ruang transparan di sekeliling karakter supaya semua karakter
            // sama tinggi dan kakinya pas di lantai.
            Bitmap trimmed = trimTransparent(raw);
            if (trimmed != raw) {
                raw.recycle();
                raw = trimmed;
            }

            float ratio = (float) raw.getWidth() / raw.getHeight();

            int gameH = Math.round(u * 1.2f);
            int prevH = Math.round(u * 5.0f);

            charGameBmp[i] = Bitmap.createScaledBitmap(
                    raw, Math.max(1, Math.round(gameH * ratio)), gameH, true);
            charPreviewBmp[i] = Bitmap.createScaledBitmap(
                    raw, Math.max(1, Math.round(prevH * ratio)), prevH, true);

            raw.recycle();
        }
    }

    public void setPlayerCharacter(int index) {
        if (index < 0 || index >= charGameBmp.length) return;
        playerBmp = charGameBmp[index];
        facingLeft = false;
    }

    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    // Menggambar gambar karakter dengan opacity rendah (dipakai layar pilih karakter).
    // feetY = posisi kaki, scale = skala dari ukuran preview, alpha = 0..255.
    public void drawCharacterGhost(Canvas c, int index, float cx, float feetY,
                                   float scale, int alpha) {
        if (index < 0 || index >= charPreviewBmp.length) return;
        Bitmap b = charPreviewBmp[index];
        if (b == null) return;

        float w = b.getWidth() * scale;
        float h = b.getHeight() * scale;

        ghostPaint.setAlpha(alpha);
        c.drawBitmap(b, null, new RectF(cx - w / 2f, feetY - h, cx + w / 2f, feetY), ghostPaint);
    }

    // Lebar gambar karakter di layar pilih karakter (dalam piksel, skala 1).
    public float getCharacterPreviewWidth(int index) {
        if (index < 0 || index >= charPreviewBmp.length) return 0f;
        Bitmap b = charPreviewBmp[index];
        return b == null ? 0f : b.getWidth();
    }

    public void drawCharacterPreview(Canvas c, int index, float cx, float top) {
        Bitmap b = charPreviewBmp[index];
        if (b == null) return;
        c.drawBitmap(b, cx - b.getWidth() / 2f, top, paint);
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
        if (enemyBmp != null) {
            paint.setColor(Color.WHITE);

            float width = e.rect.width() * 2.5f;
            float height = e.rect.height() * 2.5f;
            float bottom = e.rect.bottom + e.rect.height() * 0.6f;

            RectF dst = new RectF(
                    e.rect.centerX() - width / 2f,
                    bottom - height,
                    e.rect.centerX() + width / 2f,
                    bottom
            );

            c.drawBitmap(enemyBmp, null, dst, paint);
        } else {
            paint.setColor(Color.RED);
            c.drawRect(e.rect, paint);
        }
    }
    public void drawPlayer(Canvas c, Player p) {
        if (playerBmp != null) {
            if (p.vx < 0) facingLeft = true;
            else if (p.vx > 0) facingLeft = false;

            // Sprite berdiri di atas hitbox: tengah horizontal, kaki sejajar dasar.
            float left = p.x + p.w / 2f - playerBmp.getWidth() / 2f;
            float top = p.y + p.h - playerBmp.getHeight();

            c.save();
            if (facingLeft) {
                c.scale(-1f, 1f, p.x + p.w / 2f, 0f);
            }
            c.drawBitmap(playerBmp, left, top, paint);
            c.restore();
            return;
        }

        paint.setColor(Color.rgb(30, 80, 220));
        c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint);
    }
}