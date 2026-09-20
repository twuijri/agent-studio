# أندرويد: إزالة «إدارة وقت التشغيل»، زر التحديث المدمج، والتحقق من صفوف قدرات الوكيل

المسؤول: twuijri
الفرع: fix/android-agent-rows (من `origin/mobile-staging`)
الحالة: review (محلي؛ لم يُدفَع ولم يُفتح طلب دمج)

## المشكلة والهدف
بعد تجربة المالك لبناء iOS رقم 41 طلب ثلاثة إصلاحات، وهذا السجل يعكسها على أندرويد
كي يبقى تطبيقا الهاتف تطبيقًا واحدًا (لقطتا iOS المرجعيتان: قائمة Agent Manager وشاشة
وكيل Hermes):

1. **«إدارة وقت التشغيل» وواجهة حزم وقت التشغيل لا مكان لها على الهاتف.** المالك يشغّل
   السيرفر في Docker ووقت تشغيل Hermes جزء من الصورة؛ مثبّت وقت التشغيل يخص تطبيق سطح
   المكتب. على أندرويد كانت الورقة `RuntimeManagerSheet` في `ui/agents/AgentScreen.kt`
   (`loadRuntimeVersions`، `state.runtimeVersions`)، وزر «إدارة وقت التشغيل»/«تثبيت» على
   بطاقة Hermes في `ui/agents/AgentManagerScreen.kt:306-310`، وواجهة
   `HermesApi.runtimeVersions/activateVersion/downloadVersion/restartWebUi`
   (`/api/hermes/runtime-versions*`) ونماذج `RuntimeVersion(s)` في `AgentTools.kt`،
   ولا يستخدمها شيء آخر (تحقق بـ `grep`).
2. **شارة التحديث على بطاقة وكيل البرمجة** ظهرت على iOS ككتلة ملتفّة («Update v0.155.1»
   داخل حبّة). على أندرويد كان الزر نصيًا داخل `FlowRow` بنص «التحديث إلى %s» فيلتف
   بالإصدارات الطويلة (`v0.1.5-rc.2`).
3. **صفوف القدرات تحت الوكيل** (Jobs, Kanban, Channels, Skills, Plugins, MCP, Memory,
   Journey, Settings لـ Hermes؛ أربعة لـ Ekko؛ Skills/MCP/Settings لوكلاء البرمجة مع
   Plugins/Presets لـ dsh) لم تعمل على iOS بسبب خلل تسجيل تنقّل SwiftUI. على أندرويد
   السلسلة هي `AgentScreen` → `viewModel.openAgentSection(agent, destination)` →
   فتّاح الشاشة؛ المطلوب التحقق بأن كل صف يصل إلى شاشته بالوكيل الصحيح، مع اختبار وحدة.

خارج النطاق: `clients/ios` (قُرئ فقط)، السيرفر، أي تصميم آخر للبطاقات.

## القرار والموافقات
- **(1)** حُذفت الورقة والزر والواجهة والنماذج وحقول الحالة (`loadingRuntimeVersions`،
  `runtimeVersions`) والدوال الأربع في `AppViewModel`، والمفاتيح `agent_manage_runtime`،
  `restart_webui`، `activate` (لم يعد يستخدمها شيء) من `values` و`values-ar`. بقيت «تفاصيل
  CLI» (`HermesCliDetailsDialog`: الإصدار والمسار والخطأ) وسطر الرأس في شاشة الوكيل
  (الإصدار · المصدر). طلب المالك عبر المنسّق؛ لا يحتاج موافقة إضافية.
- **(2)** الزر الآن `UpdateButton`: سطح مستدير بلون `info` بشفافية 0.16، نص «Update»/«تحديث»
  فقط (نفس كلمات iOS)، `maxLines = 1` و`softWrap = false`، في عمود بمحاذاة `Alignment.End`
  تحت حبّة الحالة (ينعكس تلقائيًا في RTL). الإصدار المعروض ينتقل إلى سطر البيانات:
  «Local CLI · v0.154.0 · v0.155.1 available» بمفتاح جديد `agent_update_offered`
  («%1$s متاح»). حُذف `agent_update_to`. زر `FlowRow` يبقى «إعادة التثبيت»/«تثبيت».
- **(3)** قراءة السلسلة أثبتت أن كل فرع في `openAgentSection` يستدعي فتّاحًا يضبط
  `screen = Screen.X` الصحيح (`openCronJobs → CronJobs`، `openKanban → Kanban`،
  `openChannels → Channels`، `openSkills(target) → Skills` مع `skillsUi.target`،
  `openPlugins → Plugins`، `openDshPlugins → DshPlugins`، `openDshPresets → DshPresets`،
  `openMcp(agentId) → Mcp` مع `mcpUi.agentId` (null لـ Hermes، معرّف الوكيل لوكيل البرمجة)،
  `openHermesMemory → Memory`، `openJourney → Journey`، `openHermesSettings → HermesSettings`،
  `openEkko(Memory/Skills/Mcp)`، `openEkkoSettings → EkkoSettings`،
  `openAgentSettings(definition) → AgentSettings` مع `openAgentSettings.agent`)، و`AppContent`
  في `MainActivity.kt:292-313` يرسم كل شاشة منها. **لم يُعثر على صف لا يصل**.
  لأن `AppViewModel` هو `AndroidViewModel` (يحتاج `Application` وRobolectric غير متاح
  offline)، استُخرج القرار إلى `navigation/AgentSectionRoute.resolve(definition, destination)`
  (شاشة + `skillsTarget` + `mcpAgentId`)، و`openAgentSection` يوزّع على `route.screen`.
  الاختبار الجديد `AgentSectionRouteTest` يمشي بكل صف لكل وكيل في الكتالوج (8 وكلاء)
  ويؤكد الشاشة، وهدف المهارات (`claude` لـ Claude Code وإلا المعرّف)، ومعرّف MCP، وأن
  كل شاشة ناتجة مسجّلة في `NavDestination.screens`، وأن الصف غير التابع للوكيل يُرفض.
  `NavigationStructureTest` يثبّت أن الـ ViewModel يستدعي المحلّل وأن كل فتّاح يضبط شاشته.
- الأثر الجانبي المقصود: تبسيط الترميز (`skillsTarget` في `AgentSectionRoute` بدل دالة
  خاصة في الـ ViewModel).

## الملفات والتأثير
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/navigation/AgentSectionRoute.kt`،
  `clients/android/app/src/test/java/us/i3u/hermesstudio/AgentSectionRouteTest.kt`.
- معدَّل: `ui/agents/AgentScreen.kt` (حذف `RuntimeManagerSheet`)، `ui/agents/AgentManagerScreen.kt`
  (زر التحديث، حذف إدارة وقت التشغيل)، `AppViewModel.kt` (`openAgentSection`، حذف حالة/دوال
  وقت التشغيل)، `HermesApi.kt`، `AgentTools.kt`، `res/values/strings.xml`، `res/values-ar/strings.xml`.
- اختبارات: `NavigationStructureTest.kt`، `HermesApiContractTest.kt` (حذف تثبيت
  `/api/hermes/runtime-versions`).
- توثيق: `clients/android/README.md`، `docs/mobile/NAVIGATION.md` §4 (سطر «إدارة وقت
  التشغيل» — لم يكن وكيل iOS قد عدّله عند الالتزام)، هذا السجل.

## الفحوص
```
JAVA_HOME=/home/twuijri/.local/opt/jdk17 ANDROID_HOME=/home/twuijri/Android/Sdk \
  gradle --offline testDebugUnitTest assembleDebug   # في clients/android
```
→ `BUILD SUCCESSFUL`؛ 432 اختبارًا، 0 فشل، 0 متخطّى (منها `AgentSectionRouteTest`: 5 اختبارات)
→ `app/build/outputs/apk/debug/app-debug.apk`
(محاولتان سابقتان في الجلسة نفسها: خطأ ترجمة نطاق `offered` ثم فشل تثبيتين قديمين في
`NavigationStructureTest`/`NavigationParityTest` على الفروع المحذوفة؛ أُصلحا وأُعيد التشغيل.)
لم يُنفَّذ: تشغيل على جهاز أو محاكي (لقطة شاشة للزر في RTL/LTR)، وفحوص iOS.

## المخاطر والرجوع
- لا تغيير في السيرفر أو البيانات. حذف واجهة `runtime-versions` من العميل فقط؛ السيرفر
  وسطح المكتب لا يتأثران.
- من يستخدم وقت تشغيل مُدارًا (`managed-runtime`) على سطح المكتب لن يجد زر التثبيت في
  الهاتف — مقصود.
- الرجوع: إلغاء دمج الفرع؛ لا حالة محفوظة تعتمد على الحقول المحذوفة.

## التسليم والخطوة التالية
الحالة: محلي على الفرع `fix/android-agent-rows`. الخطوة التالية: قرار المالك في الدفع وفتح
طلب الدمج إلى `mobile-staging`، ثم تجربة على جهاز مع سيرفر يعرض تحديثًا لوكيل برمجة.
