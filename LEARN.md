# HITA_Agent 开发经验积累

## 2026-05-19 / 考试查询弹 4 次 Toast

### 学到什么

**LiveData 的 setValue() 用引用相等，不是 equals()**

`MutableLiveData.setValue()` 每次调用都会通知观察者——即使新值的 `equals()` 返回 true，只要是不同对象引用，观察者就触发。项目中两个 Observer 同时设置了 `selectedTermLiveData.value`，虽然 TermItem.id 相同，但因为是不同的 TermItem 对象，观察者被触发了两次。

> 以后遇到 LiveData 重复分发的问题，先检查是不是 `setValue` 被多次调用，再查是不是真有多个观察者同时监听。

**递归调用 + 副作用 = 加倍**

`startRefresh()` 在 `selectedTerm == null` 时会注册 observeForever，拿到学期数据后递归调用自己。但每次调用都重新设置 `pageController.value`，又触发 switchMap 发一次网络请求。递归一次→两次请求→两个 Observer 各触发一次→Toast 弹了 4 次。

> 递归时要注意共享状态（pageController）是否被重复设置。把副作用收紧到条件分支里。

### 涉及的关键概念
- LiveData.setValue / postValue 的分发机制
- observe vs observeForever 的区别
- switchMap 的触发条件
- Android Activity 生命周期 onStart → refresh

---

## 2026-05-19 / CardView → MaterialCardView 全量替换

### 学到什么

**跨模块资源引用会卡 release 构建**

style 模块的布局 XML 引用了 app 模块的资源（color/white、attr/backgroundIconColorBottom 等）。Debug 构建没问题（资源合并），但 Release 构建的 `VerifyLibraryResourcesTask` 要求每个模块自给自足。改了 50+ 个文件没问题，但这一个预置坑暴露了。

> Library 模块的布局引用的资源必须模块自己提供，或者关掉校验。不要把 app 模块的资源当全局变量用。

**MaterialCardView 是 CardView 的子类**

继承关系意味着所有 `app:cardCornerRadius` 等属性天然兼容，只需要改类名就行。知道继承树在哪很重要——省了 50 个文件的逐个验证。

### 涉及的关键概念
- AGP 的 Library Resource Validation
- shrinkResources 的资源裁剪逻辑
- MaterialCardView ← CardView ← FrameLayout 继承链
- multi-module 的资源可见性

---

## 2026-05-19 / Release 构建与 APK 签名

### 学到什么

**debug keystore ≠ release keystore**

每个 Android Studio 安装都会生成一个 debug keystore（`~/.android/debug.keystore`），只用于开发测试。发给用户装的 APK 必须用正式的 release keystore 签名，且一旦用过某个 key，以后所有版本都必须用同一个——否则用户安装失败 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

**keystore.properties 不能提交到 Git**

签名密码放单独的 `.properties` 文件，.gitignore 掉。build.gradle 里动态读取——代码和凭证分离是工程基本操作。

### 涉及的关键概念
- APK 签名机制（v1 JAR / v2 APK Signature Scheme）
- keytool 查看 keystore 信息
- apksigner 验证签名
- Gradle signingConfigs 配置
- .gitignore 去掉敏感文件

---

## 2026-05-19 / TextAppearance 统一

### 学到什么

**textStyle="bold" ≠ Material3 的 font weight**

textStyle="bold" 是 faux bold（weight 700），Material3 的 TitleLarge/TitleMedium 是 weight 500（medium）。替换 TextAppearance 后如果不保留 `android:textStyle="bold"`，字会明显变细。解决方案：两者同时使用——textAppearance 管字号，textStyle 管加粗。

**TextAppearance 覆盖 textSize 但可以被 override**

`android:textAppearance` 和 `android:textSize` 同时存在时，textSize 会覆盖 style 的字号。所以替换时要删掉旧的 textSize，只留 textAppearance。

### 涉及的关键概念
- Material3 Type Scale（Headline/Title/Body/Label × Large/Medium/Small）
- font weight 的数值含义（400 regular / 500 medium / 700 bold）
- Android 的 textAppearance 优先级

---

## 2026-05-19 / 学分统计 669 分 bug（之前踩的坑）

### 学到什么

**getAllSubjects() 跨课表重复计数**

同一个科目可能出现在多个课表里，遍历时不加去重就会重复计数。`distinctBy { it.code to it.name }` 按课程代码 + 名称去重，一行解决。

> 跨数据源聚合操作 → 先想清楚什么维度是唯一的，加去重。

### 涉及的关键概念
- Kotlin distinctBy 原理（底层是 LinkedHashSet）
- 数据去重的维度选择（code + name vs 只用 code）

---

## 总结：这段时间学会了什么

| 领域 | 具���技能 |
|------|------|
| Android | LiveData 分发机制、Activity 生命周期、TextAppearance 体系、MaterialCardView、签名打包 |
| Kotlin | distinctBy、observer/observeForever、recursive + side effects |
| 工程化 | 多模块资源管理、Gradle signing、release 构建流程、Git 分支/PR/Merge |
| 排障 | 递归调用链追踪、LiveData 多观察者分发、签名冲突排查 |
