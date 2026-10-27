#!/usr/bin/env python3
"""Palette guard: no cyan/blue (160-262 deg) in any style's glow. Remaps stray pixels to purple-magenta (hue 290 deg,
same sat/value), regenerates the matching gray (dye) frame, then prints a hue histogram check per style (exit 1 if any left)."""
import glob, os, sys, colorsys
from PIL import Image
import gen_dye
LO,HI=160,262
R='src/main/resources/assets/infinium/textures/'
def bad(r,g,b): h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255); return s>=0.12 and v>=0.06 and LO<=h*360<=HI
def fix(path,gray=None):
    path = path.replace('\\', '/')
    if gray: gray = gray.replace('\\', '/')
    im=Image.open(path).convert('RGBA'); px=im.load(); n=0
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=px[x,y]
            if a and bad(r,g,b):
                h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255); rr,gg,bb=colorsys.hsv_to_rgb(290/360,s,v)
                px[x,y]=(int(rr*255),int(gg*255),int(bb*255),a); n+=1
    if n:
        im.save(path)
        if gray: gen_dye.gray(path,gray)
    return n
def count(paths):
    n=t=0
    for p in paths:
        im=Image.open(p).convert('RGBA')
        for r,g,b,a in im.get_flattened_data() if hasattr(im,'get_flattened_data') else im.getdata():
            if a<16: continue
            t+=1; n+=bad(r,g,b)
    return n,t
def files(st):
    E=R+f'entity/infinium_glow/{st}/'
    return {'armor':glob.glob(E+'humanoid/*.png'),'leggings':glob.glob(E+'humanoid_leggings/*.png'),'wings':glob.glob(E+'wings/*.png'),
            'tools+icons':glob.glob(R+f'item/cracks_{st}/**/*.png',recursive=True)}
def graypath(p):
    p = p.replace('\\', '/')
    if '/infinium_glow/' in p: return p.replace('/infinium_glow/','/infinium_glow_gray/')
    st=p.split('/cracks_')[1].split('/')[0]; return p.replace(f'/cracks_{st}/',f'/cracks_{st}_gray/')
if __name__=='__main__':
    fixed=0
    if '--fix' in sys.argv:
        for p in glob.glob('frozen/cyberpsycho_wings/glow/*.png'):
            p = p.replace('\\', '/')
            fixed+=fix(p,p.replace('/glow/','/gray/'))
        for st in ('astral','cybernetic','singularity','cyberpsycho'):
            for k,ps in files(st).items():
                for p in ps:
                    g=graypath(p); fixed+=fix(p,g if os.path.exists(g) else None)
        print('remapped px:',fixed)
    tot=0
    for st in ('astral','cybernetic','singularity','cyberpsycho'):
        for k,ps in files(st).items():
            n,t=count(ps); tot+=n; print(f'{st:12s} {k:12s} files={len(ps):4d} lit px={t:7d} cyan/blue({LO}-{HI}deg) px={n}')
    print('TOTAL cyan/blue px',tot); sys.exit(1 if tot else 0)
