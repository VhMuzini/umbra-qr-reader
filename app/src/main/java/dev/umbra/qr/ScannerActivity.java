package dev.umbra.qr;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import dev.umbra.qr.camera.ImageFileDecoder;
import dev.umbra.qr.camera.QrAnalyzer;
import dev.umbra.qr.ui.GothicViewfinderView;
import dev.umbra.qr.ui.ResultSheet;
import dev.umbra.qr.util.AppLog;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The only screen: live camera scanning, image import, torch and language switching. */
public final class ScannerActivity extends AppCompatActivity {

    private static final String STATE_PERMISSION_ASKED = "permission_asked";
    private static final Size ANALYSIS_RESOLUTION = new Size(1280, 720);

    /** What the screen is currently showing. */
    private enum ScreenState {
        SCANNING,
        PERMISSION_NEEDED,
        PERMISSION_BLOCKED,
        CAMERA_UNAVAILABLE
    }

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private PreviewView preview;
    private GothicViewfinderView viewfinder;
    private View statePanel;
    private View bottomBar;
    private MaterialButton torchButton;

    private QrAnalyzer analyzer;
    private ImageFileDecoder imageFileDecoder;
    private Camera camera;
    private boolean cameraStarting;
    private boolean permissionAsked;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), this::onPermissionResult);

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Light system-bar icons always: the UI is dark regardless of the system theme.
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        permissionAsked =
                savedInstanceState != null && savedInstanceState.getBoolean(STATE_PERMISSION_ASKED);
        analyzer = new QrAnalyzer(text -> runOnUiThread(() -> showResult(text)));
        imageFileDecoder = new ImageFileDecoder(getContentResolver());

        preview = findViewById(R.id.preview);
        viewfinder = findViewById(R.id.viewfinder);
        statePanel = findViewById(R.id.state_panel);
        bottomBar = findViewById(R.id.bottom_bar);
        torchButton = findViewById(R.id.torch_button);

        applyWindowInsets();
        torchButton.setOnClickListener(v -> toggleTorch());
        findViewById(R.id.gallery_button).setOnClickListener(v -> launchImagePicker());
        findViewById(R.id.state_gallery).setOnClickListener(v -> launchImagePicker());
        findViewById(R.id.language_button).setOnClickListener(this::showLanguageMenu);

        getSupportFragmentManager()
                .setFragmentResultListener(
                        ResultSheet.REQUEST_DISMISSED, this, (key, result) -> resumeScanning());
        // A result restored after recreation must keep the scanner paused.
        if (getSupportFragmentManager().findFragmentByTag(ResultSheet.TAG) != null) {
            pauseScanning();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasCameraPermission()) {
            startCamera();
        } else if (!permissionAsked) {
            permissionAsked = true;
            cameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            render(permissionDeniedState());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PERMISSION_ASKED, permissionAsked);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
        imageExecutor.shutdown();
    }

    // region Camera

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onPermissionResult(boolean granted) {
        if (granted) {
            startCamera();
        } else {
            render(permissionDeniedState());
        }
    }

    private ScreenState permissionDeniedState() {
        // Once the user picks "don't ask again", the system dialog no longer appears.
        return shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || !permissionAsked
                ? ScreenState.PERMISSION_NEEDED
                : ScreenState.PERMISSION_BLOCKED;
    }

    private void startCamera() {
        if (camera != null || cameraStarting) {
            render(ScreenState.SCANNING);
            return;
        }
        cameraStarting = true;
        render(ScreenState.SCANNING);
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(
                () -> {
                    cameraStarting = false;
                    try {
                        bindCamera(future.get());
                    } catch (ExecutionException e) {
                        AppLog.e("Camera provider unavailable", e);
                        render(ScreenState.CAMERA_UNAVAILABLE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        render(ScreenState.CAMERA_UNAVAILABLE);
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider) {
        if (isDestroyed()) {
            return;
        }
        CameraSelector selector;
        try {
            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                selector = CameraSelector.DEFAULT_BACK_CAMERA;
            } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                selector = CameraSelector.DEFAULT_FRONT_CAMERA;
            } else {
                render(ScreenState.CAMERA_UNAVAILABLE);
                return;
            }
        } catch (CameraInfoUnavailableException e) {
            AppLog.e("Could not query cameras", e);
            render(ScreenState.CAMERA_UNAVAILABLE);
            return;
        }

        Preview previewUseCase = new Preview.Builder().build();
        previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());

        ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                                new ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                                new ResolutionStrategy(
                                                        ANALYSIS_RESOLUTION,
                                                        ResolutionStrategy
                                                                .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                        .build())
                        .build();
        analysis.setAnalyzer(analysisExecutor, analyzer);

        try {
            provider.unbindAll();
            camera = provider.bindToLifecycle(this, selector, previewUseCase, analysis);
        } catch (IllegalStateException | IllegalArgumentException e) {
            AppLog.e("Could not bind camera use cases", e);
            render(ScreenState.CAMERA_UNAVAILABLE);
            return;
        }

        boolean hasTorch = camera.getCameraInfo().hasFlashUnit();
        torchButton.setVisibility(hasTorch ? View.VISIBLE : View.GONE);
        findViewById(R.id.torch_spacer).setVisibility(hasTorch ? View.VISIBLE : View.GONE);
        camera.getCameraInfo()
                .getTorchState()
                .observe(this, state -> updateTorchButton(state != null && state == TorchState.ON));
        AppLog.d("Camera bound");
    }

    private void toggleTorch() {
        if (camera == null) {
            return;
        }
        Integer state = camera.getCameraInfo().getTorchState().getValue();
        camera.getCameraControl().enableTorch(state == null || state != TorchState.ON);
    }

    private void updateTorchButton(boolean on) {
        torchButton.setIconResource(on ? R.drawable.ic_flash_off : R.drawable.ic_flash_on);
        String label = getString(on ? R.string.action_torch_off : R.string.action_torch_on);
        torchButton.setContentDescription(label);
        torchButton.setTooltipText(label);
    }

    // endregion

    // region Results

    private void showResult(String text) {
        if (isFinishing() || getSupportFragmentManager().isStateSaved()) {
            // Cannot show UI now; drop this frame and keep scanning when we come back.
            analyzer.resume();
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag(ResultSheet.TAG) != null) {
            return;
        }
        pauseScanning();
        viewfinder.performHapticFeedback(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.VIRTUAL_KEY);
        ResultSheet.newInstance(text).show(getSupportFragmentManager(), ResultSheet.TAG);
    }

    private void pauseScanning() {
        analyzer.pause();
        viewfinder.setScanning(false);
    }

    private void resumeScanning() {
        viewfinder.setScanning(true);
        analyzer.resume();
    }

    private void launchImagePicker() {
        pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        pauseScanning();
        imageExecutor.execute(
                () -> {
                    ImageFileDecoder.Outcome outcome = imageFileDecoder.decode(uri);
                    runOnUiThread(() -> onImageDecoded(outcome));
                });
    }

    private void onImageDecoded(ImageFileDecoder.Outcome outcome) {
        if (isDestroyed()) {
            return;
        }
        switch (outcome) {
            case ImageFileDecoder.Outcome.Found found -> showResult(found.text());
            case ImageFileDecoder.Outcome.NotFound notFound -> {
                showMessage(R.string.image_no_code);
                resumeScanning();
            }
            case ImageFileDecoder.Outcome.Failed failed -> {
                AppLog.w("Could not read picked image", failed.error());
                showMessage(R.string.image_read_failed);
                resumeScanning();
            }
        }
    }

    private void showMessage(@StringRes int message) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG);
        if (bottomBar.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(bottomBar);
        }
        snackbar.show();
    }

    // endregion

    // region Screen state

    private void render(ScreenState state) {
        boolean scanning = state == ScreenState.SCANNING;
        statePanel.setVisibility(scanning ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(scanning ? View.VISIBLE : View.GONE);
        viewfinder.setVisibility(scanning ? View.VISIBLE : View.INVISIBLE);
        if (scanning) {
            return;
        }

        TextView title = findViewById(R.id.state_title);
        TextView body = findViewById(R.id.state_body);
        MaterialButton action = findViewById(R.id.state_action);
        switch (state) {
            case PERMISSION_NEEDED -> {
                title.setText(R.string.permission_title);
                body.setText(R.string.permission_rationale);
                action.setText(R.string.permission_grant);
                action.setOnClickListener(v -> cameraPermission.launch(Manifest.permission.CAMERA));
                action.setVisibility(View.VISIBLE);
            }
            case PERMISSION_BLOCKED -> {
                title.setText(R.string.permission_title);
                body.setText(R.string.permission_blocked);
                action.setText(R.string.permission_settings);
                action.setOnClickListener(v -> openAppSettings());
                action.setVisibility(View.VISIBLE);
            }
            case CAMERA_UNAVAILABLE -> {
                title.setText(R.string.camera_unavailable_title);
                body.setText(R.string.camera_unavailable_body);
                action.setVisibility(View.GONE);
            }
            case SCANNING -> throw new IllegalStateException("Handled above");
        }
    }

    private void openAppSettings() {
        Intent intent =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppLog.w("App settings screen unavailable", e);
        }
    }

    private void applyWindowInsets() {
        View topBar = findViewById(R.id.top_bar);
        int topPadding = topBar.getPaddingTop();
        int bottomPadding = bottomBar.getPaddingBottom();
        int panelPadding = statePanel.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.root),
                (view, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    topBar.setPadding(
                            topBar.getPaddingLeft(),
                            topPadding + bars.top,
                            topBar.getPaddingRight(),
                            topBar.getPaddingBottom());
                    bottomBar.setPadding(
                            bottomBar.getPaddingLeft(),
                            bottomBar.getPaddingTop(),
                            bottomBar.getPaddingRight(),
                            bottomPadding + bars.bottom);
                    statePanel.setPadding(
                            statePanel.getPaddingLeft(),
                            statePanel.getPaddingTop(),
                            statePanel.getPaddingRight(),
                            panelPadding + bars.bottom);
                    return windowInsets;
                });
    }

    // endregion

    // region Language

    private void showLanguageMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.language);
        String current = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        int checked;
        if (current.startsWith("pt")) {
            checked = R.id.language_pt_br;
        } else if (current.startsWith("fr")) {
            checked = R.id.language_fr;
        } else if (current.startsWith("en")) {
            checked = R.id.language_en;
        } else {
            checked = R.id.language_system;
        }
        menu.getMenu().findItem(checked).setChecked(true);
        menu.setOnMenuItemClickListener(
                item -> {
                    AppCompatDelegate.setApplicationLocales(localesFor(item.getItemId()));
                    return true;
                });
        menu.show();
    }

    private static LocaleListCompat localesFor(int menuItemId) {
        if (menuItemId == R.id.language_pt_br) {
            return LocaleListCompat.forLanguageTags("pt-BR");
        } else if (menuItemId == R.id.language_fr) {
            return LocaleListCompat.forLanguageTags("fr");
        } else if (menuItemId == R.id.language_en) {
            return LocaleListCompat.forLanguageTags("en");
        }
        return LocaleListCompat.getEmptyLocaleList();
    }

    // endregion
}
