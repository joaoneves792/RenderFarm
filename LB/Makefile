LB_CLASSES= LoadBalancer.java 

JAVA=java
JC=javac
CLASSPATH= /opt/aws-java-sdk/lib/aws-java-sdk-1.11.128.jar:/opt/aws-java-sdk/third-party/lib/*:.:../MetricsManager/
JAVA_OPTIONS=-XX:-UseSplitVerifier -Duser.language=en -Duser.country=US


.SUFFIXES: .java .class
.java.class:
	$(JC) -cp $(CLASSPATH) $*.java

all: warning jdk-version-check clean lb

.PHONY: jdk-version-check
jdk-version-check:
	@echo -e "$(shell javac -version 2>&1)\njavac 1.8" | sort -ct. -k1,1n -k2,2n -k3,3n
	@echo -e "$(shell java -version 2>&1|head -1)\njava 1.8" | sort -ct. -k1,1n -k2,2n -k3,3n


warning:
	@echo "+********************************+"
	@echo "| MAKE SURE YOU ARE USING JAVA 7 |"
	@echo "| To run the project do make run |"
	@echo "+********************************+"

lb: $(LB_CLASSES:.java=.class)

run: jdk-version-check
	$(JAVA) $(JAVA_OPTIONS) -cp $(CLASSPATH) LoadBalancer
	
clean:
	rm *.class
