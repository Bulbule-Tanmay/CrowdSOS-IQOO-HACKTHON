// CrowdSOS demo dashboard — zero dependencies. Run: node server.js
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto');
const KEY = Buffer.from('000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f', 'hex'); // demo key, same as in Crypto.kt
const alerts = [], clients = new Set();

function decrypt(b64) {
  const b = Buffer.from(b64, 'base64');
  const iv = b.subarray(0, 12), tag = b.subarray(b.length - 16), ct = b.subarray(12, b.length - 16);
  const d = crypto.createDecipheriv('aes-256-gcm', KEY, iv);
  d.setAuthTag(tag);
  return JSON.parse(Buffer.concat([d.update(ct), d.final()]).toString('utf8'));
}
function broadcast(a) { for (const c of clients) c.write('data: ' + JSON.stringify(a) + '\n\n'); }

http.createServer((req, res) => {
  if (req.method === 'GET' && (req.url === '/' || req.url === '/index.html')) {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    return res.end(fs.readFileSync(path.join(__dirname, 'index.html')));
  }
  if (req.method === 'GET' && req.url === '/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    alerts.forEach(a => res.write('data: ' + JSON.stringify(a) + '\n\n'));
    clients.add(res); req.on('close', () => clients.delete(res)); return;
  }
  if (req.method === 'POST' && req.url === '/sos') {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => {
      try {
        const env = JSON.parse(body);                       // routing header: id, ttl, hops, data
        if (alerts.some(a => a.id === env.id)) { res.writeHead(200); return res.end('dup'); }
        const p = decrypt(env.data);                        // encrypted payload: type, lat, lon, ts, priority
        const a = { id: env.id, hops: env.hops, ...p, receivedAt: Date.now() };
        alerts.unshift(a); broadcast(a);
        console.log('SOS received', a.id, a.type, 'hops=' + a.hops);
        res.writeHead(200); res.end('ok');
      } catch (e) { console.log('Bad packet:', e.message); res.writeHead(400); res.end('bad'); }
    });
    return;
  }
  res.writeHead(404); res.end();
}).listen(3000, '0.0.0.0', () => console.log('CrowdSOS dashboard: http://localhost:3000  (phones POST to http://<this-laptop-ip>:3000/sos)'));
