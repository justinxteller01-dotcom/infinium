import subprocess
import sys

cmd = [
    r"C:\Program Files\Java\jdk-26\bin\javap.exe",
    "-cp",
    r"F:\GrokBot\work\infinium_pass2\libs\minecraft-26.2-client.jar",
    "-c",
    sys.argv[1]
]
res = subprocess.run(cmd, capture_output=True, text=True)
for line in res.stdout.splitlines():
    if len(sys.argv) > 2:
        if any(term in line for term in sys.argv[2:]):
            print(line)
    else:
        print(line)
