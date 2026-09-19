# iOS: شاشة النماذج، ومدير الوكلاء، والصور الرمزية الهندسية

المسؤول: twuijri
الفرع: feat/ios-models-agents-avatars (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف

ثلاث ملاحظات من صاحب المشروع وردت على أندرويد، وتنطبق فعلًا على iOS بعد
الفحص:

1. **شاشة النماذج نسخة ثانية من إدارة المفاتيح.** كان المتوقّع مزوّدي الخدمة
   وتحت كل مزوّد نماذجه، كما في `packages/client/src/views/hermes/ModelsView.vue`.
2. **مدير الوكلاء لا يعرض وكلاء صاحب المشروع.** ولاحقًا ثلاث ملاحظات أدق:
   لا يمكن فتح إعدادات أي وكيل، ولا مدخل إلى جانب Hermes (كانبان والمهارات
   والذاكرة)، ولا تظهر إلا أربعة وكلاء.
3. **الصور الرمزية ما زالت وجوه الكرتون** من `multiavatar.json`، بينما انتقل
   الويب إلى مولّد `boring-avatars-vanilla` بنمط `beam`.

الهدف: مطابقة ما يعرضه Core Hub نفسه، والاعتراف صراحةً بما لا يستطيع الهاتف
فعله بدل إخفائه.

خارج النطاق: `clients/android` لم يُمسّ. `Features/Chat/ChatComposer.swift`
و`Features/GroupChat/RoomComposer.swift` لم يُقتربا منهما (عمل متزامن على فرع
آخر). الويب والخادم لم يتغيّرا.

## ما وجدته بالفحص، لا بالافتراض

- **شاشة النماذج**: مدخل «Models» في الدرج كان يفتح `ModelsView` في
  `CapabilitiesView.swift` — قائمة نماذج مسطّحة برؤوس هي مفاتيح المزوّد الخام،
  وزر أول يقود إلى `ProvidersView` وهي فعلًا قائمة مفاتيح وحالة اتصال. أما
  `ModelCatalogView` فكانت تفتح على «النموذج الافتراضي» ثم قائمة مزوّدين مدخلها
  الوحيد الاعتماديات، والنماذج مدفونة مستويين تحت مربّعات الإظهار.
- **خلل في فك الترميز**: `ModelCatalog` كانت تدمج `groups` مع `allProviders`.
  والخادم (`modules/hermes/controllers/models.ts`) يرسل في `allProviders` كامل
  فهرس المزوّدين المعرّفين مسبقًا، لا المُعدّين لهذا الملف الشخصي. أي أن الهاتف
  كان يسرد كل مزوّد يعرفه Core Hub. والويب يستعمل `groups` للعرض ويصفّي `moa`.
- **مدير الوكلاء**: `AgentManagerView` كانت تحمل مصفوفة `descriptors` مكتوبة
  يدويًا بخمسة وكلاء، فلا تظهر Grok ولا OpenCode ولا DeepSeek Harness. وفوق
  ذلك `AgentIdentity.canonicalID` كانت تُرجِع `hermes` لكل معرّف غير معروف، أي
  أن الثلاثة كانوا يندمجون في صفّ Hermes حتى لو وصلوا من الخادم. الخادم يُرجع
  ثمانية وكلاء بترتيبه في `modules/studio/public/agent-status-registry.ts`،
  وستة أدوات في `GET /api/coding-agents`.
- **«جانب Hermes غير موجود»**: غير دقيق حرفيًا في معظمه — كانبان والمهارات
  و MCP والإضافات كلها موجودة تحت الإعدادات ← أدوات مساحة العمل، والناقص هو
  المدخل من شاشة الوكلاء وهو المكان الذي يضعها فيه الويب. لكن نقطة واحدة
  صحيحة تمامًا: **متصفّح ذاكرة Hermes** (`GET/POST /api/hermes/memory`، أي
  «ملاحظاتي» و«ملف المستخدم» و«الروح») لم يكن له وجود على الهاتف إطلاقًا.
  ما كان موجودًا هو *إعدادات* الذاكرة (قسم `memory` في إعدادات الملف الشخصي)
  وذاكرة إيكو (`/api/ekko/memory`)، وهما شيئان مختلفان، وصفّ الإعدادات كان
  مسمّى «Memory» فيوهم بأنه المتصفّح.
- **مهلة الطلب**: `URLSession` مضبوطة على ٦٠ ثانية، بينما تثبيت وكيل برمجة
  ينفّذ `npm install -g` على الخادم بمهلة عشر دقائق. أي أن زر «تثبيت» كان
  سيفشل على الهاتف قبل أن ينتهي الخادم.
- **أين iOS كان مصيبًا**: `Theme/AgentAvatar.swift` (شعارات أنواع التشغيل)
  مطابق لـ `chat-agent-avatar.ts` في الويب ولا علاقة له بـ multiavatar، فتُرك
  كما هو. وبنية `ModelCatalog` للأسماء البديلة وقواعد الإظهار والنماذج
  المخصّصة كانت صحيحة أصلًا. و`AvatarSpec` كان يقرأ `dataUrl` و`seed` بشكل
  سليم.

## القرار

**النماذج.** شاشة جديدة `Features/Models/ModelsHomeView.swift`: قسم لكل مزوّد
مُعدّ، في رأسه الاسم وشارات «الافتراضي / مدمج / مخصص» ونقطة المفتاح، وفي ذيله
مفتاح المزوّد وعنوان الأساس وحالة فهرس opencode-free، وفي جسمه قائمة «النماذج
N» قابلة للطي (أو `N/الإجمالي` عند وجود قاعدة إظهار، مثل `visibleCountLabel`)
ورابط «إعدادات المزوّد». الضغط على نموذج يجعله الافتراضي لهذا المزوّد. أما
الاعتماديات وقواعد الإظهار والنماذج المخصّصة واختبار الاتصال وتحديث/استعادة
قائمة النماذج فانتقلت إلى `ProviderCatalogView`، وهو ما يفعله الويب خلف زر
التعديل. والإجراء الهدّام صار يقول ما يفعله فعلًا: «حذف هذا المزوّد» لمزوّد
مصدره الإعدادات، و«مسح المفتاح المخزَّن» لمزوّد مدمج.

**الوكلاء.** `Features/Agents/AgentManagerView.swift` يعرض ما يُرجعه
`GET /api/agents/status` بترتيب الخادم ويجمّعه بـ `kind` الذي يرسله الخادم، مع
قسم «وكلاء آخرون» لأي `kind` لا تعرفه هذه النسخة. المعرّف يُحفظ كما هو. ولأن
هذا المسار يتطلب صلاحية مدير عام، فعند رفضه تُعرض وكلاء البرمجة الستة من
`GET /api/coding-agents` مع سطر يقول إن القائمة ناقصة ولماذا — لا قائمة قصيرة
تُقدَّم وكأنها كاملة.

`Features/Agents/AgentDetailView.swift` يفتح لكل وكيل: الحالة والمصدر والإصدار
والأمر والحزمة والمسار والخطأ وكل `installations`، وسياسة التحديث التلقائي مع
مفتاحها، وأفعال التثبيت/التحديث/التحقق/الإزالة، وملفّا الإعداد اللذان تحرّرهما
صفحة الويب، وخوادم MCP. ولإيكو روابط إعداده وذاكرته ومهاراته و MCP، ولـ Hermes
روابط إصدارات بيئة التشغيل ثم مساحة عمل Hermes كاملة — وهذه هي إجابة الملاحظة
الثانية.

**الصور الرمزية.** `Theme/BoringAvatar.swift` نقلٌ حرفيّ لخوارزمية
`boring-avatars-vanilla` بنمط `beam` ولوحتها الافتراضية، بالبذرة نفسها التي
يستعملها الويب (`avatar.seed` ثم الاسم ثم `default`). النقل حرفي لا تقريبي:
`hashCode` يمرّ على **وحدات UTF-16** (الإيموجي وحدتان)، والحساب يلتفّ عند ٣٢
بت، و`Math.abs(Int32.min)` يتّسع إلى ٢١٤٧٤٨٣٦٤٨، و`getUnit` لا ينفي عندما يكون
`index` صفرًا لأن جافاسكربت تعدّ الصفر قيمة كاذبة. والرسم بـ `Canvas` في فضاء
٣٦×٣٦ نفسه، مع قواعد قصّ SVG التي يعتمد عليها الترميز (`rx="36"` على مستطيل
عرضه ٣٦ = دائرة، و`rx="1"` على عين ١٫٥×٢ = قطع ناقص كامل، وأنصاف أقطار الفم
المغلق `a1,0.75` تتوسّع إلى ٥×٣٫٧٥ لوتر طوله ١٠). فلا حاجة لمحرّك SVG.

## الملفات والتأثير

جديدة:

- `clients/ios/HermesStudio/Theme/BoringAvatar.swift`: المولّد و`BeamAvatarView`.
- `clients/ios/HermesStudio/Features/Models/ModelsHomeView.swift`: شاشة النماذج.
- `clients/ios/HermesStudio/Features/Agents/AgentManagerView.swift`: قائمة الوكلاء.
- `clients/ios/HermesStudio/Features/Agents/AgentDetailView.swift`: شاشة الوكيل.
- `clients/ios/HermesStudio/Features/Agents/AgentConfigFileView.swift`: محرّرا
  ملفّي الإعداد وقائمة خوادم MCP.
- `clients/ios/HermesStudio/Features/Agents/HermesMemoryView.swift`: متصفّح
  ذاكرة Hermes الثلاثي (`§` تُعاد إلى سطرين فارغين كما في الويب).
- `clients/ios/HermesStudioTests/BoringAvatarTests.swift`
- `clients/ios/HermesStudioTests/AgentsAndModelsTests.swift`

معدّلة:

- `Core/Models.swift`: `AgentIdentity` صار يغطّي الثمانية مع `knownIDs`
  و`fallbackName`؛ `AgentRuntimeStatus` يحفظ المعرّف كما هو ويقرأ `name`
  و`provider` و`kind` و`installations`؛ أنواع جديدة `AgentInstallation`
  و`AgentUpdatePolicy` و`CodingAgentConfigFile` و`CodingAgentMcpServer`
  (الأخير يعامل `NSNull` القادم من JSON `null` على أنه «لا خطأ»)؛
  `CodingAgentTool` يحفظ معرّفه كما هو.
- `Core/StudioModels.swift`: فصل `groups` عن `presets`، تصفية `moa`، حقول
  بطاقة الويب، و`modelCountLabel` و`isDefault`.
- `Core/APIClient.swift`: مهلة لكل طلب و`codingAgentInstallTimeout`،
  و`agentUpdatePolicies` و`setAgentAutoUpdate` و`codingAgentConfigFile`
  و`saveCodingAgentConfigFile` و`codingAgentMcpServers` و`testCodingAgentMcpServer`
  و`removeCodingAgentMcpServer` و`restoreProviderModels`؛ `installCodingAgent`
  صار يُرجع `success` لأن فشل npm يأتي بحالة 200.
- `Core/Branding.swift`: حذف `MultiAvatar` ومحرّك لقطة WebKit؛ الذاكرة المؤقتة
  صارت للصور المرفوعة فقط.
- `Theme/HermesTheme.swift`: `ProfileAvatar` يرسم `BeamAvatarView` ويحتفظ
  بالصورة المرفوعة.
- `Features/Settings/ModelCatalogView.swift`: بقي محرّر المزوّد وحده، مع قسم
  «قائمة النماذج» (اختبار/تحديث/استعادة) وتفريق الإجراء الهدّام.
- `Features/CapabilitiesView.swift`: حذف `ModelsView`، وتحويل `ProvidersView`
  إلى `ProviderSignInView` (تسجيل الدخول بالمتصفح فقط).
- `Features/AgentHubView.swift`: حذف `AgentManagerView` القديمة و`AgentDescriptor`.
- `Features/RootShell.swift` و`Features/SettingsView.swift`: توجيه المداخل،
  وصفّ «Memory» صار يفتح المتصفّح، وأُعيدت تسمية صفّ الإعدادات إلى
  «Memory settings».
- `Resources/{en,ar}.lproj/Localizable.strings`: ٨٤ مفتاحًا جديدًا بالإنجليزية
  و٨١ بالعربية (الباقي كان موجودًا في العربية أصلًا).
- `clients/ios/README.md`: قسم «Models and Agents (M5)» ووصف الصور الرمزية.

محذوفة: `Resources/multiavatar.json` و`Resources/multiavatar-LICENSE.txt` —
بعد التأكد أن لا شيء في المستودع يشير إليهما (المرجع الوحيد كان
`Branding.swift`، والملف الثاني كان يشير إلى نفسه).

## الفحوص

- مطابقة المولّد: نُفّذ `boring-avatars-vanilla` الحقيقي من
  `node_modules/` على ١٧ بذرة، واستُخرجت القيم من **SVG الذي أرجعته المكتبة
  نفسها** (لون الخلفية، و`transform` و`rx` للغلاف، و`transform` للوجه، و`x`
  للعين الأولى، ووجود `stroke-linecap` في الفم). ثم كُتب نقلٌ بايثوني حرفيّ
  للملف السويفتي وقُورن بالبذور السبع عشرة: تطابق تام، بما فيها بذرة فارغة،
  وعربية، وإيموجي (زوج بدائل UTF-16)، وحالة `faceTranslateX = 3.5` الكسرية.
  الفحص نصّيّ ومنطقيّ، لا بصريّ.
- **لم يُبنَ المشروع ولم تُشغَّل اختبارات XCTest**: لا Xcode ولا macOS في هذه
  البيئة. التجميع يقع على CI بعد الدمج. ما نُفّذ هنا: فحص توازن الأقواس لكل
  ملف معدّل مقارنةً بحالته قبل التعديل (لا فرق)، وفحص ملفّي اللغة (لا مفاتيح
  مكرّرة جديدة، وتطابق محدِّدات التنسيق `%@` و`%lld` بين الإنجليزية والعربية).
- لم تُنفَّذ فحوص المستودع الأخرى (`npm run harness:check` وغيرها) لأن التغيير
  محصور في عميل iOS.

## المخاطر والرجوع

- لا هجرة بيانات ولا تغيير في الخادم ولا في الويب.
- تغيير مرئي مقصود: كل صورة رمزية مولَّدة تتغيّر شكلًا (من وجه كرتوني إلى شكل
  `beam`)، وهذا هو المطلوب. الصور المرفوعة لا تتأثر. بقايا الذاكرة المؤقتة
  القديمة على القرص لم تعد تُقرأ للصور المولَّدة، فلا تظهر وجوه قديمة.
- خطر تجميع: الشيفرة لم تُجمَّع. أقسام الواجهة قُسِّمت إلى دوال صغيرة ولم
  يتجاوز أي `Section` عشرة عناصر، تفاديًا لانتهاء مهلة مدقّق الأنواع.
- الرجوع: الفرع مستقل، والعودة بحذفه أو بعكس المراجعة.

## ما يحتاج جهازًا

- التحقق البصري من شكل `beam` مقابل المتصفح لنفس البذرة (الاختبارات تضمن
  الأرقام، لا البكسل).
- تثبيت وكيل برمجة فعليًا للتأكد من أن مهلة الإحدى عشرة دقيقة تكفي، ومن أن
  فشل npm يظهر كنص لا كنجاح.
- سلوك شاشة الوكلاء بحساب ليس مديرًا عامًا (سطر النقص).
- اتجاه النص RTL في أقسام المزوّدين وشاشة الوكيل على جهاز عربي.

## التسليم والخطوة التالية

محليّ فقط على `feat/ios-models-agents-avatars`؛ لا دفع ولا PR ولا بناء نشر.
الخطوة التالية: مراجعة صاحب المشروع، ثم بناء وتشغيل على جهاز، ثم قرار الدفع
وفق `docs/TEAM-RULES.md`.
