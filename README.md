# Anime Video Resolver

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
