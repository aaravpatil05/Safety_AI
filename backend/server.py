import http.server
import socketserver
import os
import json
import time
import threading
import socket
import string
import re

PORT = 8000
BEACON_PORT = 8765   # UDP discovery port — Android listens here
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
TUNNEL_URL_FILE = os.path.join(BASE_DIR, "current_url.txt")

if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

def get_my_local_ip():
    """Get the Mac's LAN IP address (works on any network)."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def start_udp_beacon():
    """Broadcast presence on the LAN."""
    def beacon_loop():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        my_ip = get_my_local_ip()
        print(f"[SafetyAI] Server IP: {my_ip}")
        while True:
            try:
                payload = json.dumps({
                    "service": "safetyai_server",
                    "ip": my_ip,
                    "port": PORT
                }).encode('utf-8')
                sock.sendto(payload, ('<broadcast>', BEACON_PORT))
            except Exception:
                pass
            time.sleep(2)

    t = threading.Thread(target=beacon_loop, daemon=True)
    t.start()

def get_current_tunnel_url():
    """Read tunnel URL reliably."""
    try:
        if os.path.exists(TUNNEL_URL_FILE):
            with open(TUNNEL_URL_FILE, "r") as f:
                url = f.read().strip()
                url = "".join(filter(lambda x: x in string.printable, url))
                if url.startswith("https://"):
                    return url
    except Exception:
        pass
    return None

class SOSHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        # Clean logging
        pass

    def send_error_html(self, code, title, message):
        self.send_response(code)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        html = f"""<!DOCTYPE html><html><body style="font-family:sans-serif;text-align:center;padding:50px;">
                <h1 style="color:#d93025;">{title}</h1><p>{message}</p>
                <a href="/" style="color:#1a73e8;">Back to Home</a></body></html>"""
        self.wfile.write(html.encode('utf-8'))

    def do_POST(self):
        try:
            if self.path == '/upload':
                lat = self.headers.get('X-Location-Lat', 'Unknown')
                lon = self.headers.get('X-Location-Lon', 'Unknown')
                content_length = int(self.headers.get('Content-Length', 0))
                audio_data = self.rfile.read(content_length)
                
                timestamp = str(int(time.time()))
                audio_filename = f"{timestamp}.3gp"
                audio_filepath = os.path.join(DATA_DIR, audio_filename)
                
                with open(audio_filepath, 'wb') as f:
                    f.write(audio_data)
                    
                meta_data = {"timestamp": timestamp, "lat": lat, "lon": lon, "audio_filename": audio_filename}
                with open(os.path.join(DATA_DIR, f"{timestamp}.json"), 'w') as f:
                    json.dump(meta_data, f)
                    
                print(f"[SafetyAI] Received SOS Upload: {timestamp}")
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"id": timestamp}).encode('utf-8'))
            else:
                self.send_error(404)
        except Exception as e:
            self.send_error(500, str(e))

    def do_GET(self):
        try:
            if self.path == '/favicon.ico':
                self.send_response(204)
                self.end_headers()
                return

            if self.path == '/ping':
                self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps({"status": "online"}).encode('utf-8'))
                return

            if self.path == '/url':
                url = get_current_tunnel_url()
                self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps({"url": url if url else "null"}).encode('utf-8'))
                return

            # --- STATIC FILE SERVING WITH RANGE SUPPORT (Safari Duration Fix) ---
            if self.path.startswith('/data/'):
                fname = os.path.basename(self.path)
                file_path = os.path.join(DATA_DIR, fname)
                if not os.path.exists(file_path):
                    self.send_error(404); return

                f_size = os.path.getsize(file_path)
                m_type = 'audio/3gpp' if file_path.endswith('.3gp') else 'application/json'
                
                # Check for Range request
                range_header = self.headers.get('Range', None)
                if range_header and (m_type.startswith('audio/') or m_type.startswith('video/')):
                    # Support for "video/3gpp" as well, which is common for .3gp
                    m = re.match(r'bytes=(\d+)-(\d*)', range_header)
                    if m:
                        start = int(m.group(1))
                        end = int(m.group(2)) if m.group(2) else f_size - 1
                        if start < f_size:
                            end = min(end, f_size - 1)
                            self.send_response(206) # Partial Content
                            self.send_header('Content-type', m_type)
                            self.send_header('Accept-Ranges', 'bytes')
                            self.send_header('Content-Range', f'bytes {start}-{end}/{f_size}')
                            self.send_header('Content-Length', str(end - start + 1))
                            self.end_headers()
                            with open(file_path, 'rb') as f:
                                f.seek(start)
                                self.wfile.write(f.read(end - start + 1))
                            return

                # Normal full serving
                self.send_response(200)
                self.send_header('Content-type', m_type)
                self.send_header('Content-Length', str(f_size))
                self.send_header('Accept-Ranges', 'bytes')
                self.end_headers()
                with open(file_path, 'rb') as f:
                    self.wfile.write(f.read())
                return

            # --- EVIDENCE PAGE ---
            if self.path.startswith('/evidence/'):
                eid = self.path.split('/')[-1].split('.')[0]
                if not eid.isdigit():
                    self.send_error_html(400, "Invalid ID", "ID must be numeric."); return

                meta_p = os.path.join(DATA_DIR, f"{eid}.json")
                if not os.path.exists(meta_p):
                    self.send_error_html(404, "Not Found", "Evidence not found."); return

                with open(meta_p, 'r') as f: meta = json.load(f)
                lat, lon = meta.get('lat', 'Unknown'), meta.get('lon', 'Unknown')
                if lat == 'Unknown': lat, lon = '19.0317402', '72.8536638'
                audio_f = meta.get('audio_filename')
                time_s = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(int(eid)))

                html = f"""<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SafetyAI Case #{eid}</title>
<style>
    body {{ font-family: sans-serif; background: #f8f9fa; margin: 0; padding: 20px; }}
    .card {{ background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); max-width: 800px; margin: 0 auto; }}
    .map {{ width: 100%; height: 350px; background: #eee; border-radius: 8px; overflow: hidden; margin: 20px 0; }}
    .btn {{ display: block; text-align: center; background: #1a73e8; color: #fff; padding: 14px; border-radius: 8px; text-decoration: none; font-weight: bold; margin-top: 20px; }}
    .audio-c {{ background: #f1f3f4; padding: 20px; border-radius: 12px; margin: 20px 0; text-align: center; }}
    audio {{ width: 100%; }}
    .dl-link {{ font-size: 12px; color: #1a73e8; display: block; margin-top: 10px; }}
</style></head>
<body>
    <div class="card">
        <h2 style="color:#d93025;margin-top:0;">🚨 Critical SOS Evidence</h2>
        <p><strong>Time:</strong> {time_s} | <strong>ID:</strong> {eid}</p>
        <div class="map"><iframe width="100%" height="100%" frameborder="0" src="https://maps.google.com/maps?q={lat},{lon}&t=&z=17&ie=UTF8&iwloc=&output=embed"></iframe></div>
        <div class="audio-c">
            <strong>Incident Audio</strong>
            <audio controls autoplay><source src="/data/{audio_f}" type="audio/3gpp">Browser not supported</audio>
            <a href="/data/{audio_f}" download class="dl-link">Download Recording (.3gp)</a>
        </div>
        <a href="https://maps.google.com/?q={lat},{lon}" target="_blank" class="btn">Open in Google Maps</a>
        <p style="text-align:center; font-size:12px; color:#5f6368; margin-top:30px;">© 2026 SafetyAI Division • Case ID: {eid}</p>
    </div>
</body></html>"""
                self.send_response(200); self.send_header('Content-type', 'text/html'); self.end_headers()
                self.wfile.write(html.encode('utf-8'))
                return

            if self.path == '/':
                self.send_response(200); self.send_header('Content-type', 'text/html'); self.end_headers()
                self.wfile.write(b"SafetyAI Server Active.")
                return

            self.send_error_html(404, "Not Found", "Page missing.")
        except Exception as e:
            self.send_error_html(500, "Error", str(e))

socketserver.TCPServer.allow_reuse_address = True
start_udp_beacon()
with socketserver.TCPServer(("", PORT), SOSHandler) as httpd:
    print(f"SOS Backend running on {PORT}...")
    httpd.serve_forever()
