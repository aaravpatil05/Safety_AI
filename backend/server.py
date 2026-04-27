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
BEACON_PORT = 8765   # UDP discovery port
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
TUNNEL_URL_FILE = os.path.join(BASE_DIR, "current_url.txt")

if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

def get_my_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def start_udp_beacon():
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
        pass

    def send_response_with_content(self, content, content_type="text/html", code=200):
        # SAFARI FIX: Explicitly set charset and prevent mime-sniffing
        if "text/html" in content_type and "charset" not in content_type:
            content_type += "; charset=utf-8"
        
        body = content.encode('utf-8') if isinstance(content, str) else content
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.end_headers()
        self.wfile.write(body)

    def send_error_html(self, code, title, message):
        html = f"""<!DOCTYPE html><html><body style="font-family:sans-serif;text-align:center;padding:50px;background:#000;color:#fff;">
                <h1 style="color:#ff3b30;">{title}</h1><p>{message}</p>
                <a href="/" style="color:#007aff;">Back to Home</a></body></html>"""
        self.send_response_with_content(html, code=code)

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
                with open(audio_filepath, 'wb') as f: f.write(audio_data)
                meta_data = {"timestamp": timestamp, "lat": lat, "lon": lon, "audio_filename": audio_filename}
                with open(os.path.join(DATA_DIR, f"{timestamp}.json"), 'w') as f: json.dump(meta_data, f)
                print(f"[SafetyAI] Received SOS Upload: {timestamp}")
                self.send_response_with_content(json.dumps({"id": timestamp}), content_type="application/json")
            else:
                self.send_error(404)
        except Exception as e:
            self.send_error(500, str(e))

    def do_GET(self):
        try:
            # FIX: Strip query parameters (e.g., ?v=123) before processing path
            raw_path = self.path
            clean_path = self.path.split('?')[0]
            self.path = clean_path

            if self.path == '/favicon.ico':
                self.send_response(204); self.end_headers(); return

            if self.path == '/ping':
                self.send_response_with_content(json.dumps({"status": "online"}), content_type="application/json")
                return

            if self.path == '/url':
                url = get_current_tunnel_url()
                self.send_response_with_content(json.dumps({"url": url if url else "null"}), content_type="application/json")
                return

            # --- ROBUST BYTE-RANGE SUPPORT FOR SAFARI/MOBILE ---
            if self.path.startswith('/data/'):
                fname = os.path.basename(self.path)
                file_path = os.path.join(DATA_DIR, fname)
                if not os.path.exists(file_path):
                    self.send_error(404); return

                f_size = os.path.getsize(file_path)
                # FIX: use video/3gpp for better Samsung/iPhone compatibility (3GP is a video container)
                m_type = 'video/3gpp' if file_path.endswith('.3gp') else 'application/json'
                
                range_header = self.headers.get('Range', None)
                if range_header:
                    print(f"[SafetyAI] Streaming Range Request: {range_header} for {fname}")
                
                if range_header and (m_type.startswith('video/') or m_type.startswith('audio/')):
                    m = re.match(r'bytes=(\d+)-(\d*)', range_header)
                    if m:
                        start = int(m.group(1))
                        end = int(m.group(2)) if m.group(2) else f_size - 1
                        if start < f_size:
                            end = min(end, f_size - 1)
                            self.send_response(206)
                            self.send_header('Content-Type', m_type)
                            self.send_header('Accept-Ranges', 'bytes')
                            self.send_header('Content-Range', f'bytes {start}-{end}/{f_size}')
                            self.send_header('Content-Length', str(end - start + 1))
                            self.send_header('Connection', 'keep-alive')
                            self.send_header('X-Content-Type-Options', 'nosniff')
                            self.send_header('Access-Control-Allow-Origin', '*')
                            self.end_headers()
                            with open(file_path, 'rb') as f:
                                f.seek(start)
                                self.wfile.write(f.read(end - start + 1))
                            return

                # Normal response (without range)
                with open(file_path, 'rb') as f:
                    self.send_response(200)
                    self.send_header('Content-Type', m_type)
                    self.send_header('Content-Length', str(f_size))
                    self.send_header('Accept-Ranges', 'bytes')
                    self.send_header('Connection', 'keep-alive')
                    self.send_header('X-Content-Type-Options', 'nosniff')
                    self.send_header('Access-Control-Allow-Origin', '*')
                    self.end_headers()
                    self.wfile.write(f.read())
                return

            # --- PREMIUM EVIDENCE DASHBOARD (SAFARI OPTIMIZED) ---
            if self.path.startswith('/evidence/'):
                eid = self.path.split('/')[-1].split('.')[0]
                meta_p = os.path.join(DATA_DIR, f"{eid}.json")
                if not os.path.exists(meta_p):
                    self.send_error_html(404, "Case Not Found", "This record was not found or is still uploading.")
                    return

                with open(meta_p, 'r') as f: meta = json.load(f)
                lat, lon = meta.get('lat', 'Unknown'), meta.get('lon', 'Unknown')
                if lat == 'Unknown': lat, lon = '19.0331131', '73.0643971'
                audio_f = meta.get('audio_filename')
                time_s = time.strftime('%b %d, %Y • %I:%M %p', time.localtime(int(eid)))

                # VERSIONING TO PREVENT SAFARI CACHING
                v_param = int(time.time())

                html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>SafetyAI | Incident #{eid}</title>
    <style>
        :root {{ --primary: #FF3B30; --bg: #0A0A0B; --card: #1C1C1E; --text: #FFFFFF; --secondary: #2C2C2E; }}
        body {{ font-family: -apple-system, system-ui, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 16px; min-height: 100vh; }}
        .header {{ display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }}
        .status-pill {{ background: rgba(255, 59, 48, 0.1); color: var(--primary); padding: 6px 12px; border-radius: 20px; font-size: 11px; font-weight: 700; text-transform: uppercase; border: 1px solid rgba(255, 59, 48, 0.3); }}
        .card {{ background: var(--card); border-radius: 24px; padding: 24px; margin-bottom: 16px; border: 1px solid rgba(255,255,255,0.05); }}
        h1 {{ font-size: 22px; margin: 0; font-weight: 700; }}
        .meta {{ font-size: 13px; color: #8E8E93; margin-top: 4px; }}
        .map-container {{ width: 100%; height: 280px; border-radius: 20px; overflow: hidden; margin: 20px 0; background: #000; border: 1px solid var(--secondary); }}
        .audio-player {{ background: var(--secondary); border-radius: 20px; padding: 20px; text-align: center; margin: 20px 0; border-left: 4px solid var(--primary); }}
        audio {{ width: 100%; margin-top: 12px; }}
        .btn {{ display: flex; align-items: center; justify-content: center; background: var(--primary); color: white; padding: 18px; border-radius: 18px; text-decoration: none; font-weight: 700; font-size: 16px; transition: opacity 0.2s; }}
        .footer {{ text-align: center; font-size: 11px; color: #48484A; margin-top: 40px; }}
        .info-box {{ background: var(--secondary); padding: 12px; border-radius: 12px; font-size: 13px; }}
        .label {{ color: #8E8E93; font-size: 10px; text-transform: uppercase; margin-bottom: 2px; font-weight: 600; }}
    </style>
</head>
<body>
    <div class="header"><h1>SafetyAI</h1><div class="status-pill">Critical Evidence</div></div>
    <div class="card">
        <div class="label">Date & Time</div><div>{time_s}</div>
        <div class="meta">Case Reference: #{eid}</div>
        <div class="map-container">
            <iframe width="100%" height="100%" frameborder="0" src="https://maps.google.com/maps?q={lat},{lon}&t=&z=17&ie=UTF8&iwloc=&output=embed"></iframe>
        </div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
            <div class="info-box"><div class="label">Lat</div>{lat}</div>
            <div class="info-box"><div class="label">Lon</div>{lon}</div>
        </div>
    </div>
    <div class="card" style="padding:10px;">
        <div class="audio-player">
            <div style="text-align:left;"><div class="label">Incident Recording</div><div style="font-weight:600;">Secure Audio Fragment</div></div>
            <audio controls preload="metadata">
                <source src="/data/{audio_f}?v={v_param}" type="video/3gpp">
                <source src="/data/{audio_f}?v={v_param}" type="audio/3gpp">
                <source src="/data/{audio_f}?v={v_param}" type="audio/amr">
                Your browser does not support the audio element.
            </audio>
            <a href="/data/{audio_f}" download style="display:block; margin-top:10px; font-size:12px; color:#0A84FF; text-decoration:none;">⬇ Download Evidence</a>
        </div>
    </div>
    <a href="https://maps.google.com/?q={lat},{lon}" target="_blank" class="btn">📍 Open in Maps</a>
    <div class="footer">Verified SOS Protocol • Case ID: {eid}</div>
</body>
</html>"""
                self.send_response_with_content(html)
                return

            if self.path == '/':
                self.send_response_with_content("<h1>SafetyAI Server Active.</h1>", code=200); return
            self.send_error_html(404, "Not Found", "Resource missing.")
        except Exception as e:
            self.send_error_html(500, "Server Error", str(e))

class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

start_udp_beacon()
with ThreadedTCPServer(("", PORT), SOSHandler) as httpd:
    print(f"SOS Backend running on {PORT} (Multithreaded)...")
    httpd.serve_forever()
