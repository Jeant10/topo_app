package com.proyecto.appmaster;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import de.j4velin.mapsmeasure.R;
import de.j4velin.mapsmeasure.databinding.RowUserBinding;

public class AdapterUser extends RecyclerView.Adapter<AdapterUser.HolderUser> implements Filterable {

    private Context context;
    public ArrayList<UserModel> userArrayList, filterList;

    private static final String TAG = "USER_ADAPTER_TAG";
    private RowUserBinding binding;
    private FilterUser filter;

    //instance of our filter
    private ProgressDialog progressDialog;

    public AdapterUser(Context context, ArrayList<UserModel> userArrayList) {
        this.context = context;
        this.userArrayList = userArrayList;
        this.filterList = userArrayList;

        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);
    }

    @NonNull
    @Override
    public HolderUser onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        binding = RowUserBinding.inflate(LayoutInflater.from(context),parent,false);

        return new HolderUser(binding.getRoot());
    }

    @Override
    public void onBindViewHolder(@NonNull HolderUser holder, int position) {

        UserModel model = userArrayList.get(position);
        String uid = model.getUid();
        String name = model.getName();
        String email = model.getEmail();
        String profileImage = model.getProfileImage();
        long timestamp = model.getTimestamp();
        String userType = model.getUserType();
        boolean status = model.isState();

        SimpleDateFormat sdf = new SimpleDateFormat("dd/mm/yyy");

        String formattedDate = sdf.format(new Date(timestamp));

        holder.nameTv.setText(name);
        holder.emailTv.setText(email);
        holder.dateTv.setText(formattedDate);
        holder.typeuserTv.setText(userType);

        Glide.with(context)
                .load(profileImage)
                .placeholder(R.drawable.ic_person_gray)
                .into(holder.imageTv);

        holder.switchUser.setChecked(status);
        holder.switchUser.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b){
                    changeStateUser(b, model, holder);
                }else{
                    changeStateUser(b, model,holder);
                }
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, UserEditActivity.class);
                intent.putExtra("userId",uid);
                context.startActivity(intent);
            }
        });

    }

    private void changeStateUser(boolean status, UserModel model, HolderUser holder) {

        Log.d(TAG,"updateProfile: Updating status");

        //setup data to update in db

        HashMap<String, Object> hashMap = new HashMap<>();
        boolean state = status;
        String uid = ""+model.getUid();
        hashMap.put("state",state);

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference.child(uid)
                .updateChildren(hashMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG,"onSuccess: State updated...");
                        progressDialog.dismiss();
                        Toast.makeText(context,"State updated...",Toast.LENGTH_SHORT).show();;
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"onFailure: Failed to update status to "+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(context,"Failed to update status to "+e.getMessage(),Toast.LENGTH_SHORT).show();;
                    }
                });
    }

    @Override
    public int getItemCount() {
        return userArrayList.size();
    }

    @Override
    public Filter getFilter() {
        if (filter==null){
            filter = new FilterUser(filterList,this);
        }
        return filter;
    }

    class HolderUser extends RecyclerView.ViewHolder{
        ImageView imageTv;
        TextView nameTv, emailTv, typeuserTv, dateTv;
        Switch switchUser;
        public HolderUser(@NonNull View itemView) {
            super(itemView);

            imageTv = binding.imageTv;
            nameTv = binding.nameTv;
            emailTv = binding.emailTv;
            typeuserTv = binding.typeuserTv;
            dateTv = binding.dateTv;
            switchUser = binding.switchStd;

        }
    }
}
