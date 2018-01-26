package com.porterlee.mobileinventory;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("Select Mode");
        builder.setMessage("Would you like to preload locations or start a standard inventory?");
        builder.setNegativeButton("Preload", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                System.out.println("Preload");
                dialog.dismiss();
                startActivity(new Intent(MainActivity.this, PreloadActivity.class));
            }
        });
        builder.setPositiveButton("Standard Inventory", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                System.out.println("Standard Inventory");
                dialog.dismiss();
                startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            }
        });
        dialog = builder.create();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!dialog.isShowing()) dialog.show();
    }
}

