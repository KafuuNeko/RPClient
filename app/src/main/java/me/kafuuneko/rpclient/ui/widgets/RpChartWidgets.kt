package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 图表分段数据项，用于比例条与分段堆叠柱状图。
 *
 * @param value 当前分段的量值。
 * @param color 当前分段的填充色彩。
 * @param label 可选的分段描述或图例文案。
 */
data class RpChartSegment(
    val value: Float,
    val color: Color,
    val label: String? = null
)

/**
 * 时序趋势图通用数据点定义。
 *
 * @param key 数据点唯一标识（如日期、时间戳字符串）。
 * @param label X 轴显示的短标签（如 "08-31"）。
 * @param value 主量值（用于折线走势绘制与峰值计算）。
 * @param segments 内部子分段列表（用于多色分段柱状图）。
 * @param isCurrent 是否属于当前周期或今日高亮节点。
 */
data class RpChartDataPoint(
    val key: String,
    val label: String,
    val value: Float,
    val segments: List<RpChartSegment> = emptyList(),
    val isCurrent: Boolean = false
)

/**
 * 趋势图表支持的展示形态。
 */
enum class RpChartMode {
    /** 分段柱状图。 */
    Bar,
    /** 平滑面积折线图。 */
    Line
}

/**
 * 将数值格式化为通用的紧凑图表数字表达（<1K 展示整数，>=1K 使用 K/M/B 并最多保留 2 位有效小数）。
 */
fun formatCompactChartValue(value: Float): String {
    val longVal = value.toLong()
    if (longVal < 1_000L) {
        return longVal.toString()
    }
    val (num, unit) = when {
        longVal >= 1_000_000_000L -> (value / 1_000_000_000.0) to "B"
        longVal >= 1_000_000L -> (value / 1_000_000.0) to "M"
        else -> (value / 1_000.0) to "K"
    }
    val formattedNumber = "%.2f".format(Locale.US, num).trimEnd('0').trimEnd('.')
    return "$formattedNumber$unit"
}

/**
 * 通用多段横向比例条。
 *
 * @param segments 各分段的量值与色彩配置。
 * @param modifier 修饰符。
 * @param barHeight 比例条高度，默认 10dp。
 */
@Composable
fun RpStackedRatioBar(
    segments: List<RpChartSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 分段进度条主体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                segments.forEach { segment ->
                    val ratio = (segment.value / total).coerceIn(0f, 1f)
                    val animatedRatio by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = tween(durationMillis = 400),
                        label = "segmentRatio"
                    )
                    if (animatedRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(animatedRatio.coerceAtLeast(0.005f))
                                .fillMaxHeight()
                                .background(segment.color)
                        )
                    }
                }
            }
        }

        // 图例文案行（仅在存在标签时展示）
        val labeledSegments = segments.filter { !it.label.isNullOrBlank() }
        if (labeledSegments.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labeledSegments.forEach { segment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(segment.color))
                        Text(
                            text = segment.label.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 通用闭合直角坐标系标尺与网格背景层（工业级 L 型坐标框架）。
 *
 * 核心机制：
 * - 左侧 Y 轴标尺轨道（宽度 axisRailWidth）：垂直对齐 Max、Mid、0 刻度与微型刻度引导短线（Ticks）。
 * - 贯通式水平基准地平线（Continuous Base Floor Line）：从左侧 Y 轴原点 0 底部贯穿全宽至最右侧。
 * - 顶部与中部水平参考虚线：从左侧 Y 轴刻度精准引出至右侧边缘。
 * - 左下角自然成为坐标原点 (0, 0)，彻底消除遮挡与空白死角问题。
 *
 * @param maxValue 当前图表的最大量级。
 * @param modifier 布局修饰符。
 * @param chartHeight 图表主体绘制区高度。
 * @param axisRailWidth 左侧 Y 轴标尺轨道的宽度。
 * @param formatter 刻度数值格式化 lambda。
 * @param labelContent 自定义刻度标签渲染 slot。
 */
@Composable
fun RpChartCoordinateGrid(
    maxValue: Float,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 100.dp,
    axisRailWidth: Dp = 32.dp,
    formatter: (Float) -> String = ::formatCompactChartValue,
    labelContent: (@Composable BoxScope.(value: Float, formattedText: String, alignment: Alignment) -> Unit)? = null
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
    val maxText = remember(maxValue) { formatter(maxValue) }
    val midText = remember(maxValue) { formatter(maxValue / 2f) }
    val zeroText = remember { formatter(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        // 画布绘制：贯穿基准线、顶部/中部水平参考虚线与 Y 轴微型刻度引导线
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            val width = size.width
            val height = size.height
            val railPx = axisRailWidth.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)

            // 顶部上限水平虚线 (y = 0)
            drawLine(
                color = gridColor,
                start = Offset(railPx, 0f),
                end = Offset(width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // 中部平分水平虚线 (y = height / 2)
            drawLine(
                color = gridColor,
                start = Offset(railPx, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // 贯穿式底部基准实线 (y = height，从 x = 0 贯穿至 x = width)
            drawLine(
                color = baselineColor,
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 1.dp.toPx()
            )

            // Y 轴微型刻度引导短线 (Ticks ┤)
            val tickLength = 4.dp.toPx()
            // 顶部刻度短线
            drawLine(
                color = baselineColor,
                start = Offset(railPx - tickLength, 0f),
                end = Offset(railPx, 0f),
                strokeWidth = 1.dp.toPx()
            )
            // 中部刻度短线
            drawLine(
                color = baselineColor,
                start = Offset(railPx - tickLength, height / 2f),
                end = Offset(railPx, height / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 左侧 Y 轴刻度标签列
        Box(
            modifier = Modifier
                .width(axisRailWidth)
                .height(chartHeight)
                .padding(end = 6.dp)
        ) {
            if (labelContent != null) {
                labelContent(maxValue, maxText, Alignment.TopEnd)
                labelContent(maxValue / 2f, midText, Alignment.CenterEnd)
                labelContent(0f, zeroText, Alignment.BottomEnd)
            } else {
                // 顶部刻度值
                Text(
                    text = maxText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // 中部刻度值
                Text(
                    text = midText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                // 底部原点刻度值 0（稳稳落在基线上方）
                Text(
                    text = zeroText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

/**
 * 通用图表形态切换微型开关（用于柱形图与走势图切换）。
 */
@Composable
fun RpChartModeToggle(
    currentMode: RpChartMode,
    onModeSelected: (RpChartMode) -> Unit,
    barLabel: String,
    lineLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RpChartToggleChip(
            selected = currentMode == RpChartMode.Bar,
            icon = Icons.Rounded.BarChart,
            text = barLabel,
            onClick = { onModeSelected(RpChartMode.Bar) }
        )
        RpChartToggleChip(
            selected = currentMode == RpChartMode.Line,
            icon = Icons.AutoMirrored.Rounded.ShowChart,
            text = lineLabel,
            onClick = { onModeSelected(RpChartMode.Line) }
        )
    }
}

@Composable
private fun RpChartToggleChip(
    selected: Boolean,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 通用可横向平滑滚动的柱状图组件。
 *
 * 核心机制：
 * - 闭合直角坐标系架构，左侧轨道稳固锚定 Y 轴刻度与原点 0。
 * - 贯穿式水平基准线横贯全宽，彻底消除左下角空白死角。
 * - 柱体与折线位于轨道右侧视口，绝对无任何物理重叠。
 * - 数据点少于或等于 7 时自动均分填满宽度；多于 7 时开启横向惯性平滑滚动。
 *
 * @param points 数据点列表。
 * @param selectedKey 当前选中的数据点标识。
 * @param onSelectPoint 选中数据点回调。
 * @param modifier 布局修饰符。
 * @param chartHeight 柱体绘制区高度。
 * @param barSlotWidth 横向滚动模式下单槽位宽度。
 * @param axisRailWidth 左侧坐标轴轨道宽度。
 * @param autoScrollToEndKey 触发自动滚动到末尾的 key（如周期切换时）。
 * @param yAxisFormatter 简单数值格式化 lambda。
 * @param yAxisContent 完全自定义 Y 轴层 slot lambda（传入 null 可隐藏 Y 轴）。
 */
@Composable
fun RpScrollableBarChart(
    points: List<RpChartDataPoint>,
    selectedKey: String?,
    onSelectPoint: (String) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 135.dp,
    barSlotWidth: Dp = 42.dp,
    axisRailWidth: Dp = 32.dp,
    autoScrollToEndKey: Any? = Unit,
    yAxisFormatter: (Float) -> String = ::formatCompactChartValue,
    yAxisContent: (@Composable BoxScope.(maxValue: Float) -> Unit)? = { maxValue ->
        if (maxValue > 0.0001f) {
            RpChartCoordinateGrid(
                maxValue = maxValue,
                chartHeight = 100.dp,
                axisRailWidth = axisRailWidth,
                formatter = yAxisFormatter
            )
        }
    }
) {
    val isScrollable = points.size > 7
    val scrollState = rememberScrollState()
    val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(0.0001f) ?: 1f

    LaunchedEffect(points.size, autoScrollToEndKey) {
        if (isScrollable) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 背景层：闭合直角坐标系标尺与贯穿基线
        if (yAxisContent != null) {
            yAxisContent(maxValue)
        }

        // 前景层：位于 Y 轴轨道右侧的图表绘制与滑动视口
        val chartContentModifier = if (isScrollable) {
            Modifier
                .fillMaxWidth()
                .padding(start = axisRailWidth)
                .horizontalScroll(scrollState)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(start = axisRailWidth, end = 4.dp)
        }

        Row(
            modifier = chartContentModifier,
            horizontalArrangement = if (isScrollable) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { point ->
                val isSelected = point.key == selectedKey
                val heightRatio = (point.value / maxValue).coerceIn(0.04f, 1.0f)
                val columnModifier = if (isScrollable) {
                    Modifier.width(barSlotWidth)
                } else {
                    Modifier.weight(1f)
                }

                Column(
                    modifier = columnModifier
                        .height(chartHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectPoint(point.key) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // 柱体
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isScrollable) 0.50f else 0.55f)
                            .height((100 * heightRatio).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (point.value == 0f) {
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            )
                    ) {
                        if (point.value > 0f && point.segments.isNotEmpty()) {
                            val segmentTotal = point.segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
                            Column(modifier = Modifier.fillMaxSize()) {
                                point.segments.forEach { segment ->
                                    val segRatio = (segment.value / segmentTotal).coerceAtLeast(0.001f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(segRatio)
                                            .background(segment.color.copy(alpha = if (isSelected) 1f else 0.85f))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // X 轴日期刻度
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else if (point.isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                        },
                        fontWeight = if (isSelected || point.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 通用可横向平滑滚动的贝塞尔渐变面积折线图组件。
 *
 * 核心机制：
 * - 闭合直角坐标系架构，左侧轨道稳固锚定 Y 轴刻度与原点 0。
 * - 贯穿式水平基准线横贯全宽，从原点平稳起笔绘制贝塞尔曲线与微光面积。
 *
 * @param points 数据点列表。
 * @param selectedKey 当前选中的数据点标识。
 * @param onSelectPoint 选中数据点回调。
 * @param modifier 布局修饰符。
 * @param pointWidth 数据点横向间距。
 * @param axisRailWidth 左侧坐标轴轨道宽度。
 * @param lineColor 折线与微光主色彩。
 * @param autoScrollToEndKey 触发自动滚动到末尾的 key。
 * @param yAxisFormatter 简单数值格式化 lambda。
 * @param yAxisContent 完全自定义 Y 轴层 slot lambda（传入 null 可隐藏 Y 轴）。
 */
@Composable
fun RpScrollableLineChart(
    points: List<RpChartDataPoint>,
    selectedKey: String?,
    onSelectPoint: (String) -> Unit,
    modifier: Modifier = Modifier,
    pointWidth: Dp = 44.dp,
    axisRailWidth: Dp = 32.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    autoScrollToEndKey: Any? = Unit,
    yAxisFormatter: (Float) -> String = ::formatCompactChartValue,
    yAxisContent: (@Composable BoxScope.(maxValue: Float) -> Unit)? = { maxValue ->
        if (maxValue > 0.0001f) {
            RpChartCoordinateGrid(
                maxValue = maxValue,
                chartHeight = 100.dp,
                axisRailWidth = axisRailWidth,
                formatter = yAxisFormatter
            )
        }
    }
) {
    val isScrollable = points.size > 7
    val scrollState = rememberScrollState()
    val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(0.0001f) ?: 1f

    LaunchedEffect(points.size, autoScrollToEndKey) {
        if (isScrollable) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val totalWidth = if (isScrollable) pointWidth * points.size else 300.dp

    Box(modifier = modifier.fillMaxWidth()) {
        // 背景层：闭合直角坐标系标尺与贯穿基线
        if (yAxisContent != null) {
            yAxisContent(maxValue)
        }

        // 前景滑动层：位于 Y 轴轨道右侧的折线与面积
        val containerModifier = if (isScrollable) {
            Modifier
                .fillMaxWidth()
                .padding(start = axisRailWidth)
                .horizontalScroll(scrollState)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(start = axisRailWidth, end = 4.dp)
        }

        Column(modifier = containerModifier) {
            // 画布折线与渐变面积
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(100.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (points.isEmpty()) return@Canvas

                    val width = size.width
                    val height = size.height
                    val stepX = width / (points.size.coerceAtLeast(2) - 1).toFloat()
                    val topPadding = 6f
                    val availableHeight = height - topPadding

                    val coords = points.mapIndexed { index, point ->
                        val x = index * stepX
                        val ratio = (point.value / maxValue).coerceIn(0f, 1f)
                        val y = topPadding + (1f - ratio) * availableHeight
                        Offset(x, y)
                    }

                    // 构建平滑贝塞尔曲线路径
                    val strokePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    // 构建渐变闭合填充路径
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(coords.last().x, height)
                        lineTo(coords.first().x, height)
                        close()
                    }

                    // 绘制渐变面积背景
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.28f),
                                lineColor.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // 绘制主走势折线
                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 绘制各数据节点微光圆点
                    coords.forEachIndexed { index, offset ->
                        val isSelected = points[index].key == selectedKey
                        if (isSelected) {
                            drawCircle(
                                color = lineColor.copy(alpha = 0.25f),
                                radius = 9.dp.toPx(),
                                center = offset
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 5.dp.toPx(),
                                center = offset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = offset
                            )
                        } else if (points[index].value > 0f) {
                            drawCircle(
                                color = lineColor,
                                radius = 3.dp.toPx(),
                                center = offset
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 折线图底部 X 轴标签
            Row(
                modifier = Modifier.width(totalWidth),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { point ->
                    val isSelected = point.key == selectedKey
                    Text(
                        text = point.label,
                        modifier = Modifier
                            .width(pointWidth)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectPoint(point.key) }
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else if (point.isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                        },
                        fontWeight = if (isSelected || point.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 通用时序趋势卡片容器。
 *
 * 包装标题、副标题/峰值标签、图表形态切换开关、选中详情气泡、闭合坐标系标尺与图表绘制区。
 *
 * @param title 卡片标题。
 * @param points 数据点列表。
 * @param selectedKey 当前选中的数据点标识。
 * @param chartMode 当前图表展示形态（柱形/折线）。
 * @param onSelectPoint 选中数据点回调。
 * @param onSelectChartMode 切换形态回调。
 * @param barLabel 柱形图选项文案。
 * @param lineLabel 走势图选项文案。
 * @param modifier 布局修饰符。
 * @param peakText 峰值描述文案。
 * @param tooltipContent 选中点浮动气泡 slot。
 * @param autoScrollKey 触发自动滚动到末尾的 key。
 * @param axisRailWidth 左侧坐标轴轨道宽度。
 * @param yAxisFormatter 简单数值格式化 lambda。
 * @param yAxisContent 完全自定义 Y 轴层 slot lambda（支持根据图表模式定制，传入 null 可隐藏 Y 轴）。
 */
@Composable
fun RpTrendChartCard(
    title: String,
    points: List<RpChartDataPoint>,
    selectedKey: String?,
    chartMode: RpChartMode,
    onSelectPoint: (String) -> Unit,
    onSelectChartMode: (RpChartMode) -> Unit,
    barLabel: String,
    lineLabel: String,
    modifier: Modifier = Modifier,
    peakText: String? = null,
    tooltipContent: @Composable ((selectedPoint: RpChartDataPoint) -> Unit)? = null,
    autoScrollKey: Any? = Unit,
    axisRailWidth: Dp = 32.dp,
    yAxisFormatter: (Float) -> String = ::formatCompactChartValue,
    yAxisContent: (@Composable BoxScope.(maxValue: Float, chartMode: RpChartMode) -> Unit)? = { maxValue, _ ->
        if (maxValue > 0.0001f) {
            RpChartCoordinateGrid(
                maxValue = maxValue,
                chartHeight = 100.dp,
                axisRailWidth = axisRailWidth,
                formatter = yAxisFormatter
            )
        }
    }
) {
    val selectedPoint = points.firstOrNull { it.key == selectedKey }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 卡片头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (chartMode == RpChartMode.Bar) Icons.Rounded.BarChart else Icons.AutoMirrored.Rounded.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                RpChartModeToggle(
                    currentMode = chartMode,
                    onModeSelected = onSelectChartMode,
                    barLabel = barLabel,
                    lineLabel = lineLabel
                )
            }

            // 峰值标签
            if (!peakText.isNullOrBlank()) {
                Text(
                    text = peakText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 选中详情浮动气泡
            AnimatedVisibility(
                visible = selectedPoint != null && tooltipContent != null,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedPoint != null && tooltipContent != null) {
                    tooltipContent(selectedPoint)
                }
            }

            // 图表渲染区域（闭合直角坐标系标尺 + 横向滑动图表）
            val delegatedYAxisContent: (@Composable BoxScope.(maxValue: Float) -> Unit)? = if (yAxisContent != null) {
                { maxValue -> yAxisContent(maxValue, chartMode) }
            } else null

            if (chartMode == RpChartMode.Bar) {
                RpScrollableBarChart(
                    points = points,
                    selectedKey = selectedKey,
                    onSelectPoint = onSelectPoint,
                    autoScrollToEndKey = autoScrollKey,
                    axisRailWidth = axisRailWidth,
                    yAxisFormatter = yAxisFormatter,
                    yAxisContent = delegatedYAxisContent
                )
            } else {
                RpScrollableLineChart(
                    points = points,
                    selectedKey = selectedKey,
                    onSelectPoint = onSelectPoint,
                    autoScrollToEndKey = autoScrollKey,
                    axisRailWidth = axisRailWidth,
                    yAxisFormatter = yAxisFormatter,
                    yAxisContent = delegatedYAxisContent
                )
            }
        }
    }
}
