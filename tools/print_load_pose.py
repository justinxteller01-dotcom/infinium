import subprocess

cmd = [
    r"C:\Program Files\Java\jdk-26\bin\javap.exe",
    "-cp",
    r"F:\GrokBot\work\infinium_pass2\libs\minecraft-26.2-client.jar",
    "-c", "-p",
    "net.minecraft.client.model.geom.ModelPart"
]
res = subprocess.run(cmd, capture_output=True, text=True)
printing = False
for line in res.stdout.splitlines():
    if "loadPose(" in line:
        printing = True
    elif line.startswith("  public ") or line.startswith("  private ") or line.startswith("  protected "):
        if printing and "loadPose(" not in line:
            printing = False
    if printing:
        print(line)
