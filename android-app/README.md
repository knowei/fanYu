# 番剧解析 Android 测试版

最小流程：

1. 输入番剧名称和集数；
2. 后台 WebView 搜索作品；
3. 读取详情页线路和指定集数；
4. 加载播放页并读取 `player_aaaa` 或拦截媒体请求；
5. 显示 m3u8/mp4 地址。

WebView 会保存站点 Cookie。只有自动安全验证超过 12 秒仍未完成时，才显示验证页面。

构建调试包：

```powershell
$env:JAVA_HOME='D:\environ\Java\jdk-17'
$env:ANDROID_HOME='D:\environ\androidsdk'
.\package-app.ps1 -BuildType debug
```

打包签名正式版（默认）：

```powershell
.\package-app.ps1
```

脚本会读取 `app/build.gradle.kts` 中的版本号，并将 APK 复制到 `releases` 目录，同时输出 SHA256。

视频源管理：

- `css1.json` 作为只读订阅源，不会被 App 修改；
- “我的 → 视频源管理 → 添加”可以创建本地源；
- 输入网站首页或搜索地址后，可尝试自动识别搜索结果和选集结构；
- 自动探测失败时仍可手动填写 CSS 选择器；需要验证码的网站应先在 App 内人工验证。
- “导出规则”会生成 `fanyu-source-rules.json`；“导入规则”会校验后按规则 ID 或搜索地址合并，不会覆盖 `css1.json`。
