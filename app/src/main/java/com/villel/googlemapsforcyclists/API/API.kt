package com.villel.googlemapsforcyclists.API

import com.google.android.gms.maps.model.LatLng
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface API {

    companion object {
        const val BASE_URL = "https://us-central1-truenorth-backend.cloudfunctions.net/"
    }

    @Headers("Content-Type: application/json")
    @GET("test")
    fun test(): Call<Test>

    @GET("route/{from}+{to}")
    fun fetchRoute(@Path("from") from: LatLng, @Path("to") to: LatLng): Call<List<LatLng>>

    @GET("distance/{from}+{to}")
    fun fetchDistance(@Path("from") from: LatLng, @Path("to") to: LatLng): Call<Float>

    @GET("elevations/{from}+{to}")
    fun fetchElevations(@Path("from") from: LatLng, @Path("to") to: LatLng): Call<List<Float>>
}