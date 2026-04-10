import subprocess
import time
import re
import sys
import os

# Start the python backend
backend_proc = subprocess.Popen([sys.executable, "backend/server.py"])

print("Backend started with PID", backend_proc.pid)

# Start ssh tunnel
# Using localhost.run for stability if serveo is failing
tunnel_proc = subprocess.Popen(
    ["ssh", "-o", "StrictHostKeyChecking=no", "-R", "80:localhost:8000", "localhost.run"],
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True
)

url = None
timeout = 30
start_time = time.time()

while time.time() - start_time < timeout:
    line = tunnel_proc.stdout.readline()
    if not line:
        continue
    print("Tunnel Output: ", line.strip())
    # localhost.run URL format: https://[random].lhr.life
    match = re.search(r'(https://[a-zA-Z0-9-]+\.lhr\.life)', line)
    if match:
        url = match.group(1)
        break

if url:
    print(f"\nDiscovered URL: {url}")
    # Now patch MainActivity.java
    filepath = "app/src/main/java/com/safetyai/app/MainActivity.java"
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Replace the current BACKEND_URL line
    new_content = re.sub(
        r'public static final String BACKEND_URL = ".*";',
        f'public static final String BACKEND_URL = "{url}";',
        content
    )
    with open(filepath, 'w') as f:
        f.write(new_content)
    
    print("Successfully patched MainActivity.java with the live URL.")
else:
    print("\nFailed to get a URL.")
    tunnel_proc.terminate()
    backend_proc.terminate()
