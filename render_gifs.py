"""Animated GIF previews per style: front + back player (armor, wings, glow animated as in game) with approximated particles
(python port of ArmorParticles: Singularity rings/outer dust, CyberPsycho on-body events + outward bolts, Astral/Cybernetic motes),
plus every Infinium item icon animated (base icon + cracks_<style> strip, mcmeta frametime). Also tools_all_styles.gif."""
import os, math, random, json, shutil
from PIL import Image, ImageDraw
import render3d_lib as L
E=os.path.abspath('src/main/resources/assets/infinium/textures/entity')+'/'
IT='src/main/resources/assets/infinium/textures/item/'
OUT='previews/gif/'; os.makedirs(OUT,exist_ok=True)
STY={'astral':(40,300),'cybernetic':(40,300),'singularity':(100,100),'cyberpsycho':(80,100)}
ITEMS=['infinium_sword','infinium_pickaxe','infinium_axe','infinium_shovel','infinium_hoe','infinium_helmet','infinium_chestplate','infinium_leggings','infinium_boots']
PSY=[0xB04CFF,0xD24CFF,0xFF4CE0,0xFF7AD9,0xE03CC8,0xFF5FB0]; SING=[0x5A1A9E,0x7A2BD0,0x8E3CE6,0x4A0F86,0x6B23B8]
def rgb(c): return ((c>>16)&255,(c>>8)&255,c&255)
def item_frames(st):
    out={}
    for n in ITEMS:
        base=Image.open(IT+f'{n}.png').convert('RGBA'); strip=Image.open(IT+f'cracks_{st}/{n}.png').convert('RGBA')
        ft=json.load(open(IT+f'cracks_{st}/{n}.png.mcmeta'))['animation']['frametime']
        fr=[]
        for i in range(strip.height//16):
            im=base.copy(); im.alpha_composite(strip.crop((0,i*16,16,i*16+16))); fr.append(im)
        out[n]=(fr,ft)
    return out
def item_at(frs,ms):
    fr,ft=frs; return fr[int(ms/(ft*50))%len(fr)]
class Particles:
    """Body-local block coords: x right(+)/left, y up from feet, z forward(-) / back(+), like the model's -z = front."""
    def __init__(s,st,seed=1): s.st=st; s.r=random.Random(seed); s.live=[]; s.tick=0
    def add(s,x,y,z,c,life,size=1,v=(0,0,0)): s.live.append([x,y,z,c,life,size,v])
    def step(s):
        r=s.r; tc=s.tick; rad=0.3+0.25; h=1.8
        if s.st=='singularity':
            rings=[(0.18,0.15,0.11,0.18),(0.50,0.38,-0.07,-0.12),(0.82,0.22,0.09,0.25),(0.35,1.05,-0.045,0.30),(0.70,1.45,0.035,-0.22)]
            for i,g in enumerate(rings):
                for k in range(2):
                    a=tc*g[2]+k*math.pi+(r.random()-.5)*.2; rr=rad+g[1]+(r.random()-.5)*.08
                    c=0x0E0612 if r.random()<.2 else r.choice(SING)
                    s.add(math.cos(a)*rr,h*g[0]+math.sin(a)*g[3]*rr,math.sin(a)*rr,c,8+r.randrange(6),2 if i>=3 else 1.6)
            for k in range(2):
                a=r.random()*6.283; rr=1.1+r.random()*1.2; c=0x0E0612 if r.random()<.2 else r.choice(SING)
                s.add(math.cos(a)*rr,r.random()*h*1.1,math.sin(a)*rr,c,14,1.8,(-math.sin(a)*.03,.004,math.cos(a)*.03))
        elif s.st=='cyberpsycho':
            for e in range(2+r.randrange(2)):
                c=r.choice(PSY); a=r.random()*6.283; y0=.1+r.random()*h*.85; rr=rad+(r.random()-.4)*.2
                lx,lz=math.cos(a)*rr,math.sin(a)*rr; kind=r.random(); n=5+r.randrange(4) if kind<.45 else (4+r.randrange(3) if kind<.75 else 1+r.randrange(2))
                dx,dy,dz=[(r.random()-.5)*q for q in (.14,.18,.14)]
                for i in range(n):
                    zig=(r.random()-.5)*.12; bow=math.sin(math.pi*i/max(1,n-1))*.12 if .45<=kind<.75 else 0
                    for sg in (-1,1): s.add((lx+dx*i+zig)*sg,y0+dy*i+bow+zig*.5,lz+dz*i+bow,c,3+r.randrange(4),1.5)
            if r.random()<.7:
                for e in range(2 if r.random()<.35 else 1):
                    c=r.choice(PSY); a=r.random()*6.283; y=.2+r.random()*h*.8; r1=1.4+r.random()*.9; n=6+r.randrange(4); ya=(r.random()-.5)*.6
                    for i in range(n):
                        f=i/(n-1); rr=rad+(r1-rad)*f; aa=a+(r.random()-.5)*.35*f
                        for sg in (-1,1): s.add(math.cos(aa)*rr*sg,y+ya*f+(r.random()-.5)*.12,math.sin(aa)*rr,c,3+r.randrange(3),1.8)
        else:
            if tc%8==0:
                a=r.random()*6.283; s.add(math.cos(a)*rad,.1+r.random()*h*.55,math.sin(a)*rad,0x9B4CFF if s.st=='astral' else 0xC04CFF,20,1.6,(0,.01,0))
        for p in s.live:
            p[0]+=p[6][0]; p[1]+=p[6][1]; p[2]+=p[6][2]; p[4]-=1
        s.live=[p for p in s.live if p[4]>0]; s.tick+=1
    def draw(s,im,back,S,scale):
        d=ImageDraw.Draw(im)
        for x,y,z,c,life,size,_ in s.live:
            mx,my,mz=x*16,24-y*16,z*16           # model px; feet at y=24
            if back: mx,mz=-mx,-mz
            if mz>2 and abs(mx)<5 and -8<my<24: continue   # hidden behind the body
            sx,sy=S/2+mx*scale,S/2+(my-12)*scale; q=size*scale*.35
            d.rectangle((sx-q,sy-q,sx+q,sy+q),fill=rgb(c)+(255,))
def texroot(st):
    root=f'/tmp/texroot_{st}/'; shutil.rmtree(root,ignore_errors=True); os.makedirs(root+'entity/infinium_glow')
    os.symlink(E+'equipment',root+'entity/equipment')
    for layer in ('humanoid','humanoid_leggings','wings'): os.symlink(E+f'infinium_glow/{st}/{layer}',root+f'entity/infinium_glow/{layer}')
    return root
def style_gif(st, cache_items):
    F,ms=STY[st]; root=texroot(st); L.T=root+'entity/'; L._TC.clear(); L.FRAMES_HINT[0]=F
    S=300; scale=5.0; P=Particles(st); ticks=ms//50; IS=48
    for _ in range(20): P.step()                 # warm up
    frames=[]
    pose=dict(L.POSES['standing'],wing=(0.35,-1.05))
    for f in range(F):
        canvas=Image.new('RGB',(2*S,S+IS+16),(36,34,42))
        Q=L.build(f,pose)
        for k,(yaw,back) in enumerate(((0.0,False),(math.pi,True))):
            im=L.render(Q,yaw,0.0,0.0,size=S,scale=scale,bg=(36,34,42)); P.draw(im,back,S,scale); canvas.paste(im.convert('RGB'),(k*S,0))
        for i,n in enumerate(ITEMS):
            ic=item_at(cache_items[n],f*ms).resize((IS,IS),Image.NEAREST)
            x=8+i*(IS+18); canvas.paste(ic,(x,S+8),ic)
        ImageDraw.Draw(canvas).text((6,4),st,fill=(230,230,230))
        frames.append(canvas)
        for _ in range(ticks): P.step()
    p=OUT+f'{st}.gif'
    frames[0].save(p,save_all=True,append_images=frames[1:],duration=ms,loop=0,optimize=True)
    return p
ALL={st:item_frames(st) for st in STY}
# per-style player GIFs retired (render_ref_gifs.py makes the reference-layout GIFs)
# tools side by side: 200 ms frames over 120 s = LCM of all item loops (12/12/10/8 s) -> seamless wrap
IS=64; frames=[]
for t in range(600):
    c=Image.new('RGB',(110+len(ITEMS)*(IS+10),4*(IS+12)+10),(36,34,42)); d=ImageDraw.Draw(c)
    for r_,st in enumerate(STY):
        d.text((6,10+r_*(IS+12)+IS//2-6),st,fill=(230,230,230))
        for i,n in enumerate(ITEMS):
            ic=item_at(ALL[st][n],t*200).resize((IS,IS),Image.NEAREST); c.paste(ic,(110+i*(IS+10),10+r_*(IS+12)),ic)
    frames.append(c)
import render_ref_gifs as RG; p=RG.save(frames,'tools_all_styles.gif',200,(frames[0].width,))
print(p, os.path.getsize(p)//1024,'KB')
