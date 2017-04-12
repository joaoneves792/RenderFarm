SERVER_CLASSES= WebServer.java

INSTRUMENTED_FOLDERS= ./raytracer-master/src/raytracer ./raytracer-master/src/raytracer/pigments ./raytracer-master/src/raytracer/shapes .

JAVA=java
JC=javac
CLASSPATH=.:./BIT:./BIT/samples:./raytracer-master/src 

.SUFFIXES: .java .class
.java.class:
	$(JC) -cp $(CLASSPATH) $*.java

all: warning server bit raytracer instrumentation

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
	$(JAVA) -cp $(CLASSPATH) MethodCallsCount $@ $@

run:
# 	$(JAVA) -cp $(CLASSPATH) WebServer
	$(JAVA) -XX:-UseSplitVerifier -cp $(CLASSPATH) WebServer
	
clean:
	rm *.class
	make clean -C ./BIT/samples
	make clean -C ./raytracer-master
