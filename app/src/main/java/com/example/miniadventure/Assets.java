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
    private Bitmap rockBmp;
    public Bitmap leftArrowBmp;
    public Bitmap rightArrowBmp;
    public Bitmap jumpArrowBmp;

    public Bitmap heart1Bmp;
    public Bitmap heart2Bmp;
    public Bitmap heart3Bmp;

    private Bitmap bronzeBmp;
    private Bitmap silverBmp;
    private Bitmap goldBmp;
    private Bitmap diamondBmp;
    private Bitmap brickBmp;
    private Bitmap coinBmp;
    private Bitmap groundBmp;
    private Bitmap playerBmp;

    private Bitmap treasureClosedBmp;
    private Bitmap treasureHeartBmp;
    private Bitmap treasureSpeedBmp;
    private Bitmap treasureCoinBmp;
    private Bitmap treasureFlashlightBmp;
    private Bitmap flashlightBmp;
    private final Paint treasurePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private static final int[] CHAR_RES = {
            R.drawable.chara_girl1,
            R.drawable.chara_boy1,
            R.drawable.chara_girl2,
            R.drawable.chara_boy2,
            R.drawable.chara_robot
    };
    public static final String[] CHAR_NAMES = {"Ryka", "Kairo", "Aveline", "Jax", "Vex-9"};

    private static final float[] CHAR_SCALE = {1f, 1f, 1f, 1f, 1f};

    private final Bitmap[] charGameBmp = new Bitmap[CHAR_RES.length];
    private final Bitmap[] charPreviewBmp = new Bitmap[CHAR_RES.length];
    private boolean facingLeft = false;

    private static class AnimSet {
        final String prefix;
        final int runCount, jumpCount;
        final float scale;
        final float runCycleUnits;
        final float runBlend;
        boolean useRunMids = true;
        boolean crossfade = true;
        Bitmap[] bStand, bRun, bJump;

        AnimSet(String prefix, int runCount, int jumpCount,
                float scale, float runCycleUnits, float runBlend) {
            this.prefix = prefix;
            this.runCount = runCount;
            this.jumpCount = jumpCount;
            this.scale = scale;
            this.runCycleUnits = runCycleUnits;
            this.runBlend = runBlend;
        }

        AnimSet(String prefix, int runCount, int jumpCount,
                float scale, float runCycleUnits, boolean crossfade) {
            this(prefix, runCount, jumpCount, scale, runCycleUnits, 0.35f);
            this.crossfade = crossfade;
        }

        AnimSet(String prefix, int runCount, int jumpCount,
                float scale, float runCycleUnits, float runBlend, boolean crossfade) {
            this(prefix, runCount, jumpCount, scale, runCycleUnits, runBlend);
            this.crossfade = crossfade;
        }

        AnimSet noRunMids() {
            this.useRunMids = false;
            return this;
        }

        boolean ready() {
            return bStand != null && bRun != null && bJump != null && bJump.length >= 3;
        }
    }

    private final AnimSet[] animSets = {
            new AnimSet("ryka", 4, 5, 2.4f, 2.4f, 0.4f),
            new AnimSet("kairo", 5, 5, 2.4f, 2.2f, 0.4f, false).noRunMids(),
            new AnimSet("aveline", 5, 5, 2.4f, 1.8f, false),
            new AnimSet("jax", 5, 5, 2.4f, 2.0f, 0.5f, false),
            new AnimSet("vex", 5, 5, 2.4f, 2.8f, 0.5f)
    };

    private static final long LAND_MS = 0;
    private static final float FOOT_SINK_UNITS = 0.02f;
    private static final float GROUND_TOP_UNITS = 9f;
    private static final float GRASS_SINK_UNITS = 0.28f;

    private int currentChar = 0;
    private float unitPx = 1f;

    private final Paint blendPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap frameA, frameB;
    private int frameBAlpha;
    private float landAmount;
    private float tiltCur, stretchXCur = 1f, stretchYCur = 1f;
    private float runBob;
    private boolean wasOnGround = true;
    private long landStartMs = -1000;
    private float sinkCur = 0f;
    private long lastDrawMs = 0;
    private int previewBaseH = 0;

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
        bronzeBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.bronze
        );

        silverBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.silver
        );

        goldBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.gold
        );

        diamondBmp = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.diamond
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
        Bitmap rawRock = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.batu
        );

        if (rawRock != null) {
            int rockW = Math.round(u * Rock.DRAW_W);
            int rockH = Math.round(rockW * (float) rawRock.getHeight() / rawRock.getWidth());

            rockBmp = Bitmap.createScaledBitmap(rawRock, rockW, rockH, true);

            if (rockBmp != rawRock) {
                rawRock.recycle();
            }
        }

        treasureClosedBmp = decodeTreasure(R.drawable.treasure_closed);
        treasureHeartBmp = decodeTreasure(R.drawable.treasure_heart);
        treasureSpeedBmp = decodeTreasure(R.drawable.treasure_speed);
        treasureCoinBmp = decodeTreasure(R.drawable.treasure_coin);
        treasureFlashlightBmp = decodeTreasure(R.drawable.treasure_flashlight);
        flashlightBmp = decodeTreasure(R.drawable.senter);
        loadCharacters(u);

        Bitmap rawFlag = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.flag
        );

        if (rawFlag != null) {
            flagBmp = trimTransparent(rawFlag);

            if (flagBmp != rawFlag) {
                rawFlag.recycle();
            }
        }


        coinBmp = decodeScaled(
                R.drawable.coin,
                Math.round(u * 0.8f),
                Math.round(u * 0.8f)
        );


        BitmapFactory.Options groundOpts = new BitmapFactory.Options();
        groundOpts.inScaled = false;

        Bitmap originalGround = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.ground,
                groundOpts
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

    private Bitmap decodeTreasure(int resId) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;

        return BitmapFactory.decodeResource(
                context.getResources(),
                resId,
                opts
        );
    }

    private Bitmap getTreasureOpenedBitmap(int type) {
        switch (type) {
            case Treasure.HEART:
                return treasureHeartBmp;
            case Treasure.SPEED:
                return treasureSpeedBmp;
            case Treasure.COIN:
                return treasureCoinBmp;
            case Treasure.FLASHLIGHT:
                return treasureFlashlightBmp;
            default:
                return treasureClosedBmp;
        }
    }

    public Bitmap getLevelBadge(int round) {
        if (round <= 5) {
            return bronzeBmp;
        } else if (round <= 10) {
            return silverBmp;
        } else if (round <= 20) {
            return goldBmp;
        } else {
            return diamondBmp;
        }
    }

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

    private Bitmap trimWithCentroid(Bitmap src, float[] outCx) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        double sum = 0, sumX = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = px[y * w + x] >>> 24;
                if (a > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    sum += a;
                    sumX += (double) a * x;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            outCx[0] = w / 2f;
            return src;
        }

        outCx[0] = (float) (sumX / sum) - minX + 0.5f;
        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private Bitmap[] loadAnim(int[] resIds, int canvasW, int canvasH) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;

        Bitmap[] out = new Bitmap[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            Bitmap raw = BitmapFactory.decodeResource(
                    context.getResources(), resIds[i], opts);
            if (raw == null) return null;

            float[] cx = new float[1];
            Bitmap trimmed = trimWithCentroid(raw, cx);

            float k = (float) canvasH / trimmed.getHeight();
            Bitmap scaled = Bitmap.createScaledBitmap(
                    trimmed,
                    Math.max(1, Math.round(trimmed.getWidth() * k)),
                    canvasH,
                    true);

            Bitmap frame = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(frame);
            cv.setDensity(Bitmap.DENSITY_NONE);
            cv.drawBitmap(scaled, canvasW / 2f - cx[0] * k, 0f, null);
            out[i] = frame;

            Bitmap[] toFree = {scaled, trimmed, raw};
            for (Bitmap bmp : toFree) {
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
        }
        return out;
    }

    private int res(String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private int[] runIds(AnimSet set) {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= set.runCount; i++) {
            int id = res(set.prefix + "_run" + i);
            if (id == 0) return null;
            ids.add(id);
            int mid = res(set.prefix + "_run" + i + "_mid");
            if (mid != 0 && set.useRunMids) ids.add(mid);
        }
        return toArray(ids);
    }

    private int[] jumpIds(AnimSet set) {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= set.jumpCount; i++) {
            int id = res(set.prefix + "_jump" + i);
            if (id == 0) return null;
            ids.add(id);
            if (i <= set.jumpCount - 2) {
                int mid = res(set.prefix + "_jump" + i + "_mid");
                if (mid != 0) ids.add(mid);
            }
        }
        return toArray(ids);
    }

    private int[] toArray(java.util.List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private boolean allSameSize(int[]... groups) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        int w = -1, h = -1;
        for (int[] g : groups) {
            for (int id : g) {
                BitmapFactory.decodeResource(context.getResources(), id, o);
                if (w < 0) {
                    w = o.outWidth;
                    h = o.outHeight;
                } else if (o.outWidth != w || o.outHeight != h) {
                    return false;
                }
            }
        }
        return true;
    }

    private int footRow(int resId) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), resId, opts);
        if (bmp == null) return 0;

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        bmp.recycle();

        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 20) return y + 1;
            }
        }
        return h;
    }

    private float silhouetteHeight(int resId) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), resId, opts);
        if (bmp == null) return 0f;

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        bmp.recycle();

        int minY = h, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 20) {
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    break;
                }
            }
        }
        return maxY < minY ? 0f : (maxY - minY + 1);
    }

    private Bitmap[] loadAnimAligned(int[] resIds, float k, int cropH) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;

        Bitmap[] out = new Bitmap[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            Bitmap raw = BitmapFactory.decodeResource(
                    context.getResources(), resIds[i], opts);
            if (raw == null) return null;

            if (cropH > 0 && cropH < raw.getHeight()) {
                Bitmap cut = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), cropH);
                raw.recycle();
                raw = cut;
            }

            out[i] = Bitmap.createScaledBitmap(
                    raw,
                    Math.max(1, Math.round(raw.getWidth() * k)),
                    Math.max(1, Math.round(raw.getHeight() * k)),
                    true);
            if (out[i] != raw) raw.recycle();
        }
        return out;
    }

    private void loadAnimations(float u) {
        unitPx = u;
        for (int i = 0; i < animSets.length; i++) {
            AnimSet set = animSets[i];
            if (set == null) continue;

            int standId = res(set.prefix + "_stand");
            int[] run = runIds(set);
            int[] jump = jumpIds(set);
            if (standId == 0 || run == null || jump == null) continue;
            int[] stand = {standId};

            float charH = u * 1.2f * CHAR_SCALE[i] * set.scale;

            if (allSameSize(stand, run, jump)) {
                float sil = silhouetteHeight(standId);
                float k = sil > 0f ? charH / sil : 1f;
                int foot = footRow(standId);
                set.bStand = loadAnimAligned(stand, k, foot);
                set.bRun = loadAnimAligned(run, k, foot);
                set.bJump = loadAnimAligned(jump, k, foot);
            } else {
                int canvasH = Math.round(charH);
                int canvasW = Math.round(canvasH * 1.2f);
                set.bStand = loadAnim(stand, canvasW, canvasH);
                set.bRun = loadAnim(run, canvasW, canvasH);
                set.bJump = loadAnim(jump, canvasW, canvasH);
            }
        }
    }

    private void loadCharacters(float u) {
        loadAnimations(u);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;

        for (int i = 0; i < CHAR_RES.length; i++) {
            Bitmap raw = BitmapFactory.decodeResource(
                    context.getResources(), CHAR_RES[i], opts);
            if (raw == null) continue;

            Bitmap trimmed = trimTransparent(raw);
            if (trimmed != raw) {
                raw.recycle();
                raw = trimmed;
            }

            float ratio = (float) raw.getWidth() / raw.getHeight();

            previewBaseH = Math.round(u * 5.0f);
            int gameH = Math.round(u * 1.2f * CHAR_SCALE[i]);
            int prevH = Math.round(u * 5.0f * CHAR_SCALE[i]);

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
        currentChar = index;
        facingLeft = false;
    }

    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

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

    public float getCharacterPreviewWidth(int index) {
        if (index < 0 || index >= charPreviewBmp.length) return 0f;
        Bitmap b = charPreviewBmp[index];
        return b == null ? 0f : b.getWidth();
    }

    public void drawCharacterPreview(Canvas c, int index, float cx, float top) {
        Bitmap b = charPreviewBmp[index];
        if (b == null) return;

        float y = top + (previewBaseH - b.getHeight());
        c.drawBitmap(b, cx - b.getWidth() / 2f, y, paint);
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

            if (currentBgIndex == 2) {
                paint.setColor(Color.argb(85, 8, 18, 55));
                c.drawRect(0, 0, sw, sh, paint);
                paint.setColor(Color.WHITE);
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

                    if (currentBgIndex == 2) {
                        paint.setColor(Color.rgb(105, 115, 145));
                    } else {
                        paint.setColor(Color.WHITE);
                    }

                    c.drawBitmap(groundBmp, src, dst, paint);
                    paint.setColor(Color.WHITE);
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

    public void drawTreasure(Canvas c, Treasure treasure, float u) {
        Bitmap bmp = treasure.opened
                ? getTreasureOpenedBitmap(treasure.type)
                : treasureClosedBmp;

        if (bmp == null) {
            return;
        }

        float width = 1.25f * u;
        float height = width * bmp.getHeight() / bmp.getWidth();

        float cx = treasure.rect.centerX();
        float bottom = treasure.rect.bottom;

        if (treasure.place == Treasure.PLACE_GROUND) {
            bottom += 0.12f * u;
        } else if (treasure.place == Treasure.PLACE_BRICK) {
            bottom += 0.05f * u;
        } else {
            double sec = System.nanoTime() / 1e9;
            bottom += (float) Math.sin(sec * 2.2) * 0.1f * u;
        }

        float pop = 0f;

        if (treasure.opened) {
            float ms = (System.nanoTime() - treasure.openedAtNs) / 1_000_000f;

            if (ms >= 0f && ms < 300f) {
                pop = 1f - ms / 300f;
            }
        }

        float scale = 1f + 0.18f * pop;

        RectF dst = new RectF(
                cx - width / 2f,
                bottom - height,
                cx + width / 2f,
                bottom
        );

        c.save();
        c.scale(scale, scale, cx, bottom);
        treasurePaint.setAlpha(255);
        c.drawBitmap(bmp, null, dst, treasurePaint);
        c.restore();
    }

    public void drawTreasureIcon(Canvas c, int type, RectF box, int alpha) {
        Bitmap bmp = getTreasureOpenedBitmap(type);

        if (bmp == null) {
            return;
        }

        float k = Math.min(
                box.width() / bmp.getWidth(),
                box.height() / bmp.getHeight()
        );

        float w = bmp.getWidth() * k;
        float h = bmp.getHeight() * k;

        RectF dst = new RectF(
                box.centerX() - w / 2f,
                box.bottom - h,
                box.centerX() + w / 2f,
                box.bottom
        );

        treasurePaint.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.drawBitmap(bmp, null, dst, treasurePaint);
        treasurePaint.setAlpha(255);
    }
    public void drawFlashlightIcon(Canvas c, RectF box) {
        if (flashlightBmp == null) {
            return;
        }

        float k = Math.min(
                box.width() / flashlightBmp.getWidth(),
                box.height() / flashlightBmp.getHeight()
        );

        float w = flashlightBmp.getWidth() * k;
        float h = flashlightBmp.getHeight() * k;

        RectF dst = new RectF(
                box.centerX() - w / 2f,
                box.centerY() - h / 2f,
                box.centerX() + w / 2f,
                box.centerY() + h / 2f
        );

        c.drawBitmap(flashlightBmp, null, dst, treasurePaint);
    }
    public void drawRock(Canvas c, Rock rock) {
        if (rockBmp == null) {
            paint.setColor(Color.GRAY);
            c.drawRect(rock.rect, paint);
            paint.setColor(Color.WHITE);
            return;
        }

        float cx = rock.rect.centerX();
        float cy = rock.rect.centerY();

        paint.setColor(Color.WHITE);

        c.save();
        c.rotate(rock.angle, cx, cy);
        c.drawBitmap(
                rockBmp,
                cx - rockBmp.getWidth() / 2f,
                cy - rockBmp.getHeight() / 2f,
                paint
        );
        c.restore();
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
    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private void pickPlayerFrame(Player p) {
        frameA = null;
        frameB = null;
        frameBAlpha = 0;
        runBob = 0f;
        landAmount = 0f;

        AnimSet set = (currentChar >= 0 && currentChar < animSets.length)
                ? animSets[currentChar] : null;
        if (set == null || !set.ready()) {
            frameA = playerBmp;
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();

        if (!p.onGround) {
            wasOnGround = false;

            float v = p.vy / unitPx;
            float pos;
            if (v <= -12f) pos = 0f;
            else if (v <= -5f) pos = (v + 12f) / 7f;
            else if (v <= 0f) pos = 1f + (v + 5f) / 5f;
            else if (v <= 6f) pos = 2f + v / 6f;
            else pos = 3f;

            int air = set.bJump.length - 1;
            float posN = pos / 3f * (air - 1);
            if (!set.crossfade) {
                frameA = set.bJump[Math.max(0, Math.min(air - 1, Math.round(posN)))];
                return;
            }
            int i = Math.min((int) posN, air - 2);
            float f = posN - i;
            frameA = set.bJump[i];
            frameB = set.bJump[i + 1];
            frameBAlpha = Math.round(smooth(f) * 255f);
            return;
        }

        if (!wasOnGround) {
            wasOnGround = true;
            landStartMs = now;
        }

        if (p.vx != 0f) {
            float ph = p.x / (unitPx * set.runCycleUnits / set.bRun.length);
            int i = (int) Math.floor(ph);
            float f = ph - i;
            int n = set.bRun.length;
            frameA = set.bRun[Math.floorMod(i, n)];
            runBob = Math.abs((float) Math.sin(ph / n * 2.0 * Math.PI)) * 0.05f;

            float start = 1f - set.runBlend;
            if (f > start) {
                frameB = set.bRun[Math.floorMod(i + 1, n)];
                frameBAlpha = Math.round(smooth((f - start) / set.runBlend) * 255f);
            }
        } else {
            frameA = set.bStand[0];
        }

        float land = LAND_MS > 0 ? 1f - (now - landStartMs) / (float) LAND_MS : 0f;
        if (land > 0f) {
            landAmount = Math.min(1f, land);
            frameB = set.bJump[set.bJump.length - 1];
            frameBAlpha = Math.round(smooth(land) * 255f);
        }
    }

    public void drawPlayer(Canvas c, Player p) {
        pickPlayerFrame(p);

        if (frameA != null) {
            if (p.vx < 0) facingLeft = true;
            else if (p.vx > 0) facingLeft = false;

            float cx = p.x + p.w / 2f;
            float feetY = p.y + p.h;
            float target = 0f;
            if (p.onGround && Math.abs(feetY - GROUND_TOP_UNITS * unitPx) < 0.05f * unitPx) {
                target = GRASS_SINK_UNITS * unitPx;
            }
            long nowMs = android.os.SystemClock.uptimeMillis();
            float dt = Math.min(0.1f, Math.max(0f, (nowMs - lastDrawMs) / 1000f));
            lastDrawMs = nowMs;
            sinkCur += (target - sinkCur) * (1f - (float) Math.exp(-dt * 25f));

            float bottom = feetY + sinkCur + unitPx * FOOT_SINK_UNITS - runBob * unitPx;

            AnimSet set = (currentChar >= 0 && currentChar < animSets.length)
                    ? animSets[currentChar] : null;
            boolean smoothMotion = set != null && set.ready() && !set.crossfade;

            float tiltT = 0f, sxT = 1f, syT = 1f;
            if (smoothMotion) {
                float v = p.vy / unitPx;
                if (!p.onGround) {
                    float s = Math.min(Math.abs(v), 14f) * 0.004f;
                    syT = 1f + s;
                    sxT = 1f - s * 0.6f;
                } else {
                    if (p.vx != 0f) tiltT = 3f + runBob * 40f;
                    float sq = smooth(landAmount);
                    syT = 1f - 0.07f * sq;
                    sxT = 1f + 0.05f * sq;
                }
            }
            float ease = 1f - (float) Math.exp(-dt * 20f);
            tiltCur += (tiltT - tiltCur) * ease;
            stretchXCur += (sxT - stretchXCur) * ease;
            stretchYCur += (syT - stretchYCur) * ease;

            c.save();
            if (facingLeft) {
                c.scale(-1f, 1f, cx, 0f);
            }
            c.translate(cx, bottom);
            c.rotate(tiltCur);
            c.scale(stretchXCur, stretchYCur);

            c.drawBitmap(frameA, -frameA.getWidth() / 2f, -frameA.getHeight(), paint);

            if (frameB != null && frameBAlpha > 0) {
                blendPaint.setAlpha(frameBAlpha);
                c.drawBitmap(frameB, -frameB.getWidth() / 2f, -frameB.getHeight(), blendPaint);
            }
            c.restore();
            return;
        }

        paint.setColor(Color.rgb(30, 80, 220));
        c.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint);
    }
}