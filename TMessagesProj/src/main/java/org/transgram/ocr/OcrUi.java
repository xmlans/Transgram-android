package org.transgram.ocr;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.transgram.a11y.Announcer;
import org.transgram.a11y.Feedback;
import org.transgram.a11y.TransGramA11y;

import java.io.File;

/**
 * Everything the user sees and hears around one text recognition: the screen-reader action's availability, the model
 * download (confirmation, mobile-data question, spoken progress), waiting for the picture to download, the spoken
 * result, the "text in image" sheet and the spoken errors. Main thread only.
 */
public final class OcrUi {
    /** Percent of the running model download, or -1 when nothing is downloading. */
    private static int downloadPercent = -1;
    private static java.lang.ref.WeakReference<BaseFragment> downloadOwner = new java.lang.ref.WeakReference<>(null);
    private static Runnable stateListener;
    private static java.lang.ref.WeakReference<AlertDialog> progressDialog = new java.lang.ref.WeakReference<>(null);
    private static long downloadedBytes, downloadTotalBytes;
    private static java.util.concurrent.atomic.AtomicBoolean downloadCancel;
    private static ModelDownloader.Reason lastFailure;
    public static long downloadedBytes() { return downloadedBytes; }
    public static long downloadTotalBytes() { return downloadTotalBytes; }
    public static ModelDownloader.Reason lastFailure() { return lastFailure; }
    public static boolean isCancelling() { return isDownloading() && downloadCancel != null && downloadCancel.get(); }
    public static void cancelDownload() {
        if (downloadCancel != null && isDownloading()) { downloadCancel.set(true); changed(); }
    }

    private OcrUi() {}

    // ---- download state, for the settings screen ----

    public static int downloadPercent() {
        return downloadPercent;
    }

    public static boolean isDownloading() {
        return downloadPercent >= 0;
    }

    /** The settings screen registers a refresh callback here while it is visible (null when it goes away). */
    public static void setStateListener(Runnable r) {
        stateListener = r;
    }

    public static void changed() {
        if (stateListener != null) stateListener.run();
        AlertDialog dialog = progressDialog.get();
        if (dialog != null) {
            if (!isDownloading()) { dialog.dismiss(); progressDialog.clear(); }
            else dialog.setMessage(LocaleController.formatString(R.string.SgOcrStatusDownloading, downloadPercent)
                    + "\n" + LocaleController.formatString(R.string.TgOcrBytes,
                    AndroidUtilities.formatFileSize(downloadedBytes), AndroidUtilities.formatFileSize(downloadTotalBytes)));
        }
    }

    public static void restoreProgress(BaseFragment fragment) {
        if (!isDownloading() || downloadOwner.get() != fragment || !alive(fragment)
                || fragment instanceof ModelManagerActivity) return;
        AlertDialog existing = progressDialog.get();
        if (existing != null && existing.isShowing()) return;
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(LocaleController.getString(R.string.SgOcrDlTitle))
                .setMessage(LocaleController.formatString(R.string.SgOcrStatusDownloading, downloadPercent))
                .setNegativeButton(LocaleController.getString(R.string.TgOcrCancel), (d,w) -> cancelDownload())
                .setOnCancelListener(d -> cancelDownload()).create();
        progressDialog = new java.lang.ref.WeakReference<>(dialog);
        fragment.showDialog(dialog);
        changed();
    }

    private static Runnable completion(BaseFragment f) {
        if (!alive(f) || f.getFragmentView() == null) return null;
        Object callback = f.getFragmentView().getTag(R.id.ocr_download_completion);
        return callback instanceof Runnable ? (Runnable) callback : null;
    }


    // ---- which messages offer the action ----

    private static OcrEligibility.Media describe(MessageObject m) {
        final OcrEligibility.Media d = new OcrEligibility.Media();
        final TLRPC.MessageMedia media = MessageObject.getMedia(m.messageOwner);
        d.photo = media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null;
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            d.documentMime = media.document.mime_type;
        }
        d.sticker = m.isAnyKindOfSticker();
        d.gif = m.isGif();
        d.secret = m.needDrawBluredPreview() || m.isSecretMedia();
        d.sending = m.getId() <= 0 || m.isSending();
        return d;
    }

    /** A photo or still-image file (not a sticker, GIF, self-destructing or unsent media). */
    public static boolean isPicture(MessageObject m) {
        return m != null && m.messageOwner != null && OcrEligibility.isImage(describe(m));
    }

    /** Spoken text of the picture element: "Picture", plus the recognized text when there is a cached result. */
    public static String imageNodeText(MessageObject m) {
        if (m == null) return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SgImage);
        if (m.isHiddenSensitive()) return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SgMediaHidden);
        final String text = RecognitionHub.isSupported() ? RecognitionHub.results().get(request(m, null)) : null;
        if (text == null || text.isEmpty()) return org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SgImage);
        return org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.SgImageWithText, text);
    }

    /** The "read text in image" action is offered on this message (master switch, OCR switch, API level, picture). */
    public static boolean canOcr(MessageObject m) {
        if (m == null || m.messageOwner == null) return false;
        return !m.isHiddenSensitive() && !ModelManagerActivity.isDeleting() && OcrEligibility.offer(TransGramA11y.isEnabled(), RecognitionSettings.ocrEnabled(), RecognitionHub.isSupported(), describe(m));
    }

    private static RecognitionRequest request(MessageObject m, File file) {
        return RecognitionRequest.ocr(m.currentAccount, m.getDialogId(), m.getId(), file == null ? null : file.getAbsolutePath());
    }

    /**
     * Text to read after a picture's caption ("read text in images together with the message"): only a result that is
     * already cached (nothing is recognised here), never for media hidden by the 18+ / sensitive mask. Null otherwise.
     */
    public static String textForSpeech(MessageObject m) {
        if (m == null || m.messageOwner == null || !RecognitionHub.isSupported() || !RecognitionSettings.speakOcr()) return null;
        if (m.isHiddenSensitive() || !OcrEligibility.isImage(describe(m))) return null;
        return RecognitionHub.results().get(request(m, null));
    }

    // ---- entry point ----

    /** Runs the whole flow: model (download on request), picture (download if needed), recognition, result. */
    public static void perform(final BaseFragment fragment, final MessageObject m) {
        if (fragment == null || fragment.getParentActivity() == null || m == null) return;
        if (!RecognitionHub.isSupported()) {
            speak(R.string.SgOcrErrUnsupported);
            return;
        }
        if (m.isHiddenSensitive()) {
            speak(R.string.SgOcrHidden);   // TransGram 18+ mask or Telegram's sensitive-content blur: never look inside
            return;
        }
        final RecognitionRequest probe = request(m, null);
        if (RecognitionHub.service().isRunning(probe)) {
            speak(R.string.SgOcrBusy);
            return;
        }
        if (RecognitionHub.results().has(probe)) {
            run(fragment, m, null, false);   // answered from the cache: no model and no file needed
            return;
        }
        ensureModel(fragment, new Runnable() {
            @Override public void run() {
                ensureMedia(fragment, m, new MediaReady() {
                    @Override public void onReady(File file) {
                        OcrUi.run(fragment, m, file, false);
                    }
                });
            }
        });
    }

    private static void run(final BaseFragment fragment, final MessageObject m, final File file, final boolean retried) {
        if (!alive(fragment) || !canOcr(m)) return;
        final RecognitionService.Submitted s = RecognitionHub.service().submit(request(m, file), new RecognitionService.Callback() {
            @Override public void onStarted() {
            }

            @Override public void onResult(String text, boolean fromCache) {
                deliver(fragment, text);
            }

            @Override public void onError(RecognitionError error, String packId) {
                if (error == RecognitionError.MODEL_MISSING && !retried) {
                    // the model was deleted between the check and the job: offer the download again, then retry
                    speak(R.string.SgOcrErrModelMissing);
                    ensureModel(fragment, new Runnable() {
                        @Override public void run() {
                            OcrUi.run(fragment, m, file, true);
                        }
                    });
                } else {
                    showRecognitionError(fragment, error);
                }
            }
        });
        if (s == RecognitionService.Submitted.ALREADY_RUNNING) {
            speak(R.string.SgOcrBusy);
        } else if (s == RecognitionService.Submitted.STARTED) {
            speak(R.string.SgOcrRecognizing);
        }
    }

    // ---- result ----

    private static boolean alive(BaseFragment f) {
        return f != null && !f.isFinished && f.getParentActivity() != null;
    }

    private static void deliver(BaseFragment fragment, String text) {
        Feedback.play(Feedback.Event.ACTION_OK);
        TransGramA11y.speechVersion++;   // photo cells rebuild their spoken text (with the image text, if enabled)
        if (!alive(fragment)) {
            speak(R.string.SgOcrResult, text);
            return;
        }
        final String[] lines = text.split("\n");
        speak(R.string.SgOcrDone, lines.length);
        showSheet(fragment, text, lines);
    }

    private static void showSheet(BaseFragment fragment, final String text, String[] lines) {
        final Context ctx = fragment.getParentActivity();
        final int pad = AndroidUtilities.dp(16);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, AndroidUtilities.dp(8));

        final TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.SgOcrTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        ViewCompat.setAccessibilityHeading(title, true);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // One TextView per line, so the screen reader moves through the text line by line.
        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        for (String line : lines) {
            if (TextUtils.isEmpty(line.trim())) continue;
            final TextView tv = new TextView(ctx);
            tv.setText(line);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            tv.setTextIsSelectable(true);
            tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            tv.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            body.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        final ScrollView scroll = new ScrollView(ctx) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int max = (int) (AndroidUtilities.displaySize.y * 0.6f);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setCustomView(root);
        final BottomSheet sheet = builder.create();

        final LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.addView(button(ctx, R.string.SgOcrCopy, new View.OnClickListener() {
            @Override public void onClick(View v) {
                AndroidUtilities.addToClipboard(text);
                speak(R.string.SgOcrCopied);
            }
        }));
        buttons.addView(button(ctx, R.string.SgOcrClose, new View.OnClickListener() {
            @Override public void onClick(View v) {
                sheet.dismiss();
            }
        }));
        root.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        fragment.showDialog(sheet);
    }

    private static Button button(Context ctx, int labelRes, View.OnClickListener l) {
        final Button b = new Button(ctx);
        b.setText(LocaleController.getString(labelRes));
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        b.setMinimumHeight(AndroidUtilities.dp(48));
        b.setMinimumWidth(AndroidUtilities.dp(96));
        b.setOnClickListener(l);
        return b;
    }

    // ---- model: confirm, download, announce ----

    /** Runs `then` when the OCR pack is installed: at once, or after the user confirms and the download finishes. */
    public static void ensureModel(final BaseFragment fragment, final Runnable then) {
        final ModelManifest.Pack pack;
        try {
            pack = RecognitionEnv.ocrPack();
        } catch (ModelManifest.ManifestException e) {
            speak(R.string.SgOcrErrEngine);
            return;
        }
        if (RecognitionEnv.store().isInstalled(pack)) {
            if (then != null) then.run();
            return;
        }
        if (isDownloading() || ModelManagerActivity.isDeleting()) {
            restoreProgress(fragment);
            speak(R.string.SgOcrDlNow);
            return;
        }
        if (!alive(fragment) || ModelManagerActivity.isDeleting()) return;
        final long remaining = Math.max(0, pack.totalBytes() - RecognitionEnv.store().partialBytes(pack));
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(LocaleController.getString(R.string.SgOcrDlTitle))
                .setMessage(LocaleController.formatString(R.string.SgOcrDlMessage, AndroidUtilities.formatFileSize(remaining)))
                .setPositiveButton(LocaleController.getString(R.string.SgOcrDlYes), new AlertDialog.OnButtonClickListener() {
                    @Override public void onClick(AlertDialog dialog, int which) {
                        startDownload(fragment, pack, false, then);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.SgOcrDlNo), null)
                .create());
    }

    /** Downloads the pack on a worker thread, speaking at the start, every 25% and at the end. */
    private static void startDownload(final BaseFragment fragment, final ModelManifest.Pack pack, final boolean allowMetered, final Runnable then) {
        if (isDownloading() || ModelManagerActivity.isDeleting()) {
            restoreProgress(fragment);
            speak(R.string.SgOcrDlNow);
            return;
        }
        downloadPercent = 0;
        downloadedBytes = RecognitionEnv.store().partialBytes(pack);
        downloadTotalBytes = pack.totalBytes();
        lastFailure = null;
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        downloadCancel = cancelled;
        changed();
        speak(R.string.SgOcrDlStart);
        final ProgressSteps steps = new ProgressSteps();
        final java.lang.ref.WeakReference<BaseFragment> owner = new java.lang.ref.WeakReference<>(fragment);
        if (fragment != null && fragment.getFragmentView() != null) fragment.getFragmentView().setTag(R.id.ocr_download_completion, then);
        downloadOwner = owner;
        restoreProgress(fragment);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    RecognitionEnv.downloader().download(pack, allowMetered, new ModelDownloader.Listener() {
                        @Override public void onProgress(long done, long total) {
                            final int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                            final int milestone = steps.update(done, total);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override public void run() {
                                    if (downloadPercent >= 0 && downloadPercent != pct) {
                                        downloadPercent = pct;
                                        downloadedBytes = done;
                                        downloadTotalBytes = total;
                                        changed();
                                    }
                                    if (milestone > 0 && milestone < 100) speak(R.string.SgOcrDlProgress, milestone);
                                }
                            });
                        }
                    }, cancelled::get);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override public void run() {
                            downloadPercent = -1;
                            changed();
                            Feedback.play(Feedback.Event.DOWNLOAD_DONE);
                            speak(R.string.SgOcrDlDone);
                            Runnable callback = completion(owner.get());
                            if (callback != null) callback.run();
                        }
                    });
                } catch (final ModelDownloader.DownloadException e) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override public void run() {
                            downloadPercent = -1;
                            lastFailure = e.reason;
                            changed();
                            onDownloadFailed(owner.get(), pack, e, completion(owner.get()));
                        }
                    });
                } catch (final RuntimeException e) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override public void run() {
                            downloadPercent = -1;
                            lastFailure = ModelDownloader.Reason.ALL_SOURCES_FAILED;
                            changed();
                            showDownloadError(owner.get(), pack, R.string.SgOcrDlFailed, completion(owner.get()));
                        }
                    });
                }
            }
        }, "tg-ocr-model-download").start();
    }

    private static void onDownloadFailed(final BaseFragment fragment, final ModelManifest.Pack pack, ModelDownloader.DownloadException e, final Runnable then) {
        switch (e.reason) {
            case NEEDS_UNMETERED:
                if (!alive(fragment)) {
                    speak(R.string.SgOcrDlFailed);
                    return;
                }
                fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                        .setTitle(LocaleController.getString(R.string.SgOcrDlTitle))
                        .setMessage(LocaleController.formatString(R.string.SgOcrDlMetered,
                                AndroidUtilities.formatFileSize(Math.max(0, pack.totalBytes() - RecognitionEnv.store().partialBytes(pack)))))
                        .setPositiveButton(LocaleController.getString(R.string.SgOcrDlYes), new AlertDialog.OnButtonClickListener() {
                            @Override public void onClick(AlertDialog dialog, int which) {
                                startDownload(fragment, pack, true, then);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.SgOcrDlNo), null)
                        .create());
                break;
            case NO_SPACE:
                showDownloadError(fragment, pack, R.string.SgOcrDlNoSpace, then, AndroidUtilities.formatFileSize(pack.totalBytes() + ModelDownloader.SPACE_MARGIN));
                break;
            case VERIFY_FAILED:
                showDownloadError(fragment, pack, R.string.SgOcrDlVerify, then);
                break;
            case CANCELLED:
                break;
            default:
                showDownloadError(fragment, pack, R.string.SgOcrDlFailed, then);
                break;
        }
    }

    private static void showDownloadError(BaseFragment fragment, ModelManifest.Pack pack, int res, Runnable then, Object... args) {
        String message = args.length == 0 ? LocaleController.getString(res) : LocaleController.formatString(res, args);
        speak(res, args);
        if (!alive(fragment)) return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(LocaleController.getString(R.string.SgOcrDlTitle))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.TgOcrRetry), (d, w) -> startDownload(fragment, pack, false, then))
                .setNegativeButton(LocaleController.getString(R.string.SgOcrClose), null).create());
    }

    // ---- picture: use the local file, or download it first and continue when it arrives ----

    interface MediaReady {
        void onReady(File file);
    }

    static File localFile(MessageObject m) {
        final String attach = m.messageOwner.attachPath;
        if (attach != null && attach.length() > 0) {
            final File f = new File(attach);
            if (f.exists()) return f;
        }
        final File f = FileLoader.getInstance(m.currentAccount).getPathToMessage(m.messageOwner);
        return f != null && f.exists() && f.length() > 0 ? f : null;
    }

    private static void ensureMedia(final BaseFragment fragment, final MessageObject m, final MediaReady ready) {
        final File existing = localFile(m);
        if (existing != null) {
            ready.onReady(existing);
            return;
        }
        final int account = m.currentAccount;
        final TLRPC.MessageMedia media = MessageObject.getMedia(m.messageOwner);
        final String fileName;
        final Runnable load;
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            final TLRPC.Document doc = media.document;
            fileName = FileLoader.getAttachFileName(doc);
            speak(R.string.SgOcrWaitMedia);
            Announcer.watchDownload(doc);
            load = () -> FileLoader.getInstance(account).loadFile(doc, m, FileLoader.PRIORITY_NORMAL, 0);
        } else {
            final TLRPC.Photo photo = media != null ? media.photo : null;
            final TLRPC.PhotoSize size = photo == null ? null
                    : FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
            if (size == null) {
                showRecognitionError(fragment, RecognitionError.DECODE_FAILED);
                return;
            }
            fileName = FileLoader.getAttachFileName(size);
            speak(R.string.SgOcrWaitMedia);
            Announcer.watchDownload(size);
            load = () -> FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(size, photo), m, null, FileLoader.PRIORITY_NORMAL, 0);
        }
        if (fileName == null) {
            showRecognitionError(fragment, RecognitionError.DECODE_FAILED);
            return;
        }
        final boolean[] completed = {false};
        final Runnable[] timeout = new Runnable[1];
        final NotificationCenter nc = NotificationCenter.getInstance(account);
        final NotificationCenter.NotificationCenterDelegate waiter = new NotificationCenter.NotificationCenterDelegate() {
            @Override public void didReceivedNotification(int id, int acc, Object... args) {
                if (completed[0] || args.length == 0 || !fileName.equals(args[0])) return;
                completed[0] = true;
                AndroidUtilities.cancelRunOnUIThread(timeout[0]);
                nc.removeObserver(this, NotificationCenter.fileLoaded);
                nc.removeObserver(this, NotificationCenter.fileLoadFailed);
                if (id == NotificationCenter.fileLoaded) {
                    final File f = localFile(m);
                    if (f != null) ready.onReady(f);
                    else showRecognitionError(fragment, RecognitionError.DECODE_FAILED);
                } else {
                    showRecognitionError(fragment, RecognitionError.DECODE_FAILED);
                }
            }
        };
        timeout[0] = () -> {
            if (completed[0]) return;
            completed[0] = true;
            nc.removeObserver(waiter, NotificationCenter.fileLoaded);
            nc.removeObserver(waiter, NotificationCenter.fileLoadFailed);
            showRecognitionError(fragment, RecognitionError.DECODE_FAILED);
        };
        nc.addObserver(waiter, NotificationCenter.fileLoaded);
        nc.addObserver(waiter, NotificationCenter.fileLoadFailed);
        AndroidUtilities.runOnUIThread(timeout[0], 120_000);
        // Register before starting: a cached/local file may finish immediately.
        File now = localFile(m);
        if (now != null) waiter.didReceivedNotification(NotificationCenter.fileLoaded, account, fileName);
        else load.run();
    }

    // ---- speech ----

    public static void speak(int res, Object... args) {
        AndroidUtilities.makeAccessibilityAnnouncement(args.length == 0
                ? LocaleController.getString(res) : LocaleController.formatString(res, args));
    }

    private static void showRecognitionError(BaseFragment fragment, RecognitionError error) {
        int res;
        switch (error) {
            case MODEL_MISSING: res = R.string.SgOcrErrModelMissing; break;
            case DECODE_FAILED: res = R.string.SgOcrErrDecode; break;
            case EMPTY: res = R.string.SgOcrErrEmpty; break;
            case OUT_OF_MEMORY: res = R.string.SgOcrErrOom; break;
            case UNSUPPORTED: res = R.string.SgOcrErrUnsupported; break;
            default: res = R.string.SgOcrErrEngine;
        }
        speak(res);
        if (alive(fragment)) fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(LocaleController.getString(R.string.SgOcrTitle))
                .setMessage(LocaleController.getString(res))
                .setPositiveButton(LocaleController.getString(R.string.SgOcrClose), null).create());
    }

    public static void speakError(RecognitionError error) {
        switch (error) {
            case MODEL_MISSING: speak(R.string.SgOcrErrModelMissing); break;
            case DECODE_FAILED: speak(R.string.SgOcrErrDecode); break;
            case EMPTY: speak(R.string.SgOcrErrEmpty); break;
            case OUT_OF_MEMORY: speak(R.string.SgOcrErrOom); break;
            case UNSUPPORTED: speak(R.string.SgOcrErrUnsupported); break;
            default: speak(R.string.SgOcrErrEngine); break;
        }
    }
}
