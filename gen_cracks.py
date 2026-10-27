# Infinium v2 look: vanilla netherite base + glowing purple cracks with a slow flowing wave.
# Item icons: animated (atlas .mcmeta) crack overlay rendered by a light_emission=15 element model.
# Worn armor/wings: 32 separate frame textures, selected by time in EquipmentLayerRendererMixin (eyes render type).
import math, os, random, json, colorsys
from PIL import Image
MC='ref/mc/assets/minecraft/textures/'
RES='src/main/resources/assets/infinium/'
OUT=RES+'textures/'
F=48            # frames per loop (48 x 2 ticks = 4.8 s)
WAVELEN=10.0    # pixels per wave along the crack
DARK=(112,34,178); BRIGHT=(222,150,255)

def darken(img, k=0.38, keep=None):
    """Near-black netherite: scale values (keeps relative shading), slight gamma lift so form still reads."""
    img=img.convert('RGBA').copy(); px=img.load(); W,H=img.size
    for y in range(H):
        for x in range(W):
            r,g,b,a=px[x,y]
            if not a: continue
            kk=0.75 if keep and keep(x,y) else k
            px[x,y]=tuple(int(255*((c/255)**1.15)*kk) for c in (r,g,b))+(a,)
    return img

def opaque(img): px=img.load(); W,H=img.size; return {(x,y) for y in range(H) for x in range(W) if px[x,y][3]>0}

def make_fine_cracks(allowed, seed, cell, W, H, lmin=3, lmax=5):
    """Fine, even network: one short thin vein per cell (stratified starts), no branching."""
    rnd=random.Random(seed); d={}; aset=set(allowed)
    dirs=[(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
    for cy in range(0,H,cell):
        for cx in range(0,W,cell):
            cand=[(x,y) for (x,y) in aset if cx<=x<cx+cell and cy<=y<cy+cell]
            if not cand: continue
            x,y=rnd.choice(cand); di=rnd.randrange(8); dist=rnd.uniform(0,WAVELEN)
            for s in range(rnd.randint(lmin,lmax)):
                if (x,y) not in d: d[(x,y)]=dist
                r=rnd.random()
                if r<0.3: di=(di+1)%8
                elif r<0.6: di=(di-1)%8
                nx,ny=x+dirs[di][0],y+dirs[di][1]
                if (nx,ny) not in aset: break
                x,y=nx,ny; dist+=1
    return d

MAXGAP=2  # Chebyshev px: every allowed pixel must be within this distance of a crack

def gap_map(cr, aset):
    """BFS (8-neighbour, through allowed pixels) distance from nearest crack pixel."""
    from collections import deque
    dist={p:0 for p in cr if p in aset}; q=deque(dist)
    while q:
        x,y=q.popleft()
        for dx in(-1,0,1):
            for dy in(-1,0,1):
                n=(x+dx,y+dy)
                if n in aset and n not in dist: dist[n]=dist[(x,y)]+1; q.append(n)
    return {p:dist.get(p,999) for p in aset}

def fill_gaps(cr, allowed, seed, lmin=2, lmax=3, target=0.0, maxgap=None):
    mg=MAXGAP if maxgap is None else maxgap
    """Blue-noise style top-up: repeatedly start a thin vein at the farthest-from-crack pixel until max gap <= MAXGAP."""
    rnd=random.Random(seed*7+1); aset=set(allowed)
    dirs=[(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
    for _ in range(400):
        g=gap_map(cr,aset)
        if not g: break
        far=max(g.values())
        if far<=mg and len(cr)>=target*len(aset): break
        cands=[p for p,v in g.items() if v==far]; x,y=rnd.choice(sorted(cands))
        di=rnd.randrange(8); dist=rnd.uniform(0,WAVELEN)
        for s_ in range(rnd.randint(lmin,lmax)):
            cr.setdefault((x,y),dist)
            nx,ny=x+dirs[di][0],y+dirs[di][1]
            if (nx,ny) not in aset:
                di=rnd.randrange(8); continue
            x,y=nx,ny; dist+=1
            if rnd.random()<0.4: di=(di+rnd.choice((-1,1)))%8
    return cr

GAPREPORT={}
def make_cracks(allowed, seed, n_walks, length, W, H):
    """Random-walk veins inside allowed pixels; returns {pixel: distance along vein}."""
    rnd=random.Random(seed); d={}
    allowed=list(sorted(allowed))
    if not allowed: return d
    aset=set(allowed)
    dirs=[(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
    for w in range(n_walks):
        x,y=rnd.choice(allowed); di=rnd.randrange(8); base=rnd.uniform(0,WAVELEN)
        stack=[(x,y,di,base,length)]
        while stack:
            x,y,di,dist,left=stack.pop()
            for s in range(left):
                if (x,y) in aset and (x,y) not in d: d[(x,y)]=dist
                r=rnd.random()
                if r<0.25: di=(di+1)%8
                elif r<0.5: di=(di-1)%8
                if rnd.random()<0.06 and left>6: stack.append((x,y,(di+2)%8,dist,left//2))
                nx,ny=x+dirs[di][0],y+dirs[di][1]
                if (nx,ny) not in aset:
                    di=(di+3+rnd.randrange(3))%8; continue
                x,y=nx,ny; dist+=1
    return d

def frame(cr, W, H, f):
    im=Image.new('RGBA',(W,H),(0,0,0,0)); p=im.load()
    for (x,y),dist in cr.items():
        k=0.5+0.5*math.sin(2*math.pi*(dist/WAVELEN - f/F))
        k=k*k*(3-2*k)  # smooth
        c=tuple(int(DARK[i]+(BRIGHT[i]-DARK[i])*k) for i in range(3))
        p[x,y]=c+(255,)
    return im

SET_TGT=0.6  # per-set target scale: ~2 sets visible at once, so union per frame lands near the coverage target
KSETS=3   # independent vein networks cross-faded around the loop -> veins drift, appear and disappear
def set_weight(k,f):
    # (1+cos)/3 for 3 phase-shifted sets: weights sum to 1 every frame (constant overall glow), seamless over F
    return (1+math.cos(2*math.pi*(f/F - k/KSETS)))/KSETS

def frame_multi(sets, W, H, f):
    im=Image.new('RGBA',(W,H),(0,0,0,0)); p=im.load(); best={}
    for k,cr in enumerate(sets):
        wk=set_weight(k,f)*KSETS/2          # 0..1, soft fade in/out
        wk=wk*wk*(3-2*wk)
        for q,dist in cr.items():
            kk=0.5+0.5*math.sin(2*math.pi*(dist/WAVELEN - f/F)); kk=kk*kk*(3-2*kk)
            c=tuple(DARK[i]+(BRIGHT[i]-DARK[i])*kk for i in range(3))
            if wk>best.get(q,(0,))[0]: best[q]=(wk,c)
    for (x,y),(wk,c) in best.items():
        if wk<0.04: continue
        # fade toward near-black: scale colour (works for additive eyes glow) and alpha (works for item cutout/translucent)
        p[x,y]=tuple(int(v*wk) for v in c)+(int(255*min(1,wk*1.2)),)
    return im

def flow_frames(allowed, W, H, seed, target):
    """Flowing liquid-energy field: domain-warped travelling waves, looping seamlessly over F frames.
    Ridges (zero-crossings) of the field become thin veins that drift/merge/split; per-frame threshold
    is the target-quantile so purple coverage is constant every frame; soft falloff at vein edges."""
    rnd=random.Random(seed); A=sorted(allowed)
    waves=[]
    for i in range(4):
        ang=rnd.uniform(0,2*math.pi); lam=rnd.uniform(5.5,9.0); kx,ky=math.cos(ang)*2*math.pi/lam, math.sin(ang)*2*math.pi/lam
        waves.append((kx,ky,rnd.choice((1,-1))*rnd.choice((1,1,2)),rnd.uniform(0,2*math.pi)))  # integer cycles/loop -> seamless
    warps=[(rnd.uniform(0,2*math.pi),rnd.uniform(4,7),rnd.choice((1,-1)),rnd.uniform(0,6.3)) for _ in range(2)]
    frames=[]; cov=[]
    for f in range(F):
        t=2*math.pi*f/F; val={}
        for (x,y) in A:
            wx=x+1.6*math.sin(2*math.pi*y/warps[0][1]+warps[0][2]*t+warps[0][3])
            wy=y+1.6*math.sin(2*math.pi*x/warps[1][1]+warps[1][2]*t+warps[1][3])
            v=sum(math.sin(kx*wx+ky*wy+c*t+ph) for kx,ky,c,ph in waves)
            val[(x,y)]=abs(v)
        srt=sorted(val.values()); q=srt[max(0,min(len(srt)-1,int(target*len(srt))-1))] if srt else 0
        im=Image.new('RGBA',(W,H),(0,0,0,0)); p=im.load(); n=0
        for (x,y),v in val.items():
            if v>q*1.5: continue
            k=1.0 if v<=q else 1-(v-q)/(0.5*q)      # soft edge
            k=k*k*(3-2*k)
            e=0.5+0.5*math.sin(2*math.pi*(x*0.07+y*0.05)-t)   # slow energy brightness travelling along
            c=tuple(DARK[i]+(BRIGHT[i]-DARK[i])*e for i in range(3))
            p[x,y]=tuple(int(cc*k) for cc in c)+(int(255*k),)
            if k>0.5: n+=1
        frames.append(im); cov.append(n/max(1,len(A)))
    return frames, sum(cov)/len(cov)

def handle_px(name,x,y):
    # vanilla tool layout: handle runs along the bottom-left diagonal
    if name=='sword': return x<=4 and y>=11   # sword: grip + pommel only (blade shares the diagonal)
    return name in('pickaxe','axe','shovel','hoe') and (x+y)>=14 and x<=9 and abs((x+y)-15)<=2 and y>=6

def sheet(frames):
    W,H=frames[0].size; s=Image.new('RGBA',(W,H*len(frames)))
    for i,f in enumerate(frames): s.paste(f,(0,H*i))
    return s

ITEMS={'infinium_ingot':'netherite_ingot'}
for p in ['helmet','chestplate','leggings','boots','sword','pickaxe','axe','shovel','hoe']: ITEMS['infinium_'+p]='netherite_'+p
os.makedirs(OUT+'item/cracks',exist_ok=True)
crack_items={}
for i,(n,src) in enumerate(ITEMS.items()):
    kind=n.split('_')[-1]
    base=darken(Image.open(MC+'item/'+src+'.png'), keep=lambda x,y,k=kind: handle_px(k,x,y))
    # stay one pixel inside the outline so cracks read as "inside" the metal
    op=opaque(base); W,H=base.size
    bp=base.load()
    def lum(q): r,g,b,_=bp[q]; return 0.299*r+0.587*g+0.114*b
    edge={q for q in op if not all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))}
    edge_l=sorted(lum(q) for q in edge)
    thr=edge_l[len(edge_l)//2] if edge_l else 0
    inner=op-edge   # strict: 1-px dark border inside the silhouette stays crack-free
    allowed={q for q in inner if not handle_px(kind,*q)}
    if kind=='helmet': allowed={q for q in allowed if not (5<=q[0]<=10)}   # front-center of the helmet icon stays crack-free
    tgt=0.33 if kind in ('leggings','boots') else 0.25
    frames,cv=flow_frames(allowed, W, H, 1000+i, tgt)
    GAPREPORT[n]=(0, int(cv*len(allowed)), len(allowed))
    sheet(frames).save(OUT+f'item/cracks/{n}.png')
    json.dump({"animation":{"frametime":2,"interpolate":True}},open(OUT+f'item/cracks/{n}.png.mcmeta','w'))
    # flat (non-animated) composited icon for previews
    crack_items[n]=(base,frames)
    if os.path.exists(OUT+f'item/{n}.png.mcmeta'): os.remove(OUT+f'item/{n}.png.mcmeta')
    base.save(OUT+f'item/{n}.png')   # darkened base icon

# ---- worn layers ----
def netherite_palette():
    im=Image.open(MC+'entity/equipment/humanoid/netherite.png').convert('RGBA'); px=im.load()
    cols=sorted({px[x,y][:3] for y in range(im.size[1]) for x in range(im.size[0]) if px[x,y][3]>0}, key=lambda c:0.299*c[0]+0.587*c[1]+0.114*c[2])
    return cols
PAL=netherite_palette()
def to_netherite(img):
    img=img.convert('RGBA'); px=img.load(); W,H=img.size
    lums=sorted(0.299*px[x,y][0]+0.587*px[x,y][1]+0.114*px[x,y][2] for y in range(H) for x in range(W) if px[x,y][3]>0)
    lo,hi=lums[0],lums[-1]
    for y in range(H):
        for x in range(W):
            r,g,b,a=px[x,y]
            if a:
                t=((0.299*r+0.587*g+0.114*b)-lo)/max(1,hi-lo)
                px[x,y]=PAL[min(len(PAL)-1,int(t*(len(PAL)-1)))]+(a,)
    return img

layers={'humanoid':darken(Image.open(MC+'entity/equipment/humanoid/netherite.png')),
        'humanoid_leggings':darken(Image.open(MC+'entity/equipment/humanoid_leggings/netherite.png')),
        'wings':darken(to_netherite(Image.open(MC+'entity/equipment/wings/elytra.png')))}
for d in ['humanoid','humanoid_leggings','wings']:
    os.makedirs(OUT+f'entity/equipment/{d}',exist_ok=True)
layers['humanoid'].save(OUT+'entity/equipment/humanoid/infinium.png')
layers['humanoid_leggings'].save(OUT+'entity/equipment/humanoid_leggings/infinium.png')
layers['wings'].save(OUT+'entity/equipment/wings/infinium.png')
worn={}
for j,(lt,img) in enumerate(layers.items()):
    W,H=img.size; op=opaque(img)
    op={q for q in op if all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))}  # 1-px inset
    if lt=='humanoid':
        # keep the helmet's face/visor area (front face, eye rows and below) free of cracks: head + hat overlay front faces
        face={(x,y) for x in list(range(8,16))+list(range(40,48)) for y in range(8,16)}  # whole front face (head + hat overlay)
        op=op-face
    frames_l,cv=flow_frames(op, W, H, 77+j, 0.33 if lt=='humanoid_leggings' else 0.25)
    if lt=='humanoid':   # boots region (legs area of the main layer): modest extra purple
        boots={q for q in op if q[0]<16 and q[1]>=16}
        bf,_=flow_frames(boots, W, H, 991, 0.33)
        for fr,b2 in zip(frames_l,bf):
            for q in boots: fr.putpixel(q,b2.getpixel(q))
    GAPREPORT[lt]=(0, int(cv*len(op)), len(op))
    os.makedirs(OUT+f'entity/infinium_glow/{lt}',exist_ok=True)
    frs=[]
    for f in range(F):
        fr=frames_l[f]
        for yy in range(H):
            for xx in range(W):
                if (xx,yy) not in op: fr.putpixel((xx,yy),(0,0,0,0))
        fr.save(OUT+f'entity/infinium_glow/{lt}/{f}.png'); frs.append(fr)
    worn[lt]=(img,frs)

# ---- item models ----
gen_display=json.load(open('ref/mc/assets/minecraft/models/item/generated.json'))['display']
hand_display=dict(gen_display); hand_display.update(json.load(open('ref/mc/assets/minecraft/models/item/handheld.json'))['display'])
for n,src in ITEMS.items():
    kind=n.split('_')[-1]; tool=kind in('sword','pickaxe','axe','shovel','hoe')
    json.dump({"model":{"type":"minecraft:composite","models":[
        {"type":"minecraft:model","model":f"infinium:item/{n}"},
        {"type":"minecraft:model","model":f"infinium:item/cracks/{n}"}]}},open(RES+f'items/{n}.json','w'),indent=2)
    json.dump({"parent":"minecraft:item/handheld" if tool else "minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}},
              open(RES+f'models/item/{n}.json','w'),indent=2)
    os.makedirs(RES+'models/item/cracks',exist_ok=True)
    json.dump({"gui_light":"front","textures":{"c":f"infinium:item/cracks/{n}","particle":f"minecraft:item/{src}"},
        "elements":[{"from":[0,0,7.49],"to":[16,16,8.51],"shade":False,"light_emission":15,
            "faces":{"south":{"uv":[0,0,16,16],"texture":"#c"},"north":{"uv":[16,0,0,16],"texture":"#c"}}}],
        "display":hand_display if tool else gen_display},open(RES+f'models/item/cracks/{n}.json','w'),indent=2)

# ---- previews ----
PV='preview/'; os.makedirs(PV,exist_ok=True)
S=8
def comp(base,ov): c=base.copy(); c.alpha_composite(ov); return c
names=list(ITEMS)
grid=Image.new('RGBA',(len(names)*16*S,16*S),(60,60,60,255))
for i,n in enumerate(names):
    b,frs=crack_items[n]; grid.alpha_composite(comp(b,frs[0]).resize((16*S,16*S),0),(i*16*S,0))
grid.save(PV+'item_icons.png')
NS=8  # strip of 8 evenly spaced frames per item
strip=Image.new('RGBA',(NS*16*S,len(names)*16*S),(60,60,60,255))
for r,n in enumerate(names):
    b,frs=crack_items[n]
    for k in range(NS): strip.alpha_composite(comp(b,frs[k*F//NS]).resize((16*S,16*S),0),(k*16*S,r*16*S))
strip.save(PV+'item_animation_strip.png')
for lt,(img,frs) in worn.items():
    comp(img,frs[0]).resize((img.size[0]*8,img.size[1]*8),0).save(PV+f'worn_{lt}.png')
    W,H=img.size; st=Image.new('RGBA',(W*4*4,H*4*2),(40,40,40,255))
    for k in range(8): st.alpha_composite(comp(img,frs[k*F//8]).resize((W*4,H*4),0),((k%4)*W*4,(k//4)*H*4))
    st.save(PV+f'worn_{lt}_animation_strip.png')
# animated GIF of icons for convenience
gifs=[]
for f in range(F):
    g=Image.new('RGBA',(len(names)*16*4,64),(60,60,60,255))
    for i,n in enumerate(names):
        b,frs=crack_items[n]; g.alpha_composite(comp(b,frs[f]).resize((64,64),0),(i*64,0))
    gifs.append(g.convert('RGB'))
gifs[0].save(PV+'item_icons_animated.gif',save_all=True,append_images=gifs[1:],duration=100,loop=0)
print('done')
# ---- Netherite Rocket texture: vanilla firework rocket recolored to the netherite palette ----
# (R5) rocket texture is the user-picked infinium_rocket.png written by gen_bread.py
print('rocket ok')
for k,(g,c,a) in GAPREPORT.items(): print(f'{k:24s} mean per-frame purple (k>0.5): {100*c//a}% of {a} interior px')
# coverage relative to ALL opaque pixels of each icon, and of the worn helmet (head) region
for n,(b,frs) in crack_items.items():
    o=len(opaque(b)); c=sum(sum(1 for px in fr.getdata() if px[3]>128) for fr in frs)//F; print(f'{n:24s} per-frame visible: {100*c//max(1,GAPREPORT[n][2])}% of interior ({100*c//o}% of whole icon)')
img,frs=worn['humanoid']; head={(x,y) for (x,y) in opaque(img) if y<16}
hc=len([p for p in opaque(frs[0]) if p[1]<16]); print(f'worn helmet region          coverage {100*hc//len(head)}% ({hc}/{len(head)})')
for nm,reg in (('worn boots region',lambda q:q[0]<16 and q[1]>=16),):
    o=[q for q in opaque(img) if reg(q)]; c=[q for q in opaque(frs[0]) if reg(q)]; print(f'{nm:27s} coverage {100*len(c)//len(o)}%')
o=opaque(worn['humanoid_leggings'][0]); c=opaque(worn['humanoid_leggings'][1][0]); print(f'worn leggings layer         coverage {100*len(c)//len(o)}%')
ff=sum(1 for fr in worn['humanoid'][1] for x in list(range(8,16))+list(range(40,48)) for y in range(8,16) if fr.getpixel((x,y))[3]>0)
fi=sum(1 for fr in crack_items['infinium_helmet'][1] for x in range(5,11) for y in range(16) if fr.getpixel((x,y))[3]>0)
print('glow px on worn helmet front face (all frames):',ff,' | on helmet icon front-center:',fi)

# ---- strict containment check: every crack/glow pixel must be an interior (non-edge) opaque pixel of its base ----
def edge_ok(base, ov):
    op=opaque(base); inner=op-{q for q in op if not all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))}
    return len(opaque(ov)-inner)
bad=0
for n,(b,frs) in crack_items.items(): bad+=sum(edge_ok(b,fr) for fr in frs)
for lt,(img,frs) in worn.items(): bad+=sum(edge_ok(img,fr) for fr in frs)
print('purple pixels on border/transparent (all frames, icons+worn+wings):',bad)
if bad:
    raise SystemExit('FAIL: animated purple pixels outside the interior mask')


# ---- animated GIF of worn layers (main armor, leggings, wings) ----
gf=[]
for f in range(F):
    g=Image.new('RGBA',(64*6*2+64*6+40,32*6),(40,40,40,255)); x0=0
    for lt in ('humanoid','humanoid_leggings','wings'):
        img,frs=worn[lt]; g.alpha_composite(comp(img,frs[f]).resize((64*6//(1 if lt!='wings' else 1),32*6),0),(x0,0)); x0+=64*6+20
    gf.append(g.convert('RGB'))
gf[0].save(PV+'worn_armor_animated.gif',save_all=True,append_images=gf[1:],duration=100,loop=0)
print('worn gif ok')
