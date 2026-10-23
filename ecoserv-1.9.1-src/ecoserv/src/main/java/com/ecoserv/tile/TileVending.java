package com.ecoserv.tile;

import com.ecoserv.block.BlockVending;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * «Блок продажи» — торговый автомат.
 * <p>
 * Слот 0 — витрина (что продаём, не расходуется), слоты 1..27 — склад.
 * Игрок кликает по блоку -> проверяется баланс -> списывается цена ->
 * выдаётся заданное количество -> над хотбаром пишется чек.
 * Деньги копятся в кассе блока, забрать их может только владелец.
 * Автоматика (HBM, Mekanism, трубы, воронки) кладёт товар ТОЛЬКО в заднюю грань.
 */
public class TileVending extends TileEntity implements ISidedInventory {

    public static final int SAMPLE = 0;
    public static final int STOCK_START = 1;
    public static final int STOCK_SIZE = 27;
    public static final int SIZE = STOCK_START + STOCK_SIZE;

    public enum BuyStatus {
        OK,
        NOT_CONFIGURED,
        OUT_OF_STOCK,
        NO_MONEY,
        DISABLED,
        NO_SPACE
    }

    public static class BuyResult {
        public BuyStatus status = BuyStatus.OK;
        public long price;
        public long balance;
        public int amount;
        public String itemName = "";
    }

    private final ItemStackHandler inventory = new ItemStackHandler(SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    private final IItemHandler backInsert = new InsertOnlyHandler(inventory, false, STOCK_START);
    private final IItemHandler readOnly = new ReadOnlyHandler(inventory);

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private long price = 100L;
    private int amount = 1;
    private long storedMoney;
    private long soldTotal;
    private long earnedTotal;
    private boolean adminShop;
    private boolean harvested;

    public TileVending() {
        this.price = EcoConfig.vendingDefaultPrice;
        this.amount = EcoConfig.vendingDefaultAmount;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStack getSample() {
        return inventory.getStackInSlot(SAMPLE);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public long getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public long getStoredMoney() {
        return storedMoney;
    }

    public long getSoldTotal() {
        return soldTotal;
    }

    public long getEarnedTotal() {
        return earnedTotal;
    }

    public boolean isAdminShop() {
        return adminShop;
    }

    public boolean isHarvested() {
        return harvested;
    }

    public void setHarvested(boolean value) {
        this.harvested = value;
    }

    public void setOwner(@Nullable UUID uuid, String name) {
        this.owner = uuid;
        this.ownerName = name == null ? "" : name;
        markDirtyAndSync();
    }

    public void setAdminShop(boolean value) {
        this.adminShop = value;
        markDirtyAndSync();
    }

    public void configure(long newPrice, int newAmount) {
        this.price = Math.max(0L, Math.min(newPrice, EcoConfig.vendingMaxPrice));
        this.amount = Math.max(1, Math.min(newAmount, 512));
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

    /**
     * Сломать может только хозяин. Иначе «сломал и поставил заново» = присвоил блок вместе
     * с товаром и кассой. Админ — только при general.admins_open_private=true
     * (или сначала /ecoadmin setowner на себя — это явное действие).
     */
    public boolean canBreak(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        return owner == null || isOwner(player)
                || (com.ecoserv.config.EcoConfig.adminsOpenPrivate && Perms.isAdmin(player));
    }

    /** Грань, в которую пускаем автоматику (сзади, помечена красной точкой). */
    public EnumFacing backSide() {
        if (world == null) {
            return EnumFacing.SOUTH;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockVending) {
            return state.getValue(BlockVending.FACING).getOpposite();
        }
        return EnumFacing.SOUTH;
    }

    // --- торговля -----------------------------------------------------------

    public int countStock() {
        ItemStack sample = getSample();
        if (sample.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = STOCK_START; i < SIZE; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (matches(sample, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean matches(ItemStack sample, ItemStack stack) {
        if (sample.isEmpty() || stack.isEmpty() || sample.getItem() != stack.getItem()) {
            return false;
        }
        if (sample.getItemDamage() != stack.getItemDamage()) {
            return false;
        }
        return String.valueOf(sample.getTagCompound()).equals(String.valueOf(stack.getTagCompound()));
    }

    /** Покупка одним кликом. Всё проверяется на сервере. */
    public BuyResult buy(EntityPlayerMP buyer) {
        BuyResult result = new BuyResult();
        if (!EcoConfig.vendingEnabled) {
            result.status = BuyStatus.DISABLED;
            return result;
        }
        ItemStack sample = getSample();
        if (sample.isEmpty() || price <= 0L || amount <= 0) {
            result.status = BuyStatus.NOT_CONFIGURED;
            return result;
        }
        result.price = price;
        result.amount = amount;
        result.itemName = sample.getDisplayName();

        if (!adminShop && countStock() < amount) {
            result.status = BuyStatus.OUT_OF_STOCK;
            return result;
        }
        if (!EconomyManager.take(buyer.getUniqueID(), price)) {
            result.status = BuyStatus.NO_MONEY;
            result.balance = EconomyManager.get(buyer.getUniqueID());
            return result;
        }

        // выдаём товар
        int left = amount;
        if (adminShop) {
            while (left > 0) {
                ItemStack give = sample.copy();
                give.setCount(Math.min(left, sample.getMaxStackSize()));
                PlayerUtil.giveOrDrop(buyer, give);
                left -= give.getCount();
            }
        } else {
            for (int i = STOCK_START; i < SIZE && left > 0; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!matches(sample, stack)) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                ItemStack give = stack.copy();
                give.setCount(take);
                PlayerUtil.giveOrDrop(buyer, give);

                ItemStack rest = stack.copy();
                rest.setCount(stack.getCount() - take);
                inventory.setStackInSlot(i, rest.getCount() <= 0 ? ItemStack.EMPTY : rest);
                left -= take;
            }
        }

        long tax = (long) Math.floor(price * Math.max(0.0D, EcoConfig.vendingTaxPercent) / 100.0D);
        long payout = Math.max(0L, price - tax);
        if (!adminShop) {
            storedMoney += payout;
            earnedTotal += payout;
        }
        soldTotal += amount;
        markDirtyAndSync();

        PlayerStats stats = StatsStore.get(buyer.getUniqueID());
        stats.totalSpent += price;
        StatsStore.markDirty();

        result.balance = EconomyManager.get(buyer.getUniqueID());
        log(buyer, result);
        notifyOwner(buyer, payout);
        return result;
    }

    private void notifyOwner(EntityPlayerMP buyer, long payout) {
        if (adminShop || owner == null) {
            return;
        }
        EntityPlayerMP ownerPlayer = PlayerUtil.online(owner);
        if (ownerPlayer != null && !ownerPlayer.getUniqueID().equals(buyer.getUniqueID())) {
            ChatUtil.send(ownerPlayer, "&a" + buyer.getName() + " купил у тебя &f" + getSample().getDisplayName()
                    + " x" + amount + " &7-> &e" + Fmt.money(payout) + " &7(в кассе: " + Fmt.money(storedMoney) + ")");
        }
        if (EcoConfig.vendingAnnounceBig && price >= EcoConfig.vendingAnnounceThreshold) {
            ChatUtil.broadcast("&e" + buyer.getName() + " купил " + getSample().getDisplayName()
                    + " x" + amount + " за " + Fmt.money(price));
        }
    }

    private void log(EntityPlayerMP buyer, BuyResult result) {
        if (!EcoConfig.vendingLogSales) {
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(DataStore.file("sales.log"), true),
                    StandardCharsets.UTF_8);
            writer.write("[" + Fmt.timestamp(System.currentTimeMillis()) + "] " + buyer.getName()
                    + " -> " + (adminShop ? "ADMIN" : getOwnerName()) + " : " + result.itemName
                    + " x" + result.amount + " за " + result.price
                    + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " dim " + (world == null ? "?" : world.provider.getDimension()) + "\r\n");
        } catch (Exception ignored) {
            // лог не критичен
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                    // ничего
                }
            }
        }
    }

    /** Владелец забирает выручку. */
    public long withdraw(EntityPlayerMP player) {
        if (storedMoney <= 0L || !canAccess(player)) {
            return 0L;
        }
        long amountOut = storedMoney;
        storedMoney = 0L;
        EconomyManager.give(player.getUniqueID(), amountOut);
        markDirtyAndSync();
        return amountOut;
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
        compound.setLong("Price", price);
        compound.setInteger("Amount", amount);
        compound.setLong("Money", storedMoney);
        compound.setLong("Sold", soldTotal);
        compound.setLong("Earned", earnedTotal);
        compound.setBoolean("AdminShop", adminShop);
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
        price = compound.hasKey("Price") ? compound.getLong("Price") : EcoConfig.vendingDefaultPrice;
        amount = compound.hasKey("Amount") ? compound.getInteger("Amount") : EcoConfig.vendingDefaultAmount;
        storedMoney = compound.getLong("Money");
        soldTotal = compound.getLong("Sold");
        earnedTotal = compound.getLong("Earned");
        adminShop = compound.getBoolean("AdminShop");
    }

    /** NBT для предмета-блока: без координат, как у шалкера. */
    public NBTTagCompound writeItemTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.removeTag("x");
        tag.removeTag("y");
        tag.removeTag("z");
        return tag;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 3, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // --- capability ---------------------------------------------------------

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

    // --- ISidedInventory (HBM-конвейеры и другие моды на старом API) --------

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        if (side != backSide()) {
            return new int[0];
        }
        int[] slots = new int[STOCK_SIZE];
        for (int i = 0; i < STOCK_SIZE; i++) {
            slots[i] = STOCK_START + i;
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return index >= STOCK_START && direction == backSide();
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
        for (int i = 0; i < SIZE; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
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
        return "container.ecoserv.vending";
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
