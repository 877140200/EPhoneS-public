# Cloudflare Worker 反馈功能升级指南

## 概述

客户端已升级反馈功能，新增了以下字段：
- **category**：问题类别（功能异常/界面显示/数据丢失/性能卡顿/功能建议/其他）
- **activationCode**：激活码
- **fingerprint**：设备指纹
- **images**：图片 base64 数组（最多 10 张，每张已压缩到 500KB 内）

Worker 需要相应修改，以支持新字段的存储和推送。

---

## 修改步骤

### 1. 更新 `/feedback` 接口的请求解析

找到 `handleFeedback` 函数，在解析请求体时增加新字段：

```javascript
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

  // 在 KV 里留一份备份，key 用时间戳，便于事后翻看
  const backupKey = "feedback:" + Date.now();
  const record = JSON.stringify({
    content,
    category,
    contact,
    appVersion,
    activationCode,
    fingerprint,
    images,  // 完整保存 base64 图片数组
    at: new Date().toISOString()
  });
  await env.LICENSE_KV.put(backupKey, record);

  // 转发到 Server酱，推送到你微信
  const sendKey = env.SERVERCHAN_SENDKEY;
  if (sendKey) {
    const title = "小手机反馈";
    const desp = [
      "**问题类别**：" + (category || "未分类"),
      "**内容**：" + content,
      "**联系方式**：" + (contact || "未填"),
      "**版本**：" + (appVersion || "未知"),
      "**激活码**：" + (activationCode || "未激活"),
      "**设备码**：" + (fingerprint || "未知"),
      "**图片数量**：" + images.length + " 张",
      images.length > 0 ? "\n> 图片已存入 KV，键名：`" + backupKey + "`\n> 在 Cloudflare 后台查看完整反馈" : "",
    ].join("\n\n");
    const pushUrl = "https://sctapi.ftqq.com/" + sendKey + ".send";
    const form = new URLSearchParams();
    form.append("title", title);
    form.append("desp", desp);
    // 推送失败不影响反馈本身已存档，吞掉异常
    try {
      await fetch(pushUrl, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form.toString(),
      });
    } catch (e) {
      // 忽略推送失败
    }
  }

  return jsonResponse(200, { success: true });
}
```

---

## 关键改动说明

### 1. 图片存储策略
- **KV 完整存储**：`images` 数组完整保存到 KV，每张图片都是 base64 字符串。
- **微信推送只报数量**：推送消息只显示"图片数量：N 张"和 KV 键名，不传输图片本身。
- **查看方式**：需要查看图片时，在 Cloudflare Workers 后台 → KV → `LICENSE_KV` → 搜索键名 `feedback:时间戳`，复制 `images` 数组中的 base64 字符串，用在线 base64 解码工具查看。

### 2. 字段说明
- **category**：客户端下拉选择的问题类别，便于快速分类。
- **activationCode**：用户的激活码，用于识别是哪个用户反馈的。
- **fingerprint**：设备指纹（16 位十六进制），用于关联设备。
- **images**：base64 字符串数组，每张图片已在客户端压缩到 1080px 以内，JPEG 质量 80，单张不超过 500KB。

### 3. 注意事项
- **KV 大小限制**：Cloudflare KV 单个 value 最大 25MB，10 张 500KB 的图片总共约 5MB，在限制范围内。
- **请求超时**：免费 Workers 请求限制 30 秒 CPU 时间，解析 JSON 和存 KV 通常在 1 秒内完成。
- **推送失败兜底**：Server酱推送失败不影响反馈已存入 KV，客户端会收到成功响应。

---

## 部署

1. 复制上述修改后的 `handleFeedback` 函数，替换你现有 Worker 代码中的同名函数。
2. 在 Cloudflare Workers 编辑器中点击「Save and Deploy」。
3. 测试：在小手机内提交一条带图片的反馈，检查：
   - 微信是否收到推送（含类别、激活码、设备码、图片数量）。
   - Cloudflare KV 中是否有新记录（键名 `feedback:时间戳`）。
   - 记录中 `images` 数组是否包含 base64 数据。

---

## 查看图片的方法

1. 打开 Cloudflare 后台 → Workers → 你的 Worker → KV Bindings → `LICENSE_KV`。
2. 在「Key」搜索框输入 `feedback:` 查看所有反馈记录。
3. 点击某条记录，复制 `images` 数组中的 base64 字符串（去掉引号）。
4. 使用在线工具（如 https://base64.guru/converter/decode/image）粘贴 base64，解码查看图片。

或者，在本地编写一个简单的 HTML 工具：

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Base64 图片查看器</title>
</head>
<body>
  <h1>Base64 图片查看器</h1>
  <textarea id="input" placeholder="粘贴 base64 字符串" rows="10" style="width: 100%;"></textarea>
  <button onclick="decode()">解码</button>
  <div id="output"></div>
  <script>
    function decode() {
      const base64 = document.getElementById('input').value.trim();
      const img = document.createElement('img');
      img.src = 'data:image/jpeg;base64,' + base64;
      img.style.maxWidth = '100%';
      document.getElementById('output').innerHTML = '';
      document.getElementById('output').appendChild(img);
    }
  </script>
</body>
</html>
```

保存为 `base64_viewer.html`，浏览器打开，粘贴 base64 后点击「解码」即可查看图片。

---

## 完成

更新完成后，客户端反馈功能将完整工作，所有信息（含图片）都会安全存储在 Cloudflare KV 中。
