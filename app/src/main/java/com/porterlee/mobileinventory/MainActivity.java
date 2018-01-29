package com.porterlee.mobileinventory;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;


public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private Dialog dialog;
    private static final String[] requiredPermissions = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final String TAG = MainActivity.class.getSimpleName();
    static IScannerService iScanner = null;
    static DecodeResult mDecodeResult = new DecodeResult();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToGrant = new ArrayList<>();
            for (String requiredPermission : requiredPermissions) {
                if (checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED)
                    permissionsToGrant.add(requiredPermission);
            }
            ActivityCompat.requestPermissions(this, (String[]) permissionsToGrant.toArray(), 0);
        }

        /*if (true) {
            startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            finish();
            return;
        }*/

        setContentView(R.layout.main_layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("Select Mode");
        builder.setMessage("Would you like to preload locations or start a standard inventory?");
        builder.setNegativeButton("Preload", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Preload");
                Toast.makeText(MainActivity.this, "Preload mode is not ready yet", Toast.LENGTH_SHORT).show();
                //startActivity(new Intent(MainActivity.this, PreloadLocationsActivity.class));
                startActivity(new Intent(MainActivity.this, MainActivity.class));
                finish();
            }
        });
        builder.setPositiveButton("Standard Inventory", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Standard Inventory");
                startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            }
        });
        dialog = builder.create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length == 0 || grantResults.length == 0)
            return;
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!dialog.isShowing()) dialog.show();
    }
}

