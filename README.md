# Infinium

A Fabric mod for Minecraft Java 26.2 (Fabric Loader 0.19.5, Java 25), made for the BreadCraft server.

## Layout
- `src/main/java/dev/justin/infinium/` holds the mod code; `src/main/resources/` holds assets and data.
- `gen_*.py` and other root scripts generate textures, data and sounds; `build.ps1` runs them in order, then compiles.
- `frozen/` and `specular_out/` hold hand-locked art and specular maps the build uses.
- `PROJECT_MAP.md` is a short index of where everything lives.

## Building (Windows, PowerShell)
Needs JDK 25 (set `$env:JDK25` to its `bin` folder or have `javac`/`jar` on PATH), Python 3 with Pillow and numpy, and optionally ffmpeg for sound generation.

Not included in this repo, you need to add them yourself:
1. `libs/`, `libs_compile/`, `libs_jei/`, `libs_opt/`: the Minecraft 26.2 client jar, Fabric Loader/API and the compat mod jars it compiles against. Ask Justin for the list.
2. `ref/mc/`: the extracted contents of your own Minecraft 26.2 client jar (some generators read vanilla textures from it).

Then run:
```
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```
Output: `out\infinium-1.0.0+26.2.jar`. `quick_build.ps1` skips the generators and only compiles.
