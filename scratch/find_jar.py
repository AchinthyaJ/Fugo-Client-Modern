import zipfile

path = '/home/inchara/Project Aether/Aether One/forge-1.20.1/libs/mcef-1.12.2-1.11-api.jar'
jar = zipfile.ZipFile(path)

# Print all entries
lines = sorted(jar.namelist())
with open('/home/inchara/Project Aether/Aether One/scratch/jar_contents.txt', 'w') as f:
    for name in lines:
        f.write(name + '\n')
print("Written", len(lines), "entries to jar_contents.txt")
