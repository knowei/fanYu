# Anime Video Resolver

## 番遇 Android

Android 客户端当前支持 Bangumi / AniList 与 AniFun 索引、多站点并发解析、站点验证、播放历史及规则导入导出。视频源会根据本机积累的解析成功率、响应时间和可识别清晰度动态排序，并在播放器中标记智能推荐线路。应用每天最多自动检查一次 `knowei/fanYu` 的 GitHub Releases，也可以在“设置”中手动检查更新。

视频源规则已升级为 v2，并继续兼容 v1。v2 可声明 `player_aaaa`、`video/source` 标签、嵌套 `iframe`、媒体地址正则、JSON 接口以及页面/播放请求头；旧规则导入时会自动补齐默认视频提取器。在“我的 → 视频源管理 → 规则仓库”中可以下载或卸载本地 CSS1 规则包，本地源编辑页支持从搜索页开始完整探测、验证第一集播放地址并导出完整规则。规则运行不依赖 AI，疑难网站后续可用 AI 辅助生成规则。

输入番剧名称和集数，程序会：

1. 在配置的网站搜索番剧；
2. 优先选择标题完全匹配的结果，否则选择第一个结果；
3. 使用详情页中的第一条播放线路；
4. 找到指定集数并读取播放页的 `player_aaaa.url`；
5. 输出JSON结果。

## 使用

```powershell
npm install
npm run resolve -- "关于我转生变成史莱姆这档事第四季" 1
```

首次运行会打开Edge。若出现Cloudflare验证，请手动完成；验证状态会保存在 `.browser-profile` 中。

也可以尝试无界面模式：

```powershell
npm run resolve -- "关于我转生变成史莱姆这档事第四季" 1 --headless
```

该站当前会拦截无界面浏览器，因此默认的可见浏览器模式更可靠。程序解析完成后会自动关闭窗口。

运行单元测试：

```powershell
npm test
```

请仅解析和播放你有权访问的内容。
