import zipfile

with zipfile.ZipFile(r"F:\GrokBot\work\infinium_pass2\libs\minecraft-26.2-client.jar") as z:
    for name in z.namelist():
        if not name.endswith(".class"): continue
        data = z.read(name)
        if b"setupAnim" in data and b"ElytraModel" in data:
            print("Found in:", name)
