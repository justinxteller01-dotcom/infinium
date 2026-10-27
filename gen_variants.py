#!/usr/bin/env python3
"""10 pattern variants v01..v10 of the draft9 crackle-pulse look with the field computed in MODEL space,
so the front (helmet, chestplate, leggings, boots) is one continuous canvas. h21 helmet extras (no horns),
helmet top-edge corners removed. Usage: python3 gen_variants.py [vNN ...]
Writes build_variants/vNN/textures/... and preview/variants/vNN/anim.gif (+ description.txt)."""
import sys, os, math, random, shutil, json, zlib, colorsys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen_drafts as gd, render3d_lib as r3, gen_helmets as gh, helmet_corners as hc
F=40; TAU=2*math.pi; FRAME_MS=300
D9='build_drafts/draft9/assets/infinium/textures/entity/'
V={
 'v01':dict(sym=True, flow='cell',  k=50, n=1, drift=0.45, desc='Reference: continuous crackle-glaze cells across the whole front; pulses run outward from each cell center (symmetric).'),
 'v02':dict(sym=True, flow='down',  k=50, n=1, drift=0.45, desc='Pulses fall top-to-bottom through helmet, chest, legs and boots as one sheet (symmetric).'),
 'v03':dict(sym=True, flow='radial',k=50, n=1, drift=0.45, desc='Pulses radiate outward from the chest center over the whole body (symmetric).'),
 'v04':dict(sym=True, flow='cell',  k=30, n=1, drift=0.30, desc='Larger, calmer cells with a softer drift; same outward pulses (symmetric).'),
 'v05':dict(sym=True, flow='down',  k=80, n=2, drift=0.45, desc='Finer cells with two falling pulse fronts per loop (symmetric).'),
 'v06':dict(sym=False,flow='diag',  k=50, n=1, drift=0.45, desc='One diagonal sweep from upper left to lower right across the front (asymmetric, continuous).'),
 'v07':dict(sym=False,flow='spiral',k=50, n=1, drift=0.45, desc='A slow rotating sweep around the chest center, like a clock hand (asymmetric, continuous).'),
 'v08':dict(sym=False,flow='cell',  k=50, n=1, drift=0.45, piece=True, desc='Piece by piece: each armor piece has its own cells and pulse timing (unique per piece, not continuous).'),
 'v09':dict(sym=True, flow='down',  k=50, n=1, drift=0.45, piecephase=True, desc='Mixed: symmetric continuous cells, but each piece pulses with its own phase offset (symmetric).'),
 'v10':dict(sym=False,flow='down',  k=50, n=1, drift=0.45, frontsym=True, desc='Mixed: symmetric falling pulses on the front, free asymmetric pattern on the back.'),
 'v11':dict(sym=False,flow='singularity', k=90, n=1, drift=0.45, F=200, ms=70, title='Singularity',
            desc="Singularity: Astral-style random cells, but ~4x faster, erratic per-cell flicker and a wider violet/magenta/indigo shade range; the whole field slowly orbits the player (model-space rotation about the vertical axis, one turn per 14 s loop)."),
 'v11b':dict(sym=False,flow='sing2', k=60, n=3, drift=0.45, F=100, ms=100, cov=1.7, accents='stars', wings='singularity', title='Singularity',
            desc="Singularity (rework): slow model-space orbit (1 turn / 10 s loop) with fluid fast detail on integer harmonics, 3 rhythmic expanding rings per loop + rotating spiral arms; cosmic nebula palette (deep violet, indigo, blue, pink) with whiter cores; a few asymmetric twinkling stars and tiny comets (<10% of glow); matching elytra."),
 'v12b':dict(sym=True, flow='psycho2', k=50, n=2, drift=0.45, piecephase=True, F=80, ms=100, cov=1.3, accents=True, title='CyberPsycho',
            desc="CyberPsycho (rework): Cybernetic family - two smooth falling pulse fronts + soft scan bands, wider violet->magenta->pink range, ~30% more lit area (symmetric); plus a few smoothly blinking asymmetric glitch accents (<15% of the glow) that loop."),
 'v12c':dict(sym=True, flow='psycho2', k=50, n=2, drift=0.45, piecephase=True, F=80, ms=100, cov=1.3, accents=True, wings='cyberpsycho', title='CyberPsycho',
            desc='CyberPsycho (v12b) with the per-style CyberPsycho elytra glow (same clock, mirrored wings).'),
 'v12':dict(sym=True, flow='cyberpsycho', k=50, n=3, drift=0.45, piecephase=True, F=80, ms=100, title='CyberPsycho',
            desc="CyberPsycho: Cybernetic (v09) made louder: three falling fronts per loop, fast scan bars, mirrored glitch-block flashes and hot magenta/white highlights (symmetric)."),
}
NAMES={'v11b':'Singularity','v08':'Astral','v09':'Cybernetic','v11':'Singularity','v12':'CyberPsycho (old)','v12b':'CyberPsycho'}
STYLE_IDS={'v08':'astral','v09':'cybernetic','v11b':'singularity','v12c':'cyberpsycho'}   # v12c = user-approved reference (was v12b)
STYLE_IDS_NOTE=None   # in-game infinium:style values
BOXES={'humanoid':[('head',0,0,8,8,8,(-5,-9,-5),1.25),('body',16,16,8,12,4,(-4,0,-2),1),('arm',40,16,4,12,4,(-8,0,-2),1),('leg',0,16,4,12,4,(-3.9,12,-2),1)],
       'humanoid_leggings':[('lbody',16,16,8,12,4,(-4,0,-2),1),('lleg',0,16,4,12,4,(-3.9,12,-2),1)]}
def pos_map(key):
    P={}
    for piece,u,v,w,h,d,(X0,Y0,Z0),s in BOXES[key]:
        def put(q,x,y,z): P[q]=(X0+x*s,Y0+y*s,Z0+z*s,piece)
        for j in range(h):
            for i in range(w):
                put((u+d+i,v+d+j),i+.5,j+.5,0); put((u+2*d+w+i,v+d+j),w-i-.5,j+.5,d)
            for i in range(d):
                put((u+i,v+d+j),0,j+.5,d-i-.5); put((u+d+w+i,v+d+j),w,j+.5,i+.5)
        for j in range(d):
            for i in range(w):
                put((u+d+i,v+j),i+.5,0,j+.5); put((u+d+w+i,v+j),i+.5,h,j+.5)
    return P
POS={k:pos_map(k) for k in BOXES}
PIECES=['head','body','arm','leg','lbody','lleg']
AUX={}      # per-pixel colour hints from the last field evaluation (Singularity / CyberPsycho palettes)
def h01(*a):  # deterministic hash -> [0,1)
    return (zlib.crc32(repr(a).encode())&0xFFFFFF)/float(1<<24)
def singularity_field(cfg,seed):
    """Random cells in a cylinder around the body; seeds orbit the vertical axis once per loop (slow), while each cell
    flickers at its own integer frequency (fast, erratic) and jitters on a small fast orbit. All periods divide the loop."""
    rnd=random.Random(seed); K=cfg['k']
    seeds=[]
    for i in range(K):
        r=7.0*math.sqrt(rnd.random()); a=rnd.uniform(0,TAU); y=rnd.uniform(-10,25)
        seeds.append(dict(r=r,a=a,y=y,jr=rnd.uniform(0.4,1.4),jf=rnd.choice((3,4,5,6,7,9,11)),jp=rnd.uniform(0,TAU),
                          ff=rnd.choice((5,7,8,9,11,13,16,19,23)),fp=rnd.uniform(0,TAU),fa=rnd.uniform(0.3,1.1),
                          hue=rnd.random(),sd=rnd.choice((1,-1))))
    def S_for(key,A):
        P=POS[key]
        def S(f):
            t=f/F; th=TAU*t; out={}
            pts=[]
            for sd in seeds:
                a=sd['a']+th; jj=sd['jf']*TAU*t*sd['sd']+sd['jp']
                pts.append((sd['r']*math.cos(a)+sd['jr']*math.cos(jj), sd['y']+sd['jr']*math.sin(jj), sd['r']*math.sin(a)))
            for q in A:
                x,y,z,pc=P[q]
                best=(1e9,0); sec=1e9
                for i,(a,b,c) in enumerate(pts):
                    d=(x-a)**2+(y-b)**2+(z-c)**2
                    if d<best[0]: sec=best[0]; best=(d,i)
                    elif d<sec: sec=d
                d1,d2=math.sqrt(best[0]),math.sqrt(sec); sd=seeds[best[1]]
                fl=math.cos(sd['ff']*TAU*t+sd['fp'])
                # rotating swirl of the whole field (model-space angle about the vertical axis) + fast ripples
                ang=math.atan2(z,x)
                sw=0.5*math.cos(3*ang-th*1+y/5.0)+0.35*math.cos(TAU*(11*t)+d1*1.7)+0.25*math.cos(TAU*(17*t)-(x+z)*0.9)
                out[q]=-(d2-d1)+0.9*sd['fa']*fl+0.6*sw
                AUX[(key,q)]=(sd['hue'],0.5+0.5*math.cos(sd['ff']*TAU*t+sd['fp']+1.3))
            return out
        return S
    return S_for
def sing2_field(cfg,seed):
    """Smooth Singularity: cells orbit the vertical axis once per loop; every fast term uses an integer harmonic of the
    loop (seamless); rhythmic expanding rings (n per loop) + 2 spiral arms co-rotating with the orbit."""
    rnd=random.Random(seed); K=cfg['k']; n=cfg['n']
    seeds=[dict(r=7.0*math.sqrt(rnd.random()),a=rnd.uniform(0,TAU),y=rnd.uniform(-10,25),jr=rnd.uniform(0.3,0.9),
                jf=rnd.choice((2,3,4,5)),jp=rnd.uniform(0,TAU),ff=rnd.choice((3,4,5,6)),fp=rnd.uniform(0,TAU)) for _ in range(K)]
    def S_for(key,A):
        P=POS[key]
        def S(f):
            t=f/F; th=TAU*t; out={}
            pts=[]
            for sd in seeds:
                a=sd['a']+th; jj=sd['jf']*TAU*t+sd['jp']
                pts.append((sd['r']*math.cos(a)+sd['jr']*math.cos(jj), sd['y']+sd['jr']*math.sin(jj), sd['r']*math.sin(a)))
            for q in A:
                x,y,z,pc=P[q]
                best=(1e9,0); sec=1e9
                for i,(a,b,c) in enumerate(pts):
                    d=(x-a)**2+(y-b)**2+(z-c)**2
                    if d<best[0]: sec=best[0]; best=(d,i)
                    elif d<sec: sec=d
                d1,d2=math.sqrt(best[0]),math.sqrt(sec); sd=seeds[best[1]]
                ang=math.atan2(z,x); rho=math.sqrt(x*x+(y-8)**2+z*z)
                ring=(0.5+0.5*math.cos(TAU*(rho/7.0-n*t)))**3
                arm=0.5*math.cos(2*(ang-th)+y/5.0)
                out[q]=-(d2-d1)+0.55*math.cos(sd['ff']*TAU*t+sd['fp'])+0.9*ring+arm+0.2*math.cos(TAU*6*t+d1*1.5)
                neb=0.5+0.5*math.sin(2*(ang-th)+y/7.0)*math.cos((ang-th)-y/11.0)
                AUX[(key,q)]=(neb,ring)
            return out
        return S
    return S_for
NEB=[(0.77,0.95,1.0),(0.745,0.95,1.0),(0.78,0.98,0.6),(0.80,0.88,1.0),(0.84,0.60,1.0)]   # violet, indigo-purple (>=268deg, never blue), dark purple, purple-magenta, subtle pink (hue, sat, value)
def neb_color(n,b,boost=0.0):
    i=min(len(NEB)-2,int(n*(len(NEB)-1))); u=n*(len(NEB)-1)-i
    h=NEB[i][0]*(1-u)+NEB[i+1][0]*u; s0=NEB[i][1]*(1-u)+NEB[i+1][1]*u; vm=NEB[i][2]*(1-u)+NEB[i+1][2]*u
    s_=s0-0.60*b**3; v=(0.28+0.72*min(1.0,b+boost))*(vm+(1-vm)*b**2)   # dark purple brightens toward white only at peaks
    r,g,bb=colorsys.hsv_to_rgb(h%1.0,max(0.0,s_),min(1.0,v)); return (255*r,255*g,255*bb)
def sing2_color(q,f,b):
    neb,ring=AUX.get((CUR[0],q),(0.5,0)); return neb_color(neb,b,0.25*ring)
def astral_color(q,f,b):
    """Astral recolor (colour only): Singularity palette, nebula index from model position (static -> motion unchanged)."""
    x,y,z,_=POS[CUR[0]][q]; n=0.5+0.5*math.sin(x/4.0+y/7.0)*math.cos(z/5.0-y/11.0); return neb_color(n,b)
def cyb_color(q,f,b):
    """Cybernetic recolor (colour only): CyberPsycho violet->magenta->pink range, hue by height (symmetric, static)."""
    y=POS[CUR[0]][q][1]; h=0.74+0.14*(0.5+0.5*math.cos(y/7.0))
    r,g,bb=colorsys.hsv_to_rgb(h%1.0,0.92-0.55*b*b,0.40+0.60*b); return (255*r,255*g,255*bb)
def cyberpsycho_field(cfg,seed,base):
    """v09 field (symmetric cells, per-piece phase) + 3 falling fronts, fast scan bars and mirrored glitch blocks."""
    S0=base
    def S_for(key,A):
        P=POS[key]; inner=S0(key,A)
        def S(f):
            t=f/F; out=inner(f)
            for q in A:
                x,y,z,pc=P[q]; xx=abs(x)
                scan=math.cos(TAU*(y/3.0-7*t))**8                       # thin fast scan bars, 7 per loop
                band=int((y+12)//3); col=int(xx//2.5); slot=int(f//2)
                g=h01('g',band,col,slot) ; glitch=1.6 if g>0.93 else 0.0  # mirrored blocks (xx=|x|) flashing for 2 frames
                strobe=0.5*max(0.0,math.cos(TAU*(5*t)+y/6.0))**6
                out[q]=out[q]+0.9*scan+glitch+strobe
                AUX[(key,q)]=(scan,glitch,strobe)
            return out
        return S
    return S_for
def psycho2_field(cfg,seed,base):
    """Smooth CyberPsycho: v09-style symmetric cells with 2 falling fronts + soft scan bands (cos^4, 3 per loop) and a
    gentle 4-per-loop breathing. Every term is periodic in the loop and continuous in time -> no pops."""
    def S_for(key,A):
        P=POS[key]; inner=base(key,A)
        def S(f):
            t=f/F; out=inner(f)
            for q in A:
                x,y,z,pc=P[q]
                scan=(0.5+0.5*math.cos(TAU*(y/8.0-3*t)))**4
                breath=0.25*math.cos(TAU*(4*t)-y/10.0)
                out[q]=out[q]+0.8*scan+breath
                AUX[(key,q)]=(scan,y)
            return out
        return S
    return S_for
def make_field(cfg,seed):
    if cfg['flow']=='psycho2': return psycho2_field(cfg,seed,make_field(dict(cfg,flow='down'),seed))
    if cfg['flow']=='singularity': return singularity_field(cfg,seed)
    if cfg['flow']=='sing2': return sing2_field(cfg,seed)
    if cfg['flow']=='cyberpsycho': return cyberpsycho_field(cfg,seed,make_field(dict(cfg,flow='down'),seed))
    rnd=random.Random(seed)
    allp=[p for k in POS for p in POS[k].values()]
    def X(x,z): return abs(x) if (cfg['sym'] or (cfg.get('frontsym') and z<0.5)) else x
    def pick(pool,k): return [(X(p[0],p[2]),p[1],p[2],rnd.uniform(0.5,1.1),rnd.uniform(0,TAU),rnd.choice((1,-1))) for p in rnd.sample(pool,min(k,len(pool)))]
    if cfg.get('piece'): seeds={pc:pick([p for p in allp if p[3]==pc],max(4,cfg['k']//5)) for pc in PIECES}
    else: S0=pick(allp,cfg['k']); seeds={pc:S0 for pc in PIECES}
    poff={pc:(i*0.17 if (cfg.get('piece') or cfg.get('piecephase')) else 0.0) for i,pc in enumerate(PIECES)}
    lam=4.0 if cfg['k']>=80 else 5.5 if cfg['k']>=50 else 7.0
    def S_for(key,A):
        P=POS[key]
        def S(f):
            t=f/F; out={}
            cache={}
            for q in A:
                x,y,z,pc=P[q]; xx=X(x,z)
                if pc not in cache:
                    cache[pc]=[(sx+r*math.cos(dd*TAU*t+ph),sy+r*math.sin(dd*TAU*t+ph),sz) for sx,sy,sz,r,ph,dd in seeds[pc]]
                ds=sorted((xx-a)**2+(y-b)**2+(z-c)**2 for a,b,c in cache[pc]); d1,d2=math.sqrt(ds[0]),math.sqrt(ds[1])
                fl=cfg['flow']
                if fl=='cell': ph=d1/lam
                elif fl=='down': ph=-y/9.0
                elif fl=='radial': ph=math.sqrt(xx*xx+(y-6)**2+z*z)/9.0
                elif fl=='diag': ph=-(-xx+y)/12.0
                else: ph=(math.atan2(y-6,xx)/TAU)
                pulse=0.5+0.5*math.cos(TAU*(ph-cfg['n']*t+poff[pc]))
                out[q]=-(d2-d1)+0.9*pulse+cfg['drift']*math.cos(TAU*((xx+y)/6.0-t))+0.3*math.cos(TAU*(y/4.0+2*t))
            return out
        return S
    return S_for
CUR=['humanoid']
def sing_color(q,f,b):
    hue,sh=AUX.get((CUR[0],q),(0.5,0.5))
    h=(0.70+0.17*hue+0.03*math.sin(TAU*(f/F)*3))%1.0          # indigo .. violet .. magenta (stays purple overall)
    v=0.30+0.70*b; s_=0.95-0.55*b*b*sh                          # deep shades in the dim parts, near-white sparks at peaks
    r,g,bb=colorsys.hsv_to_rgb(h,s_,min(1.0,v*(0.75+0.45*sh)))
    return (255*r,255*g,255*bb)
def psycho_color(q,f,b):
    scan,glitch,strobe=AUX.get((CUR[0],q),(0,0,0))
    if glitch: return (255,190,255) if b>0.4 else (240,60,255)    # glitch blocks: hot magenta / white
    h=0.78-0.06*scan; s_=0.9-0.6*b*b; v=0.35+0.65*min(1.0,b+0.4*scan+0.3*strobe)
    r,g,bb=colorsys.hsv_to_rgb(h,s_,v)
    return (255*r,255*g,255*bb)
def psycho2_color(q,f,b):
    scan,y=AUX.get((CUR[0],q),(0,0))
    h=0.74+0.14*(0.5+0.5*math.cos(TAU*(f/F*2)-y/7.0))+0.04*scan      # violet .. magenta .. pink, drifting smoothly
    s_=0.92-0.55*b*b; v=0.40+0.60*min(1.0,b+0.35*scan)
    r,g,bb=colorsys.hsv_to_rgb(h%1.0,s_,v); return (255*r,255*g,255*bb)
ACCENT_MAX=0.15
def add_accents(frs,allowed,seed,name,kind=True):
    """A few asymmetric glitch accents: small 1-3 px bars at random interior spots, each blinking with a smooth
    cos^6 envelope at an integer number of blinks per loop (seamless). Returns (frames, accent share of lit pixels)."""
    rnd=random.Random(seed); A=sorted(allowed); aset=set(A); acc=[]
    for i in range(7 if kind!='stars' else 8):
        x,y=rnd.choice(A)
        if kind=='stars' and i<2:   # tiny comet: head + fading 2px tail (weights per px)
            px=[((x+k,y),1.0-0.35*k) for k in range(3) if (x+k,y) in aset]; col=(225,205,255)
        elif kind=='stars':
            px=[((x,y),1.0)]; col=rnd.choice(((255,255,255),(235,220,255),(255,225,250)))
        else:
            w=rnd.choice((1,2,3)); px=[((x+k,y),1.0) for k in range(w) if (x+k,y) in aset]; col=rnd.choice(((200,90,255),(255,90,230),(255,235,255)))
        acc.append((px,rnd.choice((1,2,3)),rnd.random(),col))
    out=[]; accpx=0; lit=0
    for f,fr in enumerate(frs):
        im=fr.copy(); p=im.load(); t=f/F
        for px,m,ph,col in acc:
            e=max(0.0,math.cos(TAU*(m*t+ph)))**6
            if e<0.05: continue
            for q,w in px:
                ee=e*w; o=p[q]; a=max(o[3],int(255*ee)); accpx+=1
                p[q]=tuple(int(o[i]*(1-ee)+col[i]*ee) for i in range(3))+(a,)
        lit+=len(gd.opaque(im)); out.append(im)
    share=accpx/max(1,lit)
    if share>(0.10 if kind=='stars' else ACCENT_MAX): raise SystemExit(f'FAIL accents {name}: {100*share:.1f}% of glow > {100*ACCENT_MAX:.0f}%')
    return out,share
def smoothness(frs):
    """Frame-to-frame smoothness: adjacent diffs (incl. seam N-1 -> 0). Returns (seam/median, max/median, seam/p90)."""
    adj=[gd.pdiff(frs[i],frs[i+1]) for i in range(len(frs)-1)]; seam=gd.pdiff(frs[-1],frs[0]); a=sorted(adj)
    med=a[len(a)//2]; p90=a[int(0.9*(len(a)-1))]
    return seam/max(1,med), max(adj+[seam])/max(1,med), seam/max(1,p90)
def build_textures(name,cfg):
    T=f'build_variants/{name}/textures/'; shutil.rmtree(f'build_variants/{name}',ignore_errors=True)
    global F, FRAME_MS
    F=cfg.get('F',40); FRAME_MS=cfg.get('ms',300); gd.F=F
    gd.FRONT_FRAME=True
    gd.COLOR_FN=None
    if cfg['flow']=='singularity': gd.COLOR_FN=lambda q,f,b: sing_color(q,f,b)
    if cfg['flow']=='cyberpsycho': gd.COLOR_FN=lambda q,f,b: psycho_color(q,f,b)
    if name=='v08': gd.COLOR_FN=lambda q,f,b: astral_color(q,f,b)
    if name=='v09': gd.COLOR_FN=lambda q,f,b: cyb_color(q,f,b)
    if cfg['flow']=='sing2': gd.COLOR_FN=lambda q,f,b: sing2_color(q,f,b)
    if cfg['flow']=='psycho2': gd.COLOR_FN=lambda q,f,b: psycho2_color(q,f,b)
    ACC={}
    bases=gd.worn_bases({'knees','cuffs'})
    S_for=make_field(cfg,zlib.crc32(name.encode())%10000); report=[]   # deterministic seed (str hash() is salted per run)
    for key in ('humanoid','humanoid_leggings'):
        img,allowed,_=bases[key]
        if key=='humanoid':
            hc.apply(img); allowed&=gd.interior(gd.opaque(img))
        M=gd.M_hum if cfg['sym'] else None
        if M: gd.sym_base(img,M); allowed={q for q in allowed if M(q) in allowed}
        else: gd.sym_base(img,gd.M_hum)       # netherite base stays symmetric for every variant
        grp=gd.face_groups(key)
        gd.field_fn=lambda style,A,W,H,seed,_k=key: S_for(_k,A)
        CUR[0]=key
        frs,cv=gd.make_frames(9,allowed,img.size[0],img.size[1],0,0.105*cfg.get('cov',1.0)*(1.1 if key=='humanoid_leggings' else 1),M,grp,gd.FACE_BOOST.get(key))
        gd.check(frs,allowed,f'{name}/{key}')                       # border rule
        lp=gd.check_loop(frs,f'{name}/{key}')                       # seamless loop
        gd.check_faces_animate(frs,grp,allowed,f'{name}/{key}')     # no static faces
        if M: gd.check_sym(frs,M,f'{name}/{key}',img)
        os.makedirs(T+f'entity/equipment/{key}',exist_ok=True); os.makedirs(T+f'entity/infinium_glow/{key}',exist_ok=True)
        img.save(T+f'entity/equipment/{key}/infinium.png')
        for f,fr in enumerate(frs): fr.save(T+f'entity/infinium_glow/{key}/{f}.png')
        sm=smoothness(frs); rep=f'{key}={100*cv:.0f}% seam={lp:.2f} smooth(seam/med={sm[0]:.2f}, max/med={sm[1]:.2f})'
        if cfg.get('accents'):
            if sm[2]>1.0: raise SystemExit(f'FAIL smooth seam {name}/{key}: seam above p90 of adjacent diffs ({sm})')
            afr,share=add_accents(frs,allowed,zlib.crc32((name+key).encode()),f'{name}/{key}',cfg['accents'])
            gd.check(afr,allowed,f'{name}/{key}+acc'); gd.check_loop(afr,f'{name}/{key}+acc'); gd.check_faces_animate(afr,grp,allowed,f'{name}/{key}+acc')
            sa=smoothness(afr)
            if sa[2]>1.0: raise SystemExit(f'FAIL smooth seam {name}/{key}+acc: {sa}')
            ACC[key]=afr; rep+=f' | +accents {100*share:.1f}% of glow, smooth(seam/med={sa[0]:.2f}, max/med={sa[1]:.2f})'
        report.append(rep)
    os.makedirs(T+'entity/equipment/wings',exist_ok=True)
    if cfg.get('wings'):   # per-style elytra glow from gen_wing_styles.py (shipped base)
        RS='src/main/resources/assets/infinium/textures/entity/'
        shutil.copy(RS+'equipment/wings/infinium.png',T+'entity/equipment/wings/infinium.png')
        shutil.copytree(RS+f"infinium_glow/{cfg['wings']}/wings",T+'entity/infinium_glow/wings')
    else:
        shutil.copy(D9+'equipment/wings/infinium.png',T+'entity/equipment/wings/infinium.png')
        shutil.copytree(D9+'infinium_glow/wings',T+'entity/infinium_glow/wings')
    open(f'build_variants/{name}/glow_anim.properties','w').write(f'frames={F}\nframeMs={FRAME_MS}\n')
    vs=None
    if cfg['sym']:
        vs=r3.view_symmetry(T,F)
        if any(vs.values()): raise SystemExit(f'FAIL view symmetry {name}: {vs}')
    for key,afr in ACC.items():          # symmetric base checked above; ship the accented frames
        for f,fr in enumerate(afr): fr.save(T+f'entity/infinium_glow/{key}/{f}.png')
    return T,report,vs
BG=(118,118,124); SKIN=Image.new('RGBA',(64,64),(196,192,186,255))
STATES=(('hidden',dict(r3.POSES['standing'],nowings=True)),('on',r3.POSES['standing']),('off',r3.POSES['fold']))
YAWS=(0.0,math.pi,math.pi/2,-math.pi/2)        # front, back, left side, right side
def figure(f,pose,yaw,size=560,scale=10):
    Q=r3.build(f,pose,SKIN)+gh.extra(gh.H21[1],f*40//F)     # helmet extras keep their own 40-frame loop
    Q=[([(x,y+5.5,z) for x,y,z in cs],c) for cs,c in Q]
    im=r3.render(Q,yaw,0.0,0.0,size=size,scale=scale,bg=BG,shade=True).convert('RGB')
    a=np.asarray(im).astype(int); fg=np.any(a!=BG,axis=2)
    edge=fg & ~(np.asarray(Image.fromarray((fg*255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)))>0)
    out=np.where(edge[...,None],np.array([20,18,22]),a).astype(np.uint8)
    return Image.fromarray(out).crop((size//2-200,size//2-215,size//2+200,size//2+215))
def _gif_frame(args):
    name,f=args
    try: font=ImageFont.load_default(size=24)
    except Exception: font=None
    G=Image.new('RGB',(400*4,36+430*3),BG); d=ImageDraw.Draw(G)
    d.text((10,6),name+(f"  '{NAMES[name]}'" if name in NAMES else ''),fill=(20,20,24),font=font)
    for r,(st,pose) in enumerate(STATES):
        for c,yaw in enumerate(YAWS): G.paste(figure(f,pose,yaw),(c*400,36+r*430))
    return G.resize((G.width*3//4,G.height*3//4))
def gif(name,T):
    r3.T=T+'entity/'; r3._TC.clear(); gh.TINT=None; r3.FRAMES_HINT[0]=F
    import multiprocessing as mp
    with mp.get_context('fork').Pool(min(8,os.cpu_count() or 1)) as pool: frames=pool.map(_gif_frame,[(name,f) for f in range(F)])
    P=f'preview/variants/{name}/'; os.makedirs(P,exist_ok=True)
    frames[0].save(P+'anim.gif',save_all=True,append_images=frames[1:],duration=FRAME_MS,loop=0,optimize=True)
def main(names,do_gif=True):
    for n in names:
        cfg=V[n]; T,rep,vs=build_textures(n,cfg)
        if do_gif: gif(n,T)
        P=f'preview/variants/{n}/'; os.makedirs(P,exist_ok=True); open(P+'description.txt','w').write(f"{n}: {cfg['desc']}\n")
        print(n,'OK',' | '.join(rep),'| view-sym:',('PASS '+str(vs)) if vs is not None else 'n/a (asymmetric)',flush=True)
    with open('preview/variants/descriptions.txt','w') as fh:
        for k,c in V.items(): fh.write(f"{k}: {c['desc']}\n")
if __name__=='__main__':
    a=[x for x in sys.argv[1:] if not x.startswith('-')]
    main(a or list(V), do_gif='--no-gif' not in sys.argv)
