package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldQuests;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Свои квесты подмира (п. 3): какие главы FTB Quests принадлежат подмиру. В его мирах книга квестов
 * показывает только эти главы, в остальных мирах они скрыты.
 */
public class ChapterMenu extends SubMenu {

    private final String id;
    private int page;

    public ChapterMenu(EntityPlayerMP player, String id, int page) {
        super(player, "Главы: " + id, 6);
        this.id = id;
        this.page = page;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new ChapterMenu(who, id, page);
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        Subworld sw = SubworldRegistry.get(id);
        setButton(4, Icons.of(Items.ENCHANTED_BOOK, "&6Главы квестов подмира",
                "&7Отмеченные главы видны только в этом подмире,", "&7в остальных мирах — скрыты.",
                "&7В режиме редактирования FTB Quests видно всё.",
                "&7Новые главы создавай в книге как обычно."));
        List<String[]> chapters = SubworldQuests.chapters();
        page = Math.min(page, pages(chapters.size(), PER_PAGE) - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < chapters.size(); i++) {
            String[] ch = chapters.get(page * PER_PAGE + i);
            boolean mine = sw != null && sw.questChapters.contains(ch[0]);
            List<String> others = new ArrayList<String>();
            for (Subworld o : SubworldRegistry.all()) {
                if (o != sw && o.questChapters.contains(ch[0])) {
                    others.add(o.name);
                }
            }
            ItemStack icon = Icons.of(mine ? Items.ENCHANTED_BOOK : Items.BOOK, (mine ? "&a✔ " : "&f") + ch[1],
                    "&7id: &f" + ch[0],
                    others.isEmpty() ? "" : "&7Ещё в: &f" + String.join(", ", others),
                    "&8ЛКМ — " + (mine ? "убрать из подмира" : "отдать подмиру"));
            setButton(9 + i, mine ? Icons.glint(icon) : icon);
        }
        if (chapters.isEmpty()) {
            setButton(22, Icons.of(Items.BOOK, "&7В FTB Quests пока нет глав"));
        }
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(chapters.size(), PER_PAGE) - 1) {
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
        Subworld sw = SubworldRegistry.get(id);
        List<String[]> chapters = SubworldQuests.chapters();
        int index = page * PER_PAGE + slot - 9;
        if (sw == null || slot < 9 || slot >= 45 || index < 0 || index >= chapters.size()) {
            return;
        }
        String chapter = chapters.get(index)[0];
        if (!sw.questChapters.remove(chapter)) {
            sw.questChapters.add(chapter);
        }
        SubworldRegistry.save();
        SubworldDims.sync(sw);
    }
}
