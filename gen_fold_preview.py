#!/usr/bin/env python3
"""Wing fold preview: open (spread) -> closed -> open with the in-game WingFold schedule (wing_fold.lerp).
Columns: back, left side, right side, back 3/4. Writes preview/wing_fold/anim.gif (+ closed.png)."""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import render3d_lib as r3, gen_helmets as gh, gen_variants as gv, wing_fold as wf
T='build_variants/v09/textures/'
VIEWS=((math.pi,'back'),(math.pi/2,'left'),(-math.pi/2,'right'),(math.pi*0.78,'back 3/4'))
N=24; HOLD=6
def pose_at(t):
    p=wf.lerp(wf.CLOSED,wf.OPEN,t)
    return {'wing':(p['rx'],p['rz']),'zs':p['zs'],'zl':p['zl'],'zr':p['zr'],'wg':1.0,'arm':0.0,'bp':0.0}
def main():
    r3.T=T+'entity/'; r3._TC.clear(); gh.TINT=None; gv.F=40; r3.FRAMES_HINT[0]=40
    ts=[1.0]*HOLD+[1-i/N for i in range(1,N+1)]+[0.0]*HOLD+[i/N for i in range(1,N+1)]
    font=ImageFont.load_default(size=22); frames=[]
    for k,t in enumerate(ts):
        G=Image.new('RGB',(400*4,36+430),gv.BG); d=ImageDraw.Draw(G)
        ov=wf.report(wf.lerp(wf.CLOSED,wf.OPEN,t))
        d.text((10,6),f'wing fold  open={t:.2f}  (closed pose: no wing/wing or wing/chest overlap)',fill=(20,20,24),font=font)
        for c,(yaw,n) in enumerate(VIEWS): G.paste(gv.figure(k%40,pose_at(t),yaw),(c*400,36))
        frames.append(G.resize((G.width*3//4,G.height*3//4)))
        if k==HOLD+N: G.save('/tmp/closed.png')
    os.makedirs('preview/wing_fold',exist_ok=True)
    frames[0].save('preview/wing_fold/anim.gif',save_all=True,append_images=frames[1:],duration=45,loop=0,optimize=True)
    Image.open('/tmp/closed.png').save('preview/wing_fold/closed.png')
    print('fold preview ok', len(frames),'frames; closed overlaps', wf.report(wf.CLOSED))
if __name__=='__main__': main()
