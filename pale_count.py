import glob, numpy as np, sys
from PIL import Image
R='src/main/resources/assets/infinium/textures/'
def cnt(st):
    ps=glob.glob(R+f'entity/infinium_glow/{st}/*/*.png')+glob.glob(R+f'item/cracks_{st}/*.png')
    if st in('cybernetic','cyberpsycho'): ps+=glob.glob(R+f'entity/infinium_helmet_extra/glow_{st}/*.png')
    if st=='astral': ps+=glob.glob(R+'entity/infinium_glow/humanoid*/*.png')+glob.glob(R+'entity/infinium_glow/wings/*.png')+glob.glob(R+'item/cracks/*.png')+glob.glob(R+'entity/infinium_helmet_extra/glow/*.png')
    n=0
    for p in ps:
        a=np.asarray(Image.open(p).convert('RGBA')).astype(float); m=a[...,3]>0
        mx=a[...,:3].max(2); mn=a[...,:3].min(2); v=mx/255; s=np.where(mx>0,(mx-mn)/np.maximum(mx,1),0)
        n+=int(((v>0.70)&(s<0.45)&m).sum())
    return n,len(ps)
for st in sys.argv[1:]: print(st,'pale px (V>70%,S<45%, all frames):',*cnt(st))
