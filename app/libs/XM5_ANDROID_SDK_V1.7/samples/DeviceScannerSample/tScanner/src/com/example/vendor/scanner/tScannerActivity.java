package com.example.vendor.scanner;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScannerService;

@SuppressWarnings("unused")
public class tScannerActivity extends Activity {
	private static final String TAG = "tScannerActivity";

	private static IScannerService iScanner = null;
	private static DecodeResult mDecodeResult = new DecodeResult();
	private boolean mKeyLock = false;

	private static TextView barTypeView = null;
	private static TextView resultView = null;
	private static CheckBox AutoScanCheck = null;
	private static CheckBox BeepCheck = null;
	private static Button scanOnBtn = null;
	private static Button scanOffBtn = null;
	private static Button upcEnableBtn = null;
	private static Button upcDisableBtn = null;
	private static Button propEnableBtn = null;

	public static class ScanResultReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (iScanner != null) {
				try {
					iScanner.aDecodeGetResult(mDecodeResult);
					barTypeView.setText(mDecodeResult.symName);
					resultView.setText(mDecodeResult.decodeValue);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tscanner_activity);

		resultView = (TextView) findViewById(R.id.textview_scan_result);
		barTypeView = (TextView) findViewById(R.id.textview_bar_type);

		AutoScanCheck = (CheckBox) findViewById(R.id.check_autoscan);
		AutoScanCheck.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (iScanner != null) {
					if (isChecked) {
						try {
							iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					} else {
						try {
							iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});

		BeepCheck = (CheckBox) findViewById(R.id.check_beep);
		BeepCheck.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (iScanner != null) {
					if (isChecked) {
						try {
							iScanner.aDecodeSetBeepEnable(1);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					} else {
						try {
							iScanner.aDecodeSetBeepEnable(0);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});

		scanOnBtn = (Button) findViewById(R.id.button_scan_on);
		scanOnBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (iScanner != null) {
					try {
						iScanner.aDecodeSetTriggerOn(1);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}
		});

		scanOffBtn = (Button) findViewById(R.id.button_scan_off);
		scanOffBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (iScanner != null) {
					if (AutoScanCheck.isChecked()) {
						try {
							iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}

					try {
						iScanner.aDecodeSetTriggerOn(0);
					} catch (RemoteException e) {
						e.printStackTrace();
					}

					if (AutoScanCheck.isChecked()) {
						try {
							iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});

		upcEnableBtn = (Button) findViewById(R.id.button_enalbe_upc);
		upcEnableBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (iScanner != null) {
					try {
						iScanner.aDecodeSymSetEnable(
								ScannerService.SymbologyID.DCD_SYM_UPCA, 1);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}
		});

		upcDisableBtn = (Button) findViewById(R.id.button_disalbe_upc);
		upcDisableBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (iScanner != null) {
					try {
						iScanner.aDecodeSymSetEnable(
								ScannerService.SymbologyID.DCD_SYM_UPCA, 0);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}
		});

		propEnableBtn = (Button) findViewById(R.id.button_prop_enalbe);
		propEnableBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (iScanner != null) {

					int propCnt = 0;
					int symID = ScannerService.SymbologyID.DCD_SYM_UPCA;
					int propIndex = 0;
					String propName; 

					try {
						propCnt = iScanner.aDecodeSymGetLocalPropCount(symID);
						for (int i = 0; i < propCnt; i++) {
							propName = iScanner.aDecodeSymGetLocalPropName(symID, i);
							if(propName.equals("Send Check Character")) {
								propIndex = i;
								break;
							}
						}

					} catch (RemoteException e1) {
						e1.printStackTrace();
					}

					if (mKeyLock == false) {
						propEnableBtn.setText(R.string.property_enable);
						mKeyLock = true;
						try {
							iScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 0);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					} else {
						propEnableBtn.setText(R.string.property_disable);
						mKeyLock = false;
						try {
							iScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 1);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});

		try {
			initScanner();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private void initScanner() throws RemoteException {

		iScanner = IScannerService.Stub.asInterface(ServiceManager
				.getService("ScannerService"));
		if (iScanner != null) {
			iScanner.aDecodeAPIInit();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			iScanner.aDecodeSetDecodeEnable(1);
			iScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);

			if (iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
				AutoScanCheck.setChecked(true);
			} else {
				AutoScanCheck.setChecked(false);
			}

			if (iScanner.aDecodeGetBeepEnable() == 1) {
				BeepCheck.setChecked(true);
			} else {
				BeepCheck.setChecked(false);
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (iScanner != null) {
			try {
				iScanner.aDecodeAPIDeinit();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		iScanner = null;
	}
}