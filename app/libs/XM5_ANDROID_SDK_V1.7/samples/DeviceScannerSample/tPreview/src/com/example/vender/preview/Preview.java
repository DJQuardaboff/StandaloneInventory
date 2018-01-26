package com.example.vender.preview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = "CustomSurfaceView";

	private PreviewThread mPreview;

	public Preview(Context context, AttributeSet attrs) {
		super(context, attrs);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		mPreview = new PreviewThread(holder);
		setFocusable(true);
		// Log.e(TAG, "[Preview.java]Preview()");
	}

	public void stopPreview() {
		// Log.e(TAG, "[Preview.java]stopPreview()");
		// mPreview.SaveCapture();
	}

	public void setPreview(byte[] data, int width, int height) {
		// Log.e(TAG, "[Preview.java]setPreview()");
		mPreview.setPreview(data, width, height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// Log.e(TAG, "[Preview.java]surfaceCreated()");
		mPreview.start();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// Log.e(TAG, "[Preview.java]surfaceChanged()");
		mPreview.setSurfaceSize(width, height);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// Log.e(TAG, "[Preview.java]surfaceDestroyed()");
		mPreview.stop();
	}

}
