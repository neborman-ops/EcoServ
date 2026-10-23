package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Горячая картошка» (2.0): у одного игрока на голове динамит. Ударь другого — динамит перейдёт к нему.
 * Когда таймер кончится, тот, у кого динамит, выбывает. Последний оставшийся побеждает.
 */
public class TntTagGame extends ArenaGame implements HitGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("tnttag", "&cГорячая картошка", "У кого динамит на голове — ударь другого, чтобы передать. "
                    + "Таймер кончился — бум, выбыл.", "minecraft:tnt", true);
            param("round", "Раунд (сек)", "Сколько тикает динамит", 25, 8, 90);
            param("size", "Размер арены", "блоков", 28, 14, 60);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new TntTagGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.minPlayers = 2;
            p.maxPlayers = 16;
            return p;
        }
    }

    private final Random rnd = new Random();
    private final int roundTicks;
    private final int size;
    private UUID holder;
    private int boomAt;
    private int lastPass;

    public TntTagGame(GameManager.Session session) {
        super(session);
        roundTicks = param("round", 25) * 20;
        size = param("size", 28);
    }

    @Override
    protected void build() {
        int h = size / 2;
        fill(-h, 0, -h, h, 0, h, GameColors.concrete(8));
        // узор и укрытия
        for (int x = -h; x <= h; x += 4) {
            for (int z = -h; z <= h; z += 4) {
                set(x, 0, z, GameColors.concrete(0));
            }
        }
        for (int i = 0; i < size / 3; i++) {
            int x = rnd.nextInt(size - 4) - h + 2;
            int z = rnd.nextInt(size - 4) - h + 2;
            int hgt = 1 + rnd.nextInt(3);
            fill(x, 1, z, x, hgt, z, Blocks.STONEBRICK.getDefaultState());
        }
        net.minecraft.block.state.IBlockState glass = Blocks.GLASS.getDefaultState();
        fill(-h - 1, 0, -h - 1, h + 1, 6, -h - 1, glass);
        fill(-h - 1, 0, h + 1, h + 1, 6, h + 1, glass);
        fill(-h - 1, 0, -h - 1, -h - 1, 6, h + 1, glass);
        fill(h + 1, 0, -h - 1, h + 1, 6, h + 1, glass);
        fill(-3, 12, -3, 3, 12, 3, glass);
        bounds = new int[]{origin.getX() - h - 3, origin.getY() - 3, origin.getZ() - h - 3,
                origin.getX() + h + 3, origin.getY() + 14, origin.getZ() + h + 3};
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        double a = index * 2.399963D;
        double r = Math.min(size / 2 - 2, 3 + index);
        return rel((int) Math.round(Math.cos(a) * r), 1, (int) Math.round(Math.sin(a) * r));
    }

    @Override
    public void begin() {
        super.begin();
        tell("&cГорячая картошка! &7У кого динамит — бей другого, чтобы передать.");
        newRound();
    }

    private void newRound() {
        List<EntityPlayerMP> alive = active();
        if (alive.size() <= 1) {
            if (alive.size() == 1) {
                finish(alive.get(0).getUniqueID(), "последний выживший");
            }
            end();
            return;
        }
        give(alive.get(rnd.nextInt(alive.size())));
        boomAt = ticks + roundTicks;
    }

    private void give(EntityPlayerMP p) {
        if (holder != null) {
            EntityPlayerMP old = here(holder);
            if (old != null) {
                old.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
                old.removePotionEffect(MobEffects.SPEED);
            }
        }
        holder = p.getUniqueID();
        p.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Blocks.TNT));
        p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 1000000, 1, true, false));
        Game.title(p, "&cДинамит у тебя!", "&7ударь кого-нибудь");
        ModSounds.play(p, ModSounds.TRAP, 1.0F, 1.4F);
        tell("&c" + p.getName() + " &7держит динамит");
        lastPass = ticks;
    }

    @Override
    public void onHit(EntityPlayerMP attacker, EntityPlayerMP victim) {
        if (holder == null || !holder.equals(attacker.getUniqueID()) || ticks - lastPass < 10) {
            return;
        }
        Game.Result r = results.get(victim.getUniqueID());
        if (r == null || r.out || r.place != 0) {
            return;
        }
        give(victim);
        // отбросить, чтобы не передали тут же обратно
        double dx = victim.posX - attacker.posX;
        double dz = victim.posZ - attacker.posZ;
        double l = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
        victim.motionX += dx / l * 0.6D;
        victim.motionZ += dz / l * 0.6D;
        victim.motionY = 0.35D;
        victim.velocityChanged = true;
    }

    @Override
    public void tick() {
        ticks++;
        for (Game.Result r : results.values()) {
            if (r.place != 0 || r.out) {
                continue;
            }
            EntityPlayerMP p = here(r.id);
            if (p == null) {
                continue;
            }
            keepAlive(p);
            r.score = ticks;
            if (p.posY < origin.getY() - 3) {
                respawn(p, "Упал");
            }
        }
        if (holder != null && ticks >= boomAt) {
            EntityPlayerMP p = here(holder);
            if (p != null) {
                world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, p.posX, p.posY + 1, p.posZ, 3, 0.5D, 0.5D, 0.5D, 0.0D);
                ModSounds.playAt(world, p.posX, p.posY, p.posZ, ModSounds.TRAP, 1.0F, 0.5F);
                p.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
                p.removePotionEffect(MobEffects.SPEED);
                out(holder, "взорвался");
                p.connection.setPlayerLocation(origin.getX() + 0.5D, origin.getY() + 13.0D, origin.getZ() + 0.5D, 0.0F, 60.0F);
            } else {
                out(holder, "ушёл с динамитом");
            }
            holder = null;
            newRound();
        }
        if (holder != null && ticks % 10 == 0) {
            int left = Math.max(0, boomAt - ticks);
            EntityPlayerMP h = here(holder);
            bar("&cДинамит: &f" + (h == null ? "?" : h.getName()) + " &8· &7" + (left / 20 + 1) + " с");
            if (h != null && left < 100) {
                ModSounds.play(h, ModSounds.GAME_COUNTDOWN, 0.6F, 2.0F - left / 100.0F);
            }
        }
    }

    @Override
    public void cleanup() {
        for (EntityPlayerMP p : online()) {
            p.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
            p.removePotionEffect(MobEffects.SPEED);
        }
        super.cleanup();
    }

    @Override
    public String status() {
        return "в игре " + active().size();
    }
}
