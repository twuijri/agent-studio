# إصلاح «An object could not be cloned» عند تفعيل برنامج في «برامج الجهاز»

المسؤول: twuijri
الفرع: fix/computer-apps-clone
الحالة: review

## المشكلة والهدف
عند تفعيل أي برنامج في صفحة «برامج الجهاز» (v1.0.1) يظهر الخطأ «An object could
not be cloned» ولا يُحفظ شيء. السبب أن الواجهة تمرر مصفوفة فيها كائنات Vue
تفاعلية (Proxy) إلى جسر سطح المكتب، والنسخ البنيوي عبر IPC يرفضها.

## القرار والموافقات
- الموافقة: المالك (2026-09-19، أرسل صورة الخطأ).
- الواجهة تحوّل القائمة إلى JSON صرف قبل `setApps`، والـ preload يفعل الشيء نفسه
  احتياطاً لأي مستدعٍ آخر. اختبار مصدر يمنع التراجع.

## الملفات والتأثير
`packages/client/src/views/hermes/AppConnectionsView.vue`،
`packages/desktop/src/preload/index.ts`، `tests/client/computer-apps-ipc-clone.test.ts`.

## الفحوص
- `vue-tsc`، فحص أنواع سطح المكتب، الاختبار الجديد، `tests/desktop/device-agent-apps`،
  `git diff --check`.
- يحتاج نسخة جديدة من التطبيق (preload) وصورة جديدة (الواجهة) ليصل للمستخدم.

## المخاطر والرجوع
- لا سلوك جديد. الرجوع بإزالة التحويل.

## التسليم والخطوة التالية
طلب دمج إلى `main` ثم `test`؛ يدخل في v1.0.2 مع #35.
