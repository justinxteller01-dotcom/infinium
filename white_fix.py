#!/usr/bin/env python3
"""ALL styles: replace white / near-white highlights (min channel >= 150) with the style's muted medium-light colour
(value <= ~80%), keep KEEP tiny white sparkles per frame; Singularity additionally scaled so its mean brightness stays
<= 0.85x the Cyber styles. Regenerates every gray dye copy from the fixed colour texture. Run once per build (gen_all)."""
import glob, os, math, numpy as np
from PIL import Image
import gen_dye
R='src/main/resources/assets/infinium/textures/'; KEEP=1; TH=150
CAP={'astral':(130,80,170),'cybernetic':(165,80,178),'cyberpsycho':(178,75,160),'singularity':(115,70,150)}
PALE=('astral','singularity','cybernetic','cyberpsycho')   # also remap pale lavenders (V>70%, S<45%) and keep NO white sparkle
import colorsys
def nebula(a):
    """Singularity: purple nebula range (deep -> mid -> brighter violet, hue 268-290 by a static spatial field so loops stay exact),
    muted pink (hue ~312) only on a sprinkle of the brightest texels. V <= 0.68, S >= 0.55 -> never pale, never blue."""
    H,W=a.shape[:2]
    for y in range(H):
        for x in range(W):
            r,g,b,al=a[y,x]
            if not al: continue
            v=max(r,g,b)/255.0
            n=0.5+0.5*math.sin(x/3.1+y/4.7+(y//16)*1.7)*math.cos(y/2.3-x/5.9)
            h=(268+22*n)/360; sat=0.92-0.25*n*v; val=0.153+0.59*v**0.85   # +9% (user)
            if v>0.62 and ((x*7+y*13)%11)<3: h=312/360; sat=0.62; val=min(val,0.66)
            rr,gg,bb=colorsys.hsv_to_rgb(h,sat,val); a[y,x,:3]=(int(rr*255),int(gg*255),int(bb*255))
    return a
def fix(a,cap,K=1.0,pale=False,neb=False):
    a=a.astype(int)
    if neb: a=nebula(a)
    rgb=a[...,:3]; m=(rgb.min(2)>=TH)&(a[...,3]>0)
    if pale:
        mx=rgb.max(2); mn=rgb.min(2); m|=(mx>0.70*255)&((mx-mn)<0.45*mx)&(a[...,3]>0)
    n0=int(m.sum()); KEEP_=0 if pale else KEEP
    ys,xs=np.nonzero(m); keep=[]
    for i in np.argsort(-rgb[ys,xs].sum(1)) if len(ys) else []:
        if len(keep)>=KEEP_: break
        if all(abs(ys[i]-y)+abs(xs[i]-x)>3 for y,x in keep): keep.append((ys[i],xs[i]))
    for y,x in zip(ys,xs):
        if (y,x) not in keep: rgb[y,x]=np.minimum(rgb[y,x],cap)
    if K<1: a[...,:3]=(a[...,:3]*K).astype(int)
    return np.clip(a,0,255).astype(np.uint8),n0
def frames_of(p):   # dir frame png, or a vertical 16px strip
    p = p.replace('\\', '/')
    a=np.asarray(Image.open(p).convert('RGBA')); W=a.shape[1]
    return [a[i*W:(i+1)*W] for i in range(a.shape[0]//W)] if '/item/' in p and a.shape[0]>W else [a]
def process(paths,cap,graymap,K=1.0,pale=False,neb=False):
    n=0
    for p in paths:
        p = p.replace('\\', '/')
        out=[]
        for fr in frames_of(p): f2,k=fix(fr,cap,K,pale,neb); n+=k; out.append(f2)
        Image.fromarray(np.concatenate(out,0)).save(p); gen_dye.gray(p,graymap(p))
    return n
def meanv(st):
    v=[]
    for p in glob.glob(R+f'entity/infinium_glow/{st}/*/*.png')[::5]:
        p = p.replace('\\', '/')
        a=np.asarray(Image.open(p).convert('RGBA')).astype(int); v+=list(a[...,:3].max(2)[a[...,3]>0])
    return float(np.mean(v))
rep=[]
for st,cap in CAP.items():
    K=1.0
    if st=='singularity':
        sg=lambda p:p.replace('/infinium_glow/','/infinium_glow_gray/') if '/infinium_glow/' in p else p.replace('/cracks_singularity/','/cracks_singularity_gray/')
        SP=glob.glob(R+'entity/infinium_glow/singularity/*/*.png')+glob.glob(R+'item/cracks_singularity/*.png')
        n=process(SP,cap,sg,1.0,True,True)
        cv=min(meanv('cyberpsycho'),meanv('cybernetic')); K=min(1.0,0.93*cv/meanv("singularity"))
        if K<1: process(SP,(255,255,255),sg,K)
        rep.append(f'singularity: {n} pale px capped, nebula remap, scale {K:.2f}'); continue
    n=process(glob.glob(R+f'entity/infinium_glow/{st}/*/*.png'),cap,lambda p:p.replace('/infinium_glow/','/infinium_glow_gray/'),K,st in PALE)
    n+=process(glob.glob(R+f'item/cracks_{st}/*.png'),cap,lambda p,st=st:p.replace(f'/cracks_{st}/',f'/cracks_{st}_gray/'),K,st in PALE)
    if st in('cybernetic','cyberpsycho'):
        n+=process(glob.glob(R+f'entity/infinium_helmet_extra/glow_{st}/*.png'),cap,lambda p,st=st:p.replace(f'/glow_{st}/',f'/glow_{st}_gray/'),1.0,True)
    rep.append(f'{st}: {n} near-white px capped (scale {K:.2f})')
# legacy / default (Astral-coloured) sheets
n=process(glob.glob(R+'entity/infinium_glow/humanoid*/*.png')+glob.glob(R+'entity/infinium_glow/wings/*.png'),CAP['astral'],lambda p:p.replace('/infinium_glow/','/infinium_glow_gray/'),1.0,True)
n+=process(glob.glob(R+'item/cracks/*.png'),CAP['astral'],lambda p:p.replace('/cracks/','/cracks_gray/'),1.0,True)
n+=process(glob.glob(R+'entity/infinium_helmet_extra/glow/*.png'),CAP['astral'],lambda p:p.replace('/glow/','/glow_gray/'),1.0,True)
rep.append(f'default/astral sheets: {n} capped')
print(' | '.join(rep))
