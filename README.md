# ClockWidgets
App that houses clocks from various android devices

## Weather Clocks
Weather clock widgets powered by [Open-Meteo](https://open-meteo.com) (free, no API key; data CC BY 4.0):

- **Weather Clock (HTC)** – HTC Sense style 4x1
- **Weather Clock (Clean)** – transparent 4x1
- **Weather Clock (Galaxy S3)** – TouchWiz Nature UX 4x2 with weather photo backgrounds
- **Weather Clock (Galaxy S4)** – TouchWiz 4x2 with weather photo backgrounds
- **Weather Clock (Galaxy S)** – TouchWiz 3.0 analog AccuWeather clock from the original Galaxy S, 4x3
- **Weather Clock (Xperia)** – Sony Ericsson Xperia (2011) Home weather widget, 2x3, tap to flip to the 3-day forecast
- **Weather Clock (Chronus)** – CyanogenMod 10 lock clock (JB digital clock + weather panel), Android 4.2+

Set them up in the app's main screen ("Weather Clocks" section): °C/°F and location (current location or
city search via Open-Meteo geocoding). The settings are shared by all weather widgets. On a widget, tap the place name
to open the setup, tap the weather to refresh. On Android 4.2+ the HTC/Clean clocks use `TextClock`;
below that the app draws/pushes the time every minute itself. Code lives in `com.ntvdm.clockwidgets.weather`.
