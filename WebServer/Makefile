SERVER_CLASSES= WebServer.java

INSTRUMENTED_FOLDERS= ./raytracer-master/src/raytracer ./raytracer-master/src/raytracer/pigments ./raytracer-master/src/raytracer/shapes .

JAVA=java
JC=javac
CLASSPATH=.:./BIT:./BIT/samples:./raytracer-master/src
JAVA_OPTIONS=-XX:-UseSplitVerifier -Duser.language=en -Duser.country=US


.SUFFIXES: .java .class
.java.class:
	$(JC) -cp $(CLASSPATH) $*.java

all: warning jdk-version-check server bit raytracer instrumentation

.PHONY: jdk-version-check
jdk-version-check:
	@echo -e "$(shell javac -version 2>&1)\njavac 1.8" | sort -ct. -k1,1n -k2,2n -k3,3n

warning:
	@echo "+********************************+"
	@echo "| MAKE SURE YOU ARE USING JAVA 7 |"
	@echo "| To run the project do make run |"
	@echo "+********************************+"

server: $(SERVER_CLASSES:.java=.class)

bit:
	make -C ./BIT/samples

raytracer:
	make -C ./raytracer-master

.PHONY: $(INSTRUMENTED_FOLDERS)
instrumentation: $(INSTRUMENTED_FOLDERS)

$(INSTRUMENTED_FOLDERS):
	$(JAVA) $(JOPTS) -cp $(CLASSPATH) MethodCallsCount $@ $@

run:
	$(JAVA) $(JAVA_OPTIONS) -cp $(CLASSPATH) WebServer
	
clean:
	rm -R rendered-images
	rm _instrumentation_data.txt
	rm *.class
	make clean -C ./BIT/samples
	make clean -C ./raytracer-master
