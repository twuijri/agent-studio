# صفحة النماذج ومدير الوكلاء والصور الرمزية في تطبيق أندرويد

المسؤول: twuijri
الفرع: feat/android-models-agents-avatars
الحالة: review

## المشكلة والهدف

ثلاث ملاحظات من صاحب المشروع على تطبيق Core Hub لأندرويد، مضمونها واحد:
«هذا لا يشبه Core Hub على الويب ولا يعمل مثله».

1. **شاشة النماذج تعرض الشيء الخطأ.** كانت تعرض النماذج بالطريقة التي تعرضها بها
   صفحة الإعدادات، أي حقول مفاتيح الواجهة، بينما المفروض أن تحاكي صفحة Models في
   Core Hub: المزوّدون، وتحت كل مزوّد النماذج التي يقدّمها. وسبب ذلك في الشيفرة
   مباشر: `SettingsGroup.Models` و«النماذج» في القائمة الجانبية كانا يفتحان
   `ModelProvidersSettings` نفسها، وهي مقابل `ModelSettings.vue` (تبويب الإعدادات)
   لا مقابل `ModelsView.vue` (الصفحة المستقلة).
2. **مدير الوكلاء يفتح صفحة إعدادات غريبة.** كان `AgentHubScreen` عمودًا من
   الروابط ولا يعرض وكيلًا واحدًا. والمنتظر: الوكلاء الموجودون، وهل لكل واحد
   إعدادات، وبالمستوى نفسه من الإتقان.
3. **الصور الرمزية قديمة.** التطبيق يحزم `multiavatar` ويرسم وجوهًا كرتونية، بينما
   انتقل Core Hub على الويب إلى `boring-avatars` بنمط `beam` في
   `ProfileAvatar.vue`، ببذرة اسم الملف الشخصي أو الوكيل، مع الرجوع إلى صورة
   مخزَّنة عند `avatar.type === 'image'`.

ثم وصلت ثلاث ملاحظات إضافية على شاشة الوكلاء (نظرها على iOS وقال إن أندرويد أسوأ):
بطاقة الوكيل لا تفتح شيئًا، وجانب Hermes (كانبان والمهارات والذاكرة) بلا مدخل
واضح، والقائمة تعرض أربعة وكلاء فقط دون بيان مصدر القائمة.

خارج النطاق: `clients/ios`، وملف `ui/navigation/CoreHubDrawer.kt` (يعيد كتابته
وكيل آخر على فرع مستقل؛ الشاشتان تُفتحان منه دون تعديله).

## القرار والموافقات

- **النماذج**: شاشة جديدة `ui/models/ModelsScreen.kt` مبنية على معمارية
  `ModelsView.vue` نفسها: تبويب المزوّدين (بطاقة لكل مزوّد: المعرّف، عنوان الخدمة،
  نمط الواجهة، حالة بيانات الدخول، حالة الفهرس، النموذج الافتراضي، النماذج كرقائق
  قابلة لإعادة التسمية، ثم الإجراءات) وتبويب السلسلة الاحتياطية. إدارة المفاتيح
  بقيت في «الإعدادات ← النماذج» كما هي على الويب تمامًا، فلا تكرار.
  ولأن القائمة الجانبية ممنوعة من التعديل، بقي `SettingsGroup.Models` هو المفتاح
  المختار، لكن `openSettingsGroup` صار يوجّه هذه المجموعة إلى `Screen.Models`.
- **مدير الوكلاء**: شاشة جديدة `ui/agents/AgentManagerScreen.kt` تعرض الوكلاء أولًا
  في ثلاثة أقسام (مدمج، منصّة Hermes، وكلاء البرمجة)، وتحتهم قسم «Hermes» الذي
  يحتفظ بكل روابط الأدوات التي كانت هي الصفحة كلها (كانبان، المهارات، الذاكرة،
  MCP، الإضافات، القنوات، السجلات…) لأن لا مدخل آخر لها في التطبيق.
- **مصدر قائمة الوكلاء**: القائمة صارت ثابتة في `AgentCatalog.kt`، منسوخة من مصدر
  الخادم (`AGENT_ORDER` في `agent-status-registry.ts` و`TOOL_DEFINITIONS` في
  `modules/coding-agents/services/index.ts`)، ثم تُدمج معها حالة الخادم من
  `/api/coding-agents` (متاح لأي مستخدم مسجَّل) و`/api/agents/status` (مشرف عام
  فقط) و`/api/coding-agents/update-policies`. الوكيل الذي لم يذكره الخادم يظهر
  «غير موجود على هذا الخادم» بدل أن يختفي.
- **إعدادات الوكيل**: شاشة `ui/agents/AgentSettingsScreen.kt` تفتح الملفين اللذين
  يحرّرهما `CodingAgentConfigView.vue` (ملف التوجيهات وملف الإعداد) عبر
  `/api/coding-agents/{id}/config-files/{key}`. محرّر الملف نص تقني: اتجاهه من
  اليسار إلى اليمين بخط أحادي المسافة مهما كانت لغة الواجهة.
- **الصور الرمزية**: `BoringAvatar.kt` نقل حرفي لنمط `beam` من
  `node_modules/boring-avatars-vanilla/dist/index.js`، بما في ذلك مسافات المخرجات،
  فتطابق نتيجة البذرة الواحدة ما يرسمه المتصفح. الصور المخزَّنة من نوع `image`
  تبقى كما هي. حُذف `multiavatar.json` وملف ترخيصه لأن لا شيء يشير إليهما بعد
  الآن، وحُدِّث ذكر الترخيص في README وفي نصَّي «عن التطبيق».
- **الصراحة في الواجهة**: بطاقة الوكيل تقول إن التثبيت والتحديث والإزالة تجري على
  خادم Core Hub لا على الهاتف، وإن فتح طرفية أصلية إجراء سطح مكتب غير متاح من
  الهاتف. الصفحة تنبّه أيضًا إذا رفض الخادم جرد الوكلاء الكامل (مشرف عام فقط).

موافقات: لم يُطلب أي إذن خارجي. لا دفع ولا PR ولا دمج ولا نشر في هذه المهمة.

## الملفات والتأثير

جديد:
- `clients/android/app/src/main/java/us/i3u/hermesstudio/BoringAvatar.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/AgentCatalog.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/models/ModelsScreen.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/agents/AgentManagerScreen.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/agents/AgentSettingsScreen.kt`
- `clients/android/app/src/test/java/us/i3u/hermesstudio/BoringAvatarTest.kt`
- `clients/android/app/src/test/java/us/i3u/hermesstudio/ModelsAndAgentsParseTest.kt`
- `clients/android/app/src/test/java/us/i3u/hermesstudio/MockStudioModelsAgentsTest.kt`
- `clients/android/app/src/test/resources/boring-avatars-beam.json`

معدَّل:
- `clients/android/app/src/main/java/us/i3u/hermesstudio/Avatars.kt` (حذف كائن
  `MultiAvatar` والاعتماد على `BoringAvatar`)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/AppViewModel.kt`
  (`Screen.Models` و`Screen.AgentSettings`، حالة الصفحتين، توجيه
  `openSettingsGroup(Models)`، إجراءات النماذج والسلسلة الاحتياطية والوكلاء
  وملفات إعداداتهم، صورة الحساب العشوائية)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/HermesApi.kt`
  (`/api/coding-agents` وتوابعه، `/api/agents/status`، سياسات التحديث، ملفات
  الإعداد، `fallback-providers`، `provider-models/cache/refresh`)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/MainActivity.kt`
  (توجيه الشاشتين الجديدتين، وتحويل `AgentHubScreen` إلى `HermesToolsSection`)
- `clients/android/app/src/androidTest/java/us/i3u/hermesstudio/AvatarTest.kt`
- `clients/android/app/src/main/res/values/strings.xml`
- `clients/android/app/src/main/res/values-ar/strings.xml`
- `clients/android/app/build.gradle.kts` (تعليق مكتبة androidsvg)
- `clients/android/tools/mock-studio.py`
- `clients/android/README.md`

محذوف:
- `clients/android/app/src/main/assets/multiavatar.json`
- `clients/android/app/src/main/assets/multiavatar-LICENSE.txt`

لم يُمَس: `clients/ios`، و`ui/navigation/CoreHubDrawer.kt`، وملفات الرخص في جذر
المستودع.

## الفحوص

البيئة: `JAVA_HOME=/home/twuijri/.local/opt/jdk17`،
`ANDROID_HOME=/home/twuijri/Android/Sdk`، Gradle 8.11.1، محليًا بلا شبكة
(`--offline`).

- `gradle --offline clean testDebugUnitTest assembleDebug` في `clients/android`:
  **BUILD SUCCESSFUL**. 39 صنف اختبار، 396 اختبارًا، صفر إخفاق.
- من ضمنها الاختبارات الجديدة: `BoringAvatarTest` (7)،
  `ModelsAndAgentsParseTest` (20)، `MockStudioModelsAgentsTest` (12).
- مطابقة الصور الرمزية: `app/src/test/resources/boring-avatars-beam.json` مولَّد
  بتشغيل `boring-avatars-vanilla` نفسها على 18 بذرة (عربية وإنجليزية وفارغة
  وبمسافات) بنداء `boring({ name, variant: 'beam', size: 144 })`، والاختبار يقارن
  النص كاملًا بعد توحيد معرّف القناع وحده. يفحص أيضًا أن العيّنات تغطي الفمين
  والشكلين ولوني الوجه.

لم يُنفَّذ:
- اختبارات `androidTest` (تحتاج جهازًا أو محاكيًا؛ لا جهاز في هذه الجلسة). عُدِّل
  `AvatarTest` ليقرأ من `BoringAvatar` بدل `MultiAvatar`، ولم يُشغَّل.
- لم تُلتقط لقطات شاشة، ولم يُثبَّت الـ APK على جهاز، فلا شهادة بصرية على الشاشتين
  الجديدتين بعد؛ التحقق الحالي بناء واختبارات وحدة فقط.
- لا شيء من هذا جرى على CI.

## المخاطر والرجوع

- **بيانات**: لا هجرة ولا تغيير مخطط. ذاكرة الصور على القرص مفتاحها بصمة تتضمن
  البذرة، فأول تشغيل بعد التحديث يعيد الرسم ويستبدل الوجوه القديمة تلقائيًا.
- **التوافق**: نداءات الوكلاء الثلاثة كل منها مستقل عن الآخر؛ رفض
  `/api/agents/status` بـ403 (غير المشرف العام) يترك القائمة كاملة مع تنبيه، لا
  شاشة فارغة. الخادم الأقدم الذي لا يعرف grok/opencode/dsh يُظهرها «غير موجودة على
  هذا الخادم».
- **الرجوع**: الفرع كله قابل للعكس بـ `git revert` لأنه لا يترك أثرًا خارج التطبيق.
  إعادة `multiavatar` تتطلب استرجاع الملفين المحذوفين مع ملف الترخيص.
- **الرخص**: لم يُمس `LICENSE` في الجذر ولا رخص الأطراف الثالثة ولا إسناد
  EKKOLearnAI. حُذف ترخيص Multiavatar لأن أصله حُذف معه، وأضيف إسناد
  `boring-avatars` (MIT) في README بدله.

## التسليم والخطوة التالية

الحالة الفعلية: الشيفرة مكتوبة ومبنية على الفرع المحلي
`feat/android-models-agents-avatars` المتفرع من `origin/mobile-staging`. **لم
يُدفع الفرع، ولم يُفتح PR، ولم يُدمج، ولم يُنشر أي شيء.**

الخطوة التالية لصاحب المشروع:
1. تشغيل الـ APK على جهاز والنظر في الشاشتين، خصوصًا الصورة الرمزية بجانب الصورة
   نفسها في المتصفح لنفس البذرة.
2. تحديد ما إذا كانت تبويبات النماذج غير المنقولة (المساعدة، التركيبية، STT/TTS)
   مطلوبة على الهاتف، وكذلك أقسام إعدادات الوكيل غير المنقولة (MCP، المهارات،
   إعدادات DeepSeek Harness). القائمة كاملة في قسم
   «Not ported from the web yet» في `clients/android/README.md`.
3. الإذن بالدفع وفتح PR إلى `mobile-staging` إن قُبل العمل.
