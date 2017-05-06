JAVA=java
JC=javac
CLASSPATH=./aws-java-sdk/lib/aws-java-sdk-1.11.126.jar:./aws-java-sdk/third-party/lib/*:.

.java:
	$(JC) -cp $(CLASSPATH) MetricsManager.java

run:
	$(JAVA) -cp $(CLASSPATH) MetricsManager