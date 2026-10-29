package com.example.zomboidmod;

/** 17 частей тела, как в Project Zomboid. Порядок важен: по нему рисуется панель здоровья. */
public enum BodyPart {
    HEAD("Голова", 4, BodyGroup.HEAD),
    NECK("Шея", 3, BodyGroup.HEAD),
    UPPER_TORSO("Верхняя часть туловища", 14, BodyGroup.TORSO),
    LOWER_TORSO("Нижняя часть туловища", 10, BodyGroup.TORSO),
    GROIN("Пах", 4, BodyGroup.TORSO),
    L_UPPER_ARM("Левое плечо", 5, BodyGroup.ARM),
    R_UPPER_ARM("Правое плечо", 5, BodyGroup.ARM),
    L_FOREARM("Левое предплечье", 5, BodyGroup.ARM),
    R_FOREARM("Правое предплечье", 5, BodyGroup.ARM),
    L_HAND("Левая кисть", 5, BodyGroup.ARM),
    R_HAND("Правая кисть", 5, BodyGroup.ARM),
    L_THIGH("Левое бедро", 5, BodyGroup.LEG),
    R_THIGH("Правое бедро", 5, BodyGroup.LEG),
    L_SHIN("Левая голень", 4, BodyGroup.LEG),
    R_SHIN("Правая голень", 4, BodyGroup.LEG),
    L_FOOT("Левая ступня", 3, BodyGroup.LEG),
    R_FOOT("Правая ступня", 3, BodyGroup.LEG);

    public final String title;
    public final int weight;      // шанс попасть именно сюда
    public final BodyGroup group;

    BodyPart(String title, int weight, BodyGroup group) {
        this.title = title;
        this.weight = weight;
        this.group = group;
    }

    public boolean isArm() {
        return group == BodyGroup.ARM;
    }

    public boolean isLeg() {
        return group == BodyGroup.LEG;
    }

    public boolean canFracture() {
        return group == BodyGroup.ARM || group == BodyGroup.LEG;
    }
}
