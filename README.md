# 文学时钟 · Wenxue Shizhong

把手机变成一面**文学钟**：每一分钟，屏幕上出现一句与这个时刻有关的文学句子，句中那个时间词标成红色。

不是报时工具，是把时间读出来的一种方式。

```
　　　　　晚上九点十五分，我从家里出发，
　　　　　穿过公园，再沿牛津街走到贝克街。

　　　　　——阿瑟·柯南·道尔《福尔摩斯探案集》
```

## 特性

- **整分翻页**：每到整分，页面绕 Y 轴翻转 180°，旧的一段侧翻出去、新的一段转回来
- **时间词标红**：每句都带一个「时间短语」，渲染时单独上色
- **横竖屏自适应**：按屏幕短边缩放字号，横屏时整块垂直居中
- **设置页**：在钟面上**上滑**进入（点一下退出时钟）
  - 背景色：黑色 / 蓝色 / 棕色 / 月色（浅底深字，自动换配色）
  - 亮屏时长：15 / 30 / 60 分钟，或常亮
- **无广告、无网络、无权限**
- **全屏**：铺到状态栏、导航栏、摄像头挖孔区，换浅色背景也不露黑边

## 安装

从 [Releases](../../releases) 下载 `clock-x.y.apk` 直接安装（Android 8.0+）。

桌面图标是一个细线钟面。点开即全屏显示；**点一下**退出，**上滑**进设置。

## 构建

需要 JDK 11+、Android SDK（build-tools + platform android-34）和 `r8.jar`。

```sh
export ANDROID_HOME=/path/to/android-sdk
./build.sh          # 产物：clock.apk
```

脚本用到的环境变量：`ANDROID_JAR`、`BUILD_TOOLS`、`R8`、`KEYSTORE`、`STORE_PASS`、`KEY_PASS`、`KEY_ALIAS`，默认值见 `build.sh` 开头。

### 签名

仓库里**没有**签名文件。自己生成一个：

```sh
keytool -genkeypair -keystore release.jks -alias clock -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass etasign -keypass etasign -dname "CN=clock"
```

（`release.jks` 已在 `.gitignore` 里，不会误提交。）

### 字体（可选）

界面默认使用 **霞鹜文楷 Light**（LXGW WenKai，SIL OFL 1.1）。把字体文件放成 `assets/LXGWWenKai-Light.ttf` 即可启用；没有这个文件时 App 自动退回系统衬线体，功能不受影响。

## 语料：为什么仓库里只有一部分

`assets/clock.json` 是一张静态表，每条形如：

```json
{"time": "21:15", "s": "晚上九点十五分，我从家里出发……", "h": "九点十五分", "b": "阿瑟·柯南·道尔《福尔摩斯探案集》"}
```

- `time`：对应分钟（`HH:MM`）
- `s`：正文
- `h`：要标红的时间短语（必须是 `s` 的子串）
- `b`：出处，写成 `作者　《书名》`

**本仓库自带的是公有领域演示语料**（诗经、楚辞、唐诗宋词元曲、明清小说、敦煌曲子词、禅宗灯录等，438 条，覆盖 841/1440 分钟），够跑通、也够日常看。

完整语料（1440 分钟全覆盖）需要**你自己准备**：把额外的条目写成同样格式的 `assets/local-corpus.json`（已 `.gitignore`），再跑：

```sh
python3 tools/make_clock.py
```

脚本会把 `tools/classic.json`、`assets/local-corpus.json` 合并，按小时给缺口语料补上候选句，并重新生成 `assets/clock.json`。之所以不直接提供完整语料，是因为其中包含仍在版权保护期内的作品（详见 [ATTRIBUTION.md](ATTRIBUTION.md)）。

## 排班是怎么算的

一天 1440 分钟，每条语料只对应一个分钟。没有语料的分钟，从「同一小时」的句子里挑候选（默认每个分钟 3 条），并按下面的规则约束：

- 每句最多排进 6 个分钟位（防止某句被反复征用）
- 同一小时的候选之间按使用次数轮转

App 端**不记忆**任何历史：它用 `day_of_month % 候选数` 选句，所以同一分钟一天之内固定，跨天轮换。这样反复打开看到的都一样，第二天才会变。

## 已知限制

- 覆盖率取决于你放多少语料。句子少的时候会出现「同一段落在相邻几分钟重复」；语料足够时不会。
- 按日期轮换意味着某些句子可能会在一天里出现 2 次左右；这是无状态设计的取舍。

## 许可

- 代码：MIT（见 [LICENSE](LICENSE)）
- 语料与译本、字体：见 [ATTRIBUTION.md](ATTRIBUTION.md)
