/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wtk.camerademo.cameraview;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;


import com.wtk.camerademo.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 自定义相机
 */
public class CameraActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback {

    private Button button;//拍摄按钮
    private SurfaceView surfaceView;//预览界面
    private Camera mCamera;//相机
    private Camera.Size mBestPictureSize;//最佳拍摄尺寸
    private Camera.Size mBestPreviewSize;//最佳拍摄尺寸
    private boolean mIsSurfaceReady;//预览页面是否准备完成
    private boolean waitTakePicture = false;//是否等待拍照完成
    private OrientationEventListener mOrEventListener; // 设备方向监听器
    private ImageView pic_img;
    //是否预览中
    private boolean isPreviewing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);  //设置全屏
        transparentStatusBar();
        setContentView(R.layout.activity_camera);

        initView();//初始化视图
        initData();//初始化数据
        initListener();//初始化监听

    }

    private void initView() {

        // 当此窗口为用户可见时，保持设备常开，并保持亮度不变。
        button = (Button) this.findViewById(R.id.button_picture);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceView_myFirst);
        pic_img = (ImageView) this.findViewById(R.id.pic_img);
    }

    private void initData() {
        //设置分辨率
//        surfaceView.getHolder().setFixedSize(320,240);
        //设置预览回调
        surfaceView.getHolder().addCallback(new HolderListener());
    }

    /**
     * 初始化监听
     */
    private void initListener() {
        // 拍摄按钮点击
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        //这个是点击对焦，不需要可以屏蔽
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int areaX = (int) (event.getX() / surfaceView.getWidth() * 2000) - 1000; // 获取映射区域的X坐标
                int areaY = (int) (event.getY() / surfaceView.getHeight() * 2000) - 1000; // 获取映射区域的Y坐标

                // 创建Rect区域
                Rect focusArea = new Rect();
                focusArea.left = Math.max(areaX - 100, -1000); // 取最大或最小值，避免范围溢出屏幕坐标
                focusArea.top = Math.max(areaY - 100, -1000);
                focusArea.right = Math.min(areaX + 100, 1000);
                focusArea.bottom = Math.min(areaY + 100, 1000);
                // 创建Camera.Area
                Camera.Area cameraArea = new Camera.Area(focusArea, 1000);
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
                final Camera.Parameters mParameters = mCamera.getParameters();
                if (mParameters.getMaxNumMeteringAreas() > 0) {
                    meteringAreas.add(cameraArea);
                    focusAreas.add(cameraArea);
                }
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置对焦模式
                mParameters.setFocusAreas(focusAreas); // 设置对焦区域
                mParameters.setMeteringAreas(meteringAreas); // 设置测光区域
                try {
                    mCamera.cancelAutoFocus(); // 每次对焦前，需要先取消对焦
                    mCamera.setParameters(mParameters); // 设置相机参数
                    mCamera.autoFocus(null); // 开启对焦
                } catch (Exception e) {
                }

                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("mIsSurfaceReady",mIsSurfaceReady+"");
//        if(!mIsSurfaceReady){
//            surfaceView.removeCallbacks(null);
//            surfaceView.getHolder().addCallback(new HolderListener());
//        }

        // 确保能够获取到SurfaceView的大小
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                //打开相机
                openCamera();
            }
        });
    }



    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        closeCamera();
    }

    private void openCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
            } catch (RuntimeException e) {
                if ("Fail to connect to camera service".equals(e.getMessage())) {
                    //提示无法打开相机，请检查是否已经开启权限
                } else if ("Camera initialization failed".equals(e.getMessage())) {
                    //提示相机初始化失败，无法打开
                } else {
                    //提示相机发生未知错误，无法打开
                }
                finish();
                return;
            }
        }
        initCamera();

    }

    private void initCamera() {
        //获取相机参数
        final Camera.Parameters parameters = mCamera.getParameters();
        //设置拍摄后的照片格式
        parameters.setPictureFormat(ImageFormat.JPEG);
        //设置自动对焦
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 连续对焦

        //设置旋转90度，竖屏拍照，所以还需要对Camera旋转90度
        parameters.setRotation(90);
        //获取预览界面宽高比
        final float ratio =  ((float)surfaceView.getMeasuredWidth() / (float)surfaceView.getMeasuredHeight());
        //获取相机支持的宽高比,此为预览画面
        List<Camera.Size> previewSize = parameters.getSupportedPreviewSizes();
        //获取相机支持的图片尺寸
        List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
//        for (Camera.Size item : pictureSizes ) {
//            System.out.println("支持的宽："+item.width+"支持的高："+item.height );
//        }
        //获取最佳图片尺寸,使图片和surfaceView的比例接近
        if (mBestPictureSize == null) {
            mBestPictureSize = getPictureSize(pictureSizes, ratio);
        }
        //获取最佳预览尺寸
        if(mBestPreviewSize == null){
            mBestPreviewSize = getPictureSize(previewSize, ratio);
        }
        //设置照片尺寸,可以改变分辨率
        parameters.setPictureSize(mBestPictureSize.width, mBestPictureSize.height);
        Log.e("camera", "图片宽高------->" + "宽："+ mBestPictureSize.width + "|" + "高"+mBestPictureSize.height);

        // 设置相机预览的尺寸
        parameters.setPreviewSize(mBestPreviewSize.width, mBestPreviewSize.height);
        Log.e("camera", "预览宽高------->" + "宽："+ mBestPreviewSize.width + "|" + "高"+mBestPreviewSize.height);

        //设置预览界面surfaceView的比例，与相机的预览尺寸比例一样，才不会导致预览出来的surfaceView结果是变形的
        ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();

        //这个宽度比高度大
        double PreviewScale = (float) mBestPreviewSize.width / (float) mBestPreviewSize.height;
        //view的宽高比
        double viewScale = (float) surfaceView.getHeight() / (float) surfaceView.getWidth();

        if(PreviewScale > viewScale){
            //如果预览的宽高比 比 view的宽高比大
            layoutParams.height = (int) (surfaceView.getWidth() * PreviewScale);
        }else{
            layoutParams.width = (int) (surfaceView.getHeight() / PreviewScale);
        }

        Log.e("camera", "实际预览宽高------->" + "宽："+ layoutParams.width + "|" + "高"+layoutParams.height);
//        surfaceView.setLayoutParams(layoutParams);
        mCamera.setParameters(parameters);

        //预览界面是否初始化完成
        if (mIsSurfaceReady) {
            startPreview();
        }
    }

    /**
     * 开启相机预览
     */
    private void startPreview() {
        if (mCamera == null) {
            return;
        }
        try {
            mCamera.setPreviewDisplay(surfaceView.getHolder());
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
            // 连续对焦，不需要可以屏蔽
            mCamera.cancelAutoFocus();

            isPreviewing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 请求自动对焦
     */
    private void requestFocus() {
        if (mCamera == null || waitTakePicture) {
            return;
        }
        mCamera.autoFocus(null);
    }

    /**
     * 拍照
     */
    public void takePicture() {
        if (mCamera == null || waitTakePicture) {
            return;
        }

        isPreviewing = false;

        // 设置自动对焦，手动对焦不需要，
//        requestFocus();
        waitTakePicture = true;

        //拍照方法
        mCamera.takePicture(null, null, new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                processPicture(data);
                waitTakePicture = false;
                // 拍照之后，预览的展示会停止。如果想继续拍照，需要先再调用startPreview()
                //预览界面是否初始化完成
                if (mIsSurfaceReady) {
                    startPreview();
                }
            }
        });


    }

    /**
     * 处理图片数据
     *
     * @param data
     */
    private void processPicture(byte[] data) {

        //sd卡路径
        String absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        File fileDir = new File(absolutePath + "/testfile");
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        File file = new File(fileDir.getPath() + "/" + System.currentTimeMillis() + ".jpg");

        // 竖屏拍摄的处理，图片不需要旋转
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data);
            os.flush();
            Toast.makeText(this, "拍摄完成", Toast.LENGTH_LONG).show();
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            pic_img.setImageBitmap(bitmap);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }


    /**
     * 停止预览
     */
    private void stopPreview() {
        if (mCamera == null) {
            return;
        }
        try {
            mCamera.setPreviewDisplay(null);
            mCamera.setDisplayOrientation(0);
            mCamera.stopPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭相机
     */
    private void closeCamera() {
        if (mCamera == null) {
            return;
        }
        mCamera.cancelAutoFocus();
        stopPreview();
        mCamera.release();
        mCamera = null;
    }


    /**
     * 相机预览回调
     */
    public class HolderListener implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mIsSurfaceReady = true;
            // 在surfaceView回调成功后再打开相机，避免出现预览黑屏，解决小米手机重后台打开无法打开预览
            startPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mIsSurfaceReady = false;
        }
    }


    /**
     * 获取pictureSize的合适值
     * @param pictureSizes      size集合
     * @param rate      传入的宽高比
     * @return
     */
    public Camera.Size getPictureSize(List<Camera.Size> pictureSizes, float rate) {

        DisplayMetrics dm = new DisplayMetrics();
        //获取屏幕信息
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        ArrayList<Camera.Size> equalRateList = new ArrayList();

        Collections.sort(pictureSizes, new CameraSizeComparator());   //统一以升序的方式排列

        for (Camera.Size s : pictureSizes) {
            if (((s.width > s.height? s.width : s.height) >= screenHeight || (s.width > s.height? s.height : s.width) >= screenWidth) && equalRate(s, rate)) {        //合适参数的判断条件， 大于我们传入的最小高度且宽高比的差值不能超过0.2
                equalRateList.add(s);
            }
        }
        if (equalRateList.size() <= 0) {                 //如果到最后也没有找到合适的，哪么就放宽条件去找
            return getBestSize(pictureSizes, rate);
        } else {

            int size = equalRateList.size();
            int index = size / 2;

            if(size % 2 != 0){
                index += 1;
            }

            if(index >= size){
                index = size - 1;
            }

            if(index < 0){
                index = 0;
            }

            return equalRateList.get(index);                 //返回找到的合适size
        }
    }

    /**
     * 遍历所有的size，找到和传入的宽高比的差值最小的一个
     * @param list
     * @param rate
     * @return
     */
    private Camera.Size getBestSize(List<Camera.Size> list, float rate) {
        float previewDisparity = 100;
        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            Camera.Size cur = list.get(i);
            float prop = (float) cur.width / (float) cur.height;
            if (Math.abs(rate - prop) < previewDisparity) {
                previewDisparity = Math.abs(rate - prop);
                index = i;
            }
        }
        return list.get(index);
    }


    private boolean equalRate(Camera.Size s, float rate) {
        float r = (float) (s.height) / (float) (s.width);

        if(r < 1.0 && rate > 1.0 || r > 1.0 && rate < 1.0){
            r = (float) (s.width) / (float) (s.height);
        }

        return Math.abs(r - rate) <= 0.2;   //传入的宽高比和size的宽高比的差不能大于0.2,要尽量的和传入的宽高比相同
    }

    private class CameraSizeComparator implements Comparator<Camera.Size> {
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            if (lhs.width == rhs.width) {
                return 0;
            } else if (lhs.width > rhs.width) {
                return 1;
            } else {
                return -1;
            }
        }

    }


    /**
     * 状态栏透明
     */
    protected void transparentStatusBar(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    public boolean isStrEmpty(String value) {
        if (null == value || "".equals(value.trim())) {
            return true;
        } else {
            // 判断是否全是全角空格
            value = value.replaceAll(" ", "").trim();
            if (null == value || "".equals(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文件是否存在
     */
    public String checkDirPath(String dirPath) {
        if (TextUtils.isEmpty(dirPath)) {
            return "";
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dirPath;
    }
}
