package com.ecoserv.tile;

import com.ecoserv.subworld.GlitchStorage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ГЛИТЧ-сундук (п. 17): какие виды пропавших предметов в нём показываются (настраивает палка
 * GLITCH SELECT). Содержимое своё у каждого игрока — хранилище ГЛИТЧ.
 */
public class TileGlitchChest extends TileEntity {

    private final Set<String> categories = new LinkedHashSet<String>();

    public TileGlitchChest() {
        // по умолчанию — «уничтоженные блоки и предметы» (глобальная правка подмира)
        categories.add(GlitchStorage.EDIT);
    }

    public Set<String> categories() {
        return categories;
    }

    public void toggle(String category) {
        if (!categories.remove(category)) {
            categories.add(category);
        }
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("categories", 9)) {
            categories.clear();
            NBTTagList list = nbt.getTagList("categories", 8);
            for (int i = 0; i < list.tagCount(); i++) {
                categories.add(list.getStringTagAt(i));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList list = new NBTTagList();
        for (String c : categories) {
            list.appendTag(new NBTTagString(c));
        }
        nbt.setTag("categories", list);
        return nbt;
    }
}
