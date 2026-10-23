package com.ecoserv.extra;

import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** /rank — время в игре, роли, следующее звание, зарплата. Админ может смотреть других: /rank <ник>. */
public class CommandRank extends CommandBase {
   public String getName() {
      return "rank";
   }

   public String getUsage(ICommandSender sender) {
      return "/rank [ник] — время в игре, звания и зарплата";
   }

   public int getRequiredPermissionLevel() {
      return 0;
   }

   public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
      return true;
   }

   public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      UUID id;
      String name;
      if (args.length > 0 && Perms.isAdmin(sender)) {
         id = PlayerUtil.uuidOf(args[0]);
         name = args[0];
         if (id == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[0] + " &cне найден.");
            return;
         }
      } else {
         EntityPlayerMP p = getCommandSenderAsPlayer(sender);
         id = p.getUniqueID();
         name = p.getName();
      }

      ChatUtil.sendRaw(sender, "&6=== " + name + " ===");
      ChatUtil.sendRaw(sender, "&7Время в игре: &f" + Fmt.duration(Ranks.playtime(id)));
      List<String> titles = new ArrayList<>();

      for (Role r : RoleManager.rolesOf(id)) {
         titles.add(r.title());
      }

      ChatUtil.sendRaw(sender, "&7Роли: " + (titles.isEmpty() ? "&8нет" : String.join("&7, ", titles)));
      String next = Ranks.describeNext(id);
      ChatUtil.sendRaw(sender, "&7Следующее звание: " + (next == null ? "&8нет" : next));
      long[] sal = Salary.best(id);
      if (sal != null) {
         long left = Salary.untilNext(id);
         ChatUtil.sendRaw(
            sender,
            "&7Зарплата: &e" + Fmt.money(sal[0]) + " &7каждые &f" + sal[1] + " мин &7в сети"
               + (left >= 0L ? " &8(следующая через " + Fmt.duration(left) + ")" : "")
         );
      } else if (!ExtraConfig.SALARIES.isEmpty()) {
         ChatUtil.sendRaw(sender, "&7Зарплата: &8нет (платят ролям: " + String.join(", ", ExtraConfig.SALARIES.keySet()) + ")");
      }

   }

   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
      return args.length == 1 && Perms.isAdmin(sender) ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : new ArrayList<>();
   }
}
