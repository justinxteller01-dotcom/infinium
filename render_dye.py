#!/usr/bin/env python3
"""previews/dye_singularity.png: Singularity undyed + red/green/blue/yellow dye (gray glow x dye tint, exactly as in game)."""
import os, shutil, glob, numpy as np
from PIL import Image, ImageDraw, ImageFont
import render_ref_gifs as RG, gen_variants as gv, render3d_lib as r3, gen_dye
E=os.path.abspath('src/main/resources/assets/infinium/textures/entity')+'/'
def tinted_root(st,idx):
    T=f'/tmp/dyeroot_{st}_{idx}/'; shutil.rmtree(T,ignore_errors=True); os.makedirs(T+'entity/infinium_glow')
    os.symlink(E+'equipment',T+'entity/equipment'); c=gen_dye.PAL[idx]; tint=np.array([c>>16&255,c>>8&255,c&255])/255
    for l in ('humanoid','humanoid_leggings','wings'):
        os.makedirs(T+f'entity/infinium_glow/{l}')
        for p in glob.glob(E+f'infinium_glow_gray/{st}/{l}/*.png'):
            a=np.asarray(Image.open(p).convert('RGBA')).astype(float); a[...,:3]*=tint
            Image.fromarray(a.astype(np.uint8)).save(T+f'entity/infinium_glow/{l}/'+os.path.basename(p))
    return T
import gen_helmets as gh, sys
def sheet(styles,dyes,out):
    font=ImageFont.load_default(size=20); cols=[]
    for st in styles:
        for lab,idx in dyes:
            RG.use(st)
            if idx is not None: r3.T=tinted_root(st,idx)+'entity/'; r3._TC.clear(); gh.TINT=gen_dye.PAL[idx]
            im=gv.figure(12,r3.POSES['standing'],0.0).convert('RGB'); gh.TINT=None
            ImageDraw.Draw(im).text((8,6),f'{RG.NAME[st] if hasattr(RG,"NAME") else st} {lab}',fill=(20,20,24),font=font); cols.append(im)
    W=Image.new('RGB',(400*len(cols),430)); [W.paste(c,(i*400,0)) for i,c in enumerate(cols)]; W.save(out); print(out)
if __name__=='__main__':
    if '--everything' in sys.argv: sheet(('singularity','cyberpsycho'),(('red',14),('green',13)),'previews/dye_everything.png')
    else: sheet(('singularity',),(('undyed',None),('red',14),('green',13),('blue',11),('yellow',4)),'previews/dye_singularity.png')
