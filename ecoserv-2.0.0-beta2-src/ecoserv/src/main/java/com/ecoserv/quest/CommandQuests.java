package com.ecoserv.quest;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /quests [квест] — книга квестов. Админ (и консоль, сценарии): /quests event &lt;ник&gt; &lt;имя&gt;,
 * /quests complete &lt;ник&gt; &lt;квест&gt;, /quests reset &lt;ник&gt; [квест], /quests reload, /quests check.
 */
public class CommandQuests extends CommandBase {

    @Override
    public String getName() {
        return "quests";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("q", "квесты", "quest");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/quests [квест] | /quests event|complete|reset <ник> ... | /quests reload | /quests check";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = Perms.isAdmin(sender);
        if (admin && (sub.equals("event") || sub.equals("complete") || sub.equals("reset"))) {
            if (args.length < 2) {
                ChatUtil.send(sender, "&c" + getUsage(sender));
                return;
            }
            UUID id = PlayerUtil.uuidOf(args[1]);
            EntityPlayerMP p = PlayerUtil.online(id);
            if (id == null) {
                ChatUtil.send(sender, "&cИгрок не найден.");
                return;
            }
            if (sub.equals("reset")) {
                QuestManager.reset(id, args.length > 2 ? args[2] : null);
                ChatUtil.send(sender, "&aПрогресс сброшен.");
                return;
            }
            if (p == null || args.length < 3) {
                ChatUtil.send(sender, "&cИгрок должен быть в сети; укажи имя.");
                return;
            }
            if (sub.equals("event")) {
                QuestManager.event(p, "event", args[2], 1);
                return;
            }
            QuestBook.Quest q = QuestManager.quest(args[2]);
            if (q == null) {
                ChatUtil.send(sender, "&cНет квеста " + args[2]);
                return;
            }
            QuestManager.progress(p.getUniqueID()).done.put(q.id, System.currentTimeMillis());
            QuestManager.markDirty();
            ChatUtil.send(sender, "&aКвест отмечен выполненным.");
            return;
        }
        if (admin && sub.equals("reload")) {
            QuestManager.load();
            ChatUtil.send(sender, "&aКнига квестов перечитана.");
            return;
        }
        if (admin && sub.equals("check")) {
            List<String> errs = QuestManager.validate();
            ChatUtil.send(sender, errs.isEmpty() ? "&aОшибок нет." : "&cОшибок: " + errs.size());
            for (String e : errs) {
                ChatUtil.send(sender, "&7- " + e);
            }
            return;
        }
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (!sub.isEmpty() && QuestManager.quest(args[0]) != null) {
            QuestScreen.open(p, args[0]);
        } else {
            QuestScreen.open(p);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1 && Perms.isAdmin(sender)) {
            return getListOfStringsMatchingLastWord(args, new ArrayList<String>(Arrays.asList("event", "complete", "reset", "reload", "check")));
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
