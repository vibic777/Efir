package ru.efir.core.input

/**
 * Маппинг координат виджета (ПК) в нормализованные координаты кадра (0..1)
 * с учётом масштабирования (letterbox / contentScale).
 *
 * Кадр может отображаться не на всю область виджета: при сохранении пропорций
 * сверху/снизу или слева/справа появляются поля. Этот класс вычисляет
 * [contentScale]-параметры для `ContentScale.Fit` и корректно переводит
 * позицию клика в координаты изображения.
 *
 * @param frameWidth/frameHeight размеры исходного кадра в пикселях.
 * @param viewWidth/viewHeight размеры области отображения на ПК.
 */
data class FrameMapping(
    val frameWidth: Int,
    val frameHeight: Int,
    val viewWidth: Float,
    val viewHeight: Float,
) {
    /** Масштаб «вписать» (ContentScale.Fit) по обеим осям. */
    val scale: Float = run {
        if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) 1f
        else minOf(viewWidth / frameWidth, viewHeight / frameHeight)
    }

    /** Ширина/высота кадра после масштабирования (в координатах виджета). */
    val scaledWidth: Float = frameWidth * scale
    val scaledHeight: Float = frameHeight * scale

    /** Смещение изображения внутри виджета (letterbox). */
    val offsetX: Float = (viewWidth - scaledWidth) / 2f
    val offsetY: Float = (viewHeight - scaledHeight) / 2f

    /** Лежит ли точка (в координатах виджета) внутри изображения. */
    fun contains(viewX: Float, viewY: Float): Boolean =
        viewX >= offsetX && viewX <= offsetX + scaledWidth &&
            viewY >= offsetY && viewY <= offsetY + scaledHeight

    /**
     * Преобразует позицию в координатах виджета в нормализованные координаты
     * кадра (0..1), обрезая по границам изображения.
     */
    fun toNormalizedX(viewX: Float): Float =
        if (scaledWidth <= 0f) 0f else ((viewX - offsetX) / scaledWidth).coerceIn(0f, 1f)

    fun toNormalizedY(viewY: Float): Float =
        if (scaledHeight <= 0f) 0f else ((viewY - offsetY) / scaledHeight).coerceIn(0f, 1f)
}
