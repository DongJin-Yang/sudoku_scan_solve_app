package com.example.sudoku_2;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

//    int [][] board_number = new int[9][9];

    private SudokuBoard gameBoard;
    private Solver gameBoardSolver;
    private Button solveBTN;

//    GALLERY
    private static final String TAG = MainActivity.class.getName();
    private final int REQ_GALLERY = 100;

    private ImageView imageView;
    private Bitmap bitmap;

    Classifier cls;

//    OpenCV 환경 구성 확인
    static {
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "OpenCV is not loaded!");
        }else {
            Log.d(TAG, "OpenCV is loaded successfully!");
        }
    }

//    onCreate() : 프래그먼트가 생성됨과 동시에 호출
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        뷰와 소스 코드 연결
        gameBoard = findViewById(R.id.SudokuBoard);
        gameBoardSolver = gameBoard.getSolver();

        solveBTN = findViewById(R.id.solveButton);


//


//        안드로이드 버전 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
//            퍼미션 상태 확인
            if(!hasPermissions(PERMISSIONS)){
//                퍼미션 허가 안되어 있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }
    }

//    GALLERY
//    프래그먼트에 연결된 모든 자원을 해제할 때 사용
    @Override
    protected void onDestroy() {
        super.onDestroy();
//      bitmap을 더 이상 사용하지 않는다면 해제
        bitmap.recycle();
        bitmap = null;

//      Classifier 닫기
        cls.finish();
    }

//    잠시 다른 액티비티로 전환 (startActivityForResult)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data){
        super.onActivityResult(requestCode, resultCode, data);

//      숫자 분류기 킴
        cls = new Classifier(this);
        try{
            cls.init();
        }
        catch (IOException e){
            e.printStackTrace();
        }


//        이미지 가져오기 & 이미지 돌아감 방지 & 숫자 분류
        switch(requestCode) {
            case REQ_GALLERY:
                if(resultCode == RESULT_OK){
                    try{
//                        이미지 가져오기
                        String path = getImagePathFromURI(data.getData());

                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 4;
                        bitmap = BitmapFactory.decodeFile(path, options);

//                        이미지 돌아감 방지
                        ExifInterface exif = null;

                        try {
                            exif = new ExifInterface(path);
                        }
                        catch(IOException e){
                            e.printStackTrace();
                        }
                        int exifOrientation;
                        int exifDegree;
                        if (exif != null) {
                            exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                            exifDegree = exifOrientationToDegrees(exifOrientation);
                        }
                        else {
                            exifDegree = 0;
                        }

//                        숫자 분류 및 저장
                        if(bitmap != null){
                            board();
                            bitmap = rotate(bitmap, exifDegree);
                            int width = bitmap.getWidth()/9;
                            int height = bitmap.getHeight()/9;


                            ArrayList<ArrayList<Integer>> aList = new ArrayList<ArrayList<Integer>>();

                            int [][] board_number = new int[9][9];
                            for (int y=0;y<9;y++){
                                ArrayList<Integer> temp = new ArrayList<Integer>();
                                for (int x=0;x<9;x++){
                                    Bitmap image = Bitmap.createBitmap(bitmap, x*width+5, y*height+13, width-10, height-15);


                                    Mat matrix = new Mat();
                                    MatOfDouble mStdDev = new MatOfDouble();
                                    Utils.bitmapToMat(image, matrix);
                                    Core.meanStdDev(matrix, new MatOfDouble(), mStdDev);
                                    double value = mStdDev.toArray()[0]*mStdDev.toArray()[0];
                                    if (value <= 3600){
//                                        temp.add(0);
                                        board_number[y][x] = 0;
                                    }
                                    else {
                                        Pair<Integer, Float> res = cls.classify(image);
//                                        temp.add(res.first);
                                        board_number[y][x] = res.first;
                                    }
                                }
//                                aList.add(temp);
                            }
                            gameBoardSolver.board = board_number;
//                            Log.d("TAG", "여기 왔니?");

                        }
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                }
        }
    }
    static final int PERMISSIONS_REQUEST_CODE = 1000;
    String[] PERMISSIONS = {"android.permission.READ_EXTERNAL_STORAGE"};
    private boolean hasPermissions(String[] permissions) {
        int result;
//        스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions){
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED){
//                허가 안된 퍼미션 발견
                return false;
            }
        }
//        모든 퍼미션이 허가 되었음
        return true;
    }

//    이미지 저장 장소 가져오기기
    public String getImagePathFromURI(Uri contentUri){
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null){
            return null;
        }else {
            int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String imgPath = cursor.getString(idx);
            cursor.close();
            return imgPath;
        }
    }
//    gallery 버튼 클릭시 전환
    public void onButtonClicked(View view){
        solveBTN.setText("Solve");

        gameBoardSolver.resetBoard();
        gameBoard.invalidate();
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
        intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//        다른 액티비티로 전환(onActivityResult)
        startActivityForResult(intent, REQ_GALLERY);
//        setNumber();
    }

//    스도쿠 보드 윤곽선 검출 및 원근 변환
    public void board(){
        Mat src = new Mat();
        Mat gray = new Mat();
        Mat blur = new Mat();
        Mat bitwise = new Mat();

        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Utils.bitmapToMat(bitmap, src);
//        이진화 및 윤곽선 검출
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.GaussianBlur(gray, blur, new Size(5, 5), 0);
        Imgproc.adaptiveThreshold(blur, blur, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2);
        Core.bitwise_not(blur,bitwise);
        Imgproc.findContours(bitwise, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);


//        원근 변환
        double maxVal = 0;
        int maxValIdx = 0;
        for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++)
        {
            double contourArea = Imgproc.contourArea(contours.get(contourIdx));
            if (maxVal < contourArea)
            {
                maxVal = contourArea;
                maxValIdx = contourIdx;
            }

        }

        MatOfPoint2f candidate2f = new MatOfPoint2f(contours.get(maxValIdx).toArray());
        MatOfPoint2f approxCandidate = new MatOfPoint2f();
        Imgproc.approxPolyDP(candidate2f, approxCandidate,
                Imgproc.arcLength(candidate2f, true)*0.02,
                true);
        double height = src.size().height;
        double width = src.size().width;


        Point[] sortedPoints = new Point[4];
        sortedPoints[0] = new Point(approxCandidate.get(1, 0)[0],  approxCandidate.get(1, 0)[1]);
        sortedPoints[1] = new Point(approxCandidate.get(0, 0)[0],  approxCandidate.get(0, 0)[1]);
        sortedPoints[2] = new Point(approxCandidate.get(2, 0)[0],  approxCandidate.get(2, 0)[1]);
        sortedPoints[3] = new Point(approxCandidate.get(3, 0)[0],  approxCandidate.get(3, 0)[1]);


        MatOfPoint2f start = new MatOfPoint2f(
                sortedPoints[0],
                sortedPoints[1],
                sortedPoints[2],
                sortedPoints[3]
        );

        MatOfPoint2f end = new MatOfPoint2f(
                new Point(0, 0),
                new Point(width, 0),
                new Point(0, height),
                new Point(width, height)
        );

        Mat warpMat = Imgproc.getPerspectiveTransform(start, end);
        Mat destImage = new Mat();
        Imgproc.warpPerspective(bitwise, destImage, warpMat, new Size(width, height));

        Utils.matToBitmap(destImage, bitmap);

    }

    // 사진의 돌아간 각도를 계산하는 메서드 선언
    private int exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    // 이미지를 회전시키는 메서드 선언
    private Bitmap rotate(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

//    public void setNumber(){
//        for (int y=0;y<9;y++) {
//            for (int x = 0; x < 9; x++) {
//                gameBoardSolver.setNumber(x, y ,board_number[x][y]);
//            }
//        }
//    }

//    해당 보드에 숫자 입력
    public void BTNOnePress(View view){
        gameBoardSolver.setNumberPos(1);
        gameBoard.invalidate();
    }
    public void BTNTwoPress(View view){
        gameBoardSolver.setNumberPos(2);
        gameBoard.invalidate();
    }
    public void BTNThreePress(View view){
        gameBoardSolver.setNumberPos(3);
        gameBoard.invalidate();
    }
    public void BTNFourPress(View view){
        gameBoardSolver.setNumberPos(4);
        gameBoard.invalidate();
    }
    public void BTNFivePress(View view){
        gameBoardSolver.setNumberPos(5);
        gameBoard.invalidate();
    }public void BTNSixPress(View view){
        gameBoardSolver.setNumberPos(6);
        gameBoard.invalidate();
    }
    public void BTNSevenPress(View view){
        gameBoardSolver.setNumberPos(7);
        gameBoard.invalidate();
    }
    public void BTNEightPress(View view){
        gameBoardSolver.setNumberPos(8);
        gameBoard.invalidate();
    }
    public void BTNNinePress(View view){
        gameBoardSolver.setNumberPos(9);
        gameBoard.invalidate();
    }
    public void BTNDeletePress(View view){
        gameBoardSolver.setNumberPos(0);
        gameBoard.invalidate();
    }


//    스도쿠 정답 표시
    public void solve (View view) {
        if (solveBTN.getText().toString().equals("Solve")){
            solveBTN.setText("Clear");

            gameBoardSolver.getEmptyBoxIndexes();

            SolveBoardThread solveBoardThread = new SolveBoardThread();

            new Thread(solveBoardThread).start();
//            화면 갱신
            gameBoard.invalidate();

        }
//        스도쿠 보드 백지화
        else {
            solveBTN.setText("Solve");

            gameBoardSolver.resetBoard();
            gameBoard.invalidate();
        }
    }
    class SolveBoardThread implements Runnable{
        @Override
        public void run() { gameBoardSolver.solve(gameBoard); }
    }

}