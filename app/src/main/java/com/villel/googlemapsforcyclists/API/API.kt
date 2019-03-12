package com.villel.googlemapsforcyclists.API

import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface API {

    companion object {
        const val BASE_URL = "https://us-central1-truenorth-backend.cloudfunctions.net/"
        // const val BASE_URL = "http://localhost:5000/truenorth-backend/us-central1/" // local... doesn't work for now
    }

    // the @Query annotation auto-converts it to the form "route/?from=value1?to=value2"
    @GET("route/")
    fun fetchRoute(@Query("from") from: String, @Query("to") to: String): Call<Route>

    @GET("distance/{from}-{to}")
    fun fetchDistance(@Path("from") from: LatLng, @Path("to") to: LatLng): Call<Float>

    @GET("elevations/{from}-{to}")
    fun fetchElevations(@Path("from") from: LatLng, @Path("to") to: LatLng): Call<List<Float>>
}