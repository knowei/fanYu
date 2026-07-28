# Android 自动发布配置

向仓库推送形如 `v1.1.4` 的版本标签后，GitHub Actions 会自动构建签名 APK、创建 GitHub Release，并根据本次提交自动生成更新说明。

## 首次配置

在 GitHub 仓库打开 **Settings → Secrets and variables → Actions**，新增以下四个仓库密钥：

- `ANDROID_KEYSTORE_BASE64`：`android-app/fanyu-release.jks` 文件的 Base64 内容
- `ANDROID_STORE_PASSWORD`：签名文件的存储密码
- `ANDROID_KEY_ALIAS`：签名密钥别名
- `ANDROID_KEY_PASSWORD`：签名密钥密码

在 `android-app` 目录执行以下 PowerShell 命令，即可将签名文件的 Base64 内容复制到剪贴板，再粘贴到第一个密钥中：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\fanyu-release.jks')) | Set-Clipboard
```

> 请勿将签名文件、密码或上述 Base64 内容提交到仓库。

## 发布版本

先修改 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`，提交并推送代码；随后创建并推送与版本号对应的标签：

```powershell
git tag v1.1.4
git push origin v1.1.4
```

在仓库的 **Actions** 页面可以查看构建进度。成功后，APK 会出现在仓库的 **Releases** 页面，可直接下载和安装。
