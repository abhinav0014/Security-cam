@echo off
setlocal

set GRADLE_HOME=gradle
set GRADLE_WRAPPER_JAR=%GRADLE_HOME%\wrapper\gradle-wrapper.jar
set GRADLE_WRAPPER_PROPERTIES=%GRADLE_HOME%\wrapper\gradle-wrapper.properties

if not exist "%GRADLE_WRAPPER_JAR%" (
    echo "Gradle wrapper jar not found. Please run 'gradle wrapper' to generate it."
    exit /b 1
)

if not exist "%GRADLE_WRAPPER_PROPERTIES%" (
    echo "Gradle wrapper properties not found. Please run 'gradle wrapper' to generate it."
    exit /b 1
)

set WRAPPER_VERSION=
for /f "tokens=2 delims==" %%i in ('findstr /b "distributionUrl=" "%GRADLE_WRAPPER_PROPERTIES%"') do (
    set WRAPPER_VERSION=%%i
)

if "%WRAPPER_VERSION%"=="" (
    echo "No Gradle version specified in gradle-wrapper.properties."
    exit /b 1
)

java -jar "%GRADLE_WRAPPER_JAR%" %* 

endlocal