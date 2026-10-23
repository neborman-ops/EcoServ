package com.ecoserv.tile;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.shop.SaleResult;
import com.ecoserv.shop.ShopService;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * «Торговый терминал»: кладёшь предметы — они продаются по прайс-листу,
 * деньги идут настроенному владельцу (даже если он офлайн).
 */
public class TileMarket extends TileEntity implements ITickable {

    public static final int SIZE = 18;

    private final ItemStackHandler inventory = new ItemStackHandler(SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            pendingSale = true;
        }
    };

    private final IItemHandler automationView = new InsertOnlyHandler(inventory, true, 0, this::shop);
    private final IItemHandler readOnly = new ReadOnlyHandler(inventory);

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean autoSell = true;

    private long earnedTotal;
    private long itemsSoldTotal;
    private long lastSaleAmount;
    private long lastSaleTime;

    private int tick;
    private int lastSellTick;
    private boolean pendingSale;
    private long notifyAccumulated;
    private long lastNotify;

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public boolean isAutoSell() {
        return autoSell;
    }

    public long getEarnedTotal() {
        return earnedTotal;
    }

    public long getItemsSoldTotal() {
        return itemsSoldTotal;
    }

    public long getLastSaleAmount() {
        return lastSaleAmount;
    }

    public void setAutoSell(boolean value) {
        this.autoSell = value;
        markDirtyAndSync();
    }

    public void setOwner(@Nullable UUID uuid, String name) {
        this.owner = uuid;
        this.ownerName = name == null ? "" : name;
        markDirtyAndSync();
    }

    /** Хозяин — тот, кто поставил терминал (или кому его передали командой). */
    public boolean isOwner(EntityPlayer player) {
        return player != null && owner != null && owner.equals(player.getUniqueID());
    }

    /** Открыть терминал может только хозяин (market.owner_only_access=false откроет всем). */
    public boolean canAccess(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        if (isOwner(player)) {
            return true;
        }
        if (!EcoConfig.marketOwnerOnlyAccess) {
            return true;
        }
        return EcoConfig.adminsOpenPrivate && com.ecoserv.util.Perms.isAdmin(player);
    }

    public boolean canBreak(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        // только хозяин (админ — при general.admins_open_private=true): иначе содержимое высыпалось бы чужому
        return owner == null || isOwner(player)
                || (EcoConfig.adminsOpenPrivate && com.ecoserv.util.Perms.isAdmin(player));
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        tick++;

        // предметы только что положили — продаём почти сразу, не дожидаясь таймера
        if (pendingSale && EcoConfig.marketInstantSell && tick - lastSellTick >= 5) {
            pendingSale = false;
            trySell();
            return;
        }

        int interval = Math.max(5, EcoConfig.marketSellIntervalTicks);
        if (tick - lastSellTick >= interval) {
            flushNotify();
            trySell();
        }
    }

    private void trySell() {
        lastSellTick = tick;
        if (!autoSell || isEmpty()) {
            return;
        }
        if (owner == null && EcoConfig.marketRequireOwner) {
            return;
        }
        sell(null);
    }

    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Прайс мира, где стоит терминал (у подмира со своими квестами — свой). */
    private com.ecoserv.shop.ShopList shop() {
        return com.ecoserv.shop.ShopList.forDim(world == null ? 0 : world.provider.getDimension());
    }

    /** Продать содержимое. trigger != null, если продажу запустил игрок кнопкой. */
    public SaleResult sell(@Nullable EntityPlayer trigger) {
        SaleResult result = ShopService.sell(inventory, owner, shop());
        if (result.net > 0L) {
            earnedTotal += result.net;
            itemsSoldTotal += result.itemsSold;
            lastSaleAmount = result.net;
            lastSaleTime = System.currentTimeMillis();
            markDirtyAndSync();

            if (trigger != null) {
                ChatUtil.send(trigger, "&aПродано: &f" + result.describe() + " &7-> &e+" + Fmt.money(result.net)
                        + (result.tax > 0L ? " &7(налог " + Fmt.money(result.tax) + ")" : ""));
                if (!result.waiting.isEmpty()) {
                    ChatUtil.send(trigger, "&7Ждут полного приёма: &f" + result.describeWaiting());
                }
            } else if (EcoConfig.marketNotifyOwner) {
                notifyAccumulated += result.net;
            }
        } else if (trigger != null) {
            if (result.limitReached) {
                ChatUtil.send(trigger, "&cДостигнут суточный лимит продажи для этих предметов.");
            } else if (result.questBlocked) {
                ChatUtil.send(trigger, "&cЧасть предметов заблокирована невыполненным квестом.");
            } else if (owner == null) {
                ChatUtil.send(trigger, "&cУ терминала не задан владелец — некому платить.");
            } else if (!result.waiting.isEmpty()) {
                ChatUtil.send(trigger, "&eНе хватает до приёма: &f" + result.describeWaiting()
                        + " &7(предметы полежат, пока не наберётся)");
            } else {
                ChatUtil.send(trigger, "&eНечего продавать: этих предметов нет в прайс-листе.");
            }
        }
        return result;
    }

    /** Раз в полминуты сообщаем владельцу накопленную выручку, чтобы не спамить чат. */
    private void flushNotify() {
        if (notifyAccumulated <= 0L || owner == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNotify < 30_000L) {
            return;
        }
        EntityPlayerMP player = PlayerUtil.online(owner);
        if (player != null) {
            ChatUtil.send(player, "&aТерминал (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + ") заработал &e" + Fmt.money(notifyAccumulated));
        }
        notifyAccumulated = 0L;
        lastNotify = now;
    }

    public void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    // --- NBT ---------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setString("OwnerName", ownerName == null ? "" : ownerName);
        compound.setBoolean("AutoSell", autoSell);
        compound.setLong("Earned", earnedTotal);
        compound.setLong("ItemsSold", itemsSoldTotal);
        compound.setLong("LastSale", lastSaleAmount);
        compound.setLong("LastSaleTime", lastSaleTime);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("Inventory"));
        }
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        ownerName = compound.getString("OwnerName");
        autoSell = !compound.hasKey("AutoSell") || compound.getBoolean("AutoSell");
        earnedTotal = compound.getLong("Earned");
        itemsSoldTotal = compound.getLong("ItemsSold");
        lastSaleAmount = compound.getLong("LastSale");
        lastSaleTime = compound.getLong("LastSaleTime");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // --- capability --------------------------------------------------------

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return facing == null || EcoConfig.marketAllowHopperInput;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == null) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(readOnly);
            }
            if (EcoConfig.marketAllowHopperInput) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automationView);
            }
            return null;
        }
        return super.getCapability(capability, facing);
    }
}
