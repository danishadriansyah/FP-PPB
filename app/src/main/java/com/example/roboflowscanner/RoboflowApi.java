package com.example.roboflowscanner;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RoboflowApi {
    @Multipart
    @POST("{model_id}")
    Call<ResponseBody> detectObjects(
            @Path("model_id") String modelId,
            @Query("api_key") String apiKey,
            @Part MultipartBody.Part image
    );
}