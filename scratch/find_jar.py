import os
import traceback

output_path = '/home/inchara/Project Aether/Aether One/scratch/output.txt'

try:
    loom_dir = '/home/inchara/.gradle/caches/fabric-loom'
    paths = []
    if os.path.exists(loom_dir):
        for root, dirs, files in os.walk(loom_dir):
            for file in files:
                if file.endswith('.jar'):
                    paths.append(os.path.join(root, file))
    else:
        paths.append("loom_dir does not exist")
        
    with open(output_path, 'w') as f:
        f.write("Success!\n")
        for p in paths:
            f.write(p + "\n")
except Exception as e:
    with open(output_path, 'w') as f:
        f.write("Error:\n")
        f.write(traceback.format_exc())
