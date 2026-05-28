#!/usr/bin/env node
const { Client } = require('ssh2');
const fs = require('fs');

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

  // 1. Read current nginx config
  const config = await exec('docker exec realtimevideo-frontend cat /etc/nginx/conf.d/default.conf');

  // 2. Insert HTML cache control before the SPA routing section
  const htmlCacheBlock = `    # HTML 文件不缓存（确保每次部署能立即生效）
    location ~* \\.html$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma no-cache;
        expires -1;
    }

    # SPA 路由：所有非文件请求返回 index.html`;

  const newConfig = config.replace(
    '# SPA 路由：所有非文件请求返回 index.html',
    htmlCacheBlock
  );

  // 3. Write updated config and copy to container
  fs.writeFileSync('/tmp/nginx-default.conf', newConfig);
  await exec('docker cp /tmp/nginx-default.conf realtimevideo-frontend:/etc/nginx/conf.d/default.conf');

  // 4. Reload nginx
  const reload = await exec('docker exec realtimevideo-frontend nginx -s reload');
  console.log('Nginx reload:', reload);

  // 5. Verify
  const verify = await exec('docker exec realtimevideo-frontend cat /etc/nginx/conf.d/default.conf');
  console.log('\n=== Updated config ===');
  console.log(verify);

  conn.end();
});

conn.connect({
  host: '47.108.204.59', port: 22,
  username: 'root', password: 'Zq010722.',
  readyTimeout: 15000,
});
