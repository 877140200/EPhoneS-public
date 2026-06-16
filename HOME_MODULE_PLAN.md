# 家园模块施工计划

> 设计敲定于 2026-06-12，与白枢讨论确定。本文件为施工蓝图，开工后逐阶段勾选验收。

## 一句话定位

等距视角的 AI 陪伴小空间，同时兼做"游戏状态栏"防止 AI 出戏。
混合标杆 = **Habbo 的骨**（自由布置 + 网格寻路 + 纸娃娃换装）+ **明日方舟基建的皮**（Spine 骨骼动画质感）。

---

## 架构地基：三层解耦（换皮不动骨）

```
①数据层(aidata，唯一真相)
   只存：房间、物品坐标、AI坐标、猫坐标、AI装扮(outfitJson)
   只认网格 (gridX,gridY)，不知道画面长啥样
        │ Repository Flow
        ▼
②AI动作层(aidata提示词 + brain)
   职责A：把快照翻译成状态栏文字注入提示词(防出戏)
   职责B：接住 MoveAvatar 动作写回数据层
   只认坐标和行为语义，不关心画面
        │
        ▼
③渲染层(home模块)
   等距投影 + Spine 骨骼动画 + 拖拽 + 随机互动动画
   依赖抽象"小人渲染器"接口：Spine 实现一个，帧动画留口子，可替换
```

- 所有写入（用户拖 / AI 控）都汇到**数据层一个口子**。
- 渲染线程**绝不直接碰 Room**，只读 Repository 给的 Flow 快照。

---

## 已敲定的玩法规则（定死，不再改动）

- **房间**：每个 AI 联系人各一间（不共享）。等距网格，物品自由摆放 `(gridX, gridY, rotation)`。
- **AI 小人**：会自主走动，能上床。AI 大脑唯一能发的家园动作 = `MoveAvatar(targetX, targetY)`。走到床格 = AI 自己上床。
- **猫（用户代理物）**：不画用户小人，用户在家园里是一只猫。猫自己不动，能被用户拖动，能被 AI 抱/亲/揍。
- **抱/亲/揍**：纯渲染层自演（AI 路过猫随机播动画），**随机、无意义、不进状态栏、不写库、AI 完全不知情**。"抱"时猫位置绑定到 AI 跟着走，放下解绑（纯渲染层内部状态）。
- **陪睡**：**纯被动入口，绝不自动、绝不靠对话语境推断**。AI 在床上是前置条件（用户拖上去 或 AI 发 MoveAvatar 走上去），只要 AI 在床上就冒"陪睡"入口，用户主动点了才进。**本次极简版：进入后仅显示一张图**。后台陪伴活动、记录回忆/约定 → 移到后期。
- **装扮（衣/发/饰）**：Spine skin/attachment 换插槽（原生支持纸娃自由组合），接入 shopping 购买。

---

## 技术选型

- **渲染**：SurfaceView + Canvas 自绘等距网格 + 画家算法遮挡排序（按 (x+y) 深度）。
- **寻路**：网格 A*。
- **人物动画**：**本次直接上 Spine 骨骼动画**（加 spine-android runtime 依赖，资源为 .skel/.json + atlas + png）。渲染器抽象成接口，Spine 实现一个、帧动画留口子。
- **陪睡特写 UI**：远期可上 Live2D（半身近景表情，它的主场）。
- **包体积**：不作约束（用户明确）。

---

## 注入提示词的状态栏（防出戏，已瘦身）

```
【家园状态】
房间(10x8)，物品：床(8,1)、书桌(2,3)、衣柜(0,0)、绿植(5,6)
{ai}位置：书桌旁(3,3)
猫(用户)位置：床边(7,2)
```

AI 靠这段知道"屋里有什么、我在哪、猫在哪"，不会编造不存在的东西、不会说错位置。抱/亲/揍不进此栏。

---

## 数据模型草案（放 aidata）

```kotlin
HomeRoomEntity(id, ownerContactId, gridWidth, gridHeight, wallpaperId, floorId, catGridX, catGridY)
HomeItemEntity(id, roomId, itemTypeId, gridX, gridY, rotation, layer)
HomeAvatarEntity(contactId, roomId, gridX, gridY, facing, currentAction, outfitJson)
```

- 默认网格 **10×8**。
- 猫坐标**并入 HomeRoomEntity**（轻量）。
- 装扮组合用 **outfitJson** 字段存（对应 Spine skin/attachment 组合）。
- 独立 **HomeDatabase**（仿 CPhoneDatabase，version 1，无迁移负担），不动 AiDataDatabase 那个大库。

---

## 施工阶段（每阶段独立验收，互不阻塞）

### 阶段 0：模块骨架（能从桌面点进一个空房间）
- `settings.gradle.kts` 加 `include(":home")`
- 新建 `home` 模块，`build.gradle.kts` 仿 cphone（core + aidata + Hilt + Room + **spine-android**）
- `home/api/HomeApi.kt` → `createHomeFragment(): Fragment`
- 空 `HomeFragment` + `HomeViewModel`（@HiltViewModel）
- 桌面接入：`FragmentProvider` 加 `createHomeFragment()`、`DesktopNavigator` 加 `launchHome()`；app 侧 `FragmentProviderImpl` / `DesktopNavigatorImpl` 实现；桌面图标注册
- **验收**：桌面点"家园"能打开一个空白 Fragment

### 阶段 1：数据层（aidata，唯一真相）
- 实体：`HomeRoomEntity`（含猫坐标）、`HomeItemEntity`、`HomeAvatarEntity`
- `HomeDao` + 独立 `HomeDatabase`（version 1）
- entity↔domain 映射函数、必要 TypeConverter（outfitJson 等）
- `HomeRepository` 接口 + Impl，暴露 Flow
- Hilt：`di/HomeModule.kt` 提供 DB / Dao / Repository
- **验收**：能写入/读出一间房和物品（可临时打日志）

### 阶段 2：渲染层基础 + Spine 接入
- `IsometricRenderView`（SurfaceView）+ 独立渲染线程
- 等距坐标换算（格子↔屏幕像素）、画家算法按 (x+y) 排序绘制
- 画地板网格 + 物品贴图
- **Spine 接入**：加载骨骼资源、播放待机动画；渲染器抽象接口（Spine 实现一个，帧动画留口子）
- 监听 Repository Flow，数据变就重绘
- **验收**：房间和物品按等距视角正确显示、遮挡正确、小人有 Spine 待机动画在动

### 阶段 3：交互（拖拽 + 寻路 + 行走动画）
- 拖拽 AI 小人、拖拽猫（手势 → 屏幕坐标转回格子 → 写数据层）
- 网格 A* 寻路工具
- AI 收到目标格后沿路径插值移动，播放 Spine 行走动画（按 facing 朝向），到达切回待机
- **验收**：能拖动 AI 和猫、AI 平滑走到指定格且动画朝向正确

### 阶段 4：AI 动作层（防出戏 + AI 控走动）
- `AiAction.kt` 加 `MoveAvatar(targetX, targetY)` 子类
- `AiActionDeserializer.kt` 注册 `move_avatar`
- `brain/ActionExecutor.kt` 加 when 分支 → 写回 `HomeAvatarEntity`
- 新建 `HomePromptBuilder` + `AiPromptService.buildHomeStatePrompt`（或在现有场景注入状态栏块）：把房间/物品/AI位/猫位序列化成【家园状态】文本
- 指令清单声明 `move_avatar` 的 JSON 格式
- **验收**：AI 回复 move_avatar 能让小人走动；提示词里能看到家园状态文本

### 阶段 5：陪睡（极简版）
- 渲染层判定"AI 当前格 == 床格" → 界面冒出"陪睡"入口按钮
- 用户点击才进：进入沉浸 UI，**仅显示一张图**
- 后台陪伴活动、记录回忆/约定 → 标记为后期，本次不做
- **验收**：AI 上床（拖或自走）出现入口，点了才进、不点无事；进入后看到一张图

### 阶段 6：装扮（Spine skin + shopping）
- 小人换装用 Spine skin / attachment 换插槽，自由组合
- `outfitJson` 存当前穿戴组合
- 接入 shopping：购买装扮件 → 写入可用衣橱 → 家园换装界面选用
- **验收**：能给小人换不同部位装扮、自由组合、商城购买后能穿

### 阶段 7：随机互动动画（抱/亲/揍）
- 纯渲染层：AI 路过猫时随机抽一个 Spine 互动动画播放
- "抱"时猫绑定 AI 坐标跟随移动，放下解绑（仅渲染层内部状态，不写库、不进提示词）
- **验收**：AI 溜达路过猫会随机抱/亲/揍，抱起来猫跟着走

### 阶段 8：导入导出（铁律）
- 完整备份加 `home/` 子模块：`data.json`（房间/物品/装扮/坐标）+ 资源
- `ExportCompleteAppDataUseCase` 的 manifest 加 home 条目 + 导出逻辑
- 若需跨模块解耦，定义 `HomeDataProvider` 接口由 app 注入
- 对应导入恢复逻辑
- **验收**：导出含家园数据、导入能还原房间布置和装扮

---

## 后期可选（不在本次范围）

- Spine 待机动作扩充、陪睡特写改用 Live2D
- 陪睡补全功能（后台陪伴活动、记录回忆/约定）

---

## 涉及改动的关键现有文件（参考接缝）

- `settings.gradle.kts` — 注册 `:home`
- `aidata/.../data/local/CPhoneDatabase.kt` — 新建 HomeDatabase 的参考样板
- `aidata/.../domain/model/AiAction.kt` — 加 MoveAvatar 子类
- `aidata/.../domain/model/AiActionDeserializer.kt` — 注册 move_avatar
- `brain/.../service/ActionExecutor.kt` — 加 when 分支
- `aidata/.../prompt/PromptComponentBuilder.kt` — 加状态栏 section
- `desktop/.../api/FragmentProvider.kt` + `DesktopNavigator.kt` — 加家园入口
- `app/.../desktop/FragmentProviderImpl.kt` + `desktop/navigation/DesktopNavigatorImpl.kt` — app 侧实现
- `aidata/.../domain/use_case/ExportCompleteAppDataUseCase.kt` — 导入导出登记
- `cphone/api/CPhoneApi.kt` — 模块 Api 入口的参考样板
