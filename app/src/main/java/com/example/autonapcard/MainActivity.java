package com.example.autonapcard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.autonapcard.databinding.ActivityMainBinding;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private final String[] permissions = {
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE,
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson =  new Gson();
    private String ispShortName;
    private final ExecutorService httpReqWorker = Executors.newFixedThreadPool(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.napcardButton.setOnClickListener(view -> {
            if (!requestNeededPermission()) {
                Toast.makeText(this, "Chưa cấp quyền đầy đủ nên không nạp card được \uD83D\uDE21",Toast.LENGTH_LONG).show();
                return;
            }

            // lấy isp name
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String isp = telephonyManager.getNetworkOperatorName();
            String[] tokens = isp.toLowerCase().split(" ");
            ispShortName = tokens[tokens.length - 1];
            Toast.makeText(this, "Chuẩn bị nạp tiền: " + ispShortName, Toast.LENGTH_LONG).show();

            // lấy thông tin mã card từ server và thực hiện nap card
            httpReqWorker.submit(()->{
                String url = "https://my-json-server.typicode.com/pqviet07/fakejson/";

                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("model", Build.MODEL);
                reqBody.addProperty("isp", ispShortName);

                MediaType JSON = MediaType.get("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(reqBody.toString(), JSON);
                Request request = new Request.Builder().url(url).post(body).build();

                String listCodeResponse = null;
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        listCodeResponse = gson.fromJson(response.body().string(), String.class);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (listCodeResponse == null) return;
                String[] arrCode = listCodeResponse.split(",");
                for (String code : arrCode) {
                    if (!code.trim().isEmpty() ) {
                        handler.post(()->{
                            String ussdCode = "*100*" + code.trim() + Uri.encode("#");
                            startActivityForResult(new Intent("android.intent.action.CALL", Uri.parse("tel:" + ussdCode)), 123);
                        });
                        try {
                            Thread.sleep(20000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestNeededPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        httpReqWorker.shutdown();
    }

    boolean requestNeededPermission() {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            // Request the permissions that were not granted
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            Map<String, Integer> perms = new HashMap<>();
            // Initialize the map with both permissions
            for (String permission : permissions) {
                perms.put(permission, PackageManager.PERMISSION_GRANTED);
            }
            // Fill with actual results from user
            if (grantResults.length > 0) {
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                }
                // Check for all permissions
                boolean allPermissionsGranted = true;
                for (String permission : permissions) {
                    allPermissionsGranted = allPermissionsGranted && (perms.get(permission) == PackageManager.PERMISSION_GRANTED);
                }

                if (!allPermissionsGranted) {
                    Toast.makeText(this, "Phải cấp quyền  \uD83D\uDE21", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}