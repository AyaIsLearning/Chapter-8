package com.bytedance.camera.demo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.VideoView;

import com.bytedance.camera.demo.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import static com.bytedance.camera.demo.utils.Utils.MEDIA_TYPE_IMAGE;
import static com.bytedance.camera.demo.utils.Utils.getOutputMediaFile;

public class CustomCameraActivity extends AppCompatActivity {

    private SurfaceView mSurfaceView;
    private Camera mCamera;

    private int CAMERA_TYPE = Camera.CameraInfo.CAMERA_FACING_BACK;
    //private int CAMERA_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private boolean isRecording = false;
    private File videoFile;
    private int rotationDegree = 0;
    MediaPlayer mediaPlayer;
    int position;
    boolean isPreview;

    int mDelayState = 0;

    boolean flashOpen = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_custom_camera);
        mediaPlayer = new MediaPlayer();
        mSurfaceView = findViewById(R.id.img);
        //todo 给SurfaceHolder添加Callback
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                startPreview(surfaceHolder);
                mCamera.startPreview();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if(mCamera != null){
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                }
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    position = mediaPlayer.getCurrentPosition();
                    mediaPlayer.stop();
                }
            }
        });

        findViewById(R.id.btn_picture).setOnClickListener(v -> {
            //todo 拍一张照片
            if(!isPreview){
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                }
                mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                startPreview(mSurfaceView.getHolder());
                mCamera.startPreview();
            }

            if (mDelayState == 0) {
                mCamera.takePicture(null,null,mPicture);
            } else {
                new CountDownTimer(mDelayState*1000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        Toast.makeText(CustomCameraActivity.this, "倒计时"+millisUntilFinished/1000+"秒", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFinish() {
                        mCamera.takePicture(null,null,mPicture);
                    }
                }.start();
            }

        });

        findViewById(R.id.btn_record).setOnClickListener(v -> {
            //todo 录制，第一次点击是start，第二次点击是stop
            //mCamera.stopPreview();

            if (isRecording) {
                //todo 停止录制
                //mMediaRecorder.stop();
                isRecording = false;
                releaseMediaRecorder();
                System.out.println(videoFile.getPath());
                releaseCameraAndPreview();
                playVideo();
            } else {
                //todo 录制
                if(!isPreview){
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    }
                    mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                    startPreview(mSurfaceView.getHolder());
                    mCamera.startPreview();
                }
                prepareVideoRecorder();
                isRecording=true;
            }
        });

        findViewById(R.id.btn_facing).setOnClickListener(v -> {
            //todo 切换前后摄像头
            if(CAMERA_TYPE == Camera.CameraInfo.CAMERA_FACING_BACK){
                CAMERA_TYPE = Camera.CameraInfo.CAMERA_FACING_FRONT;
            }
            else{
                CAMERA_TYPE = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            releaseCameraAndPreview();
            startPreview(mSurfaceView.getHolder());
        });

        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            //todo 调焦，需要判断手机是否支持
            Camera.Parameters parameters = mCamera.getParameters();
            if(parameters.isZoomSupported()){
                int zoom = parameters.getZoom();
                int max_zoom = parameters.getMaxZoom();
                if(zoom < max_zoom){
                    zoom++;
                }
                parameters.setZoom(zoom);
                mCamera.setParameters(parameters);
            }
            else{
                Toast.makeText(this, "不支持放大", Toast.LENGTH_SHORT).show();
            }
        });

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            mediaPlayer.stop();
            mediaPlayer.release();
            mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            startPreview(mSurfaceView.getHolder());
            mCamera.startPreview();
        });

        findViewById(R.id.btn_flash).setOnClickListener(view -> {
            if(!mediaPlayer.isPlaying() && mCamera != null){
                Camera.Parameters parameters = mCamera.getParameters();
                if(flashOpen){
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);//关闭
                    mCamera.setParameters(parameters);
                }
                else {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//开启
                    mCamera.setParameters(parameters);
                }
                flashOpen=!flashOpen;
            }
        });

        findViewById(R.id.btn_0S).setOnClickListener(v->{
            mDelayState=0;
            Toast.makeText(this, "设定为0S延迟", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_3S).setOnClickListener(v->{
            mDelayState=3;
            Toast.makeText(this, "设定为3S延迟", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_10S).setOnClickListener(v->{
            mDelayState=10;
            Toast.makeText(this, "设定为10S延迟", Toast.LENGTH_SHORT).show();
        });
    }


    public Camera getCamera(int position) {
        CAMERA_TYPE = position;
        if (mCamera != null) {
            releaseCameraAndPreview();
        }
        Camera cam = Camera.open(position);

        //todo 摄像头添加属性，例是否自动对焦，设置旋转方向等
        rotationDegree = getCameraDisplayOrientation(position);
        cam.setDisplayOrientation(rotationDegree);
        Camera.Parameters parameters = cam.getParameters();
        if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            cam.setParameters(parameters);
        }

        return cam;
    }


    private static final int DEGREE_90 = 90;
    private static final int DEGREE_180 = 180;
    private static final int DEGREE_270 = 270;
    private static final int DEGREE_360 = 360;

    private int getCameraDisplayOrientation(int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = DEGREE_90;
                break;
            case Surface.ROTATION_180:
                degrees = DEGREE_180;
                break;
            case Surface.ROTATION_270:
                degrees = DEGREE_270;
                break;
            default:
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % DEGREE_360;
            result = (DEGREE_360 - result) % DEGREE_360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + DEGREE_360) % DEGREE_360;
        }
        return result;
    }


    private void releaseCameraAndPreview() {
        //todo 释放camera资源
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            isPreview = false;
        }
    }

    Camera.Size size;

    private void startPreview(SurfaceHolder holder) {
        //todo 开始预览
        isPreview = true;
        if(mCamera == null){
            mCamera = getCamera(CAMERA_TYPE);
        }
        try {
            size = getOptimalPreviewSize(mCamera.getParameters().getSupportedPictureSizes(),mSurfaceView.getWidth(),mSurfaceView.getHeight());
            mCamera.getParameters().setPictureSize(size.width,size.height);
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            mCamera.cancelAutoFocus();
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private MediaRecorder mMediaRecorder;

    private boolean prepareVideoRecorder() {
        //todo 准备MediaRecorder
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        videoFile = new File(Utils.getOutputMediaFile(2).getAbsolutePath());
        mMediaRecorder.setOutputFile(videoFile);
        mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
        mMediaRecorder.setOrientationHint(rotationDegree);
        try{
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            return true;
        }catch (Exception e){
            releaseMediaRecorder();
            return false;
        }
    }


    private void releaseMediaRecorder() {
        //todo 释放MediaRecorder
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mCamera.lock();
        }
    }


    private Camera.PictureCallback mPicture = (data, camera) -> {
        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            System.out.println("wtf");
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
            //System.out.println(pictureFile.getName());
            MediaStore.Images.Media.insertImage(this.getContentResolver(),pictureFile.getPath(),pictureFile.getName(),null);
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(pictureFile);
            intent.setData(uri);
            this.sendBroadcast(intent);
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.d("mPicture", "Error accessing file: " + e.getMessage());
        }
        mCamera.startPreview();
    };


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = Math.min(w, h);

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }


    private static Rect calculateTapArea(float x, float y, float coefficient, Camera.Size previewSize) {
        int centerX = (int)(x/previewSize.height*2000)-1000;
        int centerY = (int)(y/previewSize.width*2000)-1000;
        //System.out.println(previewSize.width+" "+previewSize.height+" "+x+" "+y+" "+centerX+" "+centerY+" ");
        int left = clamp(centerX - 100, -1000, 1000);
        int top = clamp(centerY - 100, -1000, 1000);
        int right = clamp(centerX + 100, -1000, 1000);
        int bottom = clamp(centerY + 100 , -1000, 1000);
        //System.out.println("???"+centerX+" "+centerY+" "+left+" "+top);
        RectF rectF = new RectF(left, top, right, bottom);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    private static void handleFocus(MotionEvent event, Camera camera) {
        Camera.Parameters params = camera.getParameters();
        Camera.Size previewSize = params.getPreviewSize();
        Rect focusRect = calculateTapArea(event.getX(), event.getY(), 1f, previewSize);
        System.out.println( ""+focusRect.bottom+" "+focusRect.top);

        camera.cancelAutoFocus();
        Camera.Area cameraArea = new Camera.Area(focusRect, 1000);
        List<Camera.Area> meteringAreas = new ArrayList<>();
        List<Camera.Area> focusAreas = new ArrayList<>();
        /*if (params.getMaxNumMeteringAreas() > 0) {
            meteringAreas.add(cameraArea);
            focusAreas.add(cameraArea);
        }*/
        if (params.getMaxNumFocusAreas() > 0) {
            //meteringAreas.add(cameraArea);
            focusAreas.add(cameraArea);
        }
        final String currentFocusMode = params.getFocusMode();
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置对焦模式
        params.setFocusAreas(focusAreas); // 设置对焦区域
        //params.setMeteringAreas(meteringAreas); // 设置测光区域
        try {
            camera.cancelAutoFocus(); // 每次对焦前，需要先取消对焦
            camera.setParameters(params); // 设置相机参数
            //camera.autoFocus(mAutoFocusCallback); // 开启对焦
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    Camera.Parameters params = camera.getParameters();
                    params.setFocusMode(currentFocusMode);
                    camera.setParameters(params);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 1) {
            handleFocus(event, mCamera);
        }
        return true;
    }


    public void playVideo() {
        try {
            mediaPlayer.reset();//重置
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            //raw文件夹下面的内容
//            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.baishi);
//            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setDataSource(videoFile.getPath());
            mediaPlayer.setDisplay(mSurfaceView.getHolder());
            //mediaPlayer.setDataSource(URL);
            //视频输出到SurfaceView上

            mediaPlayer.prepare();//使用同步方式
            mediaPlayer.start();//开始播放

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(CustomCameraActivity.this, "WTF", Toast.LENGTH_SHORT).show();
        }
    }

    protected void onPause() {
        super.onPause();
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            position = mediaPlayer.getCurrentPosition();

        }
    }

    protected void onDestroy() {
        super.onDestroy();
        //释放资源
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

}
