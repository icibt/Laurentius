@echo off

set "WILDFLY_HOME="
set "RESOLVED_WILDFLY_HOME="
set "LAU_HOME="
set "INIT=false"
set "LAU_OPTS=-c standalone-ebms.xml"

:loop
      if ["%~1"]==[""] (
        echo done.
        goto endParamReading
      )
	  
	  if ["%~1"]==["--init"] (
        set "INIT=true"
      )
	  
	  if ["%~1"]==["-w"] (
	    
		set "WILDFLY_HOME=%~2"
		echo WILDFLY_HOME = "%WILDFLY_HOME%".
      )
	    
	  if ["%~1"]==["-s"] (
	    shift
		set "LAU_HOME=%~2"
		echo LAU_HOME = "%LAU_HOME%".
      )	  
      ::--------------------------
      shift
      goto loop
:endParamReading

pushd "%CD%\.."
	set "RESOLVED_WILDFLY_HOME=%CD%"
popd


if "x%WILDFLY_HOME%" == "x" (
		set "WILDFLY_HOME=%RESOLVED_WILDFLY_HOME%"

)

if "x%LAU_HOME%" == "x" (
  set  "LAU_HOME=%WILDFLY_HOME%\standalone\data\laurentius.home"
)

set "LAU_OPTS=%LAU_OPTS% -Dlaurentius.home=%LAU_HOME%"



if "%INIT%" == "true" (
	
	set "LAU_OPTS=%LAU_OPTS% -Dorg.sed.msh.hibernate.hbm2ddl.auto=create -Dorg.sed.msh.hibernate.dialect=org.hibernate.dialect.H2Dialect -Dorg.sed.init.lookups=%LAU_HOME%\sed-settings.xml"
)


echo *********************************************************************************************************************************
echo * WILDFLY_HOME =  "%WILDFLY_HOME%"
echo * LAU_HOME     =  "%LAU_HOME%"
echo * INIT         =  "%INIT%"
echo * LAU_OPTS     =  "%LAU_OPTS%"
echo *********************************************************************************************************************************

%WILDFLY_HOME%\bin\standalone.bat %LAU_OPTS%
