package com.ecoserv.tile;

import com.ecoserv.block.BlockVendor;
import com.ecoserv.util.Perms;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * «Склад продавца»: сюда складывают товар и выставляют его на аукцион.
 * <p>
 * Открыть и забрать может только владелец (и админ).
 * Автоматика (Mekanism, HBM и любая другая) может класть предметы
 * ТОЛЬКО в заднюю грань блока и ничего не может достать.
 * Поддержаны оба API: Forge-капабилити и старый ISidedInventory.
 */
public class TileVendor extends TileEntity implements ISidedInventory, ITickable {

    public static final int SIZE = 27;

    private final ItemStackHandler inventory = new ItemStackHandler(SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    private final IItemHandler backInsert = new InsertOnlyHandler(inventory, false);
    private final IItemHandler readOnly = new ReadOnlyHandler(inventory);

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private double price = 1.0D;
    private long soldTotal;
    private long earnedTotal;
    private int syncTick;

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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = Math.max(0.0D, price);
        markDirty();
    }

    public long getSoldTotal() {
        return soldTotal;
    }

    public long getEarnedTotal() {
        return earnedTotal;
    }

    public void addSale(int count, long money) {
        soldTotal += count;
        earnedTotal += money;
        markDirty();
    }

    public void setOwner(@Nullable UUID uuid, String name) {
        this.owner = uuid;
        this.ownerName = name == null ? "" : name;
        markDirtyAndSync();
    }

    /** Хозяин — только тот, кто поставил блок. */
    public boolean isOwner(EntityPlayer player) {
        return player != null && owner != null && owner.equals(player.getUniqueID());
    }

    /**
     * Открыть и забрать — только хозяин. Админ — лишь если это явно разрешено
     * в конфиге (general.admins_open_private). Блок без хозяина не открывается никем,
     * иначе его мог бы «присвоить» первый встречный.
     */
    public boolean canAccess(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        if (isOwner(player)) {
            return true;
        }
        return com.ecoserv.config.EcoConfig.adminsOpenPrivate && Perms.isAdmin(player);
    }

    /** Сломать может только хозяин (админ — при general.admins_open_private=true). */
    public boolean canBreak(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        return owner == null || isOwner(player)
                || (com.ecoserv.config.EcoConfig.adminsOpenPrivate && Perms.isAdmin(player));
    }

    /** Задняя грань блока — единственный вход для автоматики. */
    public EnumFacing backSide() {
        if (world == null) {
            return EnumFacing.SOUTH;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockVendor) {
            return state.getValue(BlockVendor.FACING).getOpposite();
        }
        return EnumFacing.SOUTH;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        // содержимое видно только в GUI, но владельцу полезно видеть счётчики
        if (++syncTick >= 100) {
            syncTick = 0;
        }
    }

    public boolean isEmptyInventory() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    // --- NBT ----------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setString("OwnerName", ownerName == null ? "" : ownerName);
        compound.setDouble("Price", price);
        compound.setLong("Sold", soldTotal);
        compound.setLong("Earned", earnedTotal);
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
        price = compound.hasKey("Price") ? compound.getDouble("Price") : 1.0D;
        soldTotal = compound.getLong("Sold");
        earnedTotal = compound.getLong("Earned");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 2, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // --- capability (Mekanism, трубы, современные моды) ---------------------

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return facing == null || facing == backSide();
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
            if (facing == backSide()) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(backInsert);
            }
            return null;
        }
        return super.getCapability(capability, facing);
    }

    // --- ISidedInventory (HBM и прочие моды на старом API) ------------------

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        if (side != backSide()) {
            return new int[0];
        }
        int[] slots = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return direction == backSide();
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return false;
    }

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return isEmptyInventory();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index < 0 || index >= SIZE ? ItemStack.EMPTY : inventory.getStackInSlot(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.getStackInSlot(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.splitStack(count);
        inventory.setStackInSlot(index, stack.isEmpty() ? ItemStack.EMPTY : stack);
        markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.getStackInSlot(index);
        inventory.setStackInSlot(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index >= 0 && index < SIZE) {
            inventory.setStackInSlot(index, stack);
            markDirty();
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return canAccess(player) && player.getDistanceSq(pos) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        markDirty();
    }

    @Override
    public String getName() {
        return "container.ecoserv.vendor";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}
