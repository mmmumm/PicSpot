package com.example.picspot3.tflite

import android.content.Context
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.Throws
import org.tensorflow.lite.support.common.FileUtil
import com.example.picspot3.tflite.ClassifierWithSupport
import android.graphics.Bitmap
import android.util.Pair
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.label.TensorLabel
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClassifierWithSupport(var context: Context) {
    var interpreter: Interpreter? = null
    var modelInputWidth = 0
    var modelInputHeight = 0
    var modelInputChannel = 0
    var inputImage: TensorImage? = null
    var outputBuffer: TensorBuffer? = null
    private var labels: List<String>? = null
    @Throws(IOException::class)
    fun init() {
        val model: ByteBuffer = FileUtil.loadMappedFile(context, MODEL_NAME)
        model.order(ByteOrder.nativeOrder())
        interpreter = Interpreter(model)
        initModelShape()
        labels = FileUtil.loadLabels(context, LABEL_FILE)
        //        labels.remove(0);
    }

    private fun initModelShape() {
        val inputTensor = interpreter!!.getInputTensor(0)
        val shape = inputTensor.shape()
        modelInputChannel = shape[0]
        modelInputWidth = shape[1]
        modelInputHeight = shape[2]
        inputImage = TensorImage(inputTensor.dataType())
        val outputTensor = interpreter!!.getOutputTensor(0)
        outputBuffer = TensorBuffer.createFixedSize(
            outputTensor.shape(),
            outputTensor.dataType()
        )
    }

    private fun convertBitmapToARGB8888(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun loadImage(bitmap: Bitmap): TensorImage {
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            inputImage!!.load(convertBitmapToARGB8888(bitmap))
        } else {
            inputImage!!.load(bitmap)
        }
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(modelInputWidth, modelInputHeight, ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()
        return imageProcessor.process(inputImage)
    }

    fun classify(image: Bitmap): Pair<String, Float> {
        inputImage = loadImage(image)
        interpreter!!.run(inputImage!!.buffer, outputBuffer!!.buffer.rewind())
        val output = TensorLabel(labels!!, outputBuffer!!).mapWithFloatValue
        return argmax(output)
    }

    private fun argmax(map: Map<String, Float>): Pair<String, Float> {
        var maxKey = ""
        var maxVal = -1f
        for ((key, f) in map) {
            if (f > maxVal) {
                maxKey = key
                maxVal = f
            }
        }
        return Pair(maxKey, maxVal)
    }

    fun finish() {
        if (interpreter != null) interpreter!!.close()
    }

    companion object {
        private const val MODEL_NAME = "converted_model.tflite"
        private const val LABEL_FILE = "labels.txt"
    }
}