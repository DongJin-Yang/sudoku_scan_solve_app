package com.example.sudoku_2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.core.util.Pair;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class Classifier {
//    private static final String MODEL_NAME = "keras_model_cnn.tflite";
    private static final String MODEL_NAME = "augmented.tflite";


    Context context;
    Interpreter interpreter = null;
    int modelInputWidth, modelInputHeight, modelInputChannel;
    int modelOutputClasses;

    public Classifier(Context context) {
        this.context = context;
    }

    public void init() throws IOException {
        ByteBuffer model = loadModelFile(MODEL_NAME);
        model.order(ByteOrder.nativeOrder());
        interpreter = new Interpreter(model);
        // 모델의 입출력 크기 계산 함수 호출
        initModelShape();
    }

    private ByteBuffer loadModelFile(String modelName) throws IOException {
        // AssetManager를 얻음 -> assets 폴더에 저장된 리소스에 접근하기 위한 기능 제공
        AssetManager am = context.getAssets();
        // openFd() 함수에 tflite 파일명을 전다랗면 AssetFileDescriptor를 얻음
        AssetFileDescriptor afd = am.openFd(modelName); ////  !!!!!!!!!!!!!!!! 여기 !!!!!!!!!!!!!!!!!
        // 읽은 파일의 FileDescriptor를 얻음 -> 파일의 읽기/쓰기 가능
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        // FileChannle의 map() 함수에 길이와 오프셋을 전달하면
        // ByteBuffer 클래스를 상속한 MappedByteBuffer 객체를 반환
        // tflite 파일 -> ByteBuffer 형으로 읽어들임
        long startOffset = afd.getStartOffset();
        long declaredLength = afd.getDeclaredLength();
        return fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void initModelShape(){
        // 모델의 입출력 크기 계산 함수 정의
        Tensor inputTensor = interpreter.getInputTensor(0);
        int[] inputShape = inputTensor.shape();
        modelInputChannel = inputShape[0];
        modelInputWidth = inputShape[1];
        modelInputHeight = inputShape[2];


        // 모델 출력 클래스 수 계산
        Tensor outputTensor = interpreter.getOutputTensor(0);
        int[] outputShape = outputTensor.shape();
        modelOutputClasses = outputShape[1];
    }
    private Bitmap resizeBitmap(Bitmap bitmap){
        // 입력 이미지 크기 변환
        // 함수에 변환할 이미지, 가로 크기, 세로 크기, 이미지 보간법x
        return Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, false);
    }
    private ByteBuffer convertBitmapToGrayByteBuffer(Bitmap bitmap) {
        // ARGB를 GrayScale로 변환하면서 Bitmap을 ByteBuffer 포맷으로 변환
        ByteBuffer byteByffer = ByteBuffer.allocateDirect(bitmap.getByteCount());
        byteByffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int pixel : pixels) {
            int r = pixel >> 16 & 0xFF;
            int g = pixel >> 8 & 0xFF;
            int b = pixel & 0xFF;

            float avgPixelValue = (r + g + b) / 3.0f;
            float normalizedPixelValue = avgPixelValue / 255.0f;

            byteByffer.putFloat(normalizedPixelValue);
        }

        return byteByffer;
    }
    public Pair<Integer, Float> classify(Bitmap image) {
        // 손글씨 분류 모델의 추론 및 결과 해석
        ByteBuffer buffer = convertBitmapToGrayByteBuffer(resizeBitmap(image));

        float[][] result = new float[1][modelOutputClasses];
        interpreter.run(buffer, result);
        return argmax(result[0]);
    }

    private Pair<Integer, Float> argmax(float[] array) {
        // 추론 결과 해석
        int argmax = 0;
        float max = array[0];
        for(int i = 1; i < array.length; i++) {
            float f = array[i];
            if(f > max) {
                argmax = i;
                max = f;
            }
        }
        return new Pair<>(argmax, max);
    }

    public void finish() {
        if(interpreter != null)
            interpreter.close();
    }
}
