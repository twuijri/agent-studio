# iOS: مدير الوكلاء وشاشة الوكيل وإعدادات Hermes تطابق تصميم الأندرويد

المسؤول: twuijri
الفرع: fix/ios-agent-manager-design (من `origin/mobile-staging`)
الحالة: review (لم يُدفَع، ولم يُفتح طلب دمج)

## المشكلة والهدف

قارن المالك الجهازين وفضّل تصميم مدير الوكلاء في الأندرويد: «شكل الأجنتات
بالأندرويد أجمل من شكله في الآيفون»، ثم أضاف (مع لقطة بناء iOS رقم 48) أن
قائمة الأندرويد أرتب، وبطاقاتها تحمل زر الإعدادات وصف الإجراءات، و«حتى شاشة
الإعدادات داخل وكيل Hermes في الأندرويد أرتب وأفضل من iOS».

المرجع الوحيد للقيم: مصدر الأندرويد
`clients/android/.../ui/agents/AgentManagerScreen.kt` و`AgentScreen.kt`
و`AgentSettingsScreen.kt` و`ui/agents/HermesScreens.kt` و`DesignSystem.kt`
و`MainActivity.kt` (`SettingsRowContent`, `SettingsSection`, `LoadingRow`)
و`ui/theme/CoreHubTokens.kt` و`CoreHubTheme.kt`. قُرئت ثلاث لقطات (أندرويد،
قائمة iOS، شاشة وكيل iOS) ثم لقطة البناء 48.

### الفروق التي رُصدت قبل التعديل، وما صارت إليه

| العنصر | أندرويد (المرجع) | iOS قبل | iOS الآن |
|---|---|---|---|
| سطر التعريف | «Every agent Core Hub knows…» 12 ثانوي | لا يوجد | مضاف، 12 ثانوي |
| ملاحظة «تجري على الخادم» | صندوق info@10% نصف قطر 12، حشوة 12 | لا يوجد | `AgentNoteBox` بالقيم نفسها |
| ترتيب الأقسام | Hermes runtime · Built in · Coding agents | Built in · Hermes runtime · Coding agents | ترتيب `AgentKind` |
| عنوان القسم | 13 عريض ثانوي، 8 فوقه | عنوان `List` المُدمج | `AgentSectionHeader` 13/700 |
| البطاقة | Surface نصف قطر 18، حد 1، حشوة 16، فاصل 8 | صف `List` insetGrouped | `AgentCardSurface` 18/1/16/8 |
| الصورة | 34 بحلقة بيضاء 1 + فجوة 12 | 34 + 12 | كما هو (رمز `agentCardAvatar`) |
| الاسم / المزوّد | titleMedium 14 عريض / bodySmall 12 ثانوي | 14 شبه عريض / 11 باهت | 14 عريض / 12 ثانوي |
| شارة الحالة | 11 شبه عريض على لونها 16%، حشوة 10×5 | `StatusPill` 13%، 9×5 | `AgentStatePill` 16%، 10×5 |
| زر Update | حبّة info@16% (8% معطّل)، 12×5، 11 | `.bordered .small` | حبّة بالقيم نفسها |
| سطر الميتا | `Local CLI · v0.154.0 · v0.155.1 available` 12 باهت | `Local CLI · v0.154.0` 11 | بالصيغة الأندرويدية |
| الحزمة / المسار | 11 monospace باهت، سطر واحد | لا يظهران في القائمة | `TechnicalText` 11 mono |
| سطر الخطأ | bodySmall 12 بلون `error` | 11 بلون `error` | 12 `error` |
| Update automatically | نص 13 + Switch على البطاقة | في شاشة الوكيل فقط | على البطاقة |
| صف الإجراءات | OutlinedButton بترس 16 + TextButtons | نص «Agent settings» + سهم | `Agent settings` (حبّة 40 بحد وترس 16) · `CLI details` · `Reinstall`/`Install` · `Check for an update` · `Delete` |
| التثبيت/التحديث/الحذف | من البطاقة | من شاشة الوكيل | من البطاقة |
| التحميل / الفراغ | `LoadingRow` 22 داخل 16 + نص تحذير | `ProgressView` مركزي | `LoadingRowView` + النص |
| رأس شاشة الوكيل | صورة 40 + اسم 14 عريض + `vX · source` 12 | صورة 44 + اسم 16 + المعرّف mono | 40 / 14 / `vX · Local CLI` |
| تفاصيل CLI | حوار من البطاقة (الإصدار، المسار، الخطأ) | صفوف `List` | بطاقة مجمّعة على شاشة الوكيل + تنبيه من زر البطاقة |
| صفوف القدرات | بلاطة 38 (accent@16%، نصف قطر 10) بأيقونة 20، فجوة 12، عنوان 14/500، سهم، حشوة 12×9، فاصل بمسافة 66 | `Label` بأيقونات SF داخل `List` | `AgentCapabilityRow` بالقيم نفسها؛ الأيقونات أقرب رموز SF لأيقونات Material، والترس مسار Core Hub على الجهازين |
| صف الإعدادات | آخر صف في البطاقة نفسها | قسم مستقل بتذييل | آخر صف في البطاقة |
| شريط تبويبات إعدادات Hermes | `TabRow` بعرض كامل، 48، مؤشر 2 | `TabStrip` قابل للتمرير، 38 | `MaterialTabRow` 48 / مؤشر 2 |
| صفوف الإعدادات | بطاقة 18 داخل هامش 16×4، حشوة 16×14، أيقونة 24، عنوان 14 فوق قيمة 11، Switch أو سهم | `Form` بحقول نصية وزر حفظ واحد | `SettingsCardRow` بالقيم نفسها؛ الرقم يفتح مطالبة، الاختيار يفتح ورقة خيارات |
| الحفظ | كل تغيير `PUT` بمفتاح واحد، `LoadingRow` أثناءه ثم إشعار «Saved» | زر «Save settings» يكتب الأقسام كلها | كما في الأندرويد (`store.notify("Saved")`) |
| عنوان القسم / التلميح | 12/600 داخل 22/18/22/4؛ 11 داخل 20×4 | ترويسة `Form` | `SettingsSectionLabel` / `SettingsHintText` |

## القرار والموافقات

- كل قيمة تمر عبر ملف الرموز `Theme/CoreHubTokens.swift`؛ ما كان لدى
  الأندرويد ولم يكن لدى iOS أُضيف رمزًا (`agentCard*`, `agentPill*`,
  `agentRow*`, `agentScreen*`, `settingsRow*`, `settingsSection*`,
  `tabRow*`, `loadingRow*`, `screenPaddingH`، وثلاثة رموز شفافية وثلاثة أنصاف
  أقطار). القيم التي يأخذها الأندرويد من Material دون كتابتها (ارتفاع الزر 40،
  حشوة 24/12، شريط 48، مؤشر 2، أيقونة 24) مثبّتة في الرموز والاختبار مع ذكر
  مصدرها.
- آلية التنقل التي أُصلحت للتو بقيت كما هي: البطاقة تفتح الوكيل عبر
  `store.openAgent(id)` (نقرة على البطاقة كلها؛ الأزرار داخلها أهداف مستقلة)،
  `AgentDetailView` لا يُنشأ إلا من `AgentScreenLoader`، وروابط القيمة لا توجد
  إلا في `AgentDetailView.swift` و`SettingsView.swift` — `NavigationRulesTests`
  ما زال يثبّت ذلك.
- RTL: `HStack`/`VStack(.leading/.trailing)` تنعكس تلقائيًا؛ صف الإجراءات
  `AgentActionsFlow` تخطيط مخصص يقرأ `layoutDirection` ويملأ من الحافة اليمنى
  بالعربية (تخطيطات `Layout` المخصصة لا تنعكس وحدها)؛ الأسهم `chevronForward`
  تنعكس عبر `mirrorsInRTL`؛ الشارات تلتصق بالحافة النهائية.
- نقل الإجراءات (تثبيت/تحديث/بحث عن تحديث/حذف) والمفتاح التلقائي إلى البطاقة
  كما في الأندرويد؛ أُزيل قسما «Updates» و«Actions» من شاشة الوكيل مع مفاتيحهما
  (22 مفتاحًا في الملفين). حوار الحذف وتنبيه «CLI details» على شاشة المدير.
- إعدادات Hermes: `HermesSettingsRows` جديد يعيد استخدام تعريفات الحقول في
  `StudioSettingsSection.fields` بعد إضافة `icon` و`note` لكل حقل (`.row(...)`)
  ومعه 12 عنوان حقل لم تكن في ملفَي اللغة أصلًا و11 ملاحظة، بالعربية من
  ملف الأندرويد. نموذج `StudioSectionsForm` القديم بقي للأقسام على مستوى
  التطبيق (Proxy, Compression, Privacy, Display).
- سلوك مقصود بقي: إعادة تشغيل وقت التشغيل عند حفظ قسم `agent` أو `gateway`
  (كما كان في iOS والويب) — الأندرويد لا يعيد التشغيل لهما؛ لم يُغيَّر لأنه
  سلوك لا تصميم.
- ينتظر إذن صاحب المشروع: الدفع، PR، وبناء اختبار (`feedback-batch-test-builds`).

## الملفات والتأثير

`clients/ios/HermesStudio/`:

- `Theme/CoreHubTokens.swift` — رموز الوكلاء والإعدادات (Alpha, Radius, Layout).
- `Features/Agents/AgentCardViews.swift` — جديد: `AgentCardSurface`,
  `AgentSectionHeader`, `AgentNoteBox`, `AgentStatePill`, `LoadingRowView`,
  `AgentOutlinedButton`, `AgentTextButton`, `AgentActionsFlow`,
  `AgentGroupedCard`, `AgentCardDivider`, `AgentIconTile`,
  `AgentCapabilityRow`, `AgentDetailRow`.
- `Features/Agents/AgentManagerView.swift` — أُعيدت كتابته: `ScrollView`
  ببطاقات، `AgentCardView`, `AgentCardActions`, `AgentUpdateButton`
  (حبّة)، `AgentCardText` (نصوص نقية)، الإجراءات على الخادم.
- `Features/Agents/AgentDetailView.swift` — أُعيدت كتابته: الرأس، بطاقة
  تفاصيل CLI، بطاقة القدرات والإعدادات؛ رموز SF المقابلة لأيقونات Material.
- `Features/Agents/HermesSettingsView.swift` — `MaterialTabRow` +
  `HermesSettingsRows` + `HermesSettingEdit`.
- `Features/Settings/SettingsRowViews.swift` — جديد: `MaterialTabRow`,
  `SettingsCardRow`, `SettingsSectionLabel`, `SettingsHintText`.
- `Features/Settings/StudioSectionSettings.swift` — `ConfigField.icon/note/options/row`.
- `Resources/{en,ar}.lproj/Localizable.strings` — +35 مفتاحًا، −22.

اختبارات: `HermesStudioTests/AgentManagerParityTests.swift` (جديد؛ يقرأ
Kotlin الأندرويد بتعابير نمطية كما يقرأ `DrawerParityTest` ملفات Swift)،
`NavigationContractTests.swift` (رمز `presets`).

وثائق: `clients/ios/README.md` (تحت الوكيل، قسم Agents، إعدادات Hermes، قائمة
الاختبارات)، `docs/mobile/DESIGN-SPEC.md` (قسم جديد لمدير الوكلاء وشاشة الوكيل
وصفوف الإعدادات)، هذا السجل.

## الفحوص

- لا Xcode ولا macOS هنا: **لم يُجمَّع المشروع ولم تُشغَّل XCTest**؛ التجميع
  والاختبارات على `mobile-test-track.yml` بعد الدمج.
- نُفّذ محليًا (بايثون على الشجرة):
  - توازن `{}` `()` `[]` لكل ملف Swift معدَّل أو جديد.
  - محاكاة قواعد `NavigationRulesTests`: `AgentDetailView(` في
    `AgentScreens.swift` وحده؛ `NavigationLink(value:` في `AgentDetailView.swift`
    و`SettingsView.swift` وحدهما؛ لا `NavigationLink {` في المدير.
  - محاكاة كل تعبير نمطي في `AgentManagerParityTests` على ملفات الأندرويد:
    كل قيمة مقروءة من Kotlin تساوي رمز iOS المقابل (34، 12، 16/8، 1، 6،
    18/12، 10×5، 12×5، 0.16/0.08/0.10، 16/6، 6، 8/28، 10، 8، 12، 16؛ 40، 12،
    66، 38، 20، 12×9؛ 4، 18، 16×14، 14، 22/18/22/4، 16، 22، 20×4؛ سلّم الخط
    14/12/11/13/14).
  - ملفا اللغة: لا تكرار، مجموعتا المفاتيح متطابقتان عدا
    `CFBundleDisplayName`، ومحدِّدات التنسيق متطابقة.
- لم تُنفَّذ فحوص المستودع الأخرى؛ التغيير محصور في عميل iOS ووثائقه.

## المخاطر والرجوع

- خطر تجميع لأن الشيفرة لم تُجمَّع (أجسام صغيرة، أنواع غير `private` حيث
  تُستعمل عبر الملفات، Swift 5 / iOS 17؛ `Layout` مخصص يستخدم
  `LayoutSubviews.layoutDirection`؛ `TextField` داخل `alert`).
- يجب التأكد على الجهاز: أن نقرة زر داخل البطاقة لا تفتح البطاقة
  (`onTapGesture` على الحاوية وأزرار `.plain` داخلها)، وأن `Toggle` داخل
  البطاقة يعمل، وأن صف الإجراءات يلتف ويملأ من اليمين بالعربية.
- حُذفت شاشة الإجراءات من `AgentDetailView`؛ من يعتمد على «Update to vX» هناك
  يجدها على البطاقة (`Update`) وفي `Reinstall`.
- إعدادات Hermes: كل تغيير يُحفظ فورًا (لا زر تراجع)؛ الرقم يُحلَّل إلى Int/Double
  وإن كان النص غير رقمي يُحفظ 0 — كما يفعل الأندرويد (`toIntOrNull` يتجاهل
  بدلًا من 0؛ فرق صغير يمكن تشديده لاحقًا).
- لا هجرة بيانات ولا تغيير في الخادم أو الويب أو أندرويد.
- الرجوع: الفرع مستقل؛ يُحذف أو تُعكس مراجعاته.

## التسليم والخطوة التالية

محليّ فقط على `fix/ios-agent-manager-design` (worktree). لا دفع ولا PR ولا
بناء نشر. الخطوة التالية: مراجعة صاحب المشروع، ثم الدفع وPR إلى
`mobile-staging` بإذنه، والتحقق على الجهاز (بالإنجليزية والعربية) من: القائمة
والبطاقة وصف الإجراءات، شاشة الوكيل، وإعدادات Hermes بتبويباتها الثلاث، ثم
بناء اختبار عند طلبه.
