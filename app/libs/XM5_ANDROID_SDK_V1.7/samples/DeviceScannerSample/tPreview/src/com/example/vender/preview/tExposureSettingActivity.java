package com.example.vender.preview;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import device.scanner.ExposureSettings;
import device.scanner.IScannerService;
import device.scanner.ScanConst;

public class tExposureSettingActivity extends Activity {
	public static final String TAG = "tExposureSettingActivity";
	private static IScannerService iScanner = null;
	private static EditText mMaxExposure = null;
	private static EditText mMaxGain = null;
	private static EditText mTargetValue = null;
	private static EditText mTargetPercentile = null;
	private static EditText mTargetAceeptGap = null;
	private static Button mBtnOk = null;
	private static Button mBtnCancel = null;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.exposure_setting_activity);

		iScanner = IScannerService.Stub.asInterface(ServiceManager.getService(ScanConst.TAG));
		if (iScanner != null) {
			try {
				iScanner.aDecodeAPIInit();
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		mMaxExposure = (EditText) findViewById(R.id.edit_max_exposure);
		mMaxGain = (EditText) findViewById(R.id.edit_max_gain);
		mTargetValue = (EditText) findViewById(R.id.edit_target_value);
		mTargetPercentile = (EditText) findViewById(R.id.edit_target_percentile);
		mTargetAceeptGap = (EditText) findViewById(R.id.edit_target_acceptgap);
		mBtnOk = (Button) findViewById(R.id.btn_ok);
		mBtnCancel = (Button) findViewById(R.id.btn_cancel);

		mBtnOk.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					setExposureSetting();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				finish();
			}
		});

		mBtnCancel.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
	}

	public void getExposureSetting() throws RemoteException {
		ExposureSettings exposureSetting = new ExposureSettings();

		if (iScanner != null) {
			iScanner.aDecodeGetExposureSettings(exposureSetting);
			mMaxExposure.setText(Integer.toString(exposureSetting.maxExpousre));
			mMaxGain.setText(Integer.toString(exposureSetting.maxGain));
			mTargetValue.setText(Integer.toString(exposureSetting.targetValue));
			mTargetPercentile.setText(Integer.toString(exposureSetting.targetPercentile));
			mTargetAceeptGap.setText(Integer.toString(exposureSetting.targetAcceptGap));
		}
	}

	public void setExposureSetting() throws RemoteException {
		ExposureSettings exposureSetting = new ExposureSettings();

		exposureSetting.maxExpousre = Integer.valueOf(mMaxExposure.getText().toString()).intValue();
		exposureSetting.maxGain = Integer.valueOf(mMaxGain.getText().toString()).intValue();
		exposureSetting.targetValue = Integer.valueOf(mTargetValue.getText().toString()).intValue();
		exposureSetting.targetPercentile = Integer.valueOf(mTargetPercentile.getText().toString()).intValue();
		exposureSetting.targetAcceptGap = Integer.valueOf(mTargetAceeptGap.getText().toString()).intValue();

		if (iScanner != null) {
			iScanner.aDecodeSetExposureSettings(exposureSetting);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			getExposureSetting();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
}
