# Spinecare Mom

Spinecare Mom 是根据 `脊护妈妈助手_详细设计与开发规格_V2.pdf` 生成的移动端 APP 界面原型。

## 目录

- 源码目录：`D:\2026\202606\SpinecareMom`
- 工作环境依赖目录：`D:\2026\codexwork\SpinecareMom`
- 依赖映射：`/deps/lucide.js` -> `D:\2026\codexwork\SpinecareMom\vendor\lucide.js`
- Android 原生工程：`D:\2026\202606\SpinecareMom\spinecare-android`

## Web 原型启动

```powershell
cd D:\2026\202606\SpinecareMom
.\run-dev.ps1
```

默认访问地址：

```text
http://127.0.0.1:4173
```

## Android 构建

```powershell
cd D:\2026\202606\SpinecareMom\spinecare-android
.\build-debug.ps1
```

APK 输出：

```text
D:\2026\202606\SpinecareMom\spinecare-android\app\build\outputs\apk\debug\app-debug.apk
```

## 已覆盖界面

- 登录/注册、隐私与监护人授权
- 设备绑定与建档向导
- 首页佩戴看板、三色预警、趋势图、智能解读
- AI 咨询结构化回答与就医提示
- AI 报告、复诊报告预览与导出入口
- 皮肤打卡、生长记录、影像档案
- 我的、设备管理、提醒设置、隐私与同意
- 消息中心与孩子模式
