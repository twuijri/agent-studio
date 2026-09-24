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
- الهوية داخل النسخة (كور هب) لا تتغيّر.

## الملفات والتأثير
`.github/workflows/test-track.yml`، `package.json`، `README.md`، `deploy/README.md`،
`docs/CORE-HUB-BRANDING.md`. لا كود ولا صورة.

## الفحوص
```
$ node scripts/harness-check.mjs
Harness check passed
```

## المخاطر والرجوع
- لو سحب الستاك صورة `ghcr.io/twuijri/core-hub:latest` بعد أن ينشر المنتج الجديد بهذا الاسم،
  لسحب المنتج الجديد؛ لذلك لا تُنشر صورة `latest` بهذا الاسم قبل تأكيد المالك تغيير ستاكه.
- الرجوع: استرجاع الـcommit وإعادة اسم المستودع.

## التسليم والخطوة التالية
PR إلى `main`، ثم دمج الفرع نفسه في `test` حسب القاعدة. بعده صورة `agent-studio` بطلب المالك،
ثم يغيّر المالك ستاكه.
