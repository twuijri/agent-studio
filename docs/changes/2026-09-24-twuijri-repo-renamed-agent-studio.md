# رجوع اسم المستودع إلى twuijri/agent-studio
المسؤول: twuijri
الفرع: ci/repo-renamed-agent-studio
الحالة: review

## المشكلة والهدف
قرار المالك (2026-09-24): «نغير الريبو الي هو كور هب ونرجعه ايجنت استديو كريبو ونخلي الداخلي
نفس الاسم كور هب ما عليه لين ينضج شغلنا هنا»، ثم «ايه» على تغيير الاسم. اسم `twuijri/core-hub`
صار للمنتج الجديد. غُيّر اسم المستودع على GitHub، فصار شرط مسار `test-track.yml`
(`github.repository == 'twuijri/core-hub'`) لا يتحقق أبدًا ولا يعمل مسار التجربة.

## القرار والموافقات
- شرط `test-track.yml` يقبل `twuijri/agent-studio` (ويبقى القديم كما في `personal-image.yml`).
- روابط `package.json` إلى المستودع باسمه الجديد.
- الصورة: تُنشر في `ghcr.io/twuijri/agent-studio` (`release.yml` يأخذ اسم المستودع). سطر
  الصورة في `README.md` صار هذا الاسم، وأضيف تحديث مؤرّخ في `deploy/README.md` و
  `docs/CORE-HUB-BRANDING.md` دون تعديل تاريخ القرار السابق. المالك يغيّر ستاكه بنفسه
  («ادخل على ستاكي واغيره قبل يحدث هو»)، ثم يحذف حزمة `core-hub` القديمة.
- `personal-image.yml` (مسار الصورة الشخصية) كان يختار بين `core-hub` و`core-hub-test` بأسماء
  ثابتة؛ صار الخيار العام `agent-studio` (والافتراضي)، وحُذف `core-hub` من الخيارات كي لا تُنشر
  صورة هذه النسخة بالخطأ في اسم المنتج الجديد. `latest` محجوز لـ`agent-studio`، والفحص يتوقع
  أن تكون حزمته عامة كما كانت `core-hub`. مسار التجربة `core-hub-test` لم يتغيّر.
- روابط تعمل فعلًا كانت تشير إلى `twuijri/core-hub`، وبعد أن يأخذ المنتج الجديد الاسم ستشير إليه:
  إشعار الإصدارات في تطبيق سطح المكتب (`release-notice-core.ts` يقرأ آخر إصدار من API الريبو)،
  ورابطا المستودع في القائمة الجانبية، و`homepage` تطبيق سطح المكتب، و`HTTP-Referer` الذي يرسله
  Hermes إلى OpenRouter. كلها صارت `twuijri/agent-studio`.
- اختبار الهوية (`tests/client/core-hub-branding.test.ts`) كان يثبّت `core-hub` حزمةً عامة؛ صار
  يثبّت `agent-studio` افتراضيًا وأن الخيارين `agent-studio` و`core-hub-test` فقط.
- الهوية داخل النسخة (كور هب) لا تتغيّر.

## الملفات والتأثير
`.github/workflows/test-track.yml`، `.github/workflows/personal-image.yml`، `package.json`، `packages/desktop/src/main/release-notice-core.ts`، `packages/desktop/package.json`، `packages/client/src/components/layout/AppSidebar.vue`، `packages/server/src/modules/hermes/services/bridge/manager.ts`، واختباراتها، `README.md`، `deploy/README.md`،
`docs/CORE-HUB-BRANDING.md`. روابط نصية فقط في الكود، بلا تغيير سلوك آخر.

## الفحوص
```
$ node scripts/harness-check.mjs
Harness check passed
$ npx vitest run tests/server/agent-bridge-manager.test.ts tests/desktop/release-notice.test.ts tests/client/core-hub-branding.test.ts
      Tests  46 passed (46)
# أول تشغيل في CI فشل في اختبار الهوية قبل تحديثه:
AssertionError: expected 'name: Personal Core Hub image…' to contain 'const expected = '${{ inputs.image_repo }}' === 'core-hub' …'
```

## المخاطر والرجوع
- لو سحب الستاك صورة `ghcr.io/twuijri/core-hub:latest` بعد أن ينشر المنتج الجديد بهذا الاسم،
  لسحب المنتج الجديد؛ لذلك لا تُنشر صورة `latest` بهذا الاسم قبل تأكيد المالك تغيير ستاكه.
- الرجوع: استرجاع الـcommit وإعادة اسم المستودع.

## التسليم والخطوة التالية
PR إلى `main`، ثم دمج الفرع نفسه في `test` حسب القاعدة. بعده صورة `agent-studio` بطلب المالك،
ثم يغيّر المالك ستاكه.
