#!/usr/bin/env python3
"""Per-style elytra glow (64x32 wing texture, box uv 22,0 10x20x2). Same field families as gen_tool_styles (v12b language
for CyberPsycho: pulses, scan bands, violet-magenta-pink drift, a few blinking accents), same frame count / frame time as
the worn suit of that style (shared clock). Both wings use the same texture (right wing = mirrored box), so they mirror
exactly. Glow only on interior pixels of the wing base (border rule). Writes infinium_glow/<style>/wings/<f>.png + gray."""
import math, random, os, shutil, sys
from PIL import Image
import gen_drafts as gd, gen_dye, gen_tool_styles as ts
E='src/main/resources/assets/infinium/textures/entity/'
# suit clock per style (frames, ms) - must match InfiniumStyle.java
CLOCK={'astral':(40,300),'cybernetic':(40,300),'singularity':(100,100),'cyberpsycho':(80,100)}
def wing_space(x,y):
    """texture px -> point on the wing (0..10 across, 0..20 down), front and back faces coincide."""
    if 24<=x<34 and 2<=y<22: return (x-24+0.5,y-2+0.5)
    if 36<=x<46 and 2<=y<22: return (46-x-0.5,y-2+0.5)
    return None
def psycho_frames(A):
    """v12c frames (back face) + front face filled with the mirrored back face (front x <-> back 69-x, same wing point)."""
    Aset=set(A); out=[]
    for f in range(80):
        im=Image.open(f'frozen/cyberpsycho_wings/glow/{f}.png').convert('RGBA'); p=im.load()
        for (x,y) in front_set(A): p[x,y]=p[69-x,y]
        out.append(im)
    return out
def ref_frames(vdir,F,A):
    out=[]
    for f in range(F):
        im=Image.open(f'{vdir}/textures/entity/infinium_glow/wings/{f}.png').convert('RGBA'); p=im.load()
        for (x,y) in front_set(A): p[x,y]=p[69-x,y]
        ok={(x,y) for x in range(22,46) for y in range(2,22)}
        for y in range(im.size[1]):
            for x in range(im.size[0]):
                if p[x,y][3] and (x,y) not in ok: p[x,y]=(0,0,0,0)   # border rule (edge texels stay dark)
        out.append(im)
    return out
def front_set(A):
    """Front-face texels (u 24..33) whose wing point is an interior back-face texel. The vanilla/Infinium wing base is
    transparent there, so without glow on these texels the wing shows no glow from the front (glow pass is culled)."""
    Aset=set(A); return sorted((x,y) for x in range(24,34) for y in range(2,22) if (69-x,y) in Aset)
def wingtip_fill(out,A,op):
    """No forced dark border on WINGS (user): glow runs to the wing edges. Back-face edge ring texels copy their inward
    neighbour's glow, the front face mirrors the full back face, and the 2-px side strips (u22-23 next to front u24,
    u34-35 next to back u36) copy the adjacent face column, so there is no dark seam at the wingtip from either side."""
    Aset=set(A); back=[q for q in op if 36<=q[0]<46 and 2<=q[1]<22]
    full=set(back)|{(69-x,y) for x,y in back}|{(x,y) for x in (22,23,34,35) for y in range(2,22)}
    for im in out:
        p=im.load()
        snap=im.copy().load(); bset=set(back)
        ring=[q for q in back if not all((q[0]+dx,q[1]+dy) in bset for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))]
        for (x,y) in ring:                       # only the outer ring; copy an inward neighbour that was lit BEFORE filling
            if snap[x,y][3]: continue
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                n=(x+dx,y+dy)
                if n in Aset and snap[n][3]: p[x,y]=snap[n]; break
        for (x,y) in back: p[69-x,y]=p[x,y]
        for y in range(2,22):
            for x in (22,23): p[x,y]=p[24,y]
            for x in (34,35): p[x,y]=p[36,y]
        for i in range(10):
            for v in (0,1): p[24+i,v]=p[45-i,2]; p[34+i,v]=p[45-i,21]
    return sorted(full|{(24+i,v) for i in range(10) for v in (0,1)}|{(34+i,v) for i in range(10) for v in (0,1)})
def check_and_save(st,out,A,F,ms):
    bad=set().union(*[gd.opaque(fr) for fr in out])-set(A)
    if bad: raise SystemExit(f'FAIL border wings/{st}: {sorted(bad)[:5]}')
    gd.check_loop(out,f'wings/{st}')
    adj=[gd.pdiff(out[i],out[i+1]) for i in range(F-1)]; seam=gd.pdiff(out[-1],out[0]); a=sorted(adj); med=a[len(a)//2]; p90=a[int(0.9*(F-2))]
    if seam>p90: raise SystemExit(f'FAIL smooth seam wings/{st}: {seam} > p90 {p90}')
    for nm,face in (('front',[q for q in A if q[0]<34]),('back',[q for q in A if q[0]>=36])):
        if len(face)>=2 and len({tuple(fr.getpixel(q) for q in face) for fr in out})<2: raise SystemExit(f'FAIL static wings/{st}/{nm}')
    d=E+f'infinium_glow/{st}/wings/'; g=E+f'infinium_glow_gray/{st}/wings/'
    shutil.rmtree(d,ignore_errors=True); shutil.rmtree(g,ignore_errors=True); os.makedirs(d)
    for f,fr in enumerate(out): fr.save(d+f'{f}.png'); gen_dye.gray(d+f'{f}.png',g+f'{f}.png')
    print(f'wings {st:12s} {F} x {ms} ms  seam/med={seam/max(1,med):.2f} max/med={max(adj+[seam])/max(1,med):.2f}  OK',flush=True)
def fix_base_edges():
    """Base wing texture: the 2-px side strips (u22-23 / u34-35, v2-21) and the top/bottom faces (u24-33 / u34-43, v0-1) were
    a dark, full-height column that stuck out past the cut wing silhouette (the dark '\\ /' line at the wingtips).
    Now each edge texel copies the adjacent back-face texel (same colour, transparent where the silhouette is cut). Idempotent."""
    p0=E+'equipment/wings/infinium.png'; im=Image.open(p0).convert('RGBA'); p=im.load(); n=0
    def put(q,src):
        nonlocal n
        if p[q]!=p[src]: p[q]=p[src]; n+=1
    for y in range(2,22):
        for x in (22,23): put((x,y),(45,y))          # strip next to front u24 <-> back u45
        for x in (34,35): put((x,y),(36,y))          # strip next to back u36
    for i in range(10):
        for v in (0,1): put((24+i,v),(45-i,2)); put((34+i,v),(45-i,21))   # top face <- back top row, bottom face <- back bottom row
    im.save(p0); print('wing base edge texels fixed:',n)
def main():
    fix_base_edges()
    base=Image.open(E+'equipment/wings/infinium.png').convert('RGBA'); op=gd.opaque(base)
    inner=op-{q for q in op if not all((q[0]+dx,q[1]+dy) in op for dx,dy in((1,0),(-1,0),(0,1),(0,-1)))}
    A=sorted(q for q in inner if wing_space(*q))
    P={q:(1.5+1.3*wing_space(*q)[0], 0.75*wing_space(*q)[1]) for q in A}     # into the 16x16 field space of the icons
    W=sorted(set(P.values())); back={v:[q for q in A if P[q]==v] for v in W}
    for st,(F,ms) in CLOCK.items():
        # All styles: the user-approved reference wings (build_variants v08 / v09 / v11b, frozen v12c), back face verbatim,
        # FRONT face = same look mirrored onto the same wing points (glow pass is culled, front texels were empty).
        REF={'astral':'build_variants/v08','cybernetic':'build_variants/v09','singularity':'build_variants/v11b'}
        if st=='cyberpsycho': out=psycho_frames(A)
        elif st=='cybernetic':                    # user feedback: toned-down CyberPsycho look (every 2nd v12c frame, dimmed), front+back
            out=[]
            for im in psycho_frames(A)[::2]:
                p=im.load()
                for y in range(im.size[1]):
                    for x in range(im.size[0]):
                        c=p[x,y]
                        if c[3]: p[x,y]=(int(c[0]*0.62),int(c[1]*0.55),int(c[2]*0.62),int(c[3]*0.75))
                out.append(im)
        else: out=ref_frames(REF[st],F,A)
        check_and_save(st,out,wingtip_fill(out,A,op),F,ms); continue
        S,C=ts.field(st,'wings',W,F); out=[]
        for f in range(F):
            sc={v:S(v,f) for v in W}; vals=sorted(sc.values(),reverse=True)
            hi=vals[max(0,int(ts.COV*len(vals))-1)]; lo=vals[min(len(vals)-1,int(ts.COV*1.5*len(vals))-1)]; top=vals[0]
            if lo>=hi: lo=hi-1e-3
            im=Image.new('RGBA',base.size,(0,0,0,0)); p=im.load()
            for v,s in sc.items():
                if s<=lo: continue
                k=1.0 if s>=hi else (s-lo)/(hi-lo); k=k*k*(3-2*k)
                if k<0.05: continue
                b=max(0.0,min(1.0,(s-lo)/max(1e-9,top-lo)))**1.2; k=max(k,0.55); c=C(v,f,b)
                for q in back[v]: p[q]=tuple(int(cc*k) for cc in c)+(int(255*k),)
            out.append(im)
        ts.accents(st,'acc/wings',A,out,F,4)
        check_and_save(st,out,A,F,ms)
if __name__=='__main__': main()
