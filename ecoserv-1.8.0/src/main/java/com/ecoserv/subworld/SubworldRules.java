package com.ecoserv.subworld;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.content.Actions;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import java.util.List;
import java.util.Locale;

/**
 * Своя фишка 1 — «Правила подмира»: время и погода, PvP, сохранение инвентаря, мобы, огонь, грифинг,
 * режим игры при входе, приветствие на экране, стартовый набор при первом входе.
 */
public final class SubworldRules {

    private static final String PREV_GAMEMODE = "ecoserv_sw_prev_gm";

    private SubworldRules() {
    }

    /** Свой ли WorldInfo у мира (иначе правила попали бы в основной мир). */
    public static boolean ownInfo(WorldServer world) {
        WorldInfo info = world.getWorldInfo();
        return !(info instanceof DerivedWorldInfo);
    }

    /** Применить правила ко всем загруженным мирам подмира (после правки в меню). */
    public static void applyLoaded(Subworld sw) {
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w != null && ownInfo(w)) {
                SubworldInfo.applyRules(w.getWorldInfo(), sw.rules);
            }
        }
    }

    /** Может ли игрок бить других игроков в этом измерении (подмир или настройки обычного мира). */
    public static boolean pvpAllowed(int dim) {
        Subworld sw = SubworldRegistry.subworldOf(dim);
        if (sw != null) {
            return sw.rules.pvp;
        }
        com.ecoserv.world.WorldSettings.Settings s = com.ecoserv.world.WorldSettings.peek(dim);
        return s == null || !com.ecoserv.world.WorldSettings.OFF.equals(s.pvp);
    }

    // --- вход и выход --------------------------------------------------------------------

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayerMP.PERSISTED_NBT_TAG, 10)) {
            data.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG);
    }

    /** Игрок перешёл из from в to (любые миры: подмиры и обычные). */
    public static void onTransfer(EntityPlayerMP player, int from, int to) {
        Subworld toSw = SubworldRegistry.subworldOf(to);
        Subworld fromSw = SubworldRegistry.subworldOf(from);
        gamemode(player, to);
        if (toSw == null) {
            com.ecoserv.world.WorldSettings.Settings s = com.ecoserv.world.WorldSettings.peek(to);
            if (s != null && !s.welcome.trim().isEmpty() && EcoConfig.subworldsWelcomeTitle) {
                Actions.title(player, s.welcome, com.ecoserv.world.WorldNames.displayName(to), 10, 60, 20);
            }
            return;
        }
        SubworldDim d = SubworldRegistry.dim(to);
        boolean sameSubworld = fromSw != null && fromSw.id.equals(toSw.id);
        if (!sameSubworld) {
            welcome(player, toSw, d);
            kit(player, toSw);
        }
    }

    /** Привести режим игры в соответствие с миром, где игрок сейчас (после возрождения и входа). */
    public static void reconcile(EntityPlayerMP player) {
        gamemode(player, player.dimension);
    }

    /** Режим игры, который ставится в мире (null — не менять). */
    public static GameType wantedGamemode(int dim) {
        Subworld sw = SubworldRegistry.subworldOf(dim);
        if (sw != null) {
            return parse(sw.rules.gamemode);
        }
        com.ecoserv.world.WorldSettings.Settings s = com.ecoserv.world.WorldSettings.peek(dim);
        return s == null ? null : parse(s.gamemode);
    }

    /** Режим игры: мир с режимом — поставить (запомнив прежний), без режима — вернуть прежний. */
    private static void gamemode(EntityPlayerMP player, int dim) {
        if (Perms.isAdmin(player)) {
            return;
        }
        NBTTagCompound tag = persisted(player);
        GameType wanted = wantedGamemode(dim);
        if (wanted != null) {
            if (!tag.hasKey(PREV_GAMEMODE)) {
                tag.setInteger(PREV_GAMEMODE, player.interactionManager.getGameType().getID());
            }
            if (player.interactionManager.getGameType() != wanted) {
                player.setGameType(wanted);
            }
        } else if (tag.hasKey(PREV_GAMEMODE)) {
            GameType prev = GameType.getByID(tag.getInteger(PREV_GAMEMODE));
            tag.removeTag(PREV_GAMEMODE);
            if (prev != GameType.NOT_SET && player.interactionManager.getGameType() != prev) {
                player.setGameType(prev);
            }
        }
    }

    public static GameType parse(String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "survival":
            case "выживание":
            case "0":
                return GameType.SURVIVAL;
            case "creative":
            case "творческий":
            case "1":
                return GameType.CREATIVE;
            case "adventure":
            case "приключение":
            case "2":
                return GameType.ADVENTURE;
            case "spectator":
            case "наблюдатель":
            case "3":
                return GameType.SPECTATOR;
            default:
                return null;
        }
    }

    public static String gamemodeTitle(String mode) {
        GameType t = parse(mode);
        if (t == null) {
            return "не менять";
        }
        switch (t) {
            case CREATIVE:
                return "творческий";
            case ADVENTURE:
                return "приключение";
            case SPECTATOR:
                return "наблюдатель";
            default:
                return "выживание";
        }
    }

    private static void welcome(EntityPlayerMP player, Subworld sw, SubworldDim d) {
        if (!EcoConfig.subworldsWelcomeTitle) {
            return;
        }
        String text = sw.rules.welcome;
        if (text == null || text.trim().isEmpty()) {
            String sub = d != null && d.isInstance() && !d.saveName.isEmpty() ? "&7" + d.saveName
                    : d != null && d.isTemplate() ? "&dшаблон (правка админов)" : "&7" + sw.description;
            text = "&6" + sw.name + "|" + sub;
        }
        Actions.title(player, text, sw.name, 10, 60, 20);
    }

    /** Стартовый набор при первом входе в подмир. */
    private static void kit(EntityPlayerMP player, Subworld sw) {
        if (sw.rules.starterKit.isEmpty()) {
            return;
        }
        if (!SubworldRegistry.takeKit(player.getUniqueID().toString(), sw.id)) {
            return;
        }
        List<ItemStack> items = ItemCodec.decodeAll(sw.rules.starterKit);
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                PlayerUtil.giveOrDrop(player, stack.copy());
            }
        }
    }
}
