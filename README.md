# 影库 FilmVault —— 原生安卓客户端

把目标影视站点（电影 / 剧集 / 动漫）做成的**纯原生 Android App**，
使用 **Kotlin + Jetpack Compose** 从头实现，**全程无任何 WebView 套壳**。

> 注意：本项目仅为该站点的**个人原生客户端**，账号信息由用户提供，所有数据均来自站点公开接口。
> 站点本身带有 `filejin` 反爬体系（PoW 验证 + 登录态），本应用已在网络层完整复刻。

作者：`qinlinglong`

本 App 是一个模块化影视仓库与信息聚合工具，不提供、存储、上传或分发任何影视资源。内容与第三方链接来自用户配置的站点或相关服务，版权及使用责任归原权利人和使用者。

---

## 一、它“原生”在哪

| 模块 | 实现方式 |
|------|----------|
| UI | 100% Jetpack Compose（Material3），无任何 WebView |
| 网络 | OkHttp 原生 HTTP 客户端，自研 PoW 求解器、Cookie 持久化 |
| 登录 | `POST /user/login` + `app_auth` Cookie 管理 |
| 浏览/筛选/搜索 | 原生分页列表 + 分类搜索 + 网页首页/分类筛选 |
| 详情 | 解析站点 SSR 内嵌 JSON，原生展示海报/简介/演员/分集 |
| 资源 | 磁力 / 网盘 / 在线线路 三类资源原生列表展示 |
| 播放 | 在线线路先解析为直链，m3u8/mp4 走内置 **ExoPlayer (Media3)** 原生播放 |
| 收藏/历史 | DataStore 本地收藏 + 服务器同步；观看历史原生列表 |

网盘/磁力这类依赖外部应用的资源，按 Android 标准做法通过 `Intent` 交给系统浏览器 /
BT 客户端 / 网盘 App 处理；在线播放线路则在 App 内使用 Media3 播放器播放。

---

## 二、逆向得到的接口（已内置在 `ApiClient.kt`）

| 功能 | 接口 | 说明 |
|------|------|------|
| 反爬验证 | `GET /res/pow` → `{N,x,t}`；`POST /res/pow` 提交 `y=x^(2^t) mod N` | 算出的 `y` 经服务端校验后下发 `browser_verified` Cookie |
| 登录 | `POST /user/login`（`username`,`password`,`cookietime`） | 下发 `app_auth` Cookie |
| 列表 | `GET /res/{mv|tv|ac}?page=&sort=&quality=&status=…` | 列存 JSON：`i`=ID、`t`=标题、`a`=[年份,地区码,类型码…]、`d`=评分、`im`=IMDB、`q`=画质、`g`=状态 |
| 搜索 | `GET /res/search?q=` | 同列表结构，含 `zhuyan`（演员） |
| 详情元数据 | `GET /{dir}/{id}` 的 HTML 内嵌 JSON | `summary`/`diqu`/`yuyan`/`type`/`stime`/`times`/`zhuyan`/`xle` |
| 资源 | `GET /res/downurl/{dir}/{id}` | `downlist.list.m`=磁力 info-hash（拼成 `magnet:?xt=urn:btih:`+m）；`panlist.url`=网盘直链；`playlist`=在线线路（拼 `/py/{i}/{n}`） |
| 收藏 | `GET /res/favorite/add|del/{dir}/{id}` | 增删；列表本地由 DataStore 维护 |
| 历史 | `GET /res/historys` | JSON 数组 |
| 海报 | `https://s.tutu.pm/img/{dir}/{id}/256.webp` | 当前站点可用的封面格式 |

**PoW 关键公式**（经验证与浏览器行为一致）：
```
y = pow(x, 2^t, N)          // BigInteger.modPow，t 最大约 40 万
POST /res/pow  body: "y=<hex>"
```

---

## 三、编译运行

环境要求：
- Android Studio Hedgehog / Iguana 及以上
- JDK 17（AGP 8.5 需要）
- 一台 Android 7.0+（API 24+）设备或模拟器

步骤：
1. 用 Android Studio 打开本仓库根目录（已含 `gradle-wrapper.properties`）。
2. 等待 Gradle 同步完成（会自动下载 AGP / Compose BOM / OkHttp / Coil / Media3 等依赖）。
3. 连接设备或启动模拟器，`Run 'app'`（或 `./gradlew assembleDebug`）。
4. 首次启动在登录页填写用户自己的模块化仓库地址、账号和密码。

> 本项目不内置任何站点地址；站点地址由用户在登录页或设置页自行填写。

---

## 四、工程结构

```
app/src/main/java/com/filmvault/app/
├── MyApp.kt / MainActivity.kt         # Application + 入口
├── di/AppModule.kt                    # 依赖容器
├── util/
│   ├── Constants.kt                   # 站点/接口常量
│   ├── PowSolver.kt                   # PoW 求解（bigint 模幂）
│   ├── FileCookieJar.kt               # 持久化 CookieJar
│   ├── FavoritesStore.kt              # DataStore 收藏
│   └── Playback.kt                    # 外链/磁力 Intent 处理
├── data/
│   ├── model/Models.kt                # 数据模型
│   ├── remote/ApiClient.kt            # 原生网络层（PoW/登录/列表/详情/资源/历史/收藏）
│   └── repository/FilmRepository.kt   # 业务仓库
├── viewmodel/FilmViewModels.kt        # Auth/Catalog/Detail/Library ViewModel
└── ui/
    ├── theme/Theme.kt
    ├── components/MovieCard.kt        # 海报网格
    ├── screens/                       # Login/Home/Catalog/Detail/Search/Library/Player
    └── navigation/NavGraph.kt
```

---

## 五、已知边界

- 只有成功解析出 m3u8/mp4 直链时才在 App 内播放；无法解析时会提示并尝试打开站点播放页。
- 站点接口随版本可能调整；如某接口结构变化，仅需更新 `ApiClient.kt` 的解析逻辑。
- 收藏列表以本地 DataStore 为准（与服务器增删双向同步），保证离线可读。
