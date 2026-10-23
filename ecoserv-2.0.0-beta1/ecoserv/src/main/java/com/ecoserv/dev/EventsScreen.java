package com.ecoserv.dev;

import com.ecoserv.script.Script;
import com.ecoserv.script.ScriptManager;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Привязки событий (2.0): PlayerJoin(), PlayerDeath(), zone_enter:имя… → сценарии. */
public class EventsScreen extends UiScreen {

    private String event = "join";
    private String script = "";

    public EventsScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.title("События").icon(new ItemStack(Items.CLOCK));
        ui.back(() -> new DevScreen(player).open());
        ui.text("&7Событие → сценарии. Можно уточнить контекст через двоеточие: &fworld_enter:nether&7, "
                + "&fzone_enter:арена&7, &fquest_done:iron&7, &fnpc_talk:торговец&7. Свои события — шаг &fevent имя&7.");
        ui.header("Привязать");
        ui.field("Событие", "Например join или zone_enter:арена", event, v -> event = v.trim().toLowerCase(Locale.ROOT)).max(64);
        Ui.Select sel = ui.select("Сценарий", "", script, v -> script = v);
        for (Script s : ScriptManager.all()) {
            sel.option(s.name, s.name, s.description);
        }
        ui.buttons().add("Привязать", Ui.GREEN, () -> {
            if (event.isEmpty() || ScriptManager.get(script) == null) {
                throw new UiError("Выбери событие и сценарий.");
            }
            DevManager.bind(event, script);
            toast("Привязано: " + event + " → " + script);
        });
        ui.header("Встроенные события");
        for (Map.Entry<String, String> e : DevManager.BUILTIN.entrySet()) {
            final String name = e.getKey();
            List<String> b = DevManager.bound(name);
            ui.row(new ItemStack(Items.CLOCK), "&f" + name).line("&7" + e.getValue())
                    .line(b.isEmpty() ? "&8нет сценариев" : "&a→ " + String.join(", ", b))
                    .onClick(() -> event = name);
        }
        List<String> custom = new ArrayList<String>();
        for (String k : DevManager.bindings().keySet()) {
            if (!DevManager.BUILTIN.containsKey(k)) {
                custom.add(k);
            }
        }
        ui.header("Привязки", DevManager.bindings().size() + "");
        for (Map.Entry<String, ArrayList<String>> e : DevManager.bindings().entrySet()) {
            for (final String s : e.getValue()) {
                final String ev = e.getKey();
                ui.row(new ItemStack(Items.PAPER), "&f" + ev + " &7→ &e" + s)
                        .button("✕", Ui.RED, () -> DevManager.unbind(ev, s));
            }
        }
        if (custom.isEmpty() && DevManager.bindings().isEmpty()) {
            ui.text("&8Пока ничего не привязано.");
        }
    }
}
