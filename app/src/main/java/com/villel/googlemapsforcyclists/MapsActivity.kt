package com.villel.googlemapsforcyclists

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.villel.googlemapsforcyclists.API.API
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder
import com.google.maps.android.PolyUtil
import com.villel.googlemapsforcyclists.API.Route
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback {

    // for constants
    companion object {

        private const val MAX_ZOOM = 20f
        private const val MIN_ZOOM = 4f
        private const val DEFAULT_MAP_TYPE = GoogleMap.MAP_TYPE_NORMAL
        private const val ALL_PERMISSIONS = 1
        private val PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.INTERNET)
        private const val CAMERA_MOVE_THRESHOLD = 0.0001

        // it works like a constant so why not put it here
        private val LOCATION_REQUEST = LocationRequest.create()?.apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    } // companion object

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var retrofit: Retrofit
    private lateinit var api: API
    lateinit var gson: Gson
    private var lastKnownLocation: LatLng? = null
    private var destination: LatLng? = null
    private var permissionsGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        handlePermissions()

        gson = Gson()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getInitialLocation()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult?) {

                locationResult ?: return // handy null check!
                for (location in locationResult.locations){

                    val newLastKnownLocation = locationToLatLng(location)
                    if (isOverCameraMoveThreshold(lastKnownLocation, newLastKnownLocation)) {

                        moveCameraTo(newLastKnownLocation) // only move the camera when the user is moving
                    }
                    lastKnownLocation = newLastKnownLocation
                    Log.d("VITTU", "location: " + location)
                } // for
            } // onLocationResult
        } // locationCallback

        val client = OkHttpClient.Builder()
        val interceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger { msg -> Log.d("VITTU", "body: " + msg) })
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        client.interceptors().add(interceptor)
        val finalClient = client.build()

        val gson2 = GsonBuilder()
            .setLenient()
            .create()
        retrofit = Retrofit.Builder()
            .baseUrl(API.BASE_URL)
            // .client(finalClient) // enable to view logs
            .addConverterFactory(GsonConverterFactory.create(gson2))
            .build()
        api = retrofit.create(API::class.java)

    } // onCreate

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (permissionsGranted) startLocationUpdates()
    }

    override fun onMapReady(googleMap: GoogleMap) {

        if (!permissionsGranted) return

        map = googleMap
        setupMap()

        startLocationUpdates()
    }

    // this might be unnecessary; consider removing
    private fun getInitialLocation() {

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location : Location? ->

                    lastKnownLocation = locationToLatLng(location)
                    moveCameraTo(lastKnownLocation)
                    // Log.d("VITTU", "initial location: " + lastKnownLocation)
                }
        } catch (e: SecurityException) {
            // TODO: do something...
        }
    } // getInitialLocation

    private fun startLocationUpdates() {

        // TODO: add the SettingsRequest stuffs here

        try {
            fusedLocationClient.requestLocationUpdates(LOCATION_REQUEST,
                locationCallback,
                null /* Looper */)
        } catch (e: SecurityException) {
            // TODO: handle exception...
        }
    } // startLocationUpdates

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun setupMap() {

        try {
            map.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            // TODO: do smthng...
        }

        map.setOnMyLocationClickListener {
                location: Location -> Toast.makeText(this, "Current location:\n" + locationToLatLng(location), Toast.LENGTH_LONG).show()
        }

        map.setOnMapClickListener { clickedPoint: LatLng ->

            map.clear()
            destination = clickedPoint
            val fromPoint = lastKnownLocation

            Log.d("VITTU", "clicked point: " + destination)
            if (fromPoint != null) {

                // TODO: replace this mess with RxJava ??
              val call = api.fetchRoute(gson.toJson(fromPoint), gson.toJson(clickedPoint))
                call.enqueue(object : Callback<Route> {

                    override fun onResponse(call: Call<Route>?, response: Response<Route>?) {

                        if (response != null && response.isSuccessful) {

                            val encodedPolylinePoints = response.body()!!.points
                            val decodedPoints = PolyUtil.decode(encodedPolylinePoints)

                            drawPolyLine(decodedPoints)
                            placeMarkerAt(clickedPoint)
                        } else {
                            // pokemonListListener.onFailure(appContext.getString(R.string.error_fetching_data))
                        }
                    }

                    override fun onFailure(call: Call<Route>?, t: Throwable?) {

                        Log.d("VITTU", "route failure msg: " + t!!.toString())
                        // pokemonListListener.onFailure(appContext.getString(R.string.error_fetching_data))
                    }
                }) // call.enqueue
            } // if
        } // setOnMapClickListener

        map.setOnMapLongClickListener {
            map.clear()
        }

        map.mapType = DEFAULT_MAP_TYPE
        // mMap.setMaxZoomPreference(20f)
        map.setMinZoomPreference(MIN_ZOOM)
        map.isIndoorEnabled = false
        map.uiSettings.apply {

            isCompassEnabled = false
            isRotateGesturesEnabled = false // VICTORY!!! WHOO HOOO !!! ^^
            isTiltGesturesEnabled = false
            // isMapToolbarEnabled = true // true by default ??
            isTiltGesturesEnabled = false
        }

        // Set listeners for polyline click events.
        map.setOnPolylineClickListener { _ -> Log.d("VITTU", "clicked on polyline!") }
        // map.setOnPolygonClickListener(this)
    } // setupMap

    // ********************* PERMISSIONS *******************************************************************************

    // note: only set permissionsGranted from this method, for clarity's sake
    private fun handlePermissions() {

        if (hasPermissions()) {

            permissionsGranted = true
        } else {

            permissionsGranted = false
            requestPermissions()
        }
    }

    private fun requestPermissions() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.INTERNET) || ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_NETWORK_STATE)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            // TODO: do this stuff...
        } else {
            // we can request the permission.
            ActivityCompat.requestPermissions(this, PERMISSIONS, ALL_PERMISSIONS)
        }
    } // requestPermissions

    // callback of requestPermissions()
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {

        when (requestCode) {

            ALL_PERMISSIONS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {

                    handlePermissions()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // TODO: exit the app I guess?
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        } // when
    } // onRequestPermissionsResult

    private fun hasPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // ********************* UTILS *******************************************************************************

    private fun locationToLatLng(loc: Location?): LatLng? {

        loc ?: return null
        val lat = loc.latitude
        val lng = loc.longitude
        return LatLng(lat, lng)
    }

    private fun moveCameraTo(destination: LatLng?) {

        destination ?: return
        map.moveCamera(CameraUpdateFactory.newLatLng(destination))
    }

    private fun placeMarkerAt(destination: LatLng) {

        map.addMarker(MarkerOptions().position(destination).title("Marker at $destination")) // TODO: convert lat/long to address
    }

    // determine if the camera should move to a new location (follow the user as they move)
    private fun isOverCameraMoveThreshold(from: LatLng?, to: LatLng?): Boolean {

        from ?: return false
        to ?: return false
        if (Math.abs(from.latitude) - Math.abs(to.latitude) > CAMERA_MOVE_THRESHOLD) return true
        if (Math.abs(from.longitude) - Math.abs(to.longitude) > CAMERA_MOVE_THRESHOLD) return true
        return false
    }

    private fun drawPolyLine(points: List<LatLng>) {

        map.addPolyline((PolylineOptions())
            .clickable(true)
            .addAll(points))
    }

    private fun stylePolyLine(polyline: Polyline) {

        // TODO: style it according to these instructions: https://developers.google.com/maps/documentation/android-sdk/polygon-tutorial
    }

} // MapsActivity
