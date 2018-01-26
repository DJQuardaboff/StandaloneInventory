package device.demo.serial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidParameterException;

import device.utils.serial.SerialPort;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;

public abstract class BaseActivity extends Activity {
	private static final String TAG = BaseActivity.class.getSimpleName();
	
	private SerialPort mSerialPort;
	private ReadThread mReadThread;
	private BufferedReader mReader;
	private boolean mReadLine = false;

	protected abstract void onDataReceived(final String buffer);
	
	protected boolean writeData(int oneByte) {
		if (mSerialPort == null) {
			return false;
		}
		try {
			mSerialPort.getOutputStream().write(oneByte);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	protected boolean writeData(String buffer) {
		if (mSerialPort == null || buffer == null || buffer.isEmpty()) {
			return false;
		}
		try {
			mSerialPort.getOutputStream().write(buffer.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	protected void setToReadLine() {
		mReadLine = true;
	}

	private class ReadThread extends Thread {
		@Override
		public void run() {
			super.run();
			StringBuffer buffer = new StringBuffer();
			buffer.setLength(0);
			while(!isInterrupted()) {
				try {
					if (mSerialPort == null) return;
					if (mReader.ready()) {
						buffer.append((char) mReader.read());
					} else {
						if (mReadLine) {
							int carriage = buffer.indexOf("\n");
							if (carriage != -1) {
								onDataReceived(buffer.substring(0, carriage));
								buffer.delete(0, carriage + 1);
							}
						} else {
						if (buffer.length() > 0) {
							onDataReceived(buffer.toString());
							buffer.setLength(0);
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}	
			}
		}
	}

	private void DisplayError(int resourceId) {
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle("Error");
		b.setMessage(resourceId);
		b.setPositiveButton("OK", new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				BaseActivity.this.finish();
			}
		});
		b.show();
	}
	
	@Override
	public void onResume() {
		Log.e(TAG, "onResume");
        super.onResume();
        try {
			mSerialPort = ((PortApplication) getApplication()).getSerialPort();
			mReader = new BufferedReader(new InputStreamReader(mSerialPort.getInputStream()));
			mReadThread = new ReadThread();
			mReadThread.start();
		} catch (SecurityException e) {
			DisplayError(R.string.error_security);
		} catch (IOException e) {
			DisplayError(R.string.error_unknown);
		} catch (InvalidParameterException e) {
			DisplayError(R.string.error_configuration);
		}
	}
	
	@Override
    public void onPause() {
		Log.e(TAG, "onPause");
		if (mReadThread != null) {
			mReadThread.interrupt();
		}
		try {
			if (mReader != null) {
				mReader.close();
				mReader = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
		((PortApplication) getApplication()).closeSerialPort();
		mSerialPort = null;
		super.onPause();
	}
}
