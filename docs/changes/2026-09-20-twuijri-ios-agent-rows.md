# iOS: صفوف Hermes لا تفتح، شارة التحديث الملتفّة، وإزالة «إدارة وقت التشغيل»

المسؤول: twuijri
الفرع: fix/ios-agent-rows (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف

ثلاثة عيوب وجدها صاحب المشروع في بناء iOS التجريبي 41 (TestFlight) على شاشات
مدير الوكلاء (لقطتان: قائمة مدير الوكلاء، وشاشة وكيل Hermes):

1. **صفوف قدرات Hermes (Jobs, Kanban, Channels, Skills, Plugins, MCP, Memory,
   Journey, Settings) لا تفعل شيئًا عند النقر.** على الشاشة نفسها، «Manage
   runtime» كان يفتح.
   - السبب الجذري (من الشيفرة): بطاقة مدير الوكلاء كانت رابط **وجهةِ عرض**
     (`NavigationLink { AgentDetailView(...) }`، `AgentManagerView.swift`
     الدالة `row`)، بينما صفوف القدرات روابط **بالقيمة**
     (`NavigationLink(value:)`، `AgentDetailView.swift`) تعتمد على
     `navigationDestination(for: NavDestination.self)` الوحيد في جذر
     `NavigationStack(path: $store.path)` (`RootShell.swift`).
   - قاعدة SwiftUI: المكدّس المربوط بمسار (`NavigationStack(path:)`) يحترم
     `NavigationLink(value:)` **فقط** من شاشة هي نفسها عنصر في المسار. الشاشة
     المدفوعة برابط وجهةِ عرض تقع **خارج** `store.path`؛ الرابط بالقيمة داخلها
     يُلحق القيمة بالمسار، لكن المكدّس لا يستطيع وضع الشاشة الجديدة فوق شاشة لا
     يتتبّعها، فتسقط النقرة. رابط وجهةِ عرض آخر من الشاشة نفسها («Manage
     runtime») يعمل لأنه يبقى خارج المسار أيضًا — وهذا بالضبط التباين الذي رآه
     صاحب المشروع.
   - ما فُحص واستُبعد: موضع المعدِّل صحيح (على `RootContentView` في جذر
     المكدّس، خارج أي `List`)؛ `.id(store.languageRefresh)` يعيد إنشاء المكدّس
     كاملًا ولا يكسر التسجيل؛ تبديل الوضع داخل `RootContentView` يقع تحت
     المعدِّل لا فوقه. صفوف «Tools» في الإعدادات تعمل لأن الإعدادات تُفتح عبر
     `store.show(.settings)` (مسار).
   - الدليل المقابل: الحالات `.agentHermes/.agentEkko/.agentCoding` عبر
     `AgentScreenLoader` كانت تصل إلى الشاشة نفسها **بالقيمة**، ومنها كانت
     الصفوف ستعمل — لكن لم يكن لها مدخل ظاهر؛ المدخل الوحيد هو البطاقة.
2. **شارة «Update vX» على بطاقات وكلاء البرمجة تُرسم كتلةً ملتفّة** على ثلاثة
   أسطر داخل دائرة (`StatusPill(text: "Update …")` داخل `HStack` سطر
   الإصدار).
3. **«Manage runtime» وشاشة حزم وقت التشغيل** فخّ على الهاتف: الخادم يعمل في
   Docker ووقت تشغيل Hermes جزء من الصورة؛ المثبّت لا معنى له إلا في تطبيق
   سطح المكتب.

خارج النطاق: `clients/android` لم يُمسّ. لا دفع ولا PR في هذه المهمة. لا Xcode
هنا؛ التجميع على macOS في CI بعد الدمج.

## القرار والموافقات

- (1) الآلية المختارة: **كل شاشة تحوي روابط بالقيمة تُفتح بالقيمة**، والسجل
  `NavDestination` يبقى مصدر الحقيقة الوحيد:
  - `AppStore.openAgent(_:)` يضبط `focusedAgentID` ثم `push(AgentFamily(...).destination)`؛
    `AgentFamily.destination` جديد (`.agentHermes/.agentEkko/.agentCoding`).
  - بطاقة مدير الوكلاء صارت `Button { store.openAgent(agent.id) }` (مع سهم
    `chevronForward` يعكس اتجاهه في RTL)؛ `AgentDetailView` لا يُنشأ إلا من
    `AgentScreenLoader`. التداخل (وكيل ← Kanban ← مهمة) يبقى دفعًا عاديًا لأن
    كل طبقة الآن في المسار.
  - مدير الوكلاء يعيد التحميل في `onAppear` (يعمل عند الرجوع من شاشة الوكيل)
    بدل `task`، لأن `reload` لم يعد يُمرَّر إلى الشاشة.
  - القاعدة موثّقة في ترويسة `RootShell.swift` و`AgentDetailView.swift`، ومثبّتة
    باختبارات تقرأ شجرة المصدر (`NavigationRulesTests`).
  - البديل المرفوض: تحويل روابط القيمة إلى روابط وجهةِ عرض تغلّف
    `ShellDestinationView` — كان سيُخرج كل الطبقات من المسار فيكسر
    `store.popToRoot`/`store.show`.
- (2) `AgentUpdateButton`: زر «Update» صغير مصبوغ (`.bordered`, `.small`, لون
  `info`)، نصّ فقط مع `lineLimit(1)` و`fixedSize`، تحت شارة الحالة في عمود
  `VStack(alignment: .trailing)` على الطرف النهائي؛ سطر الإصدار بقي كما هو
  (`Local CLI · v0.154.0`). النقر يشغّل `installCodingAgent` نفسه الذي يشغّله
  «Update to vX» في شاشة الوكيل، عبر `AgentInstallOutcome.note` المشترك. الإصدار
  المعروض يُقرأ عبر إمكانية الوصول («Update to vX»). الكلمة بالعربية: «تحديث».
  في RTL: `.trailing` يتبع اتجاه التخطيط، فيلتصق العمود بالحافة اليسرى وبجانبه
  السهم المعكوس، والصورة على الحافة اليمنى — المرآة الكاملة للصف.
- (3) حُذف صف «Manage runtime»، و`RuntimeVersionsView`، ونموذج
  `RuntimeVersionStatus`، ودوال API الخاصة بالمثبّت (`runtimeJobs`,
  `downloadVersion`, `activateVersion`, `deleteVersion`, `restartVersionedWebUI`).
  بقي **فحص** `GET /api/hermes/runtime-versions?remote=false` باسم
  `probeHermesRuntime()` لأن زر التحديث في مدير الوكلاء يعتمد على أثره الجانبي
  (تعبئة سجل حالة الوكلاء). كتلة «تفاصيل CLI» (الحالة، المصدر، الإصدار،
  المسار) بقيت. تذييل بطاقة Hermes صار يقول إن وقت التشغيل جزء من صورة الخادم
  ويُثبَّت/يُحدَّث على الخادم أو في سطح المكتب.
- ينتظر إذن صاحب المشروع: الدفع، PR، وبناء اختبار (`feedback-batch-test-builds`:
  لا بناء إلا بطلب).

## الملفات والتأثير

`clients/ios/HermesStudio/`:

- `Features/RootShell.swift` — القاعدة في الترويسة.
- `Core/AppStore.swift` — `openAgent(_:)`.
- `Core/ShellNavigation.swift` — `AgentFamily.destination`.
- `Features/Agents/AgentManagerView.swift` — البطاقة زر يدفع بالقيمة؛
  `AgentSummaryRow` بعمود نهائي (شارة + `AgentUpdateButton`)؛ `update(_:)`؛
  `onAppear`؛ `probeHermesRuntime`.
- `Features/Agents/AgentDetailView.swift` — حذف «Manage runtime»؛ تذييل Hermes؛
  `AgentInstallOutcome` المشترك؛ الترويسة.
- `Features/StudioToolViews.swift` — حذف `RuntimeVersionsView`.
- `Core/APIClient.swift` — `probeHermesRuntime()` بدل ست دوال.
- `Core/Models.swift` — حذف `RuntimeVersionStatus`.
- `Resources/{en,ar}.lproj/Localizable.strings` — حذف ١٣ مفتاحًا ميتًا
  (`Manage runtime`, `Runtime Versions`, `Update %@`, `Platform`, `Studio Web UI`,
  `Restart Studio`, `Hermes runtimes`, `Downloads`, `Activate`, `Download`,
  `Download started`, `Restart requested`, تذييل «installs complete runtime
  packages»)؛ إضافة `Update`/«تحديث» وتذييل Hermes الجديد في الملفين.

جديد: `clients/ios/HermesStudioTests/NavigationRulesTests.swift`.

وثائق: `clients/ios/README.md` (قسم Navigation والقاعدة، بطاقة Hermes، زر
Update، «ما زال ناقصًا»، قائمة الاختبارات)، `docs/mobile/NAVIGATION.md` §4
(سطر بطاقة Hermes)، هذا السجل.

## الفحوص

- لا Xcode ولا macOS هنا: **لم يُجمَّع المشروع ولم تُشغَّل XCTest**؛ التجميع
  والاختبارات على `mobile-test-track.yml` بعد الدمج.
- نُفّذ محليًا على الشجرة (بايثون):
  - توازن `{}` و`()` و`[]` لكل ملف Swift معدَّل أو جديد.
  - لا مراجع متبقّية إلى `RuntimeVersionsView` / `RuntimeVersionStatus` /
    `runtimeVersions(` / `runtimeJobs` / `Manage runtime` / `Update %@`.
  - ملفا اللغة: لا تكرار، مجموعتا المفاتيح متطابقتان باستثناء
    `CFBundleDisplayName`، ومحدِّدات التنسيق متطابقة (نفس فحوص
    `NavigationContractTests`).
  - محاكاة اختبارات شجرة المصدر في `NavigationRulesTests`: `AgentDetailView(`
    في `AgentScreens.swift` وحده؛ `NavigationLink(value:` في
    `AgentDetailView.swift` و`SettingsView.swift` وحدهما.
- لم تُنفَّذ فحوص المستودع الأخرى؛ التغيير محصور في عميل iOS ووثائقه.

## المخاطر والرجوع

- خطر تجميع لأن الشيفرة لم تُجمَّع (أجسام صغيرة، أنواع غير `private` حيث
  تُستعمل عبر الملفات).
- سلوك مقصود: فتح بطاقة يمرّ الآن بـ`AgentScreenLoader` (يعيد قراءة الجرد؛ مؤشر
  تحميل وجيز) بدل تمرير البيانات المحمّلة؛ زر داخل صف زر — `.bordered` يجعل
  النقر يبقى على الزر لا على البطاقة، وهذا مما يجب تأكيده على الجهاز.
- لا هجرة بيانات ولا تغيير في الخادم أو الويب أو أندرويد.
- الرجوع: الفرع مستقل؛ يُحذف أو تُعكس مراجعاته.

## التسليم والخطوة التالية

محليّ فقط على `fix/ios-agent-rows` (worktree). لا دفع ولا PR ولا بناء نشر.
يحتاج الجهاز: (أ) أن صفوف Hermes/Ekko/وكلاء البرمجة تفتح، وأن وكيل ← Kanban ←
مهمة يعمل، والرجوع يعيد إلى مدير الوكلاء؛ (ب) شكل زر Update على سطر واحد
بالإنجليزية والعربية (RTL) وأن النقر عليه يشغّل التحديث دون فتح البطاقة؛ (ج)
اختفاء «Manage runtime» مع بقاء تفاصيل CLI. الخطوة التالية: مراجعة صاحب
المشروع، ثم الدفع وPR إلى `mobile-staging` بإذنه، وبناء اختبار عند طلبه.
