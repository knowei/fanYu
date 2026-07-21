# 番剧解析 Android 测试版

最小流程：

1. 输入番剧名称和集数；
2. 后台 WebView 搜索作品；
3. 读取详情页线路和指定集数；
4. 加载播放页并读取 `player_aaaa` 或拦截媒体请求；
5. 显示 m3u8/mp4 地址。

WebView 会保存站点 Cookie。只有自动安全验证超过 12 秒仍未完成时，才显示验证页面。

构建：

```powershell
$env:JAVA_HOME='D:\environ\Java\jdk-17'
$env:ANDROID_HOME='D:\environ\androidsdk'
gradle :app:assembleDebug
```
