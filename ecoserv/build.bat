@echo off
rem Сборка EcoServ: нужен JDK 8 (8u101 или новее), Gradle 4.10.3 скачается сам.
chcp 65001 >nul
cd /d "%~dp0"
if "%JAVA_HOME%"=="" (
  echo [EcoServ] Не задан JAVA_HOME. Установи JDK 8 ^(например Temurin 8 с adoptium.net^) и выполни:
  echo   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.x.x-hotspot
  pause
  exit /b 1
)
"%JAVA_HOME%\bin\java" -version 2>&1 | findstr /c:"1.8.0_" >nul
if errorlevel 1 (
  echo [EcoServ] JAVA_HOME указывает не на JDK 8 с номером обновления:
  "%JAVA_HOME%\bin\java" -version
  echo Нужен JDK 8u101 или новее ^(не 9, не 11, не 17^).
  pause
  exit /b 1
)
call gradlew.bat build %*
if errorlevel 1 ( pause & exit /b 1 )
echo.
echo [EcoServ] Готово: build\libs\
dir /b build\libs
pause
