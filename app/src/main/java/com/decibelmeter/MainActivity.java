package com.decibelmeter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;
    private static final int PERMISSION_REQUEST_POST_NOTIFICATIONS = 2;
    private static final String CORRECT_PASSWORD = "宋繁一是傻逼";
    private static final String CHANNEL_ID = "DecibelMeterChannel";
    
    private TextView decibelTextView;
    private TextView statusTextView;
    private LinearLayout mainLayout;
    private GraphView decibelGraph;
    private Button historyButton;
    private Button settingsButton;
    
    private AudioRecord audioRecord;
    private AudioRecord screenAudioRecord;
    private boolean isRecording = false;
    private boolean isScreenRecording = false;
    private Handler handler = new Handler();
    
    private int clickCount = 0;
    private long lastClickTime = 0;
    private boolean isUnlocked = false;
    private GestureDetector gestureDetector;
    
    // 图表相关
    private LineGraphSeries<DataPoint> series;
    private int graphDataPointCount = 0;
    private static final int MAX_DATA_POINTS = 100;
    
    // 历史记录
    private List<Double> decibelHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    
    // 警告音相关
    private Ringtone warningRingtone;
    private double warningThreshold = 80.0;
    private boolean isWarningEnabled = true;
    
    // 服务端相关
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String serverUrl = "http://your-server-url.com/api";
    private boolean isServerSyncEnabled = false;
    
    // 屏幕共享音频检测
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupGestureDetector();
        checkPermissions();
        createNotificationChannel();
        startDecibelMeterService();
        loadSettings();
        syncWithServer();
    }
    
    private void initViews() {
        decibelTextView = findViewById(R.id.decibelTextView);
        statusTextView = findViewById(R.id.statusTextView);
        mainLayout = findViewById(R.id.mainLayout);
        decibelGraph = findViewById(R.id.decibelGraph);
        historyButton = findViewById(R.id.historyButton);
        settingsButton = findViewById(R.id.settingsButton);
        
        // 设置点击监听
        mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick();
            }
        });
        
        // 设置长按监听
        mainLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                handleLongClick();
                return true;
            }
        });
        
        // 历史记录按钮
        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistoryDialog();
            }
        });
        
        // 设置按钮
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });
        
        // 初始化图表
        setupGraph();
    }
    
    private void setupGraph() {
        series = new LineGraphSeries<>();
        series.setColor(Color.WHITE);
        series.setThickness(3);
        decibelGraph.addSeries(series);
        
        decibelGraph.getViewport().setXAxisBoundsManual(true);
        decibelGraph.getViewport().setMinX(0);
        decibelGraph.getViewport().setMaxX(MAX_DATA_POINTS);
        decibelGraph.getViewport().setYAxisBoundsManual(true);
        decibelGraph.getViewport().setMinY(0);
        decibelGraph.getViewport().setMaxY(120);
        
        decibelGraph.getGridLabelRenderer().setGridColor(Color.GRAY);
        decibelGraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        decibelGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return true;
            }
        });
    }
    
    private void handleClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        lastClickTime = currentTime;
        
        if (clickCount >= 10) {
            // 达到10次点击，准备触发密码框
        }
    }
    
    private void handleLongClick() {
        if (clickCount >= 10) {
            showPasswordDialog();
            clickCount = 0;
        }
    }
    
    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入密码");
        
        final EditText input = new EditText(this);
        input.setHint("密码");
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if (CORRECT_PASSWORD.equals(password)) {
                isUnlocked = true;
                statusTextView.setText("已解锁 - 错误模式"); // 修改这里
                Toast.makeText(this, "解锁成功！", Toast.LENGTH_SHORT).show();
                startScreenRecording();
            } else {
                Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void checkPermissions() {
        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.RECORD_AUDIO}, 
                    PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            startRecording();
        }
        
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        PERMISSION_REQUEST_POST_NOTIFICATIONS);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用分贝仪功能", Toast.LENGTH_LONG).show();
                statusTextView.setText("需要麦克风权限");
            }
        }
    }
    
    private void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            isRecording = true;
            audioRecord.startRecording();
            statusTextView.setText("正在监听...");
            startDecibelMonitoring();
        }
    }
    
    private void startScreenRecording() {
        if (!isUnlocked) return;
        
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        screenAudioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        
        if (screenAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            isScreenRecording = true;
            screenAudioRecord.startRecording();
            startScreenAudioMonitoring();
        }
    }
    
    private void startDecibelMonitoring() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buffer = new short[1024];
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        double decibels = calculateDecibels(buffer, read);
                        final double displayDecibels = isUnlocked ? decibels : applyDisguise(decibels);
                        
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateDecibelDisplay(displayDecibels);
                                updateGraph(displayDecibels);
                                addToHistory(displayDecibels);
                                checkWarning(displayDecibels);
                            }
                        });
                    }
                }
            }
        }).start();
    }
    
    private void startScreenAudioMonitoring() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buffer = new short[1024];
                while (isScreenRecording) {
                    int read = screenAudioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        double decibels = calculateDecibels(buffer, read);
                        
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                // 屏幕音频分贝显示
                                TextView screenDecibelTextView = findViewById(R.id.screenDecibelTextView);
                                if (screenDecibelTextView != null) {
                                    screenDecibelTextView.setText(String.format("屏幕音频: %.1f dB", decibels));
                                }
                            }
                        });
                    }
                }
            }
        }).start();
    }
    
    private double calculateDecibels(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        double rms = Math.sqrt(sum / length);
        return 20 * Math.log10(rms / 32768.0) + 90;
    }
    
    private double applyDisguise(double actualDecibels) {
        if (actualDecibels >= 60 && actualDecibels <= 80) {
            return 50 + (actualDecibels - 60) * (20.0 / 20.0);
        }
        return actualDecibels;
    }
    
    private void updateDecibelDisplay(double decibels) {
        decibelTextView.setText(String.format("%.1f dB", decibels));
        
        int color = getColorForDecibel(decibels);
        mainLayout.setBackgroundColor(color);
    }
    
    private void updateGraph(double decibels) {
        series.appendData(new DataPoint(graphDataPointCount, decibels), true, MAX_DATA_POINTS);
        graphDataPointCount++;
    }
    
    private void addToHistory(double decibels) {
        decibelHistory.add(decibels);
        if (decibelHistory.size() > MAX_HISTORY_SIZE) {
            decibelHistory.remove(0);
        }
    }
    
    private void checkWarning(double decibels) {
        if (isWarningEnabled && decibels > warningThreshold && warningRingtone != null) {
            if (!warningRingtone.isPlaying()) {
                warningRingtone.play();
            }
        }
    }
    
    private int getColorForDecibel(double decibels) {
        if (decibels < 40) {
            return 0xFF4CAF50; // 绿色 - 安静
        } else if (decibels < 70) {
            return 0xFFFFEB3B; // 黄色 - 中等
        } else if (decibels < 90) {
            return 0xFFFF9800; // 橙色 - 吵闹
        } else {
            return 0xFFF44336; // 红色 - 很吵
        }
    }
    
    private void showHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("分贝历史记录");
        
        StringBuilder historyText = new StringBuilder();
        for (int i = Math.max(0, decibelHistory.size() - 50); i < decibelHistory.size(); i++) {
            historyText.append(String.format("%d: %.1f dB\n", i + 1, decibelHistory.get(i)));
        }
        
        builder.setMessage(historyText.toString());
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        // 警告音设置
        TextView warningLabel = new TextView(this);
        warningLabel.setText("警告阈值 (dB):");
        layout.addView(warningLabel);
        
        final EditText warningInput = new EditText(this);
        warningInput.setText(String.valueOf(warningThreshold));
        layout.addView(warningInput);
        
        // 警告音选择
        TextView ringtoneLabel = new TextView(this);
        ringtoneLabel.setText("选择警告音:");
        layout.addView(ringtoneLabel);
        
        Spinner ringtoneSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.ringtone_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ringtoneSpinner.setAdapter(adapter);
        layout.addView(ringtoneSpinner);
        
        // 服务端设置
        TextView serverLabel = new TextView(this);
        serverLabel.setText("服务端 URL:");
        layout.addView(serverLabel);
        
        final EditText serverInput = new EditText(this);
        serverInput.setText(serverUrl);
        layout.addView(serverInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("保存", (dialog, which) -> {
            warningThreshold = Double.parseDouble(warningInput.getText().toString());
            serverUrl = serverInput.getText().toString();
            saveSettings();
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "分贝仪服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("分贝仪后台运行通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private void startDecibelMeterService() {
        Intent serviceIntent = new Intent(this, DecibelMeterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private void loadSettings() {
        // 从SharedPreferences加载设置
        android.content.SharedPreferences prefs = getSharedPreferences("DecibelMeterPrefs", MODE_PRIVATE);
        warningThreshold = prefs.getFloat("warningThreshold", 80.0f);
        isWarningEnabled = prefs.getBoolean("isWarningEnabled", true);
        serverUrl = prefs.getString("serverUrl", "http://your-server-url.com/api");
        
        // 加载警告音
        String ringtoneUri = prefs.getString("warningRingtone", null);
        if (ringtoneUri != null) {
            warningRingtone = RingtoneManager.getRingtone(this, Uri.parse(ringtoneUri));
        }
    }
    
    private void saveSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("DecibelMeterPrefs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("warningThreshold", (float) warningThreshold);
        editor.putBoolean("isWarningEnabled", isWarningEnabled);
        editor.putString("serverUrl", serverUrl);
        if (warningRingtone != null) {
            editor.putString("warningRingtone", warningRingtone.getTitle(this));
        }
        editor.apply();
    }
    
    private void syncWithServer() {
        if (!isServerSyncEnabled) return;
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(serverUrl + "/sync");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    
                    JSONObject data = new JSONObject();
                    data.put("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                    data.put("timestamp", System.currentTimeMillis());
                    
                    JSONArray historyArray = new JSONArray();
                    for (Double value : decibelHistory) {
                        historyArray.put(value);
                    }
                    data.put("history", historyArray);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(data.toString().getBytes());
                    os.flush();
                    os.close();
                    
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // 静默同步，不显示通知
                        Log.d("DecibelMeter", "数据同步成功");
                    }
                    
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e("DecibelMeter", "数据同步失败", e);
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
        }
        if (screenAudioRecord != null) {
            isScreenRecording = false;
            screenAudioRecord.stop();
            screenAudioRecord.release();
        }
        if (warningRingtone != null && warningRingtone.isPlaying()) {
            warningRingtone.stop();
        }
        executorService.shutdown();
    }
}