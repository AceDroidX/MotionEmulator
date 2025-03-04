package com.zhufucdev.motion_emulator.data

import com.zhufucdev.motion_emulator.dateString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream

/**
 * Basic motion record unit
 *
 * @param data SensorType to its value
 * @param elapsed Time from start (in sec.)
 */
@Serializable
data class MotionMoment(override var elapsed: Float, val data: MutableMap<Int, FloatArray>) : Moment

/**
 * Motion record, composed with series of [MotionMoment]s.
 * @param time Time when it was recorded in millis.
 * @param moments The series.
 * @param sensorsInvolved types of sensor that's possibly present in the [moments].
 * Notice that not every moment includes all the sensors.
 */
@Serializable
data class Motion(
    override val id: String,
    val name: String? = null,
    val time: Long,
    val moments: List<MotionMoment>,
    val sensorsInvolved: List<Int>
) : Data {
    override val displayName: String
        get() = name.takeIf { !it.isNullOrEmpty() } ?: dateString(time)

    @OptIn(ExperimentalSerializationApi::class)
    override fun writeTo(stream: OutputStream) {
        Json.encodeToStream(kotlinx.serialization.serializer(), this, stream)
    }
}

object Motions : DataStore<Motion>() {
    override val typeName: String get() = "motion"
    override val dataSerializer: KSerializer<Motion> get() = serializer()
}