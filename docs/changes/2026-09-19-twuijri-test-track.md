# مسار تست مستقل: تطبيق «Core Hub Test» وصورة خاصة بلا رفع للنسخة

المسؤول: twuijri
الفرع: feat/test-track
الحالة: review

## المشكلة والهدف
كل إصلاح صغير كان يتطلب رفع رقم النسخة وإصداراً كاملاً ليصل إلى المالك للتجربة.
المالك يريد أن يبقى مسار التست له وحده (لا يأخذه أحد)، بتطبيق سطح مكتب باسم
مختلف لا يمس التطبيق الأصلي وبياناته، وصورة مختلفة، وكل ذلك تلقائيًا؛ والإصدار
الرسمي فقط عند اكتمال دفعة.

## القرار والموافقات
- الموافقة: المالك (2026-09-19: «خل التست لي لحالي… تطبيق سطح المكتب يكون اسمه
  مختلف… يبني كل شي تست تطبيقات وامج وكل شي مختلف»).
- **قناة البناء في التطبيق:** `channel.ts` يقرأ `corehubChannel` من
  `package.json` المحزوم. بناء التست يُحقن عبر `extraMetadata`:
  `productName="Core Hub Test"`، `appId=us.i3u.agentstudio.test`،
  `version=<pkg>-test.<run>`. الاسم المختلف يعطي مجلد بيانات مستقلًا (هوية جهاز
  ووضع ربط مستقلين)، ومعرّف ويندوز مختلف. عناوين الحوارات وشريط النظام تستخدم
  `app.getName()`. تنبيه النسخة الجديدة معطّل في قناة التست.
- **سير عمل `test-track.yml`:** يعمل بعد نجاح `Playwright` على فرع `test` (أو
  يدويًا). يبني التطبيقات لماك (arm64/x64) وويندوز ولينكس كملفات تشغيل خاصة في
  Actions (14 يومًا)، ويشغّل `personal-image.yml` بـ `image_repo=core-hub-test`
  و`preview_tag=test` و`publish_latest=false`.
- **`personal-image.yml`:** مدخل `image_repo` (اختيار بين `core-hub` و
  `core-hub-test`)، يرفض ترقية حزمة التست إلى `latest`، وتقرير الرؤية يتوقع
  «عامة» للحزمة الرسمية و«خاصة» لحزمة التست (تحذير فقط).
- **القاعدة:** بندا 5 و6 في §9-ب من `docs/TEAM-RULES.md` صارا للمسار التلقائي،
  والإصدار الرسمي عند اكتمال دفعة فقط.
- الحزمة `core-hub-test` تُنشأ خاصة تلقائيًا عند أول دفع؛ ستاك التجربة يسحبها
  باعتماد المالك.

## الملفات والتأثير
`packages/desktop/src/main/channel.ts` (جديد)، `index.ts`، `release-notice.ts`،
`.github/workflows/test-track.yml` (جديد)، `.github/workflows/personal-image.yml`،
`tests/desktop/channel.test.ts` (جديد)، `tests/client/core-hub-branding.test.ts`،
`docs/TEAM-RULES.md`، `packages/desktop/README.md`.

## الفحوص
- `tests/desktop/channel.test.ts` (قراءة القناة، الفصل عن التطبيق الأصلي، حقن
  البيانات في سير العمل)، `core-hub-branding`، `tests/personal`، تحليل YAML
  للسيرين، فحص أنواع سطح المكتب، `harness:check`، `git diff --check`.
- التحقق الفعلي: أول تشغيل لـ `test-track.yml` بعد دمج هذا الطلب في `test`.

## المخاطر والرجوع
- بناء التست يستهلك دقائق Actions مع كل دفعة خضراء إلى `test`
  (أربعة أنظمة)؛ يمكن تقليص المصفوفة لاحقًا. الرجوع: حذف سير العمل؛ التطبيق
  الرسمي لا يتأثر لأن القناة الافتراضية `stable`.

## التسليم والخطوة التالية
طلب دمج إلى `main` ثم `test`؛ بعدها يتحول ستاك التجربة إلى
`ghcr.io/twuijri/core-hub-test:test` ويثبّت المالك «Core Hub Test» من ملفات
التشغيل.
