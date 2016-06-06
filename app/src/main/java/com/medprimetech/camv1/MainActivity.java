package com.medprimetech.camv1;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;

import android.util.Log;
import android.util.LruCache;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class MainActivity extends Activity {


    private static final int ACTIVITY_START_CAMERA_APP = 0;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE__WAIT_LOCK = 1;

    private int mState = STATE_PREVIEW;
    private ImageView mPhotoCapturedImageView;
    private String mImageFileLocation = "";
    private String GALLERY_LOCATION = "image gallery";
    private File mGalleryFolder;
    private static LruCache<String, Bitmap> mMemoryCache;

    private Size mPreviewSize;
    private String mCameraId;
    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    setupCamera(width, height);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback
            = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreviewSession();
            // Toast.makeText(getApplicationContext(), "Camera Opened!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    // Do nothing
                    break;
                case STATE__WAIT_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                        /*
                        unLockFocus();
                        Toast.makeText(getApplicationContext(), "Focus Lock Successful", Toast.LENGTH_SHORT).show();
                        */
                        captureStillImage();
                    }
                    break;
            }
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            process(result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);

            Toast.makeText(getApplicationContext(), "Focus Lock Unsuccessful", Toast.LENGTH_SHORT).show();
        }
    };
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static File mImageFile;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
                }
            };

    private static class ImageSaver implements Runnable {

        private final Image mImage;

        private ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;

            try {
                fileOutputStream = new FileOutputStream(mImageFile);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        createImageGallery();


        final int maxMemorySize = (int) Runtime.getRuntime().maxMemory() / 1024;
        final int cacheSize = maxMemorySize / 10;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };

        mTextureView = (TextureView) findViewById(R.id.textureView);
    }

    @Override
    public void onResume() {
        super.onResume();

        openBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {

        closeCamera();
        closeBackgoundThread();

        super.onPause();
    }


    public void takePhoto(View view) {
        /*
        Intent callCameraApplicationIntent = new Intent();
        callCameraApplicationIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
           photoFile = createImageFile();

        } catch (IOException e) {
            e.printStackTrace();
        }
        callCameraApplicationIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
        startActivityForResult(callCameraApplicationIntent, ACTIVITY_START_CAMERA_APP);
        */
        try {
            mImageFile = createImageFile();

        } catch (IOException e) {
            e.printStackTrace();
        }
        lockFocus();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) {
            // Toast.makeText(this, "Picture taken successfully", Toast.LENGTH_SHORT).show();
            // Bundle extras = data.getExtras();
            // Bitmap photoCapturedBitmap = (Bitmap) extras.get("data");
            // mPhotoCapturedImageView.setImageBitmap(photoCapturedBitmap);
            // Bitmap photoCapturedBitmap = BitmapFactory.decodeFile(mImageFileLocation);
            // mPhotoCapturedImageView.setImageBitmap(photoCapturedBitmap);
            // setReducedImageSize();


        }
    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(storageDirectory, GALLERY_LOCATION);
        if (!mGalleryFolder.exists()) {
            mGalleryFolder.mkdirs();
        }

    }

    File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";

        File image = File.createTempFile(imageFileName, ".jpg", mGalleryFolder);
        mImageFileLocation = image.getAbsolutePath();

        return image;

    }


    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size largestImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                        rhs.getWidth() * rhs.getHeight());
                            }
                        }
                );
                mImageReader = ImageReader.newInstance(largestImageSize.getWidth(),
                        largestImageSize.getHeight(),
                        ImageFormat.JPEG,
                        1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,
                        mBackgroundHandler);

                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() > width &&
                        option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() > height &&
                        option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {

        if(mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void createCameraPreviewSession () {
        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if(mCameraDevice == null) {
                                return;
                            }
                            try {
                                mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                                mCameraCaptureSession = session;
                                mCameraCaptureSession.setRepeatingRequest(
                                        mPreviewCaptureRequest,
                                        mSessionCaptureCallback,
                                        mBackgroundHandler
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "create camera session failed!",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void closeBackgoundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void lockFocus() {
        try {
            mState = STATE__WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unLockFocus() {
        try {
            mState = STATE_PREVIEW;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillImage() {
        try {
            CaptureRequest.Builder captureStillBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.addTarget(mImageReader.getSurface());



            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            /*
                            Toast.makeText(getApplicationContext(),
                                    "Image Captured!", Toast.LENGTH_SHORT).show();
                            */
                            unLockFocus();
                            Log.d("Rename Activity", "Start of intent calling ");
                            nextIntent();
                            Log.d("Rename Activity", "end of intent calling ");


                        }
                    };

            mCameraCaptureSession.capture(
                    captureStillBuilder.build(), captureCallback, null
            );

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void nextIntent(){

        String[] transferData=new String[2];
        Log.d("Rename Activity", "in nextintent ");
        transferData[0]=mImageFileLocation;
        Log.d("Rename Activity", "in nextintent1 ");
        transferData[1] = mGalleryFolder.toString();

        Intent renameFileActivity = new Intent(this,RenameActivity.class);

        renameFileActivity.putExtra("File_Address",transferData);

        startActivity(renameFileActivity);

    }
}
