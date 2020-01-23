package ai.deepar.example;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Toast;

import com.ksyun.media.streamer.capture.CameraCapture;
import com.ksyun.media.streamer.kit.KSYStreamer;
import com.ksyun.media.streamer.kit.StreamerConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import ai.deepar.ar.AREventListener;
import ai.deepar.ar.CameraOrientation;
import ai.deepar.ar.CameraResolutionPreset;
import ai.deepar.ar.DeepAR;

public class MainActivity extends PermissionsActivity implements AREventListener, SurfaceHolder.Callback,
        KSYStreamer.OnInfoListener, KSYStreamer.OnErrorListener {

    private final String TAG = MainActivity.class.getSimpleName();

    private SurfaceView arView;
    private CameraGrabber cameraGrabber;
    private ImageButton screenshotBtn;
    private ImageButton switchCamera;
    private ImageButton nextMask;
    private ImageButton previousMask;
    private ImageButton startLive;

    private RadioButton radioMasks;
    private RadioButton radioEffects;
    private RadioButton radioFilters;

    private final static String SLOT_MASKS = "masks";
    private final static String SLOT_EFFECTS = "effects";
    private final static String SLOT_FILTER = "filters";


    private String currentSlot = SLOT_MASKS;

    private int currentMask=0;
    private int currentEffect=0;
    private int currentFilter=0;

    private int screenOrientation;

    ArrayList<AREffect> masks;
    ArrayList<AREffect> effects;
    ArrayList<AREffect> filters;

    private DeepAR deepAR;
    // Default camera device value, change to Camera.CameraInfo.CAMERA_FACING_BACK to initialize with back camera
    private int defaultCameraDevice = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int cameraDevice = defaultCameraDevice;

    private KSYStreamer ksyStreamer;

    /*
        get interface orientation from
        https://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a/10383164
     */
    private int getScreenOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "portrait.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "landscape.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screenOrientation = getScreenOrientation();
        if(savedInstanceState!=null) {
            cameraDevice = savedInstanceState.getInt("camera", defaultCameraDevice);
        }
        deepAR = new DeepAR(this);
        deepAR.setAntialiasingLevel(4);

        deepAR.setLicenseKey("d8210637a2248b378e44113724a98cfe8c289a87a6d6a7006868476e57a64171b8d0e34ffa892a7a");

        deepAR.initialize(this, this);

        ksyStreamer = new KSYStreamer(this);
        ksyStreamer.setDisplayPreview((GLSurfaceView) arView);
        ksyStreamer.setCameraCaptureResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setPreviewResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setTargetResolution(StreamerConstants.VIDEO_RESOLUTION_540P);
        ksyStreamer.setVideoKBitrate(300 * 3 / 4, 300, 300 / 4);
        ksyStreamer.setCameraFacing(CameraCapture.FACING_FRONT);
        ksyStreamer.setOnInfoListener(this);
        ksyStreamer.setOnErrorListener(this);
        ksyStreamer.setFrontCameraMirror(true);
        ksyStreamer.setUrl("rtmp://devlive.sugarlive.co.id:30157/live/f9df2c1a-8d56-467c-ba6d-88379f5019e4");
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
                setContentView(R.layout.activity_main);

                setupDeepAR();
//                ksyStreamer.startCameraPreview();

            }

            @Override
            public void onPermissionsDenied(List<String> deniedPermissions) {
                Log.d("MainActity", "Permissions Denied!");
            }
        });
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
    protected void onPause() {
        if (recording) {
            deepAR.pauseVideoRecording();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deepAR.setAREventListener(null);
        deepAR.release();
        deepAR = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraGrabber != null) {
            outState.putInt("camera", cameraGrabber.getCurrCameraDevice());
        }
    }

    private void setupEffects() {

        masks = new ArrayList<>();
        masks.add(new AREffect("none", AREffect.EffectTypeMask));
        masks.add(new AREffect("aviators", AREffect.EffectTypeMask));
        masks.add(new AREffect("bigmouth", AREffect.EffectTypeMask));
        masks.add(new AREffect("dalmatian", AREffect.EffectTypeMask));
        masks.add(new AREffect("flowers", AREffect.EffectTypeMask));
        masks.add(new AREffect("koala", AREffect.EffectTypeMask));
        masks.add(new AREffect("lion", AREffect.EffectTypeMask));
        masks.add(new AREffect("smallface", AREffect.EffectTypeMask));
        masks.add(new AREffect("teddycigar", AREffect.EffectTypeMask));
        masks.add(new AREffect("kanye", AREffect.EffectTypeMask));
        masks.add(new AREffect("tripleface", AREffect.EffectTypeMask));
        masks.add(new AREffect("sleepingmask", AREffect.EffectTypeMask));
        masks.add(new AREffect("fatify", AREffect.EffectTypeMask));
        masks.add(new AREffect("obama", AREffect.EffectTypeMask));
        masks.add(new AREffect("mudmask", AREffect.EffectTypeMask));
        masks.add(new AREffect("pug", AREffect.EffectTypeMask));
        masks.add(new AREffect("slash", AREffect.EffectTypeMask));
        masks.add(new AREffect("twistedface", AREffect.EffectTypeMask));
        masks.add(new AREffect("grumpycat", AREffect.EffectTypeMask));

        effects = new ArrayList<>();
        effects.add(new AREffect("none", AREffect.EffectTypeAction));
        effects.add(new AREffect("fire", AREffect.EffectTypeAction));
        effects.add(new AREffect("rain", AREffect.EffectTypeAction));
        effects.add(new AREffect("heart", AREffect.EffectTypeAction));
        effects.add(new AREffect("blizzard", AREffect.EffectTypeAction));

        filters = new ArrayList<>();
        filters.add(new AREffect("none", AREffect.EffectTypeFilter));
        filters.add(new AREffect("filmcolorperfection", AREffect.EffectTypeFilter));
        filters.add(new AREffect("tv80", AREffect.EffectTypeFilter));
        filters.add(new AREffect("drawingmanga", AREffect.EffectTypeFilter));
        filters.add(new AREffect("sepia", AREffect.EffectTypeFilter));
        filters.add(new AREffect("bleachbypass", AREffect.EffectTypeFilter));
        filters.add(new AREffect("realvhs", AREffect.EffectTypeFilter));
    }

    private void radioButtonClicked() {
        if (radioMasks.isChecked()) {
            currentSlot = SLOT_MASKS;
        } else if (radioEffects.isChecked()) {
            currentSlot = SLOT_EFFECTS;
        } else if (radioFilters.isChecked()) {
            currentSlot = SLOT_FILTER;
        }
    }

    private ArrayList<AREffect> getActiveList() {
        if (currentSlot.equals(SLOT_MASKS)) {
            return masks;
        } else if (currentSlot.equals(SLOT_EFFECTS)) {
            return effects;
        } else {
            return filters;
        }
    }

    private int getActiveIndex() {
        if (currentSlot.equals(SLOT_MASKS)) {
            return currentMask;
        } else if (currentSlot.equals(SLOT_EFFECTS)) {
            return currentEffect;
        } else {
            return currentFilter;
        }
    }

    private void setActiveIndex(int index) {
        if (currentSlot.equals(SLOT_MASKS)) {
            currentMask = index;
        } else if (currentSlot.equals(SLOT_EFFECTS)) {
            currentEffect = index;
        } else {
            currentFilter = index;
        }
    }


    private void gotoNext() {
        ArrayList<AREffect> activeList = getActiveList();
        int index = getActiveIndex();
        index = index+1;

        // Example of applying masks to multiple faces
        if (index == activeList.size() && currentSlot.equals(SLOT_MASKS)) {
            setTwoFaceMask();
            return;
        }

        if (index >= activeList.size()) {
            index = 0;
        }
        setActiveIndex(index);
        deepAR.switchEffect(currentSlot, activeList.get(index).getPath());
        deepAR.switchEffect("face2", null, 1);

        findViewById(R.id.twoFacesLabel).setVisibility(View.GONE);
    }

    private void gotoPrevious() {
        ArrayList<AREffect> activeList = getActiveList();
        int index = getActiveIndex();
        index = index-1;

        if (index < 0 && currentSlot.equals(SLOT_MASKS)) {
            setTwoFaceMask();
            return;
        }

        if (index >= activeList.size()) {
            index = 0;
        }

        if (index < 0) {
            index = activeList.size()-1;
        }

        setActiveIndex(index);
        deepAR.switchEffect(currentSlot, activeList.get(index).getPath());
        deepAR.switchEffect("face2", null, 1);
    }

    private void setTwoFaceMask() {
        ArrayList<AREffect> activeList = getActiveList();
        // Set two separate amsks at different slots and different face index
        deepAR.switchEffect(currentSlot, activeList.get(1).getPath(), 0);
        deepAR.switchEffect("face2", activeList.get(3).getPath(), 1);

        // Example app internal logic, not related to the SDK
        currentMask = activeList.size();
        findViewById(R.id.twoFacesLabel).setVisibility(View.VISIBLE);
    }


    boolean recording = false;

    private void setupDeepAR() {
        cameraGrabber = new CameraGrabber(cameraDevice);

        switch (screenOrientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                cameraGrabber.setScreenOrientation(90);
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                cameraGrabber.setScreenOrientation(270);
                break;
            default:
                cameraGrabber.setScreenOrientation(0);
                break;
        }

        cameraGrabber.setResolutionPreset(CameraResolutionPreset.P640x480);

        final Activity context = this;
        cameraGrabber.initCamera(new CameraGrabberListener() {
            @Override
            public void onCameraInitialized() {
                cameraGrabber.setFrameReceiver(deepAR);
                cameraGrabber.startPreview();
                if (recording) {
                    deepAR.resumeVideoRecording();
                }

            }

            @Override
            public void onCameraError(String errorMsg) {
                Log.e(TAG, errorMsg);

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Camera error");
                builder.setMessage(errorMsg);
                builder.setCancelable(true);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });


        setupViews();
    }

    private void setupViews() {

        previousMask = findViewById(R.id.previousMask);
        nextMask = findViewById(R.id.nextMask);

        radioMasks = findViewById(R.id.masks);
        radioEffects = findViewById(R.id.effects);
        radioFilters = findViewById(R.id.filters);

        arView = findViewById(R.id.surface);
        startLive = findViewById(R.id.startLive);

        startLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ksyStreamer.startStream();
            }
        });

        arView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deepAR.onClick();
            }
        });

        arView.getHolder().addCallback(this);

        // Surface might already be initialized, so we force the call to onSurfaceChanged
        arView.setVisibility(View.GONE);
        arView.setVisibility(View.VISIBLE);

        setupEffects();

        screenshotBtn = (ImageButton)findViewById(R.id.recordButton);
        screenshotBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deepAR.takeScreenshot();
/*
                // Video recording example
                if (recording) {
                    deepAR.stopVideoRecording();
                    recording = false;
                } else {

                    Rect subframe = new Rect();
                    subframe.left = 0;
                    subframe.right = deepAR.getRenderWidth();
                    subframe.top = 0;
                    subframe.bottom = deepAR.getRenderHeight();

                    deepAR.startVideoRecording(Environment.getExternalStorageDirectory().toString() + File.separator + "video.mp4", 300, 0);
                    recording = true;
                }
                */
            }
        });

        switchCamera = (ImageButton) findViewById(R.id.switchCamera);
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraDevice = cameraGrabber.getCurrCameraDevice() ==  Camera.CameraInfo.CAMERA_FACING_FRONT ?  Camera.CameraInfo.CAMERA_FACING_BACK :  Camera.CameraInfo.CAMERA_FACING_FRONT;
                cameraGrabber.changeCameraDevice(cameraDevice);
            }
        });

        previousMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoPrevious();
            }
        });

        nextMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoNext();
            }
        });

        radioMasks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioEffects.setChecked(false);
                radioFilters.setChecked(false);
                radioButtonClicked();
            }
        });
        radioEffects.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioMasks.setChecked(false);
                radioFilters.setChecked(false);
                radioButtonClicked();
            }
        });
        radioFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioEffects.setChecked(false);
                radioMasks.setChecked(false);
                radioButtonClicked();
            }
        });
    }


    @Override
    public void screenshotTaken(final Bitmap screenshot) {
        CharSequence now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", new Date());
        try {
            File imageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/DeepAR_" + now + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            screenshot.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{imageFile.toString()}, null, null);
            Toast.makeText(MainActivity.this, getResources().getString(R.string.screenshot_saved), Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            e.printStackTrace();
        }
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
    public void initialized() {

    }

    @Override
    public void faceVisibilityChanged(boolean faceVisible) {

    }

    @Override
    public void imageVisibilityChanged(String gameObjectName, boolean imageVisible) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        deepAR.setRenderSurface(surfaceHolder.getSurface(), width, height);
        Log.d(TAG, "surfaceChanged: " + width + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (deepAR != null) {
            deepAR.setRenderSurface(null, 0, 0);
        }
    }

    @Override
    public void videoRecordingPrepared() {}

    @Override
    public void shutdownFinished() {

    }

    @Override
    public void error(String error) {
        if (error.equals(DeepAR.ERROR_EFFECT_FILE_LOAD_FAILED)) {

        } else if (error.equals(DeepAR.ERROR_MODEL_FILE_NOT_FOUND)) {

        }
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
