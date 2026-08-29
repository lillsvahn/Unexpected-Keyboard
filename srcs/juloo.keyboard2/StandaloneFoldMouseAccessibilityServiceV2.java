package juloo.keyboard2;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small standalone FoldMouse overlay.
 *
 * Cursor coordinates represent the overlay's top-left corner, but movement is
 * clamped by the cursor HOTSPOT rather than the whole cursor image. This lets
 * the pointer tip reach every screen edge while the unused part of the cursor
 * image is allowed to extend off-screen.
 */
public class StandaloneFoldMouseAccessibilityServiceV2 extends AccessibilityService
{
  private WindowManager _windowManager;

  private LinearLayout _padWindow;
  private WindowManager.LayoutParams _padWindowParams;
  private float _dragStartRawX;
  private float _dragStartRawY;
  private int _dragStartWindowX;
  private int _dragStartWindowY;

  private MouseCursorView _cursorView;
  private float _cursorX = 400f;
  private float _cursorY = 400f;
  private boolean _cursorInitialized = false;

  private float _lastTouchX;
  private float _lastTouchY;
  private float _padDownX;
  private float _padDownY;
  private boolean _padMoved;
  private long _padDownTime;
  private static final float POINTER_SENSITIVITY = 1.6f;

  private boolean _lmbInputActive;
  private boolean _lmbFinishRequested;
  private boolean _lmbDispatchInFlight;
  private float _lmbSyntheticX;
  private float _lmbSyntheticY;
  private float _lmbTargetX;
  private float _lmbTargetY;
  private GestureDescription.StrokeDescription _lmbStroke;

  private AccessibilityButtonController _buttonController;
  private AccessibilityButtonController.AccessibilityButtonCallback _buttonCallback;

  @Override
  protected void onServiceConnected()
  {
    super.onServiceConnected();
    _windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);

    if (Build.VERSION.SDK_INT >= 26)
    {
      _buttonController = getAccessibilityButtonController();
      _buttonCallback = new AccessibilityButtonController.AccessibilityButtonCallback()
      {
        @Override
        public void onClicked(AccessibilityButtonController controller)
        {
          toggleFloatingPad();
        }
      };
      _buttonController.registerAccessibilityButtonCallback(_buttonCallback);
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event)
  {
  }

  @Override
  public void onInterrupt()
  {
  }

  @Override
  public void onDestroy()
  {
    if (Build.VERSION.SDK_INT >= 26 &&
        _buttonController != null && _buttonCallback != null)
      _buttonController.unregisterAccessibilityButtonCallback(_buttonCallback);

    hideFloatingPad();
    super.onDestroy();
  }

  private void toggleFloatingPad()
  {
    if (_padWindow == null)
      showFloatingPad();
    else
      hideFloatingPad();
  }

  private void showFloatingPad()
  {
    if (_padWindow != null || _windowManager == null)
      return;

    showCursor();

    final int windowWidth = dp(300);
    final int windowHeight = dp(240);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(6), dp(6), dp(6), dp(6));
    root.setBackground(roundedBackground(Color.rgb(28, 28, 30), 16));
    root.setMotionEventSplittingEnabled(true);

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setBackground(roundedBackground(Color.rgb(58, 58, 64), 12));

    TextView dragHandle = new TextView(this);
    dragHandle.setText("≡  FoldMouse");
    dragHandle.setTextColor(Color.WHITE);
    dragHandle.setTextSize(14f);
    dragHandle.setGravity(Gravity.CENTER_VERTICAL);
    dragHandle.setPadding(dp(10), 0, 0, 0);
    dragHandle.setContentDescription("Drag FoldMouse window");
    dragHandle.setOnTouchListener(this::handleWindowDrag);

    Button close = new Button(this);
    close.setText("×");
    close.setTextSize(20f);
    close.setMinWidth(0);
    close.setMinHeight(0);
    close.setPadding(0, 0, 0, 0);
    close.setContentDescription("Close FoldMouse floating pad");
    close.setOnClickListener(v -> hideFloatingPad());

    header.addView(dragHandle, new LinearLayout.LayoutParams(
      0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    header.addView(close, new LinearLayout.LayoutParams(
      dp(46), ViewGroup.LayoutParams.MATCH_PARENT));

    TextView pad = new TextView(this);
    pad.setText("TOUCHPAD");
    pad.setTextSize(16f);
    pad.setTextColor(Color.LTGRAY);
    pad.setGravity(Gravity.CENTER);
    pad.setBackground(roundedBackground(Color.rgb(48, 48, 52), 14));
    pad.setClickable(true);
    pad.setOnTouchListener(this::handlePadTouch);

    LinearLayout buttonRow = new LinearLayout(this);
    buttonRow.setOrientation(LinearLayout.HORIZONTAL);
    buttonRow.setGravity(Gravity.CENTER);
    buttonRow.setMotionEventSplittingEnabled(true);

    Button lmb = new Button(this);
    lmb.setText("LMB");
    lmb.setMinWidth(0);
    lmb.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          lmb.setText("LMB LIVE");
          beginLiveLmb();
          return true;
        case MotionEvent.ACTION_UP:
          endLiveLmb();
          lmb.setText("LMB");
          view.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          cancelLiveLmb();
          lmb.setText("LMB");
          return true;
        default:
          return true;
      }
    });

    Button rmb = new Button(this);
    rmb.setText("RMB");
    rmb.setMinWidth(0);
    rmb.setOnClickListener(v -> rightClickAtCursor());

    buttonRow.addView(lmb, new LinearLayout.LayoutParams(0, dp(52), 1f));
    buttonRow.addView(rmb, new LinearLayout.LayoutParams(0, dp(52), 1f));

    LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
    headerParams.setMargins(0, 0, 0, dp(6));
    root.addView(header, headerParams);

    LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    padParams.setMargins(0, 0, 0, dp(6));
    root.addView(pad, padParams);
    root.addView(buttonRow, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
      windowWidth,
      windowHeight,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.START;
    params.x = dp(16);
    params.y = Math.max(dp(40), metrics.heightPixels - windowHeight - dp(110));

    try
    {
      _windowManager.addView(root, params);
      _padWindow = root;
      _padWindowParams = params;
    }
    catch (Exception ignored)
    {
      hideCursor();
    }
  }

  private void hideFloatingPad()
  {
    if (_padWindow != null && _windowManager != null)
    {
      try
      {
        _windowManager.removeView(_padWindow);
      }
      catch (Exception ignored)
      {
      }
    }

    _padWindow = null;
    _padWindowParams = null;
    resetPadGesture();
    hideCursor();
  }

  private boolean handleWindowDrag(View view, MotionEvent event)
  {
    if (_padWindowParams == null || _windowManager == null || _padWindow == null)
      return true;

    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        _dragStartRawX = event.getRawX();
        _dragStartRawY = event.getRawY();
        _dragStartWindowX = _padWindowParams.x;
        _dragStartWindowY = _padWindowParams.y;
        return true;

      case MotionEvent.ACTION_MOVE:
      {
        int dx = Math.round(event.getRawX() - _dragStartRawX);
        int dy = Math.round(event.getRawY() - _dragStartRawY);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxX = Math.max(0, metrics.widthPixels - _padWindowParams.width);
        int maxY = Math.max(0, metrics.heightPixels - _padWindowParams.height);
        _padWindowParams.x = clampInt(_dragStartWindowX + dx, 0, maxX);
        _padWindowParams.y = clampInt(_dragStartWindowY + dy, 0, maxY);
        _windowManager.updateViewLayout(_padWindow, _padWindowParams);
        return true;
      }

      case MotionEvent.ACTION_UP:
        view.performClick();
        return true;

      default:
        return true;
    }
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
        _padDownTime = event.getEventTime();
        return true;

      case MotionEvent.ACTION_MOVE:
      {
        float distance = (float)Math.hypot(
          event.getX() - _padDownX,
          event.getY() - _padDownY);
        if (distance > dp(9))
          _padMoved = true;

        float dx = (event.getX() - _lastTouchX) * POINTER_SENSITIVITY;
        float dy = (event.getY() - _lastTouchY) * POINTER_SENSITIVITY;
        _lastTouchX = event.getX();
        _lastTouchY = event.getY();
        moveCursorBy(dx, dy);
        return true;
      }

      case MotionEvent.ACTION_UP:
      {
        long tapDuration = event.getEventTime() - _padDownTime;
        if (!_padMoved && tapDuration <= 450L)
          tapAtCursor();
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
    _padDownTime = 0L;
  }

  private void showCursor()
  {
    if (_cursorView != null || _windowManager == null)
      return;

    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    if (!_cursorInitialized)
    {
      _cursorX = metrics.widthPixels / 2f;
      _cursorY = metrics.heightPixels / 2f;
      _cursorInitialized = true;
    }

    MouseCursorView cursor = new MouseCursorView(this);
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
      dp(38),
      dp(48),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.START;
    params.x = Math.round(_cursorX);
    params.y = Math.round(_cursorY);

    try
    {
      _windowManager.addView(cursor, params);
      _cursorView = cursor;
    }
    catch (Exception ignored)
    {
    }
  }

  private void hideCursor()
  {
    finishAnyActiveGestures();

    if (_cursorView != null && _windowManager != null)
    {
      try
      {
        _windowManager.removeView(_cursorView);
      }
      catch (Exception ignored)
      {
      }
    }
    _cursorView = null;
  }

  private void moveCursorBy(float dx, float dy)
  {
    if (_cursorView == null)
      showCursor();
    if (_cursorView == null)
      return;

    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    float hotspotX = _cursorView.hotspotX;
    float hotspotY = _cursorView.hotspotY;

    // Keep only the actual click hotspot inside the display. The cursor image
    // itself may extend beyond an edge because its overlay uses NO_LIMITS.
    _cursorX = clamp(
      _cursorX + dx,
      -hotspotX,
      (metrics.widthPixels - 1f) - hotspotX);
    _cursorY = clamp(
      _cursorY + dy,
      -hotspotY,
      (metrics.heightPixels - 1f) - hotspotY);

    WindowManager.LayoutParams params =
      (WindowManager.LayoutParams)_cursorView.getLayoutParams();
    params.x = Math.round(_cursorX);
    params.y = Math.round(_cursorY);
    _windowManager.updateViewLayout(_cursorView, params);

    if (_lmbInputActive || _lmbStroke != null)
      updateLiveLmbTarget();
  }

  private PointF cursorHotspotOnScreen()
  {
    if (_cursorView == null)
      return new PointF(_cursorX, _cursorY);

    int[] location = new int[2];
    _cursorView.getLocationOnScreen(location);
    return new PointF(
      location[0] + _cursorView.hotspotX,
      location[1] + _cursorView.hotspotY);
  }

  private void tapAtCursor()
  {
    PointF hotspot = cursorHotspotOnScreen();
    Path path = new Path();
    path.moveTo(hotspot.x, hotspot.y);
    GestureDescription.StrokeDescription stroke =
      new GestureDescription.StrokeDescription(path, 0, 60);
    dispatchGesture(new GestureDescription.Builder()
      .addStroke(stroke).build(), null, null);
  }

  private void rightClickAtCursor()
  {
    PointF hotspot = cursorHotspotOnScreen();
    Path path = new Path();
    path.moveTo(hotspot.x, hotspot.y);
    GestureDescription.StrokeDescription stroke =
      new GestureDescription.StrokeDescription(path, 0, 650);
    dispatchGesture(new GestureDescription.Builder()
      .addStroke(stroke).build(), null, null);
  }

  private void beginLiveLmb()
  {
    if (Build.VERSION.SDK_INT < 26)
      return;

    resetLiveLmbState();
    PointF hotspot = cursorHotspotOnScreen();
    _lmbInputActive = true;
    _lmbSyntheticX = hotspot.x;
    _lmbSyntheticY = hotspot.y;
    _lmbTargetX = hotspot.x;
    _lmbTargetY = hotspot.y;

    Path path = new Path();
    path.moveTo(_lmbSyntheticX, _lmbSyntheticY);
    GestureDescription.StrokeDescription firstStroke =
      new GestureDescription.StrokeDescription(path, 0, 12, true);
    dispatchLiveLmbStroke(firstStroke, true);
  }

  private void updateLiveLmbTarget()
  {
    if (!_lmbInputActive && _lmbStroke == null)
      return;

    PointF hotspot = cursorHotspotOnScreen();
    _lmbTargetX = hotspot.x;
    _lmbTargetY = hotspot.y;
    pumpLiveLmb();
  }

  private void endLiveLmb()
  {
    if (!_lmbInputActive && _lmbStroke == null)
      return;

    _lmbInputActive = false;
    _lmbFinishRequested = true;
    updateLiveLmbTarget();
    pumpLiveLmb();
  }

  private void cancelLiveLmb()
  {
    if (!_lmbInputActive && _lmbStroke == null)
    {
      resetLiveLmbState();
      return;
    }
    _lmbInputActive = false;
    _lmbFinishRequested = true;
    pumpLiveLmb();
  }

  private void pumpLiveLmb()
  {
    if (_lmbDispatchInFlight || _lmbStroke == null)
      return;

    float dx = _lmbTargetX - _lmbSyntheticX;
    float dy = _lmbTargetY - _lmbSyntheticY;
    float distance = (float)Math.hypot(dx, dy);

    if (distance < 0.75f)
    {
      if (_lmbFinishRequested)
        dispatchFinalLmbRelease();
      return;
    }

    float maxSegment = dp(22);
    float scale = distance > maxSegment ? maxSegment / distance : 1f;
    float newX = _lmbSyntheticX + dx * scale;
    float newY = _lmbSyntheticY + dy * scale;

    float remaining = (float)Math.hypot(
      _lmbTargetX - newX,
      _lmbTargetY - newY);
    boolean finishing = _lmbFinishRequested && remaining < 0.75f;
    boolean willContinue = !finishing;

    Path path = new Path();
    path.moveTo(_lmbSyntheticX, _lmbSyntheticY);
    path.lineTo(newX, newY);

    long duration = Math.max(12L, Math.min(38L,
      12L + Math.round(Math.min(distance, maxSegment) * 0.45f)));

    GestureDescription.StrokeDescription nextStroke =
      _lmbStroke.continueStroke(path, 0, duration, willContinue);

    _lmbSyntheticX = newX;
    _lmbSyntheticY = newY;
    dispatchLiveLmbStroke(nextStroke, willContinue);
  }

  private void dispatchLiveLmbStroke(
    final GestureDescription.StrokeDescription stroke,
    final boolean willContinue)
  {
    _lmbDispatchInFlight = true;
    _lmbStroke = stroke;

    boolean accepted = dispatchGesture(
      new GestureDescription.Builder().addStroke(stroke).build(),
      new GestureResultCallback()
      {
        @Override
        public void onCompleted(GestureDescription gestureDescription)
        {
          _lmbDispatchInFlight = false;
          if (willContinue)
            pumpLiveLmb();
          else
            resetLiveLmbState();
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription)
        {
          _lmbDispatchInFlight = false;
          resetLiveLmbState();
        }
      },
      null);

    if (!accepted)
    {
      _lmbDispatchInFlight = false;
      resetLiveLmbState();
    }
  }

  private void dispatchFinalLmbRelease()
  {
    if (_lmbDispatchInFlight)
      return;
    if (_lmbStroke == null)
    {
      resetLiveLmbState();
      return;
    }

    Path path = new Path();
    path.moveTo(_lmbSyntheticX, _lmbSyntheticY);
    GestureDescription.StrokeDescription finalStroke =
      _lmbStroke.continueStroke(path, 0, 10, false);
    dispatchLiveLmbStroke(finalStroke, false);
  }

  private void finishAnyActiveGestures()
  {
    if (_lmbInputActive || _lmbStroke != null)
      endLiveLmb();
  }

  private void resetLiveLmbState()
  {
    _lmbInputActive = false;
    _lmbFinishRequested = false;
    _lmbDispatchInFlight = false;
    _lmbSyntheticX = 0f;
    _lmbSyntheticY = 0f;
    _lmbTargetX = 0f;
    _lmbTargetY = 0f;
    _lmbStroke = null;
  }

  private GradientDrawable roundedBackground(int color, int radiusDp)
  {
    GradientDrawable background = new GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(dp(radiusDp));
    return background;
  }

  private float clamp(float value, float min, float max)
  {
    return Math.max(min, Math.min(max, value));
  }

  private int clampInt(int value, int min, int max)
  {
    return Math.max(min, Math.min(max, value));
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private static class MouseCursorView extends View
  {
    private final float density;
    final float hotspotX;
    final float hotspotY;
    private final Paint fillPaint;
    private final Paint outlinePaint;
    private final Paint hotspotPaint;

    MouseCursorView(Context context)
    {
      super(context);
      density = context.getResources().getDisplayMetrics().density;
      hotspotX = 6f * density;
      hotspotY = 6f * density;

      fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      fillPaint.setColor(Color.BLACK);
      fillPaint.setStyle(Paint.Style.FILL);

      outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      outlinePaint.setColor(Color.WHITE);
      outlinePaint.setStyle(Paint.Style.STROKE);
      outlinePaint.setStrokeWidth(density * 1.5f);
      outlinePaint.setStrokeJoin(Paint.Join.ROUND);

      hotspotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      hotspotPaint.setColor(Color.RED);
      hotspotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
      super.onDraw(canvas);
      float x = hotspotX;
      float y = hotspotY;

      Path path = new Path();
      path.moveTo(x, y);
      path.lineTo(x, y + 28f * density);
      path.lineTo(x + 7f * density, y + 21f * density);
      path.lineTo(x + 13f * density, y + 34f * density);
      path.lineTo(x + 19f * density, y + 31f * density);
      path.lineTo(x + 13f * density, y + 19f * density);
      path.lineTo(x + 24f * density, y + 19f * density);
      path.close();

      canvas.drawPath(path, fillPaint);
      canvas.drawPath(path, outlinePaint);
      canvas.drawCircle(x, y, 2.6f * density, hotspotPaint);
    }
  }
}
