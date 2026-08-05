# План реализации: Оптимизация прокрутки списка за ползунок (Scrollbar)

## Обзор задачи
Устранить лаги и задержки при прокрутке сетки фотографий пальцем за боковой ползунок (scroll indicator).

## Причины замедления
1. **Каскад корутин `scrollBy` на каждый пиксель**: На каждый пиксель смещения пальца создавалась новая корутина с вызовом `scrollBy`. В `LazyGridState` вызовы `scrollBy` с приоритетом `UserInput` отменяли предыдущие корутины, приводя к постоянному перезапуску потоков и микролагам UI.
2. **Накладные расходы при получении даты плашки**: Для показа даты над ползунком на каждый пиксель вызывался `lazyGridState.layoutInfo.visibleItemsInfo.minByOrNull { ... }`, производивший перебор видимых ячеек.
3. **Нарушение `derivedStateOf`**: В `remember(..., scrollOffset)` передача постоянно меняющегося `scrollOffset` приводила к пересозданию стейта на каждый пиксель.

## Предлагаемые изменения

### [MODIFY] [LazyGridScrollIndicator.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/ui/component/LazyGridScrollIndicator.kt)
- Перевести перетаскивание ползунка на вызов `lazyGridState.scrollToItem(targetIndex)` напрямую при изменении целевого индекса.
- Оптимизировать расчет даты метки: брать дату напрямую из списка по `targetIndex` (`listItems.getOrNull(targetIndex)?.date`), что работает мгновенно O(1).
- Убрать лишние накладные вызовы корутин `scrollBy`.

## План верификации
- Сборка проекта через Gradle (`app:assembleDebug`).
- Ручная проверка плавности перемещения ползунка с реакцией 60–120 FPS.
