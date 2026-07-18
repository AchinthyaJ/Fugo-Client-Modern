import subprocess
import traceback

output_path = '/home/inchara/Project Aether/Aether One/scratch/lighttexture_output.txt'
jar_path = '/home/inchara/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.2.0_mapped_official_1.20.1/forge-1.20.1-47.2.0_mapped_official_1.20.1.jar'

try:
    result = subprocess.run(
        ['javap', '-cp', jar_path, 'net.minecraft.client.renderer.LightTexture'],
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
