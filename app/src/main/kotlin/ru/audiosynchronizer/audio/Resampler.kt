package ru.audiosynchronizer.audio

object Resampler {

    fun linearInterleave(
        input: FloatArray,
        inputChannels: Int,
        outputChannels: Int,
        ratio: Double
    ): FloatArray {
        val inputFrames = input.size / inputChannels
        val outputFrames = if (ratio == 1.0) inputFrames else (inputFrames * ratio).toInt()
        val output = FloatArray(outputFrames * outputChannels)

        if (inputChannels == outputChannels && ratio == 1.0) {
            input.copyInto(output)
            return output
        }

        for (outFrame in 0 until outputFrames) {
            val srcFrame = outFrame / ratio
            val srcFrameInt = srcFrame.toInt()
            val frac = srcFrame - srcFrameInt

            for (ch in 0 until outputChannels) {
                val srcCh = if (ch < inputChannels) ch else (inputChannels - 1)
                val idx0 = srcFrameInt * inputChannels + srcCh
                val idx1 = ((srcFrameInt + 1).coerceAtMost(inputFrames - 1)) * inputChannels + srcCh

                output[outFrame * outputChannels + ch] = if (srcFrameInt >= inputFrames - 1) {
                    input[idx0]
                } else {
                    (input[idx0] * (1.0 - frac) + input[idx1] * frac).toFloat()
                }
            }
        }
        return output
    }

    fun resampleMonoToStereo(input: FloatArray): FloatArray {
        val output = FloatArray(input.size * 2)
        for (i in input.indices) {
            output[i * 2] = input[i]
            output[i * 2 + 1] = input[i]
        }
        return output
    }

    fun resampleWithRatio(
        input: FloatArray,
        channels: Int,
        fromRate: Int,
        toRate: Int
    ): FloatArray {
        if (fromRate == toRate) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val inputFrames = input.size / channels
        val outputFrames = (inputFrames * ratio).toInt()
        val output = FloatArray(outputFrames * channels)

        for (outFrame in 0 until outputFrames) {
            val srcFrame = outFrame / ratio
            val srcFrameInt = srcFrame.toInt()
            val frac = srcFrame - srcFrameInt

            for (ch in 0 until channels) {
                val idx0 = srcFrameInt * channels + ch
                val idx1 = ((srcFrameInt + 1).coerceAtMost(inputFrames - 1)) * channels + ch
                output[outFrame * channels + ch] = if (srcFrameInt >= inputFrames - 1) {
                    input[idx0]
                } else {
                    (input[idx0] * (1.0 - frac) + input[idx1] * frac).toFloat()
                }
            }
        }
        return output
    }
}
