import os
import glob

print("Searching for jlink.exe...")
found = False
for r, d, f in os.walk(r"C:\Program Files"):
    if "jlink.exe" in f:
        print(f"Found: {os.path.join(r, 'jlink.exe')}")
        found = True

for r, d, f in os.walk(r"C:\Program Files (x86)"):
    if "jlink.exe" in f:
        print(f"Found: {os.path.join(r, 'jlink.exe')}")
        found = True

for r, d, f in os.walk(r"C:\Users\thanh\AppData"):
    if "jlink.exe" in f:
        print(f"Found: {os.path.join(r, 'jlink.exe')}")
        found = True

try:
    for r, d, f in os.walk(r"D:\Program Files"):
        if "jlink.exe" in f:
            print(f"Found: {os.path.join(r, 'jlink.exe')}")
            found = True
except: pass

if not found:
    print("Not found anywhere.")
