package com.example.animeresolver;

import android.content.Context;

import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Shared image client. AniFun's cover host requires its site referer to allow image requests. */
public final class ImageLoader {
    private static Picasso instance;

    private ImageLoader() {}

    public static synchronized Picasso with(Context context) {
        if (instance == null) {
            OkHttpClient client = new OkHttpClient();
            Downloader downloader = new Downloader() {
                @Override
                public Response load(Request request) throws IOException {
                    Request.Builder builder = request.newBuilder();
                    String host = request.url().host();
                    if ("img.anifun.cn".equalsIgnoreCase(host)) {
                        builder.header("Referer", "https://anifun.cn/");
                        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36");
                        builder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                    }
                    return client.newCall(builder.build()).execute();
                }

                @Override
                public void shutdown() {
                    client.dispatcher().cancelAll();
                }
            };
            instance = new Picasso.Builder(context.getApplicationContext())
                    .downloader(downloader)
                    .build();
        }
        return instance;
    }
}
