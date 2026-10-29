package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;

public class PlayerHealth {
    public final PartState[] parts = new PartState[BodyPart.values().length];
    public boolean zombieInfected = false; // вирус от укуса зомби (смертелен)
    public int zombieTimer = 0;            // секунд с момента заражения
    public int painLeft = 0;               // секунд действия обезболивающего
    public double bleedAcc = 0;            // накопленная кровопотеря (не сохраняется)

    public PlayerHealth() {
        for (int i = 0; i < parts.length; i++) parts[i] = new PartState();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("zi", zombieInfected);
        n.putInt("zt", zombieTimer);
        n.putInt("pain", painLeft);
        NbtCompound pp = new NbtCompound();
        for (int i = 0; i < parts.length; i++) pp.put("p" + i, parts[i].toNbt());
        n.put("parts", pp);
        return n;
    }

    public static PlayerHealth fromNbt(NbtCompound n) {
        PlayerHealth h = new PlayerHealth();
        h.zombieInfected = n.getBoolean("zi");
        h.zombieTimer = n.getInt("zt");
        h.painLeft = n.getInt("pain");
        NbtCompound pp = n.getCompound("parts");
        for (int i = 0; i < h.parts.length; i++) h.parts[i] = PartState.fromNbt(pp.getCompound("p" + i));
        return h;
    }
}
