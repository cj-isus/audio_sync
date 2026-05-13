package ru.audiosynchronizer.sync

class KalmanFilter(
    private var processStdDev: Double = 0.01,
    private var driftProcessStdDev: Double = 0.0,
    private var forgetFactor: Double = 1.001,
    private var adaptiveCutoff: Double = 0.75,
    private var minSamples: Int = 100,
    private var driftThreshold: Double = 2.0
) {
    private var offset: Double = 0.0
    private var driftRate: Double = 0.0
    private var pOffset: Double = 1e10
    private var pDrift: Double = 1e10
    private var pCross: Double = 0.0
    private var sampleCount: Int = 0
    private var lastMeasurementTime: Long = 0L

    fun update(measurement: Double, rtt: Double, currentTimeNs: Long = System.nanoTime()) {
        val dt = if (lastMeasurementTime > 0L) {
            (currentTimeNs - lastMeasurementTime) / 1e9
        } else {
            1.0
        }
        lastMeasurementTime = currentTimeNs

        val q0 = processStdDev * processStdDev * dt
        val q1 = driftProcessStdDev * driftProcessStdDev * dt

        pOffset += q0
        pDrift += q1
        pCross += 0.0

        offset += driftRate * dt

        val r = (rtt / 2.0) * (rtt / 2.0)
        val innovation = measurement - offset
        val s = pOffset + r

        if (sampleCount >= minSamples) {
            val ratio = kotlin.math.abs(innovation) / kotlin.math.sqrt(s)
            if (ratio > adaptiveCutoff * kotlin.math.sqrt(kotlin.math.abs(pOffset))) {
                pOffset *= forgetFactor
                pDrift *= forgetFactor
            }
        }

        val k0 = pOffset / s
        val k1 = pCross / s

        offset += k0 * innovation
        driftRate += k1 * innovation

        pOffset -= k0 * pOffset
        pDrift -= k1 * pCross
        pCross -= k0 * pCross

        if (kotlin.math.abs(driftRate) > driftThreshold) {
            driftRate = driftThreshold * kotlin.math.sign(driftRate)
        }

        sampleCount++
    }

    fun getOffset(): Double = offset

    fun getDriftRate(): Double = driftRate

    fun isStable(): Boolean = sampleCount >= minSamples

    fun getSampleCount(): Int = sampleCount

    fun reset() {
        offset = 0.0
        driftRate = 0.0
        pOffset = 1e10
        pDrift = 1e10
        pCross = 0.0
        sampleCount = 0
        lastMeasurementTime = 0L
    }
}
