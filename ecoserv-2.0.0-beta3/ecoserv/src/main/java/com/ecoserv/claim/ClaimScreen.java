package com.ecoserv.claim;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Приваты (1.9.0): карта чанков вокруг (ЛКМ — занять, ПКМ — освободить), доверенные, что можно всем. */
public class ClaimScreen extends UiScreen {

    private enum Tab {
        MAP, TRUST, RULES
    }

    private static final int W = 11;
    private static final int H = 9;
    private Tab tab = Tab.MAP;

    public ClaimScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        new ClaimScreen(player).open();
    }

    @Override
    protected void build(Ui ui) {
        String me = player.getUniqueID().toString();
        int mine = ClaimManager.of(me).size();
        boolean admin = Perms.isAdmin(player);
        ui.title("Приваты").subtitle(mine + (admin ? "" : " / " + ExtraConfig.claimsMaxChunks) + " чанков").icon(UiIcons.map());
        ui.tab("Карта", UiIcons.map(), tab == Tab.MAP, () -> tab = Tab.MAP);
        ui.tab("Доверенные", UiIcons.team(), tab == Tab.TRUST, () -> tab = Tab.TRUST);
        ui.tab("Что можно всем", UiIcons.key(), tab == Tab.RULES, () -> tab = Tab.RULES);
        ui.sideLabel("&8Ещё");
        ui.sideButton("Варпы", UiIcons.compass(), Ui.GREY, () -> com.ecoserv.warp.WarpScreen.open(player));
        ui.sideButton("Освободить все", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            int n = ClaimManager.unclaimAll(me);
            toast("Освобождено чанков: " + n);
        }).confirm("Освободить ВСЕ свои чанки? Половина цены вернётся.");
        if (!ExtraConfig.claimsEnabled) {
            ui.note(Ui.YELLOW, "Приваты выключены в настройках мода.");
        }
        switch (tab) {
            case TRUST:
                buildTrust(ui, me);
                break;
            case RULES:
                buildRules(ui, me);
                break;
            default:
                buildMap(ui, me, admin);
        }
    }

    private void buildMap(Ui ui, String me, boolean admin) {
        int pcx = ((int) Math.floor(player.posX)) >> 4;
        int pcz = ((int) Math.floor(player.posZ)) >> 4;
        int dim = player.dimension;
        ui.text("&7Мир &f" + WorldNames.displayName(dim) + "&7. Ты — в центре (рамка). &aЛКМ&7 — занять чанк"
                + (ExtraConfig.claimChunkPrice > 0 && !admin ? " (&6" + Fmt.money(ExtraConfig.claimChunkPrice) + "&7)" : "")
                + ", &cПКМ&7 — освободить. Север сверху.");
        Ui.Grid g = ui.grid().columns(W);
        for (int dz = -(H / 2); dz <= H / 2; dz++) {
            for (int dx = -(W / 2); dx <= W / 2; dx++) {
                final int cx = pcx + dx;
                final int cz = pcz + dz;
                ClaimManager.Claim c = ClaimManager.at(dim, cx, cz);
                int meta;
                String state;
                if (c == null) {
                    meta = 8;
                    state = "&7свободен";
                } else if (me.equals(c.owner)) {
                    meta = 5;
                    state = "&aтвой";
                } else if (ClaimManager.trusted(player, c)) {
                    meta = 11;
                    state = "&9" + c.ownerName + " &7(тебе можно)";
                } else {
                    meta = 14;
                    state = "&c" + c.ownerName;
                }
                ItemStack icon = new ItemStack(Blocks.STAINED_GLASS_PANE, 1, meta);
                icon.setStackDisplayName("§fЧанк " + cx + ", " + cz);
                g.cell(icon, dx == 0 && dz == 0, e -> {
                    String err = e.right() ? ClaimManager.unclaim(player, dim, cx, cz) : ClaimManager.claim(player, dim, cx, cz);
                    if (err != null) {
                        throw new UiError(UiUtil.plain(err));
                    }
                    toast(e.right() ? "Чанк " + cx + ", " + cz + " освобождён." : "Чанк " + cx + ", " + cz + " занят!");
                }, state, "&8блоки " + (cx << 4) + ".." + ((cx << 4) + 15)
                        + ", " + (cz << 4) + ".." + ((cz << 4) + 15));
            }
        }
        ui.row(new ItemStack(Blocks.STAINED_GLASS_PANE, 1, 5), "&aзелёный &7— твой, &9синий &7— можно тебе, "
                + "&cкрасный &7— чужой, &7серый — свободен");
    }

    private void buildTrust(Ui ui, String me) {
        ClaimManager.Owner o = ClaimManager.owner(me);
        ui.text("&7Доверенные могут строить, ломать и открывать всё на твоей территории. Твоя команда (меню миров → "
                + "«Команда») может это и так.");
        ui.field("Добавить игрока", "Ник", "", v -> {
            UUID id = PlayerUtil.uuidOf(v.trim());
            if (id == null) {
                throw new UiError("Не знаю игрока «" + v.trim() + "».");
            }
            if (!o.trusted.contains(id.toString())) {
                o.trusted.add(id.toString());
                ClaimManager.save();
            }
            toast(PlayerUtil.nameOf(id) + " теперь может строить у тебя.");
        }).picker(Ui.PICK_PLAYER).max(32);
        List<String> list = o.trusted;
        ui.header("Доверенные", Integer.toString(list.size()));
        if (list.isEmpty()) {
            ui.text("&8Пока никого.");
        }
        for (final String t : list.toArray(new String[0])) {
            String name;
            try {
                name = PlayerUtil.nameOf(UUID.fromString(t));
            } catch (IllegalArgumentException e) {
                name = t;
            }
            ui.row(new ItemStack(Items.SKULL, 1, 3), "&f" + name).button("Убрать", Ui.RED, () -> {
                o.trusted.remove(t);
                ClaimManager.save();
            });
        }
    }

    private void buildRules(Ui ui, String me) {
        final ClaimManager.Owner o = ClaimManager.owner(me);
        ui.toggle("Двери, люки, кнопки — всем", "Остальное (сундуки, блоки) по-прежнему только своим", o.publicDoors, on -> {
            o.publicDoors = on;
            ClaimManager.save();
        });
        ui.toggle("Сундуки и хранилища — всем", "Например, общий склад или магазин", o.publicContainers, on -> {
            o.publicContainers = on;
            ClaimManager.save();
        });
        ui.toggle("PvP на моей территории", "Выключи — игроки не смогут бить друг друга у тебя", o.pvp, on -> {
            o.pvp = on;
            ClaimManager.save();
        });
        ui.note(Ui.BLUE, "Взрывы " + (ExtraConfig.claimsExplosions ? "ломают" : "не ломают")
                + " блоки в приватах (настройка сервера).");
    }
}
