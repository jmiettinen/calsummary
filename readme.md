
## Usage

Go to [Google Calendar settings](https://calendar.google.com/calendar/u/0/r/settings), select the correct calendar
and choose "Export Calendar".

Run
```shell
./gradlew build
java -jar app/build/libs/calsummary.jar <PATH_TO_MY_EXPORTED_CALENDAR>
```

For custom matchers, see
`java -jar app/build/libs/calsummary.jar` for format and then
```shell
java -jar app/build/libs/calsummary.jar -c config.json <PATH_TO_MY_EXPORTED_CALENDAR>
```
run with `config.json`.