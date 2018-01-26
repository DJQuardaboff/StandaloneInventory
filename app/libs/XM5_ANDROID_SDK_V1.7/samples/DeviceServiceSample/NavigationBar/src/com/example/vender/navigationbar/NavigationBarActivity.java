package com.example.vender.navigationbar;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import device.service.IDeviceService;

public class NavigationBarActivity extends Activity {

    Button hideBtn;
    Button exitBtn;
    Button showBtn;
    View decorView;

    boolean navVisible;

    private IDeviceService mDeviceService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDeviceService = IDeviceService.Stub.asInterface(ServiceManager.getService("DeviceService"));
        showBtn = (Button) findViewById(R.id.showBtn);
        hideBtn = (Button) findViewById(R.id.hideBtn);
        exitBtn = (Button) findViewById(R.id.exitBtn);
        try {
            navVisible = mDeviceService.getNavigationBarHide();
        } catch (RemoteException e1) {
            e1.printStackTrace();
        }
        decorView = getWindow().getDecorView();
        Toast.makeText(getApplication(), "onCreate", Toast.LENGTH_SHORT).show();
        showBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    mDeviceService.setNavigationBarHide(false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        hideBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {

                try {
                    mDeviceService.setNavigationBarHide(true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        exitBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
