package com.example.zomboidmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZomboidMod implements ModInitializer {
    public static final String MOD_ID = "zomboidmod";
    public static final int MAX_THIRST = 100;

    // ===== Настройки жажды =====
    private static final int SECONDS_PER_THIRST_POINT = 12;
    private static final int RIVER_DRINK_TICKS = 40;
    private static final int RIVER_DRINK_AMOUNT = 10;
    private static final float RIVER_POISON_CHANCE = 0.30f;
    private static final int BOTTLE_DRINK_AMOUNT = 20;
    private static final float BOTTLE_POISON_CHANCE = 0.15f;

    // Временные данные (не сохраняются)
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final Map<UUID, Integer> RIVER_PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> RIVER_LAST = new HashMap<>();
    private static final Map<UUID, Integer> BOTTLE_LEFT = new HashMap<>();

    public static Item CLEAN_WATER;
    public static Item BANDAGE;
    public static Item ANTISEPTIC;
    public static Item SUTURE_KIT;
    public static Item SPLINT;
    public static Item PAINKILLERS;
    public static Item ANTIBIOTICS;

    public static Item itemFor(MedItem.Kind kind) {
        return switch (kind) {
            case BANDAGE -> BANDAGE;
            case ANTISEPTIC -> ANTISEPTIC;
            case SUTURE -> SUTURE_KIT;
            case SPLINT -> SPLINT;
            case PAINKILLERS -> PAINKILLERS;
            case ANTIBIOTICS -> ANTIBIOTICS;
        };
    }

    public static int getThirst(ServerPlayerEntity p) {
        return ThirstState.get(p.getServer()).thirst.getOrDefault(p.getUuid(), MAX_THIRST);
    }

    private static void setThirst(ServerPlayerEntity p, int value) {
        ThirstState state = ThirstState.get(p.getServer());
        state.thirst.put(p.getUuid(), Math.max(0, Math.min(MAX_THIRST, value)));
        state.markDirty();
    }

    public static void addThirst(ServerPlayerEntity p, int amount) {
        setThirst(p, getThirst(p) + amount);
        sendThirst(p);
    }

    private static void sendThirst(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, new ThirstPayload(getThirst(p)));
    }

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), item);
    }

    @Override
    public void onInitialize() {
        CLEAN_WATER = register("clean_water", new CleanWaterItem(new Item.Settings().maxCount(16)));
        BANDAGE = register("bandage", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.BANDAGE));
        ANTISEPTIC = register("antiseptic", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.ANTISEPTIC));
        SUTURE_KIT = register("suture_kit", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.SUTURE));
        SPLINT = register("splint", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.SPLINT));
        PAINKILLERS = register("painkillers", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.PAINKILLERS));
        ANTIBIOTICS = register("antibiotics", new MedItem(new Item.Settings().maxCount(16), MedItem.Kind.ANTIBIOTICS));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK)
                .register(entries -> entries.add(CLEAN_WATER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(BANDAGE);
            entries.add(ANTISEPTIC);
            entries.add(SUTURE_KIT);
            entries.add(SPLINT);
            entries.add(PAINKILLERS);
            entries.add(ANTIBIOTICS);
        });

        PayloadTypeRegistry.playS2C().register(ThirstPayload.ID, ThirstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WoundsPayload.ID, WoundsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DrinkPayload.ID, DrinkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TreatPayload.ID, TreatPayload.CODEC);

        // Здоровье больше не восстанавливается само от сытости: только как в Project Zomboid
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server));

        // Клиент просит попить из водоёма (Shift + ПКМ)
        ServerPlayNetworking.registerGlobalReceiver(DrinkPayload.ID,
                (payload, context) -> requestRiverDrink(context.player(), context.server().getTicks()));

        // Лечение из панели здоровья
        ServerPlayNetworking.registerGlobalReceiver(TreatPayload.ID,
                (payload, context) -> Wounds.treatFromMenu(context.player(), payload.part(), payload.action()));

        // Раны от урона
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && !p.isCreative() && !p.isSpectator()) {
                Wounds.onDamage(p, source, amount);
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
                handleBottle(p);
            }
            if (server.getTicks() % 20 == 0) everySecond(server);
        });

        // После смерти жажда и раны сбрасываются
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID id = newPlayer.getUuid();
            ThirstState state = ThirstState.get(newPlayer.getServer());
            state.thirst.remove(id);
            state.markDirty();
            TIMER.remove(id);
            RIVER_PROGRESS.remove(id);
            RIVER_LAST.remove(id);
            BOTTLE_LEFT.remove(id);
            Wounds.reset(newPlayer);
        });

        // Команды
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("wounds").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                String text = Wounds.describe(p);
                ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                return 1;
            }));
            dispatcher.register(CommandManager.literal("zomboid")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("test").executes(ctx -> {
                        Wounds.testWounds(ctx.getSource().getPlayerOrThrow());
                        ctx.getSource().sendFeedback(() -> Text.literal("Тестовые раны добавлены"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("heal").executes(ctx -> {
                        Wounds.reset(ctx.getSource().getPlayerOrThrow());
                        ctx.getSource().sendFeedback(() -> Text.literal("Все раны и болезни сброшены"), false);
                        return 1;
                    })));
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
            if (prev <= 1) {
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

    // ===== Питьё из водоёма: Shift + зажатая ПКМ с пустой рукой =====
    private static void requestRiverDrink(ServerPlayerEntity p, int tick) {
        UUID id = p.getUuid();
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || !p.isSneaking()
                || !p.getMainHandStack().isEmpty() || getThirst(p) >= MAX_THIRST || !lookingAtWater(p)) {
            RIVER_PROGRESS.remove(id);
            return;
        }
        Integer last = RIVER_LAST.get(id);
        if (last == null || tick - last > 3) RIVER_PROGRESS.put(id, 0);
        RIVER_LAST.put(id, tick);

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

    private static boolean lookingAtWater(ServerPlayerEntity p) {
        HitResult hit = p.raycast(4.5, 1.0f, true);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos bp = ((BlockHitResult) hit).getBlockPos();
        return p.getWorld().getFluidState(bp).isIn(FluidTags.WATER);
    }

    private static void maybePoison(ServerPlayerEntity p, float chance) {
        if (p.getRandom().nextFloat() < chance) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 400, 0));
            p.sendMessage(Text.literal("Вода оказалась грязной, вы отравились!"), false);
        }
    }

    // ===== Раз в секунду: жажда, её дебафы и здоровье =====
    private static void everySecond(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;

            int t = TIMER.merge(p.getUuid(), p.isSprinting() ? 2 : 1, Integer::sum);
            if (t >= SECONDS_PER_THIRST_POINT) {
                TIMER.put(p.getUuid(), 0);
                setThirst(p, getThirst(p) - 1);
            }

            int thirst = getThirst(p);

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
            Wounds.tickSecond(p, server.getTicks());
        }
        HealthState.get(server).markDirty();
    }

    private static void effect(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        p.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
    }
}
