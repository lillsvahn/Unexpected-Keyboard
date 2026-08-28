package juloo.keyboard2;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.content.Intent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The touchpad UI shown in the same input area as Unexpected Keyboard. */
public class FoldMousePane extends LinearLayout
{
  private final Keyboard2 _host;
  private final View _keyboardView;
  private final TextView _pad;

  private float _lastTouchX = 0f;
  private float _lastTouchY = 0f;
  private final float _sensitivity = 1.6f;

  private float _padDownX = 0f;
  private float _padDownY = 0f;
  private boolean _padMoved = false;
  private int _padMaxPointers = 0;
  private long _padDownTime = 0L;

  public FoldMousePane(Keyboard2 host, View keyboardView)
  {
    super(host);
    _host = host;
    _keyboardView = keyboardView;

    setOrientation(VERTICAL);
    setPadding(dp(8), dp(8), dp(8), dp(8));
    setBackground(roundedBackground(Color.rgb(28, 28, 30), 18));
    setMotionEventSplittingEnabled(true);
    setLayoutParams(new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
    setMinimumHeight(dp(260));

    LinearLayout contentRow = new LinearLayout(host);
    contentRow.setOrientation(HORIZONTAL);
    contentRow.setGravity(Gravity.CENTER);
    contentRow.setMotionEventSplittingEnabled(true);

    _pad = new TextView(host);
    _pad.setTextSize(17f);
    _pad.setTextColor(Color.LTGRAY);
    _pad.setGravity(Gravity.CENTER);
    _pad.setBackground(roundedBackground(Color.rgb(48, 48, 52), 14));
    _pad.setClickable(true);
    _pad.setOnTouchListener(this::handlePadTouch);

    TextView scrollStrip = new TextView(host);
    scrollStrip.setText("▲\n3×\n▼");
    scrollStrip.setTextSize(12f);
    scrollStrip.setTextColor(Color.WHITE);
    scrollStrip.setGravity(Gravity.CENTER);
    scrollStrip.setContentDescription("Live scroll, triple speed");
    scrollStrip.setBackground(roundedBackground(Color.rgb(62, 62, 68), 14));
    scrollStrip.setClickable(true);
    scrollStrip.setOnTouchListener((view, event) -> {
      FoldMouseAccessibilityService mouse = mouse();
      if (mouse == null)
        return true;

      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          mouse.beginLiveScroll(event.getY());
          return true;
        case MotionEvent.ACTION_MOVE:
          mouse.updateLiveScroll(event.getY());
          return true;
        case MotionEvent.ACTION_UP:
          mouse.endLiveScroll();
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          mouse.cancelLiveScroll();
          return true;
        default:
          return true;
      }
    });

    LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(
      0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    padParams.setMarginEnd(dp(8));
    contentRow.addView(_pad, padParams);
    contentRow.addView(scrollStrip, new LinearLayout.LayoutParams(
      dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout buttonRow = new LinearLayout(host);
    buttonRow.setOrientation(HORIZONTAL);
    buttonRow.setGravity(Gravity.CENTER);
    buttonRow.setMotionEventSplittingEnabled(true);

    Button lmb = new Button(host);
    lmb.setText("LMB");
    lmb.setOnTouchListener((view, event) -> {
      FoldMouseAccessibilityService mouse = mouse();
      if (mouse == null)
        return true;

      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          lmb.setText("LMB LIVE");
          mouse.beginLiveLmb();
          return true;
        case MotionEvent.ACTION_UP:
          mouse.endLiveLmb();
          lmb.setText("LMB");
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          mouse.cancelLiveLmb();
          lmb.setText("LMB");
          return true;
        default:
          return true;
      }
    });

    Button rmb = new Button(host);
    rmb.setText("RMB");
    rmb.setOnClickListener(v -> {
      FoldMouseAccessibilityService mouse = mouse();
      if (mouse != null)
        mouse.rightClickAtCursor();
    });

    Button keyboard = new Button(host);
    keyboard.setText("⌨");
    keyboard.setContentDescription("Back to keyboard");
    keyboard.setOnClickListener(v -> {
      onHidden();
      _host.setInputView(_keyboardView);
    });

    buttonRow.addView(lmb, new LinearLayout.LayoutParams(0, dp(58), 1f));
    buttonRow.addView(rmb, new LinearLayout.LayoutParams(0, dp(58), 1f));
    buttonRow.addView(keyboard, new LinearLayout.LayoutParams(0, dp(58), 1f));

    addView(contentRow, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    addView(buttonRow, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    refreshStatus();
  }

  public void onShown()
  {
    FoldMouseAccessibilityService mouse = mouse();
    if (mouse != null)
      mouse.showCursor();
    refreshStatus();
  }

  public void onHidden()
  {
    FoldMouseAccessibilityService mouse = mouse();
    if (mouse != null)
      mouse.hideCursor();
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

        FoldMouseAccessibilityService mouse = mouse();
        if (mouse != null && event.getPointerCount() == 1 && _padMaxPointers == 1)
        {
          float dx = (event.getX() - _lastTouchX) * _sensitivity;
          float dy = (event.getY() - _lastTouchY) * _sensitivity;
          _lastTouchX = event.getX();
          _lastTouchY = event.getY();
          mouse.moveCursorBy(dx, dy);
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
        FoldMouseAccessibilityService mouse = mouse();

        if (mouse == null)
        {
          if (isTap)
            openAccessibilitySettings();
        }
        else if (isTap)
        {
          if (_padMaxPointers >= 2)
            mouse.rightClickAtCursor();
          else
            mouse.tapAtCursor();
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

  private FoldMouseAccessibilityService mouse()
  {
    return FoldMouseAccessibilityService.getInstance();
  }

  private void refreshStatus()
  {
    if (FoldMouseAccessibilityService.isRunning())
      _pad.setText("TOUCHPAD\nTap = LMB   •   2 fingers = RMB");
    else
      _pad.setText("TOUCHPAD\nTap here to enable FoldMouse accessibility");
  }

  private void openAccessibilitySettings()
  {
    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    _host.startActivity(intent);
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
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
