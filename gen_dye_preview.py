#!/usr/bin/env python3
"""preview/dye_colors.png: draft9 helmet + chestplate (worn front view + icons) in all 16 dye glow colours, plus default purple."""
import math
from PIL import Image, ImageDraw, ImageFont
import render3d_lib as r3, gen_dye as gd
R='build_drafts/draft9/assets/infinium/textures/'
def tint(img,rgb):
    r,g,b=rgb>>16&255,rgb>>8&255,rgb&255; im=img.copy(); p=im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            v,_,_,a=p[x,y]
            if a: p[x,y]=(v*r//255,v*g//255,v*b//255,a)
    return im
def setup(rgb,f):
    def tex(layer,ff):
        base=Image.open(R+f'entity/equipment/{layer}/infinium.png').convert('RGBA')
        if rgb is None: gl=Image.open(R+f'entity/infinium_glow/{layer}/{f}.png').convert('RGBA')
        else: gl=tint(Image.open(R+f'entity/infinium_glow_gray/{layer}/{f}.png').convert('RGBA'),rgb)
        if layer=='humanoid_leggings': gl=Image.new('RGBA',gl.size); base=Image.new('RGBA',base.size)   # helmet + chestplate only
        if layer=='humanoid':   # drop boots region (legs) to show helmet + chestplate
            for im in (base,gl):
                p=im.load()
                for y in range(16,32):
                    for x in range(0,16): p[x,y]=(0,0,0,0)
        c=base.copy(); c.alpha_composite(gl); return c
    r3.tex=tex
def icon(n,rgb,f):
    b=Image.open(R+f'item/{n}.png').convert('RGBA')
    sheet=Image.open(R+(f'item/cracks/{n}.png' if rgb is None else f'item/cracks_gray/{n}.png')).convert('RGBA')
    g=sheet.crop((0,16*f,16,16*f+16)); g=g if rgb is None else tint(g,rgb)
    b.alpha_composite(g); return b.resize((64,64),0)
f=6; cols=[('default (purple)',None)]+[(n.replace('_',' '),gd.PAL[i]) for i,n in enumerate(gd.NAMES)]
TW,TH=220,300; S=Image.new('RGB',(TW*6,TH*3),(36,34,42)); d=ImageDraw.Draw(S)
try: font=ImageFont.load_default(size=15)
except Exception: font=None
for i,(name,rgb) in enumerate(cols):
    setup(rgb,f); x,y=(i%6)*TW,(i//6)*TH
    im=r3.render(r3.build(f,dict(r3.POSES['fold'])),0.0,0.0,0.0,size=220,scale=7).crop((0,0,220,210))
    S.paste(im.convert('RGB'),(x,y+22))
    S.paste(icon('infinium_helmet',rgb,f).convert('RGB'),(x+40,y+232)); S.paste(icon('infinium_chestplate',rgb,f).convert('RGB'),(x+116,y+232))
    d.text((x+6,y+4),name,fill=(235,235,235),font=font)
S.save('preview/dye_colors.png'); print('wrote preview/dye_colors.png')
