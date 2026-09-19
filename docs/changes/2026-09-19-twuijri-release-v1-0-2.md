# إصدار v1.0.2

المسؤول: twuijri
الفرع: chore/release-v1-0-2
الحالة: review

## المشكلة والهدف
إصلاحان لصفحة «برامج الجهاز» ظهرا في v1.0.2 عند تجربة المالك: خطأ «An object
could not be cloned» عند تفعيل برنامج (#36)، والبند المكرر في درج الإعدادات (#35).
كلاهما يحتاج نسخة تطبيق وصورة جديدتين.

## القرار والموافقات
- الموافقة: المالك (2026-09-19، طلب إصلاح خطأ التفعيل).
- رفع رقم النسخة إلى 1.0.2 في `package.json` و`packages/desktop/package.json`
  وملفي القفل فقط؛ لا تغيير في الكود.
- بعد الدمج: وسم `v1.0.2` على رأس `main`، إصدار GitHub بملاحظات مولّدة، ثم
  `desktop-release.yml` (كل المنصات) و`webui-release.yml` و`personal-image.yml`
  بـ `source_ref=v1.0.2` و`publish_latest=true`.

## الملفات والتأثير
`package.json`، `package-lock.json`، `packages/desktop/package.json`،
`packages/desktop/package-lock.json`.

## الفحوص
- `npm run harness:check`، فحص القفل في CI، `git diff --check`.

## المخاطر والرجوع
- لا سلوك جديد. الرجوع: حذف الوسم والإصدار.

## التسليم والخطوة التالية
طلب دمج إلى `main`؛ بعد الدمج يُنشأ الوسم والإصدار وتُبنى التطبيقات والصورة.

