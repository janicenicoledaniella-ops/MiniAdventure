package com.example.miniadventure;

import android.graphics.RectF;

public class Treasure {
    public static final int HEART = 0;
    public static final int SPEED = 1;
    public static final int COIN = 2;
    public static final int FLASHLIGHT = 3;

    public static final int PLACE_GROUND = 0;
    public static final int PLACE_BRICK = 1;
    public static final int PLACE_AIR = 2;
    public final RectF rect;
    public final int place;

    public boolean opened;
    public int type = -1;
    public int reward = -1;
    public long openedAtNs;

    public Treasure(RectF rect, int place) {
        this.rect = rect;
        this.place = place;
    }
}