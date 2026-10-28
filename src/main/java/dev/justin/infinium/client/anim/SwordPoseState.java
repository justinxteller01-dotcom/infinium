package dev.justin.infinium.client.anim;

/** Extra per-frame data stamped onto HumanoidRenderState for the third-person sword pose. */
public interface SwordPoseState {
    boolean infinium$swordActive();
    void infinium$setSword(boolean active, float swing, float side, float equipLeft, float time);
    float infinium$swordSwing();
    float infinium$swordSide();
    float infinium$swordEquip();
    float infinium$swordTime();
}
