# أندرويد: وضع التسجيل في كاتب الرسالة (شريط ✕ · موجة · ■ · ↑)

المسؤول: twuijri
الفرع: feat/android-recording-strip (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف
أراد المالك أن يعمل الإملاء في كاتب الرسالة كما في تطبيق Claude (لقطة الشاشة
المرجعية التي أرفقها): عند الضغط على الميكروفون يُستبدل صفّ الأزرار الحبّية تحت
النص (+ · Default · Settings · Model · مايك · إرسال) بشريط تسجيل: ✕ دائري في
البداية، موجة صوتية حيّة بمستويات حقيقية تملأ الوسط، ■ إيقاف دائري، ثم ↑ إرسال
الدائري. يستمر النص المُملى بالظهور في الحقل أعلاه ويبقى الحقل قابلًا للتحرير.

قبل هذا التغيير كان الإملاء يعرض سطر «جارٍ الاستماع…» مع زر «إلغاء» نصّي فوق
الحقل، ويتحول زر المايك إلى ■؛ لا موجة، ولا ↑ أثناء التسجيل، ولا استعادة للمسودة
عند الإلغاء سوى حذف المقطع المُدرج.

السلوك المطلوب (مطابق للآيفون الذي نُفّذ بالتوازي):
- ✕ = إلغاء: يوقف التعرّف ويعيد المسودة كما كانت قبل بدء التسجيل.
- ■ = إيقاف: ينهي التسجيل ويُبقي النص، ويعود صفّ الأزرار.
- ↑ أثناء التسجيل = إيقاف ثم إرسال بعد وصول النص النهائي من المحرّك.
- الضغط المطوّل لاختيار اللغة يبقى على المايك عندما لا يكون التسجيل جاريًا، ويبقى
  التلميح العرضي وسطر «Listening in …» كما هما.
- RTL: يتعاكس الشريط (✕ في البداية في الاتجاهين) والموجة نفسها خطّ زمني مثبَّت LTR.
- إتاحة الوصول: للموجة وصف يذكر لغة الإملاء والمستوى؛ TalkBack يجد الأزرار الثلاثة نفسها.

خارج النطاق: `clients/ios` (ينفّذه وكيل آخر بالتوازي وبنفس الأسماء والنصوص)،
`ui/agents/*` ونصوص الوكلاء (يحرّرها وكيل أندرويد آخر على فرع مختلف)، وأي تغيير
في السيرفر أو في منطق اختيار لغة الإملاء.

## القرار والموافقات
- **لا مصدر صوت ثانٍ.** مستويات الموجة تأتي من `RecognitionListener.onRmsChanged`
  الذي كان `SpeechInput` يتجاهله (دسيبل تقريبًا −2…10 على محرّك Google، تُطبَّع إلى
  0…1)، ومن عيّنات PCM التي يلتقطها `Recorder` أصلًا في حالة تسجيل السيرفر
  (RMS بالـ dBFS، −50…−10). لا يُفتح المايكروفون مرة ثانية.
- **الأرقام والأسماء مشتركة مع الآيفون** بطلب المنسّق بعد هبوط النصف الآخر:
  `AudioLevelMeter` (هجوم فوري، تحرير أسّي 0.8 لكل نبضة 50 ms، عتبة الخمول 0.06)،
  `RecordingStrip` (قواعد الحالة)، `RecordingWaveform` (36 عمودًا، الأحدث أخيرًا،
  الأعمدة الخاملة نقطة بشفافية 40 %). القيم في `CoreHubTokens` (`Metrics.waveformBars`
  و`waveformTickMs` و`waveformBar/Gap/Dot` و`Alpha.WAVEFORM_IDLE`؛ الاسم بصيغة
  أندرويد الكبيرة لأن ثوابت `Alpha` كلها كذلك في هذا الملف). لا ألوان حرفية.
- **قواعد الشريط نقية** (`RecordingStrip.kt`): مُخفِّض حالات
  idle → recording → finishing يعيد تأثيرات (`StartTake`، `StopTake`، `CancelTake`،
  `RestoreDraft`، `SendDraft`) ينفّذها الكاتب. الإرسال بـ ↑ يُؤجَّل حتى يصل النص
  النهائي إلى المسودة (`Event.Finished`) لأن المحرّك يجيب بعد الضغط لا أثناءه، ولا
  يُرسَل شيء إذا انتهى التسجيل بلا نص ومرفقات. تسجيلٌ أنهاه المحرّك بنفسه (صمت
  أو خطأ) يُبقي نصّه ولا يرسل.
- **الإلغاء يعيد المسودة السابقة حرفيًا** (`draftBeforeTake`) كما نصّ الطلب، وليس
  مجرد حذف المقطع. الاختبار يثبت أن الطريقتين تتفقان بقواعد الفواصل نفسها التي
  يستعملها دمج الإملاء (`applyVoiceSegment`): المسافة التي يضيفها الدمج قبل المقطع
  تختفي مع الإلغاء. قيد مقصود: ما يكتبه المستخدم يدويًا أثناء التسجيل يضيع عند ✕،
  لأن ✕ معناه «انسَ هذا التسجيل كله»؛ ■ يحفظ كل شيء.
- **عرض الشريط يتبع حالة الصوت الحقيقية** (`state.voice == Listening`) لا حالة
  المخفِّض، حتى لا يظهر شريط لتسجيل لم يبدأ (رفض الإذن، فشل المسجّل). ولضمان أن كل
  فشل يُرى كانتقال حالة، يُستدعى `resetVoice()` قبل كل بداية.
- **المستويات على `StateFlow` مستقل** (`AppViewModel.dictationLevels`) لا داخل
  `UiState`، كي لا تُعاد تركيبة الكاتب كله 20 مرة في الثانية.
- **الحركة**: `AnimatedContent` بتلاشي `tween(transitionFastMs = 150)` — تويين
  القائمة الجانبية نفسه — ويُستبدل بـ `snap()` عندما يكون
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (إيقاف الحركة من إعدادات النظام أو
  إتاحة الوصول)، لأن Compose لا يقرأ هذا الإعداد بنفسه.
- **زر «إلغاء» النصّي في سطر الحالة أُزيل** أثناء الاستماع لأن ✕ في الشريط يقوم
  مقامه؛ سطر «Listening in …» وحالات التفريغ والخطأ بقيت كما هي.
- **قرار يحتاج نظر المالك**: دائرتا ✕ و■ بتعبئة `bgCard` كما في الطلب، وأضفتُ حدًّا
  1 dp بلون `borderLight` لأن `bgCard` في الوضع الفاتح أبيض مثل بطاقة الكاتب فلا
  تُرى الدائرة بدونه. إن أراد المالك التعبئة وحدها تُحذف سطرا `border`.
- نصوص الشريط بالإنجليزية والعربية بصياغة الآيفون: «Cancel dictation / إلغاء
  الإملاء»، «Stop dictation / إيقاف الإملاء» مع تلميحيهما، «Recording / جارٍ
  التسجيل»، «Recording in %s / جارٍ التسجيل بـ %s»، «Level %d%% / المستوى %d٪».
  ↑ يستعمل نص «إرسال» الموجود. التلميحان يُقرآن في TalkBack عبر `onClickLabel`.

## الملفات والتأثير
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/DictationLevels.kt`
  (التطبيع، `AudioLevelMeter`، تاريخ الأعمدة، قياس PCM)
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/RecordingStrip.kt`
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/chat/RecordingStripRow.kt`
  (الشريط، `RecordingWaveform`، `rememberReducedMotion`)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/SpeechInput.kt`
  (`Listener.onRms` من `onRmsChanged`)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/Recorder.kt` (`onLevel` من عيّنات PCM)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/AppViewModel.kt`
  (`dictationLevels`، نبّاض 50 ms يبدأ مع كل تسجيل ويتوقف عند الإيقاف/الإلغاء/النهاية/الخطأ)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/chat/Composer.kt`
  (تبديل الصف ↔ الشريط، تنفيذ تأثيرات المخفِّض، الإرسال بعد التسجيل، إزالة زر الإلغاء النصّي)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubTokens.kt`
- `clients/android/app/src/main/res/values/strings.xml`، `values-ar/strings.xml` (7 نصوص)
- اختبارات جديدة: `DictationLevelsTest.kt` (8)، `RecordingStripTest.kt` (10)،
  `RecordingStripWiringTest.kt` (6)
- توثيق: `clients/android/README.md` (وصف الوضع، وخريطة الملفات)
- لم يُمسّ: `clients/ios/*`، `ui/agents/*`، `RoomScreen.kt` (يستفيد تلقائيًا لأن
  الغرفة تستعمل `StudioComposer` نفسه)

## الفحوص
البيئة: Linux، JDK 17 (`/home/twuijri/.local/opt/jdk17`)، Android SDK
(`/home/twuijri/Android/Sdk`)، Gradle 8.11.1 دون شبكة، في worktree مستقل.

```
JAVA_HOME=/home/twuijri/.local/opt/jdk17 ANDROID_HOME=/home/twuijri/Android/Sdk \
  gradle --offline testDebugUnitTest assembleDebug
```
- التشغيل الأول: `assembleDebug` نجح؛ `testDebugUnitTest` 450 اختبارًا وفشل واحد في
  اختباري الجديد (`DictationLevelsTest` سطر 47): كان يتوقع `before × decay` في
  الإطار الذي يهبط فيه العمود تحت عتبة الخمول إلى الصفر مباشرة، وهذا هو السلوك
  المقصود («يستقر على نقطة»)، فصُحِّح مرشّح الاختبار لا الكود.
- بعد مواءمة الأسماء والأرقام مع الآيفون: **BUILD SUCCESSFUL in 23s**؛
  `testDebugUnitTest` **451 اختبارًا، 0 فشل، 0 خطأ** (427 قبل التغيير + 24 جديدًا)؛
  `DictationReportingTest` القديم (6) ما زال ينجح؛ `assembleDebug` أنتج
  `app/build/outputs/apk/debug/app-debug.apk`.
- لم يُنفَّذ: `npm run harness:check` و`npm run test:personal` لأن التغيير محصور في
  عميل أندرويد ولا يمسّ السيرفر أو الويب (مصفوفة الفحوص تطلبهما قبل PR؛ يُشغَّلان
  عند الدفع). لا اختبارات Compose UI في هذا المستودع.

## المخاطر والرجوع
- لا بيانات ولا API متأثرة؛ التغيير واجهة فقط. الرجوع = التراجع عن الـ commit.
- **يحتاج جهازًا حقيقيًا** (لا يوجد هنا جهاز موصول ولا AVD هاتف): مدى قيم
  `onRmsChanged` على محرّك الجهاز الفعلي (المدى −2…10 مأخوذ من محرّك Google؛ محرّك
  آخر قد يحتاج ضبط `MIN_DB/MAX_DB`)، سلاسة الموجة عند 20 Hz، التلاشي 150 ms وسلوك
  الإيقاف مع إيقاف الحركة، الإملاء بالعربية في واجهة عربية (اتجاه الشريط وتثبيت
  الموجة LTR)، قراءة TalkBack للأزرار والوصف، والإرسال بـ ↑ من الغرفة الجماعية.
- الأرقام على `RecordingStripWiringTest` تفحص المصدر نصيًا (كما `DictationReportingTest`)؛
  تغيير الصياغة داخل الكاتب يكسرها عمدًا ليُراجع السلوك.
- احتمال تعارض بسيط في `CoreHubTokens.kt` و`strings.xml` مع فرع الوكلاء المتوازي؛
  الإضافات هنا في مواضع مستقلة.

## التسليم والخطوة التالية
الحالة الفعلية: مكتمل محليًا على الفرع `feat/android-recording-strip` في worktree،
commit واحد، **لم يُدفَع** ولم يُفتح طلب دمج (بحسب التعليمات). الخطوة التالية للمالك:
مراجعة القرارين المعلَّمين أعلاه (حدّ الدائرتين، وضياع التحرير اليدوي عند ✕)،
ثم الدفع وفتح PR إلى `mobile-staging`/`main` ودمجه في `test` وفق §9-ب، ثم تجربته
على هاتف عربي وإنجليزي مع الملاحظات المذكورة في المخاطر.
