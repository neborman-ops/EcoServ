package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Давление» (1.9.0): пульт котельной. N баков, в каждом газ поступает насосом; давление растёт с температурой.
 * Держи давление в зелёной зоне весь срок: крути нагрев и сброс, латай утечки, лови скачки насоса и отказы
 * охлаждения. Бак выше 16 бар — взрыв, ниже 4 — схлопнулся: игра проиграна. Играют вместе на одном пульте.
 */
public class PressureGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("pressure", "&bДавление", "Пульт котельной: держи давление во всех баках в зелёной зоне. Нагрев, "
                    + "сброс, утечки, скачки насоса — 5 минут без взрывов.", "minecraft:cauldron", true);
            param("difficulty", "Сложность", "Как часто беды и как быстро всё меняется", 2, 1, 5);
            param("tanks", "Баков", "", 3, 1, 6);
            param("minutes", "Продержаться, минут", "", 5, 1, 30);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new PressureGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 6;
            return p;
        }
    }

    // зоны давления, бар
    static final double FAIL_LOW = 4.0D;
    static final double LOW = 8.0D;
    static final double HIGH = 12.0D;
    static final double FAIL_HIGH = 16.0D;

    static final class Tank {
        final int index;
        double gas = 10.0D;
        double temp = 27.0D;
        int heater = 30;
        int valve = 20;
        /** Утечки: у каждой «размер» (сколько раз латать). */
        final List<Integer> leaks = new ArrayList<Integer>();
        /** До какого тика насос гонит втрое больше. */
        int surgeUntil;
        /** До какого тика нагрев заклинило на максимум. */
        int stuckUntil;
        /** До какого тика датчик врёт. */
        int glitchUntil;
        boolean broken;

        Tank(int index) {
            this.index = index;
        }

        double pressure() {
            return gas * (temp + 273.0D) / 300.0D;
        }
    }

    private final List<Tank> tanks = new ArrayList<Tank>();
    private final Random rnd = new Random();
    private final int difficulty;
    private final int limitTicks;
    private int greenTicks;
    private int coolCooldown;
    private final List<String> log = new ArrayList<String>();
    private BlockPos console;

    public PressureGame(GameManager.Session session) {
        super(session);
        difficulty = param("difficulty", 2);
        limitTicks = param("minutes", 5) * 60 * 20;
        int n = param("tanks", 3);
        for (int i = 0; i < n; i++) {
            Tank t = new Tank(i + 1);
            t.gas = 9.5D + rnd.nextDouble();
            tanks.add(t);
        }
    }

    // --- пультовая --------------------------------------------------------------------------------------

    @Override
    protected void build() {
        fill(-5, 0, -5, 5, 0, 5, Blocks.IRON_BLOCK.getDefaultState());
        fill(-5, 1, -5, 5, 4, -5, Blocks.STONEBRICK.getDefaultState());
        fill(-5, 1, 5, 5, 4, 5, Blocks.STONEBRICK.getDefaultState());
        fill(-5, 1, -5, -5, 4, 5, Blocks.STONEBRICK.getDefaultState());
        fill(5, 1, -5, 5, 4, 5, Blocks.STONEBRICK.getDefaultState());
        fill(-5, 5, -5, 5, 5, 5, Blocks.GLASS.getDefaultState());
        // баки вдоль дальней стены
        for (int i = 0; i < tanks.size(); i++) {
            int x = -4 + i * 8 / Math.max(1, tanks.size() - 1);
            if (tanks.size() == 1) {
                x = 0;
            }
            fill(x, 1, 3, x, 3, 4, Blocks.IRON_BLOCK.getDefaultState());
            set(x, 4, 4, Blocks.CAULDRON.getDefaultState());
            set(x, 2, 2, Blocks.REDSTONE_LAMP.getDefaultState());
        }
        // пульт в центре
        console = rel(0, 1, 0);
        set(0, 1, 0, Blocks.BEACON.getDefaultState());
        fill(-1, 0, -1, 1, 0, 1, Blocks.SEA_LANTERN.getDefaultState());
        set(0, 0, 0, Blocks.IRON_BLOCK.getDefaultState());
        bounds = new int[]{origin.getX() - 7, origin.getY() - 1, origin.getZ() - 7,
                origin.getX() + 7, origin.getY() + 7, origin.getZ() + 7};
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        return rel((index % 3) - 1, 1, -3);
    }

    @Override
    public void begin() {
        super.begin();
        for (EntityPlayerMP p : online()) {
            open(p);
        }
        note("Смена началась. Держи давление 8–12 бар.");
    }

    void open(EntityPlayerMP p) {
        new Panel(p).open();
    }

    /** ПКМ по пульту в пультовой. */
    public boolean isConsole(BlockPos pos) {
        return console != null && console.equals(pos);
    }

    @Nullable
    @Override
    public UiScreen screen(EntityPlayerMP player) {
        return new Panel(player);
    }

    // --- физика ----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        ticks++;
        if (coolCooldown > 0) {
            coolCooldown--;
        }
        boolean allGreen = true;
        boolean alarm = false;
        for (Tank t : tanks) {
            if (t.broken) {
                continue;
            }
            double pump = 0.012D * (ticks < t.surgeUntil ? 3.2D : 1.0D) * (0.9D + difficulty * 0.05D);
            t.gas += pump;
            double heat = ticks < t.stuckUntil ? 100 : t.heater;
            double target = 10.0D + heat * 0.9D;
            t.temp += (target - t.temp) * (0.004D + difficulty * 0.0012D);
            double p = t.pressure();
            t.gas -= t.valve / 100.0D * 0.035D * p / 10.0D;
            for (int size : t.leaks) {
                t.gas -= 0.006D * size * p / 10.0D;
            }
            t.gas = Math.max(0.2D, t.gas);
            p = t.pressure();
            // беды: чем выше сложность и давление, тем чаще
            double risk = 0.0006D * difficulty * (p > HIGH ? 3.0D : 1.0D);
            if (rnd.nextDouble() < risk) {
                t.leaks.add(2 + rnd.nextInt(1 + difficulty / 2));
                note("Бак " + t.index + ": новая утечка!");
                sound(ModSounds.STEAM_LEAK, 1.0F);
            }
            if (rnd.nextDouble() < 0.0003D * difficulty) {
                t.surgeUntil = ticks + 200;
                note("Бак " + t.index + ": скачок насоса — газ идёт втрое быстрее!");
            }
            if (difficulty >= 2 && rnd.nextDouble() < 0.00022D * difficulty) {
                t.stuckUntil = ticks + 240;
                note("Бак " + t.index + ": нагрев заклинило на максимум! Аварийное охлаждение или сброс.");
            }
            if (difficulty >= 3 && rnd.nextDouble() < 0.0002D * difficulty) {
                t.glitchUntil = ticks + 300;
                note("Бак " + t.index + ": датчик барахлит — нажми «Калибровка».");
            }
            if (p >= FAIL_HIGH || p <= FAIL_LOW) {
                t.broken = true;
                note("Бак " + t.index + (p >= FAIL_HIGH ? " ВЗОРВАЛСЯ" : " схлопнулся") + "!");
                BlockPos c = console == null ? origin : console;
                world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, c.getX() + 0.5D, c.getY() + 2.0D, c.getZ() + 3.5D, 3, 1.0D, 0.5D, 0.5D, 0.0D);
                sound(ModSounds.TRAP, 1.0F);
                lose(p >= FAIL_HIGH ? "бак " + t.index + " взорвался" : "бак " + t.index + " схлопнулся");
                return;
            }
            if (p < LOW || p > HIGH) {
                allGreen = false;
            }
            if (p < LOW - 2.0D || p > HIGH + 2.0D) {
                alarm = true;
            }
            if (!t.leaks.isEmpty() && ticks % 30 == 0) {
                BlockPos c = console == null ? origin : console;
                world.spawnParticle(EnumParticleTypes.CLOUD, c.getX() + (t.index - (tanks.size() + 1) / 2.0D) * 2.0D,
                        c.getY() + 2.0D, c.getZ() + 3.0D, 6, 0.3D, 0.6D, 0.2D, 0.02D);
            }
        }
        if (allGreen) {
            greenTicks++;
        }
        if (alarm && ticks % 40 == 0) {
            sound(ModSounds.PRESSURE_ALARM, 0.8F);
        }
        for (Game.Result r : results.values()) {
            r.score = greenTicks * 100.0D / Math.max(1, ticks);
            EntityPlayerMP p = here(r.id);
            if (p != null) {
                keepAlive(p);
            }
        }
        if (ticks % 20 == 0) {
            bar("&b" + time(limitTicks - ticks) + " &7· в зелёной зоне " + (int) (greenTicks * 100.0D / Math.max(1, ticks)) + "%"
                    + (alarm ? " &c· ТРЕВОГА" : ""));
        }
        if (ticks >= limitTicks) {
            int pct = (int) (greenTicks * 100.0D / Math.max(1, ticks));
            String medal = pct >= 90 ? "золото" : pct >= 70 ? "серебро" : pct >= 40 ? "бронза" : "без медали";
            for (Game.Result r : results.values()) {
                if (!r.out) {
                    r.place = 1;
                    r.note = "смена отстояна, " + pct + "% в зелёной зоне (" + medal + ")";
                }
            }
            tell("&aСмена отстояна! &7Зелёная зона: &f" + pct + "% &7— " + medal);
            for (EntityPlayerMP p : online()) {
                ModSounds.play(p, ModSounds.GAME_WIN, 1.0F, 1.0F);
            }
            end();
        }
    }

    private void lose(String why) {
        for (Game.Result r : results.values()) {
            r.out = true;
            r.note = why;
        }
        tell("&cАвария: " + why + ". Смена провалена.");
        for (EntityPlayerMP p : online()) {
            ModSounds.play(p, ModSounds.GAME_LOSE, 1.0F, 1.0F);
        }
        end();
    }

    private void note(String text) {
        log.add(0, time(ticks) + " " + text);
        while (log.size() > 6) {
            log.remove(log.size() - 1);
        }
    }

    private void sound(net.minecraft.util.SoundEvent s, float vol) {
        for (EntityPlayerMP p : online()) {
            ModSounds.play(p, s, vol, 1.0F);
        }
    }

    @Override
    public String status() {
        return "смена " + time(ticks) + " из " + time(limitTicks);
    }

    // --- пульт (окно) ------------------------------------------------------------------------------------

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
            int pct = (int) (greenTicks * 100.0D / Math.max(1, ticks));
            ui.title("Пульт котельной").subtitle(time(limitTicks - ticks) + " до конца смены · зелёная зона " + pct + "%")
                    .icon(new ItemStack(Items.CAULDRON)).compact();
            ui.sideLabel("&8Аварийное");
            ui.sideButton(coolCooldown > 0 ? "Охлаждение (" + (coolCooldown / 20 + 1) + " с)" : "Аварийное охлаждение",
                    new ItemStack(Blocks.PACKED_ICE), coolCooldown > 0 ? Ui.GREY : Ui.BLUE, () -> {
                        if (coolCooldown > 0) {
                            return;
                        }
                        coolCooldown = 600;
                        for (Tank t : tanks) {
                            t.stuckUntil = 0;
                            t.temp = Math.max(10.0D, t.temp - 45.0D);
                        }
                        note(player.getName() + ": аварийное охлаждение");
                        sound(ModSounds.STEAM_LEAK, 0.6F);
                    }).tip("&7Все баки сразу остывают на 45°", "&7и снимает заклинивший нагрев", "&8раз в 30 с");
            ui.sideButton("Выйти из игры", new ItemStack(Blocks.BARRIER), Ui.RED, () -> {
                close();
                GameManager.leave(player);
            }).confirm("Выйти из смены? Остальные продолжат без тебя.");
            for (final Tank t : tanks) {
                double p = t.pressure();
                boolean glitch = ticks < t.glitchUntil;
                int color = t.broken ? Ui.GREY : p < LOW - 2 || p > HIGH + 2 ? Ui.RED : p < LOW || p > HIGH ? Ui.YELLOW : Ui.GREEN;
                ui.header("Бак " + t.index, t.broken ? "&8сломан" : glitch ? "&e??? бар" : String.format("%.1f бар", p));
                ui.bar(glitch ? "&eдатчик врёт" : "&7давление (зелёное — 8–12)", glitch ? 8 + rnd.nextInt(8) : p, FAIL_HIGH, color);
                StringBuilder state = new StringBuilder();
                state.append(String.format("&7Температура &f%.0f°", t.temp));
                if (ticks < t.surgeUntil) {
                    state.append(" &e· скачок насоса");
                }
                if (ticks < t.stuckUntil) {
                    state.append(" &c· нагрев заклинило");
                }
                ui.text(state.toString());
                Ui.Buttons heat = ui.buttons();
                heat.add("Нагрев −", Ui.BLUE, () -> t.heater = Math.max(0, t.heater - 10));
                heat.add("Нагрев: " + t.heater + "%", Ui.GREY, () -> { });
                heat.add("Нагрев +", Ui.RED, () -> t.heater = Math.min(100, t.heater + 10));
                Ui.Buttons valve = ui.buttons();
                valve.add("Сброс −", Ui.GREY, () -> t.valve = Math.max(0, t.valve - 10));
                valve.add("Сброс: " + t.valve + "%", Ui.GREY, () -> { });
                valve.add("Сброс +", Ui.YELLOW, () -> t.valve = Math.min(100, t.valve + 10));
                if (!t.leaks.isEmpty()) {
                    int total = 0;
                    for (int s : t.leaks) {
                        total += s;
                    }
                    ui.row(new ItemStack(Items.WATER_BUCKET), "&cУтечек: " + t.leaks.size()).line("&7латать ещё " + total + " раз")
                            .button("Залатать", Ui.GREEN, () -> {
                                if (t.leaks.isEmpty()) {
                                    return;
                                }
                                int s = t.leaks.get(0) - 1;
                                if (s <= 0) {
                                    t.leaks.remove(0);
                                    note(player.getName() + " залатал утечку в баке " + t.index);
                                } else {
                                    t.leaks.set(0, s);
                                }
                                ModSounds.play(player, ModSounds.VALVE, 0.6F, 1.4F);
                            });
                }
                if (glitch) {
                    ui.buttons().add("Калибровка датчика", Ui.PURPLE, () -> {
                        t.glitchUntil = 0;
                        note(player.getName() + " откалибровал датчик бака " + t.index);
                    });
                }
            }
            if (!log.isEmpty()) {
                ui.header("Журнал");
                for (String l : log) {
                    ui.text("&7" + l);
                }
            }
        }
    }
}
