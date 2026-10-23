package com.ecoserv.games;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Меню мини-игр (1.9.0): список игр с иконками, открытые лобби, своё лобби. Админу — включать и выключать
 * игры и делать свои (вид игры + название, иконка, настройки, ставка, награда).
 */
public class GamesScreen extends UiScreen {

    private enum Tab {
        GAMES, LOBBY, ADMIN
    }

    private Tab tab = Tab.GAMES;
    /** Открытая на правку игра (админ). */
    private String editing;
    private String newKind = "deathrun";

    public GamesScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        GamesScreen s = new GamesScreen(player);
        if (GameManager.sessionOf(player.getUniqueID()) != null) {
            s.tab = Tab.LOBBY;
        }
        s.open();
    }

    @Override
    protected int refreshSeconds() {
        return tab == Tab.LOBBY ? 1 : 3;
    }

    @Override
    protected void build(Ui ui) {
        boolean admin = Perms.isAdmin(player);
        if (tab == Tab.ADMIN && !admin) {
            tab = Tab.GAMES;
        }
        GamePreset edit = editing == null ? null : GameManager.preset(editing);
        if (edit != null && admin) {
            buildEdit(ui, edit);
            return;
        }
        editing = null;
        GameManager.Session mine = GameManager.sessionOf(player.getUniqueID());
        ui.title("Мини-игры").icon(UiIcons.race());
        ui.tab("Игры", UiIcons.race(), tab == Tab.GAMES, () -> tab = Tab.GAMES);
        ui.tab(mine == null ? "Лобби" : "Моё лобби &a●", UiIcons.team(), tab == Tab.LOBBY, () -> tab = Tab.LOBBY);
        if (admin) {
            ui.tab("Настроить игры", UiIcons.admin(), tab == Tab.ADMIN, () -> tab = Tab.ADMIN);
        }
        ui.sideLabel("&8Ещё");
        ui.sideButton("Квестовая гонка", UiIcons.race(), Ui.YELLOW, () -> com.ecoserv.minigame.RaceScreen.open(player));
        ui.sideButton("Меню миров", UiIcons.planet(), Ui.GREY, () -> com.ecoserv.subworld.menu.WorldsScreen.open(player));
        if (!ExtraConfig.gamesEnabled) {
            ui.note(Ui.YELLOW, "Мини-игры выключены в настройках мода" + (admin ? " (видишь, потому что админ)." : "."));
        }
        switch (tab) {
            case LOBBY:
                buildLobby(ui, mine);
                break;
            case ADMIN:
                buildAdmin(ui);
                break;
            default:
                buildGames(ui, mine);
        }
    }

    // --- игры и лобби ---------------------------------------------------------------------------------

    private void buildGames(Ui ui, GameManager.Session mine) {
        ui.search("поиск игры");
        ui.header("Во что поиграть");
        boolean admin = Perms.isAdmin(player);
        for (final GamePreset p : GameManager.presets()) {
            boolean playable = GameManager.playable(p);
            if (!playable && !admin) {
                continue;
            }
            GameKind k = GameManager.KINDS.get(p.kind);
            Ui.Row row = ui.row(UiUtil.icon(p.icon), "&f" + p.title);
            if (!p.description.isEmpty()) {
                row.line("&7" + p.description);
            }
            StringBuilder info = new StringBuilder("&8");
            info.append(k == null ? p.kind : UiUtil.plain(k.title)).append(" · ").append(p.minPlayers == p.maxPlayers
                    ? p.minPlayers + " игр." : p.minPlayers + "–" + p.maxPlayers + " игр.").append(" · сыграно ").append(p.played);
            row.line(info.toString());
            if (p.stake > 0) {
                row.chip("ставка " + Fmt.money(p.stake), Ui.YELLOW);
            }
            if (p.reward > 0) {
                row.chip("приз " + Fmt.money(p.reward), Ui.GREEN);
            }
            if (!playable) {
                row.chip("выключена", Ui.GREY);
            }
            if (mine == null) {
                row.button("Создать лобби", Ui.GREEN, () -> {
                    String err = GameManager.create(player, p);
                    if (err != null) {
                        throw new UiError(UiUtil.plain(err));
                    }
                    tab = Tab.LOBBY;
                });
            }
        }
        List<GameManager.Session> open = GameManager.sessions();
        ui.header("Открытые лобби", Integer.toString(open.size()));
        if (open.isEmpty()) {
            ui.text("&8Пока никто не играет — создай своё лобби.");
        }
        for (final GameManager.Session s : open) {
            Ui.Row row = ui.row(UiUtil.icon(s.preset.icon), "&e" + s.preset.title + " &7#" + s.id)
                    .line("&7Хозяин: &f" + PlayerUtil.nameOf(s.host) + " &7· игроков " + s.players.size() + "/" + s.preset.maxPlayers)
                    .line("&8" + (s.state == GameManager.State.RUNNING && s.game != null ? s.game.status()
                            : s.state == GameManager.State.COUNTDOWN ? "запускается…" : "ждёт игроков"));
            if (s.state == GameManager.State.LOBBY && mine == null) {
                Ui.Button join = row.button("Войти", Ui.GREEN, () -> {
                    String err = GameManager.join(player, s);
                    if (err != null) {
                        throw new UiError(UiUtil.plain(err));
                    }
                    tab = Tab.LOBBY;
                });
                if (s.preset.stake > 0) {
                    join.confirm("Войти в лобби? Ставка " + Fmt.money(s.preset.stake) + " спишется сразу.");
                }
            }
        }
    }

    private void buildLobby(Ui ui, GameManager.Session s) {
        if (s == null) {
            ui.note(Ui.BLUE, "Ты не в лобби. Выбери игру во вкладке «Игры» и нажми «Создать лобби» или войди в чужое.");
            return;
        }
        boolean host = s.host.equals(player.getUniqueID());
        ui.subtitle(s.preset.title + " #" + s.id);
        ui.row(UiUtil.icon(s.preset.icon), "&f" + s.preset.title).line("&7" + s.preset.description).highlight(true);
        if (s.state == GameManager.State.RUNNING && s.game != null) {
            ui.note(Ui.GREEN, "Идёт игра: " + s.game.status());
            final com.ecoserv.ui.UiScreen panel = s.game.screen(player);
            if (panel != null) {
                ui.buttons().add("Открыть пульт", Ui.BLUE, panel::open);
            }
        } else if (s.state == GameManager.State.COUNTDOWN) {
            ui.note(Ui.YELLOW, "Запуск через " + Math.max(0, (s.startAt - System.currentTimeMillis() + 999) / 1000) + " с…");
        }
        if (s.pot > 0) {
            ui.text("&7Банк: &6" + Fmt.money(s.pot) + " &8(1 место — 60%, 2 — 30%, 3 — 10%)");
        }
        ui.header("Игроки", s.players.size() + "/" + s.preset.maxPlayers);
        for (UUID id : s.players) {
            ui.row(new ItemStack(Items.SKULL, 1, 3), "&f" + PlayerUtil.nameOf(id) + (id.equals(s.host) ? " &6★" : ""));
        }
        Ui.Buttons b = ui.buttons();
        if (s.state == GameManager.State.LOBBY && host) {
            b.add("Начать", Ui.GREEN, () -> {
                String err = GameManager.start(player, s);
                if (err != null) {
                    throw new UiError(UiUtil.plain(err));
                }
            }).disabled(s.players.size() < s.preset.minPlayers);
        }
        Ui.Button quit = b.add("Выйти", Ui.RED, () -> {
            GameManager.leave(player);
            tab = Tab.GAMES;
        });
        if (s.state == GameManager.State.RUNNING) {
            quit.confirm("Выйти из идущей игры?");
        }
        if (s.players.size() < s.preset.minPlayers) {
            ui.note(Ui.YELLOW, "Нужно игроков: " + s.preset.minPlayers);
        }
    }

    // --- админ -----------------------------------------------------------------------------------------

    private void buildAdmin(Ui ui) {
        ui.note(Ui.BLUE, "Выключенные игры не видны игрокам. Свою игру можно сделать на основе любого вида: название, "
                + "иконка, сложность, ставка и приз. Для игр с ареной мир «" + GameArena.SUBWORLD + "» создаётся сам.");
        ui.header("Игры", Integer.toString(GameManager.presets().size()));
        for (final GamePreset p : GameManager.presets()) {
            Ui.Row row = ui.row(UiUtil.icon(p.icon), "&f" + p.title).line("&8" + p.id + (p.builtin ? " · встроенная" : " · своя, "
                    + p.creator));
            row.chip(p.enabled ? "вкл" : "выкл", p.enabled ? Ui.GREEN : Ui.GREY);
            row.button(p.enabled ? "Выключить" : "Включить", p.enabled ? Ui.GREY : Ui.GREEN, () -> {
                p.enabled = !p.enabled;
                GameManager.save();
            });
            row.button("⚙", Ui.BLUE, () -> editing = p.id).tip("Настроить");
        }
        ui.header("Создать свою игру");
        Ui.Select kind = ui.select("На основе", "", newKind, v -> newKind = v);
        for (GameKind k : GameManager.KINDS.values()) {
            kind.option(k.id, UiUtil.plain(k.title), UiUtil.cut(UiUtil.plain(k.description), 60), UiUtil.icon(k.icon));
        }
        ui.field("Название новой игры", "& — цвета", "", v -> {
            if (v.trim().isEmpty()) {
                throw new UiError("Назови игру.");
            }
            GamePreset p = GameManager.newPreset(newKind, v.trim(), player);
            editing = p.id;
        }).placeholder("&cЛавовый ад").max(48);
    }

    private void buildEdit(Ui ui, final GamePreset p) {
        ui.back(() -> editing = null);
        ui.title("Игра " + p.title).subtitle(p.builtin ? "встроенная" : "своя").icon(UiUtil.icon(p.icon));
        ui.sideLabel("&8Действия");
        if (!p.builtin) {
            ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
                GameManager.removePreset(p);
                editing = null;
            }).confirm("Удалить игру «" + p.title + "»?");
        } else {
            ui.sideButton("Сбросить", new ItemStack(Blocks.TNT), Ui.YELLOW, () -> {
                GamePreset d = GameManager.KINDS.get(p.kind).defaults();
                p.params.clear();
                p.params.putAll(d.params);
                p.title = d.title;
                p.description = d.description;
                p.icon = d.icon;
                GameManager.save();
            }).confirm("Вернуть настройки по умолчанию?");
        }
        ui.toggle("Включена", "", p.enabled, on -> {
            p.enabled = on;
            GameManager.save();
        });
        ui.field("Название", "& — цвета", p.title, v -> {
            p.title = v.trim().isEmpty() ? p.title : v.trim();
            GameManager.save();
        }).max(48);
        ui.field("Описание", "", p.description, v -> {
            p.description = v.trim();
            GameManager.save();
        }).clearable().max(200);
        ui.field("Иконка", "Предмет: кнопка … — выбрать", p.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            p.icon = id;
            GameManager.save();
        }).picker(Ui.PICK_ITEM).max(96);
        ui.number("Игроков минимум", "", p.minPlayers, 1, 100, v -> {
            p.minPlayers = (int) v;
            p.normalize();
            GameManager.save();
        });
        ui.number("Игроков максимум", "", p.maxPlayers, 1, 100, v -> {
            p.maxPlayers = (int) v;
            p.normalize();
            GameManager.save();
        });
        ui.money("Ставка за вход", "Банк делят 1–3 места (0 — без ставки)", p.stake, v -> {
            p.stake = v;
            GameManager.save();
        });
        ui.money("Приз победителю от сервера", "0 — без приза", p.reward, v -> {
            p.reward = v;
            GameManager.save();
        });
        GameKind k = GameManager.KINDS.get(p.kind);
        if (k != null) {
            ui.header("Настройки: " + UiUtil.plain(k.title));
            for (final GameKind.Param prm : k.params) {
                int value = p.param(prm.key, prm.def);
                if (prm.bool) {
                    ui.toggle(prm.title, prm.desc, value != 0, on -> {
                        p.params.put(prm.key, on ? 1 : 0);
                        GameManager.save();
                    });
                } else {
                    ui.number(prm.title, prm.desc, value, prm.min, prm.max, v -> {
                        p.params.put(prm.key, (int) v);
                        GameManager.save();
                    });
                }
            }
        }
    }
}
