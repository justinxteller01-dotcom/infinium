"""Closed-wing pose solver/checker. Model space (px): y down, +z = behind the back. Mirrors ElytraModel + WingsLayer (+2px z),
cube inflation 1.0, ModelPart order: translate(pivot) -> rotate Z*Y*X -> scale."""
import numpy as np, itertools, math
def R(rx,ry,rz):
    cx,sx,cy,sy,cz,sz=math.cos(rx),math.sin(rx),math.cos(ry),math.sin(ry),math.cos(rz),math.sin(rz)
    X=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]); Y=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]); Z=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
    return Z@Y@X
def corners(lo,hi): return np.array(list(itertools.product(*zip(lo,hi))),float)
# closed pose (also used by the Java mixin; keep in sync with WingFold.java)
CLOSED=dict(rx=0.2618, rz=-0.2618, zs=0.5, zl=1.9, zr=4.95)
OPEN=dict(rx=0.35, rz=-1.05, zs=1.0, zl=0.0, zr=0.0)
def sm(t): t=min(1.0,max(0.0,t)); return t*t*(3-2*t)
def lerp(a,b,t):
    """Fold schedule (WingFold.java): rotation eases over the whole transition; the layering (z offsets, z scale)
    stays closed for the first half of opening so the wings swing apart before they come back level."""
    r=sm(t); z=sm((t-0.45)/0.55)
    return {k:a[k]+(b[k]-a[k])*(z if k in('zs','zl','zr') else r) for k in a}
def wing(side,p):
    lo,hi=((-11,-1,-1),(1,21,3)) if side=='L' else ((-1,-1,-1),(11,21,3))
    c=corners(lo,hi)*np.array([1,1,p['zs']])
    rz=p['rz'] if side=='L' else -p['rz']
    piv=np.array([5 if side=='L' else -5,0,2+(p['zl'] if side=='L' else p['zr'])])
    return c@R(p['rx'],0,rz).T+piv
def axes(c):
    e=[c[4]-c[0],c[2]-c[0],c[1]-c[0]]; return [v/np.linalg.norm(v) for v in e]
def overlap(A,B):
    ax=axes(A)+axes(B)+[np.cross(a,b) for a in axes(A) for b in axes(B)]
    depth=1e9
    for a in ax:
        n=np.linalg.norm(a)
        if n<1e-9: continue
        a=a/n; pa,pb=A@a,B@a; d=min(pa.max(),pb.max())-max(pa.min(),pb.min())
        if d<=0: return 0.0
        depth=min(depth,d)
    return depth
BODY=corners((-5,-1,-3),(5,13,3))      # chestplate (inflated 1)
def report(p):
    L,Rw=wing('L',p),wing('R',p)
    return overlap(L,Rw),overlap(L,BODY),overlap(Rw,BODY)
if __name__=='__main__':
    print('closed  wing/wing, L/body, R/body:',report(CLOSED))
    for t in np.linspace(0,1,11): print(f't={t:.1f}',['%.2f'%v for v in report(lerp(CLOSED,OPEN,t))])
