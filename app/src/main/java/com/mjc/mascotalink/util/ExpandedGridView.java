package com.mjc.mascotalink.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.GridView;

/**
 * A GridView that automatically expands to show all its items without scrolling.
 * This is useful when placing a GridView inside a ScrollView.
 */
public class ExpandedGridView extends GridView {

    public ExpandedGridView(Context context) {
        super(context);
    }

    public ExpandedGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpandedGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate the height based on the content. This is the key to preventing the GridView
        // from collapsing inside a ScrollView.
        // The right shift by 2 is a standard practice to prevent integer overflow with large values.
        int heightSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightSpec);
        getLayoutParams().height = getMeasuredHeight();
    }
}
