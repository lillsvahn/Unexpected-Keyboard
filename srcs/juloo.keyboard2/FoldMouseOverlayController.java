package juloo.keyboard2;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Standalone FoldMouse touchpad shown as an accessibility overlay.
 *
 * This is used when Android has hidden the IME because no text field is active.
 * The gesture engine itself still lives in FoldMouseAccessibilityService.
 */
class FoldMouseOverlayController
{
  private final FoldMouseAccessibilityService _mouse;
  private final WindowManager _windowManager;

  private LinearLayout _root;

  private float _lastTouchX = 0f;
  private float _lastTouchY = 0f;
  private final float _sensitivity = 1.6f;

  private float _padDownX = 0f;
  private float _padDownY = 0f;
  private boolean _padMoved = false;
  private int _padMaxPointers = 0;
  private long _padDownTime = 0L;

  FoldMouseOverlayController(FoldMouseAccessibilityService mouse)
  {
    _mouse = mouse;
    _windowManager = (WindowManager)mouse.getSystemService(
      FoldMouseAccessibilityService.WINDOW_SERVICE);
  }

  boolean isVisible()
  {
    return _root != null;
  }

  void toggle()
  {
    if (isVisible())
      hide();
    else
      show();
  }

  void show()
  {
    if (_root != null || _windowManager == null)
      return;

    _mouse.showCursor();

    LinearLayout root = new LinearLayout(_mouse);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(8), dp(8), dp(8), dp(8));
    root.setBackground(roundedBackground(Color.rgb(28, 28, 30), 18));
    root.setMotionEventSplittingEnabled(true);

    LinearLayout contentRow = new LinearLayout(_mouse);
    contentRow.setOrientation(LinearLayout.HORIZONTAL);
    contentRow.setGravity(Gravity.CENTER);
    contentRow.setMotionEventSplittingEnabled(true);

    TextView pad = new TextView(_mouse);
    pad.setText("TOUCHPAD\nTap = LMB   •   2 fingers = RMB");
    pad.setTextSize(17f);
    pad.setTextColor(Color.LTGRAY);
    pad.setGravity(Gravity.CENTER);
    pad.setBackground(roundedBackground(Color.rgb(48, 48, 52), 14));
    pad.setClickable(true);
    pad.setOnTouchListener(this::handlePadTouch);

    TextView scrollStrip = new TextView(_mouse);
    scrollStrip.setText("▲\n3×\n▼");
    scrollStrip.setTextSize(12f);
    scrollStrip.setTextColor(Color.WHITE);
    scrollStrip.setGravity(Gravity.CENTER);
    scrollStrip.setContentDescription("Live scroll, triple speed");
    scrollStrip.setBackground(roundedBackground(Color.rgb(62, 62, 68), 14));
    scrollStrip.setClickable(true);
    scrollStrip.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          _mouse.beginLiveScroll(event.getY());
          return true;
        case MotionEvent.ACTION_MOVE:
          _mouse.updateLiveScroll(event.getY());
          return true;
        case MotionEvent.ACTION_UP:
          _mouse.endLiveScroll();
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          _mouse.cancelLiveScroll();
          return true;
        default:
          return true;
      }
    });

    LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(
      0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    padParams.setMarginEnd(dp(8));
    contentRow.addView(pad, padParams);
    contentRow.addView(scrollStrip, new LinearLayout.LayoutParams(
      dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout buttonRow = new LinearLayout(_mouse);
    buttonRow.setOrientation(LinearLayout.HORIZONTAL);
    buttonRow.setGravity(Gravity.CENTER);
    buttonRow.setMotionEventSplittingEnabled(true);

    Button lmb = new Button(_mouse);
    lmb.setText("LMB");
    lmb.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          lmb.setText("LMB LIVE");
          _mouse.beginLiveLmb();
          return true;
        case MotionEvent.ACTION_UP:
          _mouse.endLiveLmb();
          lmb.setText("LMB");
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          _mouse.cancelLiveLmb();
          lmb.setText("LMB");
          return true;
        default:
          return true;
      }
    });

    Button rmb = new Button(_mouse);
    rmb.setText("RMB");
    rmb.setOnClickListener(v -> _mouse.rightClickAtCursor());

    Button close = new Button(_mouse);
    close.setText("×");
    close.setTextSize(24f);
    close.setContentDescription("Close FoldMouse overlay");
    close.setOnClickListener(v -> hide());

    buttonRow.addView(lmb, new LinearLayout.LayoutParams(0, dp(58), 1f));
    buttonRow.addView(rmb, new LinearLayout.LayoutParams(0, dp(58), 1f));
    buttonRow.addView(close, new LinearLayout.LayoutParams(0, dp(58), 1f));

    root.addView(contentRow, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(buttonRow, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      dp(260),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.BOTTOM;

    try
    {
      _windowManager.addView(root, params);
      _root = root;
    }
    catch (Exception ignored)
    {
      _mouse.hideCursor();
    }
  }

  void hide()
  {
    if (_root != null && _windowManager != null)
    {
      try
      {
        _windowManager.removeView(_root);
      }
      catch (Exception ignored)
      {
      }
    }
    _root = null;
    resetPadGesture();
    _mouse.hideCursor();
  }

  void destroy()
  {
    hide();
  }

  private boolean handlePadTouch(View view, MotionEvent event)
  {
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        _padDownX = event.getX();
        _padDownY = event.getY();
        _lastTouchX = event.getX();
        _lastTouchY = event.getY();
        _padMoved = false;
        _padMaxPointers = 1;
        _padDownTime = event.getEventTime();
        return true;

      case MotionEvent.ACTION_POINTER_DOWN:
        _padMaxPointers = Math.max(_padMaxPointers, event.getPointerCount());
        return true;

      case MotionEvent.ACTION_MOVE:
      {
        _padMaxPointers = Math.max(_padMaxPointers, event.getPointerCount());
        float distance = (float)Math.hypot(
          event.getX() - _padDownX,
          event.getY() - _padDownY);
        if (distance > dp(9))
          _padMoved = true;

        if (event.getPointerCount() == 1 && _padMaxPointers == 1)
        {
          float dx = (event.getX() - _lastTouchX) * _sensitivity;
          float dy = (event.getY() - _lastTouchY) * _sensitivity;
          _lastTouchX = event.getX();
          _lastTouchY = event.getY();
          _mouse.moveCursorBy(dx, dy);
        }
        return true;
      }

      case MotionEvent.ACTION_POINTER_UP:
        _padMaxPointers = Math.max(_padMaxPointers, event.getPointerCount());
        return true;

      case MotionEvent.ACTION_UP:
      {
        long tapDuration = event.getEventTime() - _padDownTime;
        boolean isTap = !_padMoved && tapDuration <= 450L;
        if (isTap)
        {
          if (_padMaxPointers >= 2)
            _mouse.rightClickAtCursor();
          else
            _mouse.tapAtCursor();
        }

        view.performClick();
        resetPadGesture();
        return true;
      }

      case MotionEvent.ACTION_CANCEL:
        resetPadGesture();
        return true;

      default:
        return true;
    }
  }

  private void resetPadGesture()
  {
    _padDownX = 0f;
    _padDownY = 0f;
    _padMoved = false;
    _padMaxPointers = 0;
    _padDownTime = 0L;
  }

  private GradientDrawable roundedBackground(int color, int radiusDp)
  {
    GradientDrawable background = new GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(dp(radiusDp));
    return background;
  }

  private int dp(int value)
  {
    return Math.round(value * _mouse.getResources().getDisplayMetrics().density);
  }
}
