package org.transgram.a11y;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** A non-interactive window overlay: no delegates, focus ownership or touch geometry are changed. */
public final class LowVisionFocus extends Drawable implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private final View root;
    private final Rect rect = new Rect();
    private final Rect next = new Rect();
    private final Rect rootRect = new Rect();
    private final RectF drawingRect = new RectF();
    private final Point origin = new Point();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean installed;

    private LowVisionFocus(View root) {
        this.root = root;
        paint.setStyle(Paint.Style.STROKE);
        root.addOnAttachStateChangeListener(this);
        if (root.isAttachedToWindow()) onViewAttachedToWindow(root);
    }

    public static void install(View root) {
        if (LowVisionSettings.get().focusOutline && root != null && root.getTag(R.id.lowvision_focus_overlay) == null) {
            root.setTag(R.id.lowvision_focus_overlay, new LowVisionFocus(root));
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (installed) return;
        installed = true;
        root.getOverlay().add(this);
        root.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        installed = false;
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
        root.getOverlay().remove(this);
        rect.setEmpty();
    }

    @Override public boolean onPreDraw() {
        next.setEmpty();
        if (LowVisionSettings.get().focusOutline && root.hasWindowFocus()) {
            View focused = accessibilityFocus(root);
            if (focused == null) focused = root.findFocus();
            if (focused != null && focused != root && focused.getGlobalVisibleRect(next)) {
                root.getGlobalVisibleRect(rootRect, origin);
                if (!next.intersect(rootRect)) next.setEmpty();
                // Both rectangles use the same root coordinates, including dialog windows.
                next.offset(-origin.x, -origin.y);
            }
        }
        if (!rect.equals(next)) {
            rect.set(next);
            setBounds(0, 0, root.getWidth(), root.getHeight());
            invalidateSelf();
        }
        return true;
    }

    private static View accessibilityFocus(View view) {
        if (!view.isShown()) return null;
        if (view.isAccessibilityFocused()) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = accessibilityFocus(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    @Override public void draw(Canvas canvas) {
        if (rect.isEmpty() || !root.hasWindowFocus() || !LowVisionSettings.get().focusOutline) return;
        // A black halo plus white inner stroke stays visible across light, dark and image surfaces.
        drawingRect.set(rect);
        drawingRect.inset(AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        if (drawingRect.width() <= 0 || drawingRect.height() <= 0) return;
        paint.setColor(0xff000000);
        paint.setStrokeWidth(AndroidUtilities.dp(6));
        canvas.drawRoundRect(drawingRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
        paint.setColor(0xffffffff);
        paint.setStrokeWidth(AndroidUtilities.dp(3));
        canvas.drawRoundRect(drawingRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
