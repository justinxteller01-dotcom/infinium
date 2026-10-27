package dev.justin.infinium;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntList;

/** Reusable elytra boost: identical to a flight-duration-1 rocket with no stars, never consumed, no cooldown. */
public class NetheriteRocketItem extends Item {
    public NetheriteRocketItem(Properties p) { super(p); }

    /** Netherite burst: dark grey/black + deep brown, fading to ember/dull-gold sparks. One large ball + trail
     *  (no twinkle) keeps the per-burst particle count bounded (vanilla large-ball cost). Deals no damage (see FireworkRocketEntityMixin). */
    public static final FireworkExplosion BURST = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
        IntList.of(0x6A1A8A, 0xA02CC8, 0x2A0A3A), IntList.of(0xD040A8, 0x8A2AD0), true, false);

    // Not gliding: right-click a block launches a decorative firework like vanilla, but the item is never consumed.
    @Override public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && player.isFallFlying()) return InteractionResult.PASS;
        if (ctx.getLevel() instanceof ServerLevel sl) {
            ItemStack show = ctx.getItemInHand().copyWithCount(1);
            show.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(BURST)));
            Vec3 c = ctx.getClickLocation(); Direction d = ctx.getClickedFace();
            Projectile.spawnProjectile(new FireworkRocketEntity(sl, player, c.x + d.getStepX() * 0.15, c.y + d.getStepY() * 0.15, c.z + d.getStepZ() * 0.15, show), sl, show);
        }
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isFallFlying()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) {
            // Easy Elytra Takeoff (booster path) spawns its own rocket from this stack, then calls use(): no second boost (section 11)
            if (!level.getEntitiesOfClass(FireworkRocketEntity.class, player.getBoundingBox().inflate(2),
                    r -> r.getOwner() == player && r.tickCount == 0).isEmpty()) return InteractionResult.SUCCESS;
            if (player.dropAllLeashConnections(null)) level.playSound(null, player, SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            ItemStack boost = stack.copyWithCount(1);
            boost.set(DataComponents.FIREWORKS, new Fireworks(1, List.of()));
            Projectile.spawnProjectile(new FireworkRocketEntity(level, boost, player), sl, boost);
            player.getCooldowns().addCooldown(stack, 20); // Standard 1s cooldown
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
