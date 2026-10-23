package com.ecoserv.npc;

import com.ecoserv.content.Actions;
import com.ecoserv.dev.DevTools;
import com.ecoserv.dev.StepCatalog;
import com.ecoserv.dev.StepCatalog.Def;
import com.ecoserv.dev.StepCatalog.Kind;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.v2.Features;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/** Подключение NPC, голограмм и спутников: события, шаги сценариев, раздел в /dev. */
public final class NpcFeature {

    private NpcFeature() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new NpcEvents());
        Features.LEAVE.add(NpcManager::removeFollowerEntity);
        Features.DIM.add(NpcManager::removeFollowerEntity);

        StepCatalog.source("npc", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (Npc n : NpcManager.all()) {
                out.add(new String[]{n.id, ChatUtil.strip(ChatUtil.color(n.name)), n.id, MobTypes.icon(n.type)});
            }
            return out;
        });
        StepCatalog.source("hologram", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (Hologram h : NpcManager.holograms()) {
                out.add(new String[]{h.id, h.id, h.lines.isEmpty() ? "" : ChatUtil.strip(ChatUtil.color(h.lines.get(0))), "minecraft:name_tag"});
            }
            return out;
        });
        StepCatalog.source("mob", () -> {
            List<String[]> out = new ArrayList<String[]>();
            for (String[] t : MobTypes.ALL) {
                out.add(new String[]{t[0], t[1], "", t[2]});
            }
            return out;
        });

        String c = "NPC и мир";
        StepCatalog.add(new Def("npc_say", "NPC говорит", "Сообщение в чат от имени NPC", "minecraft:emerald", c)
                .pick("npc", "NPC", "npc", "", "")
                .p("text", "Текст", Kind.TEXT, "%player% — ник", "Привет, %player%!"));
        StepCatalog.add(new Def("dialog", "Открыть диалог", "Окно разговора с NPC (с нужной реплики)", "minecraft:writable_book", c)
                .pick("npc", "NPC", "npc", "", "")
                .opt("node", "Реплика", Kind.WORD, "пусто — с начала", ""));
        StepCatalog.add(new Def("npc_move", "Переместить NPC", "Поставить NPC в точку (этого мира)", "minecraft:lead", c)
                .pick("npc", "NPC", "npc", "", "")
                .p("at", "Куда", Kind.COORDS, "", "~ ~ ~"));
        StepCatalog.add(new Def("holo_line", "Строка голограммы", "Поменять текст строки", "minecraft:name_tag", c)
                .pick("holo", "Голограмма", "hologram", "", "")
                .p("line", "Номер строки", Kind.INT, "с 1", "1")
                .p("text", "Текст", Kind.TEXT, "", "&aОткрыто!"));
        StepCatalog.add(new Def("follower", "Дать спутника", "Существо следует за игроком", "minecraft:lead", c)
                .pick("type", "Кто", "mob", "", "minecraft:parrot")
                .opt("name", "Имя", Kind.TEXT, "пусто — без имени", ""));
        StepCatalog.add(new Def("follower_off", "Убрать спутника", "", "minecraft:barrier", c));

        ScriptSteps.register("npc_say", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+", 2);
            Npc n = NpcManager.get(x[0]);
            if (n != null && x.length > 1) {
                ChatUtil.sendRaw(p, ChatUtil.color(n.name) + "§7: §f" + ChatUtil.color(Actions.placeholders(x[1], p, n.id)));
            }
            return 0;
        }, a -> a.trim().split("\\s+", 2).length < 2 ? "npc_say <npc> <текст>" : null, "npc_say торговец Привет! — реплика NPC в чат");
        ScriptSteps.register("dialog", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            Npc n = NpcManager.get(x[0]);
            if (n != null) {
                new NpcDialogScreen(p, n, x.length > 1 ? x[1] : n.dialog.get(0).id).open();
            }
            return 0;
        }, a -> a.trim().isEmpty() ? "dialog <npc> [реплика]" : null, "dialog торговец — открыть диалог");
        ScriptSteps.register("npc_move", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+");
            Npc n = NpcManager.get(x[0]);
            if (n != null && x.length >= 4) {
                n.dim = p.dimension;
                n.x = coord(x[1], p.posX);
                n.y = coord(x[2], p.posY);
                n.z = coord(x[3], p.posZ);
                NpcManager.respawn(n);
            }
            return 0;
        }, a -> a.trim().split("\\s+").length < 4 ? "npc_move <npc> x y z" : null, "npc_move торговец 10 64 10");
        ScriptSteps.register("holo_line", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+", 3);
            Hologram h = NpcManager.hologram(x[0]);
            if (h != null && x.length == 3) {
                int i;
                try {
                    i = Integer.parseInt(x[1]) - 1;
                } catch (NumberFormatException e) {
                    return 0;
                }
                while (h.lines.size() <= i && h.lines.size() < 20) {
                    h.lines.add("");
                }
                if (i >= 0 && i < h.lines.size()) {
                    h.lines.set(i, Actions.placeholders(x[2], p, h.id));
                    NpcManager.respawnHologram(h);
                }
            }
            return 0;
        }, a -> a.trim().split("\\s+", 3).length < 3 ? "holo_line <голограмма> <строка> <текст>" : null,
                "holo_line табло 2 &aОткрыто");
        ScriptSteps.register("follower", (p, a, ctx) -> {
            String[] x = a.trim().split("\\s+", 2);
            NpcManager.Follower f = new NpcManager.Follower();
            f.type = x[0].contains(":") ? x[0] : "minecraft:" + x[0];
            f.name = x.length > 1 ? x[1] : "";
            f.hover = f.type.contains("parrot") || f.type.contains("bat") || f.type.contains("vex") || f.type.contains("blaze") ? 1.6D : 0.0D;
            NpcManager.setFollower(p, f);
            return 0;
        }, a -> a.trim().isEmpty() ? "follower <моб> [имя]" : null, "follower minecraft:parrot &aКеша");
        ScriptSteps.register("follower_off", (p, a, ctx) -> {
            NpcManager.setFollower(p, null);
            return 0;
        }, a -> null, "follower_off — убрать спутника");

        DevTools.add((ui, p, from) -> {
            ui.header("Персонажи");
            ui.row(new ItemStack(Items.EMERALD), "&fNPC").line("&7Жители и мобы с диалогами, снаряжением и действиями")
                    .onClick(() -> new NpcScreen(p, null).open());
            ui.row(new ItemStack(Items.NAME_TAG), "&fГолограммы").line("&7Текст в воздухе: правила, табло, подписи")
                    .onClick(() -> NpcScreen.holograms(p).open());
            ui.row(new ItemStack(Items.LEAD), "&fСпутники").line("&7Шаг сценария «Дать спутника» — питомец следует за игроком")
                    .button("Себе попугая", Ui.GREEN, () -> {
                        NpcManager.Follower f = new NpcManager.Follower();
                        f.type = "minecraft:parrot";
                        f.hover = 1.6D;
                        NpcManager.setFollower(p, NpcManager.followerOf(p) == null ? f : null);
                    });
        });
    }

    private static double coord(String s, double base) {
        try {
            if (s.startsWith("~")) {
                return base + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0.0D);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return base;
        }
    }
}
