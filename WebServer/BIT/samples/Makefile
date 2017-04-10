BIT_CLASSES=MethodCallsCount.java

JAVA=java
JC=javac
CLASSPATH=.:..

.SUFFIXES: .java .class
.java.class:
	$(JC) -cp $(CLASSPATH) $*.java 

all: $(BIT_CLASSES:.java=.class) 

clean:
	rm MethodCallsCount.class
