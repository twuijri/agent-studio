# الجوال، المرحلة الثالثة (آيفون): الدردشة بمطابقة عميل الويب

المسؤول: twuijri
الفرع: mobile-m3-ios (من `mobile`)
الحالة: review

## المشكلة والهدف
بعد M2 يعرض تطبيق iOS الفقاعات بالرموز الصحيحة لكن داخلها بدائي: لا بطاقة
أدوات، لا كتلة تفكير بوقت وعدد أحرف، لا صف إجراءات، محرّر بلا إرفاق
كاميرا/صور ولا إعدادات، رفع عبر مسار `/upload` القديم، سوكت لكل تشغيل يُغلق
بعد الرد فلا تصل طلبات الجهاز (الموقع) ولا رسائل الأقران، الموافقات في ورقة
منفصلة، ولا تشغيل مضمّن للوسائط ولا نطق لكل رسالة. الهدف: تطبيق عقد
`/chat-run` كاملًا كما في `packages/client` (الأحداث في الطلب) ومواصفة
`docs/mobile/DESIGN-SPEC.md` (Message row, Composer, Chat header)، مع
الرفع المجزّأ `app-uploads`، الوسائط عبر `files/download`، TTS، وموافقة
الموقع. خارج النطاق: أندرويد، أي تغيير في السيرفر، تكاملات التقويم/الصحة
الأصلية (تُرد `denied`).

## القرار والموافقات
- الموافقة: المالك (تكليف M3 iOS بالترتيب 1–9 في الطلب).
- بنية: سوكت دائم لكل محادثة + مخفِّض نقي (`ChatRunReducer`) يتولى كل
  الأحداث، والواجهة تكتفي بالآثار الجانبية (النطق، الموقع، تحديث الدرج).
  التفاصيل وقائمة التحقق في `docs/mobile/PLAN.md` § «M3 iOS».
- قرارات تحتاج نظر المالك: (1) المحرّر لا ينطوي إلى شريط صغير عند الفراغ
  (المواصفة: حد أدنى 150pt)؛ (2) التفريع يُرسل `/fork` كأمر جلسة كما في الويب
  (لا REST)؛ (3) «الوضع الصوتي» = نطق كل رد تلقائيًا عبر TTS الخادم مع
  الرجوع إلى صوت الجهاز؛ (4) calendar/reminder/health تُرد `denied` مع TODO
  في الخطة؛ (5) زر الإرسال يصير «إيقاف» أثناء البث وزر طابور منفصل يظهر عند
  وجود مسودة.
- لا دفع ولا PR ولا دمج من المساعد؛ الفرع محلي بانتظار مراجعة المالك.

## الملفات والتأثير
`clients/ios/**` فقط:
- جديد: `Core/ChatStream.swift`، `Core/AppUploads.swift`، `Core/MediaLinks.swift`،
  `Core/LocationConsent.swift`، `Core/MessageSpeaker.swift`،
  `Features/Chat/{MessageRow, ToolSummaryCard, ThinkingBlock, MessageActionRow,
  InteractionCard, MediaPlayers, ChatBanners, ChatComposer, AttachmentPickers,
  LocationConsentSheet}.swift`، `HermesStudioTests/ChatParityTests.swift`.
- معدّل: `Core/SocketIO.swift` (سوكت دائم، `platform=ios`، خريطة الأحداث)،
  `Core/Models.swift` (`ToolStep`، `ChatLine` بأنواع الأسطر، `ChatInteraction`،
  `Message` بالمرفقات والاستدلال)، `Core/ChatFiles.swift` (`device://`)،
  `Core/SecureStore.swift` + `Core/AppStore.swift` (إظهار نداءات الأدوات،
  الوضع الصوتي)، `Theme/CoreHubIcons.swift` (أيقونة المفتاح)،
  `Features/ConversationView.swift` (أُعيدت كتابته)، `Info.plist`
  (`NSLocationWhenInUseUsageDescription`)، `Resources/{en,ar}.lproj/
  Localizable.strings` (+72 مفتاحًا)، `README.md`.
- وثائق: `docs/mobile/PLAN.md` (§ M3 iOS)، هذا السجل. لم يُمس `clients/android`
  ولا السيرفر.

## الفحوص
- لا يوجد Xcode ولا Swift على جهاز التطوير (لينكس)؛ الكود **غير مُترجَم
  محليًا**. رُوجع يدويًا: توقيعات الدوال بين الملفات، الالتقاط في إغلاقات
  `@Sendable` (صناديق فئات بدل متغيرات محلية)، مطابقة مفاتيح السلاسل بين
  en/ar (سكربت تحقق: كل سطر مدخل صالح، لا تكرارات جديدة).
- الاختبارات الجديدة (26) في `ChatParityTests.swift` تغطي المنطق النقي فقط؛
  تُشغَّل في CI على macOS بعد الدمج مع الاختبارات السابقة (43 + 24).
- الأدلة على العقد: `packages/server/src/modules/studio/sockets/chat-run.ts`،
  `services/chat-run/{response-stream, handle-bridge-run, resume-payload,
  usage, compression, abort}.ts`، `routes/app-upload.ts` + `services/files/
  app-upload.ts`، و`packages/client/src/components/hermes/chat/{ToolRunSummary,
  MessageItem, ChatInput}.vue` للسلوك المرجعي.
- لم يُشغَّل CI (لا دفع)؛ لم يُجرَّب على جهاز.

## المخاطر والرجوع
- تغيير سلوكي: السوكت يبقى مفتوحًا طوال عرض المحادثة (اتصال واحد لكل شاشة
  مفتوحة)؛ يُغلق عند مغادرة الشاشة. الرجوع: إعادة الفرع إلى رأس `mobile`.
- الرفع صار عبر `/api/studio/app-uploads` (المسار القانوني للتطبيقات) بدل
  `/upload`؛ الدالة القديمة `APIClient.upload` بقيت لمستخدميها الآخرين.
- الموقع يُرسل مرة واحدة وبعد موافقة صريحة فقط؛ لا تخزين محلي.
- المحدودية: لا استئناف للرفع بعد قتل التطبيق (TTL 5 دقائق على الخادم)؛
  لا تكاملات تقويم/صحة.

## التسليم والخطوة التالية
الحالة: الالتزامات على `mobile-m3-ios` محليًا، بلا دفع. الخطوة التالية
للمالك: البناء في Xcode وتشغيل الاختبارات، المرور على قائمة التحقق في
`docs/mobile/PLAN.md` § «M3 iOS» على جهاز حقيقي (بث، أدوات، موافقة، مرفقات،
وسائط، نطق، موقع، RTL)، ثم الدفع وفتح PR إلى `mobile`، ثم M3 أندرويد.

## إضافة: أندرويد (المرحلة نفسها)
الالتزام `dd3aa612`: `ui/chat/MessageRow.kt` (الفقاعات، بطاقة الأدوات، كتلة التفكير،
صف الإجراءات، مشغّلات الوسائط وشارة الجهاز)، `RunCards.kt` (موافقات/استيضاحات،
الطابور، الضغط، الإيقاف، موافقة الموقع)، `ChatFormat.kt`، `AppUploads.kt`
(الرفع المجزّأ)، `MobileLocation.kt`، وإعادة كتابة `Composer.kt`؛ 44 نصًا
بالعربية والإنجليزية. البناء: BUILD SUCCESSFUL، 140 اختبارًا بلا إخفاق، وتجربة
على محاكي محلي بواجهة عربية. المحدود: التقويم/التذكيرات/الصحة تُرفض مؤقتًا،
والفيديو عبر `VideoView`.
