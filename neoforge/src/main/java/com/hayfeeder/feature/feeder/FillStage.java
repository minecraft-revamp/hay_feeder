package com.hayfeeder.feature.feeder;

import net.minecraft.util.StringRepresentable;

public enum FillStage implements StringRepresentable {
    EMPTY("empty"),
    HALF("half"),
    FULL("full");

    public static final int FULL_THRESHOLD = 64;

    private final String name;

    FillStage(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    public static FillStage fromCount(int count) {
        if (count <= 0) return EMPTY;
        if (count >= FULL_THRESHOLD) return FULL;
        return HALF;
    }
}
