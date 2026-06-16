/**
 * 小手机 · Cloudflare Worker 服务端（完整版）
 * ============================================================
 * 一个 Worker 同时承担四件事：
 *   1. POST /activate  —— 一人一码 + 一设备一码的激活账本
 *   2. GET  /version   —— 返回最新版本信息（APK 放 Gitee）
 *   3. POST /feedback  —— 收反馈（文字+图片+类别+激活码+设备码），转发到 Server酱
 *   4. GET  /feedback/:key —— 查看反馈详情（含图片），用于在浏览器中查看
 *
 * 配置要求：
 *   - 绑定一个 KV 命名空间，变量名必须是 LICENSE_KV
 *   - 在 Worker 的「设置 → 变量」里加一个加密变量 SERVERCHAN_SENDKEY（Server酱的 SendKey）
 *   - 每次加人/踢人，只改 VALID_CODES / REVOKED_CODES 两个数组
 *   - 每次发新版，只改 VERSION_INFO
 * ============================================================
 */

// ============ 1. 有效激活码白名单 ============
const VALID_CODES = [
  // "EPHONE-XXXX-0001",  // 示例：小北
  // ……在这里继续添加
];

// ============ 2. 吊销名单 ============
const REVOKED_CODES = [
  // "EPHONE-XXXX-0001",
];

// ============ 3. 版本信息（发新版时改这里） ============
const VERSION_INFO = {
  versionCode: 1,
  versionName: "1.0",
  changelog: "首个内测版本",
  downloadUrl: "https://gitee.com/REPLACE_ME/REPLACE_ME/releases/download/v1.0/app-release.apk",
  forceUpdate: false,
};

// ============ 路由分发 ============
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // POST /activate —— 激活验证
      if (path === "/activate" && request.method === "POST") {
        return await handleActivate(request, env);
      }
      // GET /version —— 版本检查
      if (path === "/version" && request.method === "GET") {
        return jsonResponse(200, VERSION_INFO);
      }
      // POST /feedback —— 提交反馈
      if (path === "/feedback" && request.method === "POST") {
        return await handleFeedback(request, env);
      }
      // GET /feedback/:key —— 查看反馈详情（网页界面，含图片）
      if (path.startsWith("/feedback/") && request.method === "GET") {
        const key = path.substring("/feedback/".length);
        return await handleViewFeedback(key, env);
      }
      return jsonResponse(404, { success: false, reason: "not_found" });
    } catch (err) {
      return jsonResponse(500, { success: false, reason: "server_error" });
    }
  },
};

// ============ /activate 激活账本逻辑 ============
async function handleActivate(request, env) {
  const body = await safeParseJson(request);
  const code = (body.code || "").trim();
  const fingerprint = (body.fingerprint || "").trim();

  if (!code || !fingerprint) {
    return jsonResponse(400, { success: false, reason: "invalid" });
  }
  if (REVOKED_CODES.includes(code)) {
    return jsonResponse(200, { success: false, reason: "revoked" });
  }
  if (!VALID_CODES.includes(code)) {
    return jsonResponse(200, { success: false, reason: "invalid" });
  }

  const bindKey = "bind:" + code;
  const boundFingerprint = await env.LICENSE_KV.get(bindKey);

  if (boundFingerprint === null) {
    await env.LICENSE_KV.put(bindKey, fingerprint);
    return jsonResponse(200, { success: true });
  }
  if (boundFingerprint === fingerprint) {
    return jsonResponse(200, { success: true });
  }
  return jsonResponse(200, { success: false, reason: "occupied" });
}

// ============ /feedback 反馈提交逻辑 ============
async function handleFeedback(request, env) {
  const body = await safeParseJson(request);
  const content = (body.content || "").trim();
  const category = (body.category || "").trim();
  const contact = (body.contact || "").trim();
  const appVersion = (body.appVersion || "").trim();
  const activationCode = (body.activationCode || "").trim();
  const fingerprint = (body.fingerprint || "").trim();
  const images = Array.isArray(body.images) ? body.images : [];

  if (!content) {
    return jsonResponse(400, { success: false, reason: "empty" });
  }

  // 在 KV 里留一份完整备份（含图片），key 用时间戳
  const backupKey = "feedback:" + Date.now();
  const record = {
    content,
    category,
    contact,
    appVersion,
    activationCode,
    fingerprint,
    images,  // 完整保存 base64 图片数组
    imageCount: images.length,
    createdAt: new Date().toISOString(),
  };
  await env.LICENSE_KV.put(backupKey, JSON.stringify(record));

  // 转发到 Server酱，推送到微信
  const sendKey = env.SERVERCHAN_SENDKEY;
  if (sendKey) {
    const title = "📱 小手机反馈";
    const viewUrl = new URL(request.url).origin + "/feedback/" + backupKey;
    const desp = [
      "### 问题类别",
      category || "未分类",
      "",
      "### 反馈内容",
      content,
      "",
      "### 联系方式",
      contact || "未填",
      "",
      "### 版本信息",
      "- 版本：" + (appVersion || "未知"),
      "- 激活码：`" + (activationCode || "未激活") + "`",
      "- 设备码：`" + (fingerprint || "未知") + "`",
      "",
      "### 附件",
      images.length > 0
        ? `包含 ${images.length} 张图片，[点击查看完整反馈](${viewUrl})`
        : "无图片",
      "",
      "---",
      `[查看详情](${viewUrl})`,
    ].join("\n");
    const pushUrl = "https://sctapi.ftqq.com/" + sendKey + ".send";
    const form = new URLSearchParams();
    form.append("title", title);
    form.append("desp", desp);
    try {
      await fetch(pushUrl, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form.toString(),
      });
    } catch (e) {
      // 推送失败不影响反馈已存档
    }
  }

  return jsonResponse(200, { success: true });
}

// ============ /feedback/:key 查看反馈详情（网页界面） ============
async function handleViewFeedback(key, env) {
  // 读取 KV 中的反馈记录
  const recordJson = await env.LICENSE_KV.get(key);
  if (!recordJson) {
    return new Response("反馈记录不存在", { status: 404 });
  }

  const record = JSON.parse(recordJson);

  // 生成 HTML 页面
  const html = `
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>反馈详情 - ${key}</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      background: #f5f5f5;
      padding: 20px;
      line-height: 1.6;
    }
    .container {
      max-width: 900px;
      margin: 0 auto;
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      overflow: hidden;
    }
    .header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 24px;
    }
    .header h1 {
      font-size: 24px;
      margin-bottom: 8px;
    }
    .header .meta {
      font-size: 14px;
      opacity: 0.9;
    }
    .section {
      padding: 24px;
      border-bottom: 1px solid #eee;
    }
    .section:last-child {
      border-bottom: none;
    }
    .section-title {
      font-size: 14px;
      font-weight: 600;
      color: #666;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 12px;
    }
    .content {
      font-size: 16px;
      color: #333;
      white-space: pre-wrap;
      word-wrap: break-word;
    }
    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }
    .info-item {
      background: #f8f9fa;
      padding: 12px;
      border-radius: 8px;
    }
    .info-item strong {
      display: block;
      font-size: 12px;
      color: #666;
      margin-bottom: 4px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .info-item span {
      font-size: 14px;
      color: #333;
      word-break: break-all;
    }
    .badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 14px;
      font-weight: 500;
      background: #e3f2fd;
      color: #1976d2;
    }
    .images {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
      gap: 16px;
    }
    .image-card {
      border-radius: 8px;
      overflow: hidden;
      border: 1px solid #e0e0e0;
      cursor: pointer;
      transition: transform 0.2s;
    }
    .image-card:hover {
      transform: scale(1.02);
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }
    .image-card img {
      width: 100%;
      height: auto;
      display: block;
    }
    .no-images {
      color: #999;
      font-style: italic;
      text-align: center;
      padding: 24px;
    }
    .lightbox {
      display: none;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0,0,0,0.9);
      z-index: 1000;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .lightbox.active {
      display: flex;
    }
    .lightbox img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }
    .lightbox-close {
      position: absolute;
      top: 20px;
      right: 20px;
      color: white;
      font-size: 32px;
      cursor: pointer;
      background: rgba(0,0,0,0.5);
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>📱 小手机反馈详情</h1>
      <div class="meta">
        ${record.createdAt ? '提交时间：' + new Date(record.createdAt).toLocaleString('zh-CN') : ''}
        &nbsp;·&nbsp;
        记录键名：${key}
      </div>
    </div>

    <div class="section">
      <div class="section-title">问题类别</div>
      <div class="badge">${escapeHtml(record.category || "未分类")}</div>
    </div>

    <div class="section">
      <div class="section-title">反馈内容</div>
      <div class="content">${escapeHtml(record.content || "无内容")}</div>
    </div>

    <div class="section">
      <div class="section-title">联系方式</div>
      <div class="content">${escapeHtml(record.contact || "未填写")}</div>
    </div>

    <div class="section">
      <div class="section-title">设备信息</div>
      <div class="info-grid">
        <div class="info-item">
          <strong>App 版本</strong>
          <span>${escapeHtml(record.appVersion || "未知")}</span>
        </div>
        <div class="info-item">
          <strong>激活码</strong>
          <span>${escapeHtml(record.activationCode || "未激活")}</span>
        </div>
        <div class="info-item">
          <strong>设备指纹</strong>
          <span>${escapeHtml(record.fingerprint || "未知")}</span>
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">
        图片附件 ${record.images && record.images.length > 0 ? '(' + record.images.length + ' 张)' : ''}
      </div>
      ${record.images && record.images.length > 0 ? `
        <div class="images">
          ${record.images.map((img, i) => `
            <div class="image-card" onclick="openLightbox(${i})">
              <img src="data:image/jpeg;base64,${img}" alt="反馈图片 ${i + 1}">
            </div>
          `).join('')}
        </div>
      ` : '<div class="no-images">无图片附件</div>'}
    </div>
  </div>

  <div class="lightbox" id="lightbox" onclick="closeLightbox()">
    <div class="lightbox-close">×</div>
    <img id="lightbox-img" src="" alt="放大查看">
  </div>

  <script>
    const images = ${JSON.stringify(record.images || [])};

    function openLightbox(index) {
      const lightbox = document.getElementById('lightbox');
      const img = document.getElementById('lightbox-img');
      img.src = 'data:image/jpeg;base64,' + images[index];
      lightbox.classList.add('active');
      event.stopPropagation();
    }

    function closeLightbox() {
      document.getElementById('lightbox').classList.remove('active');
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') closeLightbox();
    });
  </script>
</body>
</html>
  `;

  return new Response(html, {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

// ============ 工具函数 ============
function jsonResponse(status, obj) {
  return new Response(JSON.stringify(obj), {
    status: status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

async function safeParseJson(request) {
  try {
    return await request.json();
  } catch (e) {
    return {};
  }
}

function escapeHtml(text) {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, m => map[m]);
}
