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
    public final List<Rock> rocks = new ArrayList<>();

    public RectF goal;

    private final Random random = new Random();

    private static final int TREASURE_FIRST_LEVEL = 3;
    private static final int TREASURE_GAP_MIN = 2;
    private static final int TREASURE_GAP_MAX = 4;

    private static final int ROCK_MIN_ROUND = 4;
    private static final float ROCK_CHANCE = 0.75f;
    private float rockTimer;
    private int currentRound = 1;
    private float finishStartPx;

    private int nextTreasureLevel = TREASURE_FIRST_LEVEL;
    private int currentTreasureLevel = -1;
    private int lastTreasurePlace = -1;
    private boolean flashlightChestWanted;

    private int score;

    public void resetScore() {
        score = 0;

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

        addGround(0, 14, u);
        addCoins(rand(4, 7), 8, 3, u);

        List<float[]> groundSections = new ArrayList<>();

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

            addCoins(
                    x + rand(2, width - 5),
                    8,
                    2 + random.nextInt(2),
                    u
            );

            if (i == 0 ||
                    random.nextFloat() < 0.45f + difficulty * 0.2f) {

                float margin = Math.max(
                        1.3f,
                        3.5f - (round - 1) * 0.2f
                );

                float minX = x + margin;
                float maxX = x + width - margin;

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

        maybeSpawnTreasure(groundSections, brickPlatforms, u, round);
        ensureFlashlightChest(groundSections, brickPlatforms, u);

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

        finishStartPx = finishX * u;

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

    public void clearRocks() {
        rocks.clear();
        rockTimer = rand(3f, 6f);
    }

    public void updateRocks(float dt, float u, float camX, int sw) {
        if (currentRound < ROCK_MIN_ROUND) {
            return;
        }

        rockTimer -= dt;

        if (rockTimer <= 0f) {
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

    private boolean spawnBrickRock(float u, float camX, int sw) {
        float viewRight = camX + sw;
        List<RectF> candidates = new ArrayList<>();

        for (RectF platform : platforms) {
            boolean isBrick = platform.height() < 1.5f * u && platform.top < 8.5f * u;

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

    public void setFlashlightChestWanted(boolean wanted) {
        flashlightChestWanted = wanted;
    }

    private void ensureFlashlightChest(
            List<float[]> sections,
            List<RectF> bricks,
            float u
    ) {
        if (!flashlightChestWanted) {
            return;
        }

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

    private void maybeSpawnTreasure(
            List<float[]> sections,
            List<RectF> bricks,
            float u,
            int round
    ) {
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

                    nextTreasureLevel = round + TREASURE_GAP_MIN
                            + random.nextInt(TREASURE_GAP_MAX - TREASURE_GAP_MIN + 1);
                }

                return;
            }
        }
    }

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

                if (brickHasEnemy(brick, u)) {
                    continue;
                }

                float left = rand(
                        brick.left + 0.2f * u,
                        brick.right - 1.4f * u
                );

                rect = new RectF(left, brick.top - u, left + 1.2f * u, brick.top);

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