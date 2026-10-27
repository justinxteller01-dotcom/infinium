$ErrorActionPreference = 'Stop'
Set-Location 'F:\GrokBot\work\infinium_pass2'
$jdk = 'C:\Program Files\Java\jdk-26\bin'
$javac = Join-Path $jdk 'javac.exe'
New-Item -ItemType Directory -Force -Path 'build\test_classes' | Out-Null
$cp = ((Get-ChildItem libs\*.jar, libs_jei\*.jar, libs_compile\*.jar, libs_opt\*.jar).FullName) -join ';'
(Get-ChildItem src\main\java -Recurse -Filter *.java).FullName | Set-Content -Encoding ascii 'build\test_sources.txt'
& $javac --release 25 -proc:none -nowarn -cp $cp -d build\test_classes '@build\test_sources.txt'
if ($LASTEXITCODE -eq 0) {
    Write-Host 'DRY RUN COMPILE SUCCESS: 0 ERRORS'
} else {
    Write-Host "DRY RUN COMPILE FAILED: $LASTEXITCODE"
}
