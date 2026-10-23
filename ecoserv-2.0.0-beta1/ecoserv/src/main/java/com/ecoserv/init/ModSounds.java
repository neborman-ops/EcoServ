package com.ecoserv.init;

import com.ecoserv.Ref;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Свои звуки мода (1.9.0): assets/ecoserv/sounds.json и sounds/*.ogg (сгенерированы assets_src/sounds_generator.py).
 */
@Mod.EventBusSubscriber(modid = Ref.MODID)
public final class ModSounds {

    private static final List<SoundEvent> ALL = new ArrayList<SoundEvent>();

    // порталы
    public static final SoundEvent PORTAL_HUM = make("portal.hum");
    public static final SoundEvent PORTAL_WHOOSH = make("portal.whoosh");
    public static final SoundEvent PORTAL_RELEASE = make("portal.release");
    public static final SoundEvent MONOLITH_HUM = make("portal.monolith");
    public static final SoundEvent ABYSS_FALL = make("abyss.fall");
    // интерфейс
    public static final SoundEvent UI_CLICK = make("ui.click");
    public static final SoundEvent UI_OPEN = make("ui.open");
    public static final SoundEvent UI_TOAST = make("ui.toast");
    public static final SoundEvent COIN = make("ui.coin");
    // мини-игры
    public static final SoundEvent GAME_COUNTDOWN = make("game.countdown");
    public static final SoundEvent GAME_START = make("game.start");
    public static final SoundEvent GAME_WIN = make("game.win");
    public static final SoundEvent GAME_LOSE = make("game.lose");
    public static final SoundEvent GAME_CHECKPOINT = make("game.checkpoint");
    public static final SoundEvent TRAP = make("game.trap");
    public static final SoundEvent PRESSURE_ALARM = make("game.alarm");
    public static final SoundEvent STEAM_LEAK = make("game.steam");
    public static final SoundEvent VALVE = make("game.valve");
    public static final SoundEvent CHASER = make("game.chaser");
    public static final SoundEvent HEARTBEAT = make("game.heartbeat");

    private ModSounds() {
    }

    private static SoundEvent make(String name) {
        ResourceLocation id = new ResourceLocation(Ref.MODID, name);
        SoundEvent e = new SoundEvent(id);
        e.setRegistryName(id);
        ALL.add(e);
        return e;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> event) {
        for (SoundEvent e : ALL) {
            event.getRegistry().register(e);
        }
    }

    /** Звук только этому игроку (в точке, где он стоит). */
    public static void play(EntityPlayerMP player, SoundEvent sound, float volume, float pitch) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.sendPacket(new SPacketSoundEffect(sound, SoundCategory.PLAYERS,
                player.posX, player.posY, player.posZ, volume, pitch));
    }

    /** Звук всем рядом с точкой. */
    public static void playAt(net.minecraft.world.World world, double x, double y, double z, SoundEvent sound,
                              float volume, float pitch) {
        world.playSound(null, x, y, z, sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
