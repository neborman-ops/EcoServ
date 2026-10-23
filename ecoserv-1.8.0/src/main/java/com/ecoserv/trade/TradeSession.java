package com.ecoserv.trade;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/** Одна сделка между двумя игроками. Живёт на сервере; на клиенте используется пустая копия для GUI. */
public class TradeSession {

    public static final int SLOTS = 12;

    public final int id;
    @Nullable
    public final UUID uuidA;
    @Nullable
    public final UUID uuidB;
    public final String nameA;
    public final String nameB;

    public final ItemStackHandler offerA;
    public final ItemStackHandler offerB;

    public long moneyA;
    public long moneyB;
    public boolean confirmA;
    public boolean confirmB;
    public long lastChange = System.currentTimeMillis();
    public boolean finished;
    public long createdAt = System.currentTimeMillis();

    public TradeSession(int id, EntityPlayerMP a, EntityPlayerMP b) {
        this(id, a.getUniqueID(), a.getName(), b.getUniqueID(), b.getName());
    }

    public TradeSession(int id, @Nullable UUID uuidA, String nameA, @Nullable UUID uuidB, String nameB) {
        this.id = id;
        this.uuidA = uuidA;
        this.uuidB = uuidB;
        this.nameA = nameA;
        this.nameB = nameB;
        this.offerA = new TrackingHandler();
        this.offerB = new TrackingHandler();
    }

    /** Клиентская пустышка: содержимое приедет синхронизацией контейнера. */
    public static TradeSession clientDummy(int id, String nameA, String nameB) {
        return new TradeSession(id, null, nameA, null, nameB);
    }

    private class TrackingHandler extends ItemStackHandler {
        TrackingHandler() {
            super(SLOTS);
        }

        @Override
        protected void onContentsChanged(int slot) {
            resetConfirm();
        }
    }

    public boolean isA(@Nullable UUID uuid) {
        return uuid != null && uuid.equals(uuidA);
    }

    public ItemStackHandler ownOffer(@Nullable UUID uuid) {
        return isA(uuid) ? offerA : offerB;
    }

    public ItemStackHandler otherOffer(@Nullable UUID uuid) {
        return isA(uuid) ? offerB : offerA;
    }

    public long ownMoney(@Nullable UUID uuid) {
        return isA(uuid) ? moneyA : moneyB;
    }

    public long otherMoney(@Nullable UUID uuid) {
        return isA(uuid) ? moneyB : moneyA;
    }

    public boolean ownConfirm(@Nullable UUID uuid) {
        return isA(uuid) ? confirmA : confirmB;
    }

    public boolean otherConfirm(@Nullable UUID uuid) {
        return isA(uuid) ? confirmB : confirmA;
    }

    public String otherName(@Nullable UUID uuid) {
        return isA(uuid) ? nameB : nameA;
    }

    /** Любое изменение предложения сбрасывает подтверждения — защита от подмены в последний момент. */
    public void resetConfirm() {
        if (confirmA || confirmB) {
            confirmA = false;
            confirmB = false;
        }
        lastChange = System.currentTimeMillis();
    }

    public boolean bothConfirmed() {
        return confirmA && confirmB;
    }

    @Nullable
    public UUID uuidOf(EntityPlayer player) {
        return player == null ? null : player.getUniqueID();
    }

    public boolean involves(@Nullable UUID uuid) {
        return uuid != null && (uuid.equals(uuidA) || uuid.equals(uuidB));
    }
}
