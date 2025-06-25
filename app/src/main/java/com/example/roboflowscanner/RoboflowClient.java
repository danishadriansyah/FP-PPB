package com.example.roboflowscanner;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RoboflowClient {
    private static final String BASE_URL = "https://serverless.roboflow.com/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static RoboflowApi getApiService() {
        return getClient().create(RoboflowApi.class);
    }
}