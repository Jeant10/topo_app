package com.proyecto.appmaster;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;

import de.j4velin.mapsmeasure.R;
import de.j4velin.mapsmeasure.databinding.ActivityDashboardAdminBinding;

public class DashboardAdminActivity extends AppCompatActivity {

    private ActivityDashboardAdminBinding binding;
    private FirebaseAuth firebaseAuth;
    private ArrayList<ModelCategory> categoryArrayList;

    private AdapterCategory adapterCategory;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardAdminBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();

        checkUser();

        loadCategories();

        //edit text change listern, search
        binding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //called as and when user type letter

                try {
                    adapterCategory.getFilter().filter(charSequence);
                }
                catch (Exception e){

                }

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        binding.logoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                firebaseAuth.signOut();
                checkUser();
            }
        });

        //start category screen
        binding.addCategoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DashboardAdminActivity.this, CategoryAddActivity.class));
            }
        });

        binding.profileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DashboardAdminActivity.this,ProfileActivity.class));
            }
        });

    }

    private void loadCategories() {
        //init array
        categoryArrayList = new ArrayList<>();
        //get all categories from firebase
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                //clear arraylist before adding data

                categoryArrayList.clear();

                for (DataSnapshot ds: snapshot.getChildren()){
                    //get data
                    ModelCategory model = ds.getValue(ModelCategory.class);

                    //add to arraaylist
                    categoryArrayList.add(model);
                }

                //Setup adapter
                adapterCategory = new AdapterCategory(DashboardAdminActivity.this,categoryArrayList);

                //Set adapte to recyclerView

                binding.categoriesRv.setAdapter(adapterCategory);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void checkUser() {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        if(firebaseUser == null){
            startActivity(new Intent(this,MainActivity.class));
            finish();
        }else{
            //logged in, get user info
            String email = firebaseUser.getEmail();

            binding.subTitleTv.setText(email);
        }
    }
}