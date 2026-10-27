#!/usr/bin/env python3
"""Item textures for the four style smithing templates: the vanilla netherite upgrade template recoloured
(shading kept via luminance) with a per-style accent. Astral: violet/star white; Cybernetic: violet + cyan circuit;
Singularity: deep indigo with magenta core; CyberPsycho: hot magenta + cyan."""
import colorsys, os
from PIL import Image
SRC='ref/mc/assets/minecraft/textures/item/netherite_upgrade_smithing_template.png'
OUT='src/main/resources/assets/infinium/textures/item/'
PAL={'astral':((0.76,0.55),(0.13,0.10)),'cybernetic':((0.74,0.55),(0.90,0.80)),
     'singularity':((0.765,0.75),(0.86,0.95)),'cyberpsycho':((0.86,0.80),(0.93,0.95))}
def main():
    src=Image.open(SRC).convert('RGBA'); W,H=src.size
    for sid,((h1,s1),(h2,s2)) in PAL.items():
        im=src.copy(); p=im.load()
        for y in range(H):
            for x in range(W):
                r,g,b,a=p[x,y]
                if not a: continue
                hh,ss,vv=colorsys.rgb_to_hsv(r/255,g/255,b/255)
                accent=ss>0.25 and (x+y)%2==0 and vv>0.45              # vanilla coloured bits -> style accent
                hue,sat=(h2,s2) if accent else (h1,s1*min(1.0,0.4+vv))
                rr,gg,bb=colorsys.hsv_to_rgb(hue,sat,min(1.0,vv*1.15+0.04))
                p[x,y]=(int(rr*255),int(gg*255),int(bb*255),a)
        os.makedirs(OUT,exist_ok=True); im.save(OUT+f'{sid}_upgrade_smithing_template.png')
    print('templates ok')
if __name__=='__main__': main()
