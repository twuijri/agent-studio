# الجوال: إزالة ميزة الحيوانات (Pets / Petdex) من تطبيقي أندرويد وiOS

المسؤول: twuijri
الفرع: chore/mobile-remove-pets (من `origin/mobile-staging`)
الحالة: review

## المشكلة والهدف

قرّر المالك (2026-09-20) إزالة ميزة الحيوانات المرافقة (Pets/Petdex) من Core Hub
كلّه. الويب والخادم وسطح المكتب في مهمة أخرى؛ هذه المهمة تُزيلها من تطبيقي
الهاتف معًا، لأن السجل المشترك `NavDestination` واحد على المنصتين
(`docs/mobile/NAVIGATION.md`) و`NavigationParityTest` يقرأ ملفات iOS فعليًا
ويسقط عند أي انحراف بين الحالتين أو بين ملفي اللغة.

الدليل قبل التغيير: `clients/mobile-docs/STUDIO_V0712_PARITY.md` (البند 11 وقسم
«Obsolete mobile code») كان قد رصد أن كود الحيوانات ما زال مُجمَّعًا على المنصتين.

خارج النطاق: الويب، الخادم، سطح المكتب، وأي تغيير في السلوك غير المتعلق بالحيوانات.

## القرار والموافقات

إزالة كاملة لا إخفاء: الحالة من السجل، والشاشة، والنداءات الأربعة، والنماذج،
والحالة، والسلاسل، وخادم المحاكاة، والاختبارات التي ثبّتتها — على المنصتين بالترتيب
نفسه، فيبقى السجل 37 حالة متطابقة (كان 38).

- **مشترك**: حُذفت الحالة `pets` من `NavDestination.kt` و`NavDestination.swift`
  (وقائمة `settingsTools`/`tools` صارت `logs · usage · performance · skillsUsage ·
  theme · profiles`)، ومفتاح `nav_pets` من ملفات اللغة الأربعة، ومصطلح
  `Pets`/«الحيوانات» من جدول التسمية في `NAVIGATION.md` ومن قائمة Settings → Tools
  (§٢)، وصار سطر `Petdex` في قائمة «يُحذف» يقول إن الميزة أُزيلت من Core Hub كلّه.
- **أندرويد**: `PetsScreen` و`absoluteStudioUrl` (لم يستعملها غيرها) من
  `AgentToolScreens.kt`، و`Screen.Pets`، و`petsUi` ودوال `openPets/refreshPets/
  loadPets/adoptPet/setActivePet` وفرع `openTool` من `AppViewModel.kt`،
  والنداءات `petdex/activePet/adoptPet/updateActivePet` من `HermesApi.kt`،
  و`PetdexPet/ActivePet/PetsUiState` من `AgentTools.kt`، وصف Settings → Tools
  وأيقونته من `SettingsScreen.kt`، وسطر التوجيه والاستيراد من `MainActivity.kt`،
  و10 سلاسل (`nav_pets` + `pets_*`) من `values` و`values-ar`، ومسارات
  `/api/hermes/petdex|pets` وبيانات `PETS/ACTIVE_PET` و`/mock/pet.png` من
  `tools/mock-studio.py`. حُذفت أيضًا تبعية `io.coil-kt:coil-compose` لأن
  `AsyncImage` كانت تُستعمل في شاشة الحيوانات وحدها (تعليق التبعية نفسه كان يقول
  «Petdex previews»)؛ الصور الرمزية للوكلاء تُرسم بـ`androidsvg` ولا تمسّها.
- **iOS**: `PetsView` من `Features/CapabilitiesView.swift`، والفرع `.pets` من
  `ShellDestinationView` (`RootShell.swift`) و`SettingsView.symbol(for:)`،
  والثوابت الثلاثة والنداءات الأربعة من `APIClient.swift`، و`struct Pet` من
  `Models.swift`، والمفاتيح `"Pets"`، `"nav_pets"`، `"Adopt"` من `en/ar.lproj`
  (لا يستعملها ملف Swift آخر؛ `"Active"` باقية لأن شاشات أخرى تقرؤها).
- **الاختبارات**: أندرويد — `NavigationParityTest` (37 حالة، قائمة الأدوات؛ بقي
  `openPets()` في قائمة «لا يُنادى خارج السجل» كحارس ضد العودة)،
  `NavigationStructureTest` (حُذف فرع `pets`، وأُضيف تأكيد أن `PetsScreen`/
  `openPets`/`Screen.Pets` لا تعود)، `HermesApiContractTest` (حُذف اختبار
  Petdex)، `ChannelSchemaTest` (حُذف `PetsScreen` من قائمة الشاشات المتداخلة).
  iOS — `NavigationContractTests` (القائمة، `tools`، العدد 37)،
  `NavigationScreensTests` (حُذف اختبار مسارات الحيوانات).
- **الأصول**: لا ملفات صور أو أصول خاصة بالحيوانات في أي من المنصتين
  (`boring-avatars` و`AgentAvatar` للوكلاء وليست حيوانات، ولم تُمسّ).
- `clients/mobile-docs/STUDIO_V0712_PARITY.md` تقرير تدقيق تاريخي؛ تُرك كما هو
  والبند 11 فيه صار منجزًا بهذا السجل.

## الملفات والتأثير

أندرويد: `app/build.gradle.kts`, `AgentToolScreens.kt`, `AgentTools.kt`,
`AppViewModel.kt`, `HermesApi.kt`, `MainActivity.kt`,
`navigation/NavDestination.kt`, `ui/settings/SettingsScreen.kt`,
`res/values{,-ar}/strings.xml`, `tools/mock-studio.py`, واختبارات
`NavigationParityTest`, `NavigationStructureTest`, `HermesApiContractTest`,
`ChannelSchemaTest`, و`clients/android/README.md`.

iOS: `Core/NavDestination.swift`, `Core/APIClient.swift`, `Core/Models.swift`,
`Features/CapabilitiesView.swift`, `Features/RootShell.swift`,
`Features/SettingsView.swift`, `Resources/{en,ar}.lproj/Localizable.strings`,
`HermesStudioTests/NavigationContractTests.swift`,
`HermesStudioTests/NavigationScreensTests.swift`, و`clients/ios/README.md`
(صُحّح فيه أيضًا عدد الحالات المذكور: كان يقول 36 وينسى `presets`؛ الآن 37 بالقائمة الكاملة).

وثائق مشتركة: `docs/mobile/NAVIGATION.md`, `docs/mobile/PLAN.md`,
`docs/mobile/DESIGN-SPEC.md`.

## الفحوص

- أندرويد (`clients/android`, JDK 17, Gradle 8.11.1):
  - `gradle --offline testDebugUnitTest assembleDebug` سقط أول مرة في حلّ
    التبعيات لا في التجميع: بعد حذف Coil صار `okio 3.6.0` يقود
    `kotlin-stdlib-jdk8` إلى 1.9.10 التي تطلب `kotlin-stdlib-jdk7:1.9.10`، وهي
    غير مفهرسة في مخبأ هذه النسخة من Gradle رغم وجود jar/pom. نُفّذ الأمر مرة
    واحدة دون `--offline` لجلب بيانات الوصف، ثم أُعيد الأمر المطلوب بـ`--offline`.
  - النتيجة: `BUILD SUCCESSFUL` — 488 اختبار وحدة في 51 صنفًا، 0 إخفاق، 0 خطأ،
    0 متخطّى (أي أن فحوص iOS داخل `NavigationParityTest` نُفّذت فعلًا ولم تُتخطَّ)؛
    منها `NavigationParityTest` 6، `NavigationStructureTest` 14،
    `HermesApiContractTest` 50، `ChannelSchemaTest` 3. أُنتج `app-debug.apk`
    (24.5 MB). التحذيرات المطبوعة كلها تحذيرات إهمال سابقة في ملفات لم تُمسّ.
- iOS: لا Xcode هنا؛ لم يُجمَّع. نُفّذ: توازن `{}`/`()`/`[]` لكل ملف Swift معدَّل
  (فرق الأقواس المستديرة في `APIClient.swift` هو −4 قبل التعديل وبعده — نص داخل
  سلاسل، لا خلل)، وتحقق أن `ShellDestinationView` ما زالت تغطي الحالات الـ37 كلها
  دون `default`، ولا `private` عابر للملفات. التجميع وXCTest على مسار CI الخاص
  بالجوال بعد الدمج.
- `NavigationParityTest` يقرأ ملفات iOS الحقيقية من الفرع نفسه (تطابق مجموعة
  الحالات وتطابق `nav_*` إنجليزيًا وعربيًا).

## المخاطر والرجوع

- لا بيانات مستخدم على الهاتف مرتبطة بالحيوانات؛ الحالة كانت على الخادم فقط.
- إن كان خادم قديم ما زال يقدّم `/api/studio/pets/*` فلا أثر: الهاتف لا يناديه.
- الرجوع: `git revert` لالتزام واحد يعيد الميزة على المنصتين معًا (مع Coil).
- الأثر الجانبي الوحيد على البيئة المحلية: أول بناء بعد هذا التغيير يحتاج شبكة
  مرة واحدة إن لم يكن `kotlin-stdlib-jdk7:1.9.10` مفهرسًا في مخبأ Gradle.

## التسليم والخطوة التالية

- الحالة: التعديلات في فرع محلي `chore/mobile-remove-pets` دون دفع (بأمر المالك).
- الخطوة التالية: مراجعة المالك، ثم الدمج في `mobile-staging` ضمن دفعة الجوال
  (لا بناء اختباري إلا بطلب المالك)، مع مهمة الويب/الخادم/سطح المكتب الموازية.
