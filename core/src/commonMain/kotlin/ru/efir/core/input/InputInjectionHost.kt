package ru.efir.core.input

import ru.efir.core.media.deviceScreenSize
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.Ack
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.InputKey
import ru.efir.core.protocol.InputMultiTouch
import ru.efir.core.protocol.InputSwipe
import ru.efir.core.protocol.InputTap
import ru.efir.core.protocol.Message

/**
 * Принимающая сторона инъекции ввода (Android-хост).
 *
 * Регистрирует RPC-обработчики [InputTap], [InputSwipe], [InputMultiTouch],
 * [InputText] и [InputKey], преобразует нормализованные координаты в пиксели
 * экрана через [ScreenGeometry] и делегирует платформенному [InputInjector].
 */
class InputInjectionHost(
    private val session: EfirSession,
    private val injector: InputInjector = createInputInjector(),
    /**
     * Куда отдать присланный текст, когда вставить его некуда: управление этим устройством
     * не включено. Компьютер показывает такой текст окном в приложении.
     */
    private val onTextShown: ((String) -> Unit)? = null,
) {

    /** Последняя известная геометрия экрана (обновляется при старте трансляции). */
    private var geometry: ScreenGeometry? = null

    /** Устанавливает/обновляет геометрию экрана для преобразования координат. */
    fun updateGeometry(width: Int, height: Int) {
        val g = ScreenGeometry(width, height)
        geometry = g
        injector.updateGeometry(g)
    }

    /**
     * Инициализирует геометрию по физическому экрану устройства, если она
     * ещё не задана. Возвращает true, если геометрия установлена.
     */
    fun ensureGeometryFromDevice(): Boolean {
        if (geometry != null) return true
        val size = deviceScreenSize() ?: return false
        updateGeometry(size.width, size.height)
        return true
    }

    /** Регистрирует обработчики на сессии. */
    fun attach() {
        session.rpc.onRequest<InputTap> { tap -> handleTap(tap) }
        session.rpc.onRequest<InputSwipe> { swipe -> handleSwipe(swipe) }
        session.rpc.onRequest<InputMultiTouch> { multi -> handleMulti(multi) }
        session.rpc.onRequest<ru.efir.core.protocol.InputText> { text -> handleText(text) }
        session.rpc.onRequest<InputKey> { key -> handleKey(key) }
    }

    /** Готов ли инжектор (служба доступности включена). */
    val isReady: Boolean get() = injector.isReady

    private fun requireReady(): Message? = if (injector.isReady) {
        null
    } else {
        ErrorMessage(
            code = "input_not_ready",
            message = "Служба доступности Efir не включена — ввод недоступен",
        )
    }

    private suspend fun handleTap(tap: InputTap): Message {
        requireReady()?.let { return it }
        val g = geometry ?: return noGeometryError()
        injector.tap(g.mapX(tap.x), g.mapY(tap.y), tap.durationMs)
        return Ack()
    }

    private suspend fun handleSwipe(swipe: InputSwipe): Message {
        requireReady()?.let { return it }
        val g = geometry ?: return noGeometryError()
        injector.swipe(
            fromX = g.mapX(swipe.fromX),
            fromY = g.mapY(swipe.fromY),
            toX = g.mapX(swipe.toX),
            toY = g.mapY(swipe.toY),
            durationMs = swipe.durationMs,
        )
        return Ack()
    }

    private suspend fun handleMulti(multi: InputMultiTouch): Message {
        requireReady()?.let { return it }
        // Штрихи приходят в нормализованных координатах — масштабируем каждый.
        val g = geometry
        val strokes = multi.strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = g?.mapX(point.x) ?: point.x,
                        y = g?.mapY(point.y) ?: point.y,
                    )
                },
            )
        }
        injector.multiTouch(strokes)
        return Ack()
    }

    private suspend fun handleText(text: ru.efir.core.protocol.InputText): Message {
        if (!injector.isReady && onTextShown != null) {
            onTextShown.invoke(text.text)
            return Ack()
        }
        requireReady()?.let { return it }
        injector.typeText(text.text)
        return Ack()
    }

    private suspend fun handleKey(key: InputKey): Message {
        requireReady()?.let { return it }
        injector.pressKey(key.action)
        return Ack()
    }

    private fun noGeometryError(): ErrorMessage = ErrorMessage(
        code = "no_screen_geometry",
        message = "Геометрия экрана неизвестна — сначала запустите трансляцию экрана",
    )
}
