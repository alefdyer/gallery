# План реализации: Редизайн всплывающего меню и обновление иконки настроек

## Обзор задачи
Переделать выпадающее меню кнопки «Меню» во всплывающую шторку (Bottom Sheet) со стильной сеткой кнопок 4x2 с белыми кружками и подписями, идентичную предоставленному скриншоту, а также обновить векторную иконку настроек.

## Предлагаемые изменения

### 1. Обновление вектора иконки настроек
#### [MODIFY] [settings.xml](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/res/drawable/settings.xml)
- Заменить геометрию пути вектора на современную аккуратную иконку шестеренки Material 3.

### 2. Новая всплывающая шторка меню
#### [NEW] [MenuBottomSheet.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/ui/component/MenuBottomSheet.kt)
- Создать компонент на базе `ModalBottomSheet` с округлой формой верхней части (`RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`).
- Сетка из 4 колонок (4x2):
  - **Строка 1**: Видео, Избранное, Последние, Типы съемки.
  - **Строка 2**: Личный альбом, Корзина, Настройки, Студия.
- Элемент сетки: белый круг (`CircleShape`, `Surface`) с иконкой по центру и текстом под кружком.

### 3. Подключение меню
#### [MODIFY] [ViewModeBar.kt](file:///C:/Users/Alexander/AndroidStudioProjects/gallery/app/src/main/java/com/asinosoft/gallery/ui/component/ViewModeBar.kt)
- При клике на третью кнопку «Меню» вызывать `MenuBottomSheet`. По клику на пункт «Настройки» открывать экран настроек.

## План верификации
- Сборка проекта через Gradle (`app:assembleDebug`).
- Ручная проверка открытия всплывающего меню и переход к настройкам.
