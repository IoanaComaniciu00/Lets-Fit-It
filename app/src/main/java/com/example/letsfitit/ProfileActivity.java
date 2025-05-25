package com.example.letsfitit;

import android.content.Intent;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String SHARED_PREFS = "profile_prefs";
    private static final String IMAGE_URI_KEY = "profile_image_uri";

    private ImageView profileImage;
    private TextInputEditText pUsername, pPhone, pHeight, pWeight, pBust, pWaist, pHips,
            pHighHips, pInseam, pSleeve, pTrouser3_4, pTrouserFull;
    private AutoCompleteTextView etGender;
    private Uri imageUri;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        userId = user.getUid();
        db = FirebaseFirestore.getInstance();


        initializeViews();
        setupGenderDropdown();
        loadLocalImage(); // Load image locally
        loadUserData();   // Load other data from Firestore

        // Set up toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Change photo button
        findViewById(R.id.change_photo_btn).setOnClickListener(v -> openImageChooser());

        // Save button
        findViewById(R.id.save_btn).setOnClickListener(v -> saveUserData());
    }

    private void initializeViews() {
        profileImage = findViewById(R.id.profile_image);
        pUsername = findViewById(R.id.p_username);
        pPhone = findViewById(R.id.p_phone);
        etGender = findViewById(R.id.gender);
        pHeight = findViewById(R.id.p_height);
        pWeight = findViewById(R.id.p_weight);
        pBust = findViewById(R.id.p_bust);
        pWaist = findViewById(R.id.p_waist);
        pHips = findViewById(R.id.p_hips);
        pHighHips = findViewById(R.id.p_high_hips);
        pInseam = findViewById(R.id.p_inseam);
        pSleeve = findViewById(R.id.p_sleeve);
        pTrouser3_4 = findViewById(R.id.p_trouser_3_4);
        pTrouserFull = findViewById(R.id.p_trouser_full);
    }

    private void setupGenderDropdown() {
        String[] genders = getResources().getStringArray(R.array.gender_options);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,  // Using built-in layout
                genders
        );
        etGender.setAdapter(adapter);
        etGender.setOnClickListener(v -> etGender.showDropDown());
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            try {
                //URI permission
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(imageUri, takeFlags);

                // Load image
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                profileImage.setImageBitmap(bitmap);
                saveImageLocally(imageUri);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveImageLocally(Uri uri) {
        getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
                .edit()
                .putString(IMAGE_URI_KEY, uri.toString())
                .apply();
        Toast.makeText(this, "Profile image saved", Toast.LENGTH_SHORT).show();
    }

    private void loadLocalImage() {
        String uriString = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
                .getString(IMAGE_URI_KEY, null);

        if (uriString != null) {
            try {
                imageUri = Uri.parse(uriString);
                // Check for permission
                getContentResolver().takePersistableUriPermission(
                        imageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                profileImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                // Clear invalid URI
                getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
                        .edit()
                        .remove(IMAGE_URI_KEY)
                        .apply();
            }
        }
    }


    private void loadUserData() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Load personal info
                        pUsername.setText(documentSnapshot.getString("username"));
                        pPhone.setText(documentSnapshot.getString("phone"));
                        etGender.setText(documentSnapshot.getString("gender"), false);

                        // Load measurements
                        Map<String, Object> measurements = documentSnapshot.getData();
                        if (measurements != null) {
                            pHeight.setText(String.valueOf(measurements.get("height")));
                            pWeight.setText(String.valueOf(measurements.get("weight")));
                            pBust.setText(String.valueOf(measurements.get("bust")));
                            pWaist.setText(String.valueOf(measurements.get("waist")));
                            pHips.setText(String.valueOf(measurements.get("hips")));
                            pHighHips.setText(String.valueOf(measurements.get("highHips")));
                            pInseam.setText(String.valueOf(measurements.get("inseam")));
                            pSleeve.setText(String.valueOf(measurements.get("sleeve")));
                            pTrouser3_4.setText(String.valueOf(measurements.get("trouser3_4")));
                            pTrouserFull.setText(String.valueOf(measurements.get("trouserFull")));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading data", Toast.LENGTH_SHORT).show());
    }

    private void saveUserData() {

        Map<String, Object> userData = new HashMap<>();
        userData.put("username", Objects.requireNonNull(pUsername.getText()).toString().trim());
        userData.put("phone", Objects.requireNonNull(pPhone.getText()).toString().trim());
        userData.put("gender", etGender.getText().toString().trim());


        userData.put("height", parseDouble(Objects.requireNonNull(pHeight.getText()).toString()));
        userData.put("weight", parseDouble(Objects.requireNonNull(pWeight.getText()).toString()));
        userData.put("bust", parseDouble(Objects.requireNonNull(pBust.getText()).toString()));
        userData.put("waist", parseDouble(Objects.requireNonNull(pWaist.getText()).toString()));
        userData.put("hips", parseDouble(Objects.requireNonNull(pHips.getText()).toString()));
        userData.put("highHips", parseDouble(Objects.requireNonNull(pHighHips.getText()).toString()));
        userData.put("inseam", parseDouble(Objects.requireNonNull(pInseam.getText()).toString()));
        userData.put("sleeve", parseDouble(Objects.requireNonNull(pSleeve.getText()).toString()));
        userData.put("trouser3_4", parseDouble(Objects.requireNonNull(pTrouser3_4.getText()).toString()));
        userData.put("trouserFull", parseDouble(Objects.requireNonNull(pTrouserFull.getText()).toString()));

        // Save to Firestore
        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}