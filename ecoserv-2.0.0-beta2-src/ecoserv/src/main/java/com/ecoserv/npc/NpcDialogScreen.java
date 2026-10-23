package com.ecoserv.npc;

import com.ecoserv.content.Actions;
import com.ecoserv.dev.DevEvents;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Диалог с NPC: реплика и варианты ответа кнопками. */
public class NpcDialogScreen extends UiScreen {

    private static final Map<UUID, Long> LAST = new HashMap<UUID, Long>();

    private final String npc;
    private String node;

    public NpcDialogScreen(EntityPlayerMP player, Npc n, String node) {
        super(player);
        this.npc = n.id;
        this.node = node;
    }

    /** Игрок нажал на NPC. */
    public static void click(EntityPlayerMP p, Npc n) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUniqueID());
        if (last != null && now - last < Math.max(250L, n.cooldown * 1000L)) {
            return;
        }
        LAST.put(p.getUniqueID(), now);
        if (p.isSneaking() && Perms.isAdmin(p)) {
            new NpcScreen(p, n.id).open();
            return;
        }
        com.ecoserv.quest.QuestManager.event(p, "talk", n.id, 1);
        DevEvents.fire("npc_click", p, n.id);
        if ("steps".equals(n.onClick) || "both".equals(n.onClick)) {
            if (!n.clickSteps.isEmpty()) {
                ScriptRunner.startSteps(p, "npc:" + n.id, n.clickSteps, n.id);
            }
        }
        if (!"steps".equals(n.onClick) && !n.dialog.isEmpty()) {
            new NpcDialogScreen(p, n, n.dialog.get(0).id).open();
        }
    }

    @Override
    protected void build(Ui ui) {
        final Npc n = NpcManager.get(npc);
        if (n == null) {
            ui.title("NPC").note(Ui.GREY, "Этого NPC больше нет.");
            return;
        }
        Npc.Node d = n.node(node);
        ui.title(n.name).icon(UiUtil.icon(MobTypes.icon(n.type)));
        if (Perms.isAdmin(player)) {
            ui.sideButton("Настроить", com.ecoserv.ui.UiIcons.gear(), Ui.GREY, () -> new NpcScreen(player, n.id).open());
        }
        if (d == null) {
            ui.text("&8…");
            return;
        }
        for (String line : Actions.placeholders(d.text, player, n.id).split("\\\\n|\n")) {
            ui.text("&f" + line);
        }
        ui.header("");
        boolean any = false;
        for (final Npc.Option o : d.options) {
            if (!o.condition.trim().isEmpty() && !ScriptSteps.condition(player, o.condition, n.id)) {
                continue;
            }
            any = true;
            ui.row(new ItemStack(o.next.isEmpty() ? Items.OAK_DOOR : Items.PAPER), "&e› &f" + Actions.placeholders(o.label, player, n.id))
                    .onClick(() -> choose(n, o));
        }
        if (!any) {
            ui.buttons().add("Уйти", Ui.GREY, this::close);
        }
    }

    private void choose(Npc n, Npc.Option o) {
        if (!o.steps.isEmpty()) {
            ScriptRunner.startSteps(player, "npc:" + n.id, o.steps, n.id);
        }
        if (o.next.isEmpty() || n.node(o.next) == null) {
            close();
        } else {
            node = o.next;
        }
    }
}
