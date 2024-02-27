package com.proyecto.appmaster;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.internal.Storage;
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

import org.checkerframework.checker.units.qual.C;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import de.j4velin.mapsmeasure.R;
import de.j4velin.mapsmeasure.databinding.ActivityProfileEditBinding;

public class ProfileEditActivity extends AppCompatActivity {

    private ActivityProfileEditBinding binding;

    private FirebaseAuth firebaseAuth;

    //progress dialog
    private ProgressDialog progressDialog;

    private static final String TAG = "PROFILE_EDIT_TAG";

    private Uri imageUri = null;

    private String name = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //setup progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false); //dont dismiss while clicking outside of progress dialos

        firebaseAuth = FirebaseAuth.getInstance();
        loadUserInfo();

        //handle click, goback

        binding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        binding.profileTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showImageAttachMenu();
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

    private void validateData() {

        name = binding.nameEt.getText().toString().trim();

        if (TextUtils.isEmpty(name)){
            Toast.makeText(this,"Enter name...",Toast.LENGTH_SHORT).show();
        }else {
            //name is entered
            if (imageUri==null){
                //need to update without image
                updateProfile("");
            }
            else {
                //need to update with image
                updateProfile();
            }
        }
    }

    private void updateProfile() {
        Log.d(TAG,"uploadImage: Uploading profile image...");
        progressDialog.setMessage("Updating profile image");
        progressDialog.show();

        //image path and name, use uid to replace previous

        String filePathAndName = "ProfileImages/"+firebaseAuth.getUid();

        //storage reference
        StorageReference reference = FirebaseStorage.getInstance().getReference(filePathAndName);
        reference.putFile(imageUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG,"onSuccess: Profile image uploaded");
                        Log.d(TAG,"onSuccess: Getting url of uploaded image");
                        Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();

                        while (!uriTask.isSuccessful());
                        String uploadedImageUrl = ""+uriTask.getResult();
                        Log.d(TAG,"onSuccess: Uploaded Image URL: "+uploadedImageUrl);

                        updateProfile(uploadedImageUrl);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"onFailure: Failed to upload image due to "+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(ProfileEditActivity.this, "Failed to upload image due to "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateProfile(String imageUrl){
        Log.d(TAG,"updateProfile: Updating user profile");
        progressDialog.setMessage("Updating user profile");
        progressDialog.show();

        //setup data to update in db

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name",""+name);
        if (imageUri != null){
            hashMap.put("profileImage", ""+imageUrl);
        }

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference.child(firebaseAuth.getUid())
                .updateChildren(hashMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG,"onSuccess: Profile updated...");
                        progressDialog.dismiss();
                        Toast.makeText(ProfileEditActivity.this,"Profile updated...",Toast.LENGTH_SHORT).show();;

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"onFailure: Failed to update db due to "+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(ProfileEditActivity.this,"Failed to update db due to "+e.getMessage(),Toast.LENGTH_SHORT).show();;
                    }
                });
    }
    private void showImageAttachMenu() {
        //init setup popup menu

        PopupMenu popupMenu = new PopupMenu(this, binding.profileTv);

        popupMenu.getMenu().add(Menu.NONE,0,0,"Camera");
        popupMenu.getMenu().add(Menu.NONE,0,0,"Gallery");

        popupMenu.show();

        //handle menu
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                //get id of item clicked
                int which = menuItem.getItemId();

                if (which==0){
                    //camera clicked
                    pickImageCamera();
                } else if (which==1) {
                    //gallery clicked
                    pickImageGallery();
                }
                return false;
            }
        });
    }

    private void pickImageGallery() {
        //intent to pick image from gallery
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent);
    }

    private void pickImageCamera() {
        //intent to pick image from camera
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Pick");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Image Description");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraActivityResultLauncher.launch(intent);
    }

    private ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {

                    if(o.getResultCode() == Activity.RESULT_OK){
                        Log.d(TAG,"onActivityResult: Picked from Camera "+imageUri);
                        Intent data = o.getData(); //no need here as in camera case we already have image

                        binding.profileTv.setImageURI(imageUri);

                    }
                    else{
                        Toast.makeText(ProfileEditActivity.this,"Cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {

                    if(o.getResultCode() == Activity.RESULT_OK){
                        Log.d(TAG,"onActivityResult: "+imageUri);
                        Intent data = o.getData(); //no need here as in camera case we already have image
                        imageUri = data.getData();
                        Log.d(TAG,"onActivityResult: Picked from Gallery "+imageUri);
                        binding.profileTv.setImageURI(imageUri);

                    }
                    else{
                        Toast.makeText(ProfileEditActivity.this,"Cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void loadUserInfo() {
        Log.d("TAG","loadUserInfo: Loadin User infor of user "+firebaseAuth.getUid());

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");

        ref.child(firebaseAuth.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        String email = ""+snapshot.child("email").getValue();
                        String name = ""+snapshot.child("name").getValue();
                        String profileImage = ""+snapshot.child("profileImage").getValue();
                        String timestamp = ""+snapshot.child("timestamp").getValue();
                        String uid = ""+snapshot.child("uid").getValue();
                        String userType = ""+snapshot.child("userType").getValue();


                        binding.nameEt.setText(name);

                        //set image, usinde glide

                        Glide.with(ProfileEditActivity.this)
                                .load(profileImage)
                                .placeholder(R.drawable.ic_person_gray)
                                .into(binding.profileTv);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });

    }
}