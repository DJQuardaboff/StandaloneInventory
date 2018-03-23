package com.porterlee.standardinventory;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

public class SelectableRecyclerView extends RecyclerView {
    private int selectedItem;

    public SelectableRecyclerView(Context context) {
        super(context);
    }

    public SelectableRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectableRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean setSelectedItem(int index) {
        if (index >= getAdapter().getItemCount() || index == selectedItem)
            return false;

        getAdapter().notifyItemChanged(selectedItem);
        selectedItem = index;
        getAdapter().notifyItemChanged(selectedItem);
        scrollToPosition(index < 0 ? 0 : index);
        return true;
    }

    public int getSelectedItem() {
        return selectedItem;
    }
}
