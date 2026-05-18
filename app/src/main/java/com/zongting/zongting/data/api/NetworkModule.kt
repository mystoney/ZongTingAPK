package com.zongting.zongting.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.brotli.BrotliInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Referer 拦截器 - 酷我所有接口都需要
    private val refererInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Referer", "http://www.kuwo.cn/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        chain.proceed(request)
    }

    // 日志拦截器
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // OkHttpClient
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(refererInterceptor)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(BrotliInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // WAPI Retrofit (歌单、搜索、排行榜等)
    val wapiRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(KuwoApi.BASE_URL_WAPI)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // NMOBI Retrofit (播放地址)
    val nmobiRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(KuwoApi.BASE_URL_NMOBI)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // M Retrofit (歌词接口 - 需要移动端 User-Agent)
    private val mInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Referer", "http://m.kuwo.cn/")
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.0")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        chain.proceed(request)
    }

    private val mOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(mInterceptor)
        .addInterceptor(BrotliInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val mRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(KuwoApi.BASE_URL_M)
        .client(mOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Search Retrofit (老搜索接口 search.kuwo.cn)
    val searchRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(KuwoApi.BASE_URL_SEARCH)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // ── 酷我搜索专用 Retrofit (www.kuwo.cn，响应为 GBK 编码) ─────────────────────
    private val wwwInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Referer", "https://www.kuwo.cn/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/96.0.4664.110 Safari/537.36")
            .build()
        chain.proceed(request)
    }

    private val wwwOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(wwwInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val wwwOkHttp: OkHttpClient = wwwOkHttpClient

    // API 实例
    val kuwoApi: KuwoApi = wapiRetrofit.create(KuwoApi::class.java)
    val searchApi: KuwoApi = searchRetrofit.create(KuwoApi::class.java)
}
