#!/usr/bin/env node
/**
 * RealTimeVideo 前端快速更新
 * 将本地构建的前端 dist 复制到服务器并注入运行中的容器
 */

const { Client } = require('ssh2');
const path = require('path');
const fs = require('fs');

const CONFIG = {
  host: '47.108.204.59',
  port: 22,
  username: 'root',
  password: 'Zq010722.',
  localDist: path.resolve(__dirname, '..', 'frontend', 'dist'),
  containerName: 'realtimevideo-frontend',
};

function log(label, msg) {
  const ts = new Date().toISOString().substring(11, 19);
  console.log(`[${ts}] [${label}] ${msg}`);
}

function sshExec(conn, command, label, timeout = 120000) {
  return new Promise((resolve, reject) => {
    conn.exec(command, (err, stream) => {
      if (err) return reject(err);
      let stdout = '', stderr = '';
      const timer = setTimeout(() => { stream.close(); reject(new Error(`Timeout: ${label}`)); }, timeout);
      stream.on('data', (data) => { stdout += data.toString(); });
      stream.stderr.on('data', (data) => { stderr += data.toString(); });
      stream.on('close', (code) => { clearTimeout(timer); resolve({ code, stdout, stderr }); });
    });
  });
}

function sftpPut(conn, local, remote) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, sftp) => {
      if (err) return reject(err);
      sftp.fastPut(local, remote, (err2) => { sftp.end(); err2 ? reject(err2) : resolve(); });
    });
  });
}

async function uploadDistFileByFile(conn, localDir, remoteDir) {
  const entries = fs.readdirSync(localDir, { withFileTypes: true });
  
  // Create remote dir
  await sshExec(conn, `mkdir -p ${remoteDir}`, `mkdir ${remoteDir}`);
  
  for (const entry of entries) {
    const localPath = path.join(localDir, entry.name);
    const remotePath = `${remoteDir}/${entry.name}`;
    
    if (entry.isDirectory()) {
      // Recurse
      const subEntries = fs.readdirSync(localPath, { withFileTypes: true });
      for (const sub of subEntries) {
        const subLocal = path.join(localPath, sub.name);
        const subRemote = `${remotePath}/${sub.name}`;
        if (sub.isDirectory()) {
          await uploadDistFileByFile(conn, subLocal, subRemote);
        } else {
          await sftpPut(conn, subLocal, subRemote);
          process.stdout.write('.');
        }
      }
    } else if (entry.isFile()) {
      await sftpPut(conn, localPath, remotePath);
      process.stdout.write('.');
    }
  }
}

async function deploy() {
  console.log('==========================================');
  console.log('  RealTimeVideo 前端快速更新');
  console.log('==========================================\n');

  // 验证 dist 存在
  if (!fs.existsSync(path.join(CONFIG.localDist, 'index.html'))) {
    console.error('❌ 前端 dist 不存在，请先运行 npm run build');
    process.exit(1);
  }

  // 统计文件数
  function countFiles(dir) {
    let count = 0;
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.isFile()) count++;
      else if (e.isDirectory()) count += countFiles(path.join(dir, e.name));
    }
    return count;
  }
  const totalFiles = countFiles(CONFIG.localDist);
  log('INFO', `本地 dist 共 ${totalFiles} 个文件，准备上传...`);

  // SSH 连接
  log('SSH', `连接 ${CONFIG.host}...`);
  const conn = new Client();
  await new Promise((resolve, reject) => {
    conn.on('ready', () => { log('SSH', '✅ 连接成功!'); resolve(); });
    conn.on('error', reject);
    conn.connect({
      host: CONFIG.host, port: CONFIG.port,
      username: CONFIG.username, password: CONFIG.password,
      readyTimeout: 15000,
    });
  });

  try {
    // 1. 上传前端 dist 到服务器临时目录
    log('SCP', `上传 ${totalFiles} 个文件到服务器...`);
    const remoteTempDist = '/tmp/realtimevideo-dist';
    await sshExec(conn, `rm -rf ${remoteTempDist} && mkdir -p ${remoteTempDist}`, 'clean-temp');
    
    // 上传根目录文件
    const rootFiles = fs.readdirSync(CONFIG.localDist, { withFileTypes: true });
    for (const entry of rootFiles) {
      const localPath = path.join(CONFIG.localDist, entry.name);
      if (entry.isFile()) {
        await sftpPut(conn, localPath, `${remoteTempDist}/${entry.name}`);
        process.stdout.write('.');
      } else if (entry.isDirectory()) {
        // Upload assets directory
        await uploadDistFileByFile(conn, localPath, `${remoteTempDist}/${entry.name}`);
      }
    }
    console.log(); // newline after dots
    log('SCP', '✅ 上传完成!');

    // 2. 确认容器存在
    log('DOCKER', '检查容器状态...');
    const checkResult = await sshExec(conn, `docker ps -q -f name=${CONFIG.containerName}`, 'check-container');
    if (!checkResult.stdout.trim()) {
      log('DOCKER', '⚠️ 容器未运行，尝试重建并启动...');
      await sshExec(conn, `cd /opt/realtime-video && docker compose build frontend && docker compose up -d frontend`, 'rebuild', 300000);
    } else {
      // 3. 先清理容器内旧文件，再复制新 dist
      log('DOCKER', '清理容器内旧文件...');
      await sshExec(conn, `docker exec ${CONFIG.containerName} sh -c 'rm -rf /usr/share/nginx/html/assets && rm -f /usr/share/nginx/html/*.html /usr/share/nginx/html/*.svg'`, 'clean-old');
      log('DOCKER', '复制新文件到容器...');
      const copyCmd = `docker cp ${remoteTempDist}/. ${CONFIG.containerName}:/usr/share/nginx/html/`;
      await sshExec(conn, copyCmd, 'copy-dist');
      
      // 4. 刷新 nginx 缓存（不中断服务）
      log('DOCKER', '重载 Nginx 配置...');
      await sshExec(conn, `docker exec ${CONFIG.containerName} nginx -s reload`, 'nginx-reload');
    }

    // 5. 清理临时文件
    await sshExec(conn, `rm -rf ${remoteTempDist}`, 'cleanup');

    // 6. 验证
    const verifyResult = await sshExec(conn, `docker exec ${CONFIG.containerName} ls /usr/share/nginx/html/index.html`, 'verify');
    if (verifyResult.code === 0) {
      log('VERIFY', '✅ 部署验证通过!');
    }

    console.log('\n' + '='.repeat(50));
    console.log('🎉 前端更新完成！');
    console.log('='.repeat(50));
    console.log(`   访问地址: http://${CONFIG.host}`);
    console.log('='.repeat(50));
  } finally {
    conn.end();
  }
}

deploy().catch(err => {
  console.error('\n❌ 部署失败:', err.message);
  process.exit(1);
});
