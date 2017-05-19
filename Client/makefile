
JAVA_OPTIONS=-Duser.language=en -Duser.country=US

STIME = date '+%s' > $@_time
ETIME = read st < $@_time ; echo $$((`date '+%s'`-$$st))

all: clean compile

compile:
	javac *.java
	
clean:
	-rm *.class
	
run:
	java $(JAVA_OPTIONS) ServerTest

runfull:
	java $(JAVA_OPTIONS) ServerTestFull
	
runsimple:
	java $(JAVA_OPTIONS) ServerTestSimple
	
runincremental:
	java $(JAVA_OPTIONS) IncrementalServerTest
