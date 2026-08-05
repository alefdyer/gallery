# План реализации: Предотвращение перезапуска Галереи через launchMode=singleTask

## Обзор задачи
Исключить перезапуск приложения при повторном открытии или возврате из приложения Камера.

## Причина
При значении `launchMode="singleTop"` повторный запуск Галереи из лаунчера/системной панели после съёмки в Камере сопровождается интентом `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`, из-за чего Android сбрасывал стек и пересоздавал `MainActivity` с нуля через `onCreate`.

## Решение

### 1. Перевод на `android:launchMode="singleTask"` (`AndroidManifest.xml`)
Установить для `MainActivity` режим `android:launchMode="singleTask"`. В этом режиме Android нативно сохраняет главный экземпляр активности в системном стеке задач. При возврате из Камеры или нажатии на иконку приложения система гарантированно возвращает существующий экран без пересоздания активности.

### 2. Добавление `onNewIntent` в `MainActivity.kt`
Переопределить `onNewIntent(intent)` для корректной приемки повторных вызовов активности без перезапуска состояния.

## Предлагаемые изменения

### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/AndroidManifest.xml)
- Изменить `android:launchMode="singleTop"` на `android:launchMode="singleTask"`.

### [MODIFY] [MainActivity.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/MainActivity.kt)
- Добавить обработку `onNewIntent(intent)`.

## План верификации
- Сборка проекта через Gradle (`app:assembleDebug`).
- Проверка: свернуть Галерею, открыть Камеру, сделать фото, вернуться в Галерею — экран не перезапускается и мгновенно остается открытым.
