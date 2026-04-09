import http.server
import socketserver
import os
import json
import time
import urllib.parse

PORT = 8000
DATA_DIR = "data"

if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

class SOSHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/upload':
            # Receive headers for location
            lat = self.headers.get('X-Location-Lat', 'Unknown')
            lon = self.headers.get('X-Location-Lon', 'Unknown')
            
            # Read body (the audio file)
            content_length = int(self.headers.get('Content-Length', 0))
            audio_data = self.rfile.read(content_length)
            
            timestamp = str(int(time.time()))
            
            # Save audio
            audio_filename = f"{timestamp}.3gp"
            audio_filepath = os.path.join(DATA_DIR, audio_filename)
            with open(audio_filepath, 'wb') as f:
                f.write(audio_data)
                
            # Save metadata
            meta_data = {
                "timestamp": timestamp,
                "lat": lat,
                "lon": lon,
                "audio_filename": audio_filename
            }
            meta_filepath = os.path.join(DATA_DIR, f"{timestamp}.json")
            with open(meta_filepath, 'w') as f:
                json.dump(meta_data, f)
                
            # Respond
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"id": timestamp}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path.startswith('/evidence/'):
            evidence_id = self.path.split('/')[-1]
            meta_filepath = os.path.join(DATA_DIR, f"{evidence_id}.json")
            
            if os.path.exists(meta_filepath):
                with open(meta_filepath, 'r') as f:
                    meta_data = json.load(f)
                    
                lat = meta_data.get('lat')
                lon = meta_data.get('lon')
                audio_file = meta_data.get('audio_filename')
                
                # HTML Template with Google Maps and elegant aesthetics
                html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SOS Evidence Lockbox</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap');
        body {{
            margin: 0;
            padding: 0;
            background-color: #0f172a;
            color: #f8fafc;
            font-family: 'Inter', sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
        }}
        .container {{
            background: #1e293b;
            border-radius: 16px;
            padding: 2rem;
            width: 90%;
            max-width: 600px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
            border: 1px solid #334155;
            text-align: center;
        }}
        h1 {{
            color: #ef4444;
            margin-top: 0;
            font-size: 2rem;
            font-weight: 800;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
        }}
        .status {{
            background: #fef2f2;
            color: #ef4444;
            padding: 8px 16px;
            border-radius: 999px;
            font-size: 0.875rem;
            font-weight: 600;
            display: inline-block;
            margin-bottom: 2rem;
            animation: pulse 2s infinite;
        }}
        @keyframes pulse {{
            0% {{ opacity: 1; }}
            50% {{ opacity: 0.6; }}
            100% {{ opacity: 1; }}
        }}
        .map-container {{
            width: 100%;
            height: 300px;
            border-radius: 12px;
            overflow: hidden;
            margin-bottom: 2rem;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        }}
        iframe {{
            width: 100%;
            height: 100%;
            border: none;
        }}
        .audio-container {{
            background: #0f172a;
            padding: 1.5rem;
            border-radius: 12px;
            border: 1px solid #334155;
        }}
        h3 {{
            margin-top: 0;
            color: #cbd5e1;
        }}
        audio {{
            width: 100%;
            margin-top: 1rem;
        }}
        .footer {{
            margin-top: 2rem;
            color: #64748b;
            font-size: 0.875rem;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🚨 SOS Alert Active</h1>
        <div class="status">LIVE EVIDENCE LOCKBOX</div>
        
        <div class="map-container">
            <iframe src="https://maps.google.com/maps?q={lat},{lon}&hl=es;z=15&amp;output=embed"></iframe>
        </div>
        
        <div class="audio-container">
            <h3>🎙️ Secure Audio Recording</h3>
            <p style="font-size: 0.875rem; color: #94a3b8;">Captured during the incident at {evidence_id}</p>
            <audio controls>
                <source src="/data/{audio_file}" type="audio/3gpp">
                Your browser does not support the audio element.
            </audio>
        </div>
        
        <div class="footer">
            SafetyAI © 2026 • Encrypted Evidence
        </div>
    </div>
</body>
</html>"""
                self.send_response(200)
                self.send_header('Content-type', 'text/html')
                self.end_headers()
                self.wfile.write(html.encode('utf-8'))
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Evidence not found.")
        elif self.path.startswith('/data/'):
            # Allow serving static files from data/ mostly for audio
            super().do_GET()
        elif self.path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b"SafetyAI Backend Server Running.")
        else:
            self.send_response(404)
            self.end_headers()

with socketserver.TCPServer(("", PORT), SOSHandler) as httpd:
    print(f"SOS Backend starting at port {PORT}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
