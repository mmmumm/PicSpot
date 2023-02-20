package com.example.picspot3.tflite

import android.content.Context
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.Throws
import com.example.picspot3.tflite.ClassifierWithModel
import org.tensorflow.lite.support.common.FileUtil
import android.graphics.Bitmap
import android.util.Pair
import android.util.Size
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.common.ops.NormalizeOp
import kotlin.jvm.JvmOverloads
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.model.Model
import java.io.IOException
import java.nio.ByteBuffer
import java.util.HashMap

//import java.util.HashMap
//import kotlin.collections.HashMap

class ClassifierWithModel(var context: Context) {
    var model: Model? = null
    var modelInputWidth = 0
    var modelInputHeight = 0
    var modelInputChannel = 0
    var inputImage: TensorImage? = null
    var outputBuffer: TensorBuffer? = null
    private var labels: List<String>? = null
    var isInitialized = false
        private set

    @Throws(IOException::class)
    fun init() {
        model = Model.createModel(context, MODEL_NAME)
        initModelShape()
        labels = FileUtil.loadLabels(context, LABEL_FILE)
        //        labels.remove(0);
        isInitialized = true
    }

    private fun initModelShape() {
        val inputTensor = model!!.getInputTensor(0)
        val shape = inputTensor.shape()
        modelInputChannel = shape[0]
        modelInputWidth = shape[1]
        modelInputHeight = shape[2]
        inputImage = TensorImage(inputTensor.dataType())
        val outputTensor = model!!.getOutputTensor(0)
        outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
    }

    val modelInputSize: Size
        get() = if (!isInitialized) Size(0, 0) else Size(modelInputWidth, modelInputHeight)

    private fun convertBitmapToARGB8888(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun loadImage(bitmap: Bitmap, sensorOrientation: Int): TensorImage {
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            inputImage!!.load(convertBitmapToARGB8888(bitmap))
        } else {
            inputImage!!.load(bitmap)
        }
        val cropSize = Math.min(bitmap.width, bitmap.height)
        val numRotation = sensorOrientation / 90
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(modelInputWidth, modelInputHeight, ResizeMethod.NEAREST_NEIGHBOR))
            .add(Rot90Op(numRotation))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()
        return imageProcessor.process(inputImage)
    }

    @JvmOverloads
    fun classify(image: Bitmap, sensorOrientation: Int = 0): Pair<String, Float> {
        inputImage = loadImage(image, sensorOrientation)
        val inputs = arrayOf<Any>(inputImage!!.buffer)
        val outputs: MutableMap<Int?, Any?> = HashMap()
        outputs[0] = outputBuffer!!.buffer.rewind()
        model!!.run(inputs, outputs)
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
        if (model != null) {
            model!!.close()
        }
    }

    companion object {
        private const val MODEL_NAME = "converted_model.tflite"
        private const val LABEL_FILE = "labels.txt"
    }
}