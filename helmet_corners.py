#!/usr/bin/env python3
"""Helmet shell top-edge bevel: the top corner pixel on both sides, front to back, is removed (symmetric).
apply(img) is used by the generators; running this file fixes src (default look) base + glow frames so the
border rule still holds (glow on any pixel that is no longer interior is cleared)."""
import glob
from PIL import Image
CORNER={(8,y) for y in range(8)}|{(15,y) for y in range(8)}|{(x,8) for x in list(range(0,8))+list(range(16,24))}|{(8,8),(15,8),(24,8),(31,8)}   # R8 13.16 A: row v8 u8-31 dropped
def _nb(q):
    x,y=q
    if y==8 and x not in (8,15): return (x,9)
    return (9 if x==8 else 14 if x==15 else x, max(y,0) if y<8 else 9)
def apply(img):
    """R8 13.16 A: the cut corner texels are TRANSPARENT; the gap is closed by the HelmetExtras chamfer boxes instead."""
    p=img.load()
    for q in sorted(CORNER): p[q]=(0,0,0,0)
    return img
if __name__=='__main__':
    import gen_drafts as gd
    for R in ('src/main/resources/assets/infinium/textures/entity/',):
        b=Image.open(R+'equipment/humanoid/infinium.png').convert('RGBA'); apply(b); b.save(R+'equipment/humanoid/infinium.png')
        ok=gd.interior(gd.opaque(b)); n=0
        for f in glob.glob(R+'infinium_glow/humanoid/*.png'):
            g=Image.open(f).convert('RGBA'); p=g.load(); W,H=g.size
            for y in range(H):
                for x in range(W):
                    if p[x,y][3] and (x,y) not in ok: p[x,y]=(0,0,0,0); n+=1
            g.save(f)
        print('helmet corners applied to src; cleared',n,'glow px')
