package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** TransGram: "Features" page — bilingual translation and 18+ media masking (moved here from Appearance). */
public class FeaturesSettingsActivity extends BaseFragment {
    private static final int ID_BILINGUAL = 1;
    private static final int ID_NSFW = 2;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.TgFeatures));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, (item, view, position, x, y) -> false);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.TransGramSettings)));
        items.add(UItem.asCheck(ID_BILINGUAL, getString(R.string.TransGramBilingual)).setChecked(SharedConfig.bilingualTranslation));
        items.add(UItem.asShadow(getString(R.string.TransGramBilingualInfo)));
        items.add(UItem.asCheck(ID_NSFW, getString(R.string.TransGramNsfwProtection)).setChecked(SharedConfig.nsfwProtection));
        items.add(UItem.asShadow(getString(R.string.TransGramNsfwProtectionInfo)));
        items.add(UItem.asShadow(getString(R.string.TransGramSettingsInfo)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_BILINGUAL) {
            SharedConfig.toggleBilingualTranslation();
        } else if (item.id == ID_NSFW) {
            SharedConfig.toggleNsfwProtection();
        } else {
            return;
        }
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }
}
