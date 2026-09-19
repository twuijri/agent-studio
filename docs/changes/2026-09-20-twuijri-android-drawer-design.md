# أندرويد: القائمة الجانبية تطابق تصميم الآيفون

المسؤول: twuijri
الفرع: fix/android-drawer-design (من `origin/mobile-staging`)
الحالة: قيد المراجعة (لم يُدفَع، ولم يُفتح طلب دمج)

## المشكلة والهدف
قارن المالك الجهازين وقال إنّ القائمة الجانبية في الآيفون «أفضل بكثير» من
الأندرويد، وخصّ بالذكر الشريط المقسَّم (Chat · Group Chat · Workflow · History).
عند الفحص تبيّن أنّ الفرق ليس شكليًا فقط:

1. **خلل وظيفي**: الضغط على «Group Chat» في الأندرويد كان يُغلق القائمة الجانبية
   وينتقل إلى صفحة المجموعات، وإذا فُتحت القائمة من جديد بقيت تعرض جلسات الدردشة
   («RECENT 10») لا الغرف. في الآيفون الشريط يبدّل القائمة التي تحته في مكانها،
   والقائمة الجانبية تبقى مفتوحة، ولا ينتقل شيء إلا عند الضغط على صف.
2. **فروق بصرية**: الشريط المقسَّم في الأندرويد نصوص فقط، بلا أيقونات فوقها وبلا
   مؤشر ظاهر للمحدَّد، وارتفاعه 30 بدل 42، والتذييل صفوف كاملة العرض بدل شرائح
   مضغوطة، ولا يوجد فيه مفتاح للسمة أصلًا.

الهدف: جعل قائمة الأندرويد الجانبية مطابقة لمصدر الآيفون قيمةً بقيمة، مع إصلاح
الخلل الوظيفي. خارج النطاق: أي تعديل على `clients/ios` (قُرئ فقط)، وملفَّي
`ui/chat/Composer.kt` و`ui/groups/RoomScreen.kt` اللذين يعمل عليهما وكيل آخر على
فرع مختلف، وأي تغيير في السيرفر.

## القرار والموافقات
- المرجع الوحيد للقيم: `clients/ios/HermesStudio/Features/SidebarDrawer.swift`
  و`Theme/CoreHubTokens.swift` و`Features/RootShell.swift`
  و`Features/GroupChat/RoomRowView.swift`.
- كل قيمة جديدة أُضيفت كـ token في
  `clients/android/.../ui/theme/CoreHubTokens.kt`؛ لا أرقام حرفية في القائمة.
- لم تُغيَّر وظيفة أي عنصر ولم يُعَد تسمية أي وجهة تنقّل: الشريط ما زال يستدعي
  `showTab` بالوجهات نفسها، والصفوف تستدعي `openRoom` / `openWorkflow` /
  `openSession` كما كانت.
- حالة فتح القائمة نُقلت من `rememberSaveable` داخل `HomeShell` إلى
  `UiState.drawerOpen`، لأنّ كل قسم يبني `HomeShell` خاصًّا به فتضيع الحالة عند
  تغيير القسم — وهو ما يفعله الآيفون أصلًا في `AppStore.drawerOpen`.
- قرار يحتاج نظر المالك: عدد الأعضاء `memberCount` لم يكن يُقرأ في
  `RoomInfo`؛ أُضيف حقل بقيمة افتراضية 0 ليطابق صف الغرفة في الآيفون
  (`n agents  n members`).

## الملفات المتأثرة
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/navigation/CoreHubDrawer.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/navigation/HomeShell.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubTokens.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubTheme.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubIcons.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/AppViewModel.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/GroupModels.kt`
- `clients/android/app/src/main/java/us/i3u/hermesstudio/ui/groups/GroupsScreen.kt` (كلمة
  `private` → `internal` على `NewRoomDialog` فقط، لإعادة استخدام الحوار في القائمة)
- `clients/android/app/src/main/res/values/strings.xml`، `values-ar/strings.xml`
  (`room_member_count`)
- اختبارات: `DrawerParityTest.kt` (جديد)، `DrawerRtlTest.kt` (جديد)،
  `DrawerSegmentBehaviourTest.kt` (جديد)، `NavigationStructureTest.kt` (تحديث
  ثلاث توكيدات تبعًا للبنية الجديدة)
- توثيق: `clients/android/README.md`، `docs/mobile/DESIGN-SPEC.md`

## الفحوص الفعلية
```
JAVA_HOME=/home/twuijri/.local/opt/jdk17 ANDROID_HOME=/home/twuijri/Android/Sdk \
  gradle --offline testDebugUnitTest assembleDebug
```
- `testDebugUnitTest`: BUILD SUCCESSFUL — 362 اختبارًا، لا فشل (كانت 343 قبل
  التغيير؛ الزيادة 19 اختبارًا جديدًا في الملفات الثلاثة).
- `assembleDebug`: BUILD SUCCESSFUL.
- تحقُّق من أنّ اختبار التطابق «يعضّ» فعلًا: بتغيير `drawerMaxWidth` إلى 320
  يفشل `DrawerParityTest > theDrawerShellMatchesTheIosNumbers`، ثم أُعيدت القيمة.

## الحدود وما لم يُتحقَّق منه
- **لا لقطة شاشة فعلية**: لا يوجد في هذه البيئة جهاز موصول ولا AVD لهاتف
  (المتاح `NdiTvApi33` وهو تلفاز)، والتطبيق يحتاج خادمًا لتسجيل الدخول. التطابق
  مثبَّت بالقيم في الاختبارات لا بالعين.
- **RTL مثبَّت بالمصدر لا بالتشغيل**: `DrawerRtlTest` يتحقّق أنّ الانزلاق
  والمؤشّر والخط الحدّي مكتوبة بـ start/end أو عبر `LocalLayoutDirection`، لكن
  لم تُرَ القائمة تعمل بالعربية على جهاز.
- **أيقونات السمة**: الآيفون يستعمل رموز SF ممتلئة
  (`moon.fill` / `sun.max.fill` / `circle.lefthalf.filled`) وهي غير قابلة للنقل؛
  رُسمت على الأندرويد بخطوط 1.8 مثل بقية طقم Core Hub، فالشكل متقارب لا مطابق.
  وكذلك `link` و`chevron.up.chevron.down`.
- **`memberCount`** يعتمد على إرسال السيرفر له في قائمة الغرف؛ إن لم يُرسل فالصف
  يعرض 0 بدل إخفاء العنصر.

## الخطوة التالية
عرض الفرع على المالك مع لقطة من جهازه للمقارنة؛ بعد موافقته: دفع الفرع، طلب دمج
إلى `main`، ثم دمج الفرع نفسه في `test` كما في `docs/TEAM-RULES.md` §3 و§9.
