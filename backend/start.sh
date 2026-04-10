#!/bin/bash
cd "$(dirname "$0")"

# Clear old URL so app doesn't fetch a stale one
echo "" > current_url.txt

# Kill any existing server to prevent port conflicts
lsof -ti :8000 | xargs kill -9 2>/dev/null

# Start the python server in the background
python3 server.py &
SERVER_PID=$!

echo "[SafetyAI] Backend Server starting (PID $SERVER_PID)..."
sleep 2

echo "[SafetyAI] Establishing secure global tunnel (Serveo)..."
echo "[SafetyAI] --------------------------------------------------------"

# Run tunnel in a loop to auto-reconnect if it drops
while true; do
  # Run SSH tunnel, extract the fresh URL
  # -o ExitOnForwardFailure=yes makes it exit if it can't bind, so we loop
  ssh -o StrictHostKeyChecking=no -o ExitOnForwardFailure=yes -R 80:localhost:8000 serveo.net 2>&1 | while IFS= read -r line; do
    echo "$line"
    # Extract the URL from serveo output
    if echo "$line" | grep -q "serveousercontent.com"; then
      URL=$(echo "$line" | grep -o 'https://[a-zA-Z0-9._-]*serveousercontent\.com')
      if [ -n "$URL" ]; then
        echo "$URL" > current_url.txt
        echo ">>> [SafetyAI] LIVE PUBLIC LINK: $URL"
        echo ">>> [SafetyAI] You can open this on any phone, anywhere!"
      fi
    fi
  done
  echo "[SafetyAI] Tunnel disconnected or failed. Reconnecting in 5 seconds..."
  sleep 5
done

# Cleanup when user stops the tunnel
kill $SERVER_PID
