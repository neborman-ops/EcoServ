package com.ecoserv.client;

import com.ecoserv.network.packet.PacketExtraState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Клиентская копия данных новых механик (вклад, зарплата, история). */
@SideOnly(Side.CLIENT)
public final class ClientHooksExtra {
   public static volatile PacketExtraState state = initial();
   public static volatile long receivedAt;
   public static int revision;

   private ClientHooksExtra() {
   }

   private static PacketExtraState initial() {
      PacketExtraState s = new PacketExtraState();
      s.bankEnabled = true;
      return s;
   }

   public static void onExtraState(final PacketExtraState message) {
      Minecraft.getMinecraft().addScheduledTask(new Runnable() {
         @Override
         public void run() {
            ClientHooksExtra.state = message;
            ClientHooksExtra.receivedAt = System.currentTimeMillis();
            ClientHooksExtra.revision++;
         }
      });
   }

   public static List<String[]> history() {
      PacketExtraState s = state;
      List<String[]> out = new ArrayList<>();

      for (int i = 0; i < s.txTimeAmount.size(); i++) {
         out.add(new String[]{String.valueOf(s.txTimeAmount.get(i)[0]), String.valueOf(s.txTimeAmount.get(i)[1]), s.txNotes.get(i)});
      }

      return out;
   }
}
