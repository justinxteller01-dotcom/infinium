#!/usr/bin/env python3
"""20 helmet shape variants on the draft9 (Phase 1) look. Extra pieces are small cubes attached to the head part.
Writes preview/helmets/hNN/{front,three_quarter,back,icon}.png, helmet_sheet.png/.gif, descriptions.txt."""
import math, os
from PIL import Image, ImageDraw
import render3d_lib as r3
TEX='build_drafts/draft9/assets/infinium/textures/'
r3.T=TEX+'entity/'
F=40; OUT='preview/helmets/'; TINT=None; TAU=2*math.pi
DARKS=[(22,19,22),(30,26,30),(38,33,37),(46,40,44)]
def C(x0,y0,z0,w,h,d,trim=False,glow=False,rot=(0,0,0),pivot=None,mirror=True):
    return dict(b=(x0,y0,z0,w,h,d),trim=trim,glow=glow,rot=rot,pivot=pivot,mirror=mirror)
# head-local coords: head -4..4 x, -8..0 y (down +), -4..4 z (front = -z); helmet shell inflated to -5..5 / -9..1
V=[
 ('Clean vanilla-height shell, no extra pieces (reference).',[]),
 ('Small mini horns: tiny 1px nubs at the front top corners.',[C(-4.5,-10,-3.5,1,1,1,trim=True)]),
 ('Medium mini horns tilted outward ~25 deg.',[C(-0.5,-2,-0.5,1,2,1,trim=True,rot=(0,0,-0.45),pivot=(-3.8,-9,-2.5))]),
 ('Swept-back mini horns lying along the sides of the crown.',[C(-0.5,-0.5,0,1,1,3.5,trim=True,rot=(0.5,0,0),pivot=(-4.6,-9,-1))]),
 ('Low swept-back crest: thin central fin rising toward the back.',[C(-0.5,-0.6,-3,1,0.6,6,trim=True,mirror=False,rot=(-0.12,0,0),pivot=(0,-9,0))]),
 ('Flat-top: thin flat plate squaring off the crown.',[C(-5.2,-9.6,-5.2,10.4,0.6,10.4,mirror=False)]),
 ('Rounded cap: bevelled top edges soften the silhouette.',[C(-5.4,-9.2,-5.0,0.6,1.2,10,trim=True),C(-5,-9.2,-5.4,10,1.2,0.6,mirror=False,trim=True),C(-5,-9.2,4.8,10,1.2,0.6,mirror=False,trim=True)]),
 ('Cheek guards: small plates framing the open face.',[C(-5.4,-5,-5.6,1.4,4,1,trim=True)]),
 ('Brow ridge: a single glowing bar over the eyes.',[C(-4,-6.4,-5.6,8,1,0.8,mirror=False,glow=True)]),
 ('Crown points: five short spikes along the front top edge.',[C(-0.5,-10.2,-5.1,1,1.2,1,mirror=False,trim=True),C(-2.5,-9.9,-5.1,1,0.9,1,trim=True),C(-4.5,-9.7,-5.1,1,0.7,1,trim=True)]),
 ('Side fins: thin swept fins above the ears.',[C(-0.3,-3,0,0.6,3,3,trim=True,rot=(0.35,0,0),pivot=(-5.3,-5,-1))]),
 ('Back neck guard: flared plate covering the nape.',[C(-5,0,0,10,2.2,0.8,mirror=False,rot=(-0.45,0,0),pivot=(0,-0.5,5))]),
 ('Open frame: slim glowing bars outline the face opening.',[C(-4.6,-6.6,-5.5,0.7,6.4,0.6,glow=True),C(-4,-6.6,-5.5,8,0.6,0.6,mirror=False,glow=True)]),
 ('Layered plates: two thin overlapping bands step the shell.',[C(-5.3,-6.2,-5.3,10.6,0.7,10.6,mirror=False),C(-5.5,-3.4,-5.5,11,0.7,11,mirror=False)]),
 ('Tiara band: thin forehead band with a small glowing center gem.',[C(-5.25,-7.4,-5.25,10.5,0.7,10.5,mirror=False,trim=True),C(-0.7,-8.6,-5.7,1.4,1.4,0.6,mirror=False,glow=True)]),
 ('Large curved mini horns: two-segment horns curling back.',[C(-1,-2,-0.5,1.2,2,1.2,trim=True,rot=(0,0,-0.3),pivot=(-3.8,-9,-2)),C(-0.5,-1,0,1,1,1.6,trim=True,rot=(0.5,0,-0.3),pivot=(-4.7,-11,-2))]),
 ('Ram curls: compact horn coils on the temples.',[C(-6.1,-7.5,-2,1.1,2,2,trim=True),C(-6.6,-6,-0.2,1,1.2,1.2,trim=True)]),
 ('Mohawk fins: three small stepped plates along the top center.',[C(-0.4,-10.2,-3,0.8,1.2,1.6,mirror=False,trim=True),C(-0.4,-10.6,-0.8,0.8,1.6,1.6,mirror=False,trim=True),C(-0.4,-10.2,1.4,0.8,1.2,1.6,mirror=False,trim=True)]),
 ('Wing ears: small swept-up wings at the temples.',[C(-0.3,-3,0,0.6,3,2.5,trim=True,rot=(0.7,0,-0.35),pivot=(-5.3,-6,-0.5))]),
 ('Sallet tail: helmet back extends into a short sloped tail.',[C(-5,0,0,10,0.8,3.2,mirror=False,rot=(0.35,0,0),pivot=(0,-2.2,4.8))]),
]
H21=('h13 + h04: slim glowing purple bars outlining the open face (hood-like frame) with a thin pointed amethyst shard inlaid high on the forehead (glint sweeps up it every 4 s, gentle breathe). Matches dev.justin.infinium.client.HelmetExtras geometry.',
     [C(-4,-6,-5.6,1,6,0.6,glow=True), C(-3,-6,-5.6,6,1,0.6,glow=True,mirror=False),
dict(C(-0.25,-8.90,-5.35,0.5,0.4,0.35,mirror=False),gem=0),dict(C(-0.5,-8.50,-5.35,1.0,0.4,0.35,mirror=False),gem=1),dict(C(-0.75,-8.10,-5.35,1.5,0.4,0.35,mirror=False),gem=2),dict(C(-0.5,-7.70,-5.35,1.0,0.4,0.35,mirror=False),gem=3),dict(C(-0.25,-7.30,-5.35,0.5,0.4,0.35,mirror=False),gem=4)])
def cube_quads(c,f):
    x0,y0,z0,w,h,d=c['b']; out=[]
    def col(px,py,pz):
        if c.get('gem') is not None:
            AM=[(222,180,255),(176,110,240),(130,60,205),(88,30,150)]; part=c['gem']
            col_=(AM[0] if abs(px)<0.3 else AM[1]) if part<=2 else AM[2] if part==3 else AM[3]
            t=f/F; tc=(3*t)%1.0; gl=max(0.0,1-abs(tc-0.10-0.035*(4-part))/0.05)
            k=0.88+0.12*math.cos(TAU*t)+0.55*gl
            if TINT is not None:   # dyed: shard follows the dye (same brightness structure), like the in-game accent overlay
                v=min(255,max(col_)*k); return (int(v*(TINT>>16&255)/255),int(v*(TINT>>8&255)/255),int(v*(TINT&255)/255),255)
            return tuple(min(255,int(v*k)) for v in col_)+(255,)
        base=DARKS[(int(abs(px)*2)*7+int(py*2)*13+int(pz*2)*5)%4]
        if c['trim']: base=tuple(int(v+(96-v)*0.4) for v in base)
        k=0.5+0.5*math.cos(TAU*(f/F-(abs(px)+py+pz)/6.0))
        if c['glow'] or (k>0.93 and w*h*d>3):
            b=k**1.4
            if TINT is not None: v=40+215*b; return (int(v*(TINT>>16&255)/255),int(v*(TINT>>8&255)/255),int(v*(TINT&255)/255),255)
            return (int(30+130*b),int(3+67*b),int(56+172*b),255)
        return base+(255,)
    def grid(n): k=max(1,int(math.ceil(n-1e-6))); return [n*i/k for i in range(k+1)]
    gx,gy,gz=grid(w),grid(h),grid(d)
    def faces():
        for zz in (0,d):
            for i in range(len(gx)-1):
                for j in range(len(gy)-1): yield [(x0+gx[i],y0+gy[j],z0+zz),(x0+gx[i+1],y0+gy[j],z0+zz),(x0+gx[i+1],y0+gy[j+1],z0+zz),(x0+gx[i],y0+gy[j+1],z0+zz)]
        for xx in (0,w):
            for i in range(len(gz)-1):
                for j in range(len(gy)-1): yield [(x0+xx,y0+gy[j],z0+gz[i]),(x0+xx,y0+gy[j],z0+gz[i+1]),(x0+xx,y0+gy[j+1],z0+gz[i+1]),(x0+xx,y0+gy[j+1],z0+gz[i])]
        for yy in (0,h):
            for i in range(len(gx)-1):
                for j in range(len(gz)-1): yield [(x0+gx[i],y0+yy,z0+gz[j]),(x0+gx[i+1],y0+yy,z0+gz[j]),(x0+gx[i+1],y0+yy,z0+gz[j+1]),(x0+gx[i],y0+yy,z0+gz[j+1])]
    for cs in faces():
        out.append((cs,col(sum(p[0] for p in cs)/4,sum(p[1] for p in cs)/4,sum(p[2] for p in cs)/4)))
    if c['pivot']: out=r3.part(out,c['pivot'],c['rot'])
    return out
def extra(cubes,f):
    Q=[]
    for c in cubes:
        q=cube_quads(c,f); Q+=q
        if c['mirror']: Q+=[([(-x,y,z) for x,y,z in cs],col) for cs,col in q]
    return Q
_B={}
def scene(cubes,f):
    if f not in _B:
        Q=r3.build(f,dict(r3.POSES['standing'],nowings=True))
        _B[f]=[(cs,c) for cs,c in Q if min(p[1] for p in cs)<4.5]     # head + shoulders only
    Q=_B[f]+extra(cubes,f)
    return [([(x,y+12,z) for x,y,z in cs],c) for cs,c in Q]   # center on the neck
def view(cubes,f,yaw,pitch=0.0):
    return r3.render(scene(cubes,f),yaw,pitch,0.0,size=240,scale=12).crop((20,0,220,200))
VIEWS=(('front',0.0,0.0),('three_quarter',-math.pi*0.25,0.25),('back',math.pi,0.0))
IC=Image.open(TEX+'item/infinium_helmet.png').convert('RGBA')
GL=Image.open(TEX+'item/cracks/infinium_helmet.png').convert('RGBA')
def icon(cubes,f=0):
    im=IC.copy(); im.alpha_composite(GL.crop((0,16*f,16,16*f+16))); p=im.load(); cover={}
    for cs,c in extra(cubes,f):
        xs=[q[0] for q in cs]; ys=[q[1] for q in cs]; z=sum(q[2] for q in cs)/4
        for ix in range(16):
            for iy in range(16):
                X=(ix+0.5-8)/1.2; Y=(iy+0.5-3)-9
                if min(xs)<=X<=max(xs) and min(ys)<=Y<=max(ys) and ((ix,iy) not in cover or z<cover[(ix,iy)][0]): cover[(ix,iy)]=(z,c)
    for q,(z,c) in cover.items(): p[q]=c
    for q in list(cover):
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
            n=(q[0]+dx,q[1]+dy)
            if 0<=n[0]<16 and 0<=n[1]<16 and p[n][3]==0: p[n]=(12,10,12,255)
    return im
def main():
    os.makedirs(OUT,exist_ok=True); desc=[]
    from PIL import ImageFont
    try: font=ImageFont.load_default(size=16)
    except Exception: font=None
    for i,(d,cubes) in enumerate(V,1):
        P=OUT+f'h{i:02d}/'; os.makedirs(P,exist_ok=True)
        for name,yaw,pit in VIEWS: view(cubes,0,yaw,pit).save(P+name+'.png')
        icon(cubes).resize((96,96),0).save(P+'icon.png')
        open(P+'description.txt','w').write(d+'\n'); desc.append(f'h{i:02d}: {d}')
    open(OUT+'descriptions.txt','w').write('\n'.join(desc)+'\n')
    TW,TH=500,230
    def sheet(f):
        S=Image.new('RGB',(TW*4,TH*5),(36,34,42)); dr=ImageDraw.Draw(S)
        for i,(d,cubes) in enumerate(V):
            x,y=(i%4)*TW,(i//4)*TH
            S.paste(view(cubes,f,0.0).convert('RGB'),(x,y+26)); S.paste(view(cubes,f,-math.pi*0.25,0.25).convert('RGB'),(x+200,y+26))
            S.paste(icon(cubes,f).resize((96,96),0).convert('RGB'),(x+402,y+80))
            dr.text((x+6,y+5),f'h{i+1:02d}',fill=(235,235,235),font=font)
        return S
    sheet(0).save(OUT+'helmet_sheet.png'); print('sheet done',flush=True)
    fr=[sheet(f).resize((TW*2,TH*5//2)) for f in range(0,F,2)]
    fr[0].save(OUT+'helmet_sheet.gif',save_all=True,append_images=fr[1:],duration=600,loop=0)
    print('gif done')
def h21():
    import numpy as np, sys
    d,cubes=H21; P=OUT+'h21/'; os.makedirs(P,exist_ok=True)
    for name,yaw,pit in VIEWS: view(cubes,0,yaw,pit).save(P+name+'.png')
    icon(cubes).resize((96,96),0).save(P+'icon.png')
    open(P+'description.txt','w').write(d+'\n')
    bad={'front':0,'back':0}; fr=[]
    gray=r3.GRAY
    for f in range(F):
        row=Image.new('RGB',(600+96,200),(36,34,42))
        for i,(name,yaw,pit) in enumerate(VIEWS): row.paste(view(cubes,f,yaw,pit).convert('RGB'),(i*200,0))
        row.paste(icon(cubes,f).resize((96,96),0).convert('RGB'),(600,52)); fr.append(row)
        for name,yaw in (('front',0.0),('back',math.pi)):
            Q=[(cs,c) for cs,c in r3.build(f,dict(r3.POSES['standing'],nowings=True),gray) if min(p[1] for p in cs)<4.5]+extra(cubes,f)
            a=np.asarray(r3.render([([(x,y+12,z) for x,y,z in cs],c) for cs,c in Q],yaw,0,0,size=240,scale=12))
            bad[name]+=int(np.any(a!=a[:,::-1],axis=2).sum())
    cl=[view(cubes,f,0.0).crop((64,4,136,58)).resize((432,324),0).convert('RGB') for f in range(F)]
    cl[0].save(P+'front_closeup.png'); cl[0].save(P+'front_closeup.gif',save_all=True,append_images=cl[1:],duration=300,loop=0)
    fr[0].save(P+'h21_animated.gif',save_all=True,append_images=fr[1:],duration=300,loop=0)
    print('h21 view-space symmetry mismatched pixels (40 frames):',bad)
    if any(bad.values()): raise SystemExit('FAIL h21 symmetry')
    with open(OUT+'descriptions.txt') as fh: L=[l for l in fh if not l.startswith('h21:')]
    open(OUT+'descriptions.txt','w').write(''.join(L)+f'h21: {d}\n')
if __name__=='__main__':
    import sys
    h21() if 'h21' in sys.argv else main()

