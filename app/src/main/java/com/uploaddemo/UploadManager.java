package com.uploaddemo;

import android.app.Activity;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadManager {
    private static final MediaType PNG_TYPE = MediaType.parse("image/png");
    private String mUploadUrl = "";
    private static final String DEFAULT_ERROR_MSG = "图片上传失败，请重新上传";
    private Activity mActivity;

    /**
     * 图片本次保存路径
     */
    private String mFilePath;

    private File mFile;
    /**
     * 上传完成 进度100
     */
    private static final int UPLOAD_COMPLETE = 100;
    /**
     * 错误code - 参数非法
     */
    public static final int ERROR_ILLEGAL_PARAMETERS = -80000;
    /**
     * 错误code - 上传过程中发生异常
     */
    public static final int ERROR_OCCURS_EXCEPTION = -80001;
    /**
     * 错误code - 服务器异常
     */
    public static final int ERROR_SERVER = -80002;

    /**
     * 上传完成回调接口
     */
    private OnUploadCompleteListener mUploadCompleteListener;

    private OnProgressListener mProgressListener;

    public UploadManager(Activity activity) {
        mActivity = activity;
    }

    public void setLoadListener(OnProgressListener loadListener) {
        mProgressListener = loadListener;
    }

    public void setUploadListener(OnUploadCompleteListener listener) {
        mUploadCompleteListener = listener;
    }

    public void uploadPicture(final String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        mFilePath = filePath;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    upload();
                } catch (Throwable t) {
                    t.printStackTrace();
                    postFailCallback(ERROR_OCCURS_EXCEPTION, DEFAULT_ERROR_MSG);
                }
            }
        });
    }

    private void upload() {
        File imageFile = new File(mFilePath);
        if (!imageFile.exists() || !imageFile.isFile()) {
            postFailCallback(ERROR_ILLEGAL_PARAMETERS, DEFAULT_ERROR_MSG);
            return;
        }
        mFile = imageFile;

        Request request = new Request.Builder().url(mUploadUrl)
                .post(buildRequestBody())
                .build();
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS);
        OkHttpClient okHttpClient = builder.build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postFailCallback(ERROR_SERVER, "");
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    int code;
                    String message;
                    String url = "";
                    try {
                        JSONObject jsonObject = JSON.parseObject(response.body().toString());
                        code = jsonObject.getIntValue("code");
                        message = jsonObject.getString("message");
                        if (jsonObject.containsKey("result")) {
                            url = jsonObject.getString("result");
                        }
                    } catch (Exception e) {
                        code = ERROR_SERVER;
                        message = DEFAULT_ERROR_MSG;
                    }

                    if (!TextUtils.isEmpty(url)) {
                        postSuccessCallback(url);
                    } else {
                        postFailCallback(code, message);
                    }
                } else {
                    postFailCallback(response.code(), DEFAULT_ERROR_MSG);
                }
            }
        });
    }

    private RequestBody buildRequestBody() {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                //提交的是表单,一定要设置表单类型
                .setType(MultipartBody.FORM)
                .addFormDataPart("files", mFile.getName(), RequestBody.create(PNG_TYPE, mFile));
        if (null == mProgressListener) {
            return builder.build();
        }
        return new UploadRequestBody(builder.build(),
                new UploadRequestBody.ProgressRequestListener() {
                    private static final int INTERVAL = 5;

                    int progress = 0;
                    int lastProgress = 0;

                    @Override
                    public void onRequestProgress(long bytesWritten, long contentLength,
                            boolean done) {
                        if (null == mProgressListener) {
                            return;
                        }

                        progress = (int) (bytesWritten * 100 / contentLength);

                        if (progress >= UPLOAD_COMPLETE) {
                            postProgress2UIThread(bytesWritten, contentLength, progress, done);
                            return;
                        }

                        if (progress - lastProgress < INTERVAL) {
                            return;
                        }

                        lastProgress = progress;

                        postProgress2UIThread(bytesWritten, contentLength, progress, done);
                    }

                    @Override
                    public void onFail() {
                        postFailCallback(ERROR_OCCURS_EXCEPTION, DEFAULT_ERROR_MSG);
                    }
                });
    }

    private void postProgress2UIThread(final long bytesWritten, final long contentLength,
            final int progress, final boolean done) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null != mProgressListener) {
                    mProgressListener.loading(bytesWritten, contentLength, done,
                            progress == 100 ? 99 : progress);
                }
            }
        });
    }

    private void postFailCallback(final int code, final String msg) {
        if (null == mUploadCompleteListener) {
            return;
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null == mUploadCompleteListener) {
                    return;
                }
                mUploadCompleteListener.onUploadFailure(code, msg);
            }
        });
    }

    private void postSuccessCallback(final String photoUrl) {
        if (null == mUploadCompleteListener) {
            return;
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null == mUploadCompleteListener) {
                    return;
                }
                mUploadCompleteListener.onUploadSuccess(photoUrl);
            }
        });
    }

    public interface OnUploadCompleteListener {
        void onUploadSuccess(String fileUrl);

        void onUploadFailure(int code, String errorDescription);
    }

    public interface OnProgressListener {
        void loading(long current, long total, boolean done, int percent);
    }
}
