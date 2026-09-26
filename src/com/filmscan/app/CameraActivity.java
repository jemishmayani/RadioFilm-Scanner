package com.filmscan.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.filmscan.core.EdgeDetector;
import com.filmscan.core.Geom;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Home screen: a document camera that captures at the sensor's full JPEG resolution. */
public final class CameraActivity extends BaseActivity {
    private static final String TAG = "FilmScan";
    private static final int REQ_CAMERA = 1, REQ_IMPORT = 2;
    public static final String EXTRA_SAVED = "savedName", EXTRA_ADD = "addToGroup", EXTRA_WHERE = "savedWhere";
    private boolean addMode;          // opened from the editor to add pages; returns there
    private String firstNewId;        // first page shot since the editor was last opened
    private boolean lowStorageWarned;
    private LinearLayout addBar;
    private static final int ST_PREVIEW = 0, ST_WAIT_LOCK = 1, ST_WAIT_PRE = 2, ST_WAIT_NON_PRE = 3, ST_TAKEN = 4;

    private App app;
    private Prefs prefs;
    private Session session;

    // views
    private AutoFitTextureView tex;
    private EdgeOverlayView overlay;
    private ImageView flashBtn, gridBtn, settingsBtn, savedBtn, galleryBtn, pagesThumb;
    private FrameLayout pagesBtn, shutter;
    private View shutterInner, flashFx;
    private TextView pagesBadge, hint, modeSingle, modeBatch, resLabel;
    private LinearLayout permPanel;
    private final List<View> rotatables = new ArrayList<View>();

    // camera
    private HandlerThread camThread;
    private Handler camHandler;
    private final Semaphore openLock = new Semaphore(1);
    private volatile CameraDevice camera;
    private volatile CameraCaptureSession capSession;
    private volatile ImageReader reader;
    private CaptureRequest.Builder previewBuilder;
    private Surface previewSurface;
    private String cameraId;
    private Size previewSize, jpegSize, jpegStdSize;
    private boolean usingHighRes, opening;
    private int sensorOrientation = 90;
    private boolean flashAvailable, afContinuous, afAuto, fixedFocus, nrHq, edgeHq;
    private Rect activeArray;
    private int maxAfRegions, maxAeRegions;
    private volatile int state = ST_PREVIEW;
    private long waitStart;
    private volatile boolean capturing;
    private volatile boolean manualFocus;
    private volatile MeteringRectangle[] focusRegions;
    private int deviceOrientation;
    private OrientationEventListener orientationListener;
    private boolean resumed, askedPermission;
    private MediaActionSound sound;

    // live detection
    private final ExecutorService detectExec = Executors.newSingleThreadExecutor();
    private Bitmap detBmp;
    private boolean detBusy;
    private float[] smooth, lastRaw, capturedQuad;
    private int missCount;
    private long steadySince, cooldownUntil;
    private String hintText = "";

    public static Intent intent(Context c) {
        return new Intent(c, CameraActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /** Camera that adds pages to the saved scan being edited, then returns. */
    public static Intent intentAdd(Context c) {
        return new Intent(c, CameraActivity.class).putExtra(EXTRA_ADD, true);
    }

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        prefs = app.prefs();
        addMode = getIntent().getBooleanExtra(EXTRA_ADD, false);
        if (b != null) firstNewId = b.getString("firstNewId");
        if (addMode) setBackHandler(new Runnable() { @Override public void run() { finishAdd(firstNewId); } });
        session = app.session();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams wlp = getWindow().getAttributes();
        wlp.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        getWindow().setAttributes(wlp);
        buildUi();
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int o) {
                if (o == ORIENTATION_UNKNOWN) return;
                int r = ((o + 45) / 90 * 90) % 360;
                if (r != deviceOrientation) { deviceOrientation = r; rotateIcons(); }
            }
        };
        try {
            sound = new MediaActionSound();
            sound.load(MediaActionSound.SHUTTER_CLICK);
        } catch (Throwable t) {
            sound = null;
        }
        boolean fromOutside = getIntent() != null && (Intent.ACTION_SEND.equals(getIntent().getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction()) || getIntent().hasExtra(EXTRA_SAVED));
        handleShareIntent(getIntent());
        handleSavedIntent(getIntent());
        if (!addMode && b == null && !fromOutside && !app.recoveryChecked) offerRecovery();
        app.recoveryChecked = true;
    }

    /**
     * Fresh start with work left over (after a crash, or Android closing the app in the background):
     * offer to resume it or discard it. Everything was already saved to disk after each change.
     */
    private void offerRecovery() {
        final String editingId = prefs.editingId();
        final SavedStore.Entry entry = editingId == null ? null : app.saved().find(editingId);
        final Session main = app.mainSession();
        final List<Page> pages;
        final long when;
        if (entry != null) {
            pages = app.saved().pages(entry);
            when = new File(entry.dir, "pages.json").lastModified();
        } else {
            pages = new ArrayList<Page>(main.pages);
            when = main.lastSaved();
        }
        if (pages.isEmpty()) return;
        app.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                showRecoverySheet(entry, pages, when);
            }
        }, 450);
    }

    private void showRecoverySheet(final SavedStore.Entry entry, final List<Page> pages, long when) {
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        c.setPadding(pad, 0, pad, 0);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(this);
        badge.setImageDrawable(Ui.icon(this, R.drawable.ic_restore, Ui.ON_ACCENT));
        badge.setScaleType(ImageView.ScaleType.CENTER);
        badge.setBackground(Ui.oval(Ui.ACCENT));
        head.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 14), 0, 0, 0);
        titles.addView(Ui.text(this, entry != null ? "Continue editing?" : "Unfinished scan", 19, Ui.LIGHT, true));
        String count = pages.size() == 1 ? "1 page" : pages.size() + " pages";
        String date = when > 0 ? " · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(when)) : "";
        TextView sub = Ui.text(this, (entry != null ? "\u201c" + entry.name + "\u201d · " : "") + count + date, 13, Ui.MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sub);
        head.addView(titles, Ui.weight(1));
        c.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // thumbnails of what will be resumed
        LinearLayout thumbs = new LinearLayout(this);
        thumbs.setPadding(0, Ui.dp(this, 16), 0, 0);
        for (int i = 0; i < Math.min(5, pages.size()); i++) {
            final ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(Ui.round(0xFF22384D, Ui.dp(this, 8)));
            int p = Ui.dp(this, 3);
            iv.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 68));
            lp.rightMargin = Ui.dp(this, 8);
            thumbs.addView(iv, lp);
            final Page snap = pages.get(i).copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap t = Imaging.renderFinished(snap, 200);
                    app.ui(new Runnable() { @Override public void run() { if (t != null) iv.setImageBitmap(t); } });
                }
            });
        }
        if (pages.size() > 5) {
            TextView more = Ui.text(this, "+" + (pages.size() - 5), 14, Ui.MUTED, true);
            more.setGravity(Gravity.CENTER);
            thumbs.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 68)));
        }
        c.addView(thumbs);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        final TextView discard = Ui.button(this, entry != null ? "Close" : "Discard", false);
        TextView resume = Ui.button(this, "Resume", true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
        dl.rightMargin = Ui.dp(this, 12);
        actions.addView(discard, dl);
        actions.addView(resume, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.topMargin = Ui.dp(this, 20);
        c.addView(actions, al);
        if (entry != null) {
            TextView note = Ui.text(this, "Your changes are already kept in this saved scan.", 12.5f, Ui.MUTED, false);
            note.setPadding(0, Ui.dp(this, 10), 0, 0);
            c.addView(note);
        }
        holder[0] = Ui.sheet(this, c);
        Ui.reveal(thumbs, 60);
        resume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder[0].dismiss();
                Session s = entry != null ? app.beginEdit(entry) : app.mainSession();
                if (s.pages.isEmpty()) return;
                startActivity(EditActivity.intent(CameraActivity.this, s.pages.get(0).id));
            }
        });
        final boolean[] armed = {false};
        discard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (entry != null) {                       // edits already live in the saved scan
                    app.endEdit();
                    holder[0].dismiss();
                    return;
                }
                if (!armed[0]) {                           // second tap confirms a discard
                    armed[0] = true;
                    discard.setText("Tap again to discard");
                    discard.setTextColor(Ui.DANGER);
                    app.main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            armed[0] = false;
                            discard.setText("Discard");
                            discard.setTextColor(Ui.LIGHT);
                        }
                    }, 3000);
                    return;
                }
                app.mainSession().clear();
                holder[0].dismiss();
                refreshPagesButton();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("firstNewId", firstNewId);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleShareIntent(i);
        handleSavedIntent(i);
    }

    /** After a batch is saved we come back here for a new scan; confirm where it went. */
    private void handleSavedIntent(Intent i) {
        final String name = i == null ? null : i.getStringExtra(EXTRA_SAVED);
        if (name == null) return;
        i.removeExtra(EXTRA_SAVED);
        smooth = null;
        overlay.setQuad(null);
        String where = i.getStringExtra(EXTRA_WHERE);
        i.removeExtra(EXTRA_WHERE);
        showHint("Saved \u201c" + name + "\u201d" + (where != null ? " to " + where : "") + ". Also in Saved scans (top bar).", 5500);
        savedBtn.getDrawable().setTint(Ui.ACCENT);
        savedBtn.animate().scaleX(1.25f).scaleY(1.25f).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                savedBtn.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                app.main.postDelayed(new Runnable() {
                    @Override public void run() { savedBtn.getDrawable().setTint(Ui.LIGHT); }
                }, 4000);
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (!addMode && app.isEditingSaved()) app.endEdit();   // back at a fresh camera: new scans only
        session = app.session();
        startCameraThread();
        if (hasCameraPermission()) {
            Ui.setVisible(permPanel, false);
            if (tex.isAvailable()) openCamera();
        } else if (!askedPermission && Build.VERSION.SDK_INT >= 23) {
            askedPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            Ui.setVisible(permPanel, true);
        }
        if (orientationListener.canDetectOrientation()) orientationListener.enable();
        ((android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE))
                .registerDisplayListener(displayListener, app.main);
        rotateIcons();
        applyModeUi();
        overlay.setGrid(prefs.grid());
        gridBtn.getDrawable().setTint(prefs.grid() ? Ui.ACCENT : Ui.LIGHT);
        updateFlashIcon();
        refreshPagesButton();
        app.main.removeCallbacks(detectTick);
        app.main.postDelayed(detectTick, 400);
    }

    @Override
    protected void onPause() {
        resumed = false;
        app.main.removeCallbacks(detectTick);
        app.main.removeCallbacks(revertFocus);
        app.main.removeCallbacks(captureWatchdog);
        orientationListener.disable();
        ((android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE))
                .unregisterDisplayListener(displayListener);
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        detectExec.shutdownNow();
        if (sound != null) sound.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req != REQ_CAMERA) return;
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        Ui.setVisible(permPanel, !ok);
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // ================================================================== UI

    private void buildUi() {
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // ---- tools
        flashBtn = Ui.iconButton(this, R.drawable.ic_flash_off, "Flash");
        gridBtn = Ui.iconButton(this, R.drawable.ic_grid, "Framing grid");
        settingsBtn = Ui.iconButton(this, R.drawable.ic_settings, "Settings");
        savedBtn = Ui.iconButton(this, R.drawable.ic_saved, "Saved scans");
        resLabel = Ui.text(this, "", 12.5f, Ui.MUTED, false);
        resLabel.setGravity(Gravity.CENTER);
        rotatables.add(flashBtn); rotatables.add(gridBtn); rotatables.add(savedBtn); rotatables.add(settingsBtn);

        // ---- preview area
        FrameLayout preview = new FrameLayout(this);
        tex = new AutoFitTextureView(this);
        preview.addView(tex, Ui.frame(-1, -1, Gravity.CENTER));
        overlay = new EdgeOverlayView(this, tex);
        preview.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        flashFx = new View(this);
        flashFx.setBackgroundColor(0xFFFFFFFF);
        flashFx.setAlpha(0f);
        flashFx.setClickable(false);
        preview.addView(flashFx, new FrameLayout.LayoutParams(-1, -1));
        hint = Ui.text(this, "", 13.5f, Ui.LIGHT, false);
        hint.setBackground(Ui.round(0x99000000, Ui.dp(this, 16)));
        hint.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        hint.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams hlp = Ui.frame(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = Ui.dp(this, 14);
        preview.addView(hint, hlp);
        addBar = new LinearLayout(this);
        addBar.setGravity(Gravity.CENTER_VERTICAL);
        addBar.setBackground(Ui.round(0xE6152A3E, Ui.dp(this, 22)));
        addBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        SavedStore.Entry ed = app.editingEntry();
        TextView at = Ui.text(this, ed == null ? "Adding pages" : "Adding to \u201c" + ed.name + "\u201d", 13.5f, Ui.LIGHT, false);
        at.setSingleLine(true);
        at.setEllipsize(android.text.TextUtils.TruncateAt.END);
        at.setMaxWidth(Ui.dp(this, 210));
        addBar.addView(at);
        TextView addDone = Ui.button(this, "Done", true);
        addDone.setMinHeight(Ui.dp(this, 36));
        addDone.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 18)), true));
        addDone.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        LinearLayout.LayoutParams adl = new LinearLayout.LayoutParams(-2, Ui.dp(this, 36));
        adl.leftMargin = Ui.dp(this, 10);
        addBar.addView(addDone, adl);
        FrameLayout.LayoutParams abl = Ui.frame(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        abl.bottomMargin = Ui.dp(this, 14);
        preview.addView(addBar, abl);
        addBar.setVisibility(addMode ? View.VISIBLE : View.GONE);
        addDone.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finishAdd(firstNewId); } });
        permPanel = buildPermissionPanel();
        preview.addView(permPanel, Ui.frame(-1, -2, Gravity.CENTER));
        permPanel.setVisibility(View.GONE);

        // ---- capture controls
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modeSingle = modeChip("Single");
        modeBatch = modeChip("Batch");
        modes.addView(modeSingle);
        View gap = new View(this);
        modes.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        modes.addView(modeBatch);

        galleryBtn = new ImageView(this);
        galleryBtn.setImageDrawable(Ui.icon(this, R.drawable.ic_gallery, Ui.LIGHT));
        galleryBtn.setScaleType(ImageView.ScaleType.CENTER);
        galleryBtn.setBackground(Ui.ripple(Ui.oval(0xFF1B2733), true));
        galleryBtn.setContentDescription("Import from gallery");
        rotatables.add(galleryBtn);

        shutter = new FrameLayout(this);
        View ring = new View(this);
        android.graphics.drawable.GradientDrawable rd = Ui.oval(0x00000000);
        rd.setStroke(Ui.dp(this, 4), Ui.LIGHT);
        ring.setBackground(rd);
        shutter.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        shutterInner = new View(this);
        shutterInner.setBackground(Ui.oval(Ui.LIGHT));
        shutter.addView(shutterInner, Ui.frame(Ui.dp(this, 62), Ui.dp(this, 62), Gravity.CENTER));
        shutter.setContentDescription("Take photo");
        shutter.setClickable(true);

        pagesBtn = new FrameLayout(this);
        pagesThumb = new ImageView(this);
        pagesThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pagesThumb.setBackground(Ui.round(Ui.PANEL, Ui.dp(this, 10)));
        pagesThumb.setClipToOutline(true);
        pagesBtn.addView(pagesThumb, Ui.frame(Ui.dp(this, 50), Ui.dp(this, 50), Gravity.CENTER));
        pagesBadge = Ui.text(this, "", 11, Ui.ON_ACCENT, true);
        pagesBadge.setGravity(Gravity.CENTER);
        pagesBadge.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 10)));
        pagesBadge.setMinWidth(Ui.dp(this, 20));
        pagesBadge.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
        pagesBtn.addView(pagesBadge, Ui.frame(-2, Ui.dp(this, 20), Gravity.TOP | Gravity.END));
        pagesBtn.setContentDescription("Review pages");
        pagesBtn.setClickable(true);
        rotatables.add(pagesBtn);

        rootView = root;
        previewArea = preview;
        modesRow = modes;
        // the preview is never moved again: moving it would shut the camera feed down
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        arrange(landscape);
        setContentView(root);

        // re-apply the preview transform whenever the preview is laid out again
        tex.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) {
                    if (tex.isAvailable()) keepBufferSize(tex.getSurfaceTexture());
                    configureTransform(r - l, b - t);
                }
            }
        });

        tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { if (resumed) openCamera(); }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                // TextureView resets the feed's picture size to its own size whenever it is resized;
                // the camera keeps sending the configured size, which stretches the picture. Restore it.
                keepBufferSize(st);
                configureTransform(w, h);
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    float x = e.getX() - tex.getLeft(), y = e.getY() - tex.getTop();
                    if (x >= 0 && y >= 0 && x <= tex.getWidth() && y <= tex.getHeight()) {
                        overlay.showFocus(e.getX(), e.getY());
                        focusAt(x, y);
                    }
                }
                return true;
            }
        });
        shutter.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) shutterInner.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).start();
                else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            }
        });
        shutter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { takePicture(); }
        });
        galleryBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickImages(); }
        });
        pagesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (addMode) { finishAdd(firstNewId); return; }
                // straight into the editor at the first new page, in crop mode
                Page start = session.find(firstNewId);
                if (start == null && !session.pages.isEmpty()) start = session.pages.get(session.pages.size() - 1);
                firstNewId = null;
                if (start != null) startActivity(EditActivity.intent(CameraActivity.this, start.id));
            }
        });
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleFlash(); }
        });
        gridBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean g = !prefs.grid();
                prefs.grid(g);
                overlay.setGrid(g);
                gridBtn.getDrawable().setTint(g ? Ui.ACCENT : Ui.LIGHT);
            }
        });
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(SettingsActivity.intent(CameraActivity.this, cameraInfo())); }
        });
        if (addMode) savedBtn.setVisibility(View.GONE);
        savedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(CameraActivity.this, SavedActivity.class)); }
        });
        modeSingle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.batch(false); applyModeUi(); showHint("Single: review each photo right away", 2200); }
        });
        modeBatch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.batch(true); applyModeUi(); showHint("Batch: shoot several films, review at the end", 2600); }
        });
    }

    private FrameLayout rootView, previewArea;
    private LinearLayout modesRow;
    private final List<View> chrome = new ArrayList<View>();
    private boolean arrangedLandscape;

    private static void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    /**
     * Places the controls around the preview: bars above and below in portrait; tools on the left and
     * capture controls on the right in landscape (like a camera app). Only the controls move, so
     * turning a tablet doesn't restart the camera.
     */
    private void arrange(boolean land) {
        arrangedLandscape = land;
        for (View v : chrome) rootView.removeView(v);
        chrome.clear();
        for (View v : new View[]{flashBtn, gridBtn, resLabel, savedBtn, settingsBtn, modesRow, galleryBtn, shutter, pagesBtn}) detach(v);
        modesRow.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) previewArea.getLayoutParams();
        if (land) {
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setGravity(Gravity.CENTER_HORIZONTAL);
            left.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            left.addView(flashBtn);
            left.addView(gridBtn);
            left.addView(spacerV(), new LinearLayout.LayoutParams(1, 0, 1f));
            left.addView(resLabel, new LinearLayout.LayoutParams(-2, -2));
            left.addView(spacerV(), new LinearLayout.LayoutParams(1, 0, 1f));
            left.addView(savedBtn);
            left.addView(settingsBtn);
            addChrome(left, Ui.frame(Ui.dp(this, 76), -1, Gravity.START));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.CENTER_HORIZONTAL);
            right.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
            right.addView(pagesBtn, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60)));
            right.addView(spacerV(), new LinearLayout.LayoutParams(1, 0, 1f));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
            ml.bottomMargin = Ui.dp(this, 16);
            right.addView(modesRow, ml);
            right.addView(shutter, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));
            right.addView(spacerV(), new LinearLayout.LayoutParams(1, 0, 1f));
            right.addView(galleryBtn, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
            addChrome(right, Ui.frame(Ui.dp(this, 132), -1, Gravity.END));
            plp.setMargins(Ui.dp(this, 76), 0, Ui.dp(this, 132), 0);
        } else {
            LinearLayout top = Ui.topBar(this);
            top.setBackgroundColor(0xFF000000);
            top.addView(flashBtn);
            top.addView(gridBtn);
            top.addView(resLabel, Ui.weight(1));
            top.addView(savedBtn);
            top.addView(settingsBtn);
            addChrome(top, Ui.frame(-1, Ui.dp(this, 56), Gravity.TOP));
            LinearLayout bottom = new LinearLayout(this);
            bottom.setOrientation(LinearLayout.VERTICAL);
            bottom.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
            bottom.addView(modesRow, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
            FrameLayout controls = new FrameLayout(this);
            int side = Ui.dp(this, 32);
            FrameLayout.LayoutParams glp = Ui.frame(Ui.dp(this, 54), Ui.dp(this, 54), Gravity.CENTER_VERTICAL | Gravity.START);
            glp.leftMargin = side;
            controls.addView(galleryBtn, glp);
            controls.addView(shutter, Ui.frame(Ui.dp(this, 78), Ui.dp(this, 78), Gravity.CENTER));
            FrameLayout.LayoutParams pl = Ui.frame(Ui.dp(this, 60), Ui.dp(this, 60), Gravity.CENTER_VERTICAL | Gravity.END);
            pl.rightMargin = side;
            controls.addView(pagesBtn, pl);
            bottom.addView(controls, new LinearLayout.LayoutParams(-1, Ui.dp(this, 96)));
            addChrome(bottom, Ui.frame(-1, Ui.dp(this, 160), Gravity.BOTTOM));
            plp.setMargins(0, Ui.dp(this, 56), 0, Ui.dp(this, 160));
        }
        previewArea.setLayoutParams(plp);
    }

    private void addChrome(View v, FrameLayout.LayoutParams lp) {
        rootView.addView(v, lp);
        chrome.add(v);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        boolean land = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (land != arrangedLandscape) arrange(land);
        app.main.post(new Runnable() {
            @Override public void run() { refreshGeometry(); rotateIcons(); }
        });
    }

    /** Flexible empty space inside a vertical column (explicit size, never wrap-content). */
    private View spacerV() { return new View(this); }

    private TextView modeChip(String s) {
        TextView t = Ui.text(this, s, 14, Ui.MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        t.setClickable(true);
        return t;
    }

    private void applyModeUi() {
        boolean batch = prefs.batch();
        styleChip(modeSingle, !batch);
        styleChip(modeBatch, batch);
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_ACCENT : Ui.MUTED);
        t.setBackground(on ? Ui.round(Ui.ACCENT, Ui.dp(this, 16)) : null);
    }

    private LinearLayout buildPermissionPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        p.setPadding(pad, pad, pad, pad);
        TextView t = Ui.text(this, "Camera access is off", 19, Ui.LIGHT, true);
        t.setGravity(Gravity.CENTER);
        p.addView(t);
        TextView d = Ui.text(this, Branding.name() + " uses the camera to photograph films and documents. You can still import photos from your gallery.", 14.5f, Ui.MUTED, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 20));
        p.addView(d);
        TextView b = Ui.button(this, "Allow camera", true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                } else {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(CameraActivity.this, "Open Settings > Apps > RadioFilm Scanner > Permissions (the app keeps this name in system settings)", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        p.addView(b, new LinearLayout.LayoutParams(-2, -2));
        return p;
    }

    private void rotateIcons() {
        float target = -((deviceOrientation + displayDegrees()) % 360);
        for (View v : rotatables) {
            float cur = v.getRotation();
            float t = target;
            while (t - cur > 180) t -= 360;
            while (cur - t > 180) t += 360;
            v.animate().rotation(t).setDuration(220).start();
        }
    }

    private final Runnable hideHint = new Runnable() {
        @Override public void run() { hint.animate().alpha(0f).setDuration(200).start(); hintText = ""; }
    };

    private void showHint(String s, long ms) {
        app.main.removeCallbacks(hideHint);
        if (!s.equals(hintText)) {
            hint.setText(s);
            hintText = s;
        }
        hint.setVisibility(View.VISIBLE);
        hint.animate().alpha(1f).setDuration(150).start();
        if (ms > 0) app.main.postDelayed(hideHint, ms);
    }

    private void refreshPagesButton() {
        int n = session.pages.size();
        Ui.setVisible(pagesBtn, n > 0);
        if (n == 0) return;
        pagesBadge.setText(String.valueOf(n));
        final Page last = session.pages.get(n - 1);
        final String key = last.renderKey() + "@thumb";
        Bitmap c = app.cached(key);
        if (c != null) { pagesThumb.setImageBitmap(c); return; }
        final Page snap = last.copy();
        app.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap t = Imaging.renderFinished(snap, 240);
                app.cache(key, t);
                app.ui(new Runnable() {
                    @Override public void run() { if (t != null) pagesThumb.setImageBitmap(t); }
                });
            }
        });
    }

    private void updateFlashIcon() {
        int f = flashAvailable ? prefs.flash() : 0;
        int res = f == 1 ? R.drawable.ic_flash_auto : f == 2 ? R.drawable.ic_flash_on : f == 3 ? R.drawable.ic_torch : R.drawable.ic_flash_off;
        flashBtn.setImageDrawable(Ui.icon(this, res, f == 0 ? Ui.LIGHT : Ui.ACCENT));
    }

    /** Back to the editor, which opens the given page in crop mode. */
    private void finishAdd(String pageId) {
        Intent r = new Intent();
        if (pageId != null && session.find(pageId) != null) r.putExtra(EditActivity.RESULT_PAGE, pageId);
        setResult(RESULT_OK, r);
        finish();
    }


    private void cycleFlash() {
        if (!flashAvailable) { showHint("This camera has no flash", 1800); return; }
        int f = (prefs.flash() + 1) % 4;
        prefs.flash(f);
        updateFlashIcon();
        String[] names = {"Flash off", "Flash auto", "Flash on", "Torch on. Glare shows on glossy film, so use it only for paper."};
        showHint(names[f], f == 3 ? 3000 : 1400);
        if (camHandler != null) camHandler.post(new Runnable() {
            @Override
            public void run() {
                if (capSession == null || previewBuilder == null) return;
                applyDefaults(previewBuilder);
                repeat();
            }
        });
    }

    // ================================================================== settings

    private String cameraInfo() {
        if (jpegSize == null) return "Camera not started";
        return String.format(Locale.US, "%d \u00d7 %d (%.1f MP)%s", jpegSize.getWidth(), jpegSize.getHeight(),
                jpegSize.getWidth() * (double) jpegSize.getHeight() / 1e6, usingHighRes ? ", high-resolution mode" : "");
    }

    // ================================================================== camera setup

    private void startCameraThread() {
        if (camThread != null) return;
        camThread = new HandlerThread("camera");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
    }

    private void stopCameraThread() {
        if (camThread == null) return;
        camThread.quitSafely();
        try { camThread.join(1500); } catch (InterruptedException ignored) { }
        camThread = null;
        camHandler = null;
    }

    private static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static long area(Size s) { return (long) s.getWidth() * s.getHeight(); }

    private static Size largest(Size[] sizes) {
        Size best = null;
        if (sizes == null) return null;
        for (Size s : sizes) if (best == null || area(s) > area(best)) best = s;
        return best;
    }

    private boolean setupCamera() {
        CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String chosen = null, fallback = null;
            for (String id : mgr.getCameraIdList()) {
                CameraCharacteristics c = mgr.getCameraCharacteristics(id);
                if (c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) == null) continue;
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { chosen = id; break; }
                if (fallback == null || (facing != null && facing != CameraCharacteristics.LENS_FACING_FRONT)) fallback = id;
            }
            if (chosen == null) chosen = fallback;
            if (chosen == null) return false;
            cameraId = chosen;
            CameraCharacteristics ch = mgr.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            jpegStdSize = largest(map.getOutputSizes(ImageFormat.JPEG));
            Size hi = null;
            if (Build.VERSION.SDK_INT >= 23 && !getSharedPreferences("filmscan", 0).getBoolean("hiResFailed_" + cameraId, false)) {
                hi = largest(map.getHighResolutionOutputSizes(ImageFormat.JPEG));
            }
            usingHighRes = hi != null && jpegStdSize != null && area(hi) > area(jpegStdSize);
            jpegSize = usingHighRes ? hi : jpegStdSize;
            if (jpegSize == null) return false;
            previewSize = choosePreview(map.getOutputSizes(SurfaceTexture.class), jpegSize);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so == null ? 90 : so;
            flashAvailable = Boolean.TRUE.equals(ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
            int[] af = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            afContinuous = contains(af, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            afAuto = contains(af, CameraMetadata.CONTROL_AF_MODE_AUTO);
            Float minFocus = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            fixedFocus = minFocus == null || minFocus == 0f;
            nrHq = contains(ch.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES), CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            edgeHq = contains(ch.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES), CameraMetadata.EDGE_MODE_HIGH_QUALITY);
            activeArray = ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer ma = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF), me = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            maxAfRegions = ma == null ? 0 : ma;
            maxAeRegions = me == null ? 0 : me;
            applyAspect();
            reader = newReader(jpegSize);
            updateResLabel();
            updateFlashIcon();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "camera setup", e);
            return false;
        }
    }

    private void updateResLabel() {
        if (jpegSize != null) resLabel.setText(String.format(Locale.US, "%.1f MP", jpegSize.getWidth() * (double) jpegSize.getHeight() / 1e6));
    }

    private ImageReader newReader(Size s) {
        ImageReader r = ImageReader.newInstance(s.getWidth(), s.getHeight(), ImageFormat.JPEG, 2);
        r.setOnImageAvailableListener(onImage, camHandler);
        return r;
    }

    /** Largest preview size up to 1920x1080 with the capture's aspect ratio. */
    private static Size choosePreview(Size[] choices, Size jpeg) {
        double target = jpeg.getWidth() / (double) jpeg.getHeight();
        Size best = null, any = null;
        for (Size s : choices) {
            if (s.getWidth() > 1920 || s.getHeight() > 1440) continue;
            double r = s.getWidth() / (double) s.getHeight();
            if (Math.abs(r - target) < 0.02) { if (best == null || area(s) > area(best)) best = s; }
            if (any == null || Math.abs(r - target) < Math.abs(any.getWidth() / (double) any.getHeight() - target)
                    || (Math.abs(r - target) == Math.abs(any.getWidth() / (double) any.getHeight() - target) && area(s) > area(any))) any = s;
        }
        if (best != null) return best;
        if (any != null) return any;
        return choices[0];
    }

    /** How far the screen is turned from the device's natural orientation, in degrees. */
    private int displayDegrees() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    /**
     * Clockwise turn to apply to the preview. The camera delivers the preview upright for the
     * device's natural orientation (portrait on phones, landscape on most tablets), so only the
     * screen's own rotation has to be undone. A manual correction from Settings is added on top.
     */
    private int previewRotation() {
        return (360 - displayDegrees() + 90 * prefs.previewFix()) % 360;
    }

    /** Preview size as it appears in the device's natural orientation. */
    private float[] naturalSize() {
        float pw = previewSize.getWidth(), ph = previewSize.getHeight();
        return sensorOrientation % 180 != 0 ? new float[]{ph, pw} : new float[]{pw, ph};
    }

    /** Gives the viewfinder the exact shape of the picture as seen on screen. */
    private void applyAspect() {
        if (previewSize == null) return;
        float[] n = naturalSize();
        if (previewRotation() % 180 != 0) tex.setAspectRatio((int) n[1], (int) n[0]);
        else tex.setAspectRatio((int) n[0], (int) n[1]);
    }

    /**
     * Draws the preview upright and undistorted in a view of any size. TextureView stretches the
     * picture to fill itself; this matrix undoes that stretch, turns the picture, then scales it
     * uniformly to fill the view. If the view's shape is ever briefly different from the picture
     * (while layouts settle), the picture is cropped a little instead of being stretched.
     */
    private void configureTransform(int vw, int vh) {
        if (previewSize == null || vw <= 0 || vh <= 0) return;
        float[] n = naturalSize();
        int rot = previewRotation();
        float rw = rot % 180 != 0 ? n[1] : n[0], rh = rot % 180 != 0 ? n[0] : n[1];
        Matrix m = new Matrix();
        m.setScale(n[0] / vw, n[1] / vh);
        m.postTranslate(-n[0] / 2f, -n[1] / 2f);
        m.postRotate(rot);
        float k = Math.max(vw / rw, vh / rh);
        m.postScale(k, k);
        m.postTranslate(vw / 2f, vh / 2f);
        tex.setTransform(m);
    }

    /** Keeps the preview buffer at the size the camera session was configured with. */
    private void keepBufferSize(SurfaceTexture st) {
        Size ps = previewSize;
        if (st != null && ps != null) st.setDefaultBufferSize(ps.getWidth(), ps.getHeight());
    }

    /** Re-checks shape and rotation, e.g. after a 180 degree turn that does not restart the screen. */
    private void refreshGeometry() {
        if (previewSize == null) return;
        if (tex.isAvailable()) keepBufferSize(tex.getSurfaceTexture());
        applyAspect();
        configureTransform(tex.getWidth(), tex.getHeight());
    }

    private final android.hardware.display.DisplayManager.DisplayListener displayListener =
            new android.hardware.display.DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) { }
                @Override public void onDisplayRemoved(int id) { }
                @Override public void onDisplayChanged(int id) { refreshGeometry(); }
            };


    private void openCamera() {
        if (!hasCameraPermission() || camera != null || opening || camHandler == null) return;
        if (!setupCamera()) { showHint("No usable camera found. Import photos instead.", 0); return; }
        configureTransform(tex.getWidth(), tex.getHeight());
        CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!openLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) { showHint("Camera is busy. Close other camera apps.", 3000); return; }
            opening = true;
            mgr.openCamera(cameraId, deviceCallback, camHandler);
        } catch (SecurityException e) {
            openLock.release(); opening = false;
            Ui.setVisible(permPanel, true);
        } catch (Exception e) {
            openLock.release(); opening = false;
            Log.e(TAG, "open camera", e);
            showHint("The camera could not be opened", 3000);
        }
    }

    private void closeCamera() {
        try {
            openLock.acquire();
            if (capSession != null) { capSession.close(); capSession = null; }
            if (camera != null) { camera.close(); camera = null; }
            if (reader != null) { reader.close(); reader = null; }
        } catch (InterruptedException ignored) {
        } finally {
            openLock.release();
        }
        opening = false;
        capturing = false;
        manualFocus = false;
        focusRegions = null;
        state = ST_PREVIEW;
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cd) {
            openLock.release();
            camera = cd;
            createSession();
            app.ui(new Runnable() { @Override public void run() { opening = false; } });
        }

        @Override
        public void onDisconnected(CameraDevice cd) {
            openLock.release();
            cd.close();
            camera = null;
            app.ui(new Runnable() { @Override public void run() { opening = false; } });
        }

        @Override
        public void onError(CameraDevice cd, final int error) {
            openLock.release();
            cd.close();
            camera = null;
            app.ui(new Runnable() {
                @Override public void run() { opening = false; showHint("Camera error (" + error + "). Reopen the app to retry.", 4000); }
            });
        }
    };

    /** Runs on the camera thread. */
    private void createSession() {
        final CameraDevice cd = camera;
        final ImageReader rd = reader;
        if (cd == null || rd == null) return;
        try {
            SurfaceTexture st = tex.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(st);
            previewBuilder = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            cd.createCaptureSession(Arrays.asList(previewSurface, rd.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession s) {
                    if (camera == null) return;
                    capSession = s;
                    applyDefaults(previewBuilder);
                    repeat();
                    app.ui(new Runnable() { @Override public void run() { refreshGeometry(); } });
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    if (usingHighRes && camera != null) {
                        // The high resolution stream is not supported together with preview here.
                        usingHighRes = false;
                        getSharedPreferences("filmscan", 0).edit().putBoolean("hiResFailed_" + cameraId, true).apply();
                        jpegSize = jpegStdSize;
                        ImageReader old = reader;
                        reader = newReader(jpegSize);
                        if (old != null) old.close();
                        app.ui(new Runnable() { @Override public void run() { updateResLabel(); } });
                        createSession();
                    } else {
                        app.ui(new Runnable() { @Override public void run() { showHint("The camera could not start", 3000); } });
                    }
                }
            }, camHandler);
        } catch (Exception e) {
            Log.e(TAG, "create session", e);
        }
    }

    private void applyDefaults(CaptureRequest.Builder b) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        MeteringRectangle[] regions = focusRegions;
        if (manualFocus && afAuto && regions != null) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            if (maxAfRegions > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
            if (maxAeRegions > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
        } else if (afContinuous) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else if (afAuto) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
        }
        int f = flashAvailable ? prefs.flash() : 0;
        switch (f) {
            case 1:
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case 2:
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case 3:
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                break;
            default:
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
        }
    }

    private void repeat() {
        CameraCaptureSession s = capSession;
        if (s == null || previewBuilder == null) return;
        try {
            s.setRepeatingRequest(previewBuilder.build(), captureCallback, camHandler);
        } catch (Exception e) {
            Log.w(TAG, "repeat", e);
        }
    }

    // ================================================================== focus

    private void focusAt(float x, float y) {
        if (capSession == null || !afAuto || activeArray == null || capturing || camHandler == null) return;
        float nx = x / tex.getWidth(), ny = y / tex.getHeight();
        float sx, sy;
        switch ((sensorOrientation + previewRotation()) % 360) {
            case 90: sx = ny; sy = 1 - nx; break;
            case 270: sx = 1 - ny; sy = nx; break;
            case 180: sx = 1 - nx; sy = 1 - ny; break;
            default: sx = nx; sy = ny; break;
        }
        int aw = activeArray.width(), ah = activeArray.height();
        int half = (int) (0.07f * Math.min(aw, ah));
        int cx = activeArray.left + (int) (sx * aw), cy = activeArray.top + (int) (sy * ah);
        Rect r = new Rect(Math.max(activeArray.left, cx - half), Math.max(activeArray.top, cy - half),
                Math.min(activeArray.right - 1, cx + half), Math.min(activeArray.bottom - 1, cy + half));
        final MeteringRectangle mr = new MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        camHandler.post(new Runnable() {
            @Override
            public void run() {
                CameraCaptureSession s = capSession;
                if (s == null || previewBuilder == null || state != ST_PREVIEW) return;
                try {
                    previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                    s.capture(previewBuilder.build(), null, camHandler);
                    manualFocus = true;
                    focusRegions = new MeteringRectangle[]{mr};
                    applyDefaults(previewBuilder);
                    previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                    s.capture(previewBuilder.build(), captureCallback, camHandler);
                    previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                    repeat();
                } catch (Exception e) {
                    Log.w(TAG, "focus", e);
                }
            }
        });
        app.main.removeCallbacks(revertFocus);
        app.main.postDelayed(revertFocus, 6000);
    }

    /** Back to continuous autofocus with a fresh request (clears metering regions). */
    private final Runnable revertFocus = new Runnable() {
        @Override
        public void run() {
            if (camHandler == null) return;
            camHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != ST_PREVIEW) { app.main.postDelayed(revertFocus, 2000); return; }
                    CameraDevice cd = camera;
                    CameraCaptureSession s = capSession;
                    if (cd == null || s == null || previewSurface == null) return;
                    try {
                        manualFocus = false;
                        focusRegions = null;
                        CaptureRequest.Builder b = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(previewSurface);
                        applyDefaults(b);
                        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        s.capture(b.build(), null, camHandler);
                        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                        previewBuilder = b;
                        repeat();
                    } catch (Exception e) {
                        Log.w(TAG, "revert focus", e);
                    }
                }
            });
        }
    };

    // ================================================================== capture

    private void takePicture() {
        if (!hasCameraPermission()) { Ui.setVisible(permPanel, true); return; }
        if (capSession == null || capturing || camHandler == null) return;
        capturing = true;
        app.main.removeCallbacks(captureWatchdog);
        app.main.postDelayed(captureWatchdog, usingHighRes ? 12000 : 8000);
        flashFx.setAlpha(0.85f);
        flashFx.animate().alpha(0f).setDuration(260).start();
        camHandler.post(new Runnable() {
            @Override public void run() { lockFocus(); }
        });
    }

    private boolean needsPrecapture() {
        int f = flashAvailable ? prefs.flash() : 0;
        return f == 1 || f == 2;
    }

    private void lockFocus() {
        CameraCaptureSession s = capSession;
        if (s == null) { captureFailed(); return; }
        if (fixedFocus || (!afAuto && !afContinuous)) { precaptureOrCapture(); return; }
        try {
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            state = ST_WAIT_LOCK;
            waitStart = SystemClock.uptimeMillis();
            s.capture(previewBuilder.build(), captureCallback, camHandler);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        } catch (Exception e) {
            Log.w(TAG, "lock focus", e);
            precaptureOrCapture();
        }
    }

    private void precaptureOrCapture() {
        if (!needsPrecapture()) { captureStill(); return; }
        CameraCaptureSession s = capSession;
        if (s == null) { captureFailed(); return; }
        try {
            previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            state = ST_WAIT_PRE;
            waitStart = SystemClock.uptimeMillis();
            s.capture(previewBuilder.build(), captureCallback, camHandler);
            previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        } catch (Exception e) {
            Log.w(TAG, "precapture", e);
            captureStill();
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult r) {
            long waited = SystemClock.uptimeMillis() - waitStart;
            switch (state) {
                case ST_WAIT_LOCK: {
                    Integer af = r.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null || af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED || waited > 1600) {
                        state = ST_TAKEN;
                        precaptureOrCapture();
                    }
                    break;
                }
                case ST_WAIT_PRE: {
                    Integer ae = r.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                            || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                            || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED || waited > 1000) {
                        state = ST_WAIT_NON_PRE;
                        waitStart = SystemClock.uptimeMillis();
                    }
                    break;
                }
                case ST_WAIT_NON_PRE: {
                    Integer ae = r.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE || waited > 1500) {
                        state = ST_TAKEN;
                        captureStill();
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession s, CaptureRequest req, CaptureResult partial) { process(partial); }

        @Override
        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest req, TotalCaptureResult result) { process(result); }
    };

    private int jpegOrientation() {
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private void captureStill() {
        state = ST_TAKEN;
        CameraDevice cd = camera;
        CameraCaptureSession s = capSession;
        ImageReader rd = reader;
        if (cd == null || s == null || rd == null) { captureFailed(); return; }
        try {
            CaptureRequest.Builder b = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(rd.getSurface());
            applyDefaults(b);
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            if (nrHq) b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            if (edgeHq) b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY);
            s.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession ss, CaptureRequest rq, TotalCaptureResult r) {
                    if (sound != null && prefs.sound()) try { sound.play(MediaActionSound.SHUTTER_CLICK); } catch (Throwable ignored) { }
                    unlockFocus();
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession ss, CaptureRequest rq, CaptureFailure f) {
                    unlockFocus();
                    captureFailed();
                }
            }, camHandler);
        } catch (Exception e) {
            Log.e(TAG, "capture", e);
            unlockFocus();
            captureFailed();
        }
    }

    private void unlockFocus() {
        CameraCaptureSession s = capSession;
        state = ST_PREVIEW;
        if (s == null || previewBuilder == null) return;
        try {
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            s.capture(previewBuilder.build(), null, camHandler);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            repeat();
        } catch (Exception e) {
            Log.w(TAG, "unlock", e);
        }
    }

    private void captureFailed() {
        state = ST_PREVIEW;
        app.ui(new Runnable() {
            @Override public void run() { app.main.removeCallbacks(captureWatchdog); capturing = false; showHint("The photo could not be taken. Try again.", 2500); }
        });
    }

    private final ImageReader.OnImageAvailableListener onImage = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader r) {
            Image img = null;
            try {
                img = r.acquireNextImage();
                if (img == null) return;
                ByteBuffer buf = img.getPlanes()[0].getBuffer();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);
                img.close();
                img = null;
                final File f = session.newImageFile(".jpg");
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data);
                fo.getFD().sync();
                fo.close();
                app.ui(new Runnable() { @Override public void run() { onPhotoSaved(f); } });
            } catch (Exception e) {
                Log.e(TAG, "save photo", e);
                captureFailed();
            } finally {
                if (img != null) img.close();
            }
        }
    };

    /** If the camera never delivers the photo, recover instead of leaving the shutter stuck. */
    private final Runnable captureWatchdog = new Runnable() {
        @Override
        public void run() {
            if (!capturing) return;
            capturing = false;
            state = ST_PREVIEW;
            if (usingHighRes && cameraId != null) {
                // this phone lists a high-resolution mode it cannot deliver; use the standard size from now on
                getSharedPreferences("filmscan", 0).edit().putBoolean("hiResFailed_" + cameraId, true).apply();
                showHint("Switched to the standard camera resolution. Try again.", 3500);
                closeCamera();
                if (resumed) openCamera();
            } else {
                showHint("The camera did not respond. Try again.", 3000);
            }
        }
    };

    private void onPhotoSaved(File f) {
        app.main.removeCallbacks(captureWatchdog);
        capturing = false;
        final Page p = new Page();
        p.id = Session.newId();
        p.file = f.getPath();
        p.filter = com.filmscan.core.Filters.ORIGINAL;
        if (!Imaging.probe(p)) { f.delete(); showHint("The photo could not be read. Try again.", 2500); return; }
        session.add(p);
        if (firstNewId == null) firstNewId = p.id;
        long free = Storage.freeBytes();
        if (!lowStorageWarned && free >= 0 && free < 300L * 1024 * 1024) {
            lowStorageWarned = true;
            showHint("Storage almost full: " + Storage.human(free) + " left", 4000);
        }
        if (prefs.batch() || !resumed) {
            refreshPagesButton();
            showHint("Page " + session.pages.size() + " added", 1500);
            pagesBtn.setScaleX(0.8f);
            pagesBtn.setScaleY(0.8f);
            pagesBtn.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            app.run(new Runnable() {
                @Override
                public void run() {
                    Imaging.autoCrop(p);
                    app.ui(new Runnable() {
                        @Override public void run() { p.version++; session.save(); refreshPagesButton(); }
                    });
                }
            });
        } else if (addMode) {
            finishAdd(p.id);
        } else {
            firstNewId = null;
            startActivity(EditActivity.intent(this, p.id));
        }
    }

    // ================================================================== live detection

    private final Runnable detectTick = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            app.main.postDelayed(this, 200);
            if (detBusy || capSession == null || !tex.isAvailable() || permPanel.getVisibility() == View.VISIBLE) return;
            int vw = tex.getWidth(), vh = tex.getHeight();
            if (vw < 10 || vh < 10) return;
            final int dw = 300, dh = Math.max(10, Math.round(300f * vh / vw));
            if (detBmp == null || detBmp.getWidth() != dw || detBmp.getHeight() != dh) {
                detBmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            }
            final Bitmap b;
            try {
                b = tex.getBitmap(detBmp);
            } catch (Throwable t) {
                return;
            }
            if (b == null) return;
            detBusy = true;
            detectExec.execute(new Runnable() {
                @Override
                public void run() {
                    float[] n = null;
                    try {
                        int[] px = new int[dw * dh];
                        b.getPixels(px, 0, dw, 0, 0, dw, dh);
                        EdgeDetector.Result r = EdgeDetector.detect(px, dw, dh);
                        if (r != null) n = Geom.scale(r.quad, 1f / dw, 1f / dh);
                    } catch (Throwable t) {
                        Log.w(TAG, "live detect", t);
                    }
                    final float[] res = n;
                    app.ui(new Runnable() {
                        @Override public void run() { detBusy = false; if (resumed) onDetected(res); }
                    });
                }
            });
        }
    };

    private static float maxDelta(float[] a, float[] b) {
        float m = 0;
        for (int i = 0; i < 8; i++) m = Math.max(m, Math.abs(a[i] - b[i]));
        return m;
    }

    private void onDetected(float[] n) {
        long now = SystemClock.uptimeMillis();
        boolean auto = prefs.autoCapture();
        if (n == null) {
            missCount++;
            if (missCount >= 3) {
                smooth = null;
                overlay.setQuad(null);
                overlay.setSteady(0);
                steadySince = 0;
                lastRaw = null;
                if (missCount >= 5) capturedQuad = null;
            }
            return;
        }
        missCount = 0;
        // light filtering of detector jitter; the overlay then glides smoothly between results
        if (smooth == null) smooth = n.clone();
        else {
            float gain = maxDelta(smooth, n) > 0.08f ? 0.8f : 0.5f;   // follow real moves quickly, damp jitter
            for (int i = 0; i < 8; i++) smooth[i] += (n[i] - smooth[i]) * gain;
        }
        overlay.setQuad(smooth.clone());
        if (auto && !capturing && now > cooldownUntil) {
            if (capturedQuad != null && maxDelta(capturedQuad, n) < 0.06f) {
                overlay.setSteady(0);
            } else {
                if (lastRaw == null || maxDelta(lastRaw, n) > 0.02f || steadySince == 0) steadySince = now;
                float prog = Math.min(1f, (now - steadySince) / 1400f);
                overlay.setSteady(prog);
                if (prog >= 1f) {
                    capturedQuad = n.clone();
                    cooldownUntil = now + 2000;
                    steadySince = 0;
                    overlay.setSteady(0);
                    takePicture();
                }
            }
        } else if (!auto) {
            overlay.setSteady(0);
        }
        lastRaw = n;
    }

    // ================================================================== import

    private void pickImages() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose film photos"), REQ_IMPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMPORT || res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<Uri>();
        ClipData cd = data.getClipData();
        if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) { Uri u = cd.getItemAt(i).getUri(); if (u != null) uris.add(u); }
        else if (data.getData() != null) uris.add(data.getData());
        importUris(uris);
    }

    private void handleShareIntent(Intent i) {
        if (i == null || i.getAction() == null) return;
        List<Uri> uris = new ArrayList<Uri>();
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())) {
            ArrayList<Uri> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        } else {
            return;
        }
        i.setAction(null);
        importUris(uris);
    }

    private void importUris(final List<Uri> uris) {
        if (uris.isEmpty()) return;
        final AlertDialog dlg = progressDialog(uris.size() == 1 ? "Finding the film edges…" : "Importing " + uris.size() + " photos…");
        app.run(new Runnable() {
            @Override
            public void run() {
                final List<Page> added = new ArrayList<Page>();
                int failed = 0;
                int index = 0;
                for (Uri u : uris) {
                    final int num = ++index;
                    if (uris.size() > 1) app.ui(new Runnable() {
                        @Override public void run() { setProgress(dlg, "Importing photo " + num + " of " + uris.size() + "…", num - 1, uris.size()); }
                    });
                    try {
                        String type = getContentResolver().getType(u);
                        String ext = type == null ? ".jpg" : type.contains("png") ? ".png" : type.contains("webp") ? ".webp"
                                : (type.contains("heic") || type.contains("heif")) ? ".heic" : ".jpg";
                        File f = session.newImageFile(ext);
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) { failed++; continue; }
                        OutputStream out = new FileOutputStream(f);
                        byte[] buf = new byte[1 << 16];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        in.close();
                        out.close();
                        Page p = new Page();
                        p.id = Session.newId();
                        p.file = f.getPath();
                        p.filter = com.filmscan.core.Filters.ORIGINAL;
                        if (!Imaging.probe(p)) { f.delete(); failed++; continue; }
                        Imaging.autoCrop(p);
                        added.add(p);
                    } catch (Throwable t) {
                        Log.w(TAG, "import", t);
                        failed++;
                    }
                }
                final int fails = failed;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        for (Page p : added) session.pages.add(p);
                        session.save();
                        refreshPagesButton();
                        if (fails > 0) Toast.makeText(CameraActivity.this, fails + " file(s) could not be opened as images", Toast.LENGTH_LONG).show();
                        // open the first imported photo in the editor (crop mode); swipe for the rest
                        if (!added.isEmpty()) {
                            if (addMode) finishAdd(added.get(0).id);
                            else startActivity(EditActivity.intent(CameraActivity.this, added.get(0).id));
                        }
                    }
                });
            }
        });
    }

    AlertDialog progressDialog(String msg) {
        return progressDialog(this, msg);
    }

    static AlertDialog progressDialog(Activity a, String msg) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 22);
        col.setPadding(pad, pad, pad, Ui.dp(a, 18));
        LinearLayout l = new LinearLayout(a);
        l.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spin = new ProgressBar(a);
        l.addView(spin, new LinearLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32)));
        TextView t = Ui.text(a, msg, 15, Ui.LIGHT, false);
        t.setId(android.R.id.message);
        t.setPadding(Ui.dp(a, 16), 0, 0, 0);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(l);
        // determinate bar, shown once the total is known (see setProgress)
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setId(android.R.id.progress);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 8));
        bl.topMargin = Ui.dp(a, 14);
        col.addView(bar, bl);
        AlertDialog d = new AlertDialog.Builder(a).setView(col).setCancelable(false).create();
        d.show();
        return d;
    }

    /** Updates a progress dialog: "text", and a bar at done/total. */
    static void setProgress(AlertDialog d, String text, int done, int total) {
        try {
            TextView t = (TextView) d.findViewById(android.R.id.message);
            ProgressBar bar = (ProgressBar) d.findViewById(android.R.id.progress);
            if (t != null) t.setText(text);
            if (bar != null && total > 0) {
                bar.setVisibility(View.VISIBLE);
                bar.setMax(total * 100);
                android.animation.ObjectAnimator.ofInt(bar, "progress", bar.getProgress(), done * 100)
                        .setDuration(250).start();
            }
        } catch (Exception ignored) { }
    }
}
