package dev.umbra.qr;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.Manifest;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import dev.umbra.qr.ui.ResultSheet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScannerActivityTest {

    private final ActivityScenarioRule<ScannerActivity> activity =
            new ActivityScenarioRule<>(ScannerActivity.class);

    @Rule
    public final TestRule rules =
            RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA)).around(activity);

    @Test
    public void showsScannerControls() {
        onView(withId(R.id.wordmark)).check(matches(isDisplayed()));
        onView(withId(R.id.gallery_button)).check(matches(isDisplayed()));
    }

    @Test
    public void wifiResultMasksPasswordUntilTapped() {
        activity.getScenario()
                .onActivity(
                        a ->
                                ResultSheet.newInstance("WIFI:T:WPA;S:Crypt;P:nocturne;;")
                                        .show(a.getSupportFragmentManager(), ResultSheet.TAG));

        onView(withText("Crypt")).check(matches(isDisplayed()));
        onView(withText("nocturne")).check(doesNotExist());
    }
}
