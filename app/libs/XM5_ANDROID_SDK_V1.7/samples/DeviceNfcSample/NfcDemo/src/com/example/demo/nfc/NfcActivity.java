package com.example.demo.nfc;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import device.nfc.INfcService;
import device.nfc.NfcIndex;
import device.nfc.NfcResult;

@SuppressWarnings("unused")
public class NfcActivity extends Activity {
	private static final String TAG = "NfcActivity";

	private static detectResultReceiver mDetectResultReceiver = null;
	private static INfcService iNfc = null;
	private static NfcResult mNfcResult = new NfcResult();
	private boolean mKeyLock = false;

	private static TextView tagTypeView = null;
	private static TextView tagResultView = null;
	private static TextView tagApduView = null;
	private static EditText tagApduEdit = null;
	private static TextView tagKeyView = null;
	private static EditText tagKeyEdit = null;
	private static TextView tagAuthView = null;
	private static EditText tagAuthEdit = null;
	private static TextView tagReadView = null;
	private static EditText tagReadEdit = null;
	private static TextView tagDataView = null;
	private static RadioButton tagMiFareButton = null;
	private static RadioButton tagTypeABButton = null;
	private static Button tagStartBtn = null;
	
	private static boolean bDetecting = false;
	private static boolean bStarting =  false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nfc_activity);

		Log.e("NFCDEMO", "ONCREATE START");
		bStarting = true;

		tagTypeView = (TextView) findViewById(R.id.textview_nfc_type);
		tagResultView = (TextView) findViewById(R.id.textview_nfc_result);
		tagApduView = (TextView) findViewById(R.id.static_apdu);
		tagApduEdit = (EditText) findViewById(R.id.edit_tag_apdu);
		tagKeyView = (TextView) findViewById(R.id.static_tag_key);
		tagKeyEdit = (EditText) findViewById(R.id.edit_tag_key);
		tagAuthView = (TextView) findViewById(R.id.static_tag_auth);
		tagAuthEdit = (EditText) findViewById(R.id.edit_tag_auth);
		tagReadView = (TextView) findViewById(R.id.static_tag_read);
		tagReadEdit = (EditText) findViewById(R.id.edit_tag_read);
		tagDataView = (TextView) findViewById(R.id.textview_tag_data);
		tagMiFareButton = (RadioButton) findViewById(R.id.radio_mifare);
		tagTypeABButton = (RadioButton) findViewById(R.id.radio_typeAB);
		tagStartBtn = (Button) findViewById(R.id.button_nfc_start);

		tagApduView.setVisibility(View.GONE);
		tagApduEdit.setVisibility(View.GONE);

		tagKeyView.setVisibility(View.VISIBLE);
		tagKeyEdit.setVisibility(View.VISIBLE);
		tagAuthView.setVisibility(View.VISIBLE);
		tagAuthEdit.setVisibility(View.VISIBLE);
		tagReadView.setVisibility(View.VISIBLE);
		tagReadEdit.setVisibility(View.VISIBLE);
		
		tagKeyEdit.setText("");
		tagAuthEdit.setText("");
		tagReadEdit.setText("");
		
		bDetecting = false;
		try {
			initNfc();
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		if (mDetectResultReceiver == null) {
			mDetectResultReceiver = new detectResultReceiver();
		}

		tagStartBtn.setOnClickListener(mClickListener);

		tagMiFareButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					tagApduView.setVisibility(View.GONE);
					tagApduEdit.setVisibility(View.GONE);

					tagKeyView.setVisibility(View.VISIBLE);
					tagKeyEdit.setVisibility(View.VISIBLE);
					tagAuthView.setVisibility(View.VISIBLE);
					tagAuthEdit.setVisibility(View.VISIBLE);
					tagReadView.setVisibility(View.VISIBLE);
					tagReadEdit.setVisibility(View.VISIBLE);

					tagTypeABButton.setChecked(false);
				}
			}
		});

		tagTypeABButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					tagApduView.setVisibility(View.VISIBLE);
					tagApduEdit.setVisibility(View.VISIBLE);

					tagKeyView.setVisibility(View.GONE);
					tagKeyEdit.setVisibility(View.GONE);
					tagAuthView.setVisibility(View.GONE);
					tagAuthEdit.setVisibility(View.GONE);
					tagReadView.setVisibility(View.GONE);
					tagReadEdit.setVisibility(View.GONE);

					tagMiFareButton.setChecked(false);
				}
			}
		});
		
		Log.e("NFCDEMO", "ONCREATE END");
	}

	private void initNfc() throws RemoteException {
		iNfc = INfcService.Stub.asInterface(ServiceManager.getService("NfcService"));

		if (iNfc != null) {
			String str = iNfc.nfcGetPowerState();
			if (!str.equals("on")) {
				iNfc.nfcSetPowerState(true);

				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		Log.e("NFCDEMO", "ONDESTROY START");
		if (iNfc != null) {
			try {
				iNfc.nfcSetPowerState(false);
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		iNfc = null;
		Log.e("NFCDEMO", "ONDESTROY END");
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {}

		Log.d(TAG, "[NFCDEMO]onResume(+++)");
		if (mDetectResultReceiver != null) {
           registerReceiver(mDetectResultReceiver, new IntentFilter(NfcResult.INTENT_USERMSG));
		}
		Log.d(TAG, "[NFCDEMO]onResume(---)");
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "[NFCDEMO]onPause(+++)");
	    if (mDetectResultReceiver != null) {
            unregisterReceiver(mDetectResultReceiver);
            mDetectResultReceiver = null;
	    }
		super.onPause();
		Log.d(TAG, "[NFCDEMO]onPause(---)");
	}
	
	Button.OnClickListener mClickListener = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Log.e("NFCDEMO", "OnClickListener START");
			switch(v.getId())
			{
			case R.id.button_nfc_start:
				if (bDetecting)
				{
					bDetecting = false;
					tagStartBtn.setText("Nfc Start");
				}
				else 
				{
					bDetecting = true;
					bStarting = false;
					tagStartBtn.setText("Nfc Stop");
					if (iNfc != null) {
						try {
							iNfc.nfcSetDetect(0xF);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
				break;
			}
			Log.e("NFCDEMO", "OnClickListener END");
		}
	};

	public static class detectResultReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("NFCDEMO", "detectResultReceiver START");
			if (iNfc != null && bStarting == false) {
				if (NfcResult.INTENT_USERMSG.equals(intent.getAction())) {
					try {
						byte [] pdata = new byte[20];
						byte [] recvbuffer = new byte[260];
						int length = 0;
						int auth = 0;
						int readblock = 0;
						int recvlength = 0;
	
						tagStartBtn.setText("Nfc Start");
						bDetecting = false;
	
						mNfcResult = (NfcResult) intent.getParcelableExtra(NfcResult.INTENT_NFCMSG);
						iNfc.nfcGetDetectResult(mNfcResult);
	
						tagTypeView.setText(NFC_GetTagName(mNfcResult.mTagType));
						
						if (mNfcResult.mTagType == NfcIndex.NFC_TAG_TYPE_UNKNOWN) {
							//tagTypeView.setText("");
							tagResultView.setText("");
							tagDataView.setText("");
						}
						else if ((mNfcResult.mTagType >= NfcIndex.NFC_TAG_Mifare_ultralight) && 
								(mNfcResult.mTagType <= NfcIndex.NFC_TAG_Mifare_nPA)) {
							tagMiFareButton.setChecked(true);
							tagTypeABButton.setChecked(false);
	
							tagResultView.setText(mNfcResult.getTagID());
	
							if (tagKeyEdit.getText().toString().length() != 0) {
								length = tagKeyEdit.getText().toString().length();
								String str = tagKeyEdit.getText().toString();

								for (int i=0; i < length/2; i++)
								{
									pdata[i] = (byte) Integer.parseInt(str.substring(i*2, i*2+2), 16);
								}
								
								auth = Integer.parseInt(tagAuthEdit.getText().toString());
								readblock = Integer.parseInt(tagReadEdit.getText().toString());
								
								iNfc.nfcReadMifareClassicSetKey(pdata, length/2);
								iNfc.nfcReadMifareClassicAuthenticateBlock(0xA, auth);
								length = 260;
								recvlength = iNfc.nfcReadMifareClassicReadBlock(recvbuffer, length, readblock);
	
								str = "";
								for (int i = 0; i < recvlength; i ++)
								{
									str += String.format("%02x", recvbuffer[i]);
								}
								tagDataView.setText(str);
							}
						}
						else {
							tagMiFareButton.setChecked(false);
							tagTypeABButton.setChecked(true);
	
							tagResultView.setText(mNfcResult.getTagID());
	
							if (tagApduEdit.getText().toString().length() != 0) {
								length = tagApduEdit.getText().toString().length();
								String str = tagApduEdit.getText().toString();
								for (int i=0; i < length/2; i++)
								{
									pdata[i] = (byte) Integer.parseInt(str.substring(i*2, i*2+2), 16);
								}
	
								if (mNfcResult.mTagType == NfcIndex.NFC_TAG_Mifare_jcop) {
									recvlength = iNfc.nfcReadTypeADataExchange(recvbuffer, 260, pdata, length);
								}
								else if (mNfcResult.mTagType == NfcIndex. NFC_TAG_TYPE_B) {
									recvlength = iNfc.nfcReadTypeBDataExchange(recvbuffer, 260, pdata, length);
								}
								str = "";
								for (int i = 0; i < length; i ++)
								{
									str += String.format("%02x", recvbuffer[i]);
								}
								tagDataView.setText(str);
							}
						}
						iNfc.nfcReadComplete();
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}
			Log.e("NFCDEMO", "detectResultReceiver END");
		}

		//-----------------------------------------------------------------------------
		//  NFC_GetTagName()
		//-----------------------------------------------------------------------------
		public String NFC_GetTagName(int tagType)
		{
			String str = "";
			switch( tagType )
			{
				case NfcIndex.NFC_TAG_Mifare_ultralight:
					str = "MIFARE Ultralight";
					break;

				case NfcIndex.NFC_TAG_Mifare_ultralight_c:
					str = "MIFARE Ultralight C";
					break;

				case NfcIndex.NFC_TAG_Mifare_classic:
					str = "MIFARE Classic";
					break;

				case NfcIndex.NFC_TAG_Mifare_classic_1k:
					str = "MIFARE Classic 1K";
					break;

				case NfcIndex.NFC_TAG_Mifare_classic_4k:
					str = "MIFARE Classic 4K";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus:
					str = "MIFARE Plus";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_2k_sl1:
					str = "MIFARE Plus 2K SL1";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_4k_sl1:
					str = "MIFARE Plus 4K SL1";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_2k_sl2:
					str = "MIFARE Plus 2K SL2";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_4k_sl2:
					str = "MIFARE Plus 4K SL2";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_2k_sl3:
					str = "MIFARE Plus 2K SL3";
					break;

				case NfcIndex.NFC_TAG_Mifare_plus_4k_sl3:
					str = "MIFARE Plus 4K SL3";
					break;

				case NfcIndex.NFC_TAG_Mifare_desfire:
					str = "MIFARE DESFire";
					break;

				case NfcIndex.NFC_TAG_Mifare_jcop:
					str = "JCOP";
					break;

				case NfcIndex.NFC_TAG_Mifare_mini:
					str = "MIFARE Mini";
					break;

				case NfcIndex.NFC_TAG_Mifare_nPA:
					str = "German eID";
					break;

				case NfcIndex.NFC_TAG_ISO15693:
					str = "ISO15693";
					break;

				case NfcIndex.NFC_TAG_FELICA:
					str = "FELICA";
					break;

				case NfcIndex.NFC_TAG_TYPE_B:
					str = "TYPEB";
					break;

				default:
					str = "Unknown";
					break;
			}
			return str;
		}
	}
}