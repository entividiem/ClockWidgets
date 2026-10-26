# ClockWidgets
App that houses clocks from various android devices

## Weather Clocks
Four weather clock widgets powered by [Pirate Weather](https://pirateweather.net) (free API key needed):

- **Weather Clock (HTC)** – HTC Sense style 4x1
- **Weather Clock (Clean)** – transparent 4x1
- **Weather Clock (Galaxy S3)** – TouchWiz Nature UX 4x2 with weather photo backgrounds
- **Weather Clock (Galaxy S4)** – TouchWiz 4x2 with weather photo backgrounds
- **Weather Clock (Chronus)** – CyanogenMod 10 lock clock (JB digital clock + weather panel), Android 4.2+

Set them up in the app's main screen ("Weather Clocks" section): API key, °C/°F and location (current location or
city search via Open-Meteo geocoding). The settings are shared by all weather widgets. On a widget, tap the place name
to open the setup, tap the weather to refresh. On Android 4.2+ the HTC/Clean clocks use `TextClock`;
below that the app draws/pushes the time every minute itself. Code lives in `com.ntvdm.clockwidgets.weather`.
