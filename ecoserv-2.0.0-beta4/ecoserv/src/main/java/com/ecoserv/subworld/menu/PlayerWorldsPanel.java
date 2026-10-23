package com.ecoserv.subworld.menu;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Вкладка «Мои миры» (1.9.0, player_worlds.enabled): игрок создаёт свой подмир (обычный, плоский, пустой…),
 * приглашает друзей и сам решает, открыт ли мир для всех. Вкладка видна, только когда это включено.
 */
final class PlayerWorldsPanel {

    /** Черновик нового мира у каждого игрока (пока окно открыто). */
    static final class Draft {
        String name = "";
        String type = Subworld.NORMAL;
        String seed = "";
    }

    private PlayerWorldsPanel() {
    }

    static List<Subworld> ownedBy(String uuid) {
        List<Subworld> out = new ArrayList<Subworld>();
        for (Subworld sw : SubworldRegistry.all()) {
            if (uuid.equals(sw.owner)) {
                out.add(sw);
            }
        }
        return out;
    }

    static void build(Ui ui, final WorldsScreen s, final Draft draft) {
        final EntityPlayerMP player = s.player();
        final String me = player.getUniqueID().toString();
        List<Subworld> mine = ownedBy(me);
        ui.header("Мои миры", mine.size() + " / " + ExtraConfig.playerWorldsMax);
        if (mine.isEmpty()) {
            ui.text("&8Своих миров пока нет — создай ниже.");
        }
        for (final Subworld sw : mine) {
            Ui.Row row = ui.row(UiUtil.icon(sw.icon), "&e" + sw.name)
                    .line("&8" + sw.typeTitle() + " · " + (Subworld.ACCESS_PRIVATE.equals(sw.access) ? "только приглашённые ("
                            + sw.members.size() + ")" : "открыт для всех"));
            row.button("Войти", Ui.GREEN, () -> {
                s.close();
                SubworldTeleport.enter(player, sw, false);
            });
            row.button("⚙", Ui.GREY, () -> new PlayerWorldScreen(player, sw.id).open()).tip("Настроить свой мир");
        }
        // куда пригласили
        List<Subworld> guest = new ArrayList<Subworld>();
        for (Subworld sw : SubworldRegistry.all()) {
            if (sw.playerOwned() && !me.equals(sw.owner) && sw.members.contains(me)) {
                guest.add(sw);
            }
        }
        if (!guest.isEmpty()) {
            ui.header("Меня пригласили");
            for (final Subworld sw : guest) {
                ui.row(UiUtil.icon(sw.icon), "&e" + sw.name).line("&8мир игрока " + sw.ownerName)
                        .button("Войти", Ui.GREEN, () -> {
                            s.close();
                            SubworldTeleport.enter(player, sw, false);
                        });
            }
        }
        boolean admin = Perms.isAdmin(player);
        if (mine.size() >= ExtraConfig.playerWorldsMax && !admin) {
            ui.note(Ui.YELLOW, "Больше своих миров создать нельзя (предел " + ExtraConfig.playerWorldsMax + ").");
            return;
        }
        ui.header("Создать свой мир", ExtraConfig.playerWorldPrice > 0 ? "&6" + Fmt.money(ExtraConfig.playerWorldPrice) : "бесплатно");
        ui.field("Название", "Как мир будет называться в меню", draft.name, v -> draft.name = v.trim()).placeholder("Мой остров").max(32);
        Ui.Select type = ui.select("Тип мира", "", draft.type, v -> draft.type = v);
        String[][] types = {
                {Subworld.NORMAL, "Обычный", "как основной мир"},
                {Subworld.FLAT, "Плоский", "для построек"},
                {Subworld.VOID, "Пустой", "одна площадка в пустоте"},
                {Subworld.NETHER, "Незер", ""},
                {Subworld.END, "Энд", ""}};
        for (String[] t : types) {
            if (ExtraConfig.playerWorldType(t[0])) {
                type.option(t[0], t[1], t[2], t[0].equals(Subworld.VOID) ? new ItemStack(Blocks.GLASS)
                        : t[0].equals(Subworld.FLAT) ? new ItemStack(Blocks.SANDSTONE) : t[0].equals(Subworld.NETHER)
                        ? new ItemStack(Blocks.NETHERRACK) : t[0].equals(Subworld.END) ? new ItemStack(Blocks.END_STONE)
                        : new ItemStack(Blocks.GRASS));
            }
        }
        if (Subworld.NORMAL.equals(draft.type)) {
            ui.field("Сид", "Пусто — случайный", draft.seed, v -> draft.seed = v.trim()).placeholder("случайный").clearable().max(32);
        }
        ui.buttons().add("Создать мир", Ui.GREEN, () -> {
            String err = create(player, draft);
            if (err != null) {
                throw new UiError(UiUtil.plain(err));
            }
            draft.name = "";
            draft.seed = "";
            s.toastText("Мир создан! Нажми «Войти».");
        }).confirm("Создать мир «" + (draft.name.isEmpty() ? "?" : draft.name) + "»"
                + (ExtraConfig.playerWorldPrice > 0 && !admin ? " за " + Fmt.money(ExtraConfig.playerWorldPrice) : "") + "?");
        ui.note(Ui.BLUE, "Мир видишь ты и те, кого пригласишь (⚙ → «Кто может войти»). Можно открыть его для всех.");
    }

    static String create(EntityPlayerMP player, Draft draft) {
        boolean admin = Perms.isAdmin(player);
        if (!ExtraConfig.playerWorlds && !admin) {
            return "&cСвои миры выключены на сервере.";
        }
        String me = player.getUniqueID().toString();
        if (!admin && ownedBy(me).size() >= ExtraConfig.playerWorldsMax) {
            return "&cБольше своих миров нельзя.";
        }
        String title = draft.name.trim();
        if (title.isEmpty()) {
            return "&cНазови мир.";
        }
        if (!ExtraConfig.playerWorldType(draft.type) && !admin) {
            return "&cТакой тип мира создавать нельзя.";
        }
        // id: p_<ник>_<номер>
        String base = ("p_" + player.getName()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.length() > 26) {
            base = base.substring(0, 26);
        }
        String id = base;
        int n = 2;
        while (!SubworldRegistry.idAvailable(id) && n < 100) {
            id = base + "_" + n++;
        }
        long price = admin ? 0L : ExtraConfig.playerWorldPrice;
        if (price > 0 && !EconomyManager.take(player.getUniqueID(), price)) {
            return "&cНе хватает денег: нужно &6" + Fmt.money(price);
        }
        Subworld sw = new Subworld();
        sw.id = id;
        sw.name = title;
        sw.description = "Мир игрока " + player.getName();
        sw.type = draft.type;
        sw.icon = Subworld.VOID.equals(draft.type) ? "minecraft:glass" : Subworld.FLAT.equals(draft.type) ? "minecraft:sandstone"
                : Subworld.NETHER.equals(draft.type) ? "minecraft:netherrack" : Subworld.END.equals(draft.type)
                ? "minecraft:end_stone" : "minecraft:grass";
        if (!draft.seed.isEmpty()) {
            try {
                sw.seed = Long.parseLong(draft.seed);
            } catch (NumberFormatException e) {
                sw.seed = draft.seed.hashCode();
            }
        } else {
            sw.seed = new java.util.Random().nextLong();
        }
        sw.access = Subworld.ACCESS_PRIVATE;
        sw.owner = me;
        sw.ownerName = player.getName();
        sw.guests = true;
        String err = SubworldManager.create(sw, player);
        if (err != null) {
            if (price > 0) {
                EconomyManager.give(player.getUniqueID(), price);
            }
            return err;
        }
        SubworldDim d = SubworldRegistry.find(sw.id, SubworldDim.SHARED);
        if (d != null && ExtraConfig.playerWorldBorder > 0) {
            d.border = ExtraConfig.playerWorldBorder;
            SubworldRegistry.save();
        }
        return null;
    }
}
