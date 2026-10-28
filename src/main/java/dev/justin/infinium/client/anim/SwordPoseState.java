package dev.justin.infinium.client.anim;

/** Extra per-frame data stamped onto HumanoidRenderState for the third-person sword pose. */
public interface SwordPoseState {
    boolean infinium$swordActive();
    void infinium$setSword(boolean active, SwordMove move, float roll, float swing, float equipLeft, float time);
    SwordMove infinium$swordMove();
    float infinium$swordRoll();
    float infinium$swordSwing();
    float infinium$swordEquip();
    float infinium$swordTime();
}
