#!/usr/bin/env python3
"""Glow recolouring by dye. Usage: python3 gen_dye.py <resource root, e.g. src/main/resources>
Writes grayscale glow textures (+ tinted via item tint / vertex colour), tinted item model defs,
144 smithing_transform recipes (no template; netherite-tier Infinium item + dye), and GlowColor palette check."""
import sys, os, json, glob, shutil
from PIL import Image
NAMES=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
DIFF=[0xF9FFFE,0xF9801D,0xC74EBD,0x3AB3DA,0xFED83D,0x80C71F,0xF38BAA,0x474F52,0x9D9D97,0x169C9C,0x8932B8,0x3C44AA,0x835432,0x5E7C16,0xB02E26,0x1D1D21]
OVR={'white':0xE8ECFF,'black':0x5A5866,'gray':0x9A9AA6,'light_gray':0xC8C8D2}
def glow_rgb(i):
    n=NAMES[i]
    if n in OVR: return OVR[n]
    c=DIFF[i]; r,g,b=c>>16&255,c>>8&255,c&255; m=max(r,g,b); k=255/m
    return (min(255,int(r*k))<<16)|(min(255,int(g*k))<<8)|min(255,int(b*k))
PAL=[glow_rgb(i) for i in range(16)]
ITEMS=['infinium_'+p for p in ('helmet','chestplate','leggings','boots','sword','pickaxe','axe','shovel','hoe')]
def gray(src,dst):
    """Dye gradient map: grey = the original colour's brightness (max channel, absolute - no per-frame normalisation), so the
    vertex tint (dye colour at full brightness, GlowColor.RGB) reproduces the style's deep->mid->bright structure and brightness
    levels in the dye hue (red Singularity = deep..bright reds). White / gray / black dyes use silver / ash / smoke tints."""
    im=Image.open(src).convert('RGBA'); p=im.load(); W,H=im.size
    for y in range(H):
        for x in range(W):
            r,g,b,a=p[x,y]
            if a: v=max(r,g,b); p[x,y]=(v,v,v,a)
    os.makedirs(os.path.dirname(dst),exist_ok=True); im.save(dst)
def accent_gray(src,dst):
    """Non-netherite accent pixels only (purple/pink: hue 250-350, S>0.35, V>0.12) as brightness grey, rest transparent.
    Drawn tinted over the undyed base so dyes recolour the shard / face frame accents while netherite stays netherite."""
    import colorsys
    im=Image.open(src).convert('RGBA'); p=im.load(); W,H=im.size; n=0
    for y in range(H):
        for x in range(W):
            r,g,b,a=p[x,y]
            h,s_,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
            if a and s_>0.35 and v>0.12 and 250<=h*360<=350: m=max(r,g,b); p[x,y]=(m,m,m,a); n+=1
            else: p[x,y]=(0,0,0,0)
    os.makedirs(os.path.dirname(dst),exist_ok=True); im.save(dst); print('accent px',os.path.basename(src),n)
def main(R):
    R = R.replace('\\', '/')
    A=R+'/assets/infinium/'; T=A+'textures/'
    # worn + wings glow
    for src in glob.glob(T+'entity/infinium_glow/*/*.png'):
        src = src.replace('\\', '/')
        gray(src, src.replace('/infinium_glow/','/infinium_glow_gray/'))
    if os.path.exists(T+'entity/infinium_helmet_extra/base.png'): accent_gray(T+'entity/infinium_helmet_extra/base.png',T+'entity/infinium_helmet_extra/accent_gray.png')
    for src in [s for s in glob.glob(T+'entity/infinium_helmet_extra/glow_*/*.png') if not os.path.dirname(s).endswith('_gray')]:
        src = src.replace('\\', '/')
        d=os.path.dirname(src); gray(src, d+'_gray/'+os.path.basename(src))
    for src in glob.glob(T+'entity/infinium_helmet_extra/glow/*.png'):
        src = src.replace('\\', '/')
        d=os.path.dirname(src); gray(src, d+'_gray/'+os.path.basename(src))
    # item glow sheets + models + item defs
    for n in ITEMS:
        gray(T+f'item/cracks/{n}.png', T+f'item/cracks_gray/{n}.png')
        if os.path.exists(T+f'item/cracks/{n}.png.mcmeta'): shutil.copy(T+f'item/cracks/{n}.png.mcmeta',T+f'item/cracks_gray/{n}.png.mcmeta')
        m=json.load(open(A+f'models/item/cracks/{n}.json'))
        m['textures']={'c':f'infinium:item/cracks_gray/{n}','particle':m['textures'].get('particle',f'infinium:item/{n}')}
        for e in m['elements']:
            for fc in e['faces'].values(): fc['tintindex']=0
        os.makedirs(A+'models/item/cracks_gray',exist_ok=True); json.dump(m,open(A+f'models/item/cracks_gray/{n}.json','w'),indent=1)
        base={"type":"minecraft:model","model":f"infinium:item/{n}"}
        purple={"type":"minecraft:model","model":f"infinium:item/cracks/{n}"}
        sel={"type":"minecraft:select","property":"minecraft:component","component":"infinium:glow_color",
             "cases":[{"when":NAMES[i],"model":{"type":"minecraft:model","model":f"infinium:item/cracks_gray/{n}",
                      "tints":[{"type":"minecraft:constant","value":(0xFF<<24|PAL[i])-(1<<32)}]}} for i in range(16)],
             "fallback":purple}
        json.dump({"model":{"type":"minecraft:composite","models":[base,sel]}},open(A+f'items/{n}.json','w'),indent=1)
    # recipes (data pack lives next to assets)
    D=R+'/data/infinium/recipe/'
    if os.path.isdir(R+'/data/infinium'):
        os.makedirs(D,exist_ok=True)
        for f in glob.glob(D+'glow_dye_*.json'): os.remove(f)
        for n in ITEMS:
            kind=n.split('_',1)[1]
            for i,c in enumerate(NAMES):
                res={"id":f"infinium:{n}","components":{"infinium:glow_color":c}}
                if kind in ('sword','pickaxe','axe','shovel','hoe'):   # tools only: dye in the template slot on the first upgrade (armor needs a style template)
                    json.dump({"type":"minecraft:smithing_transform","template":f"minecraft:{c}_dye","base":f"minecraft:netherite_{kind}",
                               "addition":"infinium:infinium_ingot","result":res},open(D+f'glow_dye_upgrade_{n}_{c}.json','w'),indent=1)
                # recolour: dye in the template slot + Infinium item, addition slot empty
                json.dump({"type":"minecraft:smithing_transform","template":f"minecraft:{c}_dye","base":f"infinium:{n}",
                           "result":res},open(D+f'glow_dye_{n}_{c}.json','w'),indent=1)
        for f in glob.glob(D+'glow_dye_infinity_*.json'): os.remove(f)
        for p in ('helmet','chestplate','leggings','boots'):
            for c in NAMES:
                json.dump({"type":"minecraft:smithing_transform","template":f"minecraft:{c}_dye","base":f"infinium:infinity_{p}",
                           "result":{"id":f"infinium:infinity_{p}","components":{"infinium:glow_color":c}}},open(D+f'glow_dye_infinity_{p}_{c}.json','w'),indent=1)
    print('dye resources written to',R)
if __name__=='__main__':
    if '--java-table' in sys.argv: print(', '.join(f'0x{c:06X}' for c in PAL))
    else: main(sys.argv[1])
