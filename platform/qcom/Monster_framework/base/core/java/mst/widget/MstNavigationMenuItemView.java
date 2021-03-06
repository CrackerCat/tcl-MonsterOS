package mst.widget;

import mst.view.menu.MstMenuItemImpl;
import mst.view.menu.MstMenuView;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;

import com.mst.R;

public class MstNavigationMenuItemView extends MstForegroundLinearLayout implements MstMenuView.ItemView {

    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    private final int mIconSize;

    private final CheckedTextView mTextView;

    private FrameLayout mActionArea;

    private MstMenuItemImpl mItemData;

    private ColorStateList mIconTintList;

    public MstNavigationMenuItemView(Context context) {
        this(context, null);
    }

    public MstNavigationMenuItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MstNavigationMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.navigation_menu_item, this, true);
        mIconSize = context.getResources().getDimensionPixelSize(
                R.dimen.navigation_icon_size);
        mTextView = (CheckedTextView) findViewById(R.id.menu_item_text);
        mTextView.setDuplicateParentStateEnabled(true);
    }

    @Override
    public void initialize(MstMenuItemImpl itemData, int menuType) {
        mItemData = itemData;

        setVisibility(itemData.isVisible() ? VISIBLE : GONE);

        if (getBackground() == null) {
            setBackgroundDrawable(createDefaultBackground());
        }

        setCheckable(itemData.isCheckable());
        setChecked(itemData.isChecked());
        setEnabled(itemData.isEnabled());
        setTitle(itemData.getTitle());
        setIcon(itemData.getIcon());
        setActionView(itemData.getActionView());
    }

    public void recycle() {
        if (mActionArea != null) {
            mActionArea.removeAllViews();
        }
        mTextView.setCompoundDrawables(null, null, null, null);
    }

    private void setActionView(View actionView) {
        if (mActionArea == null) {
            mActionArea = (FrameLayout) ((ViewStub) findViewById(
                    R.id.menu_item_action_area_stub)).inflate();
        }
        mActionArea.removeAllViews();
        if (actionView != null) {
            mActionArea.addView(actionView);
        }
    }

    private StateListDrawable createDefaultBackground() {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.colorControlHighlight, value, true)) {
            StateListDrawable drawable = new StateListDrawable();
            drawable.addState(CHECKED_STATE_SET, new ColorDrawable(value.data));
            drawable.addState(EMPTY_STATE_SET, new ColorDrawable(Color.TRANSPARENT));
            return drawable;
        }
        return null;
    }

    @Override
    public MstMenuItemImpl getItemData() {
        return mItemData;
    }

    @Override
    public void setTitle(CharSequence title) {
        mTextView.setText(title);
    }

    @Override
    public void setCheckable(boolean checkable) {
        refreshDrawableState();
    }

    @Override
    public void setChecked(boolean checked) {
        refreshDrawableState();
        mTextView.setChecked(checked);
    }

    @Override
    public void setShortcut(boolean showShortcut, char shortcutKey) {
    }

    @Override
    public void setIcon(Drawable icon) {
        if (icon != null) {
            icon = icon.mutate();
            icon.setBounds(0, 0, mIconSize, mIconSize);
            icon.setTintList(mIconTintList);
        }
        mTextView.setCompoundDrawablesRelative(icon, null, null, null);
    }

    @Override
    public boolean prefersCondensedTitle() {
        return false;
    }

    @Override
    public boolean showsIcon() {
        return true;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mItemData != null && mItemData.isCheckable() && mItemData.isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    public void setIconTintList(ColorStateList tintList) {
        mIconTintList = tintList;
        if (mItemData != null) {
            // Update the icon so that the tint takes effect
            setIcon(mItemData.getIcon());
        }
    }

    public void setTextAppearance(Context context, int textAppearance) {
        mTextView.setTextAppearance(context, textAppearance);
    }

    public void setTextColor(ColorStateList colors) {
        mTextView.setTextColor(colors);
    }

	@Override
	public void onInitializeAccessibilityNodeInfoInternal(
			AccessibilityNodeInfo info) {
		// TODO Auto-generated method stub
		
	}

}