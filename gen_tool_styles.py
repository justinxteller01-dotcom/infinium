#!/usr/bin/env python3
"""Per-style animated glow for the Infinium tool icons (sword, pickaxe, axe, shovel, hoe).
Tools take the style of the chestplate their holder wears (client select property infinium:holder_style).
Writes textures/item/cracks_<style>/<tool>.png (+.mcmeta) and cracks_<style>_gray/ (dye tint), models and item defs.
Rules: glow only on interior non-handle pixels (same mask as gen_cracks), seamless loop, every frame changes, ~40% coverage
(more purple than the 25% of the legacy icons). Usage: python3 gen_tool_styles.py [--preview]"""
import math, random, os, json, sys, colorsys, shutil
from PIL import Image, ImageDraw, ImageFont
import gen_drafts as gd, gen_dye
TAU=2*math.pi
RES='src/main/resources/assets/infinium/'; TX=RES+'textures/item/'
TOOLS=['sword','pickaxe','axe','shovel','hoe']
ARMOR=['helmet','chestplate','leggings','boots']   # armor icons: style from the item's own infinium:style (absent = Astral)
# style -> (frames, ticks per frame); loop lengths match the worn armor styles closely
STY={'astral':(40,6),'cybernetic':(40,6),'singularity':(100,2),'cyberpsycho':(80,2)}
COV=0.40
def handle_px(name,x,y):
    if name=='sword': return x<=4 and y>=11
    return name in('pickaxe','axe','shovel','hoe') and (x+y)>=14 and x<=9 and abs((x+y)-15)<=2 and y>=6
def mask(tool):
    base=Image.open(TX+f'infinium_{tool}.png').convert('RGBA'); op=gd.opaque(base)
    inner=op-{q for q in op if not all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))}
    return base,sorted(q for q in inner if not handle_px(tool,*q))
def h01(*a): return random.Random(repr(a)).random()
def field(style,tool,A,F):
    rnd=random.Random(f'{style}/{tool}')
    if style=='astral':          # random cells, each with its own pulse timing
        S=[(rnd.uniform(0,16),rnd.uniform(0,16),rnd.uniform(0,TAU),rnd.choice((1,2)),rnd.uniform(0.6,1.6)) for _ in range(9)]
        def s(q,f):
            t=f/F; ds=sorted(((q[0]-(a+r*math.cos(TAU*t+p)))**2+(q[1]-(b+r*math.sin(TAU*t+p)))**2,i) for i,(a,b,p,m,r) in enumerate(S))
            a,b,p,m,r=S[ds[0][1]]; d1,d2=math.sqrt(ds[0][0]),math.sqrt(ds[1][0])
            return -(d2-d1)+0.9*math.cos(TAU*(m*t)+p-d1/3)
        import gen_variants as gv   # colour only: Singularity palette, static nebula index by position
        return s,lambda q,f,b:gv.neb_color(0.5+0.5*math.sin(q[0]/3.0+q[1]/5.0)*math.cos(q[1]/4.0-q[0]/6.0),b)
    if style=='cybernetic':      # cells + pulses running tip -> handle along the tool diagonal
        S=[(rnd.uniform(0,16),rnd.uniform(0,16)) for _ in range(8)]
        def s(q,f):
            t=f/F; ds=sorted((q[0]-a)**2+(q[1]-b)**2 for a,b in S); d1,d2=math.sqrt(ds[0]),math.sqrt(ds[1])
            return -(d2-d1)+1.1*math.cos(TAU*((q[0]-q[1])/10.0-t))+0.3*math.cos(TAU*(q[1]/4.0+2*t))
        return s,lambda q,f,b:hsv((0.74+0.14*(0.5+0.5*math.cos(q[1]/7.0)))%1,0.92-0.55*b*b,0.40+0.60*b)   # colour only: CyberPsycho range
    if style=='singularity':     # rework: seeds orbit the center once per loop, integer-harmonic detail, rhythmic rings, nebula palette
        S=[dict(r=7*math.sqrt(rnd.random()),a=rnd.uniform(0,TAU),ff=rnd.choice((3,4,5,6)),fp=rnd.uniform(0,TAU)) for _ in range(12)]
        aux={}
        def s(q,f):
            t=f/F+0.33; th=TAU*t   # phase offset: loop start away from a busy cell switch (still exactly periodic)
            pts=[(8+d['r']*math.cos(d['a']+th),8+d['r']*math.sin(d['a']+th)) for d in S]
            ds=sorted(((q[0]-a)**2+(q[1]-b)**2,i) for i,(a,b) in enumerate(pts)); d=S[ds[0][1]]
            d1,d2=math.sqrt(ds[0][0]),math.sqrt(ds[1][0])
            ang=math.atan2(q[1]-8,q[0]-8); rho=math.hypot(q[0]-8,q[1]-8)
            ring=(0.5+0.5*math.cos(TAU*(rho/6.0-3*t)))**3
            aux[q]=(0.5+0.5*math.sin(2*(ang-th)+q[1]/5.0)*math.cos((ang-th)-q[0]/7.0),ring)
            return -(d2-d1)+0.55*math.cos(d['ff']*TAU*t+d['fp'])+0.9*ring+0.5*math.cos(2*(ang-th)+d1)+0.2*math.cos(TAU*6*t+d1*1.5)
        import gen_variants as gv
        return s,lambda q,f,b:gv.neb_color(aux.get(q,(0.5,0))[0],b,0.25*aux.get(q,(0.5,0))[1])
    # cyberpsycho (rework): cybernetic diagonal pulses x2 + soft scan bands + smooth hue drift; accents added in frames_for
    S=[(rnd.uniform(0,16),rnd.uniform(0,16)) for _ in range(8)]; aux={}
    def s(q,f):
        t=f/F; ds=sorted((q[0]-a)**2+(q[1]-b)**2 for a,b in S); d1,d2=math.sqrt(ds[0]),math.sqrt(ds[1])
        scan=(0.5+0.5*math.cos(TAU*(q[1]/8.0-3*t)))**4; aux[q]=scan
        return -(d2-d1)+1.1*math.cos(TAU*((q[0]-q[1])/10.0-2*t))+0.8*scan+0.25*math.cos(TAU*4*t-q[1]/10.0)
    return s,lambda q,f,b:hsv((0.74+0.14*(0.5+0.5*math.cos(TAU*2*f/F-q[1]/7.0))+0.04*aux.get(q,0))%1,0.92-0.55*b*b,0.4+0.6*min(1,b+0.35*aux.get(q,0)))
def hsv(h,s,v): r,g,b=colorsys.hsv_to_rgb(h,max(0,min(1,s)),max(0,min(1,v))); return (255*r,255*g,255*b)
# armor icons: SAME glow as the worn armor (pattern, colours, clock): icon interior sampled from the worn texture's front faces
GL='src/main/resources/assets/infinium/textures/entity/infinium_glow/'
def _src(style,tool,f):
    key='humanoid_leggings' if tool=='leggings' else 'humanoid'
    t=Image.open(GL+f'{style}/{key}/{f}.png').convert('RGBA'); c=lambda r:t.crop(r)
    def row(*ims):
        W=sum(i.width for i in ims); H=max(i.height for i in ims); o=Image.new('RGBA',(W,H)); x=0
        for i in ims: o.paste(i,(x,0)); x+=i.width
        return o
    mir=lambda im:im.transpose(Image.FLIP_LEFT_RIGHT)
    if tool=='helmet': return row(c((0,8,8,16)),c((8,8,16,16)),c((16,8,24,16)))   # right side, front, left side
    if tool=='chestplate': return row(c((44,20,48,32)),c((20,20,28,32)),mir(c((44,20,48,32))))
    if tool=='leggings': return row(c((4,20,8,32)),mir(c((4,20,8,32))))
    return row(c((4,26,8,32)),mir(c((4,26,8,32))))
def armor_frames(style,tool):
    F,_=STY[style]; base,A=mask(tool); xs=[q[0] for q in A]; ys=[q[1] for q in A]
    x0,x1,y0,y1=min(xs),max(xs)+1,min(ys),max(ys)+1; out=[]
    for f in range(F):
        sr=_src(style,tool,f); sp=sr.load(); im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
        for (x,y) in A:
            ua=int((x-x0)*sr.width/(x1-x0)); ub=max(ua+1,int((x-x0+1)*sr.width/(x1-x0)))
            va=int((y-y0)*sr.height/(y1-y0)); vb=max(va+1,int((y-y0+1)*sr.height/(y1-y0)))
            cs=[sp[u,v] for u in range(ua,min(ub,sr.width)) for v in range(va,min(vb,sr.height)) if sp[u,v][3]]
            if cs: p[x,y]=max(cs,key=lambda c:c[3]*(c[0]+c[1]+c[2]))   # brightest worn texel in the footprint
        out.append(im)
    return base,A,out
def frames_for(style,tool):
    if tool in ARMOR: return armor_frames(style,tool)
    F,_=STY[style]; base,A=mask(tool); S,C=field(style,tool,A,F); out=[]
    for f in range(F):
        sc={q:S(q,f) for q in A}; vals=sorted(sc.values(),reverse=True)
        hi=vals[max(0,int(COV*len(vals))-1)]; lo=vals[min(len(vals)-1,int(COV*1.5*len(vals))-1)]; top=vals[0]
        if lo>=hi: lo=hi-1e-3
        im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
        for q,v in sc.items():
            if v<=lo: continue
            k=1.0 if v>=hi else (v-lo)/(hi-lo); k=k*k*(3-2*k)
            if k<0.05: continue
            b=max(0.0,min(1.0,(v-lo)/max(1e-9,top-lo)))**1.2; k=max(k,0.55)
            c=C(q,f,b); p[q]=tuple(int(cc*k) for cc in c)+(int(255*k),)
        out.append(im)
    accents(style,('acc/' if style!='singularity' else 'star/')+tool,A,out,F,3)
    return base,A,out
def accents(style,key,A,out,F,n):
    """Seamless asymmetric accents: CyberPsycho glitch bars; Singularity twinkling stars + one tiny comet."""
    if style not in ('cyberpsycho','singularity'): return
    rnd=random.Random(key); acc=[]; aset=set(A)
    for i in range(n+(1 if style=='singularity' else 0)):
        x,y=rnd.choice(A)
        if style=='cyberpsycho': acc.append(([(q,1.0) for q in ((x,y),(x+1,y)) if q in aset],rnd.choice((1,2,3)),rnd.random(),rnd.choice(((200,90,255),(255,90,230),(255,235,255)))))
        elif i==0: acc.append(([((x+k,y),1.0-0.35*k) for k in range(3) if (x+k,y) in aset],rnd.choice((1,2)),rnd.random(),(225,205,255)))
        else: acc.append(([((x,y),1.0)],rnd.choice((1,2,3)),rnd.random(),rnd.choice(((255,255,255),(235,220,255),(255,225,250)))))
    for f,im in enumerate(out):
        p=im.load()
        for px,m,ph,col in acc:
            e=max(0.0,math.cos(TAU*(m*f/F+ph)))**3
            if e<0.05: continue
            for q,w in px: ee=e*w; o=p[q]; p[q]=tuple(int(o[i]*(1-ee)+col[i]*ee) for i in range(3))+(max(o[3],int(255*ee)),)
def check(style,tool,base,A,frs):
    allowed=set(A)
    for i,fr in enumerate(frs):
        bad=gd.opaque(fr)-allowed
        if bad: raise SystemExit(f'FAIL border {style}/{tool} frame {i}: {sorted(bad)[:5]}')
    gd.check_loop(frs,f'{style}/{tool}')
    still=[i for i in range(len(frs)) if gd.pdiff(frs[i],frs[(i+1)%len(frs)])==0]
    if still and tool not in ARMOR: raise SystemExit(f'FAIL static {style}/{tool}: frames {still[:5]} identical to the next')
    cov=sum(len(gd.opaque(f)) for f in frs)/len(frs)/len(A)
    adj=[gd.pdiff(frs[i],frs[i+1]) for i in range(len(frs)-1)]; seam=gd.pdiff(frs[-1],frs[0]); a=sorted(adj)
    med=a[len(a)//2]; p90=a[int(0.9*(len(a)-1))]
    if style in('cyberpsycho','singularity') and seam>p90: raise SystemExit(f'FAIL smooth seam {style}/{tool}: {seam} > p90 {p90}')
    SM[(style,tool)]=(seam/max(1,med),max(adj+[seam])/max(1,med))
    return cov
SM={}
def main(preview=False):
    allfr={}
    for st,(F,ft) in STY.items():
        for g in ('','_gray'): shutil.rmtree(TX+f'cracks_{st}{g}',ignore_errors=True); os.makedirs(TX+f'cracks_{st}{g}')
        for tool in TOOLS+ARMOR:
            n=f'infinium_{tool}'; base,A,frs=frames_for(st,tool); cov=check(st,tool,base,A,frs); allfr[(st,tool)]=(base,frs)
            sh=Image.new('RGBA',(16,16*F))
            for i,fr in enumerate(frs): sh.paste(fr,(0,16*i))
            p=TX+f'cracks_{st}/{n}.png'; sh.save(p); gen_dye.gray(p,TX+f'cracks_{st}_gray/{n}.png')
            for g in ('','_gray'): json.dump({"animation":{"frametime":ft,"interpolate":False}},open(TX+f'cracks_{st}{g}/{n}.png.mcmeta','w'))
            m=json.load(open(RES+f'models/item/cracks/{n}.json'))
            for g in ('','_gray'):
                mm=json.loads(json.dumps(m)); mm['textures']['c']=f'infinium:item/cracks_{st}{g}/{n}'
                if g:
                    for e in mm['elements']:
                        for fc in e['faces'].values(): fc['tintindex']=0
                os.makedirs(RES+f'models/item/cracks_{st}{g}',exist_ok=True); json.dump(mm,open(RES+f'models/item/cracks_{st}{g}/{n}.json','w'),indent=1)
            print(f'{st:12s} {tool:8s} {F} frames x {ft} ticks, mean coverage {100*cov:.0f}% of glow area, smooth seam/med={SM[(st,tool)][0]:.2f} max/med={SM[(st,tool)][1]:.2f}',flush=True)
    infinity_models()
    for tool in TOOLS+ARMOR: write_item_def(f'infinium_{tool}')
    for a in ARMOR: write_infinity_def(f'infinity_{a}')
    if preview: gif(allfr)
def by_style(n,gray=None):
    def m(st):
        d={"type":"minecraft:model","model":f"infinium:item/cracks_{st}{'_gray' if gray is not None else ''}/{n}"}
        if gray is not None: d["tints"]=[{"type":"minecraft:constant","value":gray}]
        return d
    if n.split('_',1)[1] in ARMOR: sel={"type":"minecraft:select","property":"minecraft:component","component":"infinium:style"}
    else: sel={"type":"minecraft:select","property":"infinium:holder_style"}
    cases=[{"when":st,"model":m(st)} for st in STY if st!='astral']
    if n.split('_',1)[1] not in ARMOR: cases.append({"when":"infinity","model":m('infinity')})   # 12.6: 4 Infinity pieces worn
    return dict(sel,cases=cases,fallback=m('astral'))
def infinity_models():
    """Staged Infinity v2 sheets (copy_infinity.py): cracks_infinity{,_gray}/infinium_<tool>.png and infinity_<piece>.png, 64 x 1 tick."""
    for tool in TOOLS+ARMOR:
        n=f'infinium_{tool}'; tn=n if tool in TOOLS else f'infinity_{tool}'
        if not os.path.exists(TX+f'cracks_infinity/{tn}.png'): raise SystemExit('missing staged Infinity sheet '+tn)
        m=json.load(open(RES+f'models/item/cracks/{n}.json'))
        for g in ('','_gray'):
            mm=json.loads(json.dumps(m)); mm['textures']['c']=f'infinium:item/cracks_infinity{g}/{tn}'
            if tool in ARMOR: mm['textures']['particle']=f'infinium:item/{tn}'
            if g:
                for e in mm['elements']:
                    for fc in e['faces'].values(): fc['tintindex']=0
            os.makedirs(RES+f'models/item/cracks_infinity{g}',exist_ok=True); json.dump(mm,open(RES+f'models/item/cracks_infinity{g}/{tn}.json','w'),indent=1)
def write_infinity_def(n):
    """Infinity armor icon: base + animated Infinity glow; dyed = gray sheet tinted (same glow_color select as the other pieces)."""
    def m(gray=None):
        d={"type":"minecraft:model","model":f"infinium:item/cracks_infinity{'_gray' if gray is not None else ''}/{n}"}
        if gray is not None: d["tints"]=[{"type":"minecraft:constant","value":gray}]
        return d
    preview={"type":"minecraft:select","property":"infinium:preview_color",
             "cases":[{"when":gen_dye.NAMES[i],"model":m(tint(i))} for i in range(16)],"fallback":m()}
    sel={"type":"minecraft:select","property":"minecraft:component","component":"infinium:glow_color",
         "cases":[{"when":gen_dye.NAMES[i],"model":m(tint(i))} for i in range(16)],"fallback":preview}
    json.dump({"model":{"type":"minecraft:composite","models":[{"type":"minecraft:model","model":f"infinium:item/{n}"},sel]}},open(RES+f'items/{n}.json','w'),indent=1)
def tint(i): return (0xFF<<24|gen_dye.PAL[i])-(1<<32)
def write_item_def(n):
    # undyed: infinium:preview_color cycles the 16 dye glows only in JEI-style fake renders (no holder, GUI); else default purple
    preview={"type":"minecraft:select","property":"infinium:preview_color",
             "cases":[{"when":gen_dye.NAMES[i],"model":by_style(n,tint(i))} for i in range(16)],"fallback":by_style(n)}
    sel={"type":"minecraft:select","property":"minecraft:component","component":"infinium:glow_color",
         "cases":[{"when":gen_dye.NAMES[i],"model":by_style(n,tint(i))} for i in range(16)],"fallback":preview}
    json.dump({"model":{"type":"minecraft:composite","models":[{"type":"minecraft:model","model":f"infinium:item/{n}"},sel]}},open(RES+f'items/{n}.json','w'),indent=1)
def gif(allfr):
    S=6; font=ImageFont.load_default(size=18); names=list(STY); out=[]
    L=200   # lcm-ish: 200 frames at 50 ms covers every style loop (40x6, 200x1, 80x2 ticks) -> resample by time
    for k in range(L):
        ms=k*50; G=Image.new('RGB',(130+len(TOOLS+ARMOR)*16*S,len(names)*(16*S+8)+8),(60,60,64)); d=ImageDraw.Draw(G)
        for r,st in enumerate(names):
            F,ft=STY[st]; f=(ms//(ft*50))%F; y=8+r*(16*S+8); d.text((6,y+40),st.capitalize() if st!='cyberpsycho' else 'CyberPsycho',fill=(230,230,235),font=font)
            for c,tool in enumerate(TOOLS+ARMOR):
                base,frs=allfr[(st,tool)]; ic=base.copy(); ic.alpha_composite(frs[f]); G.paste(ic.resize((16*S,16*S),0),(130+c*16*S,y),ic.resize((16*S,16*S),0))
        out.append(G)
    os.makedirs('preview/tools',exist_ok=True)
    out[0].save('preview/tools/styles.gif',save_all=True,append_images=out[1:],duration=50,loop=0,optimize=True)
    print('preview/tools/styles.gif')
if __name__=='__main__': main('--preview' in sys.argv)
