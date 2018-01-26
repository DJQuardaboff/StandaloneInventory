package com.example.vender.service;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.TextView;
import device.service.IDeviceService;

public class SampleActivity extends Activity {

    private IDeviceService mDeviceService;

    StringBuffer buf;
    TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        output = (TextView) findViewById(R.id.sampleOutput);
        buf = new StringBuffer();

        getDeviceService();
        startSample();
    }

    private IDeviceService getDeviceService() {
        if (mDeviceService == null) {
            mDeviceService = IDeviceService.Stub.asInterface(ServiceManager.getService("DeviceService"));
        }
        return mDeviceService;
    }

    private void startSample() {
        try {
            buf.append("getHardwareRevision : ");
            buf.append(mDeviceService.getHardwareRevision() + "\n");

            buf.append("getAndroidVersion : ");
            buf.append(mDeviceService.getAndroidVersion() + "\n");

            buf.append("getKernelVersion : ");
            buf.append(mDeviceService.getKernelVersion() + "\n");

            buf.append("getBuildNumber : ");
            buf.append(mDeviceService.getBuildNumber() + "\n");

            buf.append("getManufacturer : ");
            buf.append(mDeviceService.getManufacturer() + "\n");

            buf.append("getModelName : ");
            buf.append(mDeviceService.getModelName() + "\n");

            buf.append("getProcessorInfo : ");
            buf.append(mDeviceService.getProcessorInfo() + "\n");

            buf.append("getSerialNumber : ");
            buf.append(mDeviceService.getSerialNumber() + "\n");

            buf.append("getPartNumber : ");
            buf.append(mDeviceService.getPartNumber() + "\n");

            buf.append("getManufactureDate : ");
            buf.append(mDeviceService.getManufactureDate() + "\n");

            buf.append("getCameraType : ");
            buf.append(mDeviceService.getCameraType() + "\n");
            
            buf.append("getDisplayType : ");
            buf.append(mDeviceService.getDisplayType() + "\n");

            buf.append("getKeyboardType : ");
            buf.append(mDeviceService.getKeyboardType() + "\n");

            buf.append("getNandType : ");
            buf.append(mDeviceService.getNandType() + "\n");

            buf.append("getScannerType : ");
            int scanner = mDeviceService.getScannerType();
            buf.append(scanner + "\n");            

            buf.append("getTouchType : ");
            buf.append(mDeviceService.getTouchType() + "\n");

            buf.append("getBluetoothType : ");
            buf.append(mDeviceService.getBluetoothType() + "\n");

            buf.append("getGpsType : ");
            buf.append(mDeviceService.getGpsType() + "\n");

            buf.append("getPhoneType : ");
            buf.append(mDeviceService.getPhoneType() + "\n");

            buf.append("getWifiType : ");
            buf.append(mDeviceService.getWifiType() + "\n");

            buf.append("getSensorAccelerometerType : ");
            buf.append(mDeviceService.getSensorAccelerometerType() + "\n");

            buf.append("getSensorLightType : ");
            buf.append(mDeviceService.getSensorLightType() + "\n");

            buf.append("getSensorProximityType : ");
            buf.append(mDeviceService.getSensorProximityType() + "\n");

            buf.append("getBluetoothDriverVersion : ");
            buf.append(mDeviceService.getBluetoothDriverVersion() + "\n");

            buf.append("getBluetoothMacAddress : ");
            buf.append(mDeviceService.getBluetoothMacAddress() + "\n");

            buf.append("getWifiDriverVersion : ");
            buf.append(mDeviceService.getWifiDriverVersion() + "\n");

            buf.append("getWifiFirmwareVersion : ");
            buf.append(mDeviceService.getWifiFirmwareVersion() + "\n");

            buf.append("getWifiMacAddress : ");
            buf.append(mDeviceService.getWifiMacAddress() + "\n");
            
            buf.append("getWifiIpAddress : ");
            buf.append(mDeviceService.getWifiIpAddress() + "\n");

            buf.append("getMainBatteryStatus : ");
            buf.append(mDeviceService.getMainBatteryStatus() + "\n");

            buf.append("getBackupBatteryStatus : ");
            buf.append(mDeviceService.getBackupBatteryStatus() + "\n");

            buf.append("getBatterySerialNumber : ");
            buf.append(mDeviceService.getBatterySerialNumber() + "\n");

            buf.append("getChargingMainBatteryFromUsbFlag : ");
            buf.append(mDeviceService.getChargingMainBatteryFromUsbFlag() + "\n");

            buf.append("getChargingBackupBatteryFromMainBatteryFlag : ");
            buf.append(mDeviceService.getChargingBackupBatteryFromMainBatteryFlag() + "\n");            

            buf.append("getLowBatteryWarningLevel : ");
            buf.append(mDeviceService.getLowBatteryWarningLevel() + "\n");

            buf.append("getCriticalBatteryWarningLevel : ");
            buf.append(mDeviceService.getCriticalBatteryWarningLevel() + "\n");

            buf.append("getScannerClass : ");
            buf.append(mDeviceService.getScannerClass(scanner) + "\n");

            buf.append("getScannerName : ");
            buf.append(mDeviceService.getScannerName(scanner) + "\n");

            buf.append("getScannerClassName : ");
            buf.append(mDeviceService.getScannerClassName(scanner));
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {
            output.setText(buf.toString());
        }
    }
}
