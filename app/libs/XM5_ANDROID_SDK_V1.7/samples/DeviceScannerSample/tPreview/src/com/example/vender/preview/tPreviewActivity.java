package com.example.vender.preview;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import device.scanner.IImageCallback;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;
import device.service.DevInfoIndex;
import device.service.IDeviceService;

public class tPreviewActivity extends Activity implements OnClickListener {
	public static final String TAG = "tPreviewActivity";
	private static IScannerService iScanner = null;
	private static IDeviceService iDeviceService = null;
	private static boolean mButtonLock = false;
	private static Preview mPreview = null;
	private static Button mButton = null;
	private static CheckBox mAimerCheck = null;
	private static CheckBox mIllumCheck = null;
	private static StreamReadTask ReadTask = null;
	private static int scannerBeforeStatus = 0;
	private static final int PREVIEW_SOURCE_WIDTH_N560x = 416;
	private static final int PREVIEW_SOURCE_HEIGHT_N560x = 320;

	IImageCallback mCallback = new IImageCallback.Stub() {
		@Override
		public void onImageTaken(byte[] data) throws RemoteException {
			mPreview.setPreview(data, 200, 200);
		}
	};

	private BroadcastReceiver mScanKeyEventReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
				KeyEvent event = (KeyEvent) intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);
				switch (event.getKeyCode()) {
					case ScanConst.KEYCODE_SCAN_FRONT:
					case ScanConst.KEYCODE_SCAN_LEFT:
					case ScanConst.KEYCODE_SCAN_RIGHT:
					case ScanConst.KEYCODE_SCAN_REAR:
						if (iScanner != null) {
							if (event.getAction() == KeyEvent.ACTION_DOWN) {
								PreviewStart();
							} else if (event.getAction() == KeyEvent.ACTION_UP) {
								PreviewStop();
								Capture();
							}
						}
						break;
				}
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings:
				openSettings();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void openSettings() {
		Intent exposureIntent = new Intent(getBaseContext(), tExposureSettingActivity.class);
		startActivity(exposureIntent);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.preview_activity);

		mPreview = (Preview) findViewById(R.id.preview);
		mButton = (Button) findViewById(R.id.PreviewCaptureButton);
		mAimerCheck = (CheckBox) findViewById(R.id.CheckBoxAimer);
		mIllumCheck = (CheckBox) findViewById(R.id.CheckBoxIllum);
		mButton.setOnClickListener(this);

		iScanner = IScannerService.Stub.asInterface(ServiceManager.getService(ScanConst.TAG));
		iDeviceService = IDeviceService.Stub.asInterface(ServiceManager.getService("DeviceService"));
		if (iScanner != null && iDeviceService != null) {
			int scannerType = 0;
			try {
				iScanner.aDecodeAPIInit();
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}

				iScanner.aRegisterDecodeImageCallback(mCallback);

				scannerType = iDeviceService.getScannerType();
				switch (scannerType) {
					case DevInfoIndex.SCANNER_N4313:
						Toast.makeText(getApplicationContext(),
						"This device does not support 2D decode function.", Toast.LENGTH_LONG).show();
						return;
					case DevInfoIndex.SCANNER_N5600:
					case DevInfoIndex.SCANNER_N5603:
						break;
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (iScanner != null) {
			try {
				iScanner.aDecodeSetScanImageMode(ScannerService.ScanMode.DCD_MODE_SCAN);
				iScanner.aUnregisterDecodeImageCallback(mCallback);
				iScanner = null;
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (iScanner != null) {
			if (mButtonLock) {
				PreviewStop();
				Capture();
			} else {
				PreviewStart();
			}
		}
	}

	static void PreviewStart() {
		if (iScanner != null) {
			try {
				setScanLightsMode(mAimerCheck.isChecked(), mIllumCheck.isChecked());
				iScanner.aDecodeImageStreamInit();
				iScanner.aDecodeImageStreamStart();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			ReadTask = new StreamReadTask();
			ReadTask.execute();
			mIllumCheck.setEnabled(false);
			mAimerCheck.setEnabled(false);
			mButton.setText("Stop / Capture");
			mButtonLock = true;
		}
	}

	static void PreviewStop() {
		if (ReadTask != null) {
			ReadTask.stop();
		}
		if (iScanner != null) {
			try {
				iScanner.aDecodeImageStreamStop();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		mIllumCheck.setEnabled(true);
		mAimerCheck.setEnabled(true);
		mButton.setText("Preview");
		mButtonLock = false;
	}

	static void Capture() {
		byte[] lowCaptureData = new byte[532500];

		try {
			iScanner.aDecodeImageCapture(lowCaptureData);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	static void setScanLightsMode(boolean aimOn, boolean illumOn) {
		int mode =	((aimOn == false) && (illumOn == false)) ? ScannerService.LightMode.DCD_LIGHT_MODE_OFF
				: ((aimOn == true) && (illumOn == false)) ? ScannerService.LightMode.DCD_LIGHT_MODE_AIM_ON
				: ((aimOn == false) && (illumOn == true)) ? ScannerService.LightMode.DCD_LIGHT_MODE_ILLUM_ON
				: ScannerService.LightMode.DCD_LIGHT_MODE_ON;

		if (iScanner != null) {
			try {
				iScanner.aDecodeImageSetLightMode(mode);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	void getScanLightsMode() {
		int lightMode = ScannerService.LightMode.DCD_LIGHT_MODE_OFF;
		if (iScanner != null) {
			try {
				lightMode = iScanner.aDecodeImageGetLightMode();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		switch (lightMode) {
			case ScannerService.LightMode.DCD_LIGHT_MODE_OFF:
				mAimerCheck.setChecked(false);
				mIllumCheck.setChecked(false);
				break;
			case ScannerService.LightMode.DCD_LIGHT_MODE_AIM_ON:
				mAimerCheck.setChecked(true);
				mIllumCheck.setChecked(false);
				break;
			case ScannerService.LightMode.DCD_LIGHT_MODE_ILLUM_ON:
				mAimerCheck.setChecked(false);
				mIllumCheck.setChecked(true);
				break;
			case ScannerService.LightMode.DCD_LIGHT_MODE_ON:
				mAimerCheck.setChecked(true);
				mIllumCheck.setChecked(true);
				break;
		}
	}

	static class StreamReadTask extends AsyncTask<Void, Void, Void> {
		byte[] lowPreviewData = new byte[133150];
		int imageSize = 0;
		boolean isTaskStop = false;

		public void stop() {
			mPreview.stopPreview();
			isTaskStop = true;
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			while (!isTaskStop) {
				try {
					imageSize = iScanner.aDecodeImageStreamRead(lowPreviewData);
					if (imageSize != 0) {
						mPreview.setPreview(lowPreviewData, PREVIEW_SOURCE_WIDTH_N560x, PREVIEW_SOURCE_HEIGHT_N560x);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}

		if (iScanner != null) {
			try {
				scannerBeforeStatus = iScanner.aDecodeGetDecodeEnable();
				if (scannerBeforeStatus == 0) {
					iScanner.aDecodeSetDecodeEnable(1);
				}
				iScanner.aDecodeSetScanImageMode(ScannerService.ScanMode.DCD_MODE_IMAGE);
				getScanLightsMode();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (iScanner != null) {
			if (mButtonLock) {
				PreviewStop();
			}
			try {
				iScanner.aDecodeSetScanImageMode(ScannerService.ScanMode.DCD_MODE_SCAN);
				iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
				iScanner.aDecodeSetDecodeEnable(scannerBeforeStatus);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		unregisterReceiver(mScanKeyEventReceiver);
	}
}
