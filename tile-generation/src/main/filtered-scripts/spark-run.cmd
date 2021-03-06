@echo off

rem Make sure SPARK_HOME is set
if not defined SPARK_HOME (
  echo SPARK_HOME not set.  Please set SPARK_HOME environment variable and try again.
  goto:eof
)

rem Make sure SCALA_HOME is set
if not defined SCALA_HOME (
  echo SCALA_HOME not set.  Please set SCALA_HOME environment variable and try again.
  goto:eof
)

rem Set up the Spark classpath
rem Start it empty
set SPARK_CLASSPATH=

call:addToSparkClasspath com.oculusinfo math-utilities ${project.version} "%SPARK_CLASSPATH%" SPARK_CLASSPATH
call:addToSparkClasspath com.oculusinfo binning-utilities ${project.version} "%SPARK_CLASSPATH%" SPARK_CLASSPATH
call:addToSparkClasspath com.oculusinfo tile-generation ${project.version} "%SPARK_CLASSPATH%" SPARK_CLASSPATH
call:addToSparkClasspath org.apache.hadoop. hadoop-common ${hadoop-common-version} "%SPARK_CLASSPATH%" SPARK_CLASSPATH
rem avro and commons-compress versions are derived indirectly, can't be coded here.
call:addToSparkClasspath org.apache.avro avro 1.7.4 "%SPARK_CLASSPATH%" SPARK_CLASSPATH
call:addToSparkClasspath org.apache.commons commons-compress 1.4.1 "%SPARK_CLASSPATH%" SPARK_CLASSPATH

rem Run our command
echo Running Spark from %SPARK_HOME%
echo Running Scala from %SCALA_HOME%
echo Spark Classpath is %SPARK_CLASSPATH%
echo Arguments: %*

cmd /V /E /C %SPARK_HOME%\spark-class2.cmd %*

goto:eof



:addToSparkClasspath
SETLOCAL
set groupId=%~1
set artifactId=%~2
set version=%~3
set SPARK_CLASSPATH=%~4
set groupDir=%groupId:.=\%
set repo=%HOME%.m2\repository
set jardir=%repo%\%groupDir%\%artifactId%\%version%

set jar=%jardir%\%artifactId%-%version%.jar

if NOT "" == "%SPARK_CLASSPATH%" set SPARK_CLASSPATH=%SPARK_CLASSPATH%;%jar%
if "" == "%SPARK_CLASSPATH%" set SPARK_CLASSPATH=%jar%

ENDLOCAL & set %5=%SPARK_CLASSPATH%

goto:eof
