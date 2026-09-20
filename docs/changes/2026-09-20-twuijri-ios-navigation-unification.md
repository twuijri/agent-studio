# iOS: توحيد التنقّل وفق عقد `docs/mobile/NAVIGATION.md`

المسؤول: twuijri
الفرع: refactor/ios-navigation (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف

شكوى صاحب المشروع: تطبيقا الجوال «يبدوان تطبيقين مختلفين»، و«أضغط شيئًا
فيفتح شيئًا آخر باسم مشابه». التدقيق القرائي على `origin/mobile-staging`
أثبت في iOS تحديدًا:

- ترس الدرج يبدّل جسم الدرج إلى «صفحة إعدادات» وسيطة (`SettingsDrawerView`)
  آخر صفوفها «الإعدادات» الحقيقية: إعدادات داخل إعدادات.
- `.usage` و`.performance` يفتحان الشاشة نفسها `InsightsView` (`RootShell.swift:118-130`).
- «Theme» يفتح شاشة عنوانها «Appearance»، ولها مدخل ثانٍ في الإعدادات ← العرض
  باسم «Theme and background»، وبجانبه منتقي محلي اسمه «Appearance» أيضًا.
- «Agent Manager» يفتح شاشة عنوانها «Agents».
- «Device connections» يفتح شاشة عنوانها «Connections» لها مدخل ثانٍ في
  الإعدادات ← أدوات مساحة العمل.
- قسما «متقدّم» و«أدوات مساحة العمل» في الإعدادات يرفعان إعدادات الوكيل
  وقدراته إلى مستوى التطبيق، فتظهر «Memory» مرتين (المتصفّح والإعدادات).
- «Search» يفتح السجل بلا تركيز على أي حقل (`SidebarDrawer.swift:76`).
- الملفات الشخصية لها مدخلان (شريحة التذييل بلا حراسة، والدرج للمشرف).
- `RuntimeVersionsView` له مدخلان. `ComingLaterView` كود ميت. ٢٢٥ مفتاحًا في
  `ar.lproj` بلا مقابل في `en.lproj`، ومفتاح `"Disabled"` مكرّر في العربية.
- خطأ API: الحيوانات تستعمل `/api/hermes/petdex/manifest` و`/api/hermes/pets/*`
  بينما الخادم يخدم `/api/studio/petdex/manifest` و`/api/studio/pets/*`
  (`APIClient.swift:593-600`؛ الخادم: `modules/studio/routes/{petdex,pets}.ts`).

الهدف: تطبيق العقد حرفيًا: سجل وجهات واحد (`NavDestination`) بمفاتيح
`nav_*` تقرأها التسمية والعنوان معًا، شاشة إعدادات واحدة، «تحت الوكيل»
مستوى قائم بذاته، صفحة النماذج بتبويباتها الخمسة، وورقة بحث حقيقية.

خارج النطاق: `clients/android` لم يُمسّ (قُرئ للمطابقة فقط). الويب والخادم
لم يتغيّرا. لا دفع ولا PR في هذه المهمة.

تصحيح من المنسّق أثناء التنفيذ (مؤكَّد في `HermesSettingsView.vue:66-76`):
تبويب `Agent` في إعدادات Hermes يعرض `AgentSettings` **و**`GatewayAutoStartSettings`؛
تبويب `Session` يعرض `SessionSettings` (الموافقات، موافقات المهارات، إعادة
الضبط). أي أن التشغيل التلقائي للبوابة تحت `Agent` لا تحت `Session`. طُبّق كذلك
(`HermesSettingsView.sections(for:)` واختباره).

## القرار والموافقات

- `Core/NavDestination.swift`: `enum NavDestination: String, CaseIterable`
  بالحالات الست والثلاثين المتفق عليها مع أندرويد، و`labelKey = nav_<snake_case>`
  موجود في ملفي اللغة بمصطلحات جدول العقد. `ShellDestination` حُذف، و
  `ConversationMode` صار إسقاطًا لحالات المقاطع الأربع.
- التنقّل: `NavigationStack(path:)` مربوط بـ`AppStore.path` (`NavigationPath`)
  مع `navigationDestination(for: NavDestination.self)` واحد في الجذر، فيمكن
  دفع أكثر من وجهة حيث يتداخل الويب (مدير الوكلاء ← Hermes ← كانبان ← مهمة).
  `store.show` يستبدل المسار، و`store.push` يضيف إليه. اختير `NavigationPath`
  لا مصفوفة مُنمَّطة لأنه يقبل روابط الوجهة المباشرة (`NavigationLink { … }`)
  الموجودة في الشاشات القديمة إلى جانب الروابط بالقيمة.
- الشاشات ذات الحمولة (`conversation`, `room`, `workflowDetail`, `workflowRun`,
  `agent*`, ومعها `skills`/`plugins`/`mcp`/`memory`/`*Settings` تحت الوكيل)
  تُحلّ من حالة المخزن (`selectedSession` وأخواتها، `selectedWorkflowRunID`،
  `focusedAgentID`) حتى تبقى `ShellDestinationView` شاملة على مستوى المترجم.
- الإعدادات: شاشة واحدة بتبويبات الويب + `This device` + `About` + قسم `Tools`.
  تبويب `Models` = مفاتيح المزوّدين فقط (`ModelSettings.vue`) مع رابط «افتح
  صفحة النماذج». المنتقي المحلي فاتح/داكن بقي في `Display` وفي شريحة التذييل
  باسم «Color scheme» لا «Theme».
- تحت الوكيل: `AgentDetailView` هو الأب الوحيد. Hermes: Jobs · Kanban ·
  Channels · Skills · Plugins · MCP · Memory · Journey · Settings (تبويبات
  Agent+gateway / Memory / Session+approvals) و«Manage runtime» على البطاقة
  وهو المدخل الوحيد لإصدارات وقت التشغيل. Ekko: Memory · Skills · MCP ·
  Settings (Runtime, Model, Compression, Tools, Modules, Advanced). وكيل
  البرمجة: [Plugins · Presets لـ dsh] · Skills (`/api/hermes/skills?target=`)
  · MCP · Settings (ملفا الإعدادات).
- صفحة النماذج: خمسة تبويبات؛ إعدادات الصوت الخادمية (STT/TTS) هنا لا في
  الإعدادات (الهاتف يختار المزوّد النشط؛ إدخال المفاتيح يبقى على سطح المكتب).
  خيارات الصوت الخاصة بالهاتف بقيت تحت `This device`.
- البحث: ورقة فوق الجلسات (`SessionSearchModal.vue`) بحقل مركَّز عبر
  `@FocusState`، آخر ٨ جلسات عند فراغ الحقل، ١٠ نتائج مع مقتطف، ونتيجة مصدرها
  `global_agent` تفتح محادثة الوكيل العام.
- ما لم يُطبَّق حرفيًا (مذكور للمراجع):
  - `Presets` لـ dsh ليس في سجل الوجهات المتفق عليه (٣٦ حالة ثابتة بين
    المنصتين)، فطُبّق كرابط داخل شاشة الوكيل لا كوجهة مسجّلة.
  - «شريط الإجراءات المعلّقة» (`GlobalPendingActions.vue`) غير موجود على
    الهاتف أصلًا؛ لذلك شاشة `GlobalAgentView` (قائمة الجلسات العامة وبدء جلسة)
    لها حالة `.globalAgent` وشاشة لكن لا مدخل ظاهر بعد. نتيجة البحث ذات المصدر
    `global_agent` تفتح المحادثة مباشرة كما يفعل الويب.
  - «عودة إلى Agent Manager» تتحقق بزر الرجوع في شريط التنقّل، لا بصفّ تذييل
    كما في الشريط الجانبي للويب.
  - تحرير النماذج المرجعية لمجموعة نماذج، وتثبيت حزم ويب لـ dsh، وإدخال مفاتيح
    مزوّدي الصوت: للقراءة/الاختيار فقط على الهاتف، والشاشة تقول ذلك.

## الملفات والتأثير

جديدة (`clients/ios/HermesStudio/`):

- `Core/NavDestination.swift` — السجل، `labelKey`، جدول التسمية، الترتيب.
- `Core/ModelsPageModels.swift` — `AuxiliaryModels`, `SttSettings`,
  `SttProviderCatalog`, `DshPreset`.
- `Features/Search/SessionSearchSheet.swift` — الورقة و`SessionSearchModel`.
- `Features/Insights/UsageView.swift`, `Features/Insights/PerformanceView.swift`.
- `Features/Connections/DeviceConnectionsView.swift` — التبويبات، و`TabStrip`
  و`QRImageView` (انتقلا إلى هنا).
- `Features/Settings/{DisplaySettingsView,ProviderKeysSettingsView,ThisDeviceSettingsView,StudioSectionSettings}.swift`.
- `Features/Agents/{AgentScreens,HermesSettingsView,EkkoSettingsView,CodingAgentPanels}.swift`.
- `Features/Models/ModelsPageTabs.swift`.
- `Features/Workflow/WorkflowRunScreen.swift`.
- `clients/ios/HermesStudioTests/NavigationContractTests.swift`,
  `clients/ios/HermesStudioTests/NavigationScreensTests.swift`.

معدّلة:

- `Core/AppStore.swift` — `path`, `shownDestination`, `focusedAgentID`,
  `searchOpen`, `selectedWorkflowRunID`; `show/push/popToRoot/openSearch`؛
  حذف `drawerPage`/`DrawerPage`.
- `Core/ShellNavigation.swift` — `ConversationMode` إسقاط، `AgentFamily`.
- `Core/Models.swift` — `PerformanceSnapshot` بدل `RuntimePerformance`.
- `Core/StudioModels.swift` — مُنشئ `SessionSearchResult(session:snippet:)`.
- `Core/APIClient.swift` — مسارات الحيوانات الثابتة، `performanceSnapshot`،
  `skills(profile:target:)` وأخواتها، `auxiliaryModels`/`moaConfig`/`sttSettings`
  وحفظها، مسارات dsh.
- `Features/RootShell.swift` — المكدّس الواحد، `RootContentView`، `ShellDestinationView` الشاملة.
- `Features/SidebarDrawer.swift` — الشريط من السجل، الترس يفتح الإعدادات،
  حذف «إدارة الملفات الشخصية»، شريحة «Color scheme».
- `Features/SettingsView.swift` — أُعيدت كتابتها بالكامل.
- `Features/Agents/AgentDetailView.swift` — القدرات ثم الإعدادات لكل عائلة.
- `Features/Agents/AgentManagerView.swift`, `HermesMemoryView.swift`,
  `Features/{ChatsView,KanbanView,CronJobsView,ChannelsView,CapabilitiesView}.swift`,
  `Features/Workflow/WorkflowsView.swift`, `Features/GroupChat/GroupRoomsView.swift` —
  العناوين من مفاتيح السجل؛ `SkillsView(target:)`.
- `Features/Models/ModelsHomeView.swift` — الغلاف بالتبويبات و`GeneralModelsPanel`.
- `Features/AgentHubView.swift` → `Features/StudioToolViews.swift` (إعادة تسمية)
  مع `ThemeView` بدل `ThemeStudioView` وحذف شاشة الاتصالات القديمة.
- `Resources/{en,ar}.lproj/Localizable.strings` — ٣٦ مفتاح `nav_*`، ١١١ نصًا
  جديدًا بالعربية والإنجليزية، ١٨٢ مفتاحًا كانت في العربية وحدها وتستعملها
  الشيفرة فأُضيفت إلى الإنجليزية، حذف ٤٣ مفتاحًا عربيًا لا يستعمله أحد، وحذف
  ٢٩ مفتاحًا أصبحت ميتة بهذا التغيير من الملفين، وإزالة تكرار `Disabled`.
  مجموعتا المفاتيح متطابقتان الآن باستثناء `CFBundleDisplayName`.
- `clients/ios/README.md` — قسم Navigation أُعيدت كتابته وفق العقد.
- `docs/mobile/PLAN.md` — مدخل «توحيد التنقّل».

محذوفة: `Features/SettingsDrawerView.swift` (ومعها `ComingLaterView`)،
`Features/InsightsView.swift`.

## الفحوص

- لا Xcode ولا macOS هنا: **لم يُجمَّع المشروع ولم تُشغَّل XCTest**. التجميع
  والاختبارات تقع على `mobile-test-track.yml` بعد الدمج.
- نُفّذ محليًا (سكربتات بايثون على الشجرة):
  - توازن الأقواس `{}` و`()` لكل ملف Swift معدَّل أو جديد: لا فرق.
  - لا أسماء أنواع مكرّرة على مستوى الوحدة، ولا نوع `private` مُشار إليه من
    ملف آخر.
  - لا مراجع متبقّية إلى `ShellDestination`/`drawerPage`/`InsightsView`/
    `ThemeStudioView`/`StudioConnectionsView`/`EkkoHubView`/`RuntimePerformance`.
  - ملفا اللغة: ١١٠١ مفتاحًا إنجليزيًا و١١٠٠ عربيًا، لا تكرار، الفرق الوحيد
    `CFBundleDisplayName`، ومحدِّدات التنسيق (`%@`, `%lld`) متطابقة لكل مفتاح.
  - كل `nav_*` موجود في الملفين بالمصطلحات الحرفية للعقد.
- لم تُنفَّذ فحوص المستودع الأخرى (`npm run harness:check` وغيرها) لأن التغيير
  محصور في عميل iOS ووثائقه.

## المخاطر والرجوع

- لا هجرة بيانات ولا تغيير في الخادم أو الويب.
- خطر تجميع لأن الشيفرة لم تُجمَّع؛ الأجسام قُسِّمت إلى دوال صغيرة، ولم
  يتجاوز أي `Section` عشرة عناصر.
- تغيير سلوكي مقصود: اختفاء المداخل الثانوية (قائمة الإعدادات الوسيطة،
  «متقدّم»، «أدوات مساحة العمل»، «إدارة الملفات الشخصية» من التذييل، «Provider
  sign-in» من الإعدادات — بقي في صفحة النماذج).
- الرجوع: الفرع مستقل؛ يُحذف أو تُعكس مراجعاته.

## التسليم والخطوة التالية

محليّ فقط على `refactor/ios-navigation` (worktree). لا دفع ولا PR ولا بناء
نشر. الخطوة التالية: مراجعة صاحب المشروع، ثم تجميع على macOS (أو الدمج إلى
`mobile-staging` لتشغيل `mobile-test-track.yml`)، ثم مقارنة الشكل مع فرع
أندرويد بالاختبار المتقاطع المذكور في العقد §٥.
