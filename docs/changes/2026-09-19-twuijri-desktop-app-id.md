# معرّف تطبيق سطح المكتب: com.twuijri.corehub

المسؤول: twuijri
الفرع: chore/desktop-app-id
الحالة: review

## المشكلة والهدف
توحيد هوية التطبيق على كل المنصات بمعرّف المالك بعد اشتراك آبل: الجوال صار
`com.twuijri.corehub`، والمالك يريد سطح المكتب على المعرّف نفسه بدل
`us.i3u.agentstudio` الموروث.

## القرار والموافقات
- الموافقة: المالك (2026-09-19: «حتى الماك خليته com.twuijri.corehub»).
- `appId: com.twuijri.corehub` في `electron-builder.yml`، وقناة التست
  `com.twuijri.corehub.test`. مجلد البيانات لا يتغير (يعتمد على اسم المنتج لا
  المعرّف)، لكن أذونات macOS (تسجيل الشاشة/إمكانية الوصول) مرتبطة بالمعرّف
  فستُطلب مرة أخرى بعد التحديث. معرّف ويندوز `AppUserModelID` منفصل ولم يتغير.

## الملفات والتأثير
`packages/desktop/electron-builder.yml`، `.github/workflows/test-track.yml`،
`tests/client/core-hub-branding.test.ts`، `tests/desktop/channel.test.ts`،
`packages/desktop/README.md`، `docs/CORE-HUB-BRANDING.md`.

## الفحوص
- اختبارا العلامة والقناة، تحليل YAML، `git diff --check`.

## المخاطر والرجوع
- إعادة منح أذونات macOS بعد أول تشغيل. الرجوع بإعادة المعرّف القديم.

## التسليم والخطوة التالية
طلب دمج إلى `main` ثم `test`؛ يسري على الإصدار القادم.
