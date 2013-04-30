package org.umn.jpwang.earlystagedetection;

import android.test.ActivityInstrumentationTestCase2;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class org.umn.jpwang.earlystagedetection.WelcomeActivityTest \
 * org.umn.jpwang.earlystagedetection.tests/android.test.InstrumentationTestRunner
 */
public class WelcomeActivityTest extends ActivityInstrumentationTestCase2<WelcomeActivity> {

    public WelcomeActivityTest() {
        super("org.umn.jpwang.earlystagedetection", WelcomeActivity.class);
    }

    public void testTrue ()
    {
        assertTrue(1 == 1);
    }

}
