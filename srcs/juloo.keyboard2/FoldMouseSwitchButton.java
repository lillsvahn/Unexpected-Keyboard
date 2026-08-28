package juloo.keyboard2;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

/** Button embedded in the normal keyboard that swaps the IME to FoldMouse. */
public class FoldMouseSwitchButton extends Button
{
  private FoldMousePane _pane;

  public FoldMouseSwitchButton(Context context)
  {
    super(context);
    init();
  }

  public FoldMouseSwitchButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    init();
  }

  public FoldMouseSwitchButton(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init()
  {
    setOnClickListener(v -> openFoldMouse());
  }

  private void openFoldMouse()
  {
    Keyboard2 keyboard = findKeyboardService(getContext());
    if (keyboard == null)
      return;

    View keyboardRoot = getRootView().findViewById(R.id.keyboard_container);
    if (keyboardRoot == null)
      return;

    if (_pane == null)
      _pane = new FoldMousePane(keyboard, keyboardRoot);

    _pane.onShown();
    keyboard.setInputView(_pane);
  }

  private Keyboard2 findKeyboardService(Context context)
  {
    Context current = context;
    while (current != null)
    {
      if (current instanceof Keyboard2)
        return (Keyboard2)current;

      if (current instanceof ContextWrapper)
      {
        Context next = ((ContextWrapper)current).getBaseContext();
        if (next == current)
          break;
        current = next;
      }
      else
        break;
    }
    return null;
  }
}
