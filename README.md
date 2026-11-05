# ClockWidgets
App that houses clocks from various android devices

## Weather Clocks
Weather clock widgets powered by [Open-Meteo](https://open-meteo.com) (free, no API key; data CC BY 4.0):

- **HTC senseUI 5** – HTC Sense style 4x1
- **S6 Style** – transparent 4x1
- **Galaxy S3 style** – TouchWiz Nature UX 4x2 with weather photo backgrounds
- **Galaxy S4 style** – TouchWiz 4x2 with weather photo backgrounds
- **Galaxy S1 style** – TouchWiz 3.0 analog AccuWeather clock from the original Galaxy S, 4x3
- **Xperia (2.3) style** – Sony Ericsson Xperia (2011) Home weather widget, 2x3, tap to flip to the 3-day forecast
- **LG (4.x) style** (4x2, 4x1, 2x2) – LG Optimus (2012) weather widgets with the condition photos, 4x1 with a 3-day strip
- **Chronus (4.2) style** – CyanogenMod 10 lock clock (JB digital clock + weather panel), Android 4.2+

Set them up in the app's main screen ("Weather Clocks" section): °C/°F and location (current location or
city search via Open-Meteo geocoding). The settings are shared by all weather widgets. On a widget, tap the place name
to open the setup, tap the weather to refresh. On Android 4.2+ the HTC/Clean clocks use `TextClock`;
below that the app draws/pushes the time every minute itself. Code lives in `com.ntvdm.clockwidgets.weather`.
