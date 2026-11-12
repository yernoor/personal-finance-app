# Keep every widget class apart: R8 otherwise merges the MoneyWidget subclasses into one class, and Glance (which files placed widgets by
# class name) then treats every placed widget as every widget (a Quick add widget showed Spent this month).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
