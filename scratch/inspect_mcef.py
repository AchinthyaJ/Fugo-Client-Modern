import zipfile, collections

JARS = {
    "MCEF x91l6OKB (raw)": '/home/inchara/.gradle/caches/modules-2/files-2.1/maven.modrinth/mcef/x91l6OKB/6d9fa1433d509be242d1d74282d02a0fbebde110/mcef-x91l6OKB.jar',
    "MCEF x91l6OKB (deobf)": '/home/inchara/.gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/mcef/x91l6OKB_mapped_official_1.20.1/mcef-x91l6OKB_mapped_official_1.20.1.jar',
    "Legacy API jar":  '/home/inchara/Project Aether/Aether One/forge-1.20.1/libs/mcef-1.12.2-1.11-api.jar',
}

for label, path in JARS.items():
    print(f"\n=== {label} ===")
    try:
        jar = zipfile.ZipFile(path)
        entries = [e for e in jar.namelist() if e.endswith('.class')]
        pkgs = collections.Counter()
        for e in entries:
            parts = e.split('/')
            if len(parts) >= 3:
                pkg = '/'.join(parts[:3])
            elif len(parts) >= 2:
                pkg = '/'.join(parts[:2])
            else:
                pkg = parts[0]
            pkgs[pkg] += 1
        for pkg, count in sorted(pkgs.items()):
            print(f"  {pkg}: {count} classes")
        print(f"  TOTAL: {len(entries)} classes")
    except Exception as e:
        print(f"  ERROR: {e}")
