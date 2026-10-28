# Windows equivalent of build.sh + gen_all.sh. Run from this folder:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
# Needs: JDK 25 (set $env:JDK25 to its bin folder, or have javac/jar on PATH), Python 3 with Pillow (pip install pillow numpy).
# Output: out\infinium-1.0.0+26.2.jar. Builds in a working copy; never writes into %APPDATA%\.minecraft.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdk = if ($env:JDK25) { $env:JDK25 } elseif (Test-Path 'C:\Program Files\Java\jdk-26\bin') { 'C:\Program Files\Java\jdk-26\bin' } else { '' }
$javac = if ($jdk) { Join-Path $jdk 'javac.exe' } else { 'javac' }
$jarExe = if ($jdk) { Join-Path $jdk 'jar.exe' } elseif (Get-Command jar -ErrorAction SilentlyContinue) { 'jar' } else { 'jar' }
# gen_all.sh (order matters: gen_cracks overrides the v1 purple art)
$steps = @('gen_guide_art.py','gen_textures.py','gen_data.py','copy_infinity.py','gen_templates.py','gen_cracks.py','helmet_corners.py','gen_helmet_extra.py',
  @('gen_dye.py','src/main/resources'),'gen_styles.py','gen_tool_styles.py','gen_wing_styles.py','gen_new_items.py','gen_finder.py','gen_lang_extra.py','gen_rocket_anim.py',
  'white_fix.py',@('hue_fix.py','--fix'),@('loop_check.py','--fix'),'gen_gui_icons.py','r8_post.py','gen_specular.py','gen_dark_ui.py')
foreach ($s in $steps) { $a = @($s); & python $a; if ($LASTEXITCODE -ne 0) { throw "generator failed: $($a -join ' ')" } }
if (Get-Command ffmpeg -ErrorAction SilentlyContinue) {
  & python gen_crystal_sounds.py; if ($LASTEXITCODE -ne 0) { throw 'gen_crystal_sounds.py failed' }
  & python gen_flight_sounds.py; if ($LASTEXITCODE -ne 0) { throw 'gen_flight_sounds.py failed' }
  & python gen_sword_sounds.py; if ($LASTEXITCODE -ne 0) { throw 'gen_sword_sounds.py failed' }
}
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item -ItemType Directory -Force -Path build\classes, out | Out-Null
$cp = ((Get-ChildItem libs\*.jar, libs_jei\*.jar, libs_compile\*.jar, libs_opt\*.jar).FullName) -join ';'
$src = (Get-ChildItem src\main\java -Recurse -Filter *.java).FullName
$src | Set-Content -Encoding ascii build\sources.txt
& $javac --release 25 -proc:none -nowarn -cp $cp -d build\classes '@build\sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
Copy-Item src\main\resources\* build\classes\ -Recurse -Force
if (Test-Path specular_out) { Copy-Item specular_out\* build\classes\ -Recurse -Force }  # labPBR _s maps (gen_specular.py), kept out of src so fix/check scripts never see them
$out = 'out\infinium-1.0.0+26.2.jar'; if (Test-Path $out) { Remove-Item $out }
Push-Location build\classes; & $jarExe --create --file ..\..\$out .; Pop-Location
Get-FileHash $out -Algorithm SHA256 | Format-List
