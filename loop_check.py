#!/usr/bin/env python3
"""Loop check for every animated texture: wrap step (last->first) vs mean/p90 adjacent step. FAIL if wrap > max(p90, 1.5*mean)."""
import glob, os, json, sys
import numpy as np
from PIL import Image
R='src/main/resources/assets/infinium/textures/'
def d(a,b): return float(np.abs(a.astype(int)-b.astype(int)).sum())
def judge(name,frs):
    if len(frs)<3: return None
    st=[d(frs[i],frs[i+1]) for i in range(len(frs)-1)]; w=d(frs[-1],frs[0]); m=np.mean(st); p=np.percentile(st,90)
    ok = w<=max(p,1.5*m) or w==0
    return (name,len(frs),w/max(m,1e-9),ok)
res=[]
for dd in sorted({os.path.dirname(f) for f in glob.glob(R+'**/0.png',recursive=True)}):
    n=len(glob.glob(dd+'/*.png')); frs=[np.asarray(Image.open(f'{dd}/{i}.png').convert('RGBA')) for i in range(n)]
    r=judge(dd[len(R):],frs); r and res.append(r)
for m in glob.glob(R+'**/*.png.mcmeta',recursive=True):
    im=np.asarray(Image.open(m[:-7]).convert('RGBA')); W=im.shape[1]; n=im.shape[0]//W
    r=judge(m[len(R):-7],[im[i*W:(i+1)*W] for i in range(n)]); r and res.append(r)
bad=[r for r in res if not r[3]]
if '--fix' in sys.argv:
    for name,n,_,_ in bad:   # crossfade the last 4 frames toward frame 0 (only strips/dirs that fail)
        p=R+name
        if os.path.isdir(p):
            frs=[np.asarray(Image.open(f'{p}/{i}.png').convert('RGBA')).astype(float) for i in range(n)]
        else:
            im=np.asarray(Image.open(p).convert('RGBA')).astype(float); W=im.shape[1]; frs=[im[i*W:(i+1)*W] for i in range(n)]
        K=4
        for j in range(K):
            w=(j+1)/(K+1); i=n-K+j; frs[i]=frs[i]*(1-w)+frs[0]*w
        out=[np.clip(np.rint(x),0,255).astype(np.uint8) for x in frs]
        if os.path.isdir(p): [Image.fromarray(o).save(f'{p}/{i}.png') for i,o in enumerate(out)]
        else: Image.fromarray(np.concatenate(out,0)).save(p)
        print('fixed',name)
    os.execv(sys.executable,[sys.executable,__file__])
for r in res: print(f'{"OK  " if r[3] else "FAIL"} {r[0]:70s} n={r[1]:3d} wrap/mean={r[2]:.2f}')
print(f'{len(res)} animations, {len(bad)} fail'); sys.exit(1 if bad else 0)
