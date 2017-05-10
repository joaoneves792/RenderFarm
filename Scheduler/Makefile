JAVA=java
JC=javac
CLASSPATH=./aws-java-sdk/lib/aws-java-sdk-1.11.126.jar:./aws-java-sdk/third-party/lib/*:.
CLASSNAME=Scheduler

.java:
	$(JC) -cp $(CLASSPATH) $(CLASSNAME).java

run:
	$(JAVA) -cp $(CLASSPATH) $(CLASSNAME)