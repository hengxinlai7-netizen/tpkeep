package com.hengx.tpkeep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Teleport Keep: re-syncs the CLIENT after a cross-dimension teleport.
 *
 * Root cause (verified against the pack): FTB Essentials' /home uses
 * ServerPlayer.teleportTo(ServerLevel,...), which - unlike ServerPlayer.changeDimension -
 * does NOT send ClientboundPlayerAbilitiesPacket nor ClientboundUpdateMobEffectPacket.
 * The client then rebuilds LocalPlayer in ClientPacketListener.handleRespawn and
 * GameType.updatePlayerAbilities resets mayfly/flying. The SERVER state is never lost,
 * which is why checking server-side state (hasEffect / !mayfly) does nothing.
 *
 * So this handler mirrors what changeDimension does, unconditionally:
 *   1. send ClientboundUpdateMobEffectPacket for every cached effect (view only, no
 *      stacking and no duration change on the server);
 *   2. set mayfly/flying from the cache and always call onUpdateAbilities().
 * It repeats for retryTicks ticks, because the client finishes handleRespawn/setLocalMode
 * a few ticks after arrival.
 *
 * State is cached every CACHE_INTERVAL ticks, because by the time the travel event fires
 * the snapshot may already be too late.
 */
@Mod.EventBusSubscriber(modid = TeleportKeep.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KeepHandler {

    private static final int CACHE_INTERVAL = 10;

    private static final Map<UUID, Snapshot> CACHE = new HashMap<>();
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private static int counter;

    private KeepHandler() {}

    private static final class Snapshot {
        final List<MobEffectInstance> effects = new ArrayList<>();
        boolean mayfly;
        boolean flying;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.player;

        Integer left = PENDING.get(player.getUUID());
        if (left != null) {
            if (left <= 0) {
                PENDING.remove(player.getUUID());
            }
            else {
                resync(player);
                PENDING.put(player.getUUID(), left - 1);
            }
        }

        if (counter % CACHE_INTERVAL == 0) {
            cache(player);
        }
        if (counter >= Integer.MAX_VALUE - 1) {
            counter = 0;
        }
        counter++;
    }

    private static void cache(ServerPlayer player) {
        Snapshot snap = new Snapshot();
        if (KeepConfig.keepEffects()) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                snap.effects.add(new MobEffectInstance(effect));
            }
        }
        snap.mayfly = player.getAbilities().mayfly;
        snap.flying = player.getAbilities().flying;
        CACHE.put(player.getUUID(), snap);
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!KeepConfig.enabled() || !(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        cache((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!KeepConfig.enabled() || !(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        resync(player);
        PENDING.put(player.getUUID(), KeepConfig.retryTicks());
    }

    /** Force the client back in sync with the (never lost) server state. */
    private static void resync(ServerPlayer player) {
        Snapshot snap = CACHE.get(player.getUUID());
        if (snap == null) {
            return;
        }
        int sent = 0;
        if (KeepConfig.keepEffects()) {
            for (MobEffectInstance effect : snap.effects) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(),
                    new MobEffectInstance(effect)));
                sent++;
            }
        }
        boolean flight = false;
        if (KeepConfig.keepFlight() && snap.mayfly) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = snap.flying;
            player.onUpdateAbilities();
            flight = true;
        }
        // 恢复本身与日志无关:关闭 debugLog 时连玩家名都不查,更不拼日志字符串。
        if (KeepConfig.debugLog()) {
            TeleportKeep.LOGGER.info("[tpkeep] resync: {} effect packet(s), flight={} (player {})",
                sent, flight, player.getGameProfile().getName());
        }
    }
}