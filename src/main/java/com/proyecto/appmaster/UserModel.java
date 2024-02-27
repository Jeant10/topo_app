package com.proyecto.appmaster;

public class UserModel {

    String email,name,profileImage,timestamp,uid,userType;

    public UserModel(String email, String name, String profileImage, String timestamp, String uid, String userType) {
        this.email = email;
        this.name = name;
        this.profileImage = profileImage;
        this.timestamp = timestamp;
        this.uid = uid;
        this.userType = userType;
    }
}
