package com.susking.ephone_s.eventgraph.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 事实图谱画布，用节点和连线展示实体关系。
 */
class EventGraphCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val nodePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(83, 109, 254)
        style = Paint.Style.FILL
    }
    private val selectedNodePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 77)
        style = Paint.Style.FILL
    }
    private val edgePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 144, 156)
        strokeWidth = EDGE_STROKE_WIDTH
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectedEdgePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 112, 67)
        strokeWidth = SELECTED_EDGE_STROKE_WIDTH
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = NODE_TEXT_SIZE
    }
    private val edgeTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(69, 90, 100)
        textAlign = Paint.Align.CENTER
        textSize = EDGE_TEXT_SIZE
    }
    private val selectedEdgeTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 81, 0)
        textAlign = Paint.Align.CENTER
        textSize = EDGE_TEXT_SIZE
        isFakeBoldText = true
    }
    private val edgeLabelBackgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(226, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val selectedEdgeLabelBackgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(242, 255, 243, 224)
        style = Paint.Style.FILL
    }
    private val emptyTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(117, 117, 117)
        textAlign = Paint.Align.CENTER
        textSize = EMPTY_TEXT_SIZE
    }
    private val arrowPath: Path = Path()
    private val edgePath: Path = Path()
    private val textBounds: Rect = Rect()
    private val nodePositions: MutableMap<String, PointF> = mutableMapOf()
    private val scaleDetector: ScaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector: GestureDetector = GestureDetector(context, TapListener())
    private var graphNodes: List<EventGraphNodeItem> = emptyList()
    private var graphEdges: List<EventGraphEdgeItem> = emptyList()
    private var selectedNodeId: String? = null
    private var selectedEdgeId: String? = null
    private var scaleFactor: Float = DEFAULT_SCALE
    private var translateX: Float = 0f
    private var translateY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging: Boolean = false
    private var onNodeClick: ((String) -> Unit)? = null
    private var onEdgeClick: ((String) -> Unit)? = null
    private var onSelectionClear: (() -> Unit)? = null

    fun setGraphData(nodes: List<EventGraphNodeItem>, edges: List<EventGraphEdgeItem>): Unit {
        if (graphNodes == nodes && graphEdges == edges) return
        graphNodes = nodes
        graphEdges = edges
        selectedNodeId = null
        selectedEdgeId = null
        updateNodePositions()
        invalidate()
    }

    fun setOnGraphNodeClickListener(listener: (String) -> Unit): Unit {
        onNodeClick = listener
    }

    fun setOnGraphEdgeClickListener(listener: (String) -> Unit): Unit {
        onEdgeClick = listener
    }

    fun setOnGraphSelectionClearListener(listener: () -> Unit): Unit {
        onSelectionClear = listener
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int): Unit {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateNodePositions()
    }

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        if (graphNodes.isEmpty()) {
            drawEmptyText(canvas)
            return
        }
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        drawEdges(canvas)
        drawNodes(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return true
    }

    private fun handleActionDown(event: MotionEvent): Unit {
        lastTouchX = event.x
        lastTouchY = event.y
        isDragging = true
    }

    private fun handleActionMove(event: MotionEvent): Unit {
        if (!isDragging || scaleDetector.isInProgress) return
        val deltaX: Float = event.x - lastTouchX
        val deltaY: Float = event.y - lastTouchY
        translateX += deltaX
        translateY += deltaY
        lastTouchX = event.x
        lastTouchY = event.y
        invalidate()
    }

    private fun drawEmptyText(canvas: Canvas): Unit {
        canvas.drawText("暂无节点关系可展示", width / 2f, height / 2f, emptyTextPaint)
    }

    private fun drawEdges(canvas: Canvas): Unit {
        val sortedEdges: List<EventGraphEdgeItem> = graphEdges.sortedBy { edge: EventGraphEdgeItem -> if (edge.id == selectedEdgeId) SELECTED_EDGE_DRAW_ORDER else NORMAL_EDGE_DRAW_ORDER }
        sortedEdges.forEach { edge: EventGraphEdgeItem ->
            val fromPoint: PointF = nodePositions[edge.fromNodeId] ?: return@forEach
            val toPoint: PointF = nodePositions[edge.toNodeId] ?: return@forEach
            val curvePoints: EdgeCurvePoints = createEdgeCurvePoints(edge, fromPoint, toPoint)
            val paint: Paint = if (edge.id == selectedEdgeId) selectedEdgePaint else edgePaint
            edgePath.reset()
            edgePath.moveTo(curvePoints.startPoint.x, curvePoints.startPoint.y)
            edgePath.quadTo(
                curvePoints.controlPoint.x,
                curvePoints.controlPoint.y,
                curvePoints.endPoint.x,
                curvePoints.endPoint.y
            )
            canvas.drawPath(edgePath, paint)
            drawArrow(canvas, curvePoints, paint)
            drawEdgeLabel(canvas, edge, curvePoints)
        }
    }

    private fun drawArrow(canvas: Canvas, curvePoints: EdgeCurvePoints, paint: Paint): Unit {
        val arrowProgress: Float = getArrowProgress(curvePoints)
        val targetPoint: PointF = evaluateQuadraticPoint(curvePoints, arrowProgress)
        val previousPoint: PointF = evaluateQuadraticPoint(curvePoints, (arrowProgress - ARROW_TANGENT_PROGRESS).coerceAtLeast(0f))
        val angle: Double = atan2((targetPoint.y - previousPoint.y).toDouble(), (targetPoint.x - previousPoint.x).toDouble())
        arrowPath.reset()
        arrowPath.moveTo(targetPoint.x, targetPoint.y)
        arrowPath.lineTo(
            targetPoint.x - (ARROW_SIZE * cos(angle - ARROW_ANGLE)).toFloat(),
            targetPoint.y - (ARROW_SIZE * sin(angle - ARROW_ANGLE)).toFloat()
        )
        arrowPath.moveTo(targetPoint.x, targetPoint.y)
        arrowPath.lineTo(
            targetPoint.x - (ARROW_SIZE * cos(angle + ARROW_ANGLE)).toFloat(),
            targetPoint.y - (ARROW_SIZE * sin(angle + ARROW_ANGLE)).toFloat()
        )
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawEdgeLabel(canvas: Canvas, edge: EventGraphEdgeItem, curvePoints: EdgeCurvePoints): Unit {
        val isSelected: Boolean = edge.id == selectedEdgeId
        val labelLines: List<String> = buildEdgeLabelLines(edge, isSelected)
        val labelPoint: PointF = createEdgeLabelPoint(curvePoints)
        val textPaint: Paint = if (isSelected) selectedEdgeTextPaint else edgeTextPaint
        val backgroundPaint: Paint = if (isSelected) selectedEdgeLabelBackgroundPaint else edgeLabelBackgroundPaint
        val labelRect: RectF = createEdgeLabelRect(labelLines, labelPoint, textPaint)
        canvas.drawRoundRect(labelRect, EDGE_LABEL_CORNER_RADIUS, EDGE_LABEL_CORNER_RADIUS, backgroundPaint)
        labelLines.forEachIndexed { index: Int, line: String ->
            val textY: Float = labelRect.top + EDGE_LABEL_PADDING_VERTICAL + EDGE_TEXT_SIZE + index * EDGE_LABEL_LINE_HEIGHT
            canvas.drawText(line, labelPoint.x, textY, textPaint)
        }
    }

    private fun drawNodes(canvas: Canvas): Unit {
        graphNodes.forEach { node: EventGraphNodeItem ->
            val point: PointF = nodePositions[node.id] ?: return@forEach
            val paint: Paint = if (node.id == selectedNodeId) selectedNodePaint else nodePaint
            canvas.drawCircle(point.x, point.y, NODE_RADIUS, paint)
            drawNodeText(canvas, node.label, point)
        }
    }

    private fun drawNodeText(canvas: Canvas, label: String, point: PointF): Unit {
        val displayText: String = label.take(MAX_NODE_LABEL_LENGTH)
        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
        canvas.drawText(displayText, point.x, point.y - textBounds.exactCenterY(), textPaint)
    }

    private fun updateNodePositions(): Unit {
        if (width == 0 || height == 0 || graphNodes.isEmpty()) return
        nodePositions.clear()
        val centerX: Float = width / 2f
        val centerY: Float = height / 2f
        val nodeDegrees: Map<String, Int> = buildNodeDegrees()
        val sortedNodes: List<EventGraphNodeItem> = graphNodes.sortedWith(
            compareByDescending<EventGraphNodeItem> { node: EventGraphNodeItem -> nodeDegrees[node.id] ?: 0 }
                .thenBy { node: EventGraphNodeItem -> node.label }
        )
        val baseRadius: Float = calculateLayoutRadius(sortedNodes.size, graphEdges.size)
        val averageDegree: Float = if (sortedNodes.isEmpty()) 0f else nodeDegrees.values.sum().toFloat() / sortedNodes.size
        sortedNodes.forEachIndexed { index: Int, node: EventGraphNodeItem ->
            val degree: Int = nodeDegrees[node.id] ?: 0
            val angleStep: Double = FULL_CIRCLE_RADIANS / sortedNodes.size
            val angleJitter: Double = if (index % 2 == 0) angleStep * LAYOUT_ANGLE_JITTER_RATIO else -angleStep * LAYOUT_ANGLE_JITTER_RATIO
            val angle: Double = angleStep * index + angleJitter
            val degreeRatio: Float = if (averageDegree <= 0f) 0f else (degree / averageDegree).coerceIn(0f, MAX_DEGREE_RADIUS_RATIO)
            val radius: Float = baseRadius + degreeRatio * DENSE_NODE_RADIUS_BONUS + (index % LAYOUT_LAYER_COUNT) * LAYOUT_LAYER_GAP
            nodePositions[node.id] = PointF(
                centerX + radius * cos(angle).toFloat(),
                centerY + radius * sin(angle).toFloat()
            )
        }
    }

    private fun handleTap(screenX: Float, screenY: Float): Unit {
        val graphX: Float = (screenX - translateX) / scaleFactor
        val graphY: Float = (screenY - translateY) / scaleFactor
        val tappedNodeId: String? = findTappedNodeId(graphX, graphY)
        if (tappedNodeId != null) {
            selectedNodeId = tappedNodeId
            selectedEdgeId = null
            onSelectionClear?.invoke()
            onNodeClick?.invoke(tappedNodeId)
            invalidate()
            return
        }
        val tappedEdgeId: String? = findTappedEdgeId(graphX, graphY)
        if (tappedEdgeId != null) {
            selectedNodeId = null
            selectedEdgeId = tappedEdgeId
            onEdgeClick?.invoke(tappedEdgeId)
            invalidate()
            return
        }
        clearSelection()
    }

    private fun clearSelection(): Unit {
        val hadSelection: Boolean = selectedNodeId != null || selectedEdgeId != null
        selectedNodeId = null
        selectedEdgeId = null
        onSelectionClear?.invoke()
        if (hadSelection) invalidate()
    }

    private fun findTappedNodeId(graphX: Float, graphY: Float): String? {
        return nodePositions.entries.firstOrNull { entry: Map.Entry<String, PointF> ->
            val point: PointF = entry.value
            val hitRect: RectF = RectF(
                point.x - NODE_RADIUS,
                point.y - NODE_RADIUS,
                point.x + NODE_RADIUS,
                point.y + NODE_RADIUS
            )
            hitRect.contains(graphX, graphY)
        }?.key
    }

    private fun findTappedEdgeId(graphX: Float, graphY: Float): String? {
        return graphEdges.firstOrNull { edge: EventGraphEdgeItem ->
            val fromPoint: PointF = nodePositions[edge.fromNodeId] ?: return@firstOrNull false
            val toPoint: PointF = nodePositions[edge.toNodeId] ?: return@firstOrNull false
            val curvePoints: EdgeCurvePoints = createEdgeCurvePoints(edge, fromPoint, toPoint)
            getPointToCurveDistance(graphX, graphY, curvePoints) <= EDGE_TOUCH_DISTANCE
        }?.id
    }

    private fun getEdgeCurveOffset(edge: EventGraphEdgeItem): Float {
        val samePairEdges: List<EventGraphEdgeItem> = graphEdges.filter { currentEdge: EventGraphEdgeItem ->
            buildUndirectedPairKey(currentEdge) == buildUndirectedPairKey(edge)
        }
        val edgeIndex: Int = samePairEdges.indexOfFirst { currentEdge: EventGraphEdgeItem -> currentEdge.id == edge.id }
        if (samePairEdges.size <= 1 || edgeIndex < 0) return DEFAULT_CURVE_OFFSET
        val centerIndex: Float = (samePairEdges.size - 1) / 2f
        val directionSign: Float = if (edge.fromNodeId <= edge.toNodeId) 1f else -1f
        return DEFAULT_CURVE_OFFSET + (edgeIndex - centerIndex) * PARALLEL_CURVE_OFFSET * directionSign
    }

    private fun buildUndirectedPairKey(edge: EventGraphEdgeItem): String {
        return listOf(edge.fromNodeId, edge.toNodeId).sorted().joinToString(separator = ":")
    }

    private fun createEdgeCurvePoints(edge: EventGraphEdgeItem, fromPoint: PointF, toPoint: PointF): EdgeCurvePoints {
        val deltaX: Float = toPoint.x - fromPoint.x
        val deltaY: Float = toPoint.y - fromPoint.y
        val length: Float = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
        if (length == 0f) return EdgeCurvePoints(fromPoint, fromPoint, toPoint, 0f, 0f)
        val normalX: Float = -deltaY / length
        val normalY: Float = deltaX / length
        val curveOffset: Float = getEdgeCurveOffset(edge)
        val midPointX: Float = (fromPoint.x + toPoint.x) / 2f
        val midPointY: Float = (fromPoint.y + toPoint.y) / 2f
        return EdgeCurvePoints(
            startPoint = fromPoint,
            controlPoint = PointF(midPointX + normalX * curveOffset, midPointY + normalY * curveOffset),
            endPoint = toPoint,
            normalX = normalX,
            normalY = normalY
        )
    }

    private fun createEdgeLabelPoint(curvePoints: EdgeCurvePoints): PointF {
        val centerPoint: PointF = evaluateQuadraticPoint(curvePoints, EDGE_LABEL_PROGRESS)
        return PointF(
            centerPoint.x + curvePoints.normalX * EDGE_LABEL_NORMAL_OFFSET,
            centerPoint.y + curvePoints.normalY * EDGE_LABEL_NORMAL_OFFSET
        )
    }

    private fun evaluateQuadraticPoint(curvePoints: EdgeCurvePoints, progress: Float): PointF {
        val remainingProgress: Float = 1f - progress
        val x: Float = remainingProgress * remainingProgress * curvePoints.startPoint.x +
            2f * remainingProgress * progress * curvePoints.controlPoint.x +
            progress * progress * curvePoints.endPoint.x
        val y: Float = remainingProgress * remainingProgress * curvePoints.startPoint.y +
            2f * remainingProgress * progress * curvePoints.controlPoint.y +
            progress * progress * curvePoints.endPoint.y
        return PointF(x, y)
    }

    private fun getArrowProgress(curvePoints: EdgeCurvePoints): Float {
        val distance: Float = getPointDistance(curvePoints.startPoint, curvePoints.endPoint).coerceAtLeast(NODE_RADIUS)
        return (1f - NODE_RADIUS / distance).coerceIn(MIN_ARROW_PROGRESS, MAX_ARROW_PROGRESS)
    }

    private fun getPointToCurveDistance(x: Float, y: Float, curvePoints: EdgeCurvePoints): Float {
        var minDistance: Float = Float.MAX_VALUE
        var previousPoint: PointF = curvePoints.startPoint
        for (index: Int in 1..CURVE_HIT_SAMPLE_COUNT) {
            val progress: Float = index.toFloat() / CURVE_HIT_SAMPLE_COUNT
            val currentPoint: PointF = evaluateQuadraticPoint(curvePoints, progress)
            minDistance = min(minDistance, getPointToLineDistance(x, y, previousPoint, currentPoint))
            previousPoint = currentPoint
        }
        return minDistance
    }

    private fun getPointToLineDistance(x: Float, y: Float, fromPoint: PointF, toPoint: PointF): Float {
        val deltaX: Float = toPoint.x - fromPoint.x
        val deltaY: Float = toPoint.y - fromPoint.y
        val lengthSquared: Float = deltaX * deltaX + deltaY * deltaY
        if (lengthSquared == 0f) return Float.MAX_VALUE
        val progress: Float = (((x - fromPoint.x) * deltaX + (y - fromPoint.y) * deltaY) / lengthSquared).coerceIn(0f, 1f)
        val projectionX: Float = fromPoint.x + progress * deltaX
        val projectionY: Float = fromPoint.y + progress * deltaY
        val distanceX: Float = x - projectionX
        val distanceY: Float = y - projectionY
        return kotlin.math.sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    private fun getPointDistance(fromPoint: PointF, toPoint: PointF): Float {
        val deltaX: Float = toPoint.x - fromPoint.x
        val deltaY: Float = toPoint.y - fromPoint.y
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun buildEdgeLabelLines(edge: EventGraphEdgeItem, isSelected: Boolean): List<String> {
        val label: String = edge.label.take(MAX_EDGE_LABEL_LENGTH)
        if (!isSelected) return listOf(label)
        val statusLabel: String = edge.statusLabel.take(MAX_EDGE_STATUS_LABEL_LENGTH)
        val lifecycleLabel: String = edge.lifecycleLabel.take(MAX_EDGE_LIFECYCLE_LABEL_LENGTH)
        return listOf(label, statusLabel, lifecycleLabel).filter { line: String -> line.isNotBlank() }
    }

    private fun createEdgeLabelRect(labelLines: List<String>, labelPoint: PointF, paint: Paint): RectF {
        val maxTextWidth: Float = labelLines.maxOfOrNull { line: String -> paint.measureText(line) } ?: 0f
        val labelWidth: Float = maxTextWidth + EDGE_LABEL_PADDING_HORIZONTAL * 2f
        val labelHeight: Float = EDGE_LABEL_PADDING_VERTICAL * 2f + EDGE_TEXT_SIZE + (labelLines.size - 1) * EDGE_LABEL_LINE_HEIGHT
        return RectF(
            labelPoint.x - labelWidth / 2f,
            labelPoint.y - labelHeight / 2f,
            labelPoint.x + labelWidth / 2f,
            labelPoint.y + labelHeight / 2f
        )
    }

    private fun buildNodeDegrees(): Map<String, Int> {
        val degrees: MutableMap<String, Int> = graphNodes.associate { node: EventGraphNodeItem -> node.id to 0 }.toMutableMap()
        graphEdges.forEach { edge: EventGraphEdgeItem ->
            degrees[edge.fromNodeId] = (degrees[edge.fromNodeId] ?: 0) + 1
            degrees[edge.toNodeId] = (degrees[edge.toNodeId] ?: 0) + 1
        }
        return degrees
    }

    private fun calculateLayoutRadius(nodeCount: Int, edgeCount: Int): Float {
        val densityBonus: Float = min(MAX_LAYOUT_DENSITY_BONUS, edgeCount * LAYOUT_EDGE_RADIUS_BONUS)
        val nodeBonus: Float = min(MAX_LAYOUT_NODE_BONUS, nodeCount * LAYOUT_NODE_RADIUS_BONUS)
        return max(MIN_LAYOUT_RADIUS, min(width, height) * LAYOUT_RADIUS_RATIO + densityBonus + nodeBonus)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isDragging = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScaleFactor: Float = scaleFactor
            val newScaleFactor: Float = (scaleFactor * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            if (oldScaleFactor == newScaleFactor) return true
            val focusGraphX: Float = (detector.focusX - translateX) / oldScaleFactor
            val focusGraphY: Float = (detector.focusY - translateY) / oldScaleFactor
            scaleFactor = newScaleFactor
            translateX = detector.focusX - focusGraphX * newScaleFactor
            translateY = detector.focusY - focusGraphY * newScaleFactor
            invalidate()
            return true
        }
    }

    private inner class TapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            handleTap(event.x, event.y)
            return true
        }
    }

    private data class EdgeCurvePoints(
        val startPoint: PointF,
        val controlPoint: PointF,
        val endPoint: PointF,
        val normalX: Float,
        val normalY: Float
    )

    companion object {
        private const val NORMAL_EDGE_DRAW_ORDER: Int = 0
        private const val SELECTED_EDGE_DRAW_ORDER: Int = 1
        private const val DEFAULT_SCALE: Float = 1f
        private const val MIN_SCALE: Float = 0.5f
        private const val MAX_SCALE: Float = 2.5f
        private const val NODE_RADIUS: Float = 58f
        private const val NODE_TEXT_SIZE: Float = 24f
        private const val EDGE_TEXT_SIZE: Float = 22f
        private const val EMPTY_TEXT_SIZE: Float = 34f
        private const val EDGE_STROKE_WIDTH: Float = 4f
        private const val SELECTED_EDGE_STROKE_WIDTH: Float = 7f
        private const val EDGE_TOUCH_DISTANCE: Float = 42f
        private const val DEFAULT_CURVE_OFFSET: Float = 44f
        private const val PARALLEL_CURVE_OFFSET: Float = 58f
        private const val EDGE_LABEL_PROGRESS: Float = 0.5f
        private const val EDGE_LABEL_NORMAL_OFFSET: Float = 18f
        private const val EDGE_LABEL_PADDING_HORIZONTAL: Float = 16f
        private const val EDGE_LABEL_PADDING_VERTICAL: Float = 8f
        private const val EDGE_LABEL_LINE_HEIGHT: Float = 26f
        private const val EDGE_LABEL_CORNER_RADIUS: Float = 14f
        private const val CURVE_HIT_SAMPLE_COUNT: Int = 16
        private const val ARROW_TANGENT_PROGRESS: Float = 0.04f
        private const val MIN_ARROW_PROGRESS: Float = 0.68f
        private const val MAX_ARROW_PROGRESS: Float = 0.92f
        private const val ARROW_SIZE: Float = 24f
        private const val ARROW_ANGLE: Double = Math.PI / 6.0
        private const val FULL_CIRCLE_RADIANS: Double = Math.PI * 2.0
        private const val LAYOUT_RADIUS_RATIO: Float = 0.34f
        private const val MIN_LAYOUT_RADIUS: Float = 180f
        private const val LAYOUT_EDGE_RADIUS_BONUS: Float = 3.5f
        private const val LAYOUT_NODE_RADIUS_BONUS: Float = 5f
        private const val MAX_LAYOUT_DENSITY_BONUS: Float = 180f
        private const val MAX_LAYOUT_NODE_BONUS: Float = 90f
        private const val DENSE_NODE_RADIUS_BONUS: Float = 48f
        private const val MAX_DEGREE_RADIUS_RATIO: Float = 2.2f
        private const val LAYOUT_LAYER_COUNT: Int = 3
        private const val LAYOUT_LAYER_GAP: Float = 34f
        private const val LAYOUT_ANGLE_JITTER_RATIO: Double = 0.18
        private const val MAX_NODE_LABEL_LENGTH: Int = 6
        private const val MAX_EDGE_LABEL_LENGTH: Int = 10
        private const val MAX_EDGE_STATUS_LABEL_LENGTH: Int = 14
        private const val MAX_EDGE_LIFECYCLE_LABEL_LENGTH: Int = 18
    }
}
