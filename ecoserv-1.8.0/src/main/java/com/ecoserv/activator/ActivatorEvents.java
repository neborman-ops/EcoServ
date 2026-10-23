package com.ecoserv.activator;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.item.ItemActivatorWand;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Нажатие именованной кнопки или рычага игроком.
 * Клик только запоминается, а в конце того же тика проверяется, что кнопка действительно
 * нажалась (рычаг включился): клик мог быть отменён модом, «съеден» предметом в руке и т.п.
 */
public class ActivatorEvents {

    private static final class Pending {
        final EntityPlayerMP player;
        final Activator activator;

        Pending(EntityPlayerMP player, Activator activator) {
            this.player = player;
            this.activator = activator;
        }
    }

    private final List<Pending> pending = new ArrayList<Pending>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !EcoConfig.activatorsEnabled) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer) {
            return;
        }
        if (event.getUseBlock() == Event.Result.DENY) {
            return;
        }
        // палкой активации админ настраивает кнопку, а не нажимает её
        if (player.getHeldItemMainhand().getItem() instanceof ItemActivatorWand
                || player.getHeldItemOffhand().getItem() instanceof ItemActivatorWand) {
            return;
        }
        BlockPos pos = event.getPos();
        Activator activator = ActivatorManager.get(world.provider.getDimension(), pos);
        if (activator == null) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!ActivatorManager.isActivatorBlock(state)) {
            return;
        }
        // уже нажатая кнопка или выключаемый рычаг не считаются
        Boolean powered = ActivatorManager.poweredValue(state);
        if (powered != null && powered) {
            return;
        }
        pending.add(new Pending((EntityPlayerMP) player, activator));
    }

    /** Конец тика: засчитываем только те клики, после которых кнопка/рычаг действительно включились. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
            return;
        }
        List<Pending> batch = new ArrayList<Pending>(pending);
        pending.clear();
        MinecraftServer server = PlayerUtil.server();
        if (server == null) {
            return;
        }
        for (int i = 0; i < batch.size(); i++) {
            Pending p = batch.get(i);
            Activator a = p.activator;
            if (PlayerUtil.online(p.player.getUniqueID()) != p.player || ActivatorManager.get(a.dim, a.pos()) != a) {
                continue;
            }
            WorldServer world = server.getWorld(a.dim);
            if (world == null) {
                continue;
            }
            IBlockState state = world.getBlockState(a.pos());
            Boolean powered = ActivatorManager.poweredValue(state);
            if (ActivatorManager.isActivatorBlock(state) && powered != null && powered) {
                ActivatorManager.trigger(p.player, a);
            }
        }
    }

    /** ЛКМ палкой активации — посмотреть название кнопки. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || !(event.getItemStack().getItem() instanceof ItemActivatorWand)) {
            return;
        }
        event.setCanceled(true);
        Activator activator = ActivatorManager.get(event.getWorld().provider.getDimension(), event.getPos());
        if (activator == null) {
            ChatUtil.actionBar(event.getEntityPlayer(), "&7У этого блока нет названия");
        } else {
            ChatUtil.actionBar(event.getEntityPlayer(), "&6«" + activator.name + "» &7— нажатий: &f" + activator.uses
                    + (activator.command.isEmpty() ? "" : " &7| команда: &f" + activator.command));
        }
    }
}
