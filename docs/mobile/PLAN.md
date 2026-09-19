# Core Hub Mobile — خطة البناء (فرع `mobile`)

الحالة: مسودة معتمدة من المالك (2026-09-19). الفرع `mobile` طويل الأمد في هذا
المستودع حتى النضج؛ كل تعديل في ميزات الويب/سطح المكتب يجب أن ينعكس هنا.

## ما تعلمناه من تطبيق Ekko الرسمي (تحليل APK v1.0.4، للفهم فقط، بلا نسخ كود أو أصول)
- **ليس تطبيقًا أصليًا:** مبني بإطار uni-app (Vue 3 داخل WebView عبر DCloud)،
  ثماني صفحات فقط (`index`, `login`, `login-devices`, `devices`, `bootstrap`,
  `about`, `change-password`, `location-picker`)؛ الواجهة كلها داخل صفحة
  واحدة تشبه الويب. لهذا يبدو «مثل الويب».
- **نظام تصميم خاص به** باسم `--ink-*` (بطاقة، إدخال، رسالة، كود، تمييز)
  بوضعين فاتح/داكن وتخصيص لوني، وخط 14px، وحواف آمنة. ليس نفس رموز واجهة
  الويب عندنا؛ الفكرة المهمة: **رموز مركزية واحدة** لا ثوابت متفرقة.
- **الصوت (STT):** يسجّل بـ `uni.getRecorderManager` (mp3 عبر LAME) ثم يرفع
  إلى `POST /api/studio/stt/transcribe` بحقول `provider` + ملف `audio`
  (`speech.mp3`, `audio/mpeg`)؛ يقرأ `/api/studio/stt/settings` أولًا ويرفض
  صراحة المزوّد `browser` (`app_stt_browser_provider_unsupported`). النص يُدرج
  في صندوق الرسالة (زر `composer-voice-button` بحالات starting/recording/
  stopping/transcribing) ولا يُرسل تلقائيًا. أي أن الفارق عن تطبيقنا ليس
  «داخل الجهاز»، بل: المسار الصحيح `/api/studio/*`، حقل `provider` دائمًا،
  صيغة صوت يقبلها كل مزوّد (mp3/wav)، وحالات واضحة للزر.
- **الاتصال:** `/api/auth/app-login` بعد مسح QR، حساب سحابي اختياري
  (`/api/app/auth/*` — غير موجود عندنا ولا نحتاجه)، ريلاي `/app-relay` مع
  `relay.ready` و`http.request` و`socket.open` (للسحابة فقط)، والسوكت
  `/chat-run` بنفس أحداثنا (`app.resume`, `approval.*`, `clarify.*`,
  `location/calendar/health.*`, `app.events.subscribe`, `app.event`).
- **اللغات:** 11 لغة بينها العربية مع RTL. **الإشعارات:** لا FCM/APNs؛ أحداث
  السوكت فقط.
- **الوكلاء:** أيقونات hermes/ekko/claude/codex/deepseek/grok/pi/opencode،
  ونقاط `/api/coding-agents` و`/api/agents/*` كما عندنا.

## قرارات
1. أصلي (Kotlin/Compose + SwiftUI) كما بدأ المالك، لا WebView.
2. عقد واحد: HTTP من `docs/openapi.json` (`npm run openapi:generate`) +
   وثيقة السوكت `docs/mobile/SOCKET-CONTRACT.md` (تُشتق من
   `packages/client/src/api/studio/chat.ts` و`sockets/chat-run.ts`)؛ نماذج
   المنصتين تُولَّد/تُطابق منه، مع اختبارات عقد مشتركة (ملفات JSON ثابتة).
3. المسارات القانونية `/api/studio/*` فقط (لا `/api/hermes/*`).
4. نظام تصميم «Core Hub» مشترك: رموز الألوان/الزوايا/الخطوط/الأيقونات مأخوذة
   من واجهة الويب (`packages/client/src/styles`) ومكتوبة مرة واحدة لكل منصة،
   وبنية تنقل مطابقة للويب: دردشة، اتصالات الأجهزة، برامج الجهاز، مدير
   الوكلاء (للمشرف)، النماذج، الإعدادات (نفس الأقسام).
5. الصوت: تسجيل ثم `/api/studio/stt/transcribe` مع `provider` (ومزوّد
   `local` يدعم النص الجزئي عبر `local-stream`)، وإدراج النص في الصندوق.
   التعرف داخل الجهاز (Apple Speech / Android SpeechRecognizer) خيار إضافي
   عندما لا يوجد مزوّد على السيرفر.
6. الأخطاء تُعرض دائمًا (لا `try?`/`runCatching` صامتة) عبر شريط موحّد.
7. مسار التست: بناء APK تلقائي من فرع `mobile` إلى صفحة `latest-test`
   (نفس المستودع الخاص)، وiOS عبر Xcode محليًا حتى توفر حساب المطوّر.

## المراحل (كل مرحلة طلب دمج إلى `mobile`)
- M0 الاستيراد (هذا الالتزام): `clients/android`, `clients/ios`,
  `clients/mobile-docs`.
- M1 العقد والأساس: OpenAPI + وثيقة السوكت، طبقة شبكة واحدة لكل منصة على
  `/api/studio/*`، ربط QR عبر `app-login`، اختيار بروفايل ووكيل ونموذج.
- M2 نظام التصميم وبنية التنقل.
- M3 الدردشة: بث، موافقات/استيضاحات، مرفقات (`app-uploads`)، صوت، TTS،
  ملفات `device://`.
- M4 الجلسات والمجموعات وسير العمل والإعدادات.
- M5 الإشعارات (`app.events.subscribe` + خدمة أمامية على أندرويد).
- M6 الأجهزة وبرامج الجهاز.

## M1 iOS (فرع `mobile-m1-ios`، 2026-09-19)
ما نُفِّذ في `clients/ios` فقط (غير مُتحقَّق منه بمترجم؛ لا يوجد Xcode على
جهاز التطوير — المالك يبني ويختبر في Xcode):
- **المسارات القانونية:** كل نداءات `/api/hermes/*` التي لها مقابل قانوني
  صارت `/api/studio/*` (الجلسات، الرسائل، البحث `sessions/search`،
  التصنيفات، الاستخدام، الأداء، القروبات `group-chat/rooms`، الملفات
  `files/download`، STT، TTS، `chat-run/runs`). بقيت البروفايلات والإعدادات
  والنماذج والمهارات والإضافات وMCP وكانبان والمهام تحت `/api/hermes/*`.
  أُزيل أي تفاوض studio-ثم-hermes. لم يعد الرمز يوضع في `?token=` أبدًا؛
  التنزيلات (ملفات الوكيل، ملفات مساحة العمل، مرفقات كانبان) تمر عبر
  `URLSession` بترويسة `Authorization` ثم تُفتح من نسخة محلية.
- **الاقتران بـ QR:** زر «Scan QR code» في شاشة الدخول (AVFoundation
  `AVCaptureMetadataOutput`)، تحليل الحمولة `hermes-studio.app-connection`
  في `AppConnectionQR` (مع اختبارات)، ثم `POST /api/auth/app-login` بـ
  `device_code` ثابت في Keychain واسم جهاز قابل للتعديل. الرمز وتاريخ
  انتهائه ومعرّف الاتصال تُحفظ معًا في عنصر Keychain واحد
  (`AppSessionRecord`). رسائل واضحة (عربي/إنجليزي) لحالات 400/401/403/409/410.
- **التجديد الصامت:** `POST /api/auth/app-refresh` عند الإقلاع إذا بقي أقل
  من 7 أيام أو مضى أكثر من 24 ساعة على آخر تجديد، ومرة واحدة بعد أي 401 مع
  إعادة الطلب الأصلي. 401 من التجديد نفسه = تسجيل خروج برسالة. منطق القرار
  دالة نقية `AppTokenRefreshPolicy` مع اختبارات.
  **ملاحظة:** هذا الفرع من السيرفر لا يحتوي بعد على مسار `app-refresh`؛
  الخطأ غير-401 لا يُسجّل الخروج ويبقي الرمز الحالي.
- **الصوت:** الافتراضي داخل الجهاز (`SFSpeechRecognizer` + `AVAudioEngine`،
  نتائج جزئية حية تُستبدل في صندوق الرسالة، لا إرسال تلقائي)، وخيار
  «خادم Core Hub» في الإعدادات ← الصوت. مسار الخادم: `profile-status` أولًا
  ثم تسجيل WAV 16kHz mono 16-bit ثم `stt/transcribe` متعدد الأجزاء بحقل
  `provider` والملف `audio`/`voice.wav`. حالات الزر: idle/listening/
  transcribing/error، وكل خطأ ظاهر.
- **إظهار الأخطاء:** في الملفات المعدَّلة استُبدل `try?` على نداءات الشبكة
  بـ `store.attempt { … }` الذي يعرض الخطأ في الشريط الموحّد.
- ملفات جديدة: `Core/AppSession.swift`, `Core/SpeechTranscriber.swift`,
  `Features/QRScannerView.swift`. المشروع يستخدم مجلدات Xcode المتزامنة
  فلا حاجة لإدخالات pbxproj يدوية.
- المتبقي للمالك في Xcode: البناء، تشغيل الاختبارات، تجربة الاقتران على
  جهاز حقيقي، والتأكد من أذونات الكاميرا/الميكروفون/التعرّف على الكلام.
