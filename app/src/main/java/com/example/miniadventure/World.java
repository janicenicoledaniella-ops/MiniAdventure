package com.example.miniadventure;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {

    public final List<RectF> platforms = new ArrayList<>();
    public final List<RectF> coins = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Treasure> treasures = new ArrayList<>();
    public final List<Rock> rocks = new ArrayList<>(); // batu rintangan

    public RectF goal;

    private final Random random = new Random();

    // Peti harta muncul terjadwal berdasarkan nomor level:
    // - Peti pertama selalu di level TREASURE_FIRST_LEVEL (level 3).
    // - Setelah itu jaraknya acak TREASURE_GAP_MIN..TREASURE_GAP_MAX level (2, 3, atau 4).
    //   Contoh: level 3 -> 5/6/7 -> dst.
    private static final int TREASURE_FIRST_LEVEL = 3;
    private static final int TREASURE_GAP_MIN = 2;
    private static final int TREASURE_GAP_MAX = 4;

    // Batu rintangan: mulai muncul dari level ROCK_MIN_ROUND, munculnya acak.
    private static final int ROCK_MIN_ROUND = 4;
    private static final float ROCK_CHANCE = 0.75f;   // peluang batu muncul tiap giliran
    private float rockTimer;
    private int currentRound = 1;
    private float finishStartPx;

    private int nextTreasureLevel = TREASURE_FIRST_LEVEL; // level berikutnya yang dapat peti
    private int currentTreasureLevel = -1;                // level terakhir yang sudah diberi peti
    private int lastTreasurePlace = -1;
    private boolean flashlightChestWanted; // peti senter dijamin ada di level ini

    private int score;

    public void resetScore() {
        score = 0;

        // Permainan baru: jadwal peti kembali ke level 3.
        nextTreasureLevel = TREASURE_FIRST_LEVEL;
        currentTreasureLevel = -1;
        lastTreasurePlace = -1;
    }

    public int getScore() {
        return score;
    }

    private float rand(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public void buildLevel(float u) {
        buildLevel(u, 1);
    }

    public void buildLevel(float u, int round) {
        platforms.clear();
        coins.clear();
        enemies.clear();
        treasures.clear();
        rocks.clear();
        rockTimer = rand(4f, 7f);

        // Skor tetap berlanjut antarputaran.
        round = Math.max(1, round);
        currentRound = round;

        float difficulty = Math.min(
                1f,
                (round - 1) / 10f
        );

        float enemySpeed =
                1f + (round - 1) * 0.08f;

        int sections =
                4 + Math.min(4, (round - 1) / 3);

        float normalGapMax =
                1.8f + difficulty * 2.7f;

        // Area awal tetap aman tanpa musuh.
        addGround(0, 14, u);
        addCoins(rand(4, 7), 8, 3, u);

        // Daftar tanah utama (x, lebar): tempat peti harta boleh muncul.
        List<float[]> groundSections = new ArrayList<>();

        // Daftar pijakan brick melayang: tempat peti harta di atas brick.
        List<RectF> brickPlatforms = new ArrayList<>();

        float end = 14;

        for (int i = 0; i < sections; i++) {
            boolean useIsland =
                    round >= 2 &&
                            (
                                    i == sections / 2 ||
                                            random.nextFloat() < 0.2f * difficulty
                            );

            float x;

            if (useIsland) {
                float islandGapMin;
                float islandGapMax;
                float islandWidth;

                if (round >= 5) {
                    islandGapMin = 3f;

                    islandGapMax = Math.min(
                            3.8f,
                            3f + (round - 4) * 0.12f
                    );
                } else {
                    islandGapMin = 1.5f;
                    islandGapMax = normalGapMax;
                }

                if (round >= 8) {
                    // Lebar pemain 0,8 unit.
                    // Pijakan ini cukup untuk berdiri,
                    // tetapi membutuhkan pendaratan lebih tepat.
                    islandWidth = rand(1.2f, 1.6f);
                } else if (round >= 5) {
                    islandWidth = rand(2f, 2.6f);
                } else {
                    islandWidth = rand(5.5f, 6.5f);
                }

                float islandX =
                        end + rand(islandGapMin, islandGapMax);

                addGround(
                        islandX,
                        islandWidth,
                        u
                );

                // Satu koin tepat di tengah pijakan.
                addCoins(
                        islandX + islandWidth / 2f - 0.25f,
                        8,
                        1,
                        u
                );

                x = islandX
                        + islandWidth
                        + rand(islandGapMin, islandGapMax);
            } else {
                x = end + rand(1.3f, normalGapMax);
            }

            float width = rand(12, 16);

            addGround(x, width, u);
            groundSections.add(new float[]{x, width});

            // Platform atas tidak selalu muncul.
            RectF floatingPlatform = null;

            if (random.nextFloat() < 0.65f) {
                float platformWidth = rand(2f, 3.5f);

                float platformX = x + rand(
                        3,
                        width - platformWidth - 3
                );

                float platformY = rand(6.6f, 7.3f);

                floatingPlatform = new RectF(
                        platformX * u,
                        platformY * u,
                        (platformX + platformWidth) * u,
                        (platformY + 0.5f) * u
                );

                platforms.add(floatingPlatform);
                brickPlatforms.add(floatingPlatform);

                int coinCount =
                        platformWidth >= 3f ? 3 : 2;

                float coinRowWidth =
                        (coinCount - 1) * 0.9f + 0.5f;

                float coinStart =
                        platformX
                                + (platformWidth - coinRowWidth) / 2f;

                addCoins(
                        coinStart,
                        platformY - 1,
                        coinCount,
                        u
                );
            }

            // Posisi dan jumlah koin tanah juga diacak.
            addCoins(
                    x + rand(2, width - 5),
                    8,
                    2 + random.nextInt(2),
                    u
            );

            // Musuh ditempatkan di tanah utama.
            // Pijakan kecil di tengah jurang tidak diberi musuh.
            if (i == 0 ||
                    random.nextFloat() < 0.45f + difficulty * 0.2f) {

                // Semakin tinggi putaran, semakin kecil jarak
                // batas patroli dari tepi tanah.
                float margin = Math.max(
                        1.3f,
                        3.5f - (round - 1) * 0.2f
                );

                float minX = x + margin;
                float maxX = x + width - margin;

                // Posisi awal musuh tidak selalu di tengah.
                float enemyX = rand(
                        minX,
                        maxX - 0.8f
                );

                Enemy enemy = new Enemy(
                        enemyX * u,
                        u,
                        minX * u,
                        maxX * u
                );

                enemy.setSpeedMultiplier(enemySpeed);

                enemies.add(enemy);
            }
            // Musuh tambahan pada floating platform.
            if (round >= 2 &&
                    floatingPlatform != null &&
                    random.nextFloat() < 0.15f + difficulty * 0.35f) {

                float minX = floatingPlatform.left;
                float maxX = floatingPlatform.right;

                float enemyX = rand(
                        minX,
                        maxX - 0.8f * u
                );

                Enemy platformEnemy = new Enemy(
                        enemyX,
                        u,
                        minX,
                        maxX
                );

                platformEnemy.rect.offset(
                        0,
                        floatingPlatform.top - platformEnemy.rect.bottom
                );

                platformEnemy.setSpeedMultiplier(enemySpeed);

                enemies.add(platformEnemy);
            }

            end = x + width;
        }

        // Peti harta: muncul sesuai jadwal level.
        maybeSpawnTreasure(groundSections, brickPlatforms, u, round);
        ensureFlashlightChest(groundSections, brickPlatforms, u); // peti senter untuk kabut gelap

        buildFinish(
                end,
                normalGapMax,
                round,
                u
        );
    }

    private void buildFinish(
            float previousEnd,
            float maxGap,
            int round,
            float u
    ) {
        float finishX =
                previousEnd + rand(1.3f, maxGap);

        finishStartPx = finishX * u; // batu tidak muncul di area finis

        // Tanah di bawah platform finis tetap tersedia
        // agar pemain bisa mencoba melompat kembali.
        addGround(finishX, 20, u);

        int steps;

        if (round == 1) {
            steps = 0;
        } else if (round <= 3) {
            steps = 1;
        } else if (round <= 5) {
            steps = 2;
        } else {
            steps = 3;
        }

        if (steps == 0) {
            goal = new RectF(
                    (finishX + 17) * u,
                    6 * u,
                    (finishX + 18) * u,
                    9 * u
            );

            return;
        }

        float platformX = finishX;
        float platformTop = 9;

        for (int i = 0; i < steps; i++) {
            platformX = finishX + 4 + i * 4;
            platformTop = 8 - i;

            platforms.add(new RectF(
                    platformX * u,
                    platformTop * u,
                    (platformX + 3) * u,
                    (platformTop + 0.5f) * u
            ));

            addCoins(
                    platformX + 0.4f,
                    platformTop - 1,
                    2,
                    u
            );
        }

        goal = new RectF(
                (platformX + 1.5f) * u,
                (platformTop - 2) * u,
                (platformX + 2.2f) * u,
                platformTop * u
        );
    }

    private void addGround(
            float x,
            float width,
            float u
    ) {
        platforms.add(new RectF(
                x * u,
                9f * u,
                (x + width) * u,
                12f * u
        ));
    }

    private void addCoins(
            float x,
            float y,
            int count,
            float u
    ) {
        for (int i = 0; i < count; i++) {
            coins.add(new RectF(
                    (x + i * 0.9f) * u,
                    y * u,
                    (x + i * 0.9f + 0.5f) * u,
                    (y + 0.5f) * u
            ));
        }
    }

    public void updateEnemies(float dt, float u) {
        for (Enemy enemy : enemies) {
            enemy.update(dt, u);
        }
    }

    // Hapus semua batu (dipanggil saat pemain mulai lagi setelah kehilangan nyawa).
    public void clearRocks() {
        rocks.clear();
        rockTimer = rand(3f, 6f);
    }

    // Atur kapan batu muncul dan gerakkan semua batu.
    public void updateRocks(float dt, float u, float camX, int sw) {
        if (currentRound < ROCK_MIN_ROUND) {
            return;
        }

        rockTimer -= dt;

        if (rockTimer <= 0f) {
            // Makin tinggi level, jedanya makin pendek (maksimal 40% lebih cepat).
            float pace = Math.max(0.6f, 1f - (currentRound - ROCK_MIN_ROUND) * 0.06f);

            rockTimer = rand(3.5f, 7.5f) * pace;

            int maxRocks = currentRound >= 7 ? 3 : 2;

            if (rocks.size() < maxRocks && random.nextFloat() < ROCK_CHANCE) {
                spawnRock(u, camX, sw);
            }
        }

        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock rock = rocks.get(i);

            rock.update(dt, u, platforms);

            // Hapus batu yang jatuh ke jurang, sudah jauh di belakang layar, atau tersangkut.
            if (rock.rect.top > 14f * u || rock.rect.right < camX - 3f * u || rock.isStuck()) {
                rocks.remove(i);
            }
        }
    }

    public boolean hitsRock(RectF playerRect, float u) {
        for (Rock rock : rocks) {
            if (RectF.intersects(playerRect, rock.getHitbox(u))) {
                return true;
            }
        }

        return false;
    }

    private float rockSpeed(float u) {
        float boost = 1f + Math.min(0.5f, (currentRound - ROCK_MIN_ROUND) * 0.05f);

        return rand(3.2f, 4.6f) * boost * u;
    }

    // Jenis batu diacak: menggelinding dari brick (jatuh ke tanah) atau langsung di tanah.
    private void spawnRock(float u, float camX, int sw) {
        if (random.nextFloat() < 0.6f && spawnBrickRock(u, camX, sw)) {
            return;
        }

        spawnGroundRock(u, camX + sw);
    }

    private boolean rockTooClose(float x, float u) {
        for (Rock rock : rocks) {
            if (Math.abs(rock.rect.left - x) < 7f * u) {
                return true;
            }
        }

        return false;
    }

    // Batu mulai di ujung kanan sebuah brick, menggelinding ke kiri lalu jatuh ke tanah.
    private boolean spawnBrickRock(float u, float camX, int sw) {
        float viewRight = camX + sw;
        List<RectF> candidates = new ArrayList<>();

        for (RectF platform : platforms) {
            boolean isBrick = platform.height() < 1.5f * u && platform.top < 8.5f * u;

            // Brick di 30% layar sebelah kanan (atau tepat di luarnya), supaya batu
            // tidak muncul terlalu dekat dengan pemain. Bukan area finis dan tidak dijaga musuh.
            boolean nearView = platform.right > camX + sw * 0.7f
                    && platform.right < viewRight + 12f * u;

            if (isBrick
                    && nearView
                    && platform.left < finishStartPx
                    && !brickHasEnemy(platform, u)
                    && !rockTooClose(platform.right - Rock.SIZE * u, u)) {
                candidates.add(platform);
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        RectF brick = candidates.get(random.nextInt(candidates.size()));
        float left = brick.right - (Rock.SIZE + 0.1f) * u;

        rocks.add(new Rock(left, brick.top, rockSpeed(u), u));

        return true;
    }

    // Batu mulai di luar layar sebelah kanan, di atas tanah.
    private void spawnGroundRock(float u, float viewRight) {
        List<float[]> spots = new ArrayList<>();

        for (RectF platform : platforms) {
            boolean isGround = platform.height() > 1.5f * u
                    && Math.abs(platform.top - 9f * u) < 0.1f;

            if (!isGround) {
                continue;
            }

            float x = Math.max(viewRight + 1.5f * u, platform.left + 0.5f * u);

            if (x + Rock.SIZE * u < platform.right - 0.5f * u
                    && x < finishStartPx + 2f * u
                    && !rockTooClose(x, u)) {
                spots.add(new float[]{x, platform.top});
            }
        }

        if (spots.isEmpty()) {
            return;
        }

        float[] spot = spots.get(random.nextInt(spots.size()));

        rocks.add(new Rock(spot[0], spot[1], rockSpeed(u), u));
    }

    // Diatur GameView sebelum level dibuat: true kalau kabut gelap sedang atau akan turun.
    public void setFlashlightChestWanted(boolean wanted) {
        flashlightChestWanted = wanted;
    }

    // Kalau kabut gelap akan turun, level ini pasti punya satu peti berisi senter.
    private void ensureFlashlightChest(
            List<float[]> sections,
            List<RectF> bricks,
            float u
    ) {
        if (!flashlightChestWanted) {
            return;
        }

        // Sudah ada peti biasa di level ini: jadikan peti senter.
        if (!treasures.isEmpty()) {
            treasures.get(0).reward = Treasure.FLASHLIGHT;
            return;
        }

        List<Integer> kinds = new ArrayList<>();

        if (!sections.isEmpty()) {
            kinds.add(Treasure.PLACE_GROUND);
            kinds.add(Treasure.PLACE_AIR);
        }

        if (!bricks.isEmpty()) {
            kinds.add(Treasure.PLACE_BRICK);
        }

        while (!kinds.isEmpty()) {
            int kind = kinds.remove(random.nextInt(kinds.size()));

            Treasure treasure = tryPlaceTreasure(kind, sections, bricks, u);

            if (treasure != null) {
                treasure.reward = Treasure.FLASHLIGHT;
                treasures.add(treasure);

                return;
            }
        }
    }

    public void collectCoins(RectF playerRect) {
        for (int i = coins.size() - 1; i >= 0; i--) {
            if (RectF.intersects(playerRect, coins.get(i))) {
                coins.remove(i);
                score += 10;
            }
        }
    }

    // Peti harta: muncul sesuai jadwal level, dan posisinya bervariasi
    // (di tanah, di atas brick, atau melayang). Jenis posisi yang sama
    // dengan peti sebelumnya dihindari supaya tidak monoton.
    private void maybeSpawnTreasure(
            List<float[]> sections,
            List<RectF> bricks,
            float u,
            int round
    ) {
        // Level yang sama dibangun ulang: peti tetap muncul,
        // jadwal berikutnya tidak diubah lagi.
        boolean sameLevelAgain = round == currentTreasureLevel;

        if (!sameLevelAgain && round < nextTreasureLevel) {
            return;
        }

        List<Integer> kinds = new ArrayList<>();

        if (!sections.isEmpty()) {
            kinds.add(Treasure.PLACE_GROUND);
            kinds.add(Treasure.PLACE_AIR);
        }

        if (!bricks.isEmpty()) {
            kinds.add(Treasure.PLACE_BRICK);
        }

        if (kinds.size() > 1) {
            kinds.remove(Integer.valueOf(lastTreasurePlace));
        }

        while (!kinds.isEmpty()) {
            int kind = kinds.remove(random.nextInt(kinds.size()));

            Treasure treasure = tryPlaceTreasure(kind, sections, bricks, u);

            if (treasure != null) {
                treasures.add(treasure);

                lastTreasurePlace = kind;

                if (!sameLevelAgain) {
                    currentTreasureLevel = round;

                    // Jarak ke peti berikutnya acak: 2, 3, atau 4 level.
                    nextTreasureLevel = round + TREASURE_GAP_MIN
                            + random.nextInt(TREASURE_GAP_MAX - TREASURE_GAP_MIN + 1);
                }

                return;
            }
        }
    }

    // Mencari posisi bebas untuk satu jenis penempatan. Null kalau tidak ketemu.
    private Treasure tryPlaceTreasure(
            int kind,
            List<float[]> sections,
            List<RectF> bricks,
            float u
    ) {
        for (int attempt = 0; attempt < 25; attempt++) {
            RectF rect;

            if (kind == Treasure.PLACE_BRICK) {
                RectF brick = bricks.get(random.nextInt(bricks.size()));

                // Pijakan yang sudah dijaga musuh dilewati.
                if (brickHasEnemy(brick, u)) {
                    continue;
                }

                float left = rand(
                        brick.left + 0.2f * u,
                        brick.right - 1.4f * u
                );

                rect = new RectF(left, brick.top - u, left + 1.2f * u, brick.top);

                // Koin di pijakan itu yang menghalangi peti dihapus.
                RectF clear = new RectF(rect);
                clear.inset(-0.2f * u, -0.2f * u);

                for (int i = coins.size() - 1; i >= 0; i--) {
                    if (RectF.intersects(clear, coins.get(i))) {
                        coins.remove(i);
                    }
                }

                return new Treasure(rect, kind);
            }

            float[] section = sections.get(random.nextInt(sections.size()));
            float x = section[0] + rand(1.5f, section[1] - 2.7f);

            // Tanah: dasar peti di permukaan tanah (9). Udara: melayang di atas tanah,
            // masih terjangkau dengan satu lompatan.
            float bottom = kind == Treasure.PLACE_GROUND
                    ? 9f
                    : rand(6.9f, 7.5f);

            rect = new RectF(
                    x * u,
                    (bottom - 1f) * u,
                    (x + 1.2f) * u,
                    bottom * u
            );

            if (isTreasureSpotFree(rect, kind, u)) {
                return new Treasure(rect, kind);
            }
        }

        return null;
    }

    // Peti di tanah / udara tidak boleh menumpuk dengan koin,
    // dan peti di udara tidak boleh terlalu dekat dengan pijakan melayang.
    private boolean isTreasureSpotFree(RectF rect, int kind, float u) {
        RectF area = new RectF(rect);
        area.inset(-0.3f * u, -0.2f * u);

        for (RectF coin : coins) {
            if (RectF.intersects(area, coin)) {
                return false;
            }
        }

        if (kind == Treasure.PLACE_AIR) {
            RectF air = new RectF(rect);
            air.inset(-0.8f * u, -0.8f * u);

            for (RectF platform : platforms) {
                if (RectF.intersects(air, platform)) {
                    return false;
                }
            }
        }

        return true;
    }

    // Apakah ada musuh yang berpatroli di atas pijakan brick ini?
    private boolean brickHasEnemy(RectF brick, float u) {
        for (Enemy enemy : enemies) {
            boolean sameX = enemy.rect.right > brick.left
                    && enemy.rect.left < brick.right;

            boolean onTop = Math.abs(enemy.rect.bottom - brick.top) < 0.5f * u;

            if (sameX && onTop) {
                return true;
            }
        }

        return false;
    }

    // Peti yang baru disentuh pemain dibuka. Mengembalikan peti itu (atau null).
    public Treasure openTreasure(RectF playerRect) {
        for (Treasure treasure : treasures) {
            if (!treasure.opened
                    && RectF.intersects(playerRect, treasure.rect)) {

                treasure.opened = true;
                treasure.openedAtNs = System.nanoTime();

                return treasure;
            }
        }

        return null;
    }

    // Menambah skor/koin langsung (dipakai hadiah peti harta).
    public void addScore(int amount) {
        score += amount;
    }

    public Enemy stompEnemy(
            RectF playerRect,
            float previousBottom,
            boolean falling
    ) {
        if (!falling) {
            return null;
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);

            boolean horizontalOverlap =
                    playerRect.right > enemy.rect.left &&
                            playerRect.left < enemy.rect.right;

            boolean crossedTop =
                    previousBottom <= enemy.rect.top &&
                            playerRect.bottom >= enemy.rect.top;

            if (horizontalOverlap && crossedTop) {
                enemies.remove(i);
                score += 50;

                return enemy;
            }
        }

        return null;
    }

    public boolean hitsEnemy(RectF playerRect) {
        for (Enemy enemy : enemies) {
            if (RectF.intersects(playerRect, enemy.rect)) {
                return true;
            }
        }

        return false;
    }
}