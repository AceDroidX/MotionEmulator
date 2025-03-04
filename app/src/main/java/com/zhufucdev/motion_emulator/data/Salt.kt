package com.zhufucdev.motion_emulator.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.zhufucdev.motion_emulator.data.BypassProjector.toIdeal
import com.zhufucdev.motion_emulator.hook.center
import com.zhufucdev.motion_emulator.ui.manager.FactorCanvas
import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import redempt.crunch.Crunch
import redempt.crunch.functional.EvaluationEnvironment
import kotlin.math.*
import kotlin.random.Random

@Serializable
open class Vector2D(val x: Double, val y: Double) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)

    operator fun times(matrix: Matrix2x2) = Vector2D(
        this.x * matrix[0, 0] + this.y * matrix[1, 0],
        this.x * matrix[0, 1] + this.y * matrix[1, 1]
    )

    operator fun times(factor: Double) = Vector2D(x * factor, y * factor)

    override fun toString(): String = "(x=$x, y=$y)"

    override fun equals(other: Any?): Boolean {
        if (other !is Vector2D) return false
        return other.x == x && other.y == y
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    companion object {
        val zero get() = Vector2D(0.0, 0.0)
        val one get() = Vector2D(1.0, 1.0)
    }
}

@Serializable
data class Matrix2x2(val values: DoubleArray = DoubleArray(4)) {
    operator fun get(row: Int, column: Int): Double {
        if (row <= 1 && column <= 1) {
            return values[row * 2 + column]
        } else if (row > 1) {
            dimensionError(row)
        } else {
            dimensionError(column)
        }
    }

    operator fun times(other: Matrix2x2) =
        Matrix2x2(
            doubleArrayOf(
                this[0, 0] * other[0, 0] + this[0, 1] * other[1, 0],
                this[0, 0] * other[0, 1] + this[0, 1] * other[1, 1],
                this[1, 0] * other[0, 0] + this[1, 1] * other[1, 0],
                this[1, 0] * other[0, 1] + this[1, 1] * other[1, 1]
            )
        )

    operator fun times(vector: Vector2D) =
        Vector2D(
            this[0, 0] * vector.x + this[1, 0] * vector.y,
            this[0, 1] * vector.x + this[1, 1] * vector.y
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Matrix2x2

        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    companion object {
        fun rotate(rad: Double): Matrix2x2 {
            val sin = sin(rad)
            val cos = cos(rad)
            return Matrix2x2(
                doubleArrayOf(
                    cos, sin, -sin, cos
                )
            )
        }

        fun scale(x: Double, y: Double): Matrix2x2 {
            return Matrix2x2(
                doubleArrayOf(
                    x, 0.0, 0.0, y
                )
            )
        }

        val eye get() = Matrix2x2(doubleArrayOf(1.0, 0.0, 0.0, 1.0))
    }
}

private fun dimensionError(dimension: Int): Nothing =
    throw IllegalArgumentException("Dimension error (targeting ${dimension}D)")

/**
 * Some 3 level Bézier Curve, but the start
 * and end are fixed
 */
@Serializable(LimitedBezierCurveSerializer::class)
data class LimitedBezierCurve(
    val controlStart: Vector2D = Vector2D(0.25, 0.25),
    val controlEnd: Vector2D = Vector2D(0.75, 0.75)
)

/**
 * Something that does something with some chance
 *
 * @param name How u call it
 * @param distribution How the chance varies
 */
@Serializable
data class Factor(
    val id: String = NanoIdUtils.randomNanoId(),
    val name: String,
    val distribution: LimitedBezierCurve = LimitedBezierCurve()
) {
    private val x by lazy {
        doubleArrayOf(
            0.0,
            distribution.controlStart.x,
            distribution.controlEnd.x,
            1.0
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun eval(): Double {
        // imagine drawing the curve on an RC
        // to resolve what y equals given it's x coordinate
        // first solve what t is
        val targetX = Random.nextDouble()
        val a = -x[0] + 3 * x[1] - 3 * x[2] + x[3]
        val b = 3 * x[0] - 6 * x[1] + 3 * x[2]
        val c = -3 * x[0] + 3 * x[1]
        val d = x[0] - targetX
        val d1 = b * c / a / a / 6 - b * b * b / 27 / a / a / a - d / 2 / a
        val t = -b / 3 / a + cbrt(d1) + cbrt(d1)

        // then use the solved t to manipulate the point (x, y)
        val tRounded = 1 - t
        var p = distribution.controlStart * 3.0 * t * tRounded * tRounded
        p += distribution.controlEnd * 3.0 * t * t * tRounded
        p += Vector2D.one * t * t * t
        return p.y
    }
}

data class MutableFactor(
    val id: String = NanoIdUtils.randomNanoId(),
    var name: String,
    var distribution: MutableBezierCurve = MutableBezierCurve()
)

data class MutableBezierCurve(
    var controlStart: Vector2D = Vector2D(0.25, 0.25),
    var controlEnd: Vector2D = Vector2D(0.75, 0.75)
)

fun Factor.mutable() = MutableFactor(id, name, distribution.mutable())
fun MutableFactor.immutable() = Factor(id, name, distribution.immutable())
fun LimitedBezierCurve.mutable() = MutableBezierCurve(controlStart, controlEnd)
fun MutableBezierCurve.immutable() = LimitedBezierCurve(controlStart, controlEnd)

/**
 * Runtime method set to evaluate a [SaltElement]
 *
 * To evaluate an element means to fix the possibility
 *
 * @param environment Where variables are stored
 * @param value What to evaluate
 */
class Formula<T>(
    private val environment: EvaluationEnvironment,
    val value: SaltElement
) {
    val type get() = value.type

    private val valuesCompiled by lazy {
        value.values.map {
            Crunch.compileExpression(it, environment)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun produce(clazz: Class<T>): T =
        when (clazz) {
            Matrix2x2::class.java -> {
                val eval = valuesCompiled.map { it.evaluate() }.toDoubleArray()
                when (value.type) {
                    SaltType.Rotation -> {
                        Matrix2x2.rotate(
                            if (eval[1] == 1.0)
                                eval.first()
                            else
                                eval.first() * PI / 180
                        )
                    }

                    SaltType.Scale -> Matrix2x2.scale(eval[0], eval[1])
                    SaltType.CustomMatrix -> Matrix2x2(eval)
                    else -> error("incompatible type: ${value.type.name.lowercase()} and matrix 2x2")
                } as T
            }

            Vector2D::class.java ->
                Vector2D(valuesCompiled[0].evaluate(), valuesCompiled[1].evaluate()) as T

            else -> throw NotImplementedError()
        }
}

val Matrix2x2Type get() = Matrix2x2::class.java
val Vector2DType get() = Vector2D::class.java


/**
 * Holding transforming operations of the same type,
 * which are applied in a linear order
 */
interface TransformChain {
    fun apply(point: Vector2D, anchor: MutableBox<Vector2D?>): Vector2D
}

data class TranslationChain(val values: List<Vector2D>) : TransformChain {
    private val composite by lazy {
        if (values.isEmpty()) return@lazy Vector2D.zero
        var sum = values.first()
        for (i in 1 until values.size) {
            sum += values[i]
        }
        sum
    }

    override fun apply(point: Vector2D, anchor: MutableBox<Vector2D?>): Vector2D {
        anchor.value?.let { anchor.value = it + composite }
        return point + composite
    }
}

data class TransformationChain(val values: List<Matrix2x2>) : TransformChain {
    private val composite by lazy {
        if (values.isEmpty()) return@lazy Matrix2x2.eye
        var t = values.first()
        for (i in 1 until values.size) {
            t *= values[i]
        }
        t
    }

    override fun apply(point: Vector2D, anchor: MutableBox<Vector2D?>): Vector2D {
        val captured = anchor.value ?: throw IllegalArgumentException("anchor is null")
        return composite * (point - captured) + captured
    }
}

/**
 * Runtime method set to transform a vector 2d
 *
 * One transformation may only contain one [anchor] and several [transforms]
 */
class Transformation(
    var anchor: Vector2D? = null,
    val transforms: List<TransformChain>
) {
    fun apply(point: Vector2D): Vector2D {
        if (transforms.isEmpty()) return point
        val anchor = anchor.mutbox()
        var last = transforms.first().apply(point, anchor)
        for (i in 1 until transforms.size) {
            last = transforms[i].apply(last, anchor)
        }
        return last
    }
}


enum class SaltType(val useMatrix: Boolean = true) {
    Anchor(false), Translation(false), Rotation, Scale, CustomMatrix
}

/**
 * Meta of [Matrix2x2] or [Vector2D]
 *
 * @param values Metas of the matrix produced, either name of a factor, a string
 * representation of a [Double], or a combination of them in a algebra manner.
 *
 * In case of representing a [Matrix2x2],
 * it contains 4 members corresponding to [Matrix2x2.values].
 *
 * In case of representing a [Vector2D],
 * it's a tuple of 2 members, corresponding to [Vector2D.x] and [Vector2D.y] respectively.
 */
@Serializable
data class SaltElement(val values: List<String>, val type: SaltType)

@Serializable
data class Salt2dData(
    val id: String = NanoIdUtils.randomNanoId(),
    val elements: List<SaltElement> = emptyList(),
    val factors: List<Factor> = emptyList()
)

class Salt2dRuntime(val data: Salt2dData) {
    private var transformers: List<Transformation>? = null

    /**
     * Apply the salt
     *
     * @param point The receiving point
     * @param projector The transformation happens on the **ideal** plane.
     * Basically, the [point] is projected to the idea plane, and back to
     * the target plane.
     */
    fun apply(
        point: Vector2D,
        projector: Projector = BypassProjector,
        parent: ClosedShape
    ): Vector2D {
        val transformers =
            this.transformers ?: resolve(projector, parent).also { this.transformers = it }

        if (transformers.isEmpty()) return point
        var last = transformers.first().apply(with(projector) { point.toIdeal() })
        for (i in 1 until transformers.size) {
            last = transformers[i].apply(last)
        }

        return with(projector) { last.toTarget() }
    }

    /**
     * To resolve a [Salt2dData] means to find out
     * what [Transformation]s should be taken
     * and what order they are in
     */
    internal fun resolve(projector: Projector, parent: ClosedShape): List<Transformation> =
        buildList {
            var lastAnchor: Vector2D? = null
            val center by lazy { with(projector) { parent.center(projector).toIdeal() } }
            var lastType: SaltType = data.elements.first().type
            val translate = mutableListOf<Vector2D>()
            var anchorOffset = Vector2D.zero
            val matrix = mutableListOf<Matrix2x2>()
            val chains = mutableListOf<TransformChain>()
            val environment = EvaluationEnvironment()
            data.factors.forEach {
                environment.addLazyVariable(it.name) { it.eval() }
            }
            environment.addLazyVariable("centerX") { center.x }
            environment.addLazyVariable("centerY") { center.y }

            fun commit() {
                add(Transformation(lastAnchor, chains.toList()))
                chains.clear()
            }

            fun commitTranslations() {
                if (translate.isEmpty()) return
                chains.add(TranslationChain(translate.toList()))
                translate.clear()
            }

            fun commitMatrix() {
                if (matrix.isEmpty()) return
                chains.add(TransformationChain(matrix.toList()))
                matrix.clear()
            }

            data.elements.forEach { ele ->
                when (ele.type) {
                    SaltType.Anchor -> {
                        if (lastAnchor != null) {
                            commitMatrix()
                            commitTranslations()
                            commit()
                        }
                        lastAnchor =
                            Formula<Vector2D>(environment, ele).produce(Vector2DType)
                    }

                    SaltType.Translation -> {
                        if (lastType != SaltType.Translation && lastType != SaltType.Anchor) {
                            commitTranslations()
                        }
                        val t = Formula<Vector2D>(environment, ele).produce(Vector2DType)
                        translate.add(t)
                        // update anchor offset
                        anchorOffset += t
                        environment.addLazyVariable("centerX") { center.x + anchorOffset.x }
                        environment.addLazyVariable("centerY") { center.y + anchorOffset.y }
                    }

                    else -> {
                        if (!lastType.useMatrix) {
                            commitMatrix()
                        }
                        matrix.add(Formula<Matrix2x2>(environment, ele).produce(Matrix2x2Type))
                    }
                }
                lastType = ele.type
            }
            commitMatrix()
            commitTranslations()
            commit()
        }
}

fun Salt2dData.runtime() = Salt2dRuntime(this)

/**
 * For editor
 */
class MutableSaltElement {
    val id: String
    val values: SnapshotStateList<String>
    val type: SaltType

    constructor(type: SaltType) {
        this.type = type
        id = NanoIdUtils.randomNanoId()
        values =
            when (type) {
                SaltType.Anchor ->
                    mutableStateListOf("centerX", "centerY")

                SaltType.Translation ->
                    mutableStateListOf("0", "0")

                SaltType.Rotation ->
                    mutableStateListOf("0", "0") // "0" for simple mode

                SaltType.Scale ->
                    mutableStateListOf("1", "1")

                SaltType.CustomMatrix ->
                    mutableStateListOf("1", "0", "0", "1")
            }
    }

    constructor(id: String = NanoIdUtils.randomNanoId(), values: List<String>, type: SaltType) {
        this.id = id
        this.values = values.toMutableStateList()
        this.type = type
    }

    operator fun set(dimension: Int, value: String) {
        values[dimension] = value
    }

    operator fun get(dimension: Int) = values[dimension]
}

fun SaltElement.mutable() = MutableSaltElement(values = values.toMutableStateList(), type = type)
fun MutableSaltElement.immutable() = SaltElement(values, type)


class LimitedBezierCurveSerializer : KSerializer<LimitedBezierCurve> {
    private val serializer = DoubleArraySerializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): LimitedBezierCurve {
        val array = serializer.deserialize(decoder)
        return LimitedBezierCurve(Vector2D(array[0], array[1]), Vector2D(array[2], array[3]))
    }

    override fun serialize(encoder: Encoder, value: LimitedBezierCurve) {
        serializer.serialize(
            encoder,
            doubleArrayOf(value.controlStart.x, value.controlStart.y, value.controlEnd.x, value.controlEnd.y)
        )
    }
}
