/* 匯入必要的 Android 與 Java 類別 */
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
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import java.util.Calendar;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    /* 常數定義 */
    private static final String TAG = "YTUsageDebug"; // Log 用標籤
    private static final String YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"; // YouTube 套件名稱
    private static final String CHANNEL_ID = "yt_usage_channel"; // 通知頻道 ID

    /* 宣告 UI 與控制用變數 */
    private TextView resultText; // 用來顯示結果的文字框
    private Handler handler = new Handler(); // 建立一個 Handler 用於排程更新
    private Runnable updater; // 週期性執行任務

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* 建立畫面上的 TextView 來顯示資訊 */
        resultText = new TextView(this);
        setContentView(resultText);

        /* 建立通知頻道（必要於 Android 8.0+） */
        createNotificationChannel();

        /* 檢查是否已取得使用狀況存取權限 */
        if (!hasUsageStatsPermission()) {
            /* 若無權限，導向使用者去開啟設定頁 */
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            resultText.setText("請開啟『使用狀況存取』權限後回來重新啟動 App");
        } else {
            /* 若已有權限，開始定時更新資訊 */
            startRepeatingUpdate();
        }
    }

    /* 檢查應用是否擁有「使用狀況存取權限」 */
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName()
        );
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    /* 啟動每 10 秒更新一次的任務 */
    private void startRepeatingUpdate() {
        updater = new Runnable() {
            @Override
            public void run() {
                updateInfo(); // 更新顯示資訊
                handler.postDelayed(this, 10000); // 每 10 秒重新排程
            }
        };
        updater.run(); // 立即執行第一次
    }

    /* 更新畫面與通知上的資訊 */
    private void updateInfo() {
        long youtubeTime = getTodayYouTubeTimeInMs_UsingEvents(); // 取得今天使用 YouTube 的總時間
        long bootTime = SystemClock.elapsedRealtime(); // 從開機至今的時間（毫秒）
        String bootDuration = formatDuration(bootTime); // 格式化顯示

        /* 取得現在的時間 */
        String nowTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        long Seconds = youtubeTime / 1000 ; // 換算為秒鐘
        /* 準備要顯示的輸出文字 */
        String output = "🕒 現在時間：" + nowTime +
                "\n⏱️ 開機時間：" + bootDuration +
                "\n📺 今天使用 YouTube：" + Seconds + " 秒鐘";

        /* 印出 Log 與顯示在畫面 */
        Log.d(TAG, output);
        resultText.setText(output);

        /* 顯示通知 */
        showNotification(Seconds);
    }
    private long getTodayYouTubeTimeInMs_UsingEvents() {
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                getSystemService(Context.USAGE_STATS_SERVICE);

        // 取得今天的 00:00 起始時間
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis(); // 現在
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis(); // 今天凌晨

        UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();

        long totalTime = 0;
        long lastStartTime = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);

            if (!YOUTUBE_PACKAGE_NAME.equals(event.getPackageName())) {
                continue;
            }

            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // App 進入前景，記錄開始時間
                lastStartTime = event.getTimeStamp();
            } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && lastStartTime != 0) {
                // App 退到背景，累加時間
                totalTime += event.getTimeStamp() - lastStartTime;
                lastStartTime = 0;
            }
        }

        // 如果現在還在前景中（尚未結束），加上到現在的時間
        if (lastStartTime != 0) {
            totalTime += endTime - lastStartTime;
        }

        return totalTime;
    }


    /* 將毫秒格式化成「幾小時幾分幾秒」字串 */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours + " 小時 " + minutes + " 分 " + secs + " 秒";
    }

    /* 顯示持續性通知來提示使用者 */
    private void showNotification(long youtubeSeconds) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        /* 建立點擊通知後回到本 App 的 Intent */
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        /* 建立通知內容 */
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("YouTube 使用監控")
                .setContentText("今天使用時間：約 " + youtubeSeconds + " 秒鐘")
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true) // 無法滑掉
                .setPriority(NotificationCompat.PRIORITY_LOW);

        /* 發出通知 */
        manager.notify(1, builder.build());
    }

    /* 建立通知頻道（Android 8.0 以上版本必要） */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "YT Usage Channel";
            String description = "Channel for YouTube usage notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel); // 建立頻道
            }
        }
    }
}
