package jp.ac.titech.itpro.sdl.expensesmemo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AutoCompleteTextView;

public class MyAutoCompleteTextView extends AutoCompleteTextView {
    public MyAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setThreshold(0);
    }
    public MyAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setThreshold(0);
    }
    public MyAutoCompleteTextView(Context context) {
        super(context);
        this.setThreshold(0);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        showDropDown();
        return super.onTouchEvent(event);
    }
}
