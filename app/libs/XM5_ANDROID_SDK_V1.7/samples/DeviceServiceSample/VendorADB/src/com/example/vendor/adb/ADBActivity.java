package com.example.vendor.adb;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import device.service.DeviceService;
import device.service.IDeviceService;

public class ADBActivity extends Activity {
    private TextView mStatusTextView;
    private Button mConnectButton;
    private boolean mAdbEnabled = false;

    private IDeviceService mService = null;

    private IDeviceService getDeviceService() {
        if (mService == null) {
            mService = IDeviceService.Stub.asInterface(ServiceManager.getService(DeviceService.TAG));
        }
        return mService;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adb);
        try {
            mAdbEnabled = getDeviceService().getAdbEnabled();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mStatusTextView = (TextView) findViewById(R.id.tv_status);
        mConnectButton = (Button) findViewById(R.id.btn_connector);

        mStatusTextView.setText(mAdbEnabled ?
                "Status : " + getString(R.string.connect) :
                "Status : " + getString(R.string.disconnect));
        mConnectButton.setText(mAdbEnabled ? R.string.disconnect : R.string.connect);
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    getDeviceService().setAdbEnabled(!mAdbEnabled);
                    mAdbEnabled = getDeviceService().getAdbEnabled();

                    mStatusTextView.setText(mAdbEnabled ?
                            "Status : " + getString(R.string.connect) :
                            "Status : " + getString(R.string.disconnect));
                    mConnectButton.setText(mAdbEnabled ? R.string.disconnect : R.string.connect);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
