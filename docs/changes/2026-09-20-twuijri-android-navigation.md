# أندرويد: إعادة بناء التنقّل على عقد `docs/mobile/NAVIGATION.md`

المسؤول: twuijri
الفرع: refactor/android-navigation (من `origin/mobile-staging`)
الحالة: review (محلي؛ لم يُدفَع ولم يُفتح طلب دمج)

## المشكلة والهدف
شكوى المالك: تطبيقا الجوال «يبدوان تطبيقين مختلفين»، و«أضغط شيئًا فيفتح شيء آخر
باسم مشابه». التدقيق القرائي على `origin/mobile-staging` أثبت العيوب التالية في
أندرويد (الملف:السطر عند بداية العمل):
- شاشتان بعنوان «الإعدادات» إحداهما داخل الأخرى (`SettingsDrawerScreen.kt` →
  `SettingsPageScreen.kt`).
- «Usage» و«Performance» يستدعيان `openInsights()` نفسه (`SettingsDrawerScreen.kt:53-54`).
- «Skills Usage» يفتح شاشة عنوانها «Journey»؛ «Theme» يفتح «Appearance»؛ «Pets» يفتح «Petdex».
- «Models» في القائمة و«Models» في تبويب الإعدادات شاشتان مختلفتان بلا تمييز.
- «Search» في القائمة يفتح السجل (`CoreHubDrawer.kt:283`).
- «Device connections» يفتح شاشة عنوانها «Connections» (`StudioWorkspaceScreens.kt:55`).
- «Agents» (`AgentRuntimes`) شاشة وكلاء ثانية بجانب «Agent Manager».
- `HermesToolsSection` (`MainActivity.kt:745-902`) كومة روابط تحت مدير الوكلاء.
- Profiles لها خمسة مداخل. فتح جلسة من السجل يبدّل المقطع إلى Chat (`CoreHubDrawer.kt:317`).
- كود ميت: `SettingsGroup.Server/Profile`، فروع `when` غير قابلة للوصول، مفاتيح
  `agent_hub_*`، `settings_group_about_note`، فروع `Screen.SettingsPage -> Screen.SettingsPage`
  المنسوخة (`AppViewModel.kt:4142, 4754, 5015`)، وجدول `back()` الثابت (`:5329-5341`).
- `NavigationStructureTest.everyAgentToolIsStillReachableFromTheAgentManager` يقطع على
  `"private fun AgentHubScreen"` غير الموجود فلا يفحص شيئًا.
- خلل API: الحيوانات تطلب `/api/hermes/petdex|pets` بينما السيرفر يخدم
  `/api/studio/petdex|pets` (`HermesApi.kt:2088-2131`).

الهدف: تطبيق العقد حرفيًا، بسجلّ وجهات واحد مشترك مع iOS. خارج النطاق: أي تعديل على
`clients/ios` (قُرئ فقط)، والسيرفر، وتصميم الشاشات نفسها (أُعيد توزيع المكوّنات القائمة).

## القرار والموافقات
- **السجلّ**: `navigation/NavDestination.kt` بالحالات الـ36 المتفق عليها مع وكيل iOS
  (`newChat … files`) وبمفتاح `nav_<snake_case>` لكل حالة؛ الرأس (rail)، والمقاطع،
  وأدوات الإعدادات، وأقسام كل وكيل قوائم داخل السجلّ، وتُرسم كل قائمة في مكان واحد.
- **الإعدادات شاشة واحدة** (`ui/settings/SettingsScreen.kt`): تبويبات الويب بترتيبها +
  «هذا الجهاز» + «حول» + قسم «الأدوات» بترتيب `AppSidebar.vue:113-317`. تبويب Models
  بعنوان فرعي «مفاتيح المزوّدين» ورابط «افتح صفحة النماذج» (الرابط الثانوي الوحيد
  المسموح، لأن الويب يملكه). Webhooks تبويب لا شاشة.
- **Usage/Performance/Skills Usage/Journey/Theme/Pets** شاشات مستقلة بعنوان مدخلها
  (`StudioInsightsScreens.kt`, `StudioParityScreens.kt`, `AgentToolScreens.kt`).
  Performance يعرض عمليات وقت التشغيل (العمّال، الجلسات حسب الملف) كما في
  `PerformanceView.vue`؛ وُسِّع `RuntimePerformance` لذلك.
- **البحث** ورقة فوق الجلسات (`ui/navigation/SessionSearchSheet.kt`) تستدعي
  `/api/studio/sessions/search?q=&limit=10` بعد 250ms، حقلها مركَّز عند الفتح، وتعرض
  آخر 8 جلسات عند فراغ الحقل؛ النتيجة تُفتح عبر `openSession` الذي يقرأ المصدر فتفتح
  نتيجة `global_agent` محادثة الوكيل العام.
- **اتصالات الأجهزة**: العنوان `nav_device_connections`، تبويب App (مباشر / دفع الرسائل)
  وDevices للمشرف الأعلى.
- **تحت الوكيل** (`ui/agents/AgentScreen.kt`): البطاقة هي المدخل الوحيد؛ الأقسام من
  `NavDestination.agentSections(kind)`. Hermes › Settings ثلاث تبويبات
  (`HermesScreens.kt`): Agent = `AgentSettings` (مع تشغيل البوابة التلقائي)، Memory =
  `MemoryStudioSettings`، Session = `SessionStudioSettings` (الموافقات وموافقات المهارات
  وإعادة الضبط) — كما وضّح المنسّق من `HermesSettingsView.vue:66-76`. Hermes › Memory
  متصفّح ذاكرة جديد على `GET/POST /api/hermes/memory` (كما في iOS). Ekko أربع شاشات
  (`EkkoScreens.kt`) من `EkkoHubScreen` القديمة، وإعداداتها على `GET/PUT /api/ekko/config`
  بتبويبات الويب الستة كأقسام JSON. وكيل البرمجة: Skills (بهدف الوكيل)، MCP على
  `/api/coding-agents/{id}/mcp/servers` (شاشة MCP نفسها مع `mcpUi.agentId`)، Settings
  (الشاشة القائمة). إصدارات وقت التشغيل ورقة على بطاقة Hermes («إدارة وقت التشغيل»)
  مع «تفاصيل CLI».
- **العودة**: `back()` يعتمد تاريخ الزيارة فقط ويسقط إلى جذر المقطع؛ حُذف
  `toolReturnScreen` و`profilesReturnScreen` والجدول الثابت.
- **Profiles** مدخل واحد (الإعدادات › الأدوات)؛ شريحة الملف في التذييل تبدّل فقط؛ Files من
  «تحرير الإعدادات» في ورقة بطاقة الملف (`openProfileConfig`) ويفتح `config.yaml` إن وُجد.
- **الوكيل العام** بلا مدخل قائمة: يصل إليه البحث، وشريط في `HomeShell` يظهر عند وجود
  جلسة `global_agent` غير مقروءة أو إجراء معلّق فيها (مقابل `GlobalPendingActions.vue`
  بما تملكه الهاتف من حالة).
- قرار غير منفَّذ (يحتاج نظر المالك): **Presets وPlugins لـ dsh** — السجلّ المشترك لا يحوي
  حالة لهما، وإضافة شاشة بلا وجهة تُسقط اختبار «لا كود ميت»؛ لذلك لم يُضافا لبطاقة dsh.
- قرار: صف «السمة» داخل تبويب Display حُذف لأنه كان مدخلًا ثانيًا لشاشة Theme.

## الملفات والتأثير
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/navigation/NavDestination.kt`،
  `ui/settings/SettingsScreen.kt`، `ui/navigation/SessionSearchSheet.kt`،
  `ui/agents/AgentScreen.kt`، `ui/agents/HermesScreens.kt`، `ui/agents/EkkoScreens.kt`،
  `StudioInsightsScreens.kt`، `test/.../NavigationParityTest.kt`.
- محذوف: `ui/navigation/SettingsDrawerScreen.kt`، `ui/settings/SettingsPageScreen.kt`،
  `AgentRuntimeScreen.kt`، `EkkoHubScreen.kt`، و`HermesToolsSection`/`InsightsScreen`/
  `SettingsGroupScreen`/`ServerSettings`/`ProfileSettings` من `MainActivity.kt`،
  وشاشتا Webhooks وRuntimeVersions من `StudioParityScreens.kt`، و105 مفاتيح نصوص يتيمة.
- معدَّل: `AppViewModel.kt` (Screen، UiState، back، الفتّاحات الجديدة)، `MainActivity.kt`
  (AppContent)، `HermesApi.kt` (الحيوانات، البحث، الذاكرة، إعدادات Ekko، MCP وكيل
  البرمجة، الأداء)، `AgentTools.kt`، `ui/navigation/CoreHubDrawer.kt`، `HomeShell.kt`،
  `ui/agents/AgentManagerScreen.kt`، `StudioWorkspaceScreens.kt`، `StudioParityScreens.kt`،
  `AgentToolScreens.kt`، `StudioSettings.kt`، `KanbanScreens.kt`، `CronJobs.kt`،
  `ui/groups/GroupsScreen.kt`، `ui/workflows/WorkflowsScreen.kt`، `ui/sessions/HistoryScreen.kt`،
  `ui/chat/ConversationScreen.kt`، `ui/models/ModelsScreen.kt`، `StudioOperationsScreens.kt`،
  `res/values/strings.xml`، `res/values-ar/strings.xml`.
- اختبارات: `NavigationStructureTest.kt` (أُعيدت كتابته على البنية الجديدة)،
  `DrawerParityTest.kt`، `DrawerSegmentBehaviourTest.kt`، `HermesApiContractTest.kt`
  (تثبيت مسارات الحيوانات الصحيحة + المسارات الجديدة).
- توثيق: `clients/android/README.md` (قسم التنقّل)، `docs/mobile/PLAN.md`، هذا السجل.

## الفحوص
```
JAVA_HOME=/home/twuijri/.local/opt/jdk17 ANDROID_HOME=/home/twuijri/Android/Sdk \
  gradle --offline testDebugUnitTest assembleDebug   # في clients/android
→ BUILD SUCCESSFUL؛ 426 اختبارًا، 0 فشل، 2 متخطّى
  (NavigationParityTest.bothPhonesDeclareTheSameDestinations وeveryNavLabelReadsTheSameOnBothPhones:
   ملف iOS `Core/NavDestination.swift` ومفاتيح `nav_*` لم تصل بعد؛ يعملان تلقائيًا عند وصولها)
→ app/build/outputs/apk/debug/app-debug.apk
```
لم يُنفَّذ: تشغيل على جهاز/محاكي حقيقي مع سيرفر (البحث، الذاكرة، إعدادات Ekko، MCP وكيل
البرمجة، الأداء بقيم حقيقية)؛ الفحوص تُثبت العقد على mock فقط. لم تُشغَّل فحوص iOS.

## المخاطر والرجوع
- لا تغيير في السيرفر أو البيانات. إصلاح مسارات الحيوانات يجعل شاشة Pets تعمل لأول مرة.
- `openSession` كان مسبوقًا بـ`showTab(Tab.Chat)`؛ الآن المقطع يبقى (History) والعودة
  من المحادثة تعيد إلى السجل — سلوك مقصود بالعقد.
- الرجوع: إلغاء دمج الفرع؛ لا ترحيل ولا حالة محفوظة تعتمد على الشاشات القديمة
  (`Screen` ليس مخزّنًا).

## التسليم والخطوة التالية
الحالة: محلي على الفرع `refactor/android-navigation` (لم يُدفَع بطلب المنسّق). الخطوة
التالية: وصول `clients/ios/HermesStudio/Core/NavDestination.swift` ومفاتيح `nav_*` في
`Localizable.strings` من وكيل iOS ثم تشغيل `NavigationParityTest` كاملًا؛ قرار المالك في
Presets/Plugins لـ dsh؛ ثم فتح طلب الدمج إلى `mobile-staging` بإذن المالك.
