# أندرويد: الإملاء يستمر عبر التوقفات حتى ■ أو ↑ أو ✕

المسؤول: twuijri
الفرع: fix/android-continuous-dictation (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف
على Core Hub أندرويد (البناء 48) يقول المالك «أهلاً وسهلاً شلونك»، يسكت لحظة،
فيتوقف التسجيل من تلقاء نفسه دون أن يضغط ■. المطلوب: أن يستمر الإملاء عبر
التوقفات حتى يضغط ■ إيقاف أو ↑ إرسال على شريط التسجيل، أو ✕ إلغاء.

**السبب المؤكَّد من الكود ومن مرجع أندرويد.** `SpeechRecognizer` مصمَّم لجملة
واحدة: عندما يقرر كاشف نهاية الكلام أن المتحدث سكت (ثانية أو اثنتان على محرّك
Google) يستدعي `onEndOfSpeech` ثم `onResults`، وتوثيق `onResults` نصًّا: «Called
with the results for the full speech since onReadyForSpeech» أي أن الجلسة انتهت.
وإذا لم يسمع شيئًا مفيدًا يرسل `ERROR_SPEECH_TIMEOUT` أو `ERROR_NO_MATCH`.
أما الـextras الثلاثة `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` و
`…POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` و`…MINIMUM_LENGTH_MILLIS` فالتوثيق
يقول عنها: «Depending on the recognizer implementation, these values may have
no effect» ومحرّك Google يتجاهلها فعلًا. وفي كودنا كان `SpeechInput.onResults`
و`onError` يستدعيان `release()` (تدمير المتعرّف) ثم `AppViewModel.onFinal`
يبثّ مقطعًا `Final` ويعيد `voice` إلى `Idle`، فيدفع الكاتب `Event.Finished`
إلى مخفِّض الشريط ويختفي الشريط. أي أن انتهاء *الجلسة* كان يُعامَل كانتهاء
*التسجيل*.

خارج النطاق: `clients/ios` (وكيل آخر يعالج الشيء نفسه بالتوازي)،
`KanbanScreens.kt`/`KanbanBoard.kt` (وكيل أندرويد آخر على فرع مختلف)، مسار
تسجيل السيرفر (لا يتغيّر؛ المسجِّل لا يتوقف عند الصمت أصلًا).

## القرار والموافقات
- **التسجيل سلسلة جلسات** (`ContinuousDictation.kt`، منطق نقي): ما دام الشريط
  ظاهرًا (المخفِّض في `recording`) فكل جلسة ينهيها المحرّك بنفسه تُعاد فورًا
  بالـintent نفسه (اللغة، قائمة الاكتشاف، إعدادات التبديل تُحفظ في `SpeechInput`
  وتُعاد كما هي). نصّها النهائي يُلتزَم في المسودة أولًا بنوع مقطع جديد
  `VoiceSegmentKind.Commit`، ثم تُلحق جزئيات الجلسة التالية بعده بقواعد الفواصل
  الموجودة في `applyVoiceSegment` (المسافة قبل المقطع عندما يسبقه حرف غير فراغ،
  ومسافة بعد المقطع الملتزَم/النهائي عندما يليه نص). الشريط وسطر «Listening in …»
  لا يتغيّران لأن `state.voice` يبقى `Listening` ونبّاض الموجة لا يتوقف بين
  الجلسات.
- **الأطوار**: `Closed → Listening → Restarting → Listening …`، و`Stopping` عند
  ■/↑/السقف. `Restarting` طور حقيقي (الفجوة بين جلستين) لأن ↑ فيه يجب أن يرسل
  فورًا دون انتظار محرّك لا يستمع: يُبثّ `Final("")` فيدفع الكاتب `Finished`
  ويرسل النص الملتزَم.
- **ما يوقف السلسلة فقط**: ■/↑/✕، أو خطأ حقيقي (الإذن، الصوت، الشبكة/السيرفر
  لمحرّك يعتمد السيرفر، اللغة غير مدعومة)، أو `ERROR_RECOGNIZER_BUSY`/`ERROR_CLIENT`
  بعد محاولة واحدة على متعرّف جديد بعد 300 ms، أو سقف **10 دقائق** يوقف الجلسة
  ويُبقي النص ويعرض تنبيهًا ثابتًا فوق الكاتب (`notice_dictation_ceiling`، عبر
  `dictationWarning` لأن شاشة المحادثة لا تعرض `state.notice` أصلًا).
  `ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` أثناء التسجيل ليسا خطأ: إعادة صامتة،
  مع التزام آخر فرضية إن وُجدت (كما كان السلوك السابق يحفظها).
- **حارس الإيقاف**: إن لم يجب المحرّك على `stopListening` خلال 4 ثوانٍ
  (`STOP_TIMEOUT_MILLIS`) يُغلق التسجيل بما لديه، حتى لا يعلق «جارٍ التفريغ…».
- **الـextras تُضبط بسخاء على أي حال** (صمت كامل 10 s، محتمل 8 s، الحد الأدنى 30 s)
  كتلميح للمحرّكات التي تحترمها؛ ■ ينهي الجلسة صراحة في الحالتين.
- **`VoiceSegmentOutbox`**: الكاتب يرى مقطعًا واحدًا في الحالة ويُبلغ بالرقم
  التسلسلي بعد وضعه. الجزئية الأحدث تحلّ محل جزئية لم تُوضع بعد (كما كان)، لكن
  `Commit`/`Final`/`Discard` لا يُستبدل قبل أن يُوضع — وإلا ضاعت كلمات جلسة كاملة
  لو وصلت أول جزئية من الجلسة التالية قبل إعادة التركيب. ✕ يلغي كل ما هو محجوز.
- **علّة كامنة أُصلحت لأنها في مسار ■ نفسه**: كان `Composer.kt` يصفّر مرساة
  المقطع بمجرد تحوّل الحالة إلى `Transcribing`، أي قبل وصول النص النهائي من
  المحرّك، فكان النص النهائي بعد ■ سيُدرج *بعد* الجزئية الأخيرة بدل أن يحلّ
  محلها (تكرار). لم تظهر من قبل لأن المحرّك كان ينهي التسجيل بنفسه في الإطار
  ذاته الذي يصل فيه النص. الآن تُصفَّر المرساة فقط عندما ينتهي التسجيل بلا مقطع.
- بديل نُظر فيه ولم يُعتمد: `EXTRA_SEGMENTED_SESSION` (API 33) يعيد النتائج
  على أجزاء `onSegmentResults`، لكنه مشروط بدعم المحرّك («When segmented session
  mode is supported by the recognizer implementation») وغير متاح تحت 13؛ إعادة
  الجلسة تعمل على كل مستوى وكل محرّك.
- **يحتاج نظر المالك**: قيمة السقف (10 دقائق) ونص التنبيه، وأن `ERROR_CLIENT`
  عُومل كـ«مشغول» بمحاولة واحدة.

## الملفات والتأثير
- جديد: `clients/android/app/src/main/java/us/i3u/hermesstudio/ContinuousDictation.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/SpeechInput.kt`
  (`restart(fresh)`، `end()`، حفظ الـintent والمستمع، الاستدعاءات لا تدمّر المتعرّف،
  الـextras الثلاثة، `VoiceSegmentKind.Commit`، `VoiceSegmentOutbox`،
  المسافة اللاحقة لـ`Commit` في `applyVoiceSegment`، حذف `isNoSpeech` غير المستخدم)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/AppViewModel.kt`
  (`dictate`/`runDictationEffect`/`closeTake`، مؤقّتات الإعادة والسقف وحارس
  الإيقاف، `stopVoiceInput`/`cancelVoiceInput` عبر الآلة، `emitVoiceSegment`
  عبر الصندوق الصادر، `consumeVoiceSegment` يعرض التالي)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/chat/Composer.kt`
  (`Commit` لا يُنهي الشريط؛ المرساة تبقى أثناء `Transcribing`)
- `clients/android/app/src/main/java/us/i3u/hermesstudio/RecordingStrip.kt` (تعليق فقط؛ القواعد لم تتغير)
- `clients/android/app/src/main/res/values/strings.xml`، `values-ar/strings.xml`
  (`notice_dictation_ceiling`)
- اختبارات: جديد `ContinuousDictationTest.kt` (13: إعادة بعد انتهاء الجلسة أثناء
  التسجيل، الإنهاء بعد ■، أصناف الأخطاء التي توقف/تعيد، مشغول ثم خطأ، السقف من
  المؤقّت ومن انتهاء الجلسة، ↑ في الفجوة، نص ثلاث جلسات بقواعد الفواصل، المسافة
  اللاحقة للـCommit، الصندوق الصادر)؛ `RecordingStripWiringTest.kt` (+1 على الأسلاك)
- توثيق: `clients/android/README.md` (فقرة «The take runs until you end it» وخريطة الملفات)
- لم يُمسّ: `clients/ios/*`، `KanbanScreens.kt`، `KanbanBoard.kt`

## الفحوص
البيئة: Linux، JDK 17 (`/home/twuijri/.local/opt/jdk17`)، Android SDK
(`/home/twuijri/Android/Sdk`)، Gradle 8.11.1 دون شبكة، worktree مستقل على الفرع.

```
cd clients/android && JAVA_HOME=/home/twuijri/.local/opt/jdk17 ANDROID_HOME=/home/twuijri/Android/Sdk \
  gradle --offline testDebugUnitTest assembleDebug
```
- التشغيل الأول: `assembleDebug` نجح؛ `testDebugUnitTest` **472 اختبارًا وفشل واحد**
  في اختباري الجديد (`ContinuousDictationTest` سطر 258): توقّع الاختبار
  «Hello worldthere» لجزئية أُدرجت في «Hello there» بينما النص بعد المرساة يبدأ
  بمسافة أصلًا فلا فرق بين الجزئية والالتزام هناك؛ صُحِّح الاختبار ليستعمل
  «Hellothere» (لا الكود).
- التشغيل الثاني: **BUILD SUCCESSFUL in 8s**؛ `testDebugUnitTest` **472 اختبارًا،
  0 فشل، 0 خطأ** (منها 13 في `ContinuousDictationTest`)؛ `assembleDebug` أنتج
  `app/build/outputs/apk/debug/app-debug.apk` (24.7 MB).
- لم يُنفَّذ: `npm run harness:check` و`npm run test:personal` لأن التغيير محصور في
  عميل أندرويد (تُشغَّل عند الدفع/PR). لا اختبارات Compose UI في هذا المستودع.

## المخاطر والرجوع
- لا بيانات ولا API متأثرة؛ التغيير في عميل أندرويد فقط. الرجوع = التراجع عن الـcommit.
- **يحتاج جهازًا حقيقيًا** (لا جهاز موصول ولا AVD هنا):
  1. أن الإعادة بالـinstance نفسه (`startListening` مجددًا بعد `onResults`) تعمل
     على محرّك الجهاز دون `ERROR_CLIENT`/`BUSY` متكرر؛ إن تكرر يُنظر في جعل
     `fresh = true` افتراضيًا (الكود جاهز لذلك).
  2. طول الفجوة الصوتية بين الجلستين (ما بين `onResults` و`onReadyForSpeech`
     التالية) — كلمات تُقال داخلها تضيع؛ هذا قيد المنصة نفسه.
  3. الإملاء العربي مع اكتشاف اللغة عبر عدة جلسات (كل جلسة قد تُبلغ لغة مختلفة؛
     الأخيرة تُعرض).
  4. ↑ أثناء الفجوة، و■ في بداية جلسة جديدة قبل `onReadyForSpeech` (يغطيه حارس
     الـ4 ثوانٍ لو لم يجب المحرّك).
  5. عدم تكرار النص بعد ■ (إصلاح المرساة).
  6. السقف: تجربة عملية طويلة صعبة؛ المنطق مُختبَر وحدويًا فقط.
- الاختبار على `RecordingStripWiringTest` يفحص المصدر نصيًا؛ تغيير الصياغة يكسره عمدًا.

## التسليم والخطوة التالية
الحالة الفعلية: مكتمل محليًا على الفرع `fix/android-continuous-dictation` في
worktree، commit واحد، **لم يُدفَع** ولم يُفتح طلب دمج (بحسب التعليمات). الخطوة
التالية للمالك: مراجعة القرارات المعلَّمة (السقف، نص التنبيه، معاملة
`ERROR_CLIENT`)، ثم الدفع وفتح PR إلى `mobile-staging` ودمجه في `test` وفق §9-ب،
ثم تجربة النقاط الست أعلاه على هاتف حقيقي بالعربية والإنجليزية.
