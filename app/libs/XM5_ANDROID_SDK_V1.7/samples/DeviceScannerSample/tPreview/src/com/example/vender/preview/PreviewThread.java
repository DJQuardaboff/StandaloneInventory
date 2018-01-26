package com.example.vender.preview;

import java.nio.ByteBuffer;
import java.util.Random;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.SurfaceHolder;

// import java.lang.Object;

public class PreviewThread implements Runnable {
	private static final String TAG = "PreviewThread";
	private static final boolean DEBUG = false;
	boolean onStart, onPause;
	private int canvasWidth, canvasHeight;
	private Paint mBackPaint;
	private Paint mTextPaint;
	private RectF mBackRect;
	private Bitmap mPreviewImage;
	private SurfaceHolder mSurfaceHolder;
	private Random mRandom;

	Thread mThread;

	public PreviewThread(SurfaceHolder surfaceHolder) {
		Log.e(TAG, "PreviewThread(SurfaceHolder surfaceHolder)");
		mSurfaceHolder = surfaceHolder;
		mBackPaint = new Paint();
		mBackPaint.setAntiAlias(true);
		mBackPaint.setARGB(255, 255, 255, 255);
		mTextPaint = new Paint();
		mTextPaint.setAntiAlias(true);
		mTextPaint.setARGB(255, 0, 0, 0);
		mTextPaint.setTextSize(30);
		mBackRect = new RectF(0, 0, 0, 0);
		mRandom = new Random();
	}

	public void setSurfaceSize(int width, int height) {
		synchronized (mSurfaceHolder) {
			canvasWidth = width;
			canvasHeight = height;
			mBackRect.set(0, 0, canvasWidth, canvasHeight);
			/*
			 * if (mPreviewImage != null) { mPreviewImage = Bitmap.createScaledBitmap(mPreviewImage,
			 * canvasWidth, canvasHeight, true); }
			 */
		}
	}

	private static int getA32(int c) {
		return (c & 0xFF000000) << 24;
	}

	private static int getR32(int c) {
		return (c >> 0) & 0xFF;
		// return (c & 0x00FF0000) << 16;
	}

	private static int getG32(int c) {
		return (c >> 8) & 0xFF;
		// return (c & 0x0000FF00) << 8;
	}

	private static int getB32(int c) {
		return (c >> 16) & 0xFF;
		// return (c & 0x000000FF) << 0;
	}


	private static ByteBuffer makeBuffer(byte[] src) {
		byte[] bits = new byte[src.length * 4];
		for (int i = 0; i < src.length; i++) {
			// bits[i * 4] = (byte) getB32(src[i]);
			// bits[i * 4 + 1] = (byte) getG32(src[i]);
			// bits[i * 4 + 2] = (byte) getR32(src[i]);
			// bits[i * 4 + 3] = (byte) 255;
			bits[i * 4] = (byte) (src[i]);
			bits[i * 4 + 1] = (byte) (src[i]);
			bits[i * 4 + 2] = (byte) (src[i]);
			bits[i * 4 + 3] = (byte) 0xff;
		}
		return ByteBuffer.wrap(bits);
	}

	public void setPreview(byte[] data, int width, int height) {
		synchronized (mSurfaceHolder) {
			if (mPreviewImage == null) {
				mPreviewImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			}
			// Log.e(TAG, "[PreviewThread.java]setPreview()");
			mPreviewImage.copyPixelsFromBuffer(makeBuffer(data));
		}
	}

	public void start() {
		// Log.e(TAG, "[PreviewThread.java]start()");
		onStart = true;
		mThread = new Thread(this);
		mThread.start();
	}

	public void stop() {
		// Log.e(TAG, "[PreviewThread.java]stop()");
		onStart = false;
		resume();
		if (mPreviewImage != null) {
			mPreviewImage.recycle();
			mPreviewImage = null;
		}
	}

	public void pause() {
		// Log.e(TAG, "[PreviewThread.java]pause()");
		onPause = true;
	}

	public void resume() {
		// Log.e(TAG, "[PreviewThread.java]resume()");
		onPause = false;
		try {
			synchronized (this) {
				notifyAll();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (onStart) {
			Canvas c = null;
			try {
				c = mSurfaceHolder.lockCanvas(null);
				doDraw(c);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (c != null)
					mSurfaceHolder.unlockCanvasAndPost(c);

				if (!onStart)
					break;

				if (onPause) {
					try {
						synchronized (this) {
							wait();
						}
					} catch (Exception e2) {
						e2.printStackTrace();
					}
				}

				if (!onStart)
					break;
			}
		}
	}

	public void doDraw(Canvas canvas) {
		synchronized (mSurfaceHolder) {
			if (canvas != null) {
				if (DEBUG) {
					mBackPaint.setARGB(255, mRandom.nextInt(256), mRandom.nextInt(256), mRandom.nextInt(256));
					canvas.drawRect(mBackRect, mBackPaint);
				}
				if (mPreviewImage != null) {
					canvas.drawBitmap(mPreviewImage, canvasWidth / 2 - mPreviewImage.getWidth() / 2,
							canvasHeight / 2 - mPreviewImage.getHeight() / 2, null);
				}
			}
		}
	}
}
