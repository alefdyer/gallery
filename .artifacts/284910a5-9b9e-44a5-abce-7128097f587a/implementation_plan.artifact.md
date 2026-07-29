# План реализации: Окно прогресса фоновой предзагрузки миниатюр с кнопкой закрытия

## Обзор задачи
Создать аккуратное всплывающее информационное окно (Snackbar / Card), показывающее прогресс фонового кэширования миниатюр при запуске с возможностью его закрытия пользователем по нажатию на крестик.

## Архитектура и изменения

### 1. Отслеживание прогресса предзагрузки (`ThumbnailCache.kt`)
- Добавить реактивный поток `val preloadProgress: StateFlow<PreloadProgress>` со следующими полями:
  - `isPreloading: Boolean` — активен ли процесс кэширования.
  - `current: Int` — количество обработанных файлов.
  - `total: Int` — общее количество файлов в текущей очереди.
- При вызове `preload` обновлять счетчик `current` и эмитить новые состояния.

### 2. Запуск предзагрузки (`MediaService.kt`)
- Передавать массив элементов для предкэширования в `ThumbnailCache.preloadBatch(...)`.

### 3. Компонент всплывающего окна прогресса (`MainView.kt`)
- В нижнюю часть экрана `MainView` добавить плавающую карточку (`Surface` с закругленными углами):
  - Полоса прогресса (`LinearProgressIndicator`).
  - Текстовый индикатор: *"Кэширование миниатюр: X из Y"*.
  - Кнопка-крестик (`IconButton`) для мгновенного закрытия/скрытия плашки.

## Предлагаемые изменения

### [MODIFY] [ThumbnailCache.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/data/ThumbnailCache.kt)
- Добавить `PreloadProgress` и управление состоянием `preloadProgress`.

### [MODIFY] [MediaService.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/data/MediaService.kt)
- Интегрировать передачу списка файлов в `ThumbnailCache.preloadBatch`.

### [MODIFY] [MainView.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/ui/MainView.kt)
- Добавить всплывающую панель прогресса с кнопкой закрытия (крестиком).

## План верификации
- Сборка проекта через Gradle (`app:assembleDebug`).
- Проверка работы индикатора прогресса и реакции кнопки закрытия.
