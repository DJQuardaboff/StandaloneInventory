package com.example.vendor.buttons;

import device.hijack.HiJackData;
import device.hijack.IHiJackService;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.Selection;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class VendorButtonsActivity extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "VendorButtonsActivity";
    
    private static final String KEYCODE_HOME_SYMBOL = "KEYCODE_HOME";
    private static final int KEYCODE_HOME_CODE = 3;
    
    IHiJackService mHiJackService;
    private HiJackData[] mHiJackDataList;
    
    private TextView mPropertyLabel;
    private TextView mPropertySymbol;
    
    private TextView mDefineCurrent;
    private TextView mDefinePath;
    
    private IHiJackService getHiJackService() {
        if (mHiJackService == null)
            mHiJackService = IHiJackService.Stub.asInterface(ServiceManager.getService("HiJackService"));
        return mHiJackService;
    }
    
    private void updatePropertyUI() {
        if (mHiJackDataList == null) {
            try {
                mHiJackDataList = getHiJackService().getAllHiJackData();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        
        if (mPropertyLabel != null)
            mPropertyLabel.setText(mHiJackDataList[0].getLabel());
        if (mPropertySymbol != null)
            mPropertySymbol.setText(mHiJackDataList[0].getConvertSymbol());
    }
    
    private void changeProperty() {
        int count = 0;
        try {
            count = getHiJackService().setAllHiJackData(mHiJackDataList);
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {
            if (count > 0) {
                Toast.makeText(this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
                updatePropertyUI();
            } else {
                Toast.makeText(this, getString(R.string.toast_no_change), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void updateDefineUI() {
        String currentKCMapFile = "";
        try {
            currentKCMapFile = getHiJackService().getCurrentKCMapFile();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        
        if (mDefineCurrent != null)
            mDefineCurrent.setText(currentKCMapFile);
        if (mDefinePath != null) {
            mDefinePath.setText(Environment.getExternalStorageDirectory() + "/");
            Selection.setSelection(mDefinePath.getEditableText(), mDefinePath.length());
        }
    }
    
    private void changeDefine() {
        int result = 0;
        try {
            result = getHiJackService().changeKCMapFile(mDefinePath.getText().toString());
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {            
            if (result == 0) {
                Toast.makeText(this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
                updateDefineUI();                
            } else {
                String failMsg = getString(R.string.toast_save_fail) + "(" + result + ")";
                Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void defaultDefine() {
        boolean result = true;
        try {
            result = getHiJackService().removeKCMapFile();
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {            
            if (result) {
                Toast.makeText(this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
                updateDefineUI();                
            } else {
                Toast.makeText(this, getString(R.string.toast_save_fail), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_buttons);
        
        mPropertyLabel = (TextView) findViewById(R.id.label_properies);
        mPropertySymbol = (TextView) findViewById(R.id.symbol_properies);
        
        updatePropertyUI();
        
        Button saveProperty = (Button) findViewById(R.id.save_properties);
        saveProperty.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHiJackDataList == null) return;
                mHiJackDataList[0].setFlag(HiJackData.FLAG_UPDATE);
                mHiJackDataList[0].setConvertKeyCode(KEYCODE_HOME_CODE);
                mHiJackDataList[0].setConvertSymbol(KEYCODE_HOME_SYMBOL);
                mHiJackDataList[0].setExecuteApp("");
                changeProperty();
            }
        });
        Button defaultProperty = (Button) findViewById(R.id.default_properties);
        defaultProperty.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHiJackDataList == null) return;
                mHiJackDataList[0].setFlag(HiJackData.FLAG_UPDATE);
                mHiJackDataList[0].setConvertKeyCode(mHiJackDataList[0].getDefineKeyCode());
                mHiJackDataList[0].setConvertSymbol(mHiJackDataList[0].getDefineSymbol());
                mHiJackDataList[0].setExecuteApp("");
                changeProperty();
            }
        });
        
        mDefineCurrent = (TextView) findViewById(R.id.current_define);
        mDefinePath = (TextView) findViewById(R.id.path_define);
        
        updateDefineUI();
        
        Button saveDefine = (Button) findViewById(R.id.save_define);
        saveDefine.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDefinePath == null) return;
                changeDefine();
            }
        });
        Button defaultDefine = (Button) findViewById(R.id.default_define);
        defaultDefine.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                defaultDefine();
            }
        });
    }
}
