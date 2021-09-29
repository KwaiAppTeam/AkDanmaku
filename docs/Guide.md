AkDanmaku 1.0 Android 接入指南
======

## 背景

在经历总结和调研后，我们提出了基于 Android、iOS 各自 native 平台打造自研引擎对图形化应用进行统一处理和封装的方案，以及在此基础之上的整个弹幕流程的重新设计与实现。

其中 Android 基于 [libGDX] 以 Java/Kotlin 为语言，体积小巧，灵活可配置，围绕 ECS 框架，手动控制计算、渲染等主要流程。

[libGDX]: https://github.com/libgdx/libgdx

## 准备工作

### 引用
#### gradle

```groovy
// 顶层 build.gradle
repositories {
    mavenCentral()
}

// build.gradle

dependencies {
    implementation 'com.kuaishou:akdanmaku:1.0.3'
}
```

### 反混淆

`proguard-rules.pro` 中添加反混淆

```pro
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.BuildTarget*
-dontwarn com.badlogic.gdx.graphics.g2d.freetype.FreetypeBuild

# Required if using Gdx-Controllers extension
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
```

## 初始化 & 销毁

```kotlin
val danmakuPlayer = DanmakuPlayer(
    DefaultDanmakuRenderer(applicationContext)
)

danmakuPlayer.release()
```

## 使用

当前弹幕使用方式类似于视频播放器，分为 `DanmakuView` 与 `DanmakuPlayer`，当需要跨场景使用时，复用 `DanmakuPlayer` 即可。

```kotlin
val danmakuView = findViewById<DanmakuView>(R.id.danmakuView)

danmakuPlayer.bindView(danmakuView)
```

### 启动播放

```kotlin
// config 见后文
danmakuPlayer.start(config)
```

### 停止与暂停

```kotlin
danmakuPlayer.pause()

danmakuPlayer.stop()
```

### 跳转(Seek)

```kotlin
danmakuPlayer.seekTo(positionMills)
```

### 更新数据

AkDanmaku 定义了标准的弹幕数据类型 [`DanmakuItemData`](../library/src/main/java/com/kuaishou/akdanmaku/library/DanmakuItemData.kt)，需要转换成此类型进行添加

```kotlin
// 批量更新数据
val data: List<DanmakuItemData> = parseDanmakuJson()  // 数据解析
danmakuPlayer.update(data)

// 单个添加，多用于发送
val danmaku = DanmakuItemData().apply {
    // init fields
}
danmakuPlayer.send(danmaku)
```

## 配置

除去基础的播放功能外，其他功能均通过配置项来更改，更改配置时，需要创建一个新的 [`DanmakuConfig`](../library/src/main/java/com/kuaishou/akdanmaku/library/DanmakuConfig.kt) 对象

### 可见性
```kotlin
// 调整可见性
danmakuConfig = danmakuConfig.copy(visibility = false)
danmakuPlayer.updateConfig(danmakuConfig)
```

### 屏蔽

屏蔽等操作下放给业务方，除了默认的 Filter，也可以自定义新的弹幕过滤，过滤分为两种

- 数据过滤 `DataFilter`，过滤后除非更新 `DanmakuConfig#dataFilter` 并调用 `updateFilter`，被过滤的数据全程不会再参与任何绘制流程。
- 布局过滤 `LayoutFilter`，过滤后弹幕不会进入布局、绘制的流程

自定义的过滤器需要同时自定义一个 ID，请不要与 `DanmakuFilters` 中的预制常量冲突。同样通过对应的 ID 可以找到对应的过滤器。

> 常用的集合过滤型过滤器有一个抽象类 [`SimpleDanmakuFilter`](../library/src/main/java/com/kuaishou/akdanmaku/library/ecs/component/filter/SimpleDanmakuFilter.kt)， 用户过滤、内容过滤、色彩过滤等均通过其实现

#### 用户过滤

ID 为 `DanmakuFilters.FILTER_TYPE_USER_ID`，不再支持 UserHash 过滤，对应类为 `UserIdFilter`

```kotlin
(dataFilters[DanmakuFilters.FILTER_TYPE_USER_ID] as? UserIdFilter)?.let { filter ->
  filter.clear()
  userIds.forEach { filter.addFilter(it) }
}
```

#### 内容过滤

ID 为 `DanmakuFilters.FILTER_TYPE_BLOCKED_TEXT`，对应类为 `BlockedTextFilter`

```kotlin
(dataFilters[DanmakuFilters.FILTER_TYPE_BLOCKED_TEXT] as? BlockedTextFilter)?.let { filter ->
  filter.clear()
  userIds.forEach { filter.addFilter(it) }
}
```

### 文字缩放

```kotlin
danmakuConfig = danmakuConfig.copy(
  textSizeScale = scaleSize
)
```

### 播放速度

```kotlin
danmakuPlayer.updatePlaySpeed(3f)
```

## 交互

弹幕库不检测和拦截任何的触摸事件，需要在 `DanmakuView` 上处理触摸事件时请将它视作一个普通的 View。

### 获取点击弹幕列表

```kotlin
val danmakus: List<DanmakuItem>? = danmakuPlayer.getDanmakusAtPoint(Point(x, y))
```
其中 [`DanmakuItem`](../library/src/main/java/com/kuaishou/akdanmaku/library/data/DanmakuItem.kt) 中包含了弹幕数据结构和点击时的位置与区域

### 弹幕悬停与释放

```kotlin
// 悬停，若之前存在有已经悬停的弹幕会被释放
danmakuPlayer.hold(danmaku)

// 释放，悬停的弹幕继续运动
danmakuPlayer.hold(null)
```

## 高级功能

### 动画

除去正常的弹幕展示以外，弹幕还可以添加任意的动画效果。

```kotlin
val danmaku = DanmakuItemData(
      Random.nextLong(),
      danmakuPlayer.getCurrentTimeMs() + 500,
      "这是我自己发送的内容(*^▽^*)😄",
      DanmakuItemData.DANMAKU_MODE_ROLLING,
      textSize = 25,
      textColor = Color.WHITE,
      score = 9,
      danmakuStyle = DanmakuItemData.DANMAKU_STYLE_ICON_UP,
      rank = 9
    )
    val item = danmakuPlayer.obtainItem(danmaku)
    val sequenceAction = Actions.sequence(
      Actions.rotateBy(360f, 1000L),
      Actions.scaleTo(1.5f, 1.5f, 500L),
      Actions.scaleTo(0.8f, 0.8f, 300L)
    )
    item.addAction(
      Actions.moveBy(0f, 300f, 1735L),
      sequenceAction,
      Actions.sequence(Actions.fadeOut(500L), Actions.fadeIn(300L))
    )
    danmakuPlayer.send(item)
```

### 自定义流程

目标弹幕的渲染流程为

- Data
- Layout
- Render

这三个模块部分均可以通过扩展对应的类来自定义，他们分别是

#### Data

- DanmakkuItem：单个弹幕的数据，其中一定包含 DanmakuItemData，其他业务相关数据可以在自定义时加入
- DataSource：弹幕的数据源，提供更灵活的数据提供方式

#### Layout

- DanmakuLayouter：对弹幕进行布局的类

#### Renderer

- DanmakuRenderer：绘制弹幕，默认实现了一个带描边的纯文字绘制弹幕渲染器，如果需要更多样式，可以扩展此类来实现
