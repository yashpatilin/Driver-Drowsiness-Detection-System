package com.example.drowsiness.network

object ESP32Config {
    @Volatile
    var baseIp: String = "192.168.4.1"

    val streamUrl: String
        get() = "http://$baseIp:81/stream"

    val alertOnUrl: String
        get() = "http://$baseIp:82/alert"

    val alertOffUrl: String
        get() = "http://$baseIp:82/stop"

    val pingUrl: String
        get() = "http://$baseIp:82/"

    fun setWifiUrl(ssid: String, pass: String): String {
        return "http://$baseIp:82/setwifi?ssid=$ssid&pass=$pass"
    }

    val resetWifiUrl: String
        get() = "http://$baseIp:82/resetwifi"
}
