package juloo.keyboard2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Mouse/gesture engine used by the FoldMouse pane inside Unexpected Keyboard.
 *
 * The keyboard owns the physical touch UI. This accessibility service only
 * draws the pointer and injects taps, drags and scroll gestures into the app
 * underneath it.
 */
public class FoldMouseAccessibilityService extends AccessibilityService
{
  private static FoldMouseAccessibilityService _instance;

  private WindowManager _windowManager;
  private MouseCursorView _cursorView;
  private float _cursorX = 400f;
  private float _cursorY = 400f;
  private boolean _cursorInitialized = false;

  // Live LMB state.
  private boolean _lmbInputActive = false;
  private boolean _lmbFinishRequested = false;
  private boolean _lmbDispatchInFlight = false;
  private float _lmbSyntheticX = 0f;
  private float _lmbSyntheticY = 0f;
  private float _lmbTargetX = 0f;
  private float _lmbTargetY = 0f;
  private GestureDescription.StrokeDescription _lmbStroke = null;

  // Stable live scroll state based on FoldMouse v0.10.
  private boolean _scrollInputActive = false;
  private boolean _scrollFinishRequested = false;
  private boolean _scrollDispatchInFlight = false;
  private float _scrollLastPhysicalY = 0f;
  private float _scrollQueuedSyntheticDy = 0f;
  private float _scrollSyntheticX = 0f;
  private float _scrollSyntheticY = 0f;
  private GestureDescription.StrokeDescription _scrollStroke = null;

  private static final float SCROLL_SPEED = 3.0f;

  public static FoldMouseAccessibilityService getInstance()
  {
    return _instance;
  }

  public static boolean isRunning()
  {
    return _instance != null;
  }

  @Override
  protected void onServiceConnected()
  {
    super.onServiceConnected();
    _instance = this;
    _windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
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
    hideCursor();
    if (_instance == this)
      _instance = null;
    super.onDestroy();
  }

  public void showCursor()
  {
    if (_cursorView != null || _windowManager == null)
      return;

    if (!_cursorInitialized)
    {
      WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
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

    _windowManager.addView(cursor, params);
    _cursorView = cursor;
  }

  public void hideCursor()
  {
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
    finishAnyActiveGestures();
  }

  public void moveCursorBy(float dx, float dy)
  {
    if (_cursorView == null)
      showCursor();
    if (_cursorView == null)
      return;

    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    float cursorWidth = dp(38);
    float cursorHeight = dp(48);

    _cursorX = clamp(_cursorX + dx, 0f,
      Math.max(0f, metrics.widthPixels - cursorWidth));
    _cursorY = clamp(_cursorY + dy, 0f,
      Math.max(0f, metrics.heightPixels - cursorHeight));

    updateCursorPosition();

    if (_lmbInputActive || _lmbStroke != null)
      updateLiveLmbTarget();
  }

  private void updateCursorPosition()
  {
    if (_cursorView == null || _windowManager == null)
      return;
    WindowManager.LayoutParams params =
      (WindowManager.LayoutParams)_cursorView.getLayoutParams();
    params.x = Math.round(_cursorX);
    params.y = Math.round(_cursorY);
    _windowManager.updateViewLayout(_cursorView, params);
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

  public void tapAtCursor()
  {
    PointF hotspot = cursorHotspotOnScreen();
    performTap(hotspot.x, hotspot.y);
  }

  public void rightClickAtCursor()
  {
    PointF hotspot = cursorHotspotOnScreen();
    // Android accessibility gestures are touch gestures rather than a physical
    // BUTTON_SECONDARY event. Long press is the most compatible context-click
    // fallback and matches the standalone FoldMouse behavior.
    performLongPress(hotspot.x, hotspot.y);
  }

  private void performTap(float x, float y)
  {
    Path path = new Path();
    path.moveTo(x, y);
    GestureDescription.StrokeDescription stroke =
      new GestureDescription.StrokeDescription(path, 0, 60);
    GestureDescription gesture = new GestureDescription.Builder()
      .addStroke(stroke)
      .build();
    dispatchGesture(gesture, null, null);
  }

  private void performLongPress(float x, float y)
  {
    Path path = new Path();
    path.moveTo(x, y);
    GestureDescription.StrokeDescription stroke =
      new GestureDescription.StrokeDescription(path, 0, 650);
    GestureDescription gesture = new GestureDescription.Builder()
      .addStroke(stroke)
      .build();
    dispatchGesture(gesture, null, null);
  }

  // -------------------------------------------------------------------------
  // Live LMB drag.
  // -------------------------------------------------------------------------

  public void beginLiveLmb()
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

  public void endLiveLmb()
  {
    if (!_lmbInputActive && _lmbStroke == null)
      return;
    _lmbInputActive = false;
    _lmbFinishRequested = true;
    updateLiveLmbTarget();
    pumpLiveLmb();
  }

  public void cancelLiveLmb()
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

    GestureDescription gesture = new GestureDescription.Builder()
      .addStroke(stroke)
      .build();

    boolean accepted = dispatchGesture(
      gesture,
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

  // -------------------------------------------------------------------------
  // Live 3x scroll from FoldMouse v0.10.
  // -------------------------------------------------------------------------

  public void beginLiveScroll(float physicalY)
  {
    if (Build.VERSION.SDK_INT < 26)
      return;
    resetLiveScrollState();
    _scrollInputActive = true;
    _scrollLastPhysicalY = physicalY;
  }

  public void updateLiveScroll(float physicalY)
  {
    if (!_scrollInputActive)
      return;

    float physicalDy = physicalY - _scrollLastPhysicalY;
    _scrollLastPhysicalY = physicalY;
    if (Math.abs(physicalDy) < 0.25f)
      return;

    _scrollQueuedSyntheticDy += -physicalDy * SCROLL_SPEED;
    if (Math.abs(_scrollQueuedSyntheticDy) >= dp(1))
      pumpLiveScroll();
  }

  public void endLiveScroll()
  {
    _scrollInputActive = false;
    _scrollFinishRequested = true;
    pumpLiveScroll();
  }

  public void cancelLiveScroll()
  {
    if (!_scrollInputActive && _scrollStroke == null)
    {
      resetLiveScrollState();
      return;
    }
    _scrollInputActive = false;
    _scrollFinishRequested = true;
    pumpLiveScroll();
  }

  private void pumpLiveScroll()
  {
    if (_scrollDispatchInFlight)
      return;

    float minStep = dp(1);
    if (_scrollStroke == null && Math.abs(_scrollQueuedSyntheticDy) < minStep)
    {
      if (_scrollFinishRequested)
        resetLiveScrollState();
      return;
    }

    if (_scrollStroke == null)
      prepareSyntheticScrollStart();

    if (Math.abs(_scrollQueuedSyntheticDy) < minStep)
    {
      if (_scrollFinishRequested)
        dispatchFinalScrollRelease();
      return;
    }

    // v0.10: large and short continuation segments let 3x movement keep up
    // with quick physical swipes instead of draining a backlog afterwards.
    float maxSegment = dp(96);
    float requestedStep = clamp(
      _scrollQueuedSyntheticDy,
      -maxSegment,
      maxSegment);

    float topLimit = dp(48);
    float bottomLimit = Math.max(
      dp(140),
      getResources().getDisplayMetrics().heightPixels - dp(285));

    float newY = clamp(_scrollSyntheticY + requestedStep,
      topLimit, bottomLimit);
    float actualStep = newY - _scrollSyntheticY;

    if (Math.abs(actualStep) < 0.5f)
    {
      _scrollQueuedSyntheticDy = 0f;
      if (_scrollFinishRequested)
        dispatchFinalScrollRelease();
      return;
    }

    _scrollQueuedSyntheticDy -= actualStep;
    boolean finishing = _scrollFinishRequested &&
      Math.abs(_scrollQueuedSyntheticDy) < minStep;
    boolean willContinue = !finishing;

    Path path = new Path();
    path.moveTo(_scrollSyntheticX, _scrollSyntheticY);
    path.lineTo(_scrollSyntheticX, newY);

    long duration = Math.max(10L, Math.min(24L,
      10L + Math.round(Math.abs(actualStep) / dp(24) * 2f)));

    GestureDescription.StrokeDescription nextStroke;
    if (_scrollStroke == null)
      nextStroke = new GestureDescription.StrokeDescription(
        path, 0, duration, willContinue);
    else
      nextStroke = _scrollStroke.continueStroke(
        path, 0, duration, willContinue);

    _scrollSyntheticY = newY;
    dispatchLiveScrollStroke(nextStroke, willContinue);
  }

  private void prepareSyntheticScrollStart()
  {
    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    PointF hotspot = cursorHotspotOnScreen();

    _scrollSyntheticX = clamp(
      hotspot.x,
      dp(24),
      metrics.widthPixels - dp(24));

    float topLimit = dp(72);
    float bottomLimit = Math.max(
      dp(160),
      metrics.heightPixels - dp(310));
    _scrollSyntheticY = clamp(hotspot.y, topLimit, bottomLimit);
  }

  private void dispatchLiveScrollStroke(
    final GestureDescription.StrokeDescription stroke,
    final boolean willContinue)
  {
    _scrollDispatchInFlight = true;
    _scrollStroke = stroke;

    GestureDescription gesture = new GestureDescription.Builder()
      .addStroke(stroke)
      .build();

    boolean accepted = dispatchGesture(
      gesture,
      new GestureResultCallback()
      {
        @Override
        public void onCompleted(GestureDescription gestureDescription)
        {
          _scrollDispatchInFlight = false;
          if (willContinue)
          {
            if (_scrollFinishRequested &&
                Math.abs(_scrollQueuedSyntheticDy) < dp(1))
              dispatchFinalScrollRelease();
            else
              pumpLiveScroll();
          }
          else
            resetLiveScrollState();
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription)
        {
          _scrollDispatchInFlight = false;
          resetLiveScrollState();
        }
      },
      null);

    if (!accepted)
    {
      _scrollDispatchInFlight = false;
      resetLiveScrollState();
    }
  }

  private void dispatchFinalScrollRelease()
  {
    if (_scrollDispatchInFlight)
      return;
    if (_scrollStroke == null)
    {
      resetLiveScrollState();
      return;
    }

    Path path = new Path();
    path.moveTo(_scrollSyntheticX, _scrollSyntheticY);
    GestureDescription.StrokeDescription finalStroke =
      _scrollStroke.continueStroke(path, 0, 10, false);
    dispatchLiveScrollStroke(finalStroke, false);
  }

  private void resetLiveScrollState()
  {
    _scrollInputActive = false;
    _scrollFinishRequested = false;
    _scrollDispatchInFlight = false;
    _scrollLastPhysicalY = 0f;
    _scrollQueuedSyntheticDy = 0f;
    _scrollSyntheticX = 0f;
    _scrollSyntheticY = 0f;
    _scrollStroke = null;
  }

  public void finishAnyActiveGestures()
  {
    if (_lmbInputActive || _lmbStroke != null)
      endLiveLmb();
    if (_scrollInputActive || _scrollStroke != null)
      endLiveScroll();
  }

  private float clamp(float value, float min, float max)
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
