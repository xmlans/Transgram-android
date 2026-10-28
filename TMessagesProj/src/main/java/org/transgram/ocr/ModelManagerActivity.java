package org.transgram.ocr;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.transgram.a11y.TransGramA11y;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.LocaleController.formatString;

/** Model progress, retry, cancellation and storage management; does not require a screen reader. */
public class ModelManagerActivity extends BaseFragment {
    private UniversalRecyclerView list;
    private static boolean deleting;
    public static boolean isDeleting() { return deleting; }
    @Override public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(getString(R.string.TgOcrManage));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, this::click, (i,v,p,x,y)->false);
        root.addView(list, LayoutHelper.createFrame(-1,-1));
        return fragmentView = root;
    }
    @Override public void onResume() { super.onResume(); OcrUi.setStateListener(this::refresh); refresh(); }
    @Override public void onPause() { super.onPause(); OcrUi.setStateListener(null); }
    private void refresh() { if (list != null) list.adapter.update(true); }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        ModelManifest.Pack p;
        try { p = RecognitionEnv.ocrPack(); } catch (Exception e) {
            items.add(UItem.asShadow(getString(R.string.SgOcrErrEngine))); return;
        }
        ModelStore store = RecognitionEnv.store();
        boolean busy = OcrUi.isDownloading() || deleting;
        items.add(UItem.asHeader(getString(R.string.SgOcrModel)));
        long done = OcrUi.isDownloading() ? OcrUi.downloadedBytes() : store.isInstalled(p) ? p.totalBytes() : store.partialBytes(p);
        String detail = formatString(R.string.TgOcrBytes, AndroidUtilities.formatFileSize(done), AndroidUtilities.formatFileSize(p.totalBytes()));
        if (OcrUi.isDownloading()) detail = formatString(R.string.SgOcrStatusDownloading, OcrUi.downloadPercent()) + "\n" + detail;
        items.add(UItem.asShadow(detail + "\n" + formatString(R.string.TgOcrStorage, AndroidUtilities.formatFileSize(store.totalBytesOnDisk()))));
        if (deleting) items.add(UItem.asShadow(getString(R.string.TgOcrDeleting)));
        else if (OcrUi.isDownloading()) items.add(UItem.asButton(1, getString(OcrUi.isCancelling() ? R.string.TgOcrCancelling : R.string.TgOcrCancel)).setEnabled(!OcrUi.isCancelling()));
        else if (store.isInstalled(p)) items.add(UItem.asButton(2, getString(R.string.SgOcrDelete)));
        else {
            ModelDownloader.Reason failure = OcrUi.lastFailure();
            if (failure != null) items.add(UItem.asShadow(errorText(failure)));
            items.add(UItem.asButton(1, getString(failure != null && failure != ModelDownloader.Reason.CANCELLED ? R.string.TgOcrRetry : done > 0 ? R.string.TgOcrResume : R.string.SgOcrDlYes)));
            if (store.totalBytesOnDisk() > 0) items.add(UItem.asButton(2, getString(R.string.TgOcrDeletePartial)));
        }
        items.add(UItem.asCheck(3, getString(R.string.TgOcrWifi)).setChecked(RecognitionSettings.wifiOnly()).setEnabled(!busy));
        items.add(UItem.asShadow(getString(R.string.TgOcrWifiInfo)));
    }
    private String errorText(ModelDownloader.Reason r) {
        switch (r) {
            case CANCELLED: return getString(R.string.TgOcrPaused);
            case NEEDS_UNMETERED: return getString(R.string.TgOcrWifiInfo);
            case VERIFY_FAILED: return getString(R.string.SgOcrDlVerify);
            case NO_SPACE: return getString(R.string.TgOcrFailed);
            default: return getString(R.string.TgOcrFailed);
        }
    }
    private void click(UItem item, View view, int position, float x, float y) {
        if (deleting) return;
        if (item.id == 1) {
            if (OcrUi.isDownloading()) OcrUi.cancelDownload();
            else OcrUi.ensureModel(this, this::refresh);
        } else if (item.id == 3 && !OcrUi.isDownloading()) {
            RecognitionSettings.setWifiOnly(!RecognitionSettings.wifiOnly()); refresh();
        } else if (item.id == 2 && !OcrUi.isDownloading()) {
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.SgOcrDelete))
                .setMessage(formatString(R.string.TgOcrStorage, AndroidUtilities.formatFileSize(RecognitionEnv.store().totalBytesOnDisk())))
                .setPositiveButton(getString(R.string.SgOcrDelete), (d,w)->delete())
                .setNegativeButton(getString(R.string.SgOcrDlNo),null).create());
        }
    }
    private void delete() {
        if (deleting || OcrUi.isDownloading()) return;
        deleting = true; refresh();
        RecognitionHub.service().maintainModels(() -> {
            boolean removed;
            try {
                RecognitionEnv.store().delete(RecognitionEnv.ocrPack());
                RecognitionHub.results().clearAll();
                removed = RecognitionEnv.store().totalBytesOnDisk() == 0;
            } catch (Exception e) { removed = false; }
            final boolean success = removed;
            AndroidUtilities.runOnUIThread(() -> {
                deleting = false; TransGramA11y.speechVersion++; OcrUi.changed(); refresh();
                if (!isFinished && getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity())
                    .setMessage(getString(success ? R.string.SgOcrDeleted : R.string.TgOcrDeleteFailed))
                    .setPositiveButton(getString(R.string.SgOcrClose),null).create());
            });
        });
    }
}
