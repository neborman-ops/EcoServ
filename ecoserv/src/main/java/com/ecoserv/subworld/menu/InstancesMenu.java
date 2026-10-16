package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldBorder;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;

/** Админ: все локальные миры подмира — телепорт, барьер, удаление. */
public class InstancesMenu extends SubMenu {

    private final String id;
    private int page;
    private int armedDim = Integer.MIN_VALUE;
    private long armedUntil;

    public InstancesMenu(EntityPlayerMP player, String id, int page) {
        super(player, "Миры команд: " + id, 6);
        this.id = id;
        this.page = page;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new InstancesMenu(who, id, page);
    }

    private List<SubworldDim> list() {
        List<SubworldDim> out = new ArrayList<SubworldDim>();
        for (SubworldDim d : SubworldRegistry.dimsOf(id)) {
            if (d.isInstance()) {
                out.add(d);
            }
        }
        out.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        return out;
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        Subworld sw = SubworldRegistry.get(id);
        List<SubworldDim> list = list();
        setButton(4, Icons.of(Items.MAP, "&6Локальные миры: " + (sw == null ? id : sw.name) + " &7(" + list.size() + ")",
                "&8ЛКМ — телепорт, ПКМ — расширить барьер на шаг,", "&8Shift+ЛКМ дважды — удалить мир"));
        page = Math.min(page, pages(list.size(), PER_PAGE) - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < list.size(); i++) {
            SubworldDim d = list.get(page * PER_PAGE + i);
            WorldServer w = DimensionManager.getWorld(d.dim);
            boolean armed = armedDim == d.dim && armed(armedUntil);
            ItemStack icon = Icons.of(w != null ? Items.FILLED_MAP : Items.MAP,
                    (armed ? "&c&lЕщё раз Shift+ЛКМ — удалить " : "&e") + SubworldTeams.ownerName(d.owner) + " &7— &f" + d.saveName,
                    "&7Измерение: &f" + d.dim + (w != null ? " &a(загружен, игроков: " + w.playerEntities.size() + ")" : ""),
                    "&7Барьер: &f" + (d.border > 0 ? d.border : "нет"),
                    "&7Гостей: &f" + d.guests.size(),
                    "&7Создан: &f" + Fmt.timestamp(d.created),
                    "&7Был открыт: &f" + Fmt.timestamp(d.lastUsed),
                    "&7Версия шаблона: &f" + d.templateVersion);
            setButton(9 + i, icon);
        }
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(list.size(), PER_PAGE) - 1) {
            setButton(50, Icons.arrowRight("&eДальше"));
        }
        setButton(45, Icons.back());
        setButton(53, Icons.close());
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        boolean right = type == ClickType.PICKUP && dragType == 1;
        switch (slot) {
            case 45:
                new SubworldEditMenu(player, id, SubworldEditMenu.Page.MAIN).open();
                return;
            case 53:
                player.closeScreen();
                return;
            case 48:
                if (page > 0) {
                    page--;
                }
                return;
            case 50:
                page++;
                return;
            default:
        }
        List<SubworldDim> list = list();
        int index = page * PER_PAGE + slot - 9;
        if (slot < 9 || slot >= 45 || index < 0 || index >= list.size()) {
            return;
        }
        SubworldDim d = list.get(index);
        if (shift) {
            if (armedDim == d.dim && confirmed(armedUntil)) {
                SubworldManager.deleteDim(d, "&eЭтот локальный мир удалён админом.");
                ChatUtil.send(player, "&aЛокальный мир удалён.");
                armedDim = Integer.MIN_VALUE;
            } else {
                armedDim = d.dim;
                armedUntil = arm();
            }
            return;
        }
        if (right) {
            int r = SubworldBorder.expand(d, 0);
            ChatUtil.send(player, r < 0 ? "&cУ этого мира нет барьера." : "&aБарьер: &f" + r);
            return;
        }
        player.closeScreen();
        SubworldTeleport.teleportTo(player, d, false, null);
    }
}
