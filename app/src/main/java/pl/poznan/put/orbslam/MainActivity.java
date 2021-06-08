package pl.poznan.put.orbslam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, Renderer {

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java3");
    }

    @BindView(R.id.progress_view)
    RelativeLayout progress;
    @BindView(R.id.main_activity_java_surface_view)
    CameraBridgeViewBase mOpenCvCameraView;
    @BindView(R.id.image_deald)
    ImageView imageDeald;

    static private String TAG = "MYSLAM";
    private boolean isORBInitialised = false;
    private boolean isORBRunning = true;
    private boolean isImageGrabed = false;
    private Bitmap resultImg;
    private double timestamp;
    long addr;
    int w,h;



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("MainActivity", "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mOpenCvCameraView.setMaxFrameSize(640,480);
        TextView textView = (TextView)findViewById(R.id.sample_text);
        textView.setText(stringFromJNI());

        if (!OpenCVLoader.initDebug()) {
            textView.setText(textView.getText() + "\n OpenCVLoader.initDebug(), not working.");
        } else {
            textView.setText(textView.getText() + "\n OpenCVLoader.initDebug(), WORKING.");
            textView.setText(textView.getText() + "\n" + validate(0L, 0L));
        }
        if(!isORBInitialised) {
            progress.setVisibility(View.VISIBLE);
            new Thread(new Runnable() {
                @Override
                public void run() {

                    if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                1231);
                    }

                    if (checkSelfPermission(Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA},
                                1232);
                    }

                    initSystemWithParameters(Environment.getExternalStorageDirectory().getAbsolutePath() + "/ORBvoc.txt", Environment.getExternalStorageDirectory().getAbsolutePath() + "/TUM1.yaml");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setVisibility(View.GONE);
                            isORBInitialised = true;
                            Toast.makeText(MainActivity.this, "Initialization done!", Toast.LENGTH_LONG).show();
                            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
                            mOpenCvCameraView.setCvCameraViewListener(MainActivity.this);
                            new Thread(new Runnable() {

                                @Override
                                public void run() {
                                    while(isORBRunning){
                                        timestamp = (double)System.currentTimeMillis()/1000.0;
                                        if(isImageGrabed) {
                                            isImageGrabed = false;
                                            int[] resultInt = startCurrentORBForCamera(timestamp, addr, w, h);
                                            if (resultInt.length > 0) {
                                                resultImg = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
                                                resultImg.setPixels(resultInt, 0, w, 0, 0, w, h);
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        // TODO Auto-generated method stub
                                                        imageDeald.setImageBitmap(resultImg);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                            }).start();
                        }
                    });
                }
            }).start();
        } else {
            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCvCameraViewListener(MainActivity.this);
        }

        GLSurfaceView mapView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        mapView.setRenderer(this);
        mapView.setZOrderMediaOverlay(true);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("MainActivity", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d("MainActivity", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        isORBRunning = false;
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat im=inputFrame.rgba();
        synchronized (im) {
            addr=im.getNativeObjAddr();
            isImageGrabed = true;
        }

        w=im.cols();
        h=im.rows();
        return inputFrame.rgba();
    }

    public native String stringFromJNI();

    public native String validate(long matAddrGr, long matAddrRgba);

    public native void initSystemWithParameters(String VOCPath,String calibrationPath);

    public native int[] startCurrentORBForCamera(double curTimeStamp,long addr,int w,int h);

    public native static void glesInit();
    public native static void glesRender();
    public native static void glesResize(int width, int height);

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        glesInit();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        glesResize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        glesRender();
    }
}
