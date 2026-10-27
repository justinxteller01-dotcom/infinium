#!/usr/bin/env python3
"""Textures (32x32 since R8 13.16 A; content at v0-15, chamfer net at v16-27 painted by r8_post.py) for client HelmetExtras:
base.png + glow_<style>/N.png with the frame count / clock of that style's suit glow (Astral "glow" 40x300, Cybernetic 40x300,
Singularity 100x100, Cyber Psycho 80x100; Infinity 80x40 staged).
UV: frame side bar texOffs(0,0) box 1x6x0.6; brow texOffs(4,0) box 6x1x0.6. (horns removed)"""
import math, os
from PIL import Image
D='src/main/resources/assets/infinium/textures/entity/infinium_helmet_extra/'
os.makedirs(D+'glow',exist_ok=True); F=40; TAU=2*math.pi
base=Image.new('RGBA',(32,32),(0,0,0,0)); p=base.load()
FRAME=[(x,y) for x in range(0,4) for y in range(0,7)]; BROW=[(x,y) for x in range(4,18) for y in range(0,2)]
HORN=[(x,y) for x in range(0,10) for y in range(9,14)]
for q in FRAME+BROW: p[q]=(34,6,58,255)
AM=[(222,180,255),(176,110,240),(130,60,205),(88,30,150)]   # shard facets in the glow purple scheme     # amethyst shard facets light -> dark
UVS=[(12,9),(16,9),(22,9),(16,12),(12,12)]
PARTS={i:[(u+x,v+y) for x in range(4) for y in range(2)] for i,(u,v) in enumerate(UVS)}
def gemcol(part,x,y): return [AM[0],AM[1],AM[1],AM[2],AM[3]][part]
for part,px_ in PARTS.items():
    for q in px_: p[q]=gemcol(part,*q)+(255,)
base.save(D+'base.png')
import shutil as _sh, glob as _gl
for _d in _gl.glob(D+'glow*_gray'):
    if 'infinity' not in _d: _sh.rmtree(_d)
for _d in ('glow','glow_singularity'): _sh.rmtree(D+_d,ignore_errors=True); os.makedirs(D+_d)
for _dir,F in (('glow',40),('glow_singularity',100)):   # Singularity: same Astral-family frame/gem pattern on its own 100 x 100 ms clock
  for f in range(F):
    g=Image.new('RGBA',(32,32),(0,0,0,0)); gp=g.load(); t=f/F
    def col(b): b=max(0,min(1,b))**1.4; return (int(30+130*b),int(3+67*b),int(56+172*b),255)
    for (x,y) in FRAME: gp[x,y]=col(0.35+0.65*(0.5+0.5*math.cos(TAU*(y/7.0+t))))   # pulse runs up both bars (mirrored UV)
    for (x,y) in BROW:  gp[x,y]=col(0.35+0.65*(0.5+0.5*math.cos(TAU*(abs(x+0.5-7.6)/7.0+t+1/7.0))))  # continues from bar tops to the middle
    br=0.30+0.12*math.cos(TAU*t)                 # gentle breathe over 12 s
    tc=(3*t)%1.0                                 # glint every 4 s, sweeping bottom -> tip
    for part,px_ in PARTS.items():
        gl=max(0.0,1-abs(tc-0.10-0.035*(4-part))/0.05)
        for (x,y) in px_:
            c=gemcol(part,x,y); k=min(1.0,br+0.75*gl)
            gp[x,y]=(int(c[0]*k),int(c[1]*k),int(c[2]*k),255)
    g.save(D+f'{_dir}/{f}.png')
# Per-style face perimeter (CyberPsycho strong, Cybernetic subtle): smooth looping flow that starts at the top-center shard,
# runs out along the brow to both sides (mirrored) and down the side bars. Path distance d: brow |x-center| (0..7), bars 7+y.
PSY_LO,PSY_HI=(46,4,42),(255,96,222)       # CyberPsycho pink-magenta (no blue)
for st,amp,base_b,hi,F in (('cyberpsycho',0.70,0.25,PSY_HI,80),('cybernetic',0.35,0.30,(214,92,214),40)):
    _sh.rmtree(D+f'glow_{st}',ignore_errors=True); os.makedirs(D+f'glow_{st}')
    for f in range(F):
        g=Image.new('RGBA',(32,32),(0,0,0,0)); gp=g.load(); t=f/F
        def colp(b):
            b=max(0,min(1,b))**1.3; return tuple(int(PSY_LO[i]+(hi[i]-PSY_LO[i])*b) for i in range(3))+(255,)
        def wave(d): return base_b+amp*(0.5+0.5*math.cos(TAU*(d/7.0-2*t)))   # 2 pulses per loop, outward
        for (x,y) in BROW:  gp[x,y]=colp(wave(abs(x+0.5-7.6)))
        for (x,y) in FRAME: gp[x,y]=colp(wave(7.0+y))
        src=wave(0.0)                                # shard brightens as each pulse leaves it
        for part,px_ in PARTS.items():
            for (x,y) in px_:
                c=gemcol(part,x,y); k=min(1.0,0.25+0.75*(src-base_b)/amp*(1.0 if st=='cyberpsycho' else 0.6))
                pink=(int(c[0]*k),int(c[1]*0.75*k),int(min(255,c[2])*0.9*k),255)
                gp[x,y]=pink
        g.save(D+f'glow_{st}/{f}.png')
print('helmet extra textures written')
