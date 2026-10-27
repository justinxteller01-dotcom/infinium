#!/usr/bin/env python3
"""Infinium look drafts.  Usage: python3 gen_drafts.py --style N   (N = 1..8, or 'all')
Writes a complete resource overlay to build_drafts/draftN/ (textures, models, item defs, glow_anim.properties)
and previews to preview/drafts/draftN/.  Never touches src/main/resources or out/infinium-*.jar.
Hard rule (fails with exit 1): no purple pixel in any frame outside opaque-mask minus 1-px border,
none on tool handles, helmet front-center, worn face, or trim pieces."""
import sys, os, math, random, json, shutil
from collections import deque
from PIL import Image, ImageDraw, ImageFont

MC='ref/mc/assets/minecraft/textures/'
F=40
DARK=(70,14,110); BRIGHT=(214,140,255)
TOOLS=('sword','pickaxe','axe','shovel','hoe')
ARMOR=('helmet','chestplate','leggings','boots')
ITEMS={'infinium_ingot':'netherite_ingot', **{f'infinium_{p}':f'netherite_{p}' for p in ARMOR+TOOLS}}

STYLES={
 1:dict(name='Drift',      loop=20, cov=0.09, trims={'ridge','cuffs'},
        desc='Slow drifting thin cracks: hairline ridges that slide and re-form across the metal; 20 s loop; helmet ridge + boot cuffs.'),
 2:dict(name='Lightning',  loop=12, cov=0.12, trims={'horns','shoulders','spine'},
        desc='Branching lightning fractures, glow surging outward from the roots; 12 s loop; small horns, shoulder plates, tool-head spine.'),
 3:dict(name='Pulseline',  loop=16, cov=0.10, trims={'ridge','knees','cuffs'},
        desc='Fixed fine crack network; only slow light packets travel along the cracks; 16 s loop; helmet ridge, knee guards, boot cuffs.'),
 4:dict(name='Runes',      loop=10, cov=0.12, trims={'shoulders','knees','spine'},
        desc='Etched geometric rune glyphs that light up one after another; 10 s loop; shoulder plates, knee guards, tool spine.'),
 5:dict(name='Fissure',    loop=18, cov=0.08, trims={'horns','spine'},
        desc='Sparse large jagged fissures that slowly breathe; 18 s loop; small horns, tool spine (most minimal purple).'),
 6:dict(name='Crackle',    loop=14, cov=0.14, trims={'ridge','shoulders','cuffs'},
        desc='Fine crackle glaze (Voronoi cell edges) whose cells shift subtly; 14 s loop; helmet ridge, shoulder plates, boot cuffs.'),
 7:dict(name='Embers',     loop=8,  cov=0.11, trims={'horns','knees','cuffs','spine'},
        desc='Drifting ember veins with occasional slow sparkles; 8 s loop (fastest); horns, knee guards, cuffs, spine.'),
 9:dict(name='Crackle Pulse', loop=12, cov=0.12, trims={'knees','cuffs'}, sym=True,
        desc='Draft 6 crackle-glaze cells as a fine network with slow purple pulses travelling along the cell edges (Draft 3 idea); 12 s seamless loop; short mini horns, knee guards, boot cuffs; perfect left/right mirror symmetry.'),
 8:dict(name='Circuit',    loop=20, cov=0.10, trims={'ridge','shoulders','knees','cuffs','spine'},
        desc='Right-angle circuit traces with slow energy packets and nodes; 20 s loop; full trim (ridge, shoulders, knees, cuffs, spine).'),
}

# ---------------- base textures ----------------
def darken(img, k=0.38):
    img=img.convert('RGBA').copy(); px=img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r,g,b,a=px[x,y]
            if a: px[x,y]=tuple(int(255*((c/255)**1.15)*k) for c in (r,g,b))+(a,)
    return img
def lighten(c, t=0.55, to=(96,86,92)):
    r,g,b,a=c; return (int(r+(to[0]-r)*t),int(g+(to[1]-g)*t),int(b+(to[2]-b)*t),255)
def opaque(img): px=img.load(); return {(x,y) for y in range(img.size[1]) for x in range(img.size[0]) if px[x,y][3]>0}
N4=((1,0),(-1,0),(0,1),(0,-1))
def interior(op): return {q for q in op if all((q[0]+dx,q[1]+dy) in op for dx,dy in N4)}
def handle_px(kind,x,y):
    if kind=='sword': return x<=4 and y>=11
    return kind in TOOLS and (x+y)>=14 and x<=9 and abs((x+y)-15)<=2 and y>=6
def comp(a,b): c=a.copy(); c.alpha_composite(b); return c
def netherite_palette():
    im=Image.open(MC+'entity/equipment/humanoid/netherite.png').convert('RGBA'); px=im.load()
    return sorted({px[x,y][:3] for y in range(im.size[1]) for x in range(im.size[0]) if px[x,y][3]>0}, key=lambda c:0.299*c[0]+0.587*c[1]+0.114*c[2])
PAL=netherite_palette()
def to_netherite(img):
    img=img.convert('RGBA'); px=img.load(); W,H=img.size
    l=sorted(0.299*px[x,y][0]+0.587*px[x,y][1]+0.114*px[x,y][2] for y in range(H) for x in range(W) if px[x,y][3]>0); lo,hi=l[0],l[-1]
    for y in range(H):
        for x in range(W):
            r,g,b,a=px[x,y]
            if a: px[x,y]=PAL[min(len(PAL)-1,int(((0.299*r+0.587*g+0.114*b)-lo)/max(1,hi-lo)*(len(PAL)-1)))]+(a,)
    return img

def icon_base(n, trims):
    kind=n.split('_')[-1]; src=ITEMS[n]
    img=darken(Image.open(MC+f'item/{src}.png')); px=img.load(); trim=set()
    if kind=='helmet':   # open face: remove nose + face plate (vanilla icon rows 7-11, center cols 5-10)
        for x in range(5,11):
            for y in range(7,12): px[x,y]=(0,0,0,0)
    op=opaque(img)
    def add(x,y,base=None):
        if 0<=x<16 and 0<=y<16:
            c=base or (40,34,38,255); px[x,y]=lighten(c,0.35); trim.add((x,y))
    def lite(q,t=0.55):
        if q in op: px[q]=lighten(px[q],t); trim.add(q)
    if kind=='helmet':
        if 'minihorns' in trims:          # subtle, short, symmetric
            for q in ((4,3),(3,2),(11,3),(12,2)): add(*q)
        if 'horns' in trims:
            for q in ((4,3),(3,2),(3,1),(11,3),(12,2),(12,1)): add(*q)
        if 'ridge' in trims:
            for x in range(6,10): lite((x,4))
    if kind=='chestplate' and 'shoulders' in trims:
        for x in list(range(1,6))+list(range(10,15)): add(x,1)
        for x in (2,3,4,11,12,13): lite((x,2),0.4)
    if kind=='leggings' and 'knees' in trims:
        for x in (4,5,10,11): lite((x,8))
    if kind=='boots' and 'cuffs' in trims:
        for q in ((4,4),(4,5),(11,4),(11,5),(3,4),(12,4)): lite(q)
    if kind in TOOLS and 'spine' in trims:
        for (x,y) in sorted(op):
            if y<=7 and x>=7 and (x,y-1) not in op and not handle_px(kind,x,y): lite((x,y),0.5)
    op=opaque(img)
    allowed={q for q in interior(op) if not handle_px(kind,*q)}-trim
    if kind=='helmet': allowed={q for q in allowed if not (5<=q[0]<=10 and q[1]>5)}   # crown ok; front-center/face never
    return img, allowed, interior(op)

def worn_bases(trims):
    hum=darken(Image.open(MC+'entity/equipment/humanoid/netherite.png'))
    leg=darken(Image.open(MC+'entity/equipment/humanoid_leggings/netherite.png'))
    wing=darken(to_netherite(Image.open(MC+'entity/equipment/wings/elytra.png')))
    hp=hum.load()
    for x in range(9,15):                      # open face: remove nose + lower face plate
        for y in range(11,16): hp[x,y]=(0,0,0,0)
    T={'humanoid':set(),'humanoid_leggings':set(),'wings':set()}
    def lite(img,key,q,t=0.55):
        p=img.load()
        if p[q][3]>0: p[q]=lighten(p[q],t); T[key].add(q)
    if 'ridge' in trims:
        for y in range(0,8): lite(hum,'humanoid',(11,y)); lite(hum,'humanoid',(12,y))
        for y in range(8,11): lite(hum,'humanoid',(27,y)); lite(hum,'humanoid',(28,y))
    if 'minihorns' in trims:   # top-face corner nubs + matching side-face tops (mirror pairs)
        for q in ((9,1),(14,1),(9,2),(14,2),(1,8),(22,8),(6,8),(17,8)): lite(hum,'humanoid',q,0.6)
    if 'horns' in trims:
        for q in ((8,0),(9,0),(8,1),(15,0),(14,0),(15,1),(0,8),(1,8),(16,8),(17,8)): lite(hum,'humanoid',q,0.6)
    if 'shoulders' in trims:
        for x in range(44,48):
            for y in range(16,20): lite(hum,'humanoid',(x,y))
        for x in range(40,56):
            for y in (20,21): lite(hum,'humanoid',(x,y),0.45)
    if 'knees' in trims:
        for x in range(0,12):
            for y in (24,25): lite(leg,'humanoid_leggings',(x,y),0.3)
    if 'cuffs' in trims:
        for x in range(0,16):
            for y in (26,27): lite(hum,'humanoid',(x,y),0.5)
    if 'spine' in trims:
        for y in range(2,22): lite(wing,'wings',(24,y),0.5); lite(wing,'wings',(25,y),0.35)
    out={}
    for key,img in (('humanoid',hum),('humanoid_leggings',leg),('wings',wing)):
        op=opaque(img); a=interior(op)-T[key]
        if key=='humanoid':
            a-={(x,y) for x in list(range(8,16))+list(range(40,48)) for y in range(8,16)
                if not (FRONT_FRAME and x<16 and (x in (8,9,14,15) or y==8))}   # worn face (style 9: helmet frame around the open face allowed)
            if FRONT_FRAME:   # h21 forehead shard: keep a dark netherite clear zone (shard + 1 px) on the front and the top-front edge
                a-={(x,y) for x in range(10,14) for y in range(8,11)} | {(x,0) for x in range(10,14)}
        out[key]=(img,a,interior(op))
    return out

# ---------------- pattern fields ----------------
DIRS8=[(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
def walk_net(A, rnd, n, length, branch=0.0, turn=0.3, ortho=False):
    A=sorted(A); aset=set(A); d={}
    dirs=[(1,0),(0,1),(-1,0),(0,-1)] if ortho else DIRS8
    for _ in range(n):
        if not A: break
        x,y=rnd.choice(A); di=rnd.randrange(len(dirs)); st=[(x,y,di,0.0,length)]
        while st:
            x,y,di,dist,left=st.pop()
            for _s in range(left):
                if (x,y) in aset and (x,y) not in d: d[(x,y)]=dist
                if rnd.random()<turn: di=(di+rnd.choice((-1,1)))%len(dirs)
                if branch and rnd.random()<branch and left>4: st.append((x,y,(di+rnd.choice((-1,1))*(1 if ortho else 2))%len(dirs),dist,left//2))
                nx,ny=x+dirs[di][0],y+dirs[di][1]
                if (nx,ny) not in aset: di=rnd.randrange(len(dirs)); continue
                x,y=nx,ny; dist+=1
    return d

def field_fn(style, A, W, H, seed):
    """Returns S(f) -> {pixel: score}; larger = more purple. All periodic in f over F."""
    rnd=random.Random(seed); TAU=2*math.pi; A=sorted(A)
    area=max(1,len(A)); scale=1.0 if W<=16 else 1.6
    if style in (1,7):
        waves=[]
        for i in range(3):
            ang=rnd.uniform(0,TAU); lam=rnd.uniform(6,10)*scale
            waves.append((math.cos(ang)*TAU/lam, math.sin(ang)*TAU/lam, rnd.uniform(0,TAU)))
        wp=[(rnd.uniform(5,8)*scale, rnd.uniform(0,TAU), rnd.choice((1,-1))) for _ in range(2)]
        sparks=[(rnd.choice(A), rnd.randrange(F)) for _ in range(max(1,area//40))] if style==7 else []
        def S(f):
            t=TAU*f/F; out={}
            for (x,y) in A:
                wx=x+2.0*math.sin(TAU*y/wp[0][0]+wp[0][2]*t+wp[0][1]); wy=y+2.0*math.sin(TAU*x/wp[1][0]+wp[1][2]*t+wp[1][1])
                out[(x,y)]=-abs(sum(math.sin(kx*wx+ky*wy+ph) for kx,ky,ph in waves))
            for (q,f0) in sparks:
                dd=min((f-f0)%F,(f0-f)%F)
                if dd<4: out[q]=out.get(q,-9)+ (4-dd)*0.6
            return out
        return S
    if style in (2,3,5,8):
        if style==2: net=walk_net(A,rnd,max(2,area//45),int(10*scale),branch=0.18,turn=0.22)
        if style==3: net=walk_net(A,rnd,max(3,area//14),4,turn=0.35)
        if style==5: net=walk_net(A,rnd,max(1,area//120),int(14*scale),turn=0.15)
        if style==8: net=walk_net(A,rnd,max(2,area//30),int(8*scale),branch=0.12,turn=0.25,ortho=True)
        lam={2:14.0,3:9.0,5:24.0,8:7.0}[style]
        def S(f):
            t=f/F; out={}
            for q in A:
                if q not in net: out[q]=-1e6; continue
                d=net[q]
                if style==5: v=0.6+0.4*math.cos(TAU*(t+d/lam))                 # breathing
                elif style==8: v=math.cos(TAU*(d/lam - 2*t))**8                   # sharp packets
                else: v=0.5+0.5*math.cos(TAU*(d/lam - t))
                out[q]=v+0.001*((q[0]*31+q[1]*17)%7)
            return out
        return S
    if style==4:
        glyphs=[[(0,0),(1,0),(2,0),(1,1),(1,2)],[(0,0),(0,1),(0,2),(1,2),(2,2)],[(0,0),(1,1),(2,2),(2,1),(2,0)],
                [(1,0),(0,1),(1,1),(2,1),(1,2)],[(0,0),(2,0),(1,1),(0,2),(2,2)],[(0,0),(1,0),(2,0),(2,1),(2,2),(1,2)]]
        aset=set(A); net={}; cells=[]
        step=5 if W<=16 else 6
        for cy in range(0,H,step):
            for cx in range(0,W,step):
                g=rnd.choice(glyphs); ox,oy=cx+rnd.randrange(2),cy+rnd.randrange(2)
                pts=[(ox+a,oy+b) for a,b in g]
                if sum(p in aset for p in pts)>=len(pts)-1 and rnd.random()<0.8:
                    ph=rnd.random()
                    for p in pts:
                        if p in aset: net[p]=ph
        def S(f):
            t=f/F; out={}
            for q in A:
                if q not in net: out[q]=-1e6; continue
                out[q]=0.5+0.5*math.cos(TAU*(t-net[q]))
            return out
        return S
    if style==6:
        k=max(4,area//(14 if W<=16 else 22))
        seeds=[(rnd.choice(A),rnd.uniform(0.6,1.4),rnd.uniform(0,TAU),rnd.choice((1,-1))) for _ in range(k)]
        def S(f):
            t=TAU*f/F; pts=[(sx+r*math.cos(d*t+ph), sy+r*math.sin(d*t+ph)) for (sx,sy),r,ph,d in seeds]; out={}
            for (x,y) in A:
                ds=sorted((x-px)**2+(y-py)**2 for px,py in pts); out[(x,y)]=-(math.sqrt(ds[1])-math.sqrt(ds[0]))
            return out
        return S
    if style==9:
        k=max(4,area//(13 if W<=16 else 20))
        seeds=[(rnd.choice(A),rnd.uniform(0.5,1.1),rnd.uniform(0,TAU),rnd.choice((1,-1))) for _ in range(k)]
        lam=7.0 if W<=16 else 9.0
        def S(f):
            t=f/F; pts=[(sx+r*math.cos(d*TAU*t+ph), sy+r*math.sin(d*TAU*t+ph)) for (sx,sy),r,ph,d in seeds]; out={}
            for (x,y) in A:
                ds=sorted(math.hypot(x-px,y-py) for px,py in pts)
                edge=-(ds[1]-ds[0])                       # 0 on cell edges
                pulse=0.5+0.5*math.cos(TAU*(ds[0]/lam - t)) # packets travelling outward along edges
                out[(x,y)]=edge+0.9*pulse+0.45*math.cos(TAU*((x+y)/6.0 - t))+0.3*math.cos(TAU*(y/4.0 + 2*t))   # slow drift so even tiny faces animate
            return out
        return S
    raise ValueError(style)

def make_frames(style, A, W, H, seed, cov, M=None, groups=(), boost=None):
    DARK,BRIGHT=((30,3,56),(160,70,228)) if style>=9 else (globals()['DARK'],globals()['BRIGHT'])
    S=field_fn(style, A, W, H, seed); A=sorted(A); frames=[]; covs=[]
    gs=[]; bmap={}
    for gi0,g in enumerate(groups):
        if set(g)&set(A): bmap[len(gs)]=(boost or {}).get(gi0,1.0); gs.append(sorted(set(g)&set(A)))
    boost=bmap
    rest=set(A)
    for g in gs: rest-=set(g)
    gs.append(sorted(rest))
    for f in range(F):
        s=S(f)
        if M: s={q:max(s[q],s[M(q)]) for q in A}     # mirror-symmetric score -> symmetric frame
        im=Image.new('RGBA',(W,H),(0,0,0,0)); p=im.load(); n=0
        for gi,G in enumerate(gs):
            if len(G)<=12 and gi<len(gs)-1:       # small faces: a slow travelling bump guarantees visible motion (mirror-pair symmetric)
                keys=sorted({min(q,M(q)) if M else q for q in G}); rk={k:i for i,k in enumerate(keys)}
                for q in G:
                    k_=min(q,M(q)) if M else q
                    s[q]=s[q]+1.5*math.cos(2*math.pi*(f/F - rk[k_]/max(1,len(keys))))
            c_=min(0.45,cov*(boost or {}).get(gi,1.0)) if gi<len(gs)-1 else cov
            vals=sorted((s[q] for q in G), reverse=True)
            if not vals: continue
            i1=max(0,min(len(vals)-1,int(c_*len(vals))-1)); i0=max(i1+1 if i1+1<len(vals) else i1,min(len(vals)-1,int(c_*1.5*len(vals))-1))
            while i0<len(vals)-1 and vals[i0]>=vals[i1]: i0+=1     # mirror pairs tie: lower bound must be strictly below the top value
            hi_,lo_=vals[i1],vals[i0]
            if lo_>=hi_: lo_=hi_-1e-3
            if lo_<-1e5: lo_=hi_-1e-3
            for q in G:
                v=s[q]
                if v<=lo_ or v<-1e5: continue
                k=1.0 if v>=hi_ else (v-lo_)/max(1e-9,hi_-lo_)
                k=k*k*(3-2*k)
                if k<0.05: continue
                if style>=9:
                    b=max(0.0,min(1.0,(v-lo_)/max(1e-9,vals[0]-lo_)))**1.4     # deep purple .. highlight inside every lit patch
                    c=tuple(DARK[i]+(BRIGHT[i]-DARK[i])*b for i in range(3)); k=max(k,0.55)
                    if COLOR_FN: c=COLOR_FN(q,f,b)          # per-style palette hook (gen_variants: Singularity / CyberPsycho)
                else: c=tuple(DARK[i]+(BRIGHT[i]-DARK[i])*k for i in range(3))
                p[q]=tuple(int(cc*k) for cc in c)+(int(255*k),)
                if k>0.5: n+=1
        frames.append(im); covs.append(n/max(1,len(A)))
    return frames, sum(covs)/len(covs)

# ---------------- driver ----------------
def check(frames, allowed, name):
    bad=0
    for fr in frames:
        p=fr.load(); W,H=fr.size
        bad+=sum(1 for y in range(H) for x in range(W) if p[x,y][3]>0 and (x,y) not in allowed)
    if bad: raise SystemExit(f'FAIL {name}: {bad} purple pixels outside the allowed interior mask')

# ---- symmetry ----
def M_icon_v(q): return (15-q[0],q[1])
def M_icon_d(q): return (15-q[1],15-q[0])          # mirror across the handle diagonal x+y=15
def M_hum(q):
    x,y=q
    if y<8:
        for a,b,c in ((8,15,23),(16,23,39),(40,47,87),(48,55,103)):
            if a<=x<=b: return (c-x,y)
    elif y<16:
        for a,b,c in ((0,23,23),(24,31,55),(32,55,87),(56,63,119)):
            if a<=x<=b: return (c-x,y)
    elif y<20:
        for a,b,c in ((20,27,47),(28,35,63)):
            if a<=x<=b: return (c-x,y)
    else:
        for a,b,c in ((16,31,47),(32,39,71)):
            if a<=x<=b: return (c-x,y)
    return q          # limbs & wings: the model renders the left side as a mirror of the right UV
def M_id(q): return q
def sym_base(img,M):
    p=img.load(); W,H=img.size
    for y in range(H):
        for x in range(W):
            m=M((x,y))
            if m!=(x,y) and (x,y)<m and p[x,y][3] and p[m][3]: p[m]=p[x,y]
def check_sym(frames,M,name,base=None):
    bad=0
    for fr in ([base] if base else [])+frames:
        p=fr.load(); W,H=fr.size
        for y in range(H):
            for x in range(W):
                m=M((x,y))
                if p[x,y]!=p[m] and (fr is not base or (p[x,y][3] and p[m][3])): bad+=1
    if bad: raise SystemExit(f'FAIL symmetry {name}: {bad} asymmetric pixels')
    return 'ok'
def pdiff(a,b):
    pa,pb=a.load(),b.load(); W,H=a.size
    return sum(abs(pa[x,y][i]-pb[x,y][i]) for y in range(H) for x in range(W) for i in range(4))
def check_loop(frames,name):
    adj=sorted(pdiff(frames[i],frames[i+1]) for i in range(len(frames)-1))
    seam=pdiff(frames[-1],frames[0]); med=adj[len(adj)//2]
    p90=adj[int(0.9*(len(adj)-1))]
    if seam>max(2.0*med, p90, 1): raise SystemExit(f'FAIL loop seam {name}: seam {seam} vs median adjacent {med}, max {adj[-1]}')
    return seam/max(1,med)
SYMLOG=[]
FRONT_FRAME=False
COLOR_FN=None
# per-face coverage multipliers (style 9). face order per box: top,bottom,right,front,left,back
FACE_BOOST={'humanoid':{3:1.5,5:1.5,23:1.6,27:1.8,29:1.6},'humanoid_leggings':{9:3.5,8:1.6,10:1.6,11:2.0,15:1.7,17:1.6}}
BOXES={'humanoid':[(0,0,8,8,8),(32,0,8,8,8),(16,16,8,12,4),(40,16,4,12,4),(0,16,4,12,4)],
       'humanoid_leggings':[(16,16,8,12,4),(0,16,4,12,4)], 'wings':[(22,0,10,20,2)]}
def face_groups(key):
    out=[]
    for u,v,w,h,d in BOXES[key]:
        for (fu,fv,fw,fh) in ((u+d,v,w,d),(u+d+w,v,w,d),(u,v+d,d,h),(u+d,v+d,w,h),(u+d+w,v+d,d,h),(u+2*d+w,v+d,w,h)):
            out.append({(x,y) for x in range(fu,fu+fw) for y in range(fv,fv+fh)})
    return out
def clean_alpha(img, minsize=4):
    p=img.load(); W,H=img.size
    for y in range(H):
        for x in range(W):
            r,g,b,a=p[x,y]; p[x,y]=(r,g,b,255) if a>=128 else (0,0,0,0)
    seen=set()
    for y in range(H):
        for x in range(W):
            if p[x,y][3] and (x,y) not in seen:
                comp_=[];dq=deque([(x,y)]);seen.add((x,y))
                while dq:
                    q=dq.popleft();comp_.append(q)
                    for dx,dy in N4:
                        n=(q[0]+dx,q[1]+dy)
                        if 0<=n[0]<W and 0<=n[1]<H and n not in seen and p[n][3]: seen.add(n);dq.append(n)
                if len(comp_)<minsize:
                    for q in comp_: p[q]=(0,0,0,0)
def check_faces_animate(frames, groups, allowed, name):
    dead=[]
    for gi,G in enumerate(groups):
        A=[q for q in G if q in allowed]
        if len(A)<2: continue      # 0-1 paintable px (2-px wing edge etc.): nothing can move
        seqs=[tuple(fr.getpixel(q) for q in A) for fr in frames]
        if not any(any(c[3] for c in sq) for sq in seqs) or len(set(seqs))<2: dead.append(gi)
    if dead: raise SystemExit(f'FAIL static face(s) {name}: groups {[(g,sum(1 for q in groups[g] if q in allowed)) for g in dead]}')


def sheet(frs):
    W,H=frs[0].size; s=Image.new('RGBA',(W,H*len(frs)))
    for i,f in enumerate(frs): s.paste(f,(0,H*i))
    return s

def build(style):
    global FRONT_FRAME; FRONT_FRAME=bool(STYLES[style].get('sym'))
    st=STYLES[style]; D=f'build_drafts/draft{style}/'; PV=f'preview/drafts/draft{style}/'
    shutil.rmtree(D,ignore_errors=True); os.makedirs(PV,exist_ok=True)
    R=D+'assets/infinium/'; T=R+'textures/'
    for d in ('item/cracks','entity/equipment/humanoid','entity/equipment/humanoid_leggings','entity/equipment/wings'): os.makedirs(T+d,exist_ok=True)
    os.makedirs(R+'items',exist_ok=True); os.makedirs(R+'models/item/cracks',exist_ok=True)
    frametime=max(1,round(st['loop']*20/F)); frame_ms=int(st['loop']*1000/F)
    gen_display=json.load(open('ref/mc/assets/minecraft/models/item/generated.json'))['display']
    hand_display=dict(gen_display); hand_display.update(json.load(open('ref/mc/assets/minecraft/models/item/handheld.json'))['display'])
    icons={}; report=[]
    for i,n in enumerate(ITEMS):
        kind=n.split('_')[-1]; base,allowed,_=icon_base(n, st['trims'])
        cov=st['cov']*(1.25 if kind in('leggings','boots') else 1.0)
        M=None
        if st.get('sym'):
            M=M_icon_d if (kind in TOOLS or kind=='ingot') else M_icon_v
            sym_base(base,M)
            if kind in TOOLS: allowed=opaque(base)                       # tools: border + handle exclusion relaxed, never transparent px
            allowed={q for q in allowed if M(q) in allowed}
            cov=0.2 if kind in TOOLS else max(0.105*(1.1 if kind in('leggings','boots') else 1.0), 2.0/max(1,len(allowed)))
        frs,cv=make_frames(style, allowed, 16, 16, 500*style+i, cov, M); check(frs,allowed,n)
        if M: check_sym(frs,M,n,base); SYMLOG.append((n,check_loop(frs,n)))
        base.save(T+f'item/{n}.png'); sheet(frs).save(T+f'item/cracks/{n}.png')
        json.dump({"animation":{"frametime":frametime,"interpolate":True}},open(T+f'item/cracks/{n}.png.mcmeta','w'))
        tool=kind in TOOLS
        json.dump({"model":{"type":"minecraft:composite","models":[{"type":"minecraft:model","model":f"infinium:item/{n}"},{"type":"minecraft:model","model":f"infinium:item/cracks/{n}"}]}},open(R+f'items/{n}.json','w'))
        json.dump({"parent":"minecraft:item/handheld" if tool else "minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}},open(R+f'models/item/{n}.json','w'))
        json.dump({"gui_light":"front","textures":{"c":f"infinium:item/cracks/{n}","particle":f"infinium:item/{n}"},
            "elements":[{"from":[0,0,7.49],"to":[16,16,8.51],"shade":False,"light_emission":15,
            "faces":{"south":{"uv":[0,0,16,16],"texture":"#c"},"north":{"uv":[16,0,0,16],"texture":"#c"}}}],
            "display":hand_display if tool else gen_display},open(R+f'models/item/cracks/{n}.json','w'))
        icons[n]=(base,frs); report.append((n,cv))
    worn={}
    for j,(key,(img,allowed,_)) in enumerate(worn_bases(st['trims']).items()):
        cov=st['cov']*(1.25 if key=='humanoid_leggings' else 1.0)
        M=None
        if st.get('sym'):
            M=M_hum if key!='wings' else M_id
            sym_base(img,M); allowed={q for q in allowed if M(q) in allowed}
            cov=0.105*(1.1 if key=='humanoid_leggings' else 1.0)
        grp=[]
        if st.get('sym'):
            if key=='wings': clean_alpha(img); allowed=interior(opaque(img))&allowed
            grp=face_groups(key)      # every face thresholds on its own -> every visible face gets animated purple
        frs,cv=make_frames(style, allowed, img.size[0], img.size[1], 900*style+j, cov, M, grp, FACE_BOOST.get(key) if st.get('sym') else None); check(frs,allowed,key)
        if M: check_sym(frs,M,key,img); SYMLOG.append((key,check_loop(frs,key))); check_faces_animate(frs,grp,allowed,key)
        img.save(T+f'entity/equipment/{key}/infinium.png'); os.makedirs(T+f'entity/infinium_glow/{key}',exist_ok=True)
        for f,fr in enumerate(frs): fr.save(T+f'entity/infinium_glow/{key}/{f}.png')
        worn[key]=(img,frs); report.append((key,cv))
    open(R+'glow_anim.properties','w').write(f'frames={F}\nframeMs={frame_ms}\n')
    # ---- previews ----
    S=6; names=list(ITEMS); dur=int(st['loop']*1000/F)
    gif=[]
    for f in range(F):
        g=Image.new('RGBA',(len(names)*16*S,16*S),(52,50,58,255))
        for i,n in enumerate(names): b,frs=icons[n]; g.alpha_composite(comp(b,frs[f]).resize((16*S,16*S),0),(i*16*S,0))
        gif.append(g)
    gif[0].save(PV+'item_icons.png')
    [g.convert('RGB') for g in gif][0].save(PV+'item_icons.gif',save_all=True,append_images=[g.convert('RGB') for g in gif[1:]],duration=dur,loop=0)
    import render3d_lib as r3
    r3.render_gif(T, F, dur, PV+'render3d.gif', PV+'render3d.png')
    if st.get('sym'):
        vs=r3.view_symmetry(T,F)
        print('view-space symmetry (mismatched pixels over all %d frames): '%F+', '.join(f'{k}={v}' for k,v in vs.items()))
        if any(vs.values()): raise SystemExit('FAIL view-space symmetry')
        r3.render_views(T, 0, PV+'render3d_front_back.png', gif=PV+'render3d_front_back.gif', frames=F, dur=dur)
    open(PV+'description.txt','w').write(f"Draft {style} - {st['name']}: {st['desc']}\n")
    print(f"draft{style} {st['name']}: loop {st['loop']}s ({F} frames x {frametime} ticks)  coverage: "+
          ', '.join(f"{n.replace('infinium_','')}={100*c:.0f}%" for n,c in report))
    return icons, worn

if __name__=='__main__':
    arg=sys.argv[sys.argv.index('--style')+1] if '--style' in sys.argv else 'all'
    for s in (range(1,10) if arg=='all' else [int(arg)]): build(s)
    if SYMLOG: print('symmetry check: PASS (all frames)  loop seam/median-adjacent: '+', '.join(f'{n.replace("infinium_","")}={r:.2f}' for n,r in SYMLOG))
