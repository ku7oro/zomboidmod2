package com.example.zomboidmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZomboidMod implements ModInitializer {
    public static final String MOD_ID = "zomboidmod";
    public static final int MAX_THIRST = 100;

    // ===== Настройки =====
    private static final int SECONDS_PER_THIRST_POINT = 12; // -1% жажды раз в 12 сек (бег - в 2 раза быстрее)
    private static final int RIVER_DRINK_TICKS = 40;        // сколько держать взгляд на воде (2 сек)
    private static final int RIVER_DRINK_AMOUNT = 10;       // +% жажды за глоток из водоёма
    private static final float RIVER_POISON_CHANCE = 0.30f; // шанс отравления от водоёма
    private static final int BOTTLE_DRINK_AMOUNT = 20;      // +% жажды от обычной бутылки с водой
    private static final float BOTTLE_POISON_CHANCE = 0.15f;// шанс отравления от обычной бутылки

    private static final Map<UUID, Integer> THIRST = new HashMap<>();
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final Map<UUID, Integer> RIVER_PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> BOTTLE_LEFT = new HashMap<>();

    public static Item CLEAN_WATER;

    public static int getThirst(ServerPlayerEntity p) {
        return THIRST.getOrDefault(p.getUuid(), MAX_THIRST);
    }

    public static void addThirst(ServerPlayerEntity p, int amount) {
        THIRST.put(p.getUuid(), Math.max(0, Math.min(MAX_THIRST, getThirst(p) + amount)));
        sendThirst(p);
    }

    private static void sendThirst(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, new ThirstPayload(getThirst(p)));
    }

    @Override
    public void onInitialize() {
        CLEAN_WATER = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "clean_water"),
                new CleanWaterItem(new Item.Settings().maxCount(16)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK)
                .register(entries -> entries.add(CLEAN_WATER));

        PayloadTypeRegistry.playS2C().register(ThirstPayload.ID, ThirstPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
                handleBottle(p);
                handleRiver(p);
            }
            if (server.getTicks() % 20 == 0) everySecond(server);
        });

        // После смерти жажда сбрасывается
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID id = newPlayer.getUuid();
            THIRST.remove(id);
            TIMER.remove(id);
            RIVER_PROGRESS.remove(id);
            BOTTLE_LEFT.remove(id);
        });
    }

    // ===== Питьё обычной бутылки с водой =====
    private static void handleBottle(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        boolean usingWater = p.isUsingItem() && isWaterBottle(p.getActiveItem());
        Integer prev = BOTTLE_LEFT.get(id);
        if (usingWater) {
            BOTTLE_LEFT.put(id, p.getItemUseTimeLeft());
        } else if (prev != null) {
            BOTTLE_LEFT.remove(id);
            if (prev <= 1) { // допили до конца, а не отпустили раньше
                addThirst(p, BOTTLE_DRINK_AMOUNT);
                maybePoison(p, BOTTLE_POISON_CHANCE);
            }
        }
    }

    private static boolean isWaterBottle(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) return false;
        PotionContentsComponent c = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        return c.potion().flatMap(e -> e.getKey())
                .map(k -> k.getValue().equals(Identifier.of("minecraft", "water")))
                .orElse(false);
    }

    // ===== Питьё из водоёма: присесть с пустой рукой и смотреть на воду =====
    private static void handleRiver(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        boolean looking = false;
        if (p.isSneaking() && p.getMainHandStack().isEmpty() && getThirst(p) < MAX_THIRST) {
            HitResult hit = p.raycast(4.0, 1.0f, true);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = ((BlockHitResult) hit).getBlockPos();
                looking = p.getWorld().getFluidState(bp).isIn(FluidTags.WATER);
            }
        }
        if (!looking) {
            RIVER_PROGRESS.remove(id);
            return;
        }
        int n = RIVER_PROGRESS.merge(id, 1, Integer::sum);
        if (n == 1) p.sendMessage(Text.literal("Пьёшь из водоёма..."), true);
        if (n >= RIVER_DRINK_TICKS) {
            RIVER_PROGRESS.put(id, 0);
            addThirst(p, RIVER_DRINK_AMOUNT);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);
            maybePoison(p, RIVER_POISON_CHANCE);
        }
    }

    private static void maybePoison(ServerPlayerEntity p, float chance) {
        if (p.getRandom().nextFloat() < chance) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 400, 0));
            p.sendMessage(Text.literal("Вода оказалась грязной, вы отравились!"), false);
        }
    }

    // ===== Раз в секунду: убывание жажды и дебафы =====
    private static void everySecond(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;

            int t = TIMER.merge(p.getUuid(), p.isSprinting() ? 2 : 1, Integer::sum);
            if (t >= SECONDS_PER_THIRST_POINT) {
                TIMER.put(p.getUuid(), 0);
                THIRST.put(p.getUuid(), Math.max(0, getThirst(p) - 1));
            }

            int thirst = getThirst(p);

            // Дебафы от потери жажды
            if (thirst <= 60) effect(p, StatusEffects.WEAKNESS, thirst <= 10 ? 1 : 0);
            if (thirst <= 40) effect(p, StatusEffects.MINING_FATIGUE, 0);
            if (thirst <= 25) effect(p, StatusEffects.SLOWNESS, thirst <= 10 ? 1 : 0);
            if (thirst <= 10 && server.getTicks() % 200 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false, false));
            }
            if (thirst <= 0 && server.getTicks() % 80 == 0) {
                p.damage(p.getDamageSources().starve(), 1.0f);
            }

            sendThirst(p);
        }
    }

    private static void effect(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        p.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
    }
}
