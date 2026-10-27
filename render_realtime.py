#!/usr/bin/env python3
"""Real-time previews (50 ms = 1 game tick per GIF frame). One GIF per style, length = that style's own loop
(astral/cybernetic 12 s, singularity 10 s, cyberpsycho 8 s) -> seamless. Front + back (wings spread) with ambient particles
(python port of ArmorParticles, periodic emission) + all 9 item icons at their mcmeta frametime. previews/gif/realtime/<style>.gif"""
import os, json, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import render_ref_gifs as RG, gen_variants as gv, render3d_lib as r3, gen_particles as gp
IT='src/main/resources/assets/infinium/textures/item/'; OUT='previews/gif/realtime/'; os.makedirs(OUT,exist_ok=True)
ITEMS=['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots']
NAME={'astral':'Astral','cybernetic':'Cybernetic','singularity':'Singularity','cyberpsycho':'CyberPsycho'}
def icons(st):
    out={}
    for n in ITEMS:
        base=Image.open(IT+f'infinium_{n}.png').convert('RGBA'); sh=Image.open(IT+f'cracks_{st}/infinium_{n}.png').convert('RGBA')
        ft=json.load(open(IT+f'cracks_{st}/infinium_{n}.png.mcmeta'))['animation']['frametime']; fr=[]
        for i in range(sh.height//16): im=base.copy(); im.alpha_composite(sh.crop((0,16*i,16,16*i+16))); fr.append(im)
        out[n]=(fr,ft)
    return out
def render(st):
    RG.use(st); F,ms=gv.F,gv.FRAME_MS; P=F*ms; TT=P//50; ic=icons(st); font=ImageFont.load_default(size=14)
    for n,(fr,ft) in ic.items(): assert (len(fr)*ft*50)==P or P%(len(fr)*ft*50)==0,(n,len(fr),ft)
    figc={}; ps=[]; frames=[]; S=0.5
    for tick in range(-TT,TT):
        RG.emit(NAME[st],tick,random.Random(f'{st}/{tick%TT}'),ps)
        for q in ps: q['age']+=1; q['p']=[a+b for a,b in zip(q['p'],q['v'])]
        ps=[q for q in ps if q['age']<q['life']]
        if tick<0: continue
        fi=(tick*50//ms)%F
        if fi not in figc: figc[fi]=[gv.figure(fi,r3.POSES['on'] if 'on' in r3.POSES else r3.POSES['standing'],y).convert('RGBA') for y in (0.0,math.pi)]
        C=Image.new('RGB',(400,262),(118,118,124))
        for k,yaw in enumerate((0.0,math.pi)):
            fig=figc[fi][k].copy(); a=np.asarray(fig); fg=np.any(a[...,:3]!=(118,118,124),axis=2); ys,xs=np.nonzero(fg)
            bot=ys.max(); cx=(xs.min()+xs.max())/2; ppb=(bot-ys.min())/2.0
            for q in sorted(ps,key=lambda q:q['p'][2]*(1 if k==0 else -1)):
                x,y,z=q['p']
                if k: x,z=-x,-z
                sx=cx-x*ppb; sy=bot-y*ppb; f=q['age']/q['life']
                if z>0 and 0<=int(sy)<fg.shape[0] and 0<=int(sx)<fg.shape[1] and fg[int(sy),int(sx)]: continue
                if q['k']=='portal': col=tuple(int(c*(q['br']*0.6+0.4)) for c in (230,77,255)); spr=gp.sprite(int(8*(1-f)),col,int(26*(1-f*f)+4))
                else: spr=gp.sprite(7-int(8*f),q['col'],16 if q.get('big') else 12)
                gp.put(fig,spr,sx,sy)
            C.paste(fig.convert('RGB').resize((200,215),Image.LANCZOS),(k*200,0))
        for i,n in enumerate(ITEMS):
            fr,ft=ic[n]; im=fr[(tick//ft)%len(fr)].resize((32,32),Image.NEAREST); C.paste(im,(14+i*42,222),im)
        ImageDraw.Draw(C).text((6,4),f'{NAME[st]}  (real time)',fill=(20,20,24),font=font); frames.append(C)
    p=RG.save(frames,f'realtime/{st}.gif',50,(400,360,320)); return p
if __name__=='__main__':
    for st in NAME: p=render(st); print(p,os.path.getsize(p)//1024,'KB',flush=True)
