const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const ROOT = 'D:\\bishe\\web\\dist';
const BACKEND = { host: 'localhost', port: 8083 };
const PORT = 8080;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml', '.gif': 'image/gif', '.webp': 'image/webp',
  '.ico': 'image/x-icon', '.map': 'application/json',
  '.woff': 'font/woff', '.woff2': 'font/woff2'
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const pathname = decodeURIComponent(url.pathname);

  // API 代理到后端 8083
  if (pathname.startsWith('/api/')) {
    const proxyReq = http.request({
      host: BACKEND.host, port: BACKEND.port,
      path: pathname + url.search, method: req.method,
      headers: { ...req.headers, host: BACKEND.host + ':' + BACKEND.port }
    }, (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res);
    });
    proxyReq.on('error', () => { res.writeHead(502); res.end('Bad Gateway'); });
    req.pipe(proxyReq);
    return;
  }

  // 静态文件（含 history fallback）
  let filePath = path.join(ROOT, pathname);
  if (pathname === '/' || pathname.endsWith('/')) filePath = path.join(filePath, 'index.html');
  if (!path.extname(filePath)) filePath = path.join(filePath, 'index.html');

  fs.stat(filePath, (err, stat) => {
    if (!err && stat.isFile()) {
      const ext = path.extname(filePath).toLowerCase();
      const isSW = filePath.endsWith('service-worker.js');
      res.writeHead(200, {
        'Content-Type': MIME[ext] || 'application/octet-stream',
        'Cache-Control': (isSW || filePath.endsWith('index.html')) ? 'no-cache' : 'public, max-age=86400'
      });
      fs.createReadStream(filePath).pipe(res);
    } else {
      const idx = path.join(ROOT, 'index.html');
      fs.stat(idx, (e, s) => {
        if (!e && s.isFile()) {
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          fs.createReadStream(idx).pipe(res);
        } else { res.writeHead(404); res.end('Not Found'); }
      });
    }
  });
});

// WebSocket proxy ( /ws/flow, /ws/weather -> backend 8083 )
server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, 'http://localhost');
  if (!url.pathname.startsWith('/ws/')) { socket.destroy(); return; }
  const proxyReq = http.request({
    host: BACKEND.host, port: BACKEND.port,
    path: url.pathname + url.search,
    method: 'GET',
    headers: req.headers
  });
  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
    proxySocket.write(proxyHead);
    proxySocket.pipe(socket);
    socket.pipe(proxySocket);
    proxySocket.on('error', () => {});
    socket.on('error', () => {});
  });
  proxyReq.on('error', () => socket.destroy());
  proxyReq.end();
});

server.listen(PORT, '0.0.0.0', () => console.log('PWA server on http://0.0.0.0:' + PORT));