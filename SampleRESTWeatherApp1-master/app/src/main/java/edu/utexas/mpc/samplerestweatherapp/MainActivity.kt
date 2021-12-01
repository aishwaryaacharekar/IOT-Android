package edu.utexas.mpc.samplerestweatherapp

import android.os.Build
import android.os.Bundle
import android.net.wifi.WifiManager
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.Date

class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var retrieveButton: Button
    lateinit var syncButton: Button

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mostRecentWeatherForecast: ForeCastWeatherResult
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var wifi: WifiManager
    var pisynced:Boolean=false;
    lateinit var mintemp: TextView
    lateinit var maxtemp: TextView
    lateinit var mintempfor: TextView
    lateinit var maxtempfor: TextView

    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.3:1883"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "testTopic1"
    val publishTopic = "testTopic2"

    var weatherData  = ""
    var weatherForecast  = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        retrieveButton = this.findViewById(R.id.retrieveButton)

        syncButton = this.findViewById(R.id.syncButton)

        // when the user presses the syncbutton, this method will get called
        syncButton.setOnClickListener({ syncWithPi() })
        // when the user presses the syncbutton, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })

        queue = Volley.newRequestQueue(this)
        gson = Gson()
        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        //Disabling wifi
        if(wifi.isWifiEnabled)
            wifi.isWifiEnabled = false
        // initialize the paho mqtt client with the uri and client id
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                val message = MqttMessage()

                message.payload = (weatherData + " " + weatherForecast).toByteArray()
                println("+++ before publish...")
                println(message)
                println(publishTopic)

                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
                pisynced=true;
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                textView.text = message.toString()
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    fun requestWeather(){
        println(wifi.isWifiEnabled)
        if(wifi.isWifiEnabled) {
            wifi.isWifiEnabled = false
            Thread.sleep(2_000)
        }
        //Sending stuff to PI
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?id=4254010&appid=ea9d4a9da52aaa9920a1f1ab27e0086e").toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
            com.android.volley.Response.Listener<String> { response ->
                //textView.text = response
                mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                println("requestweather")

                textView.text = mostRecentWeatherResult.weather.get(0).main

                val icon: String = mostRecentWeatherResult.weather.get(0).icon

                val iconUrl = "https://openweathermap.org/img/w/$icon.png"
                //print icon in image view
                val imageView: ImageView = this.findViewById(R.id.image_view)
                Picasso.with(this).load(iconUrl).into(imageView)

//                Toast.makeText(this@MainActivity,"Switch wifi! Look at your progress!", Toast.LENGTH_SHORT).show()

                Log.v("request weather", url);

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")
                val date = java.util.Date(mostRecentWeatherResult.dt *1000)
                val today_date = sdf.format(date)
                mintemp = this.findViewById(R.id.minitemp)
                maxtemp = this.findViewById(R.id.maxtmp)

                mintemp.text = mostRecentWeatherResult.main.temp_min.toString()
                maxtemp.text = mostRecentWeatherResult.main.temp_max.toString()

                weatherData = "WeatherData: "  +
                        " " +  today_date +
                        " " + mostRecentWeatherResult.main.temp_min.toString() +
                        " " + mostRecentWeatherResult.main.temp_max.toString() +
                        " " + mostRecentWeatherResult.main.humidity.toString()
                println(weatherData)

                requestForecast()

            },

            com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.

        queue.add(stringRequest)
    }

    fun requestForecast(){
        //Sending stuff to PI
        val url_forecast = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?id=524901&appid=ea9d4a9da52aaa9920a1f1ab27e0086e").toString()
        val forecastRequest = @RequiresApi(Build.VERSION_CODES.O)
        object : StringRequest(com.android.volley.Request.Method.GET, url_forecast,
            com.android.volley.Response.Listener<String> { response ->
                //textView.text = response
                mostRecentWeatherForecast = gson.fromJson(response, ForeCastWeatherResult::class.java)
                println("requestforecast")

                Log.v("request forecast", url_forecast);
                val forecast_epoch_date = mostRecentWeatherForecast.list.get(0).dt

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")
                val date = java.util.Date(forecast_epoch_date*1000)
                val forecast_date = sdf.format(date)

                mintempfor = this.findViewById(R.id.mintempfor)
                maxtempfor = this.findViewById(R.id.maxtempfor)
                mintempfor.text = mostRecentWeatherForecast.list.get(0).main.temp_min.toString()
                maxtempfor.text = mostRecentWeatherForecast.list.get(0).main.temp_max.toString()


                weatherForecast = "WeatherForecast: "  +
                        " " + forecast_date +
                        " " + mostRecentWeatherForecast.list.get(0).main.temp_min.toString() +
                        " " + mostRecentWeatherForecast.list.get(0).main.temp_max.toString() +
                        " " + mostRecentWeatherForecast.list.get(0).main.humidity.toString()
                println(weatherForecast)

            },

            com.android.volley.Response.ErrorListener { println("******forecast didn't work!") }) {}
        // Add the request to the RequestQueue.
//        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
//        //Disabling wifi
//        if(wifi.isWifiEnabled)
//            wifi.isWifiEnabled = false
        if(wifi.isWifiEnabled === false) {
            Toast.makeText(
                applicationContext,
                "WiFi is being enabled",
                Toast.LENGTH_LONG
            ).show()
            wifi.isWifiEnabled = true
            println("******That didn't work!"+ wifi.isWifiEnabled)
            syncWithPi()
        }
        queue.add(forecastRequest)

    }

    // this method just connects the paho mqtt client to the broker
    fun syncWithPi(){
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }
}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val dt: Long,val main: WeatherMain,val weather: Array<Weather>  )
class ForeCastWeatherResult(val list: Array<Forecast>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class Forecast(val dt: Long, val main: WeatherMain)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

