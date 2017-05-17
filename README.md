# OpenLibre
The OpenLibre app for Android can be used to read the tissue glucose measurement data from the FreeStyle Libre CGM device using a NFC capable phone.

Its allows users to see the current tissue glucose level as well an estimation of the current blood glucose level based on the tissue glucose levels from the past 15 minutes.
Historic data are visualized graphically in a plot.

Further, the data can be uploaded by the user to their online account at Tidepool for more comprehensive data visualization in combination with data from other devices.

## Privacy
By default the data read from the sensor is only stored locally on the phone.
It can be exported to the phone's memory in JSON format.
Only when explicitly specified by the user, the data will be uploaded to the servers of the open-source project Tidepool.
Only after manual confirmation by the user will crash reports be send to the author of this app.

## Building
The app depends on the [TidepoolAPILib](https://github.com/DorianScholz/TidepoolAPILib).

1. Clone the TidepoolAPILib repo into the same parent directory as the OpenLibre repo, e.g.:
- ~/src/OpenLibre
- ~/src/TidepoolAPILib

2. Then build the TidepoolAPILib using Andorid Studio so the AAR is generated:
- ~/src/TidepoolAPILib/tidepool-api-lib/build/outputs/aar/tidepool-api-lib-debug.aar

3. Finally build the OpenLibre app using Android Studio to produce the APK


## Disclaimer
This app is not affiliated with or approved by the manufacturer of the CGM device.

The author disclaims all warranties with regard to this software, including all implied warranties of merchantability and fitness.
In no event shall the author be liable for any special, indirect or consequential damages or any damages whatsoever resulting from loss of use, data or profits,
whether in an action of contract, negligence or other tortious action, arising out of or in connection with the use or performance of this software.

## Credits
The app has been inspired by [LiApp](https://github.com/CMKlug/Liapp) and [LibreAlarm](https://github.com/pimpimmi/LibreAlarm).
Thanks to Marcel Klug for figuring out the data structure of the Libre and publishing it: http://www.marcelklug.de/2015/04/15/freestyle-libre-ein-offenes-buch/

## License
The code of this project is published under the GPLv3 license.
See the LICENSE.txt file for details.
