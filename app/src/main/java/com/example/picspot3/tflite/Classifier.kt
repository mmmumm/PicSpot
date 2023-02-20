package com.example.picspot3.tflite

import android.content.Context
import kotlin.Throws
import android.content.res.AssetManager
import android.content.res.AssetFileDescriptor
import com.example.picspot3.tflite.Classifier
import android.graphics.Bitmap
import android.util.Pair
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

//import org.tensorflow.lite.support.label.TensorLabel;
class Classifier(var context: Context) {
    var interpreter: Interpreter? = null
    var modelInputWidth = 0
    var modelInputHeight = 0
    var modelInputChannel = 0
    var modelOutputClasses = 0
    @Throws(IOException::class)
    fun init() {
        interpreter = getInterpreter()
        initModelShape()
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelName: String): ByteBuffer {
        //org.tensorflow.lite.support.common.FileUtil에 구현되어있음
        //        org.tensorflow.lite.support.common.FileUtil.loadMappedFile(context, modelName);
        val am = context.assets
        val afd = am.openFd(modelName)
        val fis = FileInputStream(afd.fileDescriptor)
        val fc = fis.channel
        val startOffset = afd.startOffset
        val declaredLength = afd.declaredLength
        return fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @JvmName("getInterpreter1")
    @Throws(IOException::class)
    private fun getInterpreter(): Interpreter {
        val model = loadModelFile(MODEL_NAME)
        model.order(ByteOrder.nativeOrder())
        return Interpreter(model)
    }

    private fun initModelShape() {
        val inputTensor = interpreter!!.getInputTensor(0)
        val inputShape = inputTensor.shape()
        modelInputChannel = inputShape[0]
        modelInputWidth = inputShape[1]
        modelInputHeight = inputShape[2]
        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        modelOutputClasses = outputShape[1]
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, false)
    }

    private fun convertBitmapToGrayByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteByffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        byteByffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            val avgPixelValue = (r + g + b) / 3.0f
            val normalizedPixelValue = avgPixelValue / 255.0f
            byteByffer.putFloat(normalizedPixelValue)
        }
        return byteByffer
    }

    fun classify(image: Bitmap): Pair<Int, Float> {
        val buffer = convertBitmapToGrayByteBuffer(resizeBitmap(image))
        val result = Array(1) { FloatArray(modelOutputClasses) }
        interpreter!!.run(buffer, result)
        return argmax(result[0])
    }

    private fun argmax(array: FloatArray): Pair<Int, Float> {
        var argmax = 0
        var max = array[0]
        for (i in 1 until array.size) {
            val f = array[i]
            if (f > max) {
                argmax = i
                max = f
            }
        }
        return Pair(argmax, max)
    }

    fun finish() {
        if (interpreter != null) interpreter!!.close()
    }

    companion object {
        private const val MODEL_NAME = "converted_model.tflite"
    }
}