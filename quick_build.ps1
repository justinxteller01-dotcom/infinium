$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdk = if ($env:JDK25) { $env:JDK25 } elseif (Test-Path 'C:\Program Files\Java\jdk-26\bin') { 'C:\Program Files\Java\jdk-26\bin' } else { '' }
$javac = if ($jdk) { Join-Path $jdk 'javac.exe' } else { 'javac' }
$jarExe = if ($jdk) { Join-Path $jdk 'jar.exe' } elseif (Get-Command jar -ErrorAction SilentlyContinue) { 'jar' } else { 'jar' }
if (-not (Test-Path build\classes)) { New-Item -ItemType Directory -Force -Path build\classes, out | Out-Null }
$cp = ((Get-ChildItem libs\*.jar, libs_jei\*.jar, libs_compile\*.jar, libs_opt\*.jar).FullName) -join ';'
$src = (Get-ChildItem src\main\java -Recurse -Filter *.java).FullName
$src | Set-Content -Encoding ascii build\sources.txt
& $javac --release 25 -proc:none -nowarn -cp $cp -d build\classes '@build\sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
Copy-Item src\main\resources\* build\classes\ -Recurse -Force
if (Test-Path specular_out) { Copy-Item specular_out\* build\classes\ -Recurse -Force }
$out = 'out\infinium-1.0.0+26.2.jar'
if (Test-Path $out) { Remove-Item $out }
Push-Location build\classes
& $jarExe --create --file ..\..\$out .
Pop-Location
Get-FileHash $out -Algorithm SHA256 | Format-List
