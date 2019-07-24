package com.wtk.camerademo.cameraview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

/**
 * Created by Administrator on 2016/8/25.
 */
public class ImgUtils {

    /**
     * 将图片纠正到正确方向
     *
     * @param degree
     *            ： 图片被系统旋转的角度
     * @param bitmap
     *            ： 需纠正方向的图片
     * @return 纠向后的图片
     */
    public static Bitmap rotateBitmap(int degree, Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap bm = null;
        try {
            bm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            System.gc();
            bm = bitmap;
        }
        return bm;
    }

    /**
     * 照片写入文件
     *
     * @param b        数据
     * @param fileName 文件路径
     */
    public static boolean data2file(Bitmap b, String fileName) {
        long starttime = System.currentTimeMillis();
        Log.e("图片写文件开始","");
        boolean result = false;
        try {
            FileOutputStream fout = new FileOutputStream(fileName);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            b.compress(Bitmap.CompressFormat.JPEG, 90, bos);
            bos.flush();
            bos.close();
            fout.close();
            bos = null;
            fout.close();
            result = true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("保存图片失败-->" ,"");
        }
//        if (b != null || !b.isRecycled()) {
//            b.recycle();
//            b = null;
//            System.gc();
//        }
        long endtime = System.currentTimeMillis();
        Log.e("图片写文件结束--->" ,"");
        return result;
    }






    /**
     * byte转bitmap
     * @param b
     * @return
     */
    public static Bitmap Bytes2Bimap(byte[] b) {
        if (b.length != 0) {
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } else {
            return null;
        }
    }


    /**
     * bitmap 转 byte
     */
    public static byte[] Bitmap2Bytes(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }

}
