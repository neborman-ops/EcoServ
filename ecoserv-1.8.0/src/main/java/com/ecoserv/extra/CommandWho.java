package com.ecoserv.extra;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/**
 * /who — кто в каком мире. Обычный игрок видит игроков своего мира и общий список миров
 * с количеством людей; локальные миры команд показываются без названий чужих команд.
 */
public class CommandWho extends CommandBase {
   public String getName() {
      return "who";
   }

   public List<String> getAliases() {
      return Arrays.asList("кто", "online");
   }

   public String getUsage(ICommandSender sender) {
      return "/who — кто в каком мире";
   }

   public int getRequiredPermissionLevel() {
      return 0;
   }

   public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
      return true;
   }

   static String worldTitle(int dim, boolean admin) {
      SubworldDim d = SubworldRegistry.dim(dim);
      if (d != null) {
         Subworld sw = SubworldRegistry.get(d.subworld);
         String name = sw == null ? d.subworld : sw.name;
         if (d.isTemplate()) {
            return name + " &8(шаблон)";
         } else if (d.isInstance()) {
            return name + (admin ? " &8(" + (d.ownerName.isEmpty() ? d.owner : d.ownerName) + ")" : " &8(локальный)");
         } else {
            return name;
         }
      } else {
         String n = WorldNames.displayName(dim);
         return n == null || n.isEmpty() ? "мир " + dim : n;
      }
   }

   public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      boolean admin = Perms.isAdmin(sender);
      EntityPlayerMP self = sender instanceof EntityPlayerMP ? (EntityPlayerMP)sender : null;
      Map<String, List<String>> byWorld = new TreeMap<>();
      int total = 0;

      for (EntityPlayerMP p : PlayerUtil.allOnline()) {
         total++;
         String title = worldTitle(p.dimension, admin);
         byWorld.computeIfAbsent(title, k -> new ArrayList<>()).add(p.getName());
      }

      ChatUtil.sendRaw(sender, "&6=== В сети: &f" + total + " &6===");
      String mine = self == null ? null : worldTitle(self.dimension, admin);

      for (Entry<String, List<String>> e : byWorld.entrySet()) {
         boolean here = e.getKey().equals(mine);
         if (!admin && !here) {
            ChatUtil.sendRaw(sender, "&7" + e.getKey() + "&7: &f" + e.getValue().size());
         } else {
            ChatUtil.sendRaw(sender, (here ? "&a» " : "&7") + e.getKey() + "&7 (" + e.getValue().size() + "): &f" + String.join("&7, &f", e.getValue()));
         }
      }
   }

   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
      return new ArrayList<>();
   }
}
