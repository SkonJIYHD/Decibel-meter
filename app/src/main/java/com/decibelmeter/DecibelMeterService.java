package com.decibelmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class DecibelMeterService extends Service {
    
    private static final String CHANNEL_ID = "DecibelMeterServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecording();
        return START_STICKY; // 确保服务被系统杀死后能够重启
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "分贝仪服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("分贝仪后台监测服务");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("分贝仪正在后台运行")
                .setContentText("正在持续监测环境噪音...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }
    
    private void startRecording() {
        if (isRecording) return;
        
        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT);
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                sampleRate, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT, 
                bufferSize);
        
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            isRecording = true;
            audioRecord.startRecording();
            
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    processAudioData();
                }
            });
            recordingThread.start();
        }
    }
    
    private void stopRecording() {
        isRecording = false;
        
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e("DecibelMeterService", "Recording thread interrupted", e);
            }
            recordingThread = null;
        }
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }
    
    private void processAudioData() {
        short[] buffer = new short[1024];
        
        while (isRecording) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
                // 处理音频数据，计算分贝值
                double decibels = calculateDecibels(buffer, read);
                
                // 这里可以将数据发送到主Activity或其他组件
                Intent broadcastIntent = new Intent("DECIBEL_UPDATE");
                broadcastIntent.putExtra("decibelValue", decibels);
                sendBroadcast(broadcastIntent);
            }
        }
    }
    
    private double calculateDecibels(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        double rms = Math.sqrt(sum / length);
        return 20 * Math.log10(rms / 32768.0) + 90;
    }
}