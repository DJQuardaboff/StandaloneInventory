package com.porterlee.mobileinventory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/invinfo.txt");
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    //private ArrayList<Pair<ContentValues, Boolean>> items = new ArrayList<>();
    //private ArrayList<HashMap<String, Object>> photos = new ArrayList<>();
    private ArrayList<Animation> animations = new ArrayList<>();
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = SQLiteDatabase.openOrCreateDatabase(getFilesDir() + "/" + ScannedItemsDatabase.FILE_NAME, null);
        //db.execSQL("DROP TABLE barcodes");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedItemsDatabase.BarcodesTable.TABLE_CREATION);

        Cursor cursor = db.rawQuery("SELECT * FROM " + ScannedItemsDatabase.BarcodesTable.NAME,null);

        cursor.moveToFirst();

        /*final int idIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.ID);
        final int barcodeIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE);
        final int descriptionIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION);

        while (!cursor.isAfterLast()) {
            ContentValues values = new ContentValues();
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.ID, cursor.getLong(idIndex));
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE, cursor.getString(barcodeIndex));
            values.put(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION, cursor.getString(descriptionIndex));
            items.add(new Pair<>(values, true));
            cursor.moveToNext();
        }*/

        cursor.close();

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                //return items.get(i).first.getAsLong(ScannedItemsDatabase.BarcodesTable.Keys.ID);
                return -1;
            }

            @Override
            public int getItemCount() {
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ScannedItemsDatabase.BarcodesTable.NAME,null);
                cursor.moveToFirst();
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
                //final SimpleViewHolder simpleHolder = new SimpleViewHolder(itemLayoutView);
                //return simpleHolder;
                return new SimpleViewHolder(itemLayoutView);
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                ((SimpleViewHolder) holder).expandedMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(MainActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().getItem(2).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                removeBarcodeItem(holder.getAdapterPosition());
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                Cursor cursor = db.rawQuery("SELECT * FROM " + ScannedItemsDatabase.BarcodesTable.NAME + " LIMIT 1 OFFSET " + position,null);
                ((SimpleViewHolder) holder).bindViews(cursor);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerView.setItemAnimator(new DefaultItemAnimator());
        /*itemRecyclerView.setItemAnimator(new RecyclerView.ItemAnimator() {
            @Override
            public boolean animateDisappearance(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo) {
                /*final RecyclerView.ViewHolder finalHolder = viewHolder;
                Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.slide_out_left);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) { }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dispatchAnimationFinished(finalHolder);
                        animations.remove(animation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
                viewHolder.itemView.startAnimation(animation);
                animations.add(animation);*//*
                dispatchAnimationFinished(viewHolder);
                return false;
            }

            @Override
            public boolean animateAppearance(@NonNull RecyclerView.ViewHolder viewHolder, @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
                final RecyclerView.ViewHolder finalHolder = viewHolder;
                Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.slide_in_right);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) { }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dispatchAnimationFinished(finalHolder);
                        animations.remove(animation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
                viewHolder.itemView.startAnimation(animation);
                animations.add(animation);
                dispatchAnimationFinished(viewHolder);
                return false;
            }

            @Override
            public boolean animatePersistence(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
                //System.out.println("animatePersistence: " + viewHolder.getAdapterPosition());
                dispatchAnimationFinished(viewHolder);
                return false;
            }

            @Override
            public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
                if (oldHolder != newHolder) {
                    dispatchAnimationFinished(newHolder);
                }

                final SimpleViewHolder simpleHolder = (SimpleViewHolder) newHolder;
                Animation animation = AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in);
                if (preLayoutInfo.top > postLayoutInfo.top)
                    animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.slide_up);
                else if (preLayoutInfo.top < postLayoutInfo.top)
                    animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.slide_down);

                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) { }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dispatchAnimationFinished(simpleHolder);
                        simpleHolder.setIsRecyclable(true);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
                simpleHolder.setIsRecyclable(false);
                simpleHolder.itemView.startAnimation(animation);
                dispatchAnimationFinished(oldHolder);
                return true;
            }

            @Override
            public void runPendingAnimations() {
                //System.out.println("runPendingAnimations");
            }

            @Override
            public void endAnimation(RecyclerView.ViewHolder item) {
                //System.out.println("endAnimation");
                item.itemView.getAnimation().cancel();
            }

            @Override
            public void endAnimations() {
                for (int i = 0; i < animations.size(); i++){
                    animations.get(0).cancel();
                }
                //System.out.println("endAnimations");
            }

            @Override
            public boolean isRunning() {
                //System.out.println("isRunning");
                return true;
                //return animations.size() > 0;
            }
        });*/
        /*itemRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                System.out.println("attaching " + view);
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                view.getAnimation().cancel();
                System.out.println("detaching");
            }
        });*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete_sweep:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean removeBarcodeItem(int index) {
        if (db.delete(ScannedItemsDatabase.BarcodesTable.NAME, ScannedItemsDatabase.BarcodesTable.Keys.ID + " in ( SELECT " + ScannedItemsDatabase.BarcodesTable.Keys.ID + " FROM " + ScannedItemsDatabase.BarcodesTable.NAME + " LIMIT 1 OFFSET " + index + ")", null) > 0) {
            //itemRecyclerAdapter.notifyDataSetChanged();
            //items.remove(index);
            itemRecyclerAdapter.notifyItemRemoved(index);
            itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount() - index);
        } else return false;
        return true;
    }

    public boolean addBarcodeItem(int index, @NonNull String barcode, @Nullable String description) {
        ContentValues values = new ContentValues();
        values.put(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE, barcode);
        values.put(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION, description);

        if (db.insert(ScannedItemsDatabase.BarcodesTable.NAME, null, values) == -1) return false;
        Cursor cursor = db.rawQuery("SELECT * FROM " + ScannedItemsDatabase.BarcodesTable.NAME + " ORDER BY " + ScannedItemsDatabase.BarcodesTable.Keys.ID + " DESC LIMIT 1;", null);
        cursor.moveToFirst();
        //values.put(ScannedItemsDatabase.BarcodesTable.Keys.ID, cursor.getInt(cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.ID)));
        cursor.close();

        //items.add(index, new Pair<>(values, true));
        itemRecyclerAdapter.notifyItemInserted(index);
        itemRecyclerAdapter.notifyItemRangeChanged(index, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);
        return true;
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

    public void randomScan(View view) {
        addBarcodeItem(0, "tnyc000" + new Random().nextInt(0xffff), null);
    }

    class SimpleViewHolder extends RecyclerView.ViewHolder {
        private ProgressBar progressLoading;
        private TextView itemBarcode;
        private TextView itemDescription;
        private ImageButton expandedMenu;

        SimpleViewHolder(final View itemView) {
            super(itemView);
            progressLoading = itemView.findViewById(R.id.progress_loading);
            itemBarcode = itemView.findViewById(R.id.item_barcode);
            itemDescription = itemView.findViewById(R.id.item_description);
            expandedMenu = itemView.findViewById(R.id.menu_button);
        }

        void bindViews(Cursor cursor) {
            cursor.moveToFirst();
            final int barcodeIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE);
            final int descriptionIndex = cursor.getColumnIndex(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION);
            //String barcode = values.getAsString(ScannedItemsDatabase.BarcodesTable.Keys.BARCODE);
            //String description = values.getAsString(ScannedItemsDatabase.BarcodesTable.Keys.DESCRIPTION);
            String barcode = cursor.getString(barcodeIndex);
            String description = cursor.getString(descriptionIndex);
            cursor.close();
            //if (ready) {
                progressLoading.setVisibility(View.GONE);
                if (barcode != null) {
                    itemBarcode.setText(barcode);
                    itemBarcode.setVisibility(View.VISIBLE);
                } else itemBarcode.setVisibility(View.GONE);
                if (description != null) {
                    itemDescription.setText(description);
                    itemDescription.setVisibility(View.VISIBLE);
                } else itemDescription.setVisibility(View.GONE);
                expandedMenu.setVisibility(View.VISIBLE);
            /*} else {
                progressLoading.setVisibility(View.VISIBLE);
                itemBarcode.setVisibility(View.GONE);
                itemDescription.setVisibility(View.GONE);
                expandedMenu.setVisibility(View.GONE);
            }*/
        }
    }
}

