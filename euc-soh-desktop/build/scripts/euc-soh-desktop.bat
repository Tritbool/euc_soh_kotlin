@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  euc-soh-desktop startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and EUC_SOH_DESKTOP_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\euc-soh-desktop-1.0.0.jar;%APP_HOME%\lib\euc-soh-core-1.0.0.jar;%APP_HOME%\lib\kotlinx-coroutines-core-jvm-1.8.1.jar;%APP_HOME%\lib\kotlinx-coroutines-swing-1.8.1.jar;%APP_HOME%\lib\dataframe-0.14.1.jar;%APP_HOME%\lib\dataframe-arrow-0.14.1.jar;%APP_HOME%\lib\dataframe-excel-0.14.1.jar;%APP_HOME%\lib\dataframe-openapi-0.14.1.jar;%APP_HOME%\lib\dataframe-jdbc-0.14.1.jar;%APP_HOME%\lib\dataframe-core-0.14.1.jar;%APP_HOME%\lib\fuel-2.3.1.jar;%APP_HOME%\lib\kotlinpoet-jvm-1.18.1.jar;%APP_HOME%\lib\kotlin-reflect-2.0.20.jar;%APP_HOME%\lib\kotlinx-datetime-jvm-0.6.1.jar;%APP_HOME%\lib\kotlin-stdlib-jdk8-2.0.20.jar;%APP_HOME%\lib\kotlinx-serialization-json-jvm-1.7.1.jar;%APP_HOME%\lib\kotlinx-serialization-core-jvm-1.7.1.jar;%APP_HOME%\lib\result-3.1.0.jar;%APP_HOME%\lib\kotlin-logging-jvm-7.0.0.jar;%APP_HOME%\lib\kotlin-stdlib-jdk7-2.0.20.jar;%APP_HOME%\lib\kotlin-stdlib-2.1.0.jar;%APP_HOME%\lib\poi-ooxml-5.3.0.jar;%APP_HOME%\lib\poi-5.3.0.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\annotations-23.0.0.jar;%APP_HOME%\lib\commons-csv-1.11.0.jar;%APP_HOME%\lib\commons-compress-1.27.1.jar;%APP_HOME%\lib\swagger-parser-2.1.22.jar;%APP_HOME%\lib\swagger-parser-v2-converter-2.1.22.jar;%APP_HOME%\lib\swagger-parser-v3-2.1.22.jar;%APP_HOME%\lib\swagger-compat-spec-parser-1.0.70.jar;%APP_HOME%\lib\swagger-parser-1.0.70.jar;%APP_HOME%\lib\swagger-parser-safe-url-resolver-2.1.22.jar;%APP_HOME%\lib\swagger-parser-safe-url-resolver-1.0.70.jar;%APP_HOME%\lib\commons-io-2.16.1.jar;%APP_HOME%\lib\arrow-vector-17.0.0.jar;%APP_HOME%\lib\arrow-format-17.0.0.jar;%APP_HOME%\lib\arrow-memory-unsafe-17.0.0.jar;%APP_HOME%\lib\mariadb-java-client-3.4.1.jar;%APP_HOME%\lib\httpclient-4.5.14.jar;%APP_HOME%\lib\commons-codec-1.17.1.jar;%APP_HOME%\lib\arrow-memory-core-17.0.0.jar;%APP_HOME%\lib\swagger-parser-core-2.1.22.jar;%APP_HOME%\lib\swagger-core-2.2.21.jar;%APP_HOME%\lib\swagger-models-2.2.21.jar;%APP_HOME%\lib\swagger-core-1.6.14.jar;%APP_HOME%\lib\swagger-models-1.6.14.jar;%APP_HOME%\lib\jackson-annotations-2.17.1.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.17.1.jar;%APP_HOME%\lib\jackson-datatype-jsr310-2.17.1.jar;%APP_HOME%\lib\json-patch-1.13.jar;%APP_HOME%\lib\json-schema-validator-2.2.14.jar;%APP_HOME%\lib\json-schema-core-1.2.14.jar;%APP_HOME%\lib\jackson-coreutils-equivalence-1.0.jar;%APP_HOME%\lib\jackson-coreutils-2.0.jar;%APP_HOME%\lib\jackson-databind-2.17.1.jar;%APP_HOME%\lib\jackson-core-2.17.1.jar;%APP_HOME%\lib\flatbuffers-java-24.3.25.jar;%APP_HOME%\lib\commons-lang3-3.16.0.jar;%APP_HOME%\lib\poi-ooxml-lite-5.3.0.jar;%APP_HOME%\lib\xmlbeans-5.2.1.jar;%APP_HOME%\lib\curvesapi-1.08.jar;%APP_HOME%\lib\log4j-api-2.23.1.jar;%APP_HOME%\lib\commons-collections4-4.4.jar;%APP_HOME%\lib\SparseBitSet-1.3.jar;%APP_HOME%\lib\snakeyaml-2.2.jar;%APP_HOME%\lib\waffle-jna-3.3.0.jar;%APP_HOME%\lib\uri-template-0.10.jar;%APP_HOME%\lib\guava-32.1.3-jre.jar;%APP_HOME%\lib\msg-simple-1.2.jar;%APP_HOME%\lib\btf-1.3.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\jna-platform-5.13.0.jar;%APP_HOME%\lib\jna-5.13.0.jar;%APP_HOME%\lib\caffeine-2.9.3.jar;%APP_HOME%\lib\checker-qual-3.37.0.jar;%APP_HOME%\lib\jakarta.xml.bind-api-2.3.3.jar;%APP_HOME%\lib\swagger-annotations-2.2.21.jar;%APP_HOME%\lib\error_prone_annotations-2.21.1.jar;%APP_HOME%\lib\jakarta.activation-api-1.2.2.jar;%APP_HOME%\lib\swagger-annotations-1.6.14.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\validation-api-1.1.0.Final.jar;%APP_HOME%\lib\joda-time-2.10.5.jar;%APP_HOME%\lib\libphonenumber-8.11.1.jar;%APP_HOME%\lib\jopt-simple-5.0.4.jar;%APP_HOME%\lib\httpcore-4.4.16.jar;%APP_HOME%\lib\commons-logging-1.2.jar;%APP_HOME%\lib\rhino-1.7.7.2.jar


@rem Execute euc-soh-desktop
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %EUC_SOH_DESKTOP_OPTS%  -classpath "%CLASSPATH%" io.github.eucsoh.desktop.MainKt %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable EUC_SOH_DESKTOP_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%EUC_SOH_DESKTOP_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
