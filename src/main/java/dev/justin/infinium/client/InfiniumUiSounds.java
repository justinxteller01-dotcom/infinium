package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.ModSounds;
import dev.justin.infinium.Perk;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;

/** One Infinium UI sound at a time. A new click cuts the previous one off. Also swallows one vanilla toast chime. */
public final class InfiniumUiSounds {
    private static SoundInstance current;
    private static long currentSetSoundUntil;
    private static long toastUntil;

    public static void play(SoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || event == null) return;
        var manager = mc.getSoundManager();
        if (current != null) manager.stop(current);
        float vol = 0.8F * InfiniumClientConfig.soundVolumeMaster() * InfiniumClientConfig.soundVolumeEquip();
        SimpleSoundInstance next = SimpleSoundInstance.forUI(event, 1.0F, vol);
        current = next;
        manager.play(next);
    }

    /** Called from SoundManagerMuteMixin when any sound starts. Cuts off previous Infinium equip/crystal sound; full-set sounds override individual equip sounds. */
    public static boolean onSoundStarting(net.minecraft.client.sounds.SoundManager manager, SoundInstance sound) {
        if (sound == null || sound.getIdentifier() == null) return false;
        if (!"infinium".equals(sound.getIdentifier().getNamespace())) return false;
        String path = sound.getIdentifier().getPath();
        boolean isSetSound = path.endsWith("_set_equip");
        boolean isPieceEquip = path.endsWith("_equip") && !isSetSound;
        boolean isCrystal = path.startsWith("crystal.");
        boolean isCrystalMilestone = path.endsWith(".perk") || path.endsWith(".full");
        long now = System.currentTimeMillis();
        if ((isPieceEquip || isSetSound) && now < currentSetSoundUntil) {
            return true;   // suppress duplicate full-set sound and piece equip sound during fanfare window
        }
        if (isSetSound || isPieceEquip || isCrystal) {
            if (current != null && current != sound) {
                try { manager.stop(current); } catch (Throwable ignored) {}
            }
            current = sound;
            if (isSetSound) {
                currentSetSoundUntil = now + 2500L;
            } else if (isCrystalMilestone) {
                currentSetSoundUntil = now + 1200L;
            }
        }
        return false;
    }

    /** Set from the advancement toast constructor when the advancement is an Infinium full set. */
    public static void expectToastSilence() { toastUntil = System.currentTimeMillis() + 500L; }

    /** True once, for the next toast sound inside the window. */
    public static boolean consumeToast(SoundInstance sound) {
        if (toastUntil == 0L || System.currentTimeMillis() > toastUntil) return false;
        if (sound == null || sound.getIdentifier() == null) return false;
        String path = sound.getIdentifier().getPath();
        if (!path.contains("toast")) return false;
        toastUntil = 0L;
        return true;
    }

    private static long lastNs;

    /** Pickup/place sound for a slot click. */
    public static void slotClick(AbstractContainerMenu menu, Slot slot) {
        if (menu == null) return;
        long now = System.nanoTime();
        if (now - lastNs < 2_000_000L) return;
        ItemStack carried = menu.getCarried();
        ItemStack in = slot != null ? slot.getItem() : ItemStack.EMPTY;
        Holder<SoundEvent> s = null;
        if (!carried.isEmpty() && (slot == null || slot.mayPlace(carried))) {
            s = sound(carried, 1);
        } else if (!in.isEmpty()) {
            s = sound(in, carried.isEmpty() ? 0 : 1);
        }
        if (s == null) return;
        lastNs = now;
        play(s.value());
    }

    private static Holder<SoundEvent> sound(ItemStack st, int kind) {
        if (Infinium.isArmor(st)) return ModSounds.ARMOR[Math.max(0, Perk.family(st))][kind];
        if (Infinium.isTool(st)) return kind == 0 ? ModSounds.TOOL_PICKUP : ModSounds.TOOL_PLACE;
        return null;
    }

    private InfiniumUiSounds() {}
}
