package dev.justin.infinium.loot;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * R6d: persistent "naturally generated loot container" marker stored ON the block entity (Fabric data attachment, saved in its
 * NBT). Set only while the container still has a loot table (chunk load / loot roll). A player-placed container is a new block
 * entity without it, so position-keyed tracking can never touch it. Lootr containers count as natural (Lootr only converts loot-table chests).
 */
public final class NaturalMark {
    // javap fabric-data-attachment-api-v1 2.2.19: AttachmentRegistry.createPersistent(Identifier, Codec)
    @SuppressWarnings("deprecation")
    public static final AttachmentType<Boolean> TYPE = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("infinium", "natural_loot"), Codec.BOOL);

    public static void init() {}

    /** R6g: sets the attachment only (no BlockEntity.setChanged: that does a Level chunk lookup and deadlocked chunk LOAD
     *  under C2ME). Returns true if newly marked; the caller marks the already-loaded chunk unsaved (LevelChunk.markUnsaved, javap 26.2). */
    public static boolean mark(BlockEntity be) {
        if (be == null || isMarked(be)) return false;
        ((AttachmentTarget) (Object) be).setAttached(TYPE, Boolean.TRUE);
        return true;
    }

    public static boolean isMarked(BlockEntity be) {
        return be != null && Boolean.TRUE.equals(((AttachmentTarget) (Object) be).getAttached(TYPE));
    }

    /** True only for containers allowed to be cleared / re-rolled / refilled. */
    public static boolean natural(BlockEntity be) {
        return be != null && (LootrBridge.isLootr(be) || isMarked(be));
    }

    private NaturalMark() {}
}
