package com.ecoserv.command;

import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** /ecobackup — ручное управление бэкапами. */
public class CommandBackup extends CommandBase {

    @Override
    public String getName() {
        return "ecobackup";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ecobackup <now|list|prune|info>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return Perms.isAdmin(sender) || super.checkPermission(server, sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();

        if ("now".equals(sub)) {
            String label = args.length > 1 ? args[1] : "manual";
            BackupManager.scheduleWithWarning(sender);
            ChatUtil.send(sender, "&aБэкап запускается... (" + label + ")");
            return;
        }
        if ("prune".equals(sub)) {
            int deleted = BackupManager.prune();
            ChatUtil.send(sender, "&aУдалено: &f" + deleted + "&a. Осталось: &f"
                    + BackupManager.listBackups().size() + " &7(" + Fmt.bytes(BackupManager.totalSize()) + ")");
            return;
        }
        if ("list".equals(sub)) {
            List<File> files = BackupManager.listBackups();
            if (files.isEmpty()) {
                ChatUtil.send(sender, "&eБэкапов пока нет.");
                return;
            }
            ChatUtil.send(sender, "&6=== Бэкапы (" + files.size() + ", " + Fmt.bytes(BackupManager.totalSize()) + ") ===");
            for (int i = 0; i < files.size() && i < 15; i++) {
                File f = files.get(i);
                ChatUtil.sendRaw(sender, " &f" + f.getName() + " &7- " + Fmt.bytes(f.length())
                        + ", " + Fmt.timestamp(f.lastModified()));
            }
            return;
        }

        ChatUtil.send(sender, "&6=== Бэкапы ===");
        ChatUtil.sendRaw(sender, " &7Папка: &f" + BackupManager.backupDirectory().getAbsolutePath());
        ChatUtil.sendRaw(sender, " &7Автобэкап: &f" + (EcoConfig.backupEnabled
                ? "каждые " + Fmt.minutes(EcoConfig.backupIntervalMinutes) : "выключен"));
        ChatUtil.sendRaw(sender, " &7Хранить: &f" + EcoConfig.backupKeepCount + " шт / "
                + EcoConfig.backupMaxAgeDays + " дн / " + EcoConfig.backupMaxTotalSizeMb + " МБ");
        ChatUtil.sendRaw(sender, " &7Файлов сейчас: &f" + BackupManager.listBackups().size()
                + " (" + Fmt.bytes(BackupManager.totalSize()) + ")");
        if (BackupManager.lastBackupTime() > 0L) {
            ChatUtil.sendRaw(sender, " &7Последний: &f" + Fmt.timestamp(BackupManager.lastBackupTime())
                    + " &7за " + Fmt.duration(BackupManager.lastDuration())
                    + ", " + Fmt.bytes(BackupManager.lastSize()));
        }
        if (EcoConfig.backupEnabled) {
            long left = BackupManager.nextBackupTime() - System.currentTimeMillis();
            ChatUtil.sendRaw(sender, " &7Следующий через: &f" + Fmt.duration(Math.max(0L, left)));
        }
        if (!BackupManager.lastError().isEmpty()) {
            ChatUtil.sendRaw(sender, " &cПоследняя ошибка: &f" + BackupManager.lastError());
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "now", "list", "prune", "info");
        }
        return new ArrayList<String>();
    }
}
