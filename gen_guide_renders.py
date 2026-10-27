#!/usr/bin/env python3
"""R4: animated guide-book art from the preview render pipeline (infinity_work/inf_preview + render3d_lib).
Strips: 16 frames x 128 px, vertical. welcome (Infinity armor + particles, sword, scattered items), one clean full-set per armor
page, templates (4 smithing templates). Colour rules applied (no blue, no pale) and the mannequin gray background removed."""
import os, sys, math, colorsys
sys.path.append('/workspace/design_update/infinity_work')
import numpy as np
from PIL import Image
import inf_preview as P, render3d_lib as r3
OUT='src/main/resources/assets/infinium/textures/gui/guide/'; IT='src/main/resources/assets/infinium/textures/item/'
N=16; S=128
P.CLK.update({'astral':(40,150),'cybernetic':(40,100)})
P.HEL.update({'astral':('glow',40,4800),'cybernetic':('glow_cybernetic',40,4800)})

def legal(im):
    """transparent bg, then the colour rules: blue hues -> 280 deg, pale/low-sat bright -> V 0.62."""
    a=np.asarray(im.convert('RGBA')).astype(float)/255; out=a.copy()
    rgb=a[...,:3]; bg=np.all(np.abs(rgb*255-np.array(P.BG))<6,axis=2); out[bg,3]=0
    H,W=rgb.shape[:2]
    for y in range(H):
        for x in range(W):
            if out[y,x,3]==0: continue
            h,s,v=colorsys.rgb_to_hsv(*out[y,x,:3])
            if s>=0.15 and 200/360<=h<=262/360: h=280/360
            if s<0.45 and v>0.70: v=0.62
            out[y,x,:3]=colorsys.hsv_to_rgb(h,s,v)
    return Image.fromarray((out*255).astype(np.uint8),'RGBA')

def fit(img,box):
    a=np.asarray(img); ys,xs=np.nonzero(a[...,3]>0)
    if len(xs)==0: return img
    img=img.crop((xs.min(),ys.min(),xs.max()+1,ys.max()+1)); k=min(box[0]/img.width,box[1]/img.height)
    return img.resize((max(1,int(img.width*k)),max(1,int(img.height*k))),Image.LANCZOS)

def figure(st,i,yaw,sim=None):
    F,fms=P.CLK[st]; ms=int(i*F*fms/N)                                   # one full texture loop per strip loop
    img,zb=P.figure(st,ms,yaw)
    fr=P.draw_particles(img,zb,sim.ps,yaw) if sim else Image.fromarray(img).convert('RGB')
    return legal(fr)

def tool_frame(tool,i):
    b=Image.open(IT+tool+'.png').convert('RGBA'); b=b.crop((0,0,b.width,b.width)); b.alpha_composite(item_frame(IT+'cracks_infinity/'+tool+'.png',i)); return b
def item_frame(path,i):
    im=Image.open(path).convert('RGBA'); n=max(1,im.height//im.width); f=(i*n)//N
    return im.crop((0,f*im.width,im.width,(f+1)*im.width))

def save(name,frames):
    strip=Image.new('RGBA',(S,S*N),(0,0,0,0))
    for i,f in enumerate(frames):
        a=np.asarray(f).copy(); a[...,3]=np.where(a[...,3]>=128,255,0); f=legal(Image.fromarray(a,'RGBA'))   # hard edges, rules re-applied
        strip.alpha_composite(f,(0,i*S))
    q=strip.quantize(colors=255,method=Image.Quantize.FASTOCTREE)          # palette PNG: small jar
    pal=q.getpalette()
    for k in range(0,len(pal)-2,3):                                         # the palette itself obeys the colour rules
        h,s_,v=colorsys.rgb_to_hsv(*(c/255 for c in pal[k:k+3]))
        if s_>=0.14 and 199/360<=h<=263/360: h=280/360
        if s_<0.46 and v>0.69: v=0.62
        pal[k:k+3]=[int(round(c*255)) for c in colorsys.hsv_to_rgb(h,s_,v)]
    q.putpalette(pal); q.save(OUT+name+'.png',optimize=True); print(name,os.path.getsize(OUT+name+'.png'))

def armor_page(st):
    fr=[]
    for i in range(N):
        yaw=0.55*math.sin(2*math.pi*i/N)
        c=Image.new('RGBA',(S,S),(0,0,0,0)); f=fit(figure(st,i,yaw),(S-4,S-4)); c.alpha_composite(f,((S-f.width)//2,(S-f.height)//2)); fr.append(c)
    save(st,fr)

def welcome():
    sim=P.Sim('infinity',N*2); 
    for t in range(-80,0): sim.step(t)
    fr=[]; tick=-1
    small=[('infinium_ingot.png',(4,6)),('infinium_rocket.png',(108,8)),('astral_upgrade_smithing_template.png',(6,106)),('PICK',(2,58))]
    for i in range(N):
        while tick<i*2: tick+=1; sim.step(tick)                           # 2 ticks per frame
        yaw=0.45*math.sin(2*math.pi*i/N)
        c=Image.new('RGBA',(S,S),(0,0,0,0))
        f=fit(figure('infinity',i,yaw,sim),(118,112)); c.alpha_composite(f,((S-f.width)//2,4+(112-f.height)//2))
        sw=tool_frame('infinium_sword',i).resize((48,48),Image.NEAREST); c.alpha_composite(legal(sw),(78,76))
        for p,(x,y) in small:
            im=tool_frame('infinium_pickaxe',i) if p=='PICK' else item_frame(IT+p,i)
            c.alpha_composite(legal(im.resize((20,20),Image.NEAREST)),(x,y))
        fr.append(c)
    save('welcome',fr)

def templates():
    names=['astral','cybernetic','singularity','cyberpsycho']; fr=[]
    for i in range(N):
        c=Image.new('RGBA',(S,S),(0,0,0,0))
        for k,n in enumerate(names):
            t=legal(item_frame(IT+f'{n}_upgrade_smithing_template.png',i).resize((56,56),Image.NEAREST))
            bob=int(round(2*math.sin(2*math.pi*(i/N+k/4))))
            c.alpha_composite(t,(6+(k%2)*62,6+(k//2)*62+bob))
        fr.append(c)
    save('templates',fr)

if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    for st in ('astral','cybernetic','singularity','cyberpsycho','infinity'): armor_page(st)
    welcome(); templates()
