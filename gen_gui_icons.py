#!/usr/bin/env python3
"""13.15 B: 16x16 inventory settings gear (+ hover). 8 teeth, outer r 6, inner r 4, hole r 1.5, centerd in 12x12 (2 px margin).
1 px outline #0E0612, body #5A1E8C, top-left highlight #9B3FD6, bottom-right shade #2E0D4A; hover body #7A2DB8 / highlight #C060F0."""
import math, os
from PIL import Image
D='src/main/resources/assets/infinium/textures/gui/'; os.makedirs(D,exist_ok=True)
def inside(x,y):
    cx=cy=7.5; dx,dy=x-cx,y-cy; r=math.hypot(dx,dy); a=math.atan2(dy,dx)
    tooth=(math.cos(8*a)>0.25)
    rr=6.0 if tooth else 4.0
    return 1.5<r<=rr+0.01
def gear(body,hi,shade,name):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); p=im.load()
    m={(x,y) for x in range(16) for y in range(16) if inside(x+0.5-0.5,y+0.5-0.5)}
    rgb=lambda h:(int(h[1:3],16),int(h[3:5],16),int(h[5:7],16),255)
    for (x,y) in m:
        edge=any((x+dx,y+dy) not in m for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
        if edge: p[x,y]=rgb('#0E0612')
        elif (x+y)<14: p[x,y]=rgb(hi) if (x+y)<11 else rgb(body)
        elif (x+y)>17: p[x,y]=rgb(shade)
        else: p[x,y]=rgb(body)
    im.save(D+name)
gear('#5A1E8C','#9B3FD6','#2E0D4A','settings_button.png')
gear('#7A2DB8','#C060F0','#2E0D4A','settings_button_hover.png')
print('gui icons written')
