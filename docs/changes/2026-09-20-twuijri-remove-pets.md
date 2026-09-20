# إزالة قسم الحيوانات الأليفة (Pets / Petdex) من كور هب

المسؤول: twuijri
الفرع: chore/remove-pets
الحالة: review

## المشكلة والهدف
قرر المالك (2026-09-20) إزالة قسم الحيوانات الأليفة من كل مكان في كور هب:
«أزل هذا القسم من كل مكان في Core Hub — من التطبيق ومن الموقع». هذا السجل يغطي
واجهة الويب والسيرفر وتطبيق سطح المكتب؛ تطبيقا الجوال في مهمة مستقلة.

الجرد قبل الإزالة (بحث `pet` / `petdex` في `packages/` و`tests/` و`docs/` و`.github/`):

- الواجهة: مسارا `hermes.petdex` و`desktop.pet`، صفحتا `PetdexView.vue`
  و`DesktopPetView.vue`، المكوّن `components/hermes/pets/WebPet.vue`، وحدات
  `api/studio/{pets,petdex,pet-state}.ts`، المخزنان `stores/hermes/{pets,pet-state}.ts`،
  عنصر «الحيوانات الأليفة» في `AppSidebar.vue`، كشف نافذة الحيوان في `main.ts`
  و`index.html` وصنف `hermes-desktop-pet-window` في `global.scss`، أنواع جسر
  سطح المكتب في `utils/desktop-bridge.ts`، الأيقونة `public/icons/pet-resize.svg`،
  ومفاتيح `sidebar.petdex` و`petdex.*` في 11 ملف لغة.
- السيرفر: `modules/studio/routes/{pets,petdex}.ts` ومتحكماتها وخدمات
  `services/pets/`، مقبس `sockets/pet-state.ts` (النطاق `/pet-state`)، الواجهة
  `public/pet-events.ts`، وربطها في `bootstrap/routes.ts` و`bootstrap/http.ts`،
  واستدعاءات `observeRunChatPetEvent` داخل `sockets/chat-run.ts`
  و`services/chat-run/handle-{bridge,ekko-agent}-run.ts`، ووسما `Pets`/`Petdex`
  في `scripts/generate-openapi.mjs` (18 إدخالًا في `docs/openapi.json`).
- سطح المكتب: نافذة الحيوان في `packages/desktop/src/main/index.ts` (ثوابت،
  `ensurePetWindow`، `loadPetWindowRoute`، ثلاثة معالجات IPC، الاستدعاء عند كل
  إقلاع) وكشفها في `preload/index.ts` (`getPetWindowState` وأخواتها،
  `windowKind: 'pet'`).
- الاختبارات: `tests/client/{app-web-pet,pets-store}.test.ts`،
  `tests/server/{pets-service,petdex-service}.test.ts`، وإشارات في
  `sidebar-search`، `hermes-config-navigation`، `style-system`،
  `run-chat-ekko-context`، `tests/desktop/windows-main-window`،
  `tests/e2e/fixtures.ts` و`tests/e2e/theme.spec.ts`.
- الوثائق: `docs/harness/server-module-boundaries.md` و`docs/UPSTREAM-README.md`.
  لا ذكر في `ARCHITECTURE.md` أو `README.md` أو `.github/`.

خارج النطاق: تطبيقا الجوال، وأي تغيير في قاعدة البيانات (لا جداول للحيوانات).

## القرار والموافقات
- الموافقة: المالك (2026-09-20). لم يُطلب PR ولم يُفتح؛ الفرع محلي فقط.
- الإزالة الكاملة بدل الإخفاء: حُذفت الملفات الخاصة بالميزة، وأُزيل الربط من
  الملفات المشتركة فقط دون إعادة هيكلة. في `sockets/chat-run.ts` أُبقي على
  الدالة التي كانت تحسب البروفايل لأحداث الحيوان لأنها تُستعمل أيضًا للـ webhooks
  والتفاعلات المعلقة، وأُعيدت تسميتها إلى `resolveEventProfile`.
- ما أُبقي عمدًا:
  - بيانات المستخدم على القرص تحت
    `<HERMES_WEB_UI_HOME>/profile-metadata/<profile>/pets/active.json` لا تُمسّ ولا
    تُقرأ؛ لا جدول SQLite للميزة فلا ترحيل ولا حذف.
  - `docs/UPSTREAM-README.md` يبقى كما هو لأنه نسخة محفوظة من README الأصلي
    للنسب والمرجع، وكلمة `pets` فيه تصف upstream لا كور هب.
  - سجلات `docs/changes/` التاريخية لم تُعدَّل.
- في اختبار السمة الشامل `theme.spec.ts` أُزيل مقطع صفحة الحيوانات فقط؛ فحص
  `.app-main--card` ما زال مغطى في مقاطع أخرى من الاختبار نفسه.

## الملفات والتأثير
- محذوف (21 ملفًا): الوحدات المذكورة في الجرد أعلاه (واجهة، سيرفر، اختبارات، أيقونة).
- معدّل: `packages/client/src/{App.vue,main.ts,router/index.ts,styles/global.scss,utils/desktop-bridge.ts}`،
  `packages/client/index.html`، `components/layout/AppSidebar.vue`،
  `i18n/locales/*.ts` (11 ملفًا)، `packages/server/src/bootstrap/{routes,http}.ts`،
  `modules/studio/sockets/chat-run.ts`، `services/chat-run/handle-{bridge,ekko-agent}-run.ts`،
  `packages/desktop/src/{main,preload}/index.ts`، `scripts/generate-openapi.mjs`،
  `docs/openapi.json` (مولَّد)، `docs/harness/server-module-boundaries.md`،
  والاختبارات المذكورة أعلاه.
- الأثر على المستخدم: يختفي عنصر «الحيوانات الأليفة» من الشريط الجانبي، ولا يعود
  الحيوان يظهر فوق الصفحات في الويب، ولا تُفتح نافذة الحيوان في سطح المكتب.
  المسارات `/api/studio/pets/*` و`/api/studio/petdex/*` ونطاق المقبس `/pet-state`
  تعود 404 / غير موجودة. تطبيقات الجوال أو النسخ القديمة التي تطلبها ستفشل
  في هذه الطلبات فقط.

## الفحوص
البيئة: Node 24 عبر nvm، worktree محلي من `origin/main` عند `a8823d42`.
- `npm run harness:check`: نجح (Harness check passed + Ekko API docs current).
- `packages/desktop/node_modules/.bin/tsc -p packages/desktop/tsconfig.json --noEmit`: نجح.
- `npm run openapi:generate`: أُعيد توليد `docs/openapi.json` (‎-206 سطرًا، 436 مسارًا، 53 وسمًا، صفر ذكر لـ pet).
- `npx vitest run --maxWorkers=4 --testTimeout=15000` (الحزمة الكاملة): 704 ملفات
  ناجحة، 7 متخطاة، وملف واحد فاشل `tests/server/studio-mcp-autoinject.test.ts`
  (17 اختبارًا). الفشل بيئي لا علاقة له بالتغيير: الاختبار ينشئ مشغّل MCP داخل
  `process.cwd()`، وهنا cwd هو git worktree مرتبط فتُتخطى الحقن عمدًا. أثبتنا ذلك
  بتشغيل الملف نفسه على `origin/main` نظيفًا داخل الـ worktree (نفس 17 فشلًا) وعلى
  checkout عادي (22/22 ناجحة). يُعاد التحقق في CI.
- `npm run build`: نجح (openapi + vue-tsc + vite + server tsc + build-server).
- `PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/e2e/theme.spec.ts --workers=1`: 2/2 ناجحة.
- `git diff --check`: نظيف.
- لم يُنفّذ: بناء حزمة سطح المكتب الكاملة (electron-builder) أو تشغيل Electron
  يدويًا؛ اكتفينا بفحص الأنواع لأن التغيير حذف فقط.

## المخاطر والرجوع
- لا أثر على البيانات المخزنة؛ ملف `active.json` القديم يبقى بلا قارئ. الرجوع
  بعكس الدمج (revert) يعيد كل الملفات المحذوفة.
- توافق: أي عميل قديم (جوال/سطح مكتب قبل هذه النسخة) يطلب مسارات الحيوانات
  سيحصل على 404؛ لا يؤثر على بقية عمله.
- `windowKind` في جسر سطح المكتب أصبح `'main' | 'chat'` فقط.

## التسليم والخطوة التالية
الحالة: محلي على الفرع `chore/remove-pets`، لم يُدفع ولم يُفتح PR. الخطوة
التالية: المالك يراجع، ثم يُدفع الفرع ويُفتح PR إلى `main` ويُدمج في `test`.
بعد فتح PR: التأكد من نجاح `studio-mcp-autoinject` في CI (يعمل من checkout عادي).
