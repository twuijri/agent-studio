# الجوال: وجهة `presets` لوكيل DeepSeek Harness على المنصتين

المسؤول: twuijri
الفرع: feat/dsh-presets-destination (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف

بعد توحيد التنقّل بقيت فجوة واحدة بين تطبيقي الجوال: `docs/mobile/NAVIGATION.md`
§٤ يطلب `Plugins · Presets` تحت وكيل البرمجة `dsh` وحده
(`CodingAgentConfigSidebar.vue:19-24`، وتعرضهما `CodingAgentConfigView.vue:139-140`).

- iOS كان يعرض `Presets` كرابط عرض مباشر (`NavigationLink { DshPresetsView() }`)
  خارج السجل، بعنوان من مفتاح عام `"Presets"` لا من `nav_*`، فلا تطابق بين
  المدخل والعنوان ولا بين المنصتين.
- أندرويد لم يعرض أيًّا منهما: السجل المشترك `NavDestination` بلا حالة `presets`،
  واختبار «لا كود ميت» (`NavigationParityTest`) يمنع شاشة بلا وجهة.

الهدف: حالة سجل واحدة `presets` على المنصتين بالترتيب نفسه (بعد `plugins`)،
بمفتاح `nav_presets` = `Presets`/«الإعدادات المسبقة» في ملفي اللغة على كلتيهما،
وشاشتان على أندرويد تعكسان سلوك سطح المكتب قراءةً/اختيارًا أولًا، مع ذكر ما
لا يستطيعه الهاتف على الشاشة نفسها لا إخفاءه.

## السلوك الحقيقي على سطح المكتب (المصدر)

- `packages/client/src/api/coding-agents/dsh.ts` والمسارات في
  `packages/server/src/modules/coding-agents/routes/agents.ts` (كلها للمشرف الأعلى):
  - Presets (`DshAgentPresetsPanel.vue`): `GET …/dsh/agent-presets` → `{presets[], authorable}`
    كل صف `{id, name?, description?, trust: system|user, isDefault, broken?}`؛
    `GET …/agent-presets/{id}` → `{content}`؛ `PUT …/agent-presets/{id}/default`
    يردّ بالقائمة كاملة (الخدمة `services/dsh/agent-presets.ts:60` ترفض المعطوب
    بـ422)؛ `POST` نسخ و`DELETE` حذف و`POST …/location` فتح المجلد على الخادم.
  - Plugins (`DshPluginsPanel.vue`): تبويب «Configuration» = iframe فوق جلسة
    واجهة (`POST …/dsh/ui-session`) لا يمكن للهاتف استضافته؛ تبويب «List» =
    `GET …/dsh/plugin-inventory` → إعدادات مسبقة بمدخلاتها
    (`configuredEnabled: true|false|"conditional"`) وحزم الويب مع
    `POST …/dsh/web-plugins` بترويسة `If-Match` للتثبيت/الإزالة.

## القرار

- **السجلان**: `presets(R.string.nav_presets, setOf(Screen.DshPresets))` بعد
  `plugins` في أندرويد، و`case … plugins, presets, mcp …` في iOS. `plugins` في
  أندرويد صار يغطّي `Screen.Plugins` و`Screen.DshPlugins` (كما تغطّي `skills`
  شاشتين) لأن الوجهة واحدة والشاشة تختلف بحسب الوكيل.
- **أندرويد**: `NavDestination.dshSections = [plugins, presets]` تُسبق أقسام
  وكيل البرمجة عندما يكون `agentId == DSH_AGENT_ID` (ثابت جديد في
  `AgentCatalog.kt`). `openAgentSection` يوجّه `plugins` إلى `openDshPlugins()`
  لـ dsh و`openPlugins()` لغيره، و`presets` إلى `openDshPresets()`.
  شاشتان جديدتان في `ui/agents/DshScreens.kt`:
  - Presets: القائمة، «الافتراضي» في العنوان الفرعي وشارة على البطاقة، «عرض»
    يفتح الملف للقراءة، «اجعله الافتراضي» لغير الافتراضي وغير المعطوب. النسخ
    والحذف وتحرير الملف مذكورة على الشاشة كأعمال سطح المكتب.
  - Plugins: الجرد بمجموعات لكل إعداد مسبق (مفعّل/معطّل/مشروط لكل مدخل)، ثم
    حزم الويب، ثم جملة صريحة أن تثبيت الحزم وصفحة إعدادات الإضافات على سطح
    المكتب.
  - `HermesApi`: `dshPluginInventory()`, `dshAgentPresets()`,
    `readDshAgentPreset()`, `setDefaultDshAgentPreset()` مع أصناف بيانات.
- **iOS**: حُذف الرابط المباشر؛ `capabilities(for: .coding("dsh"))` =
  `[.plugins, .presets, .skills, .mcp]` ويمرّ بـ`NavigationLink(value:)` ثم
  `ShellDestinationView` (ما زالت شاملة: `case .presets: DshPresetsView()`).
  عنوان الشاشة من `NavDestination.presets.title`. أُضيف زر ظاهر «Use as
  default» بجانب السحب، وتذييل يقول إن النسخ وتحرير الملف على سطح المكتب.
  الحذف الموجود (سحب) بقي كما كان.
- **العقد**: أُضيف `Presets`/«الإعدادات المسبقة» إلى جدول التسمية مع الإشارة
  إلى أنه تحت dsh وحده.

## الملفات

أندرويد: `navigation/NavDestination.kt`, `AgentCatalog.kt`, `AppViewModel.kt`
(الشاشتان في `Screen`, `dshUi`, دوال dsh, `openAgentSection`), `AgentTools.kt`
(`DshUiState`), `HermesApi.kt`, `MainActivity.kt`, `ui/agents/AgentScreen.kt`,
`ui/agents/DshScreens.kt` (جديد), `res/values{,-ar}/strings.xml`,
`tools/mock-studio.py` (المسارات الأربعة بالأشكال الحقيقية),
اختبارات `NavigationParityTest`, `NavigationStructureTest`, `HermesApiContractTest`,
و`clients/android/README.md`.

iOS: `Core/NavDestination.swift`, `Resources/{en,ar}.lproj/Localizable.strings`,
`Features/RootShell.swift`, `Features/Agents/AgentDetailView.swift`,
`Features/Agents/CodingAgentPanels.swift`,
`HermesStudioTests/NavigationContractTests.swift`, و`clients/ios/README.md`.

مشترك: `docs/mobile/NAVIGATION.md`.

## الفحوص

- أندرويد: `gradle --offline testDebugUnitTest assembleDebug` — النتيجة الفعلية
  مسجّلة في تقرير المهمة (يتضمن `NavigationParityTest` الذي يقرأ ملفات iOS
  فعليًا: تطابق الحالات، وتطابق `nav_*` إنجليزيًا وعربيًا).
- iOS: لا Xcode هنا؛ لم يُجمَّع. نُفّذ توازن `{}`/`()`/`[]` لكل ملف Swift
  معدَّل (صفر فرق). التجميع وXCTest على مسار CI الخاص بالجوال.

## القيود وما بقي

- على الهاتف: نسخ إعداد مسبق أو حذفه (أندرويد) وتحرير ملفه وفتح مجلده،
  وتثبيت/إزالة حزم ويب dsh، وصفحة إعدادات الإضافات (iframe): كلها سطح مكتب،
  وكل شاشة تقول ذلك.
- هذه المسارات للمشرف الأعلى على الخادم؛ غير المشرف يرى خطأ HTTP 403 كما هو،
  مثل بقية أدوات وكيل البرمجة.
- الخطوة التالية: مراجعة المالك، ثم الدمج في `mobile-staging` ضمن دفعة الجوال
  (لا بناء اختباري إلا بطلب المالك).
