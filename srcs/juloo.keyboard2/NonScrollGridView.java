package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.GridView;

/** A non-scrollable grid that expands to its full content height so it can
    live inside the clipboard pane's outer ScrollView. */
public class NonScrollGridView extends GridView
{
  public NonScrollGridView(Context context)
  {
    super(context);
  }

  public NonScrollGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public NonScrollGridView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int expandedHeight = MeasureSpec.makeMeasureSpec(
        Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
    super.onMeasure(widthMeasureSpec, expandedHeight);
    ViewGroup.LayoutParams params = getLayoutParams();
    if (params != null)
      params.height = getMeasuredHeight();
  }
}
