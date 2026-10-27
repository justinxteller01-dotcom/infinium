package dev.justin.infinium.client;

/** Duck interface on HumanoidRenderState: wing open progress for the Infinium fold (-1 = not an Infinium fold, leave vanilla). */
public interface WingFoldState {
    float infinium$getFold();
    void infinium$setFold(float t);
    float infinium$getFlight();
    void infinium$setFlight(float t);
}
