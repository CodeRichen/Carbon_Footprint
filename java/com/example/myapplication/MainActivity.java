package com.example.myapplication;

import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "YTUsageDebug";
    private static final String YOUTUBE_PACKAGE_NAME = "com.google.android.youtube";
    private static final String CHANNEL_ID = "yt_usage_channel";

    private TextView resultText;
    private Handler handler = new Handler();
    private Runnable updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resultText = new TextView(this);
        setContentView(resultText);

        createNotificationChannel();

        if (!hasUsageStatsPermission()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            resultText.setText("請開啟『使用狀況存取』權限後回來重新啟動 App");
        } else {
            startRepeatingUpdate();
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    private void startRepeatingUpdate() {
        updater = new Runnable() {
            @Override
            public void run() {
                updateInfo();
                handler.postDelayed(this, 10000); // 每10秒執行
            }
        };
        updater.run();
    }

    private void updateInfo() {
        long youtubeTime = getTodayYouTubeTimeInMs();
        long bootTime = SystemClock.elapsedRealtime(); // 開機至今毫秒
        String bootDuration = formatDuration(bootTime);

        String nowTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        long minutes = youtubeTime / 1000 / 60;

        String output = "🕒 現在時間：" + nowTime +
                "\n⏱️ 開機時間：" + bootDuration +
                "\n📺 今天使用 YouTube：" + minutes + " 分鐘";

        Log.d(TAG, output);
        resultText.setText(output);
        showNotification(minutes);
    }

    private long getTodayYouTubeTimeInMs() {
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        List<UsageStats> usageStatsList =
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (usageStatsList != null) {
            for (UsageStats stats : usageStatsList) {
                if (YOUTUBE_PACKAGE_NAME.equals(stats.getPackageName())) {
                    return stats.getTotalTimeInForeground();
                }
            }
        }
        return 0;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours + " 小時 " + minutes + " 分 " + secs + " 秒";
    }

    private void showNotification(long youtubeMinutes) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("YouTube 使用監控")
                .setContentText("今天使用時間：約 " + youtubeMinutes + " 分鐘")
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        manager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "YT Usage Channel";
            String description = "Channel for YouTube usage notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
