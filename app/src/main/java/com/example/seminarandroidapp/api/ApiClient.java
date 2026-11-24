package com.example.seminarandroidapp.api;

import android.content.Context;
import com.example.seminarandroidapp.util.Prefs;
import com.google.gson.Gson;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String BASE_URL = "https://backend.kbnet.si/";
    private static Retrofit retrofit = null;
    private static OkHttpClient okHttpClient;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.NONE);

            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder();

                String token = Prefs.getToken(context);
                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                return chain.proceed(builder.build());
            };

            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static ApiService getApiService(Context context) {
        return getClient(context).create(ApiService.class);
    }

    public static OkHttpClient getOkHttpClient(Context context) {
        getClient(context);
        return okHttpClient;
    }

    public static String getWebSocketUrl() {
        return BASE_URL.replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://") + "ws";
    }

    public static String parseError(Response<?> response) {
        if (response != null && response.errorBody() != null) {
            try {
                ResponseBody errorBody = response.errorBody();
                Gson gson = new Gson();
                ErrorResponse err = gson.fromJson(errorBody.string(), ErrorResponse.class);
                if (err != null && err.getError() != null) {
                    if (err.getError().getMessage() != null && !err.getError().getMessage().isEmpty()) {
                        return err.getError().getMessage();
                    }
                    if (err.getError().getCode() != null && !err.getError().getCode().isEmpty()) {
                        return err.getError().getCode();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "Request failed (" + (response != null ? response.code() : "unknown error") + ")";
    }
}