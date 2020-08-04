@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%
@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

set TESTTAGFILTERS=-no_windows,-no_test_windows,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
CALL :NORMALIZE_PATH "%SCRIPTDIR%..\..\.."
set BASEDIR=%RETVAL%

@rem Generate a UUID for use as the Bazel invocation ID
FOR /F "tokens=*" %%F IN ('uuidgen') DO (
SET INVOCATIONID=%%F
)

echo "Called with the following:  OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%, BASEDIR=%BASEDIR%"

set TARGETS=
for /f %%i in (%SCRIPTDIR%targets.win) do set TARGETS=!TARGETS! %%i

for /f %%e in ('%SCRIPTDIR%bazel.cmd info execution_root') do set EXECROOT=%%e
set EXECROOT=%EXECROOT:/=\%
set DUMMYLINK=%EXECROOT%\bazel-out\host\bin\tools\base\bazel\kotlinc.exe.runfiles\dummy_link
if exist %DUMMYLINK% (
  @rem Removing dummy_link as a workaround to http://b/160885823
  del %DUMMYLINK%
)
set DUMMYLINK=%EXECROOT%\bazel-out\host\bin\tools\base\bazel\formc.exe.runfiles\dummy_link
if exist %DUMMYLINK% (
  @rem Removing dummy_link as a workaround to http://b/160885823
  del %DUMMYLINK%
)

@echo studio_win.cmd time: %time%
@rem Remove --nouse_ijars which is a temporary fix for http://b/162497186
@rem Run Bazel
CALL %SCRIPTDIR%bazel.cmd ^
 --max_idle_secs=60 ^
 test ^
 --keep_going ^
 --config=dynamic ^
 --build_tag_filters=-no_windows ^
 --invocation_id=%INVOCATIONID% ^
 --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
 --test_tag_filters=%TESTTAGFILTERS% ^
 --profile=%DISTDIR%\winprof%BUILDNUMBER%.json.gz ^
 --nouse_ijars ^
 -- ^
 //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar ^
 //tools/base/profiler/native/trace_processor_daemon ^
 %TARGETS%

SET EXITCODE=%errorlevel%
@echo studio_win.cmd time: %time%

IF NOT EXIST %DISTDIR%\ GOTO ENDSCRIPT

echo "<meta http-equiv="refresh" content="0; URL='https://source.cloud.google.com/results/invocations/%INVOCATIONID%'" />" > %DISTDIR%\upsalite_test_results.html
@echo studio_win.cmd time: %time%

@rem copy skia parser artifact to dist dir
copy %BASEDIR%\bazel-bin\tools\base\dynamic-layout-inspector\skiaparser.zip %DISTDIR%

@rem copy trace processor daemon artifact to dist dir
copy %BASEDIR%\bazel-bin\tools\base\profiler\native\trace_processor_daemon\trace_processor_daemon.exe %DISTDIR%

@echo studio_win.cmd time: %time%

set JAVA=%BASEDIR%\prebuilts\studio\jdk\win64\jre\bin\java.exe

%JAVA% -jar %BASEDIR%\bazel-bin\tools\vendor\adt_infra_internal\rbe\logscollector\logs-collector_deploy.jar ^
 -bes %DISTDIR%\bazel-%BUILDNUMBER%.bes ^
 -perfzip %DISTDIR%\perfgate_data.zip

@echo studio_win.cmd time: %time%

:ENDSCRIPT
@rem On windows we must explicitly shut down bazel. Otherwise file handles remain open.
@echo studio_win.cmd time: %time%
CALL %SCRIPTDIR%bazel.cmd shutdown
@echo studio_win.cmd time: %time%
@rem We also must call the kill-processes.py python script and kill all processes still open
@rem within the src directory.  This is due to the fact go/ab builds must be removable after
@rem execution, and any open processes will prevent this removal on windows.
CALL %BASEDIR%\tools\vendor\adt_infra_internal\build\scripts\slave\kill-processes.cmd %BASEDIR%
@echo studio_win.cmd time: %time%
EXIT /B %exitcode%

@rem HELPER FUNCTIONS
:NORMALIZE_PATH
  SET RETVAL=%~dpfn1
  EXIT /B
