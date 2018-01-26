package device.demo.serial;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class RFIDActivity extends BaseActivity {
	private static final String TAG = RFIDActivity.class.getSimpleName();

	private static boolean mConvert = false;
	private static TextView mResult;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_rfid);
		
		((PortApplication) getApplication()).setProfile("/dev/ttyO3", 115200);
		
		mResult = (TextView) findViewById(R.id.textview_result);
		
		((Button) findViewById(R.id.button_version)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConvert = false;
				int[] command = {0x02, 0x01, 0x01, 0x76, 0x76, 0x03};
				for (int oneByte : command) {
					writeData(oneByte);
				}
			}
		});
		
		((Button) findViewById(R.id.button_serial)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mConvert = true;
				int[] command = {0x02, 0x01, 0x01, 0x62, 0x62, 0x03};
				for (int oneByte : command) {
					writeData(oneByte);
				}
			}
		});
	}
	
	private String hexToString(String buffer) {
		StringBuilder sb = new StringBuilder();
		char[] hex = buffer.toCharArray();
		
		for (int i = 3; i < 15; i++) {
			hex[i] &= 0x00FF;
			if ((hex[i] >> 4) < 0xA)
				sb.append((char) (0x30 + (hex[i] >> 4)));
			else
				sb.append((char) (0x37 + (hex[i] >> 4)));
			
			if ((hex[i] & 0x0F) < 0xA)
				sb.append((char) (0x30 + (hex[i] & 0x0F)));
			else
				sb.append((char) (0x37 + (hex[i] & 0x0F)));
		}
	    return sb.toString();
	}

	@Override
	protected void onDataReceived(final String buffer) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mResult != null) {
					mResult.setText(mConvert ? hexToString(buffer) : buffer);
				}
			}
		});
	}
}
