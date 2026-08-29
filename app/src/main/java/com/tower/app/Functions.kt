package com.tower.app

/** Разделы панели. */
enum class Section(val title: String, val hint: String) {
    SPEED("СКОРОСТЬ", "Ускорение любого видео"),
    PLAYBACK("ВОСПРОИЗВЕДЕНИЕ", "Управление плеером"),
    VIDEO("ВИДЕО", "Работает поверх любого приложения"),
    YOUTUBE("YOUTUBE", "Реклама, Shorts, фокус"),
    AUDIO("ЗВУК", "Системно — для любого приложения"),
    SCREEN("ЭКРАН", "Комфорт и защита")
}

/** Одна функция панели. */
data class Fn(
    val id: String,
    val title: String,
    val icon: String,
    val section: Section,
    val desc: String,
    val toggle: Boolean = false,
    val big: Boolean = false,
    val systemwide: Boolean = false
)

/** Реестр из 30 функций (5 из них — скорость). */
object Functions {

    val all: List<Fn> = listOf(
        // ── 1. СКОРОСТЬ (5) ──────────────────────────────────────────────
        Fn("spd_2", "2X", "2X", Section.SPEED, "Ускорение видео в 2 раза", big = true),
        Fn("spd_3", "3X", "3X", Section.SPEED, "Ускорение видео в 3 раза", big = true),
        Fn("spd_4", "4X", "4X", Section.SPEED, "Ускорение видео в 4 раза", big = true),
        Fn("spd_5", "5X", "5X", Section.SPEED, "Ускорение видео в 5 раз", big = true),
        Fn("spd_10", "10X", "10X", Section.SPEED, "Ускорение видео в 10 раз", big = true),

        // ── 2. ВОСПРОИЗВЕДЕНИЕ (5) ───────────────────────────────────────
        Fn("pb_play", "Плей", "▶", Section.PLAYBACK, "Пуск / пауза"),
        Fn("pb_back", "−10 сек", "⏪", Section.PLAYBACK, "Назад на 10 секунд"),
        Fn("pb_fwd", "+10 сек", "⏩", Section.PLAYBACK, "Вперёд на 10 секунд"),
        Fn("pb_ab", "A-B повтор", "🔂", Section.PLAYBACK, "Зациклить отрезок A-B", toggle = true),
        Fn("pb_loop", "Зациклить", "🔁", Section.PLAYBACK, "Повторять видео бесконечно", toggle = true),

        // ── 3. ВИДЕО (5) ─────────────────────────────────────────────────
        Fn("vd_full", "Полный экран", "🖥", Section.VIDEO, "Развернуть окно на весь экран", toggle = true),
        Fn("vd_mini", "Мини-окно", "▣", Section.VIDEO, "Свернуть в маленькое окно-картинку", toggle = true),
        Fn("vd_shot", "Снимок экрана", "📷", Section.VIDEO, "Мгновенный скриншот в галерею", systemwide = true),
        Fn("vd_rec", "Запись экрана", "⏺", Section.VIDEO, "Запись видео с экрана", toggle = true, systemwide = true),
        Fn("vd_audio", "Только звук", "🎧", Section.VIDEO, "Слушать видео без картинки", toggle = true),

        // ── 4. YOUTUBE (5) ───────────────────────────────────────────────
        Fn("yt_skip", "Пропустить рекламу", "⏭", Section.YOUTUBE, "Сразу перемотать рекламный ролик"),
        Fn("yt_autoskip", "Авто-пропуск", "⚡", Section.YOUTUBE, "Самостоятельно жмёт «Пропустить» и ускоряет рекламу", toggle = true),
        Fn("yt_focus", "Режим фокуса", "🎯", Section.YOUTUBE, "Скрыть рекомендации, комментарии и чат", toggle = true),
        Fn("yt_shorts", "Скрыть Shorts", "🚫", Section.YOUTUBE, "Убрать короткие видео из ленты", toggle = true),
        Fn("yt_open", "Открыть ссылку", "🔗", Section.YOUTUBE, "Открыть URL или вставить из буфера обмена"),

        // ── 5. ЗВУК (5) ──────────────────────────────────────────────────
        Fn("au_mute", "Выключить звук", "🔇", Section.AUDIO, "Полный мьют системы", toggle = true, systemwide = true),
        Fn("au_boost", "Усиление", "🔊", Section.AUDIO, "Усилить звук сверх максимума (+3 дБ за шаг)", systemwide = true),
        Fn("au_reset", "Сброс звука", "↺", Section.AUDIO, "Вернуть обычный звук и громкость", systemwide = true),
        Fn("au_voice", "Режим «Голос»", "🗣", Section.AUDIO, "Поднять речь, убрать гул и бас", toggle = true, systemwide = true),
        Fn("au_vol", "Громкость", "🔈", Section.AUDIO, "Ползунок системной громкости", systemwide = true),

        // ── 6. ЭКРАН (5) ─────────────────────────────────────────────────
        Fn("sc_awake", "Не гаснет", "💡", Section.SCREEN, "Экран не выключается во время просмотра", toggle = true, systemwide = true),
        Fn("sc_night", "Ночной фильтр", "🌙", Section.SCREEN, "Тёплый фильтр поверх всего", toggle = true, systemwide = true),
        Fn("sc_bright", "Яркость 100%", "🔆", Section.SCREEN, "Поднять яркость экрана на максимум", toggle = true, systemwide = true),
        Fn("sc_sleep", "Таймер сна", "⏰", Section.SCREEN, "Остановить видео через 15/30/45/60/90 минут", systemwide = true),
        Fn("sc_lock", "Блокировка", "🔒", Section.SCREEN, "Заблокировать случайные касания", toggle = true)
    )

    val grouped: List<Pair<Section, List<Fn>>> =
        Section.values().map { s -> s to all.filter { it.section == s } }

    fun find(id: String): Fn? = all.firstOrNull { it.id == id }

    const val COUNT = 30
}
