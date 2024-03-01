package com.proyecto.appmaster;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class GeofireProvider {

    private DatabaseReference mDatabase;
    private GeoFire geoFire;

    public GeofireProvider() {
        mDatabase = FirebaseDatabase.getInstance().getReference().child("activeUsers");
        geoFire = new GeoFire(mDatabase);
    }

    public void saveLocation(String id, LatLng latLng){
        geoFire.setLocation(id,new GeoLocation(latLng.latitude,latLng.longitude));
    }

    public void removeLocation(String id){
        geoFire.removeLocation(id);
    }

    public GeoQuery getUsersActive(LatLng latLng){
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(latLng.latitude, latLng.longitude),5);
        geoQuery.removeAllListeners();
        return geoQuery;
    }
}
