# Sonify 云服务端部署指南（腾讯云上海四区 AgentWeb）

- **服务器实例**：`AgentWeb`（`lhins-iffy8l2c`，上海四区）
- **公网 IPv4**：`43.142.250.231`
- **公网 IPv6**：`2402:4e00:c032:7200:10ae:f994:427c:0`
- **服务域名**：`https://sonify.zhangbing.tech`

---

## 一、5 分钟首次部署

```bash
# 1. 在服务器创建目录并拷贝 server/sonify_server.py
sudo mkdir -p /opt/sonify-server /var/lib/sonify-server/releases /var/lib/sonify-server/crashes
sudo cp sonify_server.py /opt/sonify-server/
sudo chown -R www-data:www-data /var/lib/sonify-server

# 2. 注册并启动 systemd 常驻服务
sudo cp sonify-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sonify-server
curl -s http://127.0.0.1:8090/api/v1/health

# 3. 配置 Nginx + Let's Encrypt HTTPS（确保 DNS A 记录 sonify.zhangbing.tech -> 43.142.250.231）
sudo cp nginx_sonify.zhangbing.tech.conf /etc/nginx/sites-available/sonify.zhangbing.tech
sudo ln -sf /etc/nginx/sites-available/sonify.zhangbing.tech /etc/nginx/sites-enabled/
sudo certbot --nginx -d sonify.zhangbing.tech
sudo nginx -t && sudo systemctl reload nginx

# 4. 校验线上接口
curl -s https://sonify.zhangbing.tech/api/v1/health
```

## 二、日常一键发版

在本地仓库根目录执行：

```bash
./scripts/publish_release.sh \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version-code 3 \
  --version-name 0.3.0 \
  --notes "- 新增独立进程 Crash 拦截与 3 秒自动拉起\n- 新增全屏跨源聚合搜索与简繁互通\n- 新增应用内在线更新" \
  --ssh root@43.142.250.231
```

## 三、查看收到的脱敏崩溃日志

```bash
tail -n 20 /var/lib/sonify-server/crashes/crashes-$(date +%F).jsonl | python3 -m json.tool
```
