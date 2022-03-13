package com.gmail.calorious.igdownloader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private final int STORAGE_REQUEST_CODE = 320550;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        requestPermissions("STORAGE");

    }




    // Permissions
    private void requestPermissions(String permission_or_permission_group) {
        if (permission_or_permission_group.equalsIgnoreCase("storage")) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                return;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == STORAGE_REQUEST_CODE) {
            if (Arrays.stream(grantResults).allMatch(i -> i == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Successfully granted permission to STORAGE!", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Error: Could not obtain STORAGE permission.", Toast.LENGTH_LONG).show();
        }
    }
}
