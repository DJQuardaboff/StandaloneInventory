package device.demo.serial;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import device.service.DeviceService;
import device.service.IDeviceService;

public class OEM615Activity extends BaseActivity {
	private static final String TAG = OEM615Activity.class.getSimpleName();

	private IDeviceService mDS;
	private ProgressDialog mProgress;
	private static final int MAX_LINES = 200;
	private static ScrollView mScroll;
	private static LinearLayout mLayout;
	private static Runnable mRunnableScroll = new Runnable() {
		@Override
		public void run() {
			mScroll.fullScroll(ScrollView.FOCUS_DOWN);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_oem615);

		mDS = IDeviceService.Stub.asInterface(ServiceManager.getService(DeviceService.TAG));
		try {
			mDS.setGpsPower(true);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		((PortApplication) getApplication()).setProfile("/dev/ttyO0", 9600);
		setToReadLine();
		
		mScroll = (ScrollView) findViewById(R.id.scrollReception);
		mLayout = (LinearLayout) findViewById(R.id.layoutReception);
		
		mProgress = ProgressDialog.show(this, "", "Loading, Please wait...");
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				mProgress.dismiss();
				String[] commandList = getResources().getStringArray(R.array.enabled_oem615_nmea);
				for (String command : commandList) {
					writeData(command);
					writeData('\n');
				}
			}
		}, 10000);
	}
	
	@Override
	protected void onDestroy() {
		if (mDS != null) {
			try {
				mDS.setGpsPower(false);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		super.onDestroy();
	}

	@Override
	protected void onDataReceived(final String buffer) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mLayout.getChildCount() > MAX_LINES) {
					mLayout.removeViewAt(0);
				}

				TextView tv = new TextView(getBaseContext());
				tv.setText(buffer);
				if (buffer.contains("GPGGA")) {
					tv.setTextColor(Color.RED);
				}
				mLayout.addView(tv);
				mScroll.post(mRunnableScroll);
			}
		});
	}
}
