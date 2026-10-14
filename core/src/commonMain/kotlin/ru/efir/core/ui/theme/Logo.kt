package ru.efir.core.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Знак приложения: сигнал по воздуху от одного устройства к другому — две
 * точки и дуги между ними, белым на спокойном серо-синем. Один рисунок
 * для верхней панели и значка окна; значок Android повторяет его в
 * `res/drawable/ic_launcher_foreground.xml`.
 */
val EfirLogoVector: ImageVector by lazy {
    ImageVector.Builder("EfirLogo", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString(
                "M5,1H19A4,4 0 0 1 23,5V19A4,4 0 0 1 19,23H5A4,4 0 0 1 1,19V5A4,4 0 0 1 5,1Z",
            ).toNodes(),
            fill = SolidColor(Color(0xFF58748C)),
        )
        addPath(
            PathParser().parsePathString("M5.1,17A1.9,1.9 0 1 1 8.9,17A1.9,1.9 0 1 1 5.1,17ZM15.1,7A1.9,1.9 0 1 1 18.9,7A1.9,1.9 0 1 1 15.1,7Z").toNodes(),
            fill = SolidColor(Color.White),
        )
        addPath(
            PathParser().parsePathString("M7.87,12.08A5,5 0 0 1 11.92,16.13M8.65,7.64A9.5,9.5 0 0 1 16.36,15.35").toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}

@Composable
fun EfirLogo(modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(EfirLogoVector), contentDescription = null, modifier = modifier)
}
