package com.porterlee.standardinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.constraint.Guideline;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.*;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

import com.porterlee.standardinventory.InventoryDatabase.ItemTable;
import com.porterlee.standardinventory.InventoryDatabase.LocationTable;
import com.porterlee.standardinventory.DividerItemDecoration;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class InventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), InventoryDatabase.DIRECTORY);
    private static final String IS_LIKE_ITEM_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'e1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'E%\'";
    private static final String IS_LIKE_CONTAINER_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'m1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'M%\'";
    //private static final String IS_LIKE_LOCATION_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'V%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'L5%\'";
    //private static final String IS_LIKE_PROCESS_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'L3%\'";
    private static final String DUPLICATE_BARCODE_TAG = "D";
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = InventoryActivity.class.getSimpleName();
    private static final int errorColor = Color.RED;
    private String previousPrefix = "";
    private String previousPostfix = "";
    private SQLiteStatement IS_DUPLICATE_STATEMENT;
    private SQLiteStatement LAST_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement LAST_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement LAST_LOCATION_ID_STATEMENT;
    private SQLiteStatement TOTAL_ITEM_COUNT;
    private SQLiteStatement TOTAL_LOCATION_COUNT;
    private SharedPreferences sharedPreferences;
    private Vibrator vibrator;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    //private int[] autosizeInventoryItemTextSizes;
    //private int[] autosizeInventoryLocationTextSizes;
    private Guideline guideline;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Float, String> saveTask;
    private ScanResultReceiver resultReciever;
    private int itemCount = 0;
    private int containerCount = 0;
    private long lastLocationId = -1;
    private String lastLocationBarcode = "";
    private String lastItemBarcode = "";
    private SelectableRecyclerView itemRecyclerView;
    private SelectableRecyclerView locationRecyclerView;
    private CursorRecyclerViewAdapter<InventoryItemViewHolder> itemRecyclerAdapter;
    private CursorRecyclerViewAdapter<InventoryLocationViewHolder> locationRecyclerAdapter;
    private SQLiteDatabase mDatabase;
    private IScannerService iScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();

    private BroadcastReceiver mScanKeyEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
                KeyEvent event = intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);
                switch (event.getKeyCode()) {
                    case ScanConst.KEYCODE_SCAN_FRONT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_LEFT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_RIGHT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_REAR:
                        onScanKeyEvent(event.getAction());
                        break;
                }
            }
        }
    };

    private void onScanKeyEvent(int action) {
        if (iScanner != null) {
            try {
                if (action == KeyEvent.ACTION_DOWN) {
                    iScanner.aDecodeSetTriggerOn(1);
                } else if (action == KeyEvent.ACTION_UP) {
                    iScanner.aDecodeSetTriggerOn(0);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inventory_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%1s v%2s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        sharedPreferences = getPreferences(MODE_PRIVATE);

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        //Resources resources = getResources();
        //autosizeInventoryItemTextSizes = resources.getIntArray(R.array.autosize_inventory_item_text_sizes);
        //autosizeInventoryLocationTextSizes = resources.getIntArray(R.array.autosize_inventory_location_text_sizes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return;
            }
        }

        archiveDirectory = new File(getFilesDir() + "/" + InventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + InventoryDatabase.DIRECTORY + "/" + InventoryDatabase.FILE_NAME);
        databaseFile = new File(OUTPUT_PATH, InventoryDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            try {
                //System.out.println(databaseFile.exists());
                if (databaseFile.renameTo(File.createTempFile("error", ".db", new File(databaseFile.getParent(), InventoryDatabase.ARCHIVE_DIRECTORY)))) {
                    Toast.makeText(this, "There was an error loading the inventory file. It has been archived", Toast.LENGTH_SHORT).show();
                } else {
                    databaseLoadError();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
                databaseLoadError();
            }
        }
    }

    private void databaseLoadError() {
        AlertDialog.Builder builder = new AlertDialog.Builder(InventoryActivity.this);
        builder.setCancelable(false);
        builder.setTitle("Database Load Error");
        builder.setMessage("There was an error loading the inventory file and it could not be archived.\n\nWould you like to delete the it?\n\nAnswering no will close the app.");
        builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!databaseFile.delete()) {
                    Toast.makeText(InventoryActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Toast.makeText(InventoryActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                initialize();
                //mDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
            }
        });
        builder.create().show();
    }

    private void initialize() throws SQLiteCantOpenDatabaseException{
        mDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        ItemTable.create(mDatabase);
        LocationTable.create(mDatabase);

        IS_DUPLICATE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " IN ( SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? ) ) AND " + ItemTable.Keys.BARCODE + " = ?;");
        LAST_ITEM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1;");
        LAST_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");
        LAST_LOCATION_ID_STATEMENT = mDatabase.compileStatement("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");
        TOTAL_ITEM_COUNT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME);
        TOTAL_LOCATION_COUNT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME);

        //itemCount = getItemCount();
        //containerCount = getContainerCount();
        //lastLocationId = getLastLocationId();
        //lastLocationBarcode = getLastLocationBarcode();
        lastItemBarcode = getLastItemBarcode();

        guideline = findViewById(R.id.guideline);
        progressBar = findViewById(R.id.progress_saving);

        /*this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });*/

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new CursorRecyclerViewAdapter<InventoryItemViewHolder>(queryItems()) {
            @NonNull
            @Override
            public InventoryItemViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
                return new InventoryItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_item_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(final InventoryItemViewHolder holder, final Cursor cursor) {
                holder.bindViews(cursor, itemRecyclerView.getSelectedItem() == holder.getAdapterPosition());
            }

            @Override
            public int getItemViewType(final int i) {
                return 0;
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);
        itemRecyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider, DividerItemDecoration.VERTICAL_LIST));

        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new CursorRecyclerViewAdapter<InventoryLocationViewHolder>(queryLocations()) {
            @NonNull
            @Override
            public InventoryLocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new InventoryLocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_location_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(InventoryLocationViewHolder viewHolder, Cursor cursor) {
                viewHolder.bindViews(cursor, locationRecyclerView.getSelectedItem());
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator locationRecyclerAnimator = new DefaultItemAnimator();
        locationRecyclerAnimator.setAddDuration(100);
        locationRecyclerAnimator.setChangeDuration(100);
        locationRecyclerAnimator.setMoveDuration(100);
        locationRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(locationRecyclerAnimator);

        /*mDatabase.beginTransaction();
        for (int i = 0; i < 1000; i++) {
            //Log.e(TAG, String.valueOf(i));
            randomScan();
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();*/

        itemRecyclerView.setSelectedItem(-1);
        locationRecyclerView.setSelectedItem(-1);
        updateInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resultReciever = new ScanResultReceiver();
        IntentFilter resultFilter = new IntentFilter();
        resultFilter.setPriority(0);
        resultFilter.addAction("device.scanner.USERMSG");
        registerReceiver(resultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
        registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));
        loadCurrentScannerOptions();

        if (iScanner != null) {
            try {
                iScanner.aDecodeSetTriggerOn(0);
                previousPrefix = iScanner.aDecodeGetPrefix();
                previousPostfix = iScanner.aDecodeGetPostfix();
                iScanner.aDecodeSetPrefix("");
                iScanner.aDecodeSetPostfix("");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(resultReciever);
        unregisterReceiver(mScanKeyEventReceiver);

        if (iScanner != null) {
            try {
                iScanner.aDecodeSetTriggerOn(0);
                iScanner.aDecodeSetPrefix(previousPrefix);
                iScanner.aDecodeSetPostfix(previousPostfix);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (saveTask != null) {
            saveTask.cancel(false);
            saveTask = null;
        }

        if (iScanner != null) {
            try {
                iScanner.aDecodeSetTriggerOn(0);
                iScanner.aDecodeSetPrefix(previousPrefix);
                iScanner.aDecodeSetPostfix(previousPostfix);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if (mDatabase != null)
            mDatabase.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inventory_menu, menu);
        loadCurrentScannerOptions();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                if (TOTAL_ITEM_COUNT.simpleQueryForLong() > 0 || TOTAL_LOCATION_COUNT.simpleQueryForLong() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear Inventory");
                    builder.setMessage("Are you sure you want to clear this inventory?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask != null) {
                                return;
                            }

                            changedSinceLastArchive = true;

                            //int deletedCount = mDatabase.delete(ItemTable.NAME, "1", null);
                            //mDatabase.delete(ItemTable.NAME, null, null);
                            //mDatabase.delete(LocationTable.NAME, null, null);

                            mDatabase.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
                            ItemTable.create(mDatabase);

                            mDatabase.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);
                            LocationTable.create(mDatabase);

                            //if (itemCount + containerCount != deletedCount)
                                //Log.v(TAG, "Detected inconsistencies with number of items while deleting");

                            itemCount = 0;
                            containerCount = 0;
                            lastLocationId = -1;
                            lastLocationBarcode = "";
                            lastItemBarcode = "";

                            itemRecyclerAdapter.changeCursor(null);
                            locationRecyclerAdapter.changeCursor(null);
                            updateInfo();
                            Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_save_to_file:
                if (TOTAL_ITEM_COUNT.simpleQueryForLong() < 1) {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        return true;
                    }
                }

                if (saveTask == null) {
                    preSave();
                    archiveDatabase();
                    (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                    saveTask = new SaveToFileTask().execute();
                } else {
                    saveTask.cancel(false);
                    postSave();
                }

                return true;
            case R.id.cancel_save:
                if (saveTask != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Cancel Save");
                    builder.setMessage("Are you sure you want to stop saving this file?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask != null && !saveTask.isCancelled())
                                saveTask.cancel(false);
                        }
                    });
                    builder.create().show();
                } else {
                    postSave();
                }
                return true;
            case R.id.action_continuous:
                try {
                    if (!item.isChecked()){
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    } else {
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                    }
                    item.setChecked(!item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initScanner() throws RemoteException {
        iScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"));

        if (iScanner != null) {
            iScanner.aDecodeAPIInit();
            //try {
            //Thread.sleep(500);
            //} catch (InterruptedException e) {
            //}
            iScanner.aDecodeSetDecodeEnable(1);
            iScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private void loadCurrentScannerOptions() {
        if (mOptionsMenu != null) {
            MenuItem item = mOptionsMenu.findItem(R.id.action_continuous);
            try {
                if (iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
                    iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    item.setChecked(true);
                } else
                    item.setChecked(iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
            } catch (NullPointerException e) {
                e.printStackTrace();
                item.setVisible(false);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private int getItemCount() {
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_ITEM_CLAUSE + " AND " + ItemTable.Keys.LOCATION_ID + " = ?", new String[] { String.valueOf(lastLocationId) });
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    private int getContainerCount() {
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_CONTAINER_CLAUSE + " AND " + ItemTable.Keys.LOCATION_ID + " = ?", new String[] { String.valueOf(lastLocationId) });
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    /*public int getLocationCount() {
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }*/

    private void updateInfo() {
        //Log.v(TAG, "Updating info");
        ((TextView) findViewById(R.id.current_location)).setText(lastLocationBarcode.isEmpty() ? "-" : lastLocationBarcode);
        ((TextView) findViewById(R.id.last_scan)).setText(lastItemBarcode.isEmpty() ? "-" : lastItemBarcode);
        ((TextView) findViewById(R.id.total_items)).setText(lastLocationBarcode.isEmpty() ? "-" : String.valueOf(itemRecyclerAdapter.getItemCount()));
        //((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemCount));
        //((TextView) findViewById(R.id.total_containers)).setText(String.valueOf(containerCount));
        findViewById(R.id.total_containers_title).setVisibility(View.GONE);
        findViewById(R.id.total_containers).setVisibility(View.GONE);
    }

    private void preSave() {
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        saveTask = null;
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private static final String alphaNumeric = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private void randomScan() {
        Random r = new Random();
        final float temp = r.nextFloat();
        String barcode = (lastLocationId == -1 | temp < .1) ? "V" : (temp < .25 ? "m1" : (temp < .3 ? "M" : (temp < .9 ? "e1" : "E")));

        for (int i = r.nextInt(5) + 10; i > 0; i--)
            barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))).toUpperCase());

        if (isLocation(barcode))
            barcode = barcode.toUpperCase();

        scanBarcode(barcode);
    }

    private void scanBarcode(String barcode) {
        if (saveTask != null) {
            vibrate(300);
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isItem(barcode) && !isContainer(barcode) && !isLocation(barcode)) {
            vibrate(300);
            Toast.makeText(this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
            return;
        }

        IS_DUPLICATE_STATEMENT.bindLong(1, lastLocationId);
        IS_DUPLICATE_STATEMENT.bindString(2, barcode);
        final boolean isDuplicate = IS_DUPLICATE_STATEMENT.simpleQueryForLong() > 0;

        if (isDuplicate) {
            Toast.makeText(InventoryActivity.this, "Duplicate item scanned", Toast.LENGTH_SHORT).show();
            vibrate(300);
            return;
        }

        if (isItem(barcode)) {
            addBarcodeItem(barcode, "");
        } else if (isContainer(barcode)) {
            addBarcodeContainer(barcode, "");
        } else if (isLocation(barcode)) {
            addBarcodeLocation(barcode, "");
        } else {
            vibrate(300);
            Toast.makeText(InventoryActivity.this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    private void addBarcodeItem(@NonNull String barcode, @NonNull String tags) {
        if (saveTask != null) return;
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newItem = new ContentValues();
        newItem.put(InventoryDatabase.BARCODE, barcode);
        newItem.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newItem.put(InventoryDatabase.DESCRIPTION, "");
        newItem.put(InventoryDatabase.TAGS, tags);
        newItem.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (mDatabase.insert(ItemTable.NAME, null, newItem) == -1) {
            vibrate(300);
            Log.w(TAG, "Error adding item \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding item \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        itemRecyclerAdapter.changeCursor(queryItems());

        int selectedItem = -1;
        if (itemRecyclerAdapter.getCursor() != null) {
            int barcodeIndex = itemRecyclerAdapter.getCursor().getColumnIndex("barcode");
            itemRecyclerAdapter.getCursor().moveToFirst();

            while (!itemRecyclerAdapter.getCursor().isAfterLast()) {
                if (itemRecyclerAdapter.getCursor().getString(barcodeIndex).equals(barcode))
                    selectedItem = itemRecyclerAdapter.getCursor().getPosition();
                itemRecyclerAdapter.getCursor().moveToNext();
            }
        }

        itemRecyclerView.setSelectedItem(selectedItem);
        changedSinceLastArchive = true;
        itemCount++;
        lastItemBarcode = barcode;
        updateInfo();
    }

    private void removeBarcodeItem(@NonNull InventoryItemViewHolder holder) {
        if (saveTask != null) return;
        //System.out.println("remove item " + index);
        if (mDatabase.delete(ItemTable.NAME, InventoryDatabase.ID + " = ?;", new String[] {String.valueOf(holder.getId())}) > 0) {
            itemCount--;
            lastItemBarcode = getLastItemBarcode();
            itemRecyclerAdapter.changeCursor(queryItems());
            changedSinceLastArchive = true;
            updateInfo();
        } else {
            vibrate(300);
            Cursor cursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.w(TAG, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing item \"" + barcode +"\" from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    private void addBarcodeContainer(@NonNull String barcode, @NonNull String tags) {
        if (saveTask != null) return;
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newContainer = new ContentValues();
        newContainer.put(InventoryDatabase.BARCODE, barcode);
        newContainer.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newContainer.put(InventoryDatabase.DESCRIPTION, "");
        newContainer.put(InventoryDatabase.TAGS, tags);
        newContainer.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (mDatabase.insert(ItemTable.NAME, null, newContainer) < 0) {
            vibrate(300);
            Log.w(TAG, "Error adding container \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding container \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        itemRecyclerAdapter.changeCursor(queryItems());

        int selectedItem = -1;
        if (itemRecyclerAdapter.getCursor() != null) {
            int barcodeIndex = itemRecyclerAdapter.getCursor().getColumnIndex("barcode");
            itemRecyclerAdapter.getCursor().moveToFirst();

            while (!itemRecyclerAdapter.getCursor().isAfterLast()) {
                if (itemRecyclerAdapter.getCursor().getString(barcodeIndex).equals(barcode))
                    selectedItem = itemRecyclerAdapter.getCursor().getPosition();
                itemRecyclerAdapter.getCursor().moveToNext();
            }
        }

        itemRecyclerView.setSelectedItem(selectedItem);

        changedSinceLastArchive = true;

        containerCount++;
        lastItemBarcode = barcode;

        updateInfo();
    }

    private void removeBarcodeContainer(@NonNull InventoryItemViewHolder holder) {
        if (saveTask != null) return;
        if (mDatabase.delete(ItemTable.NAME, InventoryDatabase.ID + " = ?", new String[] { String.valueOf(holder.getId()) }) > 0) {
            containerCount--;
            lastItemBarcode = getLastItemBarcode();
            itemRecyclerAdapter.changeCursor(queryItems());
            changedSinceLastArchive = true;
            updateInfo();
        } else {
            vibrate(300);
            Cursor cursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.w(TAG, "Error removing container \"" + barcode +"\" from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing container " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    private void addBarcodeLocation(@NonNull String barcode, String tags) {
        if (saveTask != null) return;
        ContentValues newLocation = new ContentValues();
        newLocation.put(InventoryDatabase.BARCODE, barcode);
        newLocation.put(InventoryDatabase.DESCRIPTION, "");
        newLocation.put(InventoryDatabase.TAGS, tags);
        newLocation.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        long rowID = mDatabase.insert(LocationTable.NAME, null, newLocation);

        if (rowID == -1) {
            vibrate(300);
            Log.w(TAG, "Error adding location \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        locationRecyclerAdapter.changeCursor(queryLocations());

        int selectedLocation = -1;
        int barcodeIndex = locationRecyclerAdapter.getCursor().getColumnIndex("barcode");
        locationRecyclerAdapter.getCursor().moveToFirst();

        while (!locationRecyclerAdapter.getCursor().isAfterLast()) {
            if (locationRecyclerAdapter.getCursor().getString(barcodeIndex).equals(barcode))
                selectedLocation = locationRecyclerAdapter.getCursor().getPosition();
            locationRecyclerAdapter.getCursor().moveToNext();
        }

        locationRecyclerView.setSelectedItem(selectedLocation);
        changedSinceLastArchive = true;


        if (!lastLocationBarcode.equals(barcode)) {
            lastLocationId = rowID;
            lastLocationBarcode = barcode;
            itemRecyclerAdapter.changeCursor(queryItems());
            itemRecyclerView.setSelectedItem(-1);
        }

        updateInfo();
    }

    private Cursor queryLocations() {
        return mDatabase.rawQuery("SELECT MAX(" + LocationTable.Keys.ID + ") AS _id, MIN(" + LocationTable.Keys.ID + ") AS min_id, " + LocationTable.Keys.BARCODE + " AS barcode, MAX(" + LocationTable.Keys.DESCRIPTION + ") AS description, MAX(" + LocationTable.Keys.TAGS + ") AS tags, MAX(" + LocationTable.Keys.DATE_TIME + ") AS date_time FROM " + LocationTable.NAME + " GROUP BY barcode ORDER BY min_id", null);
    }

    private Cursor queryItems() {
        return mDatabase.rawQuery("SELECT * FROM ( SELECT " + ItemTable.Keys.ID + " AS _id, " + ItemTable.Keys.ID + " AS min_id, " + ItemTable.Keys.LOCATION_ID + " AS location_id, " + ItemTable.Keys.BARCODE + " AS barcode, " + ItemTable.Keys.DESCRIPTION + " AS description, " + ItemTable.Keys.TAGS + " AS tags, " + ItemTable.Keys.DATE_TIME + " AS date_time FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " AS _id FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? ) ) WHERE _id NOT NULL ORDER BY _id", new String[] { lastLocationBarcode });
    }

    private long getLastLocationId() {
        try {
            return LAST_LOCATION_ID_STATEMENT.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            return -1;
        }
    }

    private String getLastLocationBarcode() {
        try {
            return LAST_LOCATION_BARCODE_STATEMENT.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            return "-";
        }
    }

    private String getLastItemBarcode() {
        try {
            return LAST_ITEM_BARCODE_STATEMENT.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            return "-";
        }
    }

    private CharSequence formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    private boolean isItem(@NonNull String barcode) {
        return barcode.startsWith("e1") || barcode.startsWith("E");// || barcode.startsWith("t") || barcode.startsWith("T");
    }

    private boolean isContainer(@NonNull String barcode) {
        return barcode.startsWith("m1") || barcode.startsWith("M");// || barcode.startsWith("a") || barcode.startsWith("A");
    }

    private boolean isLocation(@NonNull String barcode) {
        return barcode.startsWith("V");// || barcode.startsWith("L5");
    }

    private class InventoryLocationViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;
        private View background;
        private long id = -1;
        private long minId = -1;
        private String barcode = "";
        private String description = "";
        private String tags = "";
        private String dateTime = "";
        private boolean isSelected = false;

        private InventoryLocationViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.location_text_view);
            background = itemView.findViewById(R.id.location_background);
            itemView.setClickable(true);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addBarcodeLocation(barcode, "");
                }
            });
        }

        private void bindViews(Cursor cursor, int selectedPosition) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            minId = cursor.getLong(cursor.getColumnIndex("min_id"));
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            description = cursor.getString(cursor.getColumnIndex("description"));
            tags = cursor.getString(cursor.getColumnIndex("tags"));
            dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == selectedPosition;

            itemView.setSelected(isSelected);

            background.setBackground(ContextCompat.getDrawable(InventoryActivity.this, isSelected ? R.drawable.scanned_selected_location_background : R.drawable.scanned_deselected_location_background));
            textView.setText(barcode);
        }
    }

    private class InventoryItemViewHolder extends RecyclerView.ViewHolder {
        //private MaterialProgressBar progressBarWaiting;
        private TextView barcodeTextView;
        private ColorStateList itemBarcodeTextViewDefaultColor;
        private TextView itemLocationTextView;
        private View itemDividerView;
        private ImageButton expandedMenuButton;
        private long id = -1;
        private long locationId = -1;
        private String barcode = "";
        private String description = "";
        private String tags = "";
        private String dateTime = "";
        private boolean isSelected = false;

        InventoryItemViewHolder(final View itemView) {
            super(itemView);
            barcodeTextView = itemView.findViewById(R.id.barcode_text_view);
            itemBarcodeTextViewDefaultColor = barcodeTextView.getTextColors();
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupMenu popup = new PopupMenu(InventoryActivity.this, view);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.inventory_item_popup_menu, popup.getMenu());
                    popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            if (saveTask != null) {
                                Toast.makeText(InventoryActivity.this, "Cannot edit inventory while saving", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(InventoryActivity.this);
                            builder.setCancelable(true);
                            builder.setTitle("Remove " + (isItem(barcode) ? "Item" : "Container"));
                            builder.setMessage(String.format("Are you sure you want to remove item \"%s\"?", barcode));
                            builder.setNegativeButton("no", null);
                            builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //Log.d(TAG, "Removing " + (isItem(inventoryItemViewHolder.getItemBarcode()) ? "item" : "container") + " at position " + inventoryItemViewHolder.getAdapterPosition() + " with barcode " + inventoryItemViewHolder.getItemBarcode());

                                    if (isContainer(barcode))
                                        removeBarcodeContainer(InventoryItemViewHolder.this);

                                    if (isItem(barcode))
                                        removeBarcodeItem(InventoryItemViewHolder.this);
                                }
                            });
                            builder.create().show();

                            return true;
                        }
                    });
                    popup.show();
                }
            });
        }

        long getId() {
            return id;
        }

        void bindViews(final Cursor cursor, final boolean isSelected) {
            this.id = cursor.getLong(cursor.getColumnIndex("_id"));
            //this.locationId = cursor.getLong(cursor.getColumnIndex("location_id"));
            this.barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            this.description = cursor.getString(cursor.getColumnIndex("description"));
            this.tags = cursor.getString(cursor.getColumnIndex("tags"));
            this.dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            this.isSelected = isSelected;

            barcodeTextView.setText(barcode);

            if (isSelected)
                barcodeTextView.setTypeface(null, Typeface.BOLD);
            else
                barcodeTextView.setTypeface(null, Typeface.NORMAL);

            /*if (tags.contains(DUPLICATE_BARCODE_TAG))
                barcodeTextView.setTextColor(errorColor);
            else
                barcodeTextView.setTextColor(itemBarcodeTextViewDefaultColor);*/

        }
    }

    private class SaveToFileTask extends AsyncTask<Void, Float, String> {
        private static final int MAX_UPDATES = 100;

        protected String doInBackground(Void... voids) {
            Cursor itemCursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.DATE_TIME + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " ASC;",null);
            Cursor locationCursor = mDatabase.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " ASC;", null);

            itemCursor.moveToFirst();
            int itemBarcodeIndex = itemCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int itemLocationIdIndex = itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID);
            int itemDateTimeIndex = itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            locationCursor.moveToFirst();
            int locationIdIndex = locationCursor.getColumnIndex(InventoryDatabase.ID);
            int locationBarcodeIndex = locationCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int locationDateTimeIndex = locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            PrintStream printStream = null;

            try {
                //noinspection ResultOfMethodCallIgnored
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("tmp", ".txt", OUTPUT_PATH);

                int totalItemCount = itemCursor.getCount() + 1;
                int currentLocationId = -1;

                printStream = new PrintStream(TEMP_OUTPUT_FILE);
                //Cursor locationCursor;

                int tempLocation;
                int itemIndex = 0;
                int updateNum = 0;

                //
                printStream.print(BuildConfig.APPLICATION_ID.split("\\.")[2] + "|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n");
                printStream.flush();
                //

                while (!itemCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    final float tempProgress = ((float) itemIndex) / totalItemCount;
                    if (tempProgress * MAX_UPDATES > updateNum) {
                        publishProgress(tempProgress);
                        updateNum++;
                    }

                    tempLocation = itemCursor.getInt(itemLocationIdIndex);

                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                return "Location of \"" + itemCursor.getString(itemBarcodeIndex).trim() + "\" does not exist";
                            }
                        }

                        printStream.printf("\"%1s\"|\"%2s\"\r\n", locationCursor.getString(locationBarcodeIndex), locationCursor.getString(locationDateTimeIndex));
                        printStream.flush();
                    }

                    printStream.printf("\"%1s\"|\"%2s\"\r\n", itemCursor.getString(itemBarcodeIndex), itemCursor.getString(itemDateTimeIndex));
                    printStream.flush();

                    itemCursor.moveToNext();
                    itemIndex++;
                }

                if (outputFile.exists() && !outputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not delete existing output file");
                    return "Could not delete existing output file";
                }

                refreshExternalPath();

                if (!TEMP_OUTPUT_FILE.renameTo(outputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not rename temp file to \"" + outputFile.getName() + "\"");
                    return "Could not rename temp file to \"" + outputFile.getName() + "\"";
                }

                refreshExternalPath();
            } catch (FileNotFoundException e){
                e.printStackTrace();
                return "FileNotFoundException occurred while saving";
            } catch (IOException e){
                e.printStackTrace();
                return "IOException occurred while saving";
            } finally {
                if (printStream != null)
                    printStream.close();
                itemCursor.close();
                locationCursor.close();
            }

            //Log.v(TAG, "Saved to: " + outputFile.getAbsolutePath());
            return "Saved to file";
        }

        @Override
        protected void onProgressUpdate(Float... progress) {
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress(progress[0],true);
                progressBar.animate();
            } else*/ {
                progressBar.setProgress((int) (progress[0] * progressBar.getMax()));
            }
        }

        protected void onPostExecute(String result) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(InventoryActivity.this, result, Toast.LENGTH_SHORT).show();
            if (changedSinceLastArchive)
                archiveDatabase();
            postSave();
            //MediaScannerConnection.scanFile(InventoryActivity.this, new String[]{outputFile.getAbsolutePath()}, null, null);
        }

        @Override
        protected void onCancelled(String s) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(InventoryActivity.this, s, Toast.LENGTH_SHORT).show();
            postSave();
        }
    }

    private void refreshExternalPath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(OUTPUT_PATH);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } else {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(OUTPUT_PATH)));
        }
    }

    private void archiveDatabase() {
        //noinspection ResultOfMethodCallIgnored
        //archiveDirectory.mkdirs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (saveTask == null) {
                            preSave();
                            archiveDatabase();
                            (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                            saveTask = new SaveToFileTask().execute();
                        } else {
                            saveTask.cancel(false);
                            postSave();
                        }
                    } else if (requestCode == 2) {
                        archiveDirectory = new File(getFilesDir() + "/" + InventoryDatabase.ARCHIVE_DIRECTORY);
                        //noinspection ResultOfMethodCallIgnored
                        archiveDirectory.mkdirs();
                        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "data.txt");
                        //noinspection ResultOfMethodCallIgnored
                        outputFile.getParentFile().mkdirs();
                        databaseFile = new File(getFilesDir() + "/" + InventoryDatabase.DIRECTORY + "/" + InventoryDatabase.FILE_NAME);
                        databaseFile = new File(OUTPUT_PATH, InventoryDatabase.FILE_NAME);
                        //noinspection ResultOfMethodCallIgnored
                        databaseFile.getParentFile().mkdirs();

                        try {
                            initialize();
                        } catch (SQLiteCantOpenDatabaseException e) {
                            try {
                                //System.out.println(databaseFile.exists());
                                if (databaseFile.renameTo(File.createTempFile("error", ".db", new File(databaseFile.getParent(), InventoryDatabase.ARCHIVE_DIRECTORY)))) {
                                    Toast.makeText(this, "There was an error loading the inventory file. It has been archived", Toast.LENGTH_SHORT).show();
                                } else {
                                    databaseLoadError();
                                }
                            } catch (IOException e1) {
                                e1.printStackTrace();
                                databaseLoadError();
                            }
                        }
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (iScanner != null) {
                try {
                    iScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;

                    if (barcode.equals("")) {
                        Toast.makeText(InventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                    } else if (!barcode.equals("SCAN AGAIN")) {
                        scanBarcode(barcode);
                    }
                    //System.out.println("symName: " + mDecodeResult.symName);
                    //System.out.println("decodeValue: " + mDecodeResult.decodeValue);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
