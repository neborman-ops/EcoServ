package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiManager;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * «Память» (2.0, игра в окне): пульт показывает последовательность цветов — повтори её. Каждый раунд на один
 * цвет длиннее. Ошибся — выбыл. Побеждает тот, кто дольше всех не ошибался.
 */
public class MemoryGame extends Game {

    public static final class Kind extends GameKind {
        public Kind() {
            super("memory", "&eПамять", "Запомни последовательность цветов и повтори. Каждый раунд длиннее.",
                    "minecraft:redstone_lamp", false);
            param("buttons", "Цветов на пульте", "", 4, 3, 9);
            param("speed", "Показ одного цвета (тиков)", "Меньше — быстрее", 16, 6, 40);
            param("rounds", "Раундов до победы", "", 15, 5, 40);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new MemoryGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 12;
            return p;
        }
    }

    private final Random rnd = new Random();
    private final int buttons;
    private final int speed;
    private final int rounds;
    private final List<Integer> sequence = new ArrayList<Integer>();
    /** Сколько игрок уже правильно повторил в этом раунде. */
    private final Map<UUID, Integer> entered = new HashMap<UUID, Integer>();
    private final Map<UUID, Boolean> doneRound = new HashMap<UUID, Boolean>();
    private int showStart;
    private int startPlayers;
    private int lastLit = -2;

    public MemoryGame(GameManager.Session session) {
        super(session);
        buttons = param("buttons", 4);
        speed = param("speed", 16);
        rounds = param("rounds", 15);
    }

    @Override
    public boolean prepare() {
        return true;
    }

    @Override
    public void begin() {
        startPlayers = results.size();
        nextRound();
        for (EntityPlayerMP p : online()) {
            new Panel(p).open();
        }
    }

    private void nextRound() {
        sequence.add(rnd.nextInt(buttons));
        entered.clear();
        doneRound.clear();
        showStart = ticks + 20;
    }

    private boolean showing() {
        return ticks < showStart + sequence.size() * speed + 10;
    }

    /** Какой цвет сейчас подсвечен при показе (-1 — пауза). */
    private int lit() {
        int t = ticks - showStart;
        if (t < 0) {
            return -1;
        }
        int i = t / speed;
        if (i >= sequence.size() || t % speed > speed * 3 / 4) {
            return -1;
        }
        return sequence.get(i);
    }

    @Override
    public void tick() {
        ticks++;
        int l = lit();
        if (l != lastLit) {
            // подсветка сменилась — обновить пульты сразу, а не раз в секунду
            lastLit = l;
            UiManager.refreshAll(Panel.class);
        }
        if (l >= 0 && (ticks - showStart) % speed == 0) {
            for (EntityPlayerMP p : online()) {
                ModSounds.play(p, ModSounds.UI_CLICK, 0.8F, 0.6F + l * 0.15F);
            }
        }
        // все живые прошли раунд — следующий
        List<UUID> alive = new ArrayList<UUID>();
        for (Game.Result r : results.values()) {
            if (r.place == 0 && !r.out && PlayerUtil.online(r.id) != null) {
                alive.add(r.id);
            }
        }
        if (alive.isEmpty()) {
            end();
            return;
        }
        if (startPlayers > 1 && alive.size() == 1 && sequence.size() > 1) {
            finish(alive.get(0), "раунд " + sequence.size());
            return;
        }
        boolean all = !showing();
        for (UUID id : alive) {
            if (!Boolean.TRUE.equals(doneRound.get(id))) {
                all = false;
            }
        }
        if (all) {
            if (sequence.size() >= rounds) {
                for (UUID id : alive) {
                    finish(id, "все " + rounds + " раундов");
                }
                end();
                return;
            }
            for (UUID id : alive) {
                EntityPlayerMP p = PlayerUtil.online(id);
                if (p != null) {
                    ModSounds.play(p, ModSounds.GAME_CHECKPOINT, 0.8F, 1.0F);
                }
            }
            nextRound();
        }
        // окно могли закрыть — открыть снова
        if (ticks % 40 == 0) {
            for (EntityPlayerMP p : online()) {
                Game.Result r = results.get(p.getUniqueID());
                if (r != null && r.place == 0 && !r.out && !UiManager.isOpen(p, Panel.class)) {
                    new Panel(p).open();
                }
            }
        }
    }

    private void press(EntityPlayerMP p, int b) {
        if (showing() || Boolean.TRUE.equals(doneRound.get(p.getUniqueID()))) {
            return;
        }
        int i = entered.containsKey(p.getUniqueID()) ? entered.get(p.getUniqueID()) : 0;
        if (sequence.get(i) != b) {
            Game.Result r = results.get(p.getUniqueID());
            if (r != null) {
                r.score = sequence.size() - 1;
            }
            out(p.getUniqueID(), "ошибся в раунде " + sequence.size());
            return;
        }
        ModSounds.play(p, ModSounds.UI_CLICK, 1.0F, 0.6F + b * 0.15F);
        i++;
        entered.put(p.getUniqueID(), i);
        if (i >= sequence.size()) {
            doneRound.put(p.getUniqueID(), true);
            Game.Result r = results.get(p.getUniqueID());
            if (r != null) {
                r.score = sequence.size();
            }
        }
    }

    @Override
    public void cleanup() {
        for (EntityPlayerMP p : online()) {
            if (UiManager.isOpen(p, Panel.class)) {
                UiManager.close(p);
            }
        }
    }

    @Override
    public String status() {
        return "раунд " + sequence.size() + " · играют " + active().size();
    }

    @Override
    public UiScreen screen(EntityPlayerMP player) {
        return new Panel(player);
    }

    final class Panel extends UiScreen {

        Panel(EntityPlayerMP player) {
            super(player);
        }

        @Override
        protected int refreshSeconds() {
            return 1;
        }

        @Override
        protected boolean canView() {
            return !over && results.containsKey(player.getUniqueID());
        }

        @Override
        protected void build(Ui ui) {
            Game.Result me = results.get(player.getUniqueID());
            boolean show = showing();
            ui.title("Память").subtitle("раунд " + sequence.size() + " из " + rounds).icon(new ItemStack(Blocks.REDSTONE_LAMP)).compact();
            ui.sideButton("Выйти из игры", new ItemStack(Blocks.BARRIER), Ui.RED, () -> {
                close();
                GameManager.leave(player);
            }).confirm("Выйти из игры?");
            if (me == null || me.out) {
                ui.note(Ui.RED, "Ты выбыл" + (me == null ? "" : ": " + me.note) + ". Смотри, как играют остальные.");
            } else if (show) {
                ui.note(Ui.YELLOW, "Запоминай! Пульт показывает " + sequence.size() + " цвет(а).");
            } else if (Boolean.TRUE.equals(doneRound.get(player.getUniqueID()))) {
                ui.note(Ui.GREEN, "Верно! Ждём остальных…");
            } else {
                int i = entered.containsKey(player.getUniqueID()) ? entered.get(player.getUniqueID()) : 0;
                ui.note(Ui.BLUE, "Повтори: нажато " + i + " из " + sequence.size());
            }
            int lit = show ? lit() : -1;
            ui.header("Пульт");
            Ui.Grid g = ui.grid().columns(Math.min(buttons, 5));
            for (int b = 0; b < buttons; b++) {
                final int idx = b;
                boolean on = lit == b;
                ItemStack icon = on ? GameColors.concreteStack(b) : new ItemStack(Blocks.WOOL, 1, GameColors.META[b]);
                if (on) {
                    icon.setStackDisplayName("§l" + com.ecoserv.util.ChatUtil.color(GameColors.title(b)));
                }
                g.cell(icon, on, e -> press(player, idx), GameColors.title(b));
            }
            ui.header("Игроки");
            for (Game.Result r : results.values()) {
                ui.row(new ItemStack(Items.SKULL, 1, 3), (r.out ? "&8" : r.place > 0 ? "&a" : "&f") + r.name)
                        .line(r.out ? "&c" + r.note : r.place > 0 ? "&a" + r.place + " место" : "&7раунд " + (int) r.score);
            }
        }
    }
}
