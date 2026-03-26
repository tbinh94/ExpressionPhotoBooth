import shutil
import os

src = r"C:\Users\thanh\.antigravity\extensions\redhat.java-1.53.0-win32-x64\jre\21.0.10-win32-x86_64\bin\java.exe"
dst = r"C:\Users\thanh\.antigravity\extensions\redhat.java-1.53.0-win32-x64\jre\21.0.10-win32-x86_64\bin\jlink.exe"

if os.path.exists(src):
    try:
        shutil.copyfile(src, dst)
        print("Done copying")
    except Exception as e:
        print("Error:", e)
else:
    print("Source not found")
    
f = open("copy_result.txt", "w")
f.write("Completed")
f.close()
