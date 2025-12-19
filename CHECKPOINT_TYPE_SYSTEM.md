# CheckPoint 类型系统说明

## 📋 功能概述

为 `CheckPoint` 模型增加了类型字段，支持**起点**、**检查点**、**终点**三种类型，并实现了智能推荐和自动调整功能，为后续的"比赛状态机"奠定基础。

## 🏗️ 模型层改版

### CheckPoint 模型扩展

**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/model/CheckPoint.java`

#### 新增常量

```java
public class CheckPoint extends RealmObject {
    // 打卡点类型常量
    public static final String TYPE_START = "起点";
    public static final String TYPE_CHECKPOINT = "检查点";
    public static final String TYPE_FINISH = "终点";
    
    // ... 其他字段
    private String type; // TYPE_START, TYPE_CHECKPOINT, TYPE_FINISH
}
```

#### 类型说明

| 类型 | 常量 | 描述 | 图标 |
|------|------|------|------|
| 起点 | `TYPE_START` | 赛事的开始位置，全局唯一 | 🏁 |
| 检查点 | `TYPE_CHECKPOINT` | 中间必经打卡点，可多个 | 📍 |
| 终点 | `TYPE_FINISH` | 赛事的结束位置，全局唯一 | 🎯 |

## 🎨 UI 层改进

### 1. CheckpointAdapter 增强显示

**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/view/race/CheckpointAdapter.java`

#### 功能特性

- ✅ **图标标识**: 不同类型显示对应的 Emoji 图标
- ✅ **类型文本**: 在名称后显示类型（如：起点、检查点、终点）
- ✅ **视觉区分**: 通过图标和文字双重标识

#### 显示效果

```
🏁 起点打卡点 (起点)
📍 湖边观景台 (检查点)
📍 山顶观景台 (检查点)
🎯 终点广场 (终点)
```

### 2. CreateRaceActivity 类型选择

**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/view/race/CreateRaceActivity.java`

#### 添加打卡点对话框

- **类型下拉选择器**: 使用 `AutoCompleteTextView` 提供三种类型选择
- **智能推荐**: 根据当前打卡点状态自动推荐合适的类型
- **验证逻辑**: 防止添加重复的起点或终点

#### 智能推荐逻辑

```java
private String getRecommendedCheckpointType() {
    // 1. 没有打卡点或没有起点 → 推荐起点
    if (checkPoints.isEmpty() || !hasStart) {
        return TYPE_START;
    }
    
    // 2. 已有起点，没有终点，且已有2个以上打卡点 → 推荐终点
    if (!hasFinish && checkPoints.size() >= 2) {
        return TYPE_END;
    }
    
    // 3. 其他情况 → 推荐检查点
    return TYPE_CHECKPOINT;
}
```

#### 推荐规则表

| 当前状态 | 推荐类型 | 原因 |
|---------|---------|------|
| 无打卡点 | 起点 | 首先设置起点 |
| 无起点 | 起点 | 起点是必需的 |
| 有起点，无终点，≥2个点 | 终点 | 形成完整路线 |
| 有起点和终点 | 检查点 | 中间节点 |

### 3. 自动调整类型功能

**UI 按钮**: `activity_create_race.xml` 中的"自动调整类型"按钮

#### 功能说明

自动将打卡点列表调整为规范格式：
- **第一个点** → 自动设为起点
- **最后一个点** → 自动设为终点
- **中间的点** → 自动设为检查点

#### 使用场景

1. **批量导入后调整**: 从文件或其他来源批量导入打卡点后，一键规范化
2. **误操作纠正**: 类型设置错误时快速修复
3. **顺序调整后同步**: 调整打卡点顺序后，自动更新类型

#### 实现代码

```java
private void autoAdjustCheckpointTypes() {
    if (checkPoints.isEmpty()) {
        return;
    }
    
    // 按 orderIndex 排序
    List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
    sortedPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));
    
    // 第一个点设为起点
    sortedPoints.get(0).setType(TYPE_START);
    
    // 最后一个点设为终点
    if (sortedPoints.size() > 1) {
        sortedPoints.get(sortedPoints.size() - 1).setType(TYPE_FINISH);
    }
    
    // 中间的点保持为检查点
    for (int i = 1; i < sortedPoints.size() - 1; i++) {
        sortedPoints.get(i).setType(TYPE_CHECKPOINT);
    }
    
    adapter.notifyDataSetChanged();
}
```

## 🔒 验证规则

### 添加打卡点时的验证

在 `CreateRaceActivity.addCheckpoint()` 中实现：

```java
// 1. 起点唯一性验证
if (TYPE_START.equals(type)) {
    for (CheckPoint point : checkPoints) {
        if (TYPE_START.equals(point.getType())) {
            UIUtil.showToast(this, "已存在起点，请先删除后再添加");
            return;
        }
    }
}

// 2. 终点唯一性验证
if (TYPE_END.equals(type)) {
    for (CheckPoint point : checkPoints) {
        if (TYPE_END.equals(point.getType())) {
            UIUtil.showToast(this, "已存在终点，请先删除后再添加");
            return;
        }
    }
}
```

## 🎯 排序规则

### 打卡点自动排序

不同类型的打卡点在列表中的位置自动调整：

```java
// 起点：始终在第一个位置
if (TYPE_START.equals(type)) {
    point.setOrderIndex(1);
    // 将其他点的顺序后移
    for (CheckPoint p : checkPoints) {
        p.setOrderIndex(p.getOrderIndex() + 1);
    }
    checkPoints.add(0, point);
}

// 终点：始终在最后一个位置
else if (TYPE_END.equals(type)) {
    point.setOrderIndex(checkPoints.size() + 1);
    checkPoints.add(point);
}

// 检查点：插入到终点之前
else {
    int insertIndex = checkPoints.size();
    for (int i = 0; i < checkPoints.size(); i++) {
        if (TYPE_END.equals(checkPoints.get(i).getType())) {
            insertIndex = i;
            break;
        }
    }
    // ... 插入逻辑
}
```

## 🚀 使用流程

### 管理员创建赛事流程

1. **点击地图添加打卡点**
   - 系统自动推荐类型（第一个为起点）
   - 管理员可手动调整类型
   - 输入名称和打卡半径

2. **添加更多打卡点**
   - 系统智能推荐类型
   - 第3个及以后推荐检查点
   - 当有2+个点时推荐添加终点

3. **使用自动调整功能**
   - 点击"自动调整类型"按钮
   - 系统自动规范化类型
   - Toast 提示"已自动调整：首点为起点，尾点为终点"

4. **保存赛事**
   - 类型信息自动保存到 Realm 数据库
   - 与 QR 码、坐标等信息一起持久化

### 示例操作

```
操作步骤：
1. 点击地图 → 弹出对话框，默认选择"起点" → 输入"起点广场" → 确定
2. 点击地图 → 弹出对话框，默认选择"检查点" → 输入"湖边观景台" → 确定
3. 点击地图 → 弹出对话框，默认选择"检查点" → 输入"山顶观景台" → 确定
4. 点击地图 → 弹出对话框，默认选择"终点" → 输入"终点广场" → 确定
5. 点击"自动调整类型" → Toast: "已自动调整：首点为起点，尾点为终点"
6. 点击"保存赛事" → 赛事创建成功

最终列表：
🏁 1. 起点广场 (起点)
📍 2. 湖边观景台 (检查点)
📍 3. 山顶观景台 (检查点)
🎯 4. 终点广场 (终点)
```

## 🔗 与其他模块的集成

### 1. RaceManager

- `createRace()` 和 `updateRace()` 自动保存 `type` 字段
- `getCheckPointsForRace()` 返回包含类型信息的打卡点

### 2. CheckInActivity

- 可根据类型显示不同的打卡提示
- 起点可标记为"开始计时"
- 终点可标记为"结束计时"

### 3. RaceDetailActivity

- 显示打卡点列表时，类型图标和文字同步显示
- 只读模式下也能清晰看到类型信息

## 🎓 未来扩展：比赛状态机

基于类型系统，可以实现完整的比赛状态管理：

### 状态转换

```
[未开始] → 到达起点打卡 → [进行中] → 依次打卡检查点 → [进行中] → 到达终点打卡 → [已完成]
```

### 状态验证

```java
// 伪代码示例
public boolean canCheckIn(CheckPoint point, String userId) {
    if (point.getType().equals(CheckPoint.TYPE_START)) {
        return !hasStarted(userId); // 只能打卡一次
    } else if (point.getType().equals(CheckPoint.TYPE_CHECKPOINT)) {
        return hasStarted(userId) && !hasFinished(userId);
    } else if (point.getType().equals(CheckPoint.TYPE_FINISH)) {
        return hasCheckedAllCheckpoints(userId); // 必须完成所有检查点
    }
    return false;
}
```

### 时间记录

```java
// 起点：记录开始时间
if (checkPoint.getType().equals(CheckPoint.TYPE_START)) {
    raceSession.setStartTime(new Date());
}

// 终点：记录结束时间，计算总用时
if (checkPoint.getType().equals(CheckPoint.TYPE_FINISH)) {
    raceSession.setEndTime(new Date());
    long duration = raceSession.getEndTime().getTime() - raceSession.getStartTime().getTime();
    raceSession.setTotalDuration(duration);
}
```

## 📝 配置选项

### 常量定义位置

统一在 `CheckPoint` 模型中定义：

```java
public static final String TYPE_START = "起点";
public static final String TYPE_CHECKPOINT = "检查点";
public static final String TYPE_FINISH = "终点";
```

### 在其他类中使用

```java
// CreateRaceActivity.java
private static final String TYPE_START = CheckPoint.TYPE_START;
private static final String TYPE_CHECKPOINT = CheckPoint.TYPE_CHECKPOINT;
private static final String TYPE_END = CheckPoint.TYPE_FINISH;
```

## ⚠️ 注意事项

### 1. 数据库迁移

如果现有数据库中已有打卡点数据，需要为它们设置默认类型：

```java
// 可在 RealmHelper 或 Migration 中处理
realm.executeTransaction(r -> {
    RealmResults<CheckPoint> points = r.where(CheckPoint.class).findAll();
    for (CheckPoint point : points) {
        if (point.getType() == null || point.getType().isEmpty()) {
            point.setType(CheckPoint.TYPE_CHECKPOINT); // 默认为检查点
        }
    }
});
```

### 2. 向后兼容

`addCheckpoint(String name, double lat, double lng)` 方法保留，默认使用检查点类型：

```java
private void addCheckpoint(String name, double lat, double lng) {
    addCheckpoint(name, lat, lng, TYPE_CHECKPOINT, DEFAULT_CHECK_RADIUS);
}
```

### 3. UI 一致性

所有显示打卡点的地方都应该显示类型标识（图标 + 文字）：
- ✅ `CheckpointAdapter` (管理员创建/编辑页面)
- ✅ `RaceDetailActivity` (赛事详情页面)
- 🔲 `CheckInActivity` (选手打卡页面 - 可扩展)
- 🔲 地图 Marker (可根据类型显示不同颜色)

## 📚 相关文档

- `CheckPoint.java` - 数据模型定义
- `CreateRaceActivity.java` - 类型选择和自动调整逻辑
- `CheckpointAdapter.java` - 类型显示实现
- `activity_create_race.xml` - UI 布局

---

**开发者**: AI Assistant  
**更新时间**: 2025-12-18  
**版本**: v1.0


