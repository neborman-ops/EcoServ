package com.ecoserv.command;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.subworld.WorldCrews;
import com.ecoserv.subworld.menu.SaveScreen;
import com.ecoserv.subworld.menu.WorldsScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Команда и гости в /world — понятными словами:
 * add / accept / deny / kick / leaveteam / disband / team — своя команда (общие локальные миры);
 * invite / uninvite / guests / visit — гости в одном мире; menu / help.
 */
final class WorldTeamCommands {

    static final List<String> SUBCOMMANDS = Arrays.asList("menu", "help", "team", "add", "accept", "deny", "kick",
            "leaveteam", "disband", "invite", "uninvite", "guests", "visit");

    private WorldTeamCommands() {
    }

    /** true — подкоманда наша и обработана. */
    static boolean handle(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            return false;
        }
        if ("help".equals(sub)) {
            help(sender);
            return true;
        }
        EntityPlayerMP p = CommandBase.getCommandSenderAsPlayer(sender);
        switch (sub) {
            case "menu":
                WorldsScreen.open(p);
                return true;
            case "team":
                team(p);
                return true;
            case "add": {
                UUID target = target(p, args, "/world add <ник>");
                if (target != null) {
                    report(p, WorldCrews.invite(p, target), "&aПриглашение отправлено: &f" + PlayerUtil.nameOf(target)
                            + "&a. Когда он согласится, у вас будут общие локальные миры.");
                }
                return true;
            }
            case "accept":
                report(p, WorldCrews.accept(p, args.length > 1 ? args[1] : null), "&aТы в команде! Её локальные миры — "
                        + "меню миров → «Команда».");
                return true;
            case "deny":
                report(p, WorldCrews.deny(p, args.length > 1 ? args[1] : null), "&7Приглашение отклонено.");
                return true;
            case "kick": {
                UUID target = target(p, args, "/world kick <ник>");
                if (target != null) {
                    report(p, WorldCrews.kick(p, WorldCrews.manageKey(p), target), "&a" + PlayerUtil.nameOf(target)
                            + " больше не в команде.");
                }
                return true;
            }
            case "leaveteam":
                report(p, WorldCrews.leave(p), "&aТы вышел из команды.");
                return true;
            case "disband":
                report(p, WorldCrews.disband(p), "&aКоманда распущена.");
                return true;
            case "invite":
                invite(p, args);
                return true;
            case "uninvite":
                uninvite(p, args);
                return true;
            case "guests":
                guests(p);
                return true;
            case "visit":
                visit(p, args);
                return true;
            default:
                return false;
        }
    }

    private static void report(EntityPlayerMP p, @Nullable String error, String ok) {
        ChatUtil.send(p, error == null ? ok : error);
    }

    @Nullable
    private static UUID target(EntityPlayerMP p, String[] args, String usage) {
        if (args.length < 2) {
            ChatUtil.send(p, "&cИспользование: &f" + usage);
            return null;
        }
        UUID id = PlayerUtil.knownUuid(args[1]);
        if (id == null) {
            ChatUtil.send(p, "&cИгрок &f" + args[1] + " &cне найден (он должен хотя бы раз зайти на сервер).");
        }
        return id;
    }

    private static void help(ICommandSender sender) {
        ChatUtil.send(sender, "&6=== Миры, команда и гости ===");
        ChatUtil.sendRaw(sender, " &f/world &7— список миров, &f/world <мир> &7— перейти, &f/world menu &7— окно миров");
        ChatUtil.sendRaw(sender, " &f/world leave &7— выйти из подмира туда, откуда пришёл");
        ChatUtil.sendRaw(sender, "&eКоманда &7(общие локальные миры — играете вместе во всех мирах):");
        ChatUtil.sendRaw(sender, " &f/world add <ник> &7— позвать в свою команду (он должен согласиться)");
        ChatUtil.sendRaw(sender, " &f/world accept [ник] &7/ &f/world deny [ник] &7— принять или отклонить приглашение");
        ChatUtil.sendRaw(sender, " &f/world team &7— кто в команде, &f/world kick <ник> &7— убрать, "
                + "&f/world leaveteam &7— выйти, &f/world disband &7— распустить");
        ChatUtil.sendRaw(sender, "&bГости &7(в ОДИН мир — посмотреть или помочь построить):");
        ChatUtil.sendRaw(sender, " &f/world invite <ник> [build] &7— позвать в мир, где стоишь (build — можно строить)");
        ChatUtil.sendRaw(sender, " &f/world uninvite <ник> &7— выгнать, &f/world guests &7— список гостей");
        ChatUtil.sendRaw(sender, " &f/world visit <ник> &7— войти в мир игрока, куда тебя позвали");
        ChatUtil.sendRaw(sender, "&7Всё то же самое — в окне: кнопка-планета в инвентаре → вкладка «Команда».");
    }

    private static void team(EntityPlayerMP p) {
        String key = WorldCrews.manageKey(p);
        List<UUID> members = SubworldTeams.members(key);
        ChatUtil.send(p, "&6Твоя команда: &f" + SubworldTeams.ownerName(key) + (key.startsWith("team:") ? " &7(команда FTB)" : ""));
        StringBuilder sb = new StringBuilder();
        for (UUID m : members) {
            if (sb.length() > 0) {
                sb.append("&7, ");
            }
            sb.append(PlayerUtil.online(m) != null ? "&a" : "&7").append(PlayerUtil.nameOf(m));
        }
        ChatUtil.sendRaw(p, " &7Участники: " + sb);
        List<WorldCrews.Invite> invites = WorldCrews.invitesFor(p.getUniqueID());
        for (WorldCrews.Invite inv : invites) {
            ITextComponent line = ChatUtil.component(" &bПриглашение от &f" + inv.leaderName + " ");
            line.appendSibling(ChatUtil.link("&a[Принять]", "/world accept " + inv.leaderName, "&aВступить"));
            line.appendSibling(ChatUtil.component(" "));
            line.appendSibling(ChatUtil.link("&c[Отказаться]", "/world deny " + inv.leaderName, "&cОтказаться"));
            ChatUtil.send(p, line);
        }
        ChatUtil.sendRaw(p, " &7Добавить: &f/world add <ник>&7, все команды: &f/world help");
    }

    /** Локальный мир, где стоит игрок, если он в нём участник (или админ). */
    @Nullable
    private static SubworldDim ownWorldHere(EntityPlayerMP p) {
        SubworldDim d = SubworldRegistry.dim(p.dimension);
        if (d == null || !d.isInstance()) {
            ChatUtil.send(p, "&eВстань в свой локальный мир (или позови гостя в окне: меню миров → «Команда» → ⚙ у мира).");
            return null;
        }
        if (!Perms.isAdmin(p) && !SubworldTeams.isMember(p.getUniqueID(), d.owner)) {
            ChatUtil.send(p, "&cЭто не твой мир — звать гостей могут только участники его команды.");
            return null;
        }
        return d;
    }

    private static void invite(EntityPlayerMP p, String[] args) {
        if (args.length < 2) {
            ChatUtil.send(p, "&cИспользование: &f/world invite <ник> [build] &7— позвать гостем в мир, где стоишь");
            return;
        }
        SubworldDim d = ownWorldHere(p);
        if (d == null) {
            return;
        }
        boolean build = args.length > 2 && ("build".equalsIgnoreCase(args[2]) || "строить".equalsIgnoreCase(args[2]));
        String err = SaveScreen.invite(p, d, args[1], build);
        ChatUtil.send(p, err != null ? err : "&aГость приглашён: &f" + args[1] + (build ? " &7(может строить)" : " &7(только смотреть)")
                + "&a. Он войдёт так: &f/world visit " + p.getName());
    }

    private static void uninvite(EntityPlayerMP p, String[] args) {
        if (args.length < 2) {
            ChatUtil.send(p, "&cИспользование: &f/world uninvite <ник>");
            return;
        }
        SubworldDim d = ownWorldHere(p);
        if (d == null) {
            return;
        }
        for (SubworldDim.Guest g : new ArrayList<SubworldDim.Guest>(d.guests)) {
            if (g.name.equalsIgnoreCase(args[1])) {
                SubworldDim.Guest removed = g;
                d.guests.remove(removed);
                SubworldRegistry.save();
                UUID id = PlayerUtil.knownUuid(g.name);
                EntityPlayerMP guest = id == null ? null : PlayerUtil.online(id);
                if (guest != null && guest.dimension == d.dim && !SubworldTeams.isMember(guest.getUniqueID(), d.owner)
                        && !Perms.isAdmin(guest)) {
                    SubworldTeleport.evict(guest, "&eТебя больше не ждут в этом мире.");
                }
                ChatUtil.send(p, "&a" + g.name + " больше не гость этого мира.");
                return;
            }
        }
        ChatUtil.send(p, "&e" + args[1] + " не гость этого мира. Список: &f/world guests");
    }

    private static void guests(EntityPlayerMP p) {
        SubworldDim d = ownWorldHere(p);
        if (d == null) {
            return;
        }
        if (d.guests.isEmpty()) {
            ChatUtil.send(p, "&7В мире «" + d.saveName + "» гостей нет. Позвать: &f/world invite <ник> [build]");
            return;
        }
        ChatUtil.send(p, "&6Гости мира «" + d.saveName + "»:");
        for (SubworldDim.Guest g : d.guests) {
            ChatUtil.sendRaw(p, " &b" + g.name + " &7— " + (g.build ? "может строить" : "только смотрит"));
        }
    }

    private static void visit(EntityPlayerMP p, String[] args) {
        String uuid = p.getUniqueID().toString();
        List<SubworldDim> found = new ArrayList<SubworldDim>();
        UUID host = args.length > 1 ? PlayerUtil.knownUuid(args[1]) : null;
        for (SubworldDim d : SubworldRegistry.dims()) {
            if (!d.isInstance() || d.guest(uuid) == null) {
                continue;
            }
            if (host == null || SubworldTeams.isMember(host, d.owner)) {
                found.add(d);
            }
        }
        if (found.isEmpty()) {
            ChatUtil.send(p, args.length > 1 ? "&eИгрок &f" + args[1] + " &eне звал тебя в гости."
                    : "&eТебя никто не звал в гости.");
            return;
        }
        if (found.size() > 1 && host == null) {
            ChatUtil.send(p, "&eТебя позвали в несколько миров — выбери в меню миров → «Команда» → «Тебя позвали в гости», "
                    + "или напиши &f/world visit <ник>");
            return;
        }
        found.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        SubworldDim d = found.get(0);
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw != null) {
            SubworldTeleport.visit(p, d);
        }
    }

    @Nullable
    static List<String> complete(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length != 2) {
            return null;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("add".equals(sub) || "kick".equals(sub) || "invite".equals(sub) || "uninvite".equals(sub)
                || "visit".equals(sub) || "accept".equals(sub) || "deny".equals(sub)) {
            return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return null;
    }
}
