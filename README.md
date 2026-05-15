# FanVod TTS Helper

Pomocnicza aplikacja Android dla pluginu FanVodPL (Kodi).
Udostępnia systemowy głos Android TV przez prosty HTTP server na localhost.

## Jak zbudować APK przez GitHub (bez instalowania czegokolwiek)

1. Załóż konto na https://github.com (jeśli nie masz)
2. Utwórz nowe repozytorium — kliknij "New repository", nazwa np. `fanvod-tts`
3. Wgraj wszystkie pliki z tego folderu do repozytorium (przeciągnij i upuść na stronie GitHub)
4. GitHub automatycznie uruchomi budowanie APK (zakładka **Actions**)
5. Po ~2 minutach wejdź w **Actions → Build APK → ostatni run → Artifacts**
6. Pobierz plik `FanVodTTS-debug.zip` — w środku jest `app-debug.apk`

## Instalacja APK na Android TV / boxie

1. Włącz "Nieznane źródła" w ustawieniach urządzenia
   - Ustawienia → Bezpieczeństwo → Nieznane źródła → Włącz
   - (lub: Ustawienia → Preferencje urządzenia → Bezpieczeństwo → ...)
2. Wgraj APK na urządzenie przez pendrive lub ADB:
   ```
   adb install app-debug.apk
   ```
3. Uruchom aplikację raz ręcznie — potem startuje automatycznie z TV

## Jak działa

- Serwis nasłuchuje na `http://127.0.0.1:7799/speak?text=TEKST`
- Plugin Kodi wysyła tekst → serwis czyta go głosem systemowym Android
- Automatyczny start po włączeniu TV/boxa
- Działa równolegle z Gemini TTS (jako najlepszy silnik gdy APK aktywny)

## Test ręczny (opcjonalnie przez ADB)

```bash
adb shell am startservice pl.fanvod.tts/.TtsService
adb shell curl "http://127.0.0.1:7799/speak?text=Test+głosu+FanVod"
```

## Wymagania

- Android 7.0+ (API 24)
- Polski głos Google TTS zainstalowany w systemie
  (ten sam który mówi "szukam aplikacji" w Android TV)
