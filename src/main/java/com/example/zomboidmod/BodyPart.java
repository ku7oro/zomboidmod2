package com.example.zomboidmod;

public enum BodyPart {
    HEAD("Голова"),
    TORSO("Торс"),
    LEFT_ARM("Левая рука"),
    RIGHT_ARM("Правая рука"),
    LEFT_LEG("Левая нога"),
    RIGHT_LEG("Правая нога");

    public final String title;

    BodyPart(String title) {
        this.title = title;
    }

    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    public boolean isLeg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }
}
