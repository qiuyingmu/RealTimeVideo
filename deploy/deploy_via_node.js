#!/usr/bin/env node
/**
 * RealTimeVideo 一键部署脚本 (Node.js 版)
 * 通过 SSH/SCP 上传构建包并部署到服务器
 */

const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');

const CONFIG = {
  host: '47.108.204.59',
  port: 22,
  username: 'root',
  password: 'Zq010722.',
  localTar: path.resolve(__dirname, 'realtime-video-deploy.tar.gz'),
  remoteDir: '/opt/realtime-video',
};

async function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

function log(label, msg) {
  const ts = new Date().toISOString().substring(11, 19);
  console.log(`[${ts}] [${label}] ${msg}`);
}

async function sftpPut(conn, local, remote) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, sftp) => {
      if (err) return reject(err);
      sftp.fastPut(local, remote, (err2) => {
        sftp.end();
        if (err2) return reject(err2);
        resolve();
      });
    });
  });
}

async function sshExec(conn, command, label, timeout = 300000) {
  return new Promise((resolve, reject) => {
    log(label || 'SSH', `执行: ${command.substring(0, 100)}`);
    conn.exec(command, { pty: true }, (err, stream) => {
      if (err) return reject(err);
      let stdout = '';
      let stderr = '';
      const timer = setTimeout(() => {
        stream.close();
        reject(new Error(`Timeout after ${timeout}ms: ${label}`));
      }, timeout);

      stream.on('data', (data) => {
        const text = data.toString('utf8');
        stdout += text;
        process.stdout.write(text);
      });
      stream.stderr.on('data', (data) => {
        const text = data.toString('utf8');
        stderr += text;
        process.stderr.write(text);
      });
      stream.on('close', (code) => {
        clearTimeout(timer);
        if (code !== 0 && stderr.length > 0) {
          log(label, `Exit code: ${code}`);
        }
        resolve({ code, stdout, stderr });
      });
    });
  });
}

async function deploy() {
  console.log('==========================================');
  console.log('  RealTimeVideo 一键部署脚本 (Node.js)');
  console.log('==========================================\n');

  // 1. 检查本地文件
  log('CHECK', `检查本地部署包: ${CONFIG.localTar}`);
  if (!fs.existsSync(CONFIG.localTar)) {
    console.error(`❌ 部署包不存在: ${CONFIG.localTar}`);
    console.error('   请先运行 build-package.sh 生成部署包');
    process.exit(1);
  }
  const size = fs.statSync(CONFIG.localTar).size;
  log('CHECK', `✅ 部署包大小: ${(size / 1024 / 1024).toFixed(1)} MB`);

  // 2. SSH 连接
  log('SSH', `连接 ${CONFIG.host}:${CONFIG.port}...`);
  const conn = new Client();
  await new Promise((resolve, reject) => {
    conn.on('ready', () => {
      log('SSH', '✅ 连接成功!');
      resolve();
    });
    conn.on('error', (err) => {
      reject(err);
    });
    conn.connect({
      host: CONFIG.host,
      port: CONFIG.port,
      username: CONFIG.username,
      password: CONFIG.password,
      readyTimeout: 15000,
    });
  });

  try {
    // 3. 确保远程目录存在
    log('SSH', '创建远程目录...');
    await sshExec(conn, `mkdir -p ${CONFIG.remoteDir}`, 'mkdir');

    // 4. SCP 上传
    log('SCP', `上传部署包到 ${CONFIG.remoteDir}/...`);
    const remoteTar = `${CONFIG.remoteDir}/realtime-video-deploy.tar.gz`;
    await sftpPut(conn, CONFIG.localTar, remoteTar);
    log('SCP', '✅ 上传完成!');

    // 5. 解压部署包
    log('SSH', '解压部署包...');
    await sshExec(conn, `cd ${CONFIG.remoteDir} && tar xzf realtime-video-deploy.tar.gz`, 'extract');
    await sshExec(conn, `rm -f ${CONFIG.remoteDir}/realtime-video-deploy.tar.gz`, 'cleanup');

    // 6. 检查 .env
    log('SSH', '检查 .env 文件...');
    await sshExec(conn, `
if [ ! -f ${CONFIG.remoteDir}/.env ]; then
    cp ${CONFIG.remoteDir}/deploy/env.production ${CONFIG.remoteDir}/.env
    echo "✅ Created .env from env.production"
fi
`, 'env-setup');

    // 7. 验证关键文件
    log('SSH', '验证关键文件...');
    await sshExec(conn, `ls -la ${CONFIG.remoteDir}/docker-compose.yml ${CONFIG.remoteDir}/deploy/Dockerfile.backend`, 'verify');

    // 8. 构建后端
    log('BUILD', '构建后端镜像 (首次可能需要 3-8 分钟)...');
    await sshExec(conn, `cd ${CONFIG.remoteDir} && docker compose build backend`, 'docker-build', 600000);

    // 9. 启动服务
    log('START', '启动服务...');
    await sshExec(conn, `cd ${CONFIG.remoteDir} && docker compose up -d`, 'docker-up', 120000);

    // 10. 检查状态
    log('STATUS', '检查部署状态...');
    await sshExec(conn, `cd ${CONFIG.remoteDir} && docker compose ps`, 'docker-ps');
    await sshExec(conn, `cd ${CONFIG.remoteDir} && docker compose logs --tail=20 backend`, 'logs-tail');

    console.log('\n' + '='.repeat(60));
    console.log('🎉 部署完成！');
    console.log('='.repeat(60));
    console.log(`   访问地址: http://${CONFIG.host}`);
    console.log(`   查看日志: docker compose logs -f`);
    console.log(`   停止服务: docker compose down`);
    console.log('='.repeat(60));
  } finally {
    conn.end();
  }
}

deploy().catch(err => {
  console.error('\n❌ 部署失败:', err.message);
  process.exit(1);
});
