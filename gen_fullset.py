#!/usr/bin/env python3
"""Large full-set previews: draft9 armor + h21 helmet extras, wings hidden, gray body, mid-gray bg, outline.
preview/fullset_large.png/.gif and preview/dye_colors.png (17 full-body front tiles)."""
import math, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import render3d_lib as r3, gen_helmets as gh, gen_dye as gd
R='build_drafts/draft9/assets/infinium/textures/'
BG=(118,118,124); SKIN=Image.new('RGBA',(64,64),(196,192,186,255))
def tint(img,rgb):
    r,g,b=rgb>>16&255,rgb>>8&255,rgb&255; im=img.copy(); p=im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            v,_,_,a=p[x,y]
            if a: p[x,y]=(v*r//255,v*g//255,v*b//255,a)
    return im
_C={}
def use(rgb):
    def tex(layer,f):
        k=(rgb,layer,f)
        if k not in _C:
            base=Image.open(R+f'entity/equipment/{layer}/infinium.png').convert('RGBA')
            gl=Image.open(R+f'entity/infinium_glow/{layer}/{f}.png').convert('RGBA') if rgb is None else tint(Image.open(R+f'entity/infinium_glow_gray/{layer}/{f}.png').convert('RGBA'),rgb)
            c=base.copy(); c.alpha_composite(gl); _C[k]=c
        return _C[k]
    r3.tex=tex; gh.TINT=rgb
H21=gh.H21[1]
def figure(f,yaw,size=620,scale=14,pitch=0.0):
    Q=r3.build(f,dict(r3.POSES['standing'],nowings=True),SKIN)+gh.extra(H21,f)
    Q=[([(x,y+5.5,z) for x,y,z in cs],c) for cs,c in Q]   # center the whole figure (y -11..24)
    im=r3.render(Q,yaw,pitch,0.0,size=size,scale=scale,bg=BG,shade=True).convert('RGB')
    a=np.asarray(im).astype(int); fg=np.any(a!=BG,axis=2)
    edge=fg & ~np.asarray(Image.fromarray((fg*255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)))>0
    out=np.where(edge[...,None],np.array([20,18,22]),a)     # dark outline around the silhouette
    return Image.fromarray(out.astype(np.uint8)).crop((size//2-170,(size-580)//2,size//2+170,(size+580)//2))
try: font=ImageFont.load_default(size=22); small=ImageFont.load_default(size=18)
except Exception: font=small=None
def row(f,views):
    w=340; S=Image.new("RGB",(w*len(views),580+34),BG); d=ImageDraw.Draw(S)
    for i,(name,yaw,pit) in enumerate(views):
        S.paste(figure(f,yaw,pitch=pit),(i*w,34)); d.text((i*w+10,6),name,fill=(20,20,24),font=font)
    return S
use(None)
V3=(('front',0.0,0.0),('back',math.pi,0.0),('3/4',-math.pi*0.25,0.2))
row(6,V3).save('preview/fullset_large.png')
fr=[row(f,V3[:2]) for f in range(40)]
fr[0].save('preview/fullset_large.gif',save_all=True,append_images=fr[1:],duration=300,loop=0)
cols=[('default (purple)',None)]+[(n.replace('_',' '),gd.PAL[i]) for i,n in enumerate(gd.NAMES)]
TW,TH=300,560; S=Image.new('RGB',(TW*6,TH*3),BG); d=ImageDraw.Draw(S)
for i,(name,rgb) in enumerate(cols):
    use(rgb); x,y=(i%6)*TW,(i//6)*TH
    S.paste(figure(6,0.0,size=500,scale=13).resize((TW,int(TW*420/340))) if False else figure(6,0.0,size=620,scale=12).resize((TW,int(580*TW/340))),(x,y+34))
    d.text((x+10,y+6),name,fill=(20,20,24),font=small)
S.save('preview/dye_colors.png'); print('done')
