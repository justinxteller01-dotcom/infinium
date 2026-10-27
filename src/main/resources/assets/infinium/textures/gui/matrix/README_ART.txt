M5 placeholder matrix perk logos (generated with PIL). One file per perk kind (CrystalCharge.ORDER):
noconsume, stacks, regen, reach, jump, slow, mining, attack,
plus the ability chips warp and xray_pulse (M6; file name = ability id path).
Format: PNG vertical strip, 8 square frames top to bottom (64x512 now; any square size works, keep 8 frames or change
MatrixLogo.FRAMES). Frames play at ~110 ms each while the perk is lit; frame 0 is the still frame used for
"lights off" (dimmed in code) and locked (dark + padlock drawn in code). Code also adds a halo + gentle scale pulse.
Final art should be a bigger, fancier, animated version of the matching HUD icon; keep the transparent background.
