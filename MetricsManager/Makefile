JAVA=java
JC=javac
CLASSPATH= /opt/aws-java-sdk/lib/aws-java-sdk-1.11.128.jar:/opt/aws-java-sdk/third-party/lib/*:.

all:
	$(JC) -cp $(CLASSPATH) MetricsManager.java

run:
	$(JAVA) -cp $(CLASSPATH) MetricsManager

clean:
	rm ./*.class
