package com.proyecto.appmaster;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;

import de.j4velin.mapsmeasure.R;
import de.j4velin.mapsmeasure.databinding.ActivityUserEditBinding;

public class UserEditActivity extends AppCompatActivity {


    private Context context;
    private ActivityUserEditBinding binding;

    private static final String TAG = "USER_EDIT_TAG";

    private ProgressDialog progressDialog;
    private Uri imageUri = null;

    private String rol = "";

    private String userUid = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false); //dont dismiss while clicking outside of progress dialos

        Intent intent = getIntent();
        userUid = intent.getStringExtra("userId");

        loadUserInfo(userUid);

        //handle click, goback

        binding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        //handle click, update profile
        binding.updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validateData();
            }
        });

    }

    private void loadUserInfo(String userUid) {
        Log.d(TAG,"loadUserInfo: Loading User information of user "+userUid);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");

        ref.child(userUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        String profileImage = ""+snapshot.child("profileImage").getValue();
                        String userType = ""+snapshot.child("userType").getValue();

                        binding.typeEt.setText(userType);

                        //set image, usinde glide

                        Glide.with(getApplicationContext())
                                .load(profileImage)
                                .placeholder(R.drawable.ic_person_gray)
                                .into(binding.userTv);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }

    private void validateData() {

        rol = binding.typeEt.getText().toString().trim().toLowerCase();

        if (TextUtils.isEmpty(rol)){
            Toast.makeText(this,"Enter your rol...",Toast.LENGTH_SHORT).show();
        } else if (rol.equals("admin") || rol.equals("user")){
            updateProfile();
        }
        else {
            Toast.makeText(this,"Roles must be admin or user...",Toast.LENGTH_SHORT).show();
        }
    }

    private void updateProfile(){
        Log.d(TAG,"updateProfile: Updating user");
        progressDialog.setMessage("Updating user");
        progressDialog.show();

        //setup data to update in db

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("userType",""+rol);

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference.child(userUid)
                .updateChildren(hashMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG,"onSuccess: User updated...");
                        progressDialog.dismiss();
                        Toast.makeText(UserEditActivity.this,"User updated...",Toast.LENGTH_SHORT).show();;

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"onFailure: Failed to update db due to "+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(UserEditActivity.this,"Failed to update db due to "+e.getMessage(),Toast.LENGTH_SHORT).show();;
                    }
                });
    }



}