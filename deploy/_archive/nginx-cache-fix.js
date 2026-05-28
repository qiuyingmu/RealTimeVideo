#!/usr/bin/env node
const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');

const configContent = `server {
    listen 80;
    server_name _;
    charset utf-8;

    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml image/svg+xml;
    gzip_min_length 1024;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    location ~ ^/ezuikit_cdn/.*\\.wasm$ {
        rewrite ^/ezuikit_cdn/(.*)$ /ezuikit_js/v9.0.5/ezuikit_static/$1 break;
        proxy_pass https://openstatic.ys7.com;
        proxy_set_header Host openstatic.ys7.com;
        proxy_ssl_server_name on;
        proxy_ssl_verify off;
        proxy_hide_header Content-Type;
        add_header Content-Type application/wasm;
    }
    location /ezuikit_cdn/ {
        rewrite ^/ezuikit_cdn/(.*)$ /ezuikit_js/v9.0.5/ezuikit_static/$1 break;
        proxy_pass https://openstatic.ys7.com;
        proxy_set_header Host openstatic.ys7.com;
        proxy_ssl_server_name on;
        proxy_ssl_verify off;
    }

    location ~* \\.html$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma no-cache;
        expires -1;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
`;

const conn = new Client();
conn.on('ready', async () => {
  const exec = (cmd) => new Promise((res, rej) => {
    conn.exec(cmd, (e, stream) => {
      if (e) return rej(e);
      let out = '';
      stream.on('data', d => out += d);
      stream.stderr.on('data', d => out += d);
      stream.on('close', () => res(out));
    });
  });

  function sftpPut(local, remote) {
    return new Promise((resolve, reject) => {
      conn.sftp((err, sftp) => {
        if (err) return reject(err);
        sftp.fastPut(local, remote, (err2) => { sftp.end(); err2 ? reject(err2) : resolve(); });
      });
    });
  }

  // Write local config file
  const localFile = path.join(__dirname, 'nginx-cache.conf');
  fs.writeFileSync(localFile, configContent, 'utf8');
  console.log('Config written to local file');

  // Upload to server
  console.log('Uploading config to server...');
  await sftpPut(localFile, '/tmp/nginx-cache.conf');
  console.log('Uploaded');

  // Copy into container
  console.log('Copying into container...');
  await exec('docker cp /tmp/nginx-cache.conf realtimevideo-frontend:/etc/nginx/conf.d/default.conf');
  console.log('Config copied to container');

  // Reload nginx
  const reload = await exec('docker exec realtimevideo-frontend nginx -s reload');
  console.log('Nginx reload:', reload);

  // Verify
  const verify = await exec('docker exec realtimevideo-frontend cat /etc/nginx/conf.d/default.conf');
  console.log('\n=== Updated config ===');
  console.log(verify);

  // Clean up
  fs.unlinkSync(localFile);
  conn.end();
});

conn.connect({
  host: '47.108.204.59', port: 22,
  username: 'root', password: 'Zq010722.',
  readyTimeout: 15000,
});
