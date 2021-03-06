package com.cookpad.android.issuereporter.fragment;

import com.cookpad.android.issuereporter.IIntentReceiveService;
import com.cookpad.android.issuereporter.IIntentReceiveServiceCallback;
import com.cookpad.android.issuereporter.R;
import com.cookpad.android.issuereporter.service.IntentReceiveService;
import com.cookpad.android.issuereporter.task.ScreenshotTask;
import com.cookpad.android.issuereporter.util.IntentUtils;
import com.cookpad.android.issuereporter.util.SystemProfileBuilder;
import com.cookpad.android.issuereporter.util.Utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class IssueReporterFragment extends BaseFragment {

    public static final String EXTRA_MAIL_ADDRESS = "mail_address";

    public static final String EXTRA_SUBJECT = "subject";

    private static final String FRAGMENT_TAG = IssueReporterFragment.class.getName();

    private static final String NOTIFICATION_TAG = IssueReporterFragment.class.getName();

    private static final int NOTIFICATION_ID = 0;

    public static IssueReporterFragment newInstance(String mailAddress, String subject) {
        Bundle args = new Bundle();
        args.putString(EXTRA_MAIL_ADDRESS, mailAddress);
        args.putString(EXTRA_SUBJECT, subject);

        IssueReporterFragment fragment = new IssueReporterFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static IssueReporterFragment apply(FragmentActivity activity, String mailAddress,
            String subject) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        IssueReporterFragment fragment =
                (IssueReporterFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment != null) {
            return fragment;
        }

        fragment = newInstance(mailAddress, subject);
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(fragment, IssueReporterFragment.class.getName());
        fragmentTransaction.commit();

        return fragment;
    }

    private String mailAddress;

    private String subject;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        mailAddress = args.getString(EXTRA_MAIL_ADDRESS);
        subject = args.getString(EXTRA_SUBJECT);
    }

    @Override
    public void onResume() {
        super.onResume();

        showReportButton();

        Intent intent = IntentReceiveService.createIntent(getActivity());
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();

        hideReportButton();

        unbindService(connection);
    }

    private IIntentReceiveService intentReceiverService;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            intentReceiverService = IIntentReceiveService.Stub.asInterface(service);
            try {
                intentReceiverService.registerCallback(callback);
            } catch (RemoteException e) {
                intentReceiverService = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            intentReceiverService = null;
        }
    };

    private IIntentReceiveServiceCallback callback = new IIntentReceiveServiceCallback.Stub() {
        @Override
        public void onReceiveReportIssueIntent() throws RemoteException {
            try {
                takeScreenshotAndSend();
            } catch (IOException e) {
                Toast.makeText(getActivity(), R.string.failed_to_take_screenshot,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void showReportButton() {
        PendingIntent pendingIntent = IntentReceiveService.createPendingIntent(getActivity(), 0, 0);
        Notification notification = buildNotification(pendingIntent);

        NotificationManager notificationManager = getNotificationManager();
        notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification);
    }

    private void hideReportButton() {
        NotificationManager notificationManager = getNotificationManager();
        notificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    private Notification buildNotification(PendingIntent pendingIntent) {
        String applicationName = Utils.getApplicationName(getActivity()).toString();
        String title = getString(R.string.notification_title);
        String description = getString(R.string.notification_description, applicationName);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity());
        builder.setTicker(title);
        builder.setContentTitle(title);
        builder.setContentText(description);
        builder.setSmallIcon(R.drawable.notification);
        builder.setContentIntent(pendingIntent);
        return builder.build();
    }

    private void takeScreenshotAndSend() throws IOException {
        ProgressDialogFragment.show(getActivity(), R.string.wait_a_moment);
        new ScreenshotTask(getActivity(), new ScreenshotTask.Callback() {
            @Override
            public void onTakeScreenshot(File bitmapFile) {
                FragmentActivity activity = getActivity();

                ProgressDialogFragment.dismiss(activity);
                String authority = "com.cookpad.android.issuereporter.fileprovider";
                Uri bitmapUri = FileProvider.getUriForFile(activity, authority, bitmapFile);

                String body = new SystemProfileBuilder(getActivity()).build();
                IntentUtils.sendMail(getActivity(), mailAddress, subject, body, bitmapUri);
            }

            @Override
            public void onCatchIOException(IOException e) {
                ProgressDialogFragment.dismiss(getActivity());

                Toast.makeText(getActivity(), R.string.failed_to_take_screenshot,
                        Toast.LENGTH_SHORT).show();
            }
        }).execute();
    }

}
