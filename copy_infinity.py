#!/usr/bin/env python3
"""R8 13.16 C: copy the staged Infinity v2 textures (design_update/infinity_assets) into the mod resources. Not regenerated."""
import os, shutil, glob
S='/workspace/design_update/infinity_assets/src/main/resources/'
R='src/main/resources/'
n=0
for p in glob.glob(S+'**/*',recursive=True):
    if os.path.isdir(p): continue
    q=R+os.path.relpath(p,S); os.makedirs(os.path.dirname(q),exist_ok=True); shutil.copy2(p,q); n+=1
print('copy_infinity: copied',n,'staged Infinity files')
