#!/usr/bin/env python3
"""Spacetime Fragments (slow Astral-pace shimmer, 40 x 6 ticks = 12 s), Spacetime Crystal (fast energy + spark flickers,
80 x 2 ticks = 8 s) and the 4 style smithing templates animated with their armor style's glow field / palette / clock
(gen_tool_styles.field, same frames x frametime as the worn style). Palette = helmet shard purples, never pale/white/blue."""
import math, json, random, colorsys, os
from PIL import Image
import gen_tool_styles as ts
TX='src/main/resources/assets/infinium/textures/item/'; TAU=2*math.pi
AM=[(88,30,150),(130,60,205),(176,110,240),(196,120,248)]          # helmet shard purples (pale top tone replaced)
def safe(c):
    r,g,b=[max(0,min(255,int(v))) for v in c]; h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
    if v>0.70 and s<0.45: s=0.5                                     # pale-pixel rule
    if 160/360<=h<=262/360: h=270/360                               # no blue / cyan
    r,g,b=colorsys.hsv_to_rgb(h,s,v); return (int(r*255),int(g*255),int(b*255),255)
def save(name,frames,ft):
    sh=Image.new('RGBA',(16,16*len(frames)))
    for i,f in enumerate(frames): sh.paste(f,(0,16*i))
    sh.save(TX+name+'.png'); json.dump({"animation":{"frametime":ft,"interpolate":False}},open(TX+name+'.png.mcmeta','w'))
def shard_poly(cx,cy,pts):
    return [(cx+x,cy+y) for x,y in pts]
def inside(poly,x,y):
    c=False; n=len(poly)
    for i in range(n):
        x1,y1=poly[i]; x2,y2=poly[(i+1)%n]
        if (y1>y)!=(y2>y) and x<(x2-x1)*(y-y1)/(y2-y1)+x1: c=not c
    return c
def fragments():
    """Ordered fracture: 4 long, sharp, near-identical slivers, all parallel (axis along the / diagonal), each split into a
    lit facet and a shadow facet along its length; slow shimmer sweeping along the slivers (40 x 6 ticks)."""
    ax=(0.2079,-0.9781); nx=(0.9781,0.2079)                    # vertical, tilted 12 deg (user), facet normal
    sl=[(-3.9,0.2,6.4),(0.0,-0.3,7.6),(3.9,0.4,6.6)]   # (offset across, offset along, half length)
    F=40; out=[]
    for f in range(F):
        t=f/F; im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
        for x in range(16):
            for y in range(16):
                px,py=x+.5-8,y+.5-8; u=px*ax[0]+py*ax[1]; v=px*nx[0]+py*nx[1]
                for j,(o,s0,hl) in enumerate(sl):
                    du=u-s0; dv=v-o; w=min(1.05,1.7*max(0.0,1-abs(du)/hl)**0.8)                 # tapers to a sharp point at both ends
                    if abs(du)>hl or abs(dv)>w: continue
                    edge=abs(dv)>w-0.55 or abs(du)>hl-0.8
                    lit=dv<0                                                # parallel facets: lit side / shadow side
                    c=AM[0] if edge else (AM[2] if lit else AM[1])
                    sweep=max(0,math.cos(TAU*(t-du/18.0-j*0.11)))**8
                    c=tuple(c[i]+(AM[3][i]-c[i])*sweep*(0.9 if lit else 0.5) for i in range(3)); p[x,y]=safe(c); break
        out.append(im)
    save('spacetime_fragments',out,6)
    # old filename kept as a copy so a resource pack or a half-migrated jar still has a texture
    import shutil
    shutil.copy(TX+'spacetime_fragments.png', TX+'strange_fragments.png')
    shutil.copy(TX+'spacetime_fragments.png.mcmeta', TX+'strange_fragments.png.mcmeta')
CP={'deep':(38,8,74),'dark':(66,18,120),'mid':(104,40,176),'light':(140,66,214),'edge':(168,92,236),'hot':(196,62,196)}
def crystal():
    """Faceted elongated hexagonal point (helmet crystal): left / center / right prism faces (dark / mid / light), pyramid
    tip facets, edge highlights in deep-to-mid purples (no pale). Energy streams flow UP inside each facet (fast, 80 x 2 ticks)
    with spark flickers on the facet ridges."""
    poly=[(8,0.3),(11.6,4.2),(11.6,11.6),(8,15.7),(4.4,11.6),(4.4,4.2)]
    F=80; rnd=random.Random(11); sparks=[(rnd.choice((6,8,9,10,7)),rnd.randint(3,13),rnd.choice((1,2,3)),rnd.random()) for _ in range(8)]
    out=[]
    for f in range(F):
        t=f/F; im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
        for x in range(16):
            for y in range(16):
                X,Y=x+.5,y+.5
                if not inside(poly,X,Y): continue
                edge=not all(inside(poly,X+dx,Y+dy) for dx,dy in((.9,0),(-.9,0),(0,.9),(0,-.9)))
                tip = Y<4.2+ (abs(X-8)*0) and Y < 4.2-(abs(X-8))*0.0+ (3.6-abs(X-8))*0  # placeholder
                face = 0 if X<6.6 else (1 if X<9.6 else 2)              # left / center / right
                top  = Y < 0.3+ (abs(X-8)/3.6)*3.9 + 1.2                  # pyramid tip band
                base = (CP['deep'],CP['mid'],CP['light'])[face] if not top else (CP['dark'],CP['light'],CP['edge'])[face]
                ridge = abs(X-6.6)<0.6 or abs(X-9.6)<0.6
                flow = max(0,math.cos(TAU*(4*t + Y/7.0 + face*0.31)))**3      # streams rising inside the facet
                e = 0.55*flow*(0.6 if face==0 else 1.0)
                c = tuple(base[i]+(CP['hot'][i]-base[i])*e for i in range(3))
                if ridge and not edge: c=tuple(max(c[i],CP['light'][i]*(0.8+0.2*flow)) for i in range(3))
                if edge: c=CP['edge'] if (X>8 and Y<9) else CP['dark']   # lit upper-right rim, shadowed lower-left rim
                p[x,y]=safe(c)
        for sx,sy,m,ph in sparks:
            k=max(0,math.cos(TAU*(m*t+ph)))**14
            if k>0.35 and p[sx,sy][3]: p[sx,sy]=safe((214,80,206))
        out.append(im)
    save('spacetime_crystal',out,2)
def ingot():
    """Infinium Ingot: purple spacetime veins pulsing through the dark metal (48 x 2 ticks = 4.8 s)."""
    n='infinium_ingot'; base=Image.open(TX+n+'.png').convert('RGBA')
    if base.height>16: base=base.crop((0,0,16,16))
    bp=base.load(); op={(x,y) for x in range(16) for y in range(16) if bp[x,y][3]}
    inner=[q for q in op if all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))]
    vein=[q for q in inner if abs(math.sin(q[0]*0.9+math.sin(q[1]*0.7)*2.2))<0.28 or abs(math.sin(q[1]*1.1-q[0]*0.45+1.3))<0.18]
    F=48; out=[]
    for f in range(F):
        t=f/F; im=base.copy(); p=im.load()
        for (x,y) in vein:
            e=max(0,math.cos(TAU*(2*t-(x+y)/14.0)))**2
            c=(60+120*e,16+50*e,110+120*e); o=p[x,y]; k=0.45+0.5*e
            p[x,y]=safe(tuple(o[i]*(1-k)+c[i]*k for i in range(3)))
        for q in inner:                                           # faint shimmer on the metal
            if q in vein: continue
            k=0.10*max(0,math.cos(TAU*(t-(q[0]-q[1])/20.0)))**4; o=p[q]
            p[q]=safe(tuple(o[i]*(1-k)+(120,50,190)[i]*k for i in range(3)))
        out.append(im)
    save(n,out,2)
def templates():
    for st,(F,ft) in ts.STY.items():
        n=f'{st}_upgrade_smithing_template'; base=Image.open(TX+n+'.png').convert('RGBA')
        if base.height>16: base=base.crop((0,0,16,16))                  # idempotent
        bp=base.load(); op={(x,y) for x in range(16) for y in range(16) if bp[x,y][3]}
        A=sorted(q for q in op if all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1))))
        S,C=ts.field(st,'template',A,F); out=[]
        for f in range(F):
            sc={q:S(q,f) for q in A}; vals=sorted(sc.values(),reverse=True)
            hi=vals[max(0,int(ts.COV*len(vals))-1)]; lo=vals[min(len(vals)-1,int(ts.COV*1.5*len(vals))-1)]; top=vals[0]
            if lo>=hi: lo=hi-1e-3
            im=base.copy(); p=im.load()
            for q,v in sc.items():
                if v<=lo: continue
                k=1.0 if v>=hi else (v-lo)/(hi-lo); k=k*k*(3-2*k)
                if k<0.05: continue
                b=max(0.0,min(1.0,(v-lo)/max(1e-9,top-lo)))**1.2; k=max(k,0.55); c=C(q,f,b); o=p[q]
                p[q]=safe(tuple(o[i]*(1-k)+c[i]*k for i in range(3)))
            out.append(im)
        ts.accents(st,'tpl/'+st,A,out,F,2)
        out=[Image.eval(im,lambda v:v) for im in out]
        for im in out:
            p=im.load()
            for y in range(16):
                for x in range(16):
                    if p[x,y][3]: p[x,y]=safe(p[x,y][:3])
        save(n,out,ft)
if __name__=='__main__':
    fragments(); crystal(); ingot(); templates(); print('new items + animated templates ok')
