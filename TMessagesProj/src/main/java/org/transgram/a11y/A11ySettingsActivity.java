package org.transgram.a11y;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ActionBar.AlertDialog;
import org.transgram.ocr.ModelManifest;
import org.transgram.ocr.OcrUi;
import org.transgram.ocr.RecognitionEnv;
import org.transgram.ocr.RecognitionHub;
import org.transgram.ocr.RecognitionSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** TransGram accessibility settings. Built on UniversalRecyclerView with standard cells only. */
public class A11ySettingsActivity extends BaseFragment {
    private static final int ID_ENABLED = 1;
    private static final int ID_PRESET = 10;      // + SpeechPreset ordinal
    private static final int ID_PREVIEW = 20;
    private static final int ID_VOLUME = 21;
    private static final int ID_ANN_MODE = 22;
    private static final int ID_ANN_DOWNLOADS = 23;
    private static final int ID_NAME_CLEAN = 24;
    private static final int ID_CALL_JOINLEAVE = 25;
    private static final int ID_LANG = 30;       // + index in LANGS
    private static final int ID_EXPORT = 40;
    private static final int ID_IMPORT = 41;
    private static final int ID_REPO = 42;
    private static final int ID_LICENSES = 43;
    private static final int ID_OCR_ENABLED = 50;
    private static final int ID_OCR_MODEL = 51;
    private static final int ID_OCR_SPEAK = 52;
    private static final int ID_FIELD = 100;      // + SpeechField ordinal
    private static final int ID_EVENT = 200;      // + Feedback.Event ordinal

    private static final String[] LANGS = {"system", "zh", "en"};
    private static final int[] VOLUME_PERCENT = {25, 50, 70, 100};

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.SgSettings));
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
        listView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View child) {
                ViewCompat.setAccessibilityDelegate(child, new MoveDelegate());
            }

            @Override
            public void onChildViewDetachedFromWindow(View child) {}
        });
        return fragmentView = content;
    }

    @Override
    public void onResume() {
        super.onResume();
        OcrUi.setStateListener(this::refresh);   // model download progress updates the model row
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        OcrUi.setStateListener(null);
    }

    // ---- rows ----

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        SpeechSettings s = TransGramA11y.speech();

        LowVisionRows.fill(items);

        // Speech master switch (default off). Low vision remains available above this switch.
        items.add(UItem.asCheck(ID_ENABLED, getString(R.string.TgA11yMaster)).setChecked(TransGramA11y.isEnabled()));
        items.add(UItem.asShadow(getString(R.string.TgA11yMasterInfo)));
        if (!TransGramA11y.isEnabled()) {
            items.add(UItem.asShadow(getString(R.string.SgAbout)));
            return;
        }

        items.add(UItem.asHeader(getString(R.string.SgSpeechHeader)));
        items.add(UItem.asCheck(ID_NAME_CLEAN, getString(R.string.SgNameClean)).setChecked(TransGramA11y.cleanNames()));
        items.add(UItem.asCheck(ID_CALL_JOINLEAVE, getString(R.string.SgCallJoinLeave)).setChecked(TransGramA11y.callJoinLeave()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SgPresetHeader)));
        for (SpeechPreset p : SpeechPreset.values()) {
            items.add(UItem.asRadio(ID_PRESET + p.ordinal(), presetName(p)).setChecked(s.preset() == p));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SgFieldsHeader)));
        for (SpeechField f : s.order()) {
            items.add(UItem.asCheck(ID_FIELD + f.ordinal(), fieldName(f)).setChecked(s.isOn(f)));
        }
        items.add(UItem.asButton(ID_PREVIEW, getString(R.string.SgPreview)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SgFeedbackHeader)));
        for (Feedback.Event e : Feedback.Event.values()) {
            items.add(UItem.asButton(ID_EVENT + e.ordinal(), eventName(e), feedbackModeName(Feedback.mode(e))));
        }
        items.add(UItem.asButton(ID_VOLUME, getString(R.string.SgVolume), Math.round(Feedback.volume() * 100) + "%"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SgAnnounceHeader)));
        items.add(UItem.asButton(ID_ANN_MODE, getString(R.string.SgAnnDefaultMode), announceModeName(Announcer.mode(0))));
        items.add(UItem.asCheck(ID_ANN_DOWNLOADS, getString(R.string.SgAnnounceDownloads))
                .setChecked(TransGramA11y.prefs().getBoolean("ann_downloads", true)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SgLanguage)));
        String lang = uiLang();
        for (int i = 0; i < LANGS.length; i++) {
            items.add(UItem.asRadio(ID_LANG + i, langName(LANGS[i])).setChecked(LANGS[i].equals(lang)));
        }
        items.add(UItem.asShadow(null));

        if (RecognitionHub.isSupported()) fillOcr(items);

        items.add(UItem.asHeader(getString(R.string.SgBackupHeader)));
        items.add(UItem.asButton(ID_EXPORT, getString(R.string.SgExport)));
        items.add(UItem.asButton(ID_IMPORT, getString(R.string.SgImport)));
        items.add(UItem.asButton(ID_REPO, getString(R.string.SgRepoLink), getString(R.string.SgRepoUrl)));
        items.add(UItem.asButton(ID_LICENSES, getString(R.string.SgLicenses)));
        items.add(UItem.asShadow(getString(R.string.SgAbout)));
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (LowVisionRows.onClick(this, id)) return;
        if (id == ID_ENABLED) {
            TransGramA11y.setEnabled(!TransGramA11y.isEnabled());
            refresh();
        } else if (id == ID_NAME_CLEAN) {
            TransGramA11y.setCleanNames(!TransGramA11y.cleanNames());
            refresh();
        } else if (id == ID_CALL_JOINLEAVE) {
            TransGramA11y.prefs().edit().putBoolean("call_group_joinleave", !TransGramA11y.callJoinLeave()).apply();
            refresh();
        } else if (id >= ID_PRESET && id < ID_PRESET + SpeechPreset.values().length) {
            TransGramA11y.setSpeech(SpeechSettings.forPreset(SpeechPreset.values()[id - ID_PRESET]));
            refresh();
        } else if (id >= ID_FIELD && id < ID_FIELD + SpeechField.values().length) {
            SpeechField f = SpeechField.values()[id - ID_FIELD];
            SpeechSettings s = TransGramA11y.speech();
            TransGramA11y.setSpeech(s.withField(f, !s.isOn(f)));
            refresh();
        } else if (id == ID_PREVIEW) {
            preview();
        } else if (id >= ID_EVENT && id < ID_EVENT + Feedback.Event.values().length) {
            chooseFeedbackMode(Feedback.Event.values()[id - ID_EVENT]);
        } else if (id == ID_VOLUME) {
            chooseVolume();
        } else if (id == ID_ANN_MODE) {
            chooseAnnounceMode();
        } else if (id == ID_ANN_DOWNLOADS) {
            SharedPreferences p = TransGramA11y.prefs();
            p.edit().putBoolean("ann_downloads", !p.getBoolean("ann_downloads", true)).apply();
            refresh();
        } else if (id >= ID_LANG && id < ID_LANG + LANGS.length) {
            chooseLanguage(LANGS[id - ID_LANG]);
        } else if (id == ID_EXPORT) {
            export();
        } else if (id == ID_IMPORT) {
            importFromClipboard();
        } else if (id == ID_OCR_ENABLED) {
            RecognitionSettings.setOcrEnabled(!RecognitionSettings.ocrEnabled());
            refresh();
        } else if (id == ID_OCR_MODEL) {
            onOcrModelClick();
        } else if (id == ID_OCR_SPEAK) {
            RecognitionSettings.setSpeakOcr(!RecognitionSettings.speakOcr());
            refresh();
        } else if (id == ID_LICENSES) {
            showLicenses();
        } else if (id == ID_REPO) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), getString(R.string.SgRepoUrl));
        }
    }

    // ---- offline text recognition (OCR) ----

    private void fillOcr(ArrayList<UItem> items) {
        items.add(UItem.asHeader(getString(R.string.SgOcrHeader)));
        items.add(UItem.asCheck(ID_OCR_ENABLED, getString(R.string.SgOcrEnable)).setChecked(RecognitionSettings.ocrEnabled()));
        items.add(UItem.asButton(ID_OCR_MODEL, getString(R.string.SgOcrModel), ocrModelStatus()));
        items.add(UItem.asCheck(ID_OCR_SPEAK, getString(R.string.SgOcrSpeak)).setChecked(RecognitionSettings.speakOcr()));
        items.add(UItem.asShadow(getString(R.string.SgOcrInfo)));
    }

    private static ModelManifest.Pack ocrPack() {
        try {
            return RecognitionEnv.ocrPack();
        } catch (ModelManifest.ManifestException e) {
            return null;   // the bundled manifest is guarded by ManifestAssetTest
        }
    }

    private String ocrModelStatus() {
        if (OcrUi.isDownloading()) return formatString(R.string.SgOcrStatusDownloading, OcrUi.downloadPercent());
        ModelManifest.Pack p = ocrPack();
        if (p == null) return "";
        if (RecognitionEnv.store().isInstalled(p)) {
            return formatString(R.string.SgOcrStatusDownloaded, AndroidUtilities.formatFileSize(RecognitionEnv.store().installedBytes(p)));
        }
        return formatString(R.string.SgOcrStatusNot, AndroidUtilities.formatFileSize(p.totalBytes()));
    }

    private void onOcrModelClick() {
        presentFragment(new org.transgram.ocr.ModelManagerActivity());
    }

    private void showLicenses() {
        String text;
        try {
            text = RecognitionEnv.readAsset("third_party_licenses.txt");
        } catch (Exception e) {
            text = getString(R.string.SgRepoUrl);
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.SgLicenses))
                .setMessage(text)
                .setPositiveButton(getString(R.string.SgOcrClose), null)
                .create());
    }

    // ---- actions ----

    private void preview() {
        SpeechInput in = new SpeechInput();
        in.senderName = getString(R.string.SgPreviewSender);
        in.kind = SpeechInput.Kind.TEXT;
        in.body = getString(R.string.SgPreviewSample);
        long now = System.currentTimeMillis();
        in.dateMillis = now;
        in.status = SpeechInput.Status.NONE;
        List<SpeechInput.Reaction> reactions = new ArrayList<>();
        reactions.add(new SpeechInput.Reaction("❤️", 2, false));
        in.reactions = reactions;
        SpeechFormat f = TransGramA11y.format();
        String text = MessageSpeech.join(MessageSpeech.build(in, TransGramA11y.speech(), f, now, TimeZone.getDefault()), f);
        AndroidUtilities.makeAccessibilityAnnouncement(text);
    }

    private void chooseFeedbackMode(Feedback.Event e) {
        Feedback.Mode[] modes = Feedback.Mode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) names[i] = feedbackModeName(modes[i]);
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, eventName(e),
                Feedback.mode(e).ordinal(), (d, which) -> {
                    Feedback.setMode(e, modes[which]);
                    Feedback.play(e);
                    refresh();
                }));
    }

    private void chooseVolume() {
        String[] names = new String[VOLUME_PERCENT.length];
        int cur = Math.round(Feedback.volume() * 100);
        int selected = 0;
        for (int i = 0; i < VOLUME_PERCENT.length; i++) {
            names[i] = VOLUME_PERCENT[i] + "%";
            if (Math.abs(VOLUME_PERCENT[i] - cur) < Math.abs(VOLUME_PERCENT[selected] - cur)) selected = i;
        }
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, getString(R.string.SgVolume),
                selected, (d, which) -> {
                    Feedback.setVolume(VOLUME_PERCENT[which] / 100f);
                    Feedback.play(Feedback.Event.SENT);
                    refresh();
                }));
    }

    private void chooseAnnounceMode() {
        Announcer.Mode[] modes = Announcer.Mode.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) names[i] = announceModeName(modes[i]);
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, getString(R.string.SgAnnDefaultMode),
                Announcer.mode(0).ordinal(), (d, which) -> {
                    Announcer.setMode(0, modes[which]);
                    refresh();
                }));
    }

    private void chooseLanguage(String choice) {
        if (!applyLanguage(choice, currentAccount)) {
            String msg = getString(R.string.SgLangUnavailable);
            AndroidUtilities.makeAccessibilityAnnouncement(msg);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, msg).show();
            refresh();
            return;
        }
        TransGramA11y.prefs().edit().putString("ui_lang", choice).apply();
        TransGramA11y.onLanguageChanged();
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
        refresh();
    }

    private void export() {
        String text = SettingsBackup.export(TransGramA11y.prefs().getAll());
        AndroidUtilities.addToClipboard(text);
        String done = getString(R.string.SgExported);
        AndroidUtilities.makeAccessibilityAnnouncement(done);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.copy, done).show();
    }

    private void importFromClipboard() {
        Map<String, Object> values = null;
        try {
            ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence cs = clip.getItemAt(0).coerceToText(ApplicationLoader.applicationContext);
                values = SettingsBackup.parse(cs == null ? null : cs.toString());
            }
        } catch (Exception ignore) {
            // clipboard unavailable or protected
        }
        if (values == null) {
            String failed = getString(R.string.SgImportFailed);
            AndroidUtilities.makeAccessibilityAnnouncement(failed);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, failed).show();
            return;
        }
        List<String> events = new ArrayList<>();
        for (Feedback.Event ev : Feedback.Event.values()) events.add(ev.name());
        List<String> fbModes = new ArrayList<>();
        for (Feedback.Mode m : Feedback.Mode.values()) fbModes.add(m.name());
        List<String> annModes = new ArrayList<>();
        for (Announcer.Mode m : Announcer.Mode.values()) annModes.add(m.name());
        values = SettingsBackup.sanitize(values, events, fbModes, annModes);
        TransGramA11y.clearAll();
        SharedPreferences.Editor ed = TransGramA11y.prefs().edit();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) ed.putString(e.getKey(), (String) v);
            else if (v instanceof Boolean) ed.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof Integer) ed.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Long) ed.putLong(e.getKey(), (Long) v);
            else if (v instanceof Float) ed.putFloat(e.getKey(), (Float) v);
        }
        ed.apply();
        TransGramA11y.reload();
        if (!"system".equals(uiLang())) applyLanguage(uiLang(), currentAccount);
        TransGramA11y.onLanguageChanged();
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
        AndroidUtilities.makeAccessibilityAnnouncement(getString(R.string.SgImported));
        refresh();
    }

    // ---- language ----

    private static String uiLang() {
        String v = TransGramA11y.prefs().getString("ui_lang", "system");
        return "zh".equals(v) || "en".equals(v) ? v : "system";
    }

    /** Called from FeedbackHooks.install() so a forced language survives restarts. */
    public static void applySavedLanguage() {
        String lang = uiLang();
        if ("system".equals(lang)) return;
        try {
            applyLanguage(lang, UserConfig.selectedAccount);
        } catch (Throwable ignore) {
            // never block startup
        }
    }

    /** Finds the LocaleInfo for an effective language ("zh" or "en"); Chinese only exists as a remote langpack. */
    private static LocaleController.LocaleInfo findLanguage(LocaleController lc, String effective) {
        if (!"zh".equals(effective)) return lc.getLanguageFromDict("en");
        String[] keys = {"zh_hans", "zh_cn", "zh"};
        for (String k : keys) {
            LocaleController.LocaleInfo i = lc.getLanguageFromDict(k);
            if (i != null) return i;
        }
        LocaleController.LocaleInfo best = null;
        int bestScore = -1;
        List<LocaleController.LocaleInfo> all = new ArrayList<>(lc.remoteLanguages);
        for (LocaleController.LocaleInfo i : all) {
            String plural = i.pluralLangCode == null ? "" : i.pluralLangCode.toLowerCase(Locale.ROOT);
            String shortName = i.shortName == null ? "" : i.shortName.toLowerCase(Locale.ROOT);
            if (!plural.startsWith("zh") && !shortName.startsWith("zh")) continue;
            int score = shortName.contains("hans") || shortName.contains("cn") ? 2 : shortName.contains("hant") || shortName.contains("tw") ? 0 : 1;
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        if (best == null && all.isEmpty()) lc.loadRemoteLanguages(UserConfig.selectedAccount);
        return best;
    }

    /** Returns false when the language pack is not available (nothing was changed). */
    private static boolean applyLanguage(String choice, int account) {
        String effective = choice;
        if ("system".equals(choice)) {
            effective = "zh".equals(Locale.getDefault().getLanguage()) ? "zh" : "en";
        }
        LocaleController lc = LocaleController.getInstance();
        LocaleController.LocaleInfo info = findLanguage(lc, effective);
        if (info == null) return false;
        if (info != lc.getCurrentLocaleInfo()) lc.applyLanguage(info, true, false, account);
        return true;
    }

    // ---- names ----

    private static String presetName(SpeechPreset p) {
        switch (p) {
            case BRIEF: return getString(R.string.SgPresetBrief);
            case VERBOSE: return getString(R.string.SgPresetVerbose);
            default: return getString(R.string.SgPresetStandard);
        }
    }

    private static String fieldName(SpeechField f) {
        switch (f) {
            case SENDER: return getString(R.string.SgFieldSender);
            case TYPE: return getString(R.string.SgFieldType);
            case FORWARD_REPLY: return getString(R.string.SgFieldForwardReply);
            case BODY: return getString(R.string.SgFieldBody);
            case EXTRAS: return getString(R.string.SgFieldExtras);
            case TIME: return getString(R.string.SgFieldTime);
            case STATUS: return getString(R.string.SgFieldStatus);
            case REACTIONS: return getString(R.string.SgFieldReactions);
            default: return getString(R.string.SgFieldStats);
        }
    }

    private static String eventName(Feedback.Event e) {
        switch (e) {
            case SENT: return getString(R.string.SgEvSent);
            case FAILED: return getString(R.string.SgEvFailed);
            case INCOMING: return getString(R.string.SgEvIncoming);
            case RECORD_START: return getString(R.string.SgEvRecStart);
            case RECORD_STOP: return getString(R.string.SgEvRecStop);
            case DOWNLOAD_DONE: return getString(R.string.SgEvDownload);
            case EDGE: return getString(R.string.SgEvEdge);
            case INCOMING_CALL: return getString(R.string.SgEvIncomingCall);
            default: return getString(R.string.SgEvActionOk);
        }
    }

    private static String feedbackModeName(Feedback.Mode m) {
        switch (m) {
            case SOUND: return getString(R.string.SgModeSound);
            case VIBRATE: return getString(R.string.SgModeVibrate);
            case OFF: return getString(R.string.SgModeOff);
            default: return getString(R.string.SgModeBoth);
        }
    }

    private static String announceModeName(Announcer.Mode m) {
        switch (m) {
            case PRIVATE_AND_MENTIONS: return getString(R.string.SgAnnPrivateMentions);
            case SOUND_ONLY: return getString(R.string.SgAnnSoundOnly);
            case OFF: return getString(R.string.SgAnnOff);
            default: return getString(R.string.SgAnnAll);
        }
    }

    private static String langName(String l) {
        switch (l) {
            case "zh": return getString(R.string.SgLangZh);
            case "en": return getString(R.string.SgLangEn);
            default: return getString(R.string.SgLangSystem);
        }
    }

    // ---- move up / down accessibility actions on field rows ----

    private final class MoveDelegate extends AccessibilityDelegateCompat {
        private SpeechField fieldOf(View host) {
            if (listView == null || listView.adapter == null) return null;
            int pos = listView.getChildAdapterPosition(host);
            if (pos < 0) return null;
            UItem item = listView.adapter.getItem(pos);
            if (item == null || item.id < ID_FIELD || item.id >= ID_FIELD + SpeechField.values().length) return null;
            return SpeechField.values()[item.id - ID_FIELD];
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            SpeechField f = fieldOf(host);
            if (f == null) return;
            List<SpeechField> order = TransGramA11y.speech().order();
            int i = order.indexOf(f);
            if (i > 0) {
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.sg_move_up, getString(R.string.SgMoveUp)));
            }
            if (i >= 0 && i < order.size() - 1) {
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.sg_move_down, getString(R.string.SgMoveDown)));
            }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (action == R.id.sg_move_up || action == R.id.sg_move_down) {
                SpeechField f = fieldOf(host);
                if (f == null) return false;
                TransGramA11y.setSpeech(TransGramA11y.speech().move(f, action == R.id.sg_move_up ? -1 : 1));
                int pos = TransGramA11y.speech().order().indexOf(f) + 1;
                refresh();
                AndroidUtilities.makeAccessibilityAnnouncement(formatString(R.string.SgMovedTo, pos));
                return true;
            }
            return super.performAccessibilityAction(host, action, args);
        }
    }
}
