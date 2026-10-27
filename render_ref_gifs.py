#!/usr/bin/env python3
"""GIFs in the SAME layouts as the user's reference GIFs, from the SHIPPED textures:
 previews/gif/{cyberpsycho,singularity}_grid.gif (3x4 grid, gen_variants._gif_frame), previews/gif/all_styles_front.gif
 (4-style front strip with ambient particles = python port of the current ArmorParticles), compare_*.png."""
import os, sys, math, random, shutil
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import gen_variants as gv, render3d_lib as r3, gen_helmets as gh, gen_particles as gp
E='src/main/resources/assets/infinium/textures/entity/'; OUT='previews/gif/'; os.makedirs(OUT,exist_ok=True)
CLK={'astral':(40,300),'cybernetic':(40,300),'singularity':(100,100),'cyberpsycho':(80,100)}
def root(st):
    T=f'/tmp/shiproot_{st}/'; shutil.rmtree(T,ignore_errors=True); os.makedirs(T+'entity/infinium_glow')
    os.symlink(os.path.abspath(E+'equipment'),T+'entity/equipment')
    for l in ('humanoid','humanoid_leggings','wings'): os.symlink(os.path.abspath(E+f'infinium_glow/{st}/{l}'),T+f'entity/infinium_glow/{l}')
    return T
def use(st):
    F,ms=CLK[st]; gv.F=F; gv.FRAME_MS=ms; r3.T=root(st)+'entity/'; r3._TC.clear(); gh.TINT=None; r3.FRAMES_HINT[0]=F
def grid(st,label):
    use(st); import multiprocessing as mp
    with mp.get_context('fork').Pool(min(8,os.cpu_count() or 1)) as pool: fr=pool.map(gv._gif_frame,[(label,f) for f in range(gv.F)])
    p=OUT+f'{st}_grid.gif'; fr[0].save(p,save_all=True,append_images=fr[1:],duration=gv.FRAME_MS,loop=0,optimize=True); return p,fr
RATE=1.75; PSY=[(0xB0,0x4C,0xFF),(0xD2,0x4C,0xFF),(0xFF,0x4C,0xE0),(0xFF,0x7A,0xD9),(0xE0,0x3C,0xC8),(0xFF,0x5F,0xB0)]
def emit(name,tc,rnd,ps):
    rad=0.55
    if name=='Singularity':
        for _pn in range(2 if rnd.random()<0.62 else 1):
            band=rnd.randrange(3); hy=(0.30,0.81,1.30)[band]; sp=(0.035,-0.045,0.04)[band]
            a=tc*abs(sp)+band*2*math.pi/3+(rnd.random()-.5)*.6; rr=rad+.15+rnd.random()*.25; vs=math.copysign(.04,sp)
            blk=rnd.random()<0.2; SG=[(0x32,0x09,0x60),(0x44,0x0D,0x83),(0x62,0x1C,0xAC),(0x75,0x26,0xC8),(0x85,0x2F,0xE3),(0x9B,0x41,0xF0),(0xA8,0x4E,0xF4)]; pk=rnd.random()<0.075
            ps.append(dict(p=[math.cos(a)*rr,hy+(rnd.random()-.5)*.15,math.sin(a)*rr],v=[-math.sin(a)*vs,0,math.cos(a)*vs],age=0,
                life=int(8/(rnd.random()*.8+.2)*0.7)+1,k='spark',br=1,col=(14,6,18) if blk else ((0xB7,0x3F,0x9B) if pk else rnd.choice(SG)),big=True))   # Java: purple/black dust 0.7
    elif name=='CyberPsycho':
        if rnd.random()<2.45/4:
            streak=rnd.random()<0.45
            for _ in range(2 if rnd.random()<0.2 else 1):
                col=rnd.choice(PSY); a=rnd.random()*2*math.pi; y=0.1+rnd.random()*1.53; rr=rad+(rnd.random()-.4)*.2
                lx,lz=math.cos(a)*rr,math.sin(a)*rr; n=5+rnd.randrange(3) if streak else 1+rnd.randrange(2)
                d=[(rnd.random()-.5)*.12 for _ in range(3)]; life=max(1,int(8/(rnd.random()*.8+.2)*0.45))
                for i in range(n):
                    zig=(1 if i%2==0 else -1)*0.05
                    ox=lx+d[0]*i+zig*math.sin(a); oy=y+d[1]*i+(zig if n>2 else 0); oz=lz+d[2]*i-zig*math.cos(a)
                    if oy>1.30 and -0.85<oz<0 and abs(ox)<0.35: continue   # face box (front = -z here)
                    for s in (-1,1): ps.append(dict(p=[s*ox,oy,oz],v=[0,0,0],age=0,life=life,k='spark',br=1,col=col,big=streak))
    elif name=='Cybernetic':   # lessened glitch only, legs/feet + chest band
        if rnd.random()<1.7/20:
            streak=rnd.random()<0.4; col=rnd.choice([(0xB0,0x48,0xF0),(0xC8,0x54,0xFF),(0xC8,0x50,0xE8),(0xDC,0x54,0xD0)])
            a=rnd.random()*2*math.pi; y=(0.08+rnd.random()*0.6) if rnd.random()<0.5 else (0.8+rnd.random()*0.45); rr=rad+(rnd.random()-.4)*.2
            lx,lz=math.cos(a)*rr,math.sin(a)*rr; n=4+rnd.randrange(3) if streak else 1+rnd.randrange(2)
            d=[(rnd.random()-.5)*.12 for _ in range(3)]; life=max(1,int(8/(rnd.random()*.8+.2)*0.35))
            for i in range(n):
                zig=(1 if i%2==0 else -1)*0.05
                ox=lx+d[0]*i+zig*math.sin(a); oy=y+d[1]*i+(zig if n>2 else 0); oz=lz+d[2]*i-zig*math.cos(a)
                for s in (-1,1): ps.append(dict(p=[s*ox,oy,oz],v=[0,0,0],age=0,life=life,k='spark',br=1,col=col,big=streak))
    elif rnd.random()<RATE/8:
        a=rnd.random()*2*math.pi
        ps.append(dict(p=[math.cos(a)*rad,0.1+rnd.random()*0.99,math.sin(a)*rad],v=[0,0.01,0],age=0,life=40+rnd.randint(0,10),k='portal',br=rnd.random()))
LOOP_MS=120000   # LCM of the style clocks 12 s / 12 s / 10 s / 8 s -> every texture animation wraps exactly
STEP_MS=300; TT=LOOP_MS//50
def strip():
    """Seamless: GIF length = LCM of all texture periods; particle emission is periodic (rng per tick%TT, sim warmed over a full loop)."""
    font=ImageFont.load_default(size=20); N=LOOP_MS//STEP_MS; cols=[]
    for name,st in (('Astral','astral'),('Cybernetic','cybernetic'),('Singularity','singularity'),('CyberPsycho','cyberpsycho')):
        use(st); F=gv.F; ps=[]; frames=[]; figc={}
        for tick in range(-TT,TT):
            emit(name,tick,random.Random(f'{st}/{tick%TT}'),ps)
            for q in ps: q['age']+=1; q['p']=[a+b for a,b in zip(q['p'],q['v'])]
            ps=[q for q in ps if q['age']<q['life']]
            if tick<0 or (tick*50)%STEP_MS: continue
            fi=(tick*50//gv.FRAME_MS)%F
            if fi not in figc: figc[fi]=gv.figure(fi,r3.POSES['standing'],0.0).convert('RGBA')
            fig=figc[fi].copy()
            a=np.asarray(fig); fg=np.any(a[...,:3]!=(118,118,124),axis=2); ys,xs=np.nonzero(fg)
            top,bot=ys.min(),ys.max(); cx=(xs.min()+xs.max())/2; ppb=(bot-top)/2.0
            for q in sorted(ps,key=lambda q:q['p'][2]):
                x,y,z=q['p']; sx=cx-x*ppb; sy=bot-y*ppb; f=q['age']/q['life']
                if z>0 and 0<=int(sy)<fg.shape[0] and 0<=int(sx)<fg.shape[1] and fg[int(sy),int(sx)]: continue
                if q['k']=='portal': col=tuple(int(c*(q['br']*0.6+0.4)) for c in (230,77,255)); spr=gp.sprite(int(8*(1-f)),col,int(26*(1-f*f)+4))
                else: spr=gp.sprite(7-int(8*f),q['col'],16 if q.get('big') else 12)
                gp.put(fig,spr,sx,sy)
            ImageDraw.Draw(fig).text((8,6),name,fill=(20,20,24),font=font); frames.append(fig.convert('RGB'))
        cols.append(frames); print('strip',name,len(frames),flush=True)
    out=[]
    for i in range(N):
        G=Image.new('RGB',(1600,430)); [G.paste(cols[c][i],(c*400,0)) for c in range(4)]; out.append(G.resize((960,258)))
    return save(out,'all_styles_front.gif',STEP_MS),out
def save(frames,name,ms,widths=(960,800,720,640)):
    """global palette (no per-frame palette flicker), shrink until < 7.8 MB"""
    for w in widths:
        fr=[f.resize((w,round(f.height*w/f.width)),Image.LANCZOS) if f.width!=w else f for f in frames]
        k=max(1,len(fr)//8); M=Image.new('RGB',(fr[0].width,fr[0].height*len(fr[::k])))
        for i,x in enumerate(fr[::k]): M.paste(x,(0,i*fr[0].height))
        pal=M.quantize(128,dither=Image.Dither.NONE)
        q=[f.quantize(palette=pal,dither=Image.Dither.NONE) for f in fr]
        p=OUT+name; q[0].save(p,save_all=True,append_images=q[1:],duration=ms,loop=0,optimize=True,disposal=1)
        if os.path.getsize(p)<7.8e6: return p
    return p
if __name__=='__main__':
    A='/home/box/agent-data/agents/b879e59c-ae4f-48ae-83a0-7e12cdf78c37/attachments/'
    REF={'cyberpsycho':A+'2b61eccce65e0f06ccd5f7350f8f23fe415e6991abdd51c589b057e0babbe824.gif','singularity':A+'799640b67dccf2a106371249b16fdee9168d73cc3478eb74a1cfaebf7f2f6fde.gif','strip':A+'880d010f32902d3ed054832155f83e27f86cb7cab86cc5dee4b089187f0719ea.gif'}
    def cmp(refp,new,i,name):
        r=Image.open(refp); r.seek(i); r=r.convert('RGB'); n=new[i]
        C=Image.new('RGB',(r.width+n.width+10,max(r.height,n.height)+30),(40,40,44)); d=ImageDraw.Draw(C)
        C.paste(r,(0,30)); C.paste(n,(r.width+10,30)); f=ImageFont.load_default(size=20)
        d.text((6,4),f'REFERENCE frame {i}',fill=(240,240,240),font=f); d.text((r.width+16,4),f'NEW frame {i}',fill=(240,240,240),font=f)
        C.save(OUT+f'compare_{name}.png')
    for st,lab in (('cyberpsycho','v12c'),('singularity','v11b')):
        p,fr=grid(st,lab); p=save(fr,f'{st}_grid.gif',gv.FRAME_MS,(1200,1000,900,800)); print(p,os.path.getsize(p)//1024,'KB'); cmp(REF[st],fr,20,st)
    if '--strip' in sys.argv: p,fr=strip(); print(p,os.path.getsize(p)//1024,'KB'); cmp(REF['strip'],fr,7,'all_styles_front')
