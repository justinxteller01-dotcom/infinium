#!/usr/bin/env python3
"""Ship the four template styles as runtime glow textures.
v08 Astral, v09 Cybernetic, v11 Singularity, v12 CyberPsycho (gen_variants.py) ->
  assets/infinium/textures/entity/infinium_glow/<style>/<humanoid|humanoid_leggings>/<f>.png  (+ infinium_glow_gray/ for dyes)
Frames are re-masked to the shipped armor base (shared equipment asset) and re-checked (border rule, loop, faces).
Uses build_variants/vNN if present (made by gen_variants.py, same deterministic seeds); --force rebuilds them."""
import os, sys, shutil, glob
from PIL import Image
import gen_variants as gv, gen_drafts as gd, gen_dye
R='src/main/resources/assets/infinium/textures/entity/'
def frames_of(v,key):
    d=f'build_variants/{v}/textures/entity/infinium_glow/{key}/'
    n=len(glob.glob(d+'*.png')); return [Image.open(d+f'{f}.png').convert('RGBA') for f in range(n)]
def main(force=False):
    for v,sid in gv.STYLE_IDS.items():
        cfg=gv.V[v]; F=cfg.get('F',40)
        d=f'build_variants/{v}/textures/entity/infinium_glow/humanoid/'
        if force or not os.path.isdir(d) or len(glob.glob(d+'*.png'))!=F: gv.build_textures(v,cfg)
        for key in ('humanoid','humanoid_leggings'):
            base=Image.open(R+f'equipment/{key}/infinium.png').convert('RGBA')
            op=gd.opaque(base); allowed=gd.interior(op) if key=='humanoid' else op
            if cfg['sym']: allowed={q for q in allowed if gd.M_hum(q) in allowed}
            frs=frames_of(v,key); assert len(frs)==F,(v,key,len(frs))
            out=[]
            for fr in frs:
                p=fr.load(); W,H=fr.size
                for y in range(H):
                    for x in range(W):
                        if p[x,y][3] and (x,y) not in allowed: p[x,y]=(0,0,0,0)
                out.append(fr)
            gd.check(out,allowed,f'{sid}/{key}'); gd.check_loop(out,f'{sid}/{key}')
            gd.check_faces_animate(out,gd.face_groups(key),allowed,f'{sid}/{key}')
            if cfg['sym'] and not cfg.get('accents'): gd.check_sym(out,gd.M_hum,f'{sid}/{key}')   # accented styles: base symmetry checked in gen_variants
            dst=R+f'infinium_glow/{sid}/{key}/'; gdst=R+f'infinium_glow_gray/{sid}/{key}/'
            shutil.rmtree(dst,ignore_errors=True); shutil.rmtree(gdst,ignore_errors=True); os.makedirs(dst)
            for f,fr in enumerate(out):
                fr.save(dst+f'{f}.png'); gen_dye.gray(dst+f'{f}.png',gdst+f'{f}.png')
        print(f'style {sid} ({v}): {F} frames x {cfg.get("ms",300)} ms OK',flush=True)
if __name__=='__main__': main('--force' in sys.argv)
