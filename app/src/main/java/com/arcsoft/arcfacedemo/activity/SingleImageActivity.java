package com.arcsoft.arcfacedemo.activity;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.text.ParcelableSpan;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.arcsoft.arcfacedemo.R;
import com.arcsoft.face.AgeInfo;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.Face3DAngle;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.FaceSimilar;
import com.arcsoft.face.GenderInfo;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.VersionInfo;
import com.arcsoft.face.enums.CompareModel;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.DetectModel;
import com.arcsoft.face.model.ArcSoftImageInfo;
import com.arcsoft.face.util.ImageUtils;
import com.arcsoft.imageutil.ArcSoftImageFormat;
import com.arcsoft.imageutil.ArcSoftImageUtil;
import com.arcsoft.imageutil.ArcSoftImageUtilError;
import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class SingleImageActivity extends BaseActivity {
    private static final String TAG = "SingleImageActivity";
    private ImageView ivShow;
    private TextView tvNotice;
    private FaceEngine faceEngine;
    private int faceEngineCode = -1;
    /**
     * ????????????????????????
     */
    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    /**
     * ??????????????????????????????????????????
     */
    private static final int ACTION_CHOOSE_IMAGE = 0x201;
    /**
     * ???????????????
     */
    private AlertDialog progressDialog;
    /**
     * ??????????????????
     */
    private Bitmap mBitmap = null;

    /**
     * ???????????????????????????
     */
    private static String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.READ_PHONE_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_process);
        initView();
        /**
         * ??????????????????????????????android 7.0???????????????FileProvider??????Uri????????????????????????
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            List<String> permissionList = new ArrayList<>(Arrays.asList(NEEDED_PERMISSIONS));
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            NEEDED_PERMISSIONS = permissionList.toArray(new String[0]);
        }

        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            initEngine();
        }

    }

    private void initEngine() {
        faceEngine = new FaceEngine();
        faceEngineCode = faceEngine.init(this, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                16, 10, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_AGE | FaceEngine.ASF_GENDER | FaceEngine.ASF_FACE3DANGLE | FaceEngine.ASF_LIVENESS);
        VersionInfo versionInfo = new VersionInfo();
        faceEngine.getVersion(versionInfo);
        Log.i(TAG, "initEngine: init: " + faceEngineCode + "  version:" + versionInfo);
        if (faceEngineCode != ErrorInfo.MOK) {
            showToast(getString(R.string.init_failed, faceEngineCode));
        }
    }

    /**
     * ????????????
     */
    private void unInitEngine() {
        if (faceEngine != null) {
            faceEngineCode = faceEngine.unInit();
            faceEngine = null;
            Log.i(TAG, "unInitEngine: " + faceEngineCode);
        }
    }

    @Override
    protected void onDestroy() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
        mBitmap = null;

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;

        unInitEngine();
        super.onDestroy();
    }

    private void initView() {
        tvNotice = findViewById(R.id.tv_notice);
        ivShow = findViewById(R.id.iv_show);
        ivShow.setImageResource(R.mipmap.faces);
        progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.processing)
                .setView(new ProgressBar(this))
                .create();
    }

    /**
     * ????????????????????????
     *
     * @param view
     */
    public void process(final View view) {

        view.setClickable(false);
        if (progressDialog == null || progressDialog.isShowing()) {
            return;
        }
        progressDialog.show();
        //??????????????????????????????????????????????????????????????????????????????
        Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                processImage();
                emitter.onComplete();
            }
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Object o) {
                        String add = "";
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        view.setClickable(true);
                    }
                });
    }


    /**
     * ????????????????????????
     */
    public void processImage() {
        /**
         * 1.???????????????????????????????????????BGR???
         */
        if (mBitmap == null) {
            mBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.faces);
        }
        // ????????????
        Bitmap bitmap = ArcSoftImageUtil.getAlignedBitmap(mBitmap, true);

        final SpannableStringBuilder notificationSpannableStringBuilder = new SpannableStringBuilder();
        if (faceEngineCode != ErrorInfo.MOK) {
            addNotificationInfo(notificationSpannableStringBuilder, null, " face engine not initialized!");
            showNotificationAndFinish(notificationSpannableStringBuilder);
            return;
        }
        if (bitmap == null) {
            addNotificationInfo(notificationSpannableStringBuilder, null, " bitmap is null!");
            showNotificationAndFinish(notificationSpannableStringBuilder);
            return;
        }
        if (faceEngine == null) {
            addNotificationInfo(notificationSpannableStringBuilder, null, " faceEngine is null!");
            showNotificationAndFinish(notificationSpannableStringBuilder);
            return;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        final Bitmap finalBitmap = bitmap;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.with(ivShow.getContext())
                        .load(finalBitmap)
                        .into(ivShow);
            }
        });

        // bitmap???bgr24
        long start = System.currentTimeMillis();
        byte[] bgr24 = ArcSoftImageUtil.createImageData(bitmap.getWidth(), bitmap.getHeight(), ArcSoftImageFormat.BGR24);
        int transformCode = ArcSoftImageUtil.bitmapToImageData(bitmap, bgr24, ArcSoftImageFormat.BGR24);
        if (transformCode != ArcSoftImageUtilError.CODE_SUCCESS) {
            Log.e(TAG, "transform failed, code is " + transformCode);
            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "transform bitmap To ImageData failed", "code is ", String.valueOf(transformCode), "\n");
            return;
        }
//        Log.i(TAG, "processImage:bitmapToBgr24 cost =  " + (System.currentTimeMillis() - start));
        addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "start face detection,imageWidth is ", String.valueOf(width), ", imageHeight is ", String.valueOf(height), "\n");

        List<FaceInfo> faceInfoList = new ArrayList<>();

        /**
         * 2.??????????????????BGR24 ???????????????????????????
         */
        long fdStartTime = System.currentTimeMillis();
//        ArcSoftImageInfo arcSoftImageInfo = new ArcSoftImageInfo(width,height,FaceEngine.CP_PAF_BGR24,new byte[][]{bgr24},new int[]{width * 3});
//        Log.i(TAG, "processImage: " + arcSoftImageInfo.getPlanes()[0].length);
//        int detectCode = faceEngine.detectFaces(arcSoftImageInfo, faceInfoList);
        int detectCode = faceEngine.detectFaces(bgr24, width, height, FaceEngine.CP_PAF_BGR24, DetectModel.RGB, faceInfoList);
        if (detectCode == ErrorInfo.MOK) {
//            Log.i(TAG, "processImage: fd costTime = " + (System.currentTimeMillis() - fdStartTime));
        }

        //??????bitmap
        Bitmap bitmapForDraw = bitmap.copy(Bitmap.Config.RGB_565, true);
        Canvas canvas = new Canvas(bitmapForDraw);
        Paint paint = new Paint();
        addNotificationInfo(notificationSpannableStringBuilder, null, "detect result:\nerrorCode is :", String.valueOf(detectCode), "   face Number is ", String.valueOf(faceInfoList.size()), "\n");
        /**
         * 3.?????????????????????????????????0?????????bitmap???????????????????????????????????????ImageView?????????????????????0????????????????????????????????????????????????
         */
        if (faceInfoList.size() > 0) {
            addNotificationInfo(notificationSpannableStringBuilder, null, "face list:\n");
            paint.setAntiAlias(true);
            paint.setStrokeWidth(5);
            paint.setColor(Color.YELLOW);
            for (int i = 0; i < faceInfoList.size(); i++) {
                //???????????????
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(faceInfoList.get(i).getRect(), paint);
                //??????????????????
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                int textSize = faceInfoList.get(i).getRect().width() / 2;
                paint.setTextSize(textSize);

                canvas.drawText(String.valueOf(i), faceInfoList.get(i).getRect().left, faceInfoList.get(i).getRect().top, paint);
                addNotificationInfo(notificationSpannableStringBuilder, null, "face[", String.valueOf(i), "]:", faceInfoList.get(i).toString(), "\n");
            }
            //??????
            final Bitmap finalBitmapForDraw = bitmapForDraw;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Glide.with(ivShow.getContext())
                            .load(finalBitmapForDraw)
                            .into(ivShow);
                }
            });
        } else {
            addNotificationInfo(notificationSpannableStringBuilder, null, "can not do further action, exit!");
            showNotificationAndFinish(notificationSpannableStringBuilder);
            return;
        }
        addNotificationInfo(notificationSpannableStringBuilder, null, "\n");


        /**
         * 4.????????????????????????????????????????????????????????????process????????????????????????????????????????????????????????????
         */

        long processStartTime = System.currentTimeMillis();
        int faceProcessCode = faceEngine.process(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList, FaceEngine.ASF_AGE | FaceEngine.ASF_GENDER | FaceEngine.ASF_FACE3DANGLE | FaceEngine.ASF_LIVENESS);

        if (faceProcessCode != ErrorInfo.MOK) {
            addNotificationInfo(notificationSpannableStringBuilder, new ForegroundColorSpan(Color.RED), "process failed! code is ", String.valueOf(faceProcessCode), "\n");
        } else {
//            Log.i(TAG, "processImage: process costTime = " + (System.currentTimeMillis() - processStartTime));
        }
        //??????????????????
        List<AgeInfo> ageInfoList = new ArrayList<>();
        //??????????????????
        List<GenderInfo> genderInfoList = new ArrayList<>();
        //????????????????????????
        List<Face3DAngle> face3DAngleList = new ArrayList<>();
        //??????????????????
        List<LivenessInfo> livenessInfoList = new ArrayList<>();
        //???????????????????????????????????????????????????
        int ageCode = faceEngine.getAge(ageInfoList);
        int genderCode = faceEngine.getGender(genderInfoList);
        int face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList);
        int livenessCode = faceEngine.getLiveness(livenessInfoList);

        if ((ageCode | genderCode | face3DAngleCode | livenessCode) != ErrorInfo.MOK) {
            addNotificationInfo(notificationSpannableStringBuilder, null, "at least one of age,gender,face3DAngle detect failed!,codes are:",
                    String.valueOf(ageCode), " , ", String.valueOf(genderCode), " , ", String.valueOf(face3DAngleCode));
            showNotificationAndFinish(notificationSpannableStringBuilder);
            return;
        }
        /**
         * 5.??????????????????????????????????????????????????????????????????????????????
         */
        //????????????
        if (ageInfoList.size() > 0) {
            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "age of each face:\n");
        }
        for (int i = 0; i < ageInfoList.size(); i++) {
            addNotificationInfo(notificationSpannableStringBuilder, null, "face[", String.valueOf(i), "]:", String.valueOf(ageInfoList.get(i).getAge()), "\n");
        }
        addNotificationInfo(notificationSpannableStringBuilder, null, "\n");

        //????????????
        if (genderInfoList.size() > 0) {
            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "gender of each face:\n");
        }
        for (int i = 0; i < genderInfoList.size(); i++) {
            addNotificationInfo(notificationSpannableStringBuilder, null, "face[", String.valueOf(i), "]:"
                    , genderInfoList.get(i).getGender() == GenderInfo.MALE ?
                            "MALE" : (genderInfoList.get(i).getGender() == GenderInfo.FEMALE ? "FEMALE" : "UNKNOWN"), "\n");
        }
        addNotificationInfo(notificationSpannableStringBuilder, null, "\n");


        //????????????????????????
        if (face3DAngleList.size() > 0) {
            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "face3DAngle of each face:\n");
            for (int i = 0; i < face3DAngleList.size(); i++) {
                addNotificationInfo(notificationSpannableStringBuilder, null, "face[", String.valueOf(i), "]:", face3DAngleList.get(i).toString(), "\n");
            }
        }
        addNotificationInfo(notificationSpannableStringBuilder, null, "\n");

        //??????????????????
        if (livenessInfoList.size() > 0) {
            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "liveness of each face:\n");
            for (int i = 0; i < livenessInfoList.size(); i++) {
                String liveness = null;
                switch (livenessInfoList.get(i).getLiveness()) {
                    case LivenessInfo.ALIVE:
                        liveness = "ALIVE";
                        break;
                    case LivenessInfo.NOT_ALIVE:
                        liveness = "NOT_ALIVE";
                        break;
                    case LivenessInfo.UNKNOWN:
                        liveness = "UNKNOWN";
                        break;
                    case LivenessInfo.FACE_NUM_MORE_THAN_ONE:
                        liveness = "FACE_NUM_MORE_THAN_ONE";
                        break;
                    default:
                        liveness = "UNKNOWN";
                        break;
                }
                addNotificationInfo(notificationSpannableStringBuilder, null, "face[", String.valueOf(i), "]:", liveness, "\n");
            }
        }
        addNotificationInfo(notificationSpannableStringBuilder, null, "\n");

        /**
         * 6.??????????????????????????????????????????????????????????????????????????????
         */
        if (faceInfoList.size() > 0) {

            FaceFeature[] faceFeatures = new FaceFeature[faceInfoList.size()];
            int[] extractFaceFeatureCodes = new int[faceInfoList.size()];

            addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "faceFeatureExtract:\n");
            for (int i = 0; i < faceInfoList.size(); i++) {
                faceFeatures[i] = new FaceFeature();
                //????????????????????????????????????
                long frStartTime = System.currentTimeMillis();
                extractFaceFeatureCodes[i] = faceEngine.extractFaceFeature(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList.get(i), faceFeatures[i]);

                if (extractFaceFeatureCodes[i] != ErrorInfo.MOK) {
                    addNotificationInfo(notificationSpannableStringBuilder, null, "faceFeature of face[", String.valueOf(i), "]",
                            " extract failed, code is ", String.valueOf(extractFaceFeatureCodes[i]), "\n");
                } else {
//                    Log.i(TAG, "processImage: fr costTime = " + (System.currentTimeMillis() - frStartTime));
                    addNotificationInfo(notificationSpannableStringBuilder, null, "faceFeature of face[", String.valueOf(i), "]",
                            " extract success\n");
                }
            }
            addNotificationInfo(notificationSpannableStringBuilder, null, "\n");

            //???????????????????????????2??????????????????????????????
            if (faceFeatures.length >= 2) {

                addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD), "similar of faces:\n");

                for (int i = 0; i < faceFeatures.length; i++) {
                    for (int j = i + 1; j < faceFeatures.length; j++) {
                        addNotificationInfo(notificationSpannableStringBuilder, new StyleSpan(Typeface.BOLD_ITALIC), "compare face[", String.valueOf(i), "] and  face["
                                , String.valueOf(j), "]:\n");
                        //??????????????????????????????????????????????????????
                        boolean canCompare = true;
                        if (extractFaceFeatureCodes[i] != 0) {
                            addNotificationInfo(notificationSpannableStringBuilder, null, "faceFeature of face[", String.valueOf(i), "] extract failed, can not compare!\n");
                            canCompare = false;
                        }
                        if (extractFaceFeatureCodes[j] != 0) {
                            addNotificationInfo(notificationSpannableStringBuilder, null, "faceFeature of face[", String.valueOf(j), "] extract failed, can not compare!\n");
                            canCompare = false;
                        }
                        if (!canCompare) {
                            continue;
                        }

                        FaceSimilar matching = new FaceSimilar();
                        //?????????????????????????????????????????????
                        faceEngine.compareFaceFeature(faceFeatures[i], faceFeatures[j], CompareModel.LIFE_PHOTO, matching);
                        //?????????????????????????????????
                        addNotificationInfo(notificationSpannableStringBuilder, null, "similar of face[", String.valueOf(i), "] and  face[",
                                String.valueOf(j), "] is:", String.valueOf(matching.getScore()), "\n");
                    }
                }
            }
        }

        showNotificationAndFinish(notificationSpannableStringBuilder);

    }

    /**
     * ???????????????????????????????????????
     *
     * @param stringBuilder ????????????????????????
     */
    private void showNotificationAndFinish(final SpannableStringBuilder stringBuilder) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvNotice != null) {
                    tvNotice.setText(stringBuilder);
                }
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });
    }

    /**
     * ??????????????????
     *
     * @param stringBuilder ?????????????????????????????????
     * @param styleSpan     ???????????????????????????
     * @param strings       ???????????????
     */
    private void addNotificationInfo(SpannableStringBuilder stringBuilder, ParcelableSpan styleSpan, String... strings) {
        if (stringBuilder == null || strings == null || strings.length == 0) {
            return;
        }
        int startLength = stringBuilder.length();
        for (String string : strings) {
            stringBuilder.append(string);
        }
        int endLength = stringBuilder.length();
        if (styleSpan != null) {
            stringBuilder.setSpan(styleSpan, startLength, endLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * ?????????????????????
     *
     * @param view
     */
    public void chooseLocalImage(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, ACTION_CHOOSE_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_CHOOSE_IMAGE) {
            if (data == null || data.getData() == null) {
                showToast(getString(R.string.get_picture_failed));
                return;
            }
            try {
                mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            if (mBitmap == null) {
                showToast(getString(R.string.get_picture_failed));
                return;
            }
            Glide.with(ivShow.getContext())
                    .load(mBitmap)
                    .into(ivShow);
        }
    }

    @Override
    void afterRequestPermission(int requestCode, boolean isAllGranted) {
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            if (isAllGranted) {
                initEngine();
            } else {
                showToast(getString(R.string.permission_denied));
            }
        }
    }
}
