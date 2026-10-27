#!/usr/bin/env python3
"""Approximate previews of the mode-change bursts and the per-style ambient armor particles (vanilla generic_* sprites)."""
import math, random, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
SP='/tmp/pt/assets/minecraft/textures/particle/'
GEN=[Image.open(SP+f'generic_{i}.png').convert('RGBA') for i in range(8)]
TX='src/main/resources/assets/infinium/textures/item/'
PURPLE=(0xB0,0x4C,0xFF)
def sprite(idx,col,px):
    s=GEN[max(0,min(7,idx))]; a=np.asarray(s).astype(float)/255
    a[...,0]*=col[0]/255; a[...,1]*=col[1]/255; a[...,2]*=col[2]/255
    return Image.fromarray((a*255).astype(np.uint8)).resize((max(1,px),max(1,px)),Image.NEAREST)
def put(im,spr,x,y): im.alpha_composite(spr,(int(x-spr.width/2),int(y-spr.height/2)))
# ---- bursts (2D, right->x, up->y), shapes = ModeFx.shape ----
def shape(kind,mode):
    if kind=='pickaxe': return [(0.22*math.cos(i*math.pi/3),0.22*math.sin(i*math.pi/3)) for i in range(6)]
    if kind=='shovel': n=mode+2; return [((i-(n-1)/2)*0.14,-0.15) for i in range(n)]
    if kind=='axe': return [(0.15*math.cos(i*1.3),-0.2+i*0.1) for i in range(5)]
    return [(s*(0.15+0.12*i),(0.06 if mode else -0.06)*i) for s in(-1,1) for i in range(4)]
def bursts():
    """Tools: random 6-10 dust around the held tool, random outward velocity (each panel replays 3 different random bursts).
    Wings: fixed 8 dust in two arcs behind the shoulders (unchanged)."""
    font=ImageFont.load_default(size=16); PX=420; out=[]
    panels=[('pickaxe',1,'infinium_pickaxe','Pickaxe: random 6-10 dust'),('shovel',1,'infinium_shovel','Shovel: random 6-10 dust'),
            ('axe',8,'infinium_axe','Axe: random 6-10 dust'),('wings',1,'infinium_chestplate','Wings: On (8 dust, back)')]
    rnd=random.Random(1); CYC=24
    def make(k,m):
        if k=='wings': return [(dx,dy,0,0,int(8/(rnd.random()*0.8+0.2)),0.6+0.4*rnd.random()) for dx,dy in shape(k,m)]
        ps=[]
        for _ in range(rnd.randint(6,10)):
            a=rnd.uniform(0,2*math.pi); r0=rnd.uniform(0.05,0.25); sp=rnd.uniform(0.01,0.06)
            ps.append((r0*math.cos(a),r0*math.sin(a),sp*math.cos(a),sp*math.sin(a)+rnd.uniform(0,0.02),int(8/(rnd.random()*0.8+0.2)),0.6+0.4*rnd.random()))
        return ps
    bursts_={k:[make(k,m) for _ in range(3)] for k,m,_,_ in panels}
    for f in range(3*CYC):
        G=Image.new('RGBA',(4*220,250),(118,118,124,255)); d=ImageDraw.Draw(G); c=f//CYC
        for i,(k,m,icon,lab) in enumerate(panels):
            cx=i*220+110; cy=125
            ic=Image.open(TX+icon+'.png').convert('RGBA').resize((128,128),Image.NEAREST); G.alpha_composite(ic,(cx-64,cy-64))
            age=f%CYC-2
            if age>=0:
                for dx,dy,vx,vy,life,br in bursts_[k][c]:
                    if age>=life: continue
                    col=tuple(int(cc*br) for cc in PURPLE); idx=7-int(8*age/life)
                    x=dx+vx*age*0.9; y=dy+vy*age*0.9          # dust drag ~0.96/tick, roughly linear here
                    put(G,sprite(idx,col,int(22*(1-0.3*age/life))),cx+x*PX,cy-y*PX)
            d.text((i*220+6,228),lab,fill=(20,20,24),font=font)
        out.append(G.convert('RGB'))
    out[0].save('preview/particles/bursts.gif',save_all=True,append_images=out[1:],duration=50,loop=0,optimize=True)
# ---- ambient: 4 styles, front view of the full set, particles simulated per tick ----
def ambient():
    import gen_variants as gv, render3d_lib as r3, gen_helmets as gh
    STY=[('Astral','v08'),('Cybernetic','v09'),('Singularity','v11b'),('CyberPsycho','v12b')]
    font=ImageFont.load_default(size=20); T=60; cols=[]
    for name,v in STY:
        cfg=gv.V[v]; gv.F=cfg.get('F',40); gv.FRAME_MS=cfg.get('ms',300); F=gv.F
        r3.T=f'build_variants/{v}/textures/entity/'; r3._TC.clear(); gh.TINT=None; r3.FRAMES_HINT[0]=F
        rnd=random.Random(v); ps=[]; frames=[]
        for tick in range(-80,T):
            tc=tick+1000; rad=0.3+0.25
            if name=='Singularity':
                if tc%3==0:
                    a=tc*0.045+((tc//3)%3)*(2*math.pi/3)+(rnd.random()-.5)*.3; rr=rad+.15+rnd.random()*.25
                    ps.append(dict(p=[math.cos(a)*rr,0.81+(rnd.random()-.5)*.15,math.sin(a)*rr],v=[-math.sin(a)*.04,0,math.cos(a)*.04],age=0,life=40+rnd.randint(0,10),k='portal',br=rnd.random()))
            elif name=='CyberPsycho':
                if rnd.randrange(4)==0:
                    COLS=[(0xB0,0x4C,0xFF),(0xD2,0x4C,0xFF),(0xFF,0x4C,0xE0),(0xFF,0x7A,0xD9),(0x9B,0x3B,0xFF)]
                    for _ in range(2 if rnd.random()<0.2 else 1):
                        col=rnd.choice(COLS); a=rnd.random()*2*math.pi; y=0.1+rnd.random()*1.53; rr=rad+(rnd.random()-.4)*.2
                        lx,lz=math.cos(a)*rr,math.sin(a)*rr; n=4+rnd.randrange(3) if rnd.random()<0.4 else 1+rnd.randrange(2)
                        d=[(rnd.random()-.5)*.12 for _ in range(3)]; life=max(1,int(8/(rnd.random()*.8+.2)*0.45))
                        for i in range(n):
                            zig=(1 if i%2==0 else -1)*0.05
                            ox=lx+d[0]*i+zig*math.sin(a); oy=y+d[1]*i+(zig if n>2 else 0); oz=lz+d[2]*i-zig*math.cos(a)
                            for s in (-1,1): ps.append(dict(p=[s*ox,oy,oz],v=[0,0,0],age=0,life=life,k='spark',br=1,col=col))
            elif tc%8==0:
                a=rnd.random()*2*math.pi
                ps.append(dict(p=[math.cos(a)*rad,0.1+rnd.random()*0.99,math.sin(a)*rad],v=[0,0.01,0],age=0,life=40+rnd.randint(0,10),k='portal',br=rnd.random()))
            for q in ps:
                q['age']+=1
                q['p']=[a+b for a,b in zip(q['p'],q['v'])]
            ps=[q for q in ps if q['age']<q['life']]
            if tick<0: continue
            fig=gv.figure((tick*50//gv.FRAME_MS)%F,r3.POSES['standing'],0.0).convert('RGBA')
            a=np.asarray(fig); fg=np.any(a[...,:3]!=(118,118,124),axis=2); ys,xs=np.nonzero(fg)
            top,bot=ys.min(),ys.max(); cx=(xs.min()+xs.max())/2; ppb=(bot-top)/2.0
            for q in sorted(ps,key=lambda q:q['p'][2]):
                x,y,z=q['p']; sx=cx-x*ppb; sy=bot-y*ppb; f=q['age']/q['life']
                if z>0 and 0<=int(sy)<fg.shape[0] and 0<=int(sx)<fg.shape[1] and fg[int(sy),int(sx)]: continue   # behind the body
                if q['k']=='portal': col=tuple(int(c*(q['br']*0.6+0.4)) for c in (230,77,255)); spr=sprite(int(8*(1-f)) if True else 0,col,int(26*(1-f*f)+4))
                else: spr=sprite(7-int(8*f),q['col'],12)
                put(fig,spr,sx,sy)
            d=ImageDraw.Draw(fig); d.text((8,6),name,fill=(20,20,24),font=font); frames.append(fig.convert('RGB'))
        cols.append(frames); print('ambient',name,flush=True)
    out=[]
    for i in range(T):
        G=Image.new('RGB',(4*400,430)); [G.paste(cols[c][i],(c*400,0)) for c in range(4)]; out.append(G.resize((G.width*3//5,G.height*3//5)))
    out[0].save('preview/particles/ambient.gif',save_all=True,append_images=out[1:],duration=50,loop=0,optimize=True)
if __name__=='__main__':
    os.makedirs('preview/particles',exist_ok=True); bursts(); ambient(); print('ok')
