#!/usr/bin/env python3
"""R8 13.16 A/B post-pass (runs after loop_check, before gen_specular):
 A. helmet top corners truly removed: CUT texels alpha 0 on every helmet base (Infinium + Infinity) and in every humanoid glow/gray frame;
    helmet-extra PNGs grown to 32x32 (Infinity staged 32x16 padded) and the chamfer net (texOffs 0,16) painted from the helmet's row v9
    of the side face, base and every style's glow + gray frame at the same frame index.
 B. wings W4: front-face glow region u24-33 v2-21 cleared in every wing glow + gray frame of every style (+ Infinity).
 Then accent_gray is regenerated from the 32x32 base. Prints checks."""
import glob, os
from PIL import Image
import gen_dye
E='src/main/resources/assets/infinium/textures/entity/'
CUT={(8,y) for y in range(8)}|{(15,y) for y in range(8)}|{(x,8) for x in list(range(0,8))+list(range(16,24))}|{(8,8),(15,8),(24,8),(31,8)}
def clear(img,pts):
    p=img.load(); n=0
    for q in pts:
        if p[q][3]: p[q]=(0,0,0,0); n+=1
    return n
# ---- A1: CUT ----
nb=0
for b in (E+'equipment/humanoid/infinium.png',E+'equipment/humanoid/infinity.png'):
    im=Image.open(b).convert('RGBA'); nb+=clear(im,CUT); im.save(b)
ng=0; nf=0
for f in glob.glob(E+'infinium_glow*/**/humanoid/*.png',recursive=True)+glob.glob(E+'infinium_glow*/humanoid/*.png'):
    if f.endswith('_s.png'): continue
    im=Image.open(f).convert('RGBA'); k=clear(im,CUT); nf+=1
    if k: ng+=k; im.save(f)
print(f'r8_post A: CUT cleared {nb} base px, {ng} glow px in {nf} humanoid frames')
# ---- B: wings front clear ----
FRONT=[(x,y) for x in range(24,34) for y in range(2,22)]
nw=0; wf=0
for f in glob.glob(E+'infinium_glow*/**/wings/*.png',recursive=True)+glob.glob(E+'infinium_glow*/wings/*.png'):
    if f.endswith('_s.png'): continue
    im=Image.open(f).convert('RGBA'); k=clear(im,FRONT); wf+=1
    if k: nw+=k; im.save(f)
print(f'r8_post B: wings front cleared {nw} px in {wf} frames')
# ---- A2: helmet extras 32x32 + chamfer ----
H=E+'infinium_helmet_extra/'
# chamfer cube 1.768 x 0.9 x 9.96 at texOffs (0,16): box-UV net, d=9.96 w=1.768 h=0.9
d,w_,h=9.96,1.768,0.9
def zfrac(x,y):
    """chamfer net texel -> position along the helmet side (0 = back, 1 = front); None outside the net."""
    yy=y-16
    if yy<0: return None
    if yy<d:                                   # top / bottom faces (u d..d+2w), z runs down the face
        return yy/d if d<=x<d+2*w_+1 else None
    if yy<d+h+1:                               # side row: east (0..d), north (d..d+w), west (d+w..2d+w), south (2d+w..2d+2w)
        if x<d: return x/d
        if x<d+w_: return 1.0
        if x<2*d+w_: return 1-(x-d-w_)/d
        if x<2*d+2*w_+1: return 0.0
    return None
NET=[(x,y,zfrac(x,y)) for x in range(32) for y in range(16,28) if zfrac(x,y) is not None]
def side_px(src,z):
    """helmet row v9 of the side face (east face u0..7 of the head box) at z."""
    return src.getpixel((min(7,int(z*8)),9))
def grow(p):
    im=Image.open(p).convert('RGBA')
    if im.size!=(32,32):
        big=Image.new('RGBA',(32,32),(0,0,0,0)); big.paste(im,(0,0)); im=big
    return im
def paint(dst_img,src_img):
    p=dst_img.load()
    for x,y,z in NET:
        c=side_px(src_img,z)
        p[x,y]=c if c[3] else (0,0,0,0)
STY={'glow':'astral','glow_cybernetic':'cybernetic','glow_singularity':'singularity','glow_cyberpsycho':'cyberpsycho','glow_infinity':'infinity'}
import colorsys
def unpale(im):
    """no pale / near-white texels (V>0.70 needs S>=0.45): keep hue and V (max channel, so the gray dye map is unchanged), raise S."""
    p=im.load(); W,Hh=im.size; n=0
    for y in range(Hh):
        for x in range(W):
            r,g,b,a=p[x,y]
            if not a: continue
            h,s_,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
            if v>0.70 and s_<0.45:
                if s_<0.05: h=0.80                               # neutral -> purple hue
                rr,gg,bb=colorsys.hsv_to_rgb(h,0.46,v); p[x,y]=(round(rr*255),round(gg*255),round(bb*255),a); n+=1
    return n
npale=0
for f in [H+'base.png']+[q.replace('\\', '/') for q in glob.glob(H+'glow*/*.png') if '_gray' not in q]:
    f = f.replace('\\', '/')
    im=Image.open(f).convert('RGBA'); k=unpale(im)
    if k: npale+=k; im.save(f)
print('r8_post A2: un-paled helmet extra px:',npale)
base=grow(H+'base.png'); paint(base,Image.open(E+'equipment/humanoid/infinium.png').convert('RGBA')); base.save(H+'base.png')
cnt={}
for dname,st in STY.items():
    for g in ('','_gray'):
        dd=(H+dname+g).replace('\\', '/'); fr=sorted(glob.glob(dd+'/*.png'),key=lambda s:int(os.path.basename(s)[:-4]) if os.path.basename(s)[:-4].isdigit() else -1)
        fr=[f.replace('\\', '/') for f in fr if os.path.basename(f)[:-4].isdigit()]
        src_dir=(E+('infinium_glow_gray/' if g else 'infinium_glow/')+st+'/humanoid/').replace('\\', '/')
        nsrc=len(glob.glob(src_dir+'*.png'))
        for f in fr:
            i=int(os.path.basename(f)[:-4]); im=grow(f)
            s=src_dir+f'{i % max(1,nsrc)}.png'
            if os.path.exists(s): paint(im,Image.open(s).convert('RGBA'))
            im.save(f)
        cnt[dname+g]=(len(fr),nsrc)
gen_dye.accent_gray(H+'base.png',H+'accent_gray.png')
bad=[p for p in glob.glob(H+'**/*.png',recursive=True) if not p.endswith('_s.png') and Image.open(p).size!=(32,32)]
print('r8_post A2: helmet extra frames (n, suit frames):',cnt)
print('r8_post A2: helmet extra PNGs not 32x32:',len(bad))
if bad: raise SystemExit('helmet extras not 32x32: '+str(bad[:5]))
for dname,(n,ns) in cnt.items():
    if n!=ns: raise SystemExit(f'helmet extra {dname}: {n} frames, suit has {ns}')
