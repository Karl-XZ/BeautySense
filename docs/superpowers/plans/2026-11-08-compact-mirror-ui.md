# 妆伴 V2 极简镜面界面实施方案

> **For agentic workers:** Use executing-plans task by task. Web and native presentation work are independent domains and may be delegated in parallel; final integration and physical-device operation belong to the root agent.

**Goal:** 按用户两张参考图，把底部从长文字报告和商品卡片改为轻量分类栏、商品缩略图和结构化概览；长按或详情入口显示完整内容。
**Architecture:** 保留现有单前摄工作流、脸部保留区域、选择验证与异步所有权。仅改变两端呈现层；使用现有结构化分析 JSON 与真实 PROYA 目录，无新增模型或图表依赖。
**Tech Stack:** Web ES modules/CSS/native DOM；Android Compose/Canvas/现有 JSONObject。
**Spec:** 用户本轮截图与要求，以及 ../specs/2026-11-07-beautysense-v2-requirements.md。

## Visual and interaction decisions

- 延续浅色半透明镜面底座，黑/深灰分类胶囊和细选中描边，品牌玫瑰色用于主要操作；去除默认浅紫大块背景。
- 推荐区：上方横向分类，下方横向真实商品缩略图。短名去除重复品牌前缀，最多两行。主区域不显示推荐理由、成分、介绍、使用说明。选中状态用描边及勾选，数量一行显示；每类最多一款、全局至少一款的规则不变。
- 点击单规格商品选择/取消；多个规格必须显式选择真实规格。长按产品打开详情，不能顺便选择、取消或生成。提供可见详情按钮作为键盘/辅助操作替代入口。
- 产品详情包含完整名称、推荐理由、官方介绍、已提供的成分、使用说明和官方来源。详情只能占现有底部/横屏侧边信息区域，不能遮挡椭圆保留区；有固定关闭按钮、独立滚动，支持 Back/Escape 和焦点恢复。
- 主要动作按钮长按显示简短操作帮助，不触发动作。原貌对照的既有按住行为不能被长按帮助截走。
- 分析页：六项已知 skinMetrics 显示轻量条形指标，图像特征强度 0–100，不包装成健康评分或百分比。只处理有限数值（number、纯数值字符串、对象 value），无效/缺失/越界不画假数值。未知键不显示英文或 JSON。
- summary 最多约30个汉字，面部特点最多3个短标签，其余完整报告保存在详情。任何精简不得发明肤质、年龄、医学结论或商品事实。
- 定位、生成、预览文字压缩为短标题/短状态。对话保持可滚动、朗读完整回复，长回复可查看全文；核心点击流程无需小妆或麦克风。
- 详情按当前 epoch 所有；重置、退出、重新分析、换人不能残留上一次私密分析。不得干扰进行中的分析或生成。
- 保留手机比例、前摄完整显示、竖屏上下/横屏左右的面部与信息分区，维持原先保护椭圆不重叠的几何约束。

## Impact and rollback

Web: v2/app.js, style.css, index.html, a small presentation module and focused tests. Native: MirrorScreen.kt, focused panel/presentation components and JVM tests; preview.3/versionCode8. No server API/schema/product catalog, camera implementation, auth or generation model changes. Existing state/action/session ownership must remain canonical. No data migration. Rollback by reverting the UI commit(s), preserving prior preview.2 APK.

## Tasks

- [x] 1. Presentation: tests first for known metric labels, numeric JSON forms, invalid values, compact strings and true product names; implement platform-local helpers.
- [x] 2. Web: compact cards/metrics, face-safe details, non-clicking long press with movement cancellation, keyboard alternative; preserve existing selectors where possible. New sheet #detail-sheet; .product-rail; data-metric keys; data-product buttons; data-detail-close.
- [x] 3. Native: compact cards/metrics and panel-confined detail pane, combined-click semantics, Back handling and epoch guard. testTag detail-sheet, product-rail, metric-redness etc, product-PRODUCT_ID and detail-PRODUCT_ID. Build preview.3 with 27 existing JVM tests plus new presentation tests and release Kotlin compilation.
- [x] 4. Integration: browser tests for long press not selecting/generating, detail overflow, Escape/close, numeric bars/no raw JSON, reset clearing private details, horizontal scrolling not causing long press, small phone/landscape/large text, and complete core flow.
- [x] 5. Physical phone: install preview.3, use generated test portrait for real-service analysis/recommendation/generation, long-press/close/selection/scroll checks and screenshots. Check real front camera opening/release, font scale and orientation. Do not claim real-person motion tests passed.
- [x] 6. Read-only review, resolve important findings, verify actual diff is presentation-only, package APK plus private screenshots/report; no actual camera images or secrets enter Git. Push existing feature branches only.

## Review focus

1. Long press and horizontal swipes must never also click or invoke paid generation.
2. Lower/side detail pane must never overlap the protected face region at small heights or fontScale1.3.
3. Missing or malformed metrics must remain absent, never become zero or fabricated grades.
4. Switching/resetting sessions must synchronously hide private previous detail content.
5. Item count, single-per-category selection, explicit variants and cancellation ownership must remain identical to current workflow.

## Verification evidence

- Web: 44 unit/API tests,20 browser cases,5 focused post-polish cases passed.
- Native:35 JVM tests plus debug/test APK builds and release Kotlin compile passed.
- V2509A Android16:16 cases; generated input with real analysis/recommendation/generation/dialogue; camera smoke used actual front camera without a person.
- Physical and browser screenshots, reports, credentials checks and APK are private local artifacts under Desktop/work2026/妆伴V2_极简界面_20261108; no frames or private logs are staged.
- Source camera/API/state/catalog files are unchanged. Additional work remained presentation-only: alpha-margin trimming, gesture focus/suppression fixes and measured line-height/card-fit corrections.
- Real-person dynamic tracking and spoken-input checks were not rerun in this UI task.
