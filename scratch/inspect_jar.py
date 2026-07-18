import subprocess
import traceback

output_path = '/home/inchara/Project Aether/Aether One/scratch/output.txt'

try:
    jar_path = '/home/inchara/Project Aether/Aether One/forge-1.20.1/libs/mcef-1.12.2-1.11-api.jar'
    result = subprocess.run(
        ['javap', '-cp', jar_path, 'net.montoyo.mcef.api.IBrowser'],
        capture_output=True,
        text=True
    )
    with open(output_path, 'w') as f:
        f.write("STDOUT:\n")
        f.write(result.stdout)
        f.write("\nSTDERR:\n")
        f.write(result.stderr)
        f.write(f"\nExit code: {result.returncode}\n")
except Exception as e:
    with open(output_path, 'w') as f:
        f.write("Error:\n")
        f.write(traceback.format_exc())
