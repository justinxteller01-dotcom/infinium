# Tiny software renderer (painter's algorithm, per-texel quads) of the vanilla player + armor + elytra boxes
# with the Infinium textures. Preview only; geometry follows vanilla HumanoidModel/ElytraModel box layouts.
import math, os
from PIL import Image, ImageDraw
T='src/main/resources/assets/infinium/textures/entity/'
SKIN=Image.open('ref/mc/assets/minecraft/textures/entity/player/wide/steve.png').convert('RGBA')
def comp(a,b): c=a.copy(); c.alpha_composite(b); return c
_TC={}
FRAMES_HINT=[40]   # frames of the main (armor) loop; set by gen_variants
def tex(layer,f):
    k=(T,layer,f)
    if k not in _TC:
        n=len([x for x in os.listdir(T+f'infinium_glow/{layer}') if x.endswith('.png')])
        ff=f if f<n else f*n//FRAMES_HINT[0] % n             # layer with fewer frames (wings): same loop, scaled index
        _TC[k]=comp(Image.open(T+f'equipment/{layer}/infinium.png').convert('RGBA'), Image.open(T+f'infinium_glow/{layer}/{ff}.png').convert('RGBA'))
    return _TC[k]

def rot(p,rx,ry,rz):
    x,y,z=p
    # ModelPart: Quaternionf.rotationZYX(z,y,x) = Rz*Ry*Rx, i.e. the vector is rotated about X first, then Y, then Z
    c,s=math.cos(rx),math.sin(rx); y,z=y*c-z*s,y*s+z*c
    c,s=math.cos(ry),math.sin(ry); x,z=x*c+z*s,-x*s+z*c
    c,s=math.cos(rz),math.sin(rz); x,y=x*c-y*s,x*s+y*c
    return (x,y,z)

def box_quads(img,u,v,x0,y0,z0,w,h,d,g=0.0,mirror=False):
    """Yield (corners[4], rgba) per texel for a vanilla-UV box, inflated by g.
    mirror=True reproduces ModelPart mirror(): the box is built at -(x0+w) and reflected in local x,
    so right/left faces swap sides and every face reads reversed - an exact mirror image."""
    if mirror: x0=-(x0+w)
    X0,Y0,Z0=x0-g,y0-g,z0-g; sx,sy,sz=(w+2*g)/w,(h+2*g)/h,(d+2*g)/d
    px=img.load(); out=[]
    def face(fu,fv,fw,fh,mapf):
        for j in range(fh):
            for i in range(fw):
                c=px[fu+i,fv+j]
                if c[3]<128: continue
                cs=[mapf(i+a,j+b) for a,b in((0,0),(1,0),(1,1),(0,1))]
                if mirror: cs=[(-x,y,z) for x,y,z in cs]
                out.append((cs,c[:3]+(255,)))
    face(u+d,v+d,w,h,lambda a,b:(X0+a*sx,Y0+b*sy,Z0))                       # front (-z)
    face(u+2*d+w,v+d,w,h,lambda a,b:(X0+(w-a)*sx,Y0+b*sy,Z0+d*sz))           # back (+z)
    face(u,v+d,d,h,lambda a,b:(X0,Y0+b*sy,Z0+(d-a)*sz))                      # right (-x)
    face(u+d+w,v+d,d,h,lambda a,b:(X0+w*sx,Y0+b*sy,Z0+a*sz))                 # left (+x)
    face(u+d,v,w,d,lambda a,b:(X0+a*sx,Y0,Z0+b*sz))                          # top
    face(u+d+w,v,w,d,lambda a,b:(X0+a*sx,Y0+h*sy,Z0+b*sz))                   # bottom
    return out

def part(quads,pivot,r=(0,0,0)):
    res=[]
    for cs,c in quads:
        res.append(([tuple(pivot[k]+rot(p,*r)[k] for k in range(3)) for p in cs],c))
    return res

def build(f,pose,skin=None):
    SK=skin or SKIN
    arm=tex('humanoid',f); leg=tex('humanoid_leggings',f); wing=tex('wings',f)
    Q=[]
    la=pose.get('arm',0.0)
    def limbset(img,g,parts):
        for (u,v,x0,y0,z0,w,h,d,pv,r,m) in parts: Q.extend(part(box_quads(img,u,v,x0,y0,z0,w,h,d,g,m),pv,r))
    body=[(0,0,-4,-8,-4,8,8,8,(0,0,0),(0,0,0),False),(16,16,-4,0,-2,8,12,4,(0,0,0),(0,0,0),False),
          (40,16,-3,-2,-2,4,12,4,(-5,2,0),(0,0,la),False),(40,16,-1,-2,-2,4,12,4,(5,2,0),(0,0,-la),True),
          (0,16,-2,0,-2,4,12,4,(-1.9,12,0),(0,0,0),False),(0,16,-2,0,-2,4,12,4,(1.9,12,0),(0,0,0),True)]
    limbset(SK,0.0,body)                                             # steve underneath
    limbset(arm,1.0,[p for k,p in enumerate(body) if k in(0,1,2,3,4,5)])   # helmet/chest/arms/boots layer
    limbset(leg,0.5,[body[1],body[4],body[5]])                        # leggings layer
    # elytra (64x32, wing box uv 22,0 size 10x20x2); WingsLayer translates +2px in z
    rx,rz=pose['wing']
    xs,ys,zs=pose.get('wscale',(1,1,1))
    sc=lambda qs:[([(x*xs,y*ys,z*zs) for x,y,z in cs],c) for cs,c in qs]
    if 'zs' in pose: xs,ys,zs=1,1,pose['zs']          # closed fold: thinner wings, layered (see wing_fold.py / WingFold.java)
    g=pose.get('wg',0.0); zl,zr=pose.get('zl',0.0),pose.get('zr',0.0)
    lw=part(sc(box_quads(wing,22,0,-10,0,0,10,20,2,g,False)),(5,0,2+zl),(rx,0,rz))
    rw=part(sc(box_quads(wing,22,0,0,0,0,10,20,2,g,True)),(-5,0,2+zr),(rx,0,-rz))
    if not pose.get('nowings'): Q.extend(lw); Q.extend(rw)
    return Q

import numpy as np
def render(Q,yaw,pitch,body_pitch=0.0,size=420,scale=9.0,bg=(46,44,52),shade=False):
    n=len(Q); P=np.array([cs for cs,c in Q],float); C=np.array([c[:3] for cs,c in Q],np.int64)
    x,y,z=P[...,0],P[...,1]-12,P[...,2]
    def R(x,y,z,rx,ry):
        c,s_=math.cos(rx),math.sin(rx); y,z=y*c-z*s_,y*s_+z*c
        c,s_=math.cos(ry),math.sin(ry); x,z=x*c+z*s_,-x*s_+z*c
        return x,y,z
    x,y,z=R(x,y,z,body_pitch,0); x,y,z=R(x,y,z,0,yaw); x,y,z=R(x,y,z,pitch,0)
    if shade:   # simple directional light on face normals so dark faces separate
        ax,ay,az=x[:,1]-x[:,0],y[:,1]-y[:,0],z[:,1]-z[:,0]; bx,by,bz=x[:,3]-x[:,0],y[:,3]-y[:,0],z[:,3]-z[:,0]
        nx,ny,nz=ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx; nl=np.sqrt(nx*nx+ny*ny+nz*nz)+1e-9
        lit=0.55+0.45*np.abs((-0.3*nx-0.6*ny-0.75*nz)/nl)
        C=np.clip(C*lit[:,None]+np.where(C.max(1,keepdims=True)<70,14,0),0,255).astype(np.int64)
    sx=size/2+x*scale; sy=size/2+y*scale; dep=z.mean(1)
    x0=np.floor(sx.min(1)-0.5).astype(int); y0=np.floor(sy.min(1)-0.5).astype(int)
    bw=int(np.ceil((sx.max(1)-sx.min(1)).max()))+2; bh=int(np.ceil((sy.max(1)-sy.min(1)).max()))+2
    hits=[]
    for dy in range(bh):
        for dx in range(bw):
            px_=x0+dx; py_=y0+dy; cx=px_+0.5; cy=py_+0.5
            sgn=[]
            for k in range(4):
                ax,ay=sx[:,k],sy[:,k]; bx,by=sx[:,(k+1)%4],sy[:,(k+1)%4]
                sgn.append((bx-ax)*(cy-ay)-(by-ay)*(cx-ax))
            sgn=np.stack(sgn,1); inside=(np.all(sgn>=-1e-9,1)|np.all(sgn<=1e-9,1))&(px_>=0)&(px_<size)&(py_>=0)&(py_<size)
            idx=np.nonzero(inside)[0]
            if len(idx): hits.append((py_[idx]*size+px_[idx],dep[idx],idx))
    img=np.zeros((size*size,3),np.uint8); img[:]=bg
    if hits:
        pix=np.concatenate([h[0] for h in hits]); d=np.concatenate([h[1] for h in hits]); qi=np.concatenate([h[2] for h in hits])
        key=C[qi,0]*65536+C[qi,1]*256+C[qi,2]          # deterministic tie-break by colour (mirror-invariant)
        o=np.lexsort((-key,np.round(d,6),pix)); pix,qi=pix[o],qi[o]
        first=np.ones(len(pix),bool); first[1:]=pix[1:]!=pix[:-1]
        img[pix[first]]=C[qi[first]]
    return Image.fromarray(img.reshape(size,size,3)).convert('RGBA')

POSES={'fold':{'wing':(0.2618,-0.2618),'zs':0.5,'zl':1.9,'zr':4.95,'wg':1.0,'arm':0.0,'bp':0.0},'fold_old':{'wing':(0.06,-0.05),'arm':0.0,'bp':0.0},'roll':{'wing':(0.30,-0.12),'arm':0.0,'bp':0.0,'wscale':(0.75,0.40,1.6)},'standing':{'wing':(0.35,-1.05),'wg':1.0,'arm':0.0,'bp':0.0},'gliding':{'wing':(0.35,-math.pi/2+0.05),'arm':0.3,'bp':math.pi/2-0.25}}
def render_gif(texroot, frames, dur_ms, gif_path, png_path, size=300):
    """texroot: .../textures/ ; 2 panels: standing 3/4 + gliding from behind."""
    global T
    T=texroot+'entity/'
    out=[]
    for f in range(frames):
        row=Image.new('RGBA',(size*2,size))
        im=render(build(f,POSES['standing']),math.pi*0.78,0.35,0.0,size=size,scale=6.5); row.paste(im,(0,0))
        im=render(build(f,POSES['gliding']),math.pi,0.85,POSES['gliding']['bp'],size=size,scale=6.5); row.paste(im,(size,0))
        if f==0: row.save(png_path)
        out.append(row.convert('RGB'))
    out[0].save(gif_path,save_all=True,append_images=out[1:],duration=dur_ms,loop=0)

GRAY=Image.new('RGBA',(64,64),(120,120,120,255))
VIEWS=(('front',0.0,{}),('back',math.pi,{}),('back_hidden',math.pi,{'nowings':True}),('front_hidden',0.0,{'nowings':True}),
       ('back_foldA',math.pi,{'wing':(0.06,-0.05)}),('back_rollB',math.pi,{'wing':(0.30,-0.12),'wscale':(0.75,0.40,1.6)}),('front_foldA',0.0,{'wing':(0.06,-0.05)}))
def view_symmetry(texroot, frames, size=200, scale=5):
    """Orthographic front/back renders per frame (neutral gray body so Steve's asymmetric skin doesn't count);
    compare each image with its left-right mirror. Returns {view: total mismatched pixels}."""
    global T
    T=texroot+'entity/'; res={}
    for name,yaw,extra in VIEWS:
        bad=0
        for f in range(frames):
            im=render(build(f,dict(POSES['standing'],**extra),GRAY),yaw,0.0,0.0,size=size,scale=scale)
            a=np.asarray(im); bad+=int(np.any(a!=a[:,::-1],axis=2).sum())
        res[name]=bad
    return res
PANELS=((0.0,{}),(math.pi,{}),(math.pi,{'wing':(0.06,-0.05)}),(math.pi,{'wing':(0.30,-0.12),'wscale':(0.75,0.40,1.6)}),(math.pi,{'nowings':True}))
PANEL_NAMES=('front (glide on)','back (glide on)','back, glide off A: fold','back, glide off B: roll','back, wings hidden')
def render_views(texroot, frame, path, size=300, gif=None, frames=0, dur=100):
    """Orthographic: front, back (wings), back with glide OFF (helmet + leggings back visible)."""
    global T
    T=texroot+'entity/'; st=POSES['standing']
    def row(f):
        r=Image.new('RGBA',(size*len(PANELS),size))
        for i,(yaw,extra) in enumerate(PANELS):
            r.paste(render(build(f,dict(st,**extra)),yaw,0.0,0.0,size=size,scale=6),(i*size,0))
        return r
    row(frame).save(path)
    if gif:
        fr=[row(f).convert('RGB') for f in range(frames)]
        fr[0].save(gif,save_all=True,append_images=fr[1:],duration=dur,loop=0)
