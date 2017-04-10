SERVER_CLASSES=WebServer.java

RAYTRACER_FOLDER= ./raytracer-master/src/raytracer
RAYTRACER_FOLDER2= ./raytracer-master/src/raytracer/pigments
RAYTRACER_FOLDER3= ./raytracer-master/src/raytracer/shapes

JAVA=java
JC=javac
CLASSPATH=.:./BIT:./BIT/samples:./raytracer-master/src 

all: warning server bit raytracer instrumentation

.SUFFIXES: .java .class
.java.class:
	$(JC) -cp $(CLASSPATH) $*.java 

warning:
	@echo "+******************************+"
	@echo "|MAKE SURE YOU ARE USING JAVA 7|"
	@echo "|To run the project do make run|"
	@echo "+******************************+"

server: $(SERVER_CLASSES:.java=.class)

bit:
	make -C ./BIT/samples

raytracer:
	make -C ./raytracer-master

instrumentation:
	$(JAVA) -cp $(CLASSPATH) MethodCallsCount $(RAYTRACER_FOLDER) $(RAYTRACER_FOLDER)
	$(JAVA) -cp $(CLASSPATH) MethodCallsCount $(RAYTRACER_FOLDER2) $(RAYTRACER_FOLDER2)
	$(JAVA) -cp $(CLASSPATH) MethodCallsCount $(RAYTRACER_FOLDER3) $(RAYTRACER_FOLDER3)
	
	$(JAVA) -cp $(CLASSPATH) MethodCallsCount . .

run:
	$(JAVA) -cp $(CLASSPATH) WebServer

clean:
	rm *.class
	make clean -C ./BIT/samples
	cd ./raytracer-master && make clean
