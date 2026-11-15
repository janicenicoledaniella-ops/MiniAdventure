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

    public RectF goal;

    private final Random random = new Random();

    // Peti harta dibuat jarang muncul:
    // - TREASURE_CHANCE: peluang muncul di sebuah level (0..1), maksimal 1 peti per level.
    // - TREASURE_COOLDOWN_LEVELS: setelah ada peti, sekian level berikutnya pasti tanpa peti.
    // Makin kecil peluang / makin besar cooldown, makin jarang muncul.
    private static final float TREASURE_CHANCE = 0.20f;
    private static final int TREASURE_COOLDOWN_LEVELS = 2;

    private int treasureCooldown;
    private int lastTreasurePlace = -1;

    private int score;

    public void resetScore() {
        score = 0;

        // Permainan baru: hitungan jarak antarpeti ikut diulang.
        treasureCooldown = 0;
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

        // Skor tetap berlanjut antarputaran.
        round = Math.max(1, round);

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

        // Peti harta: jarang muncul.
        maybeSpawnTreasure(groundSections, brickPlatforms, u);

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

    public void collectCoins(RectF playerRect) {
        for (int i = coins.size() - 1; i >= 0; i--) {
            if (RectF.intersects(playerRect, coins.get(i))) {
                coins.remove(i);
                score += 10;
            }
        }
    }

    // Peti harta: jarang muncul, dan posisinya bervariasi
    // (di tanah, di atas brick, atau melayang). Jenis posisi yang sama
    // dengan peti sebelumnya dihindari supaya tidak monoton.
    private void maybeSpawnTreasure(
            List<float[]> sections,
            List<RectF> bricks,
            float u
    ) {
        if (treasureCooldown > 0) {
            treasureCooldown--;
            return;
        }

        if (random.nextFloat() >= TREASURE_CHANCE) {
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
                treasureCooldown = TREASURE_COOLDOWN_LEVELS;

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