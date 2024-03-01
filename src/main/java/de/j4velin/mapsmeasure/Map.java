package de.j4velin.mapsmeasure;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.PermissionChecker;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.maps.android.SphericalUtil;
import com.proyecto.appmaster.GeofireProvider;
import com.proyecto.appmaster.MainActivity;
import com.proyecto.appmaster.ProfileActivity;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import de.j4velin.mapsmeasure.wrapper.API17Wrapper;

public class Map extends FragmentActivity implements OnMapReadyCallback {

    private final static int COLOR_LINE = Color.argb(128, 0, 0, 0), COLOR_POINT =
            Color.argb(128, 255, 0, 0);
    private final static float LINE_WIDTH = 5f;
    private final static int REQUEST_LOCATION_PERMISSION = 0;
    private FirebaseAuth firebaseAuth;
    private final static String SKU = "de.j4velin.mapsmeasure.billing.pro";

    enum MeasureType {
        DISTANCE, AREA, ELEVATION
    }

    // the map to draw to
    private GoogleMap mMap;
    private DrawerLayout mDrawerLayout;

    private String userId;

    // the stacks - everytime the user touches the map, an entry is pushed
    private final Stack<LatLng> trace = new Stack<>();
    private final Stack<Polyline> lines = new Stack<>();
    private final Stack<Marker> points = new Stack<>();

    private Polygon areaOverlay;

    private Pair<Float, Float> altitude;
    private float distance; // in meters
    private MeasureType type; // the currently selected measure type
    private TextView valueTv; // the view displaying the distance/area & unit

    static boolean metric; // display in metric units

    private static BitmapDescriptor marker;

    private static boolean PRO_VERSION = false;
    static String ELEVATION_API_KEY;

    private DrawerListAdapter drawerListAdapert;

    private ElevationView elevationView;

    private boolean navBarOnRight;
    private int drawerSize, statusbar, navBarHeight;

    // store last location callback in case we dont have location permission yet and need to execute it later
    private LocationCallback lastLocationCallback;

    private BillingClient billingClient;

    private List<Marker> mUsersMarkers = new ArrayList<>();

    private boolean isFirstTime = true;

    private LatLng mCurrentLatIng;

    private GeofireProvider mgeoFireProv;

    @SuppressLint("ConstantLocale")
    final static NumberFormat formatter_two_dec = NumberFormat.getInstance(Locale.getDefault());

    @SuppressLint("ConstantLocale")
    private final static NumberFormat formatter_no_dec =
            NumberFormat.getInstance(Locale.getDefault());

    private final PurchasesUpdatedListener purchasesUpdatedListener = (result, purchases) -> {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                boolean pro = purchase.getProducts().contains(SKU);
                PRO_VERSION = pro;
                getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("pro", pro).apply();
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams acknowledgePurchaseParams =
                            AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.getPurchaseToken())
                                    .build();
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                        if (BuildConfig.DEBUG) {
                            Logger.log("acknowledgePurchaseResponse: " + billingResult);
                        }
                    });
                }
            }
        } else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            Dialogs.getShowErrorDialog(this, getString(R.string.purchase_error, result.getResponseCode())).show();
        }
    };

    public GoogleMap getMap() {
        return mMap;
    }

    public void closeDrawer() {
        if (mDrawerLayout != null) mDrawerLayout.closeDrawers();
    }

    /**
     * Get the formatted string for the valueTextView.
     * <p/>
     * Depending on whether 'showArea' is set, the returned string shows the
     * distance of the trace or the area between them. If 'showArea' is set,
     * this call might be expensive as the area is computed here and not cached.
     *
     * @return the formatted text for the valueTextView
     */
    private String getFormattedString() {
        if (type == MeasureType.DISTANCE) {
            elevationView.setVisibility(View.GONE);
            if (metric) {
                if (distance > 1000) return formatter_two_dec.format(distance / 1000) + " km";
                else return formatter_two_dec.format(Math.max(0, distance)) + " m";
            } else {
                if (distance > 1609) return formatter_two_dec.format(distance / 1609.344f) + " mi";
                else if (distance > 30)
                    return formatter_two_dec.format(distance / 1609.344f) + " mi\n" +
                            formatter_two_dec.format(Math.max(0, distance / 0.3048f)) + " ft";
                else return formatter_two_dec.format(Math.max(0, distance / 0.3048f)) + " ft";
            }
        } else if (type == MeasureType.AREA) {
            elevationView.setVisibility(View.GONE);
            double area;
            if (areaOverlay != null) areaOverlay.remove();
            if (trace.size() >= 3) {
                area = SphericalUtil.computeArea(trace);
                areaOverlay = mMap.addPolygon(
                        new PolygonOptions().addAll(trace).strokeWidth(0).fillColor(COLOR_POINT));
            } else {
                area = 0;
            }
            if (metric) {
                if (area > 1000000)
                    return formatter_two_dec.format(Math.max(0, area / 1000000d)) + " km²";
                else return formatter_no_dec.format(Math.max(0, area)) + " m²";
            } else {
                if (area >= 2589989)
                    return formatter_two_dec.format(Math.max(0, area / 2589988.110336d)) + " mi²";
                else return formatter_no_dec.format(Math.max(0, area / 0.09290304d)) + " ft²";
            }
        } else if (type == MeasureType.ELEVATION) {
            if (altitude == null) {
                final Handler h = new Handler();
                new Thread(() -> {
                    try {
                        altitude = Util.updateElevationView(elevationView, trace);
                        h.post(() -> {
                            if (isFinishing()) return;
                            if (altitude == null) {
                                Dialogs.getElevationErrorDialog(Map.this).show();
                                changeType(MeasureType.DISTANCE);
                            } else {
                                updateValueText();
                                elevationView.invalidate();
                            }
                        });
                    } catch (IOException e) {
                        h.post(() -> {
                            if (isFinishing()) return;
                            Dialogs.getElevationErrorDialog(Map.this).show();
                        });
                    }
                }).start();
                return "Loading...";
            } else {
                String re = metric ? formatter_two_dec.format(altitude.first) + " m\u2B06, " +
                        formatter_two_dec.format(altitude.second) + " m\u2B07" :
                        formatter_two_dec.format(altitude.first / 0.3048f) + " ft\u2B06" +
                                formatter_two_dec.format(altitude.second / 0.3048f) + " ft\u2B07";
                if (!trace.isEmpty()) {
                    try {
                        float lastPoint = Util.lastElevation;
                        if (lastPoint > -Float.MAX_VALUE) {
                            re += "\n" + (metric ? formatter_two_dec.format(lastPoint) + " m" :
                                    formatter_two_dec.format(lastPoint / 0.3048f) + " ft");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                elevationView.setVisibility(trace.size() > 1 ? View.VISIBLE : View.GONE);
                altitude = null;
                return re;
            }
        } else {
            return "not yet supported";
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        try {
            metric = savedInstanceState.getBoolean("metric");
            @SuppressWarnings("unchecked")
            // Casting to Stack<LatLng> apparently results in
            // "java.lang.ClassCastException: java.util.ArrayList cannot be cast to java.util.Stack"
            // on some devices
            List<LatLng> tmp = (List<LatLng>) savedInstanceState.getSerializable("trace");
            if (tmp != null) {
                for (LatLng latLng : tmp) {
                    addPoint(latLng);
                }
            }
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(savedInstanceState.getDouble("position-lat"),
                            savedInstanceState.getDouble("position-lon")),
                    savedInstanceState.getFloat("position-zoom")));

                    //aqui tal vez
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        outState.putSerializable("trace", trace);
        outState.putBoolean("metric", metric);
        if (mMap != null) { // might be null if there is an issue with Google
            // Play Services
            outState.putDouble("position-lon", mMap.getCameraPosition().target.longitude);
            outState.putDouble("position-lat", mMap.getCameraPosition().target.latitude);
            outState.putFloat("position-zoom", mMap.getCameraPosition().zoom);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Adds a new point, calculates the new distance and draws the point and a
     * line to it
     *
     * @param p the new point
     */
    void addPoint(final LatLng p) {
        if (!trace.isEmpty()) {
            lines.push(mMap.addPolyline(
                    new PolylineOptions().color(COLOR_LINE).width(LINE_WIDTH).add(trace.peek())
                            .add(p)));
            distance += SphericalUtil.computeDistanceBetween(p, trace.peek());
        }
        points.push(drawMarker(p));
        trace.push(p);
        updateValueText();
    }

    /**
     * Resets the map by removing all points, lines and setting the text to 0
     */
    void clear() {
        mMap.clear();
        trace.clear();
        lines.clear();
        points.clear();
        distance = 0;
        updateValueText();
    }

    /**
     * Removes the last added point, the line to it and updates the distance
     */
    private void removeLast() {
        if (trace.isEmpty()) return;
        points.pop().remove();
        LatLng remove = trace.pop();
        if (!trace.isEmpty())
            distance -= SphericalUtil.computeDistanceBetween(remove, trace.peek());
        if (!lines.isEmpty()) lines.pop().remove();
        updateValueText();
    }

    @SuppressLint("NewApi")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if(BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 23 &&
        PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PermissionChecker.PERMISSION_GRANTED &&
        PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED &&
        PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED
        ){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LOCATION_PERMISSION);
        }

        firebaseAuth = FirebaseAuth.getInstance();
        userId = firebaseAuth.getCurrentUser().getUid();
        mgeoFireProv = new GeofireProvider();
        checkUser();

        try {
            super.onCreate(savedInstanceState);
        } catch (final BadParcelableException bpe) {
            if (BuildConfig.DEBUG) Logger.log(bpe);
        }
        billingClient = BillingClient.newBuilder(this)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();
        init();
    }

    /**
     * Initializes everything
     */
    private void init() {
        setContentView(R.layout.activity_map);

        elevationView = findViewById(R.id.elevationsview);

        formatter_no_dec.setMaximumFractionDigits(0);
        formatter_two_dec.setMaximumFractionDigits(2);

        final SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);

        ELEVATION_API_KEY =
                prefs.getString("elevation_api_key", getString(R.string.elevation_api_key));

        // use metric a the default everywhere, except in the US
        metric = prefs.getBoolean("metric", !Locale.getDefault().equals(Locale.US));

        final View topCenterOverlay = findViewById(R.id.topCenterOverlay);
        mDrawerLayout = findViewById(R.id.drawer_layout);

        final View menuButton = findViewById(R.id.menu);
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> mDrawerLayout.openDrawer(GravityCompat.START));
        }

        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
                private boolean menuButtonVisible = true;

                @Override
                public void onDrawerStateChanged(int newState) {
                }

                @Override
                public void onDrawerSlide(@NonNull final View drawerView, final float slideOffset) {
                    topCenterOverlay.setAlpha(1 - slideOffset);
                    if (menuButtonVisible && menuButton != null && slideOffset > 0) {
                        menuButton.setVisibility(View.INVISIBLE);
                        menuButtonVisible = false;
                    }
                }

                @Override
                public void onDrawerOpened(@NonNull final View drawerView) {
                    topCenterOverlay.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onDrawerClosed(@NonNull final View drawerView) {
                    topCenterOverlay.setVisibility(View.VISIBLE);
                    if (menuButton != null) {
                        menuButton.setVisibility(View.VISIBLE);
                        menuButtonVisible = true;
                    }
                }
            });
        }

        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(this);

        valueTv = findViewById(R.id.distance);
        updateValueText();
        valueTv.setOnClickListener(v -> {
            if (type == MeasureType.DISTANCE) {
                changeType(MeasureType.AREA);
            }
            // only switch to elevation mode is an internet connection is
            // available and user has access to this feature
            else if (type == MeasureType.AREA && Util.checkInternetConnection(Map.this) &&
                    PRO_VERSION) {
                changeType(MeasureType.ELEVATION);
            } else {
                if (BuildConfig.DEBUG) Logger.log("internet connection available: " +
                        Util.checkInternetConnection(Map.this));
                changeType(MeasureType.DISTANCE);
            }
        });

        View delete = findViewById(R.id.delete);
        delete.setOnClickListener(v -> removeLast());
        delete.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(Map.this);
            builder.setMessage(getString(R.string.delete_all, trace.size()));
            builder.setPositiveButton(android.R.string.yes,
                    (dialog, which) -> {
                        clear();
                        dialog.dismiss();
                    });
            builder.setNegativeButton(android.R.string.no,
                    (dialog, which) -> dialog.dismiss());
            builder.create().show();
            return true;
        });


        // Drawer stuff
        ListView drawerList = findViewById(R.id.left_drawer);
        drawerListAdapert = new DrawerListAdapter(this);
        drawerList.setAdapter(drawerListAdapert);
        drawerList.setDivider(null);
        drawerList.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: // Search before Android 5.0
                    Dialogs.getSearchDialog(Map.this).show();
                    closeDrawer();
                    break;
                case 2: // Units
                    Dialogs.getUnits(Map.this, distance, SphericalUtil.computeArea(trace))
                            .show();
                    closeDrawer();
                    break;
                case 3: // distance
                    changeType(MeasureType.DISTANCE);
                    break;
                case 4: // area
                    changeType(MeasureType.AREA);
                    break;
                case 5: // elevation
                    if (PRO_VERSION) {
                        changeType(MeasureType.ELEVATION);
                    } else {
                        if (billingClient.isReady()) {
                            Dialogs.showElevationAccessDialog(Map.this, () -> {
                                QueryProductDetailsParams queryProductDetailsParams =
                                        QueryProductDetailsParams.newBuilder()
                                                .setProductList(Collections.singletonList(
                                                        QueryProductDetailsParams.Product.newBuilder()
                                                                .setProductId(SKU)
                                                                .setProductType(BillingClient.ProductType.INAPP)
                                                                .build())).build();

                                billingClient.queryProductDetailsAsync(queryProductDetailsParams, (result, list) -> {
                                            for (ProductDetails pd : list) {
                                                if (pd.getProductId().equals(SKU)) {
                                                    List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                                                            Collections.singletonList(
                                                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                                                            .setProductDetails(pd)
                                                                            .build()
                                                            );

                                                    BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                                            .setProductDetailsParamsList(productDetailsParamsList)
                                                            .build();

                                                    BillingResult launchResult = billingClient.launchBillingFlow(this, billingFlowParams);
                                                    if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                                        Dialogs.getShowErrorDialog(this, getString(R.string.purchase_error, launchResult.getResponseCode())).show();
                                                    }
                                                }
                                            }
                                        }
                                );

                            });
                        } else {
                            Dialogs.getShowErrorDialog(this, getString(R.string.purchase_start_error)).show();
                        }
                    }
                    break;
                case 7: // map
                    changeView(GoogleMap.MAP_TYPE_NORMAL);
                    break;
                case 8: // satellite
                    changeView(GoogleMap.MAP_TYPE_HYBRID);
                    break;
                case 9: // terrain
                    changeView(GoogleMap.MAP_TYPE_TERRAIN);
                    break;
                case 11: // save
                    startActivity(new Intent(Map.this, ProfileActivity.class));
                    break;
                case 12: // about
                    firebaseAuth.signOut();
                    checkUser();
                    break;
                default:
                    break;
            }
        });

        changeType(MeasureType.DISTANCE);

        // KitKat translucent decor enabled? -> Add some margin/padding to the drawer
        statusbar = Util.getStatusBarHeight(this);

        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) topCenterOverlay.getLayoutParams();
        lp.setMargins(0, statusbar + 10, 0, 0);
        topCenterOverlay.setLayoutParams(lp);

        // on most devices and in most orientations, the navigation bar
        // should be at the bottom and therefore reduces the available
        // display height
        navBarHeight = Util.getNavigationBarHeight(this);

        DisplayMetrics total, available;
        total = new DisplayMetrics();
        available = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(available);
        API17Wrapper.getRealMetrics(getWindowManager().getDefaultDisplay(), total);

        navBarOnRight = getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                (total.widthPixels - available.widthPixels > 0);

        FrameLayout.LayoutParams elevationParams =
                (FrameLayout.LayoutParams) elevationView.getLayoutParams();

        drawerSize = mDrawerLayout == null ? Util.dpToPx(this, 200) : 0;

        if (navBarOnRight) {
            // in landscape on phones, the navigation bar might be at the
            // right side, reducing the available display width
            drawerList.setPadding(0, statusbar + 10, 0, 0);
            if (menuButton != null) menuButton.setPadding(0, 0, 0, 0);
            elevationParams.setMargins(drawerSize, 0, navBarHeight, 0);
        } else {
            drawerList.setPadding(0, statusbar + 10, 0, 0);
            drawerListAdapert.setMarginBottom(navBarHeight);
            if (menuButton != null) menuButton.setPadding(0, 0, 0, navBarHeight);
            elevationParams.setMargins(Math.max(drawerSize, Util.dpToPx(this, 25)), 0, 0,
                    navBarHeight);
        }
        elevationView.setLayoutParams(elevationParams);

        PRO_VERSION |= prefs.getBoolean("pro", false);

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build(),
                        purchasesUpdatedListener::onPurchasesUpdated
                );
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                if (BuildConfig.DEBUG) Logger.log("billing setup failed");
            }
        });
    }




    //Aqui estas el mapa y mas cosas abajo

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull final GoogleMap googleMap) {
        mMap = googleMap;
        marker = BitmapDescriptorFactory.fromResource(R.drawable.marker);

        changeView(getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("mapView", GoogleMap.MAP_TYPE_NORMAL));

        mMap.setOnMarkerClickListener(click -> {
            addPoint(click.getPosition());
            return true;
        });

        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setOnMyLocationButtonClickListener(() -> {
            getCurrentLocation(location -> {
                if (location != null) {
                    LatLng myLocation =
                            new LatLng(location.getLatitude(), location.getLongitude());
                    double distance = SphericalUtil.computeDistanceBetween(myLocation,
                            mMap.getCameraPosition().target);


                    // Only if the distance is less than 50cm we are on our location, add the marker
                    if (distance < 0.5) {
                        Toast.makeText(Map.this, R.string.marker_on_current_location,
                                Toast.LENGTH_SHORT).show();
                        addPoint(myLocation);
                    } else {
                        if (BuildConfig.DEBUG)
                            Logger.log("location accuracy too bad to add point");
                        moveCamera(myLocation);
                    }
                }
            });
            return true;
        });

        mMap.setOnMapClickListener(this::addPoint);

        if (hasLocationPermission()) {
            mMap.setMyLocationEnabled(true);
        }


        // KitKat translucent decor enabled? -> Add some margin/padding to the map
        if (navBarOnRight) {
            // in landscape on phones, the navigation bar might be at the
            // right side, reducing the available display width
            mMap.setPadding(drawerSize, statusbar, navBarHeight, 0);
        } else {
            mMap.setPadding(0, statusbar, 0, navBarHeight);
        }

        // check if open with csv file
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            try {
                Util.loadFromFile(getIntent().getData(), this);
            } catch (IOException e) {
                if (BuildConfig.DEBUG) Logger.log(e);
                Toast.makeText(this, getString(R.string.error,
                                e.getClass().getSimpleName() + "\n" + e.getMessage()), Toast.LENGTH_LONG)
                        .show();
                e.printStackTrace();
            }
        } else {
            // dont move to current position if started with a csv file
            getCurrentLocation(location -> {
                if (location != null && mMap.getCameraPosition().zoom <= 5) {
                    moveCamera(new LatLng(location.getLatitude(), location.getLongitude()));
                    mgeoFireProv.saveLocation(userId,new LatLng(location.getLatitude(), location.getLongitude()));

                    Log.d("user","obteniendo usuarios");
                    if (isFirstTime){
                        isFirstTime = false;
                        getActiveUsers(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                    //AQUI
                }
            });

        }
    }

    /**
     * Tries to get the users current position
     *
     * @param callback the callback which should be called when we got a location
     */
    @SuppressLint("MissingPermission")
    private void getCurrentLocation(final LocationCallback callback) {
        if (hasLocationPermission()) {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(callback::gotLocation);

        } else { // no permission
            if (Build.VERSION.SDK_INT >= 23) {
                lastLocationCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LOCATION_PERMISSION);
            } else if (BuildConfig.DEBUG) Logger.log("no permission and no way to request them");
        }
    }

    /**
     * Moves the map view to the given position
     *
     * @param pos the position to move to
     */
    public void moveCamera(final LatLng pos) {
        moveCamera(pos, 16f);
    }

    /**
     * Moves the map view to the given position
     *
     * @param pos  the position to move to
     * @param zoom the zoom to apply
     */
    private void moveCamera(final LatLng pos, float zoom) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, zoom));
    }

    /**
     * Change the "type" of measuring: Distance, Area or Altitude
     *
     * @param newType the type to change to
     */
    private void changeType(final MeasureType newType) {
        type = newType;
        drawerListAdapert.changeType(newType);
        updateValueText();
        if (mDrawerLayout != null) mDrawerLayout.closeDrawers();
        if (newType != MeasureType.AREA) {
            if (areaOverlay != null) areaOverlay.remove();
        }
    }

    /**
     * Change between normal map, satellite hybrid and terrain view
     *
     * @param newView the new view, should be one of GoogleMap.MAP_TYPE_NORMAL,
     *                GoogleMap.MAP_TYPE_HYBRID or GoogleMap.MAP_TYPE_TERRAIN
     */
    private void changeView(int newView) {
        if (mMap != null) mMap.setMapType(newView);
        drawerListAdapert.changeView(newView);
        if (mDrawerLayout != null) mDrawerLayout.closeDrawers();
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("mapView", newView)
                .apply();
    }

    /**
     * Draws a marker at the given point.
     * <p/>
     * Should be called when the users touches the map and adds an entry to the
     * stacks
     *
     * @param center the point where the user clicked
     * @return the drawn Polygon
     */
    private Marker drawMarker(final LatLng center) {
        return mMap.addMarker(
                new MarkerOptions().position(center).flat(true).anchor(0.5f, 0.5f).icon(marker));
    }

    /**
     * Updates the valueTextView at the top of the screen
     */
    void updateValueText() {
        if (valueTv != null) valueTv.setText(getFormattedString());
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                getCurrentLocation(lastLocationCallback);
                mMap.setMyLocationEnabled(true);
            } else {
                String savedLocation = getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getString("lastLocation", null);
                if (savedLocation != null && savedLocation.contains("#")) {
                    String[] data = savedLocation.split("#");
                    try {
                        if (data.length == 3 && mMap != null) {
                            moveCamera(new LatLng(Double.parseDouble(data[0]),
                                    Double.parseDouble(data[1])), Float.parseFloat(data[2]));
                        }
                    } catch (NumberFormatException nfe) {
                        nfe.printStackTrace();
                    }
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        if (mDrawerLayout == null) return true;
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) mDrawerLayout.closeDrawers();
        else mDrawerLayout.openDrawer(GravityCompat.START);
        return false;
    }

    //aqui cerrar la localizacion

    @Override
    public void onDestroy() {

        mgeoFireProv.removeLocation(userId);

        super.onDestroy();

        if (mMap != null) {
            CameraPosition lastPosition = mMap.getCameraPosition();
            getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putString("lastLocation",
                            lastPosition.target.latitude + "#" + lastPosition.target.longitude +
                                    "#" + lastPosition.zoom).apply();
        }

    }

    private boolean hasLocationPermission() {
        return PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED && PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED ;
    }

    //user check

    private void checkUser() {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        if(firebaseUser == null){
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }else{
            //logged in, get user info
            String email = firebaseUser.getEmail();

            //binding.subTitleTv.setText(email);
        }
    }



    private void getActiveUsers(LatLng latLng){

        mgeoFireProv.getUsersActive(latLng).addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                for (Marker marker: mUsersMarkers){
                    if (marker.getTag() != null){
                        if (marker.getTag().equals(key)){
                            return;
                        }
                    }
                }

                LatLng userLatIng = new LatLng(location.latitude,location.longitude);
                Marker marker1 = mMap.addMarker(new MarkerOptions().position(userLatIng).title("Usuario Disponible")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_topo)));
                marker1.setTag(key);

                mUsersMarkers.add(marker1);

            }

            @Override
            public void onKeyExited(String key) {
                for (Marker marker: mUsersMarkers){
                    if (marker.getTag() != null){
                        if (marker.getTag().equals(key)){
                            marker.remove();
                            mUsersMarkers.remove(marker);
                            return;
                        }
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                //Actualizar la posicion de cada conductor
                for (Marker marker: mUsersMarkers){
                    if (marker.getTag() != null){
                        if (marker.getTag().equals(key)){
                            marker.setPosition(new LatLng(location.latitude,location.longitude));
                        }
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }


}