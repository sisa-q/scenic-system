# 游客端 → 安卓 App（套壳）打包说明

这个目录是一个 **uni-app 套壳工程**：把「智慧景区游客端」网页装进一个安卓 App 外壳。
用户安装 App 后打开，里面加载的就是你的系统网页（游客端）。

> 本质：App 是"壳"，内容来自你的服务器。服务器没开 / 手机没网，App 就打不开内容。

## 一、前提

- Windows 电脑（用来打包）
- 安卓手机（用来安装测试）
- 手机和电脑连**同一个 WiFi**

## 二、打包步骤（约 30 分钟，含下载 HBuilderX）

1. **下载 HBuilderX**（免费）：
   https://www.dcloud.io/hbuilderx.html
   选 Windows 版，解压后运行 `HBuilderX.exe`

2. **打开本工程**：HBuilderX → 文件 → 打开目录 → 选择 `D:\bishe\scenic-android`

3. **注册并登录 DCloud 账号**（免费，云打包需要）：
   HBuilderX 右上角头像 → 注册/登录

4. **生成 AppID**：打开 `manifest.json` → 可视化界面 → 「基础配置」里点 **重新获取**（会生成 `__UNI__xxxx` 的 appid，免费）

5. **确认首页地址**：打开 `pages\index\index.vue`，把 `homeUrl` 改成你系统的地址
   - 局域网演示：`http://192.168.9.197`（手机需连同一 WiFi）
   - 正式上线：`https://你的域名`

6. **云打包**：菜单 发行 → 原生App-云打包 → 平台选 **Android** → 证书用默认「公共测试证书」→ 勾选「打正式包」→ 点 **打包**
   （云端排队一般几分钟，免费）

7. **下载 APK**：打包完成后，点击「下载」得到 `.apk` 文件

8. **手机安装**：把 `.apk` 传到手机（微信/QQ/数据线都行）→ 点击安装 → 系统提示"未知来源"时选**允许**

## 三、使用

- 手机连 WiFi，打开「智慧景区游客端」App → 就是游客端首页，可买票、看景点
- 管理端不进 App，管理员用电脑浏览器访问 `http://192.168.9.197:8082`

## 四、常见问题

| 现象 | 原因 / 解决 |
|---|---|
| App 白屏 / 无法访问 | ① 手机与电脑不在同一 WiFi；② 地址写错；③ 电脑没开机或 Docker 没启动；④ 防火墙没放行（我们已放行 80/8082） |
| 安卓 9+ 打不开 http 地址 | 已内置 `nativeResources/android/res/xml/network_security_config.xml` 允许明文 http；若仍不行，检查 manifest.json 里是否有 INTERNET 权限 |
| 想正式上线 | 把 `homeUrl` 改成 `https://域名`；买云服务器部署（见 `D:\bishe\deploy\README.md`）；可移除明文配置 |
| iOS（苹果） | 需要苹果开发者账号（付费）和证书，毕业设计一般不做 |

## 五、目录说明

| 文件 | 作用 |
|---|---|
| `pages/index/index.vue` | App 首页：web-view 加载游客端地址（**要改地址就改这里**） |
| `manifest.json` | 应用名、AppID、安卓权限（INTERNET 等） |
| `pages.json` | 页面路由配置 |
| `nativeResources/android/res/xml/network_security_config.xml` | 允许 http 明文访问（安卓 9+ 必需） |
| `App.vue` / `main.js` / `uni.scss` | uni-app 工程基础文件 |