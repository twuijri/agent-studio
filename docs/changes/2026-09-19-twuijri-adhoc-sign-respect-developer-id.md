# خطاف التوقيع ad-hoc يتراجع أمام توقيع Developer ID الحقيقي

المسؤول: twuijri
الفرع: fix/adhoc-sign-respect-developer-id
الحالة: review

## المشكلة والهدف
أول بناء بشهادة Developer ID الحقيقية (بعد اشتراك المالك) وقّع التطبيق ووثّقه
بنجاح، ثم أعاد خطاف `adhoc-sign-macos.mjs` توقيعه ad-hoc فأبطل التوقيع الحقيقي
والتوثيق. سببان: (1) بيئة CI تصدّر `CSC_KEYCHAIN` لا `CSC_LINK`، فظن الخطاف أن
لا هوية؛ (2) `codesign -dv` يطبع تفاصيل التوقيع على stderr، والفحص كان يقرأ
stdout فقط فلم يجد `Authority=Developer ID Application`.

## القرار والموافقات
- الموافقة: المالك (ضمن إعداد التوقيع 2026-09-19).
- `shouldAdhocSign` يعتبر `CSC_KEYCHAIN` هوية مضبوطة، و`isSignedByDeveloperId`
  يقرأ stdout وstderr عبر `spawnSync` (`hasDeveloperIdSignature` مُصدَّرة
  ومختبرة).

## الملفات والتأثير
`packages/desktop/scripts/adhoc-sign-macos.mjs`، `tests/desktop/adhoc-sign-macos.test.ts`.

## الفحوص
- الاختبارات المحدّثة (حالة `CSC_KEYCHAIN`، تحليل مخرجات codesign)،
  `git diff --check`. التحقق الفعلي: تشغيل مسار التست بعد الدمج والتأكد من رسالة
  «already carries a Developer ID signature; nothing to do».

## المخاطر والرجوع
- بلا شهادة يبقى السلوك السابق (توقيع ad-hoc). الرجوع بإعادة الفحص القديم.

## التسليم والخطوة التالية
طلب دمج إلى `main` ثم `test`؛ يجب دمجه قبل أي إصدار ماك رسمي.
