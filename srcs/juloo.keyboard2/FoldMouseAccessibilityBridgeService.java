package juloo.keyboard2;

import android.accessibilityservice.AccessibilityButtonController;
import android.os.Build;

/**
 * Declared accessibility-service entry point.
 *
 * The inherited class contains the tested FoldMouse gesture engine. This class
 * only adds the Android accessibility-button shortcut and standalone overlay.
 */
public class FoldMouseAccessibilityBridgeService
  extends FoldMouseAccessibilityService
{
  private FoldMouseOverlayController _overlayController;
  private AccessibilityButtonController _buttonController;
  private AccessibilityButtonController.AccessibilityButtonCallback _buttonCallback;
  private boolean _keyboardPaneOpen = false;

  @Override
  protected void onServiceConnected()
  {
    super.onServiceConnected();
    _overlayController = new FoldMouseOverlayController(this);

    if (Build.VERSION.SDK_INT >= 26)
    {
      _buttonController = getAccessibilityButtonController();
      _buttonCallback =
        new AccessibilityButtonController.AccessibilityButtonCallback()
        {
          @Override
          public void onClicked(AccessibilityButtonController controller)
          {
            toggleStandaloneOverlay();
          }
        };
      _buttonController.registerAccessibilityButtonCallback(_buttonCallback);
    }
  }

  @Override
  public void onDestroy()
  {
    if (Build.VERSION.SDK_INT >= 26 &&
        _buttonController != null &&
        _buttonCallback != null)
    {
      _buttonController.unregisterAccessibilityButtonCallback(_buttonCallback);
    }

    if (_overlayController != null)
      _overlayController.destroy();

    _overlayController = null;
    _buttonController = null;
    _buttonCallback = null;
    super.onDestroy();
  }

  public void setKeyboardPaneOpen(boolean open)
  {
    _keyboardPaneOpen = open;

    // The keyboard-integrated pane owns the pointer while it is visible.
    if (open && _overlayController != null && _overlayController.isVisible())
      _overlayController.hide();
  }

  public void toggleStandaloneOverlay()
  {
    // If the FoldMouse pane is already visible inside the keyboard there is
    // nothing useful for a second touch surface to do.
    if (_keyboardPaneOpen)
      return;

    if (_overlayController != null)
      _overlayController.toggle();
  }

  public void hideStandaloneOverlay()
  {
    if (_overlayController != null && _overlayController.isVisible())
      _overlayController.hide();
  }
}
