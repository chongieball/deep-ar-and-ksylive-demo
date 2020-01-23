package ai.deepar.example;

import android.app.Activity;
import android.app.Fragment;
import android.support.v4.app.FragmentActivity;

public class FragmentUtility {

    public static void attachTo(FragmentActivity activity, android.support.v4.app.Fragment fragment, String tag) {
        if (activity == null || fragment == null || tag == null) {
            return;
        }
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, tag)
                .commit();
    }

    public static void attachTo(FragmentActivity activity, Fragment fragment, String tag) {
        if (activity == null || fragment == null || tag == null) {
            return;
        }
        activity.getFragmentManager()
                .beginTransaction()
                .add(fragment, tag)
                .commit();
    }

    public static void attachTo(Activity activity, Fragment fragment, String tag) {
        if (activity == null || fragment == null || tag == null) {
            return;
        }
        activity.getFragmentManager()
                .beginTransaction()
                .add(fragment, tag)
                .commit();
    }
}

