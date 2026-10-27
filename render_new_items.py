#!/usr/bin/env python3
"""previews/gif/realtime/new_items.gif: fragments, crystal, ingot, 4 templates at real time (50 ms = 1 tick).
Length 120 s = LCM of the loops (12 s, 8 s, 10 s) -> seamless; identical frames merged by the encoder."""
import json, os
from PIL import Image, ImageDraw, ImageFont
import render_ref_gifs as RG
TX='src/main/resources/assets/infinium/textures/item/'
ALLN=['strange_fragments','spacetime_crystal','infinium_ingot']+[s+'_upgrade_smithing_template' for s in ('astral','cybernetic','singularity','cyberpsycho')]
N=ALLN[:3]
LAB=['Strange Fragments','Spacetime Crystal','Infinium Ingot','Astral tpl','Cybernetic tpl','Singularity tpl','CyberPsycho tpl']
def frames(n):
    im=Image.open(TX+n+'.png').convert('RGBA'); mm=TX+n+'.png.mcmeta'
    ft=json.load(open(mm))['animation'].get('frametime',1) if os.path.exists(mm) else 1
    return [im.crop((0,16*i,16,16*i+16)) for i in range(im.height//16)],ft
FR=[frames(n) for n in N]; S=80; font=ImageFont.load_default(size=11); out=[]
for t in range(480):   # 24 s = LCM of 12 s / 8 s / 4.8 s
    C=Image.new('RGB',(len(N)*(S+20)+10,S+34),(118,118,124)); d=ImageDraw.Draw(C)
    for i,(fr,ft) in enumerate(FR):
        ic=fr[(t//ft)%len(fr)].resize((S,S),Image.NEAREST); C.paste(ic,(10+i*(S+20),6),ic); d.text((10+i*(S+20),S+12),LAB[i],fill=(20,20,24),font=font)
    out.append(C)
ST=Image.new('RGBA',(len(ALLN)*72,72),(118,118,124,255))
for i,n in enumerate(ALLN):
    ic=frames(n)[0][len(frames(n)[0])//3].resize((64,64),Image.NEAREST); ST.alpha_composite(ic,(4+72*i,4))
ST.convert('RGB').save('previews/new_items_still.png')
p=RG.save(out,'realtime/new_items.gif',50,(out[0].width,)); print(p,os.path.getsize(p)//1024,'KB')
