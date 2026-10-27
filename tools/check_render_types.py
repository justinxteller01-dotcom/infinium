import subprocess

cmd = [
    r"C:\Program Files\Java\jdk-26\bin\javap.exe",
    "-cp",
    r"F:\GrokBot\work\infinium_pass2\libs\minecraft-26.2-client.jar",
    "-c", "-p",
    "net.minecraft.client.renderer.rendertype.RenderTypes"
]
res = subprocess.run(cmd, capture_output=True, text=True)
for line in res.stdout.splitlines():
    if "armor" in line.lower() or "translucent" in line.lower() or "elytra" in line.lower():
        print(line)
