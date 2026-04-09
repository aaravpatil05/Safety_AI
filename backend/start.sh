#!/bin/bash
cd "$(dirname "$0")"

# Start the python server in the background
python3 server.py &
SERVER_PID=$!

echo "Starting Backend Server (PID $SERVER_PID)..."
sleep 2

echo "Establishing secure global tunnel..."
echo "Wait for the public URL to appear below, then copy it into MainActivity.java!"
echo "--------------------------------------------------------"
ssh -R 80:localhost:8000 serveo.net

# Cleanup when user stops the tunnel
kill $SERVER_PID
