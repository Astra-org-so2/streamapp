package com.streamapp.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.streamapp.core.database.entity.LayerType

enum class WidgetCategory {
    ALERTS_DONATIONS,
    CHAT_INTERACTION,
    SOCIALS_TEXT,
    OVERLAYS_FRAMES
}

data class WidgetTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: WidgetCategory,
    val type: LayerType,
    val defaultName: String,
    val defaultContent: String,
    val placeholder: String,
    val defaultWidth: Float = 0.8f,
    val defaultHeight: Float = 0.25f,
    val defaultAlpha: Float = 1.0f,
    val iconName: String = "language"
)

object WidgetTemplatesCatalog {
    val templates = listOf(
        // Alerts & Donations
        WidgetTemplate(
            id = "da_alerts",
            title = "DonationAlerts AlertBox",
            description = "Оповещения о донатах с анимацией и звуком",
            category = WidgetCategory.ALERTS_DONATIONS,
            type = LayerType.WEB,
            defaultName = "DonationAlerts",
            defaultContent = "https://www.donationalerts.com/widget/alerts?token=",
            placeholder = "Вставьте ссылку на виджет оповещений",
            defaultWidth = 0.9f,
            defaultHeight = 0.3f
        ),
        WidgetTemplate(
            id = "da_goal",
            title = "DonationAlerts Сбор средств",
            description = "Прогресс-бар сбора на цель стрима",
            category = WidgetCategory.ALERTS_DONATIONS,
            type = LayerType.WEB,
            defaultName = "Goal Bar",
            defaultContent = "https://www.donationalerts.com/widget/goal?token=",
            placeholder = "Вставьте ссылку на шкалу сбора",
            defaultWidth = 0.85f,
            defaultHeight = 0.12f
        ),
        WidgetTemplate(
            id = "streamlabs_alerts",
            title = "Streamlabs AlertBox",
            description = "Алерты о фолловерах, донатах и подписках",
            category = WidgetCategory.ALERTS_DONATIONS,
            type = LayerType.WEB,
            defaultName = "Streamlabs Alerts",
            defaultContent = "https://streamlabs.com/alert-box/v3/",
            placeholder = "Вставьте URL виджета Streamlabs",
            defaultWidth = 0.9f,
            defaultHeight = 0.3f
        ),

        // Chat & Interaction
        WidgetTemplate(
            id = "live_chatbox",
            title = "Transparent Stream Chat",
            description = "Прозрачное окно чата поверх видеопотока",
            category = WidgetCategory.CHAT_INTERACTION,
            type = LayerType.WEB,
            defaultName = "Live Chat Overlay",
            defaultContent = "https://streamlabs.com/widgets/chat-box/v1/",
            placeholder = "Вставьте ссылку на виджет чата (Streamlabs/Botrix)",
            defaultWidth = 0.45f,
            defaultHeight = 0.55f,
            defaultAlpha = 0.9f
        ),
        WidgetTemplate(
            id = "stream_timer",
            title = "Таймер начала стрима",
            description = "Обратный отсчет до старта трансляции",
            category = WidgetCategory.CHAT_INTERACTION,
            type = LayerType.TEXT,
            defaultName = "Starting Soon Timer",
            defaultContent = "Стрим начнется через: 05:00",
            placeholder = "Текст или длительность таймера",
            defaultWidth = 0.8f,
            defaultHeight = 0.1f
        ),

        // Socials & Text
        WidgetTemplate(
            id = "tg_social",
            title = "Telegram Канал",
            description = "Стильная плашка со ссылкой на ваш Telegram",
            category = WidgetCategory.SOCIALS_TEXT,
            type = LayerType.TEXT,
            defaultName = "Telegram Tag",
            defaultContent = "✈️ Telegram: @my_channel",
            placeholder = "Например: ✈️ Telegram: @my_stream",
            defaultWidth = 0.6f,
            defaultHeight = 0.08f
        ),
        WidgetTemplate(
            id = "twitch_social",
            title = "Twitch / Kick Никнейм",
            description = "Плашка с названием вашего канала",
            category = WidgetCategory.SOCIALS_TEXT,
            type = LayerType.TEXT,
            defaultName = "Streamer Tag",
            defaultContent = "🟣 Twitch: twitch.tv/streamer",
            placeholder = "Например: 🟣 Twitch: username",
            defaultWidth = 0.65f,
            defaultHeight = 0.08f
        ),
        WidgetTemplate(
            id = "custom_text",
            title = "Пользовательский текст",
            description = "Любая надпись, правила стрима, расписание",
            category = WidgetCategory.SOCIALS_TEXT,
            type = LayerType.TEXT,
            defaultName = "Custom Text",
            defaultContent = "Добро пожаловать на стрим!",
            placeholder = "Введите текст для отображения",
            defaultWidth = 0.7f,
            defaultHeight = 0.1f
        ),

        // Overlays & Frames
        WidgetTemplate(
            id = "webcam_neon_frame",
            title = "Неоновая рамка для камеры",
            description = "Светящаяся киберпанк рамка для видоискателя",
            category = WidgetCategory.OVERLAYS_FRAMES,
            type = LayerType.TEXT,
            defaultName = "Neon Border",
            defaultContent = "⚡ [ STREAMING LIVE ] ⚡",
            placeholder = "Название оверлея",
            defaultWidth = 0.5f,
            defaultHeight = 0.08f
        ),
        WidgetTemplate(
            id = "custom_banner",
            title = "Картинка / Спонсорский баннер",
            description = "Логотип, спонсорский баннер или PNG рамка",
            category = WidgetCategory.OVERLAYS_FRAMES,
            type = LayerType.IMAGE,
            defaultName = "Sponsor Banner",
            defaultContent = "https://picsum.photos/400/200",
            placeholder = "URL или путь к PNG/JPG файлу",
            defaultWidth = 0.4f,
            defaultHeight = 0.2f
        )
    )
}
