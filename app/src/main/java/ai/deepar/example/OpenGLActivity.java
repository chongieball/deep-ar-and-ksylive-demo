package ai.deepar.example;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.ksyun.media.streamer.capture.CameraCapture;
import com.ksyun.media.streamer.kit.KSYStreamer;
import com.ksyun.media.streamer.kit.StreamerConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ai.deepar.ar.AREventListener;
import ai.deepar.ar.CameraResolutionPreset;
import ai.deepar.ar.DeepAR;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

public class OpenGLActivity extends PermissionsActivity implements GLSurfaceView.Renderer, AREventListener,
KSYStreamer.OnErrorListener, KSYStreamer.OnInfoListener{

    private final String vertexShaderCode =
                    "attribute vec4 vPosition;" +
                    "attribute vec2 vUv;" +
                    "varying vec2 uv; " +

                    "void main() {" +
                    "gl_Position = vPosition;" +
                    "uv = vUv;" +
                    "}";

    private final String fragmentShaderCode =
                    "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 uv; " +
                    "uniform samplerExternalOES sampler;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(sampler, uv); " +
                    "}";

    static float squareCoords[] = {
            -1.0f,  1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
             1.0f,  1.0f, 0.0f };

    static float uv[] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    private final short drawOrder[] = { 0, 1, 2, 0, 2, 3 };

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvbuffer;
    private ShortBuffer drawListBuffer;
    private int program;
    private int texture;
    private SurfaceTexture surfaceTexture;
    private Surface surface;

    private GLSurfaceView surfaceView;


    int surfaceWidth = 512;
    int surfaceHeight = 512;

    private DeepAR deepAR;
    private CameraGrabber cameraGrabber;


    private boolean updateTexImage = false;
    private KSYStreamer ksyStreamer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deepAR = new DeepAR(this);
        deepAR.initialize(this, this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        String cameraPermission = getResources().getString(R.string.camera_permission);
        String externalStoragePermission = getResources().getString(R.string.external_permission);

        checkMultiplePermissions(
                Arrays.asList(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO),
                cameraPermission + " " + externalStoragePermission,
                100,
                new PermissionsActivity.MultiplePermissionsCallback() {
                    @Override
                    public void onAllPermissionsGranted() {
                        setup();
                    }

                    @Override
                    public void onPermissionsDenied(List<String> deniedPermissions) {
                        Log.d("MainActity", "Permissions Denied!");
                    }
                });

    }

    void setup() {
        cameraGrabber = new CameraGrabber();
        cameraGrabber.initCamera(new CameraGrabberListener() {
            @Override
            public void onCameraInitialized() {
                cameraGrabber.setFrameReceiver(deepAR);
                cameraGrabber.startPreview();
            }

            @Override
            public void onCameraError(String errorMsg) {
                Log.e("Error", errorMsg);
            }
        });

        ksyStreamer = new KSYStreamer(this);
        ksyStreamer.setDisplayPreview(surfaceView);

        surfaceView = new GLSurfaceView(this);
        surfaceView.setEGLContextClientVersion(2);
//        surfaceView.setEGLConfigChooser(8,8,8,8,16,0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(surfaceView);

        ksyStreamer.stopStream();
        ksyStreamer.setCameraCaptureResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setPreviewResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setTargetResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setVideoKBitrate(300 * 3 / 4, 300, 300 / 4);
        ksyStreamer.setCameraFacing(CameraCapture.FACING_FRONT);
        ksyStreamer.setOnInfoListener(this);
        ksyStreamer.setOnErrorListener(this);
        ksyStreamer.setFrontCameraMirror(true);
        ksyStreamer.setUrl("rtmp://s02.sugarlive.co.id:35002/live/f9df2c1a-8d56-467c-ba6d-88379f5019e4");
//        ksyStreamer.startCameraPreview();
        ksyStreamer.startStream();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceView != null) {
            surfaceView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (surfaceView != null) {
            surfaceView.onPause();
        }
    }

    @Override
    protected void onStop() {
        cameraGrabber.setFrameReceiver(null);
        cameraGrabber.stopPreview();
        cameraGrabber.releaseCamera();
        cameraGrabber = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deepAR.release();
    }


    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        uvbuffer = bb2.asFloatBuffer();
        uvbuffer.put(uv);
        uvbuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        surfaceWidth = width;
        surfaceHeight = height;

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        texture = textures[0];

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);

        GLES20.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, surfaceWidth, surfaceHeight, 0,GLES20.GL_RGBA, GL10.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(texture);
        surface = new Surface(surfaceTexture);

        deepAR.setRenderSurface(surface, surfaceWidth, surfaceHeight);
        deepAR.setFrameRenderedCallback(new DeepAR.FrameRenderedCallback() {
            @Override
            public void frameRendered() {
                updateTexImage = true;
            }
        });

    }

    int count = 0;
    @Override
    public void onDrawFrame(GL10 gl10) {
        if (updateTexImage) {
            updateTexImage = false;
            synchronized (this) {
                surfaceTexture.updateTexImage();
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(program);
        int positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        int uvHandle = GLES20.glGetAttribLocation(program, "vUv");
        GLES20.glEnableVertexAttribArray(uvHandle);
        GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 8, uvbuffer);

        int sampler = GLES20.glGetUniformLocation(program, "sampler");
        GLES20.glUniform1i(sampler, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }


    public static int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void screenshotTaken(Bitmap bitmap) {

    }

    @Override
    public void videoRecordingStarted() {

    }

    @Override
    public void videoRecordingFinished() {

    }

    @Override
    public void videoRecordingFailed() {

    }

    @Override
    public void videoRecordingPrepared() {

    }

    @Override
    public void shutdownFinished() {

    }

    @Override
    public void initialized() {
        deepAR.switchEffect("mask", "file:///android_asset/aviators");

    }

    @Override
    public void faceVisibilityChanged(boolean b) {

    }

    @Override
    public void imageVisibilityChanged(String gameObjectName, boolean imageVisible) {

    }

    @Override
    public void error(String s) {

    }

    @Override
    public void effectSwitched(String slot) {

    }

    @Override
    public void onError(int i, int i1, int i2) {
        Log.d("ONERROR", "streaming error "+i +" msg1 = "+i1+" msg2 = "+i2);
    }

    @Override
    public void onInfo(int i, int i1, int i2) {
        Log.d("ONINFO", "streaming error "+i +" msg1 = "+i1+" msg2 = "+i2);
    }
}
