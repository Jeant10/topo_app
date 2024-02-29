package com.proyecto.appmaster;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.j4velin.mapsmeasure.R;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        //init firebase

        firebaseAuth = FirebaseAuth.getInstance();

        //after main screen after 2 seconds

        new Handler().postDelayed(new Runnable() {
            //start main screen
            @Override
            public void run() {
                checkUser();
            }
        },2000);

    }

    private void checkUser() {

        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        if(firebaseUser == null){
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }else{
            //user logged in check user type
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");
            ref.child(firebaseUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    //get user type
                    String userType = ""+snapshot.child("userType").getValue();
                    String userState = ""+snapshot.child("state").getValue();

                    if(userType.equals("user") && userState.equals("true") ){
                        //this is simple user, open user dashboard
                        startActivity(new Intent(SplashActivity.this, DashboardUserActivity.class));
                        finish();
                    }else if (userType.equals("admin") && userState.equals("true")){
                        //this is simple user, open admin dashboard
                        startActivity(new Intent(SplashActivity.this, DashboardAdminActivity.class));
                        finish();
                    }
                    else if(userState.equals("false")){
                        firebaseAuth.signOut();
                        Toast.makeText(SplashActivity.this,"onFailure: El usuario esta desactivado", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SplashActivity.this, LoginActivity.class));

                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        }
    }
}
