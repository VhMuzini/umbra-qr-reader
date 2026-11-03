package dev.umbra.qr.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dev.umbra.qr.R;
import dev.umbra.qr.core.ContentClassifier;
import dev.umbra.qr.core.ScanContent;
import dev.umbra.qr.core.UrlSafety;
import dev.umbra.qr.util.AppLog;
import java.util.Locale;
import java.util.Set;

/**
 * Shows what a QR code contains and offers actions suited to its type. Nothing is stored: the
 * payload lives only in this fragment's arguments while the sheet is open.
 */
public final class ResultSheet extends BottomSheetDialogFragment {

    public static final String TAG = "result_sheet";
    /** Fragment result key sent to the parent when the sheet is closed by the user. */
    public static final String REQUEST_DISMISSED = "result_sheet_dismissed";

    private static final String ARG_RAW = "raw";

    private LinearLayout details;
    private MaterialButton primaryAction;

    public static ResultSheet newInstance(String raw) {
        Bundle args = new Bundle();
        args.putString(ARG_RAW, raw);
        ResultSheet sheet = new ResultSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        details = view.findViewById(R.id.details);
        primaryAction = view.findViewById(R.id.primary_action);

        ScanContent content = ContentClassifier.classify(requireArguments().getString(ARG_RAW, ""));
        TextView title = view.findViewById(R.id.type_title);
        title.setText(titleFor(content));
        ViewCompat.setAccessibilityHeading(title, true);
        bind(view, content);

        // A raw Wi-Fi payload contains the password, so treat it as sensitive too.
        boolean sensitive = content instanceof ScanContent.Wifi wifi && wifi.password().isPresent();
        view.findViewById(R.id.copy_button).setOnClickListener(v -> copy(content.raw(), sensitive));
        view.findViewById(R.id.share_button).setOnClickListener(v -> share(content.raw()));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog dialog) {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        FragmentActivity activity = getActivity();
        // A configuration change also dismisses the dialog; it is re-shown afterwards, so the
        // scanner must stay paused in that case.
        if (activity != null && !activity.isChangingConfigurations()) {
            getParentFragmentManager().setFragmentResult(REQUEST_DISMISSED, Bundle.EMPTY);
        }
    }

    @StringRes
    private static int titleFor(ScanContent content) {
        return switch (content) {
            case ScanContent.Url url -> R.string.type_url;
            case ScanContent.Wifi wifi -> R.string.type_wifi;
            case ScanContent.Email email -> R.string.type_email;
            case ScanContent.Phone phone -> R.string.type_phone;
            case ScanContent.Sms sms -> R.string.type_sms;
            case ScanContent.Geo geo -> R.string.type_geo;
            case ScanContent.PlainText text -> R.string.type_text;
        };
    }

    private void bind(View root, ScanContent content) {
        switch (content) {
            case ScanContent.Url url -> {
                addRow(R.string.label_domain, url.host());
                addRow(R.string.label_link, url.link());
                showWarnings(root, url.warnings());
                setPrimary(R.string.action_open_link, () -> confirmOpenLink(url));
            }
            case ScanContent.Wifi wifi -> {
                addRow(R.string.label_network, wifi.ssid());
                addRow(R.string.label_security, securityLabel(wifi.security()));
                wifi.password()
                        .ifPresent(
                                password -> {
                                    addSecretRow(R.string.label_password, password);
                                    setPrimary(R.string.action_copy_password, () -> copy(password, true));
                                });
                if (wifi.hidden()) {
                    addRow(0, getString(R.string.wifi_hidden));
                }
            }
            case ScanContent.Email email -> {
                addRow(R.string.label_address, email.address());
                email.subject().ifPresent(subject -> addRow(R.string.label_subject, subject));
                email.body().ifPresent(body -> addRow(R.string.label_message, body));
                setPrimary(R.string.action_send_email, () -> launch(emailIntent(email)));
            }
            case ScanContent.Phone phone -> {
                addRow(R.string.label_number, phone.number());
                setPrimary(
                        R.string.action_dial,
                        () -> launch(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.number(), null))));
            }
            case ScanContent.Sms sms -> {
                addRow(R.string.label_number, sms.number());
                sms.message().ifPresent(message -> addRow(R.string.label_message, message));
                setPrimary(R.string.action_send_sms, () -> launch(smsIntent(sms)));
            }
            case ScanContent.Geo geo -> {
                addRow(R.string.label_coordinates, formatCoordinates(geo));
                geo.query().ifPresent(query -> addRow(R.string.label_place, query));
                setPrimary(R.string.action_open_map, () -> launch(new Intent(Intent.ACTION_VIEW, geoUri(geo))));
            }
            case ScanContent.PlainText text -> addRow(0, text.raw());
        }
    }

    private void addRow(@StringRes int label, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_detail_row, details, false);
        TextView labelView = row.findViewById(R.id.row_label);
        if (label == 0) {
            labelView.setVisibility(View.GONE);
        } else {
            labelView.setText(label);
        }
        ((TextView) row.findViewById(R.id.row_value)).setText(value);
        details.addView(row);
    }

    /** A row whose value stays masked until tapped, to avoid shoulder surfing. */
    private void addSecretRow(@StringRes int label, String secret) {
        View row = getLayoutInflater().inflate(R.layout.item_detail_row, details, false);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        TextView value = row.findViewById(R.id.row_value);
        value.setTextIsSelectable(false);
        value.setText(R.string.secret_masked);
        value.setContentDescription(getString(R.string.secret_hidden_description));
        value.setOnClickListener(
                v -> {
                    value.setText(secret);
                    value.setContentDescription(null);
                    value.setOnClickListener(null);
                    value.setClickable(false);
                    value.setTextIsSelectable(true);
                });
        details.addView(row);
    }

    private void setPrimary(@StringRes int label, Runnable action) {
        primaryAction.setText(label);
        primaryAction.setVisibility(View.VISIBLE);
        primaryAction.setOnClickListener(v -> action.run());
    }

    private void showWarnings(View root, Set<UrlSafety.Warning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        // Iterate the enum to keep a stable, meaningful order.
        for (UrlSafety.Warning warning : UrlSafety.Warning.values()) {
            if (warnings.contains(warning)) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("• ").append(getString(warningText(warning)));
            }
        }
        ((TextView) root.findViewById(R.id.warnings_text)).setText(text);
        root.findViewById(R.id.warnings).setVisibility(View.VISIBLE);
    }

    @StringRes
    private static int warningText(UrlSafety.Warning warning) {
        return switch (warning) {
            case INSECURE_HTTP -> R.string.warning_insecure_http;
            case PUNYCODE_HOST -> R.string.warning_punycode;
            case NON_ASCII_HOST -> R.string.warning_non_ascii;
            case IP_LITERAL_HOST -> R.string.warning_ip_literal;
            case EMBEDDED_CREDENTIALS -> R.string.warning_credentials;
        };
    }

    private String securityLabel(ScanContent.WifiSecurity security) {
        return switch (security) {
            case NONE -> getString(R.string.wifi_open);
            case WEP -> "WEP";
            case WPA -> "WPA/WPA2";
            case SAE -> "WPA3";
        };
    }

    /** Asks before handing a link to the browser, with the destination host in bold. */
    private void confirmOpenLink(ScanContent.Url url) {
        String message = getString(R.string.confirm_open_message, url.link());
        SpannableString styled = new SpannableString(message);
        int linkStart = message.lastIndexOf(url.link());
        int hostStart = linkStart < 0 ? -1 : message.toLowerCase(Locale.ROOT).indexOf(url.host(), linkStart);
        if (hostStart >= 0) {
            styled.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    hostStart,
                    hostStart + url.host().length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_open_title)
                .setMessage(styled)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.confirm_open_positive,
                        (dialog, which) ->
                                launch(new Intent(Intent.ACTION_VIEW, Uri.parse(url.link()))
                                        .addCategory(Intent.CATEGORY_BROWSABLE)))
                .show();
    }

    private static Intent emailIntent(ScanContent.Email email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {email.address()});
        email.subject().ifPresent(subject -> intent.putExtra(Intent.EXTRA_SUBJECT, subject));
        email.body().ifPresent(body -> intent.putExtra(Intent.EXTRA_TEXT, body));
        return intent;
    }

    private static Intent smsIntent(ScanContent.Sms sms) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", sms.number(), null));
        sms.message().ifPresent(message -> intent.putExtra("sms_body", message));
        return intent;
    }

    private static Uri geoUri(ScanContent.Geo geo) {
        String coordinates = String.format(Locale.ROOT, "%.6f,%.6f", geo.latitude(), geo.longitude());
        String label = geo.query().map(query -> "(" + Uri.encode(query) + ")").orElse("");
        return Uri.parse("geo:" + coordinates + "?q=" + coordinates + label);
    }

    private static String formatCoordinates(ScanContent.Geo geo) {
        return String.format(Locale.ROOT, "%.5f, %.5f", geo.latitude(), geo.longitude());
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppLog.w("No activity for action " + intent.getAction(), e);
            Toast.makeText(requireContext(), R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void share(String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        launch(Intent.createChooser(send, getString(R.string.share_chooser_title)));
    }

    private void copy(String text, boolean sensitive) {
        ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
        ClipData clip = ClipData.newPlainText(getString(R.string.clip_label), text);
        if (sensitive) {
            // Keeps passwords out of clipboard previews and keyboard suggestions.
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? ClipDescription.EXTRA_IS_SENSITIVE
                            : "android.content.extra.IS_SENSITIVE",
                    true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }
}
