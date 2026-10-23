package com.ecoserv.integration.quests;

import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldView;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.quest.Chapter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Свои квесты подмиров (п. 3) на клиенте: в подмире со своими квестами книга показывает только его главы,
 * в остальных мирах главы таких подмиров скрыты. Меняется только клиентская копия («всегда скрыта»),
 * в режиме редактирования FTB Quests всё возвращается как было — настройки глав не портятся.
 */
@SideOnly(Side.CLIENT)
public class QuestChapterFilter {

    /** Глава -> [исходное значение, выставленное нами]. */
    private final Map<Chapter, boolean[]> state = new WeakHashMap<Chapter, boolean[]>();

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ClientQuestFile.exists()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        boolean editing = file.canEdit();
        int dim = mc.player.dimension;
        SubworldView here = SubworldDims.clientView(dim);
        boolean changed = false;
        for (Chapter ch : file.chapters) {
            String id = ch.getCodeString();
            boolean[] st = state.get(ch);
            if (st == null) {
                st = new boolean[]{ch.alwaysInvisible, ch.alwaysInvisible};
                state.put(ch, st);
            } else if (ch.alwaysInvisible != st[1]) {
                // сервер прислал новое значение настройки
                st[0] = ch.alwaysInvisible;
            }
            boolean hide = !editing && !visible(id, here);
            boolean want = st[0] || hide;
            if (ch.alwaysInvisible != want) {
                ch.alwaysInvisible = want;
                changed = true;
            }
            st[1] = want;
        }
        if (changed) {
            file.refreshGui();
        }
    }

    /** Видна ли глава в текущем мире. */
    private static boolean visible(String chapter, SubworldView here) {
        if (here != null && here.ownQuests) {
            return here.chapters.contains(chapter);
        }
        for (SubworldView v : SubworldDims.CLIENT_VIEWS.values()) {
            if (v.ownQuests && v.chapters.contains(chapter)) {
                return false;
            }
        }
        return true;
    }
}
