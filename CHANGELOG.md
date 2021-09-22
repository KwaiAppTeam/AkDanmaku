Change Log
=========

## 3.0.1 2021-08-04

### Fixes:
- 修复了切片数据时索引错误导致的崩溃问题

## 3.0.0 2021-08-03
Release 版本

### Fixes:
- 修复了 cache 创建时可能出现的大小为 0 导致的崩溃

## 3.0.0-rc04 2021-08-02
备选 Release 版本 04
### New features:
None

### Changes:
None

### Fixes:
- 添加 hold 操作中的同步块，以解决在操作途中出现的数据变动

## 3.0.0-rc03 2021-08-02
备选 Release 版本 03
### New features:
None

### Changes:
- 移除 Cache 中对持有的 Bitmap 的 recycle 操作（现阶段支持的版本已经不再需要）

### Fixes:
- 移除了 recycle，修复多线程环境下出现的多次 recycle 和 recycle 后绘制导致的崩溃

## 3.0.0-rc02 2021-07-30
备选 Release 版本 02
### New features:

1. 全新的、基于 ECS 系统的 Android 原生弹幕系统
2. 具有弹幕的过滤、显示样式、倍速播放等功能
3. 添加了新的高级动画功能以及更方便的自定义绘制样式
4. 类比播放器的 Player-View 模型

### Changes:

None

### Fixes:

None

### Upgrades:

None

### Known Issues:

Nop yet
